package fleetrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.SQLException;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// The Rent and Undo rules: the Queue (WaitingList) and the Stack (CheckoutHistory) working with the database.
class RentalDeskTest {
    private Database db;
    private RentalDesk desk;
    private Vehicle vios;
    private Vehicle everest;
    private Renter ana;
    private Renter ben;
    private Renter cara;
    private final LocalDate start = LocalDate.of(2026, 3, 1);
    private final LocalDate end = LocalDate.of(2026, 3, 3);

    @BeforeEach
    void setUp() throws SQLException {
        db = new Database("jdbc:sqlite::memory:");
        db.createTables();
        desk = new RentalDesk(db);
        vios = new Sedan(0, "Toyota", "Vios", 2020, "ABC123", "White", 1500.0, 3000, null, "Car > Sedan");
        everest = new Suv(0, "Ford", "Everest", 2021, "XYZ789", "Black", 2500.0, 12000, null, "Car > SUV");
        db.insertVehicle(vios);
        db.insertVehicle(everest);
        ana = desk.renterNamed("Ana");
        ben = desk.renterNamed("Ben");
        cara = desk.renterNamed("Cara");
    }

    @AfterEach
    void tearDown() throws SQLException {
        db.close();
    }

    @Test
    void freeVehicleIsRentedAndSaved() throws SQLException {
        Rental rental = desk.rent(ana, vios, start, end);
        assertNotNull(rental);
        assertEquals(1, db.getRentalsByRenter(ana.getId()).size());
        assertEquals(1, desk.undoCount());
    }

    @Test
    void secondRenterOfTheSameDaysJoinsTheWaitingList() throws SQLException {
        desk.rent(ana, vios, start, end);
        assertNull(desk.rent(ben, vios, start, end));
        assertEquals(1, desk.waitingCount(vios.getId()));
        assertEquals(0, db.getRentalsByRenter(ben.getId()).size());
    }

    @Test
    void waitingListOfOneVehicleDoesNotAffectAnother() throws SQLException {
        desk.rent(ana, vios, start, end);
        desk.rent(ben, vios, start, end);
        assertEquals(0, desk.waitingCount(everest.getId()));
        assertNotNull(desk.rent(ben, everest, start, end));
    }

    @Test
    void undoRemovesTheLastRentalFirst() throws SQLException {
        desk.rent(ana, vios, start, end);
        Rental second = desk.rent(ben, everest, start, end);
        RentalDesk.UndoResult result = desk.undoLast();
        assertEquals(second.getId(), result.undone().getId());
        assertEquals(0, db.getRentalsByRenter(ben.getId()).size());
        assertEquals(1, db.getRentalsByRenter(ana.getId()).size());
    }

    @Test
    void undoGivesTheVehicleToTheRenterWhoWaitedLongest() throws SQLException {
        desk.rent(ana, vios, start, end);
        desk.rent(ben, vios, start, end);
        desk.rent(cara, vios, start, end);
        RentalDesk.UndoResult result = desk.undoLast();
        assertEquals("Ben", result.nextRenter().getName());
        assertEquals(1, db.getRentalsByRenter(ben.getId()).size());
        assertEquals(1, desk.waitingCount(vios.getId()));
    }

    @Test
    void undoWithNothingToUndoReturnsNull() throws SQLException {
        assertNull(desk.undoLast());
    }

    @Test
    void sameRenterNameIsReusedAndBlankNameIsRejected() throws SQLException {
        assertEquals(ana.getId(), desk.renterNamed("  ana ").getId());
        assertThrows(IllegalArgumentException.class, () -> desk.renterNamed("   "));
    }
}
