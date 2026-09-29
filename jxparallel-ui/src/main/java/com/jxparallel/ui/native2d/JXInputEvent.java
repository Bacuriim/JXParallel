package com.jxparallel.ui.native2d;

import java.util.Collections;
import java.util.List;

/**
 * Raw input of a window, delivered to {@link JXWindow#setInputListener} on the display thread.
 * Pointer events carry the nodes under the pointer ({@link #getPath()}, root first) as laid out
 * when the event arrived, so a listener on another thread need not touch the live tree.
 */
public final class JXInputEvent {
    public enum Kind {
        PRESS, RELEASE, MOVE, SCROLL, EXIT, KEY_PRESS, KEY_RELEASE, CHAR, CLOSE_REQUEST, RESIZE, FOCUS_GAINED, FOCUS_LOST
    }

    public static final int SHIFT = 1;
    public static final int CONTROL = 2;
    public static final int ALT = 4;
    public static final int META = 8;

    public static final int BUTTON_PRIMARY = 0;
    public static final int BUTTON_SECONDARY = 1;
    public static final int BUTTON_MIDDLE = 2;

    private final Kind kind;
    private final double x;
    private final double y;
    private final int button;
    private final int modifiers;
    private final int clickCount;
    private final double scrollX;
    private final double scrollY;
    private final String key;
    private final int codepoint;
    private final boolean buttonsDown;
    private final List<JXNativeNode> path;

    private JXInputEvent(Kind kind, double x, double y, int button, int modifiers, int clickCount, double scrollX,
                         double scrollY, String key, int codepoint, boolean buttonsDown, List<JXNativeNode> path) {
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.button = button;
        this.modifiers = modifiers;
        this.clickCount = clickCount;
        this.scrollX = scrollX;
        this.scrollY = scrollY;
        this.key = key;
        this.codepoint = codepoint;
        this.buttonsDown = buttonsDown;
        this.path = path == null ? Collections.<JXNativeNode>emptyList() : Collections.unmodifiableList(path);
    }

    /** A press, release, move or exit at window coordinates; {@code buttonsDown} marks a drag. */
    public static JXInputEvent pointer(Kind kind, double x, double y, int button, int modifiers, int clickCount,
                                       boolean buttonsDown, List<JXNativeNode> path) {
        return new JXInputEvent(kind, x, y, button, modifiers, clickCount, 0, 0, null, 0, buttonsDown, path);
    }

    /** Wheel or touchpad scroll in lines; positive y scrolls up, like GLFW and JavaFX. */
    public static JXInputEvent scroll(double x, double y, double scrollX, double scrollY, int modifiers, List<JXNativeNode> path) {
        return new JXInputEvent(Kind.SCROLL, x, y, 0, modifiers, 0, scrollX, scrollY, null, 0, false, path);
    }

    /** A key press or release; {@code key} is a JavaFX KeyCode name ("ENTER", "A", "DIGIT1"...). */
    public static JXInputEvent key(Kind kind, String key, int modifiers) {
        return new JXInputEvent(kind, 0, 0, 0, modifiers, 0, 0, 0, key, 0, false, null);
    }

    /** A typed character (after keyboard layout and dead keys). */
    public static JXInputEvent character(int codepoint, int modifiers) {
        return new JXInputEvent(Kind.CHAR, 0, 0, 0, modifiers, 0, 0, 0, null, codepoint, false, null);
    }

    /** Close button, resize (x, y are the new width and height), or window focus change. */
    public static JXInputEvent window(Kind kind, double width, double height) {
        return new JXInputEvent(kind, width, height, 0, 0, 0, 0, 0, null, 0, false, null);
    }

    public Kind getKind() {
        return kind;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public int getButton() {
        return button;
    }

    public int getModifiers() {
        return modifiers;
    }

    public boolean isShiftDown() {
        return (modifiers & SHIFT) != 0;
    }

    public boolean isControlDown() {
        return (modifiers & CONTROL) != 0;
    }

    public boolean isAltDown() {
        return (modifiers & ALT) != 0;
    }

    public boolean isMetaDown() {
        return (modifiers & META) != 0;
    }

    public int getClickCount() {
        return clickCount;
    }

    public double getScrollX() {
        return scrollX;
    }

    public double getScrollY() {
        return scrollY;
    }

    public String getKey() {
        return key;
    }

    public int getCodepoint() {
        return codepoint;
    }

    public boolean isButtonsDown() {
        return buttonsDown;
    }

    /** Nodes under the pointer, root first; empty for key and window events. */
    public List<JXNativeNode> getPath() {
        return path;
    }

    /** The deepest node under the pointer, or {@code null}. */
    public JXNativeNode getTarget() {
        return path.isEmpty() ? null : path.get(path.size() - 1);
    }

    @Override
    public String toString() {
        return kind + (key != null ? " " + key : "") + (codepoint != 0 ? " '" + new String(Character.toChars(codepoint)) + "'" : "")
                + (path.isEmpty() ? "" : " at " + x + "," + y + " on " + getTarget().getType());
    }
}
