package fleetrent.controller;

import fleetrent.service.AuthException;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Runs one piece of model work for a controller and turns every exception into a
 * ControllerException with a message for the user. This is the only place where the controllers
 * translate SQLException, IOException and the rule exceptions of the services.
 */
final class Guard {
    private static final String NOT_VALID = "A value is not valid.";

    /** Work that returns a value. */
    @FunctionalInterface
    interface Work<T> {
        T run() throws AuthException, SQLException, IOException, InterruptedException;
    }

    /** Work that returns nothing. */
    @FunctionalInterface
    interface Action {
        void run() throws AuthException, SQLException, IOException, InterruptedException;
    }

    private Guard() {
    }

    /** Runs the work. The action is used in the message when the database fails, e.g. "saving". */
    static <T> T database(String action, Work<T> work) throws ControllerException {
        try {
            return work.run();
        } catch (AuthException | IllegalArgumentException | IllegalStateException e) {
            throw rule(e);
        } catch (SQLException e) {
            throw ControllerException.database(action, e);
        } catch (IOException e) {
            throw new ControllerException("A file problem stopped " + action + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ControllerException("The work was stopped before " + action + " finished.", e);
        }
    }

    /** Runs work that returns nothing. */
    static void database(String action, Action work) throws ControllerException {
        database(action, () -> {
            work.run();
            return null;
        });
    }

    /** Runs a call to a web service. The service name is used in the message, e.g. "NHTSA". */
    static <T> T web(String service, Work<T> work) throws ControllerException {
        try {
            return work.run();
        } catch (AuthException | IllegalArgumentException | IllegalStateException e) {
            throw rule(e);
        } catch (SQLException | IOException e) {
            throw ControllerException.web(service, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ControllerException.web(service, e);
        }
    }

    private static RuleException rule(Exception e) {
        return new RuleException(e.getMessage() == null ? NOT_VALID : e.getMessage(), e);
    }
}
