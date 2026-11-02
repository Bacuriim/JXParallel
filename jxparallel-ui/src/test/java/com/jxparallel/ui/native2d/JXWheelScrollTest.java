package com.jxparallel.ui.native2d;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The mouse wheel on a plain JXML screen (no input listener): {@link JXScrolling#wheel}, which {@link JXWindow} calls. */
class JXWheelScrollTest {
    private static JXElement button(String label) {
        return JXElement.of("button", JXProps.builder().set("label", label).set("onAction", (Runnable) () -> { }).build());
    }

    private static JXElement rows(int count) {
        JXElement[] buttons = new JXElement[count];
        for (int i = 0; i < count; i++) {
            buttons[i] = button("Row " + i);
        }
        return JXElement.of("column", JXProps.builder().set("gap", 4.0).build(), buttons);
    }

    private static JXElement pane(int count, double vvalue) {
        JXProps.Builder props = JXProps.builder().set("prefHeight", 200.0).set("prefWidth", 300.0).set("fitToWidth", Boolean.TRUE);
        if (vvalue >= 0) {
            props.set("vvalue", vvalue);
        }
        return JXElement.of("column", JXProps.builder().build(), JXElement.of("scroll", props.build(), rows(count)));
    }

    private static JXNativeNode mount(JXElement element) {
        JXNativeNode root = JXNativeNode.createBackendNode(element);
        root.layoutForBackend(320, 480);
        return root;
    }

    private static int firstRowY(JXNativeNode root) {
        return root.getChildren().get(0).getChildren().get(0).getChildren().get(0).getY();
    }

    @Test
    void aNotchScrollsAboutFortyPixelsDownAndClampsAtTheEnd() {
        JXNativeNode root = mount(pane(40, -1));
        int top = firstRowY(root);
        assertTrue(JXScrolling.wheel(root, 50, 50, 0, -1, false), "negative dy: wheel down");
        root.layoutForBackend(320, 480);
        assertEquals(top - 40, firstRowY(root));

        assertTrue(JXScrolling.wheel(root, 50, 50, 0, 1, false), "up again");
        root.layoutForBackend(320, 480);
        assertEquals(top, firstRowY(root));
        assertFalse(JXScrolling.wheel(root, 50, 50, 0, 1, false), "already at the top");

        assertTrue(JXScrolling.wheel(root, 50, 50, 0, -10000, false));
        assertFalse(JXScrolling.wheel(root, 50, 50, 0, -1, false), "at the end");
        root.layoutForBackend(320, 480);
        JXNativeNode scroll = root.getChildren().get(0);
        JXNativeNode last = scroll.getChildren().get(0).getChildren().get(39);
        assertEquals(scroll.getY() + scroll.getHeight() - 1, last.getY() + last.getHeight(), 2, "the last row ends at the view");
    }

    @Test
    void theWheelOutsideAnyPaneOrOverAPaneThatFitsDoesNothing() {
        JXNativeNode root = mount(pane(40, -1));
        assertFalse(root.getChildren().get(0).contains(50, 400), "the pane ends at 200, its content goes on below");
        assertFalse(JXScrolling.wheel(root, 50, 400, 0, -1, false), "outside the pane");
        assertFalse(JXScrolling.wheel(mount(pane(2, -1)), 50, 20, 0, -1, false), "everything fits");
        assertFalse(JXScrolling.wheel(null, 50, 20, 0, -1, false));
    }

    @Test
    void aNestedPaneScrollsFirstAndPassesTheWheelOnAtItsEnd() {
        JXElement inner = JXElement.of("scroll", JXProps.builder().set("prefHeight", 100.0).set("prefWidth", 280.0)
                .set("fitToWidth", Boolean.TRUE).build(), rows(10));
        JXElement outerContent = JXElement.of("column", JXProps.builder().build(), inner, rows(40));
        JXElement outer = JXElement.of("scroll", JXProps.builder().set("prefHeight", 300.0).set("prefWidth", 300.0)
                .set("fitToWidth", Boolean.TRUE).build(), outerContent);
        JXNativeNode root = mount(JXElement.of("column", JXProps.builder().build(), outer));
        JXNativeNode outerNode = root.getChildren().get(0);
        JXNativeNode innerNode = outerNode.getChildren().get(0).getChildren().get(0);

        assertTrue(JXScrolling.wheel(root, 50, 50, 0, -1, false));
        assertFalse(Double.isNaN(innerNode.userScrollY), "the inner pane took it");
        assertTrue(Double.isNaN(outerNode.userScrollY));

        JXScrolling.wheel(root, 50, 50, 0, -10000, false); // the inner pane to its end
        root.layoutForBackend(320, 480);
        assertTrue(Double.isNaN(outerNode.userScrollY));
        assertTrue(JXScrolling.wheel(root, 50, 50, 0, -1, false), "at its end: the outer pane moves");
        assertEquals(40.0, outerNode.userScrollY, 0.001);
    }

    @Test
    void shiftOrDxScrollsSideways() {
        JXElement wide = JXElement.of("scroll", JXProps.builder().set("prefHeight", 100.0).set("prefWidth", 200.0).build(),
                JXElement.of("row", JXProps.builder().build(), button("one one one one one one one one one one one one"),
                        button("two two two two two two two two two two two two")));
        JXNativeNode root = mount(JXElement.of("column", JXProps.builder().build(), wide));
        JXNativeNode scroll = root.getChildren().get(0);
        assertTrue(JXScrolling.wheel(root, 50, 30, 0, -1, true), "shift + wheel down: to the right");
        assertEquals(40.0, scroll.userScrollX, 0.001);
        assertTrue(JXScrolling.wheel(root, 50, 30, -1, 0, false), "dx negative: to the left");
        assertEquals(0.0, scroll.userScrollX, 0.001);
    }

    @Test
    void theOffsetSurvivesAReconcileAndIsReleasedWhenTheApplicationChangesVvalue() {
        JXNativeNode root = mount(pane(40, -1));
        int top = firstRowY(root);
        JXScrolling.wheel(root, 50, 50, 0, -3, false);
        root.layoutForBackend(320, 480);
        assertEquals(top - 120, firstRowY(root));

        assertTrue(root.reconcile(pane(40, -1)));
        root.layoutForBackend(320, 480);
        assertEquals(top - 120, firstRowY(root), "the same props: the scroll stays");

        assertTrue(root.reconcile(pane(41, -1)));
        root.layoutForBackend(320, 480);
        assertEquals(top - 120, firstRowY(root), "another row: the scroll stays");

        assertTrue(root.reconcile(pane(40, 0)));
        root.layoutForBackend(320, 480);
        assertEquals(top, firstRowY(root), "the application set vvalue: its props decide");
    }

    @Test
    void anOnScrollHandlerIsToldAndAListScrolls() {
        List<double[]> reported = new ArrayList<double[]>();
        JXElement[] cells = new JXElement[30];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = JXElement.of("cell", JXProps.builder().set("value", "Item " + i).build());
        }
        Consumer<Object> onScroll = value -> reported.add((double[]) value);
        JXProps list = JXProps.builder().set("itemCount", 30).set("cellHeight", 24.0).set("prefHeight", 120.0).set("prefWidth", 200.0)
                .set("onScroll", onScroll).build();
        JXNativeNode root = mount(JXElement.of("column", JXProps.builder().build(), JXElement.of("list", list, cells)));
        JXNativeNode listNode = root.getChildren().get(0);
        int y = listNode.getChildren().get(0).getY();
        assertTrue(JXScrolling.wheel(root, 50, 30, 0, -1, false));
        root.layoutForBackend(320, 480);
        assertEquals(y - 40, listNode.getChildren().get(0).getY());
        assertEquals(1, reported.size());
        assertEquals(40.0, reported.get(0)[1], 0.001);
    }

    @Test
    void anOpenPopupGetsTheWheelAndTheScrollPaneUnderItDoesNot() {
        JXElement select = JXElement.of("select", JXProps.builder().set("value", "b").set("overlay", true).set("showing", true)
                .set("options", new String[] {"a", "b", "c"}).set("onChange", (Consumer<Object>) v -> { }).build());
        JXElement content = JXElement.of("column", JXProps.builder().build(), select, rows(40));
        JXElement scroll = JXElement.of("scroll", JXProps.builder().set("prefHeight", 200.0).set("prefWidth", 300.0)
                .set("fitToWidth", Boolean.TRUE).build(), content);
        JXNativeNode root = mount(JXElement.of("column", JXProps.builder().build(), scroll));
        JXNativeNode selectNode = root.getChildren().get(0).getChildren().get(0).getChildren().get(0);
        JXOverlays.Popup popup = JXOverlays.popup(selectNode);
        int x = popup.x + 5;
        int y = popup.y + 5;
        assertFalse(JXScrolling.wheel(root, x, y, 0, -1, false), "the popup does not scroll: the pane under it stays");
        assertTrue(Double.isNaN(root.getChildren().get(0).userScrollY));
        assertTrue(JXScrolling.wheel(root, 50, 190, 0, -1, false), "away from the popup the pane scrolls");
    }
}
