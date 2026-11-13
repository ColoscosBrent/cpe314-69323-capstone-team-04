package fleetrent;

/**
 * ACTIVE: the vehicle is held for the dates. CANCELLED: the renter or the owner cancelled it.
 * CONVERTED: the owner handed the vehicle over and it became a rental.
 */
public enum ReservationStatus {
    ACTIVE,
    CANCELLED,
    CONVERTED
}
