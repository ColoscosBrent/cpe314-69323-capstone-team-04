package fleetrent.service;

import fleetrent.model.ImportResult;
import fleetrent.dao.Database;
import fleetrent.model.Sedan;
import fleetrent.model.Vehicle;
import fleetrent.model.VehicleFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RentalCsvImporterTest {
    private Database db;
    private Map<String, Vehicle> plates;

    @BeforeEach
    void setUp() throws SQLException {
        db = new Database("jdbc:sqlite::memory:");
        db.createTables();
        db.insertVehicle(VehicleFactory.create(0, "SEDAN", "Toyota", "Vios", 2020, "ABC 123",
                "White", 1000, 0, null, "Car > Sedan"));
        plates = new HashMap<>();
        for (Vehicle v : db.getAllVehicles()) {
            plates.put(v.getPlateNumber().toUpperCase(), v);
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        db.close();
    }

    @Test
    void goodRowIsParsed() {
        RentalCsvImporter.Row row =
                RentalCsvImporter.parseRow("abc 123, Ana Reyes ,2026-03-01,2026-03-04,250", plates);
        assertEquals("Ana Reyes", row.renterName);
        assertEquals(LocalDate.of(2026, 3, 4), row.end);
        assertEquals(250, row.miles);
    }

    @Test
    void badRowsAreRejectedWithAMessage() {
        String[] bad = {
            "ABC 123,Ana,2026-03-01,2026-03-04",
            "ABC 123,Ana,2026-03-01,2026-03-04,10,extra",
            ",Ana,2026-03-01,2026-03-04,10",
            "ABC 123,,2026-03-01,2026-03-04,10",
            "NOPE 999,Ana,2026-03-01,2026-03-04,10",
            "ABC 123,Ana,March 1,2026-03-04,10",
            "ABC 123,Ana,2026-03-05,2026-03-04,10",
            "ABC 123,Ana,2026-03-01,2026-03-04,ten",
            "ABC 123,Ana,2026-03-01,2026-03-04,-5"
        };
        for (String line : bad) {
            assertThrows(IllegalArgumentException.class, () -> RentalCsvImporter.parseRow(line,
                    plates), line);
        }
    }

    @Test
    void importSavesGoodLinesAndSkipsBadOnes() throws SQLException {
        List<String> lines = Arrays.asList(
                RentalCsvImporter.HEADER,
                "ABC 123,Ana Reyes,2026-03-01,2026-03-04,250",
                "",
                "ABC 123,ana reyes,2026-04-01,2026-04-02,100",
                "ZZZ 000,Ben,2026-04-01,2026-04-02,100",
                "garbage");
        List<Integer> progress = new ArrayList<>();
        ImportResult result = new RentalCsvImporter().importLines(lines, db, progress::add);
        assertEquals(2, result.getImported());
        assertEquals(2, result.getSkipped());
        assertEquals(2, result.getErrors().size());
        assertTrue(result.getErrors().get(0).startsWith("Line 5"));
        assertEquals(1, db.getAllRenters().size());
        assertEquals(2, db.getAllRentals().size());
        assertEquals(350, db.getVehicleById(plates.get("ABC 123").getId()).getMileage());
        assertEquals(100, progress.get(progress.size() - 1));
    }

    @Test
    void emptyFileImportsNothing() throws SQLException {
        ImportResult result = new RentalCsvImporter().importLines(new ArrayList<>(), db, null);
        assertEquals(0, result.getImported());
        assertEquals(0, result.getSkipped());
    }

    @Test
    void headerOnlyFileImportsNothing() throws SQLException {
        ImportResult result = new RentalCsvImporter()
                .importLines(Arrays.asList(RentalCsvImporter.HEADER), db, null);
        assertEquals(0, result.getImported());
        assertEquals(0, result.getSkipped());
    }

    @Test
    void bigFileIsSavedInBatches() throws SQLException {
        List<Vehicle> vehicles = db.getAllVehicles();
        List<String> lines = SampleData.rentalLines(vehicles, 450, 1L, LocalDate.of(2026, 10, 1));
        ImportResult result = new RentalCsvImporter().importLines(lines, db, null);
        assertEquals(450, result.getImported());
        assertEquals(450, db.getAllRentals().size());
    }
}
