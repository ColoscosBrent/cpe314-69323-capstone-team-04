package fleetrent.model;


import java.util.List;
import java.util.Locale;

/**
 * What we know about the three vehicle types outside the cost rules: the name NHTSA uses for each
 * type, and the category paths a vehicle of that type can have (Car > Sedan > Compact Sedan ...).
 */
public final class VehicleTypes {
    public static final List<String> ALL = List.of("SEDAN", "SUV", "MOTORCYCLE");

    private VehicleTypes() {
    }

    /**
     * The vehicle type word the NHTSA vPIC API understands for each of our types. NHTSA files SUVs
     * under "Multipurpose Passenger Vehicle (MPV)".
     */
    public static String apiType(String type) {
        switch (type) {
            case "SEDAN":
                return "car";
            case "SUV":
                return "mpv";
            case "MOTORCYCLE":
                return "motorcycle";
            default:
                throw new IllegalArgumentException("Unknown vehicle type: " + type);
        }
    }

    /**
     * Our type for a vehicle type name that NHTSA sends back, or null if we do not support it
     * (trucks, buses, trailers ...).
     */
    public static String fromApiName(String apiName) {
        if (apiName == null) {
            return null;
        }
        String n = apiName.toLowerCase(Locale.ROOT);
        if (n.contains("motorcycle")) {
            return "MOTORCYCLE";
        }
        if (n.contains("multipurpose") || n.contains("mpv")) {
            return "SUV";
        }
        if (n.contains("passenger car")) {
            return "SEDAN";
        }
        return null;
    }

    /**
     * The category paths offered for a type. The first one is the default.
     */
    public static List<String> categoryOptions(String type) {
        switch (type) {
            case "SEDAN":
                return List.of("Car > Sedan", "Car > Sedan > Compact Sedan",
                        "Car > Sedan > Compact Sedan > Hybrid Compact Sedan");
            case "SUV":
                return List.of("Car > SUV", "Car > SUV > Compact SUV");
            case "MOTORCYCLE":
                return List.of("Motorcycle", "Motorcycle > Scooter");
            default:
                throw new IllegalArgumentException("Unknown vehicle type: " + type);
        }
    }

    /**
     * True if the category path belongs to the type.
     */
    public static boolean isCategoryOf(String type, String categoryPath) {
        return categoryOptions(type).contains(categoryPath);
    }
}
