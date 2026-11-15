package fleetrent.dao;

import fleetrent.model.Account;
import fleetrent.model.BusySpan;
import fleetrent.model.ImportBatch;
import fleetrent.model.ImportRecord;
import fleetrent.model.Maintenance;
import fleetrent.model.MaintenanceType;
import fleetrent.model.Payment;
import fleetrent.model.Rental;
import fleetrent.model.Renter;
import fleetrent.model.Reservation;
import fleetrent.model.ReservationStatus;
import fleetrent.model.Vehicle;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Keeps the connection to the SQLite database, builds the tables and runs the writes that touch
 * more than one table in one transaction. The SQL of each table is in its own DAO class
 * (VehicleDao, RentalDao, and so on). Every method here is synchronized, so one window never mixes
 * two statements on the same connection.
 */
public class Database implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(Database.class.getName());
    private static final int BUSY_TIMEOUT_MS = 5000;

    private final Connection connection;
    private final RenterDao renters;
    private final VehicleDao vehicles;
    private final RentalDao rentals;
    private final AccountDao accounts;
    private final ReservationDao reservations;
    private final PaymentDao payments;
    private final MaintenanceDao maintenance;
    private final WaitingEntryDao waiting;
    private final CheckoutLogDao checkoutLog;
    private final ImportBatchDao importBatches;
    private final ImportRecordDao importRecords;
    private String setupWarning;

    /**
     * url examples: "jdbc:sqlite:fleetrent.db" (a file) or "jdbc:sqlite::memory:" (for tests)
     */
    public Database(String url) throws SQLException {
        this.connection = DriverManager.getConnection(url);
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            // Two windows (or two copies of the app) can use the same file. Wait a few seconds
            // instead of failing at once when the other one is writing.
            st.execute("PRAGMA busy_timeout = " + BUSY_TIMEOUT_MS);
        }
        this.renters = new RenterDao(connection);
        this.vehicles = new VehicleDao(connection);
        this.rentals = new RentalDao(connection);
        this.accounts = new AccountDao(connection);
        this.reservations = new ReservationDao(connection);
        this.payments = new PaymentDao(connection);
        this.maintenance = new MaintenanceDao(connection);
        this.waiting = new WaitingEntryDao(connection);
        this.checkoutLog = new CheckoutLogDao(connection);
        this.importBatches = new ImportBatchDao(connection);
        this.importRecords = new ImportRecordDao(connection);
    }

    // ---------- Connection and schema ----------

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
        migrate();
        for (String sql : script.split(";")) {
            if (sql.isBlank()) {
                continue;
            }
            try (Statement st = connection.createStatement()) {
                st.execute(sql);
            } catch (SQLException e) {
                if (!sql.contains("UNIQUE INDEX")) {
                    throw e;
                }
                rememberIndexFailure(e);
            }
        }
    }

    // An older fleetrent.db may already hold two vehicles with the same plate. The unique index
    // cannot be built then. The app still checks the plate itself, but the owner must know.
    private void rememberIndexFailure(SQLException e) {
        setupWarning = "Two vehicles in this database have the same plate number, so the"
                + " unique-plate rule of the database is off. Change one of the plates.";
        LOG.warning("The unique plate index was not created: " + e.getMessage());
    }

    /**
     * Returns a message when the database was opened with a problem that the owner should fix, or
     * null. The message is given only once.
     */
    public synchronized String takeSetupWarning() {
        String message = setupWarning;
        setupWarning = null;
        return message;
    }

    // A fleetrent.db made by an older version keeps its data. Missing columns are added here,
    // before schema.sql builds the indexes that use them.
    private void migrate() throws SQLException {
        addColumnIfMissing("Vehicle", "active", "INTEGER NOT NULL DEFAULT 1");
        addColumnIfMissing("Account", "phone", "TEXT");
        addColumnIfMissing("Rental", "processedBy", "INTEGER REFERENCES Account(id)");
        addColumnIfMissing("ImportBatch", "fileHash", "TEXT");
    }

    private void addColumnIfMissing(String table, String column,
            String definition) throws SQLException {
        if (tableExists(table) && !columnExists(table, column)) {
            try (Statement st = connection.createStatement()) {
                st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            }
        }
    }

    private boolean tableExists(String table) throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            return rs.next();
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

    // ---------- Renter ----------

    /** Saves a new renter and gives it its new id. */
    public synchronized int insertRenter(Renter renter) throws SQLException {
        return renters.insert(renter);
    }

    /** Returns every renter. */
    public synchronized List<Renter> getAllRenters() throws SQLException {
        return renters.getAll();
    }

    /** Returns the renter with this id, or null. */
    public synchronized Renter getRenterById(int id) throws SQLException {
        return renters.getById(id);
    }

    // ---------- Vehicle ----------

    /**
     * Saves a new vehicle. Throws DuplicatePlateException when its plate is already in the fleet.
     */
    public synchronized int insertVehicle(Vehicle v) throws SQLException {
        return vehicles.insert(v);
    }

    /** Returns the vehicles that are in the fleet (not removed). */
    public synchronized List<Vehicle> getAllVehicles() throws SQLException {
        return vehicles.getAll();
    }

    /**
     * Every vehicle ever added, removed ones too. The dashboard and the history lists use it, so
     * old rentals of a removed vehicle still count and still show the vehicle.
     */
    public synchronized List<Vehicle> getAllVehiclesIncludingRemoved() throws SQLException {
        return vehicles.getAllIncludingRemoved();
    }

    /** Vehicles the owner removed from the fleet. They keep their rental history. */
    public synchronized List<Vehicle> getRemovedVehicles() throws SQLException {
        return vehicles.getRemoved();
    }

    /** True while the vehicle is in the fleet. False for a removed or unknown vehicle. */
    public synchronized boolean isVehicleActive(int vehicleId) throws SQLException {
        return vehicles.isActive(vehicleId);
    }

    /** Removes (false) or restores (true) a vehicle. The row stays, so old rentals keep it. */
    public synchronized void setVehicleActive(int vehicleId, boolean active) throws SQLException {
        vehicles.setActive(vehicleId, active);
    }

    /** Saves the edited details of a vehicle. The type is not changed here. */
    public synchronized void updateVehicle(Vehicle v) throws SQLException {
        vehicles.update(v);
    }

    /** Returns null when no vehicle has that id. */
    public synchronized Vehicle getVehicleById(int id) throws SQLException {
        return vehicles.getById(id);
    }

    /**
     * Plate numbers are compared without case. Only vehicles still in the fleet count. Returns null
     * when there is no match.
     */
    public synchronized Vehicle findVehicleByPlate(String plate) throws SQLException {
        return vehicles.findByPlate(plate);
    }

    /** Adds the miles of a returned rental to the vehicle. */
    public synchronized void addMileage(int vehicleId, int miles) throws SQLException {
        vehicles.addMileage(vehicleId, miles);
    }

    // ---------- Rental ----------

    /** Saves a new rental and gives it its new id. */
    public synchronized int insertRental(Rental r) throws SQLException {
        return rentals.insert(r);
    }

    /** Returns every rental of one renter. */
    public synchronized List<Rental> getRentalsByRenter(int renterId) throws SQLException {
        return rentals.getByRenter(renterId);
    }

    /** Returns every rental. */
    public synchronized List<Rental> getAllRentals() throws SQLException {
        return rentals.getAll();
    }

    /** Returns null when no rental has that id. */
    public synchronized Rental getRentalById(int id) throws SQLException {
        return rentals.getById(id);
    }

    /** Tells whether the vehicle is rented out right now. */
    public synchronized boolean hasOngoingRental(int vehicleId) throws SQLException {
        return rentals.hasOngoing(vehicleId);
    }

    /** Saves the return details of a rental (return date, miles, status). */
    public synchronized void updateRental(Rental r) throws SQLException {
        rentals.update(r);
    }

    /** Deletes one rental (used by Undo). */
    public synchronized void deleteRental(int id) throws SQLException {
        rentals.delete(id);
    }

    /**
     * Saves many finished rentals in ONE transaction (fast), and adds their miles to each vehicle.
     * If anything fails, nothing from this batch is saved.
     */
    public synchronized int insertRentalBatch(List<Rental> batch) throws SQLException {
        beginWrite();
        try {
            for (Rental r : batch) {
                rentals.insert(r);
                vehicles.addMileage(r.getVehicleId(), r.getMilesDriven());
            }
            commitWrite();
            return batch.size();
        } catch (SQLException | RuntimeException e) {
            rollbackWrite();
            throw e;
        }
    }

    // ---------- Account ----------

    /** Saves a new account and gives it its new id. */
    public synchronized int insertAccount(Account a) throws SQLException {
        return accounts.insert(a);
    }

    /**
     * Saves a new renter and the account that belongs to it together: both are saved or neither.
     */
    public synchronized int insertRenterWithAccount(Renter renter,
            Account account) throws SQLException {
        beginWrite();
        try {
            renters.insert(renter);
            Account linked = new Account(0, account.getUsername(), account.getRole(),
                    account.getDisplayName(), account.getPhone(),
                    account.getPasswordHash(), account.getPasswordSalt(),
                    account.getSecurityQuestion(),
                    account.getAnswerHash(), account.getAnswerSalt(), renter.getId(),
                    account.getCreatedOn());
            accounts.insert(linked);
            commitWrite();
            account.setId(linked.getId());
            return linked.getId();
        } catch (SQLException | RuntimeException e) {
            rollbackWrite();
            throw e;
        }
    }

    /** The username is compared without case. Returns null when there is no such account. */
    public synchronized Account findAccountByUsername(String username) throws SQLException {
        return accounts.findByUsername(username);
    }

    /** Returns the account with this id, or null. */
    public synchronized Account getAccountById(int id) throws SQLException {
        return accounts.getById(id);
    }

    /** Saves a new password hash and salt for an account. */
    public synchronized void updateAccountPassword(int accountId, String hash,
            String salt) throws SQLException {
        accounts.updatePassword(accountId, hash, salt);
    }

    // ---------- Reservation and availability ----------

    /** Saves a new reservation. */
    public synchronized int insertReservation(Reservation r) throws SQLException {
        return reservations.insert(r);
    }

    /** Returns the reservation with this id, or null. */
    public synchronized Reservation getReservationById(int id) throws SQLException {
        return reservations.getById(id);
    }

    /** Returns every reservation. */
    public synchronized List<Reservation> getAllReservations() throws SQLException {
        return reservations.getAll();
    }

    /** Returns every reservation of one renter. */
    public synchronized List<Reservation> getReservationsByRenter(
            int renterId) throws SQLException {
        return reservations.getByRenter(renterId);
    }

    /** Changes the status of a reservation. */
    public synchronized void updateReservationStatus(int id, ReservationStatus status,
            String note) throws SQLException {
        reservations.updateStatus(id, status, note);
    }

    /** Cancels every ACTIVE reservation of a vehicle. Returns how many were cancelled. */
    public synchronized int cancelActiveReservationsOfVehicle(int vehicleId,
            String note) throws SQLException {
        return reservations.cancelActiveOfVehicle(vehicleId, note);
    }

    /** Counts the active reservations of a vehicle. */
    public synchronized int countActiveReservationsOfVehicle(int vehicleId) throws SQLException {
        return reservations.countActiveOfVehicle(vehicleId);
    }

    /**
     * True when an ACTIVE reservation of the vehicle touches any day from start to end (both
     * included).
     */
    public synchronized boolean hasReservationOverlap(int vehicleId, LocalDate start,
            LocalDate end) throws SQLException {
        return reservations.hasOverlap(vehicleId, start, end);
    }

    /**
     * True when an ONGOING rental touches any day from start to end. A rental that is late (its end
     * date has passed and it is not returned) still blocks the vehicle until today.
     */
    public synchronized boolean hasRentalOverlap(int vehicleId, LocalDate start, LocalDate end,
            LocalDate today) throws SQLException {
        return rentals.hasOverlap(vehicleId, start, end, today);
    }

    /**
     * The days each vehicle is busy (ongoing rentals and active reservations that are not over
     * yet), for every vehicle at once.
     */
    public synchronized Map<Integer, List<BusySpan>> getBusySpans(
            LocalDate today) throws SQLException {
        Map<Integer, List<BusySpan>> map = new HashMap<>();
        rentals.addBusySpans(today, map);
        reservations.addBusySpans(today, map);
        for (List<BusySpan> spans : map.values()) {
            spans.sort(Comparator.comparing(BusySpan::start));
        }
        return map;
    }

    /**
     * True when the vehicle is in the fleet and no ongoing rental or active reservation touches any
     * day from start to end (both days included).
     */
    public synchronized boolean isVehicleFree(int vehicleId, LocalDate start, LocalDate end,
            LocalDate today) throws SQLException {
        return vehicles.isActive(vehicleId) && !rentals.hasOverlap(vehicleId, start, end, today)
                && !reservations.hasOverlap(vehicleId, start, end);
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
            rentals.insert(r);
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
            reservations.insert(r);
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
            if (rentals.hasOngoing(vehicleId)) {
                rollbackWrite();
                return -1;
            }
            int cancelled = reservations.cancelActiveOfVehicle(vehicleId, note);
            vehicles.setActive(vehicleId, false);
            waiting.removeAllOfVehicle(vehicleId);
            commitWrite();
            return cancelled;
        } catch (SQLException | RuntimeException e) {
            rollbackWrite();
            throw e;
        }
    }

    /** Turns a reservation into a rental in one step: both are saved or neither. */
    public synchronized int convertReservationToRental(Reservation reservation,
            Rental rental) throws SQLException {
        beginWrite();
        try {
            int rentalId = rentals.insert(rental);
            reservations.updateStatus(reservation.getId(), ReservationStatus.CONVERTED,
                    "Rental #" + rentalId);
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
        return payments.insert(p);
    }

    /** Returns the payment of a rental, or null when it has none (for example an imported one). */
    public synchronized Payment getPaymentByRental(int rentalId) throws SQLException {
        return payments.getByRental(rentalId);
    }

    /** Returns every payment, oldest first. */
    public synchronized List<Payment> getAllPayments() throws SQLException {
        return payments.getAll();
    }

    /**
     * Closes a rental in ONE transaction: the return details, the vehicle miles and the payment.
     */
    public synchronized void saveReturn(Rental rental, Payment payment) throws SQLException {
        beginWrite();
        try {
            rentals.update(rental);
            vehicles.addMileage(rental.getVehicleId(), rental.getMilesDriven());
            payments.insert(payment);
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
            maintenance.insert(m);
            if (m.getType() == MaintenanceType.PMS) {
                vehicles.moveLastPmsForward(m.getVehicleId(), m.getDoneOn());
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
        return maintenance.getByVehicle(vehicleId);
    }

    // ---------- Waiting list and check-out log ----------

    /** Puts a renter at the end of the waiting line of a vehicle. */
    public synchronized void addWaiting(int vehicleId, int renterId,
            LocalDate joinedOn) throws SQLException {
        waiting.add(vehicleId, renterId, joinedOn);
    }

    /** Returns the ids of the waiting renters of a vehicle, first in line first. */
    public synchronized List<Integer> getWaitingRenterIds(int vehicleId) throws SQLException {
        return waiting.getRenterIds(vehicleId);
    }

    /** Takes one renter out of the waiting line of a vehicle. */
    public synchronized void removeWaiting(int vehicleId, int renterId) throws SQLException {
        waiting.remove(vehicleId, renterId);
    }

    /** Returns the ids of the vehicles a renter waits for. */
    public synchronized List<Integer> getWaitingVehicleIds(int renterId) throws SQLException {
        return waiting.getVehicleIds(renterId);
    }

    /** Removes the first waiting entry of a vehicle. Returns false when nobody was waiting. */
    public synchronized boolean removeFirstWaiting(int vehicleId) throws SQLException {
        return waiting.removeFirst(vehicleId);
    }

    /** Remembers a check-out so it can be undone later, even after a restart. */
    public synchronized void logCheckout(int rentalId) throws SQLException {
        checkoutLog.log(rentalId);
    }

    /** Returns the logged rental ids, oldest first. */
    public synchronized List<Integer> getCheckoutLog() throws SQLException {
        return checkoutLog.getAll();
    }

    /** Forgets a logged check-out. */
    public synchronized void removeCheckoutLog(int rentalId) throws SQLException {
        checkoutLog.remove(rentalId);
    }

    // ---------- Import history ----------

    /** Saves a new, empty import in the history and returns its id. */
    public synchronized int insertImportBatch(String fileName, String importedBy,
            LocalDateTime importedOn) throws SQLException {
        return importBatches.insert(fileName, importedBy, importedOn, null);
    }

    /** Saves a new, empty import that remembers the fingerprint of the file content. */
    public synchronized int insertImportBatch(String fileName, String importedBy,
            LocalDateTime importedOn, String fileHash) throws SQLException {
        return importBatches.insert(fileName, importedBy, importedOn, fileHash);
    }

    /** Returns the import of a file with the same content, or null (never imported). */
    public synchronized ImportBatch findImportBatchByHash(String fileHash) throws SQLException {
        return importBatches.findByHash(fileHash);
    }

    /** Writes the final counts of an import. */
    public synchronized void finishImportBatch(int batchId, int rowsRead, int rowsAdded,
            int rowsSkipped) throws SQLException {
        importBatches.finish(batchId, rowsRead, rowsAdded, rowsSkipped);
    }

    /** Saves the rows of one import in one transaction. */
    public synchronized void insertImportRecords(int batchId, List<ImportRecord> records)
            throws SQLException {
        beginWrite();
        try {
            importRecords.insertAll(batchId, records);
            commitWrite();
        } catch (SQLException | RuntimeException e) {
            rollbackWrite();
            throw e;
        }
    }

    /** Every import, the newest first. */
    public synchronized List<ImportBatch> getImportBatches() throws SQLException {
        return importBatches.getAll();
    }

    /**
     * The rows of one import in file order. Pass "ADDED" or "SKIPPED" to see only those rows, or
     * null for all. At most limit rows are returned.
     */
    public synchronized List<ImportRecord> getImportRecords(int batchId, String status, int limit)
            throws SQLException {
        return importRecords.getRecords(batchId, status, limit);
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
