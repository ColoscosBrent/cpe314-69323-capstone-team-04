package fleetrent.dao;

import fleetrent.model.BusySpan;
import fleetrent.model.Rental;
import fleetrent.model.RentalStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes the Rental table.
 */
public class RentalDao extends BaseDao {
    private static final String RENTAL_COLUMNS =
            "id, renterId, vehicleId, startDate, endDate, returnedOn, milesDriven, status, "
                    + "processedBy";

    /** Creates the DAO on an open connection. */
    public RentalDao(Connection connection) {
        super(connection);
    }

    /** Saves a new rental and gives it its new id. */
    public int insert(Rental r) throws SQLException {
        String sql = "INSERT INTO Rental (renterId, vehicleId, startDate, endDate, returnedOn, "
                + "milesDriven, status, processedBy) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, r.getRenterId());
            ps.setInt(2, r.getVehicleId());
            ps.setString(3, r.getStartDate().toString());
            ps.setString(4, r.getEndDate().toString());
            ps.setString(5, r.getReturnedOn() == null ? null : r.getReturnedOn().toString());
            ps.setInt(6, r.getMilesDriven());
            ps.setString(7, r.getStatus().name());
            if (r.getProcessedBy() == null) {
                ps.setNull(8, java.sql.Types.INTEGER);
            } else {
                ps.setInt(8, r.getProcessedBy());
            }
            ps.executeUpdate();
            int newId = readGeneratedId(ps);
            r.setId(newId);
            return newId;
        }
    }

    /** Returns every rental of one renter. */
    public List<Rental> getByRenter(int renterId) throws SQLException {
        String sql = "SELECT " + RENTAL_COLUMNS + " FROM Rental WHERE renterId = ? ORDER BY id";
        List<Rental> rentals = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, renterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rentals.add(readRental(rs));
                }
            }
        }
        return rentals;
    }

    private Rental readRental(ResultSet rs) throws SQLException {
        String returned = rs.getString("returnedOn");
        Rental rental = new Rental(rs.getInt("id"), rs.getInt("renterId"),
                rs.getInt("vehicleId"), LocalDate.parse(rs.getString("startDate")),
                LocalDate.parse(rs.getString("endDate")),
                returned == null ? null : LocalDate.parse(returned),
                rs.getInt("milesDriven"), RentalStatus.valueOf(rs.getString("status")));
        int staff = rs.getInt("processedBy");
        rental.setProcessedBy(rs.wasNull() ? null : staff);
        return rental;
    }

    /** Returns every rental. */
    public List<Rental> getAll() throws SQLException {
        String sql = "SELECT " + RENTAL_COLUMNS + " FROM Rental ORDER BY id";
        List<Rental> rentals = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rentals.add(readRental(rs));
            }
        }
        return rentals;
    }

    /**
     * Returns null when no rental has that id.
     */
    public Rental getById(int id) throws SQLException {
        String sql = "SELECT " + RENTAL_COLUMNS + " FROM Rental WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readRental(rs) : null;
            }
        }
    }

    /** Tells whether the vehicle is rented out right now. */
    public boolean hasOngoing(int vehicleId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM Rental WHERE vehicleId = ? AND status = 'ONGOING'";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, vehicleId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Saves the return details of a rental (return date, miles, status).
     */
    public void update(Rental r) throws SQLException {
        String sql = "UPDATE Rental SET returnedOn = ?, milesDriven = ?, status = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, r.getReturnedOn() == null ? null : r.getReturnedOn().toString());
            ps.setInt(2, r.getMilesDriven());
            ps.setString(3, r.getStatus().name());
            ps.setInt(4, r.getId());
            ps.executeUpdate();
        }
    }

    /** Deletes one rental (used by Undo). */
    public void delete(int id) throws SQLException {
        try (PreparedStatement ps =
                connection.prepareStatement("DELETE FROM Rental WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    /**
     * True when an ONGOING rental touches any day from start to end. A rental that is late (its end
     * date has passed and it is not returned) still blocks the vehicle until today.
     */
    public boolean hasOverlap(int vehicleId, LocalDate start, LocalDate end,
            LocalDate today)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM Rental WHERE vehicleId = ? AND status = 'ONGOING' "
                + "AND startDate <= ? AND MAX(endDate, ?) >= ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, vehicleId);
            ps.setString(2, end.toString());
            ps.setString(3, today.toString());
            ps.setString(4, start.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /** Adds the days of every ongoing rental to the map (a late rental counts until today). */
    public void addBusySpans(LocalDate today, Map<Integer, List<BusySpan>> map)
            throws SQLException {
        String sql = "SELECT vehicleId, startDate, MAX(endDate, ?) AS endDay FROM Rental "
                + "WHERE status = 'ONGOING' ORDER BY startDate";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, today.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (rs.getString("startDate") == null) {
                        continue;
                    }
                    map.computeIfAbsent(rs.getInt("vehicleId"), k -> new ArrayList<>())
                            .add(new BusySpan(LocalDate.parse(rs.getString("startDate")),
                                    LocalDate.parse(rs.getString("endDay")), "RENTED"));
                }
            }
        }
    }
}
