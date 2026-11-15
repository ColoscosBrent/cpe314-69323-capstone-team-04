package fleetrent.dao;

import fleetrent.model.BusySpan;
import fleetrent.model.Reservation;
import fleetrent.model.ReservationStatus;
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
 * Reads and writes the Reservation table.
 */
public class ReservationDao extends BaseDao {
    private static final String RESERVATION_COLUMNS =
            "id, renterId, vehicleId, startDate, endDate, status, note, createdOn";

    /** Creates the DAO on an open connection. */
    public ReservationDao(Connection connection) {
        super(connection);
    }

    private Reservation readReservation(ResultSet rs) throws SQLException {
        return new Reservation(rs.getInt("id"), rs.getInt("renterId"), rs.getInt("vehicleId"),
                LocalDate.parse(rs.getString("startDate")),
                        LocalDate.parse(rs.getString("endDate")),
                ReservationStatus.valueOf(rs.getString("status")), rs.getString("note"),
                LocalDate.parse(rs.getString("createdOn")));
    }

    /** Saves a new reservation. */
    public int insert(Reservation r) throws SQLException {
        String sql =
                "INSERT INTO Reservation (renterId, vehicleId, startDate, endDate, status, note, "
                + "createdOn) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, r.getRenterId());
            ps.setInt(2, r.getVehicleId());
            ps.setString(3, r.getStartDate().toString());
            ps.setString(4, r.getEndDate().toString());
            ps.setString(5, r.getStatus().name());
            ps.setString(6, r.getNote());
            ps.setString(7, r.getCreatedOn().toString());
            ps.executeUpdate();
            int newId = readGeneratedId(ps);
            r.setId(newId);
            return newId;
        }
    }

    /** Returns the reservation with this id, or null. */
    public Reservation getById(int id) throws SQLException {
        String sql = "SELECT " + RESERVATION_COLUMNS + " FROM Reservation WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readReservation(rs) : null;
            }
        }
    }

    /** Returns every reservation. */
    public List<Reservation> getAll() throws SQLException {
        return query("", null);
    }

    /** Returns every reservation of one renter. */
    public List<Reservation> getByRenter(
            int renterId) throws SQLException {
        return query("WHERE renterId = ?", renterId);
    }

    private List<Reservation> query(String where, Integer param) throws SQLException {
        String sql = "SELECT " + RESERVATION_COLUMNS + " FROM Reservation " + where
                + " ORDER BY startDate, id";
        List<Reservation> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (param != null) {
                ps.setInt(1, param);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(readReservation(rs));
                }
            }
        }
        return list;
    }

    /** Changes the status of a reservation. */
    public void updateStatus(int id, ReservationStatus status,
            String note) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE Reservation SET status = ?, note = ? WHERE id = ?")) {
            ps.setString(1, status.name());
            ps.setString(2, note);
            ps.setInt(3, id);
            ps.executeUpdate();
        }
    }

    /**
     * Cancels every ACTIVE reservation of a vehicle. Returns how many were cancelled.
     */
    public int cancelActiveOfVehicle(int vehicleId,
            String note) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE Reservation SET status = 'CANCELLED', note = ? WHERE vehicleId = ? AND "
                        + "status = 'ACTIVE'")) {
            ps.setString(1, note);
            ps.setInt(2, vehicleId);
            return ps.executeUpdate();
        }
    }

    /** Counts the active reservations of a vehicle. */
    public int countActiveOfVehicle(int vehicleId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM Reservation WHERE vehicleId = ? AND status = 'ACTIVE'")) {
            ps.setInt(1, vehicleId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * True when an ACTIVE reservation of the vehicle touches any day from start to end (both
     * included).
     */
    public boolean hasOverlap(int vehicleId, LocalDate start,
            LocalDate end) throws SQLException {
        String sql = "SELECT COUNT(*) FROM Reservation WHERE vehicleId = ? AND status = 'ACTIVE' "
                + "AND startDate <= ? AND endDate >= ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, vehicleId);
            ps.setString(2, end.toString());
            ps.setString(3, start.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /** Adds the days of every active reservation that is not over yet to the map. */
    public void addBusySpans(LocalDate today, Map<Integer, List<BusySpan>> map)
            throws SQLException {
        String sql = "SELECT vehicleId, startDate, endDate FROM Reservation "
                + "WHERE status = 'ACTIVE' AND endDate >= ? ORDER BY startDate";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, today.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.computeIfAbsent(rs.getInt("vehicleId"), k -> new ArrayList<>())
                            .add(new BusySpan(LocalDate.parse(rs.getString("startDate")),
                                    LocalDate.parse(rs.getString("endDate")), "RESERVED"));
                }
            }
        }
    }
}
