package fleetrent.ui;

import fleetrent.controller.AuthController;
import fleetrent.controller.ControllerException;
import fleetrent.controller.RuleException;
import fleetrent.model.Account;
import fleetrent.model.Renter;
import fleetrent.model.Role;
import fleetrent.service.Validators;

import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

// The first screen: Log in (as an owner or as a renter), Sign up and Forgot password. It sits on a
// photo, with a road of moving cars under the card. Nothing else of the app can be seen until a
// login works.
final class AuthScreen {
    private static final double SUB_MAX_WIDTH = 460;
    private static final Insets HERO_PADDING = new Insets(4, 0, 2, 0);
    private static final double CARD_MAX_WIDTH = 520;
    private static final double CARD_MIN_WIDTH = 420;
    // Small gaps and padding keep the login card and its button inside a 860 x 560 window, also
    // with the wider fonts of Windows.
    private static final Insets CENTER_PADDING = new Insets(4, 18, 4, 18);
    private static final double SECTION_GAP = 8;
    private static final double DELAY_MILLIS = 220;
    private static final double FIELD_PREF_WIDTH = 300;
    private static final double ERROR_MAX_WIDTH = 340;
    private static final double L_MAX_WIDTH = 440;
    private static final double QUESTION_PREF_WIDTH = 300;
    private static final double QUESTION_MAX_WIDTH = 440;
    private final AuthController auth;
    private final Consumer<Account> onLogin;
    private final Runnable onNewWindow;
    private final VBox root = new VBox();
    private final VBox cardContent = new VBox(Layout.GAP_WIDE);
    private final LoadingRoad road = new LoadingRoad();
    private Role chosenRole = Role.RENTER;

    AuthScreen(AuthController auth, Consumer<Account> onLogin, Runnable onNewWindow) {
        this.auth = auth;
        this.onLogin = onLogin;
        this.onNewWindow = onNewWindow;

        Label hello = new Label("Rent the right ride.");
        hello.getStyleClass().add("hero-title");
        Label sub = new Label("Log in to see the cars, the prices and what is free on your dates.");
        sub.getStyleClass().add("hero-sub");
        sub.setWrapText(true);
        sub.setMaxWidth(SUB_MAX_WIDTH);
        VBox hero = new VBox(Layout.GAP_SMALL, hello, sub);
        hero.setAlignment(Pos.CENTER);
        hero.setPadding(HERO_PADDING);

        VBox card = new VBox(Layout.GAP_WIDER, cardContent);
        card.getStyleClass().add("auth-card");
        card.setMaxWidth(CARD_MAX_WIDTH);
        card.setMinWidth(CARD_MIN_WIDTH);
        VBox center = new VBox(SECTION_GAP, hero, card, road.node());
        center.setAlignment(Pos.TOP_CENTER);
        center.setPadding(CENTER_PADDING);

        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(center);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().addAll("page-scroll", "auth-scroll");
        scroll.setFocusTraversable(false);
        Pane photo = new Pane();
        photo.getStyleClass().add("auth-photo");
        Pane shade = new Pane();
        shade.getStyleClass().add("auth-shade");
        StackPane stage = new StackPane(photo, shade, scroll);
        VBox.setVgrow(stage, Priority.ALWAYS);
        root.getChildren().addAll(Chrome.header("Vehicle rental and fleet management", null, null,
                onNewWindow), stage);
        showLogin(null, null);
    }

    Node root() {
        return root;
    }

    // Stops the moving cars when the screen is left.
    void dispose() {
        road.stop();
    }

    private void show(Node panel) {
        cardContent.getChildren().setAll(panel);
        javafx.animation.FadeTransition fade =
                new javafx.animation.FadeTransition(Duration.millis(DELAY_MILLIS), panel);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    // ---------- small helpers ----------

    // A one-line input with its own red message under it.
    private static final class Row {
        final TextField field = new TextField();
        final Label error = UiHelper.errorLabel();
        final VBox node = new VBox(Layout.GAP_HAIR, field, error);

        Row(String hint) {
            field.setPromptText(hint);
            field.setPrefWidth(FIELD_PREF_WIDTH);
            error.setMaxWidth(ERROR_MAX_WIDTH);
            field.textProperty().addListener((obs, o, n) -> UiHelper.setError(error, null));
        }

        String text() {
            return field.getText() == null ? "" : field.getText().trim();
        }
    }

    private static VBox labeled(String label, Node control) {
        Label l = new Label(label);
        l.getStyleClass().add("field-label");
        return new VBox(Layout.GAP_HAIR, l, control);
    }

    private static Label status() {
        Label l = new Label();
        l.getStyleClass().add("status-label");
        l.setWrapText(true);
        l.setMaxWidth(L_MAX_WIDTH);
        l.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        return l;
    }

    private static Label hintLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("hint-label");
        l.setWrapText(true);
        l.setMaxWidth(L_MAX_WIDTH);
        return l;
    }

    private static Button link(String text, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().add("link-button");
        b.setOnAction(e -> action.run());
        b.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        return b;
    }

    private static Button primary(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("primary-button");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setDefaultButton(false);
        return b;
    }

    // ---------- log in ----------

    // The two buttons "Owner" and "Renter" at the top of the login card.
    private HBox roleChoice() {
        ToggleGroup group = new ToggleGroup();
        ToggleButton owner = new ToggleButton("Log in as Owner");
        ToggleButton renter = new ToggleButton("Log in as Renter");
        for (ToggleButton b : new ToggleButton[] {renter, owner}) {
            b.setToggleGroup(group);
            b.getStyleClass().add("segment");
            b.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        }
        (chosenRole == Role.ADMIN ? owner : renter).setSelected(true);
        group.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null && old != null) {
                old.setSelected(true);
            } else if (now != null) {
                chosenRole = now == owner ? Role.ADMIN : Role.RENTER;
            }
        });
        HBox choice = new HBox(Layout.GAP_NORMAL, renter, owner);
        choice.setAlignment(Pos.CENTER);
        return choice;
    }

    void showLogin(String message, String username) {
        Row user = new Row("Username");
        if (username != null) {
            user.field.setText(username);
        }
        PasswordInput pass = new PasswordInput("Password");
        Label status = status();
        if (message != null) {
            UiHelper.setStatus(status, message, false);
        }
        Button go = primary("Log in");
        Runnable doLogin = () -> logIn(user, pass, status);
        go.setOnAction(e -> doLogin.run());
        user.field.setOnAction(e -> pass.requestFocus());
        pass.onEnter(doLogin);
        HBox links = new HBox(Layout.GAP_WIDER, link("Forgot password?", () ->
                showForgot(user.text())),
                link("Create an account", () -> showSignUp()));
        links.setAlignment(Pos.CENTER);
        show(new VBox(Layout.GAP_WIDE, UiHelper.title("Log in"), roleChoice(),
                labeled("Username", user.node), labeled("Password", pass.node()), status, go,
                links));
        javafx.application.Platform.runLater(user.field::requestFocus);
    }

    // Checks the two boxes and logs in. The message says what is wrong: the username, the password
    // or the role that was chosen.
    private void logIn(Row user, PasswordInput pass, Label status) {
        if (user.text().isEmpty()) {
            UiHelper.setError(user.error, "Type your username.");
            return;
        }
        if (pass.text().isEmpty()) {
            pass.showError("Type your password.");
            return;
        }
        try {
            Account account = auth.login(user.text(), pass.text(), chosenRole);
            pass.clear();
            onLogin.accept(account);
        } catch (ControllerException e) {
            String text = e.getMessage();
            if (text.startsWith("The username is not correct")) {
                UiHelper.setError(user.error, text);
            } else if (text.startsWith("The password is not correct")) {
                pass.showError(text);
            }
            UiHelper.setStatus(status, text, true);
        }
    }

    // ---------- sign up ----------

    // The boxes of the sign-up form. The name and the phone are for owners and renters; the email
    // and the address are only for renters.
    private final class SignUpForm {
        final ToggleButton renterTab = new ToggleButton("I want to rent");
        final ToggleButton ownerTab = new ToggleButton("I own the rental shop");
        final Row user = new Row("4 to 20 letters, digits, _ or .");
        final PasswordInput pass = new PasswordInput("8+ characters, a letter and a digit");
        final PasswordInput confirm = new PasswordInput("Type the password again");
        final ChoiceBox<String> question = new ChoiceBox<>();
        final Label questionError = UiHelper.errorLabel();
        final Row answer = new Row("Your answer");
        final NameFields name = new NameFields();
        final PhoneField phone = new PhoneField();
        final ValidatedField email = new ValidatedField(Validators.Kind.EMAIL, "ana@example.com",
                true);
        final AddressFields address = new AddressFields();
        final VBox renterOnly = new VBox(Layout.GAP_MEDIUM, labeled("Email", email.node()),
                address.node());
        final Label status = status();

        SignUpForm() {
            ToggleGroup group = new ToggleGroup();
            for (ToggleButton b : new ToggleButton[] {renterTab, ownerTab}) {
                b.setToggleGroup(group);
                b.getStyleClass().add("segment");
                b.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            }
            renterTab.setSelected(true);
            // A segment cannot be switched off by clicking it again.
            group.selectedToggleProperty().addListener((obs, old, now) -> {
                if (now == null && old != null) {
                    old.setSelected(true);
                }
            });
            ownerTab.selectedProperty().addListener((obs, was, now) -> {
                renterOnly.setManaged(!now);
                renterOnly.setVisible(!now);
            });
            question.getItems().setAll(AuthController.securityQuestions());
            question.setPrefWidth(QUESTION_PREF_WIDTH);
            question.valueProperty().addListener((obs, o, n) ->
                    UiHelper.setError(questionError, null));
        }

        // Checks every box and shows a red message under each one that is wrong.
        boolean validate() {
            boolean owner = ownerTab.isSelected();
            boolean ok = true;
            String m = AuthController.checkUsername(user.text());
            if (m != null) {
                UiHelper.setError(user.error, m);
                ok = false;
            }
            m = AuthController.checkPassword(pass.text());
            if (m != null) {
                pass.showError(m);
                ok = false;
            } else if (!pass.text().equals(confirm.text())) {
                confirm.showError("The two passwords are not the same.");
                ok = false;
            }
            ok = name.validate() && ok;
            ok = phone.validate() && ok;
            if (!owner) {
                ok = email.validate() && ok;
                ok = address.validate() && ok;
            }
            if (question.getValue() == null) {
                UiHelper.setError(questionError, "Pick a question.");
                ok = false;
            }
            m = AuthController.checkAnswer(answer.text());
            if (m != null) {
                UiHelper.setError(answer.error, m);
                ok = false;
            }
            return ok;
        }

        VBox node() {
            HBox segments = new HBox(Layout.GAP_NORMAL, renterTab, ownerTab);
            segments.setAlignment(Pos.CENTER);
            VBox form = new VBox(Layout.GAP_MEDIUM, UiHelper.title("Create an account"), segments,
                    labeled("Username", user.node), labeled("Password", pass.node()),
                    labeled("Confirm password", confirm.node()), name.node(),
                    labeled("Phone number", phone.node()), renterOnly,
                    labeled("Security question (for Forgot password)",
                            new VBox(Layout.GAP_HAIR, question, questionError)),
                    labeled("Your answer", answer.node), status);
            return form;
        }
    }

    void showSignUp() {
        SignUpForm f = new SignUpForm();
        Button create = primary("Create account");
        create.setOnAction(e -> {
            if (!f.validate()) {
                UiHelper.setStatus(f.status, "Please fix the red messages first.", true);
                return;
            }
            try {
                if (f.ownerTab.isSelected()) {
                    auth.signUpOwner(f.user.text(), f.pass.text(), f.name.value(), f.phone.value(),
                            f.question.getValue(), f.answer.text());
                } else {
                    auth.signUpRenter(f.user.text(), f.pass.text(), new Renter(0, f.name.value(),
                            f.phone.value(), f.email.text(), f.address.value()),
                            f.question.getValue(), f.answer.text());
                }
                showLogin("Account created. Log in with your new username and password.",
                        f.user.text());
            } catch (RuleException ex) {
                if (ex.getMessage().contains("username")) {
                    UiHelper.setError(f.user.error, ex.getMessage());
                }
                UiHelper.setStatus(f.status, ex.getMessage(), true);
            } catch (ControllerException ex) {
                UiHelper.setStatus(f.status, ex.getMessage(), true);
            }
        });
        VBox form = f.node();
        form.getChildren().addAll(create,
                link("I already have an account", () -> showLogin(null, f.user.text())));
        form.getChildren().get(form.getChildren().size() - 1).setStyle("-fx-alignment: center;");
        show(form);
    }

    // ---------- forgot password ----------

    void showForgot(String username) {
        Row user = new Row("Username");
        if (username != null) {
            user.field.setText(username);
        }
        Label question = new Label();
        question.getStyleClass().add("vehicle-name");
        question.setWrapText(true);
        question.setMaxWidth(QUESTION_MAX_WIDTH);
        Row answer = new Row("Your answer");
        PasswordInput newPass = new PasswordInput(
                "New password (8+ characters, a letter and a digit)");
        PasswordInput confirm = new PasswordInput("Type the new password again");
        Label status = status();
        VBox step2 = new VBox(Layout.GAP_MEDIUM, question, labeled("Your answer", answer.node),
                labeled("New password", newPass.node()),
                labeled("Confirm new password", confirm.node()));
        step2.setManaged(false);
        step2.setVisible(false);
        Button find = primary("Find my account");
        Button reset = primary("Reset password");
        reset.setManaged(false);
        reset.setVisible(false);
        find.setOnAction(e -> {
            if (user.text().isEmpty()) {
                UiHelper.setError(user.error, "Type your username.");
                return;
            }
            try {
                question.setText(auth.securityQuestionOf(user.text()));
                step2.setManaged(true);
                step2.setVisible(true);
                reset.setManaged(true);
                reset.setVisible(true);
                find.setManaged(false);
                find.setVisible(false);
                user.field.setDisable(true);
                UiHelper.setStatus(status,
                        "Answer your security question and choose a new password.", false);
            } catch (ControllerException ex) {
                UiHelper.setStatus(status, ex.getMessage(), true);
            }
        });
        reset.setOnAction(e -> {
            boolean ok = true;
            String m = AuthController.checkAnswer(answer.text());
            if (m != null) {
                UiHelper.setError(answer.error, m);
                ok = false;
            }
            m = AuthController.checkPassword(newPass.text());
            if (m != null) {
                newPass.showError(m);
                ok = false;
            } else if (!newPass.text().equals(confirm.text())) {
                confirm.showError("The two passwords are not the same.");
                ok = false;
            }
            if (!ok) {
                UiHelper.setStatus(status, "Please fix the red messages first.", true);
                return;
            }
            try {
                auth.resetPassword(user.text(), answer.text(), newPass.text());
                showLogin("Your password was changed. Log in with the new password.", user.text());
            } catch (ControllerException ex) {
                UiHelper.setStatus(status, ex.getMessage(), true);
            }
        });
        user.field.setOnAction(e -> find.fire());
        show(new VBox(Layout.GAP_WIDE, UiHelper.title("Forgot your password?"),
                hintLabel(
                        "Type your username. You will answer the security question you chose at "
                        + "sign up."),
                labeled("Username", user.node), step2, status, find, reset,
                link("Back to log in", () -> showLogin(null, user.text()))));
        javafx.application.Platform.runLater(user.field::requestFocus);
    }
}
