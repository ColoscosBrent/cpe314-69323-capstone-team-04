package fleetrent.ui;

import fleetrent.controller.ControllerException;
import fleetrent.controller.DashboardController;
import fleetrent.controller.DashboardController.Metric;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
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
    private static final double METRIC_BOX_PREF_WIDTH = 300;
    private static final double BAR_PREF_WIDTH = 200;
    private static final double SUMMARY_MAX_WIDTH = 900;
    private static final double CHART_HOLDER_PREF_HEIGHT = 290;
    private static final double CHART_HOLDER_MIN_HEIGHT = 220;
    private static final double TREE_TEXT_MIN_HEIGHT = 130;
    private final DashboardController controller;

    private final ChoiceBox<String> metricBox = new ChoiceBox<>(FXCollections.observableArrayList(
            Arrays.stream(Metric.values()).map(Metric::label).toList()));
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
    public DashboardTab(DashboardController controller) {
        super("Dashboard");
        this.controller = controller;
        setClosable(false);

        metricBox.setValue(Metric.MOST_RENTED.label());
        metricBox.setPrefWidth(METRIC_BOX_PREF_WIDTH);
        showButton.getStyleClass().add("primary-button");
        bar.setVisible(false);
        bar.setPrefWidth(BAR_PREF_WIDTH);
        summary.getStyleClass().add("status-label");
        summary.setMaxWidth(SUMMARY_MAX_WIDTH);
        statusLabel.getStyleClass().add("hint-label");
        barChart.setAnimated(false);
        barChart.setLegendVisible(false);
        lineChart.setAnimated(false);
        lineChart.setLegendVisible(false);
        chartHolder.setPrefHeight(CHART_HOLDER_PREF_HEIGHT);
        chartHolder.setMinHeight(CHART_HOLDER_MIN_HEIGHT);
        VBox.setVgrow(chartHolder, Priority.ALWAYS);
        treeText.setEditable(false);
        treeText.setPrefRowCount(7);
        treeText.setMinHeight(TREE_TEXT_MIN_HEIGHT);

        javafx.scene.layout.FlowPane controls = buildControls();

        // The chart updates by itself whenever any control changes.
        metricBox.setOnAction(e -> refresh());
        fromPicker.setOnAction(e -> refresh());
        toPicker.setOnAction(e -> refresh());
        showButton.setOnAction(e -> refresh());

        Label treeTitle = UiHelper.title("Mileage by category (recursive total)");
        VBox card = new VBox(Layout.GAP_WIDE, UiHelper.title("Fleet dashboard"), controls, summary,
                statusLabel,
                chartHolder, treeTitle, treeText);
        card.getStyleClass().add("card");
        HBox.setHgrow(card, Priority.ALWAYS);
        HBox content = new HBox(card);
        content.setPadding(new javafx.geometry.Insets(18));
        setContent(UiHelper.scroll(content));
    }

    // The row of controls above the chart.
    private javafx.scene.layout.FlowPane buildControls() {
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
        javafx.scene.layout.FlowPane controls = new javafx.scene.layout.FlowPane(10, 8, q,
                metricBox, f, fromPicker, t, toPicker, showButton, bar);
        controls.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        controls.setColumnHalignment(javafx.geometry.HPos.LEFT);
        controls.setRowValignment(javafx.geometry.VPos.CENTER);
        return controls;
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
        Metric metric = Metric.fromLabel(metricBox.getValue());
        try {
            if (metric == Metric.LIVE) {
                treeText.setText(controller.mileageTree());
                showLive(controller.ownedMakes());
                return;
            }
            DashboardController.Result result = controller.answer(metric, from, to);
            treeText.setText(result.treeText());
            summary.setText(result.summary());
            statusLabel.setText("");
            draw(metric, result.data());
        } catch (ControllerException e) {
            UiHelper.setStatus(summary, e.getMessage(), true);
        }
    }

    // Live mining on real API data: how many models does NHTSA list for each make we own?
    // One API call per make, all on a background thread.
    private void showLive(Set<String> makes) {
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
                return controller.modelsPerMake(makes);
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
            summary.setText("NHTSA lists the most models for " + top + " (" + counts.get(top)
                    + ").");
            draw(Metric.LIVE, counts);
            bar.setVisible(false);
            showButton.setDisable(false);
        });
        task.setOnFailed(e -> {
            Throwable error = task.getException();
            UiHelper.setStatus(summary, error == null ? "NHTSA could not be reached."
                    : error.getMessage(), true);
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
    private void draw(Metric metric, Map<String, Number> data) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(metric.label());
        for (Map.Entry<String, Number> e : data.entrySet()) {
            series.getData().add(new XYChart.Data<String, Number>(e.getKey(), e.getValue()));
        }
        clearCharts();
        if (metric.isOverTime()) {
            lineChart.setTitle(metric.label());
            lineX.setLabel(metric.xLabel());
            lineY.setLabel(metric.yLabel());
            lineChart.getData().add(series);
            chartHolder.getChildren().setAll(lineChart);
        } else {
            barChart.setTitle(metric.label());
            barX.setLabel(metric.xLabel());
            barY.setLabel(metric.yLabel());
            barChart.getData().add(series);
            chartHolder.getChildren().setAll(barChart);
        }
    }
}
