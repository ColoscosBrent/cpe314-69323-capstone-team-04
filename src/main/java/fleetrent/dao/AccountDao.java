package fleetrent.dao;

import fleetrent.model.Account;
import fleetrent.model.Role;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

/**
 * Reads and writes the Account table.
 */
public class AccountDao extends BaseDao {
    private static final String ACCOUNT_COLUMNS =
            "id, username, role, displayName, phone, passwordHash, passwordSalt, "
            + "securityQuestion, answerHash, answerSalt, renterId, createdOn";

    /** Creates the DAO on an open connection. */
    public AccountDao(Connection connection) {
        super(connection);
    }

    private Account readAccount(ResultSet rs) throws SQLException {
        int renter = rs.getInt("renterId");
        Integer renterId = rs.wasNull() ? null : renter;
        return new Account(rs.getInt("id"), rs.getString("username"),
                Role.valueOf(rs.getString("role")),
                rs.getString("displayName"), rs.getString("phone"), rs.getString("passwordHash"),
                rs.getString("passwordSalt"),
                rs.getString("securityQuestion"), rs.getString("answerHash"),
                rs.getString("answerSalt"),
                renterId, LocalDate.parse(rs.getString("createdOn")));
    }

    /** Saves a new account and gives it its new id. */
    public int insert(Account a) throws SQLException {
        String sql =
                "INSERT INTO Account (username, role, displayName, phone, passwordHash, "
                + "passwordSalt, securityQuestion, answerHash, answerSalt, renterId, createdOn) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, a.getUsername());
            ps.setString(2, a.getRole().name());
            ps.setString(3, a.getDisplayName());
            ps.setString(4, a.getPhone());
            ps.setString(5, a.getPasswordHash());
            ps.setString(6, a.getPasswordSalt());
            ps.setString(7, a.getSecurityQuestion());
            ps.setString(8, a.getAnswerHash());
            ps.setString(9, a.getAnswerSalt());
            if (a.getRenterId() == null) {
                ps.setNull(10, java.sql.Types.INTEGER);
            } else {
                ps.setInt(10, a.getRenterId());
            }
            ps.setString(11, a.getCreatedOn().toString());
            ps.executeUpdate();
            int newId = readGeneratedId(ps);
            a.setId(newId);
            return newId;
        }
    }

    /**
     * The username is compared without case. Returns null when there is no such account.
     */
    public Account findByUsername(String username) throws SQLException {
        String sql = "SELECT " + ACCOUNT_COLUMNS + " FROM Account WHERE username = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username == null ? "" : username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readAccount(rs) : null;
            }
        }
    }

    /** Returns the account with this id, or null. */
    public Account getById(int id) throws SQLException {
        String sql = "SELECT " + ACCOUNT_COLUMNS + " FROM Account WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readAccount(rs) : null;
            }
        }
    }

    /** Saves a new password hash and salt for an account. */
    public void updatePassword(int accountId, String hash,
            String salt) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE Account SET passwordHash = ?, passwordSalt = ? WHERE id = ?")) {
            ps.setString(1, hash);
            ps.setString(2, salt);
            ps.setInt(3, accountId);
            ps.executeUpdate();
        }
    }
}
