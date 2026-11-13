package fleetrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// The odd cases of sign up and login: empty input, long input, strange characters, lockouts and rollbacks.
class AuthEdgeCaseTest {
    private static final String Q = AuthService.SECURITY_QUESTIONS.get(0);
    private Database db;
    private AtomicLong now;
    private AuthService auth;

    @BeforeEach
    void setUp() throws SQLException {
        db = new Database("jdbc:sqlite::memory:");
        db.createTables();
        now = new AtomicLong(5_000_000);
        auth = new AuthService(db, now::get);
    }

    @AfterEach
    void tearDown() throws SQLException {
        db.close();
    }

    private Account renter(String username) throws Exception {
        return auth.signUpRenter(username, "Passw0rdX",
                new Renter(0, "Ana Reyes", "09171234567", "ana@example.com", "12 Rizal St"), Q, "Rex");
    }

    @Test
    void nullAndEmptyInputGivesAMessageNotACrash() {
        assertNotNull(AuthService.checkUsername(null));
        assertNotNull(AuthService.checkUsername(""));
        assertNotNull(AuthService.checkUsername("   "));
        assertNotNull(AuthService.checkPassword(null));
        assertNotNull(AuthService.checkPassword(""));
        assertNotNull(AuthService.checkAnswer(null));
        assertNotNull(AuthService.checkAnswer("  "));
        assertThrows(AuthException.class, () -> auth.login(null, null));
        assertThrows(AuthException.class, () -> auth.login("", ""));
    }

    @Test
    void usernameLengthLimitsAreExact() {
        assertNotNull(AuthService.checkUsername("abc"));
        assertNull(AuthService.checkUsername("abcd"));
        assertNull(AuthService.checkUsername("a" + "b".repeat(19)));
        assertNotNull(AuthService.checkUsername("a" + "b".repeat(20)));
    }

    @Test
    void passwordLengthLimitsAreExact() {
        assertNotNull(AuthService.checkPassword("Abc1234"));
        assertNull(AuthService.checkPassword("Abcd1234"));
        assertNull(AuthService.checkPassword("A1" + "b".repeat(62)));
        assertNotNull(AuthService.checkPassword("A1" + "b".repeat(63)));
    }

    @Test
    void passwordNeedsALetterAndADigitAndNoSpaces() {
        assertNotNull(AuthService.checkPassword("12345678"));
        assertNotNull(AuthService.checkPassword("abcdefgh"));
        assertNotNull(AuthService.checkPassword("abcd 1234"));
        assertNotNull(AuthService.checkPassword("abcd\t1234"));
        assertNull(AuthService.checkPassword("abcd!@#$1"));
    }

    @Test
    void usernamesWithStrangeCharactersAreRefused() {
        for (String bad : new String[] {"1abcd", "_abcd", "ab cd", "ab-cd", "ab@cd", "ab/cd", "abcéd",
                "ab'; DROP TABLE Account;--", "a\nbcd"}) {
            assertNotNull(AuthService.checkUsername(bad), bad);
        }
    }

    @Test
    void aSqlLookingUsernameCannotHurtTheDatabase() throws Exception {
        renter("ana_reyes");
        AuthException e = assertThrows(AuthException.class, () -> auth.login("ana_reyes' OR '1'='1", "Passw0rdX"));
        assertNotNull(e.getMessage());
        assertThrows(AuthException.class, () -> auth.login("x'; DROP TABLE Account;--", "Passw0rdX"));
        assertNotNull(auth.login("ana_reyes", "Passw0rdX"));
    }

    @Test
    void aSqlLookingSecurityAnswerIsStoredAsPlainTextAndOnlyMatchesItself() throws Exception {
        auth.signUpRenter("ben_cruz", "Passw0rdX", new Renter(0, "Ben Cruz", "09181234567", null, null), Q,
                "x' OR 1=1 --");
        assertThrows(AuthException.class, () -> auth.resetPassword("ben_cruz", "wrong", "NewPassw0rd1"));
        auth.resetPassword("ben_cruz", "x' OR 1=1 --", "NewPassw0rd1");
        assertNotNull(auth.login("ben_cruz", "NewPassw0rd1"));
    }

    @Test
    void aFailedSignUpLeavesNoHalfMadeRenter() throws Exception {
        renter("ana_reyes");
        int before = db.getAllRenters().size();
        assertThrows(AuthException.class, () -> renter("ANA_REYES"));
        assertEquals(before, db.getAllRenters().size());
    }

    @Test
    void aBadRenterNameStopsTheSignUp() throws Exception {
        assertThrows(Exception.class, () -> auth.signUpRenter("zed_user", "Passw0rdX",
                new Renter(0, "   ", "09171234567", null, null), Q, "Rex"));
        assertNull(db.findAccountByUsername("zed_user"));
        assertTrue(db.getAllRenters().isEmpty());
    }

    @Test
    void usernameWithSpacesAroundItStillLogsIn() throws Exception {
        renter("ana_reyes");
        assertNotNull(auth.login("  ana_reyes  ", "Passw0rdX"));
    }

    @Test
    void thePasswordIsCaseSensitive() throws Exception {
        renter("ana_reyes");
        assertThrows(AuthException.class, () -> auth.login("ana_reyes", "passw0rdx"));
    }

    @Test
    void theLockoutOfOneUserDoesNotLockAnotherUser() throws Exception {
        renter("ana_reyes");
        renter("ben_cruz");
        for (int i = 0; i < 5; i++) {
            assertThrows(AuthException.class, () -> auth.login("ana_reyes", "wrongpass1"));
        }
        assertThrows(AuthException.class, () -> auth.login("ana_reyes", "Passw0rdX"));
        assertNotNull(auth.login("ben_cruz", "Passw0rdX"));
    }

    @Test
    void theLockoutEndsAfterThirtySeconds() throws Exception {
        renter("ana_reyes");
        for (int i = 0; i < 5; i++) {
            assertThrows(AuthException.class, () -> auth.login("ana_reyes", "wrongpass1"));
        }
        now.addAndGet(29_000);
        assertThrows(AuthException.class, () -> auth.login("ana_reyes", "Passw0rdX"));
        now.addAndGet(2_000);
        assertNotNull(auth.login("ana_reyes", "Passw0rdX"));
    }

    @Test
    void anUnknownUsernameAlsoGetsTheSameMessageAsAWrongPassword() throws Exception {
        renter("ana_reyes");
        String unknown = assertThrows(AuthException.class, () -> auth.login("nobody_here", "Passw0rdX")).getMessage();
        String wrong = assertThrows(AuthException.class, () -> auth.login("ana_reyes", "wrongpass1")).getMessage();
        assertEquals(unknown, wrong);
    }

    @Test
    void afterAPasswordChangeTheOldPasswordStopsWorking() throws Exception {
        Account a = renter("ana_reyes");
        auth.changePassword(a.getId(), "Passw0rdX", "BrandNew123");
        assertThrows(AuthException.class, () -> auth.login("ana_reyes", "Passw0rdX"));
        assertNotNull(auth.login("ana_reyes", "BrandNew123"));
    }

    @Test
    void changePasswordRefusesAWeakNewPasswordAndKeepsTheOldOne() throws Exception {
        Account a = renter("ana_reyes");
        assertThrows(AuthException.class, () -> auth.changePassword(a.getId(), "Passw0rdX", "short1"));
        assertNotNull(auth.login("ana_reyes", "Passw0rdX"));
    }

    @Test
    void changePasswordOfAnUnknownAccountIsRefused() {
        assertThrows(AuthException.class, () -> auth.changePassword(9999, "Passw0rdX", "BrandNew123"));
    }

    @Test
    void aLockedUserCannotGuessTheSecurityAnswerEitherUntilTheWaitIsOver() throws Exception {
        renter("ana_reyes");
        for (int i = 0; i < 5; i++) {
            assertThrows(AuthException.class, () -> auth.login("ana_reyes", "wrongpass1"));
        }
        assertThrows(AuthException.class, () -> auth.resetPassword("ana_reyes", "rex", "BrandNew123"));
        now.addAndGet(31_000);
        auth.resetPassword("ana_reyes", "rex", "BrandNew123");
        assertNotNull(auth.login("ana_reyes", "BrandNew123"));
    }

    @Test
    void theSecurityQuestionOfAnUnknownUserIsRefusedWithoutACrash() {
        assertThrows(AuthException.class, () -> auth.securityQuestionOf("nobody_here"));
        assertThrows(AuthException.class, () -> auth.securityQuestionOf(null));
    }

    @Test
    void theSecurityQuestionMustBeOneOfOurQuestions() {
        assertThrows(AuthException.class, () -> auth.signUpRenter("zed_user", "Passw0rdX",
                new Renter(0, "Zed", "09171234567", null, null), "What is my secret?", "Rex"));
    }

    @Test
    void everySecurityQuestionWorks() throws Exception {
        int n = 0;
        for (String q : AuthService.SECURITY_QUESTIONS) {
            String user = "user_" + (n++) + "abc";
            auth.signUpRenter(user, "Passw0rdX", new Renter(0, "Person " + "ABCDE".charAt(n - 1), "09171234567", null, null), q, "Answer");
            assertEquals(q, auth.securityQuestionOf(user));
        }
    }

    @Test
    void anOwnerCodeWithTheWrongCaseOrSpacesIsRefused() throws SQLException {
        for (String code : new String[] {"fleetrent-owner", "", "   ", null, "FLEETRENT", "FLEETRENT OWNER"}) {
            assertThrows(AuthException.class,
                    () -> auth.signUpOwner("owner01", "Passw0rdX", "Maria", Q, "Rex", code), String.valueOf(code));
        }
        assertNull(db.findAccountByUsername("owner01"));
    }

    @Test
    void aRenterAccountIsNeverAnAdmin() throws Exception {
        Account a = renter("ana_reyes");
        assertEquals(Role.RENTER, a.getRole());
        assertFalse(a.getRenterId() == null);
    }

    @Test
    void twoAccountsWithTheSamePasswordGetDifferentHashes() throws Exception {
        Account a = renter("ana_reyes");
        Account b = renter("ben_cruz");
        assertFalse(db.getAccountById(a.getId()).getPasswordHash().equals(db.getAccountById(b.getId()).getPasswordHash()));
    }
}
