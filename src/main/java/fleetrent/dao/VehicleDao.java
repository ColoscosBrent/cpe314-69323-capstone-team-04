package fleetrent.dao;

import fleetrent.model.Vehicle;
import fleetrent.model.VehicleFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes the Vehicle table.
 */
public class VehicleDao extends BaseDao {
    private static final String VEHICLE_COLUMNS =
            "id, type, make, model, year, plateNumber, color, dailyRate, mileage, lastPMS, "
                    + "categoryPath";

    /** Creates the DAO on an open connection. */
    public VehicleDao(Connection connection) {
        super(connection);
    }

    /**
     * Saves a new vehicle. Throws DuplicatePlateException when its plate is already in the fleet.
     */
    public int insert(Vehicle v) throws SQLException {
        String sql = "INSERT INTO Vehicle (type, make, model, year, plateNumber, color, dailyRate, "
                + "mileage, lastPMS, categoryPath) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, v.getType());
            ps.setString(2, v.getMake());
            ps.setString(3, v.getModel());
            if (v.getYear() > 0) {
                ps.setInt(4, v.getYear());
            } else {
                ps.setNull(4, java.sql.Types.INTEGER);
            }
            ps.setString(5, v.getPlateNumber());
            ps.setString(6, v.getColor());
            ps.setDouble(7, v.getDailyRate());
            ps.setInt(8, v.getMileage());
            ps.setString(9, v.getLastPMS() == null ? null : v.getLastPMS().toString());
            ps.setString(10, v.getCategoryPath());
            ps.executeUpdate();
            int newId = readGeneratedId(ps);
            v.setId(newId);
            return newId;
        } catch (SQLException e) {
            throw duplicatePlateOrSame(e, v.getPlateNumber());
        }
    }

    // Turns the unique-index error of the plate rule into a DuplicatePlateException. Any other
    // database error is returned as it is.
    private static SQLException duplicatePlateOrSame(SQLException e, String plate) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.contains("UNIQUE constraint failed") && message.contains("idxVehiclePlate")) {
            return new DuplicatePlateException(plate, e);
        }
        return e;
    }

    /** Returns the vehicles that are in the fleet (not removed). */
    public List<Vehicle> getAll() throws SQLException {
        return query("WHERE active = 1");
    }

    /**
     * Every vehicle ever added, removed ones too. The dashboard and the history lists use it, so
     * old rentals of a removed vehicle still count and still show the vehicle.
     */
    public List<Vehicle> getAllIncludingRemoved() throws SQLException {
        return query("");
    }

    /**
     * Vehicles the owner removed from the fleet. They keep their rental history.
     */
    public List<Vehicle> getRemoved() throws SQLException {
        return query("WHERE active = 0");
    }

    private List<Vehicle> query(String where) throws SQLException {
        String sql = "SELECT " + VEHICLE_COLUMNS + " FROM Vehicle " + where + " ORDER BY id";
        List<Vehicle> vehicles = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                vehicles.add(readVehicle(rs));
            }
        }
        return vehicles;
    }

    /**
     * True while the vehicle is in the fleet (not removed). False for a removed or unknown vehicle.
     */
    public boolean isActive(int vehicleId) throws SQLException {
        try (PreparedStatement ps =
                connection.prepareStatement("SELECT active FROM Vehicle WHERE id = ?")) {
            ps.setInt(1, vehicleId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        }
    }

    /**
     * Removes (false) or restores (true) a vehicle. The row stays, so old rentals still point to
     * it.
     */
    public void setActive(int vehicleId, boolean active) throws SQLException {
        try (PreparedStatement ps =
                connection.prepareStatement("UPDATE Vehicle SET active = ? WHERE id = ?")) {
            ps.setInt(1, active ? 1 : 0);
            ps.setInt(2, vehicleId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw duplicatePlateOrSame(e, "of this vehicle");
        }
    }

    /**
     * Saves the edited details of a vehicle. The type is not changed here.
     */
    public void update(Vehicle v) throws SQLException {
        String sql =
                "UPDATE Vehicle SET make = ?, model = ?, year = ?, plateNumber = ?, color = ?, "
                + "dailyRate = ?, mileage = ?, lastPMS = ?, categoryPath = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, v.getMake());
            ps.setString(2, v.getModel());
            if (v.getYear() > 0) {
                ps.setInt(3, v.getYear());
            } else {
                ps.setNull(3, java.sql.Types.INTEGER);
            }
            ps.setString(4, v.getPlateNumber());
            ps.setString(5, v.getColor());
            ps.setDouble(6, v.getDailyRate());
            ps.setInt(7, v.getMileage());
            ps.setString(8, v.getLastPMS() == null ? null : v.getLastPMS().toString());
            ps.setString(9, v.getCategoryPath());
            ps.setInt(10, v.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw duplicatePlateOrSame(e, v.getPlateNumber());
        }
    }

    private Vehicle readVehicle(ResultSet rs) throws SQLException {
        String pms = rs.getString("lastPMS");
        return VehicleFactory.create(rs.getInt("id"), rs.getString("type"),
                rs.getString("make"), rs.getString("model"), rs.getInt("year"),
                rs.getString("plateNumber"), rs.getString("color"), rs.getDouble("dailyRate"),
                rs.getInt("mileage"), pms == null ? null : LocalDate.parse(pms),
                rs.getString("categoryPath"));
    }

    /**
     * Returns null when no vehicle has that id.
     */
    public Vehicle getById(int id) throws SQLException {
        String sql = "SELECT " + VEHICLE_COLUMNS + " FROM Vehicle WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readVehicle(rs) : null;
            }
        }
    }

    /**
     * Plate numbers are compared without case. Only vehicles still in the fleet count. Returns null
     * when there is no match.
     */
    public Vehicle findByPlate(String plate) throws SQLException {
        String sql = "SELECT " + VEHICLE_COLUMNS
                + " FROM Vehicle WHERE UPPER(plateNumber) = UPPER(?) AND active = 1";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, plate == null ? "" : plate.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readVehicle(rs) : null;
            }
        }
    }

    /** Adds the miles of a returned rental to the vehicle. */
    public void addMileage(int vehicleId, int miles) throws SQLException {
        String sql = "UPDATE Vehicle SET mileage = mileage + ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, miles);
            ps.setInt(2, vehicleId);
            ps.executeUpdate();
        }
    }

    /** Moves the last PMS date forward to the given day. It never moves it backward. */
    public void moveLastPmsForward(int vehicleId, LocalDate doneOn) throws SQLException {
        String upd = "UPDATE Vehicle SET lastPMS = ? WHERE id = ? "
                + "AND (lastPMS IS NULL OR lastPMS < ?)";
        try (PreparedStatement ps = connection.prepareStatement(upd)) {
            ps.setString(1, doneOn.toString());
            ps.setInt(2, vehicleId);
            ps.setString(3, doneOn.toString());
            ps.executeUpdate();
        }
    }
}
