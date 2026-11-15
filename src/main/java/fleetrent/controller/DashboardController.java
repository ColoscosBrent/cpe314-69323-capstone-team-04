package fleetrent.controller;

import fleetrent.dao.Database;
import fleetrent.model.Rental;
import fleetrent.model.Vehicle;
import fleetrent.service.FleetAnalytics;
import fleetrent.service.VpicClient;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * What the owner's Dashboard asks for. It reads the saved rentals, lets FleetAnalytics do the
 * counting, and hands the chart a ready list of labels and numbers. The chart only draws.
 */
public class DashboardController extends ObservableController<DashboardController.Point> {
    private static final String SLOT_ANSWER = "answer";
    private static final String SLOT_LIVE = "live";
    /** The questions the dashboard can answer. */
    public enum Metric {
        /** Which vehicle type was rented most. */
        MOST_RENTED("Most rented vehicle type", "Vehicle type", "Number of rentals", false),
        /** How much of the fleet was in use, month by month. */
        UTILIZATION("Fleet utilization per month (%)", "Month", "Fleet in use (%)", true),
        /** Money earned by vehicle type. */
        REVENUE("Revenue by vehicle type", "Vehicle type", "Revenue", false),
        /** How many rentals started in each month. */
        MONTHLY("Rentals per month", "Month", "Number of rentals", true),
        /** The five most rented models. */
        TOP_MODELS("Top 5 rented models", "Model", "Number of rentals", false),
        /** How many models NHTSA lists for each make in the fleet (live, from the web). */
        LIVE("NHTSA models per make in our fleet (live)", "Make", "Models listed by NHTSA", false);

        private final String label;
        private final String xLabel;
        private final String yLabel;
        private final boolean overTime;

        Metric(String label, String xLabel, String yLabel, boolean overTime) {
            this.label = label;
            this.xLabel = xLabel;
            this.yLabel = yLabel;
            this.overTime = overTime;
        }

        /** The question as the user reads it. */
        public String label() {
            return label;
        }

        /** The label under the chart. */
        public String xLabel() {
            return xLabel;
        }

        /** The label beside the chart. */
        public String yLabel() {
            return yLabel;
        }

        /** True when the numbers change over time, so a line chart fits. */
        public boolean isOverTime() {
            return overTime;
        }

        /** Finds a metric by the text the user sees. */
        public static Metric fromLabel(String text) {
            for (Metric m : values()) {
                if (m.label.equals(text)) {
                    return m;
                }
            }
            return MOST_RENTED;
        }
    }

    /**
     * The result for the saved-rentals questions.
     *
     * @param treeText the mileage tree as text
     * @param data     label and number for each bar or point (empty for the live question)
     * @param summary  the summary line
     */
    public record Result(String treeText, Map<String, Number> data, String summary) {
    }

    /**
     * One bar or one point of the chart.
     *
     * @param label the name under the bar or the month of the point
     * @param value the number
     */
    public record Point(String label, Number value) {
    }

    /**
     * What the live question needs before it asks NHTSA.
     *
     * @param treeText the mileage tree as text
     * @param makes    the makes of the fleet
     */
    public record Fleet(String treeText, Set<String> makes) {
    }

    private static final int TOP_COUNT = 5;
    private static final double ROUND_TO_TENTH = 10.0;

    private final Database db;
    private final VpicClient vpic;
    private final Session session;

    /** Creates the controller. Only the owner may read the dashboard. */
    public DashboardController(Database db, VpicClient vpic, Session session) {
        this.db = db;
        this.vpic = vpic;
        this.session = session;
    }

    /**
     * Answers a saved-rentals question in the background. The bars or points go into items() and
     * show gets the whole result. A problem is put in the status property.
     */
    public void ask(Metric metric, LocalDate from, LocalDate to, Consumer<Result> show) {
        runTask(SLOT_ANSWER, () -> answer(metric, from, to), result -> {
            setPoints(result.data());
            show.accept(result);
        }, null);
    }

    /**
     * Reads the mileage tree and the makes of the fleet in the background, for the live question.
     * A problem is put in the status property.
     */
    public void loadFleet(Consumer<Fleet> show) {
        runTask(SLOT_ANSWER, () -> new Fleet(mileageTree(), ownedMakes()), show, null);
    }

    /**
     * Live question, in the background: how many models does NHTSA list for each make? The bars
     * go into items() and done gets the counts.
     */
    public void countModels(Set<String> makes, Consumer<Map<String, Number>> done,
            Consumer<String> failed) {
        runTask(SLOT_LIVE, () -> modelsPerMake(makes), counts -> {
            setPoints(counts);
            done.accept(counts);
        }, failed);
    }

    private void setPoints(Map<String, Number> data) {
        List<Point> points = new ArrayList<>();
        for (Map.Entry<String, Number> e : data.entrySet()) {
            points.add(new Point(e.getKey(), e.getValue()));
        }
        items().setAll(points);
    }

    /** Returns the mileage tree of the fleet as text. */
    public String mileageTree() throws ControllerException {
        session.requireOwner("see the dashboard");
        return Guard.database("loading the dashboard data",
                () -> FleetAnalytics.mileageTree(db.getAllVehicles()).toText(0));
    }

    /** Answers one saved-rentals question for the date range (from and to may be null). */
    public Result answer(Metric metric, LocalDate from, LocalDate to) throws ControllerException {
        session.requireOwner("see the dashboard");
        try {
            List<Vehicle> vehicles = db.getAllVehicles();
            // Old rentals of a removed vehicle still count in the money and the counts.
            List<Vehicle> everAdded = db.getAllVehiclesIncludingRemoved();
            List<Rental> rentals = db.getAllRentals();
            String tree = FleetAnalytics.mileageTree(vehicles).toText(0);
            Map<String, Number> data = new LinkedHashMap<>();
            switch (metric) {
                case MOST_RENTED -> FleetAnalytics.rentalsByType(rentals, everAdded, from, to)
                        .forEach(data::put);
                case UTILIZATION -> {
                    for (Map.Entry<YearMonth, Double> e
                            : FleetAnalytics.utilizationByMonth(rentals, vehicles, from, to)
                                    .entrySet()) {
                        data.put(e.getKey().toString(),
                                Math.round(e.getValue() * ROUND_TO_TENTH) / ROUND_TO_TENTH);
                    }
                }
                case REVENUE -> FleetAnalytics.revenueByType(rentals, everAdded, from, to)
                        .forEach(data::put);
                case MONTHLY -> {
                    for (Map.Entry<YearMonth, Integer> e
                            : FleetAnalytics.rentalsPerMonth(rentals, from, to).entrySet()) {
                        data.put(e.getKey().toString(), e.getValue());
                    }
                }
                default -> {
                    for (Map.Entry<String, Integer> e
                            : FleetAnalytics.topModels(rentals, everAdded, from, to, TOP_COUNT)) {
                        data.put(e.getKey(), e.getValue());
                    }
                }
            }
            return new Result(tree, data, FleetAnalytics.summary(rentals, everAdded, from, to));
        } catch (SQLException e) {
            throw ControllerException.database("loading the dashboard data", e);
        }
    }

    /** Returns the makes of the vehicles in the fleet, sorted, without repeats. */
    public Set<String> ownedMakes() throws ControllerException {
        session.requireOwner("see the dashboard");
        return Guard.database("loading the dashboard data", () -> {
            Set<String> makes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (Vehicle v : db.getAllVehicles()) {
                makes.add(v.getMake());
            }
            return makes;
        });
    }

    /**
     * Live question: how many models does NHTSA list for each make? One web call per make, so call
     * it from a background job.
     */
    public Map<String, Number> modelsPerMake(Set<String> makes) throws ControllerException {
        session.requireOwner("see the dashboard");
        return Guard.web("NHTSA", () -> {
            Map<String, Number> counts = new LinkedHashMap<>();
            for (String make : makes) {
                counts.put(make, vpic.fetchModels(make).size());
            }
            return counts;
        });
    }
}
