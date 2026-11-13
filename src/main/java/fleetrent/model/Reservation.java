package fleetrent.model;


import java.time.LocalDate;

/**
 * A renter holds a vehicle for future dates. It becomes a Rental when the owner hands the vehicle
 * over.
 */
public class Reservation {
    private int id;
    private final int renterId;
    private final int vehicleId;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private ReservationStatus status;
    private String note;
    private final LocalDate createdOn;

    /** Creates a reservation. */
    public Reservation(int id, int renterId, int vehicleId, LocalDate startDate, LocalDate endDate,
                       ReservationStatus status, String note, LocalDate createdOn) {
        this.id = id;
        this.renterId = renterId;
        this.vehicleId = vehicleId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
        this.note = note;
        this.createdOn = createdOn;
    }

    /** Creates a new active reservation that is not saved yet. */
    public static Reservation startNew(int renterId, int vehicleId, LocalDate start, LocalDate end,
                                       LocalDate today) {
        return new Reservation(0, renterId, vehicleId, start, end, ReservationStatus.ACTIVE, null,
                today);
    }

    /** Returns the id. */
    public int getId() { return id; }
    /** Sets the id. */
    public void setId(int id) { this.id = id; }
    /** Returns the renter id. */
    public int getRenterId() { return renterId; }
    /** Returns the vehicle id. */
    public int getVehicleId() { return vehicleId; }
    /** Returns the start date. */
    public LocalDate getStartDate() { return startDate; }
    /** Returns the end date. */
    public LocalDate getEndDate() { return endDate; }
    /** Returns the status. */
    public ReservationStatus getStatus() { return status; }
    /** Sets the status. */
    public void setStatus(ReservationStatus status) { this.status = status; }
    /** Returns the note. */
    public String getNote() { return note; }
    /** Sets the note. */
    public void setNote(String note) { this.note = note; }
    /** Returns the created on. */
    public LocalDate getCreatedOn() { return createdOn; }
}
