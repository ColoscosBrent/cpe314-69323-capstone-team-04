package fleetrent.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes the WaitingEntry table (the waiting line of a vehicle).
 */
public class WaitingEntryDao extends BaseDao {
    /** Creates the DAO on an open connection. */
    public WaitingEntryDao(Connection connection) {
        super(connection);
    }

    /** Puts a renter at the end of the waiting line of a vehicle. */
    public void add(int vehicleId, int renterId,
            LocalDate joinedOn) throws SQLException {
        String sql = "INSERT INTO WaitingEntry (vehicleId, renterId, joinedOn) VALUES (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, vehicleId);
            ps.setInt(2, renterId);
            ps.setString(3, joinedOn.toString());
            ps.executeUpdate();
        }
    }

    /** Returns the ids of the waiting renters of a vehicle, first in line first. */
    public List<Integer> getRenterIds(int vehicleId) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        String sql = "SELECT renterId FROM WaitingEntry WHERE vehicleId = ? ORDER BY id";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, vehicleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        }
        return ids;
    }

    /** Removes the first waiting entry of a vehicle. Returns false when nobody was waiting. */
    public boolean removeFirst(int vehicleId) throws SQLException {
        String sql = "DELETE FROM WaitingEntry WHERE id = "
                + "(SELECT MIN(id) FROM WaitingEntry WHERE vehicleId = ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, vehicleId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Removes the whole waiting line of a vehicle. */
    public void removeAllOfVehicle(int vehicleId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM WaitingEntry WHERE vehicleId = ?")) {
            ps.setInt(1, vehicleId);
            ps.executeUpdate();
        }
    }

    /** Takes one renter out of the line of a vehicle (the first entry of that renter). */
    public void remove(int vehicleId, int renterId) throws SQLException {
        String sql = "DELETE FROM WaitingEntry WHERE id = (SELECT MIN(id) FROM WaitingEntry "
                + "WHERE vehicleId = ? AND renterId = ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, vehicleId);
            ps.setInt(2, renterId);
            ps.executeUpdate();
        }
    }

    /** Returns the ids of the vehicles one renter waits for, oldest entry first. */
    public List<Integer> getVehicleIds(int renterId) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        String sql = "SELECT vehicleId FROM WaitingEntry WHERE renterId = ? "
                + "GROUP BY vehicleId ORDER BY MIN(id)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, renterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        }
        return ids;
    }
}
