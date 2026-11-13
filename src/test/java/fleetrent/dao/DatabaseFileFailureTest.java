package fleetrent.dao;

import fleetrent.model.Renter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// What happens when the database file itself is a problem: a corrupt file, a file that is not a
// database, and a file another program has locked.
class DatabaseFileFailureTest {
    @TempDir
    Path folder;

    @Test
    void corruptFileIsReportedAsASqlException() throws Exception {
        Path file = folder.resolve("broken.db");
        Files.write(file, "this is not a sqlite database, just text".repeat(50)
                .getBytes(StandardCharsets.UTF_8));
        assertThrows(SQLException.class, () -> {
            try (Database db = new Database("jdbc:sqlite:" + file)) {
                db.createTables();
            }
        });
    }

    @Test
    void halfWrittenFileIsReportedAsASqlException() throws Exception {
        Path good = folder.resolve("good.db");
        try (Database db = new Database("jdbc:sqlite:" + good)) {
            db.createTables();
            db.insertRenter(new Renter(0, "Ana", null, null, null));
        }
        byte[] bytes = Files.readAllBytes(good);
        Path cut = folder.resolve("cut.db");
        byte[] damaged = new byte[bytes.length];
        System.arraycopy(bytes, 0, damaged, 0, 100);
        for (int i = 100; i < damaged.length; i++) {
            damaged[i] = (byte) 0xFF;
        }
        Files.write(cut, damaged);
        assertThrows(SQLException.class, () -> {
            try (Database db = new Database("jdbc:sqlite:" + cut)) {
                db.createTables();
                db.getAllRenters();
            }
        });
    }

    @Test
    void folderThatDoesNotExistIsReportedAsASqlException() {
        Path missing = folder.resolve("nothing-here").resolve("fleet.db");
        assertThrows(SQLException.class, () -> new Database("jdbc:sqlite:" + missing));
    }

    @Test
    void lockedFileMakesAWriteFailInsteadOfHanging() throws Exception {
        Path file = folder.resolve("locked.db");
        try (Database db = new Database("jdbc:sqlite:" + file)) {
            db.createTables();
        }
        try (Connection other = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement st = other.createStatement()) {
            st.execute("PRAGMA busy_timeout = 0");
            st.execute("BEGIN EXCLUSIVE");
            try (Database db = new Database("jdbc:sqlite:" + file)) {
                long started = System.currentTimeMillis();
                assertThrows(SQLException.class, () -> db.insertRenter(new Renter(0, "Ben", null,
                        null, null)));
                // The app waits at most its busy timeout (5 seconds) and then gives up.
                assertTrue(System.currentTimeMillis() - started < 15_000);
            }
            st.execute("ROLLBACK");
        }
        try (Database db = new Database("jdbc:sqlite:" + file)) {
            assertEquals(0, db.getAllRenters().size());
        }
    }
}
