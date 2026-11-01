package fleetrent;

import java.time.LocalDate;

// Parent class for every vehicle. It cannot be created directly (abstract).
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

    // Each subclass answers with its own type: SEDAN, SUV, or MOTORCYCLE.
    public abstract String getType();

    // Each subclass has its own mileage fee rule, so each one writes its own version.
    public abstract double computeRentalCost(int days, int milesDriven);

    // Shared formula that the subclasses call with their own free miles and fee per extra mile.
    protected double baseCost(int days, int milesDriven, int freeMilesPerDay, double feePerExtraMile) {
        if (days <= 0) {
            throw new IllegalArgumentException("days must be at least 1");
        }
        if (milesDriven < 0) {
            throw new IllegalArgumentException("milesDriven cannot be negative");
        }
        int extraMiles = Math.max(0, milesDriven - freeMilesPerDay * days);
        return dailyRate * days + extraMiles * feePerExtraMile;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getMake() { return make; }
    public String getModel() { return model; }
    public int getYear() { return year; }
    public String getPlateNumber() { return plateNumber; }
    public String getColor() { return color; }
    public double getDailyRate() { return dailyRate; }
    public int getMileage() { return mileage; }
    public LocalDate getLastPMS() { return lastPMS; }
    public String getCategoryPath() { return categoryPath; }

    @Override
    public String toString() {
        return getType() + " - " + make + " " + model + " " + year + " (" + plateNumber + ")";
    }
}
