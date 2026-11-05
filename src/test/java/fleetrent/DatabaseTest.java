package fleetrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DatabaseTest {
    private Database db;

    @BeforeEach
    void openFreshDatabase() throws SQLException {
        db = new Database("jdbc:sqlite::memory:");
        db.createTables();
    }

    @AfterEach
    void closeDatabase() throws SQLException {
        db.close();
    }

    @Test
    void insertedRenterCanBeSelected() throws SQLException {
        db.insertRenter(new Renter(0, "Ana Reyes", "09171234567", "ana@x.com", "Cebu City"));
        List<Renter> renters = db.getAllRenters();
        assertEquals(1, renters.size());
        assertEquals("Ana Reyes", renters.get(0).getName());
    }

    @Test
    void insertedVehicleComesBackAsTheRightSubclass() throws SQLException {
        Vehicle suv = new Suv(0, "Ford", "Everest", 2021, "XYZ789", "Black", 2500.0, 12000,
                LocalDate.of(2026, 8, 15), "Car > SUV");
        db.insertVehicle(suv);
        Vehicle loaded = db.getAllVehicles().get(0);
        assertInstanceOf(Suv.class, loaded);
        assertEquals("XYZ789", loaded.getPlateNumber());
        assertEquals(LocalDate.of(2026, 8, 15), loaded.getLastPMS());
    }

    @Test
    void rentalIsSavedAndFoundByRenter() throws SQLException {
        int renterId = db.insertRenter(new Renter(0, "Ben", "0918", "ben@x.com", "Mandaue"));
        int vehicleId = db.insertVehicle(new Sedan(0, "Toyota", "Vios", 2020, "ABC123", "White",
                1500.0, 3000, null, "Car > Sedan"));
        db.insertRental(Rental.startNew(renterId, vehicleId, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3)));
        List<Rental> rentals = db.getRentalsByRenter(renterId);
        assertEquals(1, rentals.size());
        assertEquals(RentalStatus.ONGOING, rentals.get(0).getStatus());
        assertTrue(rentals.get(0).getReturnedOn() == null);
    }

    @Test
    void rentalWithUnknownRenterIsRejected() {
        Rental bad = Rental.startNew(999, 999, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3));
        assertThrows(SQLException.class, () -> db.insertRental(bad));
    }

    @Test
    void unknownYearIsStoredAsNullAndReadBackAsZero() throws SQLException {
        Vehicle bike = new Motorcycle(0, "Honda", "Rebel 500", 0, "MC 001", "Black", 900.0, 0, null, "Motorcycle");
        db.insertVehicle(bike);
        Vehicle loaded = db.getAllVehicles().get(0);
        assertEquals(0, loaded.getYear());
        assertEquals(null, loaded.getLastPMS());
    }

    @Test
    void renterKeepsEmailAndAddress() throws SQLException {
        db.insertRenter(new Renter(0, "Ben Cruz", "09170000000", "ben@x.com", "Mandaue City"));
        db.insertRenter(new Renter(0, "Cara Lim", "09171111111", null, null));
        List<Renter> renters = db.getAllRenters();
        assertEquals("ben@x.com", renters.get(0).getEmail());
        assertEquals("Mandaue City", renters.get(0).getAddress());
        assertEquals(null, renters.get(1).getEmail());
    }

    // Dexter: edge cases for lookups and the batch insert.
    @Test
    void unknownVehicleIdGivesNull() throws SQLException {
        assertNull(db.getVehicleById(999));
    }

    @Test
    void plateSearchIgnoresCaseAndSpacesAndHandlesNull() throws SQLException {
        db.insertVehicle(new Suv(0, "Ford", "Everest", 2021, "XYZ789", "Black", 2500.0, 12000,
                LocalDate.of(2026, 8, 15), "Car > SUV"));
        assertNotNull(db.findVehicleByPlate("  xyz789 "));
        assertNull(db.findVehicleByPlate("NOPE000"));
        assertNull(db.findVehicleByPlate(null));
    }

    @Test
    void emptyRentalBatchSavesNothing() throws SQLException {
        assertEquals(0, db.insertRentalBatch(List.of()));
        assertTrue(db.getAllRentals().isEmpty());
    }
}
