package fleetrent;

import java.net.URL;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;

// The main window: a header and four tabs (Vehicles, Rentals, Import, Dashboard).
// The colors and shapes come from src/main/resources/fleetrent.css.
public class App extends Application {
    private Database db;

    @Override
    public void start(Stage stage) throws Exception {
        try {
            db = new Database("jdbc:sqlite:fleetrent.db");
            db.createTables();
        } catch (java.sql.SQLException e) {
            // The database is the heart of the app, so without it the app tells the user why and stops.
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR,
                    ErrorMessages.forDatabase("opening the database", e)
                            + "\n\nFleetRent will close now.");
            alert.setTitle("FleetRent - database problem");
            alert.setHeaderText("The database cannot be opened");
            alert.showAndWait();
            db = null;
            javafx.application.Platform.exit();
            return;
        }

        Label title = new Label("FleetRent");
        title.getStyleClass().add("app-title");
        Label subtitle = new Label("Vehicle rental and fleet management");
        subtitle.getStyleClass().add("app-subtitle");
        VBox titles = new VBox(2, title, subtitle);
        // A little road of three cars in the corner of the header.
        HBox road = new HBox(6, CarGraphic.create("SEDAN", "Purple", 90),
                CarGraphic.create("SUV", "White", 96), CarGraphic.create("MOTORCYCLE", "Orange", 78));
        road.getStyleClass().add("road");
        road.setAlignment(Pos.BOTTOM_CENTER);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(titles, spacer, road);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("header");

        VehiclePictures pictures = new VehiclePictures(new CarImageStore());
        VehicleTab vehicleTab = new VehicleTab(db, pictures);
        RentalTab rentalTab = new RentalTab(db, pictures);
        ImportTab importTab = new ImportTab(db);
        DashboardTab dashboardTab = new DashboardTab(db);

        TabPane tabs = new TabPane(vehicleTab, rentalTab, importTab, dashboardTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        // Every time a tab is opened it reloads its data, so changes made in other tabs show up.
        tabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == vehicleTab) {
                vehicleTab.refresh();
            } else if (newTab == rentalTab) {
                rentalTab.refresh();
            } else if (newTab == dashboardTab) {
                dashboardTab.refresh();
            }
        });

        Scene scene = new Scene(new VBox(header, tabs), 1320, 800);
        URL css = App.class.getResource("/fleetrent.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        stage.setTitle("FleetRent");
        stage.setScene(scene);
        // Never open bigger than the usable screen, so the title bar and the close button stay visible.
        Rectangle2D area = Screen.getPrimary().getVisualBounds();
        stage.setWidth(Math.min(1320, area.getWidth()));
        stage.setHeight(Math.min(800, area.getHeight()));
        stage.setX(area.getMinX() + (area.getWidth() - stage.getWidth()) / 2);
        stage.setY(area.getMinY());
        // One click must be enough to type: a click on a text box or a drop-down gives it the focus at once.
        scene.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
            javafx.scene.Node n = e.getPickResult().getIntersectedNode();
            while (n != null && !(n instanceof javafx.scene.control.TextInputControl)
                    && !(n instanceof javafx.scene.control.ComboBoxBase)
                    && !(n instanceof javafx.scene.control.ChoiceBox)) {
                n = n.getParent();
            }
            if (n != null) {
                final javafx.scene.Node target = n;
                if (!target.isFocused()) {
                    target.requestFocus();
                }
                // The scroll pane also asks for the focus when it is clicked. This second request runs
                // after it, so the text box keeps the focus and the first click is enough.
                javafx.application.Platform.runLater(() -> {
                    if (!target.isFocused() && target.getScene() != null) {
                        target.requestFocus();
                    }
                });
            }
        });
        stage.show();
        stage.toFront();
        stage.requestFocus();
    }

    @Override
    public void stop() throws Exception {
        if (db != null) {
            db.close();
        }
    }
}
