package fleetrent.controller;

import fleetrent.dao.Database;
import fleetrent.model.Account;
import fleetrent.model.MaintenanceType;
import fleetrent.model.Renter;
import fleetrent.model.Vehicle;
import fleetrent.model.VehicleFactory;
import fleetrent.service.AuthService;
import fleetrent.service.CarImageClient;
import fleetrent.service.CarImageStore;
import fleetrent.service.FleetService;
import fleetrent.service.PhotoFinder;
import fleetrent.service.VpicClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Only the owner (manager) may remove or restore vehicles. A renter may not, and a wrong password
// does not sign anyone in.
class OwnerOnlyTest {
    private static final String Q = AuthService.SECURITY_QUESTIONS.get(0);
    @TempDir
    Path folder;
    private Database db;
    private AuthController auth;
    private VehicleController vehicles;
    private int vehicleId;

    @BeforeEach
    void setUp() throws Exception {
        db = new Database("jdbc:sqlite::memory:");
        db.createTables();
        Session session = new Session();
        auth = new AuthController(new AuthService(db), session);
        CarImageStore store = new CarImageStore(folder);
        vehicles = new VehicleController(db, new FleetService(db), new VpicClient(), store,
                new PhotoFinder(new CarImageClient(), store), session);
        Vehicle v = VehicleFactory.create(0, "SEDAN", "Toyota", "Vios", 2020, "ABC 123", "White", 1000, 0,
                null, "Car > Sedan");
        vehicleId = db.insertVehicle(v);
        auth.signUpOwner("owner01", "Passw0rdX", "Maria Santos", Q, "Rex", AuthService.OWNER_CODE);
        auth.signUpRenter("renter01", "Passw0rdX", new Renter(0, "Ana Cruz", "09171234567", "ana@mail.com",
                "12 Osmena Blvd, Cebu"), Q, "Rex");
        auth.signOut();
    }

    @AfterEach
    void tearDown() throws Exception {
        db.close();
    }

    @Test
    void renterCannotRemoveAVehicle() throws Exception {
        auth.login("renter01", "Passw0rdX");
        RuleException e = assertThrows(RuleException.class, () -> vehicles.remove(vehicleId));
        assertTrue(e.getMessage().contains("Only the owner"));
        assertEquals(1, vehicles.vehicles().size());
    }

    @Test
    void renterCannotRestoreAVehicle() throws Exception {
        auth.login("owner01", "Passw0rdX");
        vehicles.remove(vehicleId);
        auth.signOut();
        auth.login("renter01", "Passw0rdX");
        assertThrows(RuleException.class, () -> vehicles.restore(vehicleId));
        assertEquals(1, vehicles.removedVehicles().size());
    }

    @Test
    void nobodySignedInCannotRemove() {
        assertThrows(RuleException.class, () -> vehicles.remove(vehicleId));
    }

    @Test
    void ownerCanRemoveAndRestore() throws Exception {
        Account owner = auth.login("owner01", "Passw0rdX");
        assertTrue(auth.session().isOwner());
        assertEquals("Maria Santos", auth.session().current().getDisplayName());
        assertEquals(owner.getId(), auth.session().current().getId());
        assertEquals(0, vehicles.remove(vehicleId));
        assertTrue(vehicles.vehicles().isEmpty());
        vehicles.restore(vehicleId);
        assertEquals(1, vehicles.vehicles().size());
    }

    @Test
    void signingOutTakesTheOwnerRightsAway() throws Exception {
        auth.login("owner01", "Passw0rdX");
        auth.signOut();
        assertFalse(auth.session().isOwner());
        assertNull(auth.session().current());
        assertThrows(RuleException.class, () -> vehicles.remove(vehicleId));
    }

    @Test
    void wrongPasswordIsRefusedAndNobodyIsSignedIn() {
        assertThrows(RuleException.class, () -> auth.login("owner01", "WrongPass1"));
        assertNull(auth.session().current());
        assertThrows(RuleException.class, () -> vehicles.remove(vehicleId));
    }

    @Test
    void unknownUserIsRefused() {
        assertThrows(RuleException.class, () -> auth.login("nobody", "Passw0rdX"));
        assertNull(auth.session().current());
    }

    @Test
    void renterCannotRecordMaintenanceButOwnerCan() throws Exception {
        auth.login("renter01", "Passw0rdX");
        assertThrows(RuleException.class, () -> vehicles.logMaintenance(vehicleId, LocalDate.now(),
                MaintenanceType.PMS, 100, null));
        auth.login("owner01", "Passw0rdX");
        vehicles.logMaintenance(vehicleId, LocalDate.now(), MaintenanceType.PMS, 100, "oil");
        assertEquals(1, vehicles.maintenanceOf(vehicleId).size());
        assertEquals(LocalDate.now(), db.getVehicleById(vehicleId).getLastPMS());
    }

    @Test
    void maintenanceInTheFutureOrOnAMissingVehicleIsRefused() throws Exception {
        auth.login("owner01", "Passw0rdX");
        assertThrows(RuleException.class, () -> vehicles.logMaintenance(vehicleId, LocalDate.now().plusDays(2),
                MaintenanceType.PMS, 100, null));
        assertThrows(RuleException.class, () -> vehicles.logMaintenance(9999, LocalDate.now(),
                MaintenanceType.PMS, 100, null));
    }
}
