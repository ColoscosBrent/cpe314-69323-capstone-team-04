package fleetrent.model;

import java.time.LocalDate;
import java.util.List;

/**
 * One vehicle as a renter sees it for the dates they asked for. rentedUntil is the day it is due
 * back when it is out right now, otherwise null.
 *
 * @param vehicle      the vehicle
 * @param availability whether it can be booked for the chosen days
 * @param busy         the days it is already taken
 * @param rentedUntil  the day it is due back, or null when it is not out
 */
public record Offer(Vehicle vehicle, Availability availability, List<BusySpan> busy,
        LocalDate rentedUntil) {
}
