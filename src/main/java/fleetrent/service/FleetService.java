package fleetrent.service;

import fleetrent.dao.Database;
import fleetrent.model.Vehicle;

import java.sql.SQLException;

/**
 * What the owner can do to a vehicle that is already in the fleet: remove it (for example when it
 * is broken) and restore it. A removed vehicle disappears for renters and cannot be rented, but its
 * rental history stays. It has no screen code, so it can be unit tested.
 */
public class FleetService {
    private final Database db;

    /** Creates a fleet service. */
    public FleetService(Database db) {
        this.db = db;
    }

    /**
     * Returns how many reservations were cancelled because of the removal.
     */
    public int removeVehicle(int vehicleId) throws SQLException {
        Vehicle v = db.getVehicleById(vehicleId);
        if (v == null) {
            throw new IllegalArgumentException("That vehicle does not exist.");
        }
        if (!db.isVehicleActive(vehicleId)) {
            throw new IllegalStateException("That vehicle was already removed.");
        }
        int cancelled = db.removeVehicle(vehicleId,
                "Cancelled: the owner removed the vehicle from the fleet");
        if (cancelled < 0) {
            throw new IllegalStateException(v.displayName() + " is rented out now. Return it first "
                    + "(Rentals tab), then remove it.");
        }
        return cancelled;
    }

    /** Puts a removed vehicle back in the fleet. */
    public void restoreVehicle(int vehicleId) throws SQLException {
        Vehicle v = db.getVehicleById(vehicleId);
        if (v == null) {
            throw new IllegalArgumentException("That vehicle does not exist.");
        }
        if (db.isVehicleActive(vehicleId)) {
            throw new IllegalStateException("That vehicle is already in the fleet.");
        }
        Vehicle samePlate = db.findVehicleByPlate(v.getPlateNumber());
        if (samePlate != null && samePlate.getId() != vehicleId) {
            throw new IllegalStateException("Another vehicle in the fleet already has the plate "
                    + v.getPlateNumber() + ". Remove or edit that one first.");
        }
        db.setVehicleActive(vehicleId, true);
    }

    /**
     * Saves the edited details. The plate must not belong to another vehicle in the fleet.
     */
    public void updateVehicle(Vehicle edited) throws SQLException {
        checkDetails(edited);
        Vehicle current = db.getVehicleById(edited.getId());
        if (current == null) {
            throw new IllegalArgumentException("That vehicle does not exist.");
        }
        if (!current.getType().equals(edited.getType())) {
            throw new IllegalArgumentException("The type of a vehicle cannot be changed. "
                    + "Remove it and add it again with the new type.");
        }
        Vehicle samePlate = db.findVehicleByPlate(edited.getPlateNumber());
        if (samePlate != null && samePlate.getId() != edited.getId()) {
            throw new IllegalStateException("A vehicle with that plate number already exists.");
        }
        db.updateVehicle(edited);
    }

    // The screen already checks the form, but the rules live here too, so no other caller can save
    // a vehicle with an empty name or a rate of 0.
    private static void checkDetails(Vehicle v) {
        if (isBlank(v.getMake()) || isBlank(v.getModel())) {
            throw new IllegalArgumentException("The make and the model cannot be empty.");
        }
        if (isBlank(v.getPlateNumber())) {
            throw new IllegalArgumentException("The plate number cannot be empty.");
        }
        if (!(v.getDailyRate() > 0) || Double.isInfinite(v.getDailyRate())) {
            throw new IllegalArgumentException("The daily rate must be more than 0.");
        }
        if (v.getMileage() < 0) {
            throw new IllegalArgumentException("The mileage cannot be negative.");
        }
        if (v.getYear() < 0) {
            throw new IllegalArgumentException("The year cannot be negative.");
        }
    }

    private static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }
}
