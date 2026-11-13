package fleetrent.dao;

import fleetrent.model.Renter;
import fleetrent.model.ReservationStatus;
import fleetrent.model.Sedan;
import fleetrent.model.VehicleFactory;
import fleetrent.service.BookingService;
import fleetrent.service.FleetService;
import fleetrent.service.RentalService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Two windows (or two copies of the app) open the same fleetrent.db file with their own connection.
// These tests use two connections to one file to check that they see each other and never double
// book.
class DatabaseSyncTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private Path file;
    private Database owner;
    private Database renter;
    private int vehicleId;
    private int renterId;

    @BeforeEach
    void setUp() throws Exception {
        file = Files.createTempFile("fleetrent-sync", ".db");
        owner = new Database("jdbc:sqlite:" + file);
        owner.createTables();
        renter = new Database("jdbc:sqlite:" + file);
        renter.createTables();
        vehicleId = owner.insertVehicle(VehicleFactory.create(0, "SEDAN", "Toyota", "Vios", 2022,
                "ABC 123", "White",
                1500, 0, null, "Car > Sedan"));
        renterId = owner.insertRenter(new Renter(0, "Ana Reyes", "09171234567", null, null));
    }

    @AfterEach
    void tearDown() throws Exception {
        owner.close();
        renter.close();
        Files.deleteIfExists(file);
    }

    @Test
    void aChangeMadeByTheOtherConnectionChangesTheStamp() throws SQLException {
        String before = owner.changeStamp();
        new BookingService(renter, () -> TODAY).reserve(renterId, vehicleId, TODAY.plusDays(2),
                TODAY.plusDays(3));
        assertFalse(before.equals(owner.changeStamp()),
                "the owner window must notice the renter's reservation");
    }

    @Test
    void ownerSeesReservationAndRenterSeesRemovedVehicleGone() throws SQLException {
        BookingService renterSide = new BookingService(renter, () -> TODAY);
        renterSide.reserve(renterId, vehicleId, TODAY.plusDays(2), TODAY.plusDays(3));
        assertEquals(1, owner.getAllReservations().size());
        assertEquals(1, renterSide.browse(TODAY, TODAY).size());
        new FleetService(owner).removeVehicle(vehicleId);
        assertTrue(renterSide.browse(TODAY, TODAY).isEmpty());
        assertEquals(ReservationStatus.CANCELLED, renter.getReservationById(1).getStatus());
    }

    @Test
    void twoConnectionsCannotBookTheSameDays() throws SQLException {
        BookingService a = new BookingService(owner, () -> TODAY);
        BookingService b = new BookingService(renter, () -> TODAY);
        a.reserve(renterId, vehicleId, TODAY.plusDays(2), TODAY.plusDays(4));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> b.reserve(renterId, vehicleId, TODAY.plusDays(3), TODAY.plusDays(5)));
        assertEquals(1, owner.getAllReservations().size());
    }

    @Test
    void anOwnerRentalAndARenterRentNowCannotOverlap() throws SQLException {
        new RentalService(owner).startRental(renterId, vehicleId, TODAY, TODAY.plusDays(2));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> new BookingService(renter, () -> TODAY).rentNow(renterId, vehicleId,
                        TODAY.plusDays(1)));
    }
}
