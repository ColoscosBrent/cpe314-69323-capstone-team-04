package fleetrent.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WaitingListCheckoutHistoryTest {
    @Test
    void waitingListKeepsFirstComeFirstServedOrder() {
        WaitingList line = new WaitingList();
        Renter a = new Renter(1, "Ana", null, null, null);
        Renter b = new Renter(2, "Ben", null, null, null);
        line.join(a);
        line.join(b);
        assertSame(a, line.peekNext());
        assertEquals(2, line.size());
        assertSame(a, line.serveNext());
        assertSame(b, line.serveNext());
        assertTrue(line.isEmpty());
        assertNull(line.peekNext());
    }

    @Test
    void checkoutHistoryUndoesInReverseOrder() {
        CheckoutHistory history = new CheckoutHistory();
        history.record(10);
        history.record(11);
        history.record(12);
        assertEquals(3, history.size());
        assertEquals(12, history.undoLast());
        assertEquals(11, history.undoLast());
        assertEquals(10, history.undoLast());
        assertNull(history.undoLast());
        assertFalse(history.size() > 0);
    }
}
