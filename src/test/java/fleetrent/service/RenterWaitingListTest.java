package fleetrent.service;

import fleetrent.dao.Database;
import fleetrent.model.Renter;
import fleetrent.model.VehicleFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// A renter can join the waiting line of a car that is rented out, see the place in line and leave.
class RenterWaitingListTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 14);

    private Database db;
    private BookingService booking;
    private int vehicleId;
    private int otherVehicleId;
    private int ana;
    private int ben;
    private int cara;

    @BeforeEach
    void setUp() throws SQLException {
        db = new Database("jdbc:sqlite::memory:");
        db.createTables();
        booking = new BookingService(db, () -> TODAY);
        vehicleId = db.insertVehicle(VehicleFactory.create(0, "SEDAN", "Toyota", "Vios", 2020,
                "ABC 123", "White", 1000, 0, null, "Car > Sedan"));
        otherVehicleId = db.insertVehicle(VehicleFactory.create(0, "SUV", "Ford", "Everest", 2022,
                "XYZ 789", "Black", 2000, 0, null, "Car > SUV"));
        ana = db.insertRenter(renter("Reyes, Ana Cruz"));
        ben = db.insertRenter(renter("Cruz, Ben Lim"));
        cara = db.insertRenter(renter("Lim, Cara Sy"));
    }

    private static Renter renter(String name) {
        return new Renter(0, name, "+63 9171234567", "x@example.com",
                "12 Rizal St, Cebu City, Philippines");
    }

    @AfterEach
    void tearDown() throws SQLException {
        db.close();
    }

    @Test
    void joiningACarThatIsNotRentedOutIsRefused() {
        assertThrows(IllegalStateException.class, () -> booking.joinWaitingList(ana, vehicleId));
    }

    @Test
    void theRentersGetPlacesInTheOrderTheyJoined() throws Exception {
        booking.rentNow(cara, vehicleId, TODAY.plusDays(3));
        assertEquals(1, booking.joinWaitingList(ana, vehicleId));
        assertEquals(2, booking.joinWaitingList(ben, vehicleId));
        assertEquals(List.of(ana, ben), db.getWaitingRenterIds(vehicleId));
    }

    @Test
    void joiningTwiceIsRefused() throws Exception {
        booking.rentNow(cara, vehicleId, TODAY.plusDays(3));
        booking.joinWaitingList(ana, vehicleId);
        assertThrows(IllegalStateException.class, () -> booking.joinWaitingList(ana, vehicleId));
        assertEquals(1, db.getWaitingRenterIds(vehicleId).size());
    }

    @Test
    void leavingMovesTheOthersUpOneStep() throws Exception {
        booking.rentNow(cara, vehicleId, TODAY.plusDays(3));
        booking.joinWaitingList(ana, vehicleId);
        booking.joinWaitingList(ben, vehicleId);
        booking.leaveWaitingList(ana, vehicleId);
        assertEquals(Map.of(vehicleId, 1), booking.waitingPlacesOf(ben));
        assertTrue(booking.waitingPlacesOf(ana).isEmpty());
    }

    @Test
    void theRenterSeesThePlaceInEveryLineHeOrSheIsIn() throws Exception {
        booking.rentNow(cara, vehicleId, TODAY.plusDays(3));
        booking.rentNow(cara, otherVehicleId, TODAY.plusDays(3));
        booking.joinWaitingList(ben, vehicleId);
        booking.joinWaitingList(ana, otherVehicleId);
        booking.joinWaitingList(ben, otherVehicleId);
        assertEquals(Map.of(vehicleId, 1, otherVehicleId, 2), booking.waitingPlacesOf(ben));
    }

    @Test
    void leavingALineThatWasNotJoinedChangesNothing() throws Exception {
        booking.rentNow(cara, vehicleId, TODAY.plusDays(3));
        booking.joinWaitingList(ana, vehicleId);
        booking.leaveWaitingList(ben, vehicleId);
        assertEquals(List.of(ana), db.getWaitingRenterIds(vehicleId));
    }
}
