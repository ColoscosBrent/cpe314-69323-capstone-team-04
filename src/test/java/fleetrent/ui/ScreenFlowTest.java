package fleetrent.ui;

import fleetrent.dao.Database;
import fleetrent.dto.VehicleModelInfo;
import fleetrent.model.Account;
import fleetrent.model.Reservation;
import fleetrent.model.Vehicle;
import fleetrent.model.VehicleTypes;
import fleetrent.service.AuthService;
import fleetrent.service.BookingService;
import fleetrent.service.FleetService;
import fleetrent.service.Validators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

// Clicks through the real screens (login, owner, renter) the way a person would: log in, sign up,
// rent, reserve, add, edit and remove a vehicle. See ScreenTestBase for the set-up.
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class ScreenFlowTest extends ScreenTestBase {
    // ---------- login screen ----------

    @Test
    void theLoginScreenHasTheThreeActionsAndNoSignOut() throws Exception {
        assertNotNull(onFx(() -> button("Forgot password?")));
        assertNotNull(onFx(() -> button("Create an account")));
        assertTrue(onFx(() -> button("Sign out") == null));
    }

    @Test
    void aWrongPasswordShowsAMessageAndStaysOnTheLoginScreen() throws Exception {
        login("ana_reyes", "wrongpass1");
        until(() -> hasText("not correct"), "a message about the wrong password");
        assertNotNull(onFx(() -> button("Log in")));
        assertTrue(onFx(() -> button("Sign out") == null));
    }

    @Test
    void emptyFieldsDoNotLogInAndDoNotCrash() throws Exception {
        click("Log in");
        Thread.sleep(300);
        assertNotNull(onFx(() -> button("Log in")));
        assertTrue(onFx(() -> button("Sign out") == null));
    }

    @Test
    void forgotPasswordShowsTheSecurityQuestionOfThatUser() throws Exception {
        click("Forgot password?");
        type("Username", "ana_reyes");
        click("Find my account");
        until(() -> hasText("first pet"), "the security question appears");
        click("Back to log in");
        until(() -> button("Log in") != null, "back on the login screen");
    }

    @SuppressWarnings("unchecked")
    @Test
    void aNewRenterCanSignUpAndThenLogIn() throws Exception {
        click("Create an account");
        type("4 to 20 letters, digits, _ or .", "ben_cruz");
        typePassword("8+ characters, a letter and a digit", "Passw0rd2");
        typePassword("Type the password again", "Passw0rd2");
        type("Reyes", "Cruz");
        type("Ana", "Ben");
        type("Santos", "Lim");
        type("917 123 4567", "918 555 1234");
        type("ana@example.com", "ben@example.com");
        type("12 Rizal St", "5 Mabini St");
        type("Cebu City", "Davao City");
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
        login("ben_cruz", "Passw0rd2");
        until(() -> button("Sign out") != null, "the renter screen");
        assertEquals(List.of("Cars", "My bookings", "My account"), tabNames());
    }

    // ---------- renter screen ----------

    @Test
    void aRenterSeesOnlyTheRenterTabsAndNothingOfTheOwner() throws Exception {
        login("ana_reyes", "Passw0rd1");
        until(() -> button("Sign out") != null, "the renter screen");
        assertEquals(List.of("Cars", "My bookings", "My account"), tabNames());
        assertFalse(onFx(() -> hasText("Dashboard")));
        assertTrue(onFx(() -> button("Add vehicle") == null));
        assertTrue(onFx(() -> button("Import") == null));
        assertEquals(6, offerCards());
    }

    @Test
    void aRenterCanRentUndoReserveAndSignOut() throws Exception {
        login("ana_reyes", "Passw0rd1");
        until(() -> cards() == 6, "six car cards");
        clickFirstCar();
        click("Rent today");
        until(() -> hasText("started"), "the rental started message");
        click("Undo last");
        until(() -> hasText("was undone"), "the undo message");
        onFx(() -> {
            List<DatePicker> pickers = all(DatePicker.class);
            pickers.get(0).setValue(LocalDate.now().plusDays(3));
            pickers.get(1).setValue(LocalDate.now().plusDays(5));
            return null;
        });
        Thread.sleep(400);
        clickFirstCar();
        click("Reserve these days");
        until(() -> hasText("Reservation #"), "the reservation message");
        openTab("My bookings");
        until(() -> button("Cancel selected reservation") != null,
                "My bookings with its cancel button");
        click("Sign out");
        until(() -> button("Log in") != null, "the login screen after sign out");
        assertTrue(onFx(() -> button("Sign out") == null));
    }

    @Test
    void aRentedCarCannotBeRentedAgainByTheSameRenterForTheSameDays() throws Exception {
        login("ana_reyes", "Passw0rd1");
        until(() -> cards() == 6, "six car cards");
        clickFirstCar();
        click("Rent today");
        until(() -> hasText("started"), "the rental started message");
        clickFirstCar();
        Thread.sleep(500);
        try (Database check = new Database(System.getProperty("fleetrent.dbUrl"))) {
            assertEquals(1, check.getAllRentals().size());
        }
    }

    @Test
    void aCarTheOwnerRemovesDisappearsFromTheRentersScreenByItself() throws Exception {
        login("ana_reyes", "Passw0rd1");
        until(() -> cards() == 6, "six car cards");
        try (Database other = new Database(System.getProperty("fleetrent.dbUrl"))) {
            new FleetService(other).removeVehicle(other.getAllVehicles().get(0).getId());
        }
        until(() -> cards() == 5, "the renter screen refreshes and shows five cars");
    }

    // ---------- owner screen ----------

    @Test
    void theOwnerHasSixTabsAndSeesTheRentersReservation() throws Exception {
        try (Database db = new Database(System.getProperty("fleetrent.dbUrl"))) {
            new BookingService(db, LocalDate::now).reserve(ana, db.getAllVehicles().get(0).getId(),
                    LocalDate.now().plusDays(3), LocalDate.now().plusDays(5));
        }
        login("owner01", "Passw0rd1");
        until(() -> button("Sign out") != null, "the owner screen");
        assertEquals(List.of("Vehicles", "Rentals", "Reservations", "Import", "Import history",
                "Dashboard"),
                tabNames());
        openTab("Reservations");
        until(() -> anyListHas(Reservation.class), "the reservation is listed");
    }

    @Test
    void aReservationMadeAfterTheOwnerLoggedInAppearsByItself() throws Exception {
        login("owner01", "Passw0rd1");
        until(() -> button("Sign out") != null, "the owner screen");
        openTab("Reservations");
        try (Database db = new Database(System.getProperty("fleetrent.dbUrl"))) {
            new BookingService(db, LocalDate::now).reserve(ana, db.getAllVehicles().get(1).getId(),
                    LocalDate.now().plusDays(2), LocalDate.now().plusDays(3));
        }
        until(() -> anyListHas(Reservation.class),
                "the new reservation shows up without pressing anything");
    }

    @SuppressWarnings("unchecked")
    @Test
    void addVehicleWorksAfterChoosingTheYearFirstThenTheMakeAndTheModel() throws Exception {
        login("owner01", "Passw0rd1");
        until(() -> button("Add vehicle") != null, "the Vehicles tab with its form");
        int before = listSize(Vehicle.class, "vehicle-list");
        onFx(() -> {
            for (ChoiceBox<?> c : all(ChoiceBox.class)) {
                if (c.getItems().contains("MOTORCYCLE") && c.getItems().size() == 3) {
                    ((ChoiceBox<String>) c).setValue("MOTORCYCLE");
                }
            }
            return null;
        });
        until(() -> hasListItem(String.class, "HONDA"), "the makes list from NHTSA");
        // the year comes first: before it the make and model lists cannot be used
        assertTrue(onFx(() -> {
            for (ListView<?> l : all(ListView.class)) {
                if (l.getStyleClass().contains("plain-list") && !l.getItems().isEmpty()
                        && l.getItems().get(0) instanceof String) {
                    return l.isDisabled();
                }
            }
            return false;
        }), "the make list is locked until a year is chosen");
        onFx(() -> {
            for (ComboBox<?> c : all(ComboBox.class)) {
                if (c.getItems().contains("2022")) {
                    assertFalse(c.getItems().contains("2027"), "no future year 2027");
                    ((ComboBox<String>) c).setValue("2022");
                }
            }
            return null;
        });
        onFx(() -> {
            for (ListView<?> l : all(ListView.class)) {
                if (l.getStyleClass().contains("plain-list") && !l.getItems().isEmpty()
                        && l.getItems().get(0) instanceof String) {
                    ((ListView<String>) l).getSelectionModel().select("HONDA");
                }
            }
            return null;
        });
        until(() -> anyListHas(VehicleModelInfo.class), "the models list from NHTSA");
        onFx(() -> {
            for (ListView<?> l : all(ListView.class)) {
                if (l.getStyleClass().contains("plain-list") && !l.getItems().isEmpty()
                        && l.getItems().get(0) instanceof VehicleModelInfo) {
                    ListView<VehicleModelInfo> models = (ListView<VehicleModelInfo>) l;
                    for (VehicleModelInfo m : models.getItems()) {
                        if (Validators.sameName(m.getModelName(), "CB1000 Hornet")) {
                            models.getSelectionModel().select(m);
                        }
                    }
                }
            }
            return null;
        });
        Thread.sleep(600);
        onFx(() -> {
            for (ComboBox<?> c : all(ComboBox.class)) {
                if (c.getItems().contains("White")) {
                    ((ComboBox<String>) c).setValue("White");
                }
            }
            return null;
        });
        type("ABC 123", "NEW 777");
        type("1500", "950");
        click("Add vehicle");
        until(() -> hasText("Saved:"), "the Saved message");
        until(() -> sizeOfList(Vehicle.class, "vehicle-list") == before + 1,
                "the new vehicle is in the fleet list");
        try (Database check = new Database(System.getProperty("fleetrent.dbUrl"))) {
            Vehicle saved = check.findVehicleByPlate("NEW 777");
            assertNotNull(saved);
            assertEquals("HONDA", saved.getMake());
            assertTrue(Validators.sameName("CB1000 Hornet", saved.getModel()));
            assertEquals(950, saved.getDailyRate(), 0.001);
            assertEquals(2022, saved.getYear());
            assertTrue(saved.getCategoryPath().startsWith("Motorcycle > "));
        }
    }

    // Chooses a value in the box that offers it (the Type box, the Year box, ...).
    @SuppressWarnings("unchecked")
    private static void choose(Class<?> boxType, String value) throws Exception {
        onFx(() -> {
            for (ChoiceBox<?> c : all(ChoiceBox.class)) {
                if (boxType == ChoiceBox.class && c.getItems().contains(value)) {
                    ((ChoiceBox<String>) c).setValue(value);
                    return null;
                }
            }
            for (ComboBox<?> c : all(ComboBox.class)) {
                if (boxType == ComboBox.class && c.getItems().contains(value)) {
                    ((ComboBox<String>) c).setValue(value);
                    return null;
                }
            }
            throw new AssertionError("No box offers '" + value + "'");
        });
    }

    // The value of the box that offers the item, read on the JavaFX thread.
    private static String valueOfBoxWith(String item) throws Exception {
        return onFx(() -> {
            for (ChoiceBox<?> c : all(ChoiceBox.class)) {
                if (c.getItems().contains(item)) {
                    return String.valueOf(c.getValue());
                }
            }
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private static void pickModel(String name) throws Exception {
        onFx(() -> {
            for (ListView<?> l : all(ListView.class)) {
                if (!l.getItems().isEmpty() && l.getItems().get(0) instanceof VehicleModelInfo) {
                    ListView<VehicleModelInfo> models = (ListView<VehicleModelInfo>) l;
                    for (VehicleModelInfo m : models.getItems()) {
                        if (Validators.sameName(m.getModelName(), name)) {
                            models.getSelectionModel().select(m);
                            return null;
                        }
                    }
                }
            }
            throw new AssertionError("NHTSA list has no model " + name);
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    void aModelPickedFromNhtsaSetsItsCategoryAndTheTransmissionThatFits() throws Exception {
        login("owner01", "Passw0rd1");
        until(() -> button("Add vehicle") != null, "the Vehicles tab with its form");
        choose(ChoiceBox.class, "MOTORCYCLE");
        until(() -> hasListItem(String.class, "HONDA"), "the makes list from NHTSA");
        choose(ComboBox.class, "2022");
        onFx(() -> {
            for (ListView<?> l : all(ListView.class)) {
                if (l.getStyleClass().contains("plain-list") && !l.getItems().isEmpty()
                        && l.getItems().get(0) instanceof String) {
                    ((ListView<String>) l).getSelectionModel().select("HONDA");
                }
            }
            return null;
        });
        until(() -> anyListHas(VehicleModelInfo.class), "the models list from NHTSA");
        // A Rebel is a cruiser: the Category box follows the model, and the Transmission box
        // shows the first transmission that fits a cruiser.
        pickModel("Rebel 500");
        until(() -> hasText("Suggested for HONDA Rebel 500: Cruiser"), "the suggestion note");
        assertEquals("Cruiser", valueOfBoxWith("Cruiser"));
        String gear = VehicleTypes.transmissions("MOTORCYCLE", "Cruiser").get(0);
        assertEquals(gear, valueOfBoxWith(gear));
        // Another model of the same make changes the category again.
        pickModel("CB500F");
        until(() -> hasText("Suggested for HONDA CB500F: Standard (naked)"), "the new note");
        assertEquals("Standard (naked)", valueOfBoxWith("Cruiser"));
        // The owner can still choose another category by hand.
        choose(ChoiceBox.class, "Sport");
        assertEquals("Sport", valueOfBoxWith("Cruiser"));
    }

    @Test
    void anEmailIsAlwaysInSmallLettersAndMustLookLikeAnEmail() throws Exception {
        click("Create an account");
        // Capital letters turn small as they are typed.
        type("ana@example.com", "Ben.Cruz@Example.COM");
        assertEquals("ben.cruz@example.com", onFx(() -> {
            for (TextField t : all(TextField.class)) {
                if ("ana@example.com".equals(t.getPromptText())) {
                    return t.getText();
                }
            }
            return null;
        }));
        // Text that is not an email is refused with a message when the form is sent.
        type("ana@example.com", "ben.cruz@example");
        click("Create account");
        until(() -> hasText("Enter an email like name@example.com."), "the email message");
        assertNotNull(onFx(() -> button("Create account")), "still on the sign-up form");
    }

    @Test
    void editModeShowsSaveAndCancelAndCancelGoesBackToAdd() throws Exception {
        login("owner01", "Passw0rd1");
        until(() -> button("Edit") != null, "a vehicle card with an Edit button");
        onFx(() -> {
            button("Edit").fire();
            return null;
        });
        until(() -> button("Save changes") != null && button("Cancel editing") != null,
                "edit mode");
        click("Cancel editing");
        until(() -> button("Add vehicle") != null, "back to the Add form");
    }

    @Test
    void removeTakesTheVehicleOutOfTheListAfterTheOwnerConfirms() throws Exception {
        login("owner01", "Passw0rd1");
        until(() -> button("Remove") != null, "a vehicle card with a Remove button");
        int before = listSize(Vehicle.class, "vehicle-list");
        // the confirm window waits for an answer, so the click is sent without waiting for it to
        // return
        Platform.runLater(() -> button("Remove").fire());
        // the confirm window opens above the main window
        until(() -> {
            for (Window w : new ArrayList<>(Window.getWindows())) {
                if (w instanceof Stage s && s != mainStage() && s.isShowing()
                        && s.getScene() != null) {
                    for (Node n : s.getScene().getRoot().lookupAll(".button")) {
                        if (n instanceof Button b && "Remove it".equals(b.getText())) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }, "the confirm window with the 'Remove it' button");
        Platform.runLater(() -> {
            for (Window w : new ArrayList<>(Window.getWindows())) {
                if (w instanceof Stage s && s != mainStage() && s.isShowing()
                        && s.getScene() != null) {
                    for (Node n : s.getScene().getRoot().lookupAll(".button")) {
                        if (n instanceof Button b && "Remove it".equals(b.getText())) {
                            b.fire();
                        }
                    }
                }
            }
        });
        until(() -> sizeOfList(Vehicle.class, "vehicle-list") == before - 1,
                "the fleet list has one vehicle less");
        try (Database check = new Database(System.getProperty("fleetrent.dbUrl"))) {
            assertEquals(1, check.getRemovedVehicles().size());
        }
    }

    @Test
    void signOutOfTheOwnerScreenReturnsToTheLoginScreenAndAnotherUserCanLogIn() throws Exception {
        login("owner01", "Passw0rd1");
        until(() -> button("Sign out") != null, "the owner screen");
        click("Sign out");
        until(() -> button("Log in") != null, "the login screen");
        login("ana_reyes", "Passw0rd1");
        until(() -> button("Sign out") != null, "the renter screen");
        assertEquals(List.of("Cars", "My bookings", "My account"), tabNames());
    }
}
