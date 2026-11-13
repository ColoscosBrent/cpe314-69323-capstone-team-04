package fleetrent.ui;

import fleetrent.controller.ControllerException;
import fleetrent.controller.RentalController;
import fleetrent.model.Payment;
import fleetrent.model.Rental;
import fleetrent.model.RentalStatus;
import fleetrent.model.Renter;
import fleetrent.model.Vehicle;
import fleetrent.service.RentalMath;
import fleetrent.service.Validators;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Tab 2: add renters (every Renter field of the ERD), start a rental, return it and see the final
 * cost. Ongoing and returned rentals show the picture of the vehicle, like the Vehicles tab.
 */
public class RentalTab extends Tab {
    private static final double RENTER_FORM_HGAP = 12;
    private static final double RENTER_FORM_VGAP = 7;
    private static final double RENTER_STATUS_MAX_WIDTH = 440;
    private static final double RENTER_BOX_PREF_WIDTH = 340;
    private static final double RENTER_BOX_MIN_WIDTH = 340;
    private static final double VEHICLE_BOX_PREF_WIDTH = 340;
    private static final double VEHICLE_BOX_MIN_WIDTH = 340;
    private static final double VEHICLE_PREVIEW_MIN_HEIGHT = 120;
    private static final double AVAILABILITY_MAX_WIDTH = 440;
    private static final double START_FORM_HGAP = 12;
    private static final double START_FORM_VGAP = 7;
    private static final double START_STATUS_MAX_WIDTH = 440;
    private static final double LEFT_CARD_PREF_WIDTH = 520;
    private static final double LEFT_CARD_MIN_WIDTH = 520;
    private static final double ONGOING_LIST_PREF_HEIGHT = 190;
    private static final double ONGOING_LIST_MIN_HEIGHT = 170;
    private static final double HISTORY_LIST_PREF_HEIGHT = 190;
    private static final double HISTORY_LIST_MIN_HEIGHT = 170;
    private static final double RETURN_FORM_HGAP = 12;
    private static final double RETURN_FORM_VGAP = 7;
    private static final double RETURN_STATUS_MAX_WIDTH = 520;
    private static final double RIGHT_CARD_MIN_WIDTH = 560;
    private static final Insets CONTENT_PADDING = new Insets(14, 18, 18, 18);
    private final RentalController controller;
    private final VehiclePictures pictures;

    private final ChoiceBox<Renter> renterBox = new ChoiceBox<>();
    private final ChoiceBox<Vehicle> vehicleBox = new ChoiceBox<>();
    private final ListView<Rental> ongoingList = new ListView<>();
    private final ListView<Rental> historyList = new ListView<>();
    private final Label renterStatus = new Label("Fill in the renter, then press Add Renter.");
    private final Label startStatus = new Label("Ready.");
    private final Label returnStatus = new Label("Pick an ongoing rental to return it.");
    private final Label renterError = UiHelper.errorLabel();
    private final Label vehicleError = UiHelper.errorLabel();
    private final Label startError = UiHelper.errorLabel();
    private final Label endError = UiHelper.errorLabel();
    private final Label availability = new Label();
    private final StackPane vehiclePreview = new StackPane();
    private final Map<Integer, Renter> renters = new HashMap<>();
    private final Map<Integer, Vehicle> vehicles = new HashMap<>();
    private final Map<Integer, Payment> payments = new HashMap<>();

    /** Creates a rental tab. */
    public RentalTab(RentalController controller, VehiclePictures pictures) {
        super("Rentals");
        this.controller = controller;
        this.pictures = pictures;
        setClosable(false);

        // ----- new renter: name, contact, email, address (the Renter ERD) -----
        ValidatedField nameField = new ValidatedField(Validators.Kind.NAME, "Ana Reyes", true);
        ValidatedField contactField = new ValidatedField(Validators.Kind.CONTACT, "0917 123 4567",
                true);
        ValidatedField emailField = new ValidatedField(Validators.Kind.EMAIL,
                "ana@example.com (optional)", false);
        ValidatedField addressField = new ValidatedField(Validators.Kind.ADDRESS,
                "12 Rizal St, Cebu City (optional)", false);
        Button addRenter = new Button("Add Renter");
        addRenter.getStyleClass().add("primary-button");
        GridPane renterForm = new GridPane();
        renterForm.setHgap(RENTER_FORM_HGAP);
        renterForm.setVgap(RENTER_FORM_VGAP);
        UiHelper.addRow(renterForm, 0, "Name", nameField.node());
        UiHelper.addRow(renterForm, 1, "Contact", contactField.node());
        UiHelper.addRow(renterForm, 2, "Email", emailField.node());
        UiHelper.addRow(renterForm, 3, "Address", addressField.node());
        renterStatus.getStyleClass().add("status-label");
        renterStatus.setWrapText(true);
        renterStatus.setMaxWidth(RENTER_STATUS_MAX_WIDTH);
        addRenter.setOnAction(e -> {
            boolean ok = nameField.validate();
            ok = contactField.validate() && ok;
            ok = emailField.validate() && ok;
            ok = addressField.validate() && ok;
            if (!ok) {
                UiHelper.setStatus(renterStatus, "Please fix the red messages first.", true);
                return;
            }
            try {
                String name = nameField.text();
                controller.addRenter(new Renter(0, name, contactField.text(),
                        emailField.textOrNull(),
                        addressField.textOrNull()));
                nameField.reset();
                contactField.reset();
                emailField.reset();
                addressField.reset();
                UiHelper.setStatus(renterStatus, "Renter saved: " + name, false);
                refresh();
            } catch (ControllerException ex) {
                UiHelper.setStatus(renterStatus, ex.getMessage(), true);
            }
        });

        // ----- start a rental -----
        renterBox.setPrefWidth(RENTER_BOX_PREF_WIDTH);
        renterBox.setMinWidth(RENTER_BOX_MIN_WIDTH);
        vehicleBox.setPrefWidth(VEHICLE_BOX_PREF_WIDTH);
        vehicleBox.setMinWidth(VEHICLE_BOX_MIN_WIDTH);
        DatePicker startPicker = new DatePicker(LocalDate.now());
        DatePicker endPicker = new DatePicker(LocalDate.now().plusDays(3));
        startPicker.setEditable(false);
        endPicker.setEditable(false);
        vehiclePreview.getStyleClass().add("preview-stage");
        vehiclePreview.setMinHeight(VEHICLE_PREVIEW_MIN_HEIGHT);
        availability.getStyleClass().add("hint-label");
        availability.setWrapText(true);
        availability.setMaxWidth(AVAILABILITY_MAX_WIDTH);
        vehicleBox.valueProperty().addListener((obs, o, v) -> {
            UiHelper.setError(vehicleError, null);
            showChosenVehicle(v);
        });
        renterBox.valueProperty().addListener((obs, o, v) -> UiHelper.setError(renterError, null));
        startPicker.valueProperty().addListener((obs, o, v) -> UiHelper.setError(startError, null));
        endPicker.valueProperty().addListener((obs, o, v) -> UiHelper.setError(endError, null));
        GridPane startForm = new GridPane();
        startForm.setHgap(START_FORM_HGAP);
        startForm.setVgap(START_FORM_VGAP);
        UiHelper.addRow(startForm, 0, "Renter", UiHelper.withError(renterBox, renterError));
        UiHelper.addRow(startForm, 1, "Vehicle", UiHelper.withError(vehicleBox, vehicleError));
        UiHelper.addRow(startForm, 2, "Start date", UiHelper.withError(startPicker, startError));
        UiHelper.addRow(startForm, 3, "End date", UiHelper.withError(endPicker, endError));
        Button startButton = new Button("Start Rental");
        startButton.getStyleClass().add("primary-button");
        Button undoButton = new Button("Undo last check-out");
        startStatus.getStyleClass().add("status-label");
        startStatus.setWrapText(true);
        startStatus.setMaxWidth(START_STATUS_MAX_WIDTH);

        startButton.setOnAction(e -> {
            Renter renter = renterBox.getValue();
            Vehicle vehicle = vehicleBox.getValue();
            boolean ok = true;
            if (renter == null) {
                UiHelper.setError(renterError, "Pick a renter.");
                ok = false;
            }
            if (vehicle == null) {
                UiHelper.setError(vehicleError, "Pick a vehicle.");
                ok = false;
            }
            if (startPicker.getValue() == null) {
                UiHelper.setError(startError, "Pick the start date.");
                ok = false;
            }
            if (endPicker.getValue() == null) {
                UiHelper.setError(endError, "Pick the end date.");
                ok = false;
            } else if (startPicker.getValue() != null && endPicker.getValue()
                    .isBefore(startPicker.getValue())) {
                UiHelper.setError(endError, "The end date cannot be before the start date.");
                ok = false;
            }
            if (!ok) {
                UiHelper.setStatus(startStatus, "Please fix the red messages first.", true);
                return;
            }
            try {
                Rental rental = controller.start(renter.getId(), vehicle.getId(),
                        startPicker.getValue(), endPicker.getValue());
                if (rental == null) {
                    UiHelper.setStatus(startStatus, vehicle.getMake() + " " + vehicle.getModel()
                            + " is already rented. " + renter.getName()
                                    + " joined the waiting list ("
                            + controller.waitingCount(vehicle.getId()) + " waiting).", false);
                } else {
                    UiHelper.setStatus(startStatus, "Rental #" + rental.getId() + " started for "
                            + renter.getName() + ".", false);
                }
                refresh();
            } catch (ControllerException ex) {
                UiHelper.setStatus(startStatus, ex.getMessage(), true);
            }
        });
        undoButton.setOnAction(e -> {
            try {
                Rental undone = controller.undoLastCheckout();
                UiHelper.setStatus(startStatus,
                        undone == null ? "Nothing to undo." : "Rental #" + undone.getId()
                                + " was undone.", false);
                refresh();
            } catch (ControllerException ex) {
                UiHelper.setStatus(startStatus, ex.getMessage(), true);
            }
        });

        HBox startButtons = new HBox(Layout.GAP_MEDIUM, startButton, undoButton);
        VBox leftCard = new VBox(Layout.GAP_WIDE, UiHelper.title("New renter"), renterForm,
                addRenter,
                renterStatus,
                UiHelper.title("Start a rental"), startForm, vehiclePreview, availability,
                        startButtons, startStatus);
        leftCard.getStyleClass().add("card");
        leftCard.setPrefWidth(LEFT_CARD_PREF_WIDTH);
        leftCard.setMinWidth(LEFT_CARD_MIN_WIDTH);

        // ----- ongoing rentals, return form, history -----
        ongoingList.getStyleClass().add("vehicle-list");
        ongoingList.setCellFactory(list -> new RentalCell(false));
        ongoingList.setPrefHeight(ONGOING_LIST_PREF_HEIGHT);
        ongoingList.setMinHeight(ONGOING_LIST_MIN_HEIGHT);
        historyList.getStyleClass().add("vehicle-list");
        historyList.setCellFactory(list -> new RentalCell(true));
        historyList.setPrefHeight(HISTORY_LIST_PREF_HEIGHT);
        historyList.setMinHeight(HISTORY_LIST_MIN_HEIGHT);
        historyList.setFocusTraversable(false);

        DatePicker returnPicker = new DatePicker(LocalDate.now());
        returnPicker.setEditable(false);
        ValidatedField milesField = new ValidatedField(Validators.Kind.DIGITS, "miles driven",
                true);
        Label returnDateError = UiHelper.errorLabel();
        returnPicker.valueProperty().addListener((obs, o, v) -> UiHelper.setError(returnDateError,
                null));
        GridPane returnForm = new GridPane();
        returnForm.setHgap(RETURN_FORM_HGAP);
        returnForm.setVgap(RETURN_FORM_VGAP);
        UiHelper.addRow(returnForm, 0, "Return date", UiHelper.withError(returnPicker,
                returnDateError));
        UiHelper.addRow(returnForm, 1, "Miles driven", milesField.node());
        Button returnButton = new Button("Return Vehicle");
        returnButton.getStyleClass().add("primary-button");
        returnStatus.getStyleClass().add("status-label");
        returnStatus.setWrapText(true);
        returnStatus.setMaxWidth(RETURN_STATUS_MAX_WIDTH);
        returnButton.setOnAction(e -> {
            Rental selected = ongoingList.getSelectionModel().getSelectedItem();
            boolean ok = true;
            if (returnPicker.getValue() == null) {
                UiHelper.setError(returnDateError, "Pick the return date.");
                ok = false;
            }
            if (!milesField.validate()) {
                ok = false;
            }
            if (selected == null) {
                UiHelper.setStatus(returnStatus, "Pick an ongoing rental from the list first.",
                        true);
                return;
            }
            if (!ok) {
                UiHelper.setStatus(returnStatus, "Please fix the red messages first.", true);
                return;
            }
            try {
                int miles = Integer.parseInt(milesField.text());
                double cost = controller.returnRental(selected.getId(), returnPicker.getValue(),
                        miles);
                UiHelper.setStatus(returnStatus, String.format(Locale.US,
                        "Returned. Amount to pay: %.2f", cost), false);
                milesField.reset();
                refresh();
            } catch (NumberFormatException ex) {
                milesField.showError("Enter a whole number (digits only).");
            } catch (ControllerException ex) {
                UiHelper.setStatus(returnStatus, ex.getMessage(), true);
            }
        });
        Label historyTitle = UiHelper.title("Returned rentals");
        VBox rightCard = new VBox(Layout.GAP_MEDIUM, UiHelper.title("Ongoing rentals"),
                ongoingList, returnForm,
                returnButton, returnStatus, historyTitle, historyList);
        rightCard.getStyleClass().add("card");
        rightCard.setMinWidth(RIGHT_CARD_MIN_WIDTH);
        HBox.setHgrow(rightCard, Priority.ALWAYS);

        HBox content = new HBox(Layout.GAP_XLARGE, leftCard, rightCard);
        content.setPadding(CONTENT_PADDING);
        setContent(UiHelper.scroll(content));
        showChosenVehicle(null);
        refresh();
    }

    // The picture of the vehicle chosen in "Start a rental", and whether it is free.
    private void showChosenVehicle(Vehicle v) {
        if (v == null) {
            vehiclePreview.getChildren().setAll(new Label("Pick a vehicle to see its picture."));
            availability.setText("");
            return;
        }
        vehiclePreview.getChildren().setAll(pictures.stage(v));
        try {
            availability.setText(controller.isRentedOut(v.getId())
                    ? "This vehicle is rented out now. Starting a rental puts the renter on its "
                            + "waiting list."
                    : "This vehicle is available.");
        } catch (ControllerException e) {
            availability.setText(e.getMessage());
        }
    }

    // One rental in a list: the vehicle picture, who has it, the dates and the status.
    private class RentalCell extends ListCell<Rental> {
        private final boolean returned;

        RentalCell(boolean returned) {
            this.returned = returned;
        }

        @Override
        protected void updateItem(Rental r, boolean empty) {
            super.updateItem(r, empty);
            if (empty || r == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            Renter renter = renters.get(r.getRenterId());
            Vehicle v = vehicles.get(r.getVehicleId());
            Label title = new Label("#" + r.getId() + "  " + (renter == null ? "?"
                    : renter.getName()));
            title.getStyleClass().add("vehicle-name");
            Label car = new Label(v == null ? "?" : v.getMake() + " " + v.getModel() + " ("
                    + v.getPlateNumber() + ")");
            car.getStyleClass().add("color-name");
            Label dates = new Label(r.getStartDate() + " to " + r.getEndDate());
            dates.getStyleClass().add("vehicle-details");
            Label state = new Label(r.getStatus().name());
            state.getStyleClass().addAll("badge", returned ? "badge-returned" : "badge-ongoing");
            VBox text = new VBox(Layout.GAP_TINY, title, car, dates, state);
            if (returned) {
                Label back = new Label("Returned " + r.getReturnedOn() + "  |  "
                        + r.getMilesDriven() + " mi"
                        + finalCost(r, v, payments.get(r.getId())));
                back.getStyleClass().add("hint-label");
                back.setWrapText(true);
                text.getChildren().add(back);
            }
            HBox card = new HBox(Layout.GAP_WIDE);
            if (v != null) {
                card.getChildren().add(pictures.stage(v));
            }
            card.getChildren().add(text);
            card.setAlignment(Pos.CENTER_LEFT);
            card.getStyleClass().add("vehicle-card");
            setText(null);
            setGraphic(card);
        }
    }

    private static String finalCost(Rental r, Vehicle v, Payment paid) {
        if (paid != null) {
            String late = paid.getLateFee() > 0
                    ? String.format(Locale.US, " (late fee %.2f)", paid.getLateFee()) : "";
            return String.format(Locale.US, "  |  paid %.2f", paid.getTotal()) + late;
        }
        if (v == null || r.getReturnedOn() == null) {
            return "";
        }
        try {
            int days = RentalMath.billableDays(r.getStartDate(), r.getReturnedOn());
            return String.format(Locale.US, "  |  cost %.2f", v.computeRentalCost(days,
                    r.getMilesDriven()));
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    /** Reloads the data of this tab from the database. */
    public void refresh() {
        try {
            RentalController.Data data = controller.load();
            List<Renter> allRenters = data.renters();
            List<Vehicle> allVehicles = data.activeVehicles();
            List<Rental> allRentals = data.rentals();
            renters.clear();
            renters.putAll(data.rentersById());
            // The lists below also show rentals of vehicles that were removed later.
            vehicles.clear();
            vehicles.putAll(data.vehiclesById());
            payments.clear();
            payments.putAll(data.paymentsByRental());
            Renter keepRenter = renterBox.getValue();
            Vehicle keepVehicle = vehicleBox.getValue();
            renterBox.getItems().setAll(allRenters);
            vehicleBox.getItems().setAll(allVehicles);
            if (keepRenter != null && renters.containsKey(keepRenter.getId())) {
                renterBox.setValue(renters.get(keepRenter.getId()));
            }
            if (keepVehicle != null) {
                for (Vehicle v : allVehicles) {
                    if (v.getId() == keepVehicle.getId()) {
                        vehicleBox.setValue(v);
                        break;
                    }
                }
            }
            showChosenVehicle(vehicleBox.getValue());
            List<Rental> ongoing = new ArrayList<>();
            List<Rental> done = new ArrayList<>();
            for (Rental r : allRentals) {
                if (r.getStatus() == RentalStatus.ONGOING) {
                    ongoing.add(r);
                } else if (done.size() < 50) {
                    done.add(r);
                }
            }
            Rental keepSelected = ongoingList.getSelectionModel().getSelectedItem();
            ongoingList.getItems().setAll(ongoing);
            historyList.getItems().setAll(done);
            if (keepSelected != null) {
                for (Rental r : ongoing) {
                    if (r.getId() == keepSelected.getId()) {
                        ongoingList.getSelectionModel().select(r);
                        break;
                    }
                }
            }
        } catch (ControllerException e) {
            UiHelper.setStatus(startStatus, e.getMessage(), true);
        }
    }
}
