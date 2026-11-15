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
    private static final double STAGE_MIN_WIDTH = 172;
    private static final double LIST_LOAD_WIDTH = 320;
    private static final double LIST_PHOTO_WIDTH = 156;
    private static final double LIST_PHOTO_HEIGHT = 98;
    private static final double LIST_CAR_WIDTH = 130;
    private static final double FRAME_PAD_WIDTH = 16;
    private static final double FRAME_PAD_HEIGHT = 8;
    private static final double CAR_PAD_WIDTH = 24;
    private static final double CAR_PAD_HEIGHT = 12;
    private static final double CAR_ASPECT = 200.0 / 90.0;
    private static final double CORNER_ARC = 18;
    private static final double STAGE_PREF_WIDTH = 172;
    private static final double STAGE_MIN_HEIGHT = 106;
    private final IntFunction<Path> photos;
    private final Map<String, Image> cache = new HashMap<>();

    VehiclePictures(IntFunction<Path> photos) {
        this.photos = photos;
    }

    // The framed picture used in the lists (about 172 x 106).
    Node stage(Vehicle v) {
        Path photo = photos.apply(v.getId());
        Image image = photo == null ? null : load(photo, LIST_LOAD_WIDTH);
        StackPane stage = new StackPane(image != null
                ? photoNode(image, LIST_PHOTO_WIDTH, LIST_PHOTO_HEIGHT)
                : CarGraphic.create(v.getType(), v.getColor(), LIST_CAR_WIDTH));
        stage.getStyleClass().add("car-stage");
        stage.setMinWidth(STAGE_MIN_WIDTH);
        stage.setPrefWidth(STAGE_PREF_WIDTH);
        stage.setMinHeight(STAGE_MIN_HEIGHT);
        return stage;
    }

    // A framed picture of any size, for the renter's cards: the saved photo or the drawn car.
    Node stage(Vehicle v, double width, double height) {
        Path photo = photos.apply(v.getId());
        Image image = photo == null ? null : load(photo, width * 2);
        Node inner = image != null
                ? photoNode(image, width - FRAME_PAD_WIDTH, height - FRAME_PAD_HEIGHT)
                : CarGraphic.create(v.getType(), v.getColor(), Math.min(width - CAR_PAD_WIDTH,
                        (height - CAR_PAD_HEIGHT) * CAR_ASPECT));
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
            String key = file.toAbsolutePath() + "|" + Files.getLastModifiedTime(file) + "|"
                    + width;
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
        clip.setArcWidth(CORNER_ARC);
        clip.setArcHeight(CORNER_ARC);
        box.setClip(clip);
        return box;
    }
}
