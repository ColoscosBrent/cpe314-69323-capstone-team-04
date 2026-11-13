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
import java.sql.SQLException;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// The owner (manager) is the only account that may remove or restore vehicles. A renter, and a
// window where nobody is signed in, are refused by the controller, whatever the screen shows.
class OwnerOnlyTest {
    private static final String QUESTION = AuthService.SECURITY_QUESTIONS.get(0);

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
        Vehicle v = VehicleFactory.create(0, "SEDAN", "Toyota", "Vios", 2020, "ABC 123", "White",
                1000, 0, null, "Car > Sedan");
        vehicleId = db.insertVehicle(v);
        auth.signUpOwner("owner01", "Passw0rdX", "Santos, Maria Lopez", "+63 9171234567",
                QUESTION, "Rex");
        auth.signOut();
        auth.signUpRenter("renter01", "Passw0rdX", new Renter(0, "Cruz, Ana Lim", "+63 9171234567",
                "ana@example.com", "12 Rizal St, Cebu City, Philippines"),
                QUESTION, "Rex");
        auth.signOut();
    }

    @AfterEach
    void tearDown() throws SQLException {
        db.close();
    }

    @Test
    void ownerCanRemoveAndRestoreAVehicle() throws Exception {
        Account owner = auth.login("owner01", "Passw0rdX");
        assertTrue(auth.session().isOwner());
        assertEquals("Santos, Maria Lopez", owner.getDisplayName());
        vehicles.remove(vehicleId);
        assertEquals(0, vehicles.vehicles().size());
        vehicles.restore(vehicleId);
        assertEquals(1, vehicles.vehicles().size());
    }

    @Test
    void renterCannotRemoveAVehicle() throws Exception {
        auth.login("renter01", "Passw0rdX");
        assertFalse(auth.session().isOwner());
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
        assertEquals(0, vehicles.vehicles().size());
    }

    @Test
    void renterCannotRecordMaintenance() throws Exception {
        auth.login("renter01", "Passw0rdX");
        assertThrows(RuleException.class, () -> vehicles.logMaintenance(vehicleId,
                LocalDate.now(), MaintenanceType.PMS, 100, null));
        assertEquals(0, vehicles.maintenanceOf(vehicleId).size());
    }

    @Test
    void signedOutWindowCannotRemoveAVehicle() {
        assertNull(auth.session().current());
        assertThrows(RuleException.class, () -> vehicles.remove(vehicleId));
    }

    @Test
    void signingOutTakesTheOwnerRightsAway() throws Exception {
        auth.login("owner01", "Passw0rdX");
        auth.signOut();
        assertFalse(auth.session().isOwner());
        assertThrows(RuleException.class, () -> vehicles.remove(vehicleId));
    }

    @Test
    void wrongPasswordIsRefusedAndNobodyIsSignedIn() {
        assertThrows(RuleException.class, () -> auth.login("owner01", "WrongPass9"));
        assertNull(auth.session().current());
        assertThrows(RuleException.class, () -> vehicles.remove(vehicleId));
    }

    @Test
    void unknownUserIsRefused() {
        assertThrows(RuleException.class, () -> auth.login("nobody", "Passw0rdX"));
        assertNull(auth.session().current());
    }

    @Test
    void ownerMaintenanceUpdatesTheLastPmsDate() throws Exception {
        auth.login("owner01", "Passw0rdX");
        LocalDate day = LocalDate.now().minusDays(1);
        vehicles.logMaintenance(vehicleId, day, MaintenanceType.PMS, 2500, "oil change");
        assertEquals(day, vehicles.vehicles().get(0).getLastPMS());
        assertEquals(1, vehicles.maintenanceOf(vehicleId).size());
    }

    @Test
    void maintenanceInTheFutureOrOnAMissingVehicleIsRefused() throws Exception {
        auth.login("owner01", "Passw0rdX");
        assertThrows(RuleException.class, () -> vehicles.logMaintenance(vehicleId,
                LocalDate.now().plusDays(3), MaintenanceType.PMS, 1, null));
        assertThrows(RuleException.class, () -> vehicles.logMaintenance(999,
                LocalDate.now(), MaintenanceType.PMS, 1, null));
        assertThrows(RuleException.class, () -> vehicles.logMaintenance(vehicleId,
                LocalDate.now(), MaintenanceType.PMS, -1, null));
    }
}
