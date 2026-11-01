package fleetrent;

import java.util.ArrayDeque;
import java.util.Queue;

// Queue: renters wait in line and the first one in is the first one served (FIFO).
public class WaitingList {
    private final Queue<Renter> line = new ArrayDeque<>();

    public void join(Renter renter) {
        line.add(renter);
    }

    // Returns the next renter in line, or null if nobody is waiting.
    public Renter serveNext() {
        return line.poll();
    }

    public Renter peekNext() {
        return line.peek();
    }

    public int size() {
        return line.size();
    }

    public boolean isEmpty() {
        return line.isEmpty();
    }
}
