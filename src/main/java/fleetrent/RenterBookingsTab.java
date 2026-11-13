package fleetrent;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Renter, tab 2: the renter's own reservations and rentals, with the price. A renter sees only
 * their own.
 */
public class RenterBookingsTab extends Tab {
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final Database db;
    private final BookingService booking;
    private final VehiclePictures pictures;
    private final int renterId;
    private final Runnable onChanged;
    private final ListView<Object> current = new ListView<>();
    private final ListView<Object> history = new ListView<>();
    private final Label status = new Label("Your reservations and rentals show here.");
    private final Map<Integer, Vehicle> vehicles = new HashMap<>();

    /** Creates a renter bookings tab. */
    public RenterBookingsTab(Database db, BookingService booking, VehiclePictures pictures, int renterId,
                             Runnable onChanged) {
        super("My bookings");
        this.db = db;
        this.booking = booking;
        this.pictures = pictures;
        this.renterId = renterId;
        this.onChanged = onChanged;
        setClosable(false);

        current.getStyleClass().add("vehicle-list");
        current.setCellFactory(l -> new BookingCell());
        current.setMinHeight(180);
        current.setPrefHeight(240);
        history.getStyleClass().add("vehicle-list");
        history.setCellFactory(l -> new BookingCell());
        history.setMinHeight(160);
        history.setPrefHeight(200);
        history.setFocusTraversable(false);
        VBox.setVgrow(current, Priority.ALWAYS);

        Button cancel = new Button("Cancel selected reservation");
        cancel.getStyleClass().add("danger-button");
        cancel.setOnAction(e -> cancelSelected());
        status.getStyleClass().add("status-label");
        status.setWrapText(true);
        Label hint = new Label("Return the car to the shop. The owner closes the rental, and the final price "
                + "(with any extra miles) shows here under Past bookings.");
        hint.getStyleClass().add("hint-label");
        hint.setWrapText(true);

        VBox left = new VBox(10, UiHelper.title("Current and upcoming"), current, cancel, status, hint);
        left.getStyleClass().add("card");
        VBox right = new VBox(10, UiHelper.title("Past bookings"), history);
        right.getStyleClass().add("card");
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        HBox content = new HBox(16, left, right);
        content.setPadding(new Insets(16, 18, 16, 18));
        content.setMinWidth(960);
        setContent(UiHelper.scroll(content));
        refresh();
    }

    private void cancelSelected() {
        Object picked = current.getSelectionModel().getSelectedItem();
        if (!(picked instanceof Reservation)) {
            UiHelper.setStatus(status, "Pick one of your reservations first. A rental that already started is "
                    + "closed by the owner when you return the car.", true);
            return;
        }
        try {
            booking.cancelReservation(renterId, ((Reservation) picked).getId());
            UiHelper.setStatus(status, "Reservation #" + ((Reservation) picked).getId() + " was cancelled.", false);
            refresh();
            onChanged.run();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            UiHelper.setStatus(status, ex.getMessage(), true);
        } catch (SQLException ex) {
            UiHelper.setStatus(status, ErrorMessages.forDatabase("cancelling the reservation", ex), true);
        }
    }

    /** Reloads the data of this tab from the database. */
    public void refresh() {
        try {
            vehicles.clear();
            for (Vehicle v : db.getAllVehiclesIncludingRemoved()) {
                vehicles.put(v.getId(), v);
            }
            List<Object> now = new ArrayList<>();
            List<Object> past = new ArrayList<>();
            for (Reservation r : booking.reservationsOf(renterId)) {
                if (r.getStatus() == ReservationStatus.ACTIVE) {
                    now.add(r);
                } else if (r.getStatus() == ReservationStatus.CANCELLED) {
                    past.add(r);
                }
            }
            for (Rental r : booking.rentalsOf(renterId)) {
                (r.getStatus() == RentalStatus.ONGOING ? now : past).add(r);
            }
            now.sort(Comparator.comparing(BookingCell::startOf));
            past.sort(Comparator.comparing(BookingCell::startOf).reversed());
            Object keep = current.getSelectionModel().getSelectedItem();
            current.getItems().setAll(now);
            history.getItems().setAll(past);
            if (keep != null) {
                for (Object o : now) {
                    if (o.getClass() == keep.getClass() && BookingCell.idOf(o) == BookingCell.idOf(keep)) {
                        current.getSelectionModel().select(o);
                        break;
                    }
                }
            }
        } catch (SQLException e) {
            UiHelper.setStatus(status, ErrorMessages.forDatabase("loading your bookings", e), true);
        }
    }

    private static String money(double amount) {
        return String.format(Locale.US, "%.2f", amount);
    }

    private class BookingCell extends ListCell<Object> {
        static LocalDate startOf(Object o) {
            return o instanceof Reservation ? ((Reservation) o).getStartDate() : ((Rental) o).getStartDate();
        }

        static int idOf(Object o) {
            return o instanceof Reservation ? ((Reservation) o).getId() : ((Rental) o).getId();
        }

        @Override
        protected void updateItem(Object item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            int vehicleId = item instanceof Reservation ? ((Reservation) item).getVehicleId() : ((Rental) item).getVehicleId();
            Vehicle v = vehicles.get(vehicleId);
            LocalDate start = startOf(item);
            LocalDate end = item instanceof Reservation ? ((Reservation) item).getEndDate() : ((Rental) item).getEndDate();
            Label title = new Label((item instanceof Reservation ? "Reservation #" : "Rental #") + idOf(item) + "  "
                    + (v == null ? "?" : v.displayName()));
            title.getStyleClass().add("vehicle-name");
            title.setWrapText(true);
            Label dates = new Label(start.format(SHORT) + " to " + end.format(SHORT));
            dates.getStyleClass().add("vehicle-details");
            Label badge = new Label();
            VBox text = new VBox(4, title, dates, badge);
            if (item instanceof Reservation) {
                Reservation r = (Reservation) item;
                badge.setText(r.getStatus().name());
                badge.getStyleClass().addAll("badge", "badge-" + r.getStatus().name().toLowerCase(Locale.ROOT));
                if (v != null && r.getStatus() == ReservationStatus.ACTIVE) {
                    text.getChildren().add(hint("Price for these days: " + money(BookingService.estimate(v, start, end))
                            + "  (+ extra miles)"));
                }
                if (r.getStatus() == ReservationStatus.CANCELLED && r.getNote() != null) {
                    text.getChildren().add(hint(r.getNote()));
                }
            } else {
                Rental r = (Rental) item;
                if (r.getStatus() == RentalStatus.ONGOING) {
                    boolean late = end.isBefore(LocalDate.now());
                    badge.setText(late ? "OVERDUE - RETURN IT" : "RENTED NOW");
                    badge.getStyleClass().addAll("badge", late ? "badge-late" : "badge-rented");
                    if (v != null) {
                        text.getChildren().add(hint("Price for these days: " + money(BookingService.estimate(v, start, end))
                                + "  (+ extra miles)"));
                    }
                } else {
                    badge.setText("RETURNED");
                    badge.getStyleClass().addAll("badge", "badge-converted");
                    String cost = "";
                    if (v != null && r.getReturnedOn() != null) {
                        int days = RentalMath.billableDays(r.getStartDate(), r.getReturnedOn());
                        cost = "  |  final price " + money(v.computeRentalCost(days, r.getMilesDriven()));
                    }
                    text.getChildren().add(hint("Returned " + r.getReturnedOn() + "  |  " + r.getMilesDriven() + " mi" + cost));
                }
            }
            HBox card = new HBox(12);
            if (v != null) {
                card.getChildren().add(pictures.stage(v));
            }
            card.getChildren().add(text);
            card.setAlignment(Pos.CENTER_LEFT);
            card.getStyleClass().add("vehicle-card");
            setText(null);
            setGraphic(card);
        }

        private Label hint(String text) {
            Label l = new Label(text);
            l.getStyleClass().add("hint-label");
            l.setWrapText(true);
            return l;
        }
    }
}
