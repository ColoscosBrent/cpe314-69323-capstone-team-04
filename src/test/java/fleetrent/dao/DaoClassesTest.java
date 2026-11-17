package fleetrent.dao;

import fleetrent.model.Account;
import fleetrent.model.Rental;
import fleetrent.model.Renter;
import fleetrent.model.Role;
import fleetrent.model.Vehicle;
import fleetrent.model.VehicleFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Each table has its own DAO class. Here the DAOs are used directly, on the same connection that
// Database keeps, to check that one DAO reads and writes only its own table.
class DaoClassesTest {
    private Connection connection;
    private VehicleDao vehicles;
    private RenterDao renters;
    private RentalDao rentals;
    private AccountDao accounts;
    private WaitingEntryDao waiting;

    @TempDir
    Path folder;

    @BeforeEach
    void setUp() throws Exception {
        String url = "jdbc:sqlite:" + folder.resolve("dao.db").toAbsolutePath();
        try (Database db = new Database(url)) {
            db.createTables();
        }
        connection = DriverManager.getConnection(url);
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        }
        vehicles = new VehicleDao(connection);
        renters = new RenterDao(connection);
        rentals = new RentalDao(connection);
        accounts = new AccountDao(connection);
        waiting = new WaitingEntryDao(connection);
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    private Vehicle vehicle(String plate) {
        return VehicleFactory.create(0, "SEDAN", "Toyota", "Vios", 2020, plate, "White", 1000, 0,
                null, "Car > Sedan");
    }

    @Test
    void theVehicleDaoSavesFindsAndRemovesAVehicle() throws Exception {
        int id = vehicles.insert(vehicle("ABC 123"));
        assertEquals("ABC 123", vehicles.getById(id).getPlateNumber());
        assertEquals(id, vehicles.findByPlate("abc 123").getId());
        assertEquals(1, vehicles.getAll().size());
        vehicles.setActive(id, false);
        assertTrue(vehicles.getAll().isEmpty());
        assertEquals(1, vehicles.getRemoved().size());
        assertEquals(1, vehicles.getAllIncludingRemoved().size());
    }

    @Test
    void theRenterDaoSavesAndReadsARenter() throws Exception {
        int id = renters.insert(new Renter(0, "Reyes, Ana Cruz", "+63 9171234567",
                "ana@example.com", "12 Rizal St, Cebu City, Philippines"));
        assertEquals("Reyes, Ana Cruz", renters.getById(id).getName());
        assertEquals(1, renters.getAll().size());
        assertNull(renters.getById(id + 99));
    }

    @Test
    void theRentalDaoRemembersTheStaffAccountThatProcessedTheRental() throws Exception {
        int vehicleId = vehicles.insert(vehicle("ABC 123"));
        int renterId = renters.insert(new Renter(0, "Reyes, Ana Cruz", "+63 9171234567",
                "ana@example.com", "12 Rizal St, Cebu City, Philippines"));
        Account staff = new Account(0, "owner01", Role.ADMIN, "Santos, Maria Lopez",
                "+63 9171234567", "hash", "salt", "Question?", "h", "s", null,
                LocalDate.of(2026, 11, 14));
        int staffId = accounts.insert(staff);
        Rental rental = Rental.startNew(renterId, vehicleId, LocalDate.of(2026, 11, 14),
                LocalDate.of(2026, 11, 16));
        rental.setProcessedBy(staffId);
        int id = rentals.insert(rental);
        assertEquals(staffId, rentals.getById(id).getProcessedBy());
        assertEquals(1, rentals.getByRenter(renterId).size());
        assertTrue(rentals.hasOngoing(vehicleId));
    }

    @Test
    void theWaitingDaoKeepsTheLineInOrder() throws Exception {
        int vehicleId = vehicles.insert(vehicle("ABC 123"));
        int a = renters.insert(new Renter(0, "Reyes, Ana Cruz", "+63 9171234567", "a@example.com",
                "12 Rizal St, Cebu City, Philippines"));
        int b = renters.insert(new Renter(0, "Cruz, Ben Lim", "+63 9171234567", "b@example.com",
                "12 Rizal St, Cebu City, Philippines"));
        waiting.add(vehicleId, a, LocalDate.of(2026, 11, 14));
        waiting.add(vehicleId, b, LocalDate.of(2026, 11, 14));
        assertEquals(List.of(a, b), waiting.getRenterIds(vehicleId));
        waiting.remove(vehicleId, a);
        assertEquals(List.of(b), waiting.getRenterIds(vehicleId));
        assertEquals(List.of(vehicleId), waiting.getVehicleIds(b));
    }
}
