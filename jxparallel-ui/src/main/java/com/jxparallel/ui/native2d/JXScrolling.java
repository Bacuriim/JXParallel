package com.jxparallel.ui.native2d;

import java.util.ArrayList;
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
 * <p>A virtual list or table has only the rows it was given: without {@code onScroll} or {@code onChange} it scrolls within
 * them. With {@code onChange} (a Consumer that gets an Integer) it scrolls over the whole model and reports the row now at
 * the top whenever that changes, so the application gives the window from there ({@code first}); the offset in pixels is
 * kept when the application renders again with the row it was told.
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

    private static double itemHeight(JXNativeNode node) {
        return "list".equals(node.getType()) ? JXControlLayout.cellHeight(node) : JXControlLayout.rowHeight(node);
    }

    /** A list or table whose {@code onChange} hears of the row at the top: it moves its window itself. */
    @SuppressWarnings("unchecked")
    private static Consumer<Object> firstHandler(JXNativeNode node) {
        Object handler = node.getProperty("onChange");
        return !"scroll".equals(node.getType()) && handler instanceof Consumer ? (Consumer<Object>) handler : null;
    }

    /** {minX, maxX, minY, maxY} the node can scroll to now. */
    private static double[] range(JXNativeNode node) {
        JXControlLayout.ScrollGeometry g = JXControlLayout.scrollGeometry(node);
        double minY = 0;
        double maxY = g.maxScrollY();
        String type = node.getType();
        if (!"scroll".equals(type) && !(node.getProperty("onScroll") instanceof Consumer) && firstHandler(node) == null) {
            double item = itemHeight(node);
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
            if (scrollTo(path, i, g.scrollX + dx, g.scrollY + dy)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sets the offset of the node {@code path[i]} (clamped to what it can scroll to), as the wheel does: the node keeps it,
     * the layout and the overlays follow, {@code onScroll} hears of it. False when it stays where it is.
     */
    private static boolean scrollTo(List<JXNativeNode> path, int i, double x, double y) {
        JXNativeNode node = path.get(i);
        JXControlLayout.ScrollGeometry g = JXControlLayout.scrollGeometry(node);
        double[] r = range(node);
        double nx = Math.max(r[0], Math.min(r[1], x));
        double ny = Math.max(r[2], Math.min(r[3], y));
        if (nx == g.scrollX && ny == g.scrollY) {
            return false;
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
        Consumer<Object> onFirst = firstHandler(node);
        if (onFirst != null) {
            int top = (int) Math.max(0, Math.floor(ny / itemHeight(node)));
            if (top != (int) JXPaint.number(node, "first", 0)) {
                node.reportedFirst = top;
                onFirst.accept(Integer.valueOf(top));
            }
        }
        return true;
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

    // ---- the scroll bars ---------------------------------------------------------------------

    /** Pixels an arrow button of a bar moves. */
    private static final double ARROW_STEP = 16;

    /**
     * A primary press at the event's position: when it lands on a bar a pane, list or table draws (the numbers are those
     * of {@link JXPaint}'s bar, from {@link JXControlLayout#scrollGeometry}), that node takes it and not the content under the bar.
     * On the thumb it grabs it; on the track it centres the thumb under the pointer; on an arrow it moves a few pixels.
     * Returns the node (the drag that follows goes to {@link #dragBar}), or null when the press is not on a bar.
     */
    static JXNativeNode pressBar(List<JXNativeNode> path, JXPointerEvent event) {
        if (event.getButton() == JXInputEvent.BUTTON_SECONDARY) {
            return null;
        }
        int x = event.getX();
        int y = event.getY();
        for (int i = path.size() - 1; i >= 0; i--) {
            JXNativeNode node = path.get(i);
            if (!scrollable(node) || !node.contains(x, y) || Boolean.TRUE.equals(node.getProperty("disabled"))) {
                continue;
            }
            JXControlLayout.ScrollGeometry g = JXControlLayout.scrollGeometry(node);
            boolean vertical = g.vertical && x >= g.vx && x < g.vx + g.vw && y >= g.vy && y < g.vy + g.vh;
            boolean horizontal = g.horizontal && x >= g.hx && x < g.hx + g.hw && y >= g.hy && y < g.hy + g.hh;
            if (!vertical && !horizontal) {
                continue;
            }
            node.barDrag = 0;
            node.dragPath = new ArrayList<JXNativeNode>(path.subList(0, i + 1));
            float at = vertical ? y : x;
            float start = vertical ? g.vy : g.hx;
            float length = vertical ? g.vh : g.hw;
            float thumb = vertical ? g.vThumbY : g.hThumbX;
            float thumbLength = vertical ? g.vThumbH : g.hThumbW;
            if (at >= thumb && at <= thumb + thumbLength) {
                node.barDrag = vertical ? 1 : 2;
                node.dragOffset = at - thumb;
            } else if (at < start + JXControlLayout.BAR_BUTTON || at >= start + length - JXControlLayout.BAR_BUTTON) {
                double step = at < start + JXControlLayout.BAR_BUTTON ? -ARROW_STEP : ARROW_STEP;
                scrollTo(path, i, g.scrollX + (vertical ? 0 : step), g.scrollY + (vertical ? step : 0));
            } else {
                moveThumb(node, g, vertical, at - thumbLength / 2);
            }
            return node;
        }
        return null;
    }

    /** The pointer moved with the button held after {@link #pressBar} grabbed the thumb: the content follows. */
    static void dragBar(JXNativeNode node, JXPointerEvent event) {
        if (node.barDrag == 0 || node.dragPath == null) {
            return;
        }
        boolean vertical = node.barDrag == 1;
        moveThumb(node, JXControlLayout.scrollGeometry(node), vertical, (vertical ? event.getY() : event.getX()) - node.dragOffset);
    }

    /** Puts the thumb's start at {@code thumbStart} (window coordinates, clamped to the track) by scrolling to the matching offset. */
    private static void moveThumb(JXNativeNode node, JXControlLayout.ScrollGeometry g, boolean vertical, float thumbStart) {
        float track = (vertical ? g.vh : g.hw) - 2 * JXControlLayout.BAR_BUTTON;
        float room = track - (vertical ? g.vThumbH : g.hThumbW);
        double fraction = room <= 0 ? 0 : (thumbStart - (vertical ? g.vy : g.hx) - JXControlLayout.BAR_BUTTON) / room;
        fraction = Math.max(0, Math.min(1, fraction));
        List<JXNativeNode> path = node.dragPath;
        int i = path.size() - 1;
        if (vertical) {
            scrollTo(path, i, g.scrollX, fraction * g.maxScrollY());
        } else {
            scrollTo(path, i, fraction * g.maxScrollX(), g.scrollY);
        }
    }

    /** The application changed the state a gesture had overridden: the props decide again. */
    static void release(JXNativeNode node, JXProps previous, JXProps next) {
        if (Double.isNaN(node.userScrollX) && Double.isNaN(node.userScrollY)) {
            return;
        }
        // the application moved the window to the row it was told: its first and scrollY follow the gesture, not undo it
        Object first = next.get("first");
        boolean followed = node.reportedFirst >= 0 && first instanceof Number && ((Number) first).intValue() == node.reportedFirst;
        for (String key : STATE) {
            if (followed && ("first".equals(key) || "scrollY".equals(key))) {
                continue;
            }
            if (!Objects.deepEquals(previous.get(key), next.get(key))) {
                node.userScrollX = Double.NaN;
                node.userScrollY = Double.NaN;
                return;
            }
        }
    }
}
