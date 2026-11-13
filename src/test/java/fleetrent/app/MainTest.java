package fleetrent.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
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
    void mainMethodIsPublicStaticAndTakesTheArguments() throws NoSuchMethodException {
        Method m = Main.class.getMethod("main", String[].class);
        assertTrue(Modifier.isStatic(m.getModifiers()));
        assertEquals(void.class, m.getReturnType());
    }

    @Test
    void appClassExistsForTheLauncher() throws ClassNotFoundException {
        assertNotNull(Class.forName("fleetrent.app.App"));
    }
}
