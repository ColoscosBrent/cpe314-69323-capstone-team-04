package fleetrent;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

// Data mining on the saved rentals. Every method takes the data and a date range, so changing the
// range changes the answer. Only RETURNED rentals are counted, because only they have a final cost.
public final class FleetAnalytics {
    private FleetAnalytics() {
    }

    // Keeps returned rentals whose start date is inside from..to. A null limit means "no limit".
    public static List<Rental> filter(List<Rental> rentals, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("The 'from' date is after the 'to' date.");
        }
        List<Rental> kept = new ArrayList<>();
        for (Rental r : rentals) {
            if (r.getStatus() != RentalStatus.RETURNED || r.getReturnedOn() == null) {
                continue;
            }
            if (from != null && r.getStartDate().isBefore(from)) {
                continue;
            }
            if (to != null && r.getStartDate().isAfter(to)) {
                continue;
            }
            kept.add(r);
        }
        return kept;
    }

    public static double costOf(Rental r, Vehicle v) {
        int days = RentalMath.billableDays(r.getStartDate(), r.getReturnedOn());
        return v.computeRentalCost(days, r.getMilesDriven());
    }

    private static Map<Integer, Vehicle> byId(List<Vehicle> vehicles) {
        Map<Integer, Vehicle> map = new HashMap<>();
        for (Vehicle v : vehicles) {
            map.put(v.getId(), v);
        }
        return map;
    }

    // Total money earned per vehicle type. All three types are always in the result (0.0 if none).
    public static Map<String, Double> revenueByType(List<Rental> rentals, List<Vehicle> vehicles,
                                                    LocalDate from, LocalDate to) {
        Map<String, Double> result = new LinkedHashMap<>();
        result.put("SEDAN", 0.0);
        result.put("SUV", 0.0);
        result.put("MOTORCYCLE", 0.0);
        Map<Integer, Vehicle> lookup = byId(vehicles);
        for (Rental r : filter(rentals, from, to)) {
            Vehicle v = lookup.get(r.getVehicleId());
            if (v == null) {
                continue;
            }
            result.merge(v.getType(), costOf(r, v), Double::sum);
        }
        return result;
    }

    // Number of rentals per month, in time order. Months with no rentals between the first and
    // the last month are shown as 0 so the chart has no holes.
    public static Map<YearMonth, Integer> rentalsPerMonth(List<Rental> rentals, LocalDate from, LocalDate to) {
        TreeMap<YearMonth, Integer> counts = new TreeMap<>();
        for (Rental r : filter(rentals, from, to)) {
            counts.merge(YearMonth.from(r.getStartDate()), 1, Integer::sum);
        }
        if (!counts.isEmpty()) {
            YearMonth m = counts.firstKey();
            YearMonth last = counts.lastKey();
            while (m.isBefore(last)) {
                counts.putIfAbsent(m, 0);
                m = m.plusMonths(1);
            }
        }
        return counts;
    }

    // How many rentals each vehicle type had: the "most rented type" question. All three types are
    // always in the result (0 if none).
    public static Map<String, Integer> rentalsByType(List<Rental> rentals, List<Vehicle> vehicles,
                                                     LocalDate from, LocalDate to) {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("SEDAN", 0);
        result.put("SUV", 0);
        result.put("MOTORCYCLE", 0);
        Map<Integer, Vehicle> lookup = byId(vehicles);
        for (Rental r : filter(rentals, from, to)) {
            Vehicle v = lookup.get(r.getVehicleId());
            if (v != null) {
                result.merge(v.getType(), 1, Integer::sum);
            }
        }
        return result;
    }

    // Fleet utilization per month, in percent: (vehicle-days that were rented) / (vehicles x days in
    // the month) x 100. A vehicle counts once per day even if two rentals overlap, so it never passes
    // 100. Months between the first and the last busy month with no rentals show as 0.
    public static Map<YearMonth, Double> utilizationByMonth(List<Rental> rentals, List<Vehicle> vehicles,
                                                            LocalDate from, LocalDate to) {
        TreeMap<YearMonth, Double> result = new TreeMap<>();
        if (vehicles.isEmpty()) {
            return result;
        }
        Map<YearMonth, Set<Long>> usedDays = new TreeMap<>();
        for (Rental r : filter(rentals, from, to)) {
            int days = RentalMath.billableDays(r.getStartDate(), r.getReturnedOn());
            for (int i = 0; i < days; i++) {
                LocalDate day = r.getStartDate().plusDays(i);
                long key = r.getVehicleId() * 1_000_000L + day.toEpochDay();
                usedDays.computeIfAbsent(YearMonth.from(day), m -> new HashSet<>()).add(key);
            }
        }
        for (Map.Entry<YearMonth, Set<Long>> e : usedDays.entrySet()) {
            double capacity = (double) vehicles.size() * e.getKey().lengthOfMonth();
            result.put(e.getKey(), e.getValue().size() * 100.0 / capacity);
        }
        if (!result.isEmpty()) {
            YearMonth m = result.firstKey();
            YearMonth last = result.lastKey();
            while (m.isBefore(last)) {
                result.putIfAbsent(m, 0.0);
                m = m.plusMonths(1);
            }
        }
        return result;
    }

    // The n most rented models (make + model), biggest first. A tie is sorted by name.
    public static List<Map.Entry<String, Integer>> topModels(List<Rental> rentals, List<Vehicle> vehicles,
                                                             LocalDate from, LocalDate to, int n) {
        if (n < 1) {
            throw new IllegalArgumentException("n must be at least 1");
        }
        Map<Integer, Vehicle> lookup = byId(vehicles);
        Map<String, Integer> counts = new HashMap<>();
        for (Rental r : filter(rentals, from, to)) {
            Vehicle v = lookup.get(r.getVehicleId());
            if (v != null) {
                counts.merge(v.getMake() + " " + v.getModel(), 1, Integer::sum);
            }
        }
        List<Map.Entry<String, Integer>> list = new ArrayList<>(counts.entrySet());
        list.sort((a, b) -> {
            int byCount = Integer.compare(b.getValue(), a.getValue());
            return byCount != 0 ? byCount : a.getKey().compareTo(b.getKey());
        });
        return list.size() > n ? new ArrayList<>(list.subList(0, n)) : list;
    }

    // Average billable days of the counted rentals, or 0 when there are none.
    public static double averageDays(List<Rental> rentals, LocalDate from, LocalDate to) {
        List<Rental> kept = filter(rentals, from, to);
        if (kept.isEmpty()) {
            return 0.0;
        }
        long total = 0;
        for (Rental r : kept) {
            total += RentalMath.billableDays(r.getStartDate(), r.getReturnedOn());
        }
        return (double) total / kept.size();
    }

    // One readable sentence block about what the mining found.
    public static String summary(List<Rental> rentals, List<Vehicle> vehicles, LocalDate from, LocalDate to) {
        List<Rental> kept = filter(rentals, from, to);
        if (kept.isEmpty()) {
            return "No returned rentals in this date range.";
        }
        Map<String, Double> revenue = revenueByType(rentals, vehicles, from, to);
        String bestType = null;
        double total = 0;
        for (Map.Entry<String, Double> e : revenue.entrySet()) {
            total += e.getValue();
            if (bestType == null || e.getValue() > revenue.get(bestType)) {
                bestType = e.getKey();
            }
        }
        Map<YearMonth, Integer> months = rentalsPerMonth(rentals, from, to);
        YearMonth busiest = null;
        for (Map.Entry<YearMonth, Integer> e : months.entrySet()) {
            if (busiest == null || e.getValue() > months.get(busiest)) {
                busiest = e.getKey();
            }
        }
        return String.format(java.util.Locale.US, "%d rentals, total %.2f. Best earner: %s. Busiest month: %s (%d rentals). Average rental: %.1f days.",
                kept.size(), total, bestType, busiest, months.get(busiest), averageDays(rentals, from, to));
    }

    // Builds the category tree (Car > Sedan > ...) with each vehicle's mileage, for the recursive total.
    public static CategoryNode mileageTree(List<Vehicle> vehicles) {
        CategoryNode root = new CategoryNode("Fleet");
        for (Vehicle v : vehicles) {
            root.addVehicle(v.getCategoryPath() == null ? "Uncategorized" : v.getCategoryPath(), v.getMileage());
        }
        return root;
    }
}
