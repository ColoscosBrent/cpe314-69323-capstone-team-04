package fleetrent;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Small shared rule: how many billable days a rental has.
 */
public final class RentalMath {
    private RentalMath() {
    }

    /**
     * Same-day return still counts as 1 day. A return before the start date is not allowed.
     */
    public static int billableDays(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Both dates are required.");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("The end date cannot be before the start date.");
        }
        return (int) Math.max(1, ChronoUnit.DAYS.between(start, end));
    }
}
