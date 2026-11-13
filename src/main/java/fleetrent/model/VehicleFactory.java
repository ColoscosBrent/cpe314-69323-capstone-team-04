package fleetrent.model;


import java.time.LocalDate;

/**
 * Builds the right Vehicle subclass from the type text stored in the database.
 */
public class VehicleFactory {

    /** Creates the right Vehicle subclass for the type name (SEDAN, SUV or MOTORCYCLE). */
    public static Vehicle create(int id, String type, String make, String model, int year,
                                 String plateNumber, String color, double dailyRate,
                                 int mileage, LocalDate lastPMS, String categoryPath) {
        if (type == null) {
            throw new IllegalArgumentException("Unknown vehicle type: null");
        }
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

    /**
     * Simple starting category for each type. Used by the GUI until the full tree is built.
     */
    public static String defaultCategoryPath(String type) {
        if (type == null) {
            throw new IllegalArgumentException("Unknown vehicle type: null");
        }
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
