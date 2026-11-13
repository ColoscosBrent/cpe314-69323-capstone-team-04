package fleetrent.ui;

import fleetrent.service.PersonName;
import fleetrent.service.Validators;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

// Three boxes for a person's name: last name, first name and middle name. All three are required.
// The finished name is "Last, First Middle".
final class NameFields {
    private final ValidatedField last = new ValidatedField(Validators.Kind.NAME, "Reyes", true);
    private final ValidatedField first = new ValidatedField(Validators.Kind.NAME, "Ana", true);
    private final ValidatedField middle =
            new ValidatedField(Validators.Kind.NAME_PART, "Santos", true);
    private final VBox node = new VBox(Layout.GAP_MEDIUM, labeled("Last name", last),
            labeled("First name", first), labeled("Middle name", middle));

    private static VBox labeled(String text, ValidatedField field) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return new VBox(Layout.GAP_HAIR, label, field.node());
    }

    VBox node() {
        return node;
    }

    // The three parts joined: "Reyes, Ana Santos".
    String value() {
        return PersonName.compose(last.text(), first.text(), middle.text());
    }

    // Checks the three boxes and shows a red message under each box that is wrong.
    boolean validate() {
        boolean ok = last.validate();
        ok = first.validate() && ok;
        ok = middle.validate() && ok;
        return ok;
    }

    void reset() {
        last.reset();
        first.reset();
        middle.reset();
    }
}
