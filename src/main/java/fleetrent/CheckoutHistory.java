package fleetrent;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Stack: the last check-out is the first one undone (LIFO).
 */
public class CheckoutHistory {
    private final Deque<Rental> stack = new ArrayDeque<>();

    /** Puts a rental on top of the stack. */
    public void record(Rental rental) {
        stack.push(rental);
    }

    /**
     * Returns the rental that was undone, or null if there is nothing to undo.
     */
    public Rental undoLast() {
        return stack.poll();
    }

    /** Returns how many items there are. */
    public int size() {
        return stack.size();
    }

    /** Tells whether there is nothing in it. */
    public boolean isEmpty() {
        return stack.isEmpty();
    }
}
