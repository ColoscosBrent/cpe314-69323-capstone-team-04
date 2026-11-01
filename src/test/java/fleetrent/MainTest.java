package fleetrent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MainTest {

    @Test
    void startupMessageIsCorrect() {
        assertEquals("FleetRent starting...", Main.startupMessage());
    }
}
