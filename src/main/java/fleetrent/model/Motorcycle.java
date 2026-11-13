package fleetrent.model;


import java.time.LocalDate;

/** A motorcycle. It has its own free mileage and extra-mile fee. */
public class Motorcycle extends Vehicle {
    // Our own rule: 60 free miles per rental day, then 0.15 per extra mile.
    private static final int FREE_MILES_PER_DAY = 60;
    private static final double FEE_PER_EXTRA_MILE = 0.15;

    /** Creates a motorcycle. */
    public Motorcycle(int id, String make, String model, int year, String plateNumber, String color,
          double dailyRate, int mileage, LocalDate lastPMS, String categoryPath) {
        super(id, make, model, year, plateNumber, color, dailyRate, mileage, lastPMS, categoryPath);
    }

    @Override
    public String getType() {
        return "MOTORCYCLE";
    }

    @Override
    public int getFreeMilesPerDay() {
        return FREE_MILES_PER_DAY;
    }

    @Override
    public double getFeePerExtraMile() {
        return FEE_PER_EXTRA_MILE;
    }

    @Override
    public double computeRentalCost(int days, int milesDriven) {
        return baseCost(days, milesDriven, FREE_MILES_PER_DAY, FEE_PER_EXTRA_MILE);
    }
}
