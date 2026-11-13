package fleetrent.model;

/**
 * The kinds of work a vehicle can have done. PMS is the periodic maintenance service.
 */
public enum MaintenanceType {
    PMS("Preventive maintenance (PMS)"),
    REPAIR("Repair"),
    TIRES("Tires"),
    INSPECTION("Inspection"),
    OTHER("Other");

    private final String label;

    MaintenanceType(String label) {
        this.label = label;
    }

    /** Returns the name shown on screen. */
    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
