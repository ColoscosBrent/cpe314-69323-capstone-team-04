package fleetrent;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Queue: renters wait in line and the first one in is the first one served (FIFO).
 */
public class WaitingList {
    private final Queue<Renter> line = new ArrayDeque<>();

    /** Adds a renter at the end of the line. */
    public void join(Renter renter) {
        line.add(renter);
    }

    /**
     * Returns the next renter in line, or null if nobody is waiting.
     */
    public Renter serveNext() {
        return line.poll();
    }

    /** Returns the renter who is first in line without removing him or her. */
    public Renter peekNext() {
        return line.peek();
    }

    /** Returns how many items there are. */
    public int size() {
        return line.size();
    }

    /** Tells whether there is nothing in it. */
    public boolean isEmpty() {
        return line.isEmpty();
    }
}
