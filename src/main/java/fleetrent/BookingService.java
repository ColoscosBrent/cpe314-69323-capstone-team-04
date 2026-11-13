package fleetrent;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * What a renter can do (see the cars, rent a car today, reserve a car for later dates, cancel,
 * undo) and what the owner does with a reservation (hand the car over). It has no screen code.
 * Days work like this: the start day and the end day are both held for the renter. A vehicle is
 * free for a request only if no ongoing rental and no active reservation touches any of those days.
 */
public class BookingService {
    public static final int MAX_DAYS = 30;
    public static final int MAX_DAYS_AHEAD = 180;

    /** Whether a vehicle can be booked for the chosen days. */
    public enum Availability { AVAILABLE, RENTED, RESERVED }

    /**
     * One vehicle as a renter sees it for the dates they asked for. rentedUntil is the day it is
     * due back when it is out right now, otherwise null.
     */
    public record Offer(Vehicle vehicle, Availability availability, List<BusySpan> busy, LocalDate rentedUntil) {
    }

    // The last things a renter did, newest on top, so Undo can take them back one by one.
    private record Action(boolean reservation, int id) {
    }

    private final Database db;
    private final Supplier<LocalDate> today;
    private final Deque<Action> undoStack = new ArrayDeque<>();

    /** Creates a booking service. */
    public BookingService(Database db) {
        this(db, LocalDate::now);
    }

    /** Creates a booking service. */
    public BookingService(Database db, Supplier<LocalDate> today) {
        this.db = db;
        this.today = today;
    }

    /** Returns the date this service treats as today. */
    public LocalDate today() {
        return today.get();
    }

    // ---------- look ----------

    /**
     * Every vehicle in the fleet with its status for the chosen days.
     */
    public List<Offer> browse(LocalDate start, LocalDate end) throws SQLException {
        checkOrder(start, end);
        LocalDate now = today.get();
        Map<Integer, List<BusySpan>> busy = db.getBusySpans(now);
        List<Offer> offers = new ArrayList<>();
        for (Vehicle v : db.getAllVehicles()) {
            List<BusySpan> spans = busy.getOrDefault(v.getId(), List.of());
            Availability a = Availability.AVAILABLE;
            LocalDate until = null;
            for (BusySpan s : spans) {
                if (s.kind().equals("RENTED") && !s.start().isAfter(now)) {
                    until = s.end();
                }
                if (s.overlaps(start, end)) {
                    if (s.kind().equals("RENTED")) {
                        a = Availability.RENTED;
                    } else if (a != Availability.RENTED) {
                        a = Availability.RESERVED;
                    }
                }
            }
            offers.add(new Offer(v, a, spans, until));
        }
        return offers;
    }

    /**
     * The price of the days, without extra miles. Extra miles are added when the vehicle comes
     * back.
     */
    public static double estimate(Vehicle vehicle, LocalDate start, LocalDate end) {
        return vehicle.computeRentalCost(RentalMath.billableDays(start, end), 0);
    }

    // ---------- rent today, reserve later ----------

    /**
     * Starts a rental today and ends on the chosen day.
     */
    public Rental rentNow(int renterId, int vehicleId, LocalDate end) throws SQLException {
        LocalDate start = today.get();
        checkSpan(start, end);
        checkParties(renterId, vehicleId);
        Rental rental = Rental.startNew(renterId, vehicleId, start, end);
        if (!db.insertRentalIfFree(rental, start)) {
            throw new IllegalStateException(whyNotFree(vehicleId, start, end));
        }
        undoStack.push(new Action(false, rental.getId()));
        return rental;
    }

    /**
     * Holds the vehicle for later dates. The first day must be after today (to rent today, use
     * rentNow).
     */
    public Reservation reserve(int renterId, int vehicleId, LocalDate start, LocalDate end) throws SQLException {
        LocalDate now = today.get();
        if (start == null || end == null) {
            throw new IllegalArgumentException("Pick the first and the last day.");
        }
        if (!start.isAfter(now)) {
            throw new IllegalArgumentException("A reservation starts tomorrow or later. To rent today, use Rent.");
        }
        if (start.isAfter(now.plusDays(MAX_DAYS_AHEAD))) {
            throw new IllegalArgumentException("You can reserve up to " + MAX_DAYS_AHEAD + " days ahead.");
        }
        checkSpan(start, end);
        checkParties(renterId, vehicleId);
        Reservation reservation = Reservation.startNew(renterId, vehicleId, start, end, now);
        if (!db.insertReservationIfFree(reservation, now)) {
            throw new IllegalStateException(whyNotFree(vehicleId, start, end));
        }
        undoStack.push(new Action(true, reservation.getId()));
        return reservation;
    }

    /**
     * A renter cancels one of their own reservations.
     */
    public void cancelReservation(int renterId, int reservationId) throws SQLException {
        Reservation r = db.getReservationById(reservationId);
        if (r == null || r.getRenterId() != renterId) {
            throw new IllegalArgumentException("That reservation was not found.");
        }
        if (r.getStatus() != ReservationStatus.ACTIVE) {
            throw new IllegalStateException("That reservation is already " + r.getStatus().name().toLowerCase() + ".");
        }
        db.updateReservationStatus(reservationId, ReservationStatus.CANCELLED, "Cancelled by the renter");
    }

    /** Tells whether the last check-out can still be undone. */
    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    /**
     * Takes back the last thing the renter did: a reservation is cancelled, a rental is deleted
     * (only while the owner has not closed it). Returns a sentence for the screen, or null if
     * nothing to undo.
     */
    public String undoLast(int renterId) throws SQLException {
        Action action = undoStack.poll();
        if (action == null) {
            return null;
        }
        if (action.reservation()) {
            Reservation r = db.getReservationById(action.id());
            if (r == null || r.getRenterId() != renterId || r.getStatus() != ReservationStatus.ACTIVE) {
                throw new IllegalStateException("That reservation can no longer be undone (it was already "
                        + "cancelled or handed over).");
            }
            db.updateReservationStatus(r.getId(), ReservationStatus.CANCELLED, "Cancelled by the renter (undo)");
            return "Reservation #" + r.getId() + " was cancelled.";
        }
        Rental rental = db.getRentalById(action.id());
        if (rental == null || rental.getRenterId() != renterId || rental.getStatus() != RentalStatus.ONGOING) {
            throw new IllegalStateException("That rental can no longer be undone (the owner already closed it).");
        }
        db.deleteRental(rental.getId());
        return "Rental #" + rental.getId() + " was undone.";
    }

    // ---------- the renter's own bookings ----------

    /** Returns every rental of one renter. */
    public List<Rental> rentalsOf(int renterId) throws SQLException {
        return db.getRentalsByRenter(renterId);
    }

    /** Returns every reservation of one renter. */
    public List<Reservation> reservationsOf(int renterId) throws SQLException {
        return db.getReservationsByRenter(renterId);
    }

    // ---------- the owner ----------

    /**
     * The owner gives the vehicle to the renter. The reservation becomes a rental with the same
     * days.
     */
    public Rental handOver(int reservationId) throws SQLException {
        LocalDate now = today.get();
        Reservation r = db.getReservationById(reservationId);
        if (r == null) {
            throw new IllegalArgumentException("That reservation does not exist.");
        }
        if (r.getStatus() != ReservationStatus.ACTIVE) {
            throw new IllegalStateException("That reservation is already " + r.getStatus().name().toLowerCase() + ".");
        }
        if (r.getStartDate().isAfter(now)) {
            throw new IllegalStateException("The reservation starts on " + r.getStartDate()
                    + ". The vehicle can be handed over on that day.");
        }
        if (r.getEndDate().isBefore(now)) {
            throw new IllegalStateException("The reservation ended on " + r.getEndDate() + ". Cancel it instead.");
        }
        if (!db.isVehicleActive(r.getVehicleId())) {
            throw new IllegalStateException("That vehicle was removed from the fleet.");
        }
        if (db.hasOngoingRental(r.getVehicleId())) {
            throw new IllegalStateException("The vehicle is still rented out. Return it first (Rentals tab).");
        }
        Rental rental = Rental.startNew(r.getRenterId(), r.getVehicleId(), r.getStartDate(), r.getEndDate());
        db.convertReservationToRental(r, rental);
        return rental;
    }

    /** Lets the owner cancel any reservation. */
    public void cancelReservationByOwner(int reservationId) throws SQLException {
        Reservation r = db.getReservationById(reservationId);
        if (r == null) {
            throw new IllegalArgumentException("That reservation does not exist.");
        }
        if (r.getStatus() != ReservationStatus.ACTIVE) {
            throw new IllegalStateException("That reservation is already " + r.getStatus().name().toLowerCase() + ".");
        }
        db.updateReservationStatus(reservationId, ReservationStatus.CANCELLED, "Cancelled by the owner");
    }

    // ---------- checks ----------

    private static void checkOrder(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Pick the first and the last day.");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("The last day cannot be before the first day.");
        }
    }

    private void checkSpan(LocalDate start, LocalDate end) {
        checkOrder(start, end);
        if (ChronoUnit.DAYS.between(start, end) > MAX_DAYS) {
            throw new IllegalArgumentException("You can book up to " + MAX_DAYS + " days at a time.");
        }
    }

    private void checkParties(int renterId, int vehicleId) throws SQLException {
        if (db.getRenterById(renterId) == null) {
            throw new IllegalArgumentException("That renter does not exist.");
        }
        if (db.getVehicleById(vehicleId) == null) {
            throw new IllegalArgumentException("That vehicle does not exist.");
        }
        if (!db.isVehicleActive(vehicleId)) {
            throw new IllegalStateException("That vehicle is not available anymore. It was removed from the fleet.");
        }
    }

    private String whyNotFree(int vehicleId, LocalDate start, LocalDate end) throws SQLException {
        LocalDate now = today.get();
        if (!db.isVehicleActive(vehicleId)) {
            return "That vehicle is not available anymore. It was removed from the fleet.";
        }
        if (db.hasRentalOverlap(vehicleId, start, end, now)) {
            return "That vehicle is rented out for some of those days. Pick other dates or another vehicle.";
        }
        return "That vehicle is already reserved for some of those days. Pick other dates or another vehicle.";
    }
}
