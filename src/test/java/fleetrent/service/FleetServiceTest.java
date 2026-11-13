package fleetrent.service;

import fleetrent.dao.Database;
import fleetrent.model.Renter;
import fleetrent.model.Reservation;
import fleetrent.model.ReservationStatus;
import fleetrent.model.Sedan;
import fleetrent.model.Vehicle;
import fleetrent.model.VehicleFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FleetServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private Database db;
    private FleetService fleet;
    private int vehicleId;
    private int renterId;

    @BeforeEach
    void setUp() throws SQLException {
        db = new Database("jdbc:sqlite::memory:");
        db.createTables();
        fleet = new FleetService(db);
        Vehicle v = VehicleFactory.create(0, "SEDAN", "Toyota", "Vios", 2022, "ABC 123", "White",
                1500, 1000,
                null, "Car > Sedan");
        vehicleId = db.insertVehicle(v);
        renterId = db.insertRenter(new Renter(0, "Ana Reyes", "09171234567", null, null));
    }

    @AfterEach
    void tearDown() throws SQLException {
        db.close();
    }

    @Test
    void removedVehicleLeavesTheFleetListButKeepsItsRow() throws SQLException {
        fleet.removeVehicle(vehicleId);
        assertTrue(db.getAllVehicles().isEmpty());
        assertEquals(1, db.getRemovedVehicles().size());
        assertEquals("Vios", db.getVehicleById(vehicleId).getModel());
        assertFalse(db.isVehicleActive(vehicleId));
    }

    @Test
    void removedVehicleCannotBeRented() throws SQLException {
        fleet.removeVehicle(vehicleId);
        RentalService service = new RentalService(db);
        assertThrows(IllegalArgumentException.class,
                () -> service.startRental(renterId, vehicleId, TODAY, TODAY.plusDays(2)));
        BookingService booking = new BookingService(db, () -> TODAY);
        assertThrows(IllegalStateException.class, () -> booking.rentNow(renterId, vehicleId,
                TODAY.plusDays(2)));
    }

    @Test
    void cannotRemoveAVehicleThatIsRentedOutNow() throws SQLException {
        new RentalService(db).startRental(renterId, vehicleId, TODAY, TODAY.plusDays(2));
        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                fleet.removeVehicle(vehicleId));
        assertTrue(e.getMessage().contains("rented out"));
        assertEquals(1, db.getAllVehicles().size());
    }

    @Test
    void removingCancelsTheActiveReservations() throws SQLException {
        BookingService booking = new BookingService(db, () -> TODAY);
        Reservation r = booking.reserve(renterId, vehicleId, TODAY.plusDays(5), TODAY.plusDays(7));
        assertEquals(1, fleet.removeVehicle(vehicleId));
        Reservation after = db.getReservationById(r.getId());
        assertEquals(ReservationStatus.CANCELLED, after.getStatus());
        assertTrue(after.getNote().contains("removed"));
    }

    @Test
    void removingTwiceOrAnUnknownVehicleIsRefused() throws SQLException {
        fleet.removeVehicle(vehicleId);
        assertThrows(IllegalStateException.class, () -> fleet.removeVehicle(vehicleId));
        assertThrows(IllegalArgumentException.class, () -> fleet.removeVehicle(999));
    }

    @Test
    void restoreBringsTheVehicleBack() throws SQLException {
        fleet.removeVehicle(vehicleId);
        fleet.restoreVehicle(vehicleId);
        assertEquals(1, db.getAllVehicles().size());
        assertTrue(db.getRemovedVehicles().isEmpty());
        assertThrows(IllegalStateException.class, () -> fleet.restoreVehicle(vehicleId));
    }

    @Test
    void restoreIsRefusedWhenAnotherVehicleUsesThePlate() throws SQLException {
        fleet.removeVehicle(vehicleId);
        db.insertVehicle(VehicleFactory.create(0, "SUV", "Ford", "Everest", 2021, "ABC 123",
                "Black", 2500, 0,
                null, "Car > SUV"));
        assertThrows(IllegalStateException.class, () -> fleet.restoreVehicle(vehicleId));
    }

    @Test
    void aPlateOfARemovedVehicleCanBeUsedAgain() throws SQLException {
        fleet.removeVehicle(vehicleId);
        assertNull(db.findVehicleByPlate("ABC 123"));
    }

    @Test
    void editChangesTheDetailsAndKeepsTheId() throws SQLException {
        Vehicle edited = VehicleFactory.create(vehicleId, "SEDAN", "Toyota", "Vios", 2023,
                "XYZ 999", "Black",
                1800, 1200, LocalDate.of(2026, 10, 1), "Car > Sedan > Compact Sedan");
        fleet.updateVehicle(edited);
        Vehicle saved = db.getVehicleById(vehicleId);
        assertEquals(1800, saved.getDailyRate(), 0.001);
        assertEquals("XYZ 999", saved.getPlateNumber());
        assertEquals("Black", saved.getColor());
        assertEquals(2023, saved.getYear());
        assertEquals(LocalDate.of(2026, 10, 1), saved.getLastPMS());
        assertEquals("Car > Sedan > Compact Sedan", saved.getCategoryPath());
        assertEquals(1, db.getAllVehicles().size());
    }

    @Test
    void editRefusesAPlateOfAnotherVehicleButAllowsItsOwnPlate() throws SQLException {
        db.insertVehicle(VehicleFactory.create(0, "SUV", "Ford", "Everest", 2021, "QQQ 111",
                "Black", 2500, 0,
                null, "Car > SUV"));
        Vehicle clash = VehicleFactory.create(vehicleId, "SEDAN", "Toyota", "Vios", 2022,
                "qqq 111", "White", 1500, 0,
                null, "Car > Sedan");
        assertThrows(IllegalStateException.class, () -> fleet.updateVehicle(clash));
        Vehicle same = VehicleFactory.create(vehicleId, "SEDAN", "Toyota", "Vios", 2022, "abc 123",
                "Red", 1600, 0,
                null, "Car > Sedan");
        fleet.updateVehicle(same);
        assertEquals("Red", db.getVehicleById(vehicleId).getColor());
    }

    @Test
    void editCannotChangeTheType() {
        Vehicle other = VehicleFactory.create(vehicleId, "SUV", "Toyota", "Vios", 2022, "ABC 123",
                "White", 1500, 0,
                null, "Car > SUV");
        assertThrows(IllegalArgumentException.class, () -> fleet.updateVehicle(other));
    }

    @Test
    void oldDatabaseWithoutTheActiveColumnIsMigrated() throws Exception {
        try (Database old = new Database("jdbc:sqlite::memory:")) {
            // This connection is separate, so build the old table by hand and then run createTables
            // on it.
            java.lang.reflect.Field f = Database.class.getDeclaredField("connection");
            f.setAccessible(true);
            java.sql.Connection c = (java.sql.Connection) f.get(old);
            try (java.sql.Statement st = c.createStatement()) {
                st.execute(
                        "CREATE TABLE Vehicle (id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT "
                        + "NOT NULL, "
                        + "make TEXT NOT NULL, model TEXT NOT NULL, year INTEGER, plateNumber "
                                + "TEXT, color TEXT, "
                        + "dailyRate REAL NOT NULL, mileage INTEGER NOT NULL DEFAULT 0, lastPMS "
                                + "TEXT, categoryPath TEXT)");
                st.execute(
                        "INSERT INTO Vehicle (type, make, model, year, plateNumber, color, "
                        + "dailyRate) "
                        + "VALUES ('SEDAN', 'Honda', 'City', 2020, 'OLD 111', 'Blue', 1400)");
            }
            old.createTables();
            assertEquals(1, old.getAllVehicles().size());
            old.createTables();
            assertEquals(1, old.getAllVehicles().size());
        }
    }
}
