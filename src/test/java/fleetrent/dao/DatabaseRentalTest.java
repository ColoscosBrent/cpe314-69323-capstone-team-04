package fleetrent.dao;

import fleetrent.model.Rental;
import fleetrent.model.RentalStatus;
import fleetrent.model.Renter;
import fleetrent.model.Sedan;
import fleetrent.model.VehicleFactory;
import fleetrent.service.RentalService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Database-backed tests for rentals, including the overlap case: a vehicle that is already out
// cannot get a second rental for dates that overlap the first one.
class DatabaseRentalTest {
    private Database db;
    private RentalService service;
    private int renterA;
    private int renterB;
    private int vehicleId;

    @BeforeEach
    void setUp() throws SQLException {
        db = new Database("jdbc:sqlite::memory:");
        db.createTables();
        service = new RentalService(db);
        renterA = db.insertRenter(new Renter(0, "Ana", null, null, null));
        renterB = db.insertRenter(new Renter(0, "Ben", null, null, null));
        vehicleId = db.insertVehicle(VehicleFactory.create(0, "SEDAN", "Toyota", "Vios", 2020, "ABC 123",
                "White", 1000, 0, null, "Car > Sedan"));
    }

    @AfterEach
    void tearDown() throws SQLException {
        db.close();
    }

    private static LocalDate d(int day) {
        return LocalDate.of(2026, 3, day);
    }

    @Test
    void vehicleIsFreeAgainAfterItsRentalIsReturned() throws SQLException {
        assertFalse(db.hasOngoingRental(vehicleId));
        Rental rental = Rental.startNew(renterA, vehicleId, d(1), d(4));
        rental.setId(db.insertRental(rental));
        assertTrue(db.hasOngoingRental(vehicleId));
        rental.markReturned(d(4), 50);
        db.updateRental(rental);
        assertFalse(db.hasOngoingRental(vehicleId));
    }

    @Test
    void overlappingRentalIsNotSavedForAVehicleThatIsOut() throws SQLException {
        assertNotNull(service.startRental(renterA, vehicleId, d(1), d(5)));
        assertNull(service.startRental(renterB, vehicleId, d(3), d(7)));
        assertEquals(1, db.getAllRentals().size());
        assertTrue(db.getRentalsByRenter(renterB).isEmpty());
    }

    @Test
    void returnedRentalLeavesRoomForALaterRentalOnTheSameVehicle() throws SQLException {
        Rental first = service.startRental(renterA, vehicleId, d(1), d(3));
        service.returnRental(first.getId(), d(3), 20);
        assertNotNull(service.startRental(renterB, vehicleId, d(5), d(8)));
        List<Rental> all = db.getAllRentals();
        assertEquals(2, all.size());
        assertEquals(RentalStatus.RETURNED, all.get(0).getStatus());
        assertEquals(RentalStatus.ONGOING, all.get(1).getStatus());
    }
}
