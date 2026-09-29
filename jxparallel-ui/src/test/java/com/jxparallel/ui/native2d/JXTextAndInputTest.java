package com.jxparallel.ui.native2d;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;
import com.jxparallel.ui.text.JXTextEngine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Line breaking, text hit testing, the calendar grid, key names and pointer picking. */
class JXTextAndInputTest {
    private static float w(String s) {
        return JXTextEngine.get().width(s, JXTextEngine.DEFAULT_SIZE);
    }

    // ---- lines and ellipsis ------------------------------------------------------------------

    @Test
    void linesSplitAtBreaksKeepingEmptyLines() {
        assertEquals(Arrays.asList("a", "", "b"), JXTextLayout.lines("a\n\nb", Float.MAX_VALUE, 12, false));
        assertEquals(Arrays.asList("a", "b"), JXTextLayout.lines("a\r\nb", Float.MAX_VALUE, 12, false));
        assertEquals(Arrays.asList(""), JXTextLayout.lines("", Float.MAX_VALUE, 12, false));
    }

    @Test
    void wrappingBreaksAtSpacesAndKeepsThem() {
        float width = w("alpha beta") + 1;
        List<String> lines = JXTextLayout.lines("alpha beta gamma", width, 12, false);
        assertEquals(Arrays.asList("alpha beta ", "gamma"), lines);
        assertEquals("alpha beta gamma", String.join("", lines), "wrapped lines add up to the text");
    }

    @Test
    void aWordLongerThanTheLineBreaksBetweenCharacters() {
        List<String> lines = JXTextLayout.lines("abcdefgh", w("abc") + 0.5f, 12, false);
        assertEquals(Arrays.asList("abc", "def", "gh"), lines);
    }

    @Test
    void separatorsBetweenLines() {
        assertEquals(1, JXTextLayout.separatorAfter("a\nb", 1));
        assertEquals(2, JXTextLayout.separatorAfter("a\r\nb", 1));
        assertEquals(0, JXTextLayout.separatorAfter("ab", 1));
        assertEquals(0, JXTextLayout.separatorAfter("ab", 2));
    }

    @Test
    void ellipsisKeepsTheLongestPrefixThatFits() {
        assertEquals("Customers", JXTextLayout.ellipsize("Customers", w("Customers"), 12, false));
        String cut = JXTextLayout.ellipsize("Customers", w("Cust...") + 0.5f, 12, false);
        assertEquals("Cust...", cut);
        assertEquals("...", JXTextLayout.ellipsize("Customers", 1, 12, false));
    }

    // ---- text hit testing --------------------------------------------------------------------

    private static JXNativeNode field(String value, int caret, int width) {
        JXNativeNode n = JXNativeNode.createBackendNode(JXElement.of("input",
                JXProps.builder().set("value", value).set("caret", caret).build()));
        n.layoutForBackend(width, 25);
        return n;
    }

    @Test
    void fieldIndexIsTheNearestCaretPosition() {
        JXNativeNode n = field("abcd", 0, 200);
        assertEquals(0, JXTextHit.fieldIndex(n, 0));
        assertEquals(0, JXTextHit.fieldIndex(n, 7 + w("a") * 0.4));
        assertEquals(1, JXTextHit.fieldIndex(n, 7 + w("a") * 0.6));
        assertEquals(4, JXTextHit.fieldIndex(n, 190));
    }

    @Test
    void fieldIndexFollowsTheHorizontalScrollOfALongText() {
        String value = "0123456789012345678901234567890123456789";
        JXNativeNode n = field(value, value.length(), 60);
        // the caret is at the end, so the text is shifted left: the right edge shows its end
        assertEquals(value.length(), JXTextHit.fieldIndex(n, 60 - 7 - 1));
        assertTrue(JXTextHit.fieldIndex(n, 8) > 20, "the start of the box shows a later character");
    }

    @Test
    void textAreaIndexLinesAndVerticalMoves() {
        JXNativeNode area = JXNativeNode.createBackendNode(JXElement.of("textarea", JXProps.builder().set("value", "ab\ncdef\ng").build()));
        area.layoutForBackend(300, 200);
        float line = JXTextHit.lineHeight(area);
        double left = 1 + 7;
        double top = 1 + 4;
        assertEquals(3, JXTextHit.areaIndex(area, left, top + line * 1.5), "start of the second line");
        assertEquals(7, JXTextHit.areaIndex(area, 299, top + line * 1.5), "end of the second line");
        assertEquals(9, JXTextHit.areaIndex(area, 299, 199), "below the text: the end");
        assertArrayEquals(new int[]{1, 3}, JXTextHit.areaLineOf(area, 5));
        assertEquals(3, JXTextHit.areaLineCount(area));
        assertEquals(1, JXTextHit.areaVertical(area, 4, -1), "up from 'd' lands after 'a'");
        assertEquals(0, JXTextHit.areaVertical(area, 1, -1), "up from the first line goes to the start");
        assertEquals(9, JXTextHit.areaVertical(area, 8, 1), "down from the last line goes to the end");
    }

    // ---- calendar ----------------------------------------------------------------------------

    @Test
    void gridStartsOnTheFirstDayOfTheWeek() {
        assertEquals(LocalDate.of(2026, 8, 30), JXCalendar.gridStart(2026, 9, 7), "September 2026 starts on a Tuesday; Sunday first");
        assertEquals(LocalDate.of(2026, 8, 31), JXCalendar.gridStart(2026, 9, 1), "Monday first");
        assertEquals(LocalDate.of(2026, 2, 1), JXCalendar.gridStart(2026, 2, 7), "February 2026 starts on a Sunday");
    }

    @Test
    void calendarHitsDaysAndArrows() {
        JXNativeNode c = JXNativeNode.createBackendNode(JXElement.of("calendar",
                JXProps.builder().set("year", 2026).set("month", 9).set("firstDayOfWeek", 7).build()));
        c.layoutForBackend(JXCalendar.WIDTH, JXCalendar.HEIGHT);
        assertEquals(JXCalendar.WIDTH, c.getWidth());
        assertArrayEquals(new int[]{JXCalendar.PREVIOUS}, JXCalendar.hit(c, 6 + 10, 6 + 10));
        assertArrayEquals(new int[]{JXCalendar.NEXT}, JXCalendar.hit(c, JXCalendar.WIDTH - 6 - 10, 6 + 10));
        assertArrayEquals(new int[]{JXCalendar.NONE}, JXCalendar.hit(c, JXCalendar.WIDTH / 2, 6 + 10));
        int gridTop = 6 + 30 + 22;
        // first cell: Sunday 30 August; third cell of the first row: Tuesday 1 September
        assertArrayEquals(new int[]{JXCalendar.DAY, 2026, 8, 30}, JXCalendar.hit(c, 6 + 5, gridTop + 5));
        assertArrayEquals(new int[]{JXCalendar.DAY, 2026, 9, 1}, JXCalendar.hit(c, 6 + 2 * 32 + 5, gridTop + 5));
        assertArrayEquals(new int[]{JXCalendar.DAY, 2026, 9, 8}, JXCalendar.hit(c, 6 + 2 * 32 + 5, gridTop + 24 + 5));
        assertArrayEquals(new int[]{JXCalendar.NONE}, JXCalendar.hit(c, 1, gridTop + 5));
    }

    // ---- keys and events ---------------------------------------------------------------------

    @Test
    void glfwKeysBecomeJavaFxKeyCodeNames() {
        assertEquals("A", JXKeys.name(65));
        assertEquals("Z", JXKeys.name(90));
        assertEquals("DIGIT0", JXKeys.name(48));
        assertEquals("DIGIT9", JXKeys.name(57));
        assertEquals("F1", JXKeys.name(290));
        assertEquals("F12", JXKeys.name(301));
        assertEquals("NUMPAD0", JXKeys.name(320));
        assertEquals("NUMPAD9", JXKeys.name(329));
        assertEquals("ENTER", JXKeys.name(257));
        assertEquals("ENTER", JXKeys.name(335), "keypad enter");
        assertEquals("BACK_SPACE", JXKeys.name(259));
        assertEquals("ESCAPE", JXKeys.name(256));
        assertEquals("TAB", JXKeys.name(258));
        assertEquals("LEFT", JXKeys.name(263));
        assertEquals("SHIFT", JXKeys.name(344));
        assertEquals("CONTROL", JXKeys.name(341));
        assertEquals("UNDEFINED", JXKeys.name(-1));
    }

    @Test
    void glfwModifiersBecomeEventBits() {
        assertEquals(JXInputEvent.SHIFT, JXKeys.modifiers(1));
        assertEquals(JXInputEvent.CONTROL, JXKeys.modifiers(2));
        assertEquals(JXInputEvent.ALT, JXKeys.modifiers(4));
        assertEquals(JXInputEvent.META, JXKeys.modifiers(8));
        assertEquals(JXInputEvent.SHIFT | JXInputEvent.CONTROL, JXKeys.modifiers(3));
        assertEquals(0, JXKeys.modifiers(0));
    }

    @Test
    void inputEventsKeepWhatTheyWereGiven() {
        JXNativeNode node = JXNativeNode.createBackendNode(JXElement.text("x"));
        JXInputEvent press = JXInputEvent.pointer(JXInputEvent.Kind.PRESS, 3, 4, JXInputEvent.BUTTON_SECONDARY,
                JXInputEvent.SHIFT | JXInputEvent.ALT, 2, true, Arrays.asList(node));
        assertEquals(3, press.getX());
        assertEquals(4, press.getY());
        assertEquals(JXInputEvent.BUTTON_SECONDARY, press.getButton());
        assertTrue(press.isShiftDown());
        assertTrue(press.isAltDown());
        assertTrue(!press.isControlDown() && !press.isMetaDown());
        assertEquals(2, press.getClickCount());
        assertTrue(press.isButtonsDown());
        assertSame(node, press.getTarget());
        JXInputEvent key = JXInputEvent.key(JXInputEvent.Kind.KEY_PRESS, "ENTER", JXInputEvent.CONTROL);
        assertEquals("ENTER", key.getKey());
        assertTrue(key.isControlDown());
        assertNull(key.getTarget());
        JXInputEvent typed = JXInputEvent.character('é', 0);
        assertEquals('é', typed.getCodepoint());
        JXInputEvent wheel = JXInputEvent.scroll(1, 2, 0.5, -1, 0, null);
        assertEquals(0.5, wheel.getScrollX());
        assertEquals(-1, wheel.getScrollY());
        JXInputEvent resize = JXInputEvent.window(JXInputEvent.Kind.RESIZE, 640, 480);
        assertEquals(640, resize.getX());
        assertTrue(typed.toString().contains("'é'"));
    }

    // ---- picking -----------------------------------------------------------------------------

    @Test
    void hitPathSkipsHiddenAndMouseTransparentNodesAndRespectsClips() {
        JXElement hidden = JXElement.of("pane", JXProps.builder().set("prefWidth", 50.0).set("prefHeight", 50.0).set("hidden", true).build());
        JXElement transparent = JXElement.of("pane", JXProps.builder().set("prefWidth", 50.0).set("prefHeight", 50.0)
                .set("mouseTransparent", true).build());
        JXElement stack = JXElement.of("stack", JXProps.builder().set("alignment", "TOP_LEFT").build(), hidden, transparent);
        JXNativeNode root = JXNativeNode.createBackendNode(stack);
        root.layoutForBackend(100, 100);
        assertEquals(1, root.hitPath(10, 10).size(), "only the stack itself");

        JXElement big = JXElement.of("pane", JXProps.builder().set("prefWidth", 300.0).set("prefHeight", 300.0).build());
        JXElement clipped = JXElement.of("pane", JXProps.builder().set("clip", true).set("prefWidth", 50.0).set("prefHeight", 50.0).build(), big);
        JXElement unclipped = JXElement.of("pane", JXProps.builder().set("prefWidth", 50.0).set("prefHeight", 50.0).build(), big);
        JXNativeNode a = JXNativeNode.createBackendNode(JXElement.of("pane", JXProps.empty(), clipped));
        a.layoutForBackend(400, 400);
        assertEquals(1, a.hitPath(100, 100).size(), "outside the clip nothing below is hit");
        JXNativeNode b = JXNativeNode.createBackendNode(JXElement.of("pane", JXProps.empty(), unclipped));
        b.layoutForBackend(400, 400);
        assertEquals(3, b.hitPath(100, 100).size(), "a child outside its unclipped parent is hit");
        assertTrue(b.hitPath(500, 500).isEmpty());
    }
}
