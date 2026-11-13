package fleetrent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PhoneNumberTest {
    private static final Countries.Country PH = Countries.defaultCountry();

    @Test
    void aLeadingZeroIsDroppedAndSeparatorsAreIgnored() {
        assertEquals("9171234567", PhoneNumber.digitsOf("0917 123 4567"));
        assertEquals("9171234567", PhoneNumber.digitsOf("917-123-4567"));
        assertEquals("", PhoneNumber.digitsOf(null));
    }

    @Test
    void theSavedTextHasTheCallingCodeAndTheDigits() {
        assertEquals("+63 9171234567", PhoneNumber.compose(PH, "0917 123 4567"));
        assertEquals("+81 9012345678", PhoneNumber.compose(Countries.byName("Japan"),
                "090 1234 5678"));
    }

    @Test
    void aPhilippineMobileNeedsTenDigitsStartingWithNine() {
        assertNull(PhoneNumber.check(PH, "0917 123 4567"));
        assertNotNull(PhoneNumber.check(PH, "0817 123 4567"));
        assertNotNull(PhoneNumber.check(PH, "917 123"));
        assertEquals("This field is required.", PhoneNumber.check(PH, "  "));
        assertTrue(PhoneNumber.check(null, "9171234567").contains("country"));
    }

    @Test
    void otherCountriesUseTheirOwnLengths() {
        assertNull(PhoneNumber.check(Countries.byName("United States"), "415 555 2671"));
        assertNotNull(PhoneNumber.check(Countries.byName("United States"), "415 555"));
    }

    @Test
    void aSavedNumberIsCheckedAgainstItsCountry() {
        assertNull(PhoneNumber.checkSaved("+63 9171234567"));
        assertNotNull(PhoneNumber.checkSaved("09171234567"));
        assertNotNull(PhoneNumber.checkSaved("+63 123"));
        assertNotNull(PhoneNumber.checkSaved("+999 1234567"));
        assertNotNull(PhoneNumber.checkSaved(null));
    }
}
