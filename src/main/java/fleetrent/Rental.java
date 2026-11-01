package fleetrent;

import java.time.LocalDate;

// One rental: which renter has which vehicle, and for which days.
public class Rental {
    private int id;
    private final int renterId;
    private final int vehicleId;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private LocalDate returnedOn;
    private int milesDriven;
    private RentalStatus status;

    public Rental(int id, int renterId, int vehicleId, LocalDate startDate, LocalDate endDate,
                  LocalDate returnedOn, int milesDriven, RentalStatus status) {
        this.id = id;
        this.renterId = renterId;
        this.vehicleId = vehicleId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.returnedOn = returnedOn;
        this.milesDriven = milesDriven;
        this.status = status;
    }

    // A new rental starts as ONGOING with no return date yet.
    public static Rental startNew(int renterId, int vehicleId, LocalDate startDate, LocalDate endDate) {
        return new Rental(0, renterId, vehicleId, startDate, endDate, null, 0, RentalStatus.ONGOING);
    }

    // Works for an early return too: the status becomes RETURNED even before endDate.
    public void markReturned(LocalDate returnDate, int milesDriven) {
        this.returnedOn = returnDate;
        this.milesDriven = milesDriven;
        this.status = RentalStatus.RETURNED;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getRenterId() { return renterId; }
    public int getVehicleId() { return vehicleId; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public LocalDate getReturnedOn() { return returnedOn; }
    public int getMilesDriven() { return milesDriven; }
    public RentalStatus getStatus() { return status; }
}
