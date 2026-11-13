package fleetrent.model;

import java.time.LocalDate;

/**
 * What a renter paid when a rental was returned: the rental charge, the late fee and the date.
 */
public class Payment {
    private int id;
    private final int rentalId;
    private final double amount;
    private final double lateFee;
    private final LocalDate paidOn;

    /** Creates a payment. Amounts cannot be negative. */
    public Payment(int id, int rentalId, double amount, double lateFee, LocalDate paidOn) {
        if (amount < 0 || lateFee < 0) {
            throw new IllegalArgumentException("A payment cannot be negative.");
        }
        if (paidOn == null) {
            throw new IllegalArgumentException("The payment date is required.");
        }
        this.id = id;
        this.rentalId = rentalId;
        this.amount = amount;
        this.lateFee = lateFee;
        this.paidOn = paidOn;
    }

    /** Creates a payment that is not saved yet. */
    public static Payment forRental(int rentalId, double amount, double lateFee, LocalDate paidOn) {
        return new Payment(0, rentalId, amount, lateFee, paidOn);
    }

    /** Returns the id. */
    public int getId() {
        return id;
    }

    /** Sets the id after saving. */
    public void setId(int id) {
        this.id = id;
    }

    /** Returns the rental this payment belongs to. */
    public int getRentalId() {
        return rentalId;
    }

    /** Returns the rental charge without the late fee. */
    public double getAmount() {
        return amount;
    }

    /** Returns the late fee. */
    public double getLateFee() {
        return lateFee;
    }

    /** Returns the date it was paid. */
    public LocalDate getPaidOn() {
        return paidOn;
    }

    /** Returns the rental charge plus the late fee. */
    public double getTotal() {
        return amount + lateFee;
    }
}
