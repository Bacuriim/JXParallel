package com.jxparallel.ui.native2d;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;
import com.jxparallel.ui.text.JXTextEngine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exact edges of key mapping, line breaking, text hits, calendar hits and titled panes: the
 * cases one pixel or one index either side of a limit, which the broader tests do not pin
 * (found by mutation testing).
 */
class JXBoundariesTest {
    private static float w(String s) {
        return JXTextEngine.get().width(s, JXTextEngine.DEFAULT_SIZE);
    }

    private static JXProps.Builder p() {
        return JXProps.builder();
    }

    /** The element laid out inside a parent padded by (top, left), so its x and y are not zero. */
    private static JXNativeNode placed(JXElement element, int top, int left) {
        JXNativeNode root = JXNativeNode.createBackendNode(JXElement.of("column",
                p().set("padding", new double[]{top, 0, 0, left}).build(), element));
        root.layoutForBackend(600, 500);
        JXNativeNode node = root.getChildren().get(0);
        assertEquals(left, node.getX());
        assertEquals(top, node.getY());
        return node;
    }

    // ---- keys --------------------------------------------------------------------------------

    @Test
    void everyNamedGlfwKey() {
        Map<Integer, String> keys = new LinkedHashMap<Integer, String>();
        String[] names = {"SPACE", "QUOTE", "COMMA", "MINUS", "PERIOD", "SLASH", "SEMICOLON", "EQUALS", "OPEN_BRACKET",
                "BACK_SLASH", "CLOSE_BRACKET", "BACK_QUOTE", "ESCAPE", "ENTER", "TAB", "BACK_SPACE", "INSERT", "DELETE", "RIGHT",
                "LEFT", "DOWN", "UP", "PAGE_UP", "PAGE_DOWN", "HOME", "END", "CAPS", "SCROLL_LOCK", "NUM_LOCK", "PRINTSCREEN",
                "PAUSE", "DECIMAL", "DIVIDE", "MULTIPLY", "SUBTRACT", "ADD", "ENTER", "EQUALS", "SHIFT", "SHIFT", "CONTROL",
                "CONTROL", "ALT", "ALT", "WINDOWS", "WINDOWS", "CONTEXT_MENU"};
        int[] codes = {32, 39, 44, 45, 46, 47, 59, 61, 91, 92, 93, 96, 256, 257, 258, 259, 260, 261, 262, 263, 264, 265, 266,
                267, 268, 269, 280, 281, 282, 283, 284, 330, 331, 332, 333, 334, 335, 336, 340, 344, 341, 345, 342, 346, 343, 347, 348};
        assertEquals(names.length, codes.length);
        for (int i = 0; i < codes.length; i++) {
            keys.put(codes[i], names[i]);
        }
        for (Map.Entry<Integer, String> key : keys.entrySet()) {
            assertEquals(key.getValue(), JXKeys.name(key.getKey()), "GLFW key " + key.getKey());
        }
    }

    @Test
    void keyRangesEndExactly() {
        assertEquals("F24", JXKeys.name(313), "last function key JavaFX has");
        assertEquals("UNDEFINED", JXKeys.name(314));
        assertEquals("UNDEFINED", JXKeys.name(289));
        assertEquals("UNDEFINED", JXKeys.name(319));
        assertEquals("UNDEFINED", JXKeys.name(64));
        assertEquals("UNDEFINED", JXKeys.name(58), "':' has no key of its own");
    }

    // ---- line breaking -----------------------------------------------------------------------

    @Test
    void aLeadingLineBreakStartsWithAnEmptyLine() {
        assertEquals(Arrays.asList("", "b"), JXTextLayout.lines("\nb", Float.MAX_VALUE, 12, false));
        assertEquals(Arrays.asList("a", ""), JXTextLayout.lines("a\n", Float.MAX_VALUE, 12, false));
    }

    @Test
    void textExactlyAsWideAsTheLineFits() {
        float width = w("abc");
        assertEquals(Arrays.asList("abc ", "def"), JXTextLayout.lines("abc def", width, 12, false),
                "'abc' fills the line exactly and still fits");
    }

    @Test
    void tabsAreBreakOpportunities() {
        assertEquals(Arrays.asList("abc\t", "def"), JXTextLayout.lines("abc\tdef", w("abc") + 1, 12, false));
    }

    @Test
    void theLastLineIsNotBrokenAgainWhenItFits() {
        float width = w("bb cc") + 0.5f;
        assertTrue(w("aaaa") < width && w("aaaa b") > width);
        assertEquals(Arrays.asList("aaaa ", "bb cc"), JXTextLayout.lines("aaaa bb cc", width, 12, false));
    }

    @Test
    void aLineTooNarrowForOneCharacterStillTakesOne() {
        assertEquals(Arrays.asList("W", "W"), JXTextLayout.lines("WW", 1, 12, false));
    }

    @Test
    void aCarriageReturnAtTheEndIsNoSeparator() {
        assertEquals(0, JXTextLayout.separatorAfter("a\r", 1));
        assertEquals(1, JXTextLayout.separatorAfter("a\n", 1));
    }

    @Test
    void anEllipsisThatFitsExactlyIsKept() {
        float available = w("Cust") + w(JXTextLayout.ELLIPSIS);
        assertEquals("Cust...", JXTextLayout.ellipsize("Customers", available, 12, false));
    }

    // ---- one-line text hits ------------------------------------------------------------------

    private static JXNativeNode field(String type, String value, JXProps.Builder more) {
        return placed(JXElement.of(type, more.set("value", value).set("prefWidth", 200.0).build()), 30, 40);
    }

    @Test
    void passwordHitsCountBulletsNotCharacters() {
        JXNativeNode n = field("password", "iiii", p());
        float left = n.getX() + 7;
        float mid = (w(JXPaint.bullets(2)) + w(JXPaint.bullets(3))) / 2;
        assertEquals(2, JXTextHit.fieldIndex(n, left + mid - 0.5f));
    }

    @Test
    void theMiddleOfACharacterBelongsToTheNextIndex() {
        JXNativeNode n = field("input", "ab", p());
        double left = n.getX() + 7;
        assertEquals(1, JXTextHit.fieldIndex(n, left + w("a") / 2));
        assertEquals(0, JXTextHit.fieldIndex(n, left + w("a") / 2 - 0.25));
    }

    @Test
    void rightAndCentredTextShiftTheHits() {
        JXNativeNode right = field("input", "ab", p().set("textAlignment", "RIGHT"));
        float available = right.getWidth() - 14;
        float start = right.getX() + 7 + available - w("ab");
        assertEquals(1, JXTextHit.fieldIndex(right, start + w("a") * 0.6f));
        assertEquals(0, JXTextHit.fieldIndex(right, start + w("a") * 0.4f));
        JXNativeNode centre = field("input", "ab", p().set("textAlignment", "CENTER"));
        start = centre.getX() + 7 + (available - w("ab")) / 2;
        assertEquals(1, JXTextHit.fieldIndex(centre, start + w("a") * 0.6f));
        assertEquals(0, JXTextHit.fieldIndex(centre, start + w("a") * 0.4f));
    }

    @Test
    void buttonsNarrowTheTextOfSpinnersDatePickersAndComboBoxes() {
        Map<String, Integer> buttons = new LinkedHashMap<String, Integer>();
        buttons.put("spinner", JXControlLayout.SPINNER_BUTTON);
        buttons.put("datepicker", JXControlLayout.DATE_BUTTON);
        buttons.put("select", JXNativeNode.ARROW);
        for (Map.Entry<String, Integer> b : buttons.entrySet()) {
            JXNativeNode n = field(b.getKey(), "ab", p().set("textAlignment", "RIGHT"));
            float end = n.getX() + n.getWidth() - 7 - b.getValue();
            assertEquals(1, JXTextHit.fieldIndex(n, end - w("b") * 0.6f), b.getKey());
            assertEquals(2, JXTextHit.fieldIndex(n, end - w("b") * 0.4f), b.getKey());
        }
    }

    @Test
    void theScrollKeepsTheCaretOnePixelInsideTheRightEdge() {
        String value = "0123456789012345678901234567890123456789";
        JXNativeNode n = placed(JXElement.of("input", p().set("value", value).set("caret", value.length())
                .set("prefWidth", 60.0).set("maxWidth", 60.0).build()), 30, 40);
        float left = n.getX() + 7;
        float available = n.getWidth() - 14;
        float shift = w(value) - available + 1;
        float mid = (w(value.substring(0, 30)) + w(value.substring(0, 31))) / 2;
        assertEquals(31, JXTextHit.fieldIndex(n, left + mid + 1 - shift));
        assertEquals(30, JXTextHit.fieldIndex(n, left + mid - 1 - shift));
        JXNativeNode past = placed(JXElement.of("input", p().set("value", value).set("caret", value.length() + 1)
                .set("prefWidth", 60.0).set("maxWidth", 60.0).build()), 30, 40);
        assertEquals(0, JXTextHit.fieldIndex(past, left + w("0") * 0.3f), "a caret past the end does not scroll");
    }

    // ---- text area hits ----------------------------------------------------------------------

    private static JXNativeNode area(String value, JXProps.Builder more) {
        return placed(JXElement.of("textarea", more.set("value", value).build()), 30, 50);
    }

    @Test
    void areaHitsAreRelativeToTheBoxAndItsScroll() {
        JXNativeNode n = area("ab\ncd", p().set("scrollTop", 5.0).set("prefWidth", 200.0).set("prefHeight", 100.0));
        float line = JXTextHit.lineHeight(n);
        double left = n.getX() + 1 + 7;
        double top = n.getY() + 1 + 4 - 5;
        double half = w("c") / 2;
        assertEquals(4, JXTextHit.areaIndex(n, left + half + 0.5, top + line + 0.5));
        assertEquals(3, JXTextHit.areaIndex(n, left + half - 0.5, top + line + 0.5));
        assertEquals(1, JXTextHit.areaIndex(n, left + half + 0.5, top + line - 0.5), "still the first line");
    }

    @Test
    void theEndOfALineBelongsToIt() {
        JXNativeNode n = area("ab\ncdef", p());
        assertArrayEquals(new int[]{0, 0}, JXTextHit.areaLineOf(n, 2));
        assertArrayEquals(new int[]{1, 3}, JXTextHit.areaLineOf(n, 3));
    }

    @Test
    void downMovesToTheNearestColumnOfTheNextLine() {
        JXNativeNode n = area("ab\ncdef\ng", p());
        assertEquals(4, JXTextHit.areaVertical(n, 1, 1));
        assertEquals(9, JXTextHit.areaVertical(n, 4, 1), "from 'd' to the end of 'g'");
    }

    @Test
    void wrappedAreasWrapAtTheirInnerWidth() {
        String value = "alpha beta gamma";
        int width = (int) Math.floor(w(value)) + 16 - 1;
        JXNativeNode wrapped = area(value, p().set("wrapText", true).set("prefWidth", (double) width).set("maxWidth", (double) width));
        assertEquals(width, wrapped.getWidth());
        assertEquals(2, JXTextHit.areaLineCount(wrapped), "just too narrow for one line");
        JXNativeNode wide = area(value, p().set("wrapText", true).set("prefWidth", (double) width + 2).set("maxWidth", (double) width + 2));
        assertEquals(1, JXTextHit.areaLineCount(wide));
        JXNativeNode unwrapped = area(value, p().set("prefWidth", 40.0).set("maxWidth", 40.0));
        assertEquals(1, JXTextHit.areaLineCount(unwrapped), "without wrapText lines only break at line breaks");
    }

    // ---- calendar hits -----------------------------------------------------------------------

    private static JXNativeNode calendar(int firstDayOfWeek) {
        return placed(JXElement.of("calendar", p().set("year", 2026).set("month", 9).set("firstDayOfWeek", firstDayOfWeek).build()), 20, 30);
    }

    private static int[] hit(JXNativeNode c, double rx, double ry) {
        return JXCalendar.hit(c, c.getX() + rx, c.getY() + ry);
    }

    @Test
    void calendarHeaderEdges() {
        JXNativeNode c = calendar(7);
        int pad = JXCalendar.PAD;
        int header = JXCalendar.HEADER;
        int width = JXCalendar.WIDTH;
        int[] previous = {JXCalendar.PREVIOUS};
        int[] next = {JXCalendar.NEXT};
        int[] none = {JXCalendar.NONE};
        assertArrayEquals(previous, hit(c, pad, pad));
        assertArrayEquals(none, hit(c, pad, pad - 0.5));
        assertArrayEquals(previous, hit(c, pad, pad + header - 0.5));
        assertArrayEquals(none, hit(c, pad, pad + header), "between the header and the weekdays");
        assertArrayEquals(none, hit(c, pad - 0.5, pad + 5));
        assertArrayEquals(previous, hit(c, pad + 27.5, pad + 5));
        assertArrayEquals(none, hit(c, pad + 28, pad + 5));
        assertArrayEquals(none, hit(c, width - pad - 28.5, pad + 5));
        assertArrayEquals(next, hit(c, width - pad - 28, pad + 5));
        assertArrayEquals(next, hit(c, width - pad - 0.5, pad + 5));
        assertArrayEquals(none, hit(c, width - pad, pad + 5));
    }

    @Test
    void calendarGridEdges() {
        JXNativeNode c = calendar(7);
        double gridX = JXCalendar.PAD;
        double gridY = JXCalendar.PAD + JXCalendar.HEADER + JXCalendar.WEEKDAYS;
        double cw = JXCalendar.CELL_W;
        double ch = JXCalendar.CELL_H;
        assertArrayEquals(new int[]{JXCalendar.DAY, 2026, 8, 30}, hit(c, gridX, gridY), "first cell");
        assertArrayEquals(new int[]{JXCalendar.NONE}, hit(c, gridX - 0.5, gridY));
        assertArrayEquals(new int[]{JXCalendar.NONE}, hit(c, gridX, gridY - 0.5));
        assertArrayEquals(new int[]{JXCalendar.DAY, 2026, 9, 5}, hit(c, gridX + 7 * cw - 0.5, gridY), "last column");
        assertArrayEquals(new int[]{JXCalendar.NONE}, hit(c, gridX + 7 * cw, gridY));
        assertArrayEquals(new int[]{JXCalendar.DAY, 2026, 10, 4}, hit(c, gridX, gridY + 6 * ch - 0.5), "last row");
        assertArrayEquals(new int[]{JXCalendar.NONE}, hit(c, gridX, gridY + 6 * ch));
        assertArrayEquals(new int[]{JXCalendar.DAY, 2026, 9, 16}, hit(c, gridX + 3 * cw + 1, gridY + 2 * ch + 1));
        assertArrayEquals(new int[]{JXCalendar.DAY, 2026, 9, 15}, hit(c, gridX + 3 * cw - 1, gridY + 2 * ch + 1));
        assertArrayEquals(new int[]{JXCalendar.DAY, 2026, 9, 9}, hit(c, gridX + 3 * cw + 1, gridY + 2 * ch - 1));
    }

    @Test
    void theFirstDayOfTheWeekFallsBackToSunday() {
        int[] firstCell = {JXCalendar.PAD, JXCalendar.PAD + JXCalendar.HEADER + JXCalendar.WEEKDAYS};
        assertEquals(LocalDate.of(2026, 8, 31), day(hit(calendar(1), firstCell[0], firstCell[1])), "Monday first");
        assertEquals(LocalDate.of(2026, 8, 30), day(hit(calendar(7), firstCell[0], firstCell[1])), "Sunday first");
        assertEquals(LocalDate.of(2026, 8, 30), day(hit(calendar(0), firstCell[0], firstCell[1])), "0 is no day");
        assertEquals(LocalDate.of(2026, 8, 30), day(hit(calendar(8), firstCell[0], firstCell[1])), "8 is no day");
        assertEquals(LocalDate.of(2026, 8, 29), day(hit(calendar(6), firstCell[0], firstCell[1])), "Saturday first");
    }

    private static LocalDate day(int[] hit) {
        assertEquals(JXCalendar.DAY, hit[0]);
        return LocalDate.of(hit[1], hit[2], hit[3]);
    }

    // ---- titled panes and accordions ---------------------------------------------------------

    private static JXElement titled(String label, boolean expanded, double contentW, double contentH) {
        return JXElement.of("titled", p().set("label", label).set("expanded", expanded).build(),
                JXElement.of("pane", p().set("prefWidth", contentW).set("prefHeight", contentH).build()));
    }

    @Test
    void titleTextStartsAfterTheArrowButton() {
        JXNativeNode collapsible = placed(titled("T", true, 10, 10), 0, 0);
        assertEquals(JXTitledLayout.TITLE_PAD_X + JXTitledLayout.ARROW_BUTTON, JXTitledLayout.textOffset(collapsible));
        JXNativeNode fixed = placed(JXElement.of("titled", p().set("label", "T").set("collapsible", false).build()), 0, 0);
        assertEquals(JXTitledLayout.TITLE_PAD_X, JXTitledLayout.textOffset(fixed));
    }

    @Test
    void arrowPointsDownWhenExpandedAndRightWhenCollapsed() {
        int top = 20 + Math.round((JXTitledLayout.titleHeight() - 6) / 2.0f);
        int left = 10 + JXTitledLayout.TITLE_PAD_X;
        JXNativeNode open = placed(titled("T", true, 10, 10), 0, 0);
        assertArrayEquals(new float[]{left, top, left + 8, top, left + 4, top + 6}, JXTitledLayout.arrow(open, 10, 20));
        JXNativeNode closed = placed(titled("T", false, 10, 10), 0, 0);
        float cx = left + 4;
        float cy = top + 3;
        assertArrayEquals(new float[]{cx - 3, cy - 4, cx + 3, cy, cx - 3, cy + 4}, JXTitledLayout.arrow(closed, 10, 20));
    }

    @Test
    void titledSizesAddTheTitleAndTheContentBorder() {
        int title = JXTitledLayout.titleHeight();
        JXNativeNode wideTitle = placed(titled("A title much wider than its content", true, 20, 30), 0, 0);
        double titleWidth = Math.ceil(2 * JXTitledLayout.TITLE_PAD_X + JXTitledLayout.ARROW_BUTTON + w("A title much wider than its content"));
        assertEquals(titleWidth, wideTitle.getPrefWidth());
        assertEquals(titleWidth, wideTitle.getMinWidth());
        assertEquals(title + 30 + 1, wideTitle.getPrefHeight());
        assertEquals(title + 30 + 1, wideTitle.getMaxHeight());
        JXNativeNode wideContent = placed(titled("T", true, 300, 30), 0, 0);
        assertEquals(300 + 2, wideContent.getPrefWidth());
        JXNativeNode noArrow = placed(JXElement.of("titled", p().set("label", "Title").set("collapsible", false).build(),
                JXElement.of("pane", p().set("prefWidth", 1.0).set("prefHeight", 1.0).build())), 0, 0);
        assertEquals(Math.ceil(2 * JXTitledLayout.TITLE_PAD_X + w("Title")), noArrow.getPrefWidth());
        JXNativeNode closed = placed(titled("T", false, 20, 30), 0, 0);
        assertEquals(title, closed.getPrefHeight());
        assertEquals(title, closed.getMinHeight());
        assertEquals(title, closed.getMaxHeight());
    }

    @Test
    void contentSitsInsideTheBorderBelowTheTitle() {
        JXNativeNode pane = placed(titled("T", true, 50, 40), 30, 40);
        JXNativeNode content = pane.getChildren().get(0);
        int title = JXTitledLayout.titleHeight();
        assertEquals(pane.getX() + 1, content.getX());
        assertEquals(pane.getY() + title, content.getY());
        assertEquals(pane.getWidth() - 2, content.getWidth());
        assertEquals(pane.getHeight() - title - 1, content.getHeight());
    }

    @Test
    void theFirstExpandedPaneOfAnAccordionTakesTheRest() {
        JXElement accordion = JXElement.of("accordion", p().set("prefHeight", 300.0).build(),
                titled("A", false, 80, 50), titled("B", true, 120, 60), titled("C", true, 90, 70));
        JXNativeNode node = placed(accordion, 10, 20);
        int title = JXTitledLayout.titleHeight();
        JXNativeNode a = node.getChildren().get(0);
        JXNativeNode b = node.getChildren().get(1);
        JXNativeNode c = node.getChildren().get(2);
        double cPref = title + 70 + 1;
        assertEquals(node.getY(), a.getY());
        assertEquals(title, a.getHeight());
        assertEquals(node.getY() + title, b.getY());
        assertEquals((int) (node.getHeight() - title - cPref), b.getHeight());
        assertEquals((int) (node.getY() + title + b.getHeight()), c.getY());
        assertEquals((int) cPref, c.getHeight(), "a second expanded pane keeps its pref height");
        for (JXNativeNode pane : node.getChildren()) {
            assertEquals(node.getX(), pane.getX());
            assertEquals(node.getWidth(), pane.getWidth());
        }
        JXNativeNode free = placed(JXElement.of("accordion", p().build(),
                titled("A", false, 80, 50), titled("B", true, 120, 60), titled("C", true, 90, 70)), 10, 20);
        assertEquals(120 + 2, free.getPrefWidth(), "the widest pane");
        assertEquals(title + (title + 60 + 1) + cPref, free.getPrefHeight(), 0.001, "panes stacked");
        assertEquals(free.getChildren().get(1).getMinHeight() + title + free.getChildren().get(2).getMinHeight(),
                free.getMinHeight(), 0.001, "min heights stacked");
    }
}
