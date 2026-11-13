package fleetrent.model;

import java.time.LocalDate;

/**
 * One piece of work done on a vehicle: the date, the type and what it cost.
 */
public class Maintenance {
    private int id;
    private final int vehicleId;
    private final LocalDate doneOn;
    private final MaintenanceType type;
    private final double cost;
    private final String note;

    /** Creates a maintenance record. The cost cannot be negative. */
    public Maintenance(int id, int vehicleId, LocalDate doneOn, MaintenanceType type, double cost,
                       String note) {
        if (doneOn == null || type == null) {
            throw new IllegalArgumentException("The date and the type are required.");
        }
        if (cost < 0) {
            throw new IllegalArgumentException("The cost cannot be negative.");
        }
        this.id = id;
        this.vehicleId = vehicleId;
        this.doneOn = doneOn;
        this.type = type;
        this.cost = cost;
        this.note = note == null ? "" : note.trim();
    }

    /** Returns the id. */
    public int getId() {
        return id;
    }

    /** Sets the id after saving. */
    public void setId(int id) {
        this.id = id;
    }

    /** Returns the vehicle the work was done on. */
    public int getVehicleId() {
        return vehicleId;
    }

    /** Returns the date the work was done. */
    public LocalDate getDoneOn() {
        return doneOn;
    }

    /** Returns the kind of work. */
    public MaintenanceType getType() {
        return type;
    }

    /** Returns the cost. */
    public double getCost() {
        return cost;
    }

    /** Returns the note (empty when there is none). */
    public String getNote() {
        return note;
    }
}
