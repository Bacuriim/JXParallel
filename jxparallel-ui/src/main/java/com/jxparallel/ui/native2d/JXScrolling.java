package com.jxparallel.ui.native2d;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import com.jxparallel.ui.JXProps;

/**
 * Scrolling a scroll pane, list or table by a gesture, for hosts that own the pointer (see {@link JXFrame#scroll}).
 * The props {@code hvalue}, {@code vvalue}, {@code scrollX}, {@code scrollY} and {@code first} are the application's
 * scroll state; a gesture sets an offset the node keeps ({@code userScroll}) until the application changes one of them.
 * A node with an {@code onScroll} handler (a Consumer that gets {@code double[] {x, y}}, in pixels) also reports it, so the
 * application can move the window of a virtual list ({@code first}).
 *
 * <p>A virtual list or table has only the rows it was given: without {@code onScroll} it scrolls within them.
 */
final class JXScrolling {
    private static final String[] STATE = {"hvalue", "vvalue", "scrollX", "scrollY", "first", "itemCount"};

    private JXScrolling() {
    }

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

    private static boolean canScroll(JXNativeNode node) {
        double[] r = range(node);
        return r[1] > r[0] || r[3] > r[2];
    }

    /**
     * Whether a touch at (x, y) lands in something a drag scrolls, and not on a control that a drag operates (a
     * scroll bar, slider or divider). A host then waits to see whether the touch is a tap before it presses.
     */
    static boolean scrollsAt(JXNativeNode root, int x, int y) {
        List<JXNativeNode> path = root.hitPath(x, y);
        if (path.isEmpty()) {
            return false;
        }
        String deepest = path.get(path.size() - 1).getType();
        if ("scrollbar".equals(deepest) || "slider".equals(deepest) || "splitpane".equals(deepest)) {
            return false;
        }
        for (JXNativeNode node : path) {
            if (scrollable(node) && canScroll(node)) {
                return true;
            }
        }
        return false;
    }

    /** Moves the innermost scrollable node under (x, y) that can still move; false when none moved. */
    static boolean scrollBy(JXNativeNode root, int x, int y, double dx, double dy) {
        List<JXNativeNode> path = root.hitPath(x, y);
        for (int i = path.size() - 1; i >= 0; i--) {
            JXNativeNode node = path.get(i);
            if (!scrollable(node)) {
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
