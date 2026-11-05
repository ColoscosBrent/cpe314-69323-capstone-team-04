package fleetrent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// Talks to the SQLite database. Every query with values uses PreparedStatement.
public class Database implements AutoCloseable {
    private final Connection connection;

    // url examples: "jdbc:sqlite:fleetrent.db" (a file) or "jdbc:sqlite::memory:" (for tests)
    public Database(String url) throws SQLException {
        this.connection = DriverManager.getConnection(url);
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        }
    }

    // Reads schema.sql and creates the three tables if they do not exist yet.
    public synchronized void createTables() throws SQLException {
        String script;
        try (InputStream in = Database.class.getResourceAsStream("/schema.sql")) {
            if (in == null) {
                throw new SQLException("schema.sql was not found in src/main/resources");
            }
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SQLException("Could not read schema.sql", e);
        }
        for (String sql : script.split(";")) {
            if (sql.isBlank()) {
                continue;
            }
            try (Statement st = connection.createStatement()) {
                st.execute(sql);
            }
        }
    }

    // ---------- Renter ----------

    public synchronized int insertRenter(Renter renter) throws SQLException {
        String sql = "INSERT INTO Renter (name, contact, email, address) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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

    public synchronized List<Renter> getAllRenters() throws SQLException {
        String sql = "SELECT id, name, contact, email, address FROM Renter ORDER BY id";
        List<Renter> renters = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                renters.add(new Renter(rs.getInt("id"), rs.getString("name"), rs.getString("contact"),
                        rs.getString("email"), rs.getString("address")));
            }
        }
        return renters;
    }

    // ---------- Vehicle ----------

    public synchronized int insertVehicle(Vehicle v) throws SQLException {
        String sql = "INSERT INTO Vehicle (type, make, model, year, plateNumber, color, dailyRate, "
                + "mileage, lastPMS, categoryPath) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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
        }
    }

    public synchronized List<Vehicle> getAllVehicles() throws SQLException {
        String sql = "SELECT id, type, make, model, year, plateNumber, color, dailyRate, mileage, "
                + "lastPMS, categoryPath FROM Vehicle ORDER BY id";
        List<Vehicle> vehicles = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String pms = rs.getString("lastPMS");
                vehicles.add(VehicleFactory.create(rs.getInt("id"), rs.getString("type"),
                        rs.getString("make"), rs.getString("model"), rs.getInt("year"),
                        rs.getString("plateNumber"), rs.getString("color"), rs.getDouble("dailyRate"),
                        rs.getInt("mileage"), pms == null ? null : LocalDate.parse(pms),
                        rs.getString("categoryPath")));
            }
        }
        return vehicles;
    }

    // ---------- Rental ----------

    public synchronized int insertRental(Rental r) throws SQLException {
        String sql = "INSERT INTO Rental (renterId, vehicleId, startDate, endDate, returnedOn, "
                + "milesDriven, status) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, r.getRenterId());
            ps.setInt(2, r.getVehicleId());
            ps.setString(3, r.getStartDate().toString());
            ps.setString(4, r.getEndDate().toString());
            ps.setString(5, r.getReturnedOn() == null ? null : r.getReturnedOn().toString());
            ps.setInt(6, r.getMilesDriven());
            ps.setString(7, r.getStatus().name());
            ps.executeUpdate();
            int newId = readGeneratedId(ps);
            r.setId(newId);
            return newId;
        }
    }

    public synchronized List<Rental> getRentalsByRenter(int renterId) throws SQLException {
        String sql = "SELECT id, renterId, vehicleId, startDate, endDate, returnedOn, milesDriven, status "
                + "FROM Rental WHERE renterId = ? ORDER BY id";
        List<Rental> rentals = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, renterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String returned = rs.getString("returnedOn");
                    rentals.add(new Rental(rs.getInt("id"), rs.getInt("renterId"), rs.getInt("vehicleId"),
                            LocalDate.parse(rs.getString("startDate")), LocalDate.parse(rs.getString("endDate")),
                            returned == null ? null : LocalDate.parse(returned),
                            rs.getInt("milesDriven"), RentalStatus.valueOf(rs.getString("status"))));
                }
            }
        }
        return rentals;
    }

    // ---------- Milestone 2 additions ----------

    private Vehicle readVehicle(ResultSet rs) throws SQLException {
        String pms = rs.getString("lastPMS");
        return VehicleFactory.create(rs.getInt("id"), rs.getString("type"),
                rs.getString("make"), rs.getString("model"), rs.getInt("year"),
                rs.getString("plateNumber"), rs.getString("color"), rs.getDouble("dailyRate"),
                rs.getInt("mileage"), pms == null ? null : LocalDate.parse(pms),
                rs.getString("categoryPath"));
    }

    private Rental readRental(ResultSet rs) throws SQLException {
        String returned = rs.getString("returnedOn");
        return new Rental(rs.getInt("id"), rs.getInt("renterId"), rs.getInt("vehicleId"),
                LocalDate.parse(rs.getString("startDate")), LocalDate.parse(rs.getString("endDate")),
                returned == null ? null : LocalDate.parse(returned),
                rs.getInt("milesDriven"), RentalStatus.valueOf(rs.getString("status")));
    }

    private static final String VEHICLE_COLUMNS =
            "id, type, make, model, year, plateNumber, color, dailyRate, mileage, lastPMS, categoryPath";
    private static final String RENTAL_COLUMNS =
            "id, renterId, vehicleId, startDate, endDate, returnedOn, milesDriven, status";

    // Returns null when no vehicle has that id.
    public synchronized Vehicle getVehicleById(int id) throws SQLException {
        String sql = "SELECT " + VEHICLE_COLUMNS + " FROM Vehicle WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readVehicle(rs) : null;
            }
        }
    }

    // Plate numbers are compared without case. Returns null when there is no match.
    public synchronized Vehicle findVehicleByPlate(String plate) throws SQLException {
        String sql = "SELECT " + VEHICLE_COLUMNS + " FROM Vehicle WHERE UPPER(plateNumber) = UPPER(?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, plate == null ? "" : plate.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readVehicle(rs) : null;
            }
        }
    }

    public synchronized List<Rental> getAllRentals() throws SQLException {
        String sql = "SELECT " + RENTAL_COLUMNS + " FROM Rental ORDER BY id";
        List<Rental> rentals = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rentals.add(readRental(rs));
            }
        }
        return rentals;
    }

    // Returns null when no rental has that id.
    public synchronized Rental getRentalById(int id) throws SQLException {
        String sql = "SELECT " + RENTAL_COLUMNS + " FROM Rental WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readRental(rs) : null;
            }
        }
    }

    public synchronized boolean hasOngoingRental(int vehicleId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM Rental WHERE vehicleId = ? AND status = 'ONGOING'";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, vehicleId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    // Saves the return details of a rental (return date, miles, status).
    public synchronized void updateRental(Rental r) throws SQLException {
        String sql = "UPDATE Rental SET returnedOn = ?, milesDriven = ?, status = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, r.getReturnedOn() == null ? null : r.getReturnedOn().toString());
            ps.setInt(2, r.getMilesDriven());
            ps.setString(3, r.getStatus().name());
            ps.setInt(4, r.getId());
            ps.executeUpdate();
        }
    }

    public synchronized void deleteRental(int id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM Rental WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    public synchronized void addMileage(int vehicleId, int miles) throws SQLException {
        String sql = "UPDATE Vehicle SET mileage = mileage + ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, miles);
            ps.setInt(2, vehicleId);
            ps.executeUpdate();
        }
    }

    // Saves many finished rentals in ONE transaction (fast), and adds their miles to each vehicle.
    // If anything fails, nothing from this batch is saved.
    public synchronized int insertRentalBatch(List<Rental> rentals) throws SQLException {
        connection.setAutoCommit(false);
        try {
            for (Rental r : rentals) {
                insertRental(r);
                addMileage(r.getVehicleId(), r.getMilesDriven());
            }
            connection.commit();
            return rentals.size();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private int readGeneratedId(PreparedStatement ps) throws SQLException {
        try (ResultSet keys = ps.getGeneratedKeys()) {
            if (keys.next()) {
                return keys.getInt(1);
            }
        }
        throw new SQLException("No id was returned after the insert");
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
