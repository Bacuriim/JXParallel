package com.jxparallel.ui.native2d;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The scroll bars a pane, list or table draws take the mouse (no input listener): press on the thumb and drag, press on the track. */
class JXScrollBarDragTest {
    private static JXElement button(String label, AtomicInteger clicks, double width) {
        JXProps.Builder props = JXProps.builder().set("label", label).set("onAction", (Runnable) clicks::incrementAndGet)
                .set("prefHeight", 30.0);
        if (width > 0) {
            props.set("prefWidth", width);
        }
        return JXElement.of("button", props.build());
    }

    private static JXNativeNode mount(JXElement scroll) {
        JXNativeNode root = JXNativeNode.createBackendNode(JXElement.of("column", JXProps.builder().build(), scroll));
        root.layoutForBackend(320, 480);
        return root;
    }

    /** Tall content (40 rows of 30 px, 4 px apart) in a 300 x 200 pane; the rows are {@code width} wide (0: the pane's). */
    private static JXNativeNode tall(AtomicInteger clicks, double width) {
        JXElement[] rows = new JXElement[40];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = button("Row " + i, clicks, width);
        }
        JXProps.Builder props = JXProps.builder().set("prefHeight", 200.0).set("prefWidth", 300.0);
        if (width <= 0) {
            props.set("fitToWidth", Boolean.TRUE);
        }
        return mount(JXElement.of("scroll", props.build(), JXElement.of("column", JXProps.builder().set("gap", 4.0).build(), rows)));
    }

    private static JXPointerEvent at(float x, float y) {
        return new JXPointerEvent((int) x, (int) y, 0);
    }

    private static JXControlLayout.ScrollGeometry geometry(JXNativeNode root) {
        return JXControlLayout.scrollGeometry(root.getChildren().get(0));
    }

    private static void relayout(JXNativeNode root) {
        root.layoutForBackend(320, 480);
    }

    @Test
    void draggingTheThumbMovesTheContentProportionallyAndClamps() {
        JXNativeNode root = tall(new AtomicInteger(), 0);
        JXNativeNode pane = root.getChildren().get(0);
        JXControlLayout.ScrollGeometry g = geometry(root);
        float x = g.vx + 5;
        float grab = g.vThumbY + 6; // 6 px inside the thumb
        assertSame(pane, root.routePointer(at(x, grab)), "the pane takes the press");
        assertTrue(Double.isNaN(pane.userScrollY), "grabbing does not move it");

        float room = g.vh - 2 * JXControlLayout.BAR_BUTTON - g.vThumbH;
        pane.dispatchDrag(at(x, grab + 30));
        assertEquals(30 / room * g.maxScrollY(), pane.userScrollY, 0.5);
        relayout(root);
        assertEquals(g.vThumbY + 30, geometry(root).vThumbY, 0.5, "the thumb stayed under the pointer");

        pane.dispatchDrag(at(x, grab + 5000)); // far outside the window: still follows, clamped
        assertEquals(g.maxScrollY(), pane.userScrollY, 0.001);
        pane.dispatchDrag(at(x + 400, grab - 5000));
        assertEquals(0, pane.userScrollY, 0.001);
    }

    @Test
    void aPressOnTheTrackCentresTheThumbUnderThePointer() {
        JXNativeNode root = tall(new AtomicInteger(), 0);
        JXNativeNode pane = root.getChildren().get(0);
        JXControlLayout.ScrollGeometry g = geometry(root);
        float y = g.vy + g.vh - JXControlLayout.BAR_BUTTON - 20; // on the track, below the thumb
        assertSame(pane, root.routePointer(at(g.vx + 5, y)));
        relayout(root);
        JXControlLayout.ScrollGeometry after = geometry(root);
        assertEquals(y, after.vThumbY + after.vThumbH / 2, 1.0, "the middle of the thumb is under the pointer");
        pane.dispatchDrag(at(g.vx + 5, y - 40)); // the press on the track did not grab the thumb
        assertEquals(after.scrollY, pane.userScrollY, 0.001);
    }

    @Test
    void theHorizontalBarWorks() {
        JXElement wide = JXElement.of("row", JXProps.builder().build(), button("one", new AtomicInteger(), 700));
        JXNativeNode root = mount(JXElement.of("scroll", JXProps.builder().set("prefHeight", 100.0).set("prefWidth", 200.0).build(), wide));
        JXNativeNode pane = root.getChildren().get(0);
        JXControlLayout.ScrollGeometry g = geometry(root);
        assertTrue(g.horizontal && !g.vertical);
        float y = g.hy + 5;
        float grab = g.hThumbX + 4;
        assertSame(pane, root.routePointer(at(grab, y)));
        float room = g.hw - 2 * JXControlLayout.BAR_BUTTON - g.hThumbW;
        pane.dispatchDrag(at(grab + 20, y + 300)); // the pointer may leave the bar sideways
        assertEquals(20 / room * g.maxScrollX(), pane.userScrollX, 0.5);
        pane.dispatchDrag(at(grab + 5000, y));
        assertEquals(g.maxScrollX(), pane.userScrollX, 0.001);
        relayout(root);
        assertEquals(g.maxScrollX(), geometry(root).scrollX, 0.001);
    }

    @Test
    void aListBarDragWorksAndReports() {
        List<double[]> reported = new ArrayList<double[]>();
        JXElement[] cells = new JXElement[30];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = JXElement.of("cell", JXProps.builder().set("value", "Item " + i).build());
        }
        Consumer<Object> onScroll = value -> reported.add((double[]) value);
        JXProps list = JXProps.builder().set("itemCount", 30).set("cellHeight", 24.0).set("prefHeight", 120.0).set("prefWidth", 200.0)
                .set("onScroll", onScroll).build();
        JXNativeNode root = mount(JXElement.of("list", list, cells));
        JXNativeNode node = root.getChildren().get(0);
        JXControlLayout.ScrollGeometry g = geometry(root);
        assertSame(node, root.routePointer(at(g.vx + 5, g.vThumbY + 3)));
        node.dispatchDrag(at(g.vx + 5, g.vThumbY + 3 + 20));
        assertEquals(1, reported.size());
        float room = g.vh - 2 * JXControlLayout.BAR_BUTTON - g.vThumbH;
        assertEquals(20 / room * g.maxScrollY(), reported.get(0)[1], 0.5);
    }

    @Test
    void aPressOnTheBarDoesNotClickTheButtonUnderIt() {
        AtomicInteger clicks = new AtomicInteger();
        JXNativeNode root = tall(clicks, 600); // the rows run under the bar
        JXNativeNode pane = root.getChildren().get(0);
        JXControlLayout.ScrollGeometry g = geometry(root);
        float x = g.vx + 5;
        float y = g.vy + g.vh - JXControlLayout.BAR_BUTTON - 5; // the track, over a row
        while (root.hitTest((int) x, (int) y) == pane) {
            y--; // not in the gap between two rows: the empty gap of the column is no longer hit, the pane is
        }
        List<JXNativeNode> path = root.hitPath((int) x, (int) y);
        assertTrue(path.get(path.size() - 1) != pane, "a row lies under the bar");
        assertSame(pane, root.routePointer(at(x, y)));
        assertEquals(0, clicks.get());
        assertSame(pane, root.routePointer(at(x, geometry(root).vThumbY + 3)));
        assertEquals(0, clicks.get());
    }

    @Test
    void aPressOnContentOutsideTheBarStillReachesIt() {
        AtomicInteger clicks = new AtomicInteger();
        JXNativeNode root = tall(clicks, 0);
        JXNativeNode pane = root.getChildren().get(0);
        JXControlLayout.ScrollGeometry g = geometry(root);
        JXNativeNode hit = root.routePointer(at(g.viewX + 20, g.viewY + 10));
        assertTrue(hit != null && hit != pane, "the row");
        assertEquals(1, clicks.get());
        hit.dispatchDrag(at(g.vx + 5, g.vy + 100)); // dragging after a content press scrolls nothing
        assertTrue(Double.isNaN(pane.userScrollY));
    }
}
