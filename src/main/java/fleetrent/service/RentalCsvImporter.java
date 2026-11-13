package fleetrent.service;

import fleetrent.dao.Database;
import fleetrent.model.ImportResult;
import fleetrent.model.Rental;
import fleetrent.model.RentalStatus;
import fleetrent.model.Renter;
import fleetrent.model.Vehicle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * Reads a CSV file of finished rentals and saves them. Columns, in this order:
 * plateNumber,renterName,startDate,endDate,milesDriven      (dates look like 2026-03-15) The plate
 * must belong to a vehicle that is already in the database. A new renter name is added.
 */
public class RentalCsvImporter {
    public static final String HEADER = "plateNumber,renterName,startDate,endDate,milesDriven";
    private static final int BATCH_SIZE = 100;
    private static final int MAX_ERRORS_KEPT = 50;

    /**
     * One checked row, before it is saved.
     */
    public static class Row {
        public final Vehicle vehicle;
        public final String renterName;
        public final LocalDate start;
        public final LocalDate end;
        public final int miles;

        Row(Vehicle vehicle, String renterName, LocalDate start, LocalDate end, int miles) {
            this.vehicle = vehicle;
            this.renterName = renterName;
            this.start = start;
            this.end = end;
            this.miles = miles;
        }
    }

    /**
     * Checks one line. Throws IllegalArgumentException with a plain message when the line is bad.
     */
    public static Row parseRow(String line, Map<String, Vehicle> vehiclesByPlate) {
        String[] parts = line.split(",", -1);
        if (parts.length != 5) {
            throw new IllegalArgumentException("expected 5 columns but found " + parts.length);
        }
        String plate = parts[0].trim().toUpperCase(java.util.Locale.ROOT);
        String renterName = parts[1].trim();
        if (plate.isEmpty() || renterName.isEmpty()) {
            throw new IllegalArgumentException("plate number and renter name are required");
        }
        Vehicle vehicle = vehiclesByPlate.get(plate);
        if (vehicle == null) {
            throw new IllegalArgumentException("no vehicle with plate " + plate);
        }
        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(parts[2].trim());
            end = LocalDate.parse(parts[3].trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("dates must look like 2026-03-15");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end date is before start date");
        }
        int miles;
        try {
            miles = Integer.parseInt(parts[4].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("miles must be a whole number");
        }
        if (miles < 0) {
            throw new IllegalArgumentException("miles cannot be negative");
        }
        return new Row(vehicle, renterName, start, end, miles);
    }

    /** Reads the CSV file and saves the valid rentals. Reports the progress while it works. */
    public ImportResult importFile(Path file, Database db, IntConsumer progressPercent)
            throws IOException, SQLException {
        return importLines(Files.readAllLines(file, StandardCharsets.UTF_8), db, progressPercent);
    }

    /**
     * Saves every good line, skips bad ones (and remembers why). Calls progressPercent(0..100).
     * This is slow for big files, so the GUI calls it from a background thread.
     */
    public ImportResult importLines(List<String> lines, Database db, IntConsumer progressPercent)
            throws SQLException {
        ImportResult result = new ImportResult();
        Map<String, Vehicle> byPlate = new HashMap<>();
        for (Vehicle v : db.getAllVehicles()) {
            if (v.getPlateNumber() != null) {
                byPlate.put(v.getPlateNumber().trim().toUpperCase(java.util.Locale.ROOT), v);
            }
        }
        Map<String, Integer> renterIds = new HashMap<>();
        for (Renter r : db.getAllRenters()) {
            renterIds.putIfAbsent(r.getName().trim().toLowerCase(java.util.Locale.ROOT), r.getId());
        }

        List<Rental> batch = new ArrayList<>();
        int total = lines.size();
        for (int i = 0; i < total; i++) {
            String line = lines.get(i);
            if (i == 0 && line.startsWith("\uFEFF")) {
                line = line.substring(1); // Excel's "CSV UTF-8" puts an invisible mark at the very start
            }
            boolean isHeader = i == 0 && line.trim().toLowerCase(java.util.Locale.ROOT).startsWith("platenumber");
            if (!line.isBlank() && !isHeader) {
                try {
                    Row row = parseRow(line, byPlate);
                    String key = row.renterName.toLowerCase(java.util.Locale.ROOT);
                    Integer renterId = renterIds.get(key);
                    if (renterId == null) {
                        renterId = db.insertRenter(new Renter(0, row.renterName, null, null, null));
                        renterIds.put(key, renterId);
                    }
                    batch.add(new Rental(0, renterId, row.vehicle.getId(), row.start, row.end,
                            row.end, row.miles, RentalStatus.RETURNED));
                } catch (IllegalArgumentException e) {
                    result.countSkipped();
                    if (result.getErrors().size() < MAX_ERRORS_KEPT) {
                        result.getErrors().add("Line " + (i + 1) + ": " + e.getMessage());
                    }
                }
            }
            if (batch.size() >= BATCH_SIZE) {
                result.addImported(db.insertRentalBatch(batch));
                batch.clear();
            }
            if (progressPercent != null && (i % 50 == 0 || i == total - 1)) {
                progressPercent.accept(total == 0 ? 100 : (int) ((i + 1) * 100L / total));
            }
        }
        if (!batch.isEmpty()) {
            result.addImported(db.insertRentalBatch(batch));
        }
        if (progressPercent != null) {
            progressPercent.accept(100);
        }
        return result;
    }
}
