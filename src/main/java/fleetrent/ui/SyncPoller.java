package fleetrent.ui;

import fleetrent.controller.ChangeWatcher;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

// Keeps a screen in sync with the database file. Every few seconds it asks the controller whether
// anything changed (by this window, by another window or by another copy of the app) and, only if
// it did, runs the refresh. The controller asks the database in a background Task, so a long
// import never freezes the window. So a renter's reservation shows up in the owner's window
// without pressing anything.
final class SyncPoller {
    private final ChangeWatcher watcher;
    private final Runnable onChange;
    private final Timeline timeline;
    private String last;
    private boolean asking;

    SyncPoller(ChangeWatcher watcher, Runnable onChange, double seconds) {
        this.watcher = watcher;
        this.onChange = onChange;
        this.timeline = new Timeline(new KeyFrame(Duration.seconds(seconds), e -> tick()));
        this.timeline.setCycleCount(Animation.INDEFINITE);
    }

    void start() {
        markSeen();
        timeline.play();
    }

    void stop() {
        timeline.stop();
    }

    // Call after the screen was refreshed by hand, so the next tick does not refresh again for no
    // reason.
    void markSeen() {
        ask(false);
    }

    private void tick() {
        ask(true);
    }

    // Asks for the change stamp. The answer comes back on the JavaFX thread. Only one question is
    // open at a time. After a failed question the next tick tries again; the screens show their
    // own database messages when they load.
    private void ask(boolean refreshOnChange) {
        if (asking) {
            return;
        }
        asking = true;
        watcher.ask(now -> {
            asking = false;
            boolean changed = last == null || !last.equals(now);
            last = now;
            if (refreshOnChange && changed) {
                onChange.run();
            }
        }, () -> asking = false);
    }
}
