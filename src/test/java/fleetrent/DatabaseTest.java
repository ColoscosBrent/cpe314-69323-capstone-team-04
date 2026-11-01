package fleetrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
}
