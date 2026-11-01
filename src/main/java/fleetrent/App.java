package fleetrent;

import java.net.URL;
import java.sql.SQLException;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

// The main window. Milestone 1: an Add Vehicle form that saves to SQLite and a card list of all vehicles.
// The colors and shapes come from src/main/resources/fleetrent.css.
public class App extends Application {
    private Database db;
    private final ListView<Vehicle> vehicleList = new ListView<>();
    private final Label status = new Label("Ready.");
    private final Label countLabel = new Label("0 vehicles");

    @Override
    public void start(Stage stage) throws Exception {
        db = new Database("jdbc:sqlite:fleetrent.db");
        db.createTables();

        // ----- header -----
        Label title = new Label("FleetRent");
        title.getStyleClass().add("app-title");
        Label subtitle = new Label("Vehicle rental and fleet management");
        subtitle.getStyleClass().add("app-subtitle");
        VBox header = new VBox(2, title, subtitle);
        header.getStyleClass().add("header");

        // ----- form card (left) -----
        ChoiceBox<String> typeBox = new ChoiceBox<>(
                FXCollections.observableArrayList("SEDAN", "SUV", "MOTORCYCLE"));
        typeBox.setValue("SEDAN");
        typeBox.setPrefWidth(220);
        TextField makeField = field("Toyota");
        TextField modelField = field("Vios");
        TextField yearField = field("2020");
        TextField plateField = field("ABC 123");
        TextField colorField = field("White");
        TextField rateField = field("1500");

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        addRow(form, 0, "Type", typeBox);
        addRow(form, 1, "Make", makeField);
        addRow(form, 2, "Model", modelField);
        addRow(form, 3, "Year", yearField);
        addRow(form, 4, "Plate number", plateField);
        addRow(form, 5, "Color", colorField);
        addRow(form, 6, "Daily rate", rateField);

        Button addButton = new Button("Add Vehicle");
        addButton.getStyleClass().add("primary-button");
        status.getStyleClass().add("status-label");
        status.setMaxWidth(260);

        Label formTitle = new Label("Add a vehicle");
        formTitle.getStyleClass().add("card-title");
        VBox formCard = new VBox(14, formTitle, form, addButton, status);
        formCard.getStyleClass().add("card");
        formCard.setPrefWidth(340);
        formCard.setMinWidth(340);

        // The wired control: clicking the button runs this handler.
        addButton.setOnAction(event -> {
            try {
                String type = typeBox.getValue();
                Vehicle vehicle = VehicleFactory.create(0, type, makeField.getText().trim(),
                        modelField.getText().trim(), Integer.parseInt(yearField.getText().trim()),
                        plateField.getText().trim(), colorField.getText().trim(),
                        Double.parseDouble(rateField.getText().trim()), 0, null,
                        VehicleFactory.defaultCategoryPath(type));
                db.insertVehicle(vehicle);
                status.setText("Saved: " + vehicle);
                makeField.clear();
                modelField.clear();
                yearField.clear();
                plateField.clear();
                colorField.clear();
                rateField.clear();
                refreshList();
            } catch (NumberFormatException e) {
                status.setText("Year and daily rate must be numbers.");
            } catch (SQLException e) {
                status.setText("Database error: " + e.getMessage());
            }
        });

        // ----- fleet card (right) -----
        Label listTitle = new Label("Our fleet");
        listTitle.getStyleClass().add("card-title");
        countLabel.getStyleClass().add("count-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox listHeader = new HBox(10, listTitle, spacer, countLabel);
        listHeader.setAlignment(Pos.CENTER_LEFT);

        Label hint = new Label("Price shown is for 1 day with no extra miles.");
        hint.getStyleClass().add("hint-label");

        vehicleList.setCellFactory(list -> new VehicleCell());
        vehicleList.getStyleClass().add("vehicle-list");
        VBox.setVgrow(vehicleList, Priority.ALWAYS);

        VBox listCard = new VBox(10, listHeader, hint, vehicleList);
        listCard.getStyleClass().add("card");
        HBox.setHgrow(listCard, Priority.ALWAYS);

        // ----- whole window -----
        HBox content = new HBox(18, formCard, listCard);
        content.setPadding(new Insets(18));
        VBox.setVgrow(content, Priority.ALWAYS);
        VBox root = new VBox(header, content);

        Scene scene = new Scene(root, 980, 640);
        URL css = App.class.getResource("/fleetrent.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        stage.setTitle("FleetRent");
        stage.setScene(scene);
        stage.show();
        refreshList();
    }

    private TextField field(String hint) {
        TextField tf = new TextField();
        tf.setPromptText(hint);
        tf.setPrefWidth(220);
        return tf;
    }

    private void addRow(GridPane grid, int row, String labelText, javafx.scene.Node control) {
        Label label = new Label(labelText);
        label.getStyleClass().add("field-label");
        grid.add(label, 0, row);
        grid.add(control, 1, row);
    }

    private void refreshList() {
        try {
            vehicleList.getItems().setAll(db.getAllVehicles());
            int count = vehicleList.getItems().size();
            countLabel.setText(count + (count == 1 ? " vehicle" : " vehicles"));
        } catch (SQLException e) {
            status.setText("Database error: " + e.getMessage());
        }
    }

    @Override
    public void stop() throws Exception {
        if (db != null) {
            db.close();
        }
    }

    // How one vehicle looks in the list: a colored type badge, its name, plate and color, and its price.
    private static class VehicleCell extends ListCell<Vehicle> {
        @Override
        protected void updateItem(Vehicle v, boolean empty) {
            super.updateItem(v, empty);
            if (empty || v == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            Label badge = new Label(v.getType());
            badge.getStyleClass().addAll("badge", "badge-" + v.getType().toLowerCase());

            Label name = new Label(v.getMake() + " " + v.getModel() + " " + v.getYear());
            name.getStyleClass().add("vehicle-name");
            Label details = new Label(v.getPlateNumber() + "  |  " + v.getColor());
            details.getStyleClass().add("vehicle-details");
            VBox text = new VBox(3, name, details);

            Region space = new Region();
            HBox.setHgrow(space, Priority.ALWAYS);

            // v is a Vehicle reference. Java runs the Sedan, Suv or Motorcycle version at runtime.
            Label price = new Label(String.format("%.2f / day", v.computeRentalCost(1, 0)));
            price.getStyleClass().add("vehicle-price");

            HBox card = new HBox(14, badge, text, space, price);
            card.setAlignment(Pos.CENTER_LEFT);
            card.getStyleClass().add("vehicle-card");
            setText(null);
            setGraphic(card);
        }
    }
}
