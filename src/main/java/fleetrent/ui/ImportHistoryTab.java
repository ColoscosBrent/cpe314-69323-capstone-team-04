package fleetrent.ui;

import fleetrent.controller.ControllerException;
import fleetrent.controller.ImportController;
import fleetrent.model.ImportBatch;
import fleetrent.model.ImportRecord;

import java.time.format.DateTimeFormatter;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

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
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ImportController controller;
    private final TableView<ImportBatch> batches = new TableView<>();
    private final TableView<ImportRecord> records = new TableView<>();
    private final ChoiceBox<String> filter = new ChoiceBox<>(FXCollections.observableArrayList(
            ALL_ROWS, SAVED_ROWS, SKIPPED_ROWS));
    private final Label note = new Label("No import yet. Import a CSV file in the Import tab.");
    private final Label recordsTitle = UiHelper.title("Rows of the chosen import");

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
        HBox filterRow = new HBox(Layout.GAP_NORMAL, new Label("Show:"), filter);
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
        batches.getColumns().add(column("Read", COL_SMALL, b -> blank(b.rowsRead())));
        batches.getColumns().add(column("Saved", COL_SMALL, b -> blank(b.rowsAdded())));
        batches.getColumns().add(column("Skipped", COL_SMALL, b -> blank(b.rowsSkipped())));
        batches.setPrefHeight(BATCH_TABLE_HEIGHT);
        batches.setMinHeight(BATCH_TABLE_HEIGHT);
        batches.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        batches.setPlaceholder(new Label("No import yet."));
    }

    private void buildRecordTable() {
        records.getColumns().add(column("Line", COL_SMALL, r -> blank(r.lineNo())));
        records.getColumns().add(column("Plate", COL_MEDIUM, r -> blank(r.plateNumber())));
        records.getColumns().add(column("Type", COL_MEDIUM, r -> blank(r.vehicleType())));
        records.getColumns().add(column("Make", COL_MEDIUM, r -> blank(r.make())));
        records.getColumns().add(column("Model", COL_MEDIUM, r -> blank(r.model())));
        records.getColumns().add(column("Year", COL_SMALL, r -> blank(r.year())));
        records.getColumns().add(column("Renter", COL_WIDE, r -> blank(r.renterName())));
        records.getColumns().add(column("Start", COL_MEDIUM, r -> blank(r.startDate())));
        records.getColumns().add(column("End", COL_MEDIUM, r -> blank(r.endDate())));
        records.getColumns().add(column("Miles", COL_SMALL, r -> blank(r.milesDriven())));
        records.getColumns().add(column("Result", COL_SMALL, ImportRecord::statusLabel));
        records.getColumns().add(column("Reason", COL_REASON, r -> blank(r.message())));
        records.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        records.setPlaceholder(new Label("Choose an import above."));
    }

    /** Reloads the list of imports and keeps the chosen one when it still exists. */
    public void refresh() {
        try {
            ImportBatch keep = batches.getSelectionModel().getSelectedItem();
            List<ImportBatch> all = controller.history();
            batches.getItems().setAll(all);
            if (!all.isEmpty()) {
                int index = 0;
                for (int i = 0; i < all.size(); i++) {
                    if (keep != null && all.get(i).id() == keep.id()) {
                        index = i;
                    }
                }
                batches.getSelectionModel().select(index);
            } else {
                records.getItems().clear();
            }
            note.setText(all.isEmpty() ? "No import yet. Import a CSV file in the Import tab."
                    : all.size() + (all.size() == 1 ? " import." : " imports.") + " The newest is"
                            + " on top.");
        } catch (ControllerException e) {
            UiHelper.setStatus(note, e.getMessage(), true);
        }
    }

    private void showRecords() {
        ImportBatch batch = batches.getSelectionModel().getSelectedItem();
        if (batch == null) {
            records.getItems().clear();
            return;
        }
        String status = SAVED_ROWS.equals(filter.getValue()) ? "ADDED"
                : SKIPPED_ROWS.equals(filter.getValue()) ? "SKIPPED" : null;
        try {
            List<ImportRecord> rows = controller.records(batch.id(), status);
            records.getItems().setAll(rows);
            recordsTitle.setText("Rows of " + batch.fileName());
            if (rows.size() >= ImportController.recordLimit()) {
                UiHelper.setStatus(note, "Showing the first " + ImportController.recordLimit()
                        + " rows of this import.", false);
            }
        } catch (ControllerException e) {
            UiHelper.setStatus(note, e.getMessage(), true);
        }
    }
}
