package fleetrent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;

/**
 * Starts JavaFX once for all the tests that need it and remembers whether it worked. On a computer
 * with no display JavaFX cannot start: the tests that ask here then skip themselves instead of
 * failing or waiting forever.
 */
public final class FxToolkit {
    private static final long WAIT_SECONDS = 15;
    private static Boolean ready;

    private FxToolkit() {
    }

    /** Returns true when JavaFX is running. The first call starts it (at most 15 seconds). */
    public static synchronized boolean start() {
        if (ready == null) {
            ready = tryStart();
        }
        return ready;
    }

    // JavaFX is started from a helper thread, so a start that never answers cannot block the
    // tests. Nothing else of JavaFX is called unless the start worked.
    private static boolean tryStart() {
        CountDownLatch started = new CountDownLatch(1);
        Thread starter = new Thread(() -> {
            try {
                Platform.startup(started::countDown);
            } catch (IllegalStateException alreadyRunning) {
                Platform.runLater(started::countDown);
            } catch (Throwable noDisplay) {
                // the latch stays closed, so the answer is false
            }
        }, "fx-start");
        starter.setDaemon(true);
        starter.start();
        try {
            if (!started.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        Platform.setImplicitExit(false);
        return true;
    }
}
