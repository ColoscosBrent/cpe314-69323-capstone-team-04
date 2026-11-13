package fleetrent.ui;


import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.transform.Scale;

/**
 * Draws a small picture of a vehicle with plain JavaFX shapes. The body uses the vehicle's real
 * color. The picture is drawn on a 200 x 90 canvas and then scaled to the width you ask for.
 */
public final class CarGraphic {
    private static final double W = 200;
    private static final double H = 90;
    private static final Color TIRE = Color.web("#1a1530");
    private static final Color RIM = Color.web("#d8d4e8");
    private static final Color GLASS_TOP = Color.web("#d9ecff");
    private static final Color GLASS_BOTTOM = Color.web("#7fa6d6");

    private CarGraphic() {
    }

    /**
     * type is SEDAN, SUV or MOTORCYCLE. colorText is the vehicle's color text, for example "Red".
     */
    public static Node create(String type, String colorText, double width) {
        Color body = ColorNames.toColor(colorText);
        Group art = new Group();
        art.getChildren().add(new Ellipse(100, 82, 84, 5));
        ((Ellipse) art.getChildren().get(0)).setFill(Color.rgb(40, 20, 80, 0.20));
        String t = type == null ? "" : type.toUpperCase(java.util.Locale.ROOT);
        if ("SUV".equals(t)) {
            drawSuv(art, body);
        } else if ("MOTORCYCLE".equals(t)) {
            drawMotorcycle(art, body);
        } else {
            drawSedan(art, body);
        }
        double scale = width / W;
        art.getTransforms().add(new Scale(scale, scale, 0, 0));
        // The invisible rectangle fixes the size of the picture, so layouts can place it exactly.
        Rectangle size = new Rectangle(width, H * scale);
        size.setFill(Color.TRANSPARENT);
        Group box = new Group(size, art);
        return box;
    }

    /** Returns the picture height that goes with the given width. */
    public static double heightFor(double width) {
        return H * width / W;
    }

    private static LinearGradient paint(Color c) {
        return new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, c.interpolate(Color.WHITE, 0.35)), new Stop(0.55, c),
                new Stop(1, c.interpolate(Color.BLACK, 0.25)));
    }

    private static LinearGradient glass() {
        return new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, GLASS_TOP), new Stop(1, GLASS_BOTTOM));
    }

    private static Color edge(Color c) {
        return c.interpolate(Color.BLACK, 0.45);
    }

    private static Circle wheel(double x, double y, double r) {
        Circle tire = new Circle(x, y, r, TIRE);
        return tire;
    }

    private static void addWheel(Group g, double x, double y, double r) {
        g.getChildren().add(wheel(x, y, r));
        Circle rim = new Circle(x, y, r * 0.55, RIM);
        g.getChildren().add(rim);
        Circle hub = new Circle(x, y, r * 0.18, Color.web("#7a7390"));
        g.getChildren().add(hub);
    }

    private static void drawSedan(Group g, Color body) {
        Polygon cabin = new Polygon(46, 42, 68, 16, 130, 16, 160, 42);
        cabin.setFill(paint(body));
        cabin.setStroke(edge(body));
        Rectangle lower = new Rectangle(8, 40, 184, 26);
        lower.setArcWidth(18);
        lower.setArcHeight(18);
        lower.setFill(paint(body));
        lower.setStroke(edge(body));
        Polygon w1 = new Polygon(56, 40, 72, 20, 98, 20, 98, 40);
        Polygon w2 = new Polygon(104, 20, 127, 20, 148, 40, 104, 40);
        w1.setFill(glass());
        w2.setFill(glass());
        Rectangle head = new Rectangle(184, 47, 8, 6);
        head.setFill(Color.web("#fff3b0"));
        Rectangle tail = new Rectangle(8, 47, 7, 6);
        tail.setFill(Color.web("#ff4d6d"));
        Line door = new Line(100, 20, 100, 62);
        door.setStroke(edge(body));
        door.setOpacity(0.5);
        g.getChildren().addAll(cabin, lower, w1, w2, door, head, tail);
        addWheel(g, 50, 66, 14);
        addWheel(g, 150, 66, 14);
    }

    private static void drawSuv(Group g, Color body) {
        Polygon cabin = new Polygon(30, 38, 44, 10, 158, 10, 178, 38);
        cabin.setFill(paint(body));
        cabin.setStroke(edge(body));
        Rectangle lower = new Rectangle(8, 34, 184, 33);
        lower.setArcWidth(16);
        lower.setArcHeight(16);
        lower.setFill(paint(body));
        lower.setStroke(edge(body));
        Polygon w1 = new Polygon(40, 34, 51, 14, 84, 14, 84, 34);
        Polygon w2 = new Polygon(89, 14, 122, 14, 122, 34, 89, 34);
        Polygon w3 = new Polygon(127, 14, 153, 14, 168, 34, 127, 34);
        w1.setFill(glass());
        w2.setFill(glass());
        w3.setFill(glass());
        Line rack = new Line(50, 6, 152, 6);
        rack.setStroke(Color.web("#3b3550"));
        rack.setStrokeWidth(2.5);
        rack.setStrokeLineCap(StrokeLineCap.ROUND);
        Rectangle head = new Rectangle(184, 44, 8, 8);
        head.setFill(Color.web("#fff3b0"));
        Rectangle tail = new Rectangle(8, 44, 7, 8);
        tail.setFill(Color.web("#ff4d6d"));
        Rectangle bumper = new Rectangle(10, 60, 180, 6);
        bumper.setFill(Color.web("#2a2540"));
        bumper.setOpacity(0.55);
        g.getChildren().addAll(cabin, lower, w1, w2, w3, rack, bumper, head, tail);
        addWheel(g, 48, 67, 16);
        addWheel(g, 152, 67, 16);
    }

    private static void drawMotorcycle(Group g, Color body) {
        Line frame = new Line(48, 66, 100, 50);
        frame.setStroke(Color.web("#3b3550"));
        frame.setStrokeWidth(8);
        frame.setStrokeLineCap(StrokeLineCap.ROUND);
        Line swing = new Line(48, 66, 100, 62);
        swing.setStroke(Color.web("#3b3550"));
        swing.setStrokeWidth(6);
        swing.setStrokeLineCap(StrokeLineCap.ROUND);
        Line fork = new Line(154, 66, 136, 26);
        fork.setStroke(Color.web("#4a4466"));
        fork.setStrokeWidth(8);
        fork.setStrokeLineCap(StrokeLineCap.ROUND);
        Rectangle engine = new Rectangle(92, 48, 30, 18);
        engine.setArcWidth(8);
        engine.setArcHeight(8);
        engine.setFill(Color.web("#4a4466"));
        Ellipse tank = new Ellipse(108, 36, 28, 13);
        tank.setFill(paint(body));
        tank.setStroke(edge(body));
        Polygon fairing = new Polygon(126, 24, 148, 30, 146, 46, 124, 40);
        fairing.setFill(paint(body));
        fairing.setStroke(edge(body));
        Polygon tailCowl = new Polygon(62, 38, 84, 30, 90, 40, 70, 46);
        tailCowl.setFill(paint(body));
        tailCowl.setStroke(edge(body));
        Rectangle seat = new Rectangle(78, 29, 30, 6);
        seat.setArcWidth(6);
        seat.setArcHeight(6);
        seat.setFill(Color.web("#2a2540"));
        Line bar = new Line(130, 22, 144, 20);
        bar.setStroke(Color.web("#2a2540"));
        bar.setStrokeWidth(4);
        bar.setStrokeLineCap(StrokeLineCap.ROUND);
        Line pipe = new Line(62, 62, 96, 62);
        pipe.setStroke(Color.web("#b9b3cf"));
        pipe.setStrokeWidth(4);
        pipe.setStrokeLineCap(StrokeLineCap.ROUND);
        Rectangle lamp = new Rectangle(146, 31, 6, 8);
        lamp.setFill(Color.web("#fff3b0"));
        g.getChildren().addAll(swing, frame, fork, engine, pipe, tailCowl, seat, tank, fairing,
                bar, lamp);
        addWheel(g, 46, 66, 17);
        addWheel(g, 154, 66, 17);
    }
}
