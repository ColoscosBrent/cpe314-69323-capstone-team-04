package fleetrent.service;

import fleetrent.model.Rental;
import fleetrent.model.Renter;
import fleetrent.model.Vehicle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Turns the saved rentals into CSV lines: the rentals that were imported and the rentals made in
 * the app, nothing else. A value the app does not have (for example a renter with no email) is
 * left blank. The lines can be imported again with RentalCsvImporter.
 */
public final class RentalCsvExporter {
    private RentalCsvExporter() {
    }

    /**
     * The header and one line for every rental, in the order of the rentals. A rental whose renter
     * or vehicle is missing keeps its blanks.
     */
    public static List<String> lines(List<Rental> rentals, Map<Integer, Vehicle> vehicles,
            Map<Integer, Renter> renters) {
        List<String> lines = new ArrayList<>();
        lines.add(RentalCsvImporter.HEADER);
        for (Rental r : rentals) {
            Vehicle v = vehicles.get(r.getVehicleId());
            Renter renter = renters.get(r.getRenterId());
            lines.add(CsvLine.join(Arrays.asList(
                    v == null ? null : v.getPlateNumber(),
                    v == null ? null : v.getType(),
                    v == null ? null : v.getMake(),
                    v == null ? null : v.getModel(),
                    v == null || v.getYear() <= 0 ? null : String.valueOf(v.getYear()),
                    renter == null ? null : renter.getName(),
                    renter == null ? null : renter.getContact(),
                    renter == null ? null : renter.getEmail(),
                    renter == null ? null : renter.getAddress(),
                    String.valueOf(r.getStartDate()), String.valueOf(r.getEndDate()),
                    String.valueOf(r.getMilesDriven()), r.getStatus().name())));
        }
        return lines;
    }
}
