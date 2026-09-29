package com.jxparallel.fx.nativeimpl;

import java.util.List;

import com.jxparallel.ui.native2d.JXControlLayout;
import com.jxparallel.ui.native2d.JXInputEvent;
import com.jxparallel.ui.native2d.JXNativeNode;
import com.jxparallel.ui.native2d.JXTextHit;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.SingleSelectionModel;
import javafx.scene.input.KeyCode;

/**
 * What controls do by themselves, after an event went through the application's handlers without
 * being consumed: JavaFX's control behaviors (ButtonBehavior, TextFieldBehavior, ListViewBehavior...),
 * reduced to what screens rely on.
 */
final class NativeBehavior {
    private NativeBehavior() {
    }

    // ---- pointer -----------------------------------------------------------------------------

    static void pressed(NativeScene s, List<NativeModel> path, JXNativeNode deepest, JXInputEvent e) {
        if (e.getButton() != JXInputEvent.BUTTON_PRIMARY) {
            return;
        }
        for (int i = path.size() - 1; i >= 0; i--) {
            NativeModel m = path.get(i);
            if (NativeRuntime.disabled(m)) {
                return;
            }
            JXNativeNode node = s.node(m);
            if (node == null) {
                continue;
            }
            if (pressedOn(s, m, node, e)) {
                return;
            }
        }
    }

    /** True when the node handled the press (the search for an enclosing control stops). */
    private static boolean pressedOn(NativeScene s, NativeModel m, JXNativeNode node, JXInputEvent e) {
        String fx = m.type;
        double x = e.getX();
        double y = e.getY();
        if (NativeText.isText(m)) {
            pressText(s, m, node, e);
            return true;
        }
        switch (fx) {
            case "Button":
            case "ToggleButton":
            case "CheckBox":
            case "RadioButton":
            case "Hyperlink":
                return true; // armed: fires on release
            case "ComboBox":
            case "ChoiceBox": {
                boolean editable = "ComboBox".equals(fx) && Boolean.TRUE.equals(Native.value(m, "editable"));
                if (editable && x < node.getX() + node.getWidth() - 26) {
                    pressText(s, NativeText.editor(m), node, e);
                    return true;
                }
                togglePopup(s, m);
                return true;
            }
            case "DatePicker":
                if (x >= node.getX() + node.getWidth() - JXControlLayout.DATE_BUTTON) {
                    togglePopup(s, m);
                } else {
                    pressText(s, NativeText.editor(m), node, e);
                }
                return true;
            case "Spinner":
                if (x >= node.getX() + node.getWidth() - JXControlLayout.SPINNER_BUTTON) {
                    boolean up = y < node.getY() + node.getHeight() / 2.0;
                    step(m, up ? 1 : -1);
                } else {
                    pressText(s, NativeText.editor(m), node, e);
                }
                return true;
            case "Slider":
                slide(m, node, x, y);
                return true;
            case "TitledPane": {
                double title = node.getHeight() >= 25 ? 25 : node.getHeight();
                if (y < node.getY() + title) {
                    toggleTitled(m);
                    return true;
                }
                return false;
            }
            case "TabPane": {
                boolean[] close = new boolean[1];
                int tab = JXControlLayout.tabAt(node, x, y, close);
                if (tab >= 0) {
                    if (close[0]) {
                        closeTab(m, tab);
                    } else {
                        NativeRuntime.selectTab(m, tab);
                    }
                    return true;
                }
                return false;
            }
            case "Pagination":
                for (float[] b : JXControlLayout.pageButtons(node)) {
                    if (x >= b[0] && x < b[0] + b[2] && y >= b[1] && y < b[1] + b[3]) {
                        int count = (int) NativeElements.number(m, "pageCount", 1);
                        int current = (int) NativeElements.number(m, "currentPageIndex", 0);
                        int next = b[4] == -1 ? current - 1 : b[4] == -2 ? current + 1 : (int) b[4];
                        if (next >= 0 && next < count) {
                            Native.property(m, "currentPageIndex", int.class).setValue(next);
                        }
                        return true;
                    }
                }
                return false;
            case "ScrollPane":
            case "ListView":
            case "TableView":
                if (scrollBarPress(s, m, node, x, y)) {
                    return true;
                }
                if ("TableView".equals(fx) && y < node.getY() + 1 + JXControlLayout.TABLE_HEADER) {
                    sortColumn(m, JXControlLayout.columnAt(node, x));
                    return true;
                }
                if ("ListView".equals(fx) || "TableView".equals(fx)) {
                    int index = JXControlLayout.itemAt(node, y);
                    if (index >= 0) {
                        select(m, index, e.isControlDown(), e.isShiftDown());
                    }
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    static void released(NativeScene s, List<NativeModel> path, JXNativeNode deepest, JXInputEvent e, boolean inside) {
        s.barDrag = null;
        if (e.getButton() != JXInputEvent.BUTTON_PRIMARY || !inside) {
            return;
        }
        for (int i = path.size() - 1; i >= 0; i--) {
            NativeModel m = path.get(i);
            if (NativeRuntime.disabled(m)) {
                return;
            }
            String fx = m.type;
            switch (fx) {
                case "Button":
                case "Hyperlink":
                    fire(m);
                    return;
                case "ToggleButton":
                case "CheckBox":
                case "RadioButton":
                    toggle(m);
                    return;
                default:
                    if (NativeText.isText(m) || "ComboBox".equals(fx) || "ListView".equals(fx) || "TableView".equals(fx)) {
                        return;
                    }
                    break;
            }
        }
    }

    static void dragged(NativeScene s, List<NativeModel> path, JXNativeNode deepest, JXInputEvent e) {
        if (s.barDrag != null) {
            dragBar(s, e);
            return;
        }
        if (path == null) {
            return;
        }
        for (int i = path.size() - 1; i >= 0; i--) {
            NativeModel m = path.get(i);
            JXNativeNode node = s.node(m);
            if (node == null) {
                continue;
            }
            if (NativeText.isText(m)) {
                NativeText.moveCaret(m, textIndex(m, node, e.getX(), e.getY()), true);
                return;
            }
            if (m.is(javafx.scene.control.Slider.class)) {
                slide(m, node, e.getX(), e.getY());
                return;
            }
        }
    }

    private static void pressText(NativeScene s, NativeModel text, JXNativeNode node, JXInputEvent e) {
        int index = textIndex(text, node, e.getX(), e.getY());
        if (e.getClickCount() == 2) {
            NativeText.selectWord(text, index);
        } else if (e.getClickCount() >= 3) {
            NativeText.selectAll(text);
        } else {
            NativeText.moveCaret(text, index, e.isShiftDown());
        }
    }

    private static int textIndex(NativeModel m, JXNativeNode node, double x, double y) {
        if (m.is(javafx.scene.control.TextArea.class)) {
            return JXTextHit.areaIndex(node, x, y);
        }
        return JXTextHit.fieldIndex(node, x);
    }

    // ---- buttons and toggles -----------------------------------------------------------------

    /** Button.fire / Hyperlink.fire: an ActionEvent (a hyperlink becomes visited). */
    static void fire(NativeModel m) {
        if (NativeRuntime.disabled(m)) {
            return;
        }
        if (m.is(javafx.scene.control.Hyperlink.class)) {
            Native.property(m, "visited", boolean.class).setValue(true);
        }
        NativeEvents.fireAction(m);
    }

    /** CheckBox/ToggleButton/RadioButton.fire: new selection state, then an ActionEvent. */
    static void toggle(NativeModel m) {
        if (NativeRuntime.disabled(m)) {
            return;
        }
        if (m.is(javafx.scene.control.CheckBox.class)) {
            boolean selected = Boolean.TRUE.equals(Native.value(m, "selected"));
            boolean indeterminate = Boolean.TRUE.equals(Native.value(m, "indeterminate"));
            if (Boolean.TRUE.equals(Native.value(m, "allowIndeterminate"))) {
                if (!selected && !indeterminate) {
                    Native.property(m, "indeterminate", boolean.class).setValue(true);
                } else if (selected && !indeterminate) {
                    Native.property(m, "selected", boolean.class).setValue(false);
                } else {
                    Native.property(m, "selected", boolean.class).setValue(true);
                    Native.property(m, "indeterminate", boolean.class).setValue(false);
                }
            } else {
                Native.property(m, "selected", boolean.class).setValue(!selected);
                Native.property(m, "indeterminate", boolean.class).setValue(false);
            }
        } else if (m.is(javafx.scene.control.RadioButton.class)) {
            // RadioButton.fire: toggles like a ToggleButton when alone; in a group it only selects
            boolean selected = Boolean.TRUE.equals(Native.value(m, "selected"));
            if (NativeElements.model(Native.value(m, "toggleGroup")) == null) {
                setSelected(m, !selected);
            } else if (!selected) {
                setSelected(m, true);
            }
        } else {
            setSelected(m, !Boolean.TRUE.equals(Native.value(m, "selected")));
        }
        NativeEvents.fireAction(m);
    }

    /** Selects or deselects a toggle, keeping its group consistent (one selected at most). */
    static void setSelected(NativeModel m, boolean selected) {
        Native.property(m, "selected", boolean.class).setValue(selected);
        NativeModel group = NativeElements.model(Native.value(m, "toggleGroup"));
        if (group == null) {
            return;
        }
        if (selected) {
            for (Object o : Native.list(group, "toggles")) {
                NativeModel other = NativeElements.model(o);
                if (other != null && other != m) {
                    Native.property(other, "selected", boolean.class).setValue(false);
                }
            }
            Native.property(group, "selectedToggle", Object.class).setValue(m);
        } else if (Native.value(group, "selectedToggle") == m) {
            Native.property(group, "selectedToggle", Object.class).setValue(null);
        }
    }

    @SuppressWarnings("unchecked")
    static void fireMenuItem(NativeModel item) {
        if (item.is(javafx.scene.control.CheckMenuItem.class)) {
            Native.property(item, "selected", boolean.class).setValue(!Boolean.TRUE.equals(Native.value(item, "selected")));
        } else if (item.is(javafx.scene.control.RadioMenuItem.class)) {
            setSelected(item, true);
        }
        Object h = Native.value(item, "onAction");
        if (h instanceof EventHandler) {
            try {
                ((EventHandler<ActionEvent>) h).handle(new ActionEvent(item, item));
            } catch (RuntimeException e) {
                NativeRuntime.report(e);
            }
        }
    }

    // ---- composite controls ------------------------------------------------------------------

    static void togglePopup(NativeScene s, NativeModel control) {
        if (NativeRuntime.popupOwner(s) == control) {
            NativeEvents.closePopups(s);
        } else {
            NativeEvents.openPopup(s, control);
        }
    }

    /** A ComboBox or ChoiceBox item picked from the popup or with the keyboard. */
    static void selectComboItem(NativeModel combo, int index) {
        List<Object> items = Native.list(combo, "items");
        if (index < 0 || index >= items.size()) {
            return;
        }
        Object item = items.get(index);
        Object selection = combo.values.get("selectionModel");
        if (selection instanceof SingleSelectionModel) {
            @SuppressWarnings("unchecked")
            SingleSelectionModel<Object> sel = (SingleSelectionModel<Object>) selection;
            sel.select(index);
        }
        Object before = Native.value(combo, "value");
        Native.property(combo, "value", Object.class).setValue(item);
        if (Boolean.TRUE.equals(Native.value(combo, "editable"))) {
            NativeModel editor = NativeText.editor(combo);
            String text = NativeCells.display(combo, item);
            Native.property(editor, "text", String.class).setValue(text);
            NativeText.moveCaret(editor, text.length(), false);
        }
        if (before != item) {
            NativeEvents.fireAction(combo);
        }
    }

    private static void step(NativeModel spinner, int steps) {
        javafx.scene.control.SpinnerValueFactory<?> factory = NativeImpls.factory(spinner);
        if (factory == null) {
            return;
        }
        if (steps > 0) {
            factory.increment(steps);
        } else {
            factory.decrement(-steps);
        }
        NativeText.syncSpinner(spinner, NativeText.editor(spinner), true);
    }

    private static void slide(NativeModel m, JXNativeNode node, double x, double y) {
        double min = NativeElements.number(m, "min", 0);
        double max = NativeElements.number(m, "max", 100);
        boolean vertical = "vertical".equals(String.valueOf(node.getProperty("orientation")));
        double fraction = vertical ? 1 - (y - node.getY() - 7) / Math.max(1, node.getHeight() - 14)
                : (x - node.getX() - 7) / Math.max(1, node.getWidth() - 14);
        fraction = Math.max(0, Math.min(1, fraction));
        double value = min + fraction * (max - min);
        if (Boolean.TRUE.equals(Native.value(m, "snapToTicks"))) {
            double unit = NativeElements.number(m, "majorTickUnit", 25);
            int minor = (int) NativeElements.number(m, "minorTickCount", 3);
            double tick = unit / (minor + 1);
            value = min + Math.round((value - min) / tick) * tick;
        }
        Native.property(m, "value", double.class).setValue(Math.max(min, Math.min(max, value)));
    }

    static void toggleTitled(NativeModel m) {
        if (Boolean.FALSE.equals(Native.value(m, "collapsible"))) {
            return;
        }
        NativeModel accordion = NativeCss.parentOf(m);
        if (accordion != null && accordion.is(javafx.scene.control.Accordion.class)) {
            boolean open = NativeElements.model(Native.value(accordion, "expandedPane")) == m;
            Native.property(accordion, "expandedPane", Object.class).setValue(open ? null : m);
            for (Object o : Native.list(accordion, "panes")) {
                NativeModel pane = NativeElements.model(o);
                if (pane != null) {
                    Native.property(pane, "expanded", boolean.class).setValue(!open && pane == m);
                }
            }
            return;
        }
        boolean expanded = !Boolean.FALSE.equals(Native.value(m, "expanded"));
        Native.property(m, "expanded", boolean.class).setValue(!expanded);
    }

    @SuppressWarnings("unchecked")
    private static void closeTab(NativeModel tabPane, int index) {
        List<Object> tabs = Native.list(tabPane, "tabs");
        NativeModel tab = NativeElements.model(tabs.get(index));
        if (tab == null) {
            return;
        }
        Object request = Native.value(tab, "onCloseRequest");
        if (request instanceof EventHandler) {
            javafx.event.Event event = new javafx.event.Event(tab, tab, javafx.scene.control.Tab.TAB_CLOSE_REQUEST_EVENT);
            ((EventHandler<javafx.event.Event>) request).handle(event);
            if (event.isConsumed()) {
                return;
            }
        }
        tabs.remove(index);
        Object closed = Native.value(tab, "onClosed");
        if (closed instanceof EventHandler) {
            ((EventHandler<javafx.event.Event>) closed).handle(new javafx.event.Event(tab, tab, javafx.scene.control.Tab.CLOSED_EVENT));
        }
        if (!tabs.isEmpty()) {
            NativeRuntime.selectTab(tabPane, Math.min(index, tabs.size() - 1));
        }
    }

    /** ListView/TableView click selection: plain, control (toggle) or shift (range from the anchor). */
    static void select(NativeModel m, int index, boolean control, boolean shift) {
        NativeSelection.Multiple sel = selection(m);
        boolean multiple = sel.getSelectionMode() == javafx.scene.control.SelectionMode.MULTIPLE;
        Object anchor = m.state.get("selectionAnchor");
        if (multiple && shift && anchor instanceof Integer) {
            sel.selectRange((Integer) anchor, index, control);
        } else if (multiple && control) {
            if (sel.isSelected(index)) {
                sel.clearSelection(index);
            } else {
                sel.select(index);
            }
            m.state.put("selectionAnchor", index);
        } else {
            sel.clearAndSelect(index);
            m.state.put("selectionAnchor", index);
        }
        ensureVisible(m, index);
    }

    /** The row selection of a list or table, created on first use like JavaFX's. */
    static NativeSelection.Multiple selection(NativeModel m) {
        Object existing = m.values.get("selectionModel");
        if (existing instanceof NativeSelection.TableSelection) {
            return ((NativeSelection.TableSelection) existing).rows();
        }
        if (existing instanceof NativeSelection.Multiple) {
            return (NativeSelection.Multiple) existing;
        }
        if (m.is(javafx.scene.control.TableView.class)) {
            return ((NativeSelection.TableSelection) NativeRuntime.selectionOf(m)).rows();
        }
        NativeSelection.Multiple created = new NativeSelection.Multiple(m, "items");
        m.values.put("selectionModel", created);
        return created;
    }

    private static void ensureVisible(NativeModel m, int index) {
        double item = NativeCells.number(m.state.get("cellHeight"),
                m.is(javafx.scene.control.TableView.class) ? NativeCells.TABLE_ROW : NativeCells.LIST_CELL);
        double scroll = NativeCells.number(m.state.get("scrollY"), 0);
        double view = NativeCells.number(m.state.get("viewHeight"), 0);
        double top = index * item;
        if (top < scroll) {
            m.state.put("scrollY", top);
        } else if (view > 0 && top + item > scroll + view) {
            m.state.put("scrollY", top + item - view);
        }
    }

    /** Header click: ascending, then descending, then unsorted (JavaFX's cycle), sorting the items in place. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void sortColumn(NativeModel table, int column) {
        List<NativeModel> columns = NativeCells.leafColumns(table);
        if (column < 0 || column >= columns.size()) {
            return;
        }
        NativeModel c = columns.get(column);
        if (Boolean.FALSE.equals(Native.value(c, "sortable"))) {
            return;
        }
        Object state = table.state.get("sortColumn");
        boolean ascending = state != c || !Boolean.TRUE.equals(table.state.get("sortAscending"));
        table.state.put("sortColumn", c);
        table.state.put("sortAscending", ascending);
        List<Object> items = Native.list(table, "items");
        java.util.Comparator<Object> natural = (a, b) -> {
            Object va = NativeCells.cellValue(table, c, a);
            Object vb = NativeCells.cellValue(table, c, b);
            Object comparator = Native.value(c, "comparator");
            if (comparator instanceof java.util.Comparator) {
                return ((java.util.Comparator) comparator).compare(va, vb);
            }
            if (va == null || vb == null) {
                return va == null ? (vb == null ? 0 : -1) : 1;
            }
            if (va instanceof Comparable && va.getClass().isInstance(vb)) {
                return ((Comparable) va).compareTo(vb);
            }
            return String.valueOf(va).compareTo(String.valueOf(vb));
        };
        try {
            javafx.collections.FXCollections.sort((javafx.collections.ObservableList) items,
                    ascending ? natural : natural.reversed());
        } catch (UnsupportedOperationException e) {
            // an unmodifiable (sorted or filtered) list keeps its order, like TableView's default sort policy
        }
        NativeCells.itemsChanged(table);
    }

    // ---- scroll bars -------------------------------------------------------------------------

    /** Press on a scroll bar: arrows step, the track pages, the thumb starts a drag. */
    private static boolean scrollBarPress(NativeScene s, NativeModel m, JXNativeNode node, double x, double y) {
        JXControlLayout.ScrollGeometry g = JXControlLayout.scrollGeometry(node);
        if (g.vertical && x >= g.vx && x < g.vx + g.vw && y >= g.vy && y < g.vy + g.vh) {
            if (y < g.vy + 11) {
                scrollBy(m, node, 0, -step(m, node, true));
            } else if (y >= g.vy + g.vh - 11) {
                scrollBy(m, node, 0, step(m, node, true));
            } else if (y >= g.vThumbY && y < g.vThumbY + g.vThumbH) {
                s.barDrag = new double[] {1, y, g.scrollY};
                s.barDragModel = m;
            } else {
                scrollBy(m, node, 0, (y < g.vThumbY ? -1 : 1) * g.viewH);
            }
            return true;
        }
        if (g.horizontal && x >= g.hx && x < g.hx + g.hw && y >= g.hy && y < g.hy + g.hh) {
            if (x < g.hx + 11) {
                scrollBy(m, node, -step(m, node, false), 0);
            } else if (x >= g.hx + g.hw - 11) {
                scrollBy(m, node, step(m, node, false), 0);
            } else if (x >= g.hThumbX && x < g.hThumbX + g.hThumbW) {
                s.barDrag = new double[] {0, x, g.scrollX};
                s.barDragModel = m;
            } else {
                scrollBy(m, node, (x < g.hThumbX ? -1 : 1) * g.viewW, 0);
            }
            return true;
        }
        return false;
    }

    private static double step(NativeModel m, JXNativeNode node, boolean vertical) {
        if (!vertical) {
            return 20;
        }
        return "scroll".equals(node.getType()) ? 20 : JXControlLayout.cellHeight(node);
    }

    private static void dragBar(NativeScene s, JXInputEvent e) {
        NativeModel m = s.barDragModel;
        JXNativeNode node = m == null ? null : s.node(m);
        if (node == null) {
            s.barDrag = null;
            return;
        }
        JXControlLayout.ScrollGeometry g = JXControlLayout.scrollGeometry(node);
        boolean vertical = s.barDrag[0] == 1;
        double delta = (vertical ? e.getY() : e.getX()) - s.barDrag[1];
        double track = vertical ? g.vh - 22 - g.vThumbH : g.hw - 22 - g.hThumbW;
        double max = vertical ? g.maxScrollY() : g.maxScrollX();
        double scroll = Math.max(0, Math.min(max, s.barDrag[2] + (track <= 0 ? 0 : delta / track * max)));
        setScroll(m, node, vertical ? g.scrollX : scroll, vertical ? scroll : g.scrollY);
    }

    /** Wheel over a scrollable node: the innermost scroll pane, list, table or text area that can move. */
    static void scroll(NativeScene s, List<JXNativeNode> nodes, JXInputEvent e) {
        double dx = e.isShiftDown() ? -e.getScrollY() : -e.getScrollX();
        double dy = e.isShiftDown() ? 0 : -e.getScrollY();
        for (int i = nodes.size() - 1; i >= 0; i--) {
            JXNativeNode node = nodes.get(i);
            Object model = node.getProperty("model");
            if (!(model instanceof NativeModel)) {
                continue;
            }
            NativeModel m = (NativeModel) model;
            String type = node.getType();
            if ("textarea".equals(type)) {
                double line = JXTextHit.lineHeight(node);
                double content = JXTextHit.areaLineCount(node) * line + 8;
                double top = NativeElements.number(m, "scrollTop", 0);
                double next = Math.max(0, Math.min(content - node.getHeight() + 2, top + dy * line * 3));
                if (next != top) {
                    Native.property(m, "scrollTop", double.class).setValue(Math.max(0, next));
                    return;
                }
                continue;
            }
            if (!"scroll".equals(type) && !"list".equals(type) && !"table".equals(type)) {
                continue;
            }
            JXControlLayout.ScrollGeometry g = JXControlLayout.scrollGeometry(node);
            double unit = "scroll".equals(type) ? 40 : JXControlLayout.cellHeight(node) * 3;
            double nx = Math.max(0, Math.min(g.maxScrollX(), g.scrollX + dx * unit));
            double ny = Math.max(0, Math.min(g.maxScrollY(), g.scrollY + dy * unit));
            if (nx != g.scrollX || ny != g.scrollY) {
                setScroll(m, node, nx, ny);
                return;
            }
        }
    }

    private static void scrollBy(NativeModel m, JXNativeNode node, double dx, double dy) {
        JXControlLayout.ScrollGeometry g = JXControlLayout.scrollGeometry(node);
        setScroll(m, node, Math.max(0, Math.min(g.maxScrollX(), g.scrollX + dx)), Math.max(0, Math.min(g.maxScrollY(), g.scrollY + dy)));
    }

    /** Scroll pane offsets are its hvalue/vvalue (0..1); lists and tables keep pixel offsets. */
    private static void setScroll(NativeModel m, JXNativeNode node, double x, double y) {
        JXControlLayout.ScrollGeometry g = JXControlLayout.scrollGeometry(node);
        if ("scroll".equals(node.getType())) {
            double hmin = NativeElements.number(m, "hmin", 0);
            double hmax = NativeElements.number(m, "hmax", 1);
            double vmin = NativeElements.number(m, "vmin", 0);
            double vmax = NativeElements.number(m, "vmax", 1);
            if (g.maxScrollX() > 0) {
                Native.property(m, "hvalue", double.class).setValue(hmin + x / g.maxScrollX() * (hmax - hmin));
            }
            if (g.maxScrollY() > 0) {
                Native.property(m, "vvalue", double.class).setValue(vmin + y / g.maxScrollY() * (vmax - vmin));
            }
        } else {
            m.state.put("scrollX", Math.max(0, x));
            m.state.put("scrollY", Math.max(0, y));
        }
        NativeRuntime.pulse();
    }

    // ---- keys --------------------------------------------------------------------------------

    static void key(NativeScene s, NativeModel target, KeyCode code, JXInputEvent e) {
        NativeModel popup = NativeRuntime.popupOwner(s);
        if (popup != null && popupKey(s, popup, code)) {
            return;
        }
        boolean ctrl = e.isControlDown();
        boolean shift = e.isShiftDown();
        String fx = target.type;
        NativeModel text = NativeText.isText(target) ? target
                : ("Spinner".equals(fx) || "DatePicker".equals(fx) || ("ComboBox".equals(fx) && Boolean.TRUE.equals(Native.value(target, "editable"))))
                ? NativeText.editor(target) : null;
        if (code == KeyCode.TAB) {
            if (text != null && target.is(javafx.scene.control.TextArea.class) && !ctrl) {
                NativeText.replaceSelection(text, "\t");
            } else {
                NativeRuntime.traverse(s, !shift);
            }
            return;
        }
        if (text != null && textKey(s, target, text, code, ctrl, shift)) {
            return;
        }
        switch (code) {
            case SPACE:
                if ("Button".equals(fx) || "Hyperlink".equals(fx)) {
                    fire(target);
                } else if ("CheckBox".equals(fx) || "ToggleButton".equals(fx) || "RadioButton".equals(fx)) {
                    toggle(target);
                } else if ("ComboBox".equals(fx) || "ChoiceBox".equals(fx)) {
                    togglePopup(s, target);
                } else if ("TitledPane".equals(fx)) {
                    toggleTitled(target);
                }
                return;
            case ENTER:
                if ("Button".equals(fx) || "Hyperlink".equals(fx)) {
                    fire(target);
                    return;
                }
                fireDefault(s, "defaultButton");
                return;
            case ESCAPE:
                if (!s.overlays.isEmpty()) {
                    NativeEvents.closePopups(s);
                    NativeEvents.hideTooltip(s);
                    return;
                }
                fireDefault(s, "cancelButton");
                return;
            case UP:
            case DOWN:
            case LEFT:
            case RIGHT:
            case HOME:
            case END:
            case PAGE_UP:
            case PAGE_DOWN:
                arrows(s, target, code, ctrl, shift, e);
                return;
            case F4:
                if ("ComboBox".equals(fx) || "ChoiceBox".equals(fx) || "DatePicker".equals(fx)) {
                    togglePopup(s, target);
                }
                return;
            default:
                break;
        }
    }

    /** Keys of an open combo list: arrows move, Enter picks, Escape closes. */
    private static boolean popupKey(NativeScene s, NativeModel combo, KeyCode code) {
        if (combo.is(javafx.scene.control.DatePicker.class)) {
            if (code == KeyCode.ESCAPE) {
                NativeEvents.closePopups(s);
                return true;
            }
            return false;
        }
        int n = Native.list(combo, "items").size();
        int hover = (int) NativeCells.number(combo.state.get("popupHover"), -1);
        switch (code) {
            case DOWN:
                combo.state.put("popupHover", Math.min(n - 1, hover + 1));
                keepPopupRowVisible(combo, Math.min(n - 1, hover + 1));
                return true;
            case UP:
                combo.state.put("popupHover", Math.max(0, hover - 1));
                keepPopupRowVisible(combo, Math.max(0, hover - 1));
                return true;
            case ENTER:
            case SPACE:
                if (hover >= 0) {
                    selectComboItem(combo, hover);
                }
                NativeEvents.closePopups(s);
                return true;
            case ESCAPE:
            case TAB:
                NativeEvents.closePopups(s);
                return code == KeyCode.ESCAPE;
            default:
                return false;
        }
    }

    private static void keepPopupRowVisible(NativeModel combo, int index) {
        int rows = Math.max(1, (int) NativeElements.number(combo, "visibleRowCount", 10));
        double top = NativeCells.number(combo.state.get("popupScrollY"), 0);
        double y = index * NativeCells.LIST_CELL;
        if (y < top) {
            combo.state.put("popupScrollY", y);
        } else if (y + NativeCells.LIST_CELL > top + rows * NativeCells.LIST_CELL) {
            combo.state.put("popupScrollY", y + NativeCells.LIST_CELL - rows * NativeCells.LIST_CELL);
        }
    }

    /** Editing keys of a text input; true when handled. */
    private static boolean textKey(NativeScene s, NativeModel owner, NativeModel text, KeyCode code, boolean ctrl, boolean shift) {
        String value = NativeElements.string(text, "text");
        int caret = NativeText.caret(text);
        boolean area = text.is(javafx.scene.control.TextArea.class);
        JXNativeNode node = s.node(owner);
        switch (code) {
            case LEFT:
                if (!shift && caret != NativeText.anchor(text)) {
                    NativeText.moveCaret(text, Math.min(caret, NativeText.anchor(text)), false);
                } else {
                    NativeText.moveCaret(text, ctrl ? NativeText.previousWord(value, caret) : caret - 1, shift);
                }
                return true;
            case RIGHT:
                if (!shift && caret != NativeText.anchor(text)) {
                    NativeText.moveCaret(text, Math.max(caret, NativeText.anchor(text)), false);
                } else {
                    NativeText.moveCaret(text, ctrl ? NativeText.nextWord(value, caret) : caret + 1, shift);
                }
                return true;
            case HOME:
                if (area && !ctrl && node != null) {
                    NativeText.moveCaret(text, JXTextHit.areaLineOf(node, caret)[1], shift);
                } else {
                    NativeText.moveCaret(text, 0, shift);
                }
                return true;
            case END:
                if (area && !ctrl && node != null) {
                    int[] line = JXTextHit.areaLineOf(node, caret);
                    int next = JXTextHit.areaVertical(node, line[1], 1);
                    int end = next == value.length() || next <= line[1] ? value.length() : Math.max(line[1], nextLineStart(value, line[1]) - 1);
                    NativeText.moveCaret(text, Math.min(end, value.length()), shift);
                } else {
                    NativeText.moveCaret(text, value.length(), shift);
                }
                return true;
            case UP:
            case DOWN:
                if (area && node != null) {
                    NativeText.moveCaret(text, JXTextHit.areaVertical(node, caret, code == KeyCode.UP ? -1 : 1), shift);
                    return true;
                }
                return false;
            case BACK_SPACE:
                NativeText.deleteBackward(text, ctrl);
                return true;
            case DELETE:
                NativeText.deleteForward(text, ctrl);
                return true;
            case ENTER:
                if (area && !ctrl) {
                    NativeText.replaceSelection(text, "\n");
                    return true;
                }
                NativeText.commit(text);
                if (text == owner) {
                    NativeEvents.fireAction(owner);
                } else if (owner.is(javafx.scene.control.ComboBox.class) || owner.is(javafx.scene.control.DatePicker.class)) {
                    NativeEvents.fireAction(owner);
                }
                if (!Boolean.TRUE.equals(text.state.get("actionConsumed"))) {
                    fireDefault(s, "defaultButton");
                }
                return true;
            case A:
                if (ctrl) {
                    NativeText.selectAll(text);
                    return true;
                }
                return false;
            case C:
                if (ctrl) {
                    NativeText.copy(text);
                    return true;
                }
                return false;
            case X:
                if (ctrl) {
                    NativeText.cut(text);
                    return true;
                }
                return false;
            case V:
                if (ctrl) {
                    NativeText.paste(text);
                    return true;
                }
                return false;
            case INSERT:
                if (shift) {
                    NativeText.paste(text);
                    return true;
                }
                if (ctrl) {
                    NativeText.copy(text);
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    private static int nextLineStart(String value, int from) {
        int nl = value.indexOf('\n', from);
        return nl < 0 ? value.length() + 1 : nl + 1;
    }

    private static void arrows(NativeScene s, NativeModel target, KeyCode code, boolean ctrl, boolean shift, JXInputEvent e) {
        String fx = target.type;
        boolean up = code == KeyCode.UP || code == KeyCode.LEFT;
        switch (fx) {
            case "ComboBox":
            case "ChoiceBox": {
                if (e.isAltDown() && code == KeyCode.DOWN) {
                    togglePopup(s, target);
                    return;
                }
                List<Object> items = Native.list(target, "items");
                int index = items.indexOf(Native.value(target, "value"));
                int next = code == KeyCode.HOME ? 0 : code == KeyCode.END ? items.size() - 1 : index + (up ? -1 : 1);
                if (next >= 0 && next < items.size() && (code == KeyCode.UP || code == KeyCode.DOWN || code == KeyCode.HOME || code == KeyCode.END)) {
                    selectComboItem(target, next);
                }
                return;
            }
            case "Spinner":
                if (code == KeyCode.UP || code == KeyCode.DOWN) {
                    step(target, code == KeyCode.UP ? 1 : -1);
                }
                return;
            case "Slider": {
                double min = NativeElements.number(target, "min", 0);
                double max = NativeElements.number(target, "max", 100);
                double v = NativeElements.number(target, "value", 0);
                double unit = NativeElements.number(target, "blockIncrement", 10);
                // SliderBehavior: RIGHT/LEFT move a horizontal slider, UP/DOWN a vertical one (UP increments)
                boolean vertical = Native.value(target, "orientation") == javafx.geometry.Orientation.VERTICAL;
                double next;
                if (code == KeyCode.HOME || code == KeyCode.END) {
                    next = code == KeyCode.HOME ? min : max;
                } else if (code == (vertical ? KeyCode.UP : KeyCode.RIGHT)) {
                    next = v + unit;
                } else if (code == (vertical ? KeyCode.DOWN : KeyCode.LEFT)) {
                    next = v - unit;
                } else {
                    return; // the other axis does nothing, like JavaFX
                }
                Native.property(target, "value", double.class).setValue(Math.max(min, Math.min(max, next)));
                return;
            }
            case "TabPane": {
                int n = Native.list(target, "tabs").size();
                int at = NativeRuntime.selectedTab(target);
                if (n > 0 && (code == KeyCode.LEFT || code == KeyCode.RIGHT)) {
                    NativeRuntime.selectTab(target, (at + (up ? -1 : 1) + n) % n);
                }
                return;
            }
            case "ListView":
            case "TableView": {
                int n = Native.list(target, "items").size();
                if (n == 0 || code == KeyCode.LEFT || code == KeyCode.RIGHT) {
                    return;
                }
                NativeSelection.Multiple sel = selection(target);
                int at = sel.getSelectedIndex();
                int page = Math.max(1, (int) NativeCells.number(target.state.get("visible"), 10) - 2);
                int next;
                switch (code) {
                    case HOME:
                        next = 0;
                        break;
                    case END:
                        next = n - 1;
                        break;
                    case PAGE_UP:
                        next = Math.max(0, at - page);
                        break;
                    case PAGE_DOWN:
                        next = Math.min(n - 1, at + page);
                        break;
                    default:
                        next = Math.max(0, Math.min(n - 1, at + (up ? -1 : 1)));
                        break;
                }
                select(target, next, false, shift);
                return;
            }
            default:
                if (code == KeyCode.UP || code == KeyCode.LEFT) {
                    NativeRuntime.traverse(s, false);
                } else if (code == KeyCode.DOWN || code == KeyCode.RIGHT) {
                    NativeRuntime.traverse(s, true);
                }
        }
    }

    /** Enter fires the scene's default button, Escape its cancel button (visible and enabled ones). */
    private static void fireDefault(NativeScene s, String kind) {
        for (NativeModel m : s.focusOrder) {
            if (m.is(javafx.scene.control.Button.class) && Boolean.TRUE.equals(Native.value(m, kind)) && !NativeRuntime.disabled(m)) {
                fire(m);
                return;
            }
        }
        for (java.util.Map.Entry<NativeModel, JXNativeNode> entry : s.nodes.entrySet()) {
            NativeModel m = entry.getKey();
            if (m.is(javafx.scene.control.Button.class) && Boolean.TRUE.equals(Native.value(m, kind)) && !NativeRuntime.disabled(m)) {
                fire(m);
                return;
            }
        }
    }

    /** A typed character: text inputs insert it, a closed combo box jumps to the first item starting with it. */
    static void typed(NativeScene s, NativeModel target, String character) {
        if (character.isEmpty() || character.charAt(0) < 32 || character.charAt(0) == 127) {
            return;
        }
        String fx = target.type;
        NativeModel text = NativeText.isText(target) ? target
                : ("Spinner".equals(fx) || "DatePicker".equals(fx) || ("ComboBox".equals(fx) && Boolean.TRUE.equals(Native.value(target, "editable"))))
                ? NativeText.editor(target) : null;
        if (text != null) {
            NativeText.replaceSelection(text, character);
            return;
        }
        if ("ComboBox".equals(fx) || "ChoiceBox".equals(fx)) {
            List<Object> items = Native.list(target, "items");
            String prefix = character.toLowerCase();
            for (int i = 0; i < items.size(); i++) {
                if (NativeCells.display(target, items.get(i)).toLowerCase().startsWith(prefix)) {
                    selectComboItem(target, i);
                    return;
                }
            }
        }
    }
}
