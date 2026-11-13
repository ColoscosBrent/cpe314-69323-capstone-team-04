package fleetrent.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fleetrent.model.Renter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// What happens when the database file is damaged, locked or missing.
class DatabaseFailureTest {
    @TempDir
    Path folder;

    @Test
    void corruptFileIsRefusedWithAnSqlException() throws IOException {
        Path file = folder.resolve("broken.db");
        Files.write(file, "this is not a sqlite database, just some text".repeat(50)
                .getBytes(StandardCharsets.UTF_8));
        assertThrows(SQLException.class, () -> {
            try (Database db = new Database("jdbc:sqlite:" + file)) {
                db.createTables();
            }
        });
    }

    @Test
    void lockedFileFailsInsteadOfHangingForever() throws Exception {
        Path file = folder.resolve("locked.db");
        try (Database holder = new Database("jdbc:sqlite:" + file)) {
            holder.createTables();
            try (java.sql.Connection raw = java.sql.DriverManager.getConnection("jdbc:sqlite:" + file);
                 java.sql.Statement st = raw.createStatement()) {
                st.execute("PRAGMA busy_timeout = 100");
                st.execute("BEGIN EXCLUSIVE");
                try (Database other = new Database("jdbc:sqlite:" + file)) {
                    SQLException e = assertThrows(SQLException.class,
                            () -> other.insertRenter(new Renter(0, "Ana", null, null, null)));
                    assertTrue(e.getMessage().toLowerCase().contains("lock")
                            || e.getMessage().toLowerCase().contains("busy"), e.getMessage());
                }
                st.execute("ROLLBACK");
            }
        }
    }

    @Test
    void afterTheLockIsReleasedTheSameFileWorks() throws Exception {
        Path file = folder.resolve("again.db");
        try (Database db = new Database("jdbc:sqlite:" + file)) {
            db.createTables();
            db.insertRenter(new Renter(0, "Ana", null, null, null));
        }
        try (Database db = new Database("jdbc:sqlite:" + file)) {
            db.createTables();
            assertEquals(1, db.getAllRenters().size());
        }
    }

    @Test
    void emptyFileIsTreatedAsANewDatabase() throws Exception {
        Path file = folder.resolve("empty.db");
        Files.createFile(file);
        try (Database db = new Database("jdbc:sqlite:" + file)) {
            db.createTables();
            assertEquals(0, db.getAllRenters().size());
        }
    }

    @Test
    void closedDatabaseRefusesWork() throws SQLException {
        Database db = new Database("jdbc:sqlite::memory:");
        db.createTables();
        db.close();
        assertThrows(SQLException.class, db::getAllRenters);
    }
}
