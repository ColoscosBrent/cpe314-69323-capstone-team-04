package fleetrent;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Tab 4: data mining dashboard. Pick a question and a date range; the numbers and the chart are
 * calculated from the saved rentals (or from live NHTSA data) every time, never typed in.
 */
public class DashboardTab extends Tab {
    private static final String MOST_RENTED = "Most rented vehicle type";
    private static final String UTILIZATION = "Fleet utilization per month (%)";
    private static final String REVENUE = "Revenue by vehicle type";
    private static final String MONTHLY = "Rentals per month";
    private static final String TOP = "Top 5 rented models";
    private static final String LIVE = "NHTSA models per make in our fleet (live)";

    private final Database db;
    private final VpicClient vpic = new VpicClient();

    private final ChoiceBox<String> metricBox = new ChoiceBox<>(
            FXCollections.observableArrayList(MOST_RENTED, UTILIZATION, REVENUE, MONTHLY, TOP, LIVE));
    private final DatePicker fromPicker = new DatePicker(LocalDate.now().minusYears(1));
    private final DatePicker toPicker = new DatePicker(LocalDate.now());
    private final Button showButton = new Button("Show");
    private final ProgressBar bar = new ProgressBar(0);
    private final Label summary = new Label("Pick a question and press Show.");
    private final Label statusLabel = new Label("");
    private final TextArea treeText = new TextArea();

    // Two charts share one spot. Bar chart for groups, line chart for change over time.
    private final CategoryAxis barX = new CategoryAxis();
    private final NumberAxis barY = new NumberAxis();
    private final BarChart<String, Number> barChart = new BarChart<>(barX, barY);
    private final CategoryAxis lineX = new CategoryAxis();
    private final NumberAxis lineY = new NumberAxis();
    private final LineChart<String, Number> lineChart = new LineChart<>(lineX, lineY);
    private final StackPane chartHolder = new StackPane(barChart);

    /** Creates a dashboard tab. */
    public DashboardTab(Database db) {
        super("Dashboard");
        this.db = db;
        setClosable(false);

        metricBox.setValue(MOST_RENTED);
        metricBox.setPrefWidth(300);
        showButton.getStyleClass().add("primary-button");
        bar.setVisible(false);
        bar.setPrefWidth(200);
        summary.getStyleClass().add("status-label");
        summary.setMaxWidth(900);
        statusLabel.getStyleClass().add("hint-label");
        barChart.setAnimated(false);
        barChart.setLegendVisible(false);
        lineChart.setAnimated(false);
        lineChart.setLegendVisible(false);
        chartHolder.setPrefHeight(290);
        chartHolder.setMinHeight(220);
        VBox.setVgrow(chartHolder, Priority.ALWAYS);
        treeText.setEditable(false);
        treeText.setPrefRowCount(7);
        treeText.setMinHeight(130);

        Label q = new Label("Question");
        q.getStyleClass().add("field-label");
        Label f = new Label("From");
        f.getStyleClass().add("field-label");
        Label t = new Label("To");
        t.getStyleClass().add("field-label");
        fromPicker.setEditable(false);
        toPicker.setEditable(false);
        // A FlowPane wraps to a second line on a narrow window, so no button is squeezed to
        // "Sh...".
        javafx.scene.layout.FlowPane controls = new javafx.scene.layout.FlowPane(10, 8, q, metricBox, f, fromPicker, t, toPicker, showButton, bar);
        controls.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        controls.setColumnHalignment(javafx.geometry.HPos.LEFT);
        controls.setRowValignment(javafx.geometry.VPos.CENTER);

        // The chart updates by itself whenever any control changes.
        metricBox.setOnAction(e -> refresh());
        fromPicker.setOnAction(e -> refresh());
        toPicker.setOnAction(e -> refresh());
        showButton.setOnAction(e -> refresh());

        Label treeTitle = UiHelper.title("Mileage by category (recursive total)");
        VBox card = new VBox(12, UiHelper.title("Fleet dashboard"), controls, summary, statusLabel,
                chartHolder, treeTitle, treeText);
        card.getStyleClass().add("card");
        HBox.setHgrow(card, Priority.ALWAYS);
        HBox content = new HBox(card);
        content.setPadding(new javafx.geometry.Insets(18));
        setContent(UiHelper.scroll(content));
    }

    /** Reloads the data of this tab from the database. */
    public void refresh() {
        LocalDate from = fromPicker.getValue();
        LocalDate to = toPicker.getValue();
        if (from != null && to != null && from.isAfter(to)) {
            summary.setText("The 'From' date is after the 'To' date.");
            clearCharts();
            return;
        }
        try {
            List<Vehicle> vehicles = db.getAllVehicles();
            // Old rentals of a removed vehicle still count in the money and the counts.
            List<Vehicle> everAdded = db.getAllVehiclesIncludingRemoved();
            treeText.setText(FleetAnalytics.mileageTree(vehicles).toText(0));
            String metric = metricBox.getValue();
            if (LIVE.equals(metric)) {
                showLive(vehicles);
                return;
            }
            List<Rental> rentals = db.getAllRentals();
            Map<String, Number> data = new LinkedHashMap<>();
            String yLabel;
            String xLabel;
            if (MOST_RENTED.equals(metric)) {
                for (Map.Entry<String, Integer> e : FleetAnalytics.rentalsByType(rentals, everAdded, from, to).entrySet()) {
                    data.put(e.getKey(), e.getValue());
                }
                xLabel = "Vehicle type";
                yLabel = "Number of rentals";
            } else if (UTILIZATION.equals(metric)) {
                for (Map.Entry<YearMonth, Double> e : FleetAnalytics.utilizationByMonth(rentals, vehicles, from, to).entrySet()) {
                    data.put(e.getKey().toString(), Math.round(e.getValue() * 10) / 10.0);
                }
                xLabel = "Month";
                yLabel = "Fleet in use (%)";
            } else if (REVENUE.equals(metric)) {
                for (Map.Entry<String, Double> e : FleetAnalytics.revenueByType(rentals, everAdded, from, to).entrySet()) {
                    data.put(e.getKey(), e.getValue());
                }
                xLabel = "Vehicle type";
                yLabel = "Revenue";
            } else if (MONTHLY.equals(metric)) {
                for (Map.Entry<YearMonth, Integer> e : FleetAnalytics.rentalsPerMonth(rentals, from, to).entrySet()) {
                    data.put(e.getKey().toString(), e.getValue());
                }
                xLabel = "Month";
                yLabel = "Number of rentals";
            } else {
                for (Map.Entry<String, Integer> e : FleetAnalytics.topModels(rentals, everAdded, from, to, 5)) {
                    data.put(e.getKey(), e.getValue());
                }
                xLabel = "Model";
                yLabel = "Number of rentals";
            }
            summary.setText(FleetAnalytics.summary(rentals, everAdded, from, to));
            statusLabel.setText("");
            draw(metric, xLabel, yLabel, data);
        } catch (SQLException e) {
            UiHelper.setStatus(summary, ErrorMessages.forDatabase("loading the dashboard data", e), true);
        }
    }

    // Live mining on real API data: how many models does NHTSA list for each make we own?
    // One API call per make, all on a background thread.
    private void showLive(List<Vehicle> vehicles) {
        TreeSet<String> makes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Vehicle v : vehicles) {
            makes.add(v.getMake());
        }
        if (makes.isEmpty()) {
            summary.setText("No vehicles yet. Add vehicles first.");
            clearCharts();
            return;
        }
        showButton.setDisable(true);
        bar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        bar.setVisible(true);
        summary.setText("Asking NHTSA about " + makes.size() + " makes...");
        Task<Map<String, Number>> task = new Task<Map<String, Number>>() {
            @Override
            protected Map<String, Number> call() throws Exception {
                Map<String, Number> counts = new LinkedHashMap<>();
                for (String make : makes) {
                    counts.put(make, vpic.fetchModels(make).size());
                }
                return counts;
            }
        };
        task.setOnSucceeded(e -> {
            Map<String, Number> counts = task.getValue();
            String top = null;
            for (Map.Entry<String, Number> en : counts.entrySet()) {
                if (top == null || en.getValue().intValue() > counts.get(top).intValue()) {
                    top = en.getKey();
                }
            }
            summary.setText("NHTSA lists the most models for " + top + " (" + counts.get(top) + ").");
            draw(LIVE, "Make", "Models listed by NHTSA", counts);
            bar.setVisible(false);
            showButton.setDisable(false);
        });
        task.setOnFailed(e -> {
            Throwable error = task.getException();
            UiHelper.setStatus(summary, ErrorMessages.forApi("NHTSA", error), true);
            clearCharts();
            bar.setVisible(false);
            showButton.setDisable(false);
        });
        UiHelper.runInBackground(task);
    }

    private void clearCharts() {
        barChart.getData().clear();
        lineChart.getData().clear();
    }

    // Draws the mining result. Utilization and rentals per month change over time, so they use the
    // line chart. The other questions compare groups, so they use the bar chart.
    private void draw(String title, String xLabel, String yLabel, Map<String, Number> data) {
        boolean asLine = UTILIZATION.equals(title) || MONTHLY.equals(title);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(title);
        for (Map.Entry<String, Number> e : data.entrySet()) {
            series.getData().add(new XYChart.Data<String, Number>(e.getKey(), e.getValue()));
        }
        clearCharts();
        if (asLine) {
            lineChart.setTitle(title);
            lineX.setLabel(xLabel);
            lineY.setLabel(yLabel);
            lineChart.getData().add(series);
            chartHolder.getChildren().setAll(lineChart);
        } else {
            barChart.setTitle(title);
            barX.setLabel(xLabel);
            barY.setLabel(yLabel);
            barChart.getData().add(series);
            chartHolder.getChildren().setAll(barChart);
        }
    }
}
