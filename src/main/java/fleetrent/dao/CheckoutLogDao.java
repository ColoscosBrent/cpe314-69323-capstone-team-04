package fleetrent.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes the CheckoutLog table (check-outs that can be undone).
 */
public class CheckoutLogDao extends BaseDao {
    /** Creates the DAO on an open connection. */
    public CheckoutLogDao(Connection connection) {
        super(connection);
    }

    /** Remembers a check-out so it can be undone later, even after a restart. */
    public void log(int rentalId) throws SQLException {
        try (PreparedStatement ps =
                connection.prepareStatement("INSERT INTO CheckoutLog (rentalId) VALUES (?)")) {
            ps.setInt(1, rentalId);
            ps.executeUpdate();
        }
    }

    /** Returns the logged rental ids, oldest first. */
    public List<Integer> getAll() throws SQLException {
        List<Integer> ids = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT rentalId FROM CheckoutLog ORDER BY id")) {
            while (rs.next()) {
                ids.add(rs.getInt(1));
            }
        }
        return ids;
    }

    /** Forgets a logged check-out. */
    public void remove(int rentalId) throws SQLException {
        try (PreparedStatement ps =
                connection.prepareStatement("DELETE FROM CheckoutLog WHERE rentalId = ?")) {
            ps.setInt(1, rentalId);
            ps.executeUpdate();
        }
    }
}
