package fleetrent.model;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class QueueStackTest {
    @Test
    void waitingListServesInFirstInFirstOutOrder() {
        WaitingList list = new WaitingList();
        Renter first = new Renter(1, "Ana", "0917", "ana@x.com", "Cebu");
        Renter second = new Renter(2, "Ben", "0918", "ben@x.com", "Cebu");
        list.join(first);
        list.join(second);
        assertSame(first, list.serveNext());
        assertSame(second, list.serveNext());
    }

    @Test
    void serveNextOnEmptyListReturnsNull() {
        assertNull(new WaitingList().serveNext());
    }

    @Test
    void undoRemovesTheLastCheckoutFirst() {
        CheckoutHistory history = new CheckoutHistory();
        history.record(1);
        history.record(2);
        assertEquals(2, history.undoLast());
        assertEquals(1, history.size());
    }

    @Test
    void undoOnEmptyHistoryReturnsNull() {
        CheckoutHistory history = new CheckoutHistory();
        assertTrue(history.isEmpty());
        assertNull(history.undoLast());
    }
}
