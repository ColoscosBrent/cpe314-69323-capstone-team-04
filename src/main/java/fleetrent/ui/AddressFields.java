package fleetrent.ui;

import fleetrent.service.AddressRules;
import fleetrent.service.Countries;
import fleetrent.service.Validators;

import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

// Three boxes for an address: street address, city and country (a drop-down). All three are
// required. The finished address is "12 Rizal St, Cebu City, Philippines".
final class AddressFields {
    private static final double COUNTRY_WIDTH = 280;
    private final ValidatedField street =
            new ValidatedField(Validators.Kind.STREET, "12 Rizal St", true);
    private final ValidatedField city = new ValidatedField(Validators.Kind.CITY, "Cebu City", true);
    private final ComboBox<String> country = new ComboBox<>();
    private final Label countryError = UiHelper.errorLabel();
    private final VBox node;

    AddressFields() {
        for (Countries.Country c : Countries.all()) {
            country.getItems().add(c.name());
        }
        country.setValue(Countries.DEFAULT_NAME);
        country.setPrefWidth(COUNTRY_WIDTH);
        country.setMinWidth(COUNTRY_WIDTH);
        country.setVisibleRowCount(10);
        country.valueProperty().addListener((obs, was, now) -> UiHelper.setError(countryError,
                null));
        node = new VBox(Layout.GAP_MEDIUM, labeled("Street address", street.node()),
                labeled("City", city.node()),
                labeled("Country", UiHelper.withError(country, countryError)));
    }

    private static VBox labeled(String text, javafx.scene.Node control) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return new VBox(Layout.GAP_HAIR, label, control);
    }

    VBox node() {
        return node;
    }

    // The three parts joined: "12 Rizal St, Cebu City, Philippines".
    String value() {
        return AddressRules.compose(street.text(), city.text(), country.getValue());
    }

    // Checks the three boxes and shows a red message under each one that is wrong.
    boolean validate() {
        boolean ok = street.validate();
        ok = city.validate() && ok;
        String message = AddressRules.checkCountry(country.getValue());
        UiHelper.setError(countryError, message);
        return ok && message == null;
    }

    void reset() {
        street.reset();
        city.reset();
        country.setValue(Countries.DEFAULT_NAME);
    }
}
