package fleetrent.controller;

/** Tells a screen whether the saved data changed, so it can reload only when it has to. */
@FunctionalInterface
public interface ChangeSource {
    /** Returns a text that is different every time the saved data changes. */
    String stamp() throws ControllerException;
}
