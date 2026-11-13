package fleetrent.service;

import fleetrent.dao.Database;
import fleetrent.model.ImportBatch;
import fleetrent.model.ImportRecord;
import fleetrent.model.ImportResult;
import fleetrent.model.Rental;
import fleetrent.model.Renter;
import fleetrent.model.Vehicle;
import fleetrent.model.VehicleFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImportHistoryAndExportTest {
    private static final String ROW_OK =
            "ABC 123,SEDAN,Toyota,Vios,2020,\"Reyes, Ana Cruz\",+63 9171234567,ana@example.com,"
                    + "\"12 Rizal St, Cebu City, Philippines\",2026-03-01,2026-03-04,250,RETURNED";
    private Database db;

    @BeforeEach
    void setUp() throws SQLException {
        db = new Database("jdbc:sqlite::memory:");
        db.createTables();
        db.insertVehicle(VehicleFactory.create(0, "SEDAN", "Toyota", "Vios", 2020, "ABC 123",
                "White", 1000, 0, null, "Car > Sedan"));
    }

    @AfterEach
    void tearDown() throws SQLException {
        db.close();
    }

    @Test
    void theHeaderHasThirteenColumnsWithTheVehicleType() {
        List<String> cols = CsvLine.parse(RentalCsvImporter.HEADER);
        assertEquals(13, cols.size());
        assertEquals("vehicleType", cols.get(1));
        assertEquals("make", cols.get(2));
        assertEquals("model", cols.get(3));
    }

    @Test
    void everyRowIsRecordedInTheImportHistory() throws SQLException {
        ImportResult result = new RentalCsvImporter().importLines(Arrays.asList(
                RentalCsvImporter.HEADER, ROW_OK,
                "ZZZ 999,SEDAN,Toyota,Vios,2020,Ben,,,,2026-03-01,2026-03-04,10,RETURNED",
                "ABC 123,SUV,Toyota,Vios,2020,Ben,,,,2026-03-01,2026-03-04,10,RETURNED"),
                db, null, "march.csv", "owner01");
        assertEquals(1, result.getImported());
        assertEquals(2, result.getSkipped());
        List<ImportBatch> batches = db.getImportBatches();
        assertEquals(1, batches.size());
        ImportBatch batch = batches.get(0);
        assertEquals("march.csv", batch.fileName());
        assertEquals("owner01", batch.importedBy());
        assertEquals(3, batch.rowsRead());
        assertEquals(1, batch.rowsAdded());
        assertEquals(2, batch.rowsSkipped());
        List<ImportRecord> all = db.getImportRecords(batch.id(), null, 100);
        assertEquals(3, all.size());
        ImportRecord saved = all.get(0);
        assertTrue(saved.added());
        assertEquals("SEDAN", saved.vehicleType());
        assertEquals("Toyota", saved.make());
        assertEquals("Vios", saved.model());
        assertEquals(2020, saved.year());
        assertEquals("Saved", saved.statusLabel());
        assertEquals(1, db.getImportRecords(batch.id(), "ADDED", 100).size());
        List<ImportRecord> skipped = db.getImportRecords(batch.id(), "SKIPPED", 100);
        assertEquals(2, skipped.size());
        assertFalse(skipped.get(0).added());
        assertTrue(skipped.get(0).message().contains("no vehicle"));
        assertTrue(skipped.get(1).message().contains("is a SEDAN, not a SUV"));
    }

    @Test
    void theRecordLimitStopsTheList() throws SQLException {
        new RentalCsvImporter().importLines(Arrays.asList(ROW_OK, ROW_OK, ROW_OK), db, null);
        int batchId = db.getImportBatches().get(0).id();
        assertEquals(2, db.getImportRecords(batchId, null, 2).size());
    }

    @Test
    void theNewestImportIsFirst() throws SQLException {
        RentalCsvImporter importer = new RentalCsvImporter();
        importer.importLines(Arrays.asList(ROW_OK), db, null, "first.csv", "a");
        importer.importLines(Arrays.asList(ROW_OK), db, null, "second.csv", "b");
        assertEquals("second.csv", db.getImportBatches().get(0).fileName());
    }

    @Test
    void aRowWithADifferentCarThanItsPlateIsSkippedWithAReason() throws SQLException {
        ImportResult result = new RentalCsvImporter().importLines(Arrays.asList(
                "ABC 123,SEDAN,Honda,Civic,2020,Ben,,,,2026-03-01,2026-03-04,10,RETURNED",
                "ABC 123,SEDAN,Toyota,Vios,2019,Ben,,,,2026-03-01,2026-03-04,10,RETURNED"),
                db, null);
        assertEquals(0, result.getImported());
        assertEquals(2, result.getSkipped());
    }

    @Test
    void theOldFiveColumnFormatStillWorks() throws SQLException {
        ImportResult result = new RentalCsvImporter().importLines(Arrays.asList(
                "ABC 123,Ana Reyes,2026-03-01,2026-03-04,250"), db, null);
        assertEquals(1, result.getImported());
        assertNull(db.getImportRecords(db.getImportBatches().get(0).id(), null, 10).get(0)
                .message());
    }

    @Test
    void theExportHasOnlyTheRealRentalsAndBlanksForWhatIsNotKnown() throws SQLException {
        new RentalCsvImporter().importLines(Arrays.asList(
                ROW_OK,
                "ABC 123,,,,,Ben Cruz,,,,2026-04-01,2026-04-02,10,",
                "ZZZ 999,SEDAN,Toyota,Vios,2020,Nobody,,,,2026-03-01,2026-03-04,10,RETURNED"),
                db, null);
        Map<Integer, Vehicle> vehicles = new HashMap<>();
        for (Vehicle v : db.getAllVehiclesIncludingRemoved()) {
            vehicles.put(v.getId(), v);
        }
        Map<Integer, Renter> renters = new HashMap<>();
        for (Renter r : db.getAllRenters()) {
            renters.put(r.getId(), r);
        }
        List<Rental> rentals = db.getAllRentals();
        List<String> lines = RentalCsvExporter.lines(rentals, vehicles, renters);
        assertEquals(1 + 2, lines.size(), "the skipped row is not exported");
        assertEquals(RentalCsvImporter.HEADER, lines.get(0));
        List<String> first = CsvLine.parse(lines.get(1));
        assertEquals(13, first.size());
        assertEquals("ABC 123", first.get(0));
        assertEquals("Reyes, Ana Cruz", first.get(5));
        assertEquals("+63 9171234567", first.get(6));
        assertEquals("RETURNED", first.get(12));
        List<String> second = CsvLine.parse(lines.get(2));
        assertEquals("Ben Cruz", second.get(5));
        assertEquals("", second.get(6), "no phone was given, so it stays blank");
        assertEquals("", second.get(7));
        assertEquals("", second.get(8));
    }

    @Test
    void anExportedFileCanBeImportedAgain() throws SQLException {
        new RentalCsvImporter().importLines(Arrays.asList(ROW_OK), db, null);
        Map<Integer, Vehicle> vehicles = new HashMap<>();
        for (Vehicle v : db.getAllVehicles()) {
            vehicles.put(v.getId(), v);
        }
        Map<Integer, Renter> renters = new HashMap<>();
        for (Renter r : db.getAllRenters()) {
            renters.put(r.getId(), r);
        }
        List<String> lines = RentalCsvExporter.lines(db.getAllRentals(), vehicles, renters);
        ImportResult again = new RentalCsvImporter().importLines(lines, db, null);
        assertEquals(1, again.getImported());
        assertEquals(0, again.getSkipped());
    }
}
