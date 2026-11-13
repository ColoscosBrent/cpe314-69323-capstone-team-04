package fleetrent.service;

import fleetrent.app.Main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ValidatorsTest {
    @Test
    void nameRejectsDigitsAndSymbols() {
        assertFalse(Validators.Kind.NAME.typingAllowed("Brent1"));
        assertFalse(Validators.Kind.NAME.typingAllowed("Br@nt"));
        assertTrue(Validators.Kind.NAME.typingAllowed("Mary-Ann O'Neil"));
        assertTrue(Validators.Kind.NAME.typingAllowed("Niño"));
    }

    @Test
    void contactRejectsLettersAndAllowsLeadingPlus() {
        assertFalse(Validators.Kind.CONTACT.typingAllowed("0917abc"));
        assertTrue(Validators.Kind.CONTACT.typingAllowed("+63 917-123"));
        assertFalse(Validators.Kind.CONTACT.typingAllowed("09+17"));
    }

    @Test
    void digitsAndMoneyFilters() {
        assertTrue(Validators.Kind.DIGITS.typingAllowed("1200"));
        assertFalse(Validators.Kind.DIGITS.typingAllowed("12a"));
        assertFalse(Validators.Kind.DIGITS.typingAllowed("1.5"));
        assertTrue(Validators.Kind.MONEY.typingAllowed("1500.5"));
        assertFalse(Validators.Kind.MONEY.typingAllowed("1500.555"));
        assertFalse(Validators.Kind.MONEY.typingAllowed("1.2.3"));
        assertFalse(Validators.Kind.MONEY.typingAllowed("-5"));
    }

    @Test
    void plateAndEmailFilters() {
        assertTrue(Validators.Kind.PLATE.typingAllowed("ABC-123"));
        assertFalse(Validators.Kind.PLATE.typingAllowed("AB$1"));
        assertFalse(Validators.Kind.PLATE.typingAllowed("ABCDEFGHIJK"));
        assertTrue(Validators.Kind.EMAIL.typingAllowed("a.b@c.com"));
        assertFalse(Validators.Kind.EMAIL.typingAllowed("a@b@c"));
        assertFalse(Validators.Kind.EMAIL.typingAllowed("a b"));
    }

    @Test
    void emptyTextIsAnErrorOnlyWhenRequired() {
        assertNotNull(Validators.check(Validators.Kind.NAME, "  ", true));
        assertNull(Validators.check(Validators.Kind.EMAIL, "", false));
    }

    @Test
    void checkNameAndContact() {
        assertNull(Validators.check(Validators.Kind.NAME, "Brent Coloscos", true));
        assertNotNull(Validators.check(Validators.Kind.NAME, "B", true));
        assertNotNull(Validators.check(Validators.Kind.NAME, "-- ..", true));
        assertNull(Validators.check(Validators.Kind.CONTACT, "0917 123 4567", true));
        assertNotNull(Validators.check(Validators.Kind.CONTACT, "123", true));
    }

    @Test
    void checkEmailAddressPlateMoneyDigits() {
        assertNull(Validators.check(Validators.Kind.EMAIL, "a@b.com", false));
        assertNotNull(Validators.check(Validators.Kind.EMAIL, "a@b", false));
        assertNotNull(Validators.check(Validators.Kind.EMAIL, "ab.com", false));
        assertNull(Validators.check(Validators.Kind.ADDRESS, "12 Main St, Cebu", true));
        assertNotNull(Validators.check(Validators.Kind.ADDRESS, "1234", true));
        assertNull(Validators.check(Validators.Kind.PLATE, "ABC 123", true));
        assertNotNull(Validators.check(Validators.Kind.PLATE, "A-", true));
        assertNull(Validators.check(Validators.Kind.MONEY, "1500.50", true));
        assertNotNull(Validators.check(Validators.Kind.MONEY, "0", true));
        assertNotNull(Validators.check(Validators.Kind.MONEY, ".", true));
        assertNull(Validators.check(Validators.Kind.DIGITS, "0", true));
        assertNotNull(Validators.check(Validators.Kind.DIGITS, "x", true));
    }

    @Test
    void normalizeCleansText() {
        assertEquals("ABC 123", Validators.normalize(Validators.Kind.PLATE, " abc   123 "));
        assertEquals("a@b.com", Validators.normalize(Validators.Kind.EMAIL, " A@B.com "));
        assertEquals("Juan dela Cruz", Validators.normalize(Validators.Kind.NAME, "Juan  dela Cruz"));
    }

    @Test
    void sameNameIgnoresCapitalsAndExtraSpaces() {
        assertTrue(Validators.sameName("CB1000  Hornet", "CB1000 Hornet"));
        assertTrue(Validators.sameName(" f-150 ", "F-150"));
        assertTrue(Validators.sameName("Honda", "HONDA"));
        assertFalse(Validators.sameName("Civic", "Accord"));
        assertFalse(Validators.sameName(null, "Civic"));
        assertFalse(Validators.sameName("Civic", null));
    }

    @Test
    void aLongModelNameFromNhtsaIsValidEvenWhenItCannotBeTyped() {
        String longName = "Sprinter 2500 Cargo Van Extended Length High Roof Diesel Engine Edition";
        assertFalse(Validators.Kind.VEHICLE_TEXT.typingAllowed(longName));
        assertNull(Validators.check(Validators.Kind.VEHICLE_TEXT, longName, true));
    }
}
