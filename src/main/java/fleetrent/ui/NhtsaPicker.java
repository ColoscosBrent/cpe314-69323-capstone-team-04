package fleetrent.ui;

import fleetrent.controller.VehicleController;
import fleetrent.controller.VehicleController.Models;
import fleetrent.dto.VehicleModelInfo;
import fleetrent.model.VehicleTypes;
import fleetrent.service.Validators;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

// The "Choose from NHTSA" card of the Vehicles tab: the list of makes and the list of models that
// NHTSA gives for the chosen type and year. A click on a make or a model fills the Make and Model
// boxes of the form. When NHTSA cannot be reached, the boxes can be typed by hand.
final class NhtsaPicker {
    private static final double BAR_PREF_WIDTH = 280;
    private static final double TEXT_MAX_WIDTH = 300;
    private static final double MAKE_LIST_HEIGHT = 190;
    private static final double MODEL_LIST_HEIGHT = 230;
    private static final double CARD_WIDTH = 310;

    private final VehicleController controller;
    private final ValidatedField makeField;
    private final ValidatedField modelField;
    private final Supplier<String> type;
    private final Supplier<String> year;
    private final Consumer<String> onModelPicked;

    private final ValidatedField makeSearch = new ValidatedField(Validators.Kind.VEHICLE_TEXT,
            "Search makes...", false);
    private final ValidatedField modelSearch = new ValidatedField(Validators.Kind.VEHICLE_TEXT,
            "Search models...", false);
    private final ListView<String> makeList = new ListView<>();
    private final ListView<VehicleModelInfo> modelList = new ListView<>();
    private final ProgressBar lookupBar = new ProgressBar(0);
    private final Label lookupStatus = new Label("Choosing a type loads its makes from NHTSA.");
    private final Button reloadButton = new Button("Reload from NHTSA");
    private final VBox card;
    private List<String> allMakes = new ArrayList<>();
    private List<VehicleModelInfo> allModels = new ArrayList<>();
    // The make and model the person clicked. The Add button accepts them even when the lists are
    // reloading (for example after the Year was changed).
    private String pickedMake;
    private String pickedModel;
    private boolean apiReady;

    // type and year give the current choice of the form. onModelPicked is told the model name
    // when a model is clicked in the list.
    NhtsaPicker(VehicleController controller, ValidatedField makeField, ValidatedField modelField,
            Supplier<String> type, Supplier<String> year, Consumer<String> onModelPicked) {
        this.controller = controller;
        this.makeField = makeField;
        this.modelField = modelField;
        this.type = type;
        this.year = year;
        this.onModelPicked = onModelPicked;
        configureLists();
        wireLists();
        card = buildCard();
        setManualEntry(false);
    }

    // Sets the look of the button, the progress bar, the status line and the two lists.
    private void configureLists() {
        reloadButton.getStyleClass().add("secondary-button");
        reloadButton.setOnAction(e -> loadMakes());
        lookupBar.setVisible(false);
        lookupBar.setPrefWidth(BAR_PREF_WIDTH);
        lookupStatus.getStyleClass().add("status-label");
        lookupStatus.setWrapText(true);
        lookupStatus.setMaxWidth(TEXT_MAX_WIDTH);
        makeList.getStyleClass().add("plain-list");
        makeList.setPrefHeight(MAKE_LIST_HEIGHT);
        makeList.setMinHeight(MAKE_LIST_HEIGHT);
        modelList.getStyleClass().add("plain-list");
        modelList.setPrefHeight(MODEL_LIST_HEIGHT);
        modelList.setMinHeight(MODEL_LIST_HEIGHT);
        modelList.setCellFactory(list -> new ListCell<VehicleModelInfo>() {
            @Override
            protected void updateItem(VehicleModelInfo item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getModelName());
            }
        });
    }

    // Connects the search boxes and the clicks on a make or a model.
    private void wireLists() {
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
                onModelPicked.accept(picked.getModelName());
            }
        });
    }

    // The card with the hint, the Reload button and the two lists.
    private VBox buildCard() {
        Label hint = new Label("The Type decides the list. Click a make, then click a model. "
                + "Choose the Year first: only models made in that year are listed.");
        hint.getStyleClass().add("hint-label");
        hint.setWrapText(true);
        hint.setMaxWidth(TEXT_MAX_WIDTH);
        Label makeTitle = new Label("1. Make");
        makeTitle.getStyleClass().add("field-label");
        Label modelTitle = new Label("2. Model");
        modelTitle.getStyleClass().add("field-label");
        VBox box = new VBox(Layout.GAP_NORMAL, UiHelper.title("Choose from NHTSA"), hint,
                new HBox(Layout.GAP_NORMAL, reloadButton), lookupBar, lookupStatus, makeTitle,
                makeSearch.node(), makeList, modelTitle, modelSearch.node(), modelList);
        box.getStyleClass().add("card");
        box.setPrefWidth(CARD_WIDTH);
        box.setMinWidth(CARD_WIDTH);
        return box;
    }

    Node node() {
        return card;
    }

    // True when the NHTSA lists work (the Make and Model must then be picked from them).
    boolean isReady() {
        return apiReady;
    }

    // True when the make was chosen from NHTSA (or clicked in the list), ignoring capitals and
    // extra spaces.
    boolean isKnownMake(String text) {
        return Validators.sameName(pickedMake, text)
                || allMakes.stream().anyMatch(m -> Validators.sameName(m, text));
    }

    boolean isKnownModel(String text) {
        return Validators.sameName(pickedModel, text)
                || allModels.stream().anyMatch(m -> Validators.sameName(m.getModelName(), text));
    }

    // The boxes of the make and model can only be used after the year is chosen.
    void setEnabled(boolean enabled) {
        for (Node n : new Node[] {makeField.node(), modelField.node(), makeSearch.node(),
                modelSearch.node(), makeList, modelList}) {
            n.setDisable(!enabled);
        }
    }

    // Make and model boxes: read-only while NHTSA works, typed by hand only when NHTSA cannot be
    // reached.
    private void setManualEntry(boolean manual) {
        makeField.field().setEditable(manual);
        modelField.field().setEditable(manual);
        String hint = manual ? "Type it (NHTSA is not reachable)" : "Pick from the NHTSA list";
        makeField.field().setPromptText(hint);
        modelField.field().setPromptText(hint);
    }

    void showStatus(String text) {
        UiHelper.setStatus(lookupStatus, text, false);
    }

    // Forgets the chosen make and model and empties the lists of models.
    void clear() {
        pickedMake = null;
        pickedModel = null;
        makeField.reset();
        modelField.reset();
        makeSearch.reset();
        modelSearch.reset();
        makeList.getSelectionModel().clearSelection();
        allModels = new ArrayList<>();
        modelList.getItems().clear();
    }

    // The year changed: the models of the make may be different, so the model is chosen again.
    void yearChanged() {
        pickedModel = null;
        modelField.reset();
        allModels = new ArrayList<>();
        modelList.getItems().clear();
        if (!makeField.field().getText().isEmpty() && apiReady) {
            loadModels();
        }
    }

    void loadMakes() {
        final String vehicleType = type.get();
        final String apiType = VehicleTypes.apiType(vehicleType);
        reloadButton.setDisable(true);
        lookupBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        lookupBar.setVisible(true);
        UiHelper.setStatus(lookupStatus, "Asking NHTSA for " + vehicleType + " makes...", false);
        allMakes = new ArrayList<>();
        makeList.getItems().clear();
        modelList.getItems().clear();

        // The controller runs the question in the background. Only the newest answer comes back.
        controller.loadMakes(apiType, makes -> {
            allMakes = makes;
            apiReady = !allMakes.isEmpty();
            filterMakes();
            if (apiReady) {
                setManualEntry(false);
                UiHelper.setStatus(lookupStatus, "NHTSA lists " + allMakes.size() + " "
                        + vehicleType + " makes. Click one.", false);
            } else {
                setManualEntry(true);
                UiHelper.setStatus(lookupStatus, "NHTSA returned no makes for " + vehicleType
                        + ". You can type the make and model by hand.", true);
            }
            finishLookup();
        }, message -> {
            apiReady = false;
            setManualEntry(true);
            UiHelper.setStatus(lookupStatus, message
                    + " Until it works you can type the make and model by hand.", true);
            finishLookup();
        });
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

    // Asks NHTSA for the models of the chosen make, for the chosen type and year. If NHTSA has no
    // model of that make for that year, the list stays empty.
    void loadModels() {
        final String make = makeField.field().getText().trim();
        if (make.isEmpty() || year.get() == null) {
            return;
        }
        final String vehicleType = type.get();
        final String apiType = VehicleTypes.apiType(vehicleType);
        final int modelYear = Integer.parseInt(year.get());
        lookupBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        lookupBar.setVisible(true);
        UiHelper.setStatus(lookupStatus, "Asking NHTSA for " + make + " models...", false);
        modelList.getItems().clear();
        allModels = new ArrayList<>();

        controller.loadModels(make, modelYear, apiType,
                models -> showModels(models, make, vehicleType, modelYear), this::modelsFailed);
    }

    // Shows the models NHTSA sent back, or says that it has none.
    private void showModels(Models result, String make, String vehicleType, int modelYear) {
        allModels = result.models();
        modelSearch.reset();
        filterModels();
        if (result.models().isEmpty()) {
            modelField.field().clear();
            UiHelper.setStatus(lookupStatus, "NHTSA has no " + vehicleType + " model of "
                    + make + " for " + modelYear + ". Pick another make or another year.",
                    true);
        } else {
            UiHelper.setStatus(lookupStatus, result.models().size() + " models of " + make
                    + " for " + modelYear + ". Click one.", false);
        }
        lookupBar.setVisible(false);
    }

    // The model lookup failed: let the user type the model by hand.
    private void modelsFailed(String message) {
        apiReady = false;
        setManualEntry(true);
        UiHelper.setStatus(lookupStatus, message + " You can type the model by hand.", true);
        lookupBar.setVisible(false);
    }

    private void finishLookup() {
        lookupBar.setVisible(false);
        reloadButton.setDisable(false);
    }
}
