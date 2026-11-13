package fleetrent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class AddressRulesTest {
    @Test
    void theThreePartsAreJoinedWithCommas() {
        assertEquals("12 Rizal St, Cebu City, Philippines",
                AddressRules.compose(" 12  Rizal St ", "Cebu City", "Philippines"));
    }

    @Test
    void aGoodAddressPasses() {
        assertNull(AddressRules.checkStreet("12 Rizal St."));
        assertNull(AddressRules.checkCity("Cebu City"));
        assertNull(AddressRules.checkCountry("Philippines"));
        assertNull(AddressRules.checkSaved("12 Rizal St, Cebu City, Philippines"));
    }

    @Test
    void everyPartIsRequired() {
        assertNotNull(AddressRules.checkStreet(""));
        assertNotNull(AddressRules.checkStreet("@@"));
        assertNotNull(AddressRules.checkCity(" "));
        assertNotNull(AddressRules.checkCity("123"));
        assertNotNull(AddressRules.checkCountry(null));
        assertNotNull(AddressRules.checkCountry("Atlantis"));
    }

    @Test
    void aSavedAddressNeedsStreetCityAndCountry() {
        assertNotNull(AddressRules.checkSaved(null));
        assertNotNull(AddressRules.checkSaved("12 Rizal St"));
        assertNotNull(AddressRules.checkSaved("12 Rizal St, Cebu City"));
        assertNotNull(AddressRules.checkSaved("12 Rizal St, Cebu City, Atlantis"));
        assertNull(AddressRules.checkSaved("Unit 4, 12 Rizal St, Cebu City, Philippines"));
    }
}
