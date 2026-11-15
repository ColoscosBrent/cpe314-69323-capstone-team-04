package fleetrent.controller;

import fleetrent.dao.Database;
import fleetrent.model.Offer;
import fleetrent.model.Rental;
import fleetrent.model.Renter;
import fleetrent.model.Reservation;
import fleetrent.model.Vehicle;
import fleetrent.service.BookingService;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Everything the renter's screens (Cars, My bookings, My account) ask for: the cars for a span of
 * days, rent, reserve, cancel, undo, and the renter's own bookings. A renter's screens get only
 * this controller, so they cannot reach another renter's data.
 */
public class RenterController extends ObservableController<Offer> {
    private static final String SLOT_OFFERS = "offers";
    private static final String SLOT_BOOKINGS = "bookings";
    /** The longest booking, in days. */
    public static final int MAX_DAYS = BookingService.MAX_DAYS;
    /** How far ahead a booking may start, in days. */
    public static final int MAX_DAYS_AHEAD = BookingService.MAX_DAYS_AHEAD;

    /**
     * The renter's own bookings.
     *
     * @param vehicles     every vehicle ever added, by id (a removed vehicle still shows in
     * history)
     * @param reservations the renter's reservations
     * @param rentals      the renter's rentals
     */
    public record Bookings(Map<Integer, Vehicle> vehicles, List<Reservation> reservations,
            List<Rental> rentals) {
    }

    /**
     * The renter's own waiting lines.
     *
     * @param vehicles every vehicle ever added, by id
     * @param places   the place in line (1 is next) for each vehicle the renter waits for
     */
    public record WaitingPlaces(Map<Integer, Vehicle> vehicles, Map<Integer, Integer> places) {
    }

    /**
     * Everything the My bookings tab shows.
     *
     * @param places   the waiting lines of the renter
     * @param bookings the reservations and rentals of the renter
     */
    public record MyBookings(WaitingPlaces places, Bookings bookings) {
    }

    private final Database db;
    private final BookingService booking;
    private final Session session;

    /**
     * Creates the controller. Every method that works on a renter's data takes the renter from the
     * signed-in session, so a screen cannot ask for another renter's data.
     */
    public RenterController(Database db, BookingService booking, Session session) {
        this.db = db;
        this.booking = booking;
        this.session = session;
    }

    /** What the price of a vehicle is for the chosen days. */
    public static double estimate(Vehicle vehicle, LocalDate start, LocalDate end) {
        return BookingService.estimate(vehicle, start, end);
    }

    /** Returns the date the app treats as today. */
    public LocalDate today() {
        return booking.today();
    }

    /** Forgets what can be undone. It runs when someone signs in or out of the window. */
    public void clearUndo() {
        booking.clearUndo();
    }

    /**
     * Reloads the cars for the chosen days in the background. The offers go into items() and show
     * gets them too. A problem is put in the status property.
     */
    public void refreshOffers(LocalDate start, LocalDate end, Consumer<List<Offer>> show) {
        runTask(SLOT_OFFERS, () -> browse(start, end), offers -> {
            items().setAll(offers);
            show.accept(offers);
        }, null);
    }

    /**
     * Reloads the renter's waiting lines, reservations and rentals in the background. A problem
     * goes to failed.
     */
    public void refreshBookings(Consumer<MyBookings> show, Consumer<String> failed) {
        runTask(SLOT_BOOKINGS, () -> new MyBookings(waitingPlaces(), bookings()), show, failed);
    }

    /** Returns every vehicle in the fleet with its availability for the chosen days. */
    public List<Offer> browse(LocalDate start, LocalDate end) throws ControllerException {
        session.requireRenterId("see the cars");
        return Guard.database("loading the cars", () -> booking.browse(start, end));
    }

    /** Rents a vehicle from today until the end date. */
    public Rental rentNow(int vehicleId, LocalDate end) throws ControllerException {
        int renterId = session.requireRenterId("rent a car");
        return Guard.database("renting the car", () -> booking.rentNow(renterId, vehicleId, end));
    }

    /** Reserves a vehicle for later days. */
    public Reservation reserve(int vehicleId, LocalDate start, LocalDate end)
            throws ControllerException {
        int renterId = session.requireRenterId("reserve a car");
        return Guard.database("reserving the car",
                () -> booking.reserve(renterId, vehicleId, start, end));
    }

    /** Cancels one of the renter's own reservations. */
    public void cancelReservation(int reservationId) throws ControllerException {
        int renterId = session.requireRenterId("cancel a reservation");
        Guard.database("cancelling the reservation",
                () -> booking.cancelReservation(renterId, reservationId));
    }

    /** Tells whether there is something to undo. */
    public boolean canUndo() {
        return booking.canUndo();
    }

    /** Takes back the last thing the renter did and returns a message about it. */
    public String undoLast() throws ControllerException {
        int renterId = session.requireRenterId("undo a booking");
        return Guard.database("undoing", () -> booking.undoLast(renterId));
    }

    /** Puts the renter in the waiting line of a rented-out car. Returns the place in line. */
    public int joinWaitingList(int vehicleId) throws ControllerException {
        int renterId = session.requireRenterId("join a waiting list");
        return Guard.database("joining the waiting list",
                () -> booking.joinWaitingList(renterId, vehicleId));
    }

    /** Takes the renter out of the waiting line of a car. */
    public void leaveWaitingList(int vehicleId) throws ControllerException {
        int renterId = session.requireRenterId("leave a waiting list");
        Guard.database("leaving the waiting list",
                () -> booking.leaveWaitingList(renterId, vehicleId));
    }

    /** Returns the waiting lines the renter is in, with the place in each line. */
    public WaitingPlaces waitingPlaces() throws ControllerException {
        int renterId = session.requireRenterId("see the waiting lists");
        return Guard.database("loading your waiting lists", () -> {
            Map<Integer, Vehicle> vehicles = new HashMap<>();
            for (Vehicle v : db.getAllVehiclesIncludingRemoved()) {
                vehicles.put(v.getId(), v);
            }
            return new WaitingPlaces(vehicles, booking.waitingPlacesOf(renterId));
        });
    }

    /** Returns the reservations and rentals of the signed-in renter. */
    public Bookings bookings() throws ControllerException {
        int renterId = session.requireRenterId("see your bookings");
        return Guard.database("loading your bookings", () -> {
            Map<Integer, Vehicle> vehicles = new HashMap<>();
            for (Vehicle v : db.getAllVehiclesIncludingRemoved()) {
                vehicles.put(v.getId(), v);
            }
            return new Bookings(vehicles, booking.reservationsOf(renterId),
                    booking.rentalsOf(renterId));
        });
    }

    /** Returns the renter record of the signed-in renter, or null. */
    public Renter myDetails() throws ControllerException {
        int renterId = session.requireRenterId("see your details");
        return Guard.database("loading your details", () -> db.getRenterById(renterId));
    }
}
