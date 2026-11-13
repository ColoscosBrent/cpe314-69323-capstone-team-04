package fleetrent.dao;

import fleetrent.model.Sedan;
import fleetrent.model.Vehicle;
import fleetrent.model.VehicleFactory;
import fleetrent.service.ErrorMessages;
import fleetrent.controller.Background;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// The database itself refuses two vehicles in the fleet with the same plate, and the app uses one
// shared pool of background threads.
class DuplicatePlateTest {
    private Database db;

    @BeforeEach
    void open() throws SQLException {
        db = new Database("jdbc:sqlite::memory:");
        db.createTables();
    }

    @AfterEach
    void close() throws SQLException {
        db.close();
    }

    private static Vehicle sedan(String plate) {
        return VehicleFactory.create(0, "SEDAN", "Toyota", "Vios", 2022, plate, "White", 1500, 0, null, "Car > Sedan > Compact Sedan");
    }

    @Test
    void secondVehicleWithTheSamePlateIsRefusedByTheDatabase() throws SQLException {
        db.insertVehicle(sedan("ABC 123"));
        DuplicatePlateException e = assertThrows(DuplicatePlateException.class,
                () -> db.insertVehicle(sedan("ABC 123")));
        assertTrue(e.getMessage().contains("ABC 123"));
        assertEquals(1, db.getAllVehicles().size());
    }

    @Test
    void plateRuleIgnoresUpperAndLowerCase() throws SQLException {
        db.insertVehicle(sedan("abc 123"));
        assertThrows(DuplicatePlateException.class, () -> db.insertVehicle(sedan("ABC 123")));
    }

    @Test
    void editingToAnotherVehiclesPlateIsRefused() throws SQLException {
        db.insertVehicle(sedan("AAA 111"));
        Vehicle second = sedan("BBB 222");
        db.insertVehicle(second);
        Vehicle edited = sedan("AAA 111");
        edited.setId(second.getId());
        assertThrows(DuplicatePlateException.class, () -> db.updateVehicle(edited));
    }

    @Test
    void aRemovedVehicleFreesItsPlateButCannotComeBackWhileTheNewOneUsesIt() throws SQLException {
        Vehicle first = sedan("ABC 123");
        db.insertVehicle(first);
        db.setVehicleActive(first.getId(), false);
        assertDoesNotThrow(() -> db.insertVehicle(sedan("ABC 123")));
        assertThrows(DuplicatePlateException.class, () -> db.setVehicleActive(first.getId(), true));
    }

    @Test
    void forDatabaseShowsTheDuplicatePlateMessageAsItIs() {
        DuplicatePlateException e = new DuplicatePlateException("XYZ 999", new SQLException("x"));
        assertEquals(e.getMessage(), ErrorMessages.forDatabase("saving the vehicle", e));
    }

    @Test
    void manyBackgroundJobsUseOnlyTheFewThreadsOfTheSharedPool() throws InterruptedException {
        Set<String> names = ConcurrentHashMap.newKeySet();
        CountDownLatch done = new CountDownLatch(40);
        for (int i = 0; i < 40; i++) {
            Background.run(() -> {
                names.add(Thread.currentThread().getName());
                done.countDown();
            });
        }
        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertTrue(names.size() <= 4, "threads used: " + names);
        assertTrue(names.stream().allMatch(n -> n.startsWith("fleetrent-worker-")));
    }
}
