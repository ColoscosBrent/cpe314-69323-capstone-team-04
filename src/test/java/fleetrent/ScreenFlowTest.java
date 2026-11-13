package fleetrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

// Clicks through the real screens (login, owner, renter) the way a person would. It uses a temporary
// database file and a fake NHTSA server, so it never touches fleetrent.db and needs no internet.
// If the computer has no display for JavaFX (for example a server), every test here is skipped, not failed.
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class ScreenFlowTest {
    private static boolean fxReady;
    private static HttpServer server;
    private Path file;
    private int ana;

    @BeforeAll
    static void startJavaFx() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", ex -> {
                String path = ex.getRequestURI().toString();
                String body = path.contains("GetMakesForVehicleType")
                        ? "{\"Results\":[{\"MakeName\":\"HONDA\"},{\"MakeName\":\"YAMAHA\"},{\"MakeName\":\"DUCATI\"}]}"
                        : "{\"Results\":[{\"Make_Name\":\"HONDA\",\"Model_ID\":1,\"Model_Name\":\"CB500F\"},"
                        + "{\"Make_Name\":\"HONDA\",\"Model_ID\":2,\"Model_Name\":\"CB1000  Hornet\"},"
                        + "{\"Make_Name\":\"HONDA\",\"Model_ID\":3,\"Model_Name\":\"Rebel 500\"}]}";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, bytes.length);
                ex.getResponseBody().write(bytes);
                ex.close();
            });
            server.start();
            System.setProperty("fleetrent.vpicUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/api");
            CountDownLatch started = new CountDownLatch(1);
            try {
                Platform.startup(started::countDown);
            } catch (IllegalStateException alreadyRunning) {
                started.countDown();
            }
            fxReady = started.await(15, TimeUnit.SECONDS);
            Platform.setImplicitExit(false);
        } catch (Throwable noDisplay) {
            fxReady = false;
        }
    }

    @AfterAll
    static void stopServer() {
        System.clearProperty("fleetrent.vpicUrl");
        if (server != null) {
            server.stop(0);
        }
    }

    @BeforeEach
    void openApp() throws Exception {
        Assumptions.assumeTrue(fxReady, "JavaFX cannot start here (no display), so the screen tests are skipped");
        file = Files.createTempFile("fleetrent-screen", ".db");
        String url = "jdbc:sqlite:" + file.toAbsolutePath();
        try (Database db = new Database(url)) {
            db.createTables();
            for (Vehicle v : SampleData.demoVehicles()) {
                db.insertVehicle(v);
            }
            AuthService auth = new AuthService(db);
            auth.signUpOwner("owner01", "Passw0rd1", "Maria Santos", AuthService.SECURITY_QUESTIONS.get(0), "Rex",
                    AuthService.OWNER_CODE);
            auth.signUpRenter("ana_reyes", "Passw0rd1", new Renter(0, "Ana Reyes", "09171234567", "ana@example.com",
                    "12 Rizal St, Cebu"), AuthService.SECURITY_QUESTIONS.get(0), "Rex");
            ana = db.findAccountByUsername("ana_reyes").getRenterId();
        }
        System.setProperty("fleetrent.dbUrl", url);
        onFx(() -> {
            new App().start(new Stage());
            return null;
        });
        until(() -> button("Log in") != null, "the login screen shows");
    }

    @AfterEach
    void closeApp() throws Exception {
        if (!fxReady) {
            return;
        }
        onFx(() -> {
            AppWindow.closeAll();
            for (Window w : new ArrayList<>(Window.getWindows())) {
                w.hide();
            }
            return null;
        });
        System.clearProperty("fleetrent.dbUrl");
        try {
            Files.deleteIfExists(file);
        } catch (Exception ignored) {
            // a temp file that cannot be deleted right now is not a test failure
        }
    }

    // ---------- helpers ----------

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
            Thread.sleep(60);
        }
        throw new AssertionError("Waited 10 seconds, but this never happened: " + what);
    }

    private static Stage mainStage() {
        for (Window w : Window.getWindows()) {
            if (w instanceof Stage s && s.isShowing() && s.getTitle() != null && s.getTitle().startsWith("FleetRent") && !s.getTitle().contains("database problem")) {
                return s;
            }
        }
        return null;
    }

    private static <T extends Node> List<T> all(Class<T> type) {
        List<T> found = new ArrayList<>();
        Stage stage = mainStage();
        if (stage != null) {
            walk(stage.getScene().getRoot(), type, found);
        }
        return found;
    }

    private static <T extends Node> void walk(Node node, Class<T> type, List<T> out) {
        if (type.isInstance(node) && node.isVisible()) {
            out.add(type.cast(node));
        }
        if (node instanceof Parent p) {
            for (Node child : p.getChildrenUnmodifiable()) {
                walk(child, type, out);
            }
        }
    }

    private static Button button(String text) {
        for (Button b : all(Button.class)) {
            if (text.equals(b.getText())) {
                return b;
            }
        }
        return null;
    }

    private static boolean hasText(String text) {
        for (Label l : all(Label.class)) {
            if (l.getText() != null && l.getText().contains(text)) {
                return true;
            }
        }
        return false;
    }

    private static void click(String text) throws Exception {
        until(() -> button(text) != null && !button(text).isDisabled(), "the button '" + text + "' can be pressed");
        onFx(() -> {
            button(text).fire();
            return null;
        });
    }

    private static void type(String prompt, String text) throws Exception {
        onFx(() -> {
            for (TextField t : all(TextField.class)) {
                if (prompt.equals(t.getPromptText()) && !(t instanceof PasswordField)) {
                    t.setText(text);
                    return null;
                }
            }
            throw new AssertionError("No text box with the hint '" + prompt + "'");
        });
    }

    private static void typePassword(String prompt, String text) throws Exception {
        onFx(() -> {
            for (PasswordField p : all(PasswordField.class)) {
                if (prompt.equals(p.getPromptText())) {
                    p.setText(text);
                    return null;
                }
            }
            throw new AssertionError("No password box with the hint '" + prompt + "'");
        });
    }

    private static void login(String user, String password) throws Exception {
        type("Username", user);
        typePassword("Password", password);
        click("Log in");
    }

    private static String currentTab() throws Exception {
        return onFx(() -> {
            for (TabPane tp : all(TabPane.class)) {
                return String.valueOf(tp.getSelectionModel().getSelectedItem().getText());
            }
            return "none";
        });
    }

    private static void openTab(String name) throws Exception {
        onFx(() -> {
            for (TabPane tp : all(TabPane.class)) {
                for (Tab t : tp.getTabs()) {
                    if (name.equals(t.getText())) {
                        tp.getSelectionModel().select(t);
                        return null;
                    }
                }
            }
            throw new AssertionError("No tab named " + name);
        });
    }

    private static List<String> tabNames() throws Exception {
        return onFx(() -> {
            List<String> names = new ArrayList<>();
            for (TabPane tp : all(TabPane.class)) {
                for (Tab t : tp.getTabs()) {
                    names.add(t.getText());
                }
            }
            return names;
        });
    }

    // Runs on the JavaFX thread (call it inside until(...)).
    private static int cards() {
        int n = 0;
        for (Node node : all(Node.class)) {
            if (node.getStyleClass().contains("offer-card")) {
                n++;
            }
        }
        return n;
    }

    private static int offerCards() throws Exception {
        return onFx(ScreenFlowTest::cards);
    }

    private static void clickFirstCar() throws Exception {
        onFx(() -> {
            for (Node n : all(Node.class)) {
                if (n.getStyleClass().contains("offer-card")) {
                    n.getOnMouseClicked().handle(null);
                    return null;
                }
            }
            throw new AssertionError("There is no car card to click");
        });
    }

    // Runs on the JavaFX thread (call it inside until(...)).
    private static int sizeOfList(Class<?> itemType, String styleClass) {
        for (ListView<?> l : all(ListView.class)) {
            if (l.getStyleClass().contains(styleClass) && !l.getItems().isEmpty() && itemType.isInstance(l.getItems().get(0))) {
                return l.getItems().size();
            }
        }
        return 0;
    }

    private static int listSize(Class<?> itemType, String styleClass) throws Exception {
        return onFx(() -> sizeOfList(itemType, styleClass));
    }

    // ---------- login screen ----------

    @Test
    void theLoginScreenHasTheThreeActionsAndNoSignOut() throws Exception {
        assertNotNull(onFx(() -> button("Forgot password?")));
        assertNotNull(onFx(() -> button("Create an account")));
        assertTrue(onFx(() -> button("Sign out") == null));
    }

    @Test
    void aWrongPasswordShowsAMessageAndStaysOnTheLoginScreen() throws Exception {
        login("ana_reyes", "wrongpass1");
        until(() -> hasText("not correct"), "a message about the wrong password");
        assertNotNull(onFx(() -> button("Log in")));
        assertTrue(onFx(() -> button("Sign out") == null));
    }

    @Test
    void emptyFieldsDoNotLogInAndDoNotCrash() throws Exception {
        click("Log in");
        Thread.sleep(300);
        assertNotNull(onFx(() -> button("Log in")));
        assertTrue(onFx(() -> button("Sign out") == null));
    }

    @Test
    void forgotPasswordShowsTheSecurityQuestionOfThatUser() throws Exception {
        click("Forgot password?");
        type("Username", "ana_reyes");
        click("Find my account");
        until(() -> hasText("first pet"), "the security question appears");
        click("Back to log in");
        until(() -> button("Log in") != null, "back on the login screen");
    }

    @SuppressWarnings("unchecked")
    @Test
    void aNewRenterCanSignUpAndThenLogIn() throws Exception {
        click("Create an account");
        type("4 to 20 letters, digits, _ or .", "ben_cruz");
        typePassword("8+ characters, a letter and a digit", "Passw0rd2");
        typePassword("Type the password again", "Passw0rd2");
        type("Ana Reyes", "Ben Cruz");
        type("0917 123 4567", "0918 555 1234");
        onFx(() -> {
            for (ChoiceBox<?> c : all(ChoiceBox.class)) {
                if (c.getItems().contains(AuthService.SECURITY_QUESTIONS.get(1))) {
                    ((ChoiceBox<String>) c).setValue(AuthService.SECURITY_QUESTIONS.get(1));
                }
            }
            return null;
        });
        type("Your answer", "Mabini");
        click("Create account");
        until(() -> hasText("Account created"), "the sign-up message");
        login("ben_cruz", "Passw0rd2");
        until(() -> button("Sign out") != null, "the renter screen");
        assertEquals(List.of("Cars", "My bookings", "My account"), tabNames());
    }

    // ---------- renter screen ----------

    @Test
    void aRenterSeesOnlyTheRenterTabsAndNothingOfTheOwner() throws Exception {
        login("ana_reyes", "Passw0rd1");
        until(() -> button("Sign out") != null, "the renter screen");
        assertEquals(List.of("Cars", "My bookings", "My account"), tabNames());
        assertFalse(onFx(() -> hasText("Dashboard")));
        assertTrue(onFx(() -> button("Add Vehicle") == null));
        assertTrue(onFx(() -> button("Import") == null));
        assertEquals(6, offerCards());
    }

    @Test
    void aRenterCanRentUndoReserveAndSignOut() throws Exception {
        login("ana_reyes", "Passw0rd1");
        until(() -> cards() == 6, "six car cards");
        clickFirstCar();
        click("Rent today");
        until(() -> hasText("started"), "the rental started message");
        click("Undo last");
        until(() -> hasText("was undone"), "the undo message");
        onFx(() -> {
            List<DatePicker> pickers = all(DatePicker.class);
            pickers.get(0).setValue(LocalDate.now().plusDays(3));
            pickers.get(1).setValue(LocalDate.now().plusDays(5));
            return null;
        });
        Thread.sleep(400);
        clickFirstCar();
        click("Reserve these days");
        until(() -> hasText("Reservation #"), "the reservation message");
        openTab("My bookings");
        until(() -> button("Cancel selected reservation") != null, "My bookings with its cancel button");
        click("Sign out");
        until(() -> button("Log in") != null, "the login screen after sign out");
        assertTrue(onFx(() -> button("Sign out") == null));
    }

    @Test
    void aRentedCarCannotBeRentedAgainByTheSameRenterForTheSameDays() throws Exception {
        login("ana_reyes", "Passw0rd1");
        until(() -> cards() == 6, "six car cards");
        clickFirstCar();
        click("Rent today");
        until(() -> hasText("started"), "the rental started message");
        clickFirstCar();
        Thread.sleep(500);
        try (Database check = new Database(System.getProperty("fleetrent.dbUrl"))) {
            assertEquals(1, check.getAllRentals().size());
        }
    }

    @Test
    void aCarTheOwnerRemovesDisappearsFromTheRentersScreenByItself() throws Exception {
        login("ana_reyes", "Passw0rd1");
        until(() -> cards() == 6, "six car cards");
        try (Database other = new Database(System.getProperty("fleetrent.dbUrl"))) {
            new FleetService(other).removeVehicle(other.getAllVehicles().get(0).getId());
        }
        until(() -> cards() == 5, "the renter screen refreshes and shows five cars");
    }

    // ---------- owner screen ----------

    @Test
    void theOwnerHasFiveTabsAndSeesTheRentersReservation() throws Exception {
        try (Database db = new Database(System.getProperty("fleetrent.dbUrl"))) {
            new BookingService(db, LocalDate::now).reserve(ana, db.getAllVehicles().get(0).getId(),
                    LocalDate.now().plusDays(3), LocalDate.now().plusDays(5));
        }
        login("owner01", "Passw0rd1");
        until(() -> button("Sign out") != null, "the owner screen");
        assertEquals(List.of("Vehicles", "Rentals", "Reservations", "Import", "Dashboard"), tabNames());
        openTab("Reservations");
        until(() -> anyListHas(Reservation.class), "the reservation is listed");
    }

    private static boolean anyListHas(Class<?> type) {
        for (ListView<?> l : all(ListView.class)) {
            if (!l.getItems().isEmpty() && type.isInstance(l.getItems().get(0))) {
                return true;
            }
        }
        return false;
    }

    @Test
    void aReservationMadeAfterTheOwnerLoggedInAppearsByItself() throws Exception {
        login("owner01", "Passw0rd1");
        until(() -> button("Sign out") != null, "the owner screen");
        openTab("Reservations");
        try (Database db = new Database(System.getProperty("fleetrent.dbUrl"))) {
            new BookingService(db, LocalDate::now).reserve(ana, db.getAllVehicles().get(1).getId(),
                    LocalDate.now().plusDays(2), LocalDate.now().plusDays(3));
        }
        until(() -> anyListHas(Reservation.class), "the new reservation shows up without pressing anything");
    }

    @SuppressWarnings("unchecked")
    @Test
    void addVehicleWorksAfterPickingAMakeAndAModelAndThenChangingTheYear() throws Exception {
        login("owner01", "Passw0rd1");
        until(() -> button("Add Vehicle") != null, "the Vehicles tab with its form");
        int before = listSize(Vehicle.class, "vehicle-list");
        onFx(() -> {
            for (ChoiceBox<?> c : all(ChoiceBox.class)) {
                if (c.getItems().contains("MOTORCYCLE") && c.getItems().size() == 3) {
                    ((ChoiceBox<String>) c).setValue("MOTORCYCLE");
                }
            }
            return null;
        });
        until(() -> hasListItem(String.class, "HONDA"), "the makes list from NHTSA");
        onFx(() -> {
            for (ListView<?> l : all(ListView.class)) {
                if (l.getStyleClass().contains("plain-list") && !l.getItems().isEmpty() && l.getItems().get(0) instanceof String) {
                    ((ListView<String>) l).getSelectionModel().select("HONDA");
                }
            }
            return null;
        });
        until(() -> anyListHas(VehicleModelInfo.class), "the models list from NHTSA");
        onFx(() -> {
            for (ListView<?> l : all(ListView.class)) {
                if (l.getStyleClass().contains("plain-list") && !l.getItems().isEmpty() && l.getItems().get(0) instanceof VehicleModelInfo) {
                    ListView<VehicleModelInfo> models = (ListView<VehicleModelInfo>) l;
                    for (VehicleModelInfo m : models.getItems()) {
                        if (Validators.sameName(m.getModelName(), "CB1000 Hornet")) {
                            models.getSelectionModel().select(m);
                        }
                    }
                }
            }
            return null;
        });
        // changing the year after the model was clicked is what used to break Add Vehicle
        onFx(() -> {
            for (ChoiceBox<?> c : all(ChoiceBox.class)) {
                if (c.getItems().contains("Not specified")) {
                    ((ChoiceBox<String>) c).setValue("2022");
                }
            }
            return null;
        });
        Thread.sleep(1200);
        onFx(() -> {
            for (ChoiceBox<?> c : all(ChoiceBox.class)) {
                if (c.getItems().contains("White")) {
                    ((ChoiceBox<String>) c).setValue("White");
                }
            }
            return null;
        });
        type("ABC 123", "NEW 777");
        type("1500", "950");
        click("Add Vehicle");
        until(() -> hasText("Saved:"), "the Saved message");
        until(() -> sizeOfList(Vehicle.class, "vehicle-list") == before + 1, "the new vehicle is in the fleet list");
        try (Database check = new Database(System.getProperty("fleetrent.dbUrl"))) {
            Vehicle saved = check.findVehicleByPlate("NEW 777");
            assertNotNull(saved);
            assertEquals("HONDA", saved.getMake());
            assertTrue(Validators.sameName("CB1000 Hornet", saved.getModel()));
            assertEquals(950, saved.getDailyRate(), 0.001);
        }
    }

    private static boolean hasListItem(Class<?> type, Object item) {
        for (ListView<?> l : all(ListView.class)) {
            if (!l.getItems().isEmpty() && type.isInstance(l.getItems().get(0)) && l.getItems().contains(item)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void editModeShowsSaveAndCancelAndCancelGoesBackToAdd() throws Exception {
        login("owner01", "Passw0rd1");
        until(() -> button("Edit") != null, "a vehicle card with an Edit button");
        onFx(() -> {
            button("Edit").fire();
            return null;
        });
        until(() -> button("Save changes") != null && button("Cancel editing") != null, "edit mode");
        click("Cancel editing");
        until(() -> button("Add Vehicle") != null, "back to the Add form");
    }

    @Test
    void removeTakesTheVehicleOutOfTheListAfterTheOwnerConfirms() throws Exception {
        login("owner01", "Passw0rd1");
        until(() -> button("Remove") != null, "a vehicle card with a Remove button");
        int before = listSize(Vehicle.class, "vehicle-list");
        // the confirm window waits for an answer, so the click is sent without waiting for it to return
        Platform.runLater(() -> button("Remove").fire());
        // the confirm window opens above the main window
        until(() -> {
            for (Window w : new ArrayList<>(Window.getWindows())) {
                if (w instanceof Stage s && s != mainStage() && s.isShowing() && s.getScene() != null) {
                    for (Node n : s.getScene().getRoot().lookupAll(".button")) {
                        if (n instanceof Button b && "Remove it".equals(b.getText())) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }, "the confirm window with the 'Remove it' button");
        Platform.runLater(() -> {
            for (Window w : new ArrayList<>(Window.getWindows())) {
                if (w instanceof Stage s && s != mainStage() && s.isShowing() && s.getScene() != null) {
                    for (Node n : s.getScene().getRoot().lookupAll(".button")) {
                        if (n instanceof Button b && "Remove it".equals(b.getText())) {
                            b.fire();
                        }
                    }
                }
            }
        });
        until(() -> sizeOfList(Vehicle.class, "vehicle-list") == before - 1, "the fleet list has one vehicle less");
        try (Database check = new Database(System.getProperty("fleetrent.dbUrl"))) {
            assertEquals(1, check.getRemovedVehicles().size());
        }
    }

    @Test
    void signOutOfTheOwnerScreenReturnsToTheLoginScreenAndAnotherUserCanLogIn() throws Exception {
        login("owner01", "Passw0rd1");
        until(() -> button("Sign out") != null, "the owner screen");
        click("Sign out");
        until(() -> button("Log in") != null, "the login screen");
        login("ana_reyes", "Passw0rd1");
        until(() -> button("Sign out") != null, "the renter screen");
        assertEquals(List.of("Cars", "My bookings", "My account"), tabNames());
    }

    // ---------- window size ----------

    private static final double[][] SIZES = {{860, 560}, {1024, 640}, {1366, 700}, {1920, 1040}, {2560, 1400}};

    // Makes the window this big (like dragging its corner or pressing Maximize) and checks that the
    // header buttons are still inside the window and the tabs fill the window.
    private static void checkWindowAtEverySize(String buttonThatMustStayVisible, boolean tabs) throws Exception {
        for (double[] size : SIZES) {
            onFx(() -> {
                Stage stage = mainStage();
                stage.setWidth(size[0]);
                stage.setHeight(size[1]);
                return null;
            });
            Thread.sleep(350);
            String where = " at " + (int) size[0] + "x" + (int) size[1] + " (tab " + currentTab() + ")";
            onFx(() -> {
                Stage stage = mainStage();
                stage.getScene().getRoot().applyCss();
                stage.getScene().getRoot().layout();
                double w = stage.getScene().getWidth();
                double h = stage.getScene().getHeight();
                assertTrue(w >= 800, "the window keeps a usable width" + where);
                Button b = button(buttonThatMustStayVisible);
                assertNotNull(b, buttonThatMustStayVisible + " is there" + where);
                javafx.geometry.Bounds inScene = b.localToScene(b.getBoundsInLocal());
                javafx.scene.layout.Region rootRegion = (javafx.scene.layout.Region) stage.getScene().getRoot();
                assertTrue(inScene.getMaxX() <= w + 1, buttonThatMustStayVisible + " is not pushed out on the right" + where
                        + " [scene " + (int) w + ", button right edge " + (int) inScene.getMaxX() + ", root min width "
                        + (int) rootRegion.minWidth(-1) + ", root width " + (int) rootRegion.getWidth() + "]");
                assertTrue(inScene.getMinX() >= 0, buttonThatMustStayVisible + " is not pushed out on the left" + where);
                assertTrue(inScene.getMaxY() <= h + 1, buttonThatMustStayVisible + " is inside the window" + where);
                if (tabs) {
                    TabPane tp = all(TabPane.class).get(0);
                    javafx.geometry.Bounds tb = tp.localToScene(tp.getBoundsInLocal());
                    assertTrue(Math.abs(tb.getWidth() - w) <= 2, "the tabs fill the width" + where);
                    assertTrue(tb.getMaxY() >= h - 2, "the tabs reach the bottom of the window" + where);
                    assertTrue(tb.getHeight() >= 250, "the tabs keep a usable height" + where);
                }
                return null;
            });
        }
    }

    @Test
    void theLoginScreenStaysInsideTheWindowAtEverySize() throws Exception {
        checkWindowAtEverySize("Log in", false);
    }

    @Test
    void theOwnerScreenFillsTheWindowAtEverySizeOnEveryTab() throws Exception {
        login("owner01", "Passw0rd1");
        until(() -> button("Sign out") != null, "the owner screen");
        for (String tab : new String[]{"Vehicles", "Rentals", "Reservations", "Import", "Dashboard"}) {
            openTab(tab);
            checkWindowAtEverySize("Sign out", true);
        }
    }

    @Test
    void theRenterScreenFillsTheWindowAtEverySizeOnEveryTab() throws Exception {
        login("ana_reyes", "Passw0rd1");
        until(() -> button("Sign out") != null, "the renter screen");
        for (String tab : new String[]{"Cars", "My bookings", "My account"}) {
            openTab(tab);
            checkWindowAtEverySize("Sign out", true);
        }
    }

    @Test
    void theWindowCannotBeMadeSmallerThanWhatTheScreensNeed() throws Exception {
        onFx(() -> {
            Stage stage = mainStage();
            assertTrue(stage.getMinWidth() >= 700 && stage.getMinWidth() <= 1000, "minimum width " + stage.getMinWidth());
            assertTrue(stage.getMinHeight() >= 450 && stage.getMinHeight() <= 700, "minimum height " + stage.getMinHeight());
            return null;
        });
    }

    @Test
    void theWindowCanBeMaximizedAndRestoredWithoutLosingTheScreen() throws Exception {
        login("ana_reyes", "Passw0rd1");
        until(() -> cards() == 6, "six car cards");
        onFx(() -> {
            mainStage().setMaximized(true);
            return null;
        });
        Thread.sleep(400);
        onFx(() -> {
            mainStage().setMaximized(false);
            return null;
        });
        Thread.sleep(400);
        assertEquals(6, offerCards());
        assertNotNull(onFx(() -> button("Sign out")));
    }
}
