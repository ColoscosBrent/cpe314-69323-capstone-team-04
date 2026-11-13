package fleetrent.ui;

import fleetrent.controller.AppController;
import fleetrent.model.Account;
import fleetrent.model.Vehicle;

import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

// What the rental owner (ADMIN) sees after login: the header with the Sign out button and five
// tabs. It reloads the Vehicles, Rentals and Reservations tabs by itself when something changes in
// the database, for example when a renter reserves a car from the renter screen.
final class AdminView {
    private final VBox root = new VBox();
    private final SyncPoller poller;

    AdminView(AppController app, Account account, VehiclePictures pictures, Runnable signOut, Runnable newWindow) {
        VehicleTab vehicleTab = new VehicleTab(app.vehicles(), pictures);
        RentalTab rentalTab = new RentalTab(app.rentals(), pictures);
        ReservationTab reservationTab = new ReservationTab(app.reservations(), pictures);
        ImportTab importTab = new ImportTab(app.importer());
        DashboardTab dashboardTab = new DashboardTab(app.dashboard());

        TabPane tabs = new TabPane(vehicleTab, rentalTab, reservationTab, importTab, dashboardTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        Runnable refreshSelected = () -> {
            Tab selected = tabs.getSelectionModel().getSelectedItem();
            if (selected == vehicleTab) {
                vehicleTab.refresh();
            } else if (selected == rentalTab) {
                rentalTab.refresh();
            } else if (selected == reservationTab) {
                reservationTab.refresh();
            }
        };
        // Every time a tab is opened it reloads its data, so changes made in other tabs show up.
        tabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            refreshSelected.run();
            if (newTab == dashboardTab) {
                dashboardTab.refresh();
            }
        });
        poller = new SyncPoller(app.changes(), refreshSelected, 3);
        root.getChildren().addAll(Chrome.header("Vehicle rental and fleet management  |  Owner", account, signOut, newWindow), tabs);
        poller.start();
    }

    VBox root() {
        return root;
    }

    void dispose() {
        poller.stop();
    }
}
