package fleetrent.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;

// The frame of a FleetRent window. The window has no system title bar. This class draws its own
// title bar in the colors of the app (title, minimize, maximize and close buttons), moves the
// window when the bar is dragged, and lets the window be resized from its edges.
final class ThemedWindow {
    private static final double EDGE = 6;
    private static final double ICON_SIZE = 26;
    private static final String ICON_FILE = "/images/logo-icon.png";
    private static final String BIG_ICON_FILE = "/images/logo-256.png";
    private static final String MINIMIZE_TEXT = "–";
    private static final String MAXIMIZE_TEXT = "□";
    private static final String RESTORE_TEXT = "❐";
    private static final String CLOSE_TEXT = "✕";

    private final Stage stage;
    private final StackPane body = new StackPane();
    private final BorderPane frame = new BorderPane();
    private final Button maximizeButton = new Button(MAXIMIZE_TEXT);
    private Rectangle2D restoreBounds;
    private boolean maximized;
    private double dragOffsetX;
    private double dragOffsetY;
    private Edge edge = Edge.NONE;
    private double startX;
    private double startY;
    private double startWidth;
    private double startHeight;
    private double startMouseX;
    private double startMouseY;

    // Which edge of the window the mouse is on.
    private enum Edge {
        NONE(Cursor.DEFAULT), N(Cursor.N_RESIZE), S(Cursor.S_RESIZE), W(Cursor.W_RESIZE),
        E(Cursor.E_RESIZE), NW(Cursor.NW_RESIZE), NE(Cursor.NE_RESIZE), SW(Cursor.SW_RESIZE),
        SE(Cursor.SE_RESIZE);

        private final Cursor cursor;

        Edge(Cursor cursor) {
            this.cursor = cursor;
        }
    }

    // Builds the frame for the stage. The scene is made by the caller from frame().
    ThemedWindow(Stage stage) {
        this.stage = stage;
        frame.getStyleClass().add("window-frame");
        frame.setTop(buildTitleBar());
        frame.setCenter(body);
    }

    // The root of the scene.
    Node frame() {
        return frame;
    }

    // The place where the screens (login, owner, renter) are shown, under the title bar.
    StackPane body() {
        return body;
    }

    // Starts the edge handling. Call it once, after the scene was made.
    void install(Scene scene) {
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
            if (!maximized) {
                scene.setCursor(edgeAt(scene, e).cursor);
            }
        });
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            Edge hit = maximized ? Edge.NONE : edgeAt(scene, e);
            if (hit != Edge.NONE && e.getButton() == MouseButton.PRIMARY) {
                edge = hit;
                startX = stage.getX();
                startY = stage.getY();
                startWidth = stage.getWidth();
                startHeight = stage.getHeight();
                startMouseX = e.getScreenX();
                startMouseY = e.getScreenY();
                e.consume();
            } else {
                edge = Edge.NONE;
            }
        });
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (edge != Edge.NONE) {
                resize(e.getScreenX() - startMouseX, e.getScreenY() - startMouseY);
                e.consume();
            }
        });
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> edge = Edge.NONE);
    }

    private Node buildTitleBar() {
        Node icon = logoIcon();
        Label title = new Label();
        title.getStyleClass().add("title-bar-text");
        title.textProperty().bind(stage.titleProperty());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button minimize = windowButton(MINIMIZE_TEXT, "title-bar-button");
        minimize.setOnAction(e -> stage.setIconified(true));
        maximizeButton.getStyleClass().add("title-bar-button");
        maximizeButton.setFocusTraversable(false);
        maximizeButton.setOnAction(e -> toggleMaximized());
        Button close = windowButton(CLOSE_TEXT, "title-bar-close");
        close.setOnAction(e -> stage.close());
        HBox bar = new HBox(Layout.GAP_NORMAL, icon, title, spacer, minimize, maximizeButton,
                close);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 0, 0, Layout.GAP_WIDER));
        bar.getStyleClass().add("title-bar");
        bar.setOnMousePressed(e -> {
            dragOffsetX = e.getScreenX() - stage.getX();
            dragOffsetY = e.getScreenY() - stage.getY();
        });
        bar.setOnMouseDragged(e -> {
            if (!maximized && edge == Edge.NONE) {
                stage.setX(e.getScreenX() - dragOffsetX);
                stage.setY(e.getScreenY() - dragOffsetY);
            }
        });
        bar.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2
                    && !(e.getTarget() instanceof Button)) {
                toggleMaximized();
            }
        });
        return bar;
    }

    // The FleetRent logo for the left end of the title bar. If the picture file is missing, a
    // small diamond is shown instead, so the window still opens.
    private static Node logoIcon() {
        java.net.URL file = ThemedWindow.class.getResource(ICON_FILE);
        if (file == null) {
            Label diamond = new Label("◆");
            diamond.getStyleClass().add("title-bar-icon");
            return diamond;
        }
        ImageView logo = new ImageView(new Image(file.toExternalForm()));
        logo.setFitHeight(ICON_SIZE);
        logo.setFitWidth(ICON_SIZE);
        logo.setPreserveRatio(true);
        logo.setSmooth(true);
        logo.getStyleClass().add("title-bar-logo");
        return logo;
    }

    // Gives the window the FleetRent logo as its icon (the taskbar and Alt+Tab show it).
    static void setWindowIcon(Stage stage) {
        java.net.URL file = ThemedWindow.class.getResource(BIG_ICON_FILE);
        if (file != null) {
            stage.getIcons().add(new Image(file.toExternalForm()));
        }
    }

    private static Button windowButton(String text, String styleClass) {
        Button b = new Button(text);
        b.getStyleClass().add("title-bar-button");
        if (!"title-bar-button".equals(styleClass)) {
            b.getStyleClass().add(styleClass);
        }
        b.setFocusTraversable(false);
        return b;
    }

    // Fills the usable area of the screen (not the taskbar), or goes back to the old size.
    private void toggleMaximized() {
        if (maximized) {
            if (restoreBounds != null) {
                stage.setX(restoreBounds.getMinX());
                stage.setY(restoreBounds.getMinY());
                stage.setWidth(restoreBounds.getWidth());
                stage.setHeight(restoreBounds.getHeight());
            }
            maximized = false;
            maximizeButton.setText(MAXIMIZE_TEXT);
            return;
        }
        restoreBounds = new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(),
                stage.getHeight());
        Rectangle2D area = Screen.getScreensForRectangle(restoreBounds).stream().findFirst()
                .orElse(Screen.getPrimary()).getVisualBounds();
        stage.setX(area.getMinX());
        stage.setY(area.getMinY());
        stage.setWidth(area.getWidth());
        stage.setHeight(area.getHeight());
        maximized = true;
        maximizeButton.setText(RESTORE_TEXT);
    }

    private Edge edgeAt(Scene scene, MouseEvent e) {
        boolean west = e.getSceneX() < EDGE;
        boolean east = e.getSceneX() > scene.getWidth() - EDGE;
        boolean north = e.getSceneY() < EDGE;
        boolean south = e.getSceneY() > scene.getHeight() - EDGE;
        if (north) {
            return west ? Edge.NW : east ? Edge.NE : Edge.N;
        }
        if (south) {
            return west ? Edge.SW : east ? Edge.SE : Edge.S;
        }
        return west ? Edge.W : east ? Edge.E : Edge.NONE;
    }

    // Moves the edge that was grabbed by (dx, dy) pixels, never below the minimum size.
    private void resize(double dx, double dy) {
        double minW = Math.max(stage.getMinWidth(), 1);
        double minH = Math.max(stage.getMinHeight(), 1);
        boolean west = edge == Edge.W || edge == Edge.NW || edge == Edge.SW;
        boolean east = edge == Edge.E || edge == Edge.NE || edge == Edge.SE;
        boolean north = edge == Edge.N || edge == Edge.NW || edge == Edge.NE;
        boolean south = edge == Edge.S || edge == Edge.SW || edge == Edge.SE;
        if (east) {
            stage.setWidth(Math.max(minW, startWidth + dx));
        }
        if (south) {
            stage.setHeight(Math.max(minH, startHeight + dy));
        }
        if (west) {
            double width = Math.max(minW, startWidth - dx);
            stage.setX(startX + startWidth - width);
            stage.setWidth(width);
        }
        if (north) {
            double height = Math.max(minH, startHeight - dy);
            stage.setY(startY + startHeight - height);
            stage.setHeight(height);
        }
    }
}
