package fleetrent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Makes demo data so the dashboard has something to mine. The same seed always gives the same file.
 */
public final class SampleData {
    private static final String[] RENTERS = {"Ana Reyes", "Ben Cruz", "Carla Santos", "Dino Lopez",
            "Ella Tan", "Felix Go", "Gina Uy", "Hugo Lim", "Iris Chua", "Jun Diaz"};

    private SampleData() {
    }

    /**
     * Vehicles for a quick demo. The makes and models are real (they exist in the NHTSA list).
     */
    public static List<Vehicle> demoVehicles() {
        List<Vehicle> list = new ArrayList<>();
        list.add(VehicleFactory.create(0, "SEDAN", "Toyota", "Camry", 2022, "DEMO-001", "White", 1800, 0, null, "Car > Sedan"));
        list.add(VehicleFactory.create(0, "SEDAN", "Honda", "Civic", 2021, "DEMO-002", "Silver", 1600, 0, null, "Car > Sedan"));
        list.add(VehicleFactory.create(0, "SUV", "Ford", "Everest", 2022, "DEMO-003", "Black", 2800, 0, null, "Car > SUV"));
        list.add(VehicleFactory.create(0, "SUV", "Toyota", "RAV4", 2023, "DEMO-004", "Gray", 2600, 0, null, "Car > SUV"));
        list.add(VehicleFactory.create(0, "MOTORCYCLE", "Honda", "CB500F", 2021, "DEMO-005", "Red", 900, 0, null, "Motorcycle"));
        list.add(VehicleFactory.create(0, "MOTORCYCLE", "Yamaha", "MT-07", 2022, "DEMO-006", "Blue", 1100, 0, null, "Motorcycle"));
        return list;
    }

    /**
     * Returns the CSV lines (with header). Dates are spread over the 12 months before endOfPeriod.
     */
    public static List<String> rentalLines(List<Vehicle> vehicles, int rows, long seed, LocalDate endOfPeriod) {
        if (vehicles.isEmpty()) {
            throw new IllegalArgumentException("Add at least one vehicle first.");
        }
        if (rows < 1) {
            throw new IllegalArgumentException("rows must be at least 1");
        }
        Random random = new Random(seed);
        List<String> lines = new ArrayList<>();
        lines.add(RentalCsvImporter.HEADER);
        for (int i = 0; i < rows; i++) {
            Vehicle v = vehicles.get(random.nextInt(vehicles.size()));
            LocalDate start = endOfPeriod.minusDays(10 + random.nextInt(355));
            LocalDate end = start.plusDays(1 + random.nextInt(7));
            int miles = (1 + (end.toEpochDay() > start.toEpochDay() ? (int) (end.toEpochDay() - start.toEpochDay()) : 1))
                    * (40 + random.nextInt(120));
            lines.add(v.getPlateNumber() + "," + RENTERS[random.nextInt(RENTERS.length)] + ","
                    + start + "," + end + "," + miles);
        }
        return lines;
    }

    /** Writes sample rentals to a CSV file. */
    public static void writeRentalCsv(Path file, List<Vehicle> vehicles, int rows, long seed,
                                      LocalDate endOfPeriod) throws IOException {
        Files.write(file, rentalLines(vehicles, rows, seed, endOfPeriod), StandardCharsets.UTF_8);
    }
}
