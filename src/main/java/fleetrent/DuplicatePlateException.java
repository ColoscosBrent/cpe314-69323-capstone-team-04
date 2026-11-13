package fleetrent;

import java.sql.SQLException;

/**
 * Thrown by {@link Database} when a second vehicle in the fleet would get a plate number that
 * another vehicle in the fleet already has. The message is written for the person using the app.
 */
public class DuplicatePlateException extends SQLException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param plate the plate number that is already used
     * @param cause the error the database reported
     */
    public DuplicatePlateException(String plate, SQLException cause) {
        super("A vehicle in the fleet already has the plate number " + plate + ".", cause);
    }
}
