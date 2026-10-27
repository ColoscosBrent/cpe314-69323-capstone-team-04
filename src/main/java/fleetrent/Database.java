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
    public void createTables() throws SQLException {
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

    public int insertRenter(Renter renter) throws SQLException {
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

    public List<Renter> getAllRenters() throws SQLException {
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

    public int insertVehicle(Vehicle v) throws SQLException {
        String sql = "INSERT INTO Vehicle (type, make, model, year, plateNumber, color, dailyRate, "
                + "mileage, lastPMS, categoryPath) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, v.getType());
            ps.setString(2, v.getMake());
            ps.setString(3, v.getModel());
            ps.setInt(4, v.getYear());
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

    public List<Vehicle> getAllVehicles() throws SQLException {
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

    public int insertRental(Rental r) throws SQLException {
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

    public List<Rental> getRentalsByRenter(int renterId) throws SQLException {
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
