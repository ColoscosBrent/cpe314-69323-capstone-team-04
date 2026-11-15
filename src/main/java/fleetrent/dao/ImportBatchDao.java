package fleetrent.dao;

import fleetrent.model.ImportBatch;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes the ImportBatch table (one row per imported file).
 */
public class ImportBatchDao extends BaseDao {
    /** Creates the DAO on an open connection. */
    public ImportBatchDao(Connection connection) {
        super(connection);
    }

    /**
     * Saves a new, empty import in the history and returns its id. The file hash (may be null)
     * is the fingerprint of the file content, used to find a file that was imported before.
     */
    public int insert(String fileName, String importedBy, LocalDateTime importedOn,
            String fileHash) throws SQLException {
        String sql = "INSERT INTO ImportBatch (fileName, importedBy, importedOn, fileHash) "
                + "VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, fileName);
            ps.setString(2, importedBy);
            ps.setString(3, importedOn.toString());
            ps.setString(4, fileHash);
            ps.executeUpdate();
            return readGeneratedId(ps);
        }
    }

    /** Returns the first import of a file with this content, or null when there is none. */
    public ImportBatch findByHash(String fileHash) throws SQLException {
        String sql = "SELECT id, fileName, importedBy, importedOn, rowsRead, rowsAdded, "
                + "rowsSkipped FROM ImportBatch WHERE fileHash = ? ORDER BY id LIMIT 1";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, fileHash);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? read(rs) : null;
            }
        }
    }

    private ImportBatch read(ResultSet rs) throws SQLException {
        return new ImportBatch(rs.getInt("id"), rs.getString("fileName"),
                rs.getString("importedBy"), LocalDateTime.parse(rs.getString("importedOn")),
                rs.getInt("rowsRead"), rs.getInt("rowsAdded"), rs.getInt("rowsSkipped"));
    }

    /** Writes the final counts of an import. */
    public void finish(int batchId, int rowsRead, int rowsAdded,
            int rowsSkipped) throws SQLException {
        String sql = "UPDATE ImportBatch SET rowsRead = ?, rowsAdded = ?, rowsSkipped = ? "
                + "WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, rowsRead);
            ps.setInt(2, rowsAdded);
            ps.setInt(3, rowsSkipped);
            ps.setInt(4, batchId);
            ps.executeUpdate();
        }
    }

    /** Every import, the newest first. */
    public List<ImportBatch> getAll() throws SQLException {
        String sql = "SELECT id, fileName, importedBy, importedOn, rowsRead, rowsAdded, "
                + "rowsSkipped FROM ImportBatch ORDER BY id DESC";
        List<ImportBatch> batches = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                batches.add(read(rs));
            }
        }
        return batches;
    }
}
