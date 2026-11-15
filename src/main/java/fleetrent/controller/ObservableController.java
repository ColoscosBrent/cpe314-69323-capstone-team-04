package fleetrent.controller;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * The part every screen controller shares: an observable list of rows, the selected row, a status
 * text and a busy flag, and the method that runs slow work as a background Task. A screen binds
 * its controls to these properties and asks the controller to refresh; it never builds a Task.
 * Call the methods that start work from the JavaFX thread; the answers come back on it.
 *
 * @param <T> the kind of row the screen lists (a Vehicle, a Rental, ...)
 */
public abstract class ObservableController<T> {
    private final ObservableList<T> items = FXCollections.observableArrayList();
    private final ObjectProperty<T> selected = new SimpleObjectProperty<>();
    private final ReadOnlyStringWrapper status = new ReadOnlyStringWrapper("");
    private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper(false);
    // The number of the newest task of each slot. An older answer of the same slot is dropped.
    private final Map<String, Integer> newest = new ConcurrentHashMap<>();
    private TaskRunner runner = new TaskRunner();
    private int openTasks;

    // The window gives every controller its own runner, so closing the window stops its jobs.
    final void useRunner(TaskRunner windowRunner) {
        this.runner = windowRunner;
    }

    /** The rows the screen shows. They are replaced on the JavaFX thread after each refresh. */
    public ObservableList<T> items() {
        return items;
    }

    /** The row the user selected (the screen binds it to its list), or null. */
    public ObjectProperty<T> selectedProperty() {
        return selected;
    }

    /** The last problem of a refresh in plain words, or "" when the last refresh worked. */
    public ReadOnlyStringProperty statusProperty() {
        return status.getReadOnlyProperty();
    }

    /** True while at least one background task of this controller runs. */
    public ReadOnlyBooleanProperty busyProperty() {
        return busy.getReadOnlyProperty();
    }

    /**
     * Forgets the answers that are still on their way for a slot (for example the models of a
     * make after another vehicle type was chosen).
     */
    protected final void dropPending(String slot) {
        newest.merge(slot, 1, Integer::sum);
    }

    /**
     * Runs the work as a background Task. When it ends, done gets the result on the JavaFX
     * thread. A problem goes to failed as a plain message; when failed is null the message is put
     * in the status property instead. When slot is not null, only the newest task of that slot
     * reports, so a slow old answer never replaces a newer one.
     */
    protected final <R> void runTask(String slot, Callable<R> work, Consumer<R> done,
            Consumer<String> failed) {
        final int ticket = slot == null ? 0 : newest.merge(slot, 1, Integer::sum);
        openTasks++;
        busy.set(true);
        runner.start(work, value -> {
            taskEnded();
            if (isNewest(slot, ticket)) {
                status.set("");
                done.accept(value);
            }
        }, error -> {
            taskEnded();
            if (isNewest(slot, ticket)) {
                String message = error == null || error.getMessage() == null
                        ? "The work could not be done." : error.getMessage();
                if (failed == null) {
                    status.set(message);
                } else {
                    failed.accept(message);
                }
            }
        }, this::taskEnded);
    }

    private boolean isNewest(String slot, int ticket) {
        return slot == null || newest.getOrDefault(slot, 0) == ticket;
    }

    private void taskEnded() {
        openTasks = Math.max(0, openTasks - 1);
        busy.set(openTasks > 0);
    }
}
