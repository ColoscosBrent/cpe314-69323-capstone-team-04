package fleetrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RentalMathTest {
    @Test
    void normalRentalCountsTheDaysBetween() {
        assertEquals(3, RentalMath.billableDays(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 4)));
    }

    @Test
    void sameDayCountsAsOneDay() {
        LocalDate d = LocalDate.of(2026, 3, 1);
        assertEquals(1, RentalMath.billableDays(d, d));
    }

    @Test
    void endBeforeStartIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> RentalMath.billableDays(LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 4)));
    }

    @Test
    void missingDateIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> RentalMath.billableDays(null, LocalDate.now()));
    }
}
