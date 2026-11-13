package fleetrent.service;


import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Small shared rule: how many billable days a rental has.
 */
public final class RentalMath {
    /** The late fee for each day after the end date: this share of the daily rate. */
    public static final double LATE_FEE_RATE = 0.10;

    private RentalMath() {
    }

    /** How many days after the end date the vehicle came back (0 when it was on time). */
    public static int lateDays(LocalDate end, LocalDate returnedOn) {
        if (end == null || returnedOn == null) {
            throw new IllegalArgumentException("Both dates are required.");
        }
        return (int) Math.max(0, ChronoUnit.DAYS.between(end, returnedOn));
    }

    /** The late fee: each late day costs LATE_FEE_RATE of the daily rate. */
    public static double lateFee(double dailyRate, int lateDays) {
        return dailyRate * LATE_FEE_RATE * Math.max(0, lateDays);
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
