package com.jxparallel.ui.native2d;

import java.util.List;
import java.util.function.Consumer;

/**
 * Sortable columns of a {@code table}, controlled like the rest while the application keeps the state and sorts its
 * own data: the table shows {@code sortColumn} (the index of the column, -1 for none) and {@code sortAscending} (a
 * Boolean, true when absent) with an arrow in that column's header, and a press on a header reports the next state
 * as JavaFX cycles it: ascending, then descending, then unsorted (a press on another column starts it ascending).
 *
 * <p>The report is {@code onSortColumn} (the {@code Integer} column, -1 for none), {@code onSortAscending} (the
 * {@code Boolean}) and {@code onSort}, which gets both as a {@code double[]} {column, 1 for ascending or 0}. Each is
 * a {@code Consumer} prop; the application renders again with the new props, as with every input. The headers
 * take presses when the prop {@code sortable} is true, which is its default when the table has {@code onSort},
 * {@code onSortColumn} or {@code sortColumn}; a table without any of them is as before. There is no key for it.
 */
final class JXTableSort {
    private JXTableSort() {
    }

    /** Whether the headers of this table take presses. */
    static boolean sortable(JXNativeNode table) {
        if (!"table".equals(table.getType()) || Boolean.TRUE.equals(table.getProperty("disabled"))) {
            return false;
        }
        Object flag = table.getProperty("sortable");
        if (flag instanceof Boolean) {
            return ((Boolean) flag).booleanValue();
        }
        return table.getProperty("onSort") instanceof Consumer || table.getProperty("onSortColumn") instanceof Consumer
                || table.getProperty("sortColumn") instanceof Number;
    }

    /** The column the table is sorted by, -1 for none. */
    static int column(JXNativeNode table) {
        return Math.max(-1, (int) JXPaint.number(table, "sortColumn", -1));
    }

    static boolean ascending(JXNativeNode table) {
        return !Boolean.FALSE.equals(table.getProperty("sortAscending"));
    }

    /**
     * A primary press on the header of a sortable table. Returns the table (it takes the focus and the press goes no
     * further), or null when the press is not for it: not on a column header, or the table is not sortable.
     */
    static JXNativeNode press(List<JXNativeNode> path, JXPointerEvent event) {
        if (event.getButton() != 0) {
            return null;
        }
        for (int i = path.size() - 1; i >= 0; i--) {
            JXNativeNode table = path.get(i);
            if (!"table".equals(table.getType())) {
                continue;
            }
            if (!sortable(table)) {
                return null;
            }
            JXPointerEvent local = table.localize(event);
            int hit = columnAt(table, local.getX(), local.getY());
            if (hit < 0) {
                return null;
            }
            report(table, hit);
            return table;
        }
        return null;
    }

    /** The column whose header is at the point (window coordinates), or -1. */
    static int columnAt(JXNativeNode table, int x, int y) {
        float top = table.getY() + 1;
        if (y < top || y >= top + JXControlLayout.TABLE_HEADER || x < table.getX() + 1 || x >= table.getX() + table.getWidth() - 1) {
            return -1;
        }
        float[] widths = JXControlLayout.columnWidths(table);
        float cx = table.getX() + 1 - (float) JXControlLayout.scrollGeometry(table).scrollX;
        for (int i = 0; i < widths.length; i++) {
            if (x >= cx && x < cx + widths[i]) {
                return i;
            }
            cx += widths[i];
        }
        return -1;
    }

    /** The state after a press on {@code column}: {column or -1, ascending}. */
    static int[] next(JXNativeNode table, int column) {
        if (column(table) != column) {
            return new int[] {column, 1};
        }
        return ascending(table) ? new int[] {column, 0} : new int[] {-1, 1};
    }

    @SuppressWarnings("unchecked")
    private static void report(JXNativeNode table, int column) {
        int[] state = next(table, column);
        Object onColumn = table.getProperty("onSortColumn");
        if (onColumn instanceof Consumer) {
            ((Consumer<Object>) onColumn).accept(Integer.valueOf(state[0]));
        }
        Object onAscending = table.getProperty("onSortAscending");
        if (onAscending instanceof Consumer) {
            ((Consumer<Object>) onAscending).accept(Boolean.valueOf(state[1] == 1));
        }
        Object onSort = table.getProperty("onSort");
        if (onSort instanceof Consumer) {
            ((Consumer<Object>) onSort).accept(new double[] {state[0], state[1]});
        }
    }
}
