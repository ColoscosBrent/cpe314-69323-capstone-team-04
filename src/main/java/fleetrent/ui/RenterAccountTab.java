package fleetrent.ui;

import fleetrent.controller.AuthController;
import fleetrent.controller.ControllerException;
import fleetrent.controller.RenterController;
import fleetrent.model.Account;
import fleetrent.model.Renter;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Renter, tab 3: the renter's own details and a way to change the password.
 */
public class RenterAccountTab extends Tab {
    private static final double INFO_HGAP = 14;
    private static final double INFO_VGAP = 8;
    private static final double PROFILE_PREF_WIDTH = 430;
    private static final double PROFILE_MIN_WIDTH = 430;
    private static final double STATUS_MAX_WIDTH = 340;
    private static final double PASSWORD_PREF_WIDTH = 420;
    private static final double PASSWORD_MIN_WIDTH = 420;
    private static final Insets CONTENT_PADDING = new Insets(16, 18, 16, 18);
    private static final double L_MAX_WIDTH = 300;
    /** Creates a renter account tab. */
    public RenterAccountTab(AuthController auth, RenterController renters, Account account) {
        super("My account");
        setClosable(false);

        Renter renter = null;
        try {
            renter = account.getRenterId() == null ? null
                    : renters.findRenter(account.getRenterId());
        } catch (ControllerException e) {
            // The page still opens. The details below stay empty.
        }
        GridPane info = new GridPane();
        info.setHgap(INFO_HGAP);
        info.setVgap(INFO_VGAP);
        UiHelper.addRow(info, 0, "Name", value(renter == null ? account.getDisplayName()
                : renter.getName()));
        UiHelper.addRow(info, 1, "Username", value(account.getUsername()));
        UiHelper.addRow(info, 2, "Contact", value(renter == null ? "" : renter.getContact()));
        UiHelper.addRow(info, 3, "Email", value(renter == null || renter.getEmail() == null ? "-"
                : renter.getEmail()));
        UiHelper.addRow(info, 4, "Address", value(renter == null || renter.getAddress() == null
                ? "-" : renter.getAddress()));
        UiHelper.addRow(info, 5, "Member since", value(account.getCreatedOn()
                .format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))));
        VBox profile = new VBox(Layout.GAP_WIDE, UiHelper.title("My details"), info);
        profile.getStyleClass().add("card");
        profile.setPrefWidth(PROFILE_PREF_WIDTH);
        profile.setMinWidth(PROFILE_MIN_WIDTH);

        PasswordInput current = new PasswordInput("Current password");
        PasswordInput next = new PasswordInput(
                "New password (8+ characters, a letter and a digit)");
        PasswordInput again = new PasswordInput("Type the new password again");
        Label status = new Label();
        status.getStyleClass().add("status-label");
        status.setWrapText(true);
        status.setMaxWidth(STATUS_MAX_WIDTH);
        Button change = new Button("Change password");
        change.getStyleClass().add("primary-button");
        change.setOnAction(e -> {
            boolean ok = true;
            if (current.text().isEmpty()) {
                current.showError("Type your current password.");
                ok = false;
            }
            String m = AuthController.checkPassword(next.text());
            if (m != null) {
                next.showError(m);
                ok = false;
            } else if (!next.text().equals(again.text())) {
                again.showError("The two passwords are not the same.");
                ok = false;
            }
            if (!ok) {
                UiHelper.setStatus(status, "Please fix the red messages first.", true);
                return;
            }
            try {
                auth.changePassword(account.getId(), current.text(), next.text());
                current.clear();
                next.clear();
                again.clear();
                UiHelper.setStatus(status, "Your password was changed.", false);
            } catch (ControllerException ex) {
                UiHelper.setStatus(status, ex.getMessage(), true);
            }
        });
        VBox password = new VBox(Layout.GAP_MEDIUM, UiHelper.title("Change password"),
                field("Current password",
                current),
                field("New password", next), field("Confirm new password", again), change, status);
        password.getStyleClass().add("card");
        password.setPrefWidth(PASSWORD_PREF_WIDTH);
        password.setMinWidth(PASSWORD_MIN_WIDTH);

        HBox content = new HBox(Layout.GAP_LARGE, profile, password);
        content.setPadding(CONTENT_PADDING);
        setContent(UiHelper.scroll(content));
    }

    private static Label value(Object text) {
        Label l = new Label(String.valueOf(text));
        l.getStyleClass().add("vehicle-details");
        l.setWrapText(true);
        l.setMaxWidth(L_MAX_WIDTH);
        return l;
    }

    private static VBox field(String label, PasswordInput input) {
        Label l = new Label(label);
        l.getStyleClass().add("field-label");
        return new VBox(Layout.GAP_HAIR, l, input.node());
    }
}
