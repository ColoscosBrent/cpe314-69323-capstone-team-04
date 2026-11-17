package fleetrent.dao;

import fleetrent.model.Renter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes the Renter table.
 */
public class RenterDao extends BaseDao {
    /** Creates the DAO on an open connection. */
    public RenterDao(Connection connection) {
        super(connection);
    }

    /** Saves a new renter and gives it its new id. */
    public int insert(Renter renter) throws SQLException {
        String sql = "INSERT INTO Renter (name, contact, email, address) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, renter.getName());
            ps.setString(2, renter.getContact());
            ps.setString(3, renter.getEmail());
            ps.setString(4, renter.getAddress());
            ps.executeUpdate();
            int newId = readGeneratedId(ps);
            renter.setId(newId);
            return newId;
        }
    }

    /** Returns every renter. */
    public List<Renter> getAll() throws SQLException {
        String sql = "SELECT id, name, contact, email, address FROM Renter ORDER BY id";
        List<Renter> renters = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                renters.add(new Renter(rs.getInt("id"), rs.getString("name"),
                        rs.getString("contact"),
                        rs.getString("email"), rs.getString("address")));
            }
        }
        return renters;
    }

    /** Returns the renter with this id, or null. */
    public Renter getById(int id) throws SQLException {
        String sql = "SELECT id, name, contact, email, address FROM Renter WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new Renter(rs.getInt("id"), rs.getString("name"),
                        rs.getString("contact"),
                        rs.getString("email"), rs.getString("address")) : null;
            }
        }
    }
}
