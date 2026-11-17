package fleetrent.service;

import fleetrent.dao.Database;
import fleetrent.model.ImportBatch;
import fleetrent.model.ImportResult;
import fleetrent.model.VehicleFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// A big import can be cancelled, a failure in the middle keeps the real counts, and the file
// fingerprint is saved in the Import history.
class ImportSafetyTest {
    private static final int ROWS = 450;

    @TempDir
    Path folder;

    private String url;
    private Database db;

    @BeforeEach
    void setUp() throws SQLException {
        url = "jdbc:sqlite:" + folder.resolve("import.db").toAbsolutePath();
        db = new Database(url);
        db.createTables();
        db.insertVehicle(VehicleFactory.create(0, "SEDAN", "Toyota", "Vios", 2020, "ABC 123",
                "White", 1000, 0, null, "Car > Sedan"));
    }

    @AfterEach
    void tearDown() throws SQLException {
        db.close();
    }

    private static List<String> lines(int rows, int miles777At) {
        List<String> lines = new ArrayList<>();
        lines.add(RentalCsvImporter.HEADER);
        LocalDate first = LocalDate.of(2025, 1, 1);
        for (int i = 0; i < rows; i++) {
            LocalDate start = first.plusDays(i);
            int miles = i + 1 == miles777At ? 777 : 10;
            lines.add("ABC 123,Ana Reyes," + start + "," + start.plusDays(1) + "," + miles);
        }
        return lines;
    }

    @Test
    void cancellingStopsTheImportAndKeepsTheSavedRows() throws Exception {
        AtomicInteger checks = new AtomicInteger();
        ImportResult result = new RentalCsvImporter().importLines(lines(ROWS, 0), db, p -> { },
                "big.csv", "owner01", "hash1", () -> checks.incrementAndGet() > 250);
        assertTrue(result.isCancelled());
        assertTrue(result.getImported() > 0 && result.getImported() < ROWS);
        ImportBatch batch = db.getImportBatches().get(0);
        assertEquals(result.getImported(), batch.rowsAdded());
        assertEquals(result.getImported(), db.getAllRentals().size());
    }

    @Test
    void anImportThatIsNotCancelledIsNotMarkedCancelled() throws Exception {
        ImportResult result = new RentalCsvImporter().importLines(lines(20, 0), db, p -> { },
                "small.csv", "owner01", null, () -> false);
        assertFalse(result.isCancelled());
        assertEquals(20, result.getImported());
    }

    @Test
    void aFailureInTheMiddleKeepsTheRealCountsAndSaysHowManyWereSaved() throws Exception {
        try (Connection other = DriverManager.getConnection(url);
                Statement st = other.createStatement()) {
            st.execute("CREATE TRIGGER stopAt777 BEFORE INSERT ON Rental WHEN NEW.milesDriven = "
                    + "777 BEGIN SELECT RAISE(ABORT, 'boom'); END");
        }
        ImportStoppedException stopped = assertThrows(ImportStoppedException.class,
                () -> new RentalCsvImporter().importLines(lines(ROWS, 150), db, p -> { },
                        "broken.csv", "owner01", "hash2", () -> false));
        assertEquals(100, stopped.getSaved());
        assertTrue(stopped.getMessage().contains("100"));
        ImportBatch batch = db.getImportBatches().get(0);
        assertEquals(200, batch.rowsRead());
        assertEquals(100, batch.rowsAdded());
        assertEquals(100, db.getAllRentals().size());
    }

    @Test
    void theFileFingerprintIsSavedAndFound() throws Exception {
        assertNull(db.findImportBatchByHash("abc"));
        new RentalCsvImporter().importLines(lines(5, 0), db, p -> { }, "a.csv", "owner01",
                "abc", () -> false);
        ImportBatch found = db.findImportBatchByHash("abc");
        assertNotNull(found);
        assertEquals("a.csv", found.fileName());
        assertNull(db.findImportBatchByHash("other"));
    }

    @Test
    void importsWithoutAFingerprintNeverMatch() throws Exception {
        new RentalCsvImporter().importLines(lines(5, 0), db, p -> { }, "a.csv", "owner01");
        assertNull(db.findImportBatchByHash("null"));
    }
}
