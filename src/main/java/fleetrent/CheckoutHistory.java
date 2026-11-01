package fleetrent;

import java.util.ArrayDeque;
import java.util.Deque;

// Stack: the last check-out is the first one undone (LIFO).
public class CheckoutHistory {
    private final Deque<Rental> stack = new ArrayDeque<>();

    public void record(Rental rental) {
        stack.push(rental);
    }

    // Returns the rental that was undone, or null if there is nothing to undo.
    public Rental undoLast() {
        return stack.poll();
    }

    public int size() {
        return stack.size();
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }
}
