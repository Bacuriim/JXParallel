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
        return root.routePointer(press);
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
