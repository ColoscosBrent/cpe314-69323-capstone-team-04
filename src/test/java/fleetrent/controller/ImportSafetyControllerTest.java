package fleetrent.controller;

import fleetrent.dao.Database;
import fleetrent.model.ImportResult;
import fleetrent.service.AuthService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// The owner cannot import the same file twice (even under another name) and can cancel a big
// import. Both are checked through the ImportController, like the screen does it.
class ImportSafetyControllerTest {
    private static final int ROWS = 30;
    private static final String QUESTION = AuthService.SECURITY_QUESTIONS.get(0);

    @TempDir
    Path folder;

    private Database db;
    private ImportController importer;

    @BeforeEach
    void setUp() throws Exception {
        db = new Database("jdbc:sqlite::memory:");
        db.createTables();
        Session session = new Session();
        AuthController auth = new AuthController(new AuthService(db), session);
        importer = new ImportController(db, session);
        auth.signUpOwner("owner01", "Passw0rdX", "Santos, Maria Lopez", "+63 9171234567",
                QUESTION, "Rex");
        auth.login("owner01", "Passw0rdX");
        importer.addDemoVehicles();
    }

    @AfterEach
    void tearDown() throws SQLException {
        db.close();
    }

    // A small file with 30 rentals of the first demo vehicle, one rental per day.
    private Path sampleFile(String name) throws Exception {
        Path file = folder.resolve(name);
        StringBuilder text = new StringBuilder("plateNumber,renterName,startDate,endDate,"
                + "milesDriven\n");
        LocalDate first = LocalDate.of(2025, 1, 1);
        for (int i = 0; i < ROWS; i++) {
            text.append("DEMO-001,Ana Reyes,").append(first.plusDays(i)).append(',')
                    .append(first.plusDays(i + 1)).append(",10\n");
        }
        Files.writeString(file, text.toString());
        return file;
    }

    @Test
    void theSameFileCannotBeImportedTwice() throws Exception {
        Path file = sampleFile("first.csv");
        ImportResult first = importer.importFile(file, p -> { });
        assertEquals(ROWS, first.getImported());
        int rentals = db.getAllRentals().size();
        RuleException refused = assertThrows(RuleException.class,
                () -> importer.importFile(file, p -> { }));
        assertTrue(refused.getMessage().contains("already imported"));
        assertEquals(rentals, db.getAllRentals().size());
        assertEquals(1, importer.history().size());
    }

    @Test
    void aRenamedCopyOfTheSameFileIsRefusedToo() throws Exception {
        Path file = sampleFile("first.csv");
        importer.importFile(file, p -> { });
        Path copy = folder.resolve("copy of first.csv");
        Files.copy(file, copy);
        assertThrows(RuleException.class, () -> importer.importFile(copy, p -> { }));
    }

    @Test
    void aDifferentFileCanBeImported() throws Exception {
        importer.importFile(sampleFile("first.csv"), p -> { });
        Path other = folder.resolve("other.csv");
        Files.writeString(other, "plateNumber,renterName,startDate,endDate,milesDriven\n"
                + "DEMO-001,Zed Sy,2025-01-01,2025-01-03,12\n");
        ImportResult result = importer.importFile(other, p -> { });
        assertEquals(1, result.getImported());
        assertEquals(2, importer.history().size());
    }

    @Test
    void cancellingKeepsWhatWasSaved() throws Exception {
        Path file = sampleFile("first.csv");
        AtomicInteger checks = new AtomicInteger();
        ImportResult cut = importer.importFile(file, p -> { }, () -> checks.incrementAndGet() > 5);
        assertTrue(cut.isCancelled());
        assertFalse(importer.history().isEmpty());
        assertEquals(cut.getImported(), db.getAllRentals().size());
    }
}
