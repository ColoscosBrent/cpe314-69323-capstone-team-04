package fleetrent.service;

import java.sql.SQLException;

/**
 * Thrown when the database failed in the middle of an import. Some rows may already be saved, so
 * the exception tells how many. The Import history keeps the real counts.
 */
public class ImportStoppedException extends SQLException {
    private static final long serialVersionUID = 1L;
    private final int saved;

    /**
     * Creates the exception.
     *
     * @param saved how many rentals were saved before the failure
     * @param cause the error the database reported
     */
    public ImportStoppedException(int saved, SQLException cause) {
        super("The import stopped after " + saved + " rentals were saved.", cause);
        this.saved = saved;
    }

    /** Returns how many rentals were saved before the failure. */
    public int getSaved() {
        return saved;
    }
}
