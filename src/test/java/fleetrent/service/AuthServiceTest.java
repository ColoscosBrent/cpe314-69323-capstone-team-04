package fleetrent.service;

import fleetrent.dao.Database;
import fleetrent.model.Account;
import fleetrent.model.Renter;
import fleetrent.model.Role;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthServiceTest {
    private static final String Q = AuthService.SECURITY_QUESTIONS.get(0);
    private Database db;
    private AtomicLong now;
    private AuthService auth;

    @BeforeEach
    void setUp() throws SQLException {
        db = new Database("jdbc:sqlite::memory:");
        db.createTables();
        now = new AtomicLong(1_000_000);
        auth = new AuthService(db, now::get);
    }

    @AfterEach
    void tearDown() throws SQLException {
        db.close();
    }

    private Account owner() throws Exception {
        return auth.signUpOwner("owner01", "Passw0rdX", "Maria Santos", Q, "Rex",
                AuthService.OWNER_CODE);
    }

    private Account renter() throws Exception {
        return auth.signUpRenter("ana_reyes", "Passw0rdX",
                new Renter(0, "Ana Reyes", "09171234567", "ana@example.com", "12 Rizal St, Cebu"),
                        Q, "Rex");
    }

    @Test
    void ownerSignUpMakesAnAdminAccountWithoutRenterRow() throws Exception {
        Account a = owner();
        assertEquals(Role.ADMIN, a.getRole());
        assertNull(db.findAccountByUsername("owner01").getRenterId());
        assertTrue(db.getAllRenters().isEmpty());
    }

    @Test
    void ownerSignUpNeedsTheOwnerCode() {
        AuthException e = assertThrows(AuthException.class,
                () -> auth.signUpOwner("owner01", "Passw0rdX", "Maria Santos", Q, "Rex", "nope"));
        assertTrue(e.getMessage().contains("owner code"));
        assertThrows(AuthException.class,
                () -> auth.signUpOwner("owner01", "Passw0rdX", "Maria Santos", Q, "Rex", null));
    }

    @Test
    void renterSignUpCreatesTheRenterRowAndLinksIt() throws Exception {
        Account a = renter();
        assertEquals(Role.RENTER, a.getRole());
        assertNotNull(a.getRenterId());
        assertEquals("Ana Reyes", db.getRenterById(a.getRenterId()).getName());
        assertEquals(1, db.getAllRenters().size());
    }

    @Test
    void passwordIsNeverSavedAsPlainText() throws Exception {
        Account a = owner();
        assertTrue(!a.getPasswordHash().contains("Passw0rdX"));
        assertTrue(!db.findAccountByUsername("owner01").getAnswerHash().toLowerCase()
                .contains("rex"));
    }

    @Test
    void duplicateUsernameIsRefusedWithoutCase() throws Exception {
        owner();
        AuthException e = assertThrows(AuthException.class, () -> auth.signUpRenter("OWNER01",
                "Passw0rdX",
                new Renter(0, "Ana Reyes", "09171234567", null, null), Q, "Rex"));
        assertTrue(e.getMessage().contains("already taken"));
        assertEquals(0, db.getAllRenters().size(), "a refused sign-up must not leave a renter row");
    }

    @Test
    void badInputIsRefusedWithAMessage() {
        assertThrows(AuthException.class, () -> auth.signUpOwner("ab", "Passw0rdX", "Maria Santos",
                Q, "Rex", AuthService.OWNER_CODE));
        assertThrows(AuthException.class, () -> auth.signUpOwner("owner01", "short1",
                "Maria Santos", Q, "Rex", AuthService.OWNER_CODE));
        assertThrows(AuthException.class, () -> auth.signUpOwner("owner01", "Passw0rdX", "M", Q,
                "Rex", AuthService.OWNER_CODE));
        assertThrows(AuthException.class, () -> auth.signUpOwner("owner01", "Passw0rdX",
                "Maria Santos", "Made up?", "Rex", AuthService.OWNER_CODE));
        assertThrows(AuthException.class, () -> auth.signUpOwner("owner01", "Passw0rdX",
                "Maria Santos", Q, " ", AuthService.OWNER_CODE));
        assertThrows(AuthException.class, () -> auth.signUpRenter("ana_reyes", "Passw0rdX",
                new Renter(0, "Ana Reyes", "123", null, null), Q, "Rex"));
    }

    @Test
    void usernameAndPasswordRules() {
        assertNull(AuthService.checkUsername("owner.01"));
        assertNotNull(AuthService.checkUsername("1owner"));
        assertNotNull(AuthService.checkUsername("own er"));
        assertNotNull(AuthService.checkUsername("abc"));
        assertNotNull(AuthService.checkUsername("a".repeat(21)));
        assertNull(AuthService.checkPassword("Passw0rdX"));
        assertNotNull(AuthService.checkPassword("password"));
        assertNotNull(AuthService.checkPassword("12345678"));
        assertNotNull(AuthService.checkPassword("Pass w0rdX"));
        assertNotNull(AuthService.checkPassword("P1" + "x".repeat(70)));
        assertNull(AuthService.checkAnswer(" Rex "));
        assertNotNull(AuthService.checkAnswer(" "));
    }

    @Test
    void loginWorksWithTheRightPasswordAndUsernameWithoutCase() throws Exception {
        owner();
        assertEquals("owner01", auth.login("OWNER01", "Passw0rdX").getUsername());
    }

    @Test
    void loginFailsWithAWrongPasswordOrUnknownUser() throws Exception {
        owner();
        assertEquals("The username or the password is not correct.",
                assertThrows(AuthException.class, () -> auth.login("owner01", "wrongPass1"))
                        .getMessage());
        assertEquals("The username or the password is not correct.",
                assertThrows(AuthException.class, () -> auth.login("nobody1", "Passw0rdX"))
                        .getMessage());
    }

    @Test
    void fiveWrongTriesLockTheAccountForThirtySeconds() throws Exception {
        owner();
        for (int i = 0; i < AuthService.MAX_FAILURES; i++) {
            assertThrows(AuthException.class, () -> auth.login("owner01", "wrongPass1"));
        }
        AuthException locked = assertThrows(AuthException.class, () -> auth.login("owner01",
                "Passw0rdX"));
        assertTrue(locked.getMessage().startsWith("Too many wrong tries"));
        now.addAndGet(AuthService.LOCK_MILLIS + 1);
        assertEquals("owner01", auth.login("owner01", "Passw0rdX").getUsername());
    }

    @Test
    void aGoodLoginResetsTheCounter() throws Exception {
        owner();
        for (int i = 0; i < AuthService.MAX_FAILURES - 1; i++) {
            assertThrows(AuthException.class, () -> auth.login("owner01", "wrongPass1"));
        }
        auth.login("owner01", "Passw0rdX");
        for (int i = 0; i < AuthService.MAX_FAILURES - 1; i++) {
            assertThrows(AuthException.class, () -> auth.login("owner01", "wrongPass1"));
        }
        assertNotNull(auth.login("owner01", "Passw0rdX"));
    }

    @Test
    void forgotPasswordShowsTheQuestionAndAcceptsTheAnswerWithoutCase() throws Exception {
        owner();
        assertEquals(Q, auth.securityQuestionOf("owner01"));
        auth.resetPassword("owner01", "  REX ", "NewPassw0rd");
        assertEquals("owner01", auth.login("owner01", "NewPassw0rd").getUsername());
        assertThrows(AuthException.class, () -> auth.login("owner01", "Passw0rdX"));
    }

    @Test
    void forgotPasswordWithAWrongAnswerChangesNothing() throws Exception {
        owner();
        AuthException e = assertThrows(AuthException.class, () -> auth.resetPassword("owner01",
                "Tom", "NewPassw0rd"));
        assertEquals("That answer is not correct.", e.getMessage());
        assertNotNull(auth.login("owner01", "Passw0rdX"));
    }

    @Test
    void forgotPasswordRefusesAWeakNewPasswordAndAnUnknownUser() throws Exception {
        owner();
        assertThrows(AuthException.class, () -> auth.resetPassword("owner01", "Rex", "weak"));
        assertThrows(AuthException.class, () -> auth.securityQuestionOf("nobody1"));
        assertThrows(AuthException.class, () -> auth.resetPassword("nobody1", "Rex",
                "NewPassw0rd"));
        assertNotNull(auth.login("owner01", "Passw0rdX"));
    }

    @Test
    void changePasswordNeedsTheCurrentPassword() throws Exception {
        Account a = renter();
        assertThrows(AuthException.class, () -> auth.changePassword(a.getId(), "wrongPass1",
                "NewPassw0rd"));
        auth.changePassword(a.getId(), "Passw0rdX", "NewPassw0rd");
        assertNotNull(auth.login("ana_reyes", "NewPassw0rd"));
        assertThrows(AuthException.class, () -> auth.changePassword(a.getId(), "NewPassw0rd",
                "short"));
    }

    @Test
    void ownerCodeIsNotStoredAnywhere() throws Exception {
        Account a = owner();
        assertTrue(!a.getDisplayName().contains(AuthService.OWNER_CODE));
    }
}
