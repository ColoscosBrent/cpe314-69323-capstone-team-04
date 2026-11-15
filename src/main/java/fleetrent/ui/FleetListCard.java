package fleetrent.ui;

import fleetrent.controller.VehicleController;
import fleetrent.model.Vehicle;
import fleetrent.model.VehicleFactory;
import fleetrent.model.VehicleTypes;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

// The "Our fleet" card of the Vehicles tab: the list of vehicles with its count, and the
// Maintenance, Removed vehicles and Find photos buttons. The list shows the observable state of
// the VehicleController, so the card never loads data itself.
final class FleetListCard {
    private static final String FEE_HINT_START = "Extra-mile fees: ";
    private static final double CARD_MIN_WIDTH = 380;
    private static final double CARD_PREF_WIDTH = 420;
    private static final String NO_VEHICLES = "No vehicles yet. Add one with the form.";
    private static final String LOADING = "Loading the fleet...";

    private final VehicleController controller;
    private final Label status;
    private final Runnable refresh;
    private final ListView<Vehicle> vehicleList = new ListView<>();
    private final Label countLabel = new Label("0 vehicles");
    private final Button findPhotosButton = new Button("Find photos for all");
    private final Button removedButton = new Button("Removed vehicles");
    private final Button maintenanceButton = new Button("Maintenance");
    private final PhotoJobs photos;
    private final VBox card;
    // The id of the vehicle selected in the list, kept across reloads (0 when none).
    private int selectedId;

    // status shows the messages of the tab. onEdit and onRemove get the vehicle of the clicked
    // row. refresh reloads the tab.
    FleetListCard(VehicleController controller, VehiclePictures pictures, Label status,
            Consumer<Vehicle> onEdit, Consumer<Vehicle> onRemove, Runnable refresh) {
        this.controller = controller;
        this.status = status;
        this.refresh = refresh;
        this.photos = new PhotoJobs(controller, status, findPhotosButton, refresh);
        vehicleList.setCellFactory(list -> new VehicleListCell(pictures, onEdit, onRemove));
        vehicleList.setFocusTraversable(false);
        vehicleList.getStyleClass().add("vehicle-list");
        bindToController();
        card = buildCard();
    }

    VBox node() {
        return card;
    }

    // The window of the tab, or null while the tab is not shown yet.
    Window window() {
        return vehicleList.getScene() == null ? null : vehicleList.getScene().getWindow();
    }

    // After a vehicle is saved: copy the chosen photo, or look for one online in the background.
    void afterAdd(Vehicle vehicle, Path chosenPhoto) {
        photos.afterAdd(vehicle, chosenPhoto);
    }

    private VBox buildCard() {
        countLabel.getStyleClass().add("count-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        findPhotosButton.getStyleClass().add("secondary-button");
        findPhotosButton.setOnAction(e -> findAllPhotos());
        removedButton.getStyleClass().add("secondary-button");
        removedButton.setOnAction(e -> showRemovedVehicles());
        maintenanceButton.getStyleClass().add("secondary-button");
        maintenanceButton.setOnAction(e -> showMaintenance());
        HBox titleRow = new HBox(Layout.GAP_MEDIUM, UiHelper.title("Our fleet"), spacer,
                countLabel);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        HBox fleetButtons = new HBox(Layout.GAP_NORMAL, maintenanceButton, removedButton,
                findPhotosButton);
        VBox listHeader = new VBox(Layout.GAP_NORMAL, titleRow, fleetButtons);
        Label hint = new Label("Price shown is for 1 day with no extra miles. " + feeHint()
                + " Photos come from your file, or from Wikipedia in the color of the vehicle"
                + " when there is one.");
        hint.getStyleClass().add("hint-label");
        hint.setWrapText(true);
        VBox.setVgrow(vehicleList, Priority.ALWAYS);
        VBox box = new VBox(Layout.GAP_MEDIUM, listHeader, hint, vehicleList);
        box.getStyleClass().add("card");
        box.setMinWidth(CARD_MIN_WIDTH);
        box.setPrefWidth(CARD_PREF_WIDTH);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    // The list, the count, the chosen row and the load problems come from the controller's
    // observable state. The card only shows them.
    private void bindToController() {
        vehicleList.setItems(controller.items());
        controller.selectedProperty().bind(
                vehicleList.getSelectionModel().selectedItemProperty());
        vehicleList.getSelectionModel().selectedItemProperty().addListener((obs, old, now) -> {
            if (now != null) {
                selectedId = now.getId();
            }
        });
        controller.items().addListener((javafx.beans.Observable changed) -> {
            int count = controller.items().size();
            countLabel.setText(count + (count == 1 ? " vehicle" : " vehicles"));
            // A reload brings new objects, so the row that was selected is selected again (after
            // the list finished its own update).
            Platform.runLater(this::reselect);
        });
        Label placeholder = new Label(NO_VEHICLES);
        placeholder.getStyleClass().add("hint-label");
        controller.busyProperty().addListener((obs, was, busy) ->
                placeholder.setText(busy ? LOADING : NO_VEHICLES));
        vehicleList.setPlaceholder(placeholder);
        UiHelper.showProblems(controller, status);
    }

    private void reselect() {
        for (Vehicle v : controller.items()) {
            if (v.getId() == selectedId) {
                vehicleList.getSelectionModel().select(v);
                return;
            }
        }
    }

    // "Extra-mile fees: Sedan 100 free miles a day then 0.20 per mile, ..." built from the vehicle
    // classes, so the text always matches the real rules.
    private static String feeHint() {
        StringBuilder sb = new StringBuilder(FEE_HINT_START);
        for (String type : VehicleTypes.ALL) {
            Vehicle sample = VehicleFactory.create(0, type, "", "", 0, "", "", 1, 0, null, null);
            if (sb.length() > FEE_HINT_START.length()) {
                sb.append("; ");
            }
            sb.append(type.charAt(0)).append(type.substring(1).toLowerCase(Locale.ROOT)).append(' ')
                    .append(sample.getFreeMilesPerDay()).append(" free miles a day, then ")
                    .append(String.format(Locale.US, "%.2f", sample.getFeePerExtraMile()))
                            .append(" per mile");
        }
        return sb.append('.').toString();
    }

    private void showRemovedVehicles() {
        RemovedVehiclesWindow.show(window(), controller, restored -> {
            refresh.run();
            UiHelper.setStatus(status, restored.displayName() + " was restored to the fleet.",
                    false);
        });
    }

    private void showMaintenance() {
        Vehicle picked = vehicleList.getSelectionModel().getSelectedItem();
        if (picked == null) {
            UiHelper.setStatus(status, "Pick a vehicle in the list first.", true);
            return;
        }
        MaintenanceWindow.show(window(), controller, picked, refresh);
    }

    // Looks online for a photo of every vehicle that has none.
    private void findAllPhotos() {
        final List<Vehicle> missing = new ArrayList<>();
        for (Vehicle v : vehicleList.getItems()) {
            if (!controller.hasPhoto(v.getId())) {
                missing.add(v);
            }
        }
        photos.findAll(missing);
    }
}
