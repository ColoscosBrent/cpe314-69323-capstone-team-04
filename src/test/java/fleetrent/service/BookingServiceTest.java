package fleetrent.service;

import fleetrent.model.Availability;
import fleetrent.model.Offer;
import fleetrent.dao.Database;
import fleetrent.model.BusySpan;
import fleetrent.model.Rental;
import fleetrent.model.RentalStatus;
import fleetrent.model.Renter;
import fleetrent.model.Reservation;
import fleetrent.model.ReservationStatus;
import fleetrent.model.Sedan;
import fleetrent.model.Vehicle;
import fleetrent.model.VehicleFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BookingServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private Database db;
    private BookingService booking;
    private int car;
    private int suv;
    private int ana;
    private int ben;

    @BeforeEach
    void setUp() throws SQLException {
        db = new Database("jdbc:sqlite::memory:");
        db.createTables();
        booking = new BookingService(db, () -> TODAY);
        car = db.insertVehicle(VehicleFactory.create(0, "SEDAN", "Toyota", "Vios", 2022, "ABC 123", "White", 1500, 0,
                null, "Car > Sedan"));
        suv = db.insertVehicle(VehicleFactory.create(0, "SUV", "Ford", "Everest", 2021, "SUV 777", "Black", 2500, 0,
                null, "Car > SUV"));
        ana = db.insertRenter(new Renter(0, "Ana Reyes", "09171234567", null, null));
        ben = db.insertRenter(new Renter(0, "Ben Cruz", "09181234567", null, null));
    }

    @AfterEach
    void tearDown() throws SQLException {
        db.close();
    }

    private Offer offerOf(int vehicleId, LocalDate s, LocalDate e) throws SQLException {
        return booking.browse(s, e).stream().filter(o -> o.vehicle().getId() == vehicleId).findFirst().orElseThrow();
    }

    // ----- rent now -----

    @Test
    void rentNowStartsAnOngoingRentalTodayForTheRenter() throws SQLException {
        Rental r = booking.rentNow(ana, car, TODAY.plusDays(3));
        assertEquals(TODAY, r.getStartDate());
        assertEquals(RentalStatus.ONGOING, r.getStatus());
        assertEquals(ana, db.getRentalById(r.getId()).getRenterId());
    }

    @Test
    void rentNowIsRefusedWhileTheVehicleIsRentedOut() throws SQLException {
        booking.rentNow(ana, car, TODAY.plusDays(3));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> booking.rentNow(ben, car, TODAY.plusDays(1)));
        assertTrue(e.getMessage().contains("rented out"));
        assertEquals(1, db.getAllRentals().size());
    }

    @Test
    void rentNowIsRefusedWhenAReservationCoversThoseDays() throws SQLException {
        booking.reserve(ben, car, TODAY.plusDays(2), TODAY.plusDays(4));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> booking.rentNow(ana, car, TODAY.plusDays(3)));
        assertTrue(e.getMessage().contains("reserved"));
        booking.rentNow(ana, car, TODAY.plusDays(1));
    }

    @Test
    void rentNowChecksTheEndDateAndTheLength() {
        assertThrows(IllegalArgumentException.class, () -> booking.rentNow(ana, car, TODAY.minusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> booking.rentNow(ana, car, null));
        assertThrows(IllegalArgumentException.class,
                () -> booking.rentNow(ana, car, TODAY.plusDays(BookingService.MAX_DAYS + 1)));
        assertThrows(IllegalArgumentException.class, () -> booking.rentNow(999, car, TODAY.plusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> booking.rentNow(ana, 999, TODAY.plusDays(1)));
    }

    @Test
    void aOneDayRentalEndingTodayIsAllowed() throws SQLException {
        assertNotNull(booking.rentNow(ana, car, TODAY));
    }

    // ----- reserve -----

    @Test
    void reserveHoldsTheVehicleForFutureDates() throws SQLException {
        Reservation r = booking.reserve(ana, car, TODAY.plusDays(5), TODAY.plusDays(7));
        assertEquals(ReservationStatus.ACTIVE, r.getStatus());
        assertEquals(1, booking.reservationsOf(ana).size());
        assertTrue(booking.reservationsOf(ben).isEmpty());
    }

    @Test
    void reserveMustStartAfterTodayAndNotTooFarAhead() {
        assertThrows(IllegalArgumentException.class, () -> booking.reserve(ana, car, TODAY, TODAY.plusDays(2)));
        assertThrows(IllegalArgumentException.class, () -> booking.reserve(ana, car, TODAY.minusDays(1), TODAY.plusDays(2)));
        assertThrows(IllegalArgumentException.class,
                () -> booking.reserve(ana, car, TODAY.plusDays(BookingService.MAX_DAYS_AHEAD + 1),
                        TODAY.plusDays(BookingService.MAX_DAYS_AHEAD + 2)));
        assertThrows(IllegalArgumentException.class, () -> booking.reserve(ana, car, TODAY.plusDays(5), TODAY.plusDays(4)));
        assertThrows(IllegalArgumentException.class,
                () -> booking.reserve(ana, car, TODAY.plusDays(5), TODAY.plusDays(5 + BookingService.MAX_DAYS + 1)));
    }

    @Test
    void overlappingReservationsAreRefusedButNeighbourDaysAreFine() throws SQLException {
        booking.reserve(ana, car, TODAY.plusDays(5), TODAY.plusDays(7));
        assertThrows(IllegalStateException.class, () -> booking.reserve(ben, car, TODAY.plusDays(7), TODAY.plusDays(9)));
        assertThrows(IllegalStateException.class, () -> booking.reserve(ben, car, TODAY.plusDays(3), TODAY.plusDays(5)));
        assertThrows(IllegalStateException.class, () -> booking.reserve(ben, car, TODAY.plusDays(6), TODAY.plusDays(6)));
        assertThrows(IllegalStateException.class, () -> booking.reserve(ben, car, TODAY.plusDays(4), TODAY.plusDays(8)));
        booking.reserve(ben, car, TODAY.plusDays(8), TODAY.plusDays(9));
        booking.reserve(ben, car, TODAY.plusDays(2), TODAY.plusDays(4));
        assertEquals(3, db.getAllReservations().size());
    }

    @Test
    void anotherVehicleIsNotAffected() throws SQLException {
        booking.reserve(ana, car, TODAY.plusDays(5), TODAY.plusDays(7));
        assertNotNull(booking.reserve(ben, suv, TODAY.plusDays(5), TODAY.plusDays(7)));
    }

    @Test
    void reserveIsRefusedWhenARentalIsStillOutThatDay() throws SQLException {
        booking.rentNow(ana, car, TODAY.plusDays(6));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> booking.reserve(ben, car, TODAY.plusDays(5), TODAY.plusDays(8)));
        assertTrue(e.getMessage().contains("rented out"));
        booking.reserve(ben, car, TODAY.plusDays(7), TODAY.plusDays(8));
    }

    @Test
    void aLateRentalStillBlocksTodayButNotTomorrow() throws SQLException {
        db.insertRental(new Rental(0, ana, car, TODAY.minusDays(10), TODAY.minusDays(5), null, 0, RentalStatus.ONGOING));
        assertEquals(Availability.RENTED, offerOf(car, TODAY, TODAY).availability());
        assertThrows(IllegalStateException.class, () -> booking.rentNow(ben, car, TODAY.plusDays(1)));
        assertEquals(Availability.AVAILABLE, offerOf(car, TODAY.plusDays(1), TODAY.plusDays(2)).availability());
    }

    @Test
    void aReturnedRentalDoesNotBlockAnything() throws SQLException {
        Rental r = booking.rentNow(ana, car, TODAY.plusDays(3));
        RentalService service = new RentalService(db);
        service.returnRental(r.getId(), TODAY.plusDays(1), 20);
        assertNotNull(booking.reserve(ben, car, TODAY.plusDays(2), TODAY.plusDays(3)));
        assertNotNull(booking.rentNow(ben, car, TODAY.plusDays(1)));
    }

    // ----- browse -----

    @Test
    void browseShowsEveryVehicleInTheFleetWithItsStatus() throws SQLException {
        booking.rentNow(ana, car, TODAY.plusDays(2));
        booking.reserve(ben, suv, TODAY.plusDays(5), TODAY.plusDays(6));
        List<Offer> offers = booking.browse(TODAY.plusDays(5), TODAY.plusDays(6));
        assertEquals(2, offers.size());
        assertEquals(Availability.AVAILABLE, offerOf(car, TODAY.plusDays(5), TODAY.plusDays(6)).availability());
        assertEquals(Availability.RESERVED, offerOf(suv, TODAY.plusDays(5), TODAY.plusDays(6)).availability());
        assertEquals(Availability.RENTED, offerOf(car, TODAY, TODAY.plusDays(1)).availability());
    }

    @Test
    void browseGivesTheDueDateOfAVehicleThatIsOutNow() throws SQLException {
        booking.rentNow(ana, car, TODAY.plusDays(2));
        assertEquals(TODAY.plusDays(2), offerOf(car, TODAY.plusDays(10), TODAY.plusDays(11)).rentedUntil());
        assertNull(offerOf(suv, TODAY, TODAY).rentedUntil());
    }

    @Test
    void browseDoesNotShowRemovedVehiclesOrWhoRentsWhat() throws SQLException {
        new FleetService(db).removeVehicle(suv);
        List<Offer> offers = booking.browse(TODAY, TODAY);
        assertEquals(1, offers.size());
        assertEquals(car, offers.get(0).vehicle().getId());
    }

    @Test
    void browseListsTheBusySpansInOrder() throws SQLException {
        booking.reserve(ana, car, TODAY.plusDays(20), TODAY.plusDays(22));
        booking.reserve(ben, car, TODAY.plusDays(5), TODAY.plusDays(6));
        List<BusySpan> spans = offerOf(car, TODAY, TODAY).busy();
        assertEquals(2, spans.size());
        assertEquals(TODAY.plusDays(5), spans.get(0).start());
        assertEquals("RESERVED", spans.get(0).kind());
    }

    @Test
    void browseRefusesAnEndBeforeTheStart() {
        assertThrows(IllegalArgumentException.class, () -> booking.browse(TODAY.plusDays(2), TODAY));
        assertThrows(IllegalArgumentException.class, () -> booking.browse(null, TODAY));
    }

    @Test
    void estimateUsesTheDailyRateAndTheBillableDays() {
        Vehicle v = VehicleFactory.create(1, "SEDAN", "Toyota", "Vios", 2022, "ABC 123", "White", 1500, 0, null, "Car > Sedan");
        assertEquals(4500, BookingService.estimate(v, TODAY, TODAY.plusDays(3)), 0.001);
        assertEquals(1500, BookingService.estimate(v, TODAY, TODAY), 0.001);
    }

    // ----- cancel and undo -----

    @Test
    void renterCancelsOnlyTheirOwnActiveReservation() throws SQLException {
        Reservation r = booking.reserve(ana, car, TODAY.plusDays(5), TODAY.plusDays(7));
        assertThrows(IllegalArgumentException.class, () -> booking.cancelReservation(ben, r.getId()));
        booking.cancelReservation(ana, r.getId());
        assertEquals(ReservationStatus.CANCELLED, db.getReservationById(r.getId()).getStatus());
        assertThrows(IllegalStateException.class, () -> booking.cancelReservation(ana, r.getId()));
        assertNotNull(booking.reserve(ben, car, TODAY.plusDays(5), TODAY.plusDays(7)));
    }

    @Test
    void undoTakesBackTheLastActionFirst() throws SQLException {
        Reservation r1 = booking.reserve(ana, car, TODAY.plusDays(5), TODAY.plusDays(6));
        Rental rent = booking.rentNow(ana, suv, TODAY.plusDays(1));
        assertTrue(booking.canUndo());
        assertTrue(booking.undoLast(ana).contains("Rental #" + rent.getId()));
        assertNull(db.getRentalById(rent.getId()));
        assertTrue(booking.undoLast(ana).contains("Reservation #" + r1.getId()));
        assertEquals(ReservationStatus.CANCELLED, db.getReservationById(r1.getId()).getStatus());
        assertFalse(booking.canUndo());
        assertNull(booking.undoLast(ana));
    }

    @Test
    void undoOfARentalTheOwnerAlreadyClosedIsRefused() throws SQLException {
        Rental rent = booking.rentNow(ana, car, TODAY.plusDays(2));
        new RentalService(db).returnRental(rent.getId(), TODAY.plusDays(1), 10);
        assertThrows(IllegalStateException.class, () -> booking.undoLast(ana));
        assertNotNull(db.getRentalById(rent.getId()));
    }

    @Test
    void undoOfAReservationAlreadyCancelledIsRefused() throws SQLException {
        Reservation r = booking.reserve(ana, car, TODAY.plusDays(5), TODAY.plusDays(6));
        booking.cancelReservation(ana, r.getId());
        assertThrows(IllegalStateException.class, () -> booking.undoLast(ana));
    }

    // ----- the owner -----

    @Test
    void handOverTurnsAReservationIntoARentalOnItsStartDay() throws SQLException {
        Reservation r = booking.reserve(ana, car, TODAY.plusDays(2), TODAY.plusDays(4));
        BookingService later = new BookingService(db, () -> TODAY.plusDays(2));
        Rental rental = later.handOver(r.getId());
        assertEquals(TODAY.plusDays(2), rental.getStartDate());
        assertEquals(TODAY.plusDays(4), rental.getEndDate());
        assertEquals(ana, rental.getRenterId());
        assertEquals(ReservationStatus.CONVERTED, db.getReservationById(r.getId()).getStatus());
        assertEquals(1, db.getAllRentals().size());
        assertThrows(IllegalStateException.class, () -> later.handOver(r.getId()));
    }

    @Test
    void handOverBeforeTheStartDayOrAfterTheEndIsRefused() throws SQLException {
        Reservation r = booking.reserve(ana, car, TODAY.plusDays(2), TODAY.plusDays(4));
        assertThrows(IllegalStateException.class, () -> booking.handOver(r.getId()));
        BookingService tooLate = new BookingService(db, () -> TODAY.plusDays(5));
        assertThrows(IllegalStateException.class, () -> tooLate.handOver(r.getId()));
        assertEquals(0, db.getAllRentals().size());
    }

    @Test
    void handOverIsRefusedWhileTheVehicleIsStillOut() throws SQLException {
        db.insertRental(new Rental(0, ben, car, TODAY.minusDays(3), TODAY.plusDays(1), null, 0, RentalStatus.ONGOING));
        Reservation r = new Reservation(0, ana, car, TODAY.plusDays(2), TODAY.plusDays(3), ReservationStatus.ACTIVE, null, TODAY);
        db.insertReservation(r);
        BookingService later = new BookingService(db, () -> TODAY.plusDays(2));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> later.handOver(r.getId()));
        assertTrue(e.getMessage().contains("still rented out"));
    }

    @Test
    void ownerCancelsAReservation() throws SQLException {
        Reservation r = booking.reserve(ana, car, TODAY.plusDays(2), TODAY.plusDays(4));
        booking.cancelReservationByOwner(r.getId());
        assertEquals("Cancelled by the owner", db.getReservationById(r.getId()).getNote());
        assertThrows(IllegalStateException.class, () -> booking.cancelReservationByOwner(r.getId()));
        assertThrows(IllegalArgumentException.class, () -> booking.cancelReservationByOwner(999));
    }

    @Test
    void ownerRentalRefusesDaysThatAreReserved() throws SQLException {
        booking.reserve(ben, car, TODAY.plusDays(2), TODAY.plusDays(4));
        RentalService service = new RentalService(db);
        assertThrows(IllegalStateException.class, () -> service.startRental(ana, car, TODAY, TODAY.plusDays(3)));
        assertNotNull(service.startRental(ana, car, TODAY, TODAY.plusDays(1)));
    }

    @Test
    void changeStampChangesAfterAWrite() throws SQLException {
        String before = db.changeStamp();
        booking.reserve(ana, car, TODAY.plusDays(2), TODAY.plusDays(4));
        assertFalse(before.equals(db.changeStamp()));
        String same = db.changeStamp();
        assertEquals(same, db.changeStamp());
    }
}
