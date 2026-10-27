package fleetrent;

import javafx.application.Application;

// Start the app from this class, not from App. Starting a JavaFX Application class
// directly often gives "JavaFX runtime components are missing".
public class Main {
    public static void main(String[] args) {
        Application.launch(App.class, args);
    }
}
