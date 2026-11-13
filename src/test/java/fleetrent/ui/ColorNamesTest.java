package fleetrent.ui;

import fleetrent.model.Vehicle;
import fleetrent.service.SampleData;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

class ColorNamesTest {
    @Test
    void knownNamesGiveTheirColorWhateverTheCaseOrSpaces() {
        assertEquals(ColorNames.toColor("red"), ColorNames.toColor("  RED "));
        assertNotEquals(ColorNames.toColor("red"), ColorNames.toColor("blue"));
    }

    @Test
    void compoundNamesUseTheColorWordInside() {
        assertEquals(ColorNames.toColor("white"), ColorNames.toColor("Pearl White"));
        assertEquals(ColorNames.toColor("black"), ColorNames.toColor("matte black"));
    }

    @Test
    void hexCodesAreAccepted() {
        assertEquals(Color.web("#12ab34"), ColorNames.toColor("#12ab34"));
    }

    @Test
    void unknownEmptyOrNullTextGivesTheFallbackColor() {
        assertEquals(ColorNames.FALLBACK, ColorNames.toColor("zzzz not a color"));
        assertEquals(ColorNames.FALLBACK, ColorNames.toColor(""));
        assertEquals(ColorNames.FALLBACK, ColorNames.toColor("   "));
        assertEquals(ColorNames.FALLBACK, ColorNames.toColor(null));
    }

    @Test
    void everyColorChoiceHasARealColor() {
        for (String name : ColorNames.choices()) {
            assertNotNull(ColorNames.toColor(name));
            assertNotEquals(ColorNames.FALLBACK, ColorNames.toColor(name), name);
        }
    }

    @Test
    void demoVehiclesAllUseKnownColors() {
        for (Vehicle v : SampleData.demoVehicles()) {
            assertNotEquals(ColorNames.FALLBACK, ColorNames.toColor(v.getColor()), v.getColor());
        }
    }
}
