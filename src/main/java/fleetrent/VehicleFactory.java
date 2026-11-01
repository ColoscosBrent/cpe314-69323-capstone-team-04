package fleetrent;

import java.time.LocalDate;

// Builds the right Vehicle subclass from the type text stored in the database.
public class VehicleFactory {

    public static Vehicle create(int id, String type, String make, String model, int year,
                                 String plateNumber, String color, double dailyRate,
                                 int mileage, LocalDate lastPMS, String categoryPath) {
        switch (type) {
            case "SEDAN":
                return new Sedan(id, make, model, year, plateNumber, color, dailyRate, mileage, lastPMS, categoryPath);
            case "SUV":
                return new Suv(id, make, model, year, plateNumber, color, dailyRate, mileage, lastPMS, categoryPath);
            case "MOTORCYCLE":
                return new Motorcycle(id, make, model, year, plateNumber, color, dailyRate, mileage, lastPMS, categoryPath);
            default:
                throw new IllegalArgumentException("Unknown vehicle type: " + type);
        }
    }

    // Simple starting category for each type. Used by the GUI until the full tree is built.
    public static String defaultCategoryPath(String type) {
        switch (type) {
            case "SEDAN":
                return "Car > Sedan";
            case "SUV":
                return "Car > SUV";
            case "MOTORCYCLE":
                return "Motorcycle";
            default:
                throw new IllegalArgumentException("Unknown vehicle type: " + type);
        }
    }
}
