package fleetrent.service;

import fleetrent.dao.Database;
import fleetrent.model.Renter;
import fleetrent.model.Sedan;
import fleetrent.model.VehicleFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Several windows (each with its own connection to the same file) try to book the same days at the
// same moment. No matter how the timing falls, only one booking may be saved, and nothing may
// crash.
class ConcurrentBookingTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private Path file;
    private String url;
    private int vehicleId;
    private final List<Integer> renterIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        file = Files.createTempFile("fleetrent-race", ".db");
        url = "jdbc:sqlite:" + file.toAbsolutePath();
        try (Database db = new Database(url)) {
            db.createTables();
            vehicleId = db.insertVehicle(VehicleFactory.create(0, "SEDAN", "Toyota", "Vios", 2022,
                    "RACE 1", "White",
                    1500, 0, null, "Car > Sedan"));
            for (int i = 0; i < 6; i++) {
                renterIds.add(db.insertRenter(new Renter(0, "Renter " + i, "0917", null, null)));
            }
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        Files.deleteIfExists(file);
    }

    // Starts one thread per renter. Each thread has its own Database (its own connection) and waits
    // for
    // the same signal, then runs the action. Returns how many threads booked successfully.
    private int race(int threads, boolean reserveInsteadOfRent) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger booked = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int renterId = renterIds.get(i);
            futures.add(pool.submit(() -> {
                try (Database own = new Database(url)) {
                    BookingService booking = new BookingService(own, () -> TODAY);
                    ready.countDown();
                    go.await();
                    try {
                        if (reserveInsteadOfRent) {
                            booking.reserve(renterId, vehicleId, TODAY.plusDays(3),
                                    TODAY.plusDays(5));
                        } else {
                            booking.rentNow(renterId, vehicleId, TODAY.plusDays(2));
                        }
                        booked.incrementAndGet();
                    } catch (IllegalStateException e) {
                        refused.incrementAndGet();
                    }
                }
                return null;
            }));
        }
        assertTrue(ready.await(20, TimeUnit.SECONDS));
        go.countDown();
        for (Future<?> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertEquals(threads, booked.get() + refused.get());
        return booked.get();
    }

    @Test
    void sixWindowsRentingTheSameCarAtTheSameMomentSaveExactlyOneRental() throws Exception {
        assertEquals(1, race(6, false));
        try (Database db = new Database(url)) {
            assertEquals(1, db.getAllRentals().size());
        }
    }

    @Test
    void sixWindowsReservingTheSameDaysSaveExactlyOneReservation() throws Exception {
        assertEquals(1, race(6, true));
        try (Database db = new Database(url)) {
            assertEquals(1, db.getAllReservations().size());
        }
    }

    @Test
    void aRentAndAReserveOnTheSameDaysAtTheSameMomentCannotBothWin() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        Future<Boolean> rent = pool.submit(() -> {
            try (Database own = new Database(url)) {
                go.await();
                try {
                    new BookingService(own, () -> TODAY).rentNow(renterIds.get(0), vehicleId,
                            TODAY.plusDays(4));
                    return true;
                } catch (IllegalStateException e) {
                    return false;
                }
            }
        });
        Future<Boolean> reserve = pool.submit(() -> {
            try (Database own = new Database(url)) {
                go.await();
                try {
                    new BookingService(own, () -> TODAY).reserve(renterIds.get(1), vehicleId,
                            TODAY.plusDays(1),
                            TODAY.plusDays(3));
                    return true;
                } catch (IllegalStateException e) {
                    return false;
                }
            }
        });
        go.countDown();
        boolean a = rent.get(60, TimeUnit.SECONDS);
        boolean b = reserve.get(60, TimeUnit.SECONDS);
        pool.shutdown();
        assertTrue(a ^ b, "exactly one of the two may be saved");
    }

    @Test
    void differentCarsAtTheSameMomentAreAllBooked() throws Exception {
        List<Integer> cars = new ArrayList<>();
        try (Database db = new Database(url)) {
            for (int i = 0; i < 5; i++) {
                cars.add(db.insertVehicle(VehicleFactory.create(0, "SUV", "Ford", "Everest", 2021,
                        "CAR " + i, "Black",
                        2500, 0, null, "Car > SUV")));
            }
        }
        ExecutorService pool = Executors.newFixedThreadPool(5);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            final int car = cars.get(i);
            final int renter = renterIds.get(i);
            results.add(pool.submit(() -> {
                try (Database own = new Database(url)) {
                    go.await();
                    new BookingService(own, () -> TODAY).rentNow(renter, car, TODAY.plusDays(2));
                    return true;
                }
            }));
        }
        go.countDown();
        for (Future<Boolean> f : results) {
            assertTrue(f.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();
        try (Database db = new Database(url)) {
            assertEquals(5, db.getAllRentals().size());
        }
    }

    @Test
    void aSecondWindowSeesWhatTheFirstWindowSavedAndRefusesTheSameDays() throws Exception {
        try (Database first = new Database(url); Database second = new Database(url)) {
            new BookingService(first, () -> TODAY).reserve(renterIds.get(0), vehicleId,
                    TODAY.plusDays(1), TODAY.plusDays(2));
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                    () -> new BookingService(second, () -> TODAY).reserve(renterIds.get(1),
                            vehicleId,
                            TODAY.plusDays(2), TODAY.plusDays(3)));
        }
    }

}
