package fleetrent.dao;

import fleetrent.model.Maintenance;
import fleetrent.model.MaintenanceType;
import fleetrent.model.Payment;
import fleetrent.model.Rental;
import fleetrent.model.RentalStatus;
import fleetrent.model.Renter;
import fleetrent.model.VehicleFactory;
import fleetrent.service.RentalService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Payment, Maintenance, the saved waiting list and the saved check-out log.
class DatabaseNewTablesTest {
    private static final LocalDate START = LocalDate.of(2026, 3, 1);
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

    @Test
    void returnSavesAPaymentWithoutLateFeeWhenOnTime() throws SQLException {
        Rental r = service.startRental(renterA, vehicleId, START, START.plusDays(2));
        double paid = service.returnRental(r.getId(), START.plusDays(2), 0);
        Payment p = db.getPaymentByRental(r.getId());
        assertNotNull(p);
        assertEquals(2000, p.getAmount(), 0.001);
        assertEquals(0, p.getLateFee(), 0.001);
        assertEquals(START.plusDays(2), p.getPaidOn());
        assertEquals(paid, p.getTotal(), 0.001);
    }

    @Test
    void lateReturnAddsALateFee() throws SQLException {
        Rental r = service.startRental(renterA, vehicleId, START, START.plusDays(2));
        double paid = service.returnRental(r.getId(), START.plusDays(4), 0);
        Payment p = db.getPaymentByRental(r.getId());
        assertEquals(4000, p.getAmount(), 0.001);
        assertEquals(200, p.getLateFee(), 0.001);
        assertEquals(4200, paid, 0.001);
        assertEquals(1, db.getAllPayments().size());
    }

    @Test
    void unreturnedRentalHasNoPayment() throws SQLException {
        Rental r = service.startRental(renterA, vehicleId, START, START.plusDays(2));
        assertNull(db.getPaymentByRental(r.getId()));
        assertEquals(RentalStatus.ONGOING, db.getRentalById(r.getId()).getStatus());
    }

    @Test
    void pmsRecordMovesLastPmsForwardOnly() throws SQLException {
        db.insertMaintenance(new Maintenance(0, vehicleId, START, MaintenanceType.PMS, 2500, null));
        assertEquals(START, db.getVehicleById(vehicleId).getLastPMS());
        db.insertMaintenance(new Maintenance(0, vehicleId, START.minusDays(30), MaintenanceType.PMS, 1, null));
        assertEquals(START, db.getVehicleById(vehicleId).getLastPMS());
        db.insertMaintenance(new Maintenance(0, vehicleId, START.plusDays(9), MaintenanceType.PMS, 1, null));
        assertEquals(START.plusDays(9), db.getVehicleById(vehicleId).getLastPMS());
    }

    @Test
    void repairDoesNotChangeLastPms() throws SQLException {
        db.insertMaintenance(new Maintenance(0, vehicleId, START, MaintenanceType.REPAIR, 900, "brakes"));
        assertNull(db.getVehicleById(vehicleId).getLastPMS());
        List<Maintenance> list = db.getMaintenanceByVehicle(vehicleId);
        assertEquals(1, list.size());
        assertEquals("brakes", list.get(0).getNote());
    }

    @Test
    void maintenanceListIsNewestFirst() throws SQLException {
        db.insertMaintenance(new Maintenance(0, vehicleId, START, MaintenanceType.TIRES, 1, null));
        db.insertMaintenance(new Maintenance(0, vehicleId, START.plusDays(5), MaintenanceType.OTHER, 1, null));
        assertEquals(MaintenanceType.OTHER, db.getMaintenanceByVehicle(vehicleId).get(0).getType());
    }

    @Test
    void waitingListIsKeptInTheDatabaseAndServedInOrder() throws SQLException {
        Rental first = service.startRental(renterA, vehicleId, START, START.plusDays(2));
        assertNotNull(first);
        assertNull(service.startRental(renterB, vehicleId, START, START.plusDays(2)));
        assertEquals(List.of(renterB), db.getWaitingRenterIds(vehicleId));
        // A new service object (like a restart) still sees the line.
        RentalService restarted = new RentalService(db);
        assertEquals(1, restarted.waitingCount(vehicleId));
        assertEquals("Ben", restarted.serveNextWaiting(vehicleId).getName());
        assertEquals(0, restarted.waitingCount(vehicleId));
    }

    @Test
    void checkoutLogSurvivesARestartAndUndoWorks() throws SQLException {
        Rental r = service.startRental(renterA, vehicleId, START, START.plusDays(2));
        RentalService restarted = new RentalService(db);
        assertEquals(1, restarted.undoableCount());
        assertEquals(r.getId(), restarted.undoLastCheckout().getId());
        assertEquals(0, restarted.undoableCount());
        assertNull(db.getRentalById(r.getId()));
    }

    @Test
    void removingAVehicleClearsItsWaitingLine() throws SQLException {
        service.startRental(renterA, vehicleId, START, START.plusDays(2));
        service.startRental(renterB, vehicleId, START, START.plusDays(2));
        assertTrue(db.getWaitingRenterIds(vehicleId).size() == 1);
        Rental open = db.getAllRentals().get(0);
        service.returnRental(open.getId(), START.plusDays(1), 0);
        db.removeVehicle(vehicleId, "removed");
        assertTrue(db.getWaitingRenterIds(vehicleId).isEmpty());
    }
}
