package fleetrent.ui;

import fleetrent.controller.ControllerException;
import fleetrent.controller.VehicleController;
import fleetrent.model.Vehicle;

import java.nio.file.Path;
import java.util.List;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

// The photo jobs of the Vehicles tab: copy the photo the owner chose, or ask the controller to look
// for a photo online in the background (for one new vehicle, or for every vehicle that has none).
final class PhotoJobs {
    private final VehicleController controller;
    private final Label status;
    private final Button findAllButton;
    private final Runnable refresh;

    // status shows the messages. refresh reloads the tab when a photo was added.
    PhotoJobs(VehicleController controller, Label status, Button findAllButton,
            Runnable refresh) {
        this.controller = controller;
        this.status = status;
        this.findAllButton = findAllButton;
        this.refresh = refresh;
    }

    // After a vehicle is saved: copy the chosen photo (if any), or look for one online.
    void afterAdd(Vehicle vehicle, Path chosenPhoto) {
        if (chosenPhoto != null) {
            try {
                controller.savePhoto(vehicle.getId(), chosenPhoto);
            } catch (ControllerException e) {
                UiHelper.setStatus(status, "Saved, but the photo could not be used: "
                        + e.getMessage(), true);
            }
            return;
        }
        UiHelper.setStatus(status, "Saved: " + vehicle + ". Looking for a photo online...", false);
        controller.findPhotoLater(vehicle, found -> {
            UiHelper.setStatus(status, "Saved: " + vehicle + ". " + photoMessage(found,
                    vehicle.getColor()), false);
            refresh.run();
        }, message -> UiHelper.setStatus(status, "Saved: " + vehicle + ". " + message
                + " Showing the drawn car.", true));
    }

    // What to say about the photo of one new vehicle.
    private static String photoMessage(VehicleController.Photo found, String color) {
        if (found == VehicleController.Photo.USUAL && (color == null || color.isBlank())) {
            return "Photo added.";
        }
        switch (found) {
            case IN_COLOR:
                return "Photo added in the color of the vehicle (" + color + ").";
            case USUAL:
                return "Photo added. No " + color + " photo of this model was found online, so "
                        + "it may show another color. Use Choose photo to add yours.";
            default:
                return "No online photo found, showing the drawn car. Use Choose photo to add "
                        + "yours.";
        }
    }

    // Looks online for a photo of each vehicle in the list. The controller runs it in the
    // background and reports the progress.
    void findAll(List<Vehicle> missing) {
        if (missing.isEmpty()) {
            UiHelper.setStatus(status, "Every vehicle already has a photo.", false);
            return;
        }
        findAllButton.setDisable(true);
        controller.findPhotosLater(missing, (done, total) -> UiHelper.setStatus(status,
                "Looking for photos... " + done + " of " + total, false), r -> {
            UiHelper.setStatus(status, "Photo search done. Found " + r[0] + " photo(s), "
                    + r[2] + " in the color of the vehicle"
                    + (r[1] > 0 ? "; " + r[1] + " could not be searched." : "."), r[1] > 0);
            findAllButton.setDisable(false);
            refresh.run();
        }, message -> {
            UiHelper.setStatus(status, "Photo search stopped. " + message, true);
            findAllButton.setDisable(false);
        });
    }
}
