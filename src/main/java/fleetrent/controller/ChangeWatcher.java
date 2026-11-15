package fleetrent.controller;

import java.util.function.Consumer;

/**
 * Asks, in the background, whether the saved data changed. The answer is a stamp: two different
 * stamps mean something was saved in between (by this window or another one).
 */
public interface ChangeWatcher {
    /**
     * Starts the question. Exactly one callback runs on the JavaFX thread: answer with the stamp,
     * or failed when the database could not be read (or the window is closing).
     */
    void ask(Consumer<String> answer, Runnable failed);
}
