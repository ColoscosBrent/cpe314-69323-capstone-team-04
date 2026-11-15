package fleetrent.controller;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javafx.concurrent.Task;

/**
 * Runs the background jobs of ONE window on the shared pool and remembers them until they end.
 * The controllers build their JavaFX Tasks here, so no screen creates a Task or a thread. When the
 * window closes, shutDown cancels the jobs and waits a little, so the database is not closed under
 * a photo search or an import that still runs.
 */
public final class TaskRunner {
    private final Set<Runnable> running = new HashSet<>();
    private boolean closing;

    /**
     * Builds a Task for the work and runs it in the background. Exactly one of the three callbacks
     * runs on the JavaFX thread when the work ends: done with the result, failed with the problem,
     * or cancelled when the window closed first.
     */
    public <T> Task<T> start(Callable<T> work, Consumer<T> done, Consumer<Throwable> failed,
            Runnable cancelled) {
        Task<T> task = new Task<T>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(e -> done.accept(task.getValue()));
        task.setOnFailed(e -> failed.accept(task.getException()));
        task.setOnCancelled(e -> cancelled.run());
        run(task);
        return task;
    }

    /**
     * Runs the job on the shared pool and remembers it until it ends. After the window started to
     * close, no new job starts.
     */
    public void run(Runnable job) {
        synchronized (this) {
            if (closing) {
                if (job instanceof Task<?> task) {
                    task.cancel();
                }
                return;
            }
            running.add(job);
        }
        Background.run(() -> {
            try {
                job.run();
            } finally {
                synchronized (this) {
                    running.remove(job);
                    notifyAll();
                }
            }
        });
    }

    /**
     * Stops accepting jobs, cancels the running ones and waits until they end (at most
     * waitMillis).
     */
    public void shutDown(long waitMillis) {
        Set<Runnable> copy;
        synchronized (this) {
            closing = true;
            copy = new HashSet<>(running);
        }
        for (Runnable job : copy) {
            if (job instanceof Task<?> task) {
                task.cancel(true);
            }
        }
        long end = System.currentTimeMillis() + waitMillis;
        synchronized (this) {
            while (!running.isEmpty()) {
                long left = end - System.currentTimeMillis();
                if (left <= 0) {
                    return;
                }
                try {
                    wait(left);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
