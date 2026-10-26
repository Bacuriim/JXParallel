package com.jxparallel.ui.native2d;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;
import com.jxparallel.ui.text.JXTextEngine;

/**
 * Overlays: what is drawn over the rest of the window and takes the pointer first. One mechanism serves every widget
 * that opens over the screen:
 *
 * <ul>
 * <li><b>popup hosts</b>, controls that stay where they are and open a popup below them: {@code select} (the option
 * list), {@code datepicker} (a calendar), {@code timepicker} (hour and minute cells) and {@code colorpicker}
 * (swatches and a hex field). They draw their popup from their own props and hit it by geometry, as
 * {@link JXCalendar} does, and have no children for it;</li>
 * <li><b>menus</b>: {@code menubutton}, {@code contextmenu} and {@code menubar}, whose children are {@code menuitem}s
 * and {@code menu}s (a submenu, one level in a bar, any level below);</li>
 * <li><b>layers</b>: {@code dialog} (a column of content in the middle of the window, over a scrim), {@code drawer}
 * (a column at the left or right edge, over a scrim) and {@code snackbar} (a message at the bottom).</li>
 * </ul>
 *
 * <p>Open or closed: the {@code showing} prop when the node has one (the application holds the state, and the node
 * only asks, through {@code onShowing}, a {@code Consumer<Boolean>}, to open or close); a node without it keeps the
 * state itself. Closing is asked by an outside press, Escape, a choice in a popup or a menu, and a snack bar's timeout.
 *
 * <p>Where: {@link #layout} runs after every layout of the window ({@code layoutForBackend}) and gives each open overlay
 * its place in window coordinates. A layer or a context menu takes no room where it is written (size 0 by 0);
 * its content is placed here. {@code JXPaint} draws the open overlays after everything else, in tree order, and
 * {@code JXNativeNode.hitPath} finds them before everything else.
 */
final class JXOverlays {
    /** Rows of a select's list, and how many it shows before it scrolls to the chosen one. */
    static final int ROW = 24;
    static final int SELECT_ROWS = 10;
    /** Menus: height of an item and of a separator, padding of a panel, narrowest panel, text padding, gaps. */
    static final int MENU_ROW = 28;
    static final int MENU_SEPARATOR = 9;
    static final int MENU_PAD = 4;
    static final int MENU_MIN = 160;
    static final int MENU_TEXT = 12;
    static final int ACCELERATOR_GAP = 24;
    static final int SUBMENU_ARROW = 20;
    static final int BAR_H = 28;
    static final int BAR_PAD = 12;
    /** Time picker popup: padding, cell size, columns of the hour grid. */
    static final int TIME_PAD = 8;
    static final int TIME_CELL_W = 32;
    static final int TIME_CELL_H = 26;
    static final int TIME_COLUMNS = 6;
    static final int TIME_W = 2 * TIME_PAD + TIME_COLUMNS * TIME_CELL_W;
    /** Color picker popup: padding, swatch size and gap, columns, rows, and the height of the hex field. */
    static final int COLOR_PAD = 8;
    static final int SWATCH = 22;
    static final int SWATCH_GAP = 2;
    static final int SWATCH_COLUMNS = 8;
    static final int HEX_H = 26;
    /** Dialogs and drawers: the margin kept from the window, the narrowest drawer, the snack bar's height and margin. */
    static final int DIALOG_MARGIN = 24;
    static final int DRAWER_MIN = 240;
    static final int SNACK_H = 48;
    static final int SNACK_MARGIN = 16;
    static final int SNACK_PAD = 16;
    static final int SNACK_GAP = 24;
    private static final int ESCAPE = 256;
    private static final int ENTER = 257;

    /** The 40 swatches of the color picker, row by row: grays, then eight hues in five shades. */
    static final int[] PALETTE = {
        0xFF000000, 0xFF424242, 0xFF757575, 0xFFBDBDBD, 0xFFE0E0E0, 0xFFF5F5F5, 0xFFFFFFFF, 0xFF795548,
        0xFFB71C1C, 0xFFE91E63, 0xFF9C27B0, 0xFF673AB7, 0xFF3F51B5, 0xFF2196F3, 0xFF03A9F4, 0xFF00BCD4,
        0xFFE53935, 0xFFF06292, 0xFFBA68C8, 0xFF9575CD, 0xFF7986CB, 0xFF64B5F6, 0xFF4FC3F7, 0xFF4DD0E1,
        0xFF009688, 0xFF4CAF50, 0xFF8BC34A, 0xFFCDDC39, 0xFFFFEB3B, 0xFFFFC107, 0xFFFF9800, 0xFFFF5722,
        0xFF26A69A, 0xFF66BB6A, 0xFFAED581, 0xFFDCE775, 0xFFFFF176, 0xFFFFD54F, 0xFFFFB74D, 0xFFFF8A65};

    /** Time of day for the snack bars; tests set it so timeouts are repeatable. */
    static LongSupplier clock = System::currentTimeMillis;
    /** Today and the language of a date picker's popup (the user's); tests fix both so recordings are repeatable. */
    static java.util.function.Supplier<LocalDate> today = LocalDate::now;
    static java.util.function.Supplier<Locale> locale = Locale::getDefault;

    private JXOverlays() {
    }

    // ---- kinds ---------------------------------------------------------------------------------------

    /** Nodes that take no room where they are written: their content is placed over the window. */
    static boolean floating(String type) {
        switch (type) {
            case "dialog":
            case "drawer":
            case "snackbar":
            case "contextmenu":
                return true;
            default:
                return false;
        }
    }

    /** Nodes whose children are the content of an overlay, placed by the overlay pass, not by the parent's layout. */
    static boolean overlayContainer(String type) {
        switch (type) {
            case "dialog":
            case "drawer":
            case "contextmenu":
            case "menubutton":
            case "menubar":
            case "menu":
                return true;
            default:
                return false;
        }
    }

    static boolean popupHost(String type) {
        switch (type) {
            case "select":
            case "datepicker":
            case "timepicker":
            case "colorpicker":
                return true;
            default:
                return false;
        }
    }

    /**
     * Whether this node is one that opens over the window. A {@code select} or {@code datepicker} does so only with the
     * prop {@code overlay} true (JXML writes it): the JavaFX bridge sends the same two elements with {@code showing} and
     * draws their popups as separate elements of its own, which must not be drawn a second time.
     */
    static boolean canOpen(JXNativeNode node) {
        String type = node.getType();
        if (popupHost(type)) {
            return "timepicker".equals(type) || "colorpicker".equals(type) || Boolean.TRUE.equals(node.getProperty("overlay"));
        }
        return floating(type) || "menubutton".equals(type) || "menubar".equals(type);
    }

    static boolean modal(String type) {
        return "dialog".equals(type) || "drawer".equals(type);
    }

    /** Open: the {@code showing} prop when written, else the node's own state; a menu bar is open while a menu is. */
    static boolean isOpen(JXNativeNode node) {
        if ("menubar".equals(node.getType())) {
            return node.openChild >= 0 && node.openChild < node.getChildren().size();
        }
        Object showing = node.getProperty("showing");
        return showing instanceof Boolean ? ((Boolean) showing).booleanValue() : node.localOpen;
    }

    /**
     * Asks to open or close {@code node}. The node's own state follows at once when it has no {@code showing} prop; with
     * one, the application decides when it renders again. {@code onShowing} gets the Boolean asked for.
     */
    @SuppressWarnings("unchecked")
    static void request(JXNativeNode node, boolean open) {
        if (open == isOpen(node)) {
            return;
        }
        node.openChild = -1;
        node.hot = null;
        node.hotKey = false;
        if (!(node.getProperty("showing") instanceof Boolean)) {
            node.localOpen = open;
        }
        Object handler = node.getProperty("onShowing");
        if (handler instanceof Consumer) {
            ((Consumer<Object>) handler).accept(Boolean.valueOf(open));
        }
    }

    // ---- the open overlays ---------------------------------------------------------------------------

    /** An open overlay and the nodes above it, from the root. */
    static final class Entry {
        final JXNativeNode node;
        final List<JXNativeNode> ancestors;

        Entry(JXNativeNode node, List<JXNativeNode> ancestors) {
            this.node = node;
            this.ancestors = ancestors;
        }
    }

    /** The open overlays in tree order, so the last one is on top. Closed ones lose the geometry they had. */
    static List<Entry> collect(JXNativeNode root) {
        List<Entry> out = new ArrayList<Entry>();
        walk(root, new ArrayList<JXNativeNode>(), out);
        return out;
    }

    private static void walk(JXNativeNode node, List<JXNativeNode> path, List<Entry> out) {
        if (Boolean.TRUE.equals(node.getProperty("hidden"))) {
            return;
        }
        boolean open = false;
        if (node.overlayHost && canOpen(node)) {
            open = isOpen(node);
            if (open) {
                out.add(new Entry(node, new ArrayList<JXNativeNode>(path)));
            } else {
                node.overlayState = null;
                node.shownAt = 0;
                node.hot = null;
                node.hotKey = false;
            }
        }
        // the children of a menu are its items; the children of an open dialog or drawer are ordinary content
        if (node.overlayContent && !(modal(node.getType()) && open)) {
            return;
        }
        if (node.floatingNode && !open) {
            return;
        }
        path.add(node);
        List<JXNativeNode> kids = node.getChildren();
        for (int i = 0, n = kids.size(); i < n; i++) {
            walk(kids.get(i), path, out);
        }
        path.remove(path.size() - 1);
    }

    // ---- geometry of the popups ----------------------------------------------------------------------

    /** The geometry the overlay pass computed for one open node. */
    static final class Popup {
        int x;
        int y;
        int w;
        int h;
        /** Cells of a list or grid: x, y, width, height, kind, value. */
        int[][] cells = new int[0][];
        String[] labels = new String[0];
        /** A date picker's calendar, laid out at x, y. */
        JXNativeNode calendar;
        /** A select's list: the index of the first option shown. */
        int first;
        /** Menu panels, the outermost first. */
        final List<Panel> panels = new ArrayList<Panel>();
        /** A color picker's hex field: x, y, width, height. */
        int[] field;
        /** The window the popup was placed in. */
        int windowW;
        int windowH;

        boolean contains(int px, int py) {
            return px >= x && py >= y && px < x + w && py < y + h;
        }
    }

    /** A menu's panel: the node whose children are its rows, and where it is. */
    static final class Panel {
        final JXNativeNode container;
        int x;
        int y;
        int w;
        int h;

        Panel(JXNativeNode container) {
            this.container = container;
        }

        boolean contains(int px, int py) {
            return px >= x && py >= y && px < x + w && py < y + h;
        }
    }

    static Popup popup(JXNativeNode node) {
        return node.overlayState instanceof Popup ? (Popup) node.overlayState : null;
    }

    /** Places every open overlay of the tree in a window of this size. */
    static void layout(JXNativeNode root, int width, int height) {
        for (Entry entry : collect(root)) {
            JXNativeNode node = entry.node;
            switch (node.getType()) {
                case "select":
                    node.overlayState = selectPopup(node, width, height);
                    break;
                case "datepicker":
                    node.overlayState = datePopup(node, width, height);
                    break;
                case "timepicker":
                    node.overlayState = timePopup(node, width, height);
                    break;
                case "colorpicker":
                    node.overlayState = colorPopup(node, width, height);
                    break;
                case "menubutton":
                case "contextmenu":
                case "menubar":
                    node.overlayState = menus(node, width, height);
                    break;
                case "dialog":
                    placeDialog(node, width, height);
                    break;
                case "drawer":
                    placeDrawer(node, width, height);
                    break;
                case "snackbar":
                    placeSnackbar(node, width, height);
                    break;
                default:
                    break;
            }
        }
    }

    /** Below the host, aligned with its left edge, kept in the window; above it when it does not fit below. */
    private static void place(Popup po, JXNativeNode host, int width, int height) {
        po.windowW = width;
        po.windowH = height;
        po.x = Math.max(0, Math.min(host.getX(), width - po.w));
        int below = host.getY() + host.getHeight();
        po.y = below + po.h > height && host.getY() - po.h >= 0 ? host.getY() - po.h : below;
    }

    private static Popup selectPopup(JXNativeNode host, int width, int height) {
        String[] options = JXPaint.strings(host.getProperty("options"));
        if (options.length == 0) {
            return null;
        }
        Popup po = new Popup();
        int rows = Math.min(options.length, SELECT_ROWS);
        int chosen = Arrays.asList(options).indexOf(JXPaint.text(host, "value"));
        Popup before = popup(host);
        // opened: the chosen option in the middle; open: the list stays where it was, until the highlighted option leaves it
        int first = Math.max(0, Math.min(options.length - rows,
                before != null ? before.first : chosen < 0 ? 0 : chosen - rows / 2));
        if (host.hot instanceof Integer) {
            int hot = ((Integer) host.hot).intValue();
            first = Math.max(0, Math.min(options.length - rows, hot < first ? hot : hot >= first + rows ? hot - rows + 1 : first));
        }
        po.first = first;
        JXTextEngine engine = JXTextEngine.get(false);
        double widest = 0;
        for (String option : options) {
            widest = Math.max(widest, engine.width(option, JXPaint.fontSize(host)));
        }
        po.w = (int) Math.max(host.getWidth(), Math.ceil(widest) + 2 * JXNativeNode.PAD_X + 2);
        po.h = rows * ROW + 2;
        place(po, host, width, height);
        po.cells = new int[rows][];
        po.labels = new String[rows];
        for (int i = 0; i < rows; i++) {
            po.cells[i] = new int[] {po.x + 1, po.y + 1 + i * ROW, po.w - 2, ROW, 0, first + i};
            po.labels[i] = options[first + i];
        }
        return po;
    }

    private static Popup datePopup(JXNativeNode host, int width, int height) {
        Popup po = new Popup();
        po.w = JXCalendar.WIDTH;
        po.h = JXCalendar.HEIGHT;
        place(po, host, width, height);
        String value = JXPaint.text(host, "value");
        LocalDate chosen = parseDate(value);
        if (!value.equals(host.viewValue)) {
            host.viewValue = value;
            LocalDate shown = chosen != null ? chosen : today.get();
            host.viewYear = shown.getYear();
            host.viewMonth = shown.getMonthValue();
        }
        Locale locale = JXOverlays.locale.get();
        int first = WeekFields.of(locale).getFirstDayOfWeek().getValue();
        String[] weekdays = new String[7];
        for (int i = 0; i < 7; i++) {
            weekdays[i] = java.time.DayOfWeek.of((first - 1 + i) % 7 + 1).getDisplayName(TextStyle.SHORT, locale);
        }
        LocalDate now = today.get();
        JXProps.Builder props = JXProps.builder().set("year", Integer.valueOf(host.viewYear))
                .set("month", Integer.valueOf(host.viewMonth)).set("firstDayOfWeek", Integer.valueOf(first))
                .set("weekdays", weekdays)
                .set("title", Month.of(host.viewMonth).getDisplayName(TextStyle.FULL, locale) + " " + host.viewYear)
                .set("todayYear", Integer.valueOf(now.getYear())).set("todayMonth", Integer.valueOf(now.getMonthValue()))
                .set("todayDay", Integer.valueOf(now.getDayOfMonth()));
        if (host.hot instanceof LocalDate) {
            LocalDate hot = (LocalDate) host.hot;
            props.set("hoverYear", Integer.valueOf(hot.getYear())).set("hoverMonth", Integer.valueOf(hot.getMonthValue()))
                    .set("hoverDay", Integer.valueOf(hot.getDayOfMonth()))
                    .set("hoverLayer", Integer.valueOf(host.hotKey ? JXTheme.FOCUS : JXTheme.HOVER));
        }
        if (chosen != null) {
            props.set("selectedYear", Integer.valueOf(chosen.getYear())).set("selectedMonth", Integer.valueOf(chosen.getMonthValue()))
                    .set("selectedDay", Integer.valueOf(chosen.getDayOfMonth()));
        }
        po.calendar = JXNativeNode.createBackendNode(JXElement.of("calendar", props.build()));
        po.calendar.layout(po.x, po.y, po.w, po.h);
        return po;
    }

    static LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text.trim());
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    // ---- time of day ---------------------------------------------------------------------------------

    /** Minutes since midnight of {@code HH:mm}, or -1 when the text is not one. */
    static int parseTime(String text) {
        String s = text == null ? "" : text.trim();
        int colon = s.indexOf(':');
        if (colon < 1 || colon != s.length() - 3) {
            return -1;
        }
        try {
            int hour = Integer.parseInt(s.substring(0, colon));
            int minute = Integer.parseInt(s.substring(colon + 1));
            return hour >= 0 && hour < 24 && minute >= 0 && minute < 60 ? hour * 60 + minute : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static String formatTime(int hour, int minute) {
        return (hour < 10 ? "0" : "") + hour + ":" + (minute < 10 ? "0" : "") + minute;
    }

    /** The distance between the minutes a time picker offers: a divisor of 60, 5 when not written or not one. */
    static int minuteStep(JXNativeNode node) {
        int step = (int) JXPaint.number(node, "minuteStep", 5);
        return step >= 1 && step <= 30 && 60 % step == 0 ? step : 5;
    }

    static boolean hour24(JXNativeNode node) {
        return !Boolean.FALSE.equals(node.getProperty("hour24"));
    }

    /** What the field of a time picker shows: {@code HH:mm}, or {@code h:mm AM} without {@code hour24}. */
    static String timeText(JXNativeNode node) {
        int time = parseTime(JXPaint.text(node, "value"));
        if (time < 0) {
            return "";
        }
        int hour = time / 60;
        int minute = time % 60;
        if (hour24(node)) {
            return formatTime(hour, minute);
        }
        return (hour % 12 == 0 ? 12 : hour % 12) + ":" + (minute < 10 ? "0" : "") + minute + (hour < 12 ? " AM" : " PM");
    }

    private static final int HOUR = 0;
    private static final int MINUTE = 1;
    private static final int PERIOD = 2;

    private static Popup timePopup(JXNativeNode host, int width, int height) {
        Popup po = new Popup();
        boolean h24 = hour24(host);
        int step = minuteStep(host);
        List<int[]> cells = new ArrayList<int[]>();
        List<String> labels = new ArrayList<String>();
        int inner = TIME_W - 2 * TIME_PAD;
        int hours = h24 ? 24 : 12;
        int y = TIME_PAD;
        for (int i = 0; i < hours; i++) {
            int value = h24 ? i : (i == 0 ? 12 : i);
            cells.add(new int[] {TIME_PAD + (i % TIME_COLUMNS) * TIME_CELL_W, y + (i / TIME_COLUMNS) * TIME_CELL_H,
                TIME_CELL_W, TIME_CELL_H, HOUR, value});
            labels.add(h24 ? formatTime(value, 0).substring(0, 2) : String.valueOf(value));
        }
        y += (hours / TIME_COLUMNS) * TIME_CELL_H + TIME_PAD;
        int minutes = 60 / step;
        int columns = minutes <= 12 ? 6 : 10;
        int cellW = inner / columns;
        for (int i = 0; i < minutes; i++) {
            cells.add(new int[] {TIME_PAD + (i % columns) * cellW, y + (i / columns) * TIME_CELL_H, cellW, TIME_CELL_H, MINUTE,
                i * step});
            labels.add(formatTime(0, i * step).substring(3));
        }
        y += ((minutes + columns - 1) / columns) * TIME_CELL_H;
        if (!h24) {
            y += TIME_PAD;
            for (int i = 0; i < 2; i++) {
                cells.add(new int[] {TIME_PAD + i * (inner / 2), y, inner / 2, TIME_CELL_H, PERIOD, i});
                labels.add(i == 0 ? "AM" : "PM");
            }
            y += TIME_CELL_H;
        }
        po.w = TIME_W;
        po.h = y + TIME_PAD;
        place(po, host, width, height);
        po.cells = cells.toArray(new int[0][]);
        for (int[] cell : po.cells) {
            cell[0] += po.x;
            cell[1] += po.y;
        }
        po.labels = labels.toArray(new String[0]);
        return po;
    }

    /** The value after a click on a cell of a time picker's popup. */
    static String timeAfter(JXNativeNode host, int[] cell) {
        int time = parseTime(JXPaint.text(host, "value"));
        int hour = time < 0 ? 0 : time / 60;
        int minute = time < 0 ? 0 : time % 60;
        boolean h24 = hour24(host);
        if (cell[4] == HOUR) {
            hour = h24 ? cell[5] : (cell[5] % 12) + (hour >= 12 ? 12 : 0);
        } else if (cell[4] == MINUTE) {
            minute = cell[5];
        } else {
            hour = (hour % 12) + (cell[5] == 1 ? 12 : 0);
        }
        return formatTime(hour, minute);
    }

    /** Whether a cell of a time picker's popup is the one the value has now. */
    static boolean timeCurrent(JXNativeNode host, int[] cell) {
        int time = parseTime(JXPaint.text(host, "value"));
        if (time < 0) {
            return false;
        }
        int hour = time / 60;
        if (cell[4] == HOUR) {
            return hour24(host) ? cell[5] == hour : cell[5] == (hour % 12 == 0 ? 12 : hour % 12);
        }
        return cell[4] == MINUTE ? cell[5] == time % 60 : cell[5] == (hour >= 12 ? 1 : 0);
    }

    // ---- color ---------------------------------------------------------------------------------------

    /** {@code #RRGGBB} for {@code #RGB} or {@code #RRGGBB} in any case; null for anything else. */
    static String normalizeHex(String text) {
        String s = text == null ? "" : text.trim();
        if (!s.startsWith("#") || s.length() != 4 && s.length() != 7) {
            return null;
        }
        for (int i = 1; i < s.length(); i++) {
            if (Character.digit(s.charAt(i), 16) < 0) {
                return null;
            }
        }
        if (s.length() == 4) {
            s = "#" + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2) + s.charAt(3) + s.charAt(3);
        }
        return s.toUpperCase(Locale.ROOT);
    }

    static boolean validHex(String text) {
        return text.length() == 7 && normalizeHex(text) != null;
    }

    /** The opaque ARGB color of {@code #RRGGBB}, or 0 when the text is none. */
    static int colorOf(String text) {
        String hex = normalizeHex(text);
        return hex == null ? 0 : 0xFF000000 | Integer.parseInt(hex.substring(1), 16);
    }

    static String hex(int argb) {
        String s = Integer.toHexString(argb & 0xFFFFFF).toUpperCase(Locale.ROOT);
        return "#000000".substring(0, 7 - s.length()) + s;
    }

    /** The hex text being typed in the field of a color picker: the value until the user types. */
    static String hexDraft(JXNativeNode node) {
        String value = JXPaint.text(node, "value");
        if (!value.equals(node.hexSeen)) {
            node.hexSeen = value;
            node.hexDraft = value;
        }
        return node.hexDraft == null ? "" : node.hexDraft;
    }

    private static Popup colorPopup(JXNativeNode host, int width, int height) {
        Popup po = new Popup();
        int rows = (PALETTE.length + SWATCH_COLUMNS - 1) / SWATCH_COLUMNS;
        int step = SWATCH + SWATCH_GAP;
        po.w = 2 * COLOR_PAD + SWATCH_COLUMNS * step - SWATCH_GAP;
        po.h = COLOR_PAD + rows * step - SWATCH_GAP + 6 + HEX_H + COLOR_PAD;
        place(po, host, width, height);
        po.cells = new int[PALETTE.length][];
        for (int i = 0; i < PALETTE.length; i++) {
            po.cells[i] = new int[] {po.x + COLOR_PAD + (i % SWATCH_COLUMNS) * step, po.y + COLOR_PAD + (i / SWATCH_COLUMNS) * step,
                SWATCH, SWATCH, 0, PALETTE[i]};
        }
        po.field = new int[] {po.x + COLOR_PAD, po.y + COLOR_PAD + rows * step - SWATCH_GAP + 6, po.w - 2 * COLOR_PAD, HEX_H};
        return po;
    }

    // ---- menus ---------------------------------------------------------------------------------------

    static String label(JXNativeNode node) {
        return JXPaint.text(node, "label");
    }

    static boolean separator(JXNativeNode node) {
        return "menuitem".equals(node.getType()) && Boolean.TRUE.equals(node.getProperty("separator"));
    }

    /** Width and height of a row of a menu: a {@code menuitem} or a {@code menu} (a submenu entry). */
    static double[] rowSize(JXNativeNode node) {
        if (separator(node)) {
            return new double[] {1, MENU_SEPARATOR};
        }
        JXTextEngine engine = JXTextEngine.get(false);
        float size = JXTextEngine.DEFAULT_SIZE;
        double w = 2 * MENU_TEXT + engine.width(label(node), size);
        String accelerator = JXPaint.text(node, "accelerator");
        if (!accelerator.isEmpty()) {
            w += ACCELERATOR_GAP + engine.width(accelerator, size);
        }
        if ("menu".equals(node.getType())) {
            w += SUBMENU_ARROW;
        }
        return new double[] {Math.ceil(w), MENU_ROW};
    }

    /** The width of a menu's title in a menu bar. */
    static double headerWidth(JXNativeNode menu) {
        return Math.ceil(2 * BAR_PAD + JXTextEngine.get(false).width(label(menu), JXTextEngine.DEFAULT_SIZE));
    }

    private static Popup menus(JXNativeNode host, int width, int height) {
        Popup po = new Popup();
        po.windowW = width;
        po.windowH = height;
        JXNativeNode container;
        int ax;
        int ay;
        int above;
        if ("menubar".equals(host.getType())) {
            container = host.getChildren().get(host.openChild);
            ax = container.getX();
            ay = container.getY() + container.getHeight();
            above = container.getY();
        } else if ("contextmenu".equals(host.getType())) {
            container = host;
            ax = host.overlayX;
            ay = host.overlayY;
            above = host.overlayY;
        } else {
            container = host;
            ax = host.getX();
            ay = host.getY() + host.getHeight();
            above = host.getY();
        }
        Panel panel = panel(container, ax, ay, above, width, height, true);
        po.panels.add(panel);
        po.x = panel.x;
        po.y = panel.y;
        po.w = panel.w;
        po.h = panel.h;
        if ("contextmenu".equals(host.getType())) {
            host.layout(panel.x, panel.y, panel.w, panel.h); // it takes no room where it is written; open, it is its panel
        }
        // the chain of open submenus: each to the right of the row that opened it
        while (container.openChild >= 0 && container.openChild < container.getChildren().size()) {
            JXNativeNode row = container.getChildren().get(container.openChild);
            if (!"menu".equals(row.getType()) || row.getChildren().isEmpty()) {
                break;
            }
            Panel parent = panel;
            Panel sub = panel(row, parent.x + parent.w - 2, row.getY() - MENU_PAD, row.getY() - MENU_PAD, width, height, false);
            if (sub.x + sub.w > width && parent.x - sub.w + 2 >= 0) {
                sub.x = parent.x - sub.w + 2;
                layoutRows(sub);
            }
            po.panels.add(sub);
            panel = sub;
            container = row;
        }
        return po;
    }

    /** A panel for the rows of {@code container} with its top left at (x, y), or above {@code aboveY} if it does not fit below. */
    private static Panel panel(JXNativeNode container, int x, int y, int aboveY, int width, int height, boolean flip) {
        Panel panel = new Panel(container);
        double w = MENU_MIN;
        double h = 2 * MENU_PAD + 2;
        for (JXNativeNode row : container.getChildren()) {
            double[] s = rowSize(row);
            w = Math.max(w, s[0]);
            h += s[1];
        }
        panel.w = (int) Math.min(width, w + 2);
        panel.h = (int) Math.min(height, h);
        panel.x = Math.max(0, Math.min(x, width - panel.w));
        panel.y = y;
        if (panel.y + panel.h > height) {
            panel.y = flip && aboveY - panel.h >= 0 ? aboveY - panel.h : Math.max(0, height - panel.h);
        }
        layoutRows(panel);
        return panel;
    }

    private static void layoutRows(Panel panel) {
        int cy = panel.y + 1 + MENU_PAD;
        for (JXNativeNode row : panel.container.getChildren()) {
            int rh = (int) rowSize(row)[1];
            row.layout(panel.x + 1, cy, panel.w - 2, rh);
            cy += rh;
        }
    }

    // ---- dialog, drawer, snack bar -------------------------------------------------------------------

    /** The size a dialog or drawer wants: the written {@code prefWidth} and {@code prefHeight}, else its content's. */
    private static double[] panelSize(JXNativeNode node) {
        double[] s = JXBoxLayout.containerSizes(node);
        double w = JXPaint.number(node, "prefWidth", s[1]);
        double h = JXPaint.number(node, "prefHeight", s[4]);
        return new double[] {Math.max(w, JXPaint.number(node, "minWidth", 0)), Math.max(h, JXPaint.number(node, "minHeight", 0))};
    }

    private static void placeDialog(JXNativeNode node, int width, int height) {
        double[] want = panelSize(node);
        int w = (int) Math.max(1, Math.min(Math.ceil(want[0]), width - 2 * DIALOG_MARGIN));
        int h = (int) Math.max(1, Math.min(Math.ceil(want[1]), height - 2 * DIALOG_MARGIN));
        node.layout(Math.max(0, (width - w) / 2), Math.max(0, (height - h) / 2), w, h);
    }

    private static void placeDrawer(JXNativeNode node, int width, int height) {
        double[] want = panelSize(node);
        boolean right = "right".equalsIgnoreCase(JXPaint.text(node, "side"));
        double minimum = node.getProperty("prefWidth") instanceof Number ? 1 : DRAWER_MIN;
        int w = (int) Math.max(1, Math.min(Math.ceil(Math.max(want[0], minimum)), Math.round(width * 0.9)));
        node.layout(right ? width - w : 0, 0, w, height);
    }

    private static void placeSnackbar(JXNativeNode node, int width, int height) {
        JXTextEngine engine = JXTextEngine.get(false);
        float size = JXTextEngine.DEFAULT_SIZE;
        String action = JXPaint.text(node, "action");
        double w = 2 * SNACK_PAD + engine.width(JXPaint.text(node, "message"), size)
                + (action.isEmpty() ? 0 : SNACK_GAP + engine.width(action, size));
        int sw = (int) Math.max(1, Math.min(Math.ceil(w), width - 2 * SNACK_MARGIN));
        node.layout(Math.max(0, (width - sw) / 2), Math.max(0, height - SNACK_H - SNACK_MARGIN), sw, SNACK_H);
        if (node.shownAt == 0) {
            node.shownAt = clock.getAsLong();
        }
    }

    /** Where a snack bar's action is: x of its left edge, or -1 when it has none. */
    static int snackbarActionX(JXNativeNode node) {
        String action = JXPaint.text(node, "action");
        if (action.isEmpty()) {
            return -1;
        }
        float actionWidth = JXTextEngine.get(false).width(action, JXTextEngine.DEFAULT_SIZE);
        return (int) (node.getX() + node.getWidth() - SNACK_PAD - actionWidth - SNACK_GAP / 2);
    }

    // ---- hit testing ---------------------------------------------------------------------------------

    /** The path to what is at (x, y) in the topmost open overlay that covers it, or null when none does. */
    static List<JXNativeNode> hitPath(JXNativeNode root, int x, int y) {
        List<Entry> open = collect(root);
        for (int i = open.size() - 1; i >= 0; i--) {
            List<JXNativeNode> path = hit(open.get(i), x, y);
            if (path != null) {
                return path;
            }
        }
        return null;
    }

    private static List<JXNativeNode> hit(Entry entry, int x, int y) {
        JXNativeNode node = entry.node;
        List<JXNativeNode> path = new ArrayList<JXNativeNode>(entry.ancestors);
        switch (node.getType()) {
            case "dialog":
            case "drawer":
                if (node.contains(x, y)) {
                    path.addAll(node.hitInside(x, y));
                } else {
                    path.add(node); // the scrim
                }
                return path;
            case "snackbar":
                if (!node.contains(x, y)) {
                    return null;
                }
                path.add(node);
                return path;
            case "menubutton":
            case "contextmenu":
            case "menubar":
                return hitMenus(entry, path, x, y);
            default:
                Popup po = popup(node);
                if (po == null || !po.contains(x, y)) {
                    return null;
                }
                path.add(node);
                return path;
        }
    }

    private static List<JXNativeNode> hitMenus(Entry entry, List<JXNativeNode> path, int x, int y) {
        Popup po = popup(entry.node);
        if (po == null) {
            return null;
        }
        for (int i = po.panels.size() - 1; i >= 0; i--) {
            Panel panel = po.panels.get(i);
            if (!panel.contains(x, y)) {
                continue;
            }
            path.add(entry.node);
            for (int k = 0; k <= i; k++) {
                if (po.panels.get(k).container != entry.node) {
                    path.add(po.panels.get(k).container);
                }
            }
            List<JXNativeNode> rows = panel.container.getChildren();
            for (int r = rows.size() - 1; r >= 0; r--) {
                if (rows.get(r).contains(x, y)) {
                    path.add(rows.get(r));
                    break;
                }
            }
            return path;
        }
        return null;
    }

    // ---- the pointer ---------------------------------------------------------------------------------

    private static boolean dismissible(JXNativeNode node) {
        return !"dialog".equals(node.getType()) || !Boolean.FALSE.equals(node.getProperty("dismissible"));
    }

    /** Closes an open overlay, and the submenus it has open. */
    static void close(JXNativeNode node) {
        if ("menubar".equals(node.getType())) {
            resetMenus(node);
            return;
        }
        resetMenus(node);
        request(node, false);
    }

    private static void resetMenus(JXNativeNode node) {
        node.openChild = -1;
        if (JXOverlays.overlayContainer(node.getType())) {
            for (JXNativeNode child : node.getChildren()) {
                if ("menu".equals(child.getType())) {
                    resetMenus(child);
                }
            }
        }
    }

    /** See {@link JXNativeNode#routePointer}. */
    static JXNativeNode pointer(JXNativeNode root, JXPointerEvent event) {
        int x = event.getX();
        int y = event.getY();
        boolean secondary = event.getButton() == JXInputEvent.BUTTON_SECONDARY;
        List<Entry> open = collect(root);
        Entry top = null;
        for (int i = open.size() - 1; i >= 0; i--) {
            Entry entry = open.get(i);
            if ("snackbar".equals(entry.node.getType())) {
                List<JXNativeNode> path = hit(entry, x, y); // a snack bar takes the presses on it, and only those
                if (path != null) {
                    return secondary ? null : deliver(entry, path, event);
                }
            } else if (top == null) {
                top = entry;
            }
        }
        if (top != null) {
            List<JXNativeNode> path = hit(top, x, y);
            if (path != null) {
                if (modal(top.node.getType()) && !top.node.contains(x, y)) { // the scrim: outside the panel
                    if (dismissible(top.node)) {
                        request(top.node, false);
                    }
                    return null;
                }
                return secondary ? null : deliver(top, path, event);
            }
            if (!hostContains(top.node, x, y)) { // the host gets the press below: it toggles
                close(top.node);
                return null;
            }
        }
        if (secondary) {
            JXNativeNode menu = contextMenuAt(root, x, y);
            if (menu != null) {
                menu.overlayX = x;
                menu.overlayY = y;
                menu.openChild = -1;
                request(menu, true);
            }
            return null;
        }
        List<JXNativeNode> path = root.hitPlain(x, y);
        if (path.isEmpty()) {
            return null;
        }
        JXNativeNode node = path.get(path.size() - 1);
        node.dispatchPointer(event);
        return node;
    }

    private static boolean hostContains(JXNativeNode node, int x, int y) {
        return (popupHost(node.getType()) || "menubutton".equals(node.getType()) || "menubar".equals(node.getType()))
                && node.contains(x, y);
    }

    private static JXNativeNode deliver(Entry entry, List<JXNativeNode> path, JXPointerEvent event) {
        JXNativeNode node = path.get(path.size() - 1);
        String kind = node.getType();
        if ("menuitem".equals(kind)) {
            boolean acts = !separator(node) && !Boolean.TRUE.equals(node.getProperty("disabled"));
            node.dispatchPointer(event);
            if (acts) {
                close(entry.node);
            }
        } else if ("menu".equals(kind)) {
            if (path.size() >= 2 && !Boolean.TRUE.equals(node.getProperty("disabled"))) {
                JXNativeNode parent = path.get(path.size() - 2);
                int index = parent.getChildren().indexOf(node);
                parent.openChild = parent.openChild == index ? -1 : index;
            }
        } else {
            node.dispatchPointer(event);
        }
        return node;
    }

    /** The context menu whose parent is the deepest node under the pointer. */
    private static JXNativeNode contextMenuAt(JXNativeNode root, int x, int y) {
        JXNativeNode[] best = new JXNativeNode[1];
        int[] bestDepth = {-1};
        findContextMenu(root, x, y, 0, best, bestDepth);
        return best[0];
    }

    private static void findContextMenu(JXNativeNode parent, int x, int y, int depth, JXNativeNode[] best, int[] bestDepth) {
        if (Boolean.TRUE.equals(parent.getProperty("hidden")) || !parent.contains(x, y) && Boolean.TRUE.equals(parent.getProperty("clip"))) {
            return;
        }
        boolean inside = parent.contains(x, y);
        for (JXNativeNode child : parent.getChildren()) {
            if ("contextmenu".equals(child.getType())) {
                if (inside && depth >= bestDepth[0] && !Boolean.TRUE.equals(child.getProperty("disabled"))) {
                    best[0] = child; // the parent that is deepest in the tree owns it
                    bestDepth[0] = depth;
                }
            } else if (!overlayContainer(child.getType())) {
                findContextMenu(child, x, y, depth + 1, best, bestDepth);
            }
        }
    }

    /**
     * A press that reached a node itself (see {@code JXNativeNode.dispatchPointer}): a popup host opens or closes, or
     * picks in its popup; a menu button toggles its menu; a menu bar's title opens its menu; a snack bar's action runs.
     * True when that was all the press did.
     */
    static boolean hostPointer(JXNativeNode node, JXPointerEvent event) {
        String type = node.getType();
        int x = event.getX();
        int y = event.getY();
        switch (type) {
            case "select":
            case "datepicker":
            case "timepicker":
            case "colorpicker": {
                if (!canOpen(node)) {
                    return false; // a select or date picker of the JavaFX bridge: its popup is another element
                }
                Popup po = isOpen(node) ? popup(node) : null;
                if (po != null && po.contains(x, y)) {
                    pick(node, po, x, y);
                } else {
                    request(node, !isOpen(node));
                }
                return true;
            }
            case "menubutton":
                request(node, !isOpen(node));
                return true;
            case "menubar": {
                int hit = -1;
                for (int i = 0; i < node.getChildren().size(); i++) {
                    if (node.getChildren().get(i).contains(x, y)) {
                        hit = i;
                    }
                }
                boolean disabled = hit >= 0 && Boolean.TRUE.equals(node.getChildren().get(hit).getProperty("disabled"));
                if (hit >= 0 && disabled) {
                    return true;
                }
                int was = node.openChild;
                resetMenus(node);
                node.openChild = hit >= 0 && hit != was ? hit : -1; // a second press on the open title closes its menu
                return true;
            }
            case "snackbar": {
                int at = snackbarActionX(node);
                if (at >= 0 && x >= at) {
                    Object action = node.getProperty("onAction");
                    if (action instanceof Runnable) {
                        ((Runnable) action).run();
                    }
                    request(node, false);
                }
                return true;
            }
            case "menuitem":
                return separator(node); // a separator is not pressed; an item runs its onAction
            case "menu":
                return true; // a submenu entry or a title acts through its menu: JXOverlays.pointer
            default:
                return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static void pick(JXNativeNode host, Popup po, int x, int y) {
        Object handler = host.getProperty("onChange");
        Consumer<Object> onChange = handler instanceof Consumer ? (Consumer<Object>) handler : null;
        switch (host.getType()) {
            case "datepicker": {
                int[] hit = JXCalendar.hit(po.calendar, x, y);
                if (hit[0] == JXCalendar.PREVIOUS || hit[0] == JXCalendar.NEXT) {
                    int month = host.viewYear * 12 + (host.viewMonth - 1) + (hit[0] == JXCalendar.NEXT ? 1 : -1);
                    host.viewYear = Math.floorDiv(month, 12);
                    host.viewMonth = Math.floorMod(month, 12) + 1;
                    po.calendar = null; // drawn from the next layout
                    host.overlayState = datePopup(host, po.windowW, po.windowH);
                } else if (hit[0] == JXCalendar.DAY) {
                    if (onChange != null) {
                        onChange.accept(LocalDate.of(hit[1], hit[2], hit[3]).toString());
                    }
                    request(host, false);
                }
                return;
            }
            case "select": {
                for (int[] cell : po.cells) {
                    if (x >= cell[0] && y >= cell[1] && x < cell[0] + cell[2] && y < cell[1] + cell[3]) {
                        String[] options = JXPaint.strings(host.getProperty("options"));
                        if (onChange != null && cell[5] < options.length) {
                            onChange.accept(options[cell[5]]);
                        }
                        request(host, false);
                        return;
                    }
                }
                return;
            }
            case "timepicker": {
                for (int[] cell : po.cells) {
                    if (x >= cell[0] && y >= cell[1] && x < cell[0] + cell[2] && y < cell[1] + cell[3]) {
                        if (onChange != null) {
                            onChange.accept(timeAfter(host, cell));
                        }
                        return;
                    }
                }
                return;
            }
            default: { // colorpicker
                for (int[] cell : po.cells) {
                    if (x >= cell[0] && y >= cell[1] && x < cell[0] + cell[2] && y < cell[1] + cell[3]) {
                        String color = hex(cell[5]);
                        host.hexDraft = color;
                        if (onChange != null) {
                            onChange.accept(color);
                        }
                        return;
                    }
                }
            }
        }
    }

    // ---- the highlighted row: pointer and keys -----------------------------------------------------

    /** A row of a menu the pointer or the keys can reach: not a separator, not disabled. */
    static boolean selectable(JXNativeNode row) {
        return !separator(row) && !Boolean.TRUE.equals(row.getProperty("disabled")) && !Boolean.TRUE.equals(row.getProperty("hidden"));
    }

    /** The topmost open overlay that is not a snack bar (which is not modal), or null. */
    private static Entry topEntry(JXNativeNode root) {
        List<Entry> open = collect(root);
        for (int i = open.size() - 1; i >= 0; i--) {
            if (!"snackbar".equals(open.get(i).node.getType())) {
                return open.get(i);
            }
        }
        return null;
    }

    private static int cellAt(Popup po, int x, int y) {
        for (int i = 0; i < po.cells.length; i++) {
            int[] c = po.cells[i];
            if (x >= c[0] && y >= c[1] && x < c[0] + c[2] && y < c[1] + c[3]) {
                return i;
            }
        }
        return -1;
    }

    private static JXNativeNode menuRowAt(Popup po, int x, int y) {
        for (int i = po.panels.size() - 1; i >= 0; i--) {
            Panel panel = po.panels.get(i);
            if (panel.contains(x, y)) {
                for (JXNativeNode row : panel.container.getChildren()) {
                    if (row.contains(x, y)) {
                        return selectable(row) ? row : null;
                    }
                }
                return null;
            }
        }
        return null;
    }

    /** Places the open overlays again, in the window they were placed in: what a change of the highlight moves. */
    private static void refresh(JXNativeNode root, JXNativeNode node) {
        Popup po = popup(node);
        if (po != null) {
            layout(root, po.windowW, po.windowH);
        }
    }

    /**
     * See {@link JXNativeNode#routeHover}: the row under the pointer in the topmost open popup or menu becomes the
     * highlighted one ({@code node.hot}); a pointer that is over no row clears a highlight it made, not one the keys made.
     */
    static boolean hover(JXNativeNode root, int x, int y) {
        Entry top = topEntry(root);
        Popup po = top == null ? null : popup(top.node);
        if (po == null) {
            return false;
        }
        JXNativeNode node = top.node;
        Object now = null;
        switch (node.getType()) {
            case "menubutton":
            case "contextmenu":
            case "menubar":
                now = menuRowAt(po, x, y);
                break;
            case "select": {
                int i = cellAt(po, x, y);
                now = i < 0 ? null : Integer.valueOf(po.cells[i][5]);
                break;
            }
            case "timepicker":
            case "colorpicker": {
                int i = cellAt(po, x, y);
                now = i < 0 ? null : Integer.valueOf(i);
                break;
            }
            case "datepicker":
                if (po.calendar != null && po.contains(x, y)) {
                    int[] hit = JXCalendar.hit(po.calendar, x, y);
                    now = hit[0] == JXCalendar.DAY ? LocalDate.of(hit[1], hit[2], hit[3]) : null;
                }
                break;
            default:
                return false;
        }
        if (now == null ? node.hot == null || node.hotKey : now.equals(node.hot) && !node.hotKey) {
            return false;
        }
        node.hot = now;
        node.hotKey = false;
        refresh(root, node);
        return true;
    }

    // ---- keys ----------------------------------------------------------------------------------------

    private static final int RIGHT = 262;
    private static final int LEFT = 263;
    private static final int DOWN = 264;
    private static final int UP = 265;
    private static final int PAGE_UP = 266;
    private static final int PAGE_DOWN = 267;
    private static final int HOME = 268;
    private static final int END = 269;

    @SuppressWarnings("unchecked")
    private static Consumer<Object> changeHandler(JXNativeNode node) {
        Object handler = node.getProperty("onChange");
        return handler instanceof Consumer ? (Consumer<Object>) handler : null;
    }

    private static void highlight(JXNativeNode root, JXNativeNode node, Object row) {
        node.hot = row;
        node.hotKey = true;
        refresh(root, node);
    }

    /**
     * Keys in the topmost open popup or menu: Up and Down move the highlighted row (Page Up and Page Down by a page,
     * Home and End to the first and the last), Enter picks it, Left and Right move in a grid, open and close the
     * submenus of a menu and move along the titles of a menu bar. True when the key was used here.
     */
    private static boolean navigate(JXNativeNode root, JXNativeNode node, int code) {
        Popup po = popup(node);
        if (po == null) { // opened since the last layout (a press and a key before a frame): place it, as a frame would
            layout(root, root.getWidth(), root.getHeight());
            po = popup(node);
        }
        if (po == null) {
            return false;
        }
        switch (node.getType()) {
            case "menubutton":
            case "contextmenu":
            case "menubar":
                return navigateMenu(root, node, po, code);
            case "select":
                return navigateList(root, node, code);
            case "timepicker":
            case "colorpicker":
                return navigateGrid(root, node, po, code);
            case "datepicker":
                return navigateDays(root, node, code);
            default:
                return false;
        }
    }

    private static boolean navigateList(JXNativeNode root, JXNativeNode node, int code) {
        String[] options = JXPaint.strings(node.getProperty("options"));
        int n = options.length;
        if (n == 0) {
            return false;
        }
        boolean has = node.hot instanceof Integer;
        int cur = has ? ((Integer) node.hot).intValue() : Arrays.asList(options).indexOf(JXPaint.text(node, "value"));
        int next;
        switch (code) {
            case ENTER: {
                if (!has) {
                    return false;
                }
                Consumer<Object> onChange = changeHandler(node);
                if (onChange != null && cur >= 0 && cur < n) {
                    onChange.accept(options[cur]);
                }
                request(node, false);
                return true;
            }
            case DOWN:
                next = cur < 0 ? 0 : Math.min(n - 1, cur + 1);
                break;
            case UP:
                next = cur < 0 ? n - 1 : Math.max(0, cur - 1);
                break;
            case PAGE_DOWN:
                next = cur < 0 ? 0 : Math.min(n - 1, cur + SELECT_ROWS);
                break;
            case PAGE_UP:
                next = cur < 0 ? n - 1 : Math.max(0, cur - SELECT_ROWS);
                break;
            case HOME:
                next = 0;
                break;
            case END:
                next = n - 1;
                break;
            default:
                return false; // Left and Right belong to an editable select's text
        }
        highlight(root, node, Integer.valueOf(next));
        return true;
    }

    private static boolean navigateGrid(JXNativeNode root, JXNativeNode node, Popup po, int code) {
        int n = po.cells.length;
        if (n == 0) {
            return false;
        }
        boolean has = node.hot instanceof Integer;
        int cur = has ? ((Integer) node.hot).intValue() : -1;
        if (!has) { // the first key starts from the cell that is the value now, if the grid has it
            for (int i = 0; i < n && cur < 0; i++) {
                if ("timepicker".equals(node.getType()) ? timeCurrent(node, po.cells[i])
                        : po.cells[i][5] == colorOf(JXPaint.text(node, "value")) && colorOf(JXPaint.text(node, "value")) != 0) {
                    cur = i;
                }
            }
        }
        int next;
        switch (code) {
            case ENTER:
                if (!has) {
                    return false;
                }
                int[] cell = po.cells[Math.min(cur, n - 1)];
                pick(node, po, cell[0] + cell[2] / 2, cell[1] + cell[3] / 2);
                return true;
            case LEFT:
                next = cur < 0 ? 0 : Math.max(0, cur - 1);
                break;
            case RIGHT:
                next = cur < 0 ? 0 : Math.min(n - 1, cur + 1);
                break;
            case UP:
            case DOWN:
                next = cur < 0 ? (code == DOWN ? 0 : n - 1) : neighbour(po, cur, code == DOWN ? 1 : -1);
                break;
            case HOME:
                next = 0;
                break;
            case END:
                next = n - 1;
                break;
            default:
                return false;
        }
        highlight(root, node, Integer.valueOf(next));
        return true;
    }

    /** The cell nearest above ({@code dir} -1) or below (1) the cell {@code cur}: the next row, the closest column. */
    private static int neighbour(Popup po, int cur, int dir) {
        int[] c = po.cells[cur];
        int cx = c[0] + c[2] / 2;
        int cy = c[1] + c[3] / 2;
        int best = cur;
        int bestDy = Integer.MAX_VALUE;
        int bestDx = Integer.MAX_VALUE;
        for (int i = 0; i < po.cells.length; i++) {
            int[] d = po.cells[i];
            int dy = (d[1] + d[3] / 2 - cy) * dir;
            if (dy <= 0) {
                continue;
            }
            int dx = Math.abs(d[0] + d[2] / 2 - cx);
            if (dy < bestDy || dy == bestDy && dx < bestDx) {
                best = i;
                bestDy = dy;
                bestDx = dx;
            }
        }
        return best;
    }

    private static boolean navigateDays(JXNativeNode root, JXNativeNode node, int code) {
        boolean has = node.hot instanceof LocalDate;
        LocalDate cur = has ? (LocalDate) node.hot : parseDate(JXPaint.text(node, "value"));
        if (cur == null) {
            cur = today.get();
        }
        LocalDate next;
        switch (code) {
            case ENTER: {
                if (!has) {
                    return false;
                }
                Consumer<Object> onChange = changeHandler(node);
                if (onChange != null) {
                    onChange.accept(cur.toString());
                }
                request(node, false);
                return true;
            }
            case LEFT:
                next = cur.minusDays(1);
                break;
            case RIGHT:
                next = cur.plusDays(1);
                break;
            case UP:
                next = cur.minusWeeks(1);
                break;
            case DOWN:
                next = cur.plusWeeks(1);
                break;
            case PAGE_UP:
                next = cur.minusMonths(1);
                break;
            case PAGE_DOWN:
                next = cur.plusMonths(1);
                break;
            case HOME:
                next = cur.withDayOfMonth(1);
                break;
            case END:
                next = cur.withDayOfMonth(cur.lengthOfMonth());
                break;
            default:
                return false;
        }
        node.viewYear = next.getYear(); // the calendar shows the month of the day the keys reached
        node.viewMonth = next.getMonthValue();
        highlight(root, node, next);
        return true;
    }

    private static JXNativeNode firstSelectable(List<JXNativeNode> rows) {
        for (JXNativeNode row : rows) {
            if (selectable(row)) {
                return row;
            }
        }
        return null;
    }

    private static boolean navigateMenu(JXNativeNode root, JXNativeNode node, Popup po, int code) {
        JXNativeNode hot = node.hot instanceof JXNativeNode ? (JXNativeNode) node.hot : null;
        int active = po.panels.size() - 1; // the panel the keys work in: the one with the highlighted row, else the innermost
        if (hot != null) {
            int found = -1;
            for (int k = 0; k < po.panels.size(); k++) {
                if (po.panels.get(k).container.getChildren().contains(hot)) {
                    found = k;
                }
            }
            if (found < 0) {
                hot = null;
            } else {
                active = found;
            }
        }
        Panel panel = po.panels.get(active);
        List<JXNativeNode> rows = panel.container.getChildren();
        int n = rows.size();
        JXNativeNode next = null;
        switch (code) {
            case DOWN:
            case UP: {
                int at = hot == null ? (code == DOWN ? -1 : n) : rows.indexOf(hot);
                for (int s = 1; s <= n && next == null; s++) {
                    JXNativeNode row = rows.get(Math.floorMod(at + (code == DOWN ? s : -s), n));
                    next = selectable(row) ? row : null;
                }
                break;
            }
            case HOME:
                next = firstSelectable(rows);
                break;
            case END:
                for (int i = n - 1; i >= 0 && next == null; i--) {
                    next = selectable(rows.get(i)) ? rows.get(i) : null;
                }
                break;
            case RIGHT:
            case ENTER:
                if (hot != null && "menu".equals(hot.getType()) && selectable(hot) && !hot.getChildren().isEmpty()) {
                    panel.container.openChild = rows.indexOf(hot);
                    refresh(root, node);
                    JXNativeNode inner = firstSelectable(hot.getChildren());
                    highlight(root, node, inner != null ? inner : hot);
                    return true;
                }
                if (code == ENTER) {
                    if (hot == null) {
                        return false;
                    }
                    if (selectable(hot)) {
                        hot.dispatchPointer(new JXPointerEvent(hot.getX() + hot.getWidth() / 2, hot.getY() + hot.getHeight() / 2,
                                JXInputEvent.BUTTON_PRIMARY));
                        close(node);
                    }
                    return true;
                }
                if ("menubar".equals(node.getType())) {
                    moveHeader(root, node, 1);
                }
                return true;
            case LEFT:
                if (active > 0) { // closes the submenu: the row that opened it is the highlighted one again
                    po.panels.get(active - 1).container.openChild = -1;
                    JXNativeNode opener = panel.container;
                    refresh(root, node);
                    highlight(root, node, opener);
                } else if ("menubar".equals(node.getType())) {
                    moveHeader(root, node, -1);
                }
                return true;
            default:
                return false;
        }
        if (next != null) {
            if (active < po.panels.size() - 1 && panel.container.openChild != rows.indexOf(next)) {
                panel.container.openChild = -1; // a row of an outer panel: the submenus beyond it close
            }
            highlight(root, node, next);
        }
        return true;
    }

    /** Opens the menu of the next ({@code dir} 1) or previous title of a menu bar that is not disabled. */
    private static void moveHeader(JXNativeNode root, JXNativeNode bar, int dir) {
        List<JXNativeNode> titles = bar.getChildren();
        int n = titles.size();
        for (int s = 1; s <= n; s++) {
            int j = Math.floorMod(bar.openChild + dir * s, n);
            if (!Boolean.TRUE.equals(titles.get(j).getProperty("disabled"))) {
                resetMenus(bar);
                bar.openChild = j;
                refresh(root, bar);
                JXNativeNode first = firstSelectable(titles.get(j).getChildren());
                highlight(root, bar, first);
                return;
            }
        }
    }

    /** See {@link JXNativeNode#routeKey}. */
    static void key(JXNativeNode root, JXKeyEvent event, JXNativeNode focused) {
        Entry top = topEntry(root);
        if (top == null) {
            List<Entry> open = collect(root); // only snack bars: Escape still closes the last
            top = open.isEmpty() ? null : open.get(open.size() - 1);
        }
        if (top != null) {
            int code = event.getKeyCode();
            if (code == ESCAPE) {
                close(top.node);
                return;
            }
            if ((code >= RIGHT && code <= END || code == ENTER) && navigate(root, top.node, code)) {
                return;
            }
            if (code == ENTER) {
                if ("dialog".equals(top.node.getType()) && runDefaultButton(top.node)) {
                    return;
                }
                if (popupHost(top.node.getType()) && focused == top.node) {
                    request(top.node, false);
                    return;
                }
            }
        }
        if (focused != null) {
            focused.dispatchKey(event);
        }
    }

    private static boolean runDefaultButton(JXNativeNode node) {
        if ("button".equals(node.getType()) && Boolean.TRUE.equals(node.getProperty("defaultButton"))
                && !Boolean.TRUE.equals(node.getProperty("disabled")) && !Boolean.TRUE.equals(node.getProperty("hidden"))) {
            Object action = node.getProperty("onAction");
            if (action instanceof Runnable) {
                ((Runnable) action).run();
                return true;
            }
        }
        for (JXNativeNode child : node.getChildren()) {
            if (runDefaultButton(child)) {
                return true;
            }
        }
        return false;
    }

    // ---- time ----------------------------------------------------------------------------------------

    /** See {@link JXNativeNode#tickOverlays(long)}. */
    static long tick(JXNativeNode root, long now) {
        long next = -1;
        for (Entry entry : collect(root)) {
            JXNativeNode node = entry.node;
            if (!"snackbar".equals(node.getType())) {
                continue;
            }
            double duration = JXPaint.number(node, "duration", 0);
            if (!(duration > 0)) {
                continue;
            }
            if (node.shownAt == 0) {
                node.shownAt = now;
            }
            long left = node.shownAt + (long) duration - now;
            if (left <= 0) {
                node.shownAt = 0; // asked once; a snack bar the application keeps showing waits another duration
                request(node, false);
            } else if (next < 0 || left < next) {
                next = left;
            }
        }
        return next;
    }
}
