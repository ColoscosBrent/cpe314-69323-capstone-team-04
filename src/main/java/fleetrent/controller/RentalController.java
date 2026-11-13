package fleetrent.controller;

import fleetrent.dao.Database;
import fleetrent.model.Payment;
import fleetrent.model.Rental;
import fleetrent.model.Renter;
import fleetrent.model.Vehicle;
import fleetrent.service.RentalService;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the owner's Rentals tab asks for: add a renter, start a rental (or join the waiting list),
 * undo the last check-out and return a vehicle.
 */
public class RentalController {
    /**
     * Everything the tab shows.
     *
     * @param renters         every renter
     * @param activeVehicles  the vehicles that are in the fleet now
     * @param rentersById     every renter, by id
     * @param vehiclesById    every vehicle ever added, by id (old rentals may name a removed one)
     * @param rentals         every rental
     * @param paymentsByRental the saved payment of each returned rental (imported ones have none)
     */
    public record Data(List<Renter> renters, List<Vehicle> activeVehicles,
            Map<Integer, Renter> rentersById, Map<Integer, Vehicle> vehiclesById,
            List<Rental> rentals, Map<Integer, Payment> paymentsByRental) {
    }

    private final Database db;
    private final RentalService service;

    /** Creates the controller. */
    public RentalController(Database db, RentalService service) {
        this.db = db;
        this.service = service;
    }

    /** Loads the data of the tab. */
    public Data load() throws ControllerException {
        return Guard.database("loading the rentals", () -> {
            List<Renter> renters = db.getAllRenters();
            Map<Integer, Renter> rentersById = new HashMap<>();
            for (Renter r : renters) {
                rentersById.put(r.getId(), r);
            }
            Map<Integer, Vehicle> vehiclesById = new HashMap<>();
            for (Vehicle v : db.getAllVehiclesIncludingRemoved()) {
                vehiclesById.put(v.getId(), v);
            }
            Map<Integer, Payment> payments = new HashMap<>();
            for (Payment p : db.getAllPayments()) {
                payments.put(p.getRentalId(), p);
            }
            return new Data(renters, db.getAllVehicles(), rentersById, vehiclesById,
                    db.getAllRentals(), payments);
        });
    }

    /** Saves a new renter. */
    public void addRenter(Renter renter) throws ControllerException {
        Guard.database("saving the renter", () -> db.insertRenter(renter));
    }

    /** Starts a rental. Returns null when the vehicle is out and the renter joined the line. */
    public Rental start(int renterId, int vehicleId, LocalDate start, LocalDate end)
            throws ControllerException {
        return Guard.database("starting the rental",
                () -> service.startRental(renterId, vehicleId, start, end));
    }

    /** Returns how many renters wait for a vehicle. */
    public int waitingCount(int vehicleId) throws ControllerException {
        return Guard.database("reading the waiting list", () -> service.waitingCount(vehicleId));
    }

    /** Takes back the last check-out. Returns null when there is nothing to undo. */
    public Rental undoLastCheckout() throws ControllerException {
        return Guard.database("undoing the check-out", service::undoLastCheckout);
    }

    /** Returns a vehicle and gives what the renter pays (rental charge plus late fee). */
    public double returnRental(int rentalId, LocalDate returnDate, int milesDriven)
            throws ControllerException {
        return Guard.database("returning the vehicle",
                () -> service.returnRental(rentalId, returnDate, milesDriven));
    }

    /** Tells whether a vehicle is out on an ongoing rental. */
    public boolean isRentedOut(int vehicleId) throws ControllerException {
        return Guard.database("checking the vehicle", () -> db.hasOngoingRental(vehicleId));
    }
}
