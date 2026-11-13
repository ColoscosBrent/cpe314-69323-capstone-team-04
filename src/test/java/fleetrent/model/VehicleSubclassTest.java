package fleetrent.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class VehicleSubclassTest {
    private static final LocalDate PMS = LocalDate.of(2026, 1, 15);

    private static Vehicle sedan() {
        return new Sedan(1, "Toyota", "Vios", 2020, "ABC 123", "White", 1000, 500, PMS,
                "Car > Sedan");
    }

    private static Vehicle suv() {
        return new Suv(2, "Ford", "Everest", 2021, "DEF 456", "Black", 2000, 900, null,
                "Car > SUV");
    }

    private static Vehicle bike() {
        return new Motorcycle(3, "Honda", "Click", 2022, "GHI 789", "Red", 400, 100, PMS,
                "Motorcycle");
    }

    @Test
    void eachSubclassReportsItsOwnType() {
        assertEquals("SEDAN", sedan().getType());
        assertEquals("SUV", suv().getType());
        assertEquals("MOTORCYCLE", bike().getType());
    }

    @Test
    void eachSubclassHasItsOwnMileageRule() {
        assertNotEquals(sedan().getFreeMilesPerDay(), bike().getFreeMilesPerDay());
        assertTrue(suv().getFeePerExtraMile() > 0);
        assertTrue(bike().getFeePerExtraMile() > 0);
    }

    @Test
    void baseDetailsAreKept() {
        Vehicle v = sedan();
        assertEquals(1, v.getId());
        assertEquals("Toyota", v.getMake());
        assertEquals("Vios", v.getModel());
        assertEquals(2020, v.getYear());
        assertEquals("ABC 123", v.getPlateNumber());
        assertEquals("White", v.getColor());
        assertEquals(1000, v.getDailyRate(), 0.001);
        assertEquals(500, v.getMileage());
        assertEquals(PMS, v.getLastPMS());
        assertEquals("Car > Sedan", v.getCategoryPath());
    }

    @Test
    void lastPmsCanBeMissing() {
        assertNull(suv().getLastPMS());
    }

    @Test
    void displayNameAndFeeSummaryAreReadable() {
        assertTrue(sedan().displayName().contains("Vios"));
        assertTrue(sedan().feeSummary().contains("free"));
    }

    @Test
    void costWithinFreeMilesIsTheDailyRateTimesDays() {
        assertEquals(3000, suv().computeRentalCost(1, 0) + 1000, 0.001);
        assertEquals(800, bike().computeRentalCost(2, 0), 0.001);
    }

    @Test
    void idCanBeSetAfterSaving() {
        Vehicle v = bike();
        v.setId(77);
        assertEquals(77, v.getId());
    }
}
