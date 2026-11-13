package fleetrent.ui;

import fleetrent.service.Countries;
import fleetrent.service.PhoneNumber;
import fleetrent.service.Validators;

import javafx.scene.control.ComboBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

// A phone number: a drop-down to choose the country (its calling code) and a box for the number.
// The finished number is saved as "+63 9171234567".
final class PhoneField {
    private static final double COUNTRY_WIDTH = 190;
    private final ComboBox<Countries.Country> country = new ComboBox<>();
    private final ValidatedField number =
            new ValidatedField(Validators.Kind.PHONE_DIGITS, "917 123 4567", true);
    private final HBox row = new HBox(Layout.GAP_NORMAL, country, number.node());

    PhoneField() {
        country.getItems().setAll(Countries.all());
        country.setValue(Countries.defaultCountry());
        country.setPrefWidth(COUNTRY_WIDTH);
        country.setMinWidth(COUNTRY_WIDTH);
        country.setVisibleRowCount(10);
        number.field().setPrefWidth(COUNTRY_WIDTH);
        number.field().setMinWidth(0);
        country.valueProperty().addListener((obs, was, now) -> number.showError(null));
    }

    VBox node() {
        return new VBox(Layout.GAP_HAIR, row);
    }

    // The number with its calling code: "+63 9171234567".
    String value() {
        return PhoneNumber.compose(country.getValue(), number.text());
    }

    // Checks the number for the chosen country and shows a red message when it is wrong.
    boolean validate() {
        if (!number.validate()) {
            return false;
        }
        String message = PhoneNumber.check(country.getValue(), number.text());
        number.showError(message);
        return message == null;
    }

    void reset() {
        number.reset();
        country.setValue(Countries.defaultCountry());
    }
}
