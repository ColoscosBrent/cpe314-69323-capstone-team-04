package fleetrent;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

// The rules behind the Rent and Undo buttons. It keeps one WaitingList (Queue) for each vehicle
// and one CheckoutHistory (Stack) for the whole desk. The window calls this class and shows the result.
public class RentalDesk {
    // What happened when the last rental was undone, and who got the vehicle next (both can be null).
    public record UndoResult(Rental undone, Renter nextRenter, Rental nextRental) { }

    private final Database db;
    private final Map<Integer, WaitingList> waitingByVehicle = new HashMap<>();
    private final CheckoutHistory history = new CheckoutHistory();

    public RentalDesk(Database db) {
        this.db = db;
    }

    // Finds a saved renter with this name (any capital letters), or saves a new one.
    public Renter renterNamed(String name) throws SQLException {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("Type the renter's name.");
        }
        for (Renter saved : db.getAllRenters()) {
            if (saved.getName().equalsIgnoreCase(clean)) {
                return saved;
            }
        }
        Renter fresh = new Renter(0, clean, "-", "-", "-");
        db.insertRenter(fresh);
        return fresh;
    }

    // Starts a rental and returns it. If the vehicle is already rented on those days, the renter joins
    // the waiting list of that vehicle and the method returns null.
    public Rental rent(Renter renter, Vehicle vehicle, LocalDate start, LocalDate end) throws SQLException {
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("The end date is before the start date.");
        }
        if (!db.isVehicleFree(vehicle.getId(), start, end)) {
            waitingByVehicle.computeIfAbsent(vehicle.getId(), id -> new WaitingList()).join(renter);
            return null;
        }
        Rental rental = Rental.startNew(renter.getId(), vehicle.getId(), start, end);
        db.insertRental(rental);
        history.record(rental);
        return rental;
    }

    // Undoes the most recent rental (Stack: last in, first out). The days are free again, so the renter
    // who waited longest for that vehicle (Queue: first in, first out) gets the same days.
    public UndoResult undoLast() throws SQLException {
        Rental undone = history.undoLast();
        if (undone == null) {
            return null;
        }
        db.deleteRental(undone.getId());
        WaitingList line = waitingByVehicle.get(undone.getVehicleId());
        if (line == null || line.isEmpty()) {
            return new UndoResult(undone, null, null);
        }
        Renter next = line.serveNext();
        Rental given = Rental.startNew(next.getId(), undone.getVehicleId(), undone.getStartDate(), undone.getEndDate());
        db.insertRental(given);
        history.record(given);
        return new UndoResult(undone, next, given);
    }

    public int waitingCount(int vehicleId) {
        WaitingList line = waitingByVehicle.get(vehicleId);
        return line == null ? 0 : line.size();
    }

    public int undoCount() {
        return history.size();
    }
}
