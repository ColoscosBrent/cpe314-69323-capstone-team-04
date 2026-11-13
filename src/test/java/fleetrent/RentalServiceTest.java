package fleetrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.SQLException;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RentalServiceTest {
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
    void startRentalSavesAnOngoingRental() throws SQLException {
        Rental r = service.startRental(renterA, vehicleId, d(1), d(4));
        assertNotNull(r);
        assertEquals(RentalStatus.ONGOING, db.getRentalById(r.getId()).getStatus());
    }

    @Test
    void returnRentalComputesCostAndAddsMileage() throws SQLException {
        Rental r = service.startRental(renterA, vehicleId, d(1), d(4));
        // 3 days x 1000 = 3000, free miles 300, drove 400 -> 100 extra x 0.20 = 20
        double cost = service.returnRental(r.getId(), d(4), 400);
        assertEquals(3020.0, cost, 0.001);
        assertEquals(RentalStatus.RETURNED, db.getRentalById(r.getId()).getStatus());
        assertEquals(400, db.getVehicleById(vehicleId).getMileage());
    }

    @Test
    void earlyReturnIsBilledForTheDaysUsed() throws SQLException {
        Rental r = service.startRental(renterA, vehicleId, d(1), d(10));
        assertEquals(2000.0, service.returnRental(r.getId(), d(3), 0), 0.001);
    }

    @Test
    void sameDayReturnIsBilledOneDay() throws SQLException {
        Rental r = service.startRental(renterA, vehicleId, d(1), d(2));
        assertEquals(1000.0, service.returnRental(r.getId(), d(1), 0), 0.001);
    }

    @Test
    void secondRenterJoinsTheWaitingListForARentedVehicle() throws SQLException {
        service.startRental(renterA, vehicleId, d(1), d(4));
        assertNull(service.startRental(renterB, vehicleId, d(2), d(5)));
        assertEquals(1, service.waitingCount(vehicleId));
        assertEquals("Ben", service.serveNextWaiting(vehicleId).getName());
        assertEquals(0, service.waitingCount(vehicleId));
        assertNull(service.serveNextWaiting(vehicleId));
    }

    @Test
    void badInputIsRejected() throws SQLException {
        assertThrows(IllegalArgumentException.class, () -> service.startRental(renterA, vehicleId, d(5), d(4)));
        assertThrows(IllegalArgumentException.class, () -> service.startRental(renterA, 999, d(1), d(2)));
        assertThrows(IllegalArgumentException.class, () -> service.startRental(999, vehicleId, d(1), d(2)));
        Rental r = service.startRental(renterA, vehicleId, d(5), d(8));
        assertThrows(IllegalArgumentException.class, () -> service.returnRental(r.getId(), d(6), -1));
        assertThrows(IllegalArgumentException.class, () -> service.returnRental(r.getId(), d(4), 10));
        assertThrows(IllegalArgumentException.class, () -> service.returnRental(999, d(6), 10));
    }

    @Test
    void returningTwiceIsRejected() throws SQLException {
        Rental r = service.startRental(renterA, vehicleId, d(1), d(4));
        service.returnRental(r.getId(), d(4), 10);
        assertThrows(IllegalStateException.class, () -> service.returnRental(r.getId(), d(4), 10));
    }

    @Test
    void undoRemovesTheLastCheckoutOnly() throws SQLException {
        assertNull(service.undoLastCheckout());
        Rental r = service.startRental(renterA, vehicleId, d(1), d(4));
        assertEquals(r.getId(), service.undoLastCheckout().getId());
        assertNull(db.getRentalById(r.getId()));
        assertNull(service.undoLastCheckout());
    }

    @Test
    void undoAfterReturnIsRejected() throws SQLException {
        Rental r = service.startRental(renterA, vehicleId, d(1), d(4));
        service.returnRental(r.getId(), d(4), 10);
        assertThrows(IllegalStateException.class, () -> service.undoLastCheckout());
    }

    // Caloy: more waiting list cases.
    @Test
    void waitingRentersAreServedInTheOrderTheyJoined() throws SQLException {
        int renterC = db.insertRenter(new Renter(0, "Cara", null, null, null));
        service.startRental(renterA, vehicleId, d(1), d(4));
        assertNull(service.startRental(renterB, vehicleId, d(2), d(5)));
        assertNull(service.startRental(renterC, vehicleId, d(3), d(6)));
        assertEquals(2, service.waitingCount(vehicleId));
        assertEquals("Ben", service.serveNextWaiting(vehicleId).getName());
        assertEquals("Cara", service.serveNextWaiting(vehicleId).getName());
    }

    @Test
    void waitingListBelongsToOneVehicleOnly() throws SQLException {
        int otherVehicle = db.insertVehicle(VehicleFactory.create(0, "SUV", "Ford", "Everest", 2021, "XYZ 789",
                "Black", 2500, 0, null, "Car > SUV"));
        service.startRental(renterA, vehicleId, d(1), d(4));
        assertNull(service.startRental(renterB, vehicleId, d(2), d(5)));
        assertEquals(1, service.waitingCount(vehicleId));
        assertEquals(0, service.waitingCount(otherVehicle));
        assertNotNull(service.startRental(renterB, otherVehicle, d(2), d(5)));
    }
}
