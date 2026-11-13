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

/**
 * Everything the renter's screens (Cars, My bookings, My account) ask for: the cars for a span of
 * days, rent, reserve, cancel, undo, and the renter's own bookings. A renter's screens get only
 * this controller, so they cannot reach another renter's data.
 */
public class RenterController {
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

    private final Database db;
    private final BookingService booking;

    /** Creates the controller. */
    public RenterController(Database db, BookingService booking) {
        this.db = db;
        this.booking = booking;
    }

    /** What the price of a vehicle is for the chosen days. */
    public static double estimate(Vehicle vehicle, LocalDate start, LocalDate end) {
        return BookingService.estimate(vehicle, start, end);
    }

    /** Returns the date the app treats as today. */
    public LocalDate today() {
        return booking.today();
    }

    /** Returns every vehicle in the fleet with its availability for the chosen days. */
    public List<Offer> browse(LocalDate start, LocalDate end) throws ControllerException {
        return Guard.database("loading the cars", () -> booking.browse(start, end));
    }

    /** Rents a vehicle from today until the end date. */
    public Rental rentNow(int renterId, int vehicleId, LocalDate end) throws ControllerException {
        return Guard.database("renting the car", () -> booking.rentNow(renterId, vehicleId, end));
    }

    /** Reserves a vehicle for later days. */
    public Reservation reserve(int renterId, int vehicleId, LocalDate start, LocalDate end)
            throws ControllerException {
        return Guard.database("reserving the car",
                () -> booking.reserve(renterId, vehicleId, start, end));
    }

    /** Cancels one of the renter's own reservations. */
    public void cancelReservation(int renterId, int reservationId) throws ControllerException {
        Guard.database("cancelling the reservation",
                () -> booking.cancelReservation(renterId, reservationId));
    }

    /** Tells whether there is something to undo. */
    public boolean canUndo() {
        return booking.canUndo();
    }

    /** Takes back the last thing the renter did and returns a message about it. */
    public String undoLast(int renterId) throws ControllerException {
        return Guard.database("undoing", () -> booking.undoLast(renterId));
    }

    /** Returns the reservations and rentals of one renter. */
    public Bookings bookingsOf(int renterId) throws ControllerException {
        return Guard.database("loading your bookings", () -> {
            Map<Integer, Vehicle> vehicles = new HashMap<>();
            for (Vehicle v : db.getAllVehiclesIncludingRemoved()) {
                vehicles.put(v.getId(), v);
            }
            return new Bookings(vehicles, booking.reservationsOf(renterId),
                    booking.rentalsOf(renterId));
        });
    }

    /** Returns the renter record with this id, or null. */
    public Renter findRenter(int renterId) throws ControllerException {
        return Guard.database("loading your details", () -> db.getRenterById(renterId));
    }
}
