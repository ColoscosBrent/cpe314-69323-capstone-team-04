package fleetrent.controller;

/**
 * A rule of the app was broken by what the user asked for: a wrong password, a car that is already
 * booked, a plate that is already in the fleet. The message says what to change and can be shown
 * as it is.
 */
public class RuleException extends ControllerException {
    private static final long serialVersionUID = 1L;

    /** Creates an exception with a message for the user. */
    public RuleException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Creates an exception with a message for the user. */
    public RuleException(String message) {
        super(message, null);
    }
}
