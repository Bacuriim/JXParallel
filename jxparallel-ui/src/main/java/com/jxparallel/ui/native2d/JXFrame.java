package com.jxparallel.ui.native2d;

import com.jxparallel.ui.JXElement;

/**
 * The public entry for hosts that own their window and their canvas (an Android view, a test, an
 * embedding): mount an element tree, lay it out, paint it through a {@link JXPainter} and feed it
 * pointer and key events, without a {@link JXWindow} and without OpenGL. All methods are static
 * wrappers of the node API; call them from one thread (the host's UI thread).
 *
 * <pre>{@code
 * JXNativeNode root = JXFrame.mount(element);
 * JXFrame.layout(root, width, height);
 * JXFrame.paint(root, painter, width, height);
 * JXNativeNode hit = JXFrame.pointer(root, new JXPointerEvent(x, y, 0));
 * }</pre>
 */
public final class JXFrame {
    /** Window background, the surface color of the light theme (ARGB), painted first by {@link #paint}. */
    public static final int BACKGROUND = JXTheme.LIGHT.surface;

    private JXFrame() {
    }

    /** A native node tree for {@code element}. */
    public static JXNativeNode mount(JXElement element) {
        return JXNativeNode.createBackendNode(element);
    }

    /** Sizes the tree to a frame of {@code width} x {@code height} logical pixels, overlays included. */
    public static void layout(JXNativeNode root, int width, int height) {
        root.layoutForBackend(Math.max(1, width), Math.max(1, height));
    }

    /**
     * Paints a laid-out tree into the box {@code 0, 0, width, height} of {@code painter}: the
     * {@link #BACKGROUND} first, then the elements and the overlays above them.
     */
    public static void paint(JXNativeNode laidOut, JXPainter painter, int width, int height) {
        painter.fillRect(0, 0, width, height, BACKGROUND);
        new JXPaint(painter).paint(laidOut);
    }

    /**
     * A press at the event's position (logical pixels): overlays first, then the deepest node under the pointer. Returns
     * the node that has the focus now, or null when an overlay took the press. A secondary press opens a context menu.
     */
    public static JXNativeNode pointer(JXNativeNode root, JXPointerEvent press) {
        JXNativeNode hit = root.routePointer(press);
        if (hit != null && press.getButton() == 0 && JXTextInput.isEditable(hit)) {
            JXTextInput.placeCaret(hit, press.getX(), press.getY()); // the press puts the caret where the finger is
        }
        return hit;
    }

    /**
     * Whether a touch at (x, y) lands in a scroll pane, list or table that can scroll (and not on a scroll bar, slider or
     * divider, which a drag operates). A touch host then presses on the lift, when it is known not to be a scroll.
     */
    public static boolean scrollsAt(JXNativeNode root, int x, int y) {
        return JXScrolling.scrollsAt(root, x, y);
    }

    /**
     * Scrolls the innermost scroll pane, list or table under (x, y) that can still move by {@code dx}, {@code dy} pixels
     * (positive: further right and down). True when something moved: paint again. It keeps the offset until the
     * application changes the scroll props; with an {@code onScroll} handler (a Consumer of {@code double[] {x, y}}) the
     * application is told. A virtual list scrolls within the rows it has.
     */
    public static boolean scroll(JXNativeNode root, int x, int y, double dx, double dy) {
        return JXScrolling.scrollBy(root, x, y, dx, dy);
    }

    /** Moves the focus the host keeps from {@code previous} to {@code next} (either may be null): the caret and selection show in {@code next}. */
    public static void focus(JXNativeNode previous, JXNativeNode next) {
        if (previous != null) {
            previous.hostFocus = false;
        }
        if (next != null) {
            next.hostFocus = true;
        }
    }

    /** The pointer moved with the button down, to the event's position: a scroll bar's thumb or a divider follows it. */
    public static void drag(JXNativeNode pressed, JXPointerEvent move) {
        pressed.dispatchDrag(move);
    }

    /** A key went down: Escape and Enter belong to an open overlay first, otherwise {@code focused} (may be null) gets it. */
    public static void key(JXNativeNode root, JXKeyEvent event, JXNativeNode focused) {
        root.routeKey(event, focused);
    }

    /** A typed character for the focused node. */
    public static void character(JXNativeNode focused, char value) {
        focused.dispatchKey(new JXKeyEvent(0, value));
    }

    /**
     * Lets timed snack bars close; returns the milliseconds until the next one is due, or -1 when none waits. Call it
     * after each frame and, while it returns a time, again when that time has passed.
     */
    public static long tick(JXNativeNode root) {
        return root.tickOverlays();
    }
}
