package com.jxparallel.ui.native2d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

/**
 * The highlighted row of open popups and menus: the state layer under the pointer (routed through
 * {@link JXNativeNode#routeHover}, as the window does) and the keyboard (Up, Down, Home, End, Left, Right and Enter,
 * through {@link JXNativeNode#routeKey} with synthetic key events). Headless; no text is asserted, only rows, state and
 * painter calls.
 */
class JXOverlayKeysTest {
    private static final int ESCAPE = 256;
    private static final int ENTER = 257;
    private static final int RIGHT = 262;
    private static final int LEFT = 263;
    private static final int DOWN = 264;
    private static final int UP = 265;
    private static final int PAGE_DOWN = 267;
    private static final int HOME = 268;
    private static final int END = 269;
    private static final int W = 400;
    private static final int H = 300;

    private final List<Object> changes = new ArrayList<Object>();
    private final List<Object> showings = new ArrayList<Object>();
    private final List<String> calls = new ArrayList<String>();
    private final Consumer<Object> onChange = changes::add;
    private final Consumer<Object> onShowing = showings::add;

    private java.util.function.Supplier<LocalDate> today;
    private java.util.function.Supplier<Locale> locale;

    @BeforeEach
    void fixTheCalendar() {
        today = JXOverlays.today;
        locale = JXOverlays.locale;
        JXOverlays.today = () -> LocalDate.of(2026, 2, 3);
        JXOverlays.locale = () -> Locale.ENGLISH;
    }

    @AfterEach
    void restore() {
        JXOverlays.today = today;
        JXOverlays.locale = locale;
    }

    private static JXProps.Builder p() {
        return JXProps.builder();
    }

    private static JXElement e(String type, JXProps.Builder props, JXElement... children) {
        return JXElement.of(type, props.build(), children);
    }

    private static JXNativeNode mount(JXElement element) {
        JXNativeNode node = JXNativeNode.createBackendNode(element);
        node.layoutForBackend(W, H);
        return node;
    }

    private static void press(JXNativeNode root, int code, JXNativeNode focused) {
        root.routeKey(new JXKeyEvent(code, '\0'), focused);
    }

    private static boolean hover(JXNativeNode root, JXNativeNode node) {
        return root.routeHover(node.getX() + node.getWidth() / 2, node.getY() + node.getHeight() / 2);
    }

    private static String record(JXNativeNode root) {
        JXPaintRecordingTest.RecordingPainter painter = new JXPaintRecordingTest.RecordingPainter();
        new JXPaint(painter).paint(root);
        return painter.log.toString();
    }

    /** Whether the log has a call whose coordinates are the node's and whose color is {@code color} (hex, as logged). */
    private static boolean layerOver(String log, String call, JXNativeNode row, String color) {
        String prefix = String.format(Locale.ROOT, "%s %.2f %.2f %.2f %.2f", call, (float) row.getX(), (float) row.getY(),
                (float) row.getWidth(), (float) row.getHeight());
        for (String line : log.split("\n")) {
            if (line.startsWith(prefix) && line.endsWith(" " + color)) {
                return true;
            }
        }
        return false;
    }

    private static String hex(int argb) {
        return Integer.toHexString(argb);
    }

    // ---- menus ---------------------------------------------------------------------------------------

    private JXElement item(String label) {
        return e("menuitem", p().set("label", label).set("onAction", (Runnable) () -> calls.add(label)));
    }

    private JXElement menuButton(boolean showing) {
        return e("menubutton", p().set("label", "Actions").set("showing", showing).set("onShowing", onShowing),
                item("Open"),
                e("menuitem", p().set("label", "Locked").set("disabled", true).set("onAction", (Runnable) () -> calls.add("Locked"))),
                e("menuitem", p().set("separator", true).set("label", "")),
                e("menu", p().set("label", "Export"), item("PDF"), item("CSV")),
                item("Quit"));
    }

    private JXNativeNode host(JXNativeNode root) {
        return root.getChildren().get(0);
    }

    @Test
    void thePointerHighlightsTheMenuRowUnderItAndOnlyRowsThatCanActAndPaintsItsStateLayer() {
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), menuButton(true)));
        JXNativeNode menu = host(root);
        List<JXNativeNode> rows = menu.getChildren();
        assertTrue(hover(root, rows.get(0)));
        assertSame(rows.get(0), menu.hot);
        assertFalse(menu.hotKey);
        assertFalse(hover(root, rows.get(0)), "the same row: nothing changed, no new frame");
        assertTrue(layerOver(record(root), "fillRect", rows.get(0), hex(JXTheme.withAlpha(JXTheme.LIGHT.onSurface, JXTheme.HOVER))),
                "the hover layer, 8% of the on-surface color");
        assertTrue(hover(root, rows.get(1)), "a disabled row takes the highlight away");
        assertNull(menu.hot);
        assertFalse(hover(root, rows.get(2)), "a separator is none either");
        assertTrue(hover(root, rows.get(4)));
        assertSame(rows.get(4), menu.hot);
        assertTrue(root.routeHover(-1, -1), "the pointer leaves the window: the highlight it made goes");
        assertNull(menu.hot);
        assertFalse(layerOver(record(root), "fillRect", rows.get(4), hex(JXTheme.withAlpha(JXTheme.LIGHT.onSurface, JXTheme.HOVER))));
    }

    @Test
    void theKeysMoveTheMenuHighlightOverEnabledRowsAndWrapAround() {
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), menuButton(true)));
        JXNativeNode menu = host(root);
        List<JXNativeNode> rows = menu.getChildren();
        press(root, DOWN, menu);
        assertSame(rows.get(0), menu.hot, "the first row that can act");
        assertTrue(menu.hotKey);
        assertTrue(layerOver(record(root), "fillRect", rows.get(0), hex(JXTheme.withAlpha(JXTheme.LIGHT.onSurface, JXTheme.FOCUS))),
                "the keys' highlight is the focus layer, 12%");
        press(root, DOWN, menu);
        assertSame(rows.get(3), menu.hot, "a disabled row and a separator are skipped");
        press(root, DOWN, menu);
        assertSame(rows.get(4), menu.hot);
        press(root, DOWN, menu);
        assertSame(rows.get(0), menu.hot, "wraps to the first");
        press(root, UP, menu);
        assertSame(rows.get(4), menu.hot, "and back to the last");
        press(root, HOME, menu);
        assertSame(rows.get(0), menu.hot);
        press(root, END, menu);
        assertSame(rows.get(4), menu.hot);
        assertTrue(showings.isEmpty() && changes.isEmpty() && calls.isEmpty(), "moving does nothing but move");
    }

    @Test
    void upFromNothingStartsAtTheLastRowAndAPointerHighlightIsTheStartOfTheKeys() {
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), menuButton(true)));
        JXNativeNode menu = host(root);
        List<JXNativeNode> rows = menu.getChildren();
        press(root, UP, menu);
        assertSame(rows.get(4), menu.hot);
        hover(root, rows.get(0));
        assertFalse(menu.hotKey, "now the pointer's");
        press(root, DOWN, menu);
        assertSame(rows.get(3), menu.hot, "Down continues from the row under the pointer");
        assertTrue(menu.hotKey);
        assertFalse(root.routeHover(-1, -1), "a highlight the keys made stays when the pointer goes");
        assertSame(rows.get(3), menu.hot);
    }

    @Test
    void enterRunsTheHighlightedItemAndClosesTheMenuAndDoesNothingWithoutAHighlight() {
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), menuButton(true)));
        JXNativeNode menu = host(root);
        press(root, ENTER, menu);
        assertTrue(calls.isEmpty());
        press(root, DOWN, menu);
        press(root, ENTER, menu);
        assertEquals(Arrays.asList("Open"), calls);
        assertEquals(Arrays.<Object>asList(false), showings, "asked to close");
        assertNull(menu.hot, "closing clears the highlight");
    }

    @Test
    void rightOpensASubmenuOnItsFirstRowAndLeftClosesItBackToTheRow() {
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), menuButton(true)));
        JXNativeNode menu = host(root);
        JXNativeNode export = menu.getChildren().get(3);
        press(root, DOWN, menu);
        press(root, DOWN, menu);
        assertSame(export, menu.hot);
        assertEquals(1, JXOverlays.popup(menu).panels.size());
        press(root, RIGHT, menu);
        assertEquals(2, JXOverlays.popup(menu).panels.size(), "the submenu is open");
        assertSame(export.getChildren().get(0), menu.hot, "on its first row");
        press(root, DOWN, menu);
        assertSame(export.getChildren().get(1), menu.hot);
        press(root, DOWN, menu);
        assertSame(export.getChildren().get(0), menu.hot, "wraps inside the submenu");
        press(root, LEFT, menu);
        assertEquals(1, JXOverlays.popup(menu).panels.size(), "closed");
        assertSame(export, menu.hot, "the row that opened it is the highlighted one");
        press(root, LEFT, menu);
        assertEquals(1, JXOverlays.popup(menu).panels.size(), "Left in the outer panel of a menu button does nothing");
        press(root, ENTER, menu);
        assertEquals(2, JXOverlays.popup(menu).panels.size(), "Enter on a submenu row opens it too");
        press(root, ENTER, menu);
        assertEquals(Arrays.asList("PDF"), calls, "and Enter on its row runs it");
        assertEquals(Arrays.<Object>asList(false), showings);
    }

    @Test
    void movingInAnOuterPanelClosesTheSubmenusBeyondIt() {
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), menuButton(true)));
        JXNativeNode menu = host(root);
        press(root, DOWN, menu);
        press(root, DOWN, menu);
        press(root, RIGHT, menu);
        assertEquals(2, JXOverlays.popup(menu).panels.size());
        assertTrue(hover(root, menu.getChildren().get(4)), "the pointer reaches a row of the outer panel");
        press(root, HOME, menu);
        assertSame(menu.getChildren().get(0), menu.hot);
        assertEquals(1, JXOverlays.popup(menu).panels.size(), "the keys work in that panel and the submenu closes");
    }

    @Test
    void theArrowsOfAMenuBarOpenTheNeighbouringMenusAndTheirSubmenus() {
        JXElement bar = e("menubar", p(),
                e("menu", p().set("label", "File"), item("New"), e("menuitem", p().set("separator", true).set("label", "")), item("Exit")),
                e("menu", p().set("label", "Edit"), item("Undo"), e("menu", p().set("label", "Find"), item("Next"))));
        JXNativeNode root = mount(e("column", p(), bar));
        JXNativeNode menuBar = host(root);
        JXNativeNode file = menuBar.getChildren().get(0);
        JXNativeNode edit = menuBar.getChildren().get(1);
        root.routePointer(new JXPointerEvent(file.getX() + 4, file.getY() + 4, 0));
        assertEquals(0, menuBar.openChild);
        press(root, DOWN, menuBar);
        assertSame(file.getChildren().get(0), menuBar.hot);
        press(root, RIGHT, menuBar);
        assertEquals(1, menuBar.openChild, "Right on an item that is no submenu goes to the next title");
        assertSame(edit.getChildren().get(0), menuBar.hot, "on the first row of its menu");
        press(root, RIGHT, menuBar);
        assertEquals(0, menuBar.openChild, "and wraps");
        press(root, LEFT, menuBar);
        assertEquals(1, menuBar.openChild, "Left goes back");
        press(root, DOWN, menuBar);
        assertSame(edit.getChildren().get(1), menuBar.hot);
        press(root, RIGHT, menuBar);
        assertEquals(1, menuBar.openChild, "Right on a submenu row opens that, it does not leave the menu");
        assertEquals(2, JXOverlays.popup(menuBar).panels.size(), "the menu and its submenu");
        assertSame(edit.getChildren().get(1).getChildren().get(0), menuBar.hot);
        press(root, LEFT, menuBar);
        assertEquals(1, menuBar.openChild, "Left in a submenu closes only the submenu");
        assertSame(edit.getChildren().get(1), menuBar.hot);
        press(root, ESCAPE, menuBar);
        assertEquals(-1, menuBar.openChild);
    }

    // ---- the select's list ---------------------------------------------------------------------------

    private JXElement select(String value, String... options) {
        return e("select", p().set("value", value).set("overlay", true).set("options", options).set("showing", true)
                .set("onChange", onChange).set("onShowing", onShowing));
    }

    @Test
    void theListOfAnOpenSelectFollowsThePointerAndTheKeysAndEnterPicksTheHighlightedOption() {
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), select("b", "a", "b", "c", "d")));
        JXNativeNode select = host(root);
        JXOverlays.Popup popup = JXOverlays.popup(select);
        int[] cell = popup.cells[3];
        assertTrue(root.routeHover(cell[0] + 5, cell[1] + 5));
        assertEquals(Integer.valueOf(3), select.hot);
        String log = record(root);
        assertTrue(log.contains(String.format(Locale.ROOT, "fillRect %.2f %.2f %.2f %.2f %s", (float) cell[0] + 2, (float) cell[1],
                (float) cell[2] - 4, (float) cell[3], hex(JXTheme.withAlpha(JXTheme.LIGHT.onSurface, JXTheme.HOVER)))), log);
        press(root, UP, select);
        assertEquals(Integer.valueOf(2), select.hot);
        press(root, HOME, select);
        assertEquals(Integer.valueOf(0), select.hot);
        press(root, UP, select);
        assertEquals(Integer.valueOf(0), select.hot, "a list stops at its ends");
        press(root, END, select);
        assertEquals(Integer.valueOf(3), select.hot);
        press(root, DOWN, select);
        assertEquals(Integer.valueOf(3), select.hot);
        assertTrue(changes.isEmpty());
        press(root, ENTER, select);
        assertEquals(Arrays.<Object>asList("d"), changes);
        assertEquals(Arrays.<Object>asList(false), showings);
        assertNull(select.hot);
    }

    @Test
    void aLongListScrollsToKeepTheHighlightedOptionInViewAndHoverDoesNotScrollIt() {
        String[] options = new String[30];
        for (int i = 0; i < options.length; i++) {
            options[i] = "o" + i;
        }
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), select("o0", options)));
        JXNativeNode select = host(root);
        assertEquals(0, JXOverlays.popup(select).first);
        press(root, END, select);
        JXOverlays.Popup popup = JXOverlays.popup(select);
        assertEquals(30 - JXOverlays.SELECT_ROWS, popup.first, "the last option is the last row shown");
        assertEquals(29, popup.cells[popup.cells.length - 1][5]);
        press(root, UP, select);
        assertEquals(Integer.valueOf(28), select.hot);
        assertEquals(30 - JXOverlays.SELECT_ROWS, JXOverlays.popup(select).first, "still in view: the list does not move");
        press(root, PAGE_DOWN, select);
        assertEquals(Integer.valueOf(29), select.hot);
        press(root, HOME, select);
        assertEquals(0, JXOverlays.popup(select).first);
        int[] cell = JXOverlays.popup(select).cells[4];
        root.routeHover(cell[0] + 2, cell[1] + 2);
        assertEquals(0, JXOverlays.popup(select).first);
        assertEquals(Integer.valueOf(4), select.hot);
    }

    @Test
    void escapeStillClosesAPopupWithAHighlight() {
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), select("b", "a", "b", "c")));
        JXNativeNode select = host(root);
        press(root, DOWN, select);
        press(root, ESCAPE, select);
        assertEquals(Arrays.<Object>asList(false), showings);
        assertTrue(changes.isEmpty());
        assertNull(select.hot);
    }

    // ---- date, time and color pickers ----------------------------------------------------------------

    private JXElement date(String value) {
        return e("datepicker", p().set("value", value).set("overlay", true).set("showing", true).set("onChange", onChange)
                .set("onShowing", onShowing));
    }

    @Test
    void theKeysWalkTheDaysOfAnOpenDatePickerAndTheCalendarFollowsTheMonth() {
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), date("2026-02-14")));
        JXNativeNode picker = host(root);
        press(root, RIGHT, picker);
        assertEquals(LocalDate.of(2026, 2, 15), picker.hot);
        press(root, DOWN, picker);
        assertEquals(LocalDate.of(2026, 2, 22), picker.hot, "a week down");
        press(root, UP, picker);
        press(root, UP, picker);
        assertEquals(LocalDate.of(2026, 2, 8), picker.hot);
        press(root, END, picker);
        assertEquals(LocalDate.of(2026, 2, 28), picker.hot);
        press(root, RIGHT, picker);
        assertEquals(LocalDate.of(2026, 3, 1), picker.hot);
        assertEquals(3, picker.viewMonth, "the calendar shows March now");
        press(root, LEFT, picker);
        assertEquals(2, picker.viewMonth);
        press(root, HOME, picker);
        assertEquals(LocalDate.of(2026, 2, 1), picker.hot);
        press(root, PAGE_DOWN, picker);
        assertEquals(LocalDate.of(2026, 3, 1), picker.hot);
        JXNativeNode calendar = JXOverlays.popup(picker).calendar;
        assertEquals(Integer.valueOf(1), calendar.getProperty("hoverDay"));
        assertEquals(Integer.valueOf(JXTheme.FOCUS), calendar.getProperty("hoverLayer"));
        assertTrue(changes.isEmpty());
        press(root, ENTER, picker);
        assertEquals(Arrays.<Object>asList("2026-03-01"), changes);
        assertEquals(Arrays.<Object>asList(false), showings);
    }

    @Test
    void thePointerHighlightsTheDayUnderItAndThePickersOwnKeysWorkWhileItIsClosed() {
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), date("2026-02-14")));
        JXNativeNode picker = host(root);
        JXNativeNode calendar = JXOverlays.popup(picker).calendar;
        int x = calendar.getX() + JXCalendar.PAD + 3 * JXCalendar.CELL_W + 4;
        int y = calendar.getY() + JXCalendar.PAD + JXCalendar.HEADER + JXCalendar.WEEKDAYS + 2 * JXCalendar.CELL_H + 4;
        int[] hit = JXCalendar.hit(calendar, x, y);
        assertTrue(root.routeHover(x, y));
        assertEquals(LocalDate.of(hit[1], hit[2], hit[3]), picker.hot);
        assertEquals(Integer.valueOf(JXTheme.HOVER), JXOverlays.popup(picker).calendar.getProperty("hoverLayer"));
        assertTrue(root.routeHover(calendar.getX() + 2, calendar.getY() + 2), "off the grid: no day");
        assertNull(picker.hot);
        JXNativeNode closed = mount(e("column", p().set("padding", 8.0),
                e("datepicker", p().set("value", "2026-02-14").set("overlay", true).set("showing", false).set("onChange", onChange))));
        press(closed, DOWN, host(closed));
        assertEquals(Arrays.<Object>asList("2026-02-13"), changes, "closed, the picker keeps its own Up and Down");
    }

    @Test
    void theKeysMoveOverTheCellsOfATimePickerAndEnterPicksOne() {
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), e("timepicker", p().set("value", "10:00").set("showing", true)
                .set("hour24", true).set("onChange", onChange).set("onShowing", onShowing))));
        JXNativeNode picker = host(root);
        JXOverlays.Popup popup = JXOverlays.popup(picker);
        press(root, RIGHT, picker);
        int at = ((Integer) picker.hot).intValue();
        assertEquals(11, popup.cells[at][5], "the first key starts at the hour the value has (10) and goes one on");
        press(root, DOWN, picker);
        int below = ((Integer) picker.hot).intValue();
        assertEquals(17, popup.cells[below][5], "Down is the cell under it: six hours to a row");
        assertEquals(popup.cells[at][0], popup.cells[below][0], "in the same column");
        press(root, UP, picker);
        assertEquals(Integer.valueOf(at), picker.hot);
        press(root, DOWN, picker);
        press(root, ENTER, picker);
        assertEquals(Arrays.<Object>asList("17:00"), changes, "Enter picks the hour of the cell");
        assertTrue(showings.isEmpty(), "a time picker stays open after a pick, as it does for a press");
        press(root, END, picker);
        int last = ((Integer) picker.hot).intValue();
        assertEquals(popup.cells.length - 1, last);
        press(root, HOME, picker);
        assertEquals(Integer.valueOf(0), picker.hot);
        press(root, ENTER, picker);
        assertEquals("00:00", changes.get(1));
    }

    @Test
    void theKeysMoveOverTheSwatchesOfAColorPickerAndEnterPicksOne() {
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), e("colorpicker", p().set("value", "#E91E63").set("showing", true)
                .set("onChange", onChange).set("onShowing", onShowing))));
        JXNativeNode picker = host(root);
        press(root, RIGHT, picker);
        assertEquals(Integer.valueOf(10), picker.hot, "from the swatch that is the value (9) to the next");
        press(root, DOWN, picker);
        assertEquals(Integer.valueOf(18), picker.hot, "eight swatches to a row");
        press(root, UP, picker);
        press(root, UP, picker);
        assertEquals(Integer.valueOf(2), picker.hot);
        press(root, ENTER, picker);
        assertEquals(Arrays.<Object>asList(JXOverlays.hex(JXOverlays.PALETTE[2])), changes);
        int[] cell = JXOverlays.popup(picker).cells[5];
        assertTrue(root.routeHover(cell[0] + 2, cell[1] + 2));
        assertEquals(Integer.valueOf(5), picker.hot);
        assertFalse(picker.hotKey);
    }
}
