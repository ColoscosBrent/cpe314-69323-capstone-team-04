package fleetrent;

import java.io.File;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
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

// Tab 1: add vehicles (every Vehicle field of the ERD), choose the make and model from the NHTSA
// API, and see the fleet. The vehicle type decides which makes and models NHTSA is asked for, so
// the type and the model can never disagree.
public class VehicleTab extends Tab {
    private static final String NO_YEAR = "Not specified";
    private static final String PHOTO_HINT = "No photo chosen. FleetRent will try to find one online.";

    private final Database db;
    private final VpicClient vpic = new VpicClient();
    private final VehiclePictures pictures;
    private final CarImageStore store;
    private final PhotoFinder photoFinder;
    private Path chosenPhoto;

    private final ListView<Vehicle> vehicleList = new ListView<>();
    private final Label status = new Label("Ready.");
    private final Label countLabel = new Label("0 vehicles");
    private final Button findPhotosButton = new Button("Find photos for all");

    // form
    private final ChoiceBox<String> typeBox = new ChoiceBox<>(FXCollections.observableArrayList(VehicleTypes.ALL));
    private final ValidatedField makeField = new ValidatedField(Validators.Kind.VEHICLE_TEXT, "Pick from the NHTSA list", true);
    private final ValidatedField modelField = new ValidatedField(Validators.Kind.VEHICLE_TEXT, "Pick from the NHTSA list", true);
    private final ChoiceBox<String> yearBox = new ChoiceBox<>();
    private final ChoiceBox<String> colorBox = new ChoiceBox<>(FXCollections.observableArrayList(ColorNames.choices()));
    private final ValidatedField plateField = new ValidatedField(Validators.Kind.PLATE, "ABC 123", true);
    private final ValidatedField rateField = new ValidatedField(Validators.Kind.MONEY, "1500", true);
    private final ValidatedField mileageField = new ValidatedField(Validators.Kind.DIGITS, "0", false);
    private final DatePicker pmsPicker = new DatePicker();
    private final ChoiceBox<String> categoryBox = new ChoiceBox<>();
    private final Label colorError = UiHelper.errorLabel();
    private final Label categoryError = UiHelper.errorLabel();
    private final Label yearNote = new Label();

    // NHTSA picker
    private final ValidatedField makeSearch = new ValidatedField(Validators.Kind.VEHICLE_TEXT, "Search makes...", false);
    private final ValidatedField modelSearch = new ValidatedField(Validators.Kind.VEHICLE_TEXT, "Search models...", false);
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

    public VehicleTab(Database db, VehiclePictures pictures) {
        super("Vehicles");
        this.db = db;
        this.pictures = pictures;
        this.store = pictures.store();
        this.photoFinder = new PhotoFinder(new CarImageClient(), store);
        setClosable(false);

        typeBox.setValue(VehicleTypes.ALL.get(0));
        typeBox.setPrefWidth(280);
        typeBox.setMinWidth(280);
        yearBox.getItems().add(NO_YEAR);
        for (int y = LocalDate.now().getYear() + 1; y >= 1996; y--) {
            yearBox.getItems().add(String.valueOf(y));
        }
        yearBox.setValue(NO_YEAR);
        yearBox.setPrefWidth(280);
        yearBox.setMinWidth(280);
        colorBox.setPrefWidth(280);
        colorBox.setMinWidth(280);
        categoryBox.setPrefWidth(280);
        categoryBox.setMinWidth(280);
        categoryBox.getItems().setAll(VehicleTypes.categoryOptions(typeBox.getValue()));
        categoryBox.setValue(categoryBox.getItems().get(0));
        pmsPicker.setEditable(false);
        pmsPicker.setPromptText("Optional");
        pmsPicker.setPrefWidth(180);
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
        yearNote.setMaxWidth(300);
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

        Label photoName = new Label(PHOTO_HINT);
        photoName.getStyleClass().add("hint-label");
        photoName.setWrapText(true);
        photoName.setMaxWidth(400);
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
                if (!CarImageStore.isPictureName(file.getName())) {
                    UiHelper.setStatus(status, "Please choose a JPG, PNG, GIF or BMP picture.", true);
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
        HBox photoRow = new HBox(8, photoButton, clearPhotoButton);
        photoRow.setAlignment(Pos.CENTER_LEFT);

        // Make and model are chosen from the NHTSA lists, so they cannot be typed by hand while NHTSA works.
        setManualEntry(false);

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(7);
        int r = 0;
        UiHelper.addRow(form, r++, "Type", typeBox);
        UiHelper.addRow(form, r++, "Make", makeField.node());
        UiHelper.addRow(form, r++, "Model", modelField.node());
        UiHelper.addRow(form, r++, "Year", new VBox(3, yearBox, yearNote));
        UiHelper.addRow(form, r++, "Color", UiHelper.withError(colorBox, colorError));
        UiHelper.addRow(form, r++, "Plate number", plateField.node());
        UiHelper.addRow(form, r++, "Daily rate", rateField.node());
        UiHelper.addRow(form, r++, "Mileage", mileageField.node());
        UiHelper.addRow(form, r++, "Last PMS", new HBox(8, pmsPicker, clearPms));
        UiHelper.addRow(form, r++, "Category", UiHelper.withError(categoryBox, categoryError));

        Button addButton = new Button("Add Vehicle");
        addButton.getStyleClass().add("primary-button");
        status.getStyleClass().add("status-label");
        status.setWrapText(true);
        status.setMaxWidth(400);

        VBox formCard = new VBox(8, UiHelper.title("Add a vehicle"), preview, previewName, swatches, form,
                photoRow, photoName, addButton, status);
        formCard.getStyleClass().add("card");
        formCard.setPrefWidth(420);
        formCard.setMinWidth(420);

        addButton.setOnAction(event -> addVehicle(photoName));

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
        lookupBar.setPrefWidth(280);
        lookupStatus.getStyleClass().add("status-label");
        lookupStatus.setWrapText(true);
        lookupStatus.setMaxWidth(300);
        Label lookupHint = new Label("The Type decides the list. Click a make, then click a model. "
                + "Choosing a Year narrows the models.");
        lookupHint.getStyleClass().add("hint-label");
        lookupHint.setWrapText(true);
        lookupHint.setMaxWidth(300);
        makeList.getStyleClass().add("plain-list");
        makeList.setPrefHeight(190);
        makeList.setMinHeight(190);
        modelList.getStyleClass().add("plain-list");
        modelList.setPrefHeight(230);
        modelList.setMinHeight(230);
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
                modelField.field().setText(picked.getModelName());
                modelField.showError(null);
            }
        });
        Label makeTitle = new Label("1. Make");
        makeTitle.getStyleClass().add("field-label");
        Label modelTitle = new Label("2. Model");
        modelTitle.getStyleClass().add("field-label");
        VBox lookupCard = new VBox(8, UiHelper.title("Choose from NHTSA"), lookupHint,
                new HBox(8, reloadButton), lookupBar, lookupStatus,
                makeTitle, makeSearch.node(), makeList, modelTitle, modelSearch.node(), modelList);
        lookupCard.getStyleClass().add("card");
        lookupCard.setPrefWidth(310);
        lookupCard.setMinWidth(310);

        // ----- fleet card -----
        countLabel.getStyleClass().add("count-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        findPhotosButton.getStyleClass().add("secondary-button");
        findPhotosButton.setOnAction(e -> findAllPhotos());
        HBox listHeader = new HBox(10, UiHelper.title("Our fleet"), spacer, findPhotosButton, countLabel);
        listHeader.setAlignment(Pos.CENTER_LEFT);
        Label hint = new Label("Price shown is for 1 day with no extra miles. Photos come from your file or Wikipedia.");
        hint.getStyleClass().add("hint-label");
        hint.setWrapText(true);
        vehicleList.setCellFactory(list -> new VehicleCell());
        vehicleList.setFocusTraversable(false);
        vehicleList.getStyleClass().add("vehicle-list");
        VBox.setVgrow(vehicleList, Priority.ALWAYS);
        VBox listCard = new VBox(10, listHeader, hint, vehicleList);
        listCard.getStyleClass().add("card");
        listCard.setMinWidth(380);
        listCard.setPrefWidth(420);
        HBox.setHgrow(listCard, Priority.ALWAYS);

        HBox content = new HBox(18, formCard, lookupCard, listCard);
        content.setPadding(new Insets(18));
        setContent(UiHelper.scroll(content));
        refresh();
        loadMakes();
    }

    // ---------- live picture ----------

    private void redraw() {
        Image chosen = chosenPhoto == null ? null : pictures.load(chosenPhoto, 300);
        if (chosen != null) {
            preview.getChildren().setAll(VehiclePictures.photoNode(chosen, 240, 125));
            previewName.setText("Your photo");
            return;
        }
        String color = colorBox.getValue();
        preview.getChildren().setAll(CarGraphic.create(typeBox.getValue(), color, 170));
        previewName.setText(color == null ? "Pick a color below" : color);
    }

    // ---------- type, make, model, year ----------

    // Make and model boxes: read-only while NHTSA works, typed by hand only when NHTSA cannot be reached.
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
                return vpic.fetchMakes(apiType);
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
            UiHelper.setStatus(lookupStatus, ErrorMessages.forApi("NHTSA", task.getException())
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
        makeField.field().setText(make);
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
        final Integer year = (yearText == null || NO_YEAR.equals(yearText)) ? null : Integer.valueOf(yearText);
        final int request = ++modelsRequest;
        lookupBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        lookupBar.setVisible(true);
        UiHelper.setStatus(lookupStatus, "Asking NHTSA for " + make + " models...", false);
        modelList.getItems().clear();
        allModels = new ArrayList<>();

        Task<ModelResult> task = new Task<ModelResult>() {
            @Override
            protected ModelResult call() throws Exception {
                List<VehicleModelInfo> list = vpic.fetchModels(make, year, apiType);
                boolean fellBack = false;
                if (list.isEmpty() && year != null) {
                    list = vpic.fetchModels(make, null, apiType);
                    fellBack = true;
                }
                return new ModelResult(list, fellBack);
            }
        };
        task.setOnSucceeded(e -> {
            if (request != modelsRequest) {
                return;
            }
            ModelResult result = task.getValue();
            allModels = result.models;
            modelSearch.reset();
            filterModels();
            showYearNote(null);
            if (result.models.isEmpty()) {
                modelField.field().clear();
                UiHelper.setStatus(lookupStatus, "NHTSA has no " + typeBox.getValue() + " models for " + make
                        + ". Pick another make.", true);
            } else if (result.fellBack) {
                busyChangingForm = true;
                yearBox.setValue(NO_YEAR);
                busyChangingForm = false;
                showYearNote("NHTSA has no " + make + " models for " + year + ", so Year was set to \"" + NO_YEAR + "\".");
                UiHelper.setStatus(lookupStatus, "No models listed for " + year + ". Showing all "
                        + result.models.size() + " " + make + " models. Click one.", false);
            } else {
                UiHelper.setStatus(lookupStatus, result.models.size() + " models for " + make
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
            UiHelper.setStatus(lookupStatus, ErrorMessages.forApi("NHTSA", task.getException())
                    + " You can type the model by hand.", true);
            lookupBar.setVisible(false);
        });
        UiHelper.runInBackground(task);
    }

    private void finishLookup() {
        lookupBar.setVisible(false);
        reloadButton.setDisable(false);
    }

    private static final class ModelResult {
        final List<VehicleModelInfo> models;
        final boolean fellBack;

        ModelResult(List<VehicleModelInfo> models, boolean fellBack) {
            this.models = models;
            this.fellBack = fellBack;
        }
    }

    // ---------- saving ----------

    private void addVehicle(Label photoName) {
        boolean ok = true;
        String type = typeBox.getValue();
        if (!makeField.validate()) {
            ok = false;
        } else if (apiReady && allMakes.stream().noneMatch(m -> m.equalsIgnoreCase(makeField.text()))) {
            makeField.showError("Pick the make from the NHTSA list.");
            ok = false;
        }
        if (!modelField.validate()) {
            ok = false;
        } else if (apiReady && allModels.stream().noneMatch(m -> m.getModelName().equals(modelField.text()))) {
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
        if (categoryBox.getValue() == null || !VehicleTypes.isCategoryOf(type, categoryBox.getValue())) {
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
            Vehicle vehicle = VehicleFactory.create(0, type, makeField.text(), modelField.text(), year,
                    plateField.text(), colorBox.getValue(), Double.parseDouble(rateField.text()),
                    mileage, pms, categoryBox.getValue());
            if (db.findVehicleByPlate(vehicle.getPlateNumber()) != null) {
                plateField.showError("A vehicle with that plate number already exists.");
                UiHelper.setStatus(status, "Please fix the red messages first.", true);
                return;
            }
            db.insertVehicle(vehicle);
            UiHelper.setStatus(status, "Saved: " + vehicle, false);
            afterAdd(vehicle);
            resetForm(photoName);
            refresh();
        } catch (NumberFormatException e) {
            UiHelper.setStatus(status, "A number in the form is not valid.", true);
        } catch (SQLException e) {
            UiHelper.setStatus(status, ErrorMessages.forDatabase("saving the vehicle", e), true);
        }
    }

    private void resetForm(Label photoName) {
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
        redraw();
        UiHelper.setStatus(lookupStatus, "Click a make to see its models.", false);
    }

    public void refresh() {
        try {
            vehicleList.getItems().setAll(db.getAllVehicles());
            int count = vehicleList.getItems().size();
            countLabel.setText(count + (count == 1 ? " vehicle" : " vehicles"));
        } catch (SQLException e) {
            UiHelper.setStatus(status, ErrorMessages.forDatabase("loading the vehicles", e), true);
        }
    }

    // After a vehicle is saved: copy the chosen photo, or look for one online in the background.
    private void afterAdd(Vehicle vehicle) {
        if (chosenPhoto != null) {
            try {
                store.copyFrom(vehicle.getId(), chosenPhoto);
            } catch (java.io.IOException | IllegalArgumentException e) {
                UiHelper.setStatus(status, "Saved, but the photo could not be used: " + e.getMessage(), true);
            }
            return;
        }
        UiHelper.setStatus(status, "Saved: " + vehicle + ". Looking for a photo online...", false);
        Task<Boolean> task = new Task<Boolean>() {
            @Override
            protected Boolean call() throws Exception {
                return photoFinder.findAndSave(vehicle);
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
                + ErrorMessages.forApi("Wikipedia", task.getException()) + " Showing the drawn car.", true));
        UiHelper.runInBackground(task);
    }

    // Looks online for a photo of every vehicle that has none. Runs in the background with a progress count.
    private void findAllPhotos() {
        final List<Vehicle> missing = new ArrayList<>();
        for (Vehicle v : vehicleList.getItems()) {
            if (!store.has(v.getId())) {
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
                int found = 0;
                int failed = 0;
                int done = 0;
                Throwable last = null;
                for (Vehicle v : missing) {
                    done++;
                    updateMessage("Looking for photos... " + done + " of " + missing.size());
                    try {
                        if (photoFinder.findAndSave(v)) {
                            found++;
                        }
                    } catch (java.io.IOException e) {
                        failed++;
                        last = e;
                        System.out.println("[photo] " + v + ": " + e.getMessage());
                    }
                }
                if (failed == missing.size() && last != null) {
                    throw new java.io.IOException("all failed", last);
                }
                return new int[] {found, failed};
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
            Throwable cause = task.getException() == null ? null : task.getException().getCause();
            UiHelper.setStatus(status, "Photo search stopped. " + ErrorMessages.forApi("Wikipedia", cause), true);
            findPhotosButton.setDisable(false);
        });
        UiHelper.runInBackground(task);
    }

    // How one vehicle looks in the list: its picture, its name, plate, color and mileage, a type badge, and its price.
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
            Label name = new Label(v.getMake() + " " + v.getModel() + (v.getYear() > 0 ? " " + v.getYear() : ""));
            name.getStyleClass().add("vehicle-name");
            Circle chip = new Circle(6, ColorNames.toColor(v.getColor()));
            chip.setStroke(Color.web("#f5b800"));
            Label colorName = new Label(v.getColor() == null ? "" : v.getColor());
            colorName.getStyleClass().add("color-name");
            HBox colorRow = new HBox(6, chip, colorName, badge);
            colorRow.setAlignment(Pos.CENTER_LEFT);
            Label details = new Label(v.getPlateNumber() + "  |  " + v.getMileage() + " mi");
            details.getStyleClass().add("vehicle-details");
            Label category = new Label(v.getCategoryPath() == null ? "" : v.getCategoryPath()
                    + (v.getLastPMS() == null ? "" : "  |  PMS " + v.getLastPMS()));
            category.getStyleClass().add("hint-label");
            // v is a Vehicle reference. Java runs the Sedan, Suv or Motorcycle version at runtime.
            Label price = new Label(String.format(Locale.US, "%.2f / day", v.computeRentalCost(1, 0)));
            price.getStyleClass().add("vehicle-price");
            VBox text = new VBox(4, name, colorRow, details, category, price);
            text.setMinWidth(0);
            name.setWrapText(true);
            name.setMinHeight(Region.USE_PREF_SIZE);
            name.maxWidthProperty().bind(javafx.beans.binding.Bindings.max(120, widthProperty().subtract(235)));
            HBox card = new HBox(14, pictures.stage(v), text);
            card.setAlignment(Pos.CENTER_LEFT);
            card.getStyleClass().add("vehicle-card");
            setText(null);
            setGraphic(card);
        }
    }
}
