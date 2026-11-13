package fleetrent.service;

import fleetrent.model.Offer;
import fleetrent.dao.Database;
import fleetrent.model.Motorcycle;
import fleetrent.model.Rental;
import fleetrent.model.RentalStatus;
import fleetrent.model.Renter;
import fleetrent.model.Reservation;
import fleetrent.model.Sedan;
import fleetrent.model.Vehicle;
import fleetrent.model.VehicleFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// The boundary days and the wrong-input cases of booking, rent, reserve, undo and hand over.
class BookingEdgeCaseTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private Database db;
    private BookingService booking;
    private int car;
    private int bike;
    private int ana;
    private int ben;

    @BeforeEach
    void setUp() throws SQLException {
        db = new Database("jdbc:sqlite::memory:");
        db.createTables();
        booking = new BookingService(db, () -> TODAY);
        car = db.insertVehicle(VehicleFactory.create(0, "SEDAN", "Toyota", "Vios", 2022, "ABC 123", "White", 1500, 0,
                null, "Car > Sedan"));
        bike = db.insertVehicle(VehicleFactory.create(0, "MOTORCYCLE", "Honda", "Click", 2023, "MOTO 1", "Red", 600, 0,
                null, "Motorcycle"));
        ana = db.insertRenter(new Renter(0, "Ana Reyes", "09171234567", null, null));
        ben = db.insertRenter(new Renter(0, "Ben Cruz", "09181234567", null, null));
    }

    @AfterEach
    void tearDown() throws SQLException {
        db.close();
    }

    @Test
    void aRentalOfExactlyThirtyDaysIsAllowedAndThirtyOneIsNot() throws SQLException {
        assertNotNull(booking.rentNow(ana, car, TODAY.plusDays(BookingService.MAX_DAYS)));
        assertThrows(IllegalArgumentException.class,
                () -> booking.rentNow(ben, bike, TODAY.plusDays(BookingService.MAX_DAYS + 1)));
    }

    @Test
    void aReservationOfThirtyDaysIsAllowedAndThirtyOneIsNot() throws SQLException {
        LocalDate start = TODAY.plusDays(5);
        assertNotNull(booking.reserve(ana, car, start, start.plusDays(BookingService.MAX_DAYS)));
        assertThrows(IllegalArgumentException.class,
                () -> booking.reserve(ana, bike, start, start.plusDays(BookingService.MAX_DAYS + 1)));
    }

    @Test
    void theLastAllowedStartDayIsOneHundredEightyDaysAhead() throws SQLException {
        LocalDate last = TODAY.plusDays(BookingService.MAX_DAYS_AHEAD);
        assertNotNull(booking.reserve(ana, car, last, last));
        assertThrows(IllegalArgumentException.class, () -> booking.reserve(ana, bike, last.plusDays(1), last.plusDays(1)));
    }

    @Test
    void reservingTodayOrInThePastIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> booking.reserve(ana, car, TODAY, TODAY.plusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> booking.reserve(ana, car, TODAY.minusDays(3), TODAY));
    }

    @Test
    void anEndDateBeforeTheStartIsRefusedEverywhere() {
        assertThrows(IllegalArgumentException.class, () -> booking.reserve(ana, car, TODAY.plusDays(5), TODAY.plusDays(4)));
        assertThrows(IllegalArgumentException.class, () -> booking.rentNow(ana, car, TODAY.minusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> booking.browse(TODAY.plusDays(2), TODAY.plusDays(1)));
    }

    @Test
    void nullDatesAreRefusedWithAnExceptionThatHasAMessage() {
        assertThrows(RuntimeException.class, () -> booking.reserve(ana, car, null, null));
        assertThrows(RuntimeException.class, () -> booking.rentNow(ana, car, null));
        assertThrows(RuntimeException.class, () -> booking.browse(null, null));
    }

    @Test
    void anUnknownVehicleOrRenterIsRefusedAndNothingIsSaved() throws SQLException {
        assertThrows(RuntimeException.class, () -> booking.rentNow(ana, 9999, TODAY.plusDays(1)));
        assertThrows(RuntimeException.class, () -> booking.reserve(ana, 9999, TODAY.plusDays(2), TODAY.plusDays(3)));
        assertThrows(RuntimeException.class, () -> booking.rentNow(9999, car, TODAY.plusDays(1)));
        assertThrows(RuntimeException.class, () -> booking.reserve(9999, car, TODAY.plusDays(2), TODAY.plusDays(3)));
        assertTrue(db.getAllRentals().isEmpty());
        assertTrue(db.getAllReservations().isEmpty());
    }

    @Test
    void undoWithNothingToUndoSaysSoWithoutACrash() throws SQLException {
        assertFalse(booking.canUndo());
        assertNull(booking.undoLast(ana));
    }

    @Test
    void undoCannotTakeBackAnotherRentersBooking() throws SQLException {
        booking.reserve(ana, car, TODAY.plusDays(2), TODAY.plusDays(3));
        assertThrows(RuntimeException.class, () -> booking.undoLast(ben));
        assertEquals(1, db.countActiveReservationsOfVehicle(car));
    }

    @Test
    void undoFreesTheDaysSoSomeoneElseCanBook() throws SQLException {
        booking.reserve(ana, car, TODAY.plusDays(2), TODAY.plusDays(3));
        booking.undoLast(ana);
        assertNotNull(booking.reserve(ben, car, TODAY.plusDays(2), TODAY.plusDays(3)));
    }

    @Test
    void cancellingAReservationTwiceIsRefusedTheSecondTime() throws SQLException {
        Reservation r = booking.reserve(ana, car, TODAY.plusDays(2), TODAY.plusDays(3));
        booking.cancelReservation(ana, r.getId());
        assertThrows(RuntimeException.class, () -> booking.cancelReservation(ana, r.getId()));
    }

    @Test
    void cancellingAnUnknownReservationIsRefused() {
        assertThrows(RuntimeException.class, () -> booking.cancelReservation(ana, 9999));
        assertThrows(RuntimeException.class, () -> booking.cancelReservationByOwner(9999));
        assertThrows(RuntimeException.class, () -> booking.handOver(9999));
    }

    @Test
    void aHandedOverReservationCannotBeCancelledOrHandedOverAgain() throws SQLException {
        Reservation r = booking.reserve(ana, car, TODAY.plusDays(1), TODAY.plusDays(2));
        BookingService tomorrow = new BookingService(db, () -> TODAY.plusDays(1));
        tomorrow.handOver(r.getId());
        assertThrows(RuntimeException.class, () -> tomorrow.handOver(r.getId()));
        assertThrows(RuntimeException.class, () -> tomorrow.cancelReservationByOwner(r.getId()));
        assertThrows(RuntimeException.class, () -> tomorrow.cancelReservation(ana, r.getId()));
        assertEquals(1, db.getAllRentals().size());
    }

    @Test
    void handOverOnTheLastDayIsStillAllowed() throws SQLException {
        Reservation r = booking.reserve(ana, car, TODAY.plusDays(1), TODAY.plusDays(3));
        Rental rental = new BookingService(db, () -> TODAY.plusDays(3)).handOver(r.getId());
        assertEquals(RentalStatus.ONGOING, rental.getStatus());
    }

    @Test
    void aCancelledReservationNoLongerBlocksTheVehicle() throws SQLException {
        Reservation r = booking.reserve(ana, car, TODAY.plusDays(2), TODAY.plusDays(4));
        booking.cancelReservation(ana, r.getId());
        assertNotNull(booking.rentNow(ben, car, TODAY.plusDays(5)));
    }

    @Test
    void twoReservationsOfTheSameVehicleOnNeighbourDaysAreBothKept() throws SQLException {
        booking.reserve(ana, car, TODAY.plusDays(2), TODAY.plusDays(3));
        booking.reserve(ben, car, TODAY.plusDays(4), TODAY.plusDays(5));
        assertEquals(2, db.countActiveReservationsOfVehicle(car));
    }

    @Test
    void oneDayReservationOnTheSameDayAsAnotherIsRefused() throws SQLException {
        booking.reserve(ana, car, TODAY.plusDays(2), TODAY.plusDays(2));
        assertThrows(IllegalStateException.class, () -> booking.reserve(ben, car, TODAY.plusDays(2), TODAY.plusDays(2)));
    }

    @Test
    void aReservationInsideALongerOneIsRefused() throws SQLException {
        booking.reserve(ana, car, TODAY.plusDays(2), TODAY.plusDays(10));
        assertThrows(IllegalStateException.class, () -> booking.reserve(ben, car, TODAY.plusDays(4), TODAY.plusDays(5)));
        assertThrows(IllegalStateException.class, () -> booking.reserve(ben, car, TODAY.plusDays(1), TODAY.plusDays(11)));
    }

    @Test
    void rentalsOfAndReservationsOfShowOnlyThatRentersBookings() throws SQLException {
        booking.rentNow(ana, car, TODAY.plusDays(1));
        booking.reserve(ben, bike, TODAY.plusDays(3), TODAY.plusDays(4));
        assertEquals(1, booking.rentalsOf(ana).size());
        assertEquals(0, booking.rentalsOf(ben).size());
        assertEquals(0, booking.reservationsOf(ana).size());
        assertEquals(1, booking.reservationsOf(ben).size());
        assertTrue(booking.rentalsOf(9999).isEmpty());
    }

    @Test
    void browseWithAOneDaySpanWorks() throws SQLException {
        assertEquals(2, booking.browse(TODAY, TODAY).size());
    }

    @Test
    void browseOfAnEmptyFleetIsAnEmptyList() throws SQLException {
        Database empty = new Database("jdbc:sqlite::memory:");
        empty.createTables();
        assertTrue(new BookingService(empty, () -> TODAY).browse(TODAY, TODAY.plusDays(2)).isEmpty());
        empty.close();
    }

    @Test
    void estimateOfASameDayRentalIsOneDailyRate() {
        Vehicle v = VehicleFactory.create(1, "SEDAN", "Toyota", "Vios", 2022, "ABC 123", "White", 1500, 0, null, "Car");
        assertEquals(1500.0, BookingService.estimate(v, TODAY, TODAY), 0.001);
        assertEquals(1500.0, BookingService.estimate(v, TODAY, TODAY.plusDays(1)), 0.001);
        assertEquals(4500.0, BookingService.estimate(v, TODAY, TODAY.plusDays(3)), 0.001);
    }

    @Test
    void theRentersOwnRentalDoesNotShowWhoHasIt() throws SQLException {
        booking.rentNow(ana, car, TODAY.plusDays(2));
        Offer o = booking.browse(TODAY, TODAY.plusDays(1)).stream()
                .filter(x -> x.vehicle().getId() == car).findFirst().orElseThrow();
        assertFalse(o.toString().contains("Ana"));
        assertFalse(o.busy().toString().contains("Ana"));
    }
}
