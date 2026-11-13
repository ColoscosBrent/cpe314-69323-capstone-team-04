package fleetrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class VehicleCostTest {
    private Vehicle sedan() {
        return new Sedan(1, "Toyota", "Vios", 2020, "ABC123", "White", 1000.0, 5000, LocalDate.of(2026, 9, 1), "Car > Sedan");
    }

    @Test
    void sedanWithinFreeMilesPaysOnlyDailyRate() {
        // 2 days, 150 miles: free allowance is 200 miles, so no extra fee
        assertEquals(2000.0, sedan().computeRentalCost(2, 150), 0.001);
    }

    @Test
    void sedanOverFreeMilesPaysExtraFee() {
        // 2 days, 300 miles: 100 extra miles x 0.20 = 20
        assertEquals(2020.0, sedan().computeRentalCost(2, 300), 0.001);
    }

    @Test
    void zeroDaysIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> sedan().computeRentalCost(0, 10));
    }

    @Test
    void eachSubclassUsesItsOwnRuleThroughTheParentType() {
        Vehicle sedan = new Sedan(1, "Toyota", "Vios", 2020, "A1", "White", 1000.0, 0, null, "Car > Sedan");
        Vehicle suv = new Suv(2, "Ford", "Everest", 2021, "B2", "Black", 1000.0, 0, null, "Car > SUV");
        Vehicle bike = new Motorcycle(3, "Honda", "Click", 2022, "C3", "Red", 1000.0, 0, null, "Motorcycle");
        List<Vehicle> fleet = List.of(sedan, suv, bike);
        // 1 day, 200 miles: extra miles are 100 / 120 / 140 with fees 0.20 / 0.30 / 0.15
        double[] expected = {1020.0, 1036.0, 1021.0};
        for (int i = 0; i < fleet.size(); i++) {
            assertEquals(expected[i], fleet.get(i).computeRentalCost(1, 200), 0.001);
        }
    }

    @Test
    void feeRulesAreReadableForTheScreens() {
        Vehicle sedan = new Sedan(1, "Toyota", "Vios", 2020, "A1", "White", 1000.0, 0, null, "Car > Sedan");
        Vehicle suv = new Suv(2, "Ford", "Everest", 2021, "B2", "Black", 1000.0, 0, null, "Car > SUV");
        Vehicle bike = new Motorcycle(3, "Honda", "Click", 2022, "C3", "Red", 1000.0, 0, null, "Motorcycle");
        assertEquals(100, sedan.getFreeMilesPerDay());
        assertEquals(0.20, sedan.getFeePerExtraMile(), 0.0001);
        assertEquals(80, suv.getFreeMilesPerDay());
        assertEquals(0.30, suv.getFeePerExtraMile(), 0.0001);
        assertEquals(60, bike.getFreeMilesPerDay());
        assertEquals(0.15, bike.getFeePerExtraMile(), 0.0001);
        assertEquals("100 free miles a day, then 0.20 per extra mile", sedan.feeSummary());
        assertEquals("Toyota Vios 2020", sedan.displayName());
    }

    @Test
    void theFeeTextAndTheCostRuleAgree() {
        for (String type : VehicleTypes.ALL) {
            Vehicle v = VehicleFactory.create(1, type, "A", "B", 2020, "P1", "Red", 1000.0, 0, null,
                    VehicleTypes.categoryOptions(type).get(0));
            int free = v.getFreeMilesPerDay();
            assertEquals(1000.0, v.computeRentalCost(1, free), 0.001);
            assertEquals(1000.0 + 10 * v.getFeePerExtraMile(), v.computeRentalCost(1, free + 10), 0.001);
        }
    }
}
