package fleetrent.controller;

import fleetrent.dao.Database;
import fleetrent.model.ImportBatch;
import fleetrent.model.ImportRecord;
import fleetrent.model.ImportResult;
import fleetrent.model.Rental;
import fleetrent.model.Renter;
import fleetrent.model.Vehicle;
import fleetrent.service.ErrorMessages;
import fleetrent.service.ImportStoppedException;
import fleetrent.service.RentalCsvExporter;
import fleetrent.service.RentalCsvImporter;
import fleetrent.service.SampleData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import javafx.application.Platform;

/** What the owner's Import tab asks for: demo vehicles, a sample CSV file and the import itself. */
public class ImportController extends ObservableController<ImportBatch> {
    private static final String SLOT_HISTORY = "history";
    private static final String SLOT_RECORDS = "records";
    private static final double PERCENT = 100.0;
    /** The column names the CSV file must start with. */
    public static final String HEADER = RentalCsvImporter.HEADER;
    private static final int SAMPLE_ROWS = 50_000;
    private static final long SAMPLE_SEED = 42L;

    private static final int RECORD_LIMIT = 2000;
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM yyyy 'at' HH:mm");

    private final Database db;
    private final Session session;
    // Set by cancelImport. The running import checks it after every row.
    private volatile boolean cancelRequested;

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
            throw new ControllerException(ErrorMessages.forFile("creating the file", ioOrNull(e)),
                    e);
        }
    }

    /**
     * Imports a CSV file in the background. progress gets a value from 0 to 1 on the JavaFX
     * thread; done gets the result (also after cancelImport); a problem goes to failed.
     */
    public void startImport(Path file, DoubleConsumer progress, Consumer<ImportResult> done,
            Consumer<String> failed) {
        cancelRequested = false;
        runTask(null, () -> importFile(file,
                percent -> Platform.runLater(() -> progress.accept(percent / PERCENT)),
                () -> cancelRequested), done, failed);
    }

    /** Asks the running import to stop. The rows that are already saved stay. */
    public void cancelImport() {
        cancelRequested = true;
    }

    /** Imports a CSV file. Slow for big files, so call it from a background job. */
    public ImportResult importFile(Path file,
            IntConsumer progressPercent) throws ControllerException {
        return importFile(file, progressPercent, () -> false);
    }

    /**
     * Imports a CSV file. The import stops, keeping what is saved, when cancelled returns true. A
     * file with the same content as an earlier import is refused, so no rental is added twice.
     */
    public ImportResult importFile(Path file, IntConsumer progressPercent,
            BooleanSupplier cancelled) throws ControllerException {
        session.requireOwner("import rentals");
        String by = session.current() == null ? null : session.current().getUsername();
        try {
            String hash = fingerprint(file);
            ImportBatch before = db.findImportBatchByHash(hash);
            if (before != null) {
                throw new RuleException("This file was already imported on "
                        + before.importedOn().format(WHEN) + " (" + before.rowsAdded()
                        + " rentals added). Importing it again would add the same rentals "
                        + "twice.");
            }
            return new RentalCsvImporter().importFile(file, db, progressPercent, by, hash,
                    cancelled);
        } catch (ImportStoppedException e) {
            throw new ControllerException(e.getMessage() + " "
                    + ErrorMessages.forDatabase("importing the rentals",
                            (SQLException) e.getCause())
                    + " The saved rows are listed in the Import history.", e);
        } catch (SQLException e) {
            throw ControllerException.database("importing the rentals", e);
        } catch (IOException e) {
            throw new ControllerException(ErrorMessages.forFile("reading the file", e), e);
        }
    }

    // A fingerprint of the file content (SHA-256), so the same file is found even if it was
    // renamed.
    private static String fingerprint(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is not available", e);
        }
    }

    private static IOException ioOrNull(Exception e) {
        return e instanceof IOException io ? io : new IOException(e);
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
            throw new ControllerException(ErrorMessages.forFile("writing the file", ioOrNull(e)),
                    e);
        }
    }

    /** Writes every rental to a CSV file in the background. done gets the number of rows. */
    public void exportRecordsLater(Path file, Consumer<Integer> done, Consumer<String> failed) {
        runTask(null, () -> exportRecords(file), done, failed);
    }

    /**
     * Writes only the rentals that one import saved to a CSV file and returns how many rows were
     * written. The file has the same columns as an import file.
     */
    public int exportImport(int batchId, Path file) throws ControllerException {
        session.requireOwner("export the records");
        try {
            Map<String, Renter> rentersByName = new HashMap<>();
            for (Renter r : db.getAllRenters()) {
                rentersByName.putIfAbsent(r.getName().trim().toLowerCase(Locale.ROOT), r);
            }
            List<ImportRecord> saved = db.getImportRecords(batchId, "ADDED", Integer.MAX_VALUE);
            Files.write(file, RentalCsvExporter.linesOfImport(saved, rentersByName),
                    StandardCharsets.UTF_8);
            return saved.size();
        } catch (SQLException | IOException e) {
            throw new ControllerException(ErrorMessages.forFile("writing the file", ioOrNull(e)),
                    e);
        }
    }

    /** Writes the rentals of one import to a CSV file in the background. */
    public void exportImportLater(int batchId, Path file, Consumer<Integer> done,
            Consumer<String> failed) {
        runTask(null, () -> exportImport(batchId, file), done, failed);
    }

    /**
     * Reloads the list of imports in the background and puts it in items(), the newest first. A
     * problem is put in the status property.
     */
    public void refreshHistory() {
        runTask(SLOT_HISTORY, this::history, items()::setAll, null);
    }

    /** Reads the rows of one import in the background (see records). */
    public void loadRecords(int batchId, String status, Consumer<List<ImportRecord>> show,
            Consumer<String> failed) {
        runTask(SLOT_RECORDS, () -> records(batchId, status), show, failed);
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
