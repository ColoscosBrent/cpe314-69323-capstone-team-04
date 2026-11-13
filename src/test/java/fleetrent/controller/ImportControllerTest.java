package fleetrent.controller;

import fleetrent.dao.Database;
import fleetrent.model.ImportRecord;
import fleetrent.model.ImportResult;
import fleetrent.service.AuthService;
import fleetrent.service.CsvLine;
import fleetrent.service.RentalCsvImporter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// The import, the import history and the export belong to the owner only.
class ImportControllerTest {
    private static final String QUESTION = AuthService.SECURITY_QUESTIONS.get(0);

    @TempDir
    Path folder;

    private Database db;
    private AuthController auth;
    private ImportController importer;

    @BeforeEach
    void setUp() throws Exception {
        db = new Database("jdbc:sqlite::memory:");
        db.createTables();
        Session session = new Session();
        auth = new AuthController(new AuthService(db), session);
        importer = new ImportController(db, session);
        auth.signUpOwner("owner01", "Passw0rdX", "Santos, Maria Lopez", "+63 9171234567",
                QUESTION, "Rex");
        auth.signOut();
    }

    @AfterEach
    void tearDown() throws SQLException {
        db.close();
    }

    @Test
    void nobodySignedInCannotImportSeeTheHistoryOrExport() {
        assertThrows(RuleException.class, () -> importer.importFile(folder.resolve("a.csv"),
                p -> { }));
        assertThrows(RuleException.class, () -> importer.history());
        assertThrows(RuleException.class, () -> importer.exportRecords(folder.resolve("o.csv")));
    }

    @Test
    void theOwnerImportsThenSeesTheHistoryWithTheNameOfTheOwner() throws Exception {
        auth.login("owner01", "Passw0rdX");
        assertEquals(6, importer.addDemoVehicles());
        Path practice = folder.resolve("practice.csv");
        importer.writeSampleCsv(practice);
        List<String> lines = Files.readAllLines(practice);
        assertEquals(RentalCsvImporter.HEADER, lines.get(0));
        assertEquals(13, CsvLine.parse(lines.get(1)).size());
        Path small = folder.resolve("small.csv");
        Files.write(small, lines.subList(0, 21));
        ImportResult result = importer.importFile(small, p -> { });
        assertEquals(20, result.getImported());
        assertEquals(1, importer.history().size());
        assertEquals("owner01", importer.history().get(0).importedBy());
        assertEquals("small.csv", importer.history().get(0).fileName());
        List<ImportRecord> rows = importer.records(result.getBatchId(), null);
        assertEquals(20, rows.size());
        assertTrue(rows.get(0).added());
        assertTrue(rows.get(0).vehicleType() != null && rows.get(0).make() != null);
    }

    @Test
    void theExportHoldsExactlyTheRentalsThatAreSaved() throws Exception {
        auth.login("owner01", "Passw0rdX");
        importer.addDemoVehicles();
        Path small = folder.resolve("small.csv");
        Path practice = folder.resolve("practice.csv");
        importer.writeSampleCsv(practice);
        Files.write(small, Files.readAllLines(practice).subList(0, 11));
        importer.importFile(small, p -> { });
        Path out = folder.resolve("out.csv");
        assertEquals(10, importer.exportRecords(out));
        assertEquals(11, Files.readAllLines(out).size());
    }
}
