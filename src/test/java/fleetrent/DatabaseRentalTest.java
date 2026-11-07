package fleetrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Rental tests on a real (in-memory) SQLite database, including rentals that overlap.
class DatabaseRentalTest {
    private Database db;
    private int renterA;
    private int renterB;
    private int vehicle;

    @BeforeEach
    void openFreshDatabase() throws SQLException {
        db = new Database("jdbc:sqlite::memory:");
        db.createTables();
        renterA = db.insertRenter(new Renter(0, "Ana", "0917", "ana@x.com", "Cebu"));
        renterB = db.insertRenter(new Renter(0, "Ben", "0918", "ben@x.com", "Mandaue"));
        vehicle = db.insertVehicle(new Sedan(0, "Toyota", "Vios", 2020, "ABC123", "White",
                1500.0, 3000, null, "Car > Sedan"));
    }

    @AfterEach
    void closeDatabase() throws SQLException {
        db.close();
    }

    private static LocalDate march(int day) {
        return LocalDate.of(2026, 3, day);
    }

    @Test
    void overlappingRentalOfTheSameVehicleIsRejectedAndNotSaved() throws SQLException {
        db.insertRental(Rental.startNew(renterA, vehicle, march(1), march(5)));
        Rental overlapping = Rental.startNew(renterB, vehicle, march(3), march(7));
        assertThrows(IllegalStateException.class, () -> db.insertRental(overlapping));
        assertEquals(0, db.getRentalsByRenter(renterB).size());
    }

    @Test
    void rentalThatStartsOnTheLastDayOfAnotherOneOverlaps() throws SQLException {
        db.insertRental(Rental.startNew(renterA, vehicle, march(1), march(5)));
        assertFalse(db.isVehicleFree(vehicle, march(5), march(8)));
    }

    @Test
    void rentalRightAfterAnotherOneIsAllowed() throws SQLException {
        db.insertRental(Rental.startNew(renterA, vehicle, march(1), march(5)));
        db.insertRental(Rental.startNew(renterB, vehicle, march(6), march(8)));
        assertEquals(1, db.getRentalsByRenter(renterB).size());
    }

    @Test
    void sameDatesOnAnotherVehicleAreAllowed() throws SQLException {
        int other = db.insertVehicle(new Suv(0, "Ford", "Everest", 2021, "XYZ789", "Black", 2500.0,
                12000, null, "Car > SUV"));
        db.insertRental(Rental.startNew(renterA, vehicle, march(1), march(5)));
        db.insertRental(Rental.startNew(renterB, other, march(1), march(5)));
        assertEquals(1, db.getRentalsByRenter(renterB).size());
    }

    @Test
    void vehicleReturnedEarlyIsFreeAfterTheReturnDay() throws SQLException {
        Rental first = Rental.startNew(renterA, vehicle, march(1), march(10));
        db.insertRental(first);
        assertFalse(db.isVehicleFree(vehicle, march(4), march(6)));
        // Simulate the early return by replacing the saved row with a returned one.
        db.deleteRental(first.getId());
        Rental returned = new Rental(0, renterA, vehicle, march(1), march(10), march(3), 120, RentalStatus.RETURNED);
        db.insertRental(returned);
        assertTrue(db.isVehicleFree(vehicle, march(4), march(6)));
    }

    @Test
    void endDateBeforeStartDateIsRejected() {
        Rental backwards = Rental.startNew(renterA, vehicle, march(5), march(1));
        assertThrows(IllegalArgumentException.class, () -> db.insertRental(backwards));
    }

    @Test
    void deletedRentalFreesTheVehicle() throws SQLException {
        Rental rental = Rental.startNew(renterA, vehicle, march(1), march(5));
        db.insertRental(rental);
        db.deleteRental(rental.getId());
        assertTrue(db.isVehicleFree(vehicle, march(1), march(5)));
        assertEquals(0, db.getRentalsByRenter(renterA).size());
    }
}
