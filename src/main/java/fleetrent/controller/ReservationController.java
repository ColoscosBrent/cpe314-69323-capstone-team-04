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

/**
 * What the owner's Reservations tab asks for: the reservations that renters made, handing a
 * vehicle over (it becomes a rental) and cancelling a reservation.
 */
public class ReservationController {
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

    /** Creates the controller. */
    public ReservationController(Database db, BookingService booking) {
        this.db = db;
        this.booking = booking;
    }

    /** Loads the data of the tab. */
    public Data load() throws ControllerException {
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
        return Guard.database("handing over the vehicle", () -> booking.handOver(reservationId));
    }

    /** Cancels a reservation on behalf of the owner. */
    public void cancel(int reservationId) throws ControllerException {
        Guard.database("cancelling the reservation",
                () -> booking.cancelReservationByOwner(reservationId));
    }
}
