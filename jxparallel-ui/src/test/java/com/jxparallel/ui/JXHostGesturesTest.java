package com.jxparallel.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.input.JXClipboard;
import com.jxparallel.ui.native2d.JXFrame;
import com.jxparallel.ui.native2d.JXNativeNode;
import com.jxparallel.ui.native2d.JXPainter;
import com.jxparallel.ui.native2d.JXPointerEvent;
import com.jxparallel.ui.native2d.JXTextInput;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a touch host (Android) asks of {@link JXFrame}: scrolling by a gesture, and the caret, selection and clipboard of a text input. */
class JXHostGesturesTest {
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

    private static JXElement pane(int count, double scrollY) {
        JXProps.Builder props = JXProps.builder().set("prefHeight", 200.0).set("prefWidth", 300.0).set("fitToWidth", Boolean.TRUE);
        if (scrollY >= 0) {
            props.set("vvalue", scrollY); // 0..1 on a scroll pane
        }
        return JXElement.of("column", JXProps.builder().build(), JXElement.of("scroll", props.build(), rows(count)));
    }

    private static JXNativeNode scrollNode(JXNativeNode root) {
        return root.getChildren().get(0);
    }

    private static int firstRowY(JXNativeNode root) {
        return scrollNode(root).getChildren().get(0).getChildren().get(0).getY();
    }

    @Test
    void aGestureScrollsAPaneAndKeepsTheOffsetWhileTheApplicationBuildsTheSameTree() {
        JXNativeNode root = JXFrame.mount(pane(40, -1));
        JXFrame.layout(root, 320, 480);
        assertTrue(JXFrame.scrollsAt(root, 50, 50), "40 rows do not fit in 200 px");
        int before = firstRowY(root);

        assertTrue(JXFrame.scroll(root, 50, 50, 0, 120));
        JXFrame.layout(root, 320, 480);
        assertEquals(before - 120, firstRowY(root));

        assertTrue(root.reconcile(pane(40, -1)), "same props");
        JXFrame.layout(root, 320, 480);
        assertEquals(before - 120, firstRowY(root), "the scroll stays when the application builds the same tree");

        assertTrue(root.reconcile(pane(40, 0)), "the application sets its own scroll: the top");
        JXFrame.layout(root, 320, 480);
        assertEquals(before, firstRowY(root), "and then its props decide");
    }

    @Test
    void scrollingStopsAtTheEnds() {
        JXNativeNode root = JXFrame.mount(pane(40, -1));
        JXFrame.layout(root, 320, 480);
        assertFalse(JXFrame.scroll(root, 50, 50, 0, -30), "already at the top");
        assertTrue(JXFrame.scroll(root, 50, 50, 0, 100000));
        assertFalse(JXFrame.scroll(root, 50, 50, 0, 10), "at the bottom");
    }

    @Test
    void aPaneThatFitsDoesNotScrollAndDoesNotDeferThePress() {
        JXNativeNode root = JXFrame.mount(pane(2, -1));
        JXFrame.layout(root, 320, 480);
        assertFalse(JXFrame.scrollsAt(root, 50, 20));
        assertFalse(JXFrame.scroll(root, 50, 20, 0, 50));
        assertFalse(JXFrame.scrollsAt(root, 50, 400), "outside the pane");
    }

    @Test
    void aListScrollsWithinItsRowsAndReportsToOnScroll() {
        List<double[]> reported = new ArrayList<double[]>();
        JXElement[] cells = new JXElement[10];
        for (int i = 0; i < 10; i++) {
            cells[i] = JXElement.of("cell", JXProps.builder().set("value", "Item " + i).build());
        }
        // 100 items of 24 px, 10 of them built: a window the gesture cannot leave without the application
        JXProps.Builder props = JXProps.builder().set("itemCount", 100).set("first", 0).set("cellHeight", 24.0)
                .set("prefHeight", 120.0).set("prefWidth", 200.0);
        JXNativeNode root = JXFrame.mount(JXElement.of("column", JXProps.builder().build(), JXElement.of("list", props.build(), cells)));
        JXFrame.layout(root, 320, 480);
        assertTrue(JXFrame.scrollsAt(root, 50, 20));
        assertTrue(JXFrame.scroll(root, 50, 20, 0, 1000));
        JXFrame.layout(root, 320, 480);
        int moved = scrollNode(root).getChildren().get(0).getY();
        assertEquals(scrollNode(root).getY() + 1 - (10 * 24 - 118), moved, 1, "stopped at the end of the built rows: " + moved);

        Consumer<Object> onScroll = value -> reported.add((double[]) value);
        props.set("onScroll", onScroll);
        JXNativeNode root2 = JXFrame.mount(JXElement.of("column", JXProps.builder().build(), JXElement.of("list", props.build(), cells)));
        JXFrame.layout(root2, 320, 480);
        assertTrue(JXFrame.scroll(root2, 50, 20, 0, 1000));
        assertEquals(1, reported.size());
        assertTrue(reported.get(0)[1] > 10 * 24, "with onScroll the whole model is the range: " + reported.get(0)[1]);
    }

    // ---- text input ---------------------------------------------------------------------------

    private static final class Memory implements JXClipboard {
        String text;

        public String getText() {
            return text;
        }

        public void setText(String value) {
            text = value;
        }
    }

    private static JXNativeNode input(String value, List<String> changes) {
        Consumer<Object> onChange = v -> changes.add((String) v);
        return JXFrame.mount(JXElement.of("input", JXProps.builder().set("value", value).set("onChange", onChange).build()));
    }

    @Test
    void theCaretSelectionAndTheOneChangeOfAReplacement() {
        List<String> changes = new ArrayList<String>();
        JXNativeNode node = input("hello world", changes);
        assertEquals(11, JXTextInput.caret(node), "the end until the user puts it elsewhere");
        JXTextInput.select(node, 6, 11);
        assertEquals("world", JXTextInput.selectedText(node));
        JXTextInput.setText(node, "hello there", 11, 11);
        assertEquals(java.util.Collections.singletonList("hello there"), changes);
        assertEquals(11, JXTextInput.caret(node));
    }

    @Test
    void copyCutAndPasteGoThroughTheClipboard() {
        List<String> changes = new ArrayList<String>();
        JXNativeNode node = input("hello world", changes);
        Memory clipboard = new Memory();
        JXTextInput.select(node, 0, 5);
        assertTrue(JXTextInput.copy(node, clipboard));
        assertEquals("hello", clipboard.text);
        assertTrue(changes.isEmpty(), "copy changes nothing");

        assertTrue(JXTextInput.cut(node, clipboard));
        assertEquals(java.util.Collections.singletonList(" world"), changes);
        assertEquals(0, JXTextInput.caret(node));

        node.reconcile(JXElement.of("input", JXProps.builder().set("value", " world")
                .set("onChange", (Consumer<Object>) v -> changes.add((String) v)).build()));
        clipboard.text = "big\nthing";
        JXTextInput.select(node, 6, 6);
        assertTrue(JXTextInput.paste(node, clipboard));
        assertEquals(" worldbig thing", changes.get(changes.size() - 1), "a one-line input takes the clipboard on one line");
        assertFalse(JXTextInput.paste(node, new Memory()), "an empty clipboard pastes nothing");
    }

    @Test
    void aPasswordIsNeverCopiedOrCut() {
        List<String> changes = new ArrayList<String>();
        Consumer<Object> onChange = v -> changes.add((String) v);
        JXNativeNode node = JXFrame.mount(JXElement.of("password", JXProps.builder().set("value", "secret").set("onChange", onChange).build()));
        JXTextInput.selectAll(node);
        Memory clipboard = new Memory();
        assertFalse(JXTextInput.copy(node, clipboard));
        assertFalse(JXTextInput.cut(node, clipboard));
        assertEquals(null, clipboard.text);
        assertEquals(null, JXTextInput.selectedText(node));
    }

    @Test
    void theWordAtAnIndex() {
        assertArrayEquals(new int[] {6, 11}, JXTextInput.wordAt("hello world", 8));
        assertArrayEquals(new int[] {0, 5}, JXTextInput.wordAt("hello world", 5), "just after a word is that word");
        assertArrayEquals(new int[] {6, 11}, JXTextInput.wordAt("hello world", 11));
        assertArrayEquals(new int[] {3, 4}, JXTextInput.wordAt("a  b", 3));
        assertArrayEquals(new int[] {2, 2}, JXTextInput.wordAt("a  b", 2), "between spaces there is none");
    }

    @Test
    void aPressPutsTheCaretUnderTheFingerAndTheHostFocusShowsIt() {
        List<String> changes = new ArrayList<String>();
        JXNativeNode root = JXFrame.mount(JXElement.of("column", JXProps.builder().build(), JXElement.of("input",
                JXProps.builder().set("value", "hello world").set("onChange", (Consumer<Object>) v -> changes.add((String) v)).build())));
        JXFrame.layout(root, 320, 480);
        JXNativeNode field = root.getChildren().get(0);
        JXNativeNode hit = JXFrame.pointer(root, new JXPointerEvent(field.getX() + 2, field.getY() + 5, 0));
        assertNotNull(hit);
        assertEquals(0, JXTextInput.caret(hit), "a press at the left edge");
        JXNativeNode hit2 = JXFrame.pointer(root, new JXPointerEvent(field.getX() + field.getWidth() - 3, field.getY() + 5, 0));
        assertEquals(11, JXTextInput.caret(hit2), "and at the right edge");

        final List<String> texts = new ArrayList<String>();
        final int[] carets = {0};
        JXPainter recording = new JXPainter() {
            public void fillRect(float x, float y, float w, float h, int argb) { if (w == 1) { carets[0]++; } }
            public void fillRoundRect(float x, float y, float w, float h, float radius, int argb) { }
            public void fillRoundRectGradient(float x, float y, float w, float h, float radius, int top, int bottom) { }
            public void strokeRoundRect(float x, float y, float w, float h, float radius, float width, int argb) { }
            public void fillOval(float x, float y, float w, float h, int argb) { }
            public void strokeOval(float x, float y, float w, float h, float width, int argb) { }
            public void strokeArc(float x, float y, float w, float h, float start, float sweep, float width, int argb) { }
            public void line(float x1, float y1, float x2, float y2, float width, int argb) { }
            public void fillTriangle(float x0, float y0, float x1, float y1, float x2, float y2, int argb) { }
            public void text(String value, float x, float baseline, float size, boolean bold, int argb) { texts.add(value); }
            public void image(int[] pixels, int imageWidth, int imageHeight, float x, float y, float w, float h) { }
            public void clip(float x, float y, float w, float h) { }
            public void layer(float opacity, float blurRadius) { }
            public void restore() { }
        };
        JXFrame.paint(root, recording, 320, 480);
        assertEquals(0, carets[0], "no caret without the host's focus");
        JXFrame.focus(null, hit2);
        JXFrame.paint(root, recording, 320, 480);
        assertEquals(1, carets[0], "one 1 px caret once it has it");
    }
}
