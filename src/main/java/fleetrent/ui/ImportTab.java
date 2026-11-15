package fleetrent.ui;

import fleetrent.controller.ControllerException;
import fleetrent.controller.ImportController;
import fleetrent.model.ImportResult;

import java.io.File;
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
    private static final int LOG_ROWS = 10;
    private static final double STATUS_MAX_WIDTH = 560;

    private final ImportController controller;
    private final Label fileLabel = new Label("No file chosen.");
    private final Label status = new Label("Choose a CSV file, then press Import.");
    private final ProgressBar bar = new ProgressBar(0);
    private final TextArea log = new TextArea();
    private final Button chooseButton = new Button("Choose CSV...");
    private final Button importButton = new Button("Import");
    private final Button cancelButton = new Button("Cancel import");
    private final Button demoVehiclesButton = new Button("Add 6 demo vehicles");
    private final Button sampleButton = new Button("Create practice CSV...");
    private final Button exportButton = new Button("Export all records (CSV)...");
    private File chosen;
    private Runnable onImported = () -> { };

    /** Creates an import tab. */
    public ImportTab(ImportController controller) {
        super("Import");
        this.controller = controller;
        setClosable(false);

        VBox card = buildCard();
        card.getStyleClass().add("card");
        card.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(card, Priority.ALWAYS);
        HBox content = new HBox(card);
        content.setPadding(new javafx.geometry.Insets(Layout.GAP_XLARGE));
        setContent(UiHelper.scroll(content));
    }

    // Sets the look of the controls and what the Cancel button does.
    private void configureControls() {
        fileLabel.getStyleClass().add("hint-label");
        status.getStyleClass().add("status-label");
        status.setMaxWidth(STATUS_MAX_WIDTH);
        bar.setMaxWidth(Double.MAX_VALUE);
        importButton.getStyleClass().add("primary-button");
        importButton.setDisable(true);
        cancelButton.getStyleClass().add("danger-button");
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);
        // The import checks the request after every row and stops, keeping the rows that are
        // already saved.
        cancelButton.setOnAction(e -> {
            controller.cancelImport();
            cancelButton.setDisable(true);
            status.setText("Stopping the import...");
        });
        log.setEditable(false);
        log.setPrefRowCount(LOG_ROWS);
        VBox.setVgrow(log, Priority.ALWAYS);
    }

    // Connects every button to its job.
    private void wireButtons() {
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
        exportButton.getStyleClass().add("secondary-button");
        exportButton.setOnAction(e -> exportRecords());
    }

    private VBox buildCard() {
        configureControls();
        wireButtons();
        Label hint = new Label("CSV columns: " + ImportController.HEADER);
        hint.getStyleClass().add("hint-label");
        Label step1 = new Label("Step 1 (optional demo data, made-up names):");
        step1.getStyleClass().add("field-label");
        Label step2 = new Label("Step 2:");
        step2.getStyleClass().add("field-label");
        Label step3 = new Label("Your records:");
        step3.getStyleClass().add("field-label");
        Label exportHint = new Label("Export all writes every rental in the app: the rentals of "
                + "every imported file and the ones made here, so it can have more rows than one "
                + "file. To get the rows of one file only, open the Import history tab, choose "
                + "the file and press Export this import. A value that is not known is left "
                + "blank.");
        exportHint.getStyleClass().add("hint-label");
        exportHint.setWrapText(true);
        VBox card = new VBox(Layout.GAP_WIDE, UiHelper.title("Import rentals from CSV"), hint,
                step1, new HBox(Layout.GAP_MEDIUM, demoVehiclesButton, sampleButton),
                step2, new HBox(Layout.GAP_MEDIUM, chooseButton, importButton, cancelButton),
                fileLabel, bar, status, log, step3, new HBox(Layout.GAP_MEDIUM, exportButton),
                exportHint);
        return card;
    }

    /** Sets what happens after an import finishes (the history tab reloads itself). */
    public void setOnImported(Runnable action) {
        onImported = action;
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
        // The controller runs the import as a background Task and reports back on this thread.
        controller.startImport(file.toPath(), bar::setProgress, this::finishImport, message -> {
            UiHelper.setStatus(status, "Import failed: " + message, true);
            setBusy(false);
            onImported.run();
        });
    }

    // Shows what the import did. The same file cannot be imported twice, so the Import button
    // stays off until another file is chosen.
    private void finishImport(ImportResult r) {
        String done = r.isCancelled() ? "Cancelled. " + Format.number(r.getImported())
                + " rentals were saved before you stopped. The rest of the file was not read."
                : "Done. Imported " + Format.number(r.getImported()) + " rentals, skipped "
                        + Format.number(r.getSkipped()) + ". The app now has "
                        + Format.number(r.getTotalRentals()) + " rentals in all.";
        UiHelper.setStatus(status, done, r.isCancelled());
        log.setText(r.getErrors().isEmpty() ? "No problems found." : String.join("\n",
                r.getErrors()));
        chosen = null;
        fileLabel.setText("Choose another file to import again.");
        setBusy(false);
        onImported.run();
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
            chooser.setTitle("Save the practice CSV");
            chooser.setInitialFileName("practice_rentals.csv");
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
                    "Created a practice CSV with 50,000 made-up rentals. Press Import.");
        } catch (ControllerException ex) {
            status.setText(ex.getMessage());
        }
    }

    private void exportRecords() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save the records as CSV");
        chooser.setInitialFileName("fleetrent_records.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        File file = chooser.showSaveDialog(getTabPane() == null ? null : getTabPane().getScene()
                .getWindow());
        if (file == null) {
            return;
        }
        exportButton.setDisable(true);
        controller.exportRecordsLater(file.toPath(), rows -> {
            UiHelper.setStatus(status, "Saved " + Format.number(rows) + " rentals (every rental "
                    + "in the app) to " + file.getName() + ".", false);
            exportButton.setDisable(false);
        }, message -> {
            UiHelper.setStatus(status, message, true);
            exportButton.setDisable(false);
        });
    }

    private void setBusy(boolean busy) {
        cancelButton.setVisible(busy);
        cancelButton.setManaged(busy);
        cancelButton.setDisable(!busy);
        chooseButton.setDisable(busy);
        importButton.setDisable(busy || chosen == null);
        demoVehiclesButton.setDisable(busy);
        sampleButton.setDisable(busy);
        exportButton.setDisable(busy);
    }
}
