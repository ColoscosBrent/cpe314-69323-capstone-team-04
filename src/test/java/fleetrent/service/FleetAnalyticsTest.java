package fleetrent.service;

import fleetrent.model.CategoryNode;
import fleetrent.model.Rental;
import fleetrent.model.RentalStatus;
import fleetrent.model.Sedan;
import fleetrent.model.Vehicle;
import fleetrent.model.VehicleFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FleetAnalyticsTest {
    private List<Vehicle> vehicles;
    private List<Rental> rentals;

    private static Rental returned(int vehicleId, String start, String end, int miles) {
        return new Rental(0, 1, vehicleId, LocalDate.parse(start), LocalDate.parse(end),
                LocalDate.parse(end), miles, RentalStatus.RETURNED);
    }

    @BeforeEach
    void setUp() {
        vehicles = new ArrayList<>();
        vehicles.add(VehicleFactory.create(1, "SEDAN", "Toyota", "Camry", 2022, "A", "White", 1000,
                500, null, "Car > Sedan"));
        vehicles.add(VehicleFactory.create(2, "SUV", "Ford", "Everest", 2022, "B", "Black", 2000,
                300, null, "Car > SUV"));
        rentals = new ArrayList<>();
        rentals.add(returned(1, "2026-01-10", "2026-01-12", 100));
        rentals.add(returned(1, "2026-01-20", "2026-01-21", 50));
        rentals.add(returned(2, "2026-03-05", "2026-03-08", 100));
    }

    @Test
    void revenueByTypeAddsUpCostPerType() {
        Map<String, Double> revenue = FleetAnalytics.revenueByType(rentals, vehicles, null, null);
        assertEquals(3000.0, revenue.get("SEDAN"), 0.001);
        assertEquals(6000.0, revenue.get("SUV"), 0.001);
        assertEquals(0.0, revenue.get("MOTORCYCLE"), 0.001);
    }

    @Test
    void changingTheDateRangeChangesTheResult() {
        Map<String, Double> all = FleetAnalytics.revenueByType(rentals, vehicles, null, null);
        Map<String, Double> janOnly = FleetAnalytics.revenueByType(rentals, vehicles,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        assertEquals(6000.0, all.get("SUV"), 0.001);
        assertEquals(0.0, janOnly.get("SUV"), 0.001);
        assertEquals(3000.0, janOnly.get("SEDAN"), 0.001);
    }

    @Test
    void rangeLimitsAreInclusive() {
        List<Rental> kept = FleetAnalytics.filter(rentals, LocalDate.of(2026, 1, 10),
                LocalDate.of(2026, 1, 20));
        assertEquals(2, kept.size());
    }

    @Test
    void emptyDataGivesZerosNotErrors() {
        Map<String, Double> revenue = FleetAnalytics.revenueByType(new ArrayList<>(), vehicles,
                null, null);
        assertEquals(3, revenue.size());
        assertEquals(0.0, revenue.get("SUV"), 0.001);
        assertTrue(FleetAnalytics.rentalsPerMonth(new ArrayList<>(), null, null).isEmpty());
        assertEquals(0.0, FleetAnalytics.averageDays(new ArrayList<>(), null, null), 0.001);
        assertEquals("No returned rentals in this date range.",
                FleetAnalytics.summary(new ArrayList<>(), vehicles, null, null));
    }

    @Test
    void fromAfterToIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> FleetAnalytics.filter(rentals, LocalDate.of(2026, 2, 1), LocalDate.of(2026,
                        1, 1)));
    }

    @Test
    void ongoingRentalsAreNotCounted() {
        rentals.add(Rental.startNew(1, 1, LocalDate.of(2026, 1, 15), LocalDate.of(2026, 1, 18)));
        assertEquals(3, FleetAnalytics.filter(rentals, null, null).size());
    }

    @Test
    void rentalWithUnknownVehicleIsIgnored() {
        rentals.add(returned(99, "2026-01-11", "2026-01-12", 10));
        Map<String, Double> revenue = FleetAnalytics.revenueByType(rentals, vehicles, null, null);
        assertEquals(9000.0, revenue.get("SEDAN") + revenue.get("SUV"), 0.001);
    }

    @Test
    void rentalsPerMonthFillsTheGapMonth() {
        Map<YearMonth, Integer> months = FleetAnalytics.rentalsPerMonth(rentals, null, null);
        assertEquals(3, months.size());
        assertEquals(2, months.get(YearMonth.of(2026, 1)));
        assertEquals(0, months.get(YearMonth.of(2026, 2)));
        assertEquals(1, months.get(YearMonth.of(2026, 3)));
    }

    @Test
    void topModelsIsSortedAndLimited() {
        List<Map.Entry<String, Integer>> top = FleetAnalytics.topModels(rentals, vehicles, null,
                null, 1);
        assertEquals(1, top.size());
        assertEquals("Toyota Camry", top.get(0).getKey());
        assertEquals(2, top.get(0).getValue());
        assertEquals(2, FleetAnalytics.topModels(rentals, vehicles, null, null, 10).size());
        assertThrows(IllegalArgumentException.class, () -> FleetAnalytics.topModels(rentals,
                vehicles, null, null, 0));
    }

    @Test
    void averageDaysUsesBillableDays() {
        // 2 days, 1 day, 3 days -> average 2.0
        assertEquals(2.0, FleetAnalytics.averageDays(rentals, null, null), 0.001);
    }

    @Test
    void summaryNamesTheBestEarnerAndBusiestMonth() {
        String text = FleetAnalytics.summary(rentals, vehicles, null, null);
        assertTrue(text.contains("3 rentals"));
        assertTrue(text.contains("Best earner: SUV"));
        assertTrue(text.contains("2026-01"));
    }

    @Test
    void mileageTreeAddsMileageRecursively() {
        CategoryNode root = FleetAnalytics.mileageTree(vehicles);
        assertEquals(800, root.totalMileage());
        assertTrue(root.toText(0).contains("SUV (300 mi)"));
    }

    @Test
    void rentalsByTypeCountsRentals() {
        Map<String, Integer> counts = FleetAnalytics.rentalsByType(rentals, vehicles, null, null);
        assertEquals(2, counts.get("SEDAN"));
        assertEquals(1, counts.get("SUV"));
        assertEquals(0, counts.get("MOTORCYCLE"));
    }

    @Test
    void rentalsByTypeWithNoDataIsAllZero() {
        Map<String, Integer> counts = FleetAnalytics.rentalsByType(new ArrayList<>(), vehicles,
                null, null);
        assertEquals(3, counts.size());
        assertEquals(0, counts.get("SEDAN"));
    }

    @Test
    void utilizationIsRentedVehicleDaysOverCapacity() {
        // January: Camry rented Jan 10-11 (2 days) and Jan 20 (1 day) = 3 vehicle-days.
        // Capacity = 2 vehicles x 31 days = 62.
        Map<YearMonth, Double> u = FleetAnalytics.utilizationByMonth(rentals, vehicles, null, null);
        assertEquals(3 * 100.0 / 62, u.get(YearMonth.of(2026, 1)), 0.001);
        assertEquals(0.0, u.get(YearMonth.of(2026, 2)), 0.001);
        assertEquals(3 * 100.0 / 62, u.get(YearMonth.of(2026, 3)), 0.001);
    }

    @Test
    void utilizationNeverCountsTheSameVehicleDayTwice() {
        List<Rental> overlap = new ArrayList<>();
        overlap.add(returned(1, "2026-02-01", "2026-02-03", 10));
        overlap.add(returned(1, "2026-02-01", "2026-02-03", 10));
        Map<YearMonth, Double> u = FleetAnalytics.utilizationByMonth(overlap, vehicles, null, null);
        assertEquals(2 * 100.0 / 56, u.get(YearMonth.of(2026, 2)), 0.001);
    }

    @Test
    void utilizationSplitsARentalThatCrossesTheMonthEnd() {
        List<Rental> cross = new ArrayList<>();
        cross.add(returned(1, "2026-01-30", "2026-02-02", 10));
        Map<YearMonth, Double> u = FleetAnalytics.utilizationByMonth(cross, vehicles, null, null);
        assertEquals(2 * 100.0 / 62, u.get(YearMonth.of(2026, 1)), 0.001);
        assertEquals(1 * 100.0 / 56, u.get(YearMonth.of(2026, 2)), 0.001);
    }

    @Test
    void utilizationWithNoVehiclesOrNoRentalsIsEmpty() {
        assertTrue(FleetAnalytics.utilizationByMonth(rentals, new ArrayList<>(), null, null)
                .isEmpty());
        assertTrue(FleetAnalytics.utilizationByMonth(new ArrayList<>(), vehicles, null, null)
                .isEmpty());
    }
}
