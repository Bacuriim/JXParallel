package com.jxparallel.ui.native2d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * Selection of the rows of a {@code list}, {@code table} (and so a tree, which is one of them) handled by the renderer,
 * while the application keeps the state: the list reports what the user selected through {@code onSelect} and the
 * application renders again with the new props, as with every input.
 *
 * <p>Props of the list or table: {@code selected}, the index of the selected row (-1 for none); with
 * {@code selectionMode} "multiple", {@code selection}, the indices of the selected rows (an {@code int[]},
 * {@code double[]}, {@code Number[]} or a {@code Collection} of numbers); {@code onSelect}, a {@code Consumer} that gets
 * the {@code Integer} index of the row selected (in multiple mode the row last clicked or moved to, -1 when nothing is
 * selected any more); {@code onSelection}, in multiple mode, gets the sorted indices as a {@code double[]}. Indices count
 * the rows of the whole model, so a row outside the window of a virtual list can be selected; a tree counts its visible
 * rows. A list or table has the feature as soon as it has one of these props, and a list without any is as before.
 *
 * <p>Gestures: a click selects the row (a click on the arrow of a tree row toggles it, a button inside a row acts as
 * itself); in multiple mode Ctrl+click toggles it and Shift+click selects the range from the last row clicked. Enter or a
 * double click runs the {@code onAction} of the row (of its table row, of the cell under the pointer, or of its first
 * cell that has one): a single click only selects. With the list focused (it takes the focus with a click or Tab):
 * Up, Down, Home, End, Page Up and Page Down move the selection and scroll it into view, with Shift they extend it, and
 * Ctrl+A selects all in multiple mode.
 *
 * <p>The rows are painted selected (with the selection colour of the look, the grey one when the list is not focused) by
 * {@link JXPaint} from these props, whatever the {@code selected} prop of a row says.
 */
final class JXSelection {
    private static final int ENTER = 257;
    private static final int UP = 265;
    private static final int DOWN = 264;
    private static final int PAGE_UP = 266;
    private static final int PAGE_DOWN = 267;
    private static final int HOME = 268;
    private static final int END = 269;
    private static final int KEY_A = 65;

    private JXSelection() {
    }

    /** Whether the renderer handles the selection of this node: a list or table with a selection prop. */
    static boolean enabled(JXNativeNode node) {
        String type = node.getType();
        if (!"list".equals(type) && !"table".equals(type)) {
            return false;
        }
        java.util.Map<String, Object> props = node.getProps();
        return props.get("selected") instanceof Number || props.get("selection") != null || props.get("selectionMode") != null
                || props.get("onSelect") instanceof Consumer || props.get("onSelection") instanceof Consumer;
    }

    static boolean multiple(JXNativeNode list) {
        return "multiple".equalsIgnoreCase(String.valueOf(list.getProperty("selectionMode")));
    }

    private static int first(JXNativeNode list) {
        return Math.max(0, (int) JXPaint.number(list, "first", 0));
    }

    /** How many rows the model has: its {@code itemCount}, or the rows given when it does not say. */
    static int count(JXNativeNode list) {
        int count = (int) JXPaint.number(list, "itemCount", 0);
        return count > 0 ? count : first(list) + list.getChildren().size();
    }

    /** The sorted indices of the selected rows, from {@code selected} or, in multiple mode, {@code selection}. */
    static int[] selected(JXNativeNode list) {
        int[] out;
        Object selection = list.getProperty("selection");
        if (multiple(list) && selection != null) {
            out = indices(selection);
        } else {
            int one = (int) JXPaint.number(list, "selected", -1);
            out = one >= 0 ? new int[] {one} : new int[0];
        }
        return out;
    }

    static boolean isSelected(JXNativeNode list, int index) {
        return Arrays.binarySearch(selected(list), index) >= 0;
    }

    /** Sorted distinct non-negative indices of a prop value. */
    static int[] indices(Object value) {
        List<Integer> out = new ArrayList<Integer>();
        if (value instanceof int[]) {
            for (int v : (int[]) value) {
                out.add(Integer.valueOf(v));
            }
        } else if (value instanceof double[]) {
            for (double v : (double[]) value) {
                out.add(Integer.valueOf((int) v));
            }
        } else if (value instanceof long[]) {
            for (long v : (long[]) value) {
                out.add(Integer.valueOf((int) v));
            }
        } else if (value instanceof Object[]) {
            for (Object v : (Object[]) value) {
                if (v instanceof Number) {
                    out.add(Integer.valueOf(((Number) v).intValue()));
                }
            }
        } else if (value instanceof Collection) {
            for (Object v : (Collection<?>) value) {
                if (v instanceof Number) {
                    out.add(Integer.valueOf(((Number) v).intValue()));
                }
            }
        } else if (value instanceof Number) {
            out.add(Integer.valueOf(((Number) value).intValue()));
        }
        int[] sorted = new int[out.size()];
        int n = 0;
        for (Integer v : out) {
            if (v.intValue() >= 0) {
                sorted[n++] = v.intValue();
            }
        }
        sorted = Arrays.copyOf(sorted, n);
        Arrays.sort(sorted);
        int distinct = 0;
        for (int i = 0; i < sorted.length; i++) {
            if (i == 0 || sorted[i] != sorted[i - 1]) {
                sorted[distinct++] = sorted[i];
            }
        }
        return Arrays.copyOf(sorted, distinct);
    }

    /** How {@link JXPaint} draws the row at {@code index}: 1 plain, 2 selected in a list that is not focused, 3 selected and focused. */
    static int paintState(JXNativeNode list, int index, int[] selected) {
        if (Arrays.binarySearch(selected, index) < 0) {
            return 1;
        }
        return Boolean.TRUE.equals(list.getProperty("focused")) ? 3 : 2;
    }

    // ---- the pointer -----------------------------------------------------------------------------

    /**
     * A primary press on a row of a list whose selection the renderer handles. Returns the list (it takes the focus, and
     * the press goes no further), or null when the press is not for it: not on a row, or on a control inside the row.
     */
    static JXNativeNode press(List<JXNativeNode> path, JXPointerEvent event) {
        JXNativeNode sorted = JXTableSort.press(path, event); // a column header of a sortable table
        if (sorted != null) {
            return sorted;
        }
        for (int li = path.size() - 2; li >= 0; li--) {
            JXNativeNode list = path.get(li);
            if (!enabled(list)) {
                continue;
            }
            if (Boolean.TRUE.equals(list.getProperty("disabled"))) {
                return null;
            }
            JXNativeNode row = path.get(li + 1);
            JXNativeNode node = path.get(path.size() - 1);
            int position = list.getChildren().indexOf(row);
            if (position < 0) {
                return null;
            }
            if (node != row && JXNativeNode.takesFocus(node.getType())) {
                return null; // a button, field or other control inside the row acts as itself
            }
            if (JXChangeEvents.disclosure(node, event)) {
                return list; // the arrow of a tree row expands or collapses it
            }
            if (Boolean.TRUE.equals(row.getProperty("disabled"))) {
                return list;
            }
            int index = first(list) + position;
            select(list, index, event);
            if (event.getClickCount() >= 2) {
                activate(row, node, event);
            }
            return list;
        }
        return null;
    }

    private static void select(JXNativeNode list, int index, JXPointerEvent event) {
        int[] now = selected(list);
        if (!multiple(list)) {
            list.selAnchor = index;
            list.selCursor = index;
            report(list, now, new int[] {index}, index);
            return;
        }
        int[] next;
        if (event.isShiftDown()) {
            int from = list.selAnchor >= 0 ? list.selAnchor : cursor(list, now) >= 0 ? cursor(list, now) : index;
            int[] range = range(from, index);
            next = event.isShortcutDown() ? union(now, range) : range; // Ctrl+Shift adds the range to what is selected
            list.selAnchor = from;
        } else if (event.isShortcutDown()) {
            next = Arrays.binarySearch(now, index) >= 0 ? without(now, index) : union(now, new int[] {index});
            list.selAnchor = index;
        } else {
            next = new int[] {index};
            list.selAnchor = index;
        }
        list.selCursor = index;
        report(list, now, next, index);
    }

    private static int[] range(int a, int b) {
        int low = Math.min(a, b);
        int[] out = new int[Math.abs(a - b) + 1];
        for (int i = 0; i < out.length; i++) {
            out[i] = low + i;
        }
        return out;
    }

    private static int[] union(int[] a, int[] b) {
        int[] all = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, all, a.length, b.length);
        return indices(all);
    }

    private static int[] without(int[] a, int index) {
        int[] out = new int[a.length];
        int n = 0;
        for (int v : a) {
            if (v != index) {
                out[n++] = v;
            }
        }
        return Arrays.copyOf(out, n);
    }

    /** Tells the application what the user selected, if it is not what the list shows already. */
    @SuppressWarnings("unchecked")
    private static void report(JXNativeNode list, int[] now, int[] next, int lead) {
        if (Arrays.equals(now, next)) {
            return;
        }
        if (multiple(list)) {
            Object onSelection = list.getProperty("onSelection");
            if (onSelection instanceof Consumer) {
                double[] out = new double[next.length];
                for (int i = 0; i < out.length; i++) {
                    out[i] = next[i];
                }
                ((Consumer<Object>) onSelection).accept(out);
            }
        }
        Object onSelect = list.getProperty("onSelect");
        if (onSelect instanceof Consumer) {
            int reported = next.length == 0 ? -1 : multiple(list) ? lead : next[0];
            ((Consumer<Object>) onSelect).accept(Integer.valueOf(reported));
        }
    }

    /** The row the arrow keys move from: the last one clicked or moved to if it is selected, else the last selected. */
    private static int cursor(JXNativeNode list, int[] selected) {
        if (selected.length == 0) {
            return -1;
        }
        if (list.selCursor >= 0 && Arrays.binarySearch(selected, list.selCursor) >= 0) {
            return list.selCursor;
        }
        return selected[selected.length - 1];
    }

    /** Runs the action of a row: its own, else the one of the node pressed, else the first of its cells that has one. */
    private static boolean activate(JXNativeNode row, JXNativeNode under, JXPointerEvent event) {
        if (row.runAction(event)) {
            return true;
        }
        if (under != row && under.runAction(event)) {
            return true;
        }
        for (JXNativeNode cell : row.getChildren()) {
            if (cell.runAction(event)) {
                return true;
            }
        }
        return false;
    }

    // ---- the keys --------------------------------------------------------------------------------

    /** The list has the focus and the key is one it takes: moves the selection, or runs the action of the selected row. */
    static boolean key(JXNativeNode root, JXNativeNode focused, JXKeyEvent event) {
        // Alt, and Meta when it is not the shortcut key, are not the list's; on macOS Meta is the shortcut key (Cmd+A)
        int foreign = (JXInputEvent.ALT | JXInputEvent.META) & ~JXInputEvent.SHORTCUT;
        if (!enabled(focused) || Boolean.TRUE.equals(focused.getProperty("disabled"))
                || (event.getModifiers() & foreign) != 0) {
            return false;
        }
        int code = event.getKeyCode();
        int n = count(focused);
        int[] now = selected(focused);
        boolean multiple = multiple(focused);
        int lead = cursor(focused, now);
        if (code == ENTER) {
            int at = lead - first(focused);
            if (lead < 0 || at < 0 || at >= focused.getChildren().size()) {
                return false;
            }
            JXNativeNode row = focused.getChildren().get(at);
            return activate(row, row, new JXPointerEvent(row.getX() + row.getWidth() / 2, row.getY() + row.getHeight() / 2, 0));
        }
        if (code == KEY_A && event.isShortcutDown()) {
            if (!multiple) {
                return false;
            }
            focused.selAnchor = 0;
            focused.selCursor = n - 1;
            report(focused, now, range(0, n - 1), n - 1);
            return true;
        }
        int target;
        switch (code) {
            case UP:
                target = lead < 0 ? n - 1 : lead - 1;
                break;
            case DOWN:
                target = lead < 0 ? 0 : lead + 1;
                break;
            case HOME:
                target = 0;
                break;
            case END:
                target = n - 1;
                break;
            case PAGE_UP:
                target = (lead < 0 ? n : lead) - page(focused);
                break;
            case PAGE_DOWN:
                target = (lead < 0 ? -1 : lead) + page(focused);
                break;
            default:
                return false;
        }
        if (n == 0) {
            return true;
        }
        target = Math.max(0, Math.min(n - 1, target));
        int[] next;
        if (multiple && event.isShiftDown()) {
            int from = focused.selAnchor >= 0 ? focused.selAnchor : lead >= 0 ? lead : target;
            next = range(from, target);
            focused.selAnchor = from;
        } else {
            next = new int[] {target};
            focused.selAnchor = target;
        }
        focused.selCursor = target;
        report(focused, now, next, target);
        reveal(root, focused, target);
        return true;
    }

    /** How many rows a Page Up or Page Down moves: the rows the viewport shows, at least one. */
    private static int page(JXNativeNode list) {
        double item = itemHeight(list);
        return Math.max(1, (int) Math.floor(JXControlLayout.scrollGeometry(list).viewH / item));
    }

    private static double itemHeight(JXNativeNode list) {
        return "table".equals(list.getType()) ? JXControlLayout.rowHeight(list) : JXControlLayout.cellHeight(list);
    }

    /** Scrolls the list the least that shows the row at {@code index}. */
    static void reveal(JXNativeNode root, JXNativeNode list, int index) {
        List<JXNativeNode> path = pathTo(root, list, new ArrayList<JXNativeNode>());
        if (path == null) {
            return;
        }
        JXControlLayout.ScrollGeometry g = JXControlLayout.scrollGeometry(list);
        double item = itemHeight(list);
        double top = index * item;
        double y = g.scrollY;
        if (top < g.scrollY) {
            y = top;
        } else if (top + item > g.scrollY + g.viewH) {
            y = top + item - g.viewH;
        }
        if (y != g.scrollY) {
            JXScrolling.scrollTo(path, path.size() - 1, g.scrollX, y);
        }
    }

    private static List<JXNativeNode> pathTo(JXNativeNode node, JXNativeNode target, List<JXNativeNode> path) {
        path.add(node);
        if (node == target) {
            return path;
        }
        for (JXNativeNode child : node.getChildren()) {
            if (pathTo(child, target, path) != null) {
                return path;
            }
        }
        path.remove(path.size() - 1);
        return null;
    }
}
