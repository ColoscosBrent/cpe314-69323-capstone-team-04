package fleetrent;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * The rules for starting and returning a rental. It uses the database for saving, a Queue
 * (WaitingList) per vehicle for renters who want a vehicle that is already out, and a Stack
 * (CheckoutHistory) so the last check-out can be undone.
 */
public class RentalService {
    private final Database db;
    private final Map<Integer, WaitingList> waitingByVehicle = new HashMap<>();
    private final CheckoutHistory history = new CheckoutHistory();

    /** Creates a rental service. */
    public RentalService(Database db) {
        this.db = db;
    }

    /**
     * Starts a rental and returns it. If the vehicle is already rented out, the renter joins the
     * waiting list for that vehicle and the method returns null.
     */
    public Rental startRental(int renterId, int vehicleId, LocalDate start, LocalDate end) throws SQLException {
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
            waitingByVehicle.computeIfAbsent(vehicleId, id -> new WaitingList()).join(renter);
            return null;
        }
        if (db.hasReservationOverlap(vehicleId, start, end)) {
            throw new IllegalStateException("That vehicle is reserved by a renter for some of those days. "
                    + "Pick other dates or another vehicle.");
        }
        Rental rental = Rental.startNew(renterId, vehicleId, start, end);
        // Check and save in one step: a renter in another window may have booked these days a
        // moment ago.
        if (!db.insertRentalIfFree(rental, start)) {
            throw new IllegalStateException("That vehicle was just booked for some of those days. "
                    + "Pick other dates or another vehicle.");
        }
        history.record(rental);
        return rental;
    }

    /**
     * Closes a rental and returns the final cost. Works for an early return too.
     */
    public double returnRental(int rentalId, LocalDate returnDate, int milesDriven) throws SQLException {
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
        rental.markReturned(returnDate, milesDriven);
        db.updateRental(rental);
        db.addMileage(vehicle.getId(), milesDriven);
        return cost;
    }

    /**
     * Undoes the most recent check-out (stack, last in first out). Returns it, or null if none. A
     * rental that was already returned cannot be undone, so it is skipped with an exception.
     */
    public Rental undoLastCheckout() throws SQLException {
        Rental last = history.undoLast();
        if (last == null) {
            return null;
        }
        Rental fresh = db.getRentalById(last.getId());
        if (fresh == null) {
            return null;
        }
        if (fresh.getStatus() == RentalStatus.RETURNED) {
            throw new IllegalStateException("That rental was already returned, so it cannot be undone.");
        }
        db.deleteRental(fresh.getId());
        return fresh;
    }

    /**
     * The next renter in line for this vehicle (removed from the line), or null.
     */
    public Renter serveNextWaiting(int vehicleId) {
        WaitingList line = waitingByVehicle.get(vehicleId);
        return line == null ? null : line.serveNext();
    }

    /** Returns how many renters wait for a vehicle. */
    public int waitingCount(int vehicleId) {
        WaitingList line = waitingByVehicle.get(vehicleId);
        return line == null ? 0 : line.size();
    }

    private Renter findRenter(int renterId) throws SQLException {
        for (Renter r : db.getAllRenters()) {
            if (r.getId() == renterId) {
                return r;
            }
        }
        return null;
    }
}
