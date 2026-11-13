package fleetrent;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/**
 * Renter, tab 1: the cars. The renter picks the days and sees every car of the fleet with its
 * picture, make, model, daily rent, the extra-mile fee and whether it is free on those days. A
 * click on a car shows its details and the days it is busy (never who has it), and the Rent,
 * Reserve and Undo buttons.
 */
public class RenterBrowseTab extends Tab {
    private static final String ALL_TYPES = "All types";
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final BookingService booking;
    private final VehiclePictures pictures;
    private final int renterId;
    private final Runnable onBookingChanged;

    private final DatePicker startPicker = new DatePicker();
    private final DatePicker endPicker = new DatePicker();
    private final ChoiceBox<String> typeFilter = new ChoiceBox<>();
    private final CheckBox onlyFree = new CheckBox("Show only cars that are free on these days");
    private final FlowPane cards = new FlowPane(14, 14);
    private final Label freeNumber = new Label("0");
    private final Label totalNumber = new Label("0");
    private final Label dateNote = new Label();
    private final Label listNote = new Label();
    private final VBox detail = new VBox(10);
    private final Label bookStatus = new Label();
    private final Button rentButton = new Button("Rent today");
    private final Button reserveButton = new Button("Reserve these days");
    private final Button undoButton = new Button("Undo last");

    private List<BookingService.Offer> offers = new ArrayList<>();
    private Integer selectedId;
    private boolean loading;

    /** Creates a renter browse tab. */
    public RenterBrowseTab(BookingService booking, VehiclePictures pictures, int renterId, Runnable onBookingChanged) {
        super("Cars");
        this.booking = booking;
        this.pictures = pictures;
        this.renterId = renterId;
        this.onBookingChanged = onBookingChanged;
        setClosable(false);

        LocalDate today = booking.today();
        startPicker.setValue(today);
        endPicker.setValue(today.plusDays(2));
        startPicker.setEditable(false);
        endPicker.setEditable(false);
        startPicker.setPrefWidth(250);
        endPicker.setPrefWidth(250);
        startPicker.setDayCellFactory(p -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.isBefore(booking.today())
                        || date.isAfter(booking.today().plusDays(BookingService.MAX_DAYS_AHEAD)));
            }
        });
        endPicker.setDayCellFactory(p -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                LocalDate start = startPicker.getValue();
                setDisable(empty || date.isBefore(start == null ? booking.today() : start)
                        || date.isAfter((start == null ? booking.today() : start).plusDays(BookingService.MAX_DAYS)));
            }
        });
        startPicker.valueProperty().addListener((obs, o, n) -> {
            if (n != null && (endPicker.getValue() == null || endPicker.getValue().isBefore(n))) {
                endPicker.setValue(n);
            }
            if (n != null && endPicker.getValue() != null
                    && endPicker.getValue().isAfter(n.plusDays(BookingService.MAX_DAYS))) {
                endPicker.setValue(n.plusDays(BookingService.MAX_DAYS));
            }
            refresh();
        });
        endPicker.valueProperty().addListener((obs, o, n) -> refresh());
        typeFilter.getItems().add(ALL_TYPES);
        typeFilter.getItems().addAll(VehicleTypes.ALL);
        typeFilter.setValue(ALL_TYPES);
        typeFilter.setPrefWidth(250);
        typeFilter.valueProperty().addListener((obs, o, n) -> refresh());
        onlyFree.setSelected(false);
        onlyFree.setWrapText(true);
        onlyFree.selectedProperty().addListener((obs, o, n) -> refresh());

        freeNumber.getStyleClass().add("stat-number");
        totalNumber.getStyleClass().add("stat-number");
        HBox stats = new HBox(10, tile(freeNumber, "available"), tile(totalNumber, "in the fleet"));
        dateNote.getStyleClass().add("hint-label");
        dateNote.setWrapText(true);
        dateNote.setMaxWidth(230);
        Label rule = new Label("Starting today? Press Rent. Starting on a later day? Press Reserve (up to "
                + BookingService.MAX_DAYS_AHEAD + " days ahead, up to " + BookingService.MAX_DAYS + " days long). "
                + "The start day and the last day are both held for you.");
        rule.getStyleClass().add("hint-label");
        rule.setWrapText(true);
        rule.setMaxWidth(230);
        VBox filterCard = new VBox(10, UiHelper.title("Your dates"), labeled("First day", startPicker),
                labeled("Last day", endPicker), dateNote, labeled("Type", typeFilter), onlyFree, stats, rule);
        filterCard.getStyleClass().add("card");
        filterCard.setPrefWidth(270);
        filterCard.setMinWidth(270);
        filterCard.setMaxWidth(270);

        cards.setPadding(new Insets(4));
        cards.setAlignment(Pos.TOP_LEFT);
        listNote.getStyleClass().add("status-label");
        listNote.setWrapText(true);
        VBox cardsBox = new VBox(10, cards, listNote);
        ScrollPane cardScroll = new ScrollPane(cardsBox);
        cardScroll.setFitToWidth(true);
        cardScroll.getStyleClass().add("page-scroll");
        cardScroll.setFocusTraversable(false);
        HBox.setHgrow(cardScroll, Priority.ALWAYS);
        cardScroll.setMinWidth(300);
        cardScroll.setPrefViewportWidth(300);

        detail.getStyleClass().add("card");
        detail.setPrefWidth(350);
        detail.setMinWidth(350);
        detail.setMaxWidth(350);
        for (Button b : new Button[] {rentButton, reserveButton, undoButton}) {
            b.setMinWidth(Region.USE_PREF_SIZE);
        }
        rentButton.getStyleClass().add("primary-button");
        reserveButton.getStyleClass().add("primary-button");
        undoButton.getStyleClass().add("secondary-button");
        bookStatus.getStyleClass().add("status-label");
        bookStatus.setWrapText(true);
        bookStatus.setMaxWidth(310);
        bookStatus.setMinHeight(Region.USE_PREF_SIZE);
        rentButton.setOnAction(e -> rent());
        reserveButton.setOnAction(e -> reserve());
        undoButton.setOnAction(e -> undo());

        HBox content = new HBox(16, filterCard, cardScroll, detail);
        content.setPadding(new Insets(16, 18, 16, 18));
        content.setMinWidth(990);
        setContent(UiHelper.scroll(content));
        refresh();
    }

    private static VBox labeled(String text, Node control) {
        Label l = new Label(text);
        l.getStyleClass().add("field-label");
        return new VBox(3, l, control);
    }

    private static VBox tile(Label number, String caption) {
        Label c = new Label(caption);
        c.getStyleClass().add("hint-label");
        VBox box = new VBox(0, number, c);
        box.getStyleClass().add("stat-tile");
        return box;
    }

    private static String money(double amount) {
        return String.format(Locale.US, "%.2f", amount);
    }

    // ---------- loading the cars ----------

    /** Reloads the data of this tab from the database. */
    public void refresh() {
        if (loading) {
            return;
        }
        LocalDate start = startPicker.getValue();
        LocalDate end = endPicker.getValue();
        if (start == null || end == null) {
            return;
        }
        loading = true;
        try {
            if (end.isBefore(start)) {
                endPicker.setValue(start);
                end = start;
            }
            offers = booking.browse(start, end);
            int free = 0;
            for (BookingService.Offer o : offers) {
                if (o.availability() == BookingService.Availability.AVAILABLE) {
                    free++;
                }
            }
            freeNumber.setText(String.valueOf(free));
            totalNumber.setText(String.valueOf(offers.size()));
            int days = RentalMath.billableDays(start, end);
            dateNote.setText(start.format(SHORT) + " to " + end.format(SHORT) + " = " + days + " day" + (days == 1 ? "" : "s")
                    + (start.equals(booking.today()) ? "  (starts today)" : ""));
            showCards(start, end);
            if (selectedId != null && offers.stream().noneMatch(o -> o.vehicle().getId() == selectedId)) {
                selectedId = null;
                UiHelper.setStatus(bookStatus, "That car is not in the fleet anymore.", true);
            }
            showDetail();
        } catch (SQLException e) {
            UiHelper.setStatus(listNote, ErrorMessages.forDatabase("loading the cars", e), true);
        } catch (IllegalArgumentException e) {
            UiHelper.setStatus(listNote, e.getMessage(), true);
        } finally {
            loading = false;
        }
    }

    private BookingService.Offer selectedOffer() {
        if (selectedId == null) {
            return null;
        }
        for (BookingService.Offer o : offers) {
            if (o.vehicle().getId() == selectedId) {
                return o;
            }
        }
        return null;
    }

    private void showCards(LocalDate start, LocalDate end) {
        cards.getChildren().clear();
        int shown = 0;
        for (BookingService.Offer offer : offers) {
            Vehicle v = offer.vehicle();
            boolean free = offer.availability() == BookingService.Availability.AVAILABLE;
            if (!ALL_TYPES.equals(typeFilter.getValue()) && !v.getType().equals(typeFilter.getValue())) {
                continue;
            }
            if (onlyFree.isSelected() && !free) {
                continue;
            }
            cards.getChildren().add(card(offer, start, end));
            shown++;
        }
        if (offers.isEmpty()) {
            UiHelper.setStatus(listNote, "There are no cars yet. The owner has not added any vehicle.", false);
        } else if (shown == 0) {
            UiHelper.setStatus(listNote, "No car matches this filter. Try another type or untick the box.", false);
        } else {
            listNote.setText("");
        }
    }

    private Node card(BookingService.Offer offer, LocalDate start, LocalDate end) {
        Vehicle v = offer.vehicle();
        Label name = new Label(v.displayName());
        name.getStyleClass().add("vehicle-name");
        name.setWrapText(true);
        name.setMaxWidth(220);
        name.setMinHeight(Region.USE_PREF_SIZE);
        Label type = new Label(v.getType());
        type.getStyleClass().addAll("badge", "badge-" + v.getType().toLowerCase(Locale.ROOT));
        Label state = statusBadge(offer);
        Circle chip = new Circle(6, ColorNames.toColor(v.getColor()));
        chip.setStroke(Color.web("#f5b800"));
        Label color = new Label(v.getColor() == null ? "" : v.getColor());
        color.getStyleClass().add("color-name");
        HBox row = new HBox(6, chip, color, type);
        row.setAlignment(Pos.CENTER_LEFT);
        Label rate = new Label(money(v.getDailyRate()) + " / day");
        rate.getStyleClass().add("price-big");
        Label fee = new Label("+ " + money(v.getFeePerExtraMile()) + " per extra mile after "
                + v.getFreeMilesPerDay() + " free miles a day");
        fee.getStyleClass().add("fee-text");
        fee.setMaxWidth(220);
        Label total = new Label("Your dates: " + money(BookingService.estimate(v, start, end)));
        total.getStyleClass().add("hint-label");
        VBox box = new VBox(6, pictures.stage(v, 232, 132), name, state, row, rate, fee, total);
        box.getStyleClass().add("offer-card");
        box.setPrefWidth(256);
        box.setMinWidth(256);
        box.setMaxWidth(256);
        box.setMinHeight(330);
        if (offer.availability() != BookingService.Availability.AVAILABLE) {
            box.getStyleClass().add("offer-dim");
        }
        if (selectedId != null && selectedId == v.getId()) {
            box.getStyleClass().add("offer-card-selected");
        }
        box.setOnMouseClicked(e -> {
            selectedId = v.getId();
            bookStatus.setText("");
            for (Node n : cards.getChildren()) {
                n.getStyleClass().remove("offer-card-selected");
            }
            box.getStyleClass().add("offer-card-selected");
            showDetail();
        });
        return box;
    }

    private Label statusBadge(BookingService.Offer offer) {
        Label l;
        switch (offer.availability()) {
            case AVAILABLE:
                l = new Label("FREE ON YOUR DAYS");
                l.getStyleClass().addAll("badge", "badge-available");
                break;
            case RENTED:
                l = new Label("RENTED" + (offer.rentedUntil() == null ? "" : " until " + offer.rentedUntil().format(SHORT)));
                l.getStyleClass().addAll("badge", "badge-rented");
                break;
            default:
                l = new Label("RESERVED ON YOUR DAYS");
                l.getStyleClass().addAll("badge", "badge-reserved");
                break;
        }
        return l;
    }

    // ---------- the detail and the buttons ----------

    private void showDetail() {
        BookingService.Offer offer = selectedOffer();
        undoButton.setDisable(!booking.canUndo());
        if (offer == null) {
            Label empty = new Label("Click a car to see its details, the days it is busy, and to book it.");
            empty.getStyleClass().add("hint-label");
            empty.setWrapText(true);
            detail.getChildren().setAll(UiHelper.title("Your booking"), empty, new HBox(10, undoButton), bookStatus);
            return;
        }
        Vehicle v = offer.vehicle();
        LocalDate start = startPicker.getValue();
        LocalDate end = endPicker.getValue();
        Label name = new Label(v.displayName());
        name.getStyleClass().add("vehicle-name");
        name.setWrapText(true);
        Label meta = new Label(v.getType() + "  |  " + (v.getColor() == null ? "" : v.getColor())
                + (v.getCategoryPath() == null ? "" : "  |  " + v.getCategoryPath()));
        meta.getStyleClass().add("color-name");
        meta.setWrapText(true);
        Label rate = new Label(money(v.getDailyRate()) + " per day");
        rate.getStyleClass().add("price-big");
        Label fees = new Label("Extra fees: " + v.feeSummary() + ". Extra miles are counted when the car is returned.");
        fees.getStyleClass().add("fee-text");
        fees.setMaxWidth(310);

        VBox busyBox = new VBox(3);
        Label busyTitle = new Label("Days this car is not free");
        busyTitle.getStyleClass().add("field-label");
        busyBox.getChildren().add(busyTitle);
        if (offer.busy().isEmpty()) {
            Label none = new Label("None. It is free in the coming days.");
            none.getStyleClass().add("hint-label");
            busyBox.getChildren().add(none);
        } else {
            for (BusySpan s : offer.busy()) {
                Label l = new Label((s.kind().equals("RENTED") ? "Rented   " : "Reserved ") + s.start().format(SHORT)
                        + " to " + s.end().format(SHORT));
                l.getStyleClass().add("vehicle-details");
                busyBox.getChildren().add(l);
            }
        }

        int days = RentalMath.billableDays(start, end);
        Label mine = new Label("Your dates: " + start.format(SHORT) + " to " + end.format(SHORT) + " (" + days + " day"
                + (days == 1 ? "" : "s") + ")");
        mine.getStyleClass().add("vehicle-details");
        mine.setWrapText(true);
        Label estimate = new Label("Rent for these days: " + money(BookingService.estimate(v, start, end)));
        estimate.getStyleClass().add("vehicle-price");
        estimate.setWrapText(true);
        Label why = new Label();
        why.getStyleClass().add("hint-label");
        why.setWrapText(true);
        why.setMaxWidth(310);
        why.setMinHeight(Region.USE_PREF_SIZE);

        boolean free = offer.availability() == BookingService.Availability.AVAILABLE;
        boolean startsToday = start.equals(booking.today());
        rentButton.setDisable(!(free && startsToday));
        reserveButton.setDisable(!(free && !startsToday));
        if (!free) {
            why.setText("This car is not free on your days. Pick other days, or another car.");
        } else if (startsToday) {
            why.setText("Your first day is today, so you can press Rent. To book later days, change the first day.");
        } else {
            why.setText("Your first day is later, so you can press Reserve. To take the car today, set the first day to today.");
        }
        FlowPane buttons = new FlowPane(8, 8, rentButton, reserveButton, undoButton);
        buttons.setPrefWrapLength(330);
        detail.getChildren().setAll(UiHelper.title("Your booking"), pictures.stage(v, 330, 190), name, meta, rate, fees,
                busyBox, mine, estimate, why, buttons, bookStatus);
    }

    private void rent() {
        BookingService.Offer offer = selectedOffer();
        if (offer == null) {
            UiHelper.setStatus(bookStatus, "Click a car first.", true);
            return;
        }
        try {
            Rental r = booking.rentNow(renterId, offer.vehicle().getId(), endPicker.getValue());
            afterChange("Rental #" + r.getId() + " started: " + offer.vehicle().displayName() + " until "
                    + r.getEndDate().format(SHORT) + ". Pick it up at the shop. The owner closes the rental when you return it.", false);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            afterChange(ex.getMessage(), true);
        } catch (SQLException ex) {
            afterChange(ErrorMessages.forDatabase("renting the car", ex), true);
        }
    }

    private void reserve() {
        BookingService.Offer offer = selectedOffer();
        if (offer == null) {
            UiHelper.setStatus(bookStatus, "Click a car first.", true);
            return;
        }
        try {
            Reservation r = booking.reserve(renterId, offer.vehicle().getId(), startPicker.getValue(), endPicker.getValue());
            afterChange("Reservation #" + r.getId() + " saved: " + offer.vehicle().displayName() + " from "
                    + r.getStartDate().format(SHORT) + " to " + r.getEndDate().format(SHORT)
                    + ". You can see it in My bookings.", false);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            afterChange(ex.getMessage(), true);
        } catch (SQLException ex) {
            afterChange(ErrorMessages.forDatabase("reserving the car", ex), true);
        }
    }

    private void undo() {
        try {
            String message = booking.undoLast(renterId);
            afterChange(message == null ? "Nothing to undo." : message, false);
        } catch (IllegalStateException ex) {
            afterChange(ex.getMessage(), true);
        } catch (SQLException ex) {
            afterChange(ErrorMessages.forDatabase("undoing", ex), true);
        }
    }

    private void afterChange(String message, boolean problem) {
        refresh();
        UiHelper.setStatus(bookStatus, message, problem);
        onBookingChanged.run();
    }
}
