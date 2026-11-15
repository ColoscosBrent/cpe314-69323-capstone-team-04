package fleetrent.dao;

import fleetrent.model.VehicleFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// An older fleetrent.db keeps its data and gets the new columns. A database that holds two
// vehicles with the same plate opens, and the owner is told once.
class DatabaseUpgradeTest {
    @TempDir
    Path folder;

    private String url() {
        return "jdbc:sqlite:" + folder.resolve("old.db").toAbsolutePath();
    }

    private static boolean hasColumn(Connection c, String table, String column)
            throws SQLException {
        try (Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void anOldImportHistoryGetsTheFileHashColumnAndKeepsItsRows() throws Exception {
        try (Connection c = DriverManager.getConnection(url());
                Statement st = c.createStatement()) {
            st.execute("CREATE TABLE ImportBatch (id INTEGER PRIMARY KEY AUTOINCREMENT, fileName "
                    + "TEXT NOT NULL, importedBy TEXT, importedOn TEXT NOT NULL, rowsRead INTEGER "
                    + "NOT NULL DEFAULT 0, rowsAdded INTEGER NOT NULL DEFAULT 0, rowsSkipped "
                    + "INTEGER NOT NULL DEFAULT 0)");
            st.execute("INSERT INTO ImportBatch (fileName, importedOn, rowsRead, rowsAdded) "
                    + "VALUES ('old.csv', '2026-01-01T10:00:00', 5, 5)");
        }
        try (Database db = new Database(url())) {
            db.createTables();
            assertEquals(1, db.getImportBatches().size());
            assertEquals("old.csv", db.getImportBatches().get(0).fileName());
            assertNull(db.findImportBatchByHash("anything"));
        }
        try (Connection c = DriverManager.getConnection(url())) {
            assertTrue(hasColumn(c, "ImportBatch", "fileHash"));
        }
    }

    @Test
    void anOldRentalTableGetsTheProcessedByColumn() throws Exception {
        try (Connection c = DriverManager.getConnection(url());
                Statement st = c.createStatement()) {
            st.execute("CREATE TABLE Rental (id INTEGER PRIMARY KEY AUTOINCREMENT, renterId "
                    + "INTEGER NOT NULL, vehicleId INTEGER NOT NULL, startDate TEXT NOT NULL, "
                    + "endDate TEXT NOT NULL, returnedOn TEXT, milesDriven INTEGER NOT NULL "
                    + "DEFAULT 0, status TEXT NOT NULL DEFAULT 'ONGOING')");
        }
        try (Database db = new Database(url())) {
            db.createTables();
        }
        try (Connection c = DriverManager.getConnection(url())) {
            assertTrue(hasColumn(c, "Rental", "processedBy"));
        }
    }

    @Test
    void duplicatePlatesInAnOldDatabaseGiveAOneTimeWarningAndTheDatabaseStillOpens()
            throws Exception {
        try (Database db = new Database(url())) {
            db.createTables();
            db.insertVehicle(VehicleFactory.create(0, "SEDAN", "Toyota", "Vios", 2020, "ABC 123",
                    "White", 1000, 0, null, "Car > Sedan"));
            db.insertVehicle(VehicleFactory.create(0, "SEDAN", "Honda", "City", 2021, "XYZ 789",
                    "Red", 1000, 0, null, "Car > Sedan"));
        }
        try (Connection c = DriverManager.getConnection(url());
                Statement st = c.createStatement()) {
            st.execute("DROP INDEX idxVehiclePlate");
            st.execute("UPDATE Vehicle SET plateNumber = 'ABC 123' WHERE plateNumber = 'XYZ 789'");
        }
        try (Database db = new Database(url())) {
            db.createTables();
            String warning = db.takeSetupWarning();
            assertNotNull(warning);
            assertTrue(warning.contains("same plate"));
            assertNull(db.takeSetupWarning());
            assertEquals(2, db.getAllVehicles().size());
        }
    }

    @Test
    void aCleanDatabaseGivesNoWarning() throws Exception {
        try (Database db = new Database(url())) {
            db.createTables();
            assertNull(db.takeSetupWarning());
        }
    }
}
