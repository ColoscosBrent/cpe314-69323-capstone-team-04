package fleetrent.ui;

/**
 * The spacing values used by every screen, in pixels. Using these names keeps the gaps the same
 * from one tab to the next.
 */
final class Layout {
    /** The smallest gap, between a title and its hint. */
    static final double GAP_HAIR = 3;
    /** A tiny gap. */
    static final double GAP_TINY = 4;
    /** A small gap between related items. */
    static final double GAP_SMALL = 6;
    /** The normal gap in a row of buttons. */
    static final double GAP_NORMAL = 8;
    /** The usual gap between form parts. */
    static final double GAP_MEDIUM = 10;
    /** A wider gap. */
    static final double GAP_WIDE = 12;
    /** A wider gap, used in cards. */
    static final double GAP_WIDER = 14;
    /** A large gap between cards. */
    static final double GAP_LARGE = 16;
    /** The largest gap, between page sections. */
    static final double GAP_XLARGE = 18;
    /** The smallest gap of all, between the lines of a title block. */
    static final double GAP_MINI = 2;
    /** How many rows a drop-down list shows before it scrolls. */
    static final int DROPDOWN_ROWS = 10;

    /** The width a new window opens with. */
    static final double WINDOW_WIDTH = 1320;
    /** The height a new window opens with. */
    static final double WINDOW_HEIGHT = 800;
    /** How far each new window is moved right and down, so windows do not hide each other. */
    static final double WINDOW_STEP = 28;
    /** How many windows are moved before the first position is used again. */
    static final int WINDOW_STEPS = 5;
    /** The smallest width of a window. */
    static final double WINDOW_MIN_WIDTH = 860;
    /** The smallest height of a window. */
    static final double WINDOW_MIN_HEIGHT = 620;
    /** How long the screen fades in, in milliseconds. */
    static final double FADE_MILLIS = 260;
    /** The longest time a closing window waits for its running jobs, in milliseconds. */
    static final long CLOSE_WAIT_MILLIS = 4000;
    /** The width of the lists in the small windows (removed vehicles, maintenance). */
    static final double DIALOG_LIST_WIDTH = 460;
    /** The smallest height of the lists in the small windows. */
    static final double DIALOG_LIST_HEIGHT = 200;

    private Layout() {
    }
}
