package fleetrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Things that could go wrong inside the database layer: missing rows, bad links, half-saved data.
class DatabaseEdgeCaseTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private Database db;
    private int vehicleId;
    private int renterId;

    @BeforeEach
    void setUp() throws SQLException {
        db = new Database("jdbc:sqlite::memory:");
        db.createTables();
        vehicleId = db.insertVehicle(VehicleFactory.create(0, "SEDAN", "Toyota", "Vios", 2022, "ABC 123", "White",
                1500, 1000, null, "Car > Sedan"));
        renterId = db.insertRenter(new Renter(0, "Ana Reyes", "0917", null, null));
    }

    @AfterEach
    void tearDown() throws SQLException {
        db.close();
    }

    private Rental rental(int renter, int vehicle, int startOffset, int endOffset) {
        return new Rental(0, renter, vehicle, TODAY.plusDays(startOffset), TODAY.plusDays(endOffset), null, 0,
                RentalStatus.ONGOING);
    }

    private Reservation reservation(int renter, int vehicle, int startOffset, int endOffset) {
        return new Reservation(0, renter, vehicle, TODAY.plusDays(startOffset), TODAY.plusDays(endOffset),
                ReservationStatus.ACTIVE, null, TODAY);
    }

    @Test
    void missingRowsComeBackAsNullNotAsACrash() throws SQLException {
        assertNull(db.getVehicleById(999));
        assertNull(db.getRentalById(999));
        assertNull(db.getReservationById(999));
        assertNull(db.getAccountById(999));
        assertNull(db.getRenterById(999));
        assertNull(db.findAccountByUsername("nobody"));
        assertNull(db.findAccountByUsername(null));
        assertFalse(db.isVehicleActive(999));
        assertFalse(db.hasOngoingRental(999));
    }

    @Test
    void aRentalForAVehicleOrRenterThatDoesNotExistIsRefusedByTheDatabase() {
        assertThrows(SQLException.class, () -> db.insertRental(rental(renterId, 999, 0, 1)));
        assertThrows(SQLException.class, () -> db.insertRental(rental(999, vehicleId, 0, 1)));
        assertThrows(SQLException.class, () -> db.insertReservation(reservation(999, vehicleId, 1, 2)));
        assertThrows(SQLException.class, () -> db.insertReservation(reservation(renterId, 999, 1, 2)));
    }

    @Test
    void aRefusedInsertLeavesNoRowBehind() throws SQLException {
        assertThrows(SQLException.class, () -> db.insertRental(rental(renterId, 999, 0, 1)));
        assertTrue(db.getAllRentals().isEmpty());
    }

    @Test
    void aBatchWithOneBadRentalSavesNothingAndKeepsTheMileage() throws SQLException {
        List<Rental> batch = List.of(
                new Rental(0, renterId, vehicleId, TODAY.minusDays(5), TODAY.minusDays(4), TODAY.minusDays(4), 100,
                        RentalStatus.RETURNED),
                new Rental(0, renterId, 999, TODAY.minusDays(3), TODAY.minusDays(2), TODAY.minusDays(2), 50,
                        RentalStatus.RETURNED));
        assertThrows(SQLException.class, () -> db.insertRentalBatch(batch));
        assertTrue(db.getAllRentals().isEmpty());
        assertEquals(1000, db.getVehicleById(vehicleId).getMileage());
        // and the connection still works afterwards (auto-commit was put back)
        assertEquals(1, db.insertRentalBatch(batch.subList(0, 1)));
        assertEquals(1100, db.getVehicleById(vehicleId).getMileage());
    }

    @Test
    void anEmptyBatchIsFine() throws SQLException {
        assertEquals(0, db.insertRentalBatch(List.of()));
    }

    @Test
    void renterWithEmptyOptionalFieldsIsStoredAndReadBack() throws SQLException {
        int id = db.insertRenter(new Renter(0, "Solo", null, null, null));
        Renter r = db.getRenterById(id);
        assertEquals("Solo", r.getName());
        assertNull(r.getEmail());
        assertNull(r.getAddress());
    }

    @Test
    void renterNameWithQuotesAndSemicolonsIsStoredAsText() throws SQLException {
        String name = "Robert'); DROP TABLE Renter;-- Ñ";
        int id = db.insertRenter(new Renter(0, name, "1", "a@b.c", "x'y"));
        assertEquals(name, db.getRenterById(id).getName());
        assertEquals(2, db.getAllRenters().size());
    }

    @Test
    void creatingTheTablesAgainKeepsTheData() throws SQLException {
        db.createTables();
        db.createTables();
        assertEquals(1, db.getAllVehicles().size());
        assertEquals(1, db.getAllRenters().size());
    }

    @Test
    void theChangeStampMovesOnEveryWriteAndStaysStillWithoutOne() throws SQLException {
        String before = db.changeStamp();
        assertEquals(before, db.changeStamp());
        db.insertRenter(new Renter(0, "New", null, null, null));
        String afterInsert = db.changeStamp();
        assertNotEquals(before, afterInsert);
        db.addMileage(vehicleId, 5);
        assertNotEquals(afterInsert, db.changeStamp());
    }

    @Test
    void addMileageAddsAndAUnknownVehicleChangesNothing() throws SQLException {
        db.addMileage(vehicleId, 250);
        db.addMileage(vehicleId, 0);
        db.addMileage(999, 100);
        assertEquals(1250, db.getVehicleById(vehicleId).getMileage());
    }

    @Test
    void oneAccountPerUsernameEvenWithOtherCapitals() throws SQLException {
        Account a = new Account(0, "maria_s", Role.ADMIN, "Maria", "h", "s", "q", "ah", "as", null, TODAY);
        db.insertAccount(a);
        Account same = new Account(0, "MARIA_S", Role.ADMIN, "Maria 2", "h", "s", "q", "ah", "as", null, TODAY);
        assertThrows(SQLException.class, () -> db.insertAccount(same));
        assertEquals("maria_s", db.findAccountByUsername("Maria_S").getUsername());
    }

    @Test
    void anAccountForARenterThatDoesNotExistIsRefused() {
        Account a = new Account(0, "ghost_user", Role.RENTER, "Ghost", "h", "s", "q", "ah", "as", 999, TODAY);
        assertThrows(SQLException.class, () -> db.insertAccount(a));
    }

    @Test
    void signingUpARenterIsAllOrNothing() throws SQLException {
        db.insertAccount(new Account(0, "taken_name", Role.ADMIN, "Owner", "h", "s", "q", "ah", "as", null, TODAY));
        Renter r = new Renter(0, "Half Made", "1", null, null);
        Account clash = new Account(0, "TAKEN_NAME", Role.RENTER, "Half Made", "h", "s", "q", "ah", "as", null, TODAY);
        assertThrows(SQLException.class, () -> db.insertRenterWithAccount(r, clash));
        assertEquals(1, db.getAllRenters().size());
    }

    @Test
    void aLateRentalBlocksTodayButNotNextWeekAndAReturnedOneBlocksNothing() throws SQLException {
        int id = db.insertRental(rental(renterId, vehicleId, -10, -5));
        assertFalse(db.isVehicleFree(vehicleId, TODAY, TODAY, TODAY));
        assertTrue(db.isVehicleFree(vehicleId, TODAY.plusDays(1), TODAY.plusDays(3), TODAY));
        Rental late = db.getRentalById(id);
        late.markReturned(TODAY, 0);
        db.updateRental(late);
        assertTrue(db.isVehicleFree(vehicleId, TODAY, TODAY, TODAY));
    }

    @Test
    void touchingDaysCountAsBusyButNeighbourDaysDoNot() throws SQLException {
        db.insertReservation(reservation(renterId, vehicleId, 5, 7));
        assertFalse(db.isVehicleFree(vehicleId, TODAY.plusDays(7), TODAY.plusDays(9), TODAY));
        assertFalse(db.isVehicleFree(vehicleId, TODAY.plusDays(3), TODAY.plusDays(5), TODAY));
        assertTrue(db.isVehicleFree(vehicleId, TODAY.plusDays(8), TODAY.plusDays(9), TODAY));
        assertTrue(db.isVehicleFree(vehicleId, TODAY.plusDays(3), TODAY.plusDays(4), TODAY));
    }

    @Test
    void busySpansSkipFinishedCancelledAndPastBookings() throws SQLException {
        int past = db.insertReservation(reservation(renterId, vehicleId, -9, -7));
        int cancelled = db.insertReservation(reservation(renterId, vehicleId, 20, 22));
        db.updateReservationStatus(cancelled, ReservationStatus.CANCELLED, "x");
        db.insertReservation(reservation(renterId, vehicleId, 30, 31));
        db.insertRental(new Rental(0, renterId, vehicleId, TODAY.minusDays(4), TODAY.minusDays(3), TODAY.minusDays(3), 0,
                RentalStatus.RETURNED));
        Map<Integer, List<BusySpan>> spans = db.getBusySpans(TODAY);
        assertEquals(1, spans.get(vehicleId).size());
        assertEquals("RESERVED", spans.get(vehicleId).get(0).kind());
        assertNotNull(db.getReservationById(past));
    }

    @Test
    void busySpansAreSortedByStartDay() throws SQLException {
        db.insertReservation(reservation(renterId, vehicleId, 30, 31));
        db.insertRental(rental(renterId, vehicleId, 0, 2));
        db.insertReservation(reservation(renterId, vehicleId, 10, 11));
        List<BusySpan> spans = db.getBusySpans(TODAY).get(vehicleId);
        assertEquals(3, spans.size());
        assertTrue(spans.get(0).start().isBefore(spans.get(1).start()));
        assertTrue(spans.get(1).start().isBefore(spans.get(2).start()));
    }

    @Test
    void convertingAReservationNeedsAValidRentalOrNothingChanges() throws SQLException {
        int id = db.insertReservation(reservation(renterId, vehicleId, 0, 2));
        Reservation res = db.getReservationById(id);
        Rental bad = rental(renterId, 999, 0, 2);
        assertThrows(SQLException.class, () -> db.convertReservationToRental(res, bad));
        assertEquals(ReservationStatus.ACTIVE, db.getReservationById(id).getStatus());
        assertTrue(db.getAllRentals().isEmpty());
    }

    @Test
    void removeVehicleOfAnUnknownIdChangesNothingAndDoesNotCrash() throws SQLException {
        assertEquals(0, db.removeVehicle(999, "n"));
        assertEquals(1, db.getAllVehicles().size());
    }

    @Test
    void theRentalsOfOneRenterAreNotMixedWithAnotherRenter() throws SQLException {
        int other = db.insertRenter(new Renter(0, "Ben", null, null, null));
        db.insertRental(rental(renterId, vehicleId, 0, 1));
        db.insertRental(rental(other, vehicleId, 5, 6));
        assertEquals(1, db.getRentalsByRenter(renterId).size());
        assertEquals(1, db.getRentalsByRenter(other).size());
        assertEquals(0, db.getRentalsByRenter(999).size());
        assertEquals(2, db.getAllRentals().size());
    }

    @Test
    void deletingAnUnknownRentalIsHarmless() throws SQLException {
        db.deleteRental(999);
        assertTrue(db.getAllRentals().isEmpty());
    }

    @Test
    void aVehicleWithoutYearOrServiceDateRoundTrips() throws SQLException {
        int id = db.insertVehicle(VehicleFactory.create(0, "MOTORCYCLE", "Honda", "Click", 0, "MOTO 1", "Red", 500, 0,
                null, "Motorcycle"));
        Vehicle v = db.getVehicleById(id);
        assertEquals(0, v.getYear());
        assertNull(v.getLastPMS());
        assertEquals("MOTORCYCLE", v.getType());
    }

    @Test
    void closingTheDatabaseTwiceDoesNotThrow() throws SQLException {
        Database other = new Database("jdbc:sqlite::memory:");
        other.close();
        other.close();
    }
}
