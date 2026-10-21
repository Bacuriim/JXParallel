package com.jxparallel.ui.native2d;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 * The overlays (popups of the select, date, time and color pickers; menu button, context menu and menu bar; dialog,
 * drawer and snack bar): where they are placed, that they are drawn over everything and hit first, and what a press,
 * Escape and Enter do. Nothing here depends on the system font: sizes are compared with each other or with zero, text
 * is never asserted, painter calls are matched by kind, color and order.
 */
class JXOverlaysTest {
    private static final int ESCAPE = 256;
    private static final int ENTER = 257;
    private static final int BACKSPACE = 259;
    private static final int UP = 265;
    private static final int DOWN = 264;
    private static final int PAGE_DOWN = 267;
    private static final int W = 400;
    private static final int H = 300;

    private final List<Object> changes = new ArrayList<Object>();
    private final List<Object> showings = new ArrayList<Object>();
    private final List<String> calls = new ArrayList<String>();
    private final Consumer<Object> onChange = changes::add;
    private final Consumer<Object> onShowing = showings::add;

    private java.util.function.Supplier<LocalDate> today;
    private java.util.function.Supplier<Locale> locale;
    private java.util.function.LongSupplier clock;

    @BeforeEach
    void fixTheCalendarAndTheClock() {
        today = JXOverlays.today;
        locale = JXOverlays.locale;
        clock = JXOverlays.clock;
        JXOverlays.today = () -> LocalDate.of(2026, 2, 3);
        JXOverlays.locale = () -> Locale.ENGLISH;
    }

    @AfterEach
    void restore() {
        JXOverlays.today = today;
        JXOverlays.locale = locale;
        JXOverlays.clock = clock;
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

    private static void remount(JXNativeNode root, JXElement element) {
        assertTrue(root.reconcile(element));
        root.layoutForBackend(W, H);
    }

    private static String record(JXNativeNode root) {
        JXPaintRecordingTest.RecordingPainter painter = new JXPaintRecordingTest.RecordingPainter();
        new JXPaint(painter).paint(root);
        return painter.log.toString();
    }

    private static List<String> lines(String log) {
        return Arrays.asList(log.split("\n"));
    }

    private static int lastIndexOf(List<String> log, String prefix) {
        for (int i = log.size() - 1; i >= 0; i--) {
            if (log.get(i).startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }

    private static int firstIndexOf(List<String> log, String prefix) {
        for (int i = 0; i < log.size(); i++) {
            if (log.get(i).startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }

    private static JXNativeNode click(JXNativeNode root, int x, int y) {
        return root.routePointer(new JXPointerEvent(x, y, 0));
    }

    private static JXNativeNode secondaryClick(JXNativeNode root, int x, int y) {
        return root.routePointer(new JXPointerEvent(x, y, JXInputEvent.BUTTON_SECONDARY));
    }

    private static void press(JXNativeNode root, int code, char typed, JXNativeNode focused) {
        root.routeKey(new JXKeyEvent(code, typed), focused);
    }

    private static int[] center(int[] cell) {
        return new int[] {cell[0] + cell[2] / 2, cell[1] + cell[3] / 2};
    }

    private static int[] center(JXNativeNode node) {
        return new int[] {node.getX() + node.getWidth() / 2, node.getY() + node.getHeight() / 2};
    }

    private JXElement select(boolean showing, String... options) {
        return e("select", p().set("value", "b").set("overlay", true).set("options", options).set("showing", showing).set("onChange", onChange)
                .set("onShowing", onShowing));
    }

    private JXElement button(String label) {
        return e("button", p().set("label", label).set("onAction", (Runnable) () -> calls.add(label)));
    }

    // ---- the select's list ---------------------------------------------------------------------

    @Test
    void anOpenSelectListsItsOptionsBelowItInOrderAndAClosedOneDoesNot() {
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), select(true, "a", "b", "c")));
        JXNativeNode select = root.getChildren().get(0);
        JXOverlays.Popup popup = JXOverlays.popup(select);
        assertNotNull(popup);
        assertEquals(3, popup.cells.length);
        assertEquals(select.getY() + select.getHeight(), popup.y, "right below the select");
        assertEquals(select.getX(), popup.x);
        assertTrue(popup.w >= select.getWidth() && popup.h > 0);
        for (int i = 0; i < 3; i++) {
            assertEquals(JXOverlays.ROW, popup.cells[i][3]);
            assertTrue(i == 0 || popup.cells[i][1] > popup.cells[i - 1][1], "rows from top to bottom");
        }
        assertArrayEquals(new String[] {"a", "b", "c"}, popup.labels);
        remount(root, e("column", p().set("padding", 8.0), select(false, "a", "b", "c")));
        assertNull(JXOverlays.popup(select), "closed: no popup");
        int closed = lines(record(root)).size();
        remount(root, e("column", p().set("padding", 8.0), select(true, "a", "b", "c")));
        assertTrue(lines(record(root)).size() > closed + 3, "the popup adds its box, its rows and their texts");
    }

    @Test
    void theOpenListIsDrawnAfterWhatLiesBelowIt() {
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), select(true, "a", "b", "c"), button("Below")));
        JXNativeNode below = root.getChildren().get(1);
        JXOverlays.Popup popup = JXOverlays.popup(root.getChildren().get(0));
        assertTrue(popup.contains(below.getX() + 2, below.getY() + 2), "the list covers the button under the select");
        List<String> log = lines(record(root));
        int buttonLabel = lastIndexOf(log, "text \"Below\"");
        int listBorder = lastIndexOf(log, "fillRoundRect " + String.format(Locale.ROOT, "%.2f", (float) popup.x));
        assertTrue(buttonLabel >= 0 && listBorder > buttonLabel, "the list is painted after the button it covers");
        // the covered button does not get the press
        assertSame(root.getChildren().get(0), root.hitTest(below.getX() + 2, below.getY() + 2));
    }

    @Test
    void aPressOnAnOptionReportsItAndAsksToClose() {
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), select(true, "a", "b", "c")));
        JXOverlays.Popup popup = JXOverlays.popup(root.getChildren().get(0));
        int[] c = center(popup.cells[2]);
        assertSame(root.getChildren().get(0), click(root, c[0], c[1]));
        assertEquals(Arrays.<Object>asList("c"), changes);
        assertEquals(Arrays.<Object>asList(false), showings);
    }

    @Test
    void aPressOnAClosedSelectAsksToOpenAndOnAnOpenOneToClose() {
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), select(false, "a", "b", "c")));
        JXNativeNode select = root.getChildren().get(0);
        click(root, select.getX() + 3, select.getY() + 3);
        assertEquals(Arrays.<Object>asList(true), showings);
        assertNull(JXOverlays.popup(select), "controlled: it opens when the application renders again with showing");
        remount(root, e("column", p().set("padding", 8.0), select(true, "a", "b", "c")));
        click(root, select.getX() + 3, select.getY() + 3);
        assertEquals(Arrays.<Object>asList(true, false), showings);
        assertTrue(changes.isEmpty());
    }

    @Test
    void aSelectWithoutShowingKeepsItsOwnState() {
        JXElement bare = e("select", p().set("value", "b").set("overlay", true).set("options", new String[] {"a", "b"}));
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), bare));
        JXNativeNode select = root.getChildren().get(0);
        assertNull(JXOverlays.popup(select));
        click(root, select.getX() + 3, select.getY() + 3);
        root.layoutForBackend(W, H);
        assertNotNull(JXOverlays.popup(select), "opened by the press");
        click(root, select.getX() + 3, select.getY() + 3);
        root.layoutForBackend(W, H);
        assertNull(JXOverlays.popup(select), "closed by the next one");
    }

    @Test
    void aPressOutsideClosesTheTopmostOverlayAndGoesNoFurther() {
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), select(true, "a", "b", "c"), button("Below"),
                e("pane", p().set("prefHeight", 150.0))));
        JXNativeNode pane = root.getChildren().get(2);
        assertNull(click(root, pane.getX() + 5, pane.getY() + 100));
        assertEquals(Arrays.<Object>asList(false), showings);
        // a button that is not under the popup takes nothing while the press closes the list
        JXNativeNode other = mount(e("column", p().set("padding", 8.0), select(true, "a"), e("pane", p().set("prefHeight", 60.0)),
                button("Far")));
        JXNativeNode far = other.getChildren().get(2);
        assertNull(click(other, far.getX() + 2, far.getY() + 2));
        assertTrue(calls.isEmpty(), "the press that closes a popup does not click what is under it");
        // with nothing open, the same press clicks the button
        JXNativeNode closed = mount(e("column", p().set("padding", 8.0), select(false, "a"), e("pane", p().set("prefHeight", 60.0)),
                button("Far")));
        JXNativeNode hit = click(closed, closed.getChildren().get(2).getX() + 2, closed.getChildren().get(2).getY() + 2);
        assertEquals("button", hit.getType());
        assertEquals(Arrays.asList("Far"), calls);
    }

    @Test
    void escapeClosesTheTopmostOverlayAndOtherKeysGoToTheFocusedNode() {
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), select(true, "a", "b", "c")));
        JXNativeNode select = root.getChildren().get(0);
        press(root, ESCAPE, '\0', select);
        assertEquals(Arrays.<Object>asList(false), showings);
        press(root, DOWN, '\0', select); // Down chooses the next option: the select's own key handling
        assertEquals(Arrays.<Object>asList("c"), changes);
        press(root, ENTER, '\0', select);
        assertEquals(Arrays.<Object>asList(false, false), showings, "Enter closes an open popup");
        JXNativeNode closed = mount(e("column", p().set("padding", 8.0), select(false, "a", "b", "c")));
        press(closed, ESCAPE, '\0', closed.getChildren().get(0));
        assertEquals(2, showings.size(), "Escape with nothing open does nothing");
    }

    @Test
    void aLongListShowsTenRowsAroundTheChosenOne() {
        String[] options = new String[30];
        for (int i = 0; i < options.length; i++) {
            options[i] = "option " + i;
        }
        JXElement list = e("select", p().set("value", "option 20").set("overlay", true).set("options", options).set("showing", true).set("onChange", onChange));
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), list));
        JXOverlays.Popup popup = JXOverlays.popup(root.getChildren().get(0));
        assertEquals(JXOverlays.SELECT_ROWS, popup.cells.length);
        assertEquals("option 15", popup.labels[0]);
        assertEquals(30, ((String[]) root.getChildren().get(0).getProperty("options")).length);
        int[] c = center(popup.cells[5]);
        click(root, c[0], c[1]);
        assertEquals(Arrays.<Object>asList("option 20"), changes);
    }

    @Test
    void aPopupAtTheBottomOfTheWindowOpensAboveItsHost() {
        JXElement low = e("select", p().set("value", "b").set("overlay", true).set("options", new String[] {"a", "b", "c", "d"}).set("showing", true)
                .set("prefWidth", 120.0));
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), e("pane", p().set("prefHeight", 255.0)), low));
        JXNativeNode select = root.getChildren().get(1);
        JXOverlays.Popup popup = JXOverlays.popup(select);
        assertEquals(select.getY() - popup.h, popup.y);
        assertTrue(popup.y >= 0 && popup.y + popup.h <= H);
    }

    // ---- date picker -----------------------------------------------------------------------------

    private JXElement date(String value, boolean showing) {
        return e("datepicker", p().set("value", value).set("overlay", true).set("showing", showing).set("onChange", onChange).set("onShowing", onShowing));
    }

    @Test
    void anOpenDatePickerShowsTheMonthOfItsValueAndAPressOnADayReportsIt() {
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), date("2026-02-14", true)));
        JXNativeNode host = root.getChildren().get(0);
        JXOverlays.Popup popup = JXOverlays.popup(host);
        assertEquals(JXCalendar.WIDTH, popup.w);
        assertEquals(host.getY() + host.getHeight(), popup.y);
        assertEquals(Integer.valueOf(2), popup.calendar.getProperty("month"));
        assertEquals(Integer.valueOf(14), popup.calendar.getProperty("selectedDay"));
        assertEquals(Integer.valueOf(3), popup.calendar.getProperty("todayDay"));
        assertEquals("February 2026", popup.calendar.getProperty("title"));
        // the 14th is a Saturday: find its cell by the calendar's own hit test and press it
        int[] day = null;
        for (int dy = 0; dy < JXCalendar.HEIGHT && day == null; dy++) {
            for (int dx = 0; dx < JXCalendar.WIDTH; dx++) {
                int[] hit = JXCalendar.hit(popup.calendar, popup.x + dx, popup.y + dy);
                if (hit[0] == JXCalendar.DAY && hit[2] == 2 && hit[3] == 20) {
                    day = new int[] {popup.x + dx, popup.y + dy};
                    break;
                }
            }
        }
        assertNotNull(day);
        click(root, day[0], day[1]);
        assertEquals(Arrays.<Object>asList("2026-02-20"), changes);
        assertEquals(Arrays.<Object>asList(false), showings);
    }

    @Test
    void theArrowsOfTheCalendarMoveTheMonthWithoutReportingAndAnEmptyValueStartsToday() {
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), date("", true)));
        JXNativeNode host = root.getChildren().get(0);
        JXOverlays.Popup popup = JXOverlays.popup(host);
        assertEquals("February 2026", popup.calendar.getProperty("title"));
        assertNull(popup.calendar.getProperty("selectedDay"));
        int next = popup.x + JXCalendar.WIDTH - JXCalendar.PAD - 14;
        int y = popup.y + JXCalendar.PAD + JXCalendar.HEADER / 2;
        click(root, next, y);
        assertEquals("March 2026", JXOverlays.popup(host).calendar.getProperty("title"));
        int previous = popup.x + JXCalendar.PAD + 14;
        click(root, previous, y);
        click(root, previous, y);
        click(root, previous, y);
        assertEquals("December 2025", JXOverlays.popup(host).calendar.getProperty("title"), "back across the year");
        assertTrue(changes.isEmpty() && showings.isEmpty());
        root.layoutForBackend(W, H);
        assertEquals("December 2025", JXOverlays.popup(host).calendar.getProperty("title"), "the view survives a layout");
    }

    @Test
    void aSelectOrDatePickerWithoutOverlayIsTheBridgesAndDrawsNoPopupOfItsOwn() {
        // the JavaFX bridge sends these elements with showing and draws their popups as elements of its own
        JXElement bridged = e("column", p().set("padding", 8.0),
                e("select", p().set("value", "b").set("options", new String[] {"a", "b", "c"}).set("showing", true).set("onShowing", onShowing)),
                e("datepicker", p().set("value", "2026-02-14").set("showing", true).set("onShowing", onShowing)));
        JXNativeNode root = mount(bridged);
        assertNull(JXOverlays.popup(root.getChildren().get(0)));
        assertNull(JXOverlays.popup(root.getChildren().get(1)));
        assertNull(root.getChildren().get(0).getPopupBounds());
        assertEquals(0, root.getChildren().get(0).getPopupCells().length);
        JXNativeNode select = root.getChildren().get(0);
        assertSame(select, click(root, select.getX() + 3, select.getY() + 3));
        press(root, ESCAPE, '\0', select);
        assertTrue(showings.isEmpty(), "nothing asks to open or close: the bridge does that itself");
        assertSame(select, root.hitTest(select.getX() + 3, select.getY() + 3));
        assertFalse(record(root).contains("text \"a\""), "no list of options is drawn");
    }

    @Test
    void aPressOutsideTheCalendarClosesItAndTheCalendarIsDrawn() {
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), date("2026-02-14", true), e("pane", p().set("prefHeight", 200.0))));
        List<String> log = lines(record(root));
        assertTrue(firstIndexOf(log, "text \"February 2026\"") >= 0);
        assertNull(click(root, 395, 295));
        assertEquals(Arrays.<Object>asList(false), showings);
    }

    // ---- time picker -----------------------------------------------------------------------------

    private JXElement time(String value, boolean showing, boolean hour24) {
        return e("timepicker", p().set("value", value).set("showing", showing).set("hour24", hour24).set("onChange", onChange)
                .set("onShowing", onShowing));
    }

    private int[] timeCell(JXNativeNode host, int kind, int value) {
        for (int[] cell : JXOverlays.popup(host).cells) {
            if (cell[4] == kind && cell[5] == value) {
                return cell;
            }
        }
        throw new AssertionError("no cell " + kind + "/" + value);
    }

    @Test
    void aTimePickerOffersTwentyFourHoursAndTwelveMinutesInOrder() {
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), time("09:30", true, true)));
        JXNativeNode host = root.getChildren().get(0);
        JXOverlays.Popup popup = JXOverlays.popup(host);
        assertEquals(24 + 12, popup.cells.length);
        assertEquals(host.getY() + host.getHeight(), popup.y);
        assertTrue(timeCell(host, 0, 13)[1] > timeCell(host, 0, 5)[1], "13 h is on a lower row than 5 h");
        assertTrue(timeCell(host, 1, 5)[1] > timeCell(host, 0, 23)[1], "minutes come below the hours");
        assertEquals(timeCell(host, 0, 0)[0], timeCell(host, 0, 6)[0], "six hours to a row");
        List<String> log = lines(record(root));
        assertTrue(firstIndexOf(log, "text \"13\"") >= 0 && firstIndexOf(log, "text \"45\"") >= 0);
        assertTrue(lastIndexOf(log, "fillRoundRect " + String.format(Locale.ROOT, "%.2f", (float) timeCell(host, 0, 9)[0] + 2) + " "
                + String.format(Locale.ROOT, "%.2f", (float) timeCell(host, 0, 9)[1]) + " 28.00 26.00 4.00 "
                + Integer.toHexString(JXTheme.LIGHT.primary)) >= 0,
                "the cell of the current hour is filled with the primary color");
    }

    @Test
    void aPressOnAnHourOrAMinuteReportsTheNewTimeAndKeepsThePopupOpen() {
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), time("09:30", true, true)));
        JXNativeNode host = root.getChildren().get(0);
        int[] c = center(timeCell(host, 0, 14));
        click(root, c[0], c[1]);
        c = center(timeCell(host, 1, 45));
        click(root, c[0], c[1]);
        assertEquals(Arrays.<Object>asList("14:30", "09:45"), changes);
        assertTrue(showings.isEmpty(), "the popup stays open for the second choice");
        JXNativeNode empty = mount(e("column", p().set("padding", 8.0), time("", true, true)));
        c = center(timeCell(empty.getChildren().get(0), 0, 7));
        click(empty, c[0], c[1]);
        assertEquals("07:00", changes.get(2), "an empty time starts at midnight");
    }

    @Test
    void theTwelveHourFaceHasTwelveHoursAndAmPm() {
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), time("09:30", true, false)));
        JXNativeNode host = root.getChildren().get(0);
        assertEquals(12 + 12 + 2, JXOverlays.popup(host).cells.length);
        assertTrue(timeCell(host, 2, 0)[1] > timeCell(host, 1, 55)[1], "AM and PM are the last row");
        int[] c = center(timeCell(host, 2, 1)); // PM
        click(root, c[0], c[1]);
        c = center(timeCell(host, 0, 3)); // 3 o'clock, while the value is still AM
        click(root, c[0], c[1]);
        assertEquals(Arrays.<Object>asList("21:30", "03:30"), changes);
        JXNativeNode pm = mount(e("column", p().set("padding", 8.0), time("21:30", true, false)));
        c = center(timeCell(pm.getChildren().get(0), 0, 12)); // 12 o'clock after noon is 12:30
        click(pm, c[0], c[1]);
        assertEquals("12:30", changes.get(2));
        assertEquals("9:30 AM", JXOverlays.timeText(host));
        assertEquals("9:30 PM", JXOverlays.timeText(pm.getChildren().get(0)).replace("21", "9"));
    }

    @Test
    void theMinuteStepSetsTheMinutesOfferedAndAnInvalidOneFallsBackToFive() {
        JXElement quarter = e("timepicker", p().set("value", "10:00").set("showing", true).set("minuteStep", 15));
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), quarter));
        assertEquals(24 + 4, JXOverlays.popup(root.getChildren().get(0)).cells.length);
        JXElement odd = e("timepicker", p().set("value", "10:00").set("showing", true).set("minuteStep", 7));
        assertEquals(24 + 12, JXOverlays.popup(mount(e("column", p().set("padding", 8.0), odd)).getChildren().get(0)).cells.length);
        JXElement fine = e("timepicker", p().set("value", "10:00").set("showing", true).set("minuteStep", 1));
        assertEquals(24 + 60, JXOverlays.popup(mount(e("column", p().set("padding", 8.0), fine)).getChildren().get(0)).cells.length);
    }

    @Test
    void theKeysMoveATimePickerByItsStepAndByTheHourAndWrapAroundTheDay() {
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), time("23:55", false, true)));
        JXNativeNode host = root.getChildren().get(0);
        press(root, UP, '\0', host);
        press(root, PAGE_DOWN, '\0', host);
        press(root, DOWN, '\0', host);
        assertEquals(Arrays.<Object>asList("00:00", "22:55", "23:50"), changes);
        JXNativeNode empty = mount(e("column", p().set("padding", 8.0), time("", false, true)));
        press(empty, UP, '\0', empty.getChildren().get(0));
        assertEquals("00:05", changes.get(3));
    }

    @Test
    void timeTextIsParsedAndFormattedStrictly() {
        assertEquals(9 * 60 + 5, JXOverlays.parseTime("09:05"));
        assertEquals(-1, JXOverlays.parseTime("9:05x"));
        assertEquals(-1, JXOverlays.parseTime("24:00"));
        assertEquals(-1, JXOverlays.parseTime("12:60"));
        assertEquals(-1, JXOverlays.parseTime(""));
        assertEquals(-1, JXOverlays.parseTime(null));
        assertEquals("07:08", JXOverlays.formatTime(7, 8));
    }

    // ---- color picker ----------------------------------------------------------------------------

    private JXElement color(String value, boolean showing) {
        return e("colorpicker", p().set("value", value).set("showing", showing).set("onChange", onChange).set("onShowing", onShowing));
    }

    @Test
    void aColorPickerOffersFortySwatchesAndAHexFieldAndAPressOnASwatchReportsItsColor() {
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), color("#E91E63", true)));
        JXNativeNode host = root.getChildren().get(0);
        JXOverlays.Popup popup = JXOverlays.popup(host);
        assertEquals(40, popup.cells.length);
        assertEquals(host.getY() + host.getHeight(), popup.y);
        assertTrue(popup.field[1] > popup.cells[39][1] + popup.cells[39][3] - 1, "the hex field is below the swatches");
        List<String> log = lines(record(root));
        for (int[] cell : popup.cells) {
            assertTrue(firstIndexOf(log, "fillRoundRect " + String.format(Locale.ROOT, "%.2f %.2f 22.00 22.00 2.00 %s", (float) cell[0],
                    (float) cell[1], Integer.toHexString(cell[5]))) >= 0, "swatch " + Integer.toHexString(cell[5]));
        }
        assertTrue(firstIndexOf(log, "strokeRoundRect") >= 0, "a ring around the swatch of the current color");
        int[] c = center(popup.cells[3]);
        click(root, c[0], c[1]);
        assertEquals(Arrays.<Object>asList("#BDBDBD"), changes);
        assertTrue(showings.isEmpty(), "the popup stays open: the hex field may follow");
    }

    @Test
    void typingHexDigitsInAnOpenColorPickerReportsEachCompleteColor() {
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), color("", true)));
        JXNativeNode host = root.getChildren().get(0);
        for (char ch : "#12ab".toCharArray()) {
            press(root, 0, ch, host);
        }
        press(root, 0, 'x', host); // not a hex digit
        assertTrue(changes.isEmpty() && JXOverlays.hexDraft(host).equals("#12AB"), JXOverlays.hexDraft(host));
        press(root, 0, 'c', host);
        press(root, 0, 'd', host);
        assertEquals(Arrays.<Object>asList("#12ABCD"), changes);
        press(root, 0, 'e', host); // already seven characters
        assertEquals(1, changes.size());
        press(root, BACKSPACE, '\0', host);
        press(root, 0, 'F', host);
        assertEquals(Arrays.<Object>asList("#12ABCD", "#12ABCF"), changes);
        JXNativeNode bare = mount(e("column", p().set("padding", 8.0), color("", true)));
        for (char ch : "12ab34".toCharArray()) {
            press(bare, 0, ch, bare.getChildren().get(0));
        }
        assertEquals("#12AB34", changes.get(2), "the # is added to the first digit");
    }

    @Test
    void aClosedColorPickerIgnoresTypingAndTheDraftFollowsTheValue() {
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), color("#112233", false)));
        JXNativeNode host = root.getChildren().get(0);
        press(root, 0, 'a', host);
        assertTrue(changes.isEmpty());
        remount(root, e("column", p().set("padding", 8.0), color("#112233", true)));
        assertEquals("#112233", JXOverlays.hexDraft(host));
        press(root, BACKSPACE, '\0', host);
        press(root, BACKSPACE, '\0', host);
        assertEquals("#1122", JXOverlays.hexDraft(host));
        remount(root, e("column", p().set("padding", 8.0), color("#445566", true)));
        assertEquals("#445566", JXOverlays.hexDraft(host), "a new value replaces the text being typed");
        assertEquals("#00FF7F", JXOverlays.normalizeHex(" #00ff7f "));
        assertEquals("#AABBCC", JXOverlays.normalizeHex("#abc"));
        assertNull(JXOverlays.normalizeHex("abc"));
        assertNull(JXOverlays.normalizeHex("#abcd"));
        assertEquals(0xFF112233, JXOverlays.colorOf("#123"));
        assertEquals("#00BFFF", JXOverlays.hex(0xFF00BFFF));
    }

    // ---- menus -----------------------------------------------------------------------------------

    private JXElement item(String label, String accelerator) {
        JXProps.Builder b = p().set("label", label).set("onAction", (Runnable) () -> calls.add(label));
        if (accelerator != null) {
            b.set("accelerator", accelerator);
        }
        return e("menuitem", b);
    }

    private JXElement disabledItem(String label) {
        return e("menuitem", p().set("label", label).set("disabled", true).set("onAction", (Runnable) () -> calls.add(label)));
    }

    private JXElement separator() {
        return e("menuitem", p().set("separator", true).set("label", ""));
    }

    private JXElement menuButton(Boolean showing) {
        JXProps.Builder b = p().set("label", "Actions").set("onShowing", onShowing);
        if (showing != null) {
            b.set("showing", showing);
        }
        return e("menubutton", b, item("Open", "Ctrl+O"), disabledItem("Locked"), separator(),
                e("menu", p().set("label", "Export"), item("PDF", null), item("CSV", null)), item("Quit", null));
    }

    @Test
    void aMenuButtonOpensItsItemsBelowItInOrderWithTheirKindsAndClosesOnAChoice() {
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), menuButton(null), button("Under")));
        JXNativeNode host = root.getChildren().get(0);
        assertNull(JXOverlays.popup(host));
        assertEquals(host, click(root, host.getX() + 3, host.getY() + 3));
        root.layoutForBackend(W, H);
        JXOverlays.Popup popup = JXOverlays.popup(host);
        assertNotNull(popup);
        List<JXNativeNode> rows = host.getChildren();
        assertEquals(5, rows.size());
        assertEquals(host.getY() + host.getHeight(), popup.y);
        for (int i = 0; i < rows.size(); i++) {
            assertTrue(rows.get(i).getWidth() > 0 && rows.get(i).getHeight() > 0);
            assertTrue(i == 0 || rows.get(i).getY() >= rows.get(i - 1).getY() + rows.get(i - 1).getHeight(), "rows in order");
        }
        assertEquals(JXOverlays.MENU_SEPARATOR, rows.get(2).getHeight());
        assertEquals(JXOverlays.MENU_ROW, rows.get(0).getHeight());
        // the panel covers the button under the host, and the open menu takes the press first
        JXNativeNode under = root.getChildren().get(1);
        assertTrue(popup.panels.get(0).contains(under.getX() + 2, under.getY() + 2));
        int[] open = center(rows.get(0));
        assertSame(rows.get(0), click(root, open[0], open[1]));
        assertEquals(Arrays.asList("Open"), calls);
        root.layoutForBackend(W, H);
        assertNull(JXOverlays.popup(host), "a choice closes the menu");
        assertTrue(showings.isEmpty() || showings.get(showings.size() - 1).equals(false));
    }

    @Test
    void aDisabledItemAndASeparatorDoNothingAndKeepTheMenuOpen() {
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), menuButton(null)));
        JXNativeNode host = root.getChildren().get(0);
        click(root, host.getX() + 3, host.getY() + 3);
        root.layoutForBackend(W, H);
        int[] locked = center(host.getChildren().get(1));
        click(root, locked[0], locked[1]);
        int[] line = center(host.getChildren().get(2));
        click(root, line[0], line[1]);
        assertTrue(calls.isEmpty());
        root.layoutForBackend(W, H);
        assertNotNull(JXOverlays.popup(host));
    }

    @Test
    void aSubmenuOpensToTheRightOfItsRowAndAChoiceInItClosesEverything() {
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), menuButton(null)));
        JXNativeNode host = root.getChildren().get(0);
        click(root, host.getX() + 3, host.getY() + 3);
        root.layoutForBackend(W, H);
        JXNativeNode export = host.getChildren().get(3);
        int[] row = center(export);
        assertSame(export, click(root, row[0], row[1]));
        root.layoutForBackend(W, H);
        JXOverlays.Popup popup = JXOverlays.popup(host);
        assertEquals(2, popup.panels.size(), "the submenu is a second panel");
        JXOverlays.Panel sub = popup.panels.get(1);
        assertSame(export, sub.container);
        assertTrue(sub.x >= popup.panels.get(0).x + popup.panels.get(0).w - 4, "to the right of the first panel");
        JXNativeNode pdf = export.getChildren().get(0);
        assertTrue(pdf.getWidth() > 0 && pdf.getX() >= sub.x);
        int[] c = center(pdf);
        assertSame(pdf, click(root, c[0], c[1]));
        assertEquals(Arrays.asList("PDF"), calls);
        root.layoutForBackend(W, H);
        assertNull(JXOverlays.popup(host));
        assertEquals(-1, host.openChild, "the submenu is closed too");
    }

    @Test
    void aMenuClosesOnAPressOutsideAndOnEscapeAndDrawsOverTheRest() {
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), menuButton(null), button("Under")));
        JXNativeNode host = root.getChildren().get(0);
        click(root, host.getX() + 3, host.getY() + 3);
        root.layoutForBackend(W, H);
        List<String> log = lines(record(root));
        assertTrue(lastIndexOf(log, "triangle") > lastIndexOf(log, "text \"Under\""), "the submenu arrow is over the button under it");
        assertTrue(firstIndexOf(log, "text \"Ctrl+O\"") > lastIndexOf(log, "text \"Under\""), "the accelerator text");
        assertNull(click(root, 395, 295));
        root.layoutForBackend(W, H);
        assertNull(JXOverlays.popup(host));
        click(root, host.getX() + 3, host.getY() + 3);
        root.layoutForBackend(W, H);
        assertNotNull(JXOverlays.popup(host));
        press(root, ESCAPE, '\0', host);
        root.layoutForBackend(W, H);
        assertNull(JXOverlays.popup(host));
        assertTrue(calls.isEmpty());
    }

    @Test
    void aMenuButtonWithShowingOnlyAsksAndTheApplicationOpensIt() {
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), menuButton(Boolean.FALSE)));
        JXNativeNode host = root.getChildren().get(0);
        click(root, host.getX() + 3, host.getY() + 3);
        assertEquals(Arrays.<Object>asList(true), showings);
        assertNull(JXOverlays.popup(host));
        remount(root, e("column", p().set("padding", 8.0), menuButton(Boolean.TRUE)));
        assertNotNull(JXOverlays.popup(host));
        int[] c = center(host.getChildren().get(4));
        click(root, c[0], c[1]);
        assertEquals(Arrays.asList("Quit"), calls);
        assertNotNull(JXOverlays.popup(host), "the application closes it, by showing");
        assertEquals(Arrays.<Object>asList(true, false), showings);
    }

    @Test
    void aContextMenuTakesNoRoomAndOpensAtTheSecondaryPressOverItsParent() {
        JXElement area = e("stack", p().set("prefWidth", 200.0).set("prefHeight", 100.0),
                e("contextmenu", p().set("onShowing", onShowing), item("Copy", null), item("Paste", null)));
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), area, button("Other")));
        JXNativeNode stack = root.getChildren().get(0);
        JXNativeNode menu = stack.getChildren().get(0);
        assertEquals(0, menu.getWidth());
        assertEquals(0, menu.getPrefWidth(), 0);
        assertEquals(0, menu.getPrefHeight(), 0);
        click(root, stack.getX() + 20, stack.getY() + 20);
        root.layoutForBackend(W, H);
        assertNull(JXOverlays.popup(menu), "a primary press does not open it");
        assertNull(secondaryClick(root, root.getChildren().get(1).getX() + 3, root.getChildren().get(1).getY() + 3));
        root.layoutForBackend(W, H);
        assertNull(JXOverlays.popup(menu), "outside its parent");
        secondaryClick(root, stack.getX() + 30, stack.getY() + 40);
        root.layoutForBackend(W, H);
        JXOverlays.Popup popup = JXOverlays.popup(menu);
        assertNotNull(popup);
        assertEquals(stack.getX() + 30, popup.panels.get(0).x, "at the pointer");
        assertEquals(stack.getY() + 40, popup.panels.get(0).y);
        assertEquals(Arrays.<Object>asList(true), showings);
        int[] paste = center(menu.getChildren().get(1));
        assertSame(menu.getChildren().get(1), click(root, paste[0], paste[1]));
        assertEquals(Arrays.asList("Paste"), calls);
        root.layoutForBackend(W, H);
        assertNull(JXOverlays.popup(menu));
        secondaryClick(root, stack.getX() + 30, stack.getY() + 40);
        root.layoutForBackend(W, H);
        press(root, ESCAPE, '\0', null);
        root.layoutForBackend(W, H);
        assertNull(JXOverlays.popup(menu), "Escape closes it");
    }

    @Test
    void theDeepestParentUnderThePointerOwnsTheContextMenu() {
        JXElement inner = e("stack", p().set("prefWidth", 60.0).set("prefHeight", 60.0).set("maxWidth", 60.0).set("maxHeight", 60.0),
                e("contextmenu", p(), item("Inner", null)));
        JXElement outer = e("stack", p().set("prefWidth", 300.0).set("prefHeight", 200.0).set("alignment", "TOP_LEFT"), inner,
                e("contextmenu", p(), item("Outer", null)));
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), outer));
        JXNativeNode outerNode = root.getChildren().get(0);
        JXNativeNode innerNode = outerNode.getChildren().get(0);
        secondaryClick(root, innerNode.getX() + 5, innerNode.getY() + 5);
        root.layoutForBackend(W, H);
        assertNotNull(JXOverlays.popup(innerNode.getChildren().get(0)));
        assertNull(JXOverlays.popup(outerNode.getChildren().get(1)));
        press(root, ESCAPE, '\0', null);
        secondaryClick(root, outerNode.getX() + 200, outerNode.getY() + 150);
        root.layoutForBackend(W, H);
        assertNotNull(JXOverlays.popup(outerNode.getChildren().get(1)));
    }

    private JXElement menuBar() {
        return e("menubar", p(),
                e("menu", p().set("label", "File"), item("New", "Ctrl+N"), separator(), item("Exit", null)),
                e("menu", p().set("label", "Edit"), item("Undo", null), e("menu", p().set("label", "Find"), item("Next", null))));
    }

    @Test
    void aMenuBarLaysItsTitlesInARowAndOpensTheMenuOfThePressedOne() {
        JXNativeNode root = mount(e("column", p(), menuBar(), button("Under")));
        JXNativeNode bar = root.getChildren().get(0);
        JXNativeNode file = bar.getChildren().get(0);
        JXNativeNode edit = bar.getChildren().get(1);
        assertTrue(file.getWidth() > 0 && edit.getX() >= file.getX() + file.getWidth(), "titles side by side");
        assertEquals(JXOverlays.BAR_H, bar.getHeight());
        assertNull(JXOverlays.popup(bar));
        assertSame(bar, click(root, edit.getX() + 3, edit.getY() + 3));
        root.layoutForBackend(W, H);
        JXOverlays.Popup popup = JXOverlays.popup(bar);
        assertNotNull(popup);
        assertSame(edit, popup.panels.get(0).container);
        assertEquals(edit.getX() + 0, popup.panels.get(0).x);
        assertEquals(edit.getY() + edit.getHeight(), popup.panels.get(0).y);
        click(root, file.getX() + 3, file.getY() + 3); // another title: its menu
        root.layoutForBackend(W, H);
        assertSame(file, JXOverlays.popup(bar).panels.get(0).container);
        int[] exit = center(file.getChildren().get(2));
        click(root, exit[0], exit[1]);
        assertEquals(Arrays.asList("Exit"), calls);
        root.layoutForBackend(W, H);
        assertNull(JXOverlays.popup(bar), "a choice closes the bar's menu");
        click(root, file.getX() + 3, file.getY() + 3);
        click(root, file.getX() + 3, file.getY() + 3); // the same title again closes it
        root.layoutForBackend(W, H);
        assertNull(JXOverlays.popup(bar));
        click(root, edit.getX() + 3, edit.getY() + 3);
        root.layoutForBackend(W, H);
        press(root, ESCAPE, '\0', bar);
        root.layoutForBackend(W, H);
        assertNull(JXOverlays.popup(bar));
        click(root, edit.getX() + 3, edit.getY() + 3);
        root.layoutForBackend(W, H);
        assertNull(click(root, 395, 295));
        root.layoutForBackend(W, H);
        assertNull(JXOverlays.popup(bar));
    }

    @Test
    void aMenuBarSubmenuOpensOneLevelDown() {
        JXNativeNode root = mount(e("column", p(), menuBar()));
        JXNativeNode bar = root.getChildren().get(0);
        JXNativeNode edit = bar.getChildren().get(1);
        click(root, edit.getX() + 3, edit.getY() + 3);
        root.layoutForBackend(W, H);
        JXNativeNode find = edit.getChildren().get(1);
        int[] row = center(find);
        click(root, row[0], row[1]);
        root.layoutForBackend(W, H);
        JXOverlays.Popup popup = JXOverlays.popup(bar);
        assertEquals(2, popup.panels.size());
        assertSame(find, popup.panels.get(1).container);
        List<String> log = lines(record(root));
        String container = " " + Integer.toHexString(JXTheme.LIGHT.surfaceContainer);
        assertEquals(2, log.stream().filter(line -> line.startsWith("fillRoundRect ") && line.endsWith(container)).count(),
                "a raised surface for each panel");
        int[] next = center(find.getChildren().get(0));
        click(root, next[0], next[1]);
        assertEquals(Arrays.asList("Next"), calls);
    }

    // ---- dialog ----------------------------------------------------------------------------------

    private JXElement dialog(boolean showing, boolean dismissible) {
        return e("dialog", p().set("showing", showing).set("dismissible", dismissible).set("onShowing", onShowing)
                .set("padding", 20.0).set("gap", 10.0).set("alignment", "TOP_LEFT"),
                e("#text", p().set("value", "Delete it?").set("bold", true)),
                e("#text", p().set("value", "This cannot be undone.")),
                e("buttonbar", p(),
                        e("button", p().set("label", "Cancel").set("onAction", (Runnable) () -> calls.add("cancel"))),
                        e("button", p().set("label", "Delete").set("defaultButton", true)
                                .set("onAction", (Runnable) () -> calls.add("delete")))));
    }

    @Test
    void aClosedDialogTakesNoRoomAndAnOpenOneIsCenteredWithItsContentInside() {
        JXNativeNode root = mount(e("stack", p(), button("Behind"), dialog(false, true)));
        JXNativeNode dialog = root.getChildren().get(1);
        assertEquals(0, dialog.getWidth() * dialog.getHeight());
        assertEquals(0, dialog.getPrefWidth(), 0);
        JXNativeNode behind = root.getChildren().get(0);
        int closedX = behind.getX();
        remount(root, e("stack", p(), button("Behind"), dialog(true, true)));
        assertEquals(closedX, root.getChildren().get(0).getX(), "an open dialog does not move what is behind");
        assertTrue(dialog.getWidth() > 0 && dialog.getHeight() > 0 && dialog.getWidth() < W && dialog.getHeight() < H);
        assertEquals((W - dialog.getWidth()) / 2, dialog.getX());
        assertEquals((H - dialog.getHeight()) / 2, dialog.getY());
        JXNativeNode title = dialog.getChildren().get(0);
        assertEquals(dialog.getX() + 20, title.getX(), "inside the padding");
        assertTrue(dialog.getChildren().get(1).getY() > title.getY() && dialog.getChildren().get(2).getY() > dialog.getChildren().get(1).getY());
        for (JXNativeNode child : dialog.getChildren()) {
            assertTrue(child.getY() + child.getHeight() <= dialog.getY() + dialog.getHeight() + 1);
        }
    }

    @Test
    void anOpenDialogIsDrawnLastOverAScrimAndItsContentAfterTheScrim() {
        JXNativeNode root = mount(e("stack", p(), button("Behind"), dialog(true, true)));
        List<String> log = lines(record(root));
        int scrim = lastIndexOf(log, "fillRect 0.00 0.00 400.00 300.00 52000000");
        assertTrue(scrim > lastIndexOf(log, "text \"Behind\""), "the scrim covers what is behind");
        assertTrue(firstIndexOf(log, "text \"Delete it?\"") > scrim, "the dialog is drawn over the scrim");
        assertTrue(lastIndexOf(log, "text \"Delete\"") > scrim);
        String closed = record(mount(e("stack", p(), button("Behind"), dialog(false, true))));
        assertFalse(closed.contains("52000000") || closed.contains("Delete it?"));
    }

    @Test
    void theDialogTakesThePointerFirstAndItsButtonsReachTheirHandlers() {
        JXNativeNode root = mount(e("stack", p(), button("Behind"), dialog(true, true)));
        JXNativeNode behind = root.getChildren().get(0);
        JXNativeNode dialog = root.getChildren().get(1);
        JXNativeNode cancel = dialog.getChildren().get(2).getChildren().get(0);
        assertTrue(cancel.getWidth() > 0);
        assertSame(cancel, root.hitTest(cancel.getX() + 2, cancel.getY() + 2));
        click(root, cancel.getX() + 2, cancel.getY() + 2);
        assertEquals(Arrays.asList("cancel"), calls);
        // the content of the dialog, not the button behind it, is what the pointer finds
        assertNotNull(root.hitTest(behind.getX() + 1, behind.getY() + 1));
        click(root, dialog.getX() + 3, dialog.getY() + 3);
        assertTrue(showings.isEmpty(), "a press on the dialog itself is not a dismissal");
        assertEquals(Arrays.asList("cancel"), calls);
    }

    @Test
    void aPressOnTheScrimAsksToCloseUnlessTheDialogIsNotDismissibleAndNothingBehindIsPressed() {
        JXNativeNode root = mount(e("stack", p().set("alignment", "TOP_LEFT"), button("Behind"), dialog(true, true)));
        JXNativeNode behind = root.getChildren().get(0);
        JXNativeNode card = root.getChildren().get(1);
        assertEquals(0, behind.getX());
        assertTrue(!card.contains(2, 2) && card.contains(W / 2, H / 2), "the scrim is the rest of the window");
        assertSame(card, root.hitTest(2, 2), "over the button in the corner, the scrim is what the pointer finds");
        assertNull(click(root, 2, 2));
        assertEquals(Arrays.<Object>asList(false), showings);
        assertTrue(calls.isEmpty(), "the button behind the scrim is not pressed");
        JXNativeNode fixed = mount(e("stack", p().set("alignment", "TOP_LEFT"), button("Behind"), dialog(true, false)));
        assertNull(click(fixed, 2, 2));
        assertEquals(1, showings.size(), "a dialog that is not dismissible ignores the scrim");
        assertTrue(calls.isEmpty());
        press(fixed, ESCAPE, '\0', null);
        assertEquals(Arrays.<Object>asList(false, false), showings, "Escape closes it anyway");
    }

    @Test
    void enterRunsTheDefaultButtonOfTheOpenDialogOnly() {
        JXNativeNode root = mount(e("stack", p(), button("Behind"), dialog(true, true)));
        press(root, ENTER, '\0', root.getChildren().get(0));
        assertEquals(Arrays.asList("delete"), calls);
        JXNativeNode closed = mount(e("stack", p(), button("Behind"), dialog(false, true)));
        calls.clear();
        press(closed, ENTER, '\0', closed.getChildren().get(0));
        assertTrue(calls.isEmpty(), "no dialog is open: the key goes to the focused node");
        JXElement plain = e("dialog", p().set("showing", true).set("onShowing", onShowing), button("Only"));
        JXNativeNode noDefault = mount(e("stack", p(), plain));
        press(noDefault, ENTER, '\0', noDefault);
        assertTrue(calls.isEmpty(), "a dialog without a default button leaves Enter alone");
    }

    @Test
    void aSelectInsideADialogOpensOverIt() {
        JXElement inside = e("dialog", p().set("showing", true).set("onShowing", onShowing).set("padding", 20.0).set("prefWidth", 260.0),
                select(true, "a", "b", "c"));
        JXNativeNode root = mount(e("stack", p(), inside));
        JXNativeNode dialog = root.getChildren().get(0);
        JXNativeNode select = dialog.getChildren().get(0);
        JXOverlays.Popup popup = JXOverlays.popup(select);
        assertNotNull(popup, "the select in the dialog's content has its popup");
        int[] c = center(popup.cells[0]);
        assertSame(select, click(root, c[0], c[1]));
        assertEquals(Arrays.<Object>asList("a"), changes);
        // a press outside the open list closes the list (the topmost overlay), not the dialog under it
        assertEquals(Arrays.<Object>asList(false), showings, "the choice asked its list to close");
        assertNull(click(root, dialog.getX() + dialog.getWidth() - 2, dialog.getY() + dialog.getHeight() - 2));
        assertEquals(Arrays.<Object>asList(false, false), showings, "and the press inside the dialog asked the still open list again");
    }

    // ---- drawer ----------------------------------------------------------------------------------

    private JXElement drawer(boolean showing, String side) {
        return e("drawer", p().set("showing", showing).set("side", side).set("onShowing", onShowing).set("padding", 12.0).set("gap", 6.0),
                button("Home"), button("Settings"));
    }

    @Test
    void aDrawerIsAFullHeightColumnAtTheLeftOrRightEdgeOverAScrim() {
        JXNativeNode left = mount(e("stack", p(), button("Behind"), drawer(true, "left")));
        JXNativeNode d = left.getChildren().get(1);
        assertEquals(0, d.getX());
        assertEquals(0, d.getY());
        assertEquals(H, d.getHeight());
        assertTrue(d.getWidth() >= JXOverlays.DRAWER_MIN && d.getWidth() < W);
        assertTrue(d.getChildren().get(1).getY() > d.getChildren().get(0).getY(), "its content is a column");
        JXNativeNode right = mount(e("stack", p(), button("Behind"), drawer(true, "right")));
        JXNativeNode r = right.getChildren().get(1);
        assertEquals(W, r.getX() + r.getWidth());
        List<String> log = lines(record(left));
        assertTrue(lastIndexOf(log, "fillRect 0.00 0.00 400.00 300.00 52000000") > lastIndexOf(log, "text \"Behind\""));
        assertTrue(firstIndexOf(log, "text \"Home\"") > lastIndexOf(log, "fillRect 0.00 0.00 400.00 300.00 52000000"));
        assertEquals(0, mount(e("stack", p(), button("Behind"), drawer(false, "left"))).getChildren().get(1).getWidth());
    }

    @Test
    void aPressOnTheDrawerScrimAsksToCloseAndAPressInsideReachesItsContent() {
        JXNativeNode root = mount(e("stack", p(), button("Behind"), drawer(true, "left")));
        JXNativeNode d = root.getChildren().get(1);
        JXNativeNode settings = d.getChildren().get(1);
        click(root, settings.getX() + 3, settings.getY() + 3);
        assertEquals(Arrays.asList("Settings"), calls);
        assertTrue(showings.isEmpty());
        assertNull(click(root, W - 5, H / 2));
        assertEquals(Arrays.<Object>asList(false), showings);
        press(root, ESCAPE, '\0', null);
        assertEquals(Arrays.<Object>asList(false, false), showings);
    }

    // ---- snack bar -------------------------------------------------------------------------------

    private JXElement snackbar(boolean showing, String action, double duration) {
        JXProps.Builder b = p().set("message", "Saved").set("showing", showing).set("onShowing", onShowing);
        if (action != null) {
            b.set("action", action).set("onAction", (Runnable) () -> calls.add("undo"));
        }
        if (duration > 0) {
            b.set("duration", Double.valueOf(duration));
        }
        return e("snackbar", b);
    }

    @Test
    void aSnackBarIsAPillAtTheBottomCenterAndItsActionRunsItsHandler() {
        JXNativeNode root = mount(e("stack", p(), button("Behind"), snackbar(true, "Undo", 0)));
        JXNativeNode snack = root.getChildren().get(1);
        assertTrue(snack.getWidth() > 0 && snack.getHeight() == JXOverlays.SNACK_H);
        assertEquals(H - JXOverlays.SNACK_H - JXOverlays.SNACK_MARGIN, snack.getY());
        assertEquals((W - snack.getWidth()) / 2, snack.getX());
        click(root, snack.getX() + 6, snack.getY() + 10);
        assertTrue(calls.isEmpty() && showings.isEmpty(), "the message is not the action");
        click(root, snack.getX() + snack.getWidth() - 10, snack.getY() + 10);
        assertEquals(Arrays.asList("undo"), calls);
        assertEquals(Arrays.<Object>asList(false), showings);
        List<String> log = lines(record(root));
        assertTrue(lastIndexOf(log, "text \"Saved\"") > lastIndexOf(log, "text \"Behind\""));
        assertTrue(firstIndexOf(log, "text \"Undo\"") > firstIndexOf(log, "text \"Saved\""));
        JXNativeNode closed = mount(e("stack", p(), snackbar(false, "Undo", 0)));
        assertEquals(0, closed.getChildren().get(0).getWidth());
        assertFalse(record(closed).contains("Saved"));
    }

    @Test
    void aSnackBarDoesNotTakeThePressesOutsideItAndEscapeClosesIt() {
        JXNativeNode root = mount(e("stack", p(), button("Behind"), snackbar(true, null, 0)));
        JXNativeNode behind = root.getChildren().get(0);
        JXNativeNode hit = click(root, behind.getX() + 2, behind.getY() + 2);
        assertEquals("button", hit.getType());
        assertEquals(Arrays.asList("Behind"), calls);
        assertTrue(showings.isEmpty());
        press(root, ESCAPE, '\0', null);
        assertEquals(Arrays.<Object>asList(false), showings);
    }

    @Test
    void aSnackBarAsksToCloseWhenItsDurationHasPassed() {
        final long[] now = {5000};
        JXOverlays.clock = () -> now[0];
        JXNativeNode root = mount(e("stack", p(), snackbar(true, null, 3000)));
        assertEquals(3000, root.tickOverlays(now[0]), "shown at 5000, due 3000 ms later");
        now[0] = 6200;
        assertEquals(1800, root.tickOverlays(now[0]));
        assertTrue(showings.isEmpty());
        now[0] = 8000;
        assertEquals(-1, root.tickOverlays(now[0]));
        assertEquals(Arrays.<Object>asList(false), showings);
        // shown again later: a new period
        remount(root, e("stack", p(), snackbar(false, null, 3000)));
        now[0] = 20000;
        remount(root, e("stack", p(), snackbar(true, null, 3000)));
        assertEquals(3000, root.tickOverlays(now[0]));
        // no duration: it waits for the application
        JXNativeNode plain = mount(e("stack", p(), snackbar(true, null, 0)));
        assertEquals(-1, plain.tickOverlays(Long.MAX_VALUE / 2));
        assertTrue(showings.size() == 1);
    }

    // ---- incremental painting --------------------------------------------------------------------

    @Test
    void repaintingOnlyTheDamageOfAnOpeningAndClosingPopupGivesTheWholeRepaintsPixels() {
        JXElement closed = e("column", p().set("padding", 8.0), select(false, "a", "b", "c"), button("Below"));
        JXElement open = e("column", p().set("padding", 8.0), select(true, "a", "b", "c"), button("Below"));
        JXElement dialogOpen = e("stack", p(), button("Behind"), dialog(true, true));
        JXElement dialogClosed = e("stack", p(), button("Behind"), dialog(false, true));
        for (JXElement[] pair : new JXElement[][] {{closed, open}, {dialogClosed, dialogOpen}}) {
            JXNativeNode root = mount(pair[0]);
            JXSoftwareFrame incremental = new JXSoftwareFrame(true);
            incremental.paint(root, W, H, 1f);
            for (JXElement step : new JXElement[] {pair[1], pair[0], pair[1]}) {
                remount(root, step);
                incremental.paint(root, W, H, 1f);
                JXSoftwareFrame whole = new JXSoftwareFrame(false);
                whole.paint(root, W, H, 1f);
                assertArrayEquals(Arrays.copyOf(whole.pixels(), W * H), Arrays.copyOf(incremental.pixels(), W * H));
            }
        }
    }

    @Test
    void anOpenPopupColorsPixelsOverWhatIsBehindIt() {
        JXNativeNode root = mount(e("column", p().set("padding", 8.0), select(true, "a", "b", "c"), button("Below")));
        JXOverlays.Popup popup = JXOverlays.popup(root.getChildren().get(0));
        JXSoftwareFrame frame = new JXSoftwareFrame(false);
        frame.paint(root, W, H, 1f);
        int[] px = Arrays.copyOf(frame.pixels(), W * H);
        assertEquals(JXTheme.LIGHT.surfaceContainer, px[(popup.y + 1) * W + popup.x + 20], "the surface of the list, over the button below");
        assertEquals(JXTheme.LIGHT.surfaceContainer, px[(popup.y + 3) * W + popup.x + popup.w - 6], "the surface of a row, right of its text");
    }

    @Test
    void aDialogScrimDarkensThePixelsBehindIt() {
        JXNativeNode closed = mount(e("stack", p().set("background", 0xFFFFFFFF), button("Behind"), dialog(false, true)));
        JXNativeNode open = mount(e("stack", p().set("background", 0xFFFFFFFF), button("Behind"), dialog(true, true)));
        JXSoftwareFrame a = new JXSoftwareFrame(false);
        a.paint(closed, W, H, 1f);
        JXSoftwareFrame b = new JXSoftwareFrame(false);
        b.paint(open, W, H, 1f);
        int corner = 4 * W + 4;
        assertEquals(0xFFFFFFFF, a.pixels()[corner]);
        int shaded = b.pixels()[corner];
        assertTrue((shaded & 0xFF) < 0xF0 && (shaded & 0xFF) > 0x80, Integer.toHexString(shaded));
    }

    // ---- tree rows -------------------------------------------------------------------------------

    private JXElement treeRow(String label, int indent, String disclosure, boolean toggle) {
        JXProps.Builder b = p().set("value", label).set("indent", Double.valueOf(indent)).set("disclosure", disclosure)
                .set("onAction", (Runnable) () -> calls.add("row " + label));
        if (toggle) {
            b.set("onToggle", (Runnable) () -> calls.add("toggle " + label));
        }
        return e("cell", b);
    }

    @Test
    void aTreeRowIndentsItsContentByDepthAndAPressOnItsArrowTogglesInsteadOfSelecting() {
        JXElement list = e("list", p().set("itemCount", 3).set("cellHeight", 24.0).set("prefWidth", 300.0).set("prefHeight", 100.0),
                treeRow("root", 0, "expanded", true), treeRow("child", 1, "collapsed", true), treeRow("leaf", 2, "leaf", true));
        JXNativeNode tree = mount(e("column", p().set("padding", 8.0), list)).getChildren().get(0);
        JXNativeNode root = tree.getChildren().get(0);
        JXNativeNode leaf = tree.getChildren().get(2);
        assertTrue(JXControlLayout.treeLeft(leaf) > JXControlLayout.treeLeft(root));
        assertEquals(JXControlLayout.TREE_ARROW, JXControlLayout.treeLeft(root), 0);
        assertEquals(2 * JXControlLayout.TREE_INDENT + JXControlLayout.TREE_ARROW, JXControlLayout.treeLeft(leaf), 0);
        assertTrue(JXControlLayout.disclosureAt(root, root.getX() + 7 + 4));
        assertFalse(JXControlLayout.disclosureAt(root, root.getX() + 7 + JXControlLayout.TREE_ARROW + 2));
        assertFalse(JXControlLayout.disclosureAt(leaf, leaf.getX() + 7 + 2 * JXControlLayout.TREE_INDENT + 4), "a leaf has no arrow");
        int arrowX = root.getX() + 7 + 4;
        root.dispatchPointer(new JXPointerEvent(arrowX, root.getY() + 5, 0));
        root.dispatchPointer(new JXPointerEvent(root.getX() + 150, root.getY() + 5, 0));
        assertEquals(Arrays.asList("toggle root", "row root"), calls);
        leaf.dispatchPointer(new JXPointerEvent(leaf.getX() + 7 + 2 * JXControlLayout.TREE_INDENT + 4, leaf.getY() + 5, 0));
        assertEquals("row leaf", calls.get(2), "the arrow place of a leaf selects it");
        // a row without a handler for the arrow selects
        JXNativeNode bare = mount(e("column", p().set("padding", 8.0), e("list", p().set("itemCount", 1).set("cellHeight", 24.0)
                .set("prefWidth", 300.0).set("prefHeight", 40.0), treeRow("only", 0, "collapsed", false)))).getChildren().get(0)
                .getChildren().get(0);
        bare.dispatchPointer(new JXPointerEvent(bare.getX() + 7 + 4, bare.getY() + 5, 0));
        assertEquals("row only", calls.get(3));
    }

    @Test
    void theArrowsOfATreeAreDrawnWithTrianglesAndLeavesDrawNone() {
        JXElement list = e("list", p().set("itemCount", 3).set("cellHeight", 24.0).set("prefWidth", 300.0).set("prefHeight", 100.0),
                treeRow("root", 0, "expanded", true), treeRow("child", 1, "collapsed", true), treeRow("leaf", 2, "leaf", true));
        String log = record(mount(e("column", p().set("padding", 8.0), list)));
        int triangles = 0;
        for (String line : log.split("\n")) {
            if (line.startsWith("triangle ")) {
                triangles++;
            }
        }
        assertEquals(2, triangles, "an arrow for the expanded and for the collapsed row, none for the leaf");
        List<String> lines = lines(log);
        assertTrue(lines.get(firstIndexOf(lines, "text \"leaf\"")).compareTo("") != 0);
        // text of a deeper row starts further right
        double rootX = Double.parseDouble(lines.get(firstIndexOf(lines, "text \"root\"")).split(" ")[2]);
        double childX = Double.parseDouble(lines.get(firstIndexOf(lines, "text \"child\"")).split(" ")[2]);
        double leafX = Double.parseDouble(lines.get(firstIndexOf(lines, "text \"leaf\"")).split(" ")[2]);
        assertEquals(JXControlLayout.TREE_INDENT, childX - rootX, 0.01);
        assertEquals(JXControlLayout.TREE_INDENT, leafX - childX, 0.01);
    }
}
