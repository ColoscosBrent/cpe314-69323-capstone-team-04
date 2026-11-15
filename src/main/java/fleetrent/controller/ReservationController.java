package fleetrent.controller;

import fleetrent.dao.Database;
import fleetrent.model.Rental;
import fleetrent.model.Renter;
import fleetrent.model.Reservation;
import fleetrent.model.Vehicle;
import fleetrent.service.BookingService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * What the owner's Reservations tab asks for: the reservations that renters made, handing a
 * vehicle over (it becomes a rental) and cancelling a reservation.
 */
public class ReservationController extends ObservableController<Reservation> {
    private static final String SLOT_DATA = "data";
    /**
     * Everything the tab shows.
     *
     * @param renters      every renter, by id
     * @param vehicles     every vehicle ever added, by id
     * @param reservations every reservation
     */
    public record Data(Map<Integer, Renter> renters, Map<Integer, Vehicle> vehicles,
            List<Reservation> reservations) {
    }

    private final Database db;
    private final BookingService booking;
    private final Session session;

    /** Creates the controller. Only the owner may use it. */
    public ReservationController(Database db, BookingService booking, Session session) {
        this.db = db;
        this.booking = booking;
        this.session = session;
    }

    /**
     * Reloads the data of the tab in the background. The reservations go into items() and show
     * gets everything. A problem is put in the status property.
     */
    public void refresh(Consumer<Data> show) {
        runTask(SLOT_DATA, this::load, data -> {
            items().setAll(data.reservations());
            show.accept(data);
        }, null);
    }

    /** Loads the data of the tab. */
    public Data load() throws ControllerException {
        session.requireOwner("see the reservations");
        return Guard.database("loading the reservations", () -> {
            Map<Integer, Renter> renters = new HashMap<>();
            for (Renter r : db.getAllRenters()) {
                renters.put(r.getId(), r);
            }
            Map<Integer, Vehicle> vehicles = new HashMap<>();
            for (Vehicle v : db.getAllVehiclesIncludingRemoved()) {
                vehicles.put(v.getId(), v);
            }
            return new Data(renters, vehicles, db.getAllReservations());
        });
    }

    /** Hands the vehicle over: the reservation becomes a rental. */
    public Rental handOver(int reservationId) throws ControllerException {
        session.requireOwner("hand over a vehicle");
        int staff = session.accountId();
        return Guard.database("handing over the vehicle",
                () -> booking.handOver(reservationId, staff));
    }

    /** Cancels a reservation on behalf of the owner. */
    public void cancel(int reservationId) throws ControllerException {
        session.requireOwner("cancel a reservation for a renter");
        Guard.database("cancelling the reservation",
                () -> booking.cancelReservationByOwner(reservationId));
    }
}
