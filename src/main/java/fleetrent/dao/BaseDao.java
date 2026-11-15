package fleetrent.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Shared part of the DAO classes. A DAO runs the SQL of one table on the connection of its
 * {@link Database}. The Database holds the lock, so a DAO does not lock by itself.
 */
abstract class BaseDao {
    /** The open connection of the Database that made this DAO. */
    protected final Connection connection;

    BaseDao(Connection connection) {
        this.connection = connection;
    }

    /** Returns the id SQLite gave to the row that the statement just inserted. */
    protected int readGeneratedId(PreparedStatement ps) throws SQLException {
        try (Statement st = ps.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        throw new SQLException("No id was returned after the insert");
    }
}
