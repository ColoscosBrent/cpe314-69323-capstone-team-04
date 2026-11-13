package fleetrent.service;


/**
 * A sign-up, login or password problem with a message that can be shown to the user as it is.
 */
public class AuthException extends Exception {
    private static final long serialVersionUID = 1L;

    /** Creates an auth exception. */
    public AuthException(String message) {
        super(message);
    }
}
