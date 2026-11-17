package fleetrent.ui;

import javafx.scene.control.ListCell;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

// One row of the color list: the color dot beside the color name.
final class ColorCell extends ListCell<String> {
    private static final double SWATCH_RADIUS = 8;

    @Override
    protected void updateItem(String name, boolean empty) {
        super.updateItem(name, empty);
        if (empty || name == null) {
            setText("Pick a color");
            setGraphic(null);
            return;
        }
        Circle dot = new Circle(SWATCH_RADIUS, ColorNames.toColor(name));
        dot.setStroke(Color.web("#8a8f9c"));
        setText(name);
        setGraphic(dot);
    }
}
