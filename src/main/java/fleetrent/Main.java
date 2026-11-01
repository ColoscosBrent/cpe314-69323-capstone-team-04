package fleetrent;

import javafx.application.Application;

// Start the app from this class, not from App. Starting a JavaFX Application class
// directly often gives "JavaFX runtime components are missing".
public class Main {

    // Kept from the Maven setup, because MainTest checks it.
    public static String startupMessage() {
        return "FleetRent starting...";
    }

    public static void main(String[] args) {
        System.out.println(startupMessage());
        Application.launch(App.class, args);
    }
}
