package fleetrent.model;


import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What we know about the three vehicle types outside the cost rules: the name NHTSA uses for each
 * type, and the category a vehicle of that type can have: a body style and a transmission (for
 * example Motorcycle > Sport > Manual (clutch)).
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

    private static final String SEP = " > ";
    private static final String AUTOMATIC = "Automatic";
    private static final String MANUAL = "Manual (clutch)";
    private static final String CVT = "CVT";
    private static final String DUAL_CLUTCH = "Dual-clutch";
    private static final String BIKE_AUTOMATIC = "Automatic (twist-and-go)";
    private static final String BIKE_SEMI = "Semi-automatic (no clutch lever)";
    private static final String SCOOTER = "Scooter";
    private static final String UNDERBONE = "Underbone (moped)";
    private static final String ELECTRIC_BIKE = "Electric motorcycle";
    private static final List<String> CAR_GEARS = List.of(AUTOMATIC, MANUAL, CVT, DUAL_CLUTCH);
    private static final List<String> HYBRID_GEARS = List.of(AUTOMATIC, CVT);
    private static final List<String> BIKE_GEARS = List.of(MANUAL, BIKE_SEMI, BIKE_AUTOMATIC);
    private static final List<String> SEDAN_STYLES = List.of("Subcompact Sedan", "Compact Sedan",
            "Mid-size Sedan", "Full-size Sedan", "Luxury Sedan", "Sports Sedan", "Hybrid Sedan",
            "Electric Sedan");
    private static final List<String> SUV_STYLES = List.of("Subcompact SUV", "Compact SUV",
            "Mid-size SUV", "Full-size SUV", "Luxury SUV", "Crossover", "Off-road SUV",
            "MPV / Minivan", "Hybrid SUV", "Electric SUV");
    private static final List<String> BIKE_STYLES = List.of(SCOOTER, UNDERBONE, "Standard (naked)",
            "Sport", "Cruiser", "Touring", "Adventure (dual-purpose)", "Off-road (dirt)",
            ELECTRIC_BIKE);
    // Category paths that older versions saved. They stay valid for vehicles already in the fleet.
    private static final List<String> OLD_PATHS = List.of("Car > Sedan",
            "Car > Sedan > Compact Sedan",
            "Car > Sedan > Compact Sedan > Hybrid Compact Sedan", "Car > SUV",
            "Car > SUV > Compact SUV", "Motorcycle", "Motorcycle > Scooter");

    private static String root(String type) {
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

    /**
     * The body styles (the category) a vehicle of this type can have. The first one is the default.
     */
    public static List<String> styles(String type) {
        switch (type) {
            case "SEDAN":
                return SEDAN_STYLES;
            case "SUV":
                return SUV_STYLES;
            case "MOTORCYCLE":
                return BIKE_STYLES;
            default:
                throw new IllegalArgumentException("Unknown vehicle type: " + type);
        }
    }

    /**
     * The transmissions that fit a type and a body style. A scooter is twist-and-go, an underbone
     * has no clutch lever, an electric vehicle has no gears. The first one is the default.
     */
    public static List<String> transmissions(String type, String style) {
        if ("MOTORCYCLE".equals(type)) {
            if (SCOOTER.equals(style) || ELECTRIC_BIKE.equals(style)) {
                return List.of(BIKE_AUTOMATIC);
            }
            return UNDERBONE.equals(style) ? List.of(BIKE_SEMI) : BIKE_GEARS;
        }
        if (style != null && style.startsWith("Electric")) {
            return List.of(AUTOMATIC);
        }
        return style != null && style.startsWith("Hybrid") ? HYBRID_GEARS : CAR_GEARS;
    }

    /** The saved category path: the type, the body style and the transmission. */
    public static String categoryPath(String type, String style, String transmission) {
        return root(type) + SEP + style + SEP + transmission;
    }

    /**
     * The category paths offered for a type: every body style with every transmission that fits
     * it. The first one is the default.
     */
    public static List<String> categoryOptions(String type) {
        List<String> paths = new ArrayList<>();
        for (String style : styles(type)) {
            for (String gear : transmissions(type, style)) {
                paths.add(categoryPath(type, style, gear));
            }
        }
        return paths;
    }

    /**
     * The body style inside a saved category path, or null for a path from an older version.
     */
    public static String styleOf(String type, String categoryPath) {
        for (String style : styles(type)) {
            if (categoryPath != null && categoryPath.startsWith(root(type) + SEP + style + SEP)) {
                return style;
            }
        }
        return null;
    }

    /**
     * The transmission inside a saved category path, or null for a path from an older version.
     */
    public static String transmissionOf(String type, String categoryPath) {
        String style = styleOf(type, categoryPath);
        if (style == null) {
            return null;
        }
        String gear = categoryPath.substring((root(type) + SEP + style + SEP).length());
        return transmissions(type, style).contains(gear) ? gear : null;
    }

    /**
     * True if the category path belongs to the type: a body style with a transmission that fits
     * it, or a path from an older version.
     */
    public static boolean isCategoryOf(String type, String categoryPath) {
        return categoryOptions(type).contains(categoryPath)
                || (OLD_PATHS.contains(categoryPath) && categoryPath.startsWith(root(type)));
    }
}
