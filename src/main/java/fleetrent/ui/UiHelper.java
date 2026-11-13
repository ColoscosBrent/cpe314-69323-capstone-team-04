package fleetrent.ui;

import fleetrent.controller.Background;


import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

// Small helpers shared by the tabs.
public final class UiHelper {
    private UiHelper() {
    }

    static TextField field(String hint) {
        TextField tf = new TextField();
        tf.setPromptText(hint);
        tf.setPrefWidth(220);
        return tf;
    }

    static void addRow(GridPane grid, int row, String labelText, javafx.scene.Node control) {
        Label label = new Label(labelText);
        label.getStyleClass().add("field-label");
        label.setMinWidth(90);
        grid.add(label, 0, row);
        grid.add(control, 1, row);
    }

    static Label title(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("card-title");
        return l;
    }

    // A red message label that takes no space until it has text.
    static Label errorLabel() {
        Label l = new Label();
        l.getStyleClass().add("error-label");
        l.setWrapText(true);
        l.setMaxWidth(260);
        setError(l, null);
        return l;
    }

    static void setError(Label label, String message) {
        boolean has = message != null && !message.isEmpty();
        label.setText(has ? message : "");
        label.setVisible(has);
        label.setManaged(has);
    }

    // A control with its error label under it, for a grid cell.
    static javafx.scene.layout.VBox withError(Node control, Label error) {
        return new javafx.scene.layout.VBox(3, control, error);
    }

    // Shows a status line in green (ok) or red (problem).
    static void setStatus(Label label, String text, boolean problem) {
        label.setText(text);
        label.getStyleClass().removeAll("status-error", "status-ok");
        label.getStyleClass().add(problem ? "status-error" : "status-ok");
    }

    // Makes every button as wide as its full text, so a label is never cut to "Sh...".
    static void fixButtons(Node node) {
        if (node instanceof javafx.scene.control.Button) {
            ((javafx.scene.control.Button) node).setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        }
        if (node instanceof javafx.scene.Parent) {
            for (Node child : ((javafx.scene.Parent) node).getChildrenUnmodifiable()) {
                fixButtons(child);
            }
        }
    }

    // Wraps a tab page so small screens get scroll bars instead of cut-off buttons.
    static ScrollPane scroll(Node content) {
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);
        sp.setPannable(false);
        // The page never gets shorter than it needs. Its width is limited by the minimum widths of
        // its parts (each tab sets them), so long texts wrap in a narrow window instead of pushing
        // the page wider. The page never gets smaller than the size it needs. On a small screen the
        // scroll bars appear instead, so every button can still be reached.
        if (content instanceof javafx.scene.layout.Region) {
            javafx.scene.layout.Region region = (javafx.scene.layout.Region) content;
            region.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        }
        // The scroll pane itself may shrink to nothing: what does not fit is reached with the
        // scroll bars, and the header above it (title, Sign out) is never pushed out of the window
        // by a wide page.
        sp.setMinSize(0, 0);
        sp.getStyleClass().add("page-scroll");
        // The page, not the scroll pane, takes the click focus, so a text field reacts to one
        // click.
        sp.setFocusTraversable(false);
        fixButtons(content);
        return sp;
    }

    // Runs the job (usually a Task) on the shared pool of the controller layer, so the window
    // never freezes and a double click cannot start many threads.
    static void runInBackground(Runnable job) {
        Background.run(job);
    }

    static void onFxThread(Runnable r) {
        if (Platform.isFxApplicationThread()) {
            r.run();
        } else {
            Platform.runLater(r);
        }
    }
}
