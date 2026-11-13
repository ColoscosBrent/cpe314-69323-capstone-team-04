package fleetrent.ui;

import fleetrent.model.Vehicle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntFunction;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

// One place that makes the picture of a vehicle, so the Vehicles tab and the Rentals tab show the
// same thing: the saved photo if there is one, otherwise the drawn car in the vehicle's color.
final class VehiclePictures {
    private final IntFunction<Path> photos;
    private final Map<String, Image> cache = new HashMap<>();

    VehiclePictures(IntFunction<Path> photos) {
        this.photos = photos;
    }

    // The framed picture used in the lists (about 172 x 106).
    Node stage(Vehicle v) {
        Path photo = photos.apply(v.getId());
        Image image = photo == null ? null : load(photo, 320);
        StackPane stage = new StackPane(image != null ? photoNode(image, 156, 98)
                : CarGraphic.create(v.getType(), v.getColor(), 130));
        stage.getStyleClass().add("car-stage");
        stage.setMinWidth(172);
        stage.setPrefWidth(172);
        stage.setMinHeight(106);
        return stage;
    }

    // A framed picture of any size, for the renter's cards: the saved photo or the drawn car.
    Node stage(Vehicle v, double width, double height) {
        Path photo = photos.apply(v.getId());
        Image image = photo == null ? null : load(photo, width * 2);
        Node inner = image != null ? photoNode(image, width - 16, height - 8)
                : CarGraphic.create(v.getType(), v.getColor(), Math.min(width - 24, (height - 12) * 200.0 / 90.0));
        StackPane stage = new StackPane(inner);
        stage.getStyleClass().add("car-stage");
        stage.setMinSize(width, height);
        stage.setPrefSize(width, height);
        stage.setMaxSize(width, height);
        return stage;
    }

    // Reads a picture file once and keeps it in memory. Returns null if the file cannot be read.
    Image load(Path file, double width) {
        try {
            String key = file.toAbsolutePath() + "|" + Files.getLastModifiedTime(file) + "|" + width;
            Image cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
            Image image = new Image(file.toUri().toString(), width, 0, true, true);
            if (image.isError() || image.getWidth() <= 0) {
                return null;
            }
            cache.put(key, image);
            return image;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    // A picture cut to a rounded box of the given size.
    static StackPane photoNode(Image image, double w, double h) {
        ImageView view = new ImageView(image);
        view.setFitWidth(w);
        view.setFitHeight(h);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        StackPane box = new StackPane(view);
        box.setMinSize(w, h);
        box.setPrefSize(w, h);
        box.setMaxSize(w, h);
        Rectangle clip = new Rectangle(w, h);
        clip.setArcWidth(18);
        clip.setArcHeight(18);
        box.setClip(clip);
        return box;
    }
}
