package fleetrent.service;

import fleetrent.dao.Database;
import fleetrent.model.ImportRecord;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * Reads a CSV file of finished rentals and saves them. Columns, in this order:
 * plateNumber,vehicleType,make,model,year,renterName,renterPhone,renterEmail,renterAddress,
 * startDate,endDate,milesDriven,status (dates look like 2026-03-15). The plate must belong to a
 * vehicle that is already in the fleet. The vehicle type, make, model, year, phone, email, address
 * and status may be left blank; when the type, make, model or year is filled in, it must be the
 * same as the vehicle with that plate. A new renter name is added. The old file with only five
 * columns (plateNumber,renterName,startDate,endDate,milesDriven) still works. Every row, saved or
 * skipped, is written to the Import history.
 */
public class RentalCsvImporter {
    private static final int COLUMN_COUNT = 13;
    private static final int OLD_COLUMN_COUNT = 5;
    private static final int PLATE = 0;
    private static final int TYPE = 1;
    private static final int MAKE = 2;
    private static final int MODEL = 3;
    private static final int YEAR = 4;
    private static final int RENTER = 5;
    private static final int PHONE = 6;
    private static final int EMAIL = 7;
    private static final int ADDRESS = 8;
    private static final int START = 9;
    private static final int END = 10;
    private static final int MILES = 11;
    private static final int STATUS = 12;
    private static final int PROGRESS_EVERY = 50;
    private static final int PERCENT_FULL = 100;
    private static final int BATCH_SIZE = 100;
    private static final int RECORD_BATCH_SIZE = 500;
    private static final int MAX_ERRORS_KEPT = 50;
    /** The first line of a file this importer reads (and of the file the app exports). */
    public static final String HEADER = "plateNumber,vehicleType,make,model,year,renterName,"
            + "renterPhone,renterEmail,renterAddress,startDate,endDate,milesDriven,status";

    /**
     * One checked row, before it is saved.
     */
    public static class Row {
        public final Vehicle vehicle;
        public final String renterName;
        public final String renterPhone;
        public final String renterEmail;
        public final String renterAddress;
        public final LocalDate start;
        public final LocalDate end;
        public final int miles;

        Row(Vehicle vehicle, String[] renter, LocalDate start, LocalDate end, int miles) {
            this.vehicle = vehicle;
            this.renterName = renter[0];
            this.renterPhone = renter[1];
            this.renterEmail = renter[2];
            this.renterAddress = renter[3];
            this.start = start;
            this.end = end;
            this.miles = miles;
        }
    }

    // A value of the line, or "" when the line is shorter. Old five-column lines are turned into
    // the new order first (see widen).
    private static String col(List<String> cols, int index) {
        return index < cols.size() ? cols.get(index).trim() : "";
    }

    // Turns an old five-column line into the 13 columns, with the new ones blank.
    private static List<String> widen(List<String> cols) {
        if (cols.size() != OLD_COLUMN_COUNT) {
            return cols;
        }
        List<String> wide = new ArrayList<>();
        for (int i = 0; i < COLUMN_COUNT; i++) {
            wide.add("");
        }
        wide.set(PLATE, cols.get(0));
        wide.set(RENTER, cols.get(1));
        wide.set(START, cols.get(2));
        wide.set(END, cols.get(3));
        wide.set(MILES, cols.get(4));
        return wide;
    }

    /**
     * Checks one line. Throws IllegalArgumentException with a plain message when the line is bad.
     */
    public static Row parseRow(String line, Map<String, Vehicle> vehiclesByPlate) {
        List<String> cols = CsvLine.parse(line);
        if (cols.size() != COLUMN_COUNT && cols.size() != OLD_COLUMN_COUNT) {
            throw new IllegalArgumentException("expected 13 columns but found " + cols.size());
        }
        cols = widen(cols);
        String plate = col(cols, PLATE).toUpperCase(Locale.ROOT);
        String renterName = col(cols, RENTER);
        if (plate.isEmpty() || renterName.isEmpty()) {
            throw new IllegalArgumentException("plate number and renter name are required");
        }
        Vehicle vehicle = vehiclesByPlate.get(plate);
        if (vehicle == null) {
            throw new IllegalArgumentException("no vehicle with plate " + plate);
        }
        checkVehicleColumns(cols, vehicle);
        String[] renter = checkRenterColumns(cols, renterName);
        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(col(cols, START));
            end = LocalDate.parse(col(cols, END));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("dates must look like 2026-03-15");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end date is before start date");
        }
        String status = col(cols, STATUS);
        if (!status.isEmpty() && !"RETURNED".equalsIgnoreCase(status)) {
            throw new IllegalArgumentException("only finished rentals (RETURNED) can be imported");
        }
        return new Row(vehicle, renter, start, end, parseMiles(col(cols, MILES)));
    }

    private static int parseMiles(String text) {
        int miles;
        try {
            miles = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("miles must be a whole number");
        }
        if (miles < 0) {
            throw new IllegalArgumentException("miles cannot be negative");
        }
        return miles;
    }

    // The type, make, model and year are optional, but when they are filled in they must be the
    // car with that plate.
    private static void checkVehicleColumns(List<String> cols, Vehicle vehicle) {
        String type = col(cols, TYPE);
        if (!type.isEmpty() && !type.equalsIgnoreCase(vehicle.getType())) {
            throw new IllegalArgumentException("plate " + vehicle.getPlateNumber() + " is a "
                    + vehicle.getType() + ", not a " + type);
        }
        String make = col(cols, MAKE);
        String model = col(cols, MODEL);
        if ((!make.isEmpty() && !Validators.sameName(make, vehicle.getMake()))
                || (!model.isEmpty() && !Validators.sameName(model, vehicle.getModel()))) {
            throw new IllegalArgumentException("plate " + vehicle.getPlateNumber() + " is a "
                    + vehicle.getMake() + " " + vehicle.getModel() + ", not " + make + " "
                    + model);
        }
        String year = col(cols, YEAR);
        if (!year.isEmpty() && !year.equals(String.valueOf(vehicle.getYear()))) {
            throw new IllegalArgumentException("plate " + vehicle.getPlateNumber() + " is a "
                    + vehicle.getYear() + " model, not " + year);
        }
    }

    // The renter columns are optional. A filled-in phone, email or address must be valid.
    private static String[] checkRenterColumns(List<String> cols, String renterName) {
        String phone = col(cols, PHONE);
        String email = col(cols, EMAIL);
        String address = col(cols, ADDRESS);
        if (!phone.isEmpty() && PhoneNumber.checkSaved(phone) != null) {
            throw new IllegalArgumentException("renter phone must look like +63 9171234567");
        }
        if (!email.isEmpty() && Validators.check(Validators.Kind.EMAIL, email, false) != null) {
            throw new IllegalArgumentException("renter email is not valid");
        }
        if (!address.isEmpty() && AddressRules.checkSaved(address) != null) {
            throw new IllegalArgumentException("renter address must be: street, city, country");
        }
        return new String[] {renterName, blankToNull(phone), blankToNull(email),
                blankToNull(address)};
    }

    private static String blankToNull(String text) {
        return text.isEmpty() ? null : text;
    }

    /** Reads the CSV file and saves the valid rentals. Reports the progress while it works. */
    public ImportResult importFile(Path file, Database db, IntConsumer progressPercent)
            throws IOException, SQLException {
        return importFile(file, db, progressPercent, null);
    }

    /**
     * Same as above, and the Import history also says who imported the file (importedBy may be
     * null).
     */
    public ImportResult importFile(Path file, Database db, IntConsumer progressPercent,
            String importedBy) throws IOException, SQLException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        return importLines(lines, db, progressPercent, String.valueOf(file.getFileName()),
                importedBy);
    }

    /**
     * Saves every good line, skips bad ones (and remembers why). Calls progressPercent(0..100).
     * This is slow for big files, so the GUI calls it from a background thread.
     */
    public ImportResult importLines(List<String> lines, Database db, IntConsumer progressPercent)
            throws SQLException {
        return importLines(lines, db, progressPercent, "CSV text", null);
    }

    /** The full import: it also writes the file name and every row to the Import history. */
    public ImportResult importLines(List<String> lines, Database db, IntConsumer progressPercent,
            String fileName, String importedBy) throws SQLException {
        ImportResult result = new ImportResult();
        int batchId = db.insertImportBatch(fileName, importedBy, LocalDateTime.now());
        result.setBatchId(batchId);
        Map<String, Vehicle> byPlate = new HashMap<>();
        for (Vehicle v : db.getAllVehicles()) {
            if (v.getPlateNumber() != null) {
                byPlate.put(v.getPlateNumber().trim().toUpperCase(Locale.ROOT), v);
            }
        }
        Map<String, Integer> renterIds = new HashMap<>();
        for (Renter r : db.getAllRenters()) {
            renterIds.putIfAbsent(r.getName().trim().toLowerCase(Locale.ROOT), r.getId());
        }
        List<Rental> batch = new ArrayList<>();
        List<ImportRecord> records = new ArrayList<>();
        int rowsRead = 0;
        int total = lines.size();
        for (int i = 0; i < total; i++) {
            String line = i == 0 && lines.get(i).startsWith("﻿") ? lines.get(i).substring(1)
                    : lines.get(i);
            if (!line.isBlank() && !isHeader(i, line)) {
                rowsRead++;
                saveOrSkip(line, i + 1, byPlate, renterIds, db, batch, records, result);
            }
            if (batch.size() >= BATCH_SIZE || records.size() >= RECORD_BATCH_SIZE) {
                flush(db, batchId, batch, records, result);
            }
            if (progressPercent != null && (i % PROGRESS_EVERY == 0 || i == total - 1)) {
                progressPercent.accept(total == 0 ? PERCENT_FULL
                        : (int) ((i + 1) * (long) PERCENT_FULL / total));
            }
        }
        flush(db, batchId, batch, records, result);
        db.finishImportBatch(batchId, rowsRead, result.getImported(), result.getSkipped());
        if (progressPercent != null) {
            progressPercent.accept(PERCENT_FULL);
        }
        return result;
    }

    private static boolean isHeader(int index, String line) {
        return index == 0 && line.trim().toLowerCase(Locale.ROOT).startsWith("platenumber");
    }

    // Checks one line. A good line joins the batch; a bad line is counted and remembered.
    private void saveOrSkip(String line, int lineNo, Map<String, Vehicle> byPlate,
            Map<String, Integer> renterIds, Database db, List<Rental> batch,
            List<ImportRecord> records, ImportResult result) throws SQLException {
        try {
            Row row = parseRow(line, byPlate);
            String key = row.renterName.toLowerCase(Locale.ROOT);
            Integer renterId = renterIds.get(key);
            if (renterId == null) {
                renterId = db.insertRenter(new Renter(0, row.renterName, row.renterPhone,
                        row.renterEmail, row.renterAddress));
                renterIds.put(key, renterId);
            }
            batch.add(new Rental(0, renterId, row.vehicle.getId(), row.start, row.end, row.end,
                    row.miles, RentalStatus.RETURNED));
            records.add(record(lineNo, line, row, null));
        } catch (IllegalArgumentException e) {
            result.countSkipped();
            if (result.getErrors().size() < MAX_ERRORS_KEPT) {
                result.getErrors().add("Line " + lineNo + ": " + e.getMessage());
            }
            records.add(record(lineNo, line, null, e.getMessage()));
        }
    }

    // Saves the waiting rentals first, then the history rows that describe them.
    private void flush(Database db, int batchId, List<Rental> batch, List<ImportRecord> records,
            ImportResult result) throws SQLException {
        if (!batch.isEmpty()) {
            result.addImported(db.insertRentalBatch(batch));
            batch.clear();
        }
        if (!records.isEmpty()) {
            db.insertImportRecords(batchId, records);
            records.clear();
        }
    }

    // The history row of a line. A saved row shows the real car; a skipped row shows what the file
    // said, as far as it can be read.
    private static ImportRecord record(int lineNo, String line, Row row, String problem) {
        if (row != null) {
            Vehicle v = row.vehicle;
            return new ImportRecord(0, 0, lineNo, v.getPlateNumber(), v.getType(), v.getMake(),
                    v.getModel(), v.getYear() > 0 ? v.getYear() : null, row.renterName, row.start,
                    row.end, row.miles, true, null);
        }
        List<String> cols = widen(safeParse(line));
        return new ImportRecord(0, 0, lineNo, nullIfBlank(col(cols, PLATE)),
                nullIfBlank(col(cols, TYPE)), nullIfBlank(col(cols, MAKE)),
                nullIfBlank(col(cols, MODEL)), asInt(col(cols, YEAR)),
                nullIfBlank(col(cols, RENTER)), asDate(col(cols, START)), asDate(col(cols, END)),
                asInt(col(cols, MILES)), false, problem);
    }

    private static List<String> safeParse(String line) {
        try {
            return CsvLine.parse(line);
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }

    private static String nullIfBlank(String text) {
        return text.isEmpty() ? null : text;
    }

    private static Integer asInt(String text) {
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate asDate(String text) {
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
