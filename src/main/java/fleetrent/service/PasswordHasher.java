package fleetrent.service;


import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Passwords and security answers are never saved as plain text. Each one gets its own random salt
 * and is turned into a hash with PBKDF2 (a standard method that comes with Java, so no extra
 * library).
 */
public final class PasswordHasher {
    private static final int ITERATIONS = 100_000;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    /**
     * A new random salt, as text.
     */
    public static String newSalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /** Returns the PBKDF2 hash of the secret with the given salt. */
    public static String hash(String secret, String salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(secret.toCharArray(), Base64.getDecoder().decode(salt),
                    ITERATIONS, KEY_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return Base64.getEncoder().encodeToString(factory.generateSecret(spec).getEncoded());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("This Java cannot hash passwords: " + e.getMessage(), e);
        }
    }

    /**
     * True when the secret gives the saved hash. The comparison always takes the same time.
     */
    public static boolean matches(String secret, String salt, String expectedHash) {
        if (secret == null || salt == null || expectedHash == null) {
            return false;
        }
        byte[] a = hash(secret, salt).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] b = expectedHash.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}
