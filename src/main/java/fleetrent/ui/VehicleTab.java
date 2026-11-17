package fleetrent.ui;

import fleetrent.controller.ControllerException;
import fleetrent.controller.RuleException;
import fleetrent.controller.VehicleController;
import fleetrent.dto.VehicleModelInfo;
import fleetrent.model.Vehicle;
import fleetrent.model.VehicleFactory;
import fleetrent.model.VehicleTypes;
import fleetrent.service.Validators;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;

/**
 * Tab 1: add, edit and remove vehicles (every Vehicle field of the ERD), choose the make and model
 * from the NHTSA API, and see the fleet. The vehicle type decides which makes and models NHTSA is
 * asked for, so the type and the model can never disagree.
 */
public class VehicleTab extends Tab {
    private static final double PREVIEW_LOAD_WIDTH = 300;
    private static final double PREVIEW_WIDTH = 240;
    private static final double PREVIEW_HEIGHT = 125;
    private static final double TYPE_BOX_PREF_WIDTH = 280;
    private static final double TYPE_BOX_MIN_WIDTH = 280;
    private static final double YEAR_BOX_PREF_WIDTH = 280;
    private static final double YEAR_BOX_MIN_WIDTH = 280;
    private static final double COLOR_BOX_PREF_WIDTH = 280;
    private static final double COLOR_BOX_MIN_WIDTH = 280;
    private static final double CATEGORY_BOX_PREF_WIDTH = 280;
    private static final double CATEGORY_BOX_MIN_WIDTH = 280;
    private static final double PMS_BOX_WIDTH = 280;
    private static final double YEAR_NOTE_MAX_WIDTH = 300;
    private static final double PHOTO_NAME_MAX_WIDTH = 400;
    private static final double FORM_HGAP = 12;
    private static final double FORM_VGAP = 7;
    private static final double TYPE_NOTE_MAX_WIDTH = 400;
    private static final double STATUS_MAX_WIDTH = 400;
    private static final double FORM_CARD_PREF_WIDTH = 420;
    private static final double FORM_CARD_MIN_WIDTH = 420;
    private static final Insets CONTENT_PADDING = new Insets(18);
    private static final int OLDEST_YEAR = 1996;
    private static final String YEAR_FIRST = "Choose the year first. Then pick the make and model.";
    private static final String PHOTO_HINT =
            "No photo chosen. FleetRent will look online for one in the chosen color.";
    private static final String CATEGORY_HINT = "NHTSA lists makes and models only, so choose "
            + "the category and the transmission of your unit.";

    private final VehicleController controller;
    private final VehiclePictures pictures;
    private Path chosenPhoto;
    // The vehicle being edited, or null while the form adds a new vehicle.
    private Vehicle editing;

    private final Label status = new Label("Ready.");
    private final Button addButton = new Button("Add vehicle");
    private final Button cancelEditButton = new Button("Cancel editing");
    private final Button removePhotoButton = new Button("Remove saved photo");
    private final Label formTitle = UiHelper.title("Add a vehicle");
    private final Label photoName = new Label(PHOTO_HINT);
    private final Label typeNote =
            new Label(
            "The type cannot be changed. To change it, remove the vehicle and add it again.");

    // form
    private final ChoiceBox<String> typeBox =
            new ChoiceBox<>(FXCollections.observableArrayList(VehicleTypes.ALL));
    private final ValidatedField makeField = new ValidatedField(Validators.Kind.VEHICLE_TEXT,
            "Pick from the NHTSA list", true);
    private final ValidatedField modelField = new ValidatedField(Validators.Kind.VEHICLE_TEXT,
            "Pick from the NHTSA list", true);
    private final ComboBox<String> yearBox = new ComboBox<>();
    private final ComboBox<String> colorBox =
            new ComboBox<>(FXCollections.observableArrayList(ColorNames.choices()));
    private final ValidatedField plateField = new ValidatedField(Validators.Kind.PLATE, "ABC 123",
            true);
    private final ValidatedField rateField = new ValidatedField(Validators.Kind.MONEY, "1500",
            true);
    private final ValidatedField mileageField = new ValidatedField(Validators.Kind.DIGITS, "0",
            false);
    private final DatePicker pmsPicker = new DatePicker();
    private final ChoiceBox<String> categoryBox = new ChoiceBox<>();
    private final ChoiceBox<String> gearBox = new ChoiceBox<>();
    private final Label yearError = UiHelper.errorLabel();
    private final Label colorError = UiHelper.errorLabel();
    private final Label categoryError = UiHelper.errorLabel();
    private final Label yearNote = new Label();

    private final Label categoryNote = new Label(CATEGORY_HINT);

    private final NhtsaPicker picker;
    private final FleetListCard fleetCard;
    private boolean busyChangingForm;
    private StackPane preview;
    private Label previewName;

    /** Creates a vehicle tab. */
    public VehicleTab(VehicleController controller, VehiclePictures pictures) {
        super("Vehicles");
        this.controller = controller;
        this.pictures = pictures;
        setClosable(false);

        configureInputs();
        initPreview();
        HBox photoRow = buildPhotoRow();
        // Make and model are chosen from the NHTSA lists, so they cannot be typed by hand while
        // NHTSA works.
        picker = new NhtsaPicker(controller, makeField, modelField, typeBox::getValue,
                yearBox::getValue, this::suggestCategory);
        fleetCard = new FleetListCard(controller, pictures, status, this::beginEdit,
                this::removeVehicle, this::refresh);
        VBox formCard = buildFormCard(photoRow);

        HBox content = new HBox(Layout.GAP_XLARGE, formCard, picker.node(), fleetCard.node());
        content.setPadding(CONTENT_PADDING);
        setContent(UiHelper.scroll(content));
        refresh();
        picker.loadMakes();
        onYearChanged(null);
    }

    // Sets the size, the prompt and the choices of the boxes of the form.
    private void configureInputs() {
        typeBox.setValue(VehicleTypes.ALL.get(0));
        typeBox.setPrefWidth(TYPE_BOX_PREF_WIDTH);
        typeBox.setMinWidth(TYPE_BOX_MIN_WIDTH);
        for (int y = LocalDate.now().getYear(); y >= OLDEST_YEAR; y--) {
            yearBox.getItems().add(String.valueOf(y));
        }
        yearBox.setPromptText("Choose the year");
        yearBox.setPrefWidth(YEAR_BOX_PREF_WIDTH);
        yearBox.setMinWidth(YEAR_BOX_MIN_WIDTH);
        colorBox.setPromptText("Pick a color");
        colorBox.setCellFactory(list -> new ColorCell());
        colorBox.setButtonCell(new ColorCell());
        colorBox.setPrefWidth(COLOR_BOX_PREF_WIDTH);
        colorBox.setMinWidth(COLOR_BOX_MIN_WIDTH);
        categoryBox.setPrefWidth(CATEGORY_BOX_PREF_WIDTH);
        categoryBox.setMinWidth(CATEGORY_BOX_MIN_WIDTH);
        gearBox.setPrefWidth(CATEGORY_BOX_PREF_WIDTH);
        gearBox.setMinWidth(CATEGORY_BOX_MIN_WIDTH);
        categoryBox.valueProperty().addListener((obs, o, n) -> onStyleChanged());
        fillStyles();
        pmsPicker.setEditable(false);
        pmsPicker.setPromptText("Optional");
        pmsPicker.setMaxWidth(Double.MAX_VALUE);
        Format.styleDatePicker(pmsPicker);
        pmsPicker.setDayCellFactory(p -> new javafx.scene.control.DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.isAfter(LocalDate.now()));
            }
        });
        yearNote.getStyleClass().add("hint-label");
        yearNote.setWrapText(true);
        yearNote.setMaxWidth(YEAR_NOTE_MAX_WIDTH);
        yearNote.setManaged(false);
        yearNote.setVisible(false);
        mileageField.field().setText("0");
    }

    // The live car picture. It follows the Type box and the Color box.
    private void initPreview() {
        preview = new StackPane();
        preview.getStyleClass().add("preview-stage");
        previewName = new Label("Pick a color below");
        previewName.getStyleClass().add("hint-label");
        colorBox.valueProperty().addListener((obs, o, n) -> {
            UiHelper.setError(colorError, null);
            redraw();
        });
        redraw();
    }

    private HBox buildPhotoRow() {
        photoName.getStyleClass().add("hint-label");
        photoName.setWrapText(true);
        photoName.setMaxWidth(PHOTO_NAME_MAX_WIDTH);
        Button photoButton = new Button("Choose photo...");
        photoButton.getStyleClass().add("secondary-button");
        Button clearPhotoButton = new Button("Clear");
        clearPhotoButton.getStyleClass().add("ghost-button");
        photoButton.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose a photo of the car");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    "Pictures (JPG, PNG, GIF, BMP)", "*.jpg", "*.jpeg", "*.png", "*.gif", "*.bmp"));
            File file = chooser.showOpenDialog(photoButton.getScene().getWindow());
            if (file != null) {
                if (!VehicleController.isPictureName(file.getName())) {
                    UiHelper.setStatus(status, "Please choose a JPG, PNG, GIF or BMP picture.",
                            true);
                    return;
                }
                chosenPhoto = file.toPath();
                photoName.setText(file.getName());
                redraw();
            }
        });
        clearPhotoButton.setOnAction(e -> {
            chosenPhoto = null;
            photoName.setText(PHOTO_HINT);
            redraw();
        });
        HBox photoRow = new HBox(Layout.GAP_NORMAL, photoButton, clearPhotoButton,
                removePhotoButton);
        photoRow.setAlignment(Pos.CENTER_LEFT);
        return photoRow;
    }

    // The form to add or edit a vehicle, with its buttons and its status line.
    private VBox buildFormCard(HBox photoRow) {
        Button clearPms = new Button("Clear");
        clearPms.getStyleClass().add("ghost-button");
        clearPms.setOnAction(e -> pmsPicker.setValue(null));
        GridPane form = new GridPane();
        form.setHgap(FORM_HGAP);
        form.setVgap(FORM_VGAP);
        int r = 0;
        UiHelper.addRow(form, r++, "Type", typeBox);
        UiHelper.addRow(form, r++, "Year", new VBox(Layout.GAP_HAIR, yearBox, yearNote,
                yearError));
        UiHelper.addRow(form, r++, "Make", makeField.node());
        UiHelper.addRow(form, r++, "Model", modelField.node());
        UiHelper.addRow(form, r++, "Color", UiHelper.withError(colorBox, colorError));
        UiHelper.addRow(form, r++, "Plate number", plateField.node());
        UiHelper.addRow(form, r++, "Daily rate", rateField.node());
        UiHelper.addRow(form, r++, "Mileage", mileageField.node());
        UiHelper.addRow(form, r++, "Last PMS", pmsRow(clearPms));
        UiHelper.addRow(form, r++, "Category", UiHelper.withError(categoryBox, categoryError));
        UiHelper.addRow(form, r++, "Transmission", new VBox(Layout.GAP_HAIR, gearBox,
                categoryNote));
        styleFormControls();

        HBox saveRow = new HBox(Layout.GAP_MEDIUM, addButton, cancelEditButton);
        saveRow.setAlignment(Pos.CENTER_LEFT);
        VBox formCard = new VBox(Layout.GAP_NORMAL, formTitle, preview, previewName,
                typeNote, form,
                photoRow, photoName, saveRow, status);
        formCard.getStyleClass().add("card");
        formCard.setPrefWidth(FORM_CARD_PREF_WIDTH);
        formCard.setMinWidth(FORM_CARD_MIN_WIDTH);

        addButton.setOnAction(event -> saveVehicle());

        typeBox.valueProperty().addListener((obs, o, n) -> onTypeChanged());
        yearBox.valueProperty().addListener((obs, o, n) -> onYearChanged(n));
        return formCard;
    }

    // The look of the buttons and the notes of the form, and what the edit buttons do.
    private void styleFormControls() {
        categoryNote.getStyleClass().add("hint-label");
        categoryNote.setWrapText(true);
        categoryNote.setMaxWidth(YEAR_NOTE_MAX_WIDTH);
        addButton.getStyleClass().add("primary-button");
        cancelEditButton.getStyleClass().add("secondary-button");
        cancelEditButton.setOnAction(e -> cancelEdit());
        removePhotoButton.getStyleClass().add("secondary-button");
        removePhotoButton.setOnAction(e -> removeSavedPhoto());
        typeNote.getStyleClass().add("hint-label");
        typeNote.setWrapText(true);
        typeNote.setMaxWidth(TYPE_NOTE_MAX_WIDTH);
        setEditControls(false);
        status.getStyleClass().add("status-label");
        status.setWrapText(true);
        status.setMaxWidth(STATUS_MAX_WIDTH);
    }

    // The date box and its Clear button share one row that is as wide as the other boxes.
    private HBox pmsRow(Button clearPms) {
        HBox row = new HBox(Layout.GAP_NORMAL, pmsPicker, clearPms);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPrefWidth(PMS_BOX_WIDTH);
        row.setMinWidth(PMS_BOX_WIDTH);
        row.setMaxWidth(PMS_BOX_WIDTH);
        HBox.setHgrow(pmsPicker, Priority.ALWAYS);
        clearPms.setMinWidth(Region.USE_PREF_SIZE);
        return row;
    }

    // ---------- live picture ----------

    private void redraw() {
        Image chosen = chosenPhoto == null ? null : pictures.load(chosenPhoto, PREVIEW_LOAD_WIDTH);
        if (chosen != null) {
            preview.getChildren().setAll(VehiclePictures.photoNode(chosen, PREVIEW_WIDTH,
                    PREVIEW_HEIGHT));
            previewName.setText("Your photo");
            return;
        }
        Path saved = editing == null ? null : controller.photoFile(editing.getId());
        Image savedImage = saved == null ? null : pictures.load(saved, PREVIEW_LOAD_WIDTH);
        if (savedImage != null) {
            preview.getChildren().setAll(VehiclePictures.photoNode(savedImage, PREVIEW_WIDTH,
                    PREVIEW_HEIGHT));
            previewName.setText("Saved photo");
            return;
        }
        String color = colorBox.getValue();
        preview.getChildren().setAll(CarGraphic.create(typeBox.getValue(), color, 170));
        previewName.setText(color == null ? "Pick a color below" : color);
    }

    // ---------- type, make, model, year ----------

    private void onTypeChanged() {
        fillStyles();
        UiHelper.setError(categoryError, null);
        clearMakeAndModel();
        redraw();
        picker.loadMakes();
    }

    // The Category box lists the styles of the chosen type (Sedan: Compact, Luxury ...; Motorcycle:
    // Scooter, Sport, Cruiser ...).
    private void fillStyles() {
        categoryBox.getItems().setAll(VehicleTypes.styles(typeBox.getValue()));
        categoryBox.setValue(categoryBox.getItems().get(0));
        onStyleChanged();
        categoryNote.setText(CATEGORY_HINT);
    }

    // The Transmission box lists the gearboxes that exist for the chosen style.
    private void onStyleChanged() {
        String style = categoryBox.getValue();
        if (style == null) {
            return;
        }
        gearBox.getItems().setAll(VehicleTypes.transmissions(typeBox.getValue(), style));
        gearBox.setValue(gearBox.getItems().get(0));
    }

    // A model was clicked in the NHTSA list. NHTSA does not send the category or the
    // transmission, so the app suggests the category of a model it knows and says so. The owner
    // can still change both boxes.
    private void suggestCategory(String model) {
        String type = typeBox.getValue();
        String style = VehicleController.suggestStyle(type, makeField.text(), model);
        if (style == null || !categoryBox.getItems().contains(style)) {
            categoryNote.setText(CATEGORY_HINT);
            return;
        }
        categoryBox.setValue(style);
        categoryNote.setText("Suggested for " + makeField.text() + " " + model + ": " + style
                + ", " + gearBox.getValue() + ". Change them if your unit is different.");
    }

    // The make and model can only be chosen after the year, because a model may not exist in
    // every year.
    private void onYearChanged(String year) {
        boolean chosen = year != null;
        picker.setEnabled(chosen);
        UiHelper.setError(yearError, null);
        if (!chosen) {
            showYearNote(YEAR_FIRST);
            return;
        }
        showYearNote(null);
        if (busyChangingForm) {
            return;
        }
        picker.yearChanged();
    }

    private void clearMakeAndModel() {
        picker.clear();
        showYearNote(yearBox.getValue() == null ? YEAR_FIRST : null);
    }

    private void showYearNote(String text) {
        boolean has = text != null;
        yearNote.setText(has ? text : "");
        yearNote.setManaged(has);
        yearNote.setVisible(has);
    }

    // ---------- saving ----------

    // The saved category: the style and the transmission of the type, or null when one is missing.
    private String categoryPathOrNull(String type) {
        return categoryBox.getValue() == null || gearBox.getValue() == null ? null
                : VehicleTypes.categoryPath(type, categoryBox.getValue(), gearBox.getValue());
    }

    // Checks every box of the form and shows a red message under each one that is wrong.
    private boolean formIsValid(String type) {
        boolean ok = true;
        // While editing, the make and model that the vehicle already has are always fine.
        boolean makeUnchanged = editing != null && Validators.sameName(makeField.text(),
                editing.getMake());
        boolean modelUnchanged = editing != null && Validators.sameName(modelField.text(),
                editing.getModel());
        if (yearBox.getValue() == null) {
            UiHelper.setError(yearError, "Choose the year first.");
            ok = false;
        }
        if (!makeField.validate()) {
            ok = false;
        } else if (picker.isReady() && !makeUnchanged && !picker.isKnownMake(makeField.text())) {
            makeField.showError("Pick the make from the NHTSA list.");
            ok = false;
        }
        if (!modelField.validate()) {
            ok = false;
        } else if (picker.isReady() && !modelUnchanged && !picker.isKnownModel(modelField.text())) {
            modelField.showError("Pick the model from the NHTSA list.");
            ok = false;
        }
        if (colorBox.getValue() == null) {
            UiHelper.setError(colorError, "Pick a color.");
            ok = false;
        }
        if (!plateField.validate()) {
            ok = false;
        }
        if (!rateField.validate()) {
            ok = false;
        }
        if (!mileageField.validate()) {
            ok = false;
        }
        String path = categoryPathOrNull(type);
        if (path == null || !VehicleTypes.isCategoryOf(type, path)) {
            UiHelper.setError(categoryError, "Pick a category that belongs to " + type + ".");
            ok = false;
        }
        LocalDate pms = pmsPicker.getValue();
        if (pms != null && pms.isAfter(LocalDate.now())) {
            UiHelper.setStatus(status, "The last PMS date cannot be in the future.", true);
            ok = false;
        }
        return ok;
    }

    // The button under the form: it adds a new vehicle, or saves the changes of the vehicle being
    // edited.
    private void saveVehicle() {
        String type = typeBox.getValue();
        if (!formIsValid(type)) {
            if (!status.getText().startsWith("The last PMS")) {
                UiHelper.setStatus(status, "Please fix the red messages first.", true);
            }
            return;
        }
        String path = categoryPathOrNull(type);
        LocalDate pms = pmsPicker.getValue();
        try {
            int year = Integer.parseInt(yearBox.getValue());
            String mileageText = mileageField.text();
            int mileage = mileageText.isEmpty() ? 0 : Integer.parseInt(mileageText);
            int id = editing == null ? 0 : editing.getId();
            VehicleModelInfo info = new VehicleModelInfo(makeField.text(), 0, modelField.text());
            Vehicle vehicle = VehicleFactory.create(id, type, info, year,
                    plateField.text(), colorBox.getValue(), Double.parseDouble(rateField.text()),
                    mileage, pms, path);
            Vehicle samePlate = controller.findByPlate(vehicle.getPlateNumber());
            if (samePlate != null && samePlate.getId() != id) {
                plateField.showError("A vehicle with that plate number already exists.");
                UiHelper.setStatus(status, "Please fix the red messages first.", true);
                return;
            }
            if (editing != null) {
                saveEdit(vehicle);
                return;
            }
            controller.add(vehicle);
            UiHelper.setStatus(status, "Saved: " + vehicle, false);
            fleetCard.afterAdd(vehicle, chosenPhoto);
            resetForm();
            refresh();
        } catch (NumberFormatException e) {
            UiHelper.setStatus(status, "A number in the form is not valid.", true);
        } catch (IllegalArgumentException e) {
            // VehicleFactory throws this for an unknown vehicle type. Show a red message, do not
            // let it escape.
            UiHelper.setStatus(status, e.getMessage() == null ? "A value in the form is not valid."
                    : e.getMessage(), true);
        } catch (ControllerException e) {
            UiHelper.setStatus(status, e.getMessage(), true);
        }
    }

    private void saveEdit(Vehicle vehicle) throws ControllerException {
        try {
            controller.update(vehicle);
        } catch (RuleException e) {
            UiHelper.setStatus(status, e.getMessage(), true);
            return;
        }
        String photoNote = "";
        if (chosenPhoto != null) {
            try {
                controller.savePhoto(vehicle.getId(), chosenPhoto);
            } catch (ControllerException e) {
                photoNote = " The photo could not be used: " + e.getMessage();
            }
        }
        String done = "Saved the changes of " + vehicle.displayName() + " ("
                + vehicle.getPlateNumber() + ")." + photoNote;
        exitEditMode();
        resetForm();
        refresh();
        UiHelper.setStatus(status, done, !photoNote.isEmpty());
    }

    // ---------- edit ----------

    private void setEditControls(boolean editMode) {
        formTitle.setText(editMode && editing != null ? "Edit vehicle #" + editing.getId()
                : "Add a vehicle");
        addButton.setText(editMode ? "Save changes" : "Add vehicle");
        for (javafx.scene.Node n : new javafx.scene.Node[] {cancelEditButton, removePhotoButton,
                typeNote}) {
            n.setVisible(editMode);
            n.setManaged(editMode);
        }
        typeBox.setDisable(editMode);
    }

    // Puts the vehicle in the form. The button then saves the changes of this vehicle.
    private void beginEdit(Vehicle v) {
        editing = v;
        setEditControls(true);
        if (!v.getType().equals(typeBox.getValue())) {
            typeBox.setValue(v.getType());
        } else {
            clearMakeAndModel();
            picker.loadMakes();
        }
        makeField.setValue(v.getMake());
        modelField.setValue(v.getModel());
        busyChangingForm = true;
        String yearText = v.getYear() > 0 ? String.valueOf(v.getYear()) : null;
        if (yearText != null && !yearBox.getItems().contains(yearText)) {
            yearBox.getItems().add(yearText);
        }
        yearBox.setValue(yearText);
        busyChangingForm = false;
        colorBox.setValue(v.getColor());
        plateField.setValue(v.getPlateNumber() == null ? "" : v.getPlateNumber());
        rateField.setValue(new java.math.BigDecimal(Double.toString(v.getDailyRate()))
                .stripTrailingZeros().toPlainString());
        mileageField.setValue(String.valueOf(v.getMileage()));
        pmsPicker.setValue(v.getLastPMS());
        String style = VehicleTypes.styleOf(v.getType(), v.getCategoryPath());
        if (style != null) {
            categoryBox.setValue(style);
            String gear = VehicleTypes.transmissionOf(v.getType(), v.getCategoryPath());
            if (gear != null) {
                gearBox.setValue(gear);
            }
        }
        UiHelper.setError(colorError, null);
        UiHelper.setError(categoryError, null);
        chosenPhoto = null;
        photoName.setText(controller.hasPhoto(v.getId())
                ? "Keeping the saved photo. Choose a new one to replace it. For a photo in a new "
                        + "color, remove the saved photo and press Find photos for all."
                : "No photo yet. Choose one, or FleetRent will use the drawn car.");
        categoryNote.setText(CATEGORY_HINT);
        removePhotoButton.setDisable(!controller.hasPhoto(v.getId()));
        redraw();
        UiHelper.setStatus(status, "Editing " + v.displayName()
                + ". Change what you need, then press Save changes.", false);
    }

    private void exitEditMode() {
        editing = null;
        setEditControls(false);
    }

    private void cancelEdit() {
        exitEditMode();
        resetForm();
        UiHelper.setStatus(status, "Editing cancelled. Nothing was changed.", false);
    }

    private void removeSavedPhoto() {
        if (editing == null) {
            return;
        }
        try {
            controller.deletePhoto(editing.getId());
            chosenPhoto = null;
            photoName.setText(
                    "The saved photo was removed. Choose a new one, or the drawn car is used.");
            removePhotoButton.setDisable(true);
            redraw();
            refresh();
            UiHelper.setStatus(status, "The saved photo was removed.", false);
        } catch (ControllerException e) {
            UiHelper.setStatus(status, e.getMessage(), true);
        }
    }

    // ---------- remove and restore ----------

    private void removeVehicle(Vehicle v) {
        try {
            int reserved = controller.activeReservations(v.getId());
            ButtonType remove = new ButtonType("Remove it", ButtonBar.ButtonData.OK_DONE);
            ButtonType keep = new ButtonType("Keep it", ButtonBar.ButtonData.CANCEL_CLOSE);
            String text = "Renters will not see this vehicle anymore and cannot rent or reserve it."
                    + (reserved > 0 ? "\n\nIts " + reserved
                            + " active reservation(s) will be cancelled." : "")
                    + "\n\nThe rental history is kept. You can bring it back with \"Removed "
                            + "vehicles\".";
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, text, remove, keep);
            UiHelper.themeDialog(alert);
            alert.setTitle("Remove vehicle");
            alert.setHeaderText("Remove " + v.displayName() + " (" + v.getPlateNumber() + ")?");
            alert.initOwner(fleetCard.window());
            java.util.Optional<ButtonType> answer = alert.showAndWait();
            if (answer.isEmpty() || answer.get() != remove) {
                return;
            }
            int cancelled = controller.remove(v.getId());
            if (editing != null && editing.getId() == v.getId()) {
                exitEditMode();
                resetForm();
            }
            refresh();
            UiHelper.setStatus(status, "Removed " + v.displayName() + " (" + v.getPlateNumber()
                    + ")"
                    + (cancelled > 0 ? ". " + cancelled + " reservation(s) were cancelled." : ".")
                    + " It is hidden from renters.", false);
        } catch (ControllerException e) {
            UiHelper.setStatus(status, e.getMessage(), true);
        }
    }

    private void resetForm() {
        clearMakeAndModel();
        plateField.reset();
        rateField.reset();
        mileageField.reset();
        mileageField.field().setText("0");
        mileageField.showError(null);
        busyChangingForm = true;
        yearBox.setValue(null);
        busyChangingForm = false;
        colorBox.setValue(null);
        pmsPicker.setValue(null);
        fillStyles();
        chosenPhoto = null;
        photoName.setText(PHOTO_HINT);
        UiHelper.setError(colorError, null);
        UiHelper.setError(categoryError, null);
        redraw();
        picker.showStatus("Choose the year, then click a make.");
    }

    /** Reloads the data of this tab from the database. */
    public void refresh() {
        controller.refresh();
    }


}
