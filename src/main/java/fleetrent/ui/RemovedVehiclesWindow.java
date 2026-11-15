package fleetrent.ui;

import fleetrent.controller.ControllerException;
import fleetrent.controller.VehicleController;
import fleetrent.model.Vehicle;

import java.util.function.Consumer;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

// A small window with the vehicles the owner removed and a Restore button.
final class RemovedVehiclesWindow {
    private final VehicleController controller;
    private final Consumer<Vehicle> onRestored;
    private final ListView<Vehicle> list = new ListView<>();
    private final Label info = new Label();

    private RemovedVehiclesWindow(VehicleController controller, Consumer<Vehicle> onRestored) {
        this.controller = controller;
        this.onRestored = onRestored;
    }

    // Shows the window and waits until it is closed. onRestored runs after a vehicle is back.
    static void show(Window owner, VehicleController controller, Consumer<Vehicle> onRestored) {
        new RemovedVehiclesWindow(controller, onRestored).open(owner);
    }

    private void open(Window owner) {
        list.setPrefWidth(Layout.DIALOG_LIST_WIDTH);
        list.setMinHeight(Layout.DIALOG_LIST_HEIGHT);
        list.setCellFactory(l -> new ListCell<Vehicle>() {
            @Override
            protected void updateItem(Vehicle item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayName() + "  |  "
                        + item.getPlateNumber() + "  |  " + item.getType());
            }
        });
        list.getStyleClass().add("plain-list");
        VBox.setVgrow(list, Priority.ALWAYS);
        info.getStyleClass().add("status-label");
        info.setWrapText(true);
        info.setMaxWidth(Layout.DIALOG_LIST_WIDTH);
        Button restore = new Button("Restore selected");
        restore.getStyleClass().add("primary-button");
        restore.setOnAction(e -> restoreSelected());
        load();
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Removed vehicles");
        dialog.setHeaderText("Vehicles you removed from the fleet");
        dialog.getDialogPane().setContent(new VBox(Layout.GAP_MEDIUM, list, info, restore));
        dialog.getDialogPane().getButtonTypes().add(new ButtonType("Close",
                ButtonBar.ButtonData.CANCEL_CLOSE));
        dialog.setResizable(true);
        UiHelper.themeDialog(dialog);
        dialog.initOwner(owner);
        dialog.showAndWait();
    }

    private void load() {
        try {
            list.getItems().setAll(controller.removedVehicles());
            info.setText(list.getItems().isEmpty() ? "No removed vehicles."
                    : "Pick a vehicle, then press Restore.");
        } catch (ControllerException e) {
            UiHelper.setStatus(info, e.getMessage(), true);
        }
    }

    private void restoreSelected() {
        Vehicle picked = list.getSelectionModel().getSelectedItem();
        if (picked == null) {
            UiHelper.setStatus(info, "Pick a vehicle first.", true);
            return;
        }
        try {
            controller.restore(picked.getId());
            load();
            onRestored.accept(picked);
            UiHelper.setStatus(info, picked.displayName() + " is back in the fleet.", false);
        } catch (ControllerException ex) {
            UiHelper.setStatus(info, ex.getMessage(), true);
        }
    }
}
