package fleetrent.ui;

import java.util.ArrayList;
import java.util.List;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

// A road with cars that drive from left to right, and a loading line under it that fills again and
// again, like a loading screen. It is drawn with shapes, so it needs no picture file.
final class LoadingRoad {
    private static final double ROAD_WIDTH = 860;
    private static final double ROAD_HEIGHT = 84;
    private static final double ASPHALT_TOP = 30;
    private static final double ASPHALT_HEIGHT = 38;
    private static final double BAR_TOP = 76;
    private static final double BAR_HEIGHT = 5;
    private static final double CAR_TOP = 16;
    private static final double CAR_START = -220;
    private static final double LANE_Y = ASPHALT_TOP + ASPHALT_HEIGHT / 2;
    private static final double DASH_LENGTH = 22;
    private static final double DASH_GAP = 16;
    private static final double DASH_CYCLE_SECONDS = 0.7;
    private static final double BAR_CYCLE_SECONDS = 5;
    private static final double[] CAR_SECONDS = {9, 11, 8};
    private static final double[] CAR_DELAY = {0, 3.2, 6.1};
    private static final double[] CAR_WIDTH = {104, 112, 90};
    private static final String[] CAR_TYPE = {"SEDAN", "SUV", "MOTORCYCLE"};
    private static final String[] CAR_COLOR = {"Gold", "Purple", "Orange"};
    private static final double SHADE = 0.55;
    private final Pane pane = new Pane();
    private final List<Timeline> timelines = new ArrayList<>();

    LoadingRoad() {
        pane.setMinSize(ROAD_WIDTH, ROAD_HEIGHT);
        pane.setPrefSize(ROAD_WIDTH, ROAD_HEIGHT);
        pane.setMaxSize(ROAD_WIDTH, ROAD_HEIGHT);
        // The cars enter from outside the road, so everything beyond its edge is cut off.
        pane.setClip(new Rectangle(ROAD_WIDTH, ROAD_HEIGHT));
        Rectangle asphalt = new Rectangle(0, ASPHALT_TOP, ROAD_WIDTH, ASPHALT_HEIGHT);
        asphalt.setFill(Color.rgb(20, 18, 30, SHADE + 0.3));
        asphalt.setArcWidth(Layout.GAP_WIDER);
        asphalt.setArcHeight(Layout.GAP_WIDER);
        asphalt.setStroke(Color.web("#f5b800"));
        Line dashes = new Line(0, LANE_Y, ROAD_WIDTH, LANE_Y);
        dashes.setStroke(Color.web("#f3eefc"));
        dashes.setStrokeWidth(2);
        dashes.getStrokeDashArray().setAll(DASH_LENGTH, DASH_GAP);
        Rectangle track = new Rectangle(0, BAR_TOP, ROAD_WIDTH, BAR_HEIGHT);
        track.setFill(Color.rgb(255, 255, 255, 0.18));
        Rectangle fill = new Rectangle(0, BAR_TOP, 0, BAR_HEIGHT);
        fill.setFill(Color.web("#f5b800"));
        pane.getChildren().addAll(asphalt, dashes, track, fill);
        addDashAnimation(dashes);
        addBarAnimation(fill);
        for (int i = 0; i < CAR_TYPE.length; i++) {
            addCar(i);
        }
    }

    Node node() {
        return pane;
    }

    // The dashes slide to the left, so the road seems to move under the cars.
    private void addDashAnimation(Line dashes) {
        Timeline t = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(dashes.strokeDashOffsetProperty(), 0)),
                new KeyFrame(Duration.seconds(DASH_CYCLE_SECONDS), new KeyValue(
                        dashes.strokeDashOffsetProperty(), DASH_LENGTH + DASH_GAP,
                        Interpolator.LINEAR)));
        start(t);
    }

    // The loading line fills from left to right and starts again.
    private void addBarAnimation(Rectangle fill) {
        Timeline t = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(fill.widthProperty(), 0)),
                new KeyFrame(Duration.seconds(BAR_CYCLE_SECONDS), new KeyValue(
                        fill.widthProperty(), ROAD_WIDTH, Interpolator.LINEAR)));
        start(t);
    }

    private void addCar(int index) {
        Node car = CarGraphic.create(CAR_TYPE[index], CAR_COLOR[index], CAR_WIDTH[index]);
        car.setLayoutY(CAR_TOP);
        car.setTranslateX(CAR_START);
        pane.getChildren().add(car);
        Timeline t = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(car.translateXProperty(), CAR_START)),
                new KeyFrame(Duration.seconds(CAR_SECONDS[index]), new KeyValue(
                        car.translateXProperty(), ROAD_WIDTH, Interpolator.LINEAR)));
        t.setDelay(Duration.seconds(CAR_DELAY[index]));
        start(t);
    }

    private void start(Timeline t) {
        t.setCycleCount(Animation.INDEFINITE);
        t.play();
        timelines.add(t);
    }

    // Stops every animation when the login screen is left.
    void stop() {
        for (Timeline t : timelines) {
            t.stop();
        }
    }
}
