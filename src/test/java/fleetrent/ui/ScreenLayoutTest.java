package fleetrent.ui;

import fleetrent.model.Account;
import fleetrent.service.AuthService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

// How the screens look and fit: the title bar of the app, the prompts and drop-downs of the login
// screen, and the window at every size. See ScreenTestBase for the set-up.
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class ScreenLayoutTest extends ScreenTestBase {
    // ---------- title bar, drop-downs and the login screen ----------

    @Test
    void theWindowHasTheTitleBarOfTheApp() throws Exception {
        assertTrue(onFx(() -> {
            for (Node n : mainStage().getScene().getRoot().lookupAll(".title-bar")) {
                return n.isVisible();
            }
            return false;
        }));
        assertTrue(onFx(() -> hasText("FleetRent")));
        assertNotNull(onFx(() -> mainStage().getScene().getRoot().lookup(".title-bar-close")));
    }

    @Test
    void theTitleBarShowsTheFleetRentLogoInsteadOfADiamond() throws Exception {
        assertTrue(onFx(() -> {
            Node logo = mainStage().getScene().getRoot().lookup(".title-bar-logo");
            return logo instanceof javafx.scene.image.ImageView view && view.isVisible()
                    && view.getImage() != null && !view.getImage().isError()
                    && view.getImage().getWidth() > 0;
        }), "the logo picture is in the title bar");
        assertNull(onFx(() -> mainStage().getScene().getRoot().lookup(".title-bar-icon")),
                "the old diamond is gone");
    }

    @Test
    void theSecurityQuestionBoxHasAPromptAndNoDefault() throws Exception {
        click("Create an account");
        onFx(() -> {
            for (ComboBox<?> c : all(ComboBox.class)) {
                if (c.getItems().contains(AuthService.SECURITY_QUESTIONS.get(0))) {
                    assertEquals("Choose a question", c.getPromptText());
                    assertNull(c.getValue());
                    return null;
                }
            }
            throw new AssertionError("No security question box");
        });
    }

    @Test
    void theOpenListOfADropDownHasASolidBackground() throws Exception {
        click("Create an account");
        onFx(() -> {
            for (ComboBox<?> c : all(ComboBox.class)) {
                if (c.getItems().contains(AuthService.SECURITY_QUESTIONS.get(0))) {
                    c.show();
                    return null;
                }
            }
            throw new AssertionError("No security question box");
        });
        until(() -> {
            for (Window w : Window.getWindows()) {
                Node list = w.getScene() == null ? null
                        : w.getScene().getRoot().lookup(".list-view");
                if (w != mainStage() && list instanceof javafx.scene.layout.Region r
                        && r.getBackground() != null && !r.getBackground().getFills().isEmpty()) {
                    javafx.scene.paint.Paint fill = r.getBackground().getFills().get(0).getFill();
                    return fill instanceof javafx.scene.paint.Color c && c.getOpacity() == 1.0;
                }
            }
            return false;
        }, "the open drop-down list has a solid background");
    }

    @Test
    void anEmptyLoginMessageDoesNotMoveTheLoginButton() throws Exception {
        double before = onFx(() -> button("Log in").localToScene(
                button("Log in").getBoundsInLocal()).getMinY());
        click("Log in");
        until(() -> hasText("Type your username."), "the empty username message");
        double after = onFx(() -> button("Log in").localToScene(
                button("Log in").getBoundsInLocal()).getMinY());
        assertEquals(before, after, 0.5);
    }

    @SuppressWarnings("unchecked")
    @Test
    void afterAnOwnerAccountIsCreatedTheLoginFormAsksForTheOwnerRole() throws Exception {
        click("Create an account");
        onFx(() -> {
            for (javafx.scene.control.ToggleButton t
                    : all(javafx.scene.control.ToggleButton.class)) {
                if ("I own the rental shop".equals(t.getText())) {
                    t.setSelected(true);
                }
            }
            return null;
        });
        type("4 to 20 letters, digits, _ or .", "owner02");
        typePassword("8+ characters, a letter and a digit", "Passw0rd2");
        typePassword("Type the password again", "Passw0rd2");
        type("Reyes", "Lopez");
        type("Ana", "Maria");
        type("Santos", "Cruz");
        type("917 123 4567", "918 555 1234");
        onFx(() -> {
            for (ComboBox<?> c : all(ComboBox.class)) {
                if (c.getItems().contains(AuthService.SECURITY_QUESTIONS.get(1))) {
                    ((ComboBox<String>) c).setValue(AuthService.SECURITY_QUESTIONS.get(1));
                }
            }
            return null;
        });
        type("Your answer", "Mabini");
        click("Create account");
        until(() -> hasText("Account created"), "the sign-up message");
        assertTrue(onFx(() -> {
            for (javafx.scene.control.ToggleButton t
                    : all(javafx.scene.control.ToggleButton.class)) {
                if ("Log in as Owner".equals(t.getText())) {
                    return t.isSelected();
                }
            }
            return false;
        }));
    }

    // ---------- window size ----------

    private static final double[][] SIZES = {{860, 560}, {1024, 640}, {1366, 700}, {1920, 1040},
            {2560, 1400}};

    // Makes the window this big (like dragging its corner or pressing Maximize) and checks that the
    // header buttons are still inside the window and the tabs fill the window.
    private static void checkWindowAtEverySize(String buttonThatMustStayVisible,
            boolean tabs) throws Exception {
        for (double[] size : SIZES) {
            onFx(() -> {
                Stage stage = mainStage();
                stage.setWidth(size[0]);
                stage.setHeight(size[1]);
                return null;
            });
            Thread.sleep(350);
            String where = " at " + (int) size[0] + "x" + (int) size[1] + " (tab " + currentTab()
                    + ")";
            onFx(() -> {
                Stage stage = mainStage();
                stage.getScene().getRoot().applyCss();
                stage.getScene().getRoot().layout();
                double w = stage.getScene().getWidth();
                double h = stage.getScene().getHeight();
                assertTrue(w >= 800, "the window keeps a usable width" + where);
                Button b = button(buttonThatMustStayVisible);
                assertNotNull(b, buttonThatMustStayVisible + " is there" + where);
                javafx.geometry.Bounds inScene = b.localToScene(b.getBoundsInLocal());
                javafx.scene.layout.Region rootRegion =
                        (javafx.scene.layout.Region) stage.getScene().getRoot();
                assertTrue(inScene.getMaxX() <= w + 1, buttonThatMustStayVisible
                        + " is not pushed out on the right" + where
                        + " [scene " + (int) w + ", button right edge " + (int) inScene.getMaxX()
                                + ", root min width "
                        + (int) rootRegion.minWidth(-1) + ", root width "
                                + (int) rootRegion.getWidth() + "]");
                assertTrue(inScene.getMinX() >= 0, buttonThatMustStayVisible
                        + " is not pushed out on the left" + where);
                assertTrue(inScene.getMaxY() <= h + 1, buttonThatMustStayVisible
                        + " is inside the window" + where + " [button bottom "
                        + (int) inScene.getMaxY() + ", scene height " + (int) h + "]");
                if (tabs) {
                    TabPane tp = all(TabPane.class).get(0);
                    javafx.geometry.Bounds tb = tp.localToScene(tp.getBoundsInLocal());
                    assertTrue(Math.abs(tb.getWidth() - w) <= 2, "the tabs fill the width" + where);
                    assertTrue(tb.getMaxY() >= h - 2, "the tabs reach the bottom of the window"
                            + where);
                    assertTrue(tb.getHeight() >= 250, "the tabs keep a usable height" + where);
                }
                return null;
            });
        }
    }

    @Test
    void theLoginScreenStaysInsideTheWindowAtEverySize() throws Exception {
        checkWindowAtEverySize("Log in", false);
    }

    @Test
    void theOwnerScreenFillsTheWindowAtEverySizeOnEveryTab() throws Exception {
        login("owner01", "Passw0rd1");
        until(() -> button("Sign out") != null, "the owner screen");
        for (String tab : new String[]{"Vehicles", "Rentals", "Reservations", "Import",
                "Import history", "Dashboard"}) {
            openTab(tab);
            checkWindowAtEverySize("Sign out", true);
        }
    }

    @Test
    void theRenterScreenFillsTheWindowAtEverySizeOnEveryTab() throws Exception {
        login("ana_reyes", "Passw0rd1");
        until(() -> button("Sign out") != null, "the renter screen");
        for (String tab : new String[]{"Cars", "My bookings", "My account"}) {
            openTab(tab);
            checkWindowAtEverySize("Sign out", true);
        }
    }

    @Test
    void theWindowCannotBeMadeSmallerThanWhatTheScreensNeed() throws Exception {
        onFx(() -> {
            Stage stage = mainStage();
            assertTrue(stage.getMinWidth() >= 700 && stage.getMinWidth() <= 1000, "minimum width "
                    + stage.getMinWidth());
            assertTrue(stage.getMinHeight() >= 450 && stage.getMinHeight() <= 700,
                    "minimum height " + stage.getMinHeight());
            return null;
        });
    }

    @Test
    void theWindowCanBeMaximizedAndRestoredWithoutLosingTheScreen() throws Exception {
        login("ana_reyes", "Passw0rd1");
        until(() -> cards() == 6, "six car cards");
        onFx(() -> {
            mainStage().setMaximized(true);
            return null;
        });
        Thread.sleep(400);
        onFx(() -> {
            mainStage().setMaximized(false);
            return null;
        });
        Thread.sleep(400);
        assertEquals(6, offerCards());
        assertNotNull(onFx(() -> button("Sign out")));
    }
}
