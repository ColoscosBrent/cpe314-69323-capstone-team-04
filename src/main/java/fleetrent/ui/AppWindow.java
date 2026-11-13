package fleetrent.ui;

import fleetrent.controller.AppController;
import fleetrent.controller.ControllerException;
import fleetrent.model.Account;
import fleetrent.model.Role;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * One FleetRent window. It opens the database, shows the login screen, and after a login swaps the
 * screen for the owner's or the renter's. Sign out brings the login screen back. Every window has
 * its own database connection to the same file, so two windows (an owner and a renter) stay in
 * sync.
 */
public final class AppWindow {
    private static final double STAGE_MIN_WIDTH = 860;
    private static final double STAGE_MIN_HEIGHT = 560;
    private static final double FADE_MILLIS = 260;
    private static final List<AppWindow> OPEN = new ArrayList<>();
    private static int opened;

    private final Stage stage;
    private VehiclePictures pictures;
    private AppController app;
    private Scene scene;
    private AuthScreen authScreen;
    private AdminView adminView;
    private RenterView renterView;

    public AppWindow(Stage stage) {
        this.stage = stage;
    }

    // Opens another window with its own login. Used by the "New window" button.
    static void openNew() {
        new AppWindow(new Stage()).open();
    }

    /** Closes the database of every open window. */
    public static void closeAll() {
        for (AppWindow w : new ArrayList<>(OPEN)) {
            w.closeDatabase();
        }
    }

    /** Opens this window. Returns false when the database could not be opened. */
    public boolean open() {
        try {
            // The file name can be changed with -Dfleetrent.dbUrl=..., which the screen tests use
            // so they never touch your data.
            app = AppController.open(System.getProperty("fleetrent.dbUrl",
                    "jdbc:sqlite:fleetrent.db"));
        } catch (ControllerException e) {
            // The database is the heart of the app, so without it the app tells the user why and
            // stops.
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    e.getMessage() + "\n\nFleetRent will close now.");
            alert.setTitle("FleetRent - database problem");
            alert.setHeaderText("The database cannot be opened");
            alert.showAndWait();
            closeDatabase();
            if (OPEN.isEmpty()) {
                Platform.exit();
            }
            return false;
        }
        pictures = new VehiclePictures(app.vehicles()::photoFile);
        OPEN.add(this);
        int number = opened++;

        scene = new Scene(new StackPane(), 1320, 800);
        URL css = AppWindow.class.getResource("/fleetrent.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        stage.setTitle("FleetRent");
        stage.setScene(scene);
        // Small enough for a laptop, big enough that the header and tabs never overlap. Maximize
        // works at any size.
        stage.setMinWidth(STAGE_MIN_WIDTH);
        stage.setMinHeight(STAGE_MIN_HEIGHT);
        // Never open bigger than the usable screen, so the title bar and the close button stay
        // visible.
        Rectangle2D area = Screen.getPrimary().getVisualBounds();
        stage.setWidth(Math.min(1320, area.getWidth()));
        stage.setHeight(Math.min(800, area.getHeight()));
        stage.setX(area.getMinX() + (area.getWidth() - stage.getWidth()) / 2 + 28 * (number % 5));
        stage.setY(area.getMinY() + 28 * (number % 5));
        stage.setOnHidden(e -> {
            disposeViews();
            closeDatabase();
        });
        installClickFocus(scene);
        showAuth();
        stage.show();
        stage.toFront();
        stage.requestFocus();
        return true;
    }

    private void closeDatabase() {
        OPEN.remove(this);
        if (app != null) {
            app.close();
            app = null;
        }
    }

    private void disposeViews() {
        if (authScreen != null) {
            authScreen.dispose();
            authScreen = null;
        }
        if (adminView != null) {
            adminView.dispose();
            adminView = null;
        }
        if (renterView != null) {
            renterView.dispose();
            renterView = null;
        }
    }

    private void swap(Node root) {
        scene.setRoot(new StackPane(root));
        FadeTransition fade = new FadeTransition(Duration.millis(FADE_MILLIS), scene.getRoot());
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    void showAuth() {
        app.auth().signOut();
        disposeViews();
        stage.setTitle("FleetRent - Log in");
        authScreen = new AuthScreen(app.auth(), this::showFor, AppWindow::openNew);
        swap(authScreen.root());
    }

    private void showFor(Account account) {
        app.auth().session().start(account);
        disposeViews();
        if (account.getRole() == Role.ADMIN) {
            stage.setTitle("FleetRent - Owner: " + account.getDisplayName());
            adminView = new AdminView(app, account, pictures, this::showAuth, AppWindow::openNew);
            swap(adminView.root());
        } else {
            stage.setTitle("FleetRent - " + account.getDisplayName());
            renterView = new RenterView(app, account, pictures, this::showAuth, AppWindow::openNew);
            swap(renterView.root());
        }
    }

    // One click must be enough to type: a click on a text box or a drop-down gives it the focus at
    // once.
    private static void installClickFocus(Scene scene) {
        scene.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
            Node n = e.getPickResult().getIntersectedNode();
            while (n != null && !(n instanceof javafx.scene.control.TextInputControl)
                    && !(n instanceof javafx.scene.control.ComboBoxBase)
                    && !(n instanceof javafx.scene.control.ChoiceBox)) {
                n = n.getParent();
            }
            if (n != null) {
                final Node target = n;
                if (!target.isFocused()) {
                    target.requestFocus();
                }
                // The scroll pane also asks for the focus when it is clicked. This second request
                // runs after it, so the text box keeps the focus and the first click is enough.
                Platform.runLater(() -> {
                    if (!target.isFocused() && target.getScene() != null) {
                        target.requestFocus();
                    }
                });
            }
        });
    }
}
