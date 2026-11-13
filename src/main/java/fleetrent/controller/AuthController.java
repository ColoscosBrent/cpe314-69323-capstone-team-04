package fleetrent.controller;

import fleetrent.model.Account;
import fleetrent.model.Renter;
import fleetrent.model.Role;
import fleetrent.service.AuthService;

import java.util.List;

/**
 * What the login screen and the "My account" tab can ask for: log in, sign up, forgot password and
 * change password. It only passes the work to AuthService and turns its problems into messages
 * the screen can show.
 */
public class AuthController {
    private final AuthService auth;
    private final Session session;

    /** Creates the controller. */
    public AuthController(AuthService auth, Session session) {
        this.auth = auth;
        this.session = session;
    }

    /** Returns who is signed in on this window. */
    public Session session() {
        return session;
    }

    /** Signs out: the window no longer has an account. */
    public void signOut() {
        session.end();
    }

    /** The security questions a new account can choose from. */
    public static List<String> securityQuestions() {
        return AuthService.SECURITY_QUESTIONS;
    }

    /** Returns a message when the username cannot be used, otherwise null. */
    public static String checkUsername(String username) {
        return AuthService.checkUsername(username);
    }

    /** Returns a message when the password is too weak, otherwise null. */
    public static String checkPassword(String password) {
        return AuthService.checkPassword(password);
    }

    /** Returns a message when the security answer cannot be used, otherwise null. */
    public static String checkAnswer(String answer) {
        return AuthService.checkAnswer(answer);
    }

    /** Checks the username and password and returns the account. */
    public Account login(String username, String password) throws ControllerException {
        return login(username, password, null);
    }

    /**
     * Checks the username and password and that the account is an owner (ADMIN) or a renter
     * account, as chosen on the login screen. Pass null to accept both.
     */
    public Account login(String username, String password, Role role) throws ControllerException {
        Account account = Guard.database("logging in",
                () -> auth.login(username, password, role));
        session.start(account);
        return account;
    }

    /**
     * Creates an owner account. Any rental shop owner can sign up (there is no owner code). The
     * name
     * is "Last, First Middle" and the phone is like "+63 9171234567".
     */
    public Account signUpOwner(String username, String password, String displayName, String phone,
            String question, String answer) throws ControllerException {
        Account account = Guard.database("creating the account",
                () -> auth.signUpOwner(username, password, displayName, phone, question, answer));
        session.start(account);
        return account;
    }

    /** Creates a renter account together with the renter's details. */
    public Account signUpRenter(String username, String password, Renter details, String question,
            String answer) throws ControllerException {
        Account account = Guard.database("creating the account",
                () -> auth.signUpRenter(username, password, details, question, answer));
        session.start(account);
        return account;
    }

    /** Returns the security question of an account (first step of "forgot password"). */
    public String securityQuestionOf(String username) throws ControllerException {
        return Guard.database("finding the account", () -> auth.securityQuestionOf(username));
    }

    /** Sets a new password when the security answer is right. */
    public void resetPassword(String username, String answer, String newPassword)
            throws ControllerException {
        Guard.database("changing the password", () -> auth.resetPassword(username, answer,
                newPassword));
    }

    /** Changes the password of a logged-in account when the old password is right. */
    public void changePassword(int accountId, String oldPassword, String newPassword)
            throws ControllerException {
        Guard.database("changing the password",
                () -> auth.changePassword(accountId, oldPassword, newPassword));
    }
}
