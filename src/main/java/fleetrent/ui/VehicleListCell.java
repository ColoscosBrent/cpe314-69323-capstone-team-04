package fleetrent.ui;

import fleetrent.model.Vehicle;

import java.util.function.Consumer;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

// How one vehicle looks in the fleet list: its picture, its name, plate, color and mileage, a type
// badge, its price, and the Edit and Remove buttons.
final class VehicleListCell extends ListCell<Vehicle> {
    private static final double TEXT_RESERVED_WIDTH = 235;
    private static final double TEXT_MIN_WIDTH = 120;

    private final VehiclePictures pictures;
    private final Consumer<Vehicle> onEdit;
    private final Consumer<Vehicle> onRemove;

    VehicleListCell(VehiclePictures pictures, Consumer<Vehicle> onEdit,
            Consumer<Vehicle> onRemove) {
        this.pictures = pictures;
        this.onEdit = onEdit;
        this.onRemove = onRemove;
    }

    @Override
    protected void updateItem(Vehicle v, boolean empty) {
        super.updateItem(v, empty);
        if (empty || v == null) {
            setText(null);
            setGraphic(null);
            return;
        }
        Label name = new Label(v.displayName());
        name.getStyleClass().add("vehicle-name");
        HBox colorRow = colorRow(v);
        Label details = new Label(v.getPlateNumber() + "  |  " + Format.miles(v.getMileage()));
        details.getStyleClass().add("vehicle-details");
        Label category = new Label(v.getCategoryPath() == null ? "" : v.getCategoryPath()
                + (v.getLastPMS() == null ? "" : "  |  PMS " + Format.date(v.getLastPMS())));
        category.getStyleClass().add("hint-label");
        // v is a Vehicle reference. Java runs the Sedan, Suv or Motorcycle version at runtime.
        Label price = new Label(Format.money(v.computeRentalCost(1, 0)) + " / day");
        price.getStyleClass().add("vehicle-price");
        Label fee = new Label("Extra miles: " + v.feeSummary());
        fee.getStyleClass().add("hint-label");
        fee.setWrapText(true);
        fee.maxWidthProperty().bind(Bindings.max(TEXT_MIN_WIDTH,
                widthProperty().subtract(TEXT_RESERVED_WIDTH)));
        VBox text = new VBox(Layout.GAP_TINY, name, colorRow, details, category, price, fee);
        text.setMinWidth(0);
        name.setWrapText(true);
        name.setMinHeight(Region.USE_PREF_SIZE);
        name.maxWidthProperty().bind(Bindings.max(TEXT_MIN_WIDTH,
                widthProperty().subtract(TEXT_RESERVED_WIDTH)));
        text.getChildren().add(buttonRow(v));
        HBox card = new HBox(Layout.GAP_WIDER, pictures.stage(v), text);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("vehicle-card");
        setText(null);
        setGraphic(card);
    }

    // The color chip, the color name and the type badge.
    private static HBox colorRow(Vehicle v) {
        Label badge = new Label(v.getType());
        badge.getStyleClass().addAll("badge", "badge-" + v.getType().toLowerCase(
                java.util.Locale.ROOT));
        Circle chip = new Circle(6, ColorNames.toColor(v.getColor()));
        chip.setStroke(Color.web("#f5b800"));
        Label colorName = new Label(v.getColor() == null ? "" : v.getColor());
        colorName.getStyleClass().add("color-name");
        HBox colorRow = new HBox(Layout.GAP_SMALL, chip, colorName, badge);
        colorRow.setAlignment(Pos.CENTER_LEFT);
        return colorRow;
    }

    // The Edit and Remove buttons of one vehicle.
    private HBox buttonRow(Vehicle v) {
        Button edit = new Button("Edit");
        edit.getStyleClass().addAll("secondary-button", "mini-button");
        edit.setOnAction(e -> onEdit.accept(v));
        Button remove = new Button("Remove");
        remove.getStyleClass().addAll("danger-button", "mini-button");
        remove.setOnAction(e -> onRemove.accept(v));
        edit.setMinWidth(Region.USE_PREF_SIZE);
        remove.setMinWidth(Region.USE_PREF_SIZE);
        return new HBox(Layout.GAP_NORMAL, edit, remove);
    }
}
