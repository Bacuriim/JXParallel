package com.jxparallel.fx.nativeimpl;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.jxparallel.fx.Fx;
import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

import javafx.beans.value.ObservableValue;
import javafx.util.Callback;
import javafx.util.StringConverter;

/**
 * Cells of native ListView, TableView and ComboBox. Only the cells in view exist (virtual flow):
 * the scene reports how many fit after each layout and where the view is scrolled. Cells come from
 * the control's cell factory (the application's ListCell/TableCell subclasses, whose updateItem runs
 * as in JavaFX) or are default cells showing the item's text. A cell's updateItem runs when the
 * item at its index changes, like JavaFX's reuse of cells.
 */
final class NativeCells {
    static final double LIST_CELL = 23;
    static final double TABLE_ROW = 24;
    private static final int DEFAULT_VISIBLE = 25;

    private NativeCells() {
    }

    // ---- ListView ----------------------------------------------------------------------------

    static JXElement list(NativeModel m, JXProps.Builder p, NativeElements.Context ctx) {
        List<Object> items = Native.list(m, "items");
        int n = items.size();
        double fixed = NativeElements.number(m, "fixedCellSize", -1);
        double cellHeight = fixed > 0 ? fixed : number(m.state.get("cellHeight"), LIST_CELL);
        double scrollY = clampScroll(m, n * cellHeight);
        int first = n == 0 ? 0 : Math.max(0, Math.min(n - 1, (int) Math.floor(scrollY / cellHeight)));
        int visible = (int) number(m.state.get("visible"), DEFAULT_VISIBLE);
        int last = Math.min(n, first + visible);
        boolean focused = ctx.focused(m);
        NativeSelection.Multiple selection = selection(m);
        List<JXElement> cells = new ArrayList<>();
        Map<Integer, NativeModel> pool = pool(m, first, last);
        NativeModel outer = NativeElements.building;
        NativeElements.building = m;
        try {
            for (int i = first; i < last; i++) {
                NativeModel cell = listCell(m, pool, i, items.get(i));
                Native.property(cell, "selected", boolean.class).setValue(selection != null && selection.isSelected(i));
                cell.state.put("listFocused", focused);
                cells.add(NativeElements.child(cell, ctx, m));
            }
        } finally {
            NativeElements.building = outer;
        }
        p.set("itemCount", n).set("first", first).set("scrollY", scrollY);
        if (fixed > 0) {
            p.set("cellHeight", fixed);
        }
        return JXElement.of("list", p.build(), cells.toArray(new JXElement[0]));
    }

    private static NativeModel listCell(NativeModel list, Map<Integer, NativeModel> pool, int index, Object item) {
        NativeModel cell = pool.get(index);
        Object factory = Native.value(list, "cellFactory");
        if (cell == null) {
            cell = reuse(list, factory);
            if (cell != null) {
                pool.put(index, cell);
            }
        }
        if (cell == null || cell.state.get("factory") != factory) {
            cell = createCell(list, factory, javafx.scene.control.ListCell.class);
            cell.state.put("factory", factory);
            Native.property(cell, "listView", Object.class).setValue(list);
            pool.put(index, cell);
        }
        update(list, cell, index, item);
        return cell;
    }

    /** A cell from the factory, or a default cell (the item's text, or the item itself when it is a node). */
    static NativeModel createCell(NativeModel control, Object factory, Class<?> kind) {
        if (factory instanceof Callback) {
            try {
                @SuppressWarnings("unchecked")
                Object created = ((Callback<Object, Object>) factory).call(control);
                NativeModel cell = NativeElements.model(created);
                if (cell != null) {
                    return cell;
                }
            } catch (RuntimeException e) {
                NativeRuntime.report(e);
            }
        }
        Object jx;
        if (kind == javafx.scene.control.TableCell.class) {
            jx = new com.jxparallel.fx.scene.control.TableCell<Object, Object>();
        } else if (kind == javafx.scene.control.TableRow.class) {
            jx = new com.jxparallel.fx.scene.control.TableRow<Object>();
        } else {
            jx = new com.jxparallel.fx.scene.control.ListCell<Object>();
        }
        NativeModel cell = (NativeModel) ((Fx.Backed) jx).fxPeer();
        cell.state.put("defaultCell", true);
        return cell;
    }

    /** Index and item of a cell; updateItem runs only when they change (JavaFX reuses cells the same way). */
    private static void update(NativeModel control, NativeModel cell, int index, Object item) {
        Native.property(cell, "index", int.class).setValue(index);
        boolean changed = !cell.state.containsKey("item") || cell.state.get("item") != item
                || !Objects.equals(cell.state.get("itemVersion"), control.state.get("itemsVersion"));
        if (!changed) {
            return;
        }
        cell.state.put("item", item);
        cell.state.put("itemVersion", control.state.get("itemsVersion"));
        if (Boolean.TRUE.equals(cell.state.get("defaultCell"))) {
            Native.property(cell, "item", Object.class).setValue(item);
            Native.property(cell, "empty", boolean.class).setValue(false);
            NativeModel node = NativeElements.model(item);
            if (node != null) {
                Native.property(cell, "graphic", Object.class).setValue(node);
                Native.property(cell, "text", String.class).setValue(null);
            } else {
                Native.property(cell, "graphic", Object.class).setValue(null);
                Native.property(cell, "text", String.class).setValue(plainText(control, item));
            }
            return;
        }
        callUpdateItem(cell, item, false);
    }

    /** Calls the cell's updateItem(item, empty), the application's override if it has one. */
    static void callUpdateItem(NativeModel cell, Object item, boolean empty) {
        Object jx = cell.jxOwner();
        if (jx == null) {
            Native.property(cell, "item", Object.class).setValue(item);
            Native.property(cell, "empty", boolean.class).setValue(empty);
            return;
        }
        Method method = updateItem(jx.getClass());
        if (method == null) {
            return;
        }
        try {
            method.invoke(jx, Fx.jx(item), empty);
        } catch (java.lang.reflect.InvocationTargetException e) {
            NativeRuntime.report(e.getCause());
        } catch (ReflectiveOperationException e) {
            NativeRuntime.report(e);
        }
    }

    private static final Map<Class<?>, Method> UPDATE_ITEM = new HashMap<>();

    private static synchronized Method updateItem(Class<?> type) {
        if (UPDATE_ITEM.containsKey(type)) {
            return UPDATE_ITEM.get(type);
        }
        Method found = null;
        for (Class<?> c = type; c != null && found == null; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if ("updateItem".equals(method.getName()) && method.getParameterCount() == 2
                        && method.getParameterTypes()[1] == boolean.class && !method.isBridge()) {
                    method.setAccessible(true);
                    found = method;
                    break;
                }
            }
        }
        UPDATE_ITEM.put(type, found);
        return found;
    }

    /**
     * Cells kept for the visible range. Like JavaFX's VirtualFlow, cells scrolled out of view are
     * kept aside and reused for the indices that scroll in (their updateItem runs with the new
     * item), instead of creating a cell (and, in a table, a row of cells) per index shown.
     */
    @SuppressWarnings("unchecked")
    private static Map<Integer, NativeModel> pool(NativeModel control, int first, int last) {
        Map<Integer, NativeModel> pool = (Map<Integer, NativeModel>) control.state.get("cells");
        if (pool == null) {
            pool = new HashMap<>();
            control.state.put("cells", pool);
        }
        java.util.Deque<NativeModel> spare = spare(control);
        java.util.Iterator<Map.Entry<Integer, NativeModel>> it = pool.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, NativeModel> e = it.next();
            if (e.getKey() < first || e.getKey() >= last) {
                spare.push(e.getValue());
                it.remove();
            }
        }
        return pool;
    }

    @SuppressWarnings("unchecked")
    private static java.util.Deque<NativeModel> spare(NativeModel control) {
        Object spare = control.state.get("spareCells");
        if (!(spare instanceof java.util.Deque)) {
            spare = new java.util.ArrayDeque<NativeModel>();
            control.state.put("spareCells", spare);
        }
        return (java.util.Deque<NativeModel>) spare;
    }

    /** A cell scrolled out of view, from the same factory, for another index; null when there is none. */
    private static NativeModel reuse(NativeModel control, Object factory) {
        java.util.Deque<NativeModel> spare = spare(control);
        while (!spare.isEmpty()) {
            NativeModel cell = spare.pop();
            if (cell.state.get("factory") == factory) {
                return cell;
            }
        }
        return null; // cells of another factory are dropped, like JavaFX recreating them
    }

    private static double clampScroll(NativeModel m, double content) {
        double scroll = number(m.state.get("scrollY"), 0);
        double view = number(m.state.get("viewHeight"), content);
        double max = Math.max(0, content - view);
        scroll = Math.max(0, Math.min(scroll, max));
        m.state.put("scrollY", scroll);
        return scroll;
    }

    static NativeSelection.Multiple selection(NativeModel m) {
        Object model = m.values.get("selectionModel");
        if (model instanceof NativeSelection.TableSelection) {
            return ((NativeSelection.TableSelection) model).rows(); // a table's rows, not only a list's
        }
        return model instanceof NativeSelection.Multiple ? (NativeSelection.Multiple) model : null;
    }

    // ---- TableView ---------------------------------------------------------------------------

    static JXElement table(NativeModel m, JXProps.Builder p, NativeElements.Context ctx) {
        List<Object> items = Native.list(m, "items");
        int n = items.size();
        List<NativeModel> columns = leafColumns(m);
        String[] titles = new String[columns.size()];
        double[] widths = new double[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            NativeModel c = columns.get(i);
            titles[i] = NativeElements.string(c, "text");
            Object explicit = c.values.get("prefWidth");
            widths[i] = explicit != null ? NativeElements.number(c, "prefWidth", 80) : -1;
            c.state.put("renderParent", m);
        }
        double fixed = NativeElements.number(m, "fixedCellSize", -1);
        double rowHeight = fixed > 0 ? fixed : TABLE_ROW;
        double scrollY = clampScroll(m, n * rowHeight);
        int first = n == 0 ? 0 : Math.max(0, Math.min(n - 1, (int) Math.floor(scrollY / rowHeight)));
        int visible = (int) number(m.state.get("visible"), DEFAULT_VISIBLE);
        int last = Math.min(n, first + visible);
        boolean focused = ctx.focused(m);
        NativeSelection.Multiple selection = selection(m);
        Object policy = Native.value(m, "columnResizePolicy");
        boolean constrained = policy != null && policy == javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY;
        List<JXElement> rows = new ArrayList<>();
        Map<Integer, NativeModel> pool = pool(m, first, last);
        NativeModel outer = NativeElements.building;
        NativeElements.building = m;
        try {
            for (int i = first; i < last; i++) {
                Object item = items.get(i);
                NativeModel row = tableRow(m, pool, i, item);
                boolean selected = selection != null && selection.isSelected(i);
                Native.property(row, "selected", boolean.class).setValue(selected);
                row.state.put("renderParent", m);
                List<JXElement> cells = new ArrayList<>();
                for (int c = 0; c < columns.size(); c++) {
                    NativeModel cell = tableCell(m, row, columns.get(c), i, item);
                    Native.property(cell, "selected", boolean.class).setValue(selected);
                    cell.state.put("listFocused", focused);
                    cells.add(NativeElements.child(cell, ctx, row));
                }
                rows.add(rowElement(row, selected, i % 2 == 1, focused, cells, ctx));
            }
        } finally {
            NativeElements.building = outer;
        }
        p.set("columns", titles).set("columnWidths", widths).set("constrained", constrained)
                .set("itemCount", n).set("first", first).set("scrollY", scrollY)
                .set("scrollX", number(m.state.get("scrollX"), 0));
        if (fixed > 0) {
            p.set("rowHeight", fixed);
        }
        return JXElement.of("table", p.build(), rows.toArray(new JXElement[0]));
    }

    /**
     * The element of a table row, the same instance as last time while the row, its state and its
     * cells' elements are the same: a row that only moved while scrolling is then skipped by the
     * reconcile, which keys rows by their model, and only moved by the layout.
     */
    private static JXElement rowElement(NativeModel row, boolean selected, boolean odd, boolean focused,
                                        List<JXElement> cells, NativeElements.Context ctx) {
        Object[] inputs = new Object[cells.size() + 4];
        inputs[0] = row.version;
        inputs[1] = selected;
        inputs[2] = odd;
        inputs[3] = focused;
        for (int c = 0; c < cells.size(); c++) {
            inputs[c + 4] = cells.get(c);
        }
        if (row.element != null && row.elementScene == ctx.scene && java.util.Arrays.equals(row.elementInputs, inputs)) {
            return row.element; // JXElement compares by identity: the very same cell elements
        }
        JXProps.Builder rp = JXProps.builder().set("model", row).set("key", row).set("selected", selected).set("odd", odd)
                .set("listFocused", focused);
        ctx.css.apply(row, rp);
        JXElement element = JXElement.of("tablerow", rp.build(), cells.toArray(new JXElement[0]));
        row.element = element;
        row.elementScene = ctx.scene;
        row.elementInputs = inputs;
        return element;
    }

    /** Visible leaf columns, left to right (nested columns contribute their leaves). */
    static List<NativeModel> leafColumns(NativeModel table) {
        List<NativeModel> out = new ArrayList<>();
        for (Object o : Native.list(table, "columns")) {
            collectLeaves(NativeElements.model(o), out);
        }
        return out;
    }

    private static void collectLeaves(NativeModel column, List<NativeModel> out) {
        if (column == null || Boolean.FALSE.equals(Native.value(column, "visible"))) {
            return;
        }
        List<Object> nested = Native.list(column, "columns");
        if (nested.isEmpty()) {
            out.add(column);
            return;
        }
        for (Object o : nested) {
            collectLeaves(NativeElements.model(o), out);
        }
    }

    private static NativeModel tableRow(NativeModel table, Map<Integer, NativeModel> pool, int index, Object item) {
        NativeModel row = pool.get(index);
        Object factory = Native.value(table, "rowFactory");
        if (row == null) {
            row = reuse(table, factory); // with its cells: they take the new row's values
            if (row != null) {
                pool.put(index, row);
            }
        }
        if (row == null || row.state.get("factory") != factory) {
            row = createCell(table, factory, javafx.scene.control.TableRow.class);
            row.state.put("factory", factory);
            Native.property(row, "tableView", Object.class).setValue(table);
            pool.put(index, row);
        }
        Native.property(row, "index", int.class).setValue(index);
        if (!row.state.containsKey("item") || row.state.get("item") != item
                || !Objects.equals(row.state.get("itemVersion"), table.state.get("itemsVersion"))) {
            row.state.put("item", item);
            row.state.put("itemVersion", table.state.get("itemsVersion"));
            if (Boolean.TRUE.equals(row.state.get("defaultCell"))) {
                Native.property(row, "item", Object.class).setValue(item);
                Native.property(row, "empty", boolean.class).setValue(false);
            } else {
                callUpdateItem(row, item, false);
            }
        }
        return row;
    }

    @SuppressWarnings("unchecked")
    private static NativeModel tableCell(NativeModel table, NativeModel row, NativeModel column, int index, Object item) {
        Map<NativeModel, NativeModel> cells = (Map<NativeModel, NativeModel>) row.state.get("cells");
        if (cells == null) {
            cells = new IdentityHashMap<>();
            row.state.put("cells", cells);
        }
        NativeModel cell = cells.get(column);
        Object factory = Native.value(column, "cellFactory");
        if (cell == null || cell.state.get("factory") != factory) {
            cell = createCell(column, factory, javafx.scene.control.TableCell.class);
            cell.state.put("factory", factory);
            Native.property(cell, "tableView", Object.class).setValue(table);
            Native.property(cell, "tableColumn", Object.class).setValue(column);
            cells.put(column, cell);
        }
        Native.property(cell, "tableRow", Object.class).setValue(row);
        Native.property(cell, "index", int.class).setValue(index);
        Object value = shownValue(cell, table, column, item);
        boolean changed = !cell.state.containsKey("value") || !Objects.equals(cell.state.get("value"), value)
                || cell.state.get("rowItem") != item;
        if (changed) {
            cell.state.put("value", value);
            cell.state.put("rowItem", item);
            if (Boolean.TRUE.equals(cell.state.get("defaultCell"))) {
                Native.property(cell, "item", Object.class).setValue(value);
                Native.property(cell, "empty", boolean.class).setValue(false);
                NativeModel node = NativeElements.model(value);
                Native.property(cell, "graphic", Object.class).setValue(node);
                Native.property(cell, "text", String.class).setValue(node != null || value == null ? null : String.valueOf(value));
            } else {
                callUpdateItem(cell, value, false);
            }
        }
        return cell;
    }

    /**
     * The value a table cell shows: the cell value factory runs once per row item, and its observable
     * value is read on every render, as a JavaFX TableCell listens to it (so a changed property shows).
     */
    private static Object shownValue(NativeModel cell, NativeModel table, NativeModel column, Object item) {
        Object factory = Native.value(column, "cellValueFactory");
        if (cell.state.get("observedRow") != item || cell.state.get("observedFactory") != factory || !cell.state.containsKey("observed")) {
            Object previous = cell.state.get("observed");
            Object listener = cell.state.get("observer");
            if (previous instanceof ObservableValue && listener instanceof javafx.beans.InvalidationListener) {
                ((ObservableValue<?>) previous).removeListener((javafx.beans.InvalidationListener) listener);
            }
            ObservableValue<?> next = observable(table, column, item);
            if (next != null) {
                // like TableCell: a change of the row's property repaints the cell (one listener per cell)
                javafx.beans.InvalidationListener repaint = listener instanceof javafx.beans.InvalidationListener
                        ? (javafx.beans.InvalidationListener) listener
                        : o -> {
                            NativeElements.invalidate(cell);
                            NativeRuntime.pulse();
                        };
                next.addListener(repaint);
                cell.state.put("observer", repaint);
            }
            cell.state.put("observed", next);
            cell.state.put("observedRow", item);
            cell.state.put("observedFactory", factory);
        }
        Object observed = cell.state.get("observed");
        return observed instanceof ObservableValue ? ((ObservableValue<?>) observed).getValue() : null;
    }

    /** The value a column shows for a row item, from its cellValueFactory. */
    static Object cellValue(NativeModel table, NativeModel column, Object item) {
        ObservableValue<?> observable = observable(table, column, item);
        return observable == null ? null : observable.getValue();
    }

    /** What the column's cellValueFactory gives for a row item (null when nothing). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ObservableValue<?> observable(NativeModel table, NativeModel column, Object item) {
        Object factory = Native.value(column, "cellValueFactory");
        if (factory == null || item == null) {
            return null;
        }
        try {
            ObservableValue<?> observable;
            if (factory instanceof NativeModel && ((NativeModel) factory).is(javafx.scene.control.cell.MapValueFactory.class)) {
                // MapValueFactory is native (its API names a table column): read the row map by its key
                Object value = item instanceof java.util.Map ? ((java.util.Map<?, ?>) item).get(Native.value((NativeModel) factory, "key")) : null;
                return value instanceof ObservableValue ? (ObservableValue<?>) value : new javafx.beans.property.ReadOnlyObjectWrapper<>(value);
            } else if (Fx.jx(factory) != null
                    && Fx.jx(factory).getClass() == com.jxparallel.fx.scene.control.cell.PropertyValueFactory.class) {
                // the common case, read directly: no CellDataFeatures and no adapter calls per row
                // (a subclass may override call(), so it goes the general way)
                Object result = ((com.jxparallel.fx.scene.control.cell.PropertyValueFactory<Object, ?>) Fx.jx(factory))
                        .valueFor(Fx.jx(item));
                result = Fx.fx(result);
                observable = result instanceof ObservableValue ? (ObservableValue<?>) result : null;
            } else if (factory instanceof Callback) {
                Object features = new com.jxparallel.fx.scene.control.TableColumn.CellDataFeatures(
                        (com.jxparallel.fx.scene.control.TableView) Fx.jx(table),
                        (com.jxparallel.fx.scene.control.TableColumn) Fx.jx(column), Fx.jx(item));
                Object result = ((Callback) factory).call(((Fx.Backed) features).fxPeer());
                result = Fx.fx(result);
                observable = result instanceof ObservableValue ? (ObservableValue<?>) result : null;
            } else {
                observable = null;
            }
            return observable;
        } catch (RuntimeException e) {
            NativeRuntime.report(e);
            return null;
        }
    }

    // ---- ComboBox / ChoiceBox ----------------------------------------------------------------

    /**
     * The text a combo box or choice box shows for an item: its cell factory's cell (JavaFX uses it
     * for the button cell too), else its converter, else toString.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static String display(NativeModel control, Object item) {
        if (item == null) {
            return "";
        }
        Object factory = Native.value(control, "cellFactory");
        if (factory instanceof Callback) {
            Map<Object, String> cache = (Map<Object, String>) control.state.computeIfAbsent("displayCache", k -> new IdentityHashMap<>());
            if (control.state.get("displayFactory") != factory) {
                cache.clear();
                control.state.put("displayFactory", factory);
            }
            String cached = cache.get(item);
            if (cached != null) {
                return cached;
            }
            NativeModel cell = (NativeModel) control.state.get("buttonCell");
            if (cell == null || cell.state.get("factory") != factory) {
                // JavaFX calls a combo box's cell factory with the ListView of its popup
                cell = createCell(popupListView(control), factory, javafx.scene.control.ListCell.class);
                cell.state.put("factory", factory);
                control.state.put("buttonCell", cell);
            }
            if (Boolean.TRUE.equals(cell.state.get("defaultCell"))) {
                return plainText(control, item); // the factory failed: shown like a combo box without one
            }
            callUpdateItem(cell, item, false);
            String text = NativeElements.string(cell, "text");
            cache.put(item, text);
            return text;
        }
        return plainText(control, item);
    }

    /** The ListView a combo box's cell factory receives: native, over the combo box's items. */
    private static NativeModel popupListView(NativeModel combo) {
        NativeModel list = (NativeModel) combo.state.get("popupListView");
        if (list == null) {
            list = (NativeModel) ((Fx.Backed) new com.jxparallel.fx.scene.control.ListView<Object>()).fxPeer();
            list.values.put("items", Native.list(combo, "items"));
            combo.state.put("popupListView", list);
        }
        return list;
    }

    /** The text of an item without a cell factory: the control's converter, else toString. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static String plainText(NativeModel control, Object item) {
        if (item == null) {
            return "";
        }
        Object converter = Native.value(control, "converter");
        if (converter instanceof StringConverter) {
            try {
                String s = ((StringConverter) converter).toString(item);
                return s == null ? "" : s;
            } catch (RuntimeException e) {
                NativeRuntime.report(e);
            }
        }
        return String.valueOf(item);
    }

    /** The popup list of an open combo box: its items as cells, the value's item selected. */
    static JXElement comboPopup(NativeModel combo, NativeScene scene, double width) {
        List<Object> items = Native.list(combo, "items");
        int n = items.size();
        int rows = Math.max(1, Math.min(n, (int) NativeElements.number(combo, "visibleRowCount", 10)));
        double scrollY = number(combo.state.get("popupScrollY"), 0);
        double max = Math.max(0, n * LIST_CELL - rows * LIST_CELL);
        scrollY = Math.max(0, Math.min(scrollY, max));
        combo.state.put("popupScrollY", scrollY);
        int first = (int) Math.floor(scrollY / LIST_CELL);
        Object value = Native.value(combo, "value");
        int hovered = (int) number(combo.state.get("popupHover"), -1);
        List<JXElement> cells = new ArrayList<>();
        for (int i = first; i < Math.min(n, first + rows + 1); i++) {
            Object item = items.get(i);
            boolean selected = Objects.equals(item, value);
            cells.add(JXElement.of("cell", JXProps.builder().set("value", display(combo, item)).set("selected", selected)
                    .set("listFocused", true).set("hover", i == hovered).set("popupIndex", i).build()));
        }
        JXProps props = JXProps.builder().set("popupOf", combo).set("itemCount", n).set("first", first).set("scrollY", scrollY)
                .set("cellHeight", LIST_CELL).set("prefWidth", width).set("prefHeight", rows * LIST_CELL + 2)
                .set("minHeight", rows * LIST_CELL + 2).set("maxHeight", rows * LIST_CELL + 2).build();
        return JXElement.of("list", props, cells.toArray(new JXElement[0]));
    }

    /** Marks a control's items as changed, so every cell runs updateItem again. */
    static void itemsChanged(NativeModel control) {
        Object v = control.state.get("itemsVersion");
        control.state.put("itemsVersion", v instanceof Integer ? (Integer) v + 1 : 1);
    }

    static double number(Object value, double fallback) {
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }
}
