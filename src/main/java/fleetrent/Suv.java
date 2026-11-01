package fleetrent;

import java.time.LocalDate;

public class Suv extends Vehicle {
    // Our own rule: 80 free miles per rental day, then 0.30 per extra mile.
    private static final int FREE_MILES_PER_DAY = 80;
    private static final double FEE_PER_EXTRA_MILE = 0.30;

    public Suv(int id, String make, String model, int year, String plateNumber, String color,
          double dailyRate, int mileage, LocalDate lastPMS, String categoryPath) {
        super(id, make, model, year, plateNumber, color, dailyRate, mileage, lastPMS, categoryPath);
    }

    @Override
    public String getType() {
        return "SUV";
    }

    @Override
    public double computeRentalCost(int days, int milesDriven) {
        return baseCost(days, milesDriven, FREE_MILES_PER_DAY, FEE_PER_EXTRA_MILE);
    }
}
