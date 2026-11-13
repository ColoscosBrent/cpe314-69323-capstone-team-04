package fleetrent.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RenterTest {
    @Test
    void keepsTheDetails() {
        Renter r = new Renter(4, "Ana Cruz", "09171234567", "ana@mail.com", "Cebu City");
        assertEquals(4, r.getId());
        assertEquals("Ana Cruz", r.getName());
        assertEquals("09171234567", r.getContact());
        assertEquals("ana@mail.com", r.getEmail());
        assertEquals("Cebu City", r.getAddress());
    }

    @Test
    void idCanBeSetAfterSaving() {
        Renter r = new Renter(0, "Ben", null, null, null);
        r.setId(12);
        assertEquals(12, r.getId());
    }

    @Test
    void toStringShowsTheName() {
        assertTrue(new Renter(1, "Cara", null, null, null).toString().contains("Cara"));
    }
}
