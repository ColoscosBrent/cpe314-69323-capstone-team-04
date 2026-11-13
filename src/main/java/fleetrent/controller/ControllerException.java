package fleetrent.controller;

import fleetrent.service.ErrorMessages;

import java.sql.SQLException;

/**
 * A problem that a controller could not fix: the database or a web service failed. The message is
 * written for the user and can be shown as it is. The screens never see SQLException or
 * IOException, only this class.
 */
public class ControllerException extends Exception {
    private static final long serialVersionUID = 1L;

    /** Creates an exception with a message for the user. */
    public ControllerException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Creates an exception for a failed database action, such as "saving the vehicle". */
    public static ControllerException database(String action, SQLException cause) {
        return new ControllerException(ErrorMessages.forDatabase(action, cause), cause);
    }

    /** Creates an exception for a failed call to a web service, such as "NHTSA". */
    public static ControllerException web(String service, Throwable cause) {
        return new ControllerException(ErrorMessages.forApi(service, cause), cause);
    }
}
