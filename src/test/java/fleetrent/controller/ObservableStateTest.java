package fleetrent.controller;

import fleetrent.FxToolkit;
import fleetrent.dao.Database;
import fleetrent.model.Vehicle;
import fleetrent.model.VehicleFactory;
import fleetrent.service.AuthService;
import fleetrent.service.FleetService;
import fleetrent.service.RentalService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javafx.application.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

// The observable state of the controllers: the list, the selected row, the status and the busy
// flag, and the background Tasks the controllers run. A Task reports on the JavaFX thread, so
// these tests start JavaFX. On a computer with no display they are skipped, not failed.
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class ObservableStateTest {
    private static final String QUESTION = AuthService.SECURITY_QUESTIONS.get(0);
    private static boolean fxReady;

    private Database db;
    private Session session;
    private AuthController auth;
    private VehicleController vehicles;

    // A controller with nothing but the shared part, so the test can run any work through it.
    private static final class Probe extends ObservableController<String> {
        void run(String slot, Callable<String> work, List<String> answers, List<String> problems) {
            runTask(slot, work, answers::add, problems == null ? null : problems::add);
        }
    }

    @BeforeAll
    static void startJavaFx() {
        fxReady = FxToolkit.start();
    }

    @BeforeEach
    void setUp() throws Exception {
        Assumptions.assumeTrue(fxReady,
                "JavaFX cannot start here (no display), so these tests are skipped");
        db = new Database("jdbc:sqlite::memory:");
        db.createTables();
        session = new Session();
        auth = new AuthController(new AuthService(db), session);
        vehicles = new VehicleController(db, new FleetService(db), null, null, null, session);
        db.insertVehicle(VehicleFactory.create(0, "SEDAN", "Toyota", "Vios", 2020, "ABC 123",
                "White", 1000, 0, null, "Car > Sedan"));
        auth.startSession(auth.signUpOwner("owner01", "Passw0rdX", "Santos, Maria Lopez",
                "+63 9171234567", QUESTION, "Rex"));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (db != null) {
            db.close();
        }
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(20, TimeUnit.SECONDS);
    }

    private static void until(Supplier<Boolean> condition, String what) throws Exception {
        long end = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < end) {
            if (onFx(condition::get)) {
                return;
            }
            Thread.sleep(30);
        }
        throw new AssertionError("Waited 10 seconds, but this never happened: " + what);
    }

    @Test
    void refreshFillsTheObservableListAndTheBusyFlagGoesOffAgain() throws Exception {
        assertTrue(vehicles.items().isEmpty());
        List<Boolean> busySeen = new ArrayList<>();
        onFx(() -> {
            vehicles.busyProperty().addListener((obs, was, now) -> busySeen.add(now));
            vehicles.refresh();
            return null;
        });
        until(() -> vehicles.items().size() == 1, "the fleet is in the list");
        until(() -> !vehicles.busyProperty().get(), "the controller is not busy anymore");
        assertEquals("ABC 123", onFx(() -> vehicles.items().get(0).getPlateNumber()));
        assertEquals(List.of(true, false), busySeen);
        assertEquals("", onFx(() -> vehicles.statusProperty().get()));
        // A new vehicle shows up after the next refresh, without the screen touching the list.
        db.insertVehicle(VehicleFactory.create(0, "SUV", "Ford", "Everest", 2022, "XYZ 789",
                "Black", 2800, 0, null, "Car > SUV"));
        onFx(() -> {
            vehicles.refresh();
            return null;
        });
        until(() -> vehicles.items().size() == 2, "the second vehicle is in the list");
    }

    @Test
    void theSelectedRowIsAPropertyTheScreenCanSet() throws Exception {
        onFx(() -> {
            vehicles.refresh();
            return null;
        });
        until(() -> vehicles.items().size() == 1, "the fleet is in the list");
        assertNull(onFx(() -> vehicles.selectedProperty().get()));
        Vehicle first = onFx(() -> vehicles.items().get(0));
        onFx(() -> {
            vehicles.selectedProperty().set(first);
            return null;
        });
        assertEquals(first, onFx(() -> vehicles.selectedProperty().get()));
    }

    @Test
    void aLoadProblemIsPutInTheStatusPropertyInPlainWords() throws Exception {
        RentalController rentals = new RentalController(db, new RentalService(db), session);
        auth.signOut();
        List<String> shown = new ArrayList<>();
        onFx(() -> {
            rentals.refresh(data -> shown.add("data"));
            return null;
        });
        until(() -> !rentals.statusProperty().get().isEmpty(), "the problem is in the status");
        assertTrue(onFx(() -> rentals.statusProperty().get()).contains("owner"));
        assertTrue(shown.isEmpty());
        assertTrue(onFx(() -> rentals.items().isEmpty()));
        assertFalse(onFx(() -> rentals.busyProperty().get()));
    }

    @Test
    void onlyTheNewestAnswerOfASlotIsShown() throws Exception {
        Probe probe = new Probe();
        CountDownLatch release = new CountDownLatch(1);
        List<String> answers = new ArrayList<>();
        onFx(() -> {
            // The first question is slow; the second one of the same slot is asked before it ends.
            probe.run("makes", () -> {
                release.await(10, TimeUnit.SECONDS);
                return "old";
            }, answers, null);
            probe.run("makes", () -> "new", answers, null);
            return null;
        });
        until(() -> answers.contains("new"), "the newest answer arrived");
        release.countDown();
        until(() -> !probe.busyProperty().get(), "both tasks ended");
        assertEquals(List.of("new"), answers);
    }

    @Test
    void aProblemGoesToTheCallerWhenTheCallerAsksForIt() throws Exception {
        Probe probe = new Probe();
        List<String> answers = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        onFx(() -> {
            probe.run(null, () -> {
                throw new ControllerException("NHTSA could not be reached.", null);
            }, answers, problems);
            return null;
        });
        until(() -> !problems.isEmpty(), "the problem arrived");
        assertEquals(List.of("NHTSA could not be reached."), problems);
        assertTrue(answers.isEmpty());
        // The status property is only for problems nobody else takes.
        assertEquals("", onFx(() -> probe.statusProperty().get()));
    }

    @Test
    void aRunnerThatWasShutDownStartsNoNewTask() throws Exception {
        TaskRunner runner = new TaskRunner();
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<String> ended = new ArrayList<>();
        runner.run(() -> {
            inside.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            ended.add("first");
        });
        assertTrue(inside.await(5, TimeUnit.SECONDS));
        // shutDown waits for the running job, but not longer than it was told to.
        long start = System.currentTimeMillis();
        runner.shutDown(200);
        assertTrue(System.currentTimeMillis() - start < 5_000);
        release.countDown();
        runner.shutDown(5_000);
        assertEquals(List.of("first"), ended);
        List<String> late = new ArrayList<>();
        List<String> cancelled = new ArrayList<>();
        onFx(() -> runner.start(() -> "late", late::add, e -> late.add("failed"),
                () -> cancelled.add("cancelled")));
        until(() -> !cancelled.isEmpty(), "the late task was cancelled");
        assertTrue(late.isEmpty());
    }
}
