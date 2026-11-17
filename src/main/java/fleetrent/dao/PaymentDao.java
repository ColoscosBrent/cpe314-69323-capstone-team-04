package fleetrent.dao;

import fleetrent.model.Payment;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes the Payment table.
 */
public class PaymentDao extends BaseDao {
    /** Creates the DAO on an open connection. */
    public PaymentDao(Connection connection) {
        super(connection);
    }

    /** Saves a payment and gives it its new id. */
    public int insert(Payment p) throws SQLException {
        String sql = "INSERT INTO Payment (rentalId, amount, lateFee, paidOn) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, p.getRentalId());
            ps.setDouble(2, p.getAmount());
            ps.setDouble(3, p.getLateFee());
            ps.setString(4, p.getPaidOn().toString());
            ps.executeUpdate();
            p.setId(readGeneratedId(ps));
            return p.getId();
        }
    }

    /** Returns the payment of a rental, or null when it has none (for example an imported one). */
    public Payment getByRental(int rentalId) throws SQLException {
        String sql = "SELECT id, rentalId, amount, lateFee, paidOn FROM Payment WHERE rentalId = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, rentalId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readPayment(rs) : null;
            }
        }
    }

    /** Returns every payment, oldest first. */
    public List<Payment> getAll() throws SQLException {
        List<Payment> list = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT id, rentalId, amount, lateFee, paidOn FROM Payment ORDER BY id")) {
            while (rs.next()) {
                list.add(readPayment(rs));
            }
        }
        return list;
    }

    private Payment readPayment(ResultSet rs) throws SQLException {
        return new Payment(rs.getInt("id"), rs.getInt("rentalId"), rs.getDouble("amount"),
                rs.getDouble("lateFee"), LocalDate.parse(rs.getString("paidOn")));
    }
}
