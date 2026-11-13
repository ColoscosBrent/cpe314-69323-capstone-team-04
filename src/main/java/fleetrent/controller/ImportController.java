package fleetrent.controller;

import fleetrent.dao.Database;
import fleetrent.model.ImportBatch;
import fleetrent.model.ImportRecord;
import fleetrent.model.ImportResult;
import fleetrent.model.Rental;
import fleetrent.model.Renter;
import fleetrent.model.Vehicle;
import fleetrent.service.RentalCsvExporter;
import fleetrent.service.RentalCsvImporter;
import fleetrent.service.SampleData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/** What the owner's Import tab asks for: demo vehicles, a sample CSV file and the import itself. */
public class ImportController {
    /** The column names the CSV file must start with. */
    public static final String HEADER = RentalCsvImporter.HEADER;
    private static final int SAMPLE_ROWS = 50_000;
    private static final long SAMPLE_SEED = 42L;

    private static final int RECORD_LIMIT = 2000;

    private final Database db;
    private final Session session;

    /** Creates the controller. Only the owner may import, export or read the history. */
    public ImportController(Database db, Session session) {
        this.db = db;
        this.session = session;
    }

    /** Saves the demo vehicles that are not in the fleet yet and returns how many were added. */
    public int addDemoVehicles() throws ControllerException {
        return Guard.database("adding the demo vehicles", () -> {
            int added = 0;
            for (Vehicle v : SampleData.demoVehicles()) {
                if (db.findVehicleByPlate(v.getPlateNumber()) == null) {
                    db.insertVehicle(v);
                    added++;
                }
            }
            return added;
        });
    }

    /** Tells whether the fleet has at least one vehicle (a sample file needs vehicles). */
    public boolean hasVehicles() throws ControllerException {
        return Guard.database("checking the fleet", () -> !db.getAllVehicles().isEmpty());
    }

    /**
     * Writes a practice CSV file with 50,000 made-up rentals (random renter names) for the vehicles
     * in the fleet. It is only for trying a big import. To get your own records use
     * exportRecords.
     */
    public void writeSampleCsv(Path file) throws ControllerException {
        session.requireOwner("create a practice file");
        try {
            List<Vehicle> vehicles = db.getAllVehicles();
            Files.write(file, SampleData.rentalLines(vehicles, SAMPLE_ROWS, SAMPLE_SEED,
                    LocalDate.now()));
        } catch (SQLException | IOException e) {
            throw new ControllerException("Could not create the file: " + e.getMessage(), e);
        }
    }

    /** Imports a CSV file. Slow for big files, so call it from a background job. */
    public ImportResult importFile(Path file,
            IntConsumer progressPercent) throws ControllerException {
        session.requireOwner("import rentals");
        String by = session.current() == null ? null : session.current().getUsername();
        try {
            return new RentalCsvImporter().importFile(file, db, progressPercent, by);
        } catch (SQLException e) {
            throw ControllerException.database("importing the rentals", e);
        } catch (IOException e) {
            throw new ControllerException(e.getMessage(), e);
        }
    }

    /**
     * Writes every rental the app has (imported ones and the ones made in the app) to a CSV file
     * and returns how many rows were written. A value that is not known is left blank.
     */
    public int exportRecords(Path file) throws ControllerException {
        session.requireOwner("export the records");
        try {
            Map<Integer, Vehicle> vehicles = new HashMap<>();
            for (Vehicle v : db.getAllVehiclesIncludingRemoved()) {
                vehicles.put(v.getId(), v);
            }
            Map<Integer, Renter> renters = new HashMap<>();
            for (Renter r : db.getAllRenters()) {
                renters.put(r.getId(), r);
            }
            List<Rental> rentals = db.getAllRentals();
            Files.write(file, RentalCsvExporter.lines(rentals, vehicles, renters),
                    StandardCharsets.UTF_8);
            return rentals.size();
        } catch (SQLException | IOException e) {
            throw new ControllerException("Could not write the file: " + e.getMessage(), e);
        }
    }

    /** Every import so far, the newest first. */
    public List<ImportBatch> history() throws ControllerException {
        session.requireOwner("see the import history");
        return Guard.database("reading the import history", db::getImportBatches);
    }

    /**
     * The rows of one import. Pass "ADDED" or "SKIPPED" to see only those, or null for all. The
     * list stops at 2,000 rows to keep the screen quick.
     */
    public List<ImportRecord> records(int batchId, String status) throws ControllerException {
        session.requireOwner("see the import history");
        return Guard.database("reading the import records",
                () -> db.getImportRecords(batchId, status, RECORD_LIMIT));
    }

    /** The most rows records() returns for one import. */
    public static int recordLimit() {
        return RECORD_LIMIT;
    }
}
