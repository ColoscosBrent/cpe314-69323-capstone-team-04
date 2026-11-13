package fleetrent.controller;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One shared pool for every background job of every window. A fast double click cannot start many
 * threads: at most WORKER_COUNT jobs run at once and the rest wait in line. The screens never
 * create a thread; they hand a job (usually a JavaFX Task) to this class.
 */
public final class Background {
    private static final int WORKER_COUNT = 4;
    private static final AtomicInteger WORKER_NUMBER = new AtomicInteger();
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(WORKER_COUNT, job -> {
        Thread thread = new Thread(job, "fleetrent-worker-" + WORKER_NUMBER.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    private Background() {
    }

    /** Runs the job on the shared pool so the window never freezes. */
    public static void run(Runnable job) {
        WORKERS.execute(job);
    }

    /** Stops the shared pool when the program closes. Jobs that are still running are interrupted. */
    public static void stop() {
        WORKERS.shutdownNow();
    }
}
