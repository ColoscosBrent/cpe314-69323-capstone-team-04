package fleetrent.service;

import fleetrent.dao.Database;
import fleetrent.model.CheckoutHistory;
import fleetrent.model.Payment;
import fleetrent.model.Rental;
import fleetrent.model.RentalStatus;
import fleetrent.model.Renter;
import fleetrent.model.Vehicle;
import fleetrent.model.WaitingList;

import java.sql.SQLException;
import java.time.LocalDate;

/**
 * The rules for starting and returning a rental. It uses the database for saving. A Queue
 * (WaitingList) per vehicle holds renters who want a vehicle that is already out, and a Stack
 * (CheckoutHistory) lets the last check-out be undone. Both are saved in the database, so they
 * survive a restart: they are rebuilt from the saved rows each time they are used.
 */
public class RentalService {
    private final Database db;

    /** Creates a rental service. */
    public RentalService(Database db) {
        this.db = db;
    }

    /**
     * Starts a rental and returns it. If the vehicle is already rented out, the renter joins the
     * waiting list for that vehicle and the method returns null.
     */
    public Rental startRental(int renterId, int vehicleId, LocalDate start,
            LocalDate end) throws SQLException {
        RentalMath.billableDays(start, end);
        Vehicle vehicle = db.getVehicleById(vehicleId);
        if (vehicle == null) {
            throw new IllegalArgumentException("That vehicle does not exist.");
        }
        Renter renter = findRenter(renterId);
        if (renter == null) {
            throw new IllegalArgumentException("That renter does not exist.");
        }
        if (!db.isVehicleActive(vehicleId)) {
            throw new IllegalArgumentException("That vehicle was removed from the fleet.");
        }
        if (db.hasOngoingRental(vehicleId)) {
            db.addWaiting(vehicleId, renterId, LocalDate.now());
            return null;
        }
        if (db.hasReservationOverlap(vehicleId, start, end)) {
            throw new IllegalStateException(
                    "That vehicle is reserved by a renter for some of those days. "
                    + "Pick other dates or another vehicle.");
        }
        Rental rental = Rental.startNew(renterId, vehicleId, start, end);
        // Check and save in one step: a renter in another window may have booked these days a
        // moment ago.
        if (!db.insertRentalIfFree(rental, start)) {
            throw new IllegalStateException("That vehicle was just booked for some of those days. "
                    + "Pick other dates or another vehicle.");
        }
        db.logCheckout(rental.getId());
        return rental;
    }

    /**
     * Closes a rental and returns what the renter pays (the rental charge plus any late fee). The
     * payment is saved with the return. Works for an early return too.
     */
    public double returnRental(int rentalId, LocalDate returnDate,
            int milesDriven) throws SQLException {
        if (milesDriven < 0) {
            throw new IllegalArgumentException("Miles driven cannot be negative.");
        }
        Rental rental = db.getRentalById(rentalId);
        if (rental == null) {
            throw new IllegalArgumentException("That rental does not exist.");
        }
        if (rental.getStatus() == RentalStatus.RETURNED) {
            throw new IllegalStateException("That rental was already returned.");
        }
        int days = RentalMath.billableDays(rental.getStartDate(), returnDate);
        Vehicle vehicle = db.getVehicleById(rental.getVehicleId());
        double cost = vehicle.computeRentalCost(days, milesDriven);
        double lateFee = RentalMath.lateFee(vehicle.getDailyRate(),
                RentalMath.lateDays(rental.getEndDate(), returnDate));
        rental.markReturned(returnDate, milesDriven);
        Payment payment = Payment.forRental(rentalId, cost, lateFee, returnDate);
        db.saveReturn(rental, payment);
        return payment.getTotal();
    }

    /**
     * Undoes the most recent check-out (stack, last in first out). Returns it, or null if none. A
     * rental that was already returned cannot be undone, so it is skipped with an exception.
     */
    public Rental undoLastCheckout() throws SQLException {
        CheckoutHistory history = loadHistory();
        Integer lastId = history.undoLast();
        if (lastId == null) {
            return null;
        }
        Rental fresh = db.getRentalById(lastId);
        if (fresh == null) {
            db.removeCheckoutLog(lastId);
            return null;
        }
        if (fresh.getStatus() == RentalStatus.RETURNED) {
            db.removeCheckoutLog(lastId);
            throw new IllegalStateException(
                    "That rental was already returned, so it cannot be undone.");
        }
        db.deleteRental(fresh.getId());
        return fresh;
    }

    /** Returns how many check-outs can still be undone. */
    public int undoableCount() throws SQLException {
        return loadHistory().size();
    }

    /**
     * The next renter in line for this vehicle (removed from the line), or null.
     */
    public Renter serveNextWaiting(int vehicleId) throws SQLException {
        WaitingList line = loadWaiting(vehicleId);
        Renter next = line.serveNext();
        if (next != null) {
            db.removeFirstWaiting(vehicleId);
        }
        return next;
    }

    /** Returns how many renters wait for a vehicle. */
    public int waitingCount(int vehicleId) throws SQLException {
        return db.getWaitingRenterIds(vehicleId).size();
    }

    private CheckoutHistory loadHistory() throws SQLException {
        CheckoutHistory history = new CheckoutHistory();
        for (int id : db.getCheckoutLog()) {
            history.record(id);
        }
        return history;
    }

    private WaitingList loadWaiting(int vehicleId) throws SQLException {
        WaitingList line = new WaitingList();
        for (int renterId : db.getWaitingRenterIds(vehicleId)) {
            Renter renter = findRenter(renterId);
            if (renter != null) {
                line.join(renter);
            }
        }
        return line;
    }

    private Renter findRenter(int renterId) throws SQLException {
        return db.getRenterById(renterId);
    }
}
