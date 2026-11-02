package com.jxparallel.ui.native2d;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import com.jxparallel.ui.JXProps;

/**
 * Scrolling a scroll pane, list or table with the mouse wheel (see {@link JXWindow}), or by a gesture of a host that owns the pointer.
 * The props {@code hvalue}, {@code vvalue}, {@code scrollX}, {@code scrollY} and {@code first} are the application's
 * scroll state; the wheel sets an offset the node keeps ({@code userScroll}) until the application changes one of them.
 * A node with an {@code onScroll} handler (a Consumer that gets {@code double[] {x, y}}, in pixels) also reports it, so the
 * application can move the window of a virtual list ({@code first}).
 *
 * <p>A virtual list or table has only the rows it was given: without {@code onScroll} it scrolls within them.
 */
final class JXScrolling {
    private static final String[] STATE = {"hvalue", "vvalue", "scrollX", "scrollY", "first", "itemCount"};

    private JXScrolling() {
    }

    /** Pixels one wheel notch moves: about 3 lines of 16 px, JavaFX's default is 40. */
    static final double WHEEL_STEP = 40;

    private static boolean scrollable(JXNativeNode node) {
        String type = node.getType();
        return "scroll".equals(type) || "list".equals(type) || "table".equals(type);
    }

    /** {minX, maxX, minY, maxY} the node can scroll to now. */
    private static double[] range(JXNativeNode node) {
        JXControlLayout.ScrollGeometry g = JXControlLayout.scrollGeometry(node);
        double minY = 0;
        double maxY = g.maxScrollY();
        String type = node.getType();
        if (!"scroll".equals(type) && !(node.getProperty("onScroll") instanceof Consumer)) {
            double item = "list".equals(type) ? JXControlLayout.cellHeight(node) : JXControlLayout.rowHeight(node);
            double first = Math.max(0, JXPaint.number(node, "first", 0));
            minY = Math.min(first * item, maxY);
            maxY = Math.max(minY, Math.min(maxY, (first + node.getChildren().size()) * item - g.viewH));
        }
        return new double[] {0, g.maxScrollX(), minY, maxY};
    }

    /** Moves the innermost scrollable node under (x, y) that can still move; false when none moved. */
    static boolean scrollBy(JXNativeNode root, int x, int y, double dx, double dy) {
        List<JXNativeNode> path = root.hitPath(x, y);
        int low = 0;
        if (JXOverlays.hitPath(root, x, y) != null) {
            // an open popup, menu or dialog covers the point: only what is inside it scrolls, not the pane under it
            for (int i = path.size() - 1; i >= 0 && low == 0; i--) {
                if (path.get(i).overlayHost) {
                    low = i + 1;
                }
            }
        }
        for (int i = path.size() - 1; i >= low; i--) {
            JXNativeNode node = path.get(i);
            if (!scrollable(node) || !node.contains(x, y)) { // the hit path also holds content that sticks out of its pane
                continue;
            }
            JXControlLayout.ScrollGeometry g = JXControlLayout.scrollGeometry(node);
            double[] r = range(node);
            double nx = Math.max(r[0], Math.min(r[1], g.scrollX + dx));
            double ny = Math.max(r[2], Math.min(r[3], g.scrollY + dy));
            if (nx == g.scrollX && ny == g.scrollY) {
                continue;
            }
            node.userScrollX = nx;
            node.userScrollY = ny;
            JXNativeNode.revision++; // an overlay hosted below may sit elsewhere now
            node.requestRelayout();
            for (int k = 0; k < i; k++) {
                path.get(k).childNeedsRelayout();
            }
            Object handler = node.getProperty("onScroll");
            if (handler instanceof Consumer) {
                @SuppressWarnings("unchecked")
                Consumer<Object> onScroll = (Consumer<Object>) handler;
                onScroll.accept(new double[] {nx, ny});
            }
            return true;
        }
        return false;
    }

    /**
     * A wheel turned at (x, y) by {@code dx}, {@code dy} notches (GLFW: positive dy is up, positive dx is right); with
     * {@code shift} a vertical wheel scrolls sideways. The innermost scrollable node under the point that can still move
     * takes it, so a nested pane passes the wheel to the outer one when it is at its end. True when something moved: paint again.
     */
    static boolean wheel(JXNativeNode root, int x, int y, double dx, double dy, boolean shift) {
        if (root == null) {
            return false;
        }
        double horizontal = shift && dx == 0 ? -dy : dx;
        double vertical = shift ? 0 : -dy;
        return scrollBy(root, x, y, horizontal * WHEEL_STEP, vertical * WHEEL_STEP);
    }

    /** The application changed the state a gesture had overridden: the props decide again. */
    static void release(JXNativeNode node, JXProps previous, JXProps next) {
        if (Double.isNaN(node.userScrollX) && Double.isNaN(node.userScrollY)) {
            return;
        }
        for (String key : STATE) {
            if (!Objects.deepEquals(previous.get(key), next.get(key))) {
                node.userScrollX = Double.NaN;
                node.userScrollY = Double.NaN;
                return;
            }
        }
    }
}
