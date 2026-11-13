package fleetrent.service;

import fleetrent.dao.Database;
import fleetrent.dao.DuplicatePlateException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.sql.SQLException;
import java.util.Locale;
import javax.net.ssl.SSLException;

/**
 * Turns technical errors into plain messages for the screen. One place, so every tab says the same
 * thing for the same problem. It has no screen code, so it can be unit tested.
 */
public final class ErrorMessages {
    private ErrorMessages() {
    }

    /**
     * service is a short name such as "NHTSA" or "Wikipedia".
     */
    public static String forApi(String service, Throwable error) {
        if (error == null) {
            return "Could not get data from " + service + ".";
        }
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof UnknownHostException) {
                return "No internet connection: the computer cannot find " + service
                        + ". Check the Wi-Fi or cable and try again.";
            }
            if (t instanceof HttpTimeoutException || t instanceof HttpConnectTimeoutException
                    || t instanceof SocketTimeoutException) {
                return service + " took too long to answer. Try again in a moment.";
            }
            if (t instanceof ConnectException || t instanceof NoRouteToHostException) {
                return "Could not connect to " + service
                        + ". Check the internet, the firewall or the proxy and try again.";
            }
            if (t instanceof SSLException) {
                return "The secure connection to " + service
                        + " failed. Check that the date and time of this computer are correct.";
            }
            if (t instanceof InterruptedException) {
                return "The request to " + service + " was cancelled.";
            }
        }
        String message = error.getMessage() == null ? "" : error.getMessage();
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("tunnel failed") || lower.contains("proxy")) {
            return "A proxy or firewall blocked the connection to " + service
                    + ". Try another network.";
        }
        if (lower.contains("http 5")) {
            return service + " is having problems right now (" + message + "). Try again later.";
        }
        if (lower.contains("http 4")) {
            return service + " refused the request (" + message + ").";
        }
        if (lower.contains("could not read") || lower.contains("not valid json")) {
            return service + " sent an answer the app could not read. Try again later.";
        }
        if (error instanceof IOException || error instanceof RuntimeException) {
            return "Could not get data from " + service + (message.isEmpty() ? "." : ": "
                    + message);
        }
        return "Could not get data from " + service + ".";
    }

    /**
     * action is a short phrase such as "saving the vehicle".
     */
    public static String forDatabase(String action, SQLException error) {
        if (error instanceof DuplicatePlateException) {
            return error.getMessage();
        }
        String message = error == null || error.getMessage() == null ? "" : error.getMessage();
        String lower = message.toLowerCase(Locale.ROOT);
        String start = "Database error while " + action + ". ";
        if (lower.contains("locked") || lower.contains("busy")) {
            return start
                    + "The database file fleetrent.db is busy or locked. Close other programs "
                    + "that use it "
                    + "(for example DB Browser or a second copy of FleetRent) and try again.";
        }
        if (lower.contains("foreign key")) {
            return start + "The data points to a renter or vehicle that does not exist.";
        }
        if (lower.contains("unique")) {
            return start + "That value already exists.";
        }
        if (lower.contains("constraint")) {
            return start + "The data does not follow the database rules (" + message + ").";
        }
        if (lower.contains("readonly") || lower.contains("read-only")
                || lower.contains("permission")) {
            return start + "The file fleetrent.db cannot be written. Check the folder permissions.";
        }
        if (lower.contains("not a database") || lower.contains("malformed")
                || lower.contains("corrupt")) {
            return start
                    + "The file fleetrent.db is damaged. Close the app, rename the file and start "
                    + "again.";
        }
        if (lower.contains("unable to open") || lower.contains("cannot open")) {
            return start
                    + "The file fleetrent.db cannot be opened. Check that the folder exists and "
                    + "can be written.";
        }
        return start + (message.isEmpty() ? "Please try again." : "Details: " + message);
    }
}
