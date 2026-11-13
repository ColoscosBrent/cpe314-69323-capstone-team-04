package fleetrent.model;

/** Whether a vehicle can be booked for the days a renter chose. */
public enum Availability {
    /** Free for all the chosen days. */
    AVAILABLE,
    /** An ongoing rental touches the chosen days. */
    RENTED,
    /** An active reservation touches the chosen days. */
    RESERVED
}
