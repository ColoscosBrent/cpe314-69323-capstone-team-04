package fleetrent;

import java.sql.SQLException;
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
    /** Creates a renter account tab. */
    public RenterAccountTab(Database db, AuthService auth, Account account) {
        super("My account");
        setClosable(false);

        Renter renter = null;
        try {
            renter = account.getRenterId() == null ? null : db.getRenterById(account.getRenterId());
        } catch (SQLException e) {
            // The page still opens. The details below stay empty.
        }
        GridPane info = new GridPane();
        info.setHgap(14);
        info.setVgap(8);
        UiHelper.addRow(info, 0, "Name", value(renter == null ? account.getDisplayName() : renter.getName()));
        UiHelper.addRow(info, 1, "Username", value(account.getUsername()));
        UiHelper.addRow(info, 2, "Contact", value(renter == null ? "" : renter.getContact()));
        UiHelper.addRow(info, 3, "Email", value(renter == null || renter.getEmail() == null ? "-" : renter.getEmail()));
        UiHelper.addRow(info, 4, "Address", value(renter == null || renter.getAddress() == null ? "-" : renter.getAddress()));
        UiHelper.addRow(info, 5, "Member since", value(account.getCreatedOn()
                .format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))));
        VBox profile = new VBox(12, UiHelper.title("My details"), info);
        profile.getStyleClass().add("card");
        profile.setPrefWidth(430);
        profile.setMinWidth(430);

        PasswordInput current = new PasswordInput("Current password");
        PasswordInput next = new PasswordInput("New password (8+ characters, a letter and a digit)");
        PasswordInput again = new PasswordInput("Type the new password again");
        Label status = new Label();
        status.getStyleClass().add("status-label");
        status.setWrapText(true);
        status.setMaxWidth(340);
        Button change = new Button("Change password");
        change.getStyleClass().add("primary-button");
        change.setOnAction(e -> {
            boolean ok = true;
            if (current.text().isEmpty()) {
                current.showError("Type your current password.");
                ok = false;
            }
            String m = AuthService.checkPassword(next.text());
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
            } catch (AuthException ex) {
                UiHelper.setStatus(status, ex.getMessage(), true);
            } catch (SQLException ex) {
                UiHelper.setStatus(status, ErrorMessages.forDatabase("changing the password", ex), true);
            }
        });
        VBox password = new VBox(10, UiHelper.title("Change password"), field("Current password", current),
                field("New password", next), field("Confirm new password", again), change, status);
        password.getStyleClass().add("card");
        password.setPrefWidth(420);
        password.setMinWidth(420);

        HBox content = new HBox(16, profile, password);
        content.setPadding(new Insets(16, 18, 16, 18));
        setContent(UiHelper.scroll(content));
    }

    private static Label value(Object text) {
        Label l = new Label(String.valueOf(text));
        l.getStyleClass().add("vehicle-details");
        l.setWrapText(true);
        l.setMaxWidth(300);
        return l;
    }

    private static VBox field(String label, PasswordInput input) {
        Label l = new Label(label);
        l.getStyleClass().add("field-label");
        return new VBox(3, l, input.node());
    }
}
