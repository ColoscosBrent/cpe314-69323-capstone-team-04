package fleetrent;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

// The header bar that the login screen, the owner screen and the renter screen all share, so the
// whole app looks like one program: the same title, the same little road of cars, the same colors.
final class Chrome {
    private Chrome() {
    }

    static String roleText(Role role) {
        return role == Role.ADMIN ? "Rental owner" : "Renter";
    }

    // account is null on the login screen. signOut and newWindow may be null to hide the button.
    static HBox header(String subtitleText, Account account, Runnable signOut, Runnable newWindow) {
        Label title = new Label("FleetRent");
        title.getStyleClass().add("app-title");
        // The title never gets cut to "Fleet...", even in a narrow window.
        title.setMinWidth(Region.USE_PREF_SIZE);
        Label subtitle = new Label(subtitleText);
        subtitle.getStyleClass().add("app-subtitle");
        VBox titles = new VBox(2, title, subtitle);
        HBox road = new HBox(6, CarGraphic.create("SEDAN", "Purple", 90),
                CarGraphic.create("SUV", "White", 96), CarGraphic.create("MOTORCYCLE", "Orange", 78));
        road.getStyleClass().add("road");
        road.setAlignment(Pos.BOTTOM_CENTER);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(14, titles, spacer, road);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("header");
        // The little road of cars is decoration: it hides in a narrow window so the buttons keep
        // their room.
        road.visibleProperty().bind(header.widthProperty().greaterThan(1060));
        road.managedProperty().bind(road.visibleProperty());
        if (newWindow != null) {
            Button another = new Button("New window");
            another.getStyleClass().add("ghost-button");
            another.setMinWidth(Region.USE_PREF_SIZE);
            another.setOnAction(e -> newWindow.run());
            header.getChildren().add(another);
        }
        if (account != null) {
            Label name = new Label(account.getDisplayName());
            name.getStyleClass().add("user-name");
            name.setMaxWidth(190);
            Label role = new Label(roleText(account.getRole()));
            role.getStyleClass().addAll("badge", account.getRole() == Role.ADMIN ? "role-badge-admin" : "role-badge-renter");
            role.setMinWidth(Region.USE_PREF_SIZE);
            Button out = new Button("Sign out");
            out.getStyleClass().add("secondary-button");
            out.setMinWidth(Region.USE_PREF_SIZE);
            out.setOnAction(e -> {
                if (signOut != null) {
                    signOut.run();
                }
            });
            HBox chip = new HBox(10, new VBox(3, name, role), out);
            chip.setAlignment(Pos.CENTER_LEFT);
            chip.getStyleClass().add("user-chip");
            header.getChildren().add(chip);
        }
        return header;
    }
}
