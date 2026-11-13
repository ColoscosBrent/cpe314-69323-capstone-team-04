package fleetrent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
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
    private final Database db;
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
    public ImportTab(Database db) {
        super("Import");
        this.db = db;
        setClosable(false);

        Label hint = new Label("CSV columns: " + RentalCsvImporter.HEADER);
        hint.getStyleClass().add("hint-label");
        fileLabel.getStyleClass().add("hint-label");
        status.getStyleClass().add("status-label");
        status.setMaxWidth(560);
        bar.setMaxWidth(Double.MAX_VALUE);
        importButton.getStyleClass().add("primary-button");
        importButton.setDisable(true);
        log.setEditable(false);
        log.setPrefRowCount(10);
        VBox.setVgrow(log, Priority.ALWAYS);

        chooseButton.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose a rentals CSV file");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
            File file = chooser.showOpenDialog(getTabPane() == null ? null : getTabPane().getScene().getWindow());
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
        VBox card = new VBox(12, UiHelper.title("Import rentals from CSV"), hint,
                step1, new HBox(10, demoVehiclesButton, sampleButton),
                step2, new HBox(10, chooseButton, importButton), fileLabel, bar, status, log);
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
        Task<RentalCsvImporter.ImportResult> task = new Task<RentalCsvImporter.ImportResult>() {
            @Override
            protected RentalCsvImporter.ImportResult call() throws Exception {
                // This code runs on the background thread. It only touches the window through
                // Platform.runLater.
                return new RentalCsvImporter().importFile(file.toPath(), db,
                        percent -> Platform.runLater(() -> bar.setProgress(percent / 100.0)));
            }
        };
        task.setOnSucceeded(e -> {
            RentalCsvImporter.ImportResult r = task.getValue();
            status.setText("Done. Imported " + r.getImported() + " rentals, skipped " + r.getSkipped() + ".");
            log.setText(r.getErrors().isEmpty() ? "No problems found." : String.join("\n", r.getErrors()));
            setBusy(false);
        });
        task.setOnFailed(e -> {
            Throwable error = task.getException();
            UiHelper.setStatus(status, "Import failed: " + (error instanceof java.sql.SQLException
                    ? ErrorMessages.forDatabase("importing the rentals", (java.sql.SQLException) error)
                    : (error == null ? "unknown error" : error.getMessage())), true);
            setBusy(false);
        });
        UiHelper.runInBackground(task);
    }

    private void addDemoVehicles() {
        try {
            int added = 0;
            for (Vehicle v : SampleData.demoVehicles()) {
                if (db.findVehicleByPlate(v.getPlateNumber()) == null) {
                    db.insertVehicle(v);
                    added++;
                }
            }
            status.setText(added == 0 ? "The demo vehicles are already there." : "Added " + added + " demo vehicles.");
        } catch (SQLException ex) {
            UiHelper.setStatus(status, ErrorMessages.forDatabase("adding the demo vehicles", ex), true);
        }
    }

    private void createSampleCsv() {
        try {
            List<Vehicle> vehicles = db.getAllVehicles();
            if (vehicles.isEmpty()) {
                status.setText("Add some vehicles first (or press 'Add 6 demo vehicles').");
                return;
            }
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save the sample CSV");
            chooser.setInitialFileName("sample_rentals.csv");
            File file = chooser.showSaveDialog(getTabPane() == null ? null : getTabPane().getScene().getWindow());
            if (file == null) {
                return;
            }
            Files.write(file.toPath(), SampleData.rentalLines(vehicles, 50000, 42L, LocalDate.now()));
            chosen = file;
            fileLabel.setText(file.getAbsolutePath());
            importButton.setDisable(false);
            status.setText("Created a CSV with 50,000 rentals for your current vehicles. Press Import.");
        } catch (IOException | SQLException ex) {
            status.setText("Could not create the file: " + ex.getMessage());
        }
    }

    private void setBusy(boolean busy) {
        chooseButton.setDisable(busy);
        importButton.setDisable(busy || chosen == null);
        demoVehiclesButton.setDisable(busy);
        sampleButton.setDisable(busy);
    }
}
