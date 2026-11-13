package fleetrent.app;


import java.util.logging.Logger;
import javafx.application.Application;

/**
 * Start the app from this class, not from App. Starting a JavaFX Application class directly often
 * gives "JavaFX runtime components are missing".
 */
public class Main {
    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    /**
     * Kept from the Maven setup, because MainTest checks it.
     */
    public static String startupMessage() {
        return "FleetRent starting...";
    }

    public static void main(String[] args) {
        LOG.info(startupMessage());
        // Use the proxy settings of the computer (school or office networks), if there are any.
        System.setProperty("java.net.useSystemProxies", "true");
        Application.launch(App.class, args);
    }
}
