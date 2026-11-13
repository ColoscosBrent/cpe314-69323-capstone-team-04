package fleetrent.service;

import fleetrent.dao.Database;
import fleetrent.model.Account;
import fleetrent.model.Renter;
import fleetrent.model.Role;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Sign up, log in, "Forgot password" and "Change password" for the rental owner (ADMIN) and for
 * renters. Any rental shop owner can sign up; there is no owner code. It has no screen code, so it
 * can be unit tested. A person who types a wrong password or
 * answer 5 times in a row must wait 30 seconds.
 */
public class AuthService {
    private static final int USERNAME_MIN = 4;
    private static final int USERNAME_MAX = 20;
    private static final int PASSWORD_MIN = 8;
    private static final int PASSWORD_MAX = 64;
    private static final int ANSWER_MIN = 2;
    private static final int ANSWER_MAX = 60;
    private static final long MILLIS_PER_SECOND = 1000;
    public static final int MAX_FAILURES = 5;
    public static final long LOCK_MILLIS = 30_000;

    public static final List<String> SECURITY_QUESTIONS = List.of(
            "What was the name of your first pet?",
            "What is the name of the street you grew up on?",
            "What city were you born in?",
            "What was the name of your elementary school?",
            "What is your favorite food?");

    private final Database db;
    private final LongSupplier clock;
    private final Map<String, int[]> failures = new HashMap<>();
    private final Map<String, Long> lockedUntil = new HashMap<>();

    /** Creates an auth service. */
    public AuthService(Database db) {
        this(db, System::currentTimeMillis);
    }

    /**
     * The clock is a parameter so a test can move time forward.
     */
    public AuthService(Database db, LongSupplier clock) {
        this.db = db;
        this.clock = clock;
    }

    // ---------- rules that need no database ----------

    /**
     * Returns null when fine, otherwise a message.
     */
    public static String checkUsername(String username) {
        String u = username == null ? "" : username.trim();
        if (u.length() < USERNAME_MIN || u.length() > USERNAME_MAX) {
            return "The username needs 4 to 20 characters.";
        }
        if (!u.matches("[A-Za-z][A-Za-z0-9_.]*")) {
            return "Start with a letter. Use letters, digits, _ and . only.";
        }
        return null;
    }

    /** Checks a new password and returns a message when it is too weak, or null when it is fine. */
    public static String checkPassword(String password) {
        String p = password == null ? "" : password;
        if (p.length() < PASSWORD_MIN || p.length() > PASSWORD_MAX) {
            return "The password needs 8 to 64 characters.";
        }
        if (p.chars().anyMatch(Character::isWhitespace)) {
            return "The password cannot have spaces.";
        }
        if (p.chars().noneMatch(Character::isLetter) || p.chars().noneMatch(Character::isDigit)) {
            return "Use at least one letter and one digit.";
        }
        return null;
    }

    /**
     * Checks the answer to the security question and returns a message when it is not accepted, or
     * null.
     */
    public static String checkAnswer(String answer) {
        String a = normalizeAnswer(answer);
        if (a.length() < ANSWER_MIN || a.length() > ANSWER_MAX) {
            return "The answer needs 2 to 60 characters.";
        }
        return null;
    }

    // The answer is compared without capital letters and extra spaces.
    static String normalizeAnswer(String answer) {
        return answer == null ? "" : answer.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    // ---------- sign up ----------

    /**
     * Creates an owner account. Anyone who wants to run a rental shop can sign up as an owner. The
     * name is saved as "Last, First Middle" and the phone as "+63 9171234567".
     */
    public Account signUpOwner(String username, String password, String displayName, String phone,
            String question, String answer) throws AuthException, SQLException {
        String message = PersonName.check(displayName);
        if (message == null) {
            message = PhoneNumber.checkSaved(phone);
        }
        if (message != null) {
            throw new AuthException(message);
        }
        checkNewAccount(username, password, question, answer);
        Account account = buildAccount(username, Role.ADMIN, displayName.trim(), phone.trim(),
                password, question, answer, null);
        try {
            db.insertAccount(account);
        } catch (SQLException e) {
            throw translateDuplicate(e);
        }
        return account;
    }

    /**
     * Creates a renter account together with the renter record. The name, the phone, the email and
     * the address are all required.
     */
    public Account signUpRenter(String username, String password, Renter details, String question,
                                String answer) throws AuthException, SQLException {
        String message = PersonName.check(details.getName());
        if (message == null) {
            message = PhoneNumber.checkSaved(details.getContact());
        }
        if (message == null) {
            message = Validators.check(Validators.Kind.EMAIL, details.getEmail(), true);
        }
        if (message == null) {
            message = AddressRules.checkSaved(details.getAddress());
        }
        if (message != null) {
            throw new AuthException(message);
        }
        checkNewAccount(username, password, question, answer);
        Account account = buildAccount(username, Role.RENTER, details.getName().trim(),
                details.getContact().trim(), password, question, answer, null);
        try {
            db.insertRenterWithAccount(details, account);
        } catch (SQLException e) {
            throw translateDuplicate(e);
        }
        return db.getAccountById(account.getId());
    }

    private void checkNewAccount(String username, String password, String question, String answer)
            throws AuthException, SQLException {
        String message = checkUsername(username);
        if (message == null) {
            message = checkPassword(password);
        }
        if (message == null && (question == null || !SECURITY_QUESTIONS.contains(question))) {
            message = "Pick a security question.";
        }
        if (message == null) {
            message = checkAnswer(answer);
        }
        if (message != null) {
            throw new AuthException(message);
        }
        if (db.findAccountByUsername(username) != null) {
            throw new AuthException("That username is already taken. Try another one.");
        }
    }

    private Account buildAccount(String username, Role role, String displayName, String phone,
            String password, String question, String answer, Integer renterId) {
        String passSalt = PasswordHasher.newSalt();
        String answerSalt = PasswordHasher.newSalt();
        return new Account(0, username.trim(), role, displayName, phone,
                PasswordHasher.hash(password, passSalt), passSalt,
                question, PasswordHasher.hash(normalizeAnswer(answer), answerSalt), answerSalt,
                renterId, LocalDate.now());
    }

    private AuthException translateDuplicate(SQLException e) throws SQLException {
        String m = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        if (m.contains("unique")) {
            return new AuthException("That username is already taken. Try another one.");
        }
        throw e;
    }

    // ---------- log in ----------

    /** Checks the username and password and returns the account. */
    public Account login(String username, String password) throws AuthException, SQLException {
        return login(username, password, null);
    }

    /**
     * Checks the username and the password and, when a role is given, that the account has that
     * role. Each problem has its own message: the username, the password or the role. Five wrong
     * tries in a row lock the username for 30 seconds.
     */
    public Account login(String username, String password, Role expectedRole)
            throws AuthException, SQLException {
        String key = key(username);
        checkLock(key);
        Account account = db.findAccountByUsername(username);
        if (account == null) {
            registerFailure(key);
            throw new AuthException("The username is not correct. No account has that username.");
        }
        if (!PasswordHasher.matches(password, account.getPasswordSalt(),
                account.getPasswordHash())) {
            registerFailure(key);
            throw new AuthException("The password is not correct.");
        }
        if (expectedRole != null && account.getRole() != expectedRole) {
            throw new AuthException(expectedRole == Role.ADMIN
                    ? "This is a renter account. Choose Renter above to log in."
                    : "This is an owner account. Choose Owner above to log in.");
        }
        clearFailures(key);
        return account;
    }

    // ---------- forgot password ----------

    /**
     * The question of the account, so the person can answer it.
     */
    public String securityQuestionOf(String username) throws AuthException, SQLException {
        Account account = db.findAccountByUsername(username);
        if (account == null) {
            throw new AuthException("There is no account with that username.");
        }
        return account.getSecurityQuestion();
    }

    /** Sets a new password after the security answer is correct. */
    public void resetPassword(String username, String answer,
            String newPassword) throws AuthException, SQLException {
        String key = key(username);
        checkLock(key);
        Account account = db.findAccountByUsername(username);
        if (account == null) {
            throw new AuthException("There is no account with that username.");
        }
        if (!PasswordHasher.matches(normalizeAnswer(answer), account.getAnswerSalt(),
                account.getAnswerHash())) {
            registerFailure(key);
            throw new AuthException("That answer is not correct.");
        }
        String message = checkPassword(newPassword);
        if (message != null) {
            throw new AuthException(message);
        }
        clearFailures(key);
        setPassword(account, newPassword);
    }

    /** Changes the password of a signed-in account after the old password is checked. */
    public void changePassword(int accountId, String oldPassword,
            String newPassword) throws AuthException, SQLException {
        Account account = db.getAccountById(accountId);
        if (account == null) {
            throw new AuthException("That account no longer exists.");
        }
        String key = key(account.getUsername());
        checkLock(key);
        if (!PasswordHasher.matches(oldPassword, account.getPasswordSalt(),
                account.getPasswordHash())) {
            registerFailure(key);
            throw new AuthException("The current password is not correct.");
        }
        String message = checkPassword(newPassword);
        if (message != null) {
            throw new AuthException(message);
        }
        clearFailures(key);
        setPassword(account, newPassword);
    }

    private void setPassword(Account account, String newPassword) throws SQLException {
        String salt = PasswordHasher.newSalt();
        db.updateAccountPassword(account.getId(), PasswordHasher.hash(newPassword, salt), salt);
    }

    // ---------- too many wrong tries ----------

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private void checkLock(String key) throws AuthException {
        Long until = lockedUntil.get(key);
        if (until == null) {
            return;
        }
        long left = until - clock.getAsLong();
        if (left > 0) {
            throw new AuthException("Too many wrong tries. Wait " + ((left
                    + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND)
                    + " seconds and try again.");
        }
        lockedUntil.remove(key);
        failures.remove(key);
    }

    private void registerFailure(String key) {
        int[] count = failures.computeIfAbsent(key, k -> new int[1]);
        count[0]++;
        if (count[0] >= MAX_FAILURES) {
            lockedUntil.put(key, clock.getAsLong() + LOCK_MILLIS);
            count[0] = 0;
        }
    }

    private void clearFailures(String key) {
        failures.remove(key);
        lockedUntil.remove(key);
    }
}
