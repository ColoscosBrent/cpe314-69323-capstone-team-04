package fleetrent.ui;

import fleetrent.controller.ControllerException;
import fleetrent.controller.RuleException;
import fleetrent.controller.VehicleController;
import fleetrent.controller.VehicleController.Models;
import fleetrent.dto.VehicleModelInfo;
import fleetrent.model.Maintenance;
import fleetrent.model.MaintenanceType;
import fleetrent.model.Vehicle;
import fleetrent.model.VehicleFactory;
import fleetrent.model.VehicleTypes;
import fleetrent.service.Validators;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tab;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;

/**
 * Tab 1: add, edit and remove vehicles (every Vehicle field of the ERD), choose the make and model
 * from the NHTSA API, and see the fleet. The vehicle type decides which makes and models NHTSA is
 * asked for, so the type and the model can never disagree.
 */
public class VehicleTab extends Tab {
    private static final double TYPE_BOX_PREF_WIDTH = 280;
    private static final double TYPE_BOX_MIN_WIDTH = 280;
    private static final double YEAR_BOX_PREF_WIDTH = 280;
    private static final double YEAR_BOX_MIN_WIDTH = 280;
    private static final double COLOR_BOX_PREF_WIDTH = 280;
    private static final double COLOR_BOX_MIN_WIDTH = 280;
    private static final double CATEGORY_BOX_PREF_WIDTH = 280;
    private static final double CATEGORY_BOX_MIN_WIDTH = 280;
    private static final double PMS_PICKER_PREF_WIDTH = 180;
    private static final double YEAR_NOTE_MAX_WIDTH = 300;
    private static final double PHOTO_NAME_MAX_WIDTH = 400;
    private static final double FORM_HGAP = 12;
    private static final double FORM_VGAP = 7;
    private static final double TYPE_NOTE_MAX_WIDTH = 400;
    private static final double STATUS_MAX_WIDTH = 400;
    private static final double FORM_CARD_PREF_WIDTH = 420;
    private static final double FORM_CARD_MIN_WIDTH = 420;
    private static final double LOOKUP_BAR_PREF_WIDTH = 280;
    private static final double LOOKUP_STATUS_MAX_WIDTH = 300;
    private static final double LOOKUP_HINT_MAX_WIDTH = 300;
    private static final double MAKE_LIST_PREF_HEIGHT = 190;
    private static final double MAKE_LIST_MIN_HEIGHT = 190;
    private static final double MODEL_LIST_PREF_HEIGHT = 230;
    private static final double MODEL_LIST_MIN_HEIGHT = 230;
    private static final double LOOKUP_CARD_PREF_WIDTH = 310;
    private static final double LOOKUP_CARD_MIN_WIDTH = 310;
    private static final double LIST_CARD_MIN_WIDTH = 380;
    private static final double LIST_CARD_PREF_WIDTH = 420;
    private static final Insets CONTENT_PADDING = new Insets(18);
    private static final double INFO_MAX_WIDTH = 460;
    private static final double MAINTENANCE_WIDTH = 460;
    private static final double MAINTENANCE_LIST_HEIGHT = 200;
    private static final String NO_YEAR = "Not specified";
    private static final String PHOTO_HINT =
            "No photo chosen. FleetRent will try to find one online.";

    private final VehicleController controller;
    private final VehiclePictures pictures;
    private Path chosenPhoto;
    // The vehicle being edited, or null while the form adds a new vehicle.
    private Vehicle editing;
    // The make and model the person clicked in the NHTSA lists. The Add button accepts them even
    // when the lists are reloading (for example after the Year was changed).
    private String pickedMake;
    private String pickedModel;

    private final ListView<Vehicle> vehicleList = new ListView<>();
    private final Label status = new Label("Ready.");
    private final Label countLabel = new Label("0 vehicles");
    private final Button findPhotosButton = new Button("Find photos for all");
    private final Button removedButton = new Button("Removed vehicles");
    private final Button maintenanceButton = new Button("Maintenance");
    private final Button addButton = new Button("Add Vehicle");
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
    private final ChoiceBox<String> yearBox = new ChoiceBox<>();
    private final ChoiceBox<String> colorBox =
            new ChoiceBox<>(FXCollections.observableArrayList(ColorNames.choices()));
    private final ValidatedField plateField = new ValidatedField(Validators.Kind.PLATE, "ABC 123",
            true);
    private final ValidatedField rateField = new ValidatedField(Validators.Kind.MONEY, "1500",
            true);
    private final ValidatedField mileageField = new ValidatedField(Validators.Kind.DIGITS, "0",
            false);
    private final DatePicker pmsPicker = new DatePicker();
    private final ChoiceBox<String> categoryBox = new ChoiceBox<>();
    private final Label colorError = UiHelper.errorLabel();
    private final Label categoryError = UiHelper.errorLabel();
    private final Label yearNote = new Label();

    // NHTSA picker
    private final ValidatedField makeSearch = new ValidatedField(Validators.Kind.VEHICLE_TEXT,
            "Search makes...", false);
    private final ValidatedField modelSearch = new ValidatedField(Validators.Kind.VEHICLE_TEXT,
            "Search models...", false);
    private final ListView<String> makeList = new ListView<>();
    private final ListView<VehicleModelInfo> modelList = new ListView<>();
    private final ProgressBar lookupBar = new ProgressBar(0);
    private final Label lookupStatus = new Label("Choosing a type loads its makes from NHTSA.");
    private final Button reloadButton = new Button("Reload from NHTSA");
    private List<String> allMakes = new ArrayList<>();
    private List<VehicleModelInfo> allModels = new ArrayList<>();
    private boolean apiReady;
    private boolean busyChangingForm;
    private int makesRequest;
    private int modelsRequest;
    private StackPane preview;
    private Label previewName;

    /** Creates a vehicle tab. */
    public VehicleTab(VehicleController controller, VehiclePictures pictures) {
        super("Vehicles");
        this.controller = controller;
        this.pictures = pictures;
        setClosable(false);

        typeBox.setValue(VehicleTypes.ALL.get(0));
        typeBox.setPrefWidth(TYPE_BOX_PREF_WIDTH);
        typeBox.setMinWidth(TYPE_BOX_MIN_WIDTH);
        yearBox.getItems().add(NO_YEAR);
        for (int y = LocalDate.now().getYear() + 1; y >= 1996; y--) {
            yearBox.getItems().add(String.valueOf(y));
        }
        yearBox.setValue(NO_YEAR);
        yearBox.setPrefWidth(YEAR_BOX_PREF_WIDTH);
        yearBox.setMinWidth(YEAR_BOX_MIN_WIDTH);
        colorBox.setPrefWidth(COLOR_BOX_PREF_WIDTH);
        colorBox.setMinWidth(COLOR_BOX_MIN_WIDTH);
        categoryBox.setPrefWidth(CATEGORY_BOX_PREF_WIDTH);
        categoryBox.setMinWidth(CATEGORY_BOX_MIN_WIDTH);
        categoryBox.getItems().setAll(VehicleTypes.categoryOptions(typeBox.getValue()));
        categoryBox.setValue(categoryBox.getItems().get(0));
        pmsPicker.setEditable(false);
        pmsPicker.setPromptText("Optional");
        pmsPicker.setPrefWidth(PMS_PICKER_PREF_WIDTH);
        pmsPicker.setDayCellFactory(p -> new javafx.scene.control.DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.isAfter(LocalDate.now()));
            }
        });
        Button clearPms = new Button("Clear");
        clearPms.getStyleClass().add("ghost-button");
        clearPms.setOnAction(e -> pmsPicker.setValue(null));
        yearNote.getStyleClass().add("hint-label");
        yearNote.setWrapText(true);
        yearNote.setMaxWidth(YEAR_NOTE_MAX_WIDTH);
        yearNote.setManaged(false);
        yearNote.setVisible(false);
        mileageField.field().setText("0");

        // Live car picture: it follows the Type box and the Color box.
        preview = new StackPane();
        preview.getStyleClass().add("preview-stage");
        previewName = new Label("Pick a color below");
        previewName.getStyleClass().add("hint-label");
        HBox swatches = new HBox(7);
        for (String name : ColorNames.swatchNames()) {
            Circle dot = new Circle(11, ColorNames.toColor(name));
            dot.setStroke(Color.web("#f5b800"));
            dot.setStrokeWidth(1.5);
            dot.setOnMouseClicked(e -> colorBox.setValue(name));
            swatches.getChildren().add(dot);
        }
        colorBox.valueProperty().addListener((obs, o, n) -> {
            UiHelper.setError(colorError, null);
            redraw();
        });
        redraw();

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

        // Make and model are chosen from the NHTSA lists, so they cannot be typed by hand while
        // NHTSA works.
        setManualEntry(false);

        GridPane form = new GridPane();
        form.setHgap(FORM_HGAP);
        form.setVgap(FORM_VGAP);
        int r = 0;
        UiHelper.addRow(form, r++, "Type", typeBox);
        UiHelper.addRow(form, r++, "Make", makeField.node());
        UiHelper.addRow(form, r++, "Model", modelField.node());
        UiHelper.addRow(form, r++, "Year", new VBox(Layout.GAP_HAIR, yearBox, yearNote));
        UiHelper.addRow(form, r++, "Color", UiHelper.withError(colorBox, colorError));
        UiHelper.addRow(form, r++, "Plate number", plateField.node());
        UiHelper.addRow(form, r++, "Daily rate", rateField.node());
        UiHelper.addRow(form, r++, "Mileage", mileageField.node());
        UiHelper.addRow(form, r++, "Last PMS", new HBox(Layout.GAP_NORMAL, pmsPicker, clearPms));
        UiHelper.addRow(form, r++, "Category", UiHelper.withError(categoryBox, categoryError));

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

        HBox saveRow = new HBox(Layout.GAP_MEDIUM, addButton, cancelEditButton);
        saveRow.setAlignment(Pos.CENTER_LEFT);
        VBox formCard = new VBox(Layout.GAP_NORMAL, formTitle, preview, previewName, swatches,
                typeNote, form,
                photoRow, photoName, saveRow, status);
        formCard.getStyleClass().add("card");
        formCard.setPrefWidth(FORM_CARD_PREF_WIDTH);
        formCard.setMinWidth(FORM_CARD_MIN_WIDTH);

        addButton.setOnAction(event -> saveVehicle());

        typeBox.valueProperty().addListener((obs, o, n) -> onTypeChanged());
        yearBox.valueProperty().addListener((obs, o, n) -> {
            if (!busyChangingForm && !makeField.field().getText().isEmpty() && apiReady) {
                loadModels();
            }
        });

        // ----- NHTSA picker card -----
        reloadButton.getStyleClass().add("secondary-button");
        reloadButton.setOnAction(e -> loadMakes());
        lookupBar.setVisible(false);
        lookupBar.setPrefWidth(LOOKUP_BAR_PREF_WIDTH);
        lookupStatus.getStyleClass().add("status-label");
        lookupStatus.setWrapText(true);
        lookupStatus.setMaxWidth(LOOKUP_STATUS_MAX_WIDTH);
        Label lookupHint = new Label("The Type decides the list. Click a make, then click a model. "
                + "Choosing a Year narrows the models.");
        lookupHint.getStyleClass().add("hint-label");
        lookupHint.setWrapText(true);
        lookupHint.setMaxWidth(LOOKUP_HINT_MAX_WIDTH);
        makeList.getStyleClass().add("plain-list");
        makeList.setPrefHeight(MAKE_LIST_PREF_HEIGHT);
        makeList.setMinHeight(MAKE_LIST_MIN_HEIGHT);
        modelList.getStyleClass().add("plain-list");
        modelList.setPrefHeight(MODEL_LIST_PREF_HEIGHT);
        modelList.setMinHeight(MODEL_LIST_MIN_HEIGHT);
        modelList.setCellFactory(list -> new ListCell<VehicleModelInfo>() {
            @Override
            protected void updateItem(VehicleModelInfo item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getModelName());
            }
        });
        makeSearch.field().textProperty().addListener((obs, o, n) -> filterMakes());
        modelSearch.field().textProperty().addListener((obs, o, n) -> filterModels());
        makeList.getSelectionModel().selectedItemProperty().addListener((obs, o, picked) -> {
            if (picked != null) {
                onMakePicked(picked);
            }
        });
        modelList.getSelectionModel().selectedItemProperty().addListener((obs, o, picked) -> {
            if (picked != null) {
                pickedModel = picked.getModelName();
                modelField.setValue(picked.getModelName());
                modelField.showError(null);
            }
        });
        Label makeTitle = new Label("1. Make");
        makeTitle.getStyleClass().add("field-label");
        Label modelTitle = new Label("2. Model");
        modelTitle.getStyleClass().add("field-label");
        VBox lookupCard = new VBox(Layout.GAP_NORMAL, UiHelper.title("Choose from NHTSA"),
                lookupHint,
                new HBox(Layout.GAP_NORMAL, reloadButton), lookupBar, lookupStatus,
                makeTitle, makeSearch.node(), makeList, modelTitle, modelSearch.node(), modelList);
        lookupCard.getStyleClass().add("card");
        lookupCard.setPrefWidth(LOOKUP_CARD_PREF_WIDTH);
        lookupCard.setMinWidth(LOOKUP_CARD_MIN_WIDTH);

        // ----- fleet card -----
        countLabel.getStyleClass().add("count-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        findPhotosButton.getStyleClass().add("secondary-button");
        findPhotosButton.setOnAction(e -> findAllPhotos());
        removedButton.getStyleClass().add("secondary-button");
        removedButton.setOnAction(e -> showRemovedVehicles());
        maintenanceButton.getStyleClass().add("secondary-button");
        maintenanceButton.setOnAction(e -> showMaintenance());
        HBox titleRow = new HBox(Layout.GAP_MEDIUM, UiHelper.title("Our fleet"), spacer,
                countLabel);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        HBox fleetButtons = new HBox(Layout.GAP_NORMAL, maintenanceButton, removedButton,
                findPhotosButton);
        VBox listHeader = new VBox(Layout.GAP_NORMAL, titleRow, fleetButtons);
        Label hint = new Label("Price shown is for 1 day with no extra miles. " + feeHint()
                + " Photos come from your file or Wikipedia.");
        hint.getStyleClass().add("hint-label");
        hint.setWrapText(true);
        vehicleList.setCellFactory(list -> new VehicleCell());
        vehicleList.setFocusTraversable(false);
        vehicleList.getStyleClass().add("vehicle-list");
        VBox.setVgrow(vehicleList, Priority.ALWAYS);
        VBox listCard = new VBox(Layout.GAP_MEDIUM, listHeader, hint, vehicleList);
        listCard.getStyleClass().add("card");
        listCard.setMinWidth(LIST_CARD_MIN_WIDTH);
        listCard.setPrefWidth(LIST_CARD_PREF_WIDTH);
        HBox.setHgrow(listCard, Priority.ALWAYS);

        HBox content = new HBox(Layout.GAP_XLARGE, formCard, lookupCard, listCard);
        content.setPadding(CONTENT_PADDING);
        setContent(UiHelper.scroll(content));
        refresh();
        loadMakes();
    }

    // "Extra-mile fees: Sedan 100 free miles a day then 0.20 per mile, ..." built from the vehicle
    // classes, so the text always matches the real rules.
    private static String feeHint() {
        StringBuilder sb = new StringBuilder("Extra-mile fees: ");
        for (String type : VehicleTypes.ALL) {
            Vehicle sample = VehicleFactory.create(0, type, "", "", 0, "", "", 1, 0, null, null);
            if (sb.length() > 17) {
                sb.append("; ");
            }
            sb.append(type.charAt(0)).append(type.substring(1).toLowerCase(Locale.ROOT)).append(' ')
                    .append(sample.getFreeMilesPerDay()).append(" free miles a day, then ")
                    .append(String.format(Locale.US, "%.2f", sample.getFeePerExtraMile()))
                            .append(" per mile");
        }
        return sb.append('.').toString();
    }

    // ---------- live picture ----------

    private void redraw() {
        Image chosen = chosenPhoto == null ? null : pictures.load(chosenPhoto, 300);
        if (chosen != null) {
            preview.getChildren().setAll(VehiclePictures.photoNode(chosen, 240, 125));
            previewName.setText("Your photo");
            return;
        }
        Path saved = editing == null ? null : controller.photoFile(editing.getId());
        Image savedImage = saved == null ? null : pictures.load(saved, 300);
        if (savedImage != null) {
            preview.getChildren().setAll(VehiclePictures.photoNode(savedImage, 240, 125));
            previewName.setText("Saved photo");
            return;
        }
        String color = colorBox.getValue();
        preview.getChildren().setAll(CarGraphic.create(typeBox.getValue(), color, 170));
        previewName.setText(color == null ? "Pick a color below" : color);
    }

    // ---------- type, make, model, year ----------

    // Make and model boxes: read-only while NHTSA works, typed by hand only when NHTSA cannot be
    // reached.
    private void setManualEntry(boolean manual) {
        makeField.field().setEditable(manual);
        modelField.field().setEditable(manual);
        String hint = manual ? "Type it (NHTSA is not reachable)" : "Pick from the NHTSA list";
        makeField.field().setPromptText(hint);
        modelField.field().setPromptText(hint);
    }

    private void onTypeChanged() {
        categoryBox.getItems().setAll(VehicleTypes.categoryOptions(typeBox.getValue()));
        categoryBox.setValue(categoryBox.getItems().get(0));
        UiHelper.setError(categoryError, null);
        clearMakeAndModel();
        redraw();
        loadMakes();
    }

    private void clearMakeAndModel() {
        pickedMake = null;
        pickedModel = null;
        makeField.reset();
        modelField.reset();
        makeSearch.reset();
        modelSearch.reset();
        makeList.getSelectionModel().clearSelection();
        allModels = new ArrayList<>();
        modelList.getItems().clear();
        showYearNote(null);
    }

    private void showYearNote(String text) {
        boolean has = text != null;
        yearNote.setText(has ? text : "");
        yearNote.setManaged(has);
        yearNote.setVisible(has);
    }

    private void loadMakes() {
        final String type = typeBox.getValue();
        final String apiType = VehicleTypes.apiType(type);
        final int request = ++makesRequest;
        modelsRequest++;
        reloadButton.setDisable(true);
        lookupBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        lookupBar.setVisible(true);
        UiHelper.setStatus(lookupStatus, "Asking NHTSA for " + type + " makes...", false);
        allMakes = new ArrayList<>();
        makeList.getItems().clear();
        modelList.getItems().clear();

        Task<List<String>> task = new Task<List<String>>() {
            @Override
            protected List<String> call() throws Exception {
                return controller.fetchMakes(apiType);
            }
        };
        task.setOnSucceeded(e -> {
            if (request != makesRequest) {
                return;
            }
            allMakes = task.getValue();
            apiReady = !allMakes.isEmpty();
            filterMakes();
            if (apiReady) {
                setManualEntry(false);
                UiHelper.setStatus(lookupStatus, "NHTSA lists " + allMakes.size() + " " + type
                        + " makes. Click one.", false);
            } else {
                setManualEntry(true);
                UiHelper.setStatus(lookupStatus, "NHTSA returned no makes for " + type
                        + ". You can type the make and model by hand.", true);
            }
            finishLookup();
        });
        task.setOnFailed(e -> {
            if (request != makesRequest) {
                return;
            }
            apiReady = false;
            setManualEntry(true);
            UiHelper.setStatus(lookupStatus, task.getException().getMessage()
                    + " Until it works you can type the make and model by hand.", true);
            finishLookup();
        });
        UiHelper.runInBackground(task);
    }

    private void filterMakes() {
        String q = makeSearch.field().getText().trim().toLowerCase(Locale.ROOT);
        List<String> shown = new ArrayList<>();
        for (String m : allMakes) {
            if (q.isEmpty() || m.toLowerCase(Locale.ROOT).contains(q)) {
                shown.add(m);
            }
        }
        makeList.getItems().setAll(shown);
    }

    private void filterModels() {
        String q = modelSearch.field().getText().trim().toLowerCase(Locale.ROOT);
        List<VehicleModelInfo> shown = new ArrayList<>();
        for (VehicleModelInfo m : allModels) {
            if (q.isEmpty() || m.getModelName().toLowerCase(Locale.ROOT).contains(q)) {
                shown.add(m);
            }
        }
        modelList.getItems().setAll(shown);
    }

    private void onMakePicked(String make) {
        pickedMake = make;
        pickedModel = null;
        makeField.setValue(make);
        makeField.showError(null);
        modelField.reset();
        loadModels();
    }

    // Asks NHTSA for the models of the chosen make, for the chosen type and (if chosen) year.
    // If NHTSA has nothing for that year, the list falls back to all years and says so.
    private void loadModels() {
        final String make = makeField.field().getText().trim();
        if (make.isEmpty()) {
            return;
        }
        final String apiType = VehicleTypes.apiType(typeBox.getValue());
        final String yearText = yearBox.getValue();
        final Integer year = (yearText == null || NO_YEAR.equals(yearText)) ? null
                : Integer.valueOf(yearText);
        final int request = ++modelsRequest;
        lookupBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        lookupBar.setVisible(true);
        UiHelper.setStatus(lookupStatus, "Asking NHTSA for " + make + " models...", false);
        modelList.getItems().clear();
        allModels = new ArrayList<>();

        Task<Models> task = new Task<Models>() {
            @Override
            protected Models call() throws Exception {
                return controller.fetchModels(make, year, apiType);
            }
        };
        task.setOnSucceeded(e -> {
            if (request != modelsRequest) {
                return;
            }
            Models result = task.getValue();
            allModels = result.models();
            modelSearch.reset();
            filterModels();
            showYearNote(null);
            if (result.models().isEmpty()) {
                modelField.field().clear();
                UiHelper.setStatus(lookupStatus, "NHTSA has no " + typeBox.getValue()
                        + " models for " + make
                        + ". Pick another make.", true);
            } else if (result.fellBack()) {
                busyChangingForm = true;
                yearBox.setValue(NO_YEAR);
                busyChangingForm = false;
                showYearNote("NHTSA has no " + make + " models for " + year
                        + ", so Year was set to \"" + NO_YEAR + "\".");
                UiHelper.setStatus(lookupStatus, "No models listed for " + year + ". Showing all "
                        + result.models().size() + " " + make + " models. Click one.", false);
            } else {
                UiHelper.setStatus(lookupStatus, result.models().size() + " models for " + make
                        + (year == null ? "" : " " + year) + ". Click one.", false);
            }
            lookupBar.setVisible(false);
        });
        task.setOnFailed(e -> {
            if (request != modelsRequest) {
                return;
            }
            apiReady = false;
            setManualEntry(true);
            UiHelper.setStatus(lookupStatus, task.getException().getMessage()
                    + " You can type the model by hand.", true);
            lookupBar.setVisible(false);
        });
        UiHelper.runInBackground(task);
    }

    private void finishLookup() {
        lookupBar.setVisible(false);
        reloadButton.setDisable(false);
    }

    // ---------- saving ----------

    // True when the make was chosen from NHTSA (or clicked in the list), ignoring capitals and
    // extra spaces.
    private boolean isKnownMake(String text) {
        return Validators.sameName(pickedMake, text)
                || allMakes.stream().anyMatch(m -> Validators.sameName(m, text));
    }

    private boolean isKnownModel(String text) {
        return Validators.sameName(pickedModel, text)
                || allModels.stream().anyMatch(m -> Validators.sameName(m.getModelName(), text));
    }

    // The button under the form: it adds a new vehicle, or saves the changes of the vehicle being
    // edited.
    private void saveVehicle() {
        boolean ok = true;
        String type = typeBox.getValue();
        // While editing, the make and model that the vehicle already has are always fine.
        boolean makeUnchanged = editing != null && Validators.sameName(makeField.text(),
                editing.getMake());
        boolean modelUnchanged = editing != null && Validators.sameName(modelField.text(),
                editing.getModel());
        if (!makeField.validate()) {
            ok = false;
        } else if (apiReady && !makeUnchanged && !isKnownMake(makeField.text())) {
            makeField.showError("Pick the make from the NHTSA list.");
            ok = false;
        }
        if (!modelField.validate()) {
            ok = false;
        } else if (apiReady && !modelUnchanged && !isKnownModel(modelField.text())) {
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
        if (categoryBox.getValue() == null || !VehicleTypes.isCategoryOf(type,
                categoryBox.getValue())) {
            UiHelper.setError(categoryError, "Pick a category that belongs to " + type + ".");
            ok = false;
        }
        LocalDate pms = pmsPicker.getValue();
        if (pms != null && pms.isAfter(LocalDate.now())) {
            UiHelper.setStatus(status, "The last PMS date cannot be in the future.", true);
            ok = false;
        }
        if (!ok) {
            if (!status.getText().startsWith("The last PMS")) {
                UiHelper.setStatus(status, "Please fix the red messages first.", true);
            }
            return;
        }
        try {
            int year = (yearBox.getValue() == null || NO_YEAR.equals(yearBox.getValue())) ? 0
                    : Integer.parseInt(yearBox.getValue());
            String mileageText = mileageField.text();
            int mileage = mileageText.isEmpty() ? 0 : Integer.parseInt(mileageText);
            int id = editing == null ? 0 : editing.getId();
            Vehicle vehicle = VehicleFactory.create(id, type, makeField.text(), modelField.text(),
                    year,
                    plateField.text(), colorBox.getValue(), Double.parseDouble(rateField.text()),
                    mileage, pms, categoryBox.getValue());
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
            afterAdd(vehicle);
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
        addButton.setText(editMode ? "Save changes" : "Add Vehicle");
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
            loadMakes();
        }
        makeField.setValue(v.getMake());
        modelField.setValue(v.getModel());
        busyChangingForm = true;
        String yearText = v.getYear() > 0 ? String.valueOf(v.getYear()) : NO_YEAR;
        if (!yearBox.getItems().contains(yearText)) {
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
        categoryBox.setValue(VehicleTypes.isCategoryOf(v.getType(), v.getCategoryPath())
                ? v.getCategoryPath() : categoryBox.getItems().get(0));
        UiHelper.setError(colorError, null);
        UiHelper.setError(categoryError, null);
        chosenPhoto = null;
        photoName.setText(controller.hasPhoto(v.getId())
                ? "Keeping the saved photo. Choose a new one to replace it."
                : "No photo yet. Choose one, or FleetRent will use the drawn car.");
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

    private Alert styledAlert(Alert.AlertType type, String text, ButtonType... buttons) {
        Alert alert = new Alert(type, text, buttons);
        java.net.URL css = VehicleTab.class.getResource("/fleetrent.css");
        if (css != null) {
            alert.getDialogPane().getStylesheets().add(css.toExternalForm());
        }
        return alert;
    }

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
            Alert alert = styledAlert(Alert.AlertType.CONFIRMATION, text, remove, keep);
            alert.setTitle("Remove vehicle");
            alert.setHeaderText("Remove " + v.displayName() + " (" + v.getPlateNumber() + ")?");
            alert.initOwner(vehicleList.getScene() == null ? null : vehicleList.getScene()
                    .getWindow());
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

    // A small window with the removed vehicles and a Restore button.
    private void showRemovedVehicles() {
        ListView<Vehicle> list = new ListView<>();
        list.setPrefSize(460, 260);
        list.setCellFactory(l -> new ListCell<Vehicle>() {
            @Override
            protected void updateItem(Vehicle item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayName() + "  |  "
                        + item.getPlateNumber()
                        + "  |  " + item.getType());
            }
        });
        list.getStyleClass().add("plain-list");
        Label info = new Label();
        info.getStyleClass().add("status-label");
        info.setWrapText(true);
        info.setMaxWidth(INFO_MAX_WIDTH);
        Button restore = new Button("Restore selected");
        restore.getStyleClass().add("primary-button");
        Runnable load = () -> {
            try {
                list.getItems().setAll(controller.removedVehicles());
                info.setText(list.getItems().isEmpty() ? "No removed vehicles."
                        : "Pick a vehicle, then press Restore.");
            } catch (ControllerException e) {
                UiHelper.setStatus(info, e.getMessage(), true);
            }
        };
        restore.setOnAction(e -> {
            Vehicle picked = list.getSelectionModel().getSelectedItem();
            if (picked == null) {
                UiHelper.setStatus(info, "Pick a vehicle first.", true);
                return;
            }
            try {
                controller.restore(picked.getId());
                load.run();
                refresh();
                UiHelper.setStatus(info, picked.displayName() + " is back in the fleet.", false);
                UiHelper.setStatus(status, picked.displayName() + " was restored to the fleet.",
                        false);
            } catch (IllegalArgumentException | IllegalStateException ex) {
                UiHelper.setStatus(info, ex.getMessage(), true);
            } catch (ControllerException ex) {
                UiHelper.setStatus(info, ex.getMessage(), true);
            }
        });
        load.run();
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Removed vehicles");
        dialog.setHeaderText("Vehicles you removed from the fleet");
        dialog.getDialogPane().setContent(new VBox(Layout.GAP_MEDIUM, list, info, restore));
        dialog.getDialogPane().getButtonTypes().add(new ButtonType("Close",
                ButtonBar.ButtonData.CANCEL_CLOSE));
        java.net.URL css = VehicleTab.class.getResource("/fleetrent.css");
        if (css != null) {
            dialog.getDialogPane().getStylesheets().add(css.toExternalForm());
        }
        dialog.initOwner(vehicleList.getScene() == null ? null : vehicleList.getScene()
                .getWindow());
        dialog.showAndWait();
    }

    // A small window with the maintenance of the selected vehicle and a form to add a record.
    private void showMaintenance() {
        Vehicle picked = vehicleList.getSelectionModel().getSelectedItem();
        if (picked == null) {
            UiHelper.setStatus(status, "Pick a vehicle in the list first.", true);
            return;
        }
        ListView<String> list = new ListView<>();
        list.setPrefSize(MAINTENANCE_WIDTH, MAINTENANCE_LIST_HEIGHT);
        list.getStyleClass().add("plain-list");
        Label info = new Label();
        info.getStyleClass().add("status-label");
        info.setWrapText(true);
        info.setMaxWidth(MAINTENANCE_WIDTH);
        javafx.scene.control.ComboBox<MaintenanceType> typeBox =
                new javafx.scene.control.ComboBox<>();
        typeBox.getItems().setAll(MaintenanceType.values());
        typeBox.getSelectionModel().select(MaintenanceType.PMS);
        javafx.scene.control.DatePicker datePicker =
                new javafx.scene.control.DatePicker(LocalDate.now());
        javafx.scene.control.TextField costField = new javafx.scene.control.TextField();
        costField.setPromptText("Cost, for example 2500");
        javafx.scene.control.TextField noteField = new javafx.scene.control.TextField();
        noteField.setPromptText("Note (optional)");
        Button save = new Button("Save record");
        save.getStyleClass().add("primary-button");
        Runnable load = () -> {
            try {
                List<String> lines = new ArrayList<>();
                for (Maintenance m : controller.maintenanceOf(picked.getId())) {
                    lines.add(m.getDoneOn() + "  |  " + m.getType().getLabel()
                            + String.format(Locale.US, "  |  %.2f", m.getCost())
                            + (m.getNote().isEmpty() ? "" : "  |  " + m.getNote()));
                }
                list.getItems().setAll(lines);
                info.setText(lines.isEmpty() ? "No maintenance recorded yet." : lines.size()
                        + " record(s).");
            } catch (ControllerException e) {
                UiHelper.setStatus(info, e.getMessage(), true);
            }
        };
        save.setOnAction(e -> {
            try {
                double cost = Double.parseDouble(costField.getText().trim());
                controller.logMaintenance(picked.getId(), datePicker.getValue(),
                        typeBox.getValue(), cost,
                        noteField.getText());
                costField.clear();
                noteField.clear();
                load.run();
                refresh();
                UiHelper.setStatus(info, "Saved.", false);
            } catch (NumberFormatException ex) {
                UiHelper.setStatus(info, "Enter the cost as a number.", true);
            } catch (ControllerException ex) {
                UiHelper.setStatus(info, ex.getMessage(), true);
            }
        });
        load.run();
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Maintenance");
        dialog.setHeaderText("Maintenance of " + picked.displayName() + " ("
                + picked.getPlateNumber() + ")");
        dialog.getDialogPane().setContent(new VBox(Layout.GAP_MEDIUM, list, info, typeBox,
                datePicker, costField,
                noteField, save));
        dialog.getDialogPane().getButtonTypes().add(new ButtonType("Close",
                ButtonBar.ButtonData.CANCEL_CLOSE));
        java.net.URL css = VehicleTab.class.getResource("/fleetrent.css");
        if (css != null) {
            dialog.getDialogPane().getStylesheets().add(css.toExternalForm());
        }
        dialog.initOwner(vehicleList.getScene() == null ? null : vehicleList.getScene()
                .getWindow());
        dialog.showAndWait();
    }

    private void resetForm() {
        clearMakeAndModel();
        plateField.reset();
        rateField.reset();
        mileageField.reset();
        mileageField.field().setText("0");
        mileageField.showError(null);
        busyChangingForm = true;
        yearBox.setValue(NO_YEAR);
        busyChangingForm = false;
        colorBox.setValue(null);
        pmsPicker.setValue(null);
        categoryBox.setValue(categoryBox.getItems().get(0));
        chosenPhoto = null;
        photoName.setText(PHOTO_HINT);
        UiHelper.setError(colorError, null);
        UiHelper.setError(categoryError, null);
        redraw();
        UiHelper.setStatus(lookupStatus, "Click a make to see its models.", false);
    }

    /** Reloads the data of this tab from the database. */
    public void refresh() {
        try {
            vehicleList.getItems().setAll(controller.vehicles());
            int count = vehicleList.getItems().size();
            countLabel.setText(count + (count == 1 ? " vehicle" : " vehicles"));
        } catch (ControllerException e) {
            UiHelper.setStatus(status, e.getMessage(), true);
        }
    }

    // After a vehicle is saved: copy the chosen photo, or look for one online in the background.
    private void afterAdd(Vehicle vehicle) {
        if (chosenPhoto != null) {
            try {
                controller.savePhoto(vehicle.getId(), chosenPhoto);
            } catch (ControllerException e) {
                UiHelper.setStatus(status, "Saved, but the photo could not be used: "
                        + e.getMessage(), true);
            }
            return;
        }
        UiHelper.setStatus(status, "Saved: " + vehicle + ". Looking for a photo online...", false);
        Task<Boolean> task = new Task<Boolean>() {
            @Override
            protected Boolean call() throws Exception {
                return controller.findPhoto(vehicle);
            }
        };
        task.setOnSucceeded(e -> {
            UiHelper.setStatus(status, task.getValue()
                    ? "Saved: " + vehicle + ". Photo added."
                    : "Saved: " + vehicle + ". No online photo found, showing the drawn car. "
                            + "Use Choose photo to add yours.", false);
            refresh();
        });
        task.setOnFailed(e -> UiHelper.setStatus(status, "Saved: " + vehicle + ". "
                + task.getException().getMessage() + " Showing the drawn car.", true));
        UiHelper.runInBackground(task);
    }

    // Looks online for a photo of every vehicle that has none. Runs in the background with a
    // progress count.
    private void findAllPhotos() {
        final List<Vehicle> missing = new ArrayList<>();
        for (Vehicle v : vehicleList.getItems()) {
            if (!controller.hasPhoto(v.getId())) {
                missing.add(v);
            }
        }
        if (missing.isEmpty()) {
            UiHelper.setStatus(status, "Every vehicle already has a photo.", false);
            return;
        }
        findPhotosButton.setDisable(true);
        Task<int[]> task = new Task<int[]>() {
            @Override
            protected int[] call() throws Exception {
                return controller.findPhotos(missing, (done, total) ->
                        updateMessage("Looking for photos... " + done + " of " + total));
            }
        };
        task.messageProperty().addListener((obs, o, n) -> UiHelper.setStatus(status, n, false));
        task.setOnSucceeded(e -> {
            int[] r = task.getValue();
            UiHelper.setStatus(status, "Photo search done. Found " + r[0] + " photo(s)"
                    + (r[1] > 0 ? ", " + r[1] + " could not be searched." : "."), r[1] > 0);
            findPhotosButton.setDisable(false);
            refresh();
        });
        task.setOnFailed(e -> {
            Throwable cause = task.getException();
            UiHelper.setStatus(status, "Photo search stopped. "
                    + (cause == null ? "Unknown problem." : cause.getMessage()), true);
            findPhotosButton.setDisable(false);
        });
        UiHelper.runInBackground(task);
    }

    // How one vehicle looks in the list: its picture, its name, plate, color and mileage, a type
    // badge, and its price.
    private class VehicleCell extends ListCell<Vehicle> {
        @Override
        protected void updateItem(Vehicle v, boolean empty) {
            super.updateItem(v, empty);
            if (empty || v == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            Label badge = new Label(v.getType());
            badge.getStyleClass().addAll("badge", "badge-" + v.getType().toLowerCase(Locale.ROOT));
            Label name = new Label(v.getMake() + " " + v.getModel() + (v.getYear() > 0 ? " "
                    + v.getYear() : ""));
            name.getStyleClass().add("vehicle-name");
            Circle chip = new Circle(6, ColorNames.toColor(v.getColor()));
            chip.setStroke(Color.web("#f5b800"));
            Label colorName = new Label(v.getColor() == null ? "" : v.getColor());
            colorName.getStyleClass().add("color-name");
            HBox colorRow = new HBox(Layout.GAP_SMALL, chip, colorName, badge);
            colorRow.setAlignment(Pos.CENTER_LEFT);
            Label details = new Label(v.getPlateNumber() + "  |  " + v.getMileage() + " mi");
            details.getStyleClass().add("vehicle-details");
            Label category = new Label(v.getCategoryPath() == null ? "" : v.getCategoryPath()
                    + (v.getLastPMS() == null ? "" : "  |  PMS " + v.getLastPMS()));
            category.getStyleClass().add("hint-label");
            // v is a Vehicle reference. Java runs the Sedan, Suv or Motorcycle version at runtime.
            Label price = new Label(String.format(Locale.US, "%.2f / day", v.computeRentalCost(1,
                    0)));
            price.getStyleClass().add("vehicle-price");
            Label fee = new Label("Extra miles: " + v.feeSummary());
            fee.getStyleClass().add("hint-label");
            fee.setWrapText(true);
            fee.maxWidthProperty().bind(javafx.beans.binding.Bindings.max(120, widthProperty()
                    .subtract(235)));
            VBox text = new VBox(Layout.GAP_TINY, name, colorRow, details, category, price, fee);
            text.setMinWidth(0);
            name.setWrapText(true);
            name.setMinHeight(Region.USE_PREF_SIZE);
            name.maxWidthProperty().bind(javafx.beans.binding.Bindings.max(120, widthProperty()
                    .subtract(235)));
            Button edit = new Button("Edit");
            edit.getStyleClass().addAll("secondary-button", "mini-button");
            edit.setOnAction(e -> beginEdit(v));
            Button remove = new Button("Remove");
            remove.getStyleClass().addAll("danger-button", "mini-button");
            remove.setOnAction(e -> removeVehicle(v));
            edit.setMinWidth(Region.USE_PREF_SIZE);
            remove.setMinWidth(Region.USE_PREF_SIZE);
            text.getChildren().add(new HBox(Layout.GAP_NORMAL, edit, remove));
            HBox card = new HBox(Layout.GAP_WIDER, pictures.stage(v), text);
            card.setAlignment(Pos.CENTER_LEFT);
            card.getStyleClass().add("vehicle-card");
            setText(null);
            setGraphic(card);
        }
    }
}
