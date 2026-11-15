package fleetrent.ui;

import fleetrent.model.Account;
import fleetrent.model.Rental;
import fleetrent.model.Renter;
import fleetrent.model.Role;

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
    private static final double NAME_MAX_WIDTH = 190;
    private static final double ROAD_MIN_HEADER_WIDTH = 1060;
    private static final double ROAD_SEDAN_WIDTH = 90;
    private static final double ROAD_SUV_WIDTH = 96;
    private static final double ROAD_MOTORCYCLE_WIDTH = 78;
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
        VBox titles = new VBox(Layout.GAP_MINI, title, subtitle);
        HBox road = new HBox(Layout.GAP_SMALL,
                CarGraphic.create("SEDAN", "Purple", ROAD_SEDAN_WIDTH),
                CarGraphic.create("SUV", "White", ROAD_SUV_WIDTH),
                CarGraphic.create("MOTORCYCLE", "Orange", ROAD_MOTORCYCLE_WIDTH));
        road.getStyleClass().add("road");
        road.setAlignment(Pos.BOTTOM_CENTER);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(Layout.GAP_WIDER, titles, spacer, road);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("header");
        // The little road of cars is decoration. It may shrink to nothing and is clipped, so it
        // never widens the header, and it hides in a narrow window so the buttons keep their room.
        road.setMinWidth(0);
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.widthProperty().bind(road.widthProperty());
        clip.heightProperty().bind(road.heightProperty());
        road.setClip(clip);
        road.visibleProperty().bind(header.widthProperty().greaterThan(ROAD_MIN_HEADER_WIDTH));
        if (newWindow != null) {
            Button another = new Button("New window");
            another.getStyleClass().add("ghost-button");
            another.setMinWidth(Region.USE_PREF_SIZE);
            another.setOnAction(e -> newWindow.run());
            header.getChildren().add(another);
        }
        if (account != null) {
            header.getChildren().add(userChip(account, signOut));
        }
        return header;
    }

    // The name, the role badge and the Sign out button of the signed-in account.
    private static HBox userChip(Account account, Runnable signOut) {
        Label name = new Label(account.getDisplayName());
        name.getStyleClass().add("user-name");
        name.setMaxWidth(NAME_MAX_WIDTH);
        Label role = new Label(roleText(account.getRole()));
        role.getStyleClass().addAll("badge", account.getRole() == Role.ADMIN
                ? "role-badge-admin" : "role-badge-renter");
        role.setMinWidth(Region.USE_PREF_SIZE);
        Button out = new Button("Sign out");
        out.getStyleClass().add("secondary-button");
        out.setMinWidth(Region.USE_PREF_SIZE);
        out.setOnAction(e -> {
            if (signOut != null) {
                signOut.run();
            }
        });
        HBox chip = new HBox(Layout.GAP_MEDIUM, new VBox(Layout.GAP_HAIR, name, role), out);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.getStyleClass().add("user-chip");
        return chip;
    }
}
