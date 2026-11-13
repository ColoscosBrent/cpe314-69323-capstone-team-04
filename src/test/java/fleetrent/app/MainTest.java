package fleetrent.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class MainTest {

    @Test
    void startupMessageIsCorrect() {
        assertEquals("FleetRent starting...", Main.startupMessage());
    }

    @Test
    void startupMessageIsTheSameEveryTime() {
        assertEquals(Main.startupMessage(), Main.startupMessage());
        assertFalse(Main.startupMessage().isBlank());
    }

    @Test
    void mainIsAPublicStaticEntryPointThatIsNotTheApplicationClass() throws Exception {
        var main = Main.class.getMethod("main", String[].class);
        assertTrue(Modifier.isStatic(main.getModifiers()));
        assertEquals(void.class, main.getReturnType());
        assertFalse(javafx.application.Application.class.isAssignableFrom(Main.class));
    }

    @Test
    void theApplicationClassIsFoundForTheLauncher() {
        assertNotNull(App.class);
        assertTrue(javafx.application.Application.class.isAssignableFrom(App.class));
    }
}
