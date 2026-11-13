package fleetrent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class PersonNameTest {
    @Test
    void thePartsAreJoinedAsLastCommaFirstMiddle() {
        assertEquals("Reyes, Ana Cruz", PersonName.compose("Reyes", "Ana", "Cruz"));
        assertEquals("Dela Cruz, Juan Pablo", PersonName.compose("  Dela   Cruz ", "Juan",
                " Pablo"));
    }

    @Test
    void aJoinedNamePassesTheCheck() {
        assertNull(PersonName.check("Reyes, Ana Cruz"));
        assertNull(PersonName.check(PersonName.compose("Dela Cruz", "Juan", "Pablo")));
        assertNull(PersonName.check("O'Brien, Mary-Ann Lopez"));
    }

    @Test
    void aNameWithoutAllThreePartsIsRefused() {
        for (String bad : new String[] {null, "", "  ", "Ana Reyes", "Reyes,", "Reyes, Ana",
                ", Ana Cruz", "R, A C", "Reyes, A Cruz", "Reyes 1, Ana Cruz"}) {
            assertNotNull(PersonName.check(bad), String.valueOf(bad));
        }
    }
}
