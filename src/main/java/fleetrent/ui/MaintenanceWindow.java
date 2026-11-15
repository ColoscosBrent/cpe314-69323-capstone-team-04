package fleetrent.ui;

import fleetrent.controller.ControllerException;
import fleetrent.controller.VehicleController;
import fleetrent.model.Maintenance;
import fleetrent.model.MaintenanceType;
import fleetrent.model.Vehicle;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

// A small window with the maintenance of one vehicle and a form to add a record.
final class MaintenanceWindow {
    private final VehicleController controller;
    private final Vehicle vehicle;
    private final Runnable onSaved;
    private final ListView<String> list = new ListView<>();
    private final Label info = new Label();
    private final ComboBox<MaintenanceType> typeBox = new ComboBox<>();
    private final DatePicker datePicker = new DatePicker(LocalDate.now());
    private final TextField costField = new TextField();
    private final TextField noteField = new TextField();

    private MaintenanceWindow(VehicleController controller, Vehicle vehicle, Runnable onSaved) {
        this.controller = controller;
        this.vehicle = vehicle;
        this.onSaved = onSaved;
    }

    // Shows the window and waits until it is closed. onSaved runs after each saved record.
    static void show(Window owner, VehicleController controller, Vehicle vehicle,
            Runnable onSaved) {
        new MaintenanceWindow(controller, vehicle, onSaved).open(owner);
    }

    private void open(Window owner) {
        list.setPrefWidth(Layout.DIALOG_LIST_WIDTH);
        list.setMinHeight(Layout.DIALOG_LIST_HEIGHT);
        list.getStyleClass().add("plain-list");
        VBox.setVgrow(list, Priority.ALWAYS);
        info.getStyleClass().add("status-label");
        info.setWrapText(true);
        info.setMaxWidth(Layout.DIALOG_LIST_WIDTH);
        typeBox.getItems().setAll(MaintenanceType.values());
        typeBox.getSelectionModel().select(MaintenanceType.PMS);
        datePicker.setEditable(false);
        costField.setPromptText("Cost, for example 2500");
        noteField.setPromptText("Note (optional)");
        Button save = new Button("Save record");
        save.getStyleClass().add("primary-button");
        save.setOnAction(e -> saveRecord());
        load();
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Maintenance");
        dialog.setHeaderText("Maintenance of " + vehicle.displayName() + " ("
                + vehicle.getPlateNumber() + ")");
        dialog.getDialogPane().setContent(new VBox(Layout.GAP_MEDIUM, list, info, typeBox,
                datePicker, costField, noteField, save));
        dialog.getDialogPane().getButtonTypes().add(new ButtonType("Close",
                ButtonBar.ButtonData.CANCEL_CLOSE));
        UiHelper.themeDialog(dialog);
        dialog.initOwner(owner);
        dialog.showAndWait();
    }

    private void load() {
        try {
            List<String> lines = new ArrayList<>();
            for (Maintenance m : controller.maintenanceOf(vehicle.getId())) {
                lines.add(m.getDoneOn() + "  |  " + m.getType().getLabel()
                        + String.format(Locale.US, "  |  %,.2f", m.getCost())
                        + (m.getNote().isEmpty() ? "" : "  |  " + m.getNote()));
            }
            list.getItems().setAll(lines);
            info.setText(lines.isEmpty() ? "No maintenance recorded yet."
                    : lines.size() + " record(s).");
        } catch (ControllerException e) {
            UiHelper.setStatus(info, e.getMessage(), true);
        }
    }

    private void saveRecord() {
        try {
            double cost = Double.parseDouble(costField.getText().trim());
            controller.logMaintenance(vehicle.getId(), datePicker.getValue(), typeBox.getValue(),
                    cost, noteField.getText());
            costField.clear();
            noteField.clear();
            load();
            onSaved.run();
            UiHelper.setStatus(info, "Saved.", false);
        } catch (NumberFormatException ex) {
            UiHelper.setStatus(info, "Enter the cost as a number.", true);
        } catch (ControllerException ex) {
            UiHelper.setStatus(info, ex.getMessage(), true);
        }
    }
}
