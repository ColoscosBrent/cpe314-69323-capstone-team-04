package fleetrent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CountriesTest {
    @Test
    void thePhilippinesIsFirstAndTheDefault() {
        assertEquals("Philippines", Countries.defaultCountry().name());
        assertEquals("63", Countries.defaultCountry().dialCode());
        assertEquals(Countries.defaultCountry(), Countries.all().get(0));
    }

    @Test
    void thereAreManyCountriesWithUniqueNames() {
        Set<String> names = new HashSet<>();
        for (Countries.Country c : Countries.all()) {
            assertTrue(names.add(c.name()), "duplicate " + c.name());
            assertTrue(c.minDigits() > 0 && c.minDigits() <= c.maxDigits(), c.name());
            assertTrue(c.dialCode().matches("\\d{1,4}"), c.name());
        }
        assertTrue(Countries.all().size() >= 40);
    }

    @Test
    void aCountryCanBeFoundByNameIgnoringCapitals() {
        assertEquals("Japan", Countries.byName("  japan ").name());
        assertNull(Countries.byName("Atlantis"));
        assertNull(Countries.byName(null));
    }

    @Test
    void aCountryCanBeFoundByItsCallingCode() {
        assertEquals("Philippines", Countries.byDialCode("63").name());
        assertNotNull(Countries.byDialCode("1"));
        assertNull(Countries.byDialCode("999"));
    }

    @Test
    void theLabelShowsTheNameAndTheCode() {
        assertTrue(Countries.defaultCountry().label().contains("+63"));
        assertTrue(Countries.defaultCountry().label().contains("Philippines"));
    }
}
