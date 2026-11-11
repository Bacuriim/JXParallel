package com.jxparallel.ui.native2d;

/**
 * Tells "Alt pressed and released alone" from Alt used with another key or the mouse, which is what opens the menu bar on
 * Windows and Linux. Free of GLFW; the window feeds it every key and button event.
 */
final class JXAltTap {
    private static final int LEFT_ALT = 342;
    private static final int RIGHT_ALT = 346;
    private boolean pending;

    /** A key event; {@code press} is true for a press or a repeat. True on the release of Alt that completes a tap. */
    boolean key(int code, boolean press) {
        if (code != LEFT_ALT && code != RIGHT_ALT) {
            if (press) {
                pending = false; // Alt+F, Alt+Tab...
            }
            return false;
        }
        if (press) {
            pending = true;
            return false;
        }
        boolean tap = pending;
        pending = false;
        return tap;
    }

    /** A mouse button went down: Alt+click is not a tap. */
    void cancel() {
        pending = false;
    }
}
