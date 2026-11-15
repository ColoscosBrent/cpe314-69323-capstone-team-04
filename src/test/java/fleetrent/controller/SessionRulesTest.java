package fleetrent.controller;

import fleetrent.dao.Database;
import fleetrent.model.Account;
import fleetrent.model.Rental;
import fleetrent.model.Renter;
import fleetrent.model.Vehicle;
import fleetrent.model.VehicleFactory;
import fleetrent.service.AuthService;
import fleetrent.service.BookingService;
import fleetrent.service.RentalService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// The controllers read who is signed in from the Session. A renter cannot do owner jobs, a renter
// only sees his or her own data, and the undo of one person never reaches the next person.
class SessionRulesTest {
    private static final String QUESTION = AuthService.SECURITY_QUESTIONS.get(0);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 14);

    private Database db;
    private AuthController auth;
    private RentalController rentals;
    private ReservationController reservations;
    private RenterController renter;
    private VehicleController vehicles;
    private int vehicleId;
    private int anaRenterId;
    private int benRenterId;
    private int ownerAccountId;

    @BeforeEach
    void setUp() throws Exception {
        db = new Database("jdbc:sqlite::memory:");
        db.createTables();
        Session session = new Session();
        BookingService booking = new BookingService(db, () -> TODAY);
        auth = new AuthController(new AuthService(db), session);
        rentals = new RentalController(db, new RentalService(db), session);
        reservations = new ReservationController(db, booking, session);
        renter = new RenterController(db, booking, session);
        vehicles = new VehicleController(db, null, null, null, null, session);
        auth.onSessionChange(renter::clearUndo);
        vehicleId = db.insertVehicle(VehicleFactory.create(0, "SEDAN", "Toyota", "Vios", 2020,
                "ABC 123", "White", 1000, 0, null, "Car > Sedan"));
        ownerAccountId = auth.signUpOwner("owner01", "Passw0rdX", "Santos, Maria Lopez",
                "+63 9171234567", QUESTION, "Rex").getId();
        anaRenterId = signUpRenter("ana_reyes", "Reyes, Ana Cruz");
        benRenterId = signUpRenter("ben_cruz", "Cruz, Ben Lim");
        auth.signOut();
    }

    private int signUpRenter(String username, String name) throws Exception {
        Account account = auth.signUpRenter(username, "Passw0rdX", new Renter(0, name,
                "+63 9171234567", username + "@example.com",
                "12 Rizal St, Cebu City, Philippines"), QUESTION, "Rex");
        return account.getRenterId();
    }

    @AfterEach
    void tearDown() throws SQLException {
        db.close();
    }

    private Vehicle anotherVehicle() {
        return VehicleFactory.create(0, "SUV", "Ford", "Everest", 2022, "XYZ 789", "Black", 2000,
                0, null, "Car > SUV");
    }

    @Test
    void aRenterCannotAddOrEditAVehicle() throws Exception {
        auth.login("ana_reyes", "Passw0rdX");
        assertThrows(RuleException.class, () -> vehicles.add(anotherVehicle()));
        assertThrows(RuleException.class, () -> vehicles.update(db.getAllVehicles().get(0)));
        assertEquals(1, db.getAllVehicles().size());
    }

    @Test
    void aRenterCannotStartOrReturnARentalOrHandOverAReservation() throws Exception {
        auth.login("ana_reyes", "Passw0rdX");
        assertThrows(RuleException.class, () -> rentals.start(anaRenterId, vehicleId, TODAY,
                TODAY.plusDays(1)));
        assertThrows(RuleException.class, () -> rentals.returnRental(1, TODAY, 0));
        assertThrows(RuleException.class, () -> rentals.addRenter(new Renter(0, "Lim, Zed Sy",
                "+63 9171234567", "z@example.com", "1 Main St, Cebu City, Philippines")));
        assertThrows(RuleException.class, () -> reservations.handOver(1));
        assertThrows(RuleException.class, () -> reservations.cancel(1));
        assertThrows(RuleException.class, () -> reservations.load());
        assertTrue(db.getAllRentals().isEmpty());
    }

    @Test
    void nobodySignedInCannotDoOwnerJobs() {
        assertThrows(RuleException.class, () -> vehicles.add(anotherVehicle()));
        assertThrows(RuleException.class, () -> rentals.load());
        assertThrows(RuleException.class, () -> reservations.load());
    }

    @Test
    void theOwnerCanAddAVehicleAndStartARentalForARenter() throws Exception {
        auth.login("owner01", "Passw0rdX");
        assertTrue(vehicles.add(anotherVehicle()) > 0);
        Rental rental = rentals.start(anaRenterId, vehicleId, TODAY, TODAY.plusDays(2));
        assertEquals(anaRenterId, rental.getRenterId());
    }

    @Test
    void theRentalRemembersTheOwnerAccountThatStartedIt() throws Exception {
        auth.login("owner01", "Passw0rdX");
        Rental rental = rentals.start(anaRenterId, vehicleId, TODAY, TODAY.plusDays(2));
        assertEquals(ownerAccountId, db.getRentalById(rental.getId()).getProcessedBy());
    }

    @Test
    void aRentalMadeByTheRenterHasNoStaffAccount() throws Exception {
        auth.login("ana_reyes", "Passw0rdX");
        Rental rental = renter.rentNow(vehicleId, TODAY.plusDays(1));
        assertNull(db.getRentalById(rental.getId()).getProcessedBy());
    }

    @Test
    void aRenterOnlySeesHisOrHerOwnBookings() throws Exception {
        auth.login("ana_reyes", "Passw0rdX");
        renter.rentNow(vehicleId, TODAY.plusDays(1));
        assertEquals(1, renter.bookings().rentals().size());
        auth.signOut();
        auth.login("ben_cruz", "Passw0rdX");
        assertTrue(renter.bookings().rentals().isEmpty());
        assertEquals("Cruz, Ben Lim", renter.myDetails().getName());
        assertEquals(benRenterId, renter.myDetails().getId());
    }

    @Test
    void theRenterControllerRefusesAnOwnerAndNobody() throws Exception {
        assertThrows(RuleException.class, () -> renter.bookings());
        auth.login("owner01", "Passw0rdX");
        assertThrows(RuleException.class, () -> renter.rentNow(vehicleId, TODAY.plusDays(1)));
        assertThrows(RuleException.class, () -> renter.myDetails());
    }

    @Test
    void signingOutClearsTheUndoOfTheRenter() throws Exception {
        auth.login("ana_reyes", "Passw0rdX");
        renter.rentNow(vehicleId, TODAY.plusDays(1));
        assertTrue(renter.canUndo());
        auth.signOut();
        assertFalse(renter.canUndo());
        auth.login("ben_cruz", "Passw0rdX");
        assertFalse(renter.canUndo());
        assertNull(renter.undoLast());
        assertEquals(1, db.getAllRentals().size());
    }

    @Test
    void startingANewSessionAlsoClearsTheUndo() throws Exception {
        auth.login("ana_reyes", "Passw0rdX");
        renter.rentNow(vehicleId, TODAY.plusDays(1));
        auth.login("ben_cruz", "Passw0rdX");
        assertFalse(renter.canUndo());
    }
}
