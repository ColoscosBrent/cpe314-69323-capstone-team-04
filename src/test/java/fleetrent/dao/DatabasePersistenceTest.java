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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// The waiting list, the check-out history, payments and maintenance are saved in the database, so
// they are still there after the app is closed and opened again.
class DatabasePersistenceTest {
    private static final LocalDate DAY = LocalDate.of(2026, 3, 10);

    @TempDir
    Path folder;

    private String url() {
        return "jdbc:sqlite:" + folder.resolve("fleet.db");
    }

    private static int addVehicle(Database db, String plate) throws SQLException {
        return db.insertVehicle(VehicleFactory.create(0, "SEDAN", "Toyota", "Vios", 2020, plate,
                "White", 1000, 0, null, "Car > Sedan"));
    }

    @Test
    void waitingListSurvivesARestart() throws SQLException {
        int vehicleId;
        try (Database db = new Database(url())) {
            db.createTables();
            int ana = db.insertRenter(new Renter(0, "Ana", null, null, null));
            int ben = db.insertRenter(new Renter(0, "Ben", null, null, null));
            int cara = db.insertRenter(new Renter(0, "Cara", null, null, null));
            vehicleId = addVehicle(db, "ABC 123");
            RentalService service = new RentalService(db);
            service.startRental(ana, vehicleId, DAY, DAY.plusDays(2));
            assertNull(service.startRental(ben, vehicleId, DAY, DAY.plusDays(2)));
            assertNull(service.startRental(cara, vehicleId, DAY, DAY.plusDays(2)));
        }
        try (Database db = new Database(url())) {
            db.createTables();
            RentalService service = new RentalService(db);
            assertEquals(2, service.waitingCount(vehicleId));
            assertEquals("Ben", service.serveNextWaiting(vehicleId).getName());
            assertEquals("Cara", service.serveNextWaiting(vehicleId).getName());
            assertNull(service.serveNextWaiting(vehicleId));
        }
    }

    @Test
    void lastCheckoutCanStillBeUndoneAfterARestart() throws SQLException {
        int rentalId;
        try (Database db = new Database(url())) {
            db.createTables();
            int ana = db.insertRenter(new Renter(0, "Ana", null, null, null));
            int v = addVehicle(db, "ABC 123");
            rentalId = new RentalService(db).startRental(ana, v, DAY, DAY.plusDays(2)).getId();
        }
        try (Database db = new Database(url())) {
            db.createTables();
            Rental undone = new RentalService(db).undoLastCheckout();
            assertEquals(rentalId, undone.getId());
            assertNull(db.getRentalById(rentalId));
        }
    }

    @Test
    void returningARentalSavesAPaymentWithTheLateFee() throws SQLException {
        try (Database db = new Database(url())) {
            db.createTables();
            int ana = db.insertRenter(new Renter(0, "Ana", null, null, null));
            int v = addVehicle(db, "ABC 123");
            RentalService service = new RentalService(db);
            Rental r = service.startRental(ana, v, DAY, DAY.plusDays(2));
            double total = service.returnRental(r.getId(), DAY.plusDays(4), 0);
            Payment p = db.getPaymentByRental(r.getId());
            assertNotNull(p);
            assertEquals(4000, p.getAmount(), 0.001);
            assertEquals(200, p.getLateFee(), 0.001);
            assertEquals(total, p.getTotal(), 0.001);
            assertEquals(DAY.plusDays(4), p.getPaidOn());
            assertEquals(RentalStatus.RETURNED, db.getRentalById(r.getId()).getStatus());
        }
    }

    @Test
    void onTimeReturnHasNoLateFee() throws SQLException {
        try (Database db = new Database(url())) {
            db.createTables();
            int ana = db.insertRenter(new Renter(0, "Ana", null, null, null));
            int v = addVehicle(db, "ABC 123");
            RentalService service = new RentalService(db);
            Rental r = service.startRental(ana, v, DAY, DAY.plusDays(2));
            service.returnRental(r.getId(), DAY.plusDays(2), 0);
            assertEquals(0, db.getPaymentByRental(r.getId()).getLateFee(), 0.001);
            assertEquals(1, db.getAllPayments().size());
        }
    }

    @Test
    void aSecondPaymentForTheSameRentalIsRefused() throws SQLException {
        try (Database db = new Database(url())) {
            db.createTables();
            int ana = db.insertRenter(new Renter(0, "Ana", null, null, null));
            int v = addVehicle(db, "ABC 123");
            Rental r = new RentalService(db).startRental(ana, v, DAY, DAY.plusDays(1));
            db.insertPayment(Payment.forRental(r.getId(), 10, 0, DAY));
            assertThrows(SQLException.class, () -> db.insertPayment(Payment.forRental(r.getId(),
                    10, 0, DAY)));
        }
    }

    @Test
    void pmsMaintenanceMovesTheLastPmsDateForward() throws SQLException {
        try (Database db = new Database(url())) {
            db.createTables();
            int v = addVehicle(db, "ABC 123");
            db.insertMaintenance(new Maintenance(0, v, DAY, MaintenanceType.PMS, 2500, "oil"));
            assertEquals(DAY, db.getVehicleById(v).getLastPMS());
            db.insertMaintenance(new Maintenance(0, v, DAY.minusDays(30), MaintenanceType.PMS, 100,
                    null));
            assertEquals(DAY, db.getVehicleById(v).getLastPMS());
        }
    }

    @Test
    void otherMaintenanceDoesNotChangeTheLastPmsDate() throws SQLException {
        try (Database db = new Database(url())) {
            db.createTables();
            int v = addVehicle(db, "ABC 123");
            db.insertMaintenance(new Maintenance(0, v, DAY, MaintenanceType.REPAIR, 900, "brake"));
            assertNull(db.getVehicleById(v).getLastPMS());
            List<Maintenance> list = db.getMaintenanceByVehicle(v);
            assertEquals(1, list.size());
            assertEquals(MaintenanceType.REPAIR, list.get(0).getType());
        }
    }

    @Test
    void maintenanceIsListedNewestFirstAndKeptAfterARestart() throws SQLException {
        int v;
        try (Database db = new Database(url())) {
            db.createTables();
            v = addVehicle(db, "ABC 123");
            db.insertMaintenance(new Maintenance(0, v, DAY, MaintenanceType.TIRES, 1, null));
            db.insertMaintenance(new Maintenance(0, v, DAY.plusDays(5), MaintenanceType.OTHER, 2,
                    null));
        }
        try (Database db = new Database(url())) {
            db.createTables();
            List<Maintenance> list = db.getMaintenanceByVehicle(v);
            assertEquals(2, list.size());
            assertEquals(MaintenanceType.OTHER, list.get(0).getType());
        }
    }

    @Test
    void removingAVehicleClearsItsWaitingLine() throws SQLException {
        try (Database db = new Database(url())) {
            db.createTables();
            int ana = db.insertRenter(new Renter(0, "Ana", null, null, null));
            int ben = db.insertRenter(new Renter(0, "Ben", null, null, null));
            int v = addVehicle(db, "ABC 123");
            RentalService service = new RentalService(db);
            Rental r = service.startRental(ana, v, DAY, DAY.plusDays(1));
            service.startRental(ben, v, DAY, DAY.plusDays(1));
            service.returnRental(r.getId(), DAY.plusDays(1), 0);
            db.removeVehicle(v, "test");
            assertEquals(0, service.waitingCount(v));
        }
    }
}
