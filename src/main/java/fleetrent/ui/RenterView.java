package fleetrent.ui;

import fleetrent.controller.AppController;
import fleetrent.controller.RenterController;
import fleetrent.model.Account;
import fleetrent.model.Renter;
import fleetrent.model.Vehicle;

import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

// What a renter sees after login: the header with Sign out and three tabs (Cars, My bookings,
// My account). Nothing of the owner's screen is here: no other renters, no plates, no mileage,
// no money totals. It reloads by itself when the owner changes something.
final class RenterView {
    private final VBox root = new VBox();
    private final SyncPoller poller;

    RenterView(AppController app, Account account, VehiclePictures pictures, Runnable signOut,
               Runnable newWindow) {
        RenterController booking = app.renter();
        int renterId = account.getRenterId();
        RenterBookingsTab[] bookings = new RenterBookingsTab[1];
        RenterBrowseTab browse = new RenterBrowseTab(booking, pictures, renterId, () -> bookings[0].refresh());
        bookings[0] = new RenterBookingsTab(booking, pictures, renterId, browse::refresh);
        RenterAccountTab accountTab = new RenterAccountTab(app.auth(), booking, account);

        TabPane tabs = new TabPane(browse, bookings[0], accountTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        Runnable refreshSelected = () -> {
            Tab selected = tabs.getSelectionModel().getSelectedItem();
            if (selected == browse) {
                browse.refresh();
            } else if (selected == bookings[0]) {
                bookings[0].refresh();
            }
        };
        tabs.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> refreshSelected.run());
        poller = new SyncPoller(app.changes(), () -> {
            browse.refresh();
            bookings[0].refresh();
        }, 3);
        root.getChildren().addAll(Chrome.header("Vehicle rental  |  Renter", account, signOut, newWindow), tabs);
        poller.start();
    }

    VBox root() {
        return root;
    }

    void dispose() {
        poller.stop();
    }
}
