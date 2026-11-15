package fleetrent.dao;

import fleetrent.model.Maintenance;
import fleetrent.model.MaintenanceType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes the Maintenance table.
 */
public class MaintenanceDao extends BaseDao {
    /** Creates the DAO on an open connection. */
    public MaintenanceDao(Connection connection) {
        super(connection);
    }

    /** Saves a maintenance record and gives it its new id. */
    public int insert(Maintenance m) throws SQLException {
        String sql = "INSERT INTO Maintenance (vehicleId, doneOn, type, cost, note) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, m.getVehicleId());
            ps.setString(2, m.getDoneOn().toString());
            ps.setString(3, m.getType().name());
            ps.setDouble(4, m.getCost());
            ps.setString(5, m.getNote());
            ps.executeUpdate();
            m.setId(readGeneratedId(ps));
            return m.getId();
        }
    }

    /** Returns the maintenance of one vehicle, newest first. */
    public List<Maintenance> getByVehicle(
            int vehicleId) throws SQLException {
        String sql = "SELECT id, vehicleId, doneOn, type, cost, note FROM Maintenance "
                + "WHERE vehicleId = ? ORDER BY doneOn DESC, id DESC";
        List<Maintenance> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, vehicleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Maintenance(rs.getInt("id"), rs.getInt("vehicleId"),
                            LocalDate.parse(rs.getString("doneOn")),
                            MaintenanceType.valueOf(rs.getString("type")), rs.getDouble("cost"),
                            rs.getString("note")));
                }
            }
        }
        return list;
    }
}
