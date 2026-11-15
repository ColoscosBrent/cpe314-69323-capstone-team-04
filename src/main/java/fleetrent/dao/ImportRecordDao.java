package fleetrent.dao;

import fleetrent.model.ImportRecord;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes the ImportRecord table (one row per line of an imported file).
 */
public class ImportRecordDao extends BaseDao {
    /** Creates the DAO on an open connection. */
    public ImportRecordDao(Connection connection) {
        super(connection);
    }

    /**
     * Saves the rows of one import with one batch statement. The caller opens and closes the
     * transaction.
     */
    public void insertAll(int batchId, List<ImportRecord> records) throws SQLException {
        String sql = "INSERT INTO ImportRecord (batchId, lineNo, plateNumber, vehicleType, make, "
                + "model, year, renterName, startDate, endDate, milesDriven, status, message) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (ImportRecord r : records) {
                ps.setInt(1, batchId);
                ps.setInt(2, r.lineNo());
                ps.setString(3, r.plateNumber());
                ps.setString(4, r.vehicleType());
                ps.setString(5, r.make());
                ps.setString(6, r.model());
                ps.setObject(7, r.year());
                ps.setString(8, r.renterName());
                ps.setString(9, r.startDate() == null ? null : r.startDate().toString());
                ps.setString(10, r.endDate() == null ? null : r.endDate().toString());
                ps.setObject(11, r.milesDriven());
                ps.setString(12, r.added() ? "ADDED" : "SKIPPED");
                ps.setString(13, r.message());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * The rows of one import in file order. Pass "ADDED" or "SKIPPED" to see only those rows, or
     * null for all. At most limit rows are returned.
     */
    public List<ImportRecord> getRecords(int batchId, String status, int limit)
            throws SQLException {
        String sql = "SELECT id, batchId, lineNo, plateNumber, vehicleType, make, model, year, "
                + "renterName, startDate, endDate, milesDriven, status, message FROM ImportRecord "
                + "WHERE batchId = ? AND (? IS NULL OR status = ?) ORDER BY lineNo LIMIT ?";
        List<ImportRecord> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, batchId);
            ps.setString(2, status);
            ps.setString(3, status);
            ps.setInt(4, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(readImportRecord(rs));
                }
            }
        }
        return rows;
    }

    private ImportRecord readImportRecord(ResultSet rs) throws SQLException {
        String start = rs.getString("startDate");
        String end = rs.getString("endDate");
        return new ImportRecord(rs.getInt("id"), rs.getInt("batchId"), rs.getInt("lineNo"),
                rs.getString("plateNumber"), rs.getString("vehicleType"), rs.getString("make"),
                rs.getString("model"), (Integer) rs.getObject("year"), rs.getString("renterName"),
                start == null ? null : LocalDate.parse(start),
                end == null ? null : LocalDate.parse(end), (Integer) rs.getObject("milesDriven"),
                "ADDED".equals(rs.getString("status")), rs.getString("message"));
    }
}
