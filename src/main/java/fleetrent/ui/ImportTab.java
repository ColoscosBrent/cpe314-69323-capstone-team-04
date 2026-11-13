package fleetrent.ui;

import fleetrent.controller.ControllerException;
import fleetrent.controller.ImportController;
import fleetrent.model.ImportResult;

import java.io.File;
import javafx.concurrent.Task;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * Tab 3: load many rentals from a CSV file. The import runs on a background thread with a progress
 * bar.
 */
public class ImportTab extends Tab {
    private static final double STATUS_MAX_WIDTH = 560;
    private static final double PERCENT = 100.0;

    private final ImportController controller;
    private final Label fileLabel = new Label("No file chosen.");
    private final Label status = new Label("Choose a CSV file, then press Import.");
    private final ProgressBar bar = new ProgressBar(0);
    private final TextArea log = new TextArea();
    private final Button chooseButton = new Button("Choose CSV...");
    private final Button importButton = new Button("Import");
    private final Button demoVehiclesButton = new Button("Add 6 demo vehicles");
    private final Button sampleButton = new Button("Create sample CSV...");
    private File chosen;

    /** Creates an import tab. */
    public ImportTab(ImportController controller) {
        super("Import");
        this.controller = controller;
        setClosable(false);

        Label hint = new Label("CSV columns: " + ImportController.HEADER);
        hint.getStyleClass().add("hint-label");
        fileLabel.getStyleClass().add("hint-label");
        status.getStyleClass().add("status-label");
        status.setMaxWidth(STATUS_MAX_WIDTH);
        bar.setMaxWidth(Double.MAX_VALUE);
        importButton.getStyleClass().add("primary-button");
        importButton.setDisable(true);
        log.setEditable(false);
        log.setPrefRowCount(10);
        VBox.setVgrow(log, Priority.ALWAYS);

        chooseButton.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose a rentals CSV file");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files",
                    "*.csv"));
            File file = chooser.showOpenDialog(getTabPane() == null ? null : getTabPane().getScene()
                    .getWindow());
            if (file != null) {
                chosen = file;
                fileLabel.setText(file.getAbsolutePath());
                importButton.setDisable(false);
            }
        });
        importButton.setOnAction(e -> runImport());
        demoVehiclesButton.setOnAction(e -> addDemoVehicles());
        sampleButton.setOnAction(e -> createSampleCsv());

        Label step1 = new Label("Step 1 (optional demo data):");
        step1.getStyleClass().add("field-label");
        Label step2 = new Label("Step 2:");
        step2.getStyleClass().add("field-label");
        VBox card = new VBox(Layout.GAP_WIDE, UiHelper.title("Import rentals from CSV"), hint,
                step1, new HBox(Layout.GAP_MEDIUM, demoVehiclesButton, sampleButton),
                step2, new HBox(Layout.GAP_MEDIUM, chooseButton, importButton), fileLabel, bar,
                        status, log);
        card.getStyleClass().add("card");
        card.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(card, Priority.ALWAYS);
        HBox content = new HBox(card);
        content.setPadding(new javafx.geometry.Insets(18));
        setContent(UiHelper.scroll(content));
    }

    private void runImport() {
        final File file = chosen;
        if (file == null) {
            status.setText("Choose a file first.");
            return;
        }
        setBusy(true);
        bar.setProgress(0);
        log.clear();
        status.setText("Importing...");
        Task<ImportResult> task = new Task<ImportResult>() {
            @Override
            protected ImportResult call() throws Exception {
                // This code runs on the background thread. It only touches the window through
                // Platform.runLater.
                return controller.importFile(file.toPath(),
                        percent -> Platform.runLater(() -> bar.setProgress(percent / PERCENT)));
            }
        };
        task.setOnSucceeded(e -> {
            ImportResult r = task.getValue();
            status.setText("Done. Imported " + r.getImported() + " rentals, skipped "
                    + r.getSkipped() + ".");
            log.setText(r.getErrors().isEmpty() ? "No problems found." : String.join("\n",
                    r.getErrors()));
            setBusy(false);
        });
        task.setOnFailed(e -> {
            Throwable error = task.getException();
            UiHelper.setStatus(status, "Import failed: "
                    + (error == null ? "unknown error" : error.getMessage()), true);
            setBusy(false);
        });
        UiHelper.runInBackground(task);
    }

    private void addDemoVehicles() {
        try {
            int added = controller.addDemoVehicles();
            status.setText(added == 0 ? "The demo vehicles are already there."
                    : "Added " + added + " demo vehicles.");
        } catch (ControllerException ex) {
            UiHelper.setStatus(status, ex.getMessage(), true);
        }
    }

    private void createSampleCsv() {
        try {
            if (!controller.hasVehicles()) {
                status.setText("Add some vehicles first (or press 'Add 6 demo vehicles').");
                return;
            }
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save the sample CSV");
            chooser.setInitialFileName("sample_rentals.csv");
            File file = chooser.showSaveDialog(getTabPane() == null ? null : getTabPane().getScene()
                    .getWindow());
            if (file == null) {
                return;
            }
            controller.writeSampleCsv(file.toPath());
            chosen = file;
            fileLabel.setText(file.getAbsolutePath());
            importButton.setDisable(false);
            status.setText(
                    "Created a CSV with 50,000 rentals for your current vehicles. Press Import.");
        } catch (ControllerException ex) {
            status.setText(ex.getMessage());
        }
    }

    private void setBusy(boolean busy) {
        chooseButton.setDisable(busy);
        importButton.setDisable(busy || chosen == null);
        demoVehiclesButton.setDisable(busy);
        sampleButton.setDisable(busy);
    }
}
