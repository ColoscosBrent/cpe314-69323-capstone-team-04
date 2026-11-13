package fleetrent.service;

import fleetrent.model.ImportResult;
import fleetrent.dao.Database;
import fleetrent.model.Rental;
import fleetrent.model.RentalStatus;
import fleetrent.model.Renter;
import fleetrent.model.Sedan;
import fleetrent.model.VehicleFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// The owner's rental desk and the CSV import with strange input.
class RentalEdgeCaseTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private Database db;
    private RentalService service;
    private int vehicleId;
    private int renterId;

    @BeforeEach
    void setUp() throws SQLException {
        db = new Database("jdbc:sqlite::memory:");
        db.createTables();
        service = new RentalService(db);
        vehicleId = db.insertVehicle(VehicleFactory.create(0, "SEDAN", "Toyota", "Vios", 2022,
                "ABC 123", "White",
                1500, 0, null, "Car > Sedan"));
        renterId = db.insertRenter(new Renter(0, "Ana Reyes", "0917", null, null));
    }

    @AfterEach
    void tearDown() throws SQLException {
        db.close();
    }

    @Test
    void nullDatesAndBackwardsDatesAreRefusedWithAMessage() {
        IllegalArgumentException a = assertThrows(IllegalArgumentException.class,
                () -> service.startRental(renterId, vehicleId, null, TODAY));
        assertTrue(a.getMessage().length() > 5);
        assertThrows(IllegalArgumentException.class, () -> service.startRental(renterId, vehicleId,
                TODAY, null));
        assertThrows(IllegalArgumentException.class, () -> service.startRental(renterId, vehicleId,
                TODAY, TODAY.minusDays(1)));
    }

    @Test
    void unknownRenterOrVehicleIsRefusedAndNothingIsSaved() throws SQLException {
        assertThrows(IllegalArgumentException.class, () -> service.startRental(999, vehicleId,
                TODAY, TODAY.plusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> service.startRental(renterId, 999,
                TODAY, TODAY.plusDays(1)));
        assertTrue(db.getAllRentals().isEmpty());
    }

    @Test
    void returningBeforeTheStartDateOrWithNegativeMilesIsRefused() throws SQLException {
        Rental r = service.startRental(renterId, vehicleId, TODAY, TODAY.plusDays(3));
        assertThrows(IllegalArgumentException.class, () -> service.returnRental(r.getId(),
                TODAY.minusDays(1), 10));
        assertThrows(IllegalArgumentException.class, () -> service.returnRental(r.getId(), TODAY,
                -1));
        assertThrows(IllegalArgumentException.class, () -> service.returnRental(r.getId(), null,
                10));
        assertThrows(IllegalArgumentException.class, () -> service.returnRental(999, TODAY, 10));
        assertEquals(RentalStatus.ONGOING, db.getRentalById(r.getId()).getStatus());
        assertEquals(0, db.getVehicleById(vehicleId).getMileage());
    }

    @Test
    void zeroMilesIsAllowedAndCostsOnlyTheDays() throws SQLException {
        Rental r = service.startRental(renterId, vehicleId, TODAY, TODAY.plusDays(2));
        assertEquals(3000, service.returnRental(r.getId(), TODAY.plusDays(2), 0), 0.001);
    }

    @Test
    void aVeryLongRentalWithManyMilesStillAddsUpWithoutOverflow() throws SQLException {
        Rental r = service.startRental(renterId, vehicleId, TODAY, TODAY.plusDays(400));
        double cost = service.returnRental(r.getId(), TODAY.plusDays(400), 1_000_000);
        assertTrue(cost > 400 * 1500);
        assertEquals(1_000_000, db.getVehicleById(vehicleId).getMileage());
    }

    @Test
    void undoWithNothingToUndoReturnsNullTwiceInARow() throws SQLException {
        assertNull(service.undoLastCheckout());
        assertNull(service.undoLastCheckout());
    }

    @Test
    void undoGoesBackOneRentalAtATimeNewestFirst() throws SQLException {
        int second = db.insertVehicle(VehicleFactory.create(0, "SUV", "Ford", "Everest", 2021,
                "SUV 1", "Black", 2500,
                0, null, "Car > SUV"));
        Rental first = service.startRental(renterId, vehicleId, TODAY, TODAY.plusDays(1));
        Rental newest = service.startRental(renterId, second, TODAY, TODAY.plusDays(1));
        assertEquals(newest.getId(), service.undoLastCheckout().getId());
        assertEquals(first.getId(), service.undoLastCheckout().getId());
        assertNull(service.undoLastCheckout());
        assertTrue(db.getAllRentals().isEmpty());
    }

    @Test
    void waitingForARemovedVehicleIsNotPossible() throws SQLException {
        new FleetService(db).removeVehicle(vehicleId);
        assertThrows(IllegalArgumentException.class, () -> service.startRental(renterId, vehicleId,
                TODAY, TODAY.plusDays(1)));
        assertEquals(0, service.waitingCount(vehicleId));
    }

    @Test
    void anOwnerRentalOnDaysHeldByAReservationIsRefusedAndNothingIsSaved() throws SQLException {
        new BookingService(db, () -> TODAY).reserve(renterId, vehicleId, TODAY.plusDays(2),
                TODAY.plusDays(4));
        assertThrows(IllegalStateException.class, () -> service.startRental(renterId, vehicleId,
                TODAY.plusDays(3),
                TODAY.plusDays(5)));
        assertTrue(db.getAllRentals().isEmpty());
        // the days before the reservation are still free
        assertNotNull(service.startRental(renterId, vehicleId, TODAY, TODAY.plusDays(1)));
    }

    @Test
    void ownerRentalAndRenterRentalOnTheSameDaysCannotBothBeSaved() throws Exception {
        BookingService booking = new BookingService(db, () -> TODAY);
        booking.rentNow(renterId, vehicleId, TODAY.plusDays(3));
        // the vehicle is rented out now, so the owner's second rental only joins the waiting list
        assertNull(service.startRental(renterId, vehicleId, TODAY, TODAY.plusDays(1)));
        assertEquals(1, db.getAllRentals().size());
        assertEquals(1, service.waitingCount(vehicleId));
    }

    // ---------- CSV import ----------

    @Test
    void aFileFromExcelWithAnInvisibleMarkAtTheStartStillHasItsHeaderSkipped() throws SQLException {
        List<String> lines = Arrays.asList("﻿" + RentalCsvImporter.HEADER,
                "ABC 123,Ana Reyes,2026-03-01,2026-03-04,100");
        ImportResult result = new RentalCsvImporter().importLines(lines, db, null);
        assertEquals(1, result.getImported());
        assertEquals(0, result.getSkipped());
    }

    @Test
    void blankLinesSpacesAndLowerCasePlatesAreAccepted() throws SQLException {
        List<String> lines = Arrays.asList(RentalCsvImporter.HEADER, "", "   ",
                " abc 123 , Ana Reyes ,2026-03-01,2026-03-04, 100 ",
                "");
        ImportResult result = new RentalCsvImporter().importLines(lines, db, null);
        assertEquals(1, result.getImported());
        assertEquals(0, result.getSkipped());
    }

    @Test
    void aRemovedVehiclesPlateIsNotImportedInto() throws SQLException {
        new FleetService(db).removeVehicle(vehicleId);
        ImportResult result = new RentalCsvImporter().importLines(
                Arrays.asList(RentalCsvImporter.HEADER,
                        "ABC 123,Ana Reyes,2026-03-01,2026-03-04,100"), db, null);
        assertEquals(0, result.getImported());
        assertEquals(1, result.getSkipped());
    }

    @Test
    void theSameRenterNameInDifferentCapitalsIsOneRenter() throws SQLException {
        int before = db.getAllRenters().size();
        new RentalCsvImporter().importLines(Arrays.asList(
                "ABC 123,Maria Cruz,2026-03-01,2026-03-02,10",
                "ABC 123,MARIA CRUZ,2026-03-03,2026-03-04,10",
                "ABC 123,maria cruz,2026-03-05,2026-03-06,10"), db, null);
        assertEquals(before + 1, db.getAllRenters().size());
        assertEquals(3, db.getAllRentals().size());
    }

    @Test
    void badQuotesOrColumnCountIsSkippedWithItsLineNumber() throws SQLException {
        ImportResult result = new RentalCsvImporter().importLines(Arrays.asList(
                RentalCsvImporter.HEADER,
                "ABC 123,\"Cruz, Maria\",2026-03-01,2026-03-02,10",
                "ABC 123,Ben,2026-03-01,2026-03-02",
                "ABC 123,\"Dela Cruz,2026-03-01,2026-03-02,10"), db, null);
        assertEquals(1, result.getImported(), "a quoted comma is allowed");
        assertEquals(2, result.getSkipped());
        assertTrue(result.getErrors().get(0).startsWith("Line 3"));
        assertTrue(result.getErrors().get(1).startsWith("Line 4"));
    }

    @Test
    void impossibleDatesAndHugeMilesAreSkippedNotCrashed() throws SQLException {
        ImportResult result = new RentalCsvImporter().importLines(Arrays.asList(
                "ABC 123,Ana,2026-02-30,2026-03-02,10",
                "ABC 123,Ana,2026-13-01,2026-03-02,10",
                "ABC 123,Ana,2026-03-01,2026-03-02,99999999999"), db, null);
        assertEquals(0, result.getImported());
        assertEquals(3, result.getSkipped());
    }

    @Test
    void theProgressNeverGoesBackwardsAndEndsAt100() throws SQLException {
        List<Integer> seen = new java.util.ArrayList<>();
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        lines.add(RentalCsvImporter.HEADER);
        for (int i = 0; i < 400; i++) {
            lines.add("ABC 123,Renter " + (i % 7) + ",2026-03-01,2026-03-02,5");
        }
        new RentalCsvImporter().importLines(lines, db, seen::add);
        for (int i = 1; i < seen.size(); i++) {
            assertTrue(seen.get(i) >= seen.get(i - 1));
        }
        assertEquals(100, seen.get(seen.size() - 1));
        assertEquals(400, db.getAllRentals().size());
        assertEquals(2000, db.getVehicleById(vehicleId).getMileage());
    }
}
