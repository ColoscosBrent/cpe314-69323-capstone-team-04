package fleetrent.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

// The drop-down lists, the menus, the date pickers and the window title bar must have a solid
// background in the colors of the app. This reads the style sheet, so it needs no display.
class DropdownThemeTest {
    private static String css() throws Exception {
        try (InputStream in = DropdownThemeTest.class.getResourceAsStream("/fleetrent.css")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // The text of the rule block that starts with the selector.
    private static String rule(String css, String selector) {
        int start = css.indexOf(selector + " {");
        assertTrue(start >= 0, "no rule for " + selector);
        return css.substring(start, css.indexOf('}', start));
    }

    @Test
    void theListOfADropDownHasASolidBackgroundAndAGoldBorder() throws Exception {
        String r = rule(css(), ".combo-box-popup > .list-view");
        assertTrue(r.contains("-fx-background-color: #1b1233"));
        assertTrue(r.contains("-fx-control-inner-background: #1b1233"));
        assertTrue(r.contains("#f5b800"));
    }

    @Test
    void theRowsOfADropDownAreSolidAndShowHoverAndSelection() throws Exception {
        String css = css();
        String cell = ".combo-box-popup > .list-view > .virtual-flow > .clipped-container > "
                + ".sheet > .list-cell";
        assertTrue(rule(css, cell).contains("-fx-background-color: #1b1233"));
        assertTrue(rule(css, cell + ":filled:hover").contains("#5b21b6"));
        assertTrue(rule(css, cell + ":filled:selected").contains("#f5b800"));
    }

    @Test
    void menusAndDatePickersAreSolidToo() throws Exception {
        String css = css();
        assertTrue(rule(css, ".context-menu .menu-item").contains("#1b1233"));
        assertTrue(rule(css, ".date-picker-popup .month-year-pane, .date-picker-popup "
                + ".calendar-grid").contains("#1b1233"));
        assertTrue(rule(css, ".date-picker-popup .day-cell").contains("#1b1233"));
    }

    @Test
    void theTitleBarUsesTheColorsOfTheHeaderOfTheApp() throws Exception {
        String css = css();
        String header = rule(css, ".header");
        String bar = rule(css, ".title-bar");
        assertTrue(header.contains("linear-gradient(to right, #050309, #2a1157 55%, #5b21b6)"));
        assertTrue(bar.contains("linear-gradient(to right, #050309, #2a1157 55%, #5b21b6)"));
        assertTrue(bar.contains("#f5b800"));
        assertTrue(rule(css, ".title-bar-text").contains("#f5b800"));
        assertTrue(rule(css, ".button.title-bar-close:hover").contains("#e5384d"));
        assertTrue(rule(css, ".window-frame").contains("#f5b800"));
    }
}
