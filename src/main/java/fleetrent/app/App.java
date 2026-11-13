package fleetrent.app;

import fleetrent.ui.AppWindow;
import fleetrent.controller.Background;

import javafx.application.Application;
import javafx.stage.Stage;

/**
 * The JavaFX application. Each window (AppWindow) shows the login screen first, then the owner's
 * screen (Vehicles, Rentals, Reservations, Import, Dashboard) or the renter's screen (Cars, My
 * bookings, My account). The colors and shapes come from src/main/resources/fleetrent.css.
 */
public class App extends Application {

    @Override
    public void start(Stage stage) {
        new AppWindow(stage).open();
    }

    @Override
    public void stop() {
        AppWindow.closeAll();
        Background.stop();
    }
}
