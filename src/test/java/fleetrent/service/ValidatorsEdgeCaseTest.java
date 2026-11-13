package fleetrent.service;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fleetrent.service.Validators.Kind;
import org.junit.jupiter.api.Test;

// The exact edges of every input rule: the longest allowed text, one more, nothing at all, and null.
class ValidatorsEdgeCaseTest {

    @Test
    void nullTextIsNeverTypedAndIsAnErrorOnlyWhenRequired() {
        for (Kind k : Kind.values()) {
            assertFalse(k.typingAllowed(null));
            assertNull(Validators.check(k, null, false));
            assertEquals("This field is required.", Validators.check(k, null, true));
            assertEquals("This field is required.", Validators.check(k, "   ", true));
            assertEquals("", Validators.normalize(k, null));
        }
    }

    @Test
    void theEmptyTextMayAlwaysBeTypedSoABackspaceWorks() {
        for (Kind k : Kind.values()) {
            assertTrue(k.typingAllowed(""));
        }
    }

    @Test
    void lengthLimitsAreExactForEveryKind() {
        assertTrue(Kind.NAME.typingAllowed("a".repeat(60)));
        assertFalse(Kind.NAME.typingAllowed("a".repeat(61)));
        assertTrue(Kind.CONTACT.typingAllowed("1".repeat(20)));
        assertFalse(Kind.CONTACT.typingAllowed("1".repeat(21)));
        assertTrue(Kind.EMAIL.typingAllowed("a".repeat(80)));
        assertFalse(Kind.EMAIL.typingAllowed("a".repeat(81)));
        assertTrue(Kind.ADDRESS.typingAllowed("a".repeat(120)));
        assertFalse(Kind.ADDRESS.typingAllowed("a".repeat(121)));
        assertTrue(Kind.PLATE.typingAllowed("A".repeat(10)));
        assertFalse(Kind.PLATE.typingAllowed("A".repeat(11)));
        assertTrue(Kind.DIGITS.typingAllowed("1234567"));
        assertFalse(Kind.DIGITS.typingAllowed("12345678"));
        assertTrue(Kind.VEHICLE_TEXT.typingAllowed("a".repeat(50)));
        assertFalse(Kind.VEHICLE_TEXT.typingAllowed("a".repeat(51)));
    }

    @Test
    void moneyAllowsTwoDecimalsAndNoMore() {
        assertTrue(Kind.MONEY.typingAllowed("1500.50"));
        assertTrue(Kind.MONEY.typingAllowed("1500."));
        assertTrue(Kind.MONEY.typingAllowed(".5"));
        assertFalse(Kind.MONEY.typingAllowed("1500.505"));
        assertFalse(Kind.MONEY.typingAllowed("1.2.3"));
        assertFalse(Kind.MONEY.typingAllowed("-5"));
        assertFalse(Kind.MONEY.typingAllowed("1e5"));
        assertFalse(Kind.MONEY.typingAllowed("1,500"));
        assertFalse(Kind.MONEY.typingAllowed("NaN"));
        assertFalse(Kind.MONEY.typingAllowed("1234567"));
    }

    @Test
    void moneyMustBeMoreThanZeroWhenFinished() {
        assertNotNull(Validators.check(Kind.MONEY, "0", true));
        assertNotNull(Validators.check(Kind.MONEY, "0.00", true));
        assertNotNull(Validators.check(Kind.MONEY, ".", true));
        assertNull(Validators.check(Kind.MONEY, ".5", true));
        assertNull(Validators.check(Kind.MONEY, "0.01", true));
        assertNull(Validators.check(Kind.MONEY, "999999.99", true));
    }

    @Test
    void phoneNumbersNeedSevenToFifteenDigits() {
        assertNotNull(Validators.check(Kind.CONTACT, "123456", true));
        assertNull(Validators.check(Kind.CONTACT, "1234567", true));
        assertNull(Validators.check(Kind.CONTACT, "+63 917-123-4567", true));
        assertNull(Validators.check(Kind.CONTACT, "1".repeat(15), true));
        assertNotNull(Validators.check(Kind.CONTACT, "1".repeat(16), true));
        assertNotNull(Validators.check(Kind.CONTACT, "+", true));
        assertNotNull(Validators.check(Kind.CONTACT, "09a71234567", true));
        assertFalse(Kind.CONTACT.typingAllowed("12+34"));
        assertFalse(Kind.CONTACT.typingAllowed("++1234"));
    }

    @Test
    void emailsNeedOneAtSignAndADottedDomain() {
        assertNull(Validators.check(Kind.EMAIL, "ana.reyes+rent@example.com", true));
        assertNull(Validators.check(Kind.EMAIL, "a@b.co", true));
        assertNotNull(Validators.check(Kind.EMAIL, "ana@example", true));
        assertNotNull(Validators.check(Kind.EMAIL, "@example.com", true));
        assertNotNull(Validators.check(Kind.EMAIL, "ana@.com", true));
        assertNotNull(Validators.check(Kind.EMAIL, "ana@@example.com", true));
        assertFalse(Kind.EMAIL.typingAllowed("a@b@c"));
        assertFalse(Kind.EMAIL.typingAllowed("a b"));
        assertEquals("ana@example.com", Validators.normalize(Kind.EMAIL, "  ANA@Example.COM "));
    }

    @Test
    void namesAcceptLettersFromAnyLanguageButNotDigitsOrEmoji() {
        assertNull(Validators.check(Kind.NAME, "José Ñandú", true));
        assertNull(Validators.check(Kind.NAME, "李小龍", true));
        assertNull(Validators.check(Kind.NAME, "O'Brien-Smith Jr.", true));
        assertNotNull(Validators.check(Kind.NAME, "A", true));
        assertNotNull(Validators.check(Kind.NAME, "1Ana", true));
        assertNotNull(Validators.check(Kind.NAME, "-Ana", true));
        assertFalse(Kind.NAME.typingAllowed("Ana 😀"));
        assertFalse(Kind.NAME.typingAllowed("Ana2"));
        assertFalse(Kind.NAME.typingAllowed("Ana;"));
    }

    @Test
    void platesNeedThreeLettersOrDigitsAndAreSavedInCapitals() {
        assertNotNull(Validators.check(Kind.PLATE, "A-", true));
        assertNotNull(Validators.check(Kind.PLATE, "- -", true));
        assertNull(Validators.check(Kind.PLATE, "ab 1", true));
        assertEquals("ABC 123", Validators.normalize(Kind.PLATE, "  abc    123 "));
        assertFalse(Kind.PLATE.typingAllowed("AB_1"));
        assertFalse(Kind.PLATE.typingAllowed("AB#1"));
    }

    @Test
    void addressesNeedFiveCharactersAndALetter() {
        assertNotNull(Validators.check(Kind.ADDRESS, "12 3", true));
        assertNotNull(Validators.check(Kind.ADDRESS, "12345", true));
        assertNull(Validators.check(Kind.ADDRESS, "12 Rizal St., Cebu City #4", true));
        assertFalse(Kind.ADDRESS.typingAllowed("12 <b>"));
    }

    @Test
    void wholeNumbersAcceptZeroButNotSignsOrDecimals() {
        assertNull(Validators.check(Kind.DIGITS, "0", true));
        assertNull(Validators.check(Kind.DIGITS, "9999999", true));
        assertNotNull(Validators.check(Kind.DIGITS, "12.5", true));
        assertNotNull(Validators.check(Kind.DIGITS, "-1", true));
        assertNotNull(Validators.check(Kind.DIGITS, "12345678", true));
    }

    @Test
    void vehicleTextNeedsAtLeastOneLetterOrDigit() {
        assertNotNull(Validators.check(Kind.VEHICLE_TEXT, "()", true));
        assertNotNull(Validators.check(Kind.VEHICLE_TEXT, "- -", true));
        assertNull(Validators.check(Kind.VEHICLE_TEXT, "F-150 (Lightning)", true));
        assertNull(Validators.check(Kind.VEHICLE_TEXT, "RAV4 Hybrid & Co.", true));
        assertFalse(Kind.VEHICLE_TEXT.typingAllowed("a;b"));
        assertFalse(Kind.VEHICLE_TEXT.typingAllowed("a<b"));
    }

    @Test
    void normalizeRemovesExtraSpacesInsideAndAroundButKeepsLetters() {
        assertEquals("Ana Maria Reyes", Validators.normalize(Kind.NAME, "  Ana   Maria\tReyes  "));
        assertEquals("", Validators.normalize(Kind.NAME, "    "));
    }

    @Test
    void sameNameHandlesNullAndEmptyWithoutACrash() {
        assertFalse(Validators.sameName(null, "a"));
        assertFalse(Validators.sameName("a", null));
        assertFalse(Validators.sameName(null, null));
        assertTrue(Validators.sameName("", "   "));
        assertTrue(Validators.sameName("F-150  Lightning", " f-150 lightning"));
        assertFalse(Validators.sameName("F-150", "F150"));
    }
}
