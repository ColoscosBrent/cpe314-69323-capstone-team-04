package fleetrent.model;

import java.time.LocalDate;

/**
 * One row of an imported CSV file as the owner sees it in the Import history: the car (type, make,
 * model, year, plate), the renter, the dates, the miles, and whether the row was saved or skipped
 * (with the reason). Blank values stay null.
 */
public record ImportRecord(int id, int batchId, int lineNo, String plateNumber, String vehicleType,
        String make, String model, Integer year, String renterName, LocalDate startDate,
        LocalDate endDate, Integer milesDriven, boolean added, String message) {

    /** The status word shown in the table: Saved or Skipped. */
    public String statusLabel() {
        return added ? "Saved" : "Skipped";
    }
}
