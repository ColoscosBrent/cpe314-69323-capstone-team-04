package fleetrent.service;

import fleetrent.model.VehicleTypes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ModelGuideTest {
    @Test
    void wellKnownCarsGetTheirCategory() {
        assertEquals("Mid-size Sedan", ModelGuide.suggestStyle("SEDAN", "TOYOTA", "Camry"));
        assertEquals("Compact Sedan", ModelGuide.suggestStyle("SEDAN", "Honda", "Civic"));
        assertEquals("Subcompact Sedan", ModelGuide.suggestStyle("SEDAN", "Toyota", "Vios"));
        assertEquals("Compact SUV", ModelGuide.suggestStyle("SUV", "Toyota", "RAV4"));
        assertEquals("Mid-size SUV", ModelGuide.suggestStyle("SUV", "FORD", "Everest"));
        assertEquals("MPV / Minivan", ModelGuide.suggestStyle("SUV", "Toyota", "Innova"));
        assertEquals("Off-road SUV", ModelGuide.suggestStyle("SUV", "Jeep", "Wrangler"));
    }

    @Test
    void wellKnownMotorcyclesGetTheirCategory() {
        assertEquals("Standard (naked)", ModelGuide.suggestStyle("MOTORCYCLE", "HONDA", "CB500F"));
        assertEquals("Standard (naked)", ModelGuide.suggestStyle("MOTORCYCLE", "Yamaha", "MT-07"));
        assertEquals("Sport", ModelGuide.suggestStyle("MOTORCYCLE", "Honda", "CBR500R"));
        assertEquals("Sport", ModelGuide.suggestStyle("MOTORCYCLE", "Kawasaki", "Ninja 400"));
        assertEquals("Cruiser", ModelGuide.suggestStyle("MOTORCYCLE", "Honda", "Rebel 500"));
        assertEquals("Scooter", ModelGuide.suggestStyle("MOTORCYCLE", "Yamaha", "NMAX"));
        assertEquals("Adventure (dual-purpose)",
                ModelGuide.suggestStyle("MOTORCYCLE", "KTM", "390 Adventure"));
    }

    @Test
    void theWordsHybridAndElectricDecideFirst() {
        assertEquals("Hybrid Sedan", ModelGuide.suggestStyle("SEDAN", "Toyota", "Camry Hybrid"));
        assertEquals("Hybrid SUV", ModelGuide.suggestStyle("SUV", "Toyota", "RAV4 Prime"));
        assertEquals("Electric SUV", ModelGuide.suggestStyle("SUV", "Hyundai", "Kona EV"));
        assertEquals("Electric Sedan", ModelGuide.suggestStyle("SEDAN", "Tesla", "Model 3"));
        assertEquals("Electric motorcycle",
                ModelGuide.suggestStyle("MOTORCYCLE", "Zero", "SR/F"));
    }

    @Test
    void aWordInsideAnotherWordDoesNotCount() {
        // "ev" is inside "Everest" and "city" is inside "Velocity".
        assertEquals("Mid-size SUV", ModelGuide.suggestStyle("SUV", "Ford", "Everest"));
        assertNull(ModelGuide.suggestStyle("SEDAN", "Acme", "Velocity"));
        // A Yamaha Bolt is a cruiser, not an electric car.
        assertEquals("Cruiser", ModelGuide.suggestStyle("MOTORCYCLE", "Yamaha", "Bolt"));
    }

    @Test
    void aLuxuryMakeGivesTheLuxuryCategory() {
        assertEquals("Luxury Sedan", ModelGuide.suggestStyle("SEDAN", "BMW", "330i"));
        assertEquals("Luxury SUV", ModelGuide.suggestStyle("SUV", "Lexus", "RX 350"));
    }

    @Test
    void anUnknownModelOrABlankOneGivesNoSuggestion() {
        assertNull(ModelGuide.suggestStyle("SEDAN", "Acme", "Roadster 9000"));
        assertNull(ModelGuide.suggestStyle("SEDAN", "Toyota", ""));
        assertNull(ModelGuide.suggestStyle("SEDAN", "Toyota", null));
        assertNull(ModelGuide.suggestStyle(null, "Toyota", "Camry"));
        assertNull(ModelGuide.suggestStyle("TRUCK", "Toyota", "Hilux"));
        // A sedan name asked for another type is not forced into that type.
        assertNull(ModelGuide.suggestStyle("MOTORCYCLE", "Toyota", "Camry"));
    }

    @Test
    void everySuggestionIsACategoryOfThatType() {
        String[][] samples = {{"SEDAN", "Toyota", "Camry"}, {"SEDAN", "Toyota", "Prius"},
            {"SEDAN", "Tesla", "Model S"}, {"SEDAN", "Audi", "A4"}, {"SEDAN", "Subaru", "WRX"},
            {"SEDAN", "Toyota", "Avalon"}, {"SUV", "Toyota", "Fortuner"},
            {"SUV", "Toyota", "Land Cruiser"}, {"SUV", "Suzuki", "Jimny"},
            {"SUV", "Honda", "HR-V"}, {"SUV", "Subaru", "Outback"}, {"SUV", "Kia", "EV9"},
            {"MOTORCYCLE", "Honda", "Wave 110"}, {"MOTORCYCLE", "Honda", "Gold Wing"},
            {"MOTORCYCLE", "Honda", "CRF250L"}, {"MOTORCYCLE", "Honda", "PCX160"},
            {"MOTORCYCLE", "Harley-Davidson", "LiveWire"}};
        for (String[] s : samples) {
            String style = ModelGuide.suggestStyle(s[0], s[1], s[2]);
            assertTrue(style != null && VehicleTypes.styles(s[0]).contains(style),
                    s[1] + " " + s[2] + " gave " + style);
        }
    }
}
