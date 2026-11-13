package fleetrent;

import java.time.LocalDate;

/**
 * Days when a vehicle is not free. The kind is RENTED (an ongoing rental) or RESERVED. It holds no
 * renter name, so a renter can see when a vehicle is busy without seeing who has it.
 */
public record BusySpan(LocalDate start, LocalDate end, String kind) {
    /** Tells whether this busy span shares at least one day with the given dates. */
    public boolean overlaps(LocalDate from, LocalDate to) {
        return !start.isAfter(to) && !end.isBefore(from);
    }
}
