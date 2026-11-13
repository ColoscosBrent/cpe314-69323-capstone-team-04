package fleetrent.ui;

import fleetrent.controller.AuthController;
import fleetrent.controller.ControllerException;
import fleetrent.controller.RuleException;
import fleetrent.model.Account;
import fleetrent.model.Renter;
import fleetrent.model.Vehicle;
import fleetrent.service.Validators;

import java.util.function.Consumer;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
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
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

// The first screen: Log in, Sign up (renter or rental owner) and Forgot password. Nothing else of
// the app can be seen until a login works.
final class AuthScreen {
    private final AuthController auth;
    private final Consumer<Account> onLogin;
    private final Runnable onNewWindow;
    private final VBox root = new VBox();
    private final VBox cardContent = new VBox(12);
    private Timeline roadAnimation;

    AuthScreen(AuthController auth, Consumer<Account> onLogin, Runnable onNewWindow) {
        this.auth = auth;
        this.onLogin = onLogin;
        this.onNewWindow = onNewWindow;

        Label hello = new Label("Rent the right ride.");
        hello.getStyleClass().add("hero-title");
        Label sub = new Label("Log in to see the cars, the prices and what is free on your dates.");
        sub.getStyleClass().add("hero-sub");
        sub.setWrapText(true);
        sub.setMaxWidth(460);
        VBox hero = new VBox(6, hello, sub);
        hero.setAlignment(Pos.CENTER);
        hero.setPadding(new Insets(10, 0, 6, 0));

        VBox card = new VBox(14, cardContent);
        card.getStyleClass().add("auth-card");
        card.setMaxWidth(520);
        card.setMinWidth(420);
        VBox center = new VBox(18, hero, card, road());
        center.setAlignment(Pos.TOP_CENTER);
        center.setPadding(new Insets(22, 18, 30, 18));
        
        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(center);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("page-scroll");
        scroll.setFocusTraversable(false);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.getChildren().addAll(Chrome.header("Vehicle rental and fleet management", null, null, onNewWindow), scroll);
        showLogin(null, null);
    }

    Node root() {
        return root;
    }

    // Stops the moving cars when the screen is left.
    void dispose() {
        if (roadAnimation != null) {
            roadAnimation.stop();
        }
    }

    // A strip where three cars drive by, so the first screen is not a plain form.
    private Node road() {
        Pane strip = new Pane();
        strip.setMinHeight(70);
        strip.setPrefHeight(70);
        strip.setMaxWidth(860);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(strip.widthProperty());
        clip.heightProperty().bind(strip.heightProperty());
        strip.setClip(clip);
        Node[] cars = {CarGraphic.create("SEDAN", "Gold", 110), CarGraphic.create("SUV", "Purple", 118),
                CarGraphic.create("MOTORCYCLE", "Orange", 96)};
        roadAnimation = new Timeline();
        double offset = 0;
        for (Node car : cars) {
            car.setLayoutY(8);
            car.setTranslateX(-200);
            strip.getChildren().add(car);
            roadAnimation.getKeyFrames().addAll(
                    new KeyFrame(Duration.seconds(0), new KeyValue(car.translateXProperty(), -200 - offset)),
                    new KeyFrame(Duration.seconds(16), new KeyValue(car.translateXProperty(), 1000 - offset)));
            offset += 260;
        }
        roadAnimation.setCycleCount(Animation.INDEFINITE);
        roadAnimation.play();
        return strip;
    }

    private void show(Node panel) {
        cardContent.getChildren().setAll(panel);
        javafx.animation.FadeTransition fade = new javafx.animation.FadeTransition(Duration.millis(220), panel);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    // ---------- small helpers ----------

    // A one-line input with its own red message under it.
    private static final class Row {
        final TextField field = new TextField();
        final Label error = UiHelper.errorLabel();
        final VBox node = new VBox(3, field, error);

        Row(String hint) {
            field.setPromptText(hint);
            field.setPrefWidth(300);
            error.setMaxWidth(340);
            field.textProperty().addListener((obs, o, n) -> UiHelper.setError(error, null));
        }

        String text() {
            return field.getText() == null ? "" : field.getText().trim();
        }
    }

    private static VBox labeled(String label, Node control) {
        Label l = new Label(label);
        l.getStyleClass().add("field-label");
        return new VBox(3, l, control);
    }

    private static Label status() {
        Label l = new Label();
        l.getStyleClass().add("status-label");
        l.setWrapText(true);
        l.setMaxWidth(440);
        l.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        return l;
    }

    private static Label hintLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("hint-label");
        l.setWrapText(true);
        l.setMaxWidth(440);
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
        Runnable doLogin = () -> {
            if (user.text().isEmpty()) {
                UiHelper.setError(user.error, "Type your username.");
                return;
            }
            if (pass.text().isEmpty()) {
                pass.showError("Type your password.");
                return;
            }
            try {
                Account account = auth.login(user.text(), pass.text());
                pass.clear();
                onLogin.accept(account);
            } catch (ControllerException e) {
                UiHelper.setStatus(status, e.getMessage(), true);
            }
        };
        go.setOnAction(e -> doLogin.run());
        user.field.setOnAction(e -> pass.requestFocus());
        pass.onEnter(doLogin);
        HBox links = new HBox(14, link("Forgot password?", () -> showForgot(user.text())),
                link("Create an account", () -> showSignUp()));
        links.setAlignment(Pos.CENTER);
        Label title = UiHelper.title("Log in");
        show(new VBox(12, title, labeled("Username", user.node), labeled("Password", pass.node()), status, go, links));
        javafx.application.Platform.runLater(user.field::requestFocus);
    }

    // ---------- sign up ----------

    void showSignUp() {
        ToggleGroup group = new ToggleGroup();
        ToggleButton renterTab = new ToggleButton("I want to rent");
        ToggleButton ownerTab = new ToggleButton("I own the rental shop");
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
        HBox segments = new HBox(8, renterTab, ownerTab);
        segments.setAlignment(Pos.CENTER);

        Row user = new Row("4 to 20 letters, digits, _ or .");
        PasswordInput pass = new PasswordInput("8+ characters, a letter and a digit");
        PasswordInput confirm = new PasswordInput("Type the password again");
        ChoiceBox<String> question = new ChoiceBox<>();
        question.getItems().setAll(AuthController.securityQuestions());
        question.setPrefWidth(300);
        Label questionError = UiHelper.errorLabel();
        question.valueProperty().addListener((obs, o, n) -> UiHelper.setError(questionError, null));
        Row answer = new Row("Your answer");

        ValidatedField name = new ValidatedField(Validators.Kind.NAME, "Ana Reyes", true);
        ValidatedField contact = new ValidatedField(Validators.Kind.CONTACT, "0917 123 4567", true);
        ValidatedField email = new ValidatedField(Validators.Kind.EMAIL, "ana@example.com (optional)", false);
        ValidatedField address = new ValidatedField(Validators.Kind.ADDRESS, "12 Rizal St, Cebu City (optional)", false);
        VBox renterFields = new VBox(10, labeled("Full name", name.node()), labeled("Contact number", contact.node()),
                labeled("Email", email.node()), labeled("Address", address.node()));

        ValidatedField ownerName = new ValidatedField(Validators.Kind.NAME, "Maria Santos", true);
        PasswordInput ownerCode = new PasswordInput("Ask the shop for the owner code");
        VBox ownerFields = new VBox(10, labeled("Your name", ownerName.node()), labeled("Owner code", ownerCode.node()));
        ownerFields.setManaged(false);
        ownerFields.setVisible(false);
        ownerTab.selectedProperty().addListener((obs, was, now) -> {
            ownerFields.setManaged(now);
            ownerFields.setVisible(now);
            renterFields.setManaged(!now);
            renterFields.setVisible(!now);
        });

        Label status = status();
        Button create = primary("Create account");
        create.setOnAction(e -> {
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
            if (question.getValue() == null) {
                UiHelper.setError(questionError, "Pick a question.");
                ok = false;
            }
            m = AuthController.checkAnswer(answer.text());
            if (m != null) {
                UiHelper.setError(answer.error, m);
                ok = false;
            }
            if (owner) {
                ok = ownerName.validate() && ok;
                if (ownerCode.text().isBlank()) {
                    ownerCode.showError("Type the owner code.");
                    ok = false;
                }
            } else {
                ok = name.validate() && ok;
                ok = contact.validate() && ok;
                ok = email.validate() && ok;
                ok = address.validate() && ok;
            }
            if (!ok) {
                UiHelper.setStatus(status, "Please fix the red messages first.", true);
                return;
            }
            try {
                if (owner) {
                    auth.signUpOwner(user.text(), pass.text(), ownerName.text(), question.getValue(), answer.text(),
                            ownerCode.text());
                } else {
                    auth.signUpRenter(user.text(), pass.text(), new Renter(0, name.text(), contact.text(),
                            email.textOrNull(), address.textOrNull()), question.getValue(), answer.text());
                }
                showLogin("Account created. Log in with your new username and password.", user.text());
            } catch (RuleException ex) {
                String text = ex.getMessage();
                if (text.contains("owner code")) {
                    ownerCode.showError(text);
                } else if (text.contains("username")) {
                    UiHelper.setError(user.error, text);
                }
                UiHelper.setStatus(status, text, true);
            } catch (ControllerException ex) {
                UiHelper.setStatus(status, ex.getMessage(), true);
            }
        });
        VBox form = new VBox(10, UiHelper.title("Create an account"), segments,
                labeled("Username", user.node), labeled("Password", pass.node()),
                labeled("Confirm password", confirm.node()), renterFields, ownerFields,
                labeled("Security question (for Forgot password)", new VBox(3, question, questionError)),
                labeled("Your answer", answer.node), status, create,
                link("I already have an account", () -> showLogin(null, user.text())));
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
        question.setMaxWidth(440);
        Row answer = new Row("Your answer");
        PasswordInput newPass = new PasswordInput("New password (8+ characters, a letter and a digit)");
        PasswordInput confirm = new PasswordInput("Type the new password again");
        Label status = status();
        VBox step2 = new VBox(10, question, labeled("Your answer", answer.node), labeled("New password", newPass.node()),
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
                UiHelper.setStatus(status, "Answer your security question and choose a new password.", false);
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
        show(new VBox(12, UiHelper.title("Forgot your password?"),
                hintLabel("Type your username. You will answer the security question you chose at sign up."),
                labeled("Username", user.node), step2, status, find, reset,
                link("Back to log in", () -> showLogin(null, user.text()))));
        javafx.application.Platform.runLater(user.field::requestFocus);
    }
}
