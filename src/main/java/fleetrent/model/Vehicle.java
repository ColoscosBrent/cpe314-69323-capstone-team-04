package fleetrent.model;


import java.time.LocalDate;

/**
 * Parent class for every vehicle. It cannot be created directly (abstract).
 */
public abstract class Vehicle {
    private int id;
    private final String make;
    private final String model;
    private final int year;
    private final String plateNumber;
    private final String color;
    private final double dailyRate;
    private final int mileage;
    private final LocalDate lastPMS;
    private final String categoryPath;

    protected Vehicle(int id, String make, String model, int year, String plateNumber,
                      String color, double dailyRate, int mileage, LocalDate lastPMS,
                      String categoryPath) {
        this.id = id;
        this.make = make;
        this.model = model;
        this.year = year;
        this.plateNumber = plateNumber;
        this.color = color;
        this.dailyRate = dailyRate;
        this.mileage = mileage;
        this.lastPMS = lastPMS;
        this.categoryPath = categoryPath;
    }

    /** Each subclass answers with its own type: SEDAN, SUV, or MOTORCYCLE. */
    public abstract String getType();

    /** The free miles per rental day of this type, shown to renters before they book. */
    public abstract int getFreeMilesPerDay();

    /** The fee for each mile over the free miles. */
    public abstract double getFeePerExtraMile();

    /**
     * One line for the screen, for example "100 free miles a day, then 0.20 per extra mile".
     */
    public String feeSummary() {
        return String.format(java.util.Locale.US, "%d free miles a day, then %.2f per extra mile",
                getFreeMilesPerDay(), getFeePerExtraMile());
    }

    /**
     * "Toyota Vios 2022": the name shown to people.
     */
    public String displayName() {
        return make + " " + model + (year > 0 ? " " + year : "");
    }

    /** The cost of a rental. Each subclass has its own mileage fee rule. */
    public abstract double computeRentalCost(int days, int milesDriven);

    // Shared formula that the subclasses call with their own free miles and fee per extra mile.
    protected double baseCost(int days, int milesDriven, int freeMilesPerDay,
            double feePerExtraMile) {
        if (days <= 0) {
            throw new IllegalArgumentException("days must be at least 1");
        }
        if (milesDriven < 0) {
            throw new IllegalArgumentException("milesDriven cannot be negative");
        }
        int extraMiles = Math.max(0, milesDriven - freeMilesPerDay * days);
        return dailyRate * days + extraMiles * feePerExtraMile;
    }

    /** Returns the id. */
    public int getId() { return id; }
    /** Sets the id. */
    public void setId(int id) { this.id = id; }
    /** Returns the make. */
    public String getMake() { return make; }
    /** Returns the model. */
    public String getModel() { return model; }
    /** Returns the year. */
    public int getYear() { return year; }
    /** Returns the plate number. */
    public String getPlateNumber() { return plateNumber; }
    /** Returns the color. */
    public String getColor() { return color; }
    /** Returns the daily rate. */
    public double getDailyRate() { return dailyRate; }
    /** Returns the mileage. */
    public int getMileage() { return mileage; }
    /** Returns the last p m s. */
    public LocalDate getLastPMS() { return lastPMS; }
    /** Returns the category path. */
    public String getCategoryPath() { return categoryPath; }

    @Override
    public String toString() {
        return getType() + " - " + make + " " + model + (year > 0 ? " " + year : "") + " ("
                + plateNumber + ")";
    }
}
