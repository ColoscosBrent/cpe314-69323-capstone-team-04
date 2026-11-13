package fleetrent;

import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

// A password box with a "Show password" check box. The dots box and the plain box share one text,
// and only one of them is visible, so the person can check what was typed.
final class PasswordInput {
    private final PasswordField hidden = new PasswordField();
    private final TextField shown = new TextField();
    private final CheckBox toggle = new CheckBox("Show");
    private final Label error = UiHelper.errorLabel();
    private final VBox node;

    PasswordInput(String hint) {
        hidden.setPromptText(hint);
        shown.setPromptText(hint);
        shown.textProperty().bindBidirectional(hidden.textProperty());
        shown.setVisible(false);
        shown.setManaged(false);
        hidden.setPrefWidth(300);
        shown.setPrefWidth(300);
        toggle.selectedProperty().addListener((obs, was, now) -> {
            shown.setVisible(now);
            shown.setManaged(now);
            hidden.setVisible(!now);
            hidden.setManaged(!now);
        });
        error.setMaxWidth(340);
        StackPane box = new StackPane(hidden, shown);
        node = new VBox(4, box, toggle, error);
        hidden.textProperty().addListener((obs, o, n) -> showError(null));
    }

    VBox node() {
        return node;
    }

    String text() {
        return hidden.getText();
    }

    void clear() {
        hidden.setText("");
        toggle.setSelected(false);
        showError(null);
    }

    void showError(String message) {
        UiHelper.setError(error, message);
    }

    void onEnter(Runnable action) {
        hidden.setOnAction(e -> action.run());
        shown.setOnAction(e -> action.run());
    }

    void requestFocus() {
        hidden.requestFocus();
    }
}
