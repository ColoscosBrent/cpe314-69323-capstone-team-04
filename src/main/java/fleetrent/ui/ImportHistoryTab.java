package fleetrent.ui;

import fleetrent.controller.ImportController;
import fleetrent.model.ImportBatch;
import fleetrent.model.ImportRecord;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * Tab 5: the history of the imported CSV files, for the owner. The top table lists every import
 * (file, who, when, rows saved and skipped). The bottom table shows the rows of the chosen import:
 * the car (type, make, model, year, plate), the renter, the dates and why a row was skipped.
 */
public class ImportHistoryTab extends Tab {
    private static final double BATCH_TABLE_HEIGHT = 190;
    private static final double COL_WIDE = 200;
    private static final double COL_MEDIUM = 130;
    private static final double COL_SMALL = 80;
    private static final double COL_REASON = 320;
    private static final String ALL_ROWS = "All rows";
    private static final String SAVED_ROWS = "Saved only";
    private static final String SKIPPED_ROWS = "Skipped only";
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", java.util.Locale.ENGLISH);

    private final ImportController controller;
    // The id of the import that is chosen in the top table, kept across reloads (0 when none).
    private int keepId;
    private final TableView<ImportBatch> batches = new TableView<>();
    private final TableView<ImportRecord> records = new TableView<>();
    private final ChoiceBox<String> filter = new ChoiceBox<>(FXCollections.observableArrayList(
            ALL_ROWS, SAVED_ROWS, SKIPPED_ROWS));
    private final Label note = new Label("No import yet. Import a CSV file in the Import tab.");
    private final Label recordsTitle = UiHelper.title("Rows of the chosen import");
    private final Button exportButton = new Button("Export this import (CSV)...");

    /** Creates the import history tab. */
    public ImportHistoryTab(ImportController controller) {
        super("Import history");
        this.controller = controller;
        setClosable(false);
        buildBatchTable();
        buildRecordTable();
        filter.setValue(ALL_ROWS);
        filter.valueProperty().addListener((obs, o, n) -> showRecords());
        batches.getSelectionModel().selectedItemProperty().addListener((obs, o, n) ->
                showRecords());
        note.getStyleClass().add("hint-label");
        note.setWrapText(true);
        exportButton.getStyleClass().add("secondary-button");
        exportButton.setDisable(true);
        exportButton.setOnAction(e -> exportChosenImport());
        bindToController();
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox filterRow = new HBox(Layout.GAP_NORMAL, new Label("Show:"), filter, spacer,
                exportButton);
        filterRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox.setVgrow(records, Priority.ALWAYS);
        VBox card = new VBox(Layout.GAP_MEDIUM, UiHelper.title("Imported CSV files"), batches,
                recordsTitle, filterRow, records, note);
        card.getStyleClass().add("card");
        HBox.setHgrow(card, Priority.ALWAYS);
        HBox content = new HBox(card);
        content.setPadding(new javafx.geometry.Insets(Layout.GAP_XLARGE));
        setContent(content);
        refresh();
    }

    private static <S> TableColumn<S, String> column(String title, double width,
            java.util.function.Function<S, String> text) {
        TableColumn<S, String> c = new TableColumn<>(title);
        c.setPrefWidth(width);
        c.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                text.apply(cell.getValue())));
        return c;
    }

    private static String blank(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private void buildBatchTable() {
        batches.getColumns().add(column("File", COL_WIDE, ImportBatch::fileName));
        batches.getColumns().add(column("Imported by", COL_MEDIUM, b -> blank(b.importedBy())));
        batches.getColumns().add(column("When", COL_MEDIUM, b -> WHEN.format(b.importedOn())));
        batches.getColumns().add(column("Read", COL_SMALL, b -> Format.number(b.rowsRead())));
        batches.getColumns().add(column("Saved", COL_SMALL, b -> Format.number(b.rowsAdded())));
        batches.getColumns().add(column("Skipped", COL_SMALL, b -> Format.number(b.rowsSkipped())));
        batches.setPrefHeight(BATCH_TABLE_HEIGHT);
        batches.setMinHeight(BATCH_TABLE_HEIGHT);
        batches.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        batches.setPlaceholder(new Label("No import yet."));
    }

    private void buildRecordTable() {
        records.getColumns().add(column("Line", COL_SMALL, r -> Format.number(r.lineNo())));
        records.getColumns().add(column("Plate", COL_MEDIUM, r -> blank(r.plateNumber())));
        records.getColumns().add(column("Type", COL_MEDIUM, r -> blank(r.vehicleType())));
        records.getColumns().add(column("Make", COL_MEDIUM, r -> blank(r.make())));
        records.getColumns().add(column("Model", COL_MEDIUM, r -> blank(r.model())));
        records.getColumns().add(column("Year", COL_SMALL, r -> blank(r.year())));
        records.getColumns().add(column("Renter", COL_WIDE, r -> blank(r.renterName())));
        records.getColumns().add(column("Start", COL_MEDIUM, r -> Format.date(r.startDate())));
        records.getColumns().add(column("End", COL_MEDIUM, r -> Format.date(r.endDate())));
        records.getColumns().add(column("Miles", COL_SMALL,
                r -> r.milesDriven() == null ? "" : Format.number(r.milesDriven())));
        records.getColumns().add(column("Result", COL_SMALL, ImportRecord::statusLabel));
        records.getColumns().add(column("Reason", COL_REASON, r -> blank(r.message())));
        records.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        records.setPlaceholder(new Label("Choose an import above."));
    }

    // The table of imports shows the controller's observable list. The chosen import and the
    // load problems are part of the controller's state too.
    private void bindToController() {
        batches.setItems(controller.items());
        controller.selectedProperty().bind(batches.getSelectionModel().selectedItemProperty());
        // The table finishes its own update first, then the row is selected again.
        controller.items().addListener((javafx.beans.Observable changed) ->
                Platform.runLater(this::showBatches));
        UiHelper.showProblems(controller, note);
    }

    /** Reloads the list of imports and keeps the chosen one when it still exists. */
    public void refresh() {
        ImportBatch chosen = batches.getSelectionModel().getSelectedItem();
        if (chosen != null) {
            keepId = chosen.id();
        }
        controller.refreshHistory();
    }

    // Runs after the controller replaced the list: selects the import that was chosen before (or
    // the newest one) and updates the note.
    private void showBatches() {
        List<ImportBatch> all = batches.getItems();
        if (!all.isEmpty()) {
            int index = 0;
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).id() == keepId) {
                    index = i;
                }
            }
            batches.getSelectionModel().select(index);
        } else {
            records.getItems().clear();
        }
        exportButton.setDisable(all.isEmpty());
        note.setText(all.isEmpty() ? "No import yet. Import a CSV file in the Import tab."
                : all.size() + (all.size() == 1 ? " import." : " imports.") + " The newest is"
                        + " on top.");
    }

    private void showRecords() {
        ImportBatch batch = batches.getSelectionModel().getSelectedItem();
        if (batch == null) {
            records.getItems().clear();
            return;
        }
        keepId = batch.id();
        String status = SAVED_ROWS.equals(filter.getValue()) ? "ADDED"
                : SKIPPED_ROWS.equals(filter.getValue()) ? "SKIPPED" : null;
        controller.loadRecords(batch.id(), status, rows -> {
            records.getItems().setAll(rows);
            recordsTitle.setText("Rows of " + batch.fileName());
            if (rows.size() >= ImportController.recordLimit()) {
                UiHelper.setStatus(note, "Showing the first "
                        + Format.number(ImportController.recordLimit()) + " rows of this import.",
                        false);
            }
        }, message -> UiHelper.setStatus(note, message, true));
    }

    // Writes the saved rows of the chosen import to a CSV file: exactly the rentals that file
    // added, not every rental in the app.
    private void exportChosenImport() {
        ImportBatch batch = batches.getSelectionModel().getSelectedItem();
        if (batch == null) {
            UiHelper.setStatus(note, "Choose an import in the top table first.", true);
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save the rows of this import as CSV");
        chooser.setInitialFileName("import_" + batch.id() + "_records.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        File file = chooser.showSaveDialog(batches.getScene() == null ? null
                : batches.getScene().getWindow());
        if (file == null) {
            return;
        }
        exportButton.setDisable(true);
        controller.exportImportLater(batch.id(), file.toPath(), rows -> {
            UiHelper.setStatus(note, "Saved the " + Format.number(rows) + " rentals of "
                    + batch.fileName() + " to " + file.getName() + ".", false);
            exportButton.setDisable(false);
        }, message -> {
            UiHelper.setStatus(note, message, true);
            exportButton.setDisable(false);
        });
    }
}
