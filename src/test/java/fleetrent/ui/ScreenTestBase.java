package fleetrent.ui;

import fleetrent.FxToolkit;
import fleetrent.app.App;
import fleetrent.dao.Database;
import fleetrent.model.Renter;
import fleetrent.model.Vehicle;
import fleetrent.service.AuthService;
import fleetrent.service.SampleData;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
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

// What the screen tests share: a temporary database file, a fake NHTSA server, the app window
// opened before each test and closed after it, and helpers that find and press the controls the
// way a person would. The tests never touch fleetrent.db and need no internet. If the computer
// has no display for JavaFX (for example a server), every screen test is skipped, not failed.
abstract class ScreenTestBase {
    private static boolean fxReady;
    private static HttpServer server;
    Path file;
    int ana;

    @BeforeAll
    static void startJavaFx() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", ex -> {
                String path = ex.getRequestURI().toString();
                String body = path.contains("GetMakesForVehicleType")
                        ? "{\"Results\":[{\"MakeName\":\"HONDA\"},{\"MakeName\":\"YAMAHA\"},{\"Ma"
                                + "keName\":\"DUCATI\"}]}"
                        : "{\"Results\":[{\"Make_Name\":\"HONDA\",\"Model_ID\":1,\"Model_Nam"
                                + "e\":\"CB500F\"},"
                        + "{\"Make_Name\":\"HONDA\",\"Model_ID\":2,\"Model_Name\":\"CB1000  "
                                + "Hornet\"},"
                        + "{\"Make_Name\":\"HONDA\",\"Model_ID\":3,\"Model_Name\":\"Rebel 500\"}]}";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, bytes.length);
                ex.getResponseBody().write(bytes);
                ex.close();
            });
            server.start();
            System.setProperty("fleetrent.vpicUrl", "http://127.0.0.1:" + server.getAddress()
                    .getPort() + "/api");
            fxReady = FxToolkit.start();
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
        Assumptions.assumeTrue(fxReady,
                "JavaFX cannot start here (no display), so the screen tests are skipped");
        file = Files.createTempFile("fleetrent-screen", ".db");
        String url = "jdbc:sqlite:" + file.toAbsolutePath();
        try (Database db = new Database(url)) {
            db.createTables();
            for (Vehicle v : SampleData.demoVehicles()) {
                db.insertVehicle(v);
            }
            AuthService auth = new AuthService(db);
            auth.signUpOwner("owner01", "Passw0rd1", "Santos, Maria Lopez", "+63 9171234567",
                    AuthService.SECURITY_QUESTIONS.get(0), "Rex");
            auth.signUpRenter("ana_reyes", "Passw0rd1", new Renter(0, "Reyes, Ana Cruz",
                    "+63 9171234567",
                    "ana@example.com",
                    "12 Rizal St, Cebu City, Philippines"), AuthService.SECURITY_QUESTIONS.get(0),
                            "Rex");
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

    static <T> T onFx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(20, TimeUnit.SECONDS);
    }

    static void until(Supplier<Boolean> condition, String what) throws Exception {
        long end = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < end) {
            if (onFx(condition::get)) {
                return;
            }
            Thread.sleep(60);
        }
        throw new AssertionError("Waited 10 seconds, but this never happened: " + what);
    }

    static Stage mainStage() {
        for (Window w : Window.getWindows()) {
            if (w instanceof Stage s && s.isShowing() && s.getTitle() != null && s.getTitle()
                    .startsWith("FleetRent") && !s.getTitle().contains("database problem")) {
                return s;
            }
        }
        return null;
    }

    static <T extends Node> List<T> all(Class<T> type) {
        List<T> found = new ArrayList<>();
        Stage stage = mainStage();
        if (stage != null) {
            walk(stage.getScene().getRoot(), type, found);
        }
        return found;
    }

    static <T extends Node> void walk(Node node, Class<T> type, List<T> out) {
        if (type.isInstance(node) && node.isVisible()) {
            out.add(type.cast(node));
        }
        if (node instanceof Parent p) {
            for (Node child : p.getChildrenUnmodifiable()) {
                walk(child, type, out);
            }
        }
    }

    static Button button(String text) {
        for (Button b : all(Button.class)) {
            if (text.equals(b.getText())) {
                return b;
            }
        }
        return null;
    }

    static boolean hasText(String text) {
        for (Label l : all(Label.class)) {
            if (l.getText() != null && l.getText().contains(text)) {
                return true;
            }
        }
        return false;
    }

    static void click(String text) throws Exception {
        until(() -> button(text) != null && !button(text).isDisabled(), "the button '" + text
                + "' can be pressed");
        onFx(() -> {
            button(text).fire();
            return null;
        });
    }

    static void type(String prompt, String text) throws Exception {
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

    static void typePassword(String prompt, String text) throws Exception {
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

    // Presses "Log in as Owner" or "Log in as Renter" above the login boxes.
    static void chooseRole(String text) throws Exception {
        onFx(() -> {
            for (javafx.scene.control.ToggleButton t
                    : all(javafx.scene.control.ToggleButton.class)) {
                if (text.equals(t.getText())) {
                    t.setSelected(true);
                    return null;
                }
            }
            throw new AssertionError("No role button '" + text + "'");
        });
    }

    static void login(String user, String password) throws Exception {
        chooseRole(user.startsWith("owner") ? "Log in as Owner" : "Log in as Renter");
        type("Username", user);
        typePassword("Password", password);
        click("Log in");
    }

    static String currentTab() throws Exception {
        return onFx(() -> {
            for (TabPane tp : all(TabPane.class)) {
                return String.valueOf(tp.getSelectionModel().getSelectedItem().getText());
            }
            return "none";
        });
    }

    static void openTab(String name) throws Exception {
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

    static List<String> tabNames() throws Exception {
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
    static int cards() {
        int n = 0;
        for (Node node : all(Node.class)) {
            if (node.getStyleClass().contains("offer-card")) {
                n++;
            }
        }
        return n;
    }

    static int offerCards() throws Exception {
        return onFx(ScreenTestBase::cards);
    }

    static void clickFirstCar() throws Exception {
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
    static int sizeOfList(Class<?> itemType, String styleClass) {
        for (ListView<?> l : all(ListView.class)) {
            if (l.getStyleClass().contains(styleClass) && !l.getItems().isEmpty()
                    && itemType.isInstance(l.getItems().get(0))) {
                return l.getItems().size();
            }
        }
        return 0;
    }

    static int listSize(Class<?> itemType, String styleClass) throws Exception {
        return onFx(() -> sizeOfList(itemType, styleClass));
    }

    static boolean anyListHas(Class<?> type) {
        for (ListView<?> l : all(ListView.class)) {
            if (!l.getItems().isEmpty() && type.isInstance(l.getItems().get(0))) {
                return true;
            }
        }
        return false;
    }

    static boolean hasListItem(Class<?> type, Object item) {
        for (ListView<?> l : all(ListView.class)) {
            if (!l.getItems().isEmpty() && type.isInstance(l.getItems().get(0)) && l.getItems()
                    .contains(item)) {
                return true;
            }
        }
        return false;
    }
}
