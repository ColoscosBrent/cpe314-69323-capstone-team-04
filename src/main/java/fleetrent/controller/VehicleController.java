package fleetrent.controller;

import fleetrent.dao.Database;
import fleetrent.dto.VehicleModelInfo;
import fleetrent.model.Maintenance;
import fleetrent.model.MaintenanceType;
import fleetrent.model.Vehicle;
import fleetrent.service.CarImageStore;
import fleetrent.service.FleetService;
import fleetrent.service.PhotoFinder;
import fleetrent.service.VpicClient;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

/**
 * What the owner's Vehicles tab asks for: the fleet, add, edit, remove and restore a vehicle, the
 * makes and models from NHTSA, and the photos. The tab never talks to the database, the web or
 * the picture folder itself.
 */
public class VehicleController {
    private static final Logger LOG = Logger.getLogger(VehicleController.class.getName());

    /**
     * The models NHTSA lists for a make.
     *
     * @param models   the models found
     * @param fellBack true when none were listed for the chosen year, so the list is for any year
     */
    public record Models(List<VehicleModelInfo> models, boolean fellBack) {
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

    /** Returns the vehicles the owner removed. */
    public List<Vehicle> removedVehicles() throws ControllerException {
        return Guard.database("loading the removed vehicles", db::getRemovedVehicles);
    }

    /** Returns the vehicle in the fleet with this plate, or null. */
    public Vehicle findByPlate(String plate) throws ControllerException {
        return Guard.database("saving the vehicle", () -> db.findVehicleByPlate(plate));
    }

    /** Saves a new vehicle and returns its id. */
    public int add(Vehicle vehicle) throws ControllerException {
        return Guard.database("saving the vehicle", () -> db.insertVehicle(vehicle));
    }

    /** Saves the changes of a vehicle. */
    public void update(Vehicle vehicle) throws ControllerException {
        Guard.database("saving the vehicle", () -> fleet.updateVehicle(vehicle));
    }

    /** Returns how many active reservations a vehicle has. */
    public int activeReservations(int vehicleId) throws ControllerException {
        return Guard.database("removing the vehicle", () -> db.countActiveReservationsOfVehicle(vehicleId));
    }

    /** Removes a vehicle from the fleet (owner only) and returns how many reservations were cancelled. */
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
        return Guard.database("loading the maintenance", () -> db.getMaintenanceByVehicle(vehicleId));
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
        return Guard.web("NHTSA", () -> vpic.fetchMakes(apiType));
    }

    /**
     * Asks NHTSA for the models of a make. When the year has no models, it asks again for any
     * year. Slow, so call it from a background job.
     */
    public Models fetchModels(String make, Integer year, String apiType) throws ControllerException {
        return Guard.web("NHTSA", () -> {
            List<VehicleModelInfo> list = vpic.fetchModels(make, year, apiType);
            boolean fellBack = false;
            if (list.isEmpty() && year != null) {
                list = vpic.fetchModels(make, null, apiType);
                fellBack = true;
            }
            return new Models(list, fellBack);
        });
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
            throw new ControllerException("The photo could not be removed: " + e.getMessage(), e);
        }
    }

    /** Copies a picture file in as the photo of a vehicle. */
    public void savePhoto(int vehicleId, Path source) throws ControllerException {
        try {
            store.copyFrom(vehicleId, source);
        } catch (IOException | IllegalArgumentException e) {
            throw new ControllerException(e.getMessage(), e);
        }
    }

    /** Looks for a photo on Wikipedia and saves it. Returns true when one was found. */
    public boolean findPhoto(Vehicle vehicle) throws ControllerException {
        return Guard.web("Wikipedia", () -> photoFinder.findAndSave(vehicle));
    }

    /**
     * Looks for a photo of each vehicle in the list. The progress callback gets (done, total)
     * before each search. Returns {found, failed}. When every search failed it throws.
     */
    public int[] findPhotos(List<Vehicle> vehicles, BiConsumer<Integer, Integer> progress)
            throws ControllerException {
        int found = 0;
        int failed = 0;
        int done = 0;
        ControllerException last = null;
        for (Vehicle v : vehicles) {
            done++;
            progress.accept(done, vehicles.size());
            try {
                if (findPhoto(v)) {
                    found++;
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
        return new int[] {found, failed};
    }
}
