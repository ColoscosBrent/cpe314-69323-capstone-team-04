package fleetrent.controller;

import fleetrent.dao.Database;
import fleetrent.model.ImportResult;
import fleetrent.model.Vehicle;
import fleetrent.service.RentalCsvImporter;
import fleetrent.service.SampleData;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.function.IntConsumer;

/** What the owner's Import tab asks for: demo vehicles, a sample CSV file and the import itself. */
public class ImportController {
    /** The column names the CSV file must start with. */
    public static final String HEADER = RentalCsvImporter.HEADER;
    private static final int SAMPLE_ROWS = 50_000;
    private static final long SAMPLE_SEED = 42L;

    private final Database db;

    /** Creates the controller. */
    public ImportController(Database db) {
        this.db = db;
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

    /** Writes a CSV file with 50,000 sample rentals for the vehicles in the fleet. */
    public void writeSampleCsv(Path file) throws ControllerException {
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
        try {
            return new RentalCsvImporter().importFile(file, db, progressPercent);
        } catch (SQLException e) {
            throw ControllerException.database("importing the rentals", e);
        } catch (IOException e) {
            throw new ControllerException(e.getMessage(), e);
        }
    }
}
