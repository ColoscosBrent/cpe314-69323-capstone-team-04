package fleetrent.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PaymentAndMaintenanceTest {
    private static final LocalDate DAY = LocalDate.of(2026, 6, 1);

    @Test
    void paymentTotalIsChargePlusLateFee() {
        Payment p = Payment.forRental(5, 2000, 150, DAY);
        assertEquals(2150, p.getTotal(), 0.001);
        assertEquals(5, p.getRentalId());
        assertEquals(DAY, p.getPaidOn());
        assertEquals(0, p.getId());
    }

    @Test
    void paymentRefusesNegativeAmountsAndMissingDate() {
        assertThrows(IllegalArgumentException.class, () -> Payment.forRental(1, -1, 0, DAY));
        assertThrows(IllegalArgumentException.class, () -> Payment.forRental(1, 1, -1, DAY));
        assertThrows(IllegalArgumentException.class, () -> Payment.forRental(1, 1, 0, null));
    }

    @Test
    void maintenanceKeepsItsDetailsAndTrimsTheNote() {
        Maintenance m = new Maintenance(0, 3, DAY, MaintenanceType.TIRES, 4500, "  front pair ");
        assertEquals(3, m.getVehicleId());
        assertEquals(MaintenanceType.TIRES, m.getType());
        assertEquals(4500, m.getCost(), 0.001);
        assertEquals("front pair", m.getNote());
    }

    @Test
    void maintenanceWithoutNoteHasEmptyNote() {
        assertEquals("", new Maintenance(0, 1, DAY, MaintenanceType.PMS, 0, null).getNote());
    }

    @Test
    void maintenanceRefusesBadInput() {
        assertThrows(IllegalArgumentException.class,
                () -> new Maintenance(0, 1, DAY, MaintenanceType.PMS, -5, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Maintenance(0, 1, null, MaintenanceType.PMS, 5, null));
        assertThrows(IllegalArgumentException.class, () -> new Maintenance(0, 1, DAY, null, 5,
                null));
    }

    @Test
    void lateFeeIsATenthOfTheDailyRatePerLateDay() {
        assertEquals(0, fleetrent.service.RentalMath.lateFee(1000, 0), 0.001);
        assertEquals(300, fleetrent.service.RentalMath.lateFee(1000, 3), 0.001);
        assertEquals(3, fleetrent.service.RentalMath.lateDays(DAY, DAY.plusDays(3)));
        assertEquals(0, fleetrent.service.RentalMath.lateDays(DAY, DAY.minusDays(2)));
    }
}
