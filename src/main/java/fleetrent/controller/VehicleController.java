package fleetrent.controller;

import fleetrent.dao.Database;
import fleetrent.dto.VehicleModelInfo;
import fleetrent.model.Maintenance;
import fleetrent.model.MaintenanceType;
import fleetrent.model.Vehicle;
import fleetrent.service.ApiMapper;
import fleetrent.service.CarImageStore;
import fleetrent.service.ErrorMessages;
import fleetrent.service.FleetService;
import fleetrent.service.ModelGuide;
import fleetrent.service.PhotoFinder;
import fleetrent.service.VpicClient;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Logger;
import javafx.application.Platform;

/**
 * What the owner's Vehicles tab asks for: the fleet, add, edit, remove and restore a vehicle, the
 * makes and models from NHTSA, and the photos. The tab never talks to the database, the web or
 * the picture folder itself.
 */
public class VehicleController extends ObservableController<Vehicle> {
    private static final String SLOT_FLEET = "fleet";
    private static final String SLOT_MAKES = "makes";
    private static final String SLOT_MODELS = "models";
    private static final Logger LOG = Logger.getLogger(VehicleController.class.getName());

    /**
     * The models NHTSA lists for a make in one model year.
     *
     * @param models the models found (empty when none were made that year)
     */
    public record Models(List<VehicleModelInfo> models) {
    }

    private final Database db;
    private final FleetService fleet;
    private final VpicClient vpic;
    private final CarImageStore store;
    private final PhotoFinder photoFinder;
    private final Session session;

    /** Creates the controller. */
    public VehicleController(Database db, FleetService fleet, VpicClient vpic, CarImageStore store,
            PhotoFinder photoFinder, Session session) {
        this.session = session;
        this.db = db;
        this.fleet = fleet;
        this.vpic = vpic;
        this.store = store;
        this.photoFinder = photoFinder;
    }

    // ---------- the fleet ----------

    /** Returns the vehicles that are in the fleet now. */
    public List<Vehicle> vehicles() throws ControllerException {
        return Guard.database("loading the vehicles", db::getAllVehicles);
    }

    /**
     * Reloads the fleet in the background and puts it in items(). A problem is put in the status
     * property.
     */
    public void refresh() {
        runTask(SLOT_FLEET, this::vehicles, items()::setAll, null);
    }

    /** Returns the vehicles the owner removed. */
    public List<Vehicle> removedVehicles() throws ControllerException {
        return Guard.database("loading the removed vehicles", db::getRemovedVehicles);
    }

    /** Returns the vehicle in the fleet with this plate, or null. */
    public Vehicle findByPlate(String plate) throws ControllerException {
        return Guard.database("saving the vehicle", () -> db.findVehicleByPlate(plate));
    }

    /** Saves a new vehicle (owner only) and returns its id. */
    public int add(Vehicle vehicle) throws ControllerException {
        session.requireOwner("add vehicles");
        return Guard.database("saving the vehicle", () -> db.insertVehicle(vehicle));
    }

    /** Saves the changes of a vehicle (owner only). */
    public void update(Vehicle vehicle) throws ControllerException {
        session.requireOwner("edit vehicles");
        Guard.database("saving the vehicle", () -> fleet.updateVehicle(vehicle));
    }

    /** Returns how many active reservations a vehicle has. */
    public int activeReservations(int vehicleId) throws ControllerException {
        return Guard.database("removing the vehicle", () ->
                db.countActiveReservationsOfVehicle(vehicleId));
    }

    /**
     * Removes a vehicle from the fleet (owner only) and returns how many reservations were
     * cancelled.
     */
    public int remove(int vehicleId) throws ControllerException {
        session.requireOwner("remove vehicles");
        return Guard.database("removing the vehicle", () -> fleet.removeVehicle(vehicleId));
    }

    /** Puts a removed vehicle back in the fleet. */
    public void restore(int vehicleId) throws ControllerException {
        session.requireOwner("restore vehicles");
        Guard.database("restoring the vehicle", () -> fleet.restoreVehicle(vehicleId));
    }

    // ---------- maintenance ----------

    /** Returns the maintenance records of a vehicle, newest first. */
    public List<Maintenance> maintenanceOf(int vehicleId) throws ControllerException {
        return Guard.database("loading the maintenance", () ->
                db.getMaintenanceByVehicle(vehicleId));
    }

    /**
     * Saves a maintenance record (owner only). A PMS record also updates the vehicle's last PMS
     * date.
     */
    public void logMaintenance(int vehicleId, LocalDate doneOn, MaintenanceType type, double cost,
            String note) throws ControllerException {
        session.requireOwner("record maintenance");
        Guard.database("saving the maintenance", () -> {
            if (db.getVehicleById(vehicleId) == null) {
                throw new IllegalArgumentException("That vehicle does not exist.");
            }
            if (doneOn != null && doneOn.isAfter(LocalDate.now())) {
                throw new IllegalArgumentException("The date cannot be in the future.");
            }
            db.insertMaintenance(new Maintenance(0, vehicleId, doneOn, type, cost, note));
        });
    }

    // ---------- NHTSA ----------

    /** Asks NHTSA for the makes of a vehicle type. Slow, so call it from a background job. */
    public List<String> fetchMakes(String apiType) throws ControllerException {
        return Guard.web("NHTSA", () -> ApiMapper.makeNames(vpic.fetchMakes(apiType)));
    }

    /**
     * Asks NHTSA for the models of a make that were made in one model year (the vehicle type
     * narrows the list too). A year with no models gives an empty list: a model must not be
     * offered for a year it was not made. Slow, so call it from a background job.
     */
    public Models fetchModels(String make, int year, String apiType) throws ControllerException {
        return Guard.web("NHTSA",
                () -> new Models(vpic.fetchModels(make, Integer.valueOf(year), apiType)));
    }

    /**
     * Asks NHTSA for the makes of a vehicle type in the background. An answer that is still on its
     * way for the models is dropped, because the type changed.
     */
    public void loadMakes(String apiType, Consumer<List<String>> done, Consumer<String> failed) {
        dropPending(SLOT_MODELS);
        runTask(SLOT_MAKES, () -> fetchMakes(apiType), done, failed);
    }

    /** Asks NHTSA for the models of a make, a year and a type in the background. */
    public void loadModels(String make, int year, String apiType, Consumer<Models> done,
            Consumer<String> failed) {
        runTask(SLOT_MODELS, () -> fetchModels(make, year, apiType), done, failed);
    }

    /**
     * Suggests the category of a model picked from NHTSA, or null when the model is not known.
     * NHTSA does not say the category or the transmission, so this is only a starting value.
     */
    public static String suggestStyle(String type, String make, String model) {
        return ModelGuide.suggestStyle(type, make, model);
    }

    // ---------- photos ----------

    /** Tells whether a file name is a picture the app can use. */
    public static boolean isPictureName(String fileName) {
        return CarImageStore.isPictureName(fileName);
    }

    /** Returns the saved photo of a vehicle, or null. */
    public Path photoFile(int vehicleId) {
        return store.find(vehicleId);
    }

    /** Tells whether a vehicle has a saved photo. */
    public boolean hasPhoto(int vehicleId) {
        return store.has(vehicleId);
    }

    /** Deletes the saved photo of a vehicle. */
    public void deletePhoto(int vehicleId) throws ControllerException {
        try {
            store.delete(vehicleId);
        } catch (IOException e) {
            throw new ControllerException(ErrorMessages.forFile("removing the photo", e), e);
        }
    }

    /** Copies a picture file in as the photo of a vehicle. */
    public void savePhoto(int vehicleId, Path source) throws ControllerException {
        try {
            store.copyFrom(vehicleId, source);
        } catch (IllegalArgumentException e) {
            throw new ControllerException(e.getMessage(), e);
        } catch (IOException e) {
            throw new ControllerException(ErrorMessages.forFile("saving the photo", e), e);
        }
    }

    /** What a photo search found. */
    public enum Photo {
        /** No picture was found. */
        NONE,
        /** The usual photo of the model was saved (it may show another color). */
        USUAL,
        /** A photo in the color of the vehicle was saved. */
        IN_COLOR
    }

    /**
     * Looks online for a photo and saves it: first one in the color of the vehicle, then the
     * usual photo of the model.
     */
    public Photo findPhoto(Vehicle vehicle) throws ControllerException {
        return Guard.web("Wikipedia",
                () -> Photo.valueOf(photoFinder.search(vehicle).name()));
    }

    /**
     * Looks for a photo of each vehicle in the list. The progress callback gets (done, total)
     * before each search. Returns {found, failed, found in the color of the vehicle}. When every
     * search failed it throws.
     */
    public int[] findPhotos(List<Vehicle> vehicles, BiConsumer<Integer, Integer> progress)
            throws ControllerException {
        int found = 0;
        int inColor = 0;
        int failed = 0;
        int done = 0;
        ControllerException last = null;
        for (Vehicle v : vehicles) {
            done++;
            progress.accept(done, vehicles.size());
            try {
                Photo photo = findPhoto(v);
                if (photo != Photo.NONE) {
                    found++;
                }
                if (photo == Photo.IN_COLOR) {
                    inColor++;
                }
            } catch (ControllerException e) {
                failed++;
                last = e;
                LOG.warning("[photo] " + v + ": " + e.getMessage());
            }
        }
        if (failed == vehicles.size() && last != null) {
            throw last;
        }
        return new int[] {found, failed, inColor};
    }

    /** Looks for a photo of one vehicle in the background. done gets what was found. */
    public void findPhotoLater(Vehicle vehicle, Consumer<Photo> done, Consumer<String> failed) {
        runTask(null, () -> findPhoto(vehicle), done, failed);
    }

    /**
     * Looks for a photo of each vehicle in the background. progress gets (done, total) on the
     * JavaFX thread before each search; done gets {found, failed, found in the color}.
     */
    public void findPhotosLater(List<Vehicle> vehicles, BiConsumer<Integer, Integer> progress,
            Consumer<int[]> done, Consumer<String> failed) {
        runTask(null, () -> findPhotos(vehicles, (count, total) ->
                Platform.runLater(() -> progress.accept(count, total))), done, failed);
    }
}
