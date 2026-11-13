package fleetrent;

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

// Cases that could go wrong when the owner removes, restores or edits a vehicle, and when the
// vehicle classes are created with strange input.
class FleetEdgeCaseTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private Database db;
    private FleetService fleet;
    private BookingService booking;
    private int carId;
    private int suvId;
    private int renterId;

    @BeforeEach
    void setUp() throws SQLException {
        db = new Database("jdbc:sqlite::memory:");
        db.createTables();
        fleet = new FleetService(db);
        booking = new BookingService(db, () -> TODAY);
        carId = db.insertVehicle(VehicleFactory.create(0, "SEDAN", "Toyota", "Vios", 2022, "ABC 123", "White",
                1500, 1000, null, "Car > Sedan"));
        suvId = db.insertVehicle(VehicleFactory.create(0, "SUV", "Ford", "Everest", 2021, "SUV 777", "Black",
                2500, 5000, null, "Car > SUV"));
        renterId = db.insertRenter(new Renter(0, "Ana Reyes", "09171234567", null, null));
    }

    @AfterEach
    void tearDown() throws SQLException {
        db.close();
    }

    private Vehicle edited(String make, String model, int year, String plate, double rate, int mileage) {
        return VehicleFactory.create(carId, "SEDAN", make, model, year, plate, "White", rate, mileage, null,
                "Car > Sedan");
    }

    @Test
    void editRefusesAnEmptyMakeOrModel() {
        assertThrows(IllegalArgumentException.class, () -> fleet.updateVehicle(edited("", "Vios", 2022, "ABC 123", 1500, 0)));
        assertThrows(IllegalArgumentException.class, () -> fleet.updateVehicle(edited("   ", "Vios", 2022, "ABC 123", 1500, 0)));
        assertThrows(IllegalArgumentException.class, () -> fleet.updateVehicle(edited("Toyota", null, 2022, "ABC 123", 1500, 0)));
    }

    @Test
    void editRefusesAnEmptyPlate() {
        assertThrows(IllegalArgumentException.class, () -> fleet.updateVehicle(edited("Toyota", "Vios", 2022, "", 1500, 0)));
        assertThrows(IllegalArgumentException.class, () -> fleet.updateVehicle(edited("Toyota", "Vios", 2022, null, 1500, 0)));
    }

    @Test
    void editRefusesARateOfZeroOrLessOrNotANumber() {
        assertThrows(IllegalArgumentException.class, () -> fleet.updateVehicle(edited("Toyota", "Vios", 2022, "ABC 123", 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> fleet.updateVehicle(edited("Toyota", "Vios", 2022, "ABC 123", -5, 0)));
        assertThrows(IllegalArgumentException.class, () -> fleet.updateVehicle(edited("Toyota", "Vios", 2022, "ABC 123", Double.NaN, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> fleet.updateVehicle(edited("Toyota", "Vios", 2022, "ABC 123", Double.POSITIVE_INFINITY, 0)));
    }

    @Test
    void editRefusesANegativeMileageOrYear() {
        assertThrows(IllegalArgumentException.class, () -> fleet.updateVehicle(edited("Toyota", "Vios", 2022, "ABC 123", 1500, -1)));
        assertThrows(IllegalArgumentException.class, () -> fleet.updateVehicle(edited("Toyota", "Vios", -2022, "ABC 123", 1500, 0)));
    }

    @Test
    void aRefusedEditChangesNothingInTheDatabase() throws SQLException {
        assertThrows(IllegalArgumentException.class, () -> fleet.updateVehicle(edited("Toyota", "Vios", 2022, "ABC 123", 0, 0)));
        Vehicle same = db.getVehicleById(carId);
        assertEquals(1500, same.getDailyRate(), 0.001);
        assertEquals(1000, same.getMileage());
    }

    @Test
    void editWithYearZeroMeansNotSpecifiedAndIsAllowed() throws SQLException {
        fleet.updateVehicle(edited("Toyota", "Vios", 0, "ABC 123", 1500, 1000));
        assertEquals(0, db.getVehicleById(carId).getYear());
    }

    @Test
    void editToThePlateOfAnotherVehicleIsRefusedEvenWithOtherCapitalsAndSpaces() {
        assertThrows(IllegalStateException.class, () -> fleet.updateVehicle(edited("Toyota", "Vios", 2022, "suv 777", 1500, 0)));
        assertThrows(IllegalStateException.class, () -> fleet.updateVehicle(edited("Toyota", "Vios", 2022, "  SUV 777 ", 1500, 0)));
    }

    @Test
    void editToThePlateOfARemovedVehicleIsAllowed() throws SQLException {
        fleet.removeVehicle(suvId);
        fleet.updateVehicle(edited("Toyota", "Vios", 2022, "SUV 777", 1500, 1000));
        assertEquals("SUV 777", db.getVehicleById(carId).getPlateNumber());
    }

    @Test
    void editOfAnUnknownVehicleIsRefused() {
        Vehicle ghost = VehicleFactory.create(999, "SEDAN", "Toyota", "Vios", 2022, "GHO 999", "White", 1500, 0, null,
                "Car > Sedan");
        assertThrows(IllegalArgumentException.class, () -> fleet.updateVehicle(ghost));
    }

    @Test
    void editKeepsTheRentalAndReservationHistoryOfTheVehicle() throws SQLException {
        booking.rentNow(renterId, carId, TODAY.plusDays(1));
        booking.reserve(renterId, carId, TODAY.plusDays(10), TODAY.plusDays(12));
        fleet.updateVehicle(edited("Toyota", "Vios", 2023, "NEW 111", 1700, 1000));
        assertEquals(1, db.getAllRentals().size());
        assertEquals(1, db.getAllReservations().size());
        assertEquals(1700, db.getVehicleById(carId).getDailyRate(), 0.001);
    }

    @Test
    void theNewDailyRateIsUsedByTheNextBookingPrice() throws SQLException {
        fleet.updateVehicle(edited("Toyota", "Vios", 2022, "ABC 123", 2000, 1000));
        BookingService.Offer offer = booking.browse(TODAY, TODAY.plusDays(1)).stream()
                .filter(o -> o.vehicle().getId() == carId).findFirst().orElseThrow();
        assertEquals(2000, offer.vehicle().getDailyRate(), 0.001);
    }

    @Test
    void removingOneVehicleDoesNotTouchTheOtherOnesReservations() throws SQLException {
        Reservation other = booking.reserve(renterId, suvId, TODAY.plusDays(3), TODAY.plusDays(4));
        booking.reserve(renterId, carId, TODAY.plusDays(3), TODAY.plusDays(4));
        assertEquals(1, fleet.removeVehicle(carId));
        assertEquals(ReservationStatus.ACTIVE, db.getReservationById(other.getId()).getStatus());
    }

    @Test
    void aRemovedVehicleKeepsItsReturnedRentalsInTheHistory() throws SQLException {
        RentalService service = new RentalService(db);
        Rental r = service.startRental(renterId, carId, TODAY, TODAY.plusDays(1));
        service.returnRental(r.getId(), TODAY.plusDays(1), 50);
        fleet.removeVehicle(carId);
        assertEquals(1, db.getAllRentals().size());
        assertEquals(RentalStatus.RETURNED, db.getAllRentals().get(0).getStatus());
        assertNotNull(db.getVehicleById(carId));
    }

    @Test
    void aRemovedVehicleIsHiddenFromTheRenterBrowseList() throws SQLException {
        fleet.removeVehicle(carId);
        assertTrue(booking.browse(TODAY, TODAY.plusDays(1)).stream().noneMatch(o -> o.vehicle().getId() == carId));
        assertEquals(1, booking.browse(TODAY, TODAY.plusDays(1)).size());
    }

    @Test
    void removeThenRestoreThenRemoveAgainWorksEveryTime() throws SQLException {
        for (int i = 0; i < 3; i++) {
            fleet.removeVehicle(carId);
            assertFalse(db.isVehicleActive(carId));
            fleet.restoreVehicle(carId);
            assertTrue(db.isVehicleActive(carId));
        }
    }

    @Test
    void restoreOfAnUnknownVehicleIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> fleet.restoreVehicle(999));
    }

    @Test
    void aVehicleCanBeRemovedAfterItsRentalIsReturned() throws SQLException {
        RentalService service = new RentalService(db);
        Rental r = service.startRental(renterId, carId, TODAY, TODAY.plusDays(1));
        assertThrows(IllegalStateException.class, () -> fleet.removeVehicle(carId));
        service.returnRental(r.getId(), TODAY, 10);
        assertEquals(0, fleet.removeVehicle(carId));
    }

    @Test
    void theFactoryRefusesAnUnknownOrMissingType() {
        assertThrows(IllegalArgumentException.class,
                () -> VehicleFactory.create(0, "TRUCK", "A", "B", 2020, "X 1", "Red", 100, 0, null, "Car"));
        assertThrows(IllegalArgumentException.class,
                () -> VehicleFactory.create(0, null, "A", "B", 2020, "X 1", "Red", 100, 0, null, "Car"));
        assertThrows(IllegalArgumentException.class,
                () -> VehicleFactory.create(0, "sedan", "A", "B", 2020, "X 1", "Red", 100, 0, null, "Car"));
        assertThrows(IllegalArgumentException.class, () -> VehicleFactory.defaultCategoryPath(null));
        assertThrows(IllegalArgumentException.class, () -> VehicleFactory.defaultCategoryPath("TRUCK"));
    }

    @Test
    void everyTypeHasADefaultCategoryAndFeeRule() {
        for (String type : new String[]{"SEDAN", "SUV", "MOTORCYCLE"}) {
            Vehicle v = VehicleFactory.create(0, type, "A", "B", 2020, "X 1", "Red", 100, 0, null,
                    VehicleFactory.defaultCategoryPath(type));
            assertEquals(type, v.getType());
            assertTrue(v.getFreeMilesPerDay() > 0);
            assertTrue(v.getFeePerExtraMile() > 0);
            assertFalse(v.feeSummary().isBlank());
            assertFalse(v.displayName().isBlank());
        }
    }

    @Test
    void strangeTextInVehicleFieldsIsStoredAsTextAndReadBackTheSame() throws SQLException {
        String make = "O'Brien; DROP TABLE Vehicle;--";
        String model = "Ñandú \"Ünï\" 日本語";
        int id = db.insertVehicle(VehicleFactory.create(0, "SUV", make, model, 2020, "Ñ-1 '2'", "Red", 100, 0, null,
                "Car > SUV"));
        Vehicle back = db.getVehicleById(id);
        assertEquals(make, back.getMake());
        assertEquals(model, back.getModel());
        assertEquals("Ñ-1 '2'", back.getPlateNumber());
        assertEquals(3, db.getAllVehicles().size());
        assertNull(db.findVehicleByPlate("nothing"));
    }
}
