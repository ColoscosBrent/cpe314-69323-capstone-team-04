package fleetrent.ui;

import fleetrent.controller.ControllerException;
import fleetrent.controller.ReservationController;
import fleetrent.model.Rental;
import fleetrent.model.Renter;
import fleetrent.model.Reservation;
import fleetrent.model.ReservationStatus;
import fleetrent.model.Vehicle;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Tab 3 (owner): the reservations that renters made from their own screen. The owner hands the
 * vehicle over on the start day (it becomes a rental) or cancels the reservation.
 */
public class ReservationTab extends Tab {
    private static final double LIST_MIN_HEIGHT = 200;
    private static final double LIST_PREF_HEIGHT = 230;
    private static final Insets CONTENT_PADDING = new Insets(18);
    private static final double CONTENT_MIN_WIDTH = 640;
    private static final String ACTIVE_ONLY = "Active reservations";
    private static final String EVERYTHING = "All reservations";

    private final ReservationController controller;
    private final VehiclePictures pictures;
    private final ListView<Reservation> list = new ListView<>();
    private final ChoiceBox<String> filter = new ChoiceBox<>();
    private final Label status = new Label("Pick a reservation to hand over or cancel it.");
    private final Label countLabel = new Label("0");
    private final Map<Integer, Renter> renters = new HashMap<>();
    private final Map<Integer, Vehicle> vehicles = new HashMap<>();

    /** Creates a reservation tab. */
    public ReservationTab(ReservationController controller, VehiclePictures pictures) {
        super("Reservations");
        this.controller = controller;
        this.pictures = pictures;
        setClosable(false);
        // The chosen reservation and the load problems are part of the controller's observable
        // state.
        controller.selectedProperty().bind(list.getSelectionModel().selectedItemProperty());
        UiHelper.showProblems(controller, status);

        VBox card = new VBox(Layout.GAP_MEDIUM, buildTop(), buildHint());
        card.getChildren().addAll(buildListAndButtons());
        card.getStyleClass().add("card");
        HBox.setHgrow(card, Priority.ALWAYS);
        HBox content = new HBox(card);
        content.setPadding(CONTENT_PADDING);
        content.setMinWidth(CONTENT_MIN_WIDTH);
        setContent(UiHelper.scroll(content));
        refresh();
    }

    private HBox buildTop() {
        filter.getItems().setAll(ACTIVE_ONLY, EVERYTHING);
        filter.setValue(ACTIVE_ONLY);
        filter.valueProperty().addListener((obs, o, n) -> refresh());
        countLabel.getStyleClass().add("count-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(Layout.GAP_MEDIUM, UiHelper.title("Reservations from renters"), spacer,
                filter, countLabel);
        top.setAlignment(Pos.CENTER_LEFT);
        return top;
    }

    private static Label buildHint() {
        Label hint = new Label(
                "Renters reserve from their own screen. On the start day press Hand over: the "
                + "reservation becomes a rental (return it in the Rentals tab). Cancel frees the "
                + "days.");
        hint.getStyleClass().add("hint-label");
        hint.setWrapText(true);
        return hint;
    }

    // The list, the two buttons and the status line.
    private List<Node> buildListAndButtons() {
        list.getStyleClass().add("vehicle-list");
        list.setCellFactory(l -> new ReservationCell());
        VBox.setVgrow(list, Priority.ALWAYS);
        list.setMinHeight(LIST_MIN_HEIGHT);
        list.setPrefHeight(LIST_PREF_HEIGHT);
        Button handOver = new Button("Hand over (start rental)");
        handOver.getStyleClass().add("primary-button");
        Button cancel = new Button("Cancel reservation");
        cancel.getStyleClass().add("danger-button");
        status.getStyleClass().add("status-label");
        status.setWrapText(true);
        handOver.setOnAction(e -> handOver());
        cancel.setOnAction(e -> cancel());
        return List.of(list, new HBox(Layout.GAP_MEDIUM, handOver, cancel), status);
    }

    private void handOver() {
        Reservation r = list.getSelectionModel().getSelectedItem();
        if (r == null) {
            UiHelper.setStatus(status, "Pick a reservation first.", true);
            return;
        }
        try {
            Rental rental = controller.handOver(r.getId());
            UiHelper.setStatus(status, "Handed over. Rental #" + rental.getId() + " started for "
                    + nameOf(r.getRenterId()) + ".", false);
            refresh();
        } catch (ControllerException ex) {
            UiHelper.setStatus(status, ex.getMessage(), true);
        }
    }

    private void cancel() {
        Reservation r = list.getSelectionModel().getSelectedItem();
        if (r == null) {
            UiHelper.setStatus(status, "Pick a reservation first.", true);
            return;
        }
        try {
            controller.cancel(r.getId());
            UiHelper.setStatus(status, "Reservation #" + r.getId() + " was cancelled.", false);
            refresh();
        } catch (ControllerException ex) {
            UiHelper.setStatus(status, ex.getMessage(), true);
        }
    }

    private String nameOf(int renterId) {
        Renter r = renters.get(renterId);
        return r == null ? "?" : r.getName();
    }

    /** Reloads the data of this tab from the database. */
    public void refresh() {
        controller.refresh(this::show);
    }

    private void show(ReservationController.Data data) {
        renters.clear();
        renters.putAll(data.renters());
        vehicles.clear();
        vehicles.putAll(data.vehicles());
        Reservation keep = list.getSelectionModel().getSelectedItem();
        List<Reservation> shown = new ArrayList<>();
        for (Reservation r : data.reservations()) {
            if (EVERYTHING.equals(filter.getValue())
                    || r.getStatus() == ReservationStatus.ACTIVE) {
                shown.add(r);
            }
        }
        list.getItems().setAll(shown);
        if (keep != null) {
            for (Reservation r : shown) {
                if (r.getId() == keep.getId()) {
                    list.getSelectionModel().select(r);
                    break;
                }
            }
        }
        countLabel.setText(String.valueOf(shown.size()));
    }

    private class ReservationCell extends ListCell<Reservation> {
        @Override
        protected void updateItem(Reservation r, boolean empty) {
            super.updateItem(r, empty);
            if (empty || r == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            Renter renter = renters.get(r.getRenterId());
            Vehicle v = vehicles.get(r.getVehicleId());
            VBox text = textColumn(r, renter, v);
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

        // The lines of text of one reservation: who, what, when, status, note.
        private VBox textColumn(Reservation r, Renter renter, Vehicle v) {
            Label title = new Label("#" + r.getId() + "  " + (renter == null ? "?"
                    : renter.getName()));
            title.getStyleClass().add("vehicle-name");
            Label contact = new Label(renter == null ? "" : renter.getContact());
            contact.getStyleClass().add("color-name");
            Label car = new Label(v == null ? "?" : v.displayName() + " (" + v.getPlateNumber()
                    + ")");
            car.getStyleClass().add("color-name");
            Label dates = new Label(Format.date(r.getStartDate()) + " to "
                    + Format.date(r.getEndDate()));
            dates.getStyleClass().add("vehicle-details");
            VBox text = new VBox(Layout.GAP_TINY, title, contact, car, dates, badgeRow(r));
            if (r.getNote() != null && !r.getNote().isEmpty()) {
                Label note = new Label(r.getNote());
                note.getStyleClass().add("hint-label");
                note.setWrapText(true);
                text.getChildren().add(note);
            }
            return text;
        }

        // The status badge plus the "late" or "ready" hint for an active reservation.
        private HBox badgeRow(Reservation r) {
            Label badge = new Label(r.getStatus().name());
            badge.getStyleClass().addAll("badge", "badge-" + r.getStatus().name()
                    .toLowerCase(Locale.ROOT));
            HBox badges = new HBox(Layout.GAP_SMALL, badge);
            if (r.getStatus() == ReservationStatus.ACTIVE) {
                LocalDate today = LocalDate.now();
                if (r.getEndDate().isBefore(today)) {
                    Label late = new Label("ENDED - CANCEL IT");
                    late.getStyleClass().addAll("badge", "badge-late");
                    badges.getChildren().add(late);
                } else if (!r.getStartDate().isAfter(today)) {
                    Label ready = new Label("READY TO HAND OVER");
                    ready.getStyleClass().addAll("badge", "badge-available");
                    badges.getChildren().add(ready);
                }
            }
            return badges;
        }
    }
}
