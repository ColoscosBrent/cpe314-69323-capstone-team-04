package fleetrent.controller;

import fleetrent.dao.Database;
import fleetrent.service.AuthService;
import fleetrent.service.BookingService;
import fleetrent.service.CarImageClient;
import fleetrent.service.CarImageStore;
import fleetrent.service.FleetService;
import fleetrent.service.PhotoFinder;
import fleetrent.service.RentalService;
import fleetrent.service.VpicClient;

import java.sql.SQLException;

/**
 * The controllers of one window. A window opens its own connection to the database file and builds
 * every controller from it, so two windows (an owner and a renter) stay in sync through the file.
 * The screens only ever see the controllers returned here.
 */
public final class AppController {
    private final Database db;
    private final AuthController auth;
    private final VehicleController vehicles;
    private final RentalController rentals;
    private final ReservationController reservations;
    private final RenterController renter;
    private final ImportController importer;
    private final DashboardController dashboard;

    private AppController(Database db) {
        this.db = db;
        CarImageStore store = new CarImageStore();
        VpicClient vpic = new VpicClient();
        Session session = new Session();
        this.auth = new AuthController(new AuthService(db), session);
        this.vehicles = new VehicleController(db, new FleetService(db), vpic, store,
                new PhotoFinder(new CarImageClient(), store), session);
        this.rentals = new RentalController(db, new RentalService(db));
        this.reservations = new ReservationController(db, new BookingService(db));
        this.renter = new RenterController(db, new BookingService(db));
        this.importer = new ImportController(db, session);
        this.dashboard = new DashboardController(db, vpic);
    }

    /**
     * Opens the database file (it is created when it does not exist) and builds the controllers.
     */
    public static AppController open(String url) throws ControllerException {
        Database db = null;
        try {
            db = new Database(url);
            db.createTables();
            return new AppController(db);
        } catch (SQLException e) {
            if (db != null) {
                try {
                    db.close();
                } catch (SQLException ignored) {
                    // The first problem is the one to show.
                }
            }
            throw ControllerException.database("opening the database", e);
        }
    }

    /** The controller of the login screen. */
    public AuthController auth() {
        return auth;
    }

    /** The controller of the owner's Vehicles tab. */
    public VehicleController vehicles() {
        return vehicles;
    }

    /** The controller of the owner's Rentals tab. */
    public RentalController rentals() {
        return rentals;
    }

    /** The controller of the owner's Reservations tab. */
    public ReservationController reservations() {
        return reservations;
    }

    /** The controller of the renter's screens. */
    public RenterController renter() {
        return renter;
    }

    /** The controller of the owner's Import tab. */
    public ImportController importer() {
        return importer;
    }

    /** The controller of the owner's Dashboard tab. */
    public DashboardController dashboard() {
        return dashboard;
    }

    /** Tells a screen when the saved data changed (by this window or another one). */
    public ChangeSource changes() {
        return () -> {
            try {
                return db.changeStamp();
            } catch (SQLException e) {
                throw ControllerException.database("checking for changes", e);
            }
        };
    }

    /** Closes the database connection of this window. */
    public void close() {
        try {
            db.close();
        } catch (SQLException e) {
            // The window is closing anyway.
        }
    }
}
