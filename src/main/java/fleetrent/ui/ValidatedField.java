package fleetrent.ui;

import fleetrent.service.Validators;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.VBox;

// A text field that only accepts the characters that belong in it, with a red message under it.
// Blocked keys show the message at once; the finished text is checked when the user leaves the
// field or presses a button (validate()). The rules live in Validators, which is unit tested.
final class ValidatedField {
    private static final double FIELD_PREF_WIDTH = 280;
    private static final double FIELD_MIN_WIDTH = 280;
    private static final double ERROR_MAX_WIDTH = 300;
    private final Validators.Kind kind;
    private final boolean required;
    private final TextField field = new TextField();
    private final Label error = new Label();
    private final VBox node = new VBox(Layout.GAP_HAIR);
    private boolean touched;
    private boolean programmatic;

    ValidatedField(Validators.Kind kind, String hint, boolean required) {
        this.kind = kind;
        this.required = required;
        field.setPromptText(hint);
        field.setPrefWidth(FIELD_PREF_WIDTH);
        field.setMinWidth(FIELD_MIN_WIDTH);
        error.getStyleClass().add("error-label");
        error.setWrapText(true);
        error.setMaxWidth(ERROR_MAX_WIDTH);
        error.setVisible(false);
        error.setManaged(false);
        node.getChildren().addAll(field, error);
        field.setTextFormatter(new TextFormatter<String>(change -> {
            if (!change.isContentChange()) {
                return change;
            }
            // A value that the program sets (a make or a model chosen from the NHTSA list) is not a
            // key press.
            if (programmatic || kind.typingAllowed(change.getControlNewText())) {
                return change;
            }
            show(kind.rejectMessage());
            return null;
        }));
        field.textProperty().addListener((obs, oldText, newText) -> {
            if (touched) {
                check();
            } else {
                show(null);
            }
        });
        field.focusedProperty().addListener((obs, was, now) -> {
            if (!now && !field.getText().isEmpty()) {
                touched = true;
                check();
            }
        });
    }

    VBox node() {
        return node;
    }

    TextField field() {
        return field;
    }

    String text() {
        return Validators.normalize(kind, field.getText());
    }

    // Puts text in the box without the typing rules. Used for names that come from NHTSA, which can
    // be longer than 50 characters or have a character a person would not type.
    void setValue(String value) {
        programmatic = true;
        try {
            field.setText(value == null ? "" : value);
        } finally {
            programmatic = false;
        }
        show(null);
    }

    // The text for saving, or null when the box is empty.
    String textOrNull() {
        String t = text();
        return t.isEmpty() ? null : t;
    }

    // Checks the text now and shows the message. Returns true when the text is fine.
    boolean validate() {
        touched = true;
        return check();
    }

    // Shows a message that comes from outside the field (for example "pick a make").
    void showError(String message) {
        show(message);
    }

    void reset() {
        touched = false;
        field.setText("");
        show(null);
    }

    private boolean check() {
        String message = Validators.check(kind, field.getText(), required);
        show(message);
        return message == null;
    }

    private void show(String message) {
        boolean has = message != null && !message.isEmpty();
        error.setText(has ? message : "");
        error.setVisible(has);
        error.setManaged(has);
        if (has) {
            if (!field.getStyleClass().contains("invalid-field")) {
                field.getStyleClass().add("invalid-field");
            }
        } else {
            field.getStyleClass().remove("invalid-field");
        }
    }
}
