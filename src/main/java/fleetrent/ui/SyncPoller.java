package fleetrent.ui;

import fleetrent.controller.ChangeSource;
import fleetrent.controller.ControllerException;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

// Keeps a screen in sync with the database file. Every few seconds it asks the database whether
// anything changed (by this window, by another window or by another copy of the app) and, only if
// it did, runs the refresh. So a renter's reservation shows up in the owner's window without
// pressing anything.
final class SyncPoller {
    private final ChangeSource source;
    private final Runnable onChange;
    private final Timeline timeline;
    private String last;

    SyncPoller(ChangeSource source, Runnable onChange, double seconds) {
        this.source = source;
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
        try {
            last = source.stamp();
        } catch (ControllerException e) {
            last = null;
        }
    }

    private void tick() {
        try {
            String now = source.stamp();
            if (last == null || !last.equals(now)) {
                last = now;
                onChange.run();
            }
        } catch (ControllerException e) {
            // The next tick tries again. The screens show their own database messages when they
            // load.
        }
    }
}
