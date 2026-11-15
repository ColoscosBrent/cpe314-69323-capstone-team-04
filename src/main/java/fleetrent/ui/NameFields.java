package fleetrent.ui;

import fleetrent.service.PersonName;
import fleetrent.service.Validators;

import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

// Three boxes for a person's name: last name, first name and middle name. All three are required.
// The finished name is "Last, First Middle".
final class NameFields {
    private final ValidatedField last = new ValidatedField(Validators.Kind.NAME, "Reyes", true);
    private final ValidatedField first = new ValidatedField(Validators.Kind.NAME, "Ana", true);
    private final ValidatedField middle =
            new ValidatedField(Validators.Kind.NAME_PART, "Santos", true);

    private static VBox labeled(String text, ValidatedField field) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return new VBox(Layout.GAP_HAIR, label, field.node());
    }

    // The three boxes stacked, each with a small label above it. Use this or addRows, not both.
    VBox node() {
        return new VBox(Layout.GAP_MEDIUM, labeled("Last name", last),
                labeled("First name", first), labeled("Middle name", middle));
    }

    // Puts the three boxes on a shared grid, one label per row in the left column.
    // Returns the next free row.
    int addRows(GridPane grid, int row) {
        UiHelper.addRow(grid, row, "Last name", last.node());
        UiHelper.addRow(grid, row + 1, "First name", first.node());
        UiHelper.addRow(grid, row + 2, "Middle name", middle.node());
        return row + 3;
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
