package fleetrent.service;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {
    @Test
    void samePasswordAndSaltGiveTheSameHash() {
        String salt = PasswordHasher.newSalt();
        assertEquals(PasswordHasher.hash("Secret123", salt), PasswordHasher.hash("Secret123",
                salt));
    }

    @Test
    void differentSaltsGiveDifferentHashes() {
        assertNotEquals(PasswordHasher.newSalt(), PasswordHasher.newSalt());
        assertNotEquals(PasswordHasher.hash("Secret123", PasswordHasher.newSalt()),
                PasswordHasher.hash("Secret123", PasswordHasher.newSalt()));
    }

    @Test
    void hashIsNotThePlainPassword() {
        assertFalse(PasswordHasher.hash("Secret123", PasswordHasher.newSalt())
                .contains("Secret123"));
    }

    @Test
    void matchesAcceptsTheRightPasswordOnly() {
        String salt = PasswordHasher.newSalt();
        String hash = PasswordHasher.hash("Secret123", salt);
        assertTrue(PasswordHasher.matches("Secret123", salt, hash));
        assertFalse(PasswordHasher.matches("secret123", salt, hash));
        assertFalse(PasswordHasher.matches("", salt, hash));
        assertFalse(PasswordHasher.matches(null, salt, hash));
    }
}
