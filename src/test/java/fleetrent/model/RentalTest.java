package fleetrent.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RentalTest {
    private static final LocalDate START = LocalDate.of(2026, 5, 1);
    private static final LocalDate END = LocalDate.of(2026, 5, 4);

    @Test
    void newRentalIsOngoingWithoutReturnDate() {
        Rental r = Rental.startNew(2, 3, START, END);
        assertEquals(RentalStatus.ONGOING, r.getStatus());
        assertNull(r.getReturnedOn());
        assertEquals(0, r.getMilesDriven());
        assertEquals(0, r.getId());
        assertEquals(2, r.getRenterId());
        assertEquals(3, r.getVehicleId());
        assertEquals(START, r.getStartDate());
        assertEquals(END, r.getEndDate());
    }

    @Test
    void markReturnedSavesDateMilesAndStatus() {
        Rental r = Rental.startNew(1, 1, START, END);
        r.markReturned(START.plusDays(1), 42);
        assertEquals(RentalStatus.RETURNED, r.getStatus());
        assertEquals(START.plusDays(1), r.getReturnedOn());
        assertEquals(42, r.getMilesDriven());
    }

    @Test
    void earlyReturnIsAllowed() {
        Rental r = Rental.startNew(1, 1, START, END);
        r.markReturned(START, 0);
        assertEquals(RentalStatus.RETURNED, r.getStatus());
    }

    @Test
    void idCanBeSetAfterSaving() {
        Rental r = Rental.startNew(1, 1, START, END);
        r.setId(9);
        assertEquals(9, r.getId());
    }
}
