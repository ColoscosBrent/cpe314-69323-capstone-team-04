package fleetrent.dao;

import fleetrent.model.Account;
import fleetrent.model.BusySpan;
import fleetrent.model.ImportBatch;
import fleetrent.model.ImportRecord;
import fleetrent.model.Maintenance;
import fleetrent.model.MaintenanceType;
import fleetrent.model.Payment;
import fleetrent.model.Rental;
import fleetrent.model.RentalStatus;
import fleetrent.model.Renter;
import fleetrent.model.Reservation;
import fleetrent.model.ReservationStatus;
import fleetrent.model.Role;
import fleetrent.model.Vehicle;
import fleetrent.model.VehicleFactory;

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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Talks to the SQLite database. Every query with values uses PreparedStatement.
 */
public class Database implements AutoCloseable {
    private final Connection connection;

    /**
     * url examples: "jdbc:sqlite:fleetrent.db" (a file) or "jdbc:sqlite::memory:" (for tests)
     */
    public Database(String url) throws SQLException {
        this.connection = DriverManager.getConnection(url);
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            // Two windows (or two copies of the app) can use the same file. Wait up to 5 seconds
            // instead of failing at once when the other one is writing.
            st.execute("PRAGMA busy_timeout = 5000");
        }
    }

    /**
     * Reads schema.sql and creates the eleven tables if they do not exist yet.
     */
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
            } catch (SQLException e) {
                // An older fleetrent.db may already hold two vehicles with the same plate. The
                // unique index cannot be built then; FleetService and the Vehicles tab still check
                // the plate.
                if (!sql.contains("UNIQUE INDEX")) {
                    throw e;
                }
            }
        }
        migrate();
    }

    // A fleetrent.db made by an older version keeps its data. Missing columns are added here.
    private void migrate() throws SQLException {
        if (!columnExists("Vehicle", "active")) {
            try (Statement st = connection.createStatement()) {
                st.execute("ALTER TABLE Vehicle ADD COLUMN active INTEGER NOT NULL DEFAULT 1");
            }
        }
        if (!columnExists("Account", "phone")) {
            try (Statement st = connection.createStatement()) {
                st.execute("ALTER TABLE Account ADD COLUMN phone TEXT");
            }
        }
    }

    private boolean columnExists(String table, String column) throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Changes when ANYTHING in the file changes: by this connection (total_changes) or by another
     * window or program (data_version). The screens compare it to know when to reload.
     */
    public synchronized String changeStamp() throws SQLException {
        long version;
        long changes;
        try (Statement st = connection.createStatement()) {
            try (ResultSet rs = st.executeQuery("PRAGMA data_version")) {
                version = rs.next() ? rs.getLong(1) : 0;
            }
            try (ResultSet rs = st.executeQuery("SELECT total_changes()")) {
                changes = rs.next() ? rs.getLong(1) : 0;
            }
        }
        return version + ":" + changes;
    }

    // ---------- Renter ----------

    /** Saves a new renter and gives it its new id. */
    public synchronized int insertRenter(Renter renter) throws SQLException {
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
    public synchronized List<Renter> getAllRenters() throws SQLException {
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

    // ---------- Vehicle ----------

    /**
     * Saves a new vehicle. Throws DuplicatePlateException when its plate is already in the fleet.
     */
    public synchronized int insertVehicle(Vehicle v) throws SQLException {
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
    public synchronized List<Vehicle> getAllVehicles() throws SQLException {
        return queryVehicles("WHERE active = 1");
    }

    /**
     * Every vehicle ever added, removed ones too. The dashboard and the history lists use it, so
     * old rentals of a removed vehicle still count and still show the vehicle.
     */
    public synchronized List<Vehicle> getAllVehiclesIncludingRemoved() throws SQLException {
        return queryVehicles("");
    }

    /**
     * Vehicles the owner removed from the fleet. They keep their rental history.
     */
    public synchronized List<Vehicle> getRemovedVehicles() throws SQLException {
        return queryVehicles("WHERE active = 0");
    }

    private List<Vehicle> queryVehicles(String where) throws SQLException {
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
    public synchronized boolean isVehicleActive(int vehicleId) throws SQLException {
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
    public synchronized void setVehicleActive(int vehicleId, boolean active) throws SQLException {
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
    public synchronized void updateVehicle(Vehicle v) throws SQLException {
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

    // ---------- Rental ----------

    /** Saves a new rental. Refuses it when the vehicle is rented on any of those days. */
    public synchronized int insertRental(Rental r) throws SQLException {
        String sql = "INSERT INTO Rental (renterId, vehicleId, startDate, endDate, returnedOn, "
                + "milesDriven, status) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
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

    /** Returns every rental of one renter. */
    public synchronized List<Rental> getRentalsByRenter(int renterId) throws SQLException {
        String sql =
                "SELECT id, renterId, vehicleId, startDate, endDate, returnedOn, milesDriven, "
                + "status "
                + "FROM Rental WHERE renterId = ? ORDER BY id";
        List<Rental> rentals = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, renterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String returned = rs.getString("returnedOn");
                    rentals.add(new Rental(rs.getInt("id"), rs.getInt("renterId"),
                            rs.getInt("vehicleId"),
                            LocalDate.parse(rs.getString("startDate")),
                                    LocalDate.parse(rs.getString("endDate")),
                            returned == null ? null : LocalDate.parse(returned),
                            rs.getInt("milesDriven"),
                                    RentalStatus.valueOf(rs.getString("status"))));
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
                LocalDate.parse(rs.getString("startDate")),
                        LocalDate.parse(rs.getString("endDate")),
                returned == null ? null : LocalDate.parse(returned),
                rs.getInt("milesDriven"), RentalStatus.valueOf(rs.getString("status")));
    }

    private static final String VEHICLE_COLUMNS =
            "id, type, make, model, year, plateNumber, color, dailyRate, mileage, lastPMS, "
                    + "categoryPath";
    private static final String RENTAL_COLUMNS =
            "id, renterId, vehicleId, startDate, endDate, returnedOn, milesDriven, status";

    /**
     * Returns null when no vehicle has that id.
     */
    public synchronized Vehicle getVehicleById(int id) throws SQLException {
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
    public synchronized Vehicle findVehicleByPlate(String plate) throws SQLException {
        String sql = "SELECT " + VEHICLE_COLUMNS
                + " FROM Vehicle WHERE UPPER(plateNumber) = UPPER(?) AND active = 1";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, plate == null ? "" : plate.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readVehicle(rs) : null;
            }
        }
    }

    /** Returns every rental. */
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

    /**
     * Returns null when no rental has that id.
     */
    public synchronized Rental getRentalById(int id) throws SQLException {
        String sql = "SELECT " + RENTAL_COLUMNS + " FROM Rental WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readRental(rs) : null;
            }
        }
    }

    /** Tells whether the vehicle is rented out right now. */
    public synchronized boolean hasOngoingRental(int vehicleId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM Rental WHERE vehicleId = ? AND status = 'ONGOING'";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, vehicleId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Saves the return details of a rental (return date, miles, status).
     */
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

    /** Deletes one rental (used by Undo). */
    public synchronized void deleteRental(int id) throws SQLException {
        try (PreparedStatement ps =
                connection.prepareStatement("DELETE FROM Rental WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    /** Adds the miles of a returned rental to the vehicle. */
    public synchronized void addMileage(int vehicleId, int miles) throws SQLException {
        String sql = "UPDATE Vehicle SET mileage = mileage + ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, miles);
            ps.setInt(2, vehicleId);
            ps.executeUpdate();
        }
    }

    // A write that checks first and saves after (book a car, remove a vehicle, sign up) must hold
    // the write lock from its first statement. BEGIN IMMEDIATE does that: if another window is
    // writing, this one waits (busy_timeout) instead of failing halfway with "database is locked".
    private void beginWrite() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("BEGIN IMMEDIATE");
        }
    }

    private void commitWrite() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("COMMIT");
        }
    }

    private void rollbackWrite() {
        try (Statement st = connection.createStatement()) {
            st.execute("ROLLBACK");
        } catch (SQLException ignored) {
            // nothing is open to roll back (for example the database was already closed)
        }
    }

    /**
     * Saves many finished rentals in ONE transaction (fast), and adds their miles to each vehicle.
     * If anything fails, nothing from this batch is saved.
     */
    public synchronized int insertRentalBatch(List<Rental> rentals) throws SQLException {
        beginWrite();
        try {
            for (Rental r : rentals) {
                insertRental(r);
                addMileage(r.getVehicleId(), r.getMilesDriven());
            }
            commitWrite();
            return rentals.size();
        } catch (SQLException | RuntimeException e) {
            rollbackWrite();
            throw e;
        }
    }

    // ---------- Milestone 3 (Week 10 review): accounts ----------

    private static final String ACCOUNT_COLUMNS =
            "id, username, role, displayName, phone, passwordHash, passwordSalt, "
            + "securityQuestion, answerHash, answerSalt, renterId, createdOn";

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
    public synchronized int insertAccount(Account a) throws SQLException {
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
     * Saves a new renter and the account that belongs to it together: both are saved or neither.
     */
    public synchronized int insertRenterWithAccount(Renter renter,
            Account account) throws SQLException {
        beginWrite();
        try {
            insertRenter(renter);
            Account linked = new Account(0, account.getUsername(), account.getRole(),
                    account.getDisplayName(), account.getPhone(),
                    account.getPasswordHash(), account.getPasswordSalt(),
                    account.getSecurityQuestion(),
                    account.getAnswerHash(), account.getAnswerSalt(), renter.getId(),
                    account.getCreatedOn());
            insertAccount(linked);
            commitWrite();
            account.setId(linked.getId());
            return linked.getId();
        } catch (SQLException | RuntimeException e) {
            rollbackWrite();
            throw e;
        }
    }

    /**
     * The username is compared without case. Returns null when there is no such account.
     */
    public synchronized Account findAccountByUsername(String username) throws SQLException {
        String sql = "SELECT " + ACCOUNT_COLUMNS + " FROM Account WHERE username = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username == null ? "" : username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readAccount(rs) : null;
            }
        }
    }

    /** Returns the account with this id, or null. */
    public synchronized Account getAccountById(int id) throws SQLException {
        String sql = "SELECT " + ACCOUNT_COLUMNS + " FROM Account WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readAccount(rs) : null;
            }
        }
    }

    /** Saves a new password hash and salt for an account. */
    public synchronized void updateAccountPassword(int accountId, String hash,
            String salt) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE Account SET passwordHash = ?, passwordSalt = ? WHERE id = ?")) {
            ps.setString(1, hash);
            ps.setString(2, salt);
            ps.setInt(3, accountId);
            ps.executeUpdate();
        }
    }

    /** Returns the renter with this id, or null. */
    public synchronized Renter getRenterById(int id) throws SQLException {
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

    // ---------- reservations and availability ----------

    private static final String RESERVATION_COLUMNS =
            "id, renterId, vehicleId, startDate, endDate, status, note, createdOn";

    private Reservation readReservation(ResultSet rs) throws SQLException {
        return new Reservation(rs.getInt("id"), rs.getInt("renterId"), rs.getInt("vehicleId"),
                LocalDate.parse(rs.getString("startDate")),
                        LocalDate.parse(rs.getString("endDate")),
                ReservationStatus.valueOf(rs.getString("status")), rs.getString("note"),
                LocalDate.parse(rs.getString("createdOn")));
    }

    /** Saves a new reservation. */
    public synchronized int insertReservation(Reservation r) throws SQLException {
        String sql =
                "INSERT INTO Reservation (renterId, vehicleId, startDate, endDate, status, note, "
                + "createdOn) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, r.getRenterId());
            ps.setInt(2, r.getVehicleId());
            ps.setString(3, r.getStartDate().toString());
            ps.setString(4, r.getEndDate().toString());
            ps.setString(5, r.getStatus().name());
            ps.setString(6, r.getNote());
            ps.setString(7, r.getCreatedOn().toString());
            ps.executeUpdate();
            int newId = readGeneratedId(ps);
            r.setId(newId);
            return newId;
        }
    }

    /** Returns the reservation with this id, or null. */
    public synchronized Reservation getReservationById(int id) throws SQLException {
        String sql = "SELECT " + RESERVATION_COLUMNS + " FROM Reservation WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readReservation(rs) : null;
            }
        }
    }

    /** Returns every reservation. */
    public synchronized List<Reservation> getAllReservations() throws SQLException {
        return queryReservations("", null);
    }

    /** Returns every reservation of one renter. */
    public synchronized List<Reservation> getReservationsByRenter(
            int renterId) throws SQLException {
        return queryReservations("WHERE renterId = ?", renterId);
    }

    private List<Reservation> queryReservations(String where, Integer param) throws SQLException {
        String sql = "SELECT " + RESERVATION_COLUMNS + " FROM Reservation " + where
                + " ORDER BY startDate, id";
        List<Reservation> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (param != null) {
                ps.setInt(1, param);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(readReservation(rs));
                }
            }
        }
        return list;
    }

    /** Changes the status of a reservation. */
    public synchronized void updateReservationStatus(int id, ReservationStatus status,
            String note) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE Reservation SET status = ?, note = ? WHERE id = ?")) {
            ps.setString(1, status.name());
            ps.setString(2, note);
            ps.setInt(3, id);
            ps.executeUpdate();
        }
    }

    /**
     * Cancels every ACTIVE reservation of a vehicle. Returns how many were cancelled.
     */
    public synchronized int cancelActiveReservationsOfVehicle(int vehicleId,
            String note) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE Reservation SET status = 'CANCELLED', note = ? WHERE vehicleId = ? AND "
                        + "status = 'ACTIVE'")) {
            ps.setString(1, note);
            ps.setInt(2, vehicleId);
            return ps.executeUpdate();
        }
    }

    /** Counts the active reservations of a vehicle. */
    public synchronized int countActiveReservationsOfVehicle(int vehicleId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM Reservation WHERE vehicleId = ? AND status = 'ACTIVE'")) {
            ps.setInt(1, vehicleId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * True when an ACTIVE reservation of the vehicle touches any day from start to end (both
     * included).
     */
    public synchronized boolean hasReservationOverlap(int vehicleId, LocalDate start,
            LocalDate end) throws SQLException {
        String sql = "SELECT COUNT(*) FROM Reservation WHERE vehicleId = ? AND status = 'ACTIVE' "
                + "AND startDate <= ? AND endDate >= ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, vehicleId);
            ps.setString(2, end.toString());
            ps.setString(3, start.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * True when an ONGOING rental touches any day from start to end. A rental that is late (its end
     * date has passed and it is not returned) still blocks the vehicle until today.
     */
    public synchronized boolean hasRentalOverlap(int vehicleId, LocalDate start, LocalDate end,
            LocalDate today)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM Rental WHERE vehicleId = ? AND status = 'ONGOING' "
                + "AND startDate <= ? AND MAX(endDate, ?) >= ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, vehicleId);
            ps.setString(2, end.toString());
            ps.setString(3, today.toString());
            ps.setString(4, start.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * The days each vehicle is busy (ongoing rentals and active reservations that are not over
     * yet), for every vehicle at once. One query for rentals and one for reservations.
     */
    public synchronized java.util.Map<Integer,
            List<BusySpan>> getBusySpans(LocalDate today) throws SQLException {
        java.util.Map<Integer, List<BusySpan>> map = new java.util.HashMap<>();
        String rentals = "SELECT vehicleId, startDate, MAX(endDate, ?) AS endDay FROM Rental "
                + "WHERE status = 'ONGOING' ORDER BY startDate";
        try (PreparedStatement ps = connection.prepareStatement(rentals)) {
            ps.setString(1, today.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (rs.getString("startDate") == null) {
                        continue;
                    }
                    map.computeIfAbsent(rs.getInt("vehicleId"), k -> new ArrayList<>())
                            .add(new BusySpan(
                            LocalDate.parse(rs.getString("startDate")),
                                    LocalDate.parse(rs.getString("endDay")), "RENTED"));
                }
            }
        }
        String reservations = "SELECT vehicleId, startDate, endDate FROM Reservation "
                + "WHERE status = 'ACTIVE' AND endDate >= ? ORDER BY startDate";
        try (PreparedStatement ps = connection.prepareStatement(reservations)) {
            ps.setString(1, today.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.computeIfAbsent(rs.getInt("vehicleId"), k -> new ArrayList<>())
                            .add(new BusySpan(
                            LocalDate.parse(rs.getString("startDate")),
                                    LocalDate.parse(rs.getString("endDate")), "RESERVED"));
                }
            }
        }
        for (List<BusySpan> spans : map.values()) {
            spans.sort(java.util.Comparator.comparing(BusySpan::start));
        }
        return map;
    }

    /**
     * True when the vehicle is in the fleet and no ongoing rental or active reservation touches any
     * day from start to end (both days included).
     */
    public synchronized boolean isVehicleFree(int vehicleId, LocalDate start, LocalDate end,
            LocalDate today)
            throws SQLException {
        return isVehicleActive(vehicleId) && !hasRentalOverlap(vehicleId, start, end, today)
                && !hasReservationOverlap(vehicleId, start, end);
    }

    /**
     * Checks that the vehicle is free and saves the rental in ONE transaction. Returns false (and
     * saves nothing) when the vehicle is not free, so two windows can never book the same days.
     */
    public synchronized boolean insertRentalIfFree(Rental r, LocalDate today) throws SQLException {
        beginWrite();
        try {
            if (!isVehicleFree(r.getVehicleId(), r.getStartDate(), r.getEndDate(), today)) {
                rollbackWrite();
                return false;
            }
            insertRental(r);
            commitWrite();
            return true;
        } catch (SQLException | RuntimeException e) {
            rollbackWrite();
            throw e;
        }
    }

    /**
     * Saves a reservation only when the vehicle is free on those days. Check and save happen in one
     * transaction.
     */
    public synchronized boolean insertReservationIfFree(Reservation r,
            LocalDate today) throws SQLException {
        beginWrite();
        try {
            if (!isVehicleFree(r.getVehicleId(), r.getStartDate(), r.getEndDate(), today)) {
                rollbackWrite();
                return false;
            }
            insertReservation(r);
            commitWrite();
            return true;
        } catch (SQLException | RuntimeException e) {
            rollbackWrite();
            throw e;
        }
    }

    /**
     * Takes a vehicle out of the fleet and cancels its active reservations, in one transaction.
     * Returns how many reservations were cancelled, or -1 (and changes nothing) when the vehicle is
     * rented out right now.
     */
    public synchronized int removeVehicle(int vehicleId, String note) throws SQLException {
        beginWrite();
        try {
            if (hasOngoingRental(vehicleId)) {
                rollbackWrite();
                return -1;
            }
            int cancelled = cancelActiveReservationsOfVehicle(vehicleId, note);
            setVehicleActive(vehicleId, false);
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM WaitingEntry WHERE vehicleId = ?")) {
                ps.setInt(1, vehicleId);
                ps.executeUpdate();
            }
            commitWrite();
            return cancelled;
        } catch (SQLException | RuntimeException e) {
            rollbackWrite();
            throw e;
        }
    }

    /**
     * Turns a reservation into a rental in one step: both are saved or neither.
     */
    public synchronized int convertReservationToRental(Reservation reservation,
            Rental rental) throws SQLException {
        beginWrite();
        try {
            int rentalId = insertRental(rental);
            updateReservationStatus(reservation.getId(), ReservationStatus.CONVERTED, "Rental #"
                    + rentalId);
            commitWrite();
            reservation.setStatus(ReservationStatus.CONVERTED);
            return rentalId;
        } catch (SQLException | RuntimeException e) {
            rollbackWrite();
            throw e;
        }
    }

    // ---------- Payment ----------

    /** Saves a payment and gives it its new id. */
    public synchronized int insertPayment(Payment p) throws SQLException {
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
    public synchronized Payment getPaymentByRental(int rentalId) throws SQLException {
        String sql = "SELECT id, rentalId, amount, lateFee, paidOn FROM Payment WHERE rentalId = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, rentalId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readPayment(rs) : null;
            }
        }
    }

    /** Returns every payment, oldest first. */
    public synchronized List<Payment> getAllPayments() throws SQLException {
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

    /**
     * Closes a rental in ONE transaction: the return details, the vehicle miles and the payment.
     */
    public synchronized void saveReturn(Rental rental, Payment payment) throws SQLException {
        beginWrite();
        try {
            updateRental(rental);
            addMileage(rental.getVehicleId(), rental.getMilesDriven());
            insertPayment(payment);
            commitWrite();
        } catch (SQLException | RuntimeException e) {
            rollbackWrite();
            throw e;
        }
    }

    // ---------- Maintenance ----------

    /**
     * Saves a maintenance record. A PMS record also moves the vehicle's last PMS date forward
     * (never backward), in the same transaction.
     */
    public synchronized int insertMaintenance(Maintenance m) throws SQLException {
        beginWrite();
        try {
            String sql = "INSERT INTO Maintenance (vehicleId, doneOn, type, cost, note) "
                    + "VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql,
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, m.getVehicleId());
                ps.setString(2, m.getDoneOn().toString());
                ps.setString(3, m.getType().name());
                ps.setDouble(4, m.getCost());
                ps.setString(5, m.getNote());
                ps.executeUpdate();
                m.setId(readGeneratedId(ps));
            }
            if (m.getType() == MaintenanceType.PMS) {
                String upd = "UPDATE Vehicle SET lastPMS = ? WHERE id = ? "
                        + "AND (lastPMS IS NULL OR lastPMS < ?)";
                try (PreparedStatement ps = connection.prepareStatement(upd)) {
                    ps.setString(1, m.getDoneOn().toString());
                    ps.setInt(2, m.getVehicleId());
                    ps.setString(3, m.getDoneOn().toString());
                    ps.executeUpdate();
                }
            }
            commitWrite();
            return m.getId();
        } catch (SQLException | RuntimeException e) {
            rollbackWrite();
            throw e;
        }
    }

    /** Returns the maintenance of one vehicle, newest first. */
    public synchronized List<Maintenance> getMaintenanceByVehicle(
            int vehicleId) throws SQLException {
        String sql = "SELECT id, vehicleId, doneOn, type, cost, note FROM Maintenance "
                + "WHERE vehicleId = ? ORDER BY doneOn DESC, id DESC";
        List<Maintenance> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, vehicleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Maintenance(rs.getInt("id"), rs.getInt("vehicleId"),
                            LocalDate.parse(rs.getString("doneOn")),
                            MaintenanceType.valueOf(rs.getString("type")), rs.getDouble("cost"),
                            rs.getString("note")));
                }
            }
        }
        return list;
    }

    // ---------- Waiting list and check-out log ----------

    /** Puts a renter at the end of the waiting line of a vehicle. */
    public synchronized void addWaiting(int vehicleId, int renterId,
            LocalDate joinedOn) throws SQLException {
        String sql = "INSERT INTO WaitingEntry (vehicleId, renterId, joinedOn) VALUES (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, vehicleId);
            ps.setInt(2, renterId);
            ps.setString(3, joinedOn.toString());
            ps.executeUpdate();
        }
    }

    /** Returns the ids of the waiting renters of a vehicle, first in line first. */
    public synchronized List<Integer> getWaitingRenterIds(int vehicleId) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        String sql = "SELECT renterId FROM WaitingEntry WHERE vehicleId = ? ORDER BY id";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, vehicleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        }
        return ids;
    }

    /** Removes the first waiting entry of a vehicle. Returns false when nobody was waiting. */
    public synchronized boolean removeFirstWaiting(int vehicleId) throws SQLException {
        String sql = "DELETE FROM WaitingEntry WHERE id = "
                + "(SELECT MIN(id) FROM WaitingEntry WHERE vehicleId = ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, vehicleId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Remembers a check-out so it can be undone later, even after a restart. */
    public synchronized void logCheckout(int rentalId) throws SQLException {
        try (PreparedStatement ps =
                connection.prepareStatement("INSERT INTO CheckoutLog (rentalId) VALUES (?)")) {
            ps.setInt(1, rentalId);
            ps.executeUpdate();
        }
    }

    /** Returns the logged rental ids, oldest first. */
    public synchronized List<Integer> getCheckoutLog() throws SQLException {
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
    public synchronized void removeCheckoutLog(int rentalId) throws SQLException {
        try (PreparedStatement ps =
                connection.prepareStatement("DELETE FROM CheckoutLog WHERE rentalId = ?")) {
            ps.setInt(1, rentalId);
            ps.executeUpdate();
        }
    }

    // ---------- import history ----------

    /** Saves a new, empty import in the history and returns its id. */
    public synchronized int insertImportBatch(String fileName, String importedBy,
            LocalDateTime importedOn) throws SQLException {
        String sql = "INSERT INTO ImportBatch (fileName, importedBy, importedOn) VALUES (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, fileName);
            ps.setString(2, importedBy);
            ps.setString(3, importedOn.toString());
            ps.executeUpdate();
            return readGeneratedId(ps);
        }
    }

    /** Writes the final counts of an import. */
    public synchronized void finishImportBatch(int batchId, int rowsRead, int rowsAdded,
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

    /** Saves the rows of one import in one transaction. */
    public synchronized void insertImportRecords(int batchId, List<ImportRecord> records)
            throws SQLException {
        String sql = "INSERT INTO ImportRecord (batchId, lineNo, plateNumber, vehicleType, make, "
                + "model, year, renterName, startDate, endDate, milesDriven, status, message) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        beginWrite();
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
            commitWrite();
        } catch (SQLException | RuntimeException e) {
            rollbackWrite();
            throw e;
        }
    }

    /** Every import, the newest first. */
    public synchronized List<ImportBatch> getImportBatches() throws SQLException {
        String sql = "SELECT id, fileName, importedBy, importedOn, rowsRead, rowsAdded, "
                + "rowsSkipped FROM ImportBatch ORDER BY id DESC";
        List<ImportBatch> batches = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                batches.add(new ImportBatch(rs.getInt("id"), rs.getString("fileName"),
                        rs.getString("importedBy"), LocalDateTime.parse(rs.getString("importedOn")),
                        rs.getInt("rowsRead"), rs.getInt("rowsAdded"), rs.getInt("rowsSkipped")));
            }
        }
        return batches;
    }

    /**
     * The rows of one import in file order. Pass "ADDED" or "SKIPPED" to see only those rows, or
     * null for all. At most limit rows are returned.
     */
    public synchronized List<ImportRecord> getImportRecords(int batchId, String status, int limit)
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
