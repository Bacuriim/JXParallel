package com.jxparallel.fx.nativeimpl;

import com.jxparallel.fx.Fx;

import javafx.collections.ObservableList;

/**
 * Native implementations of JX node methods that are not plain bean accesses, added as the
 * DeviceConfig screen checks report them missing ({@link Native#missing()}).
 */
final class NativeImpls {
    private NativeImpls() {
    }

    static void register() {
        // GridPane placement: add(child, column, row[, columnSpan, rowSpan]), addRow, addColumn, setConstraints
        Native.register("GridPane.add(Node,int,int)", (self, m, a) -> gridAdd(m, a[0], (Integer) a[1], (Integer) a[2], 1, 1));
        Native.register("GridPane.add(Node,int,int,int,int)", (self, m, a) -> gridAdd(m, a[0], (Integer) a[1], (Integer) a[2],
                (Integer) a[3], (Integer) a[4]));
        Native.register("GridPane.addRow(int,Node[])", (self, m, a) -> {
            Object[] nodes = (Object[]) a[1];
            for (int i = 0; i < nodes.length; i++) {
                gridAdd(m, nodes[i], firstFreeColumn(m, (Integer) a[0]) , (Integer) a[0], 1, 1);
            }
            return null;
        });
        Native.register("GridPane.addColumn(int,Node[])", (self, m, a) -> {
            Object[] nodes = (Object[]) a[1];
            for (Object node : nodes) {
                gridAdd(m, node, (Integer) a[0], firstFreeRow(m, (Integer) a[0]), 1, 1);
            }
            return null;
        });
        Native.register("GridPane.setConstraints(Node,int,int)", (self, m, a) -> constraints(a[0], (Integer) a[1], (Integer) a[2], 1, 1));
        Native.register("GridPane.setConstraints(Node,int,int,int,int)", (self, m, a) -> constraints(a[0], (Integer) a[1],
                (Integer) a[2], (Integer) a[3], (Integer) a[4]));
        Native.register("GridPane.clearConstraints(Node)", (self, m, a) -> {
            NativeModel child = NativeElements.model(a[0]);
            if (child != null) {
                child.constraints.keySet().removeIf(k -> k.startsWith("GridPane."));
                Native.changed(child);
            }
            return null;
        });
        // Region size shorthands
        for (String kind : new String[]{"Pref", "Min", "Max"}) {
            String lower = kind.toLowerCase();
            Native.register("Region.set" + kind + "Size(double,double)", (self, m, a) -> {
                Native.property(m, lower + "Width", double.class).setValue(a[0]);
                Native.property(m, lower + "Height", double.class).setValue(a[1]);
                return null;
            });
        }
        Native.register("Node.relocate(double,double)", (self, m, a) -> {
            Native.property(m, "layoutX", double.class).setValue(a[0]);
            Native.property(m, "layoutY", double.class).setValue(a[1]);
            return null;
        });
        Native.register("Node.getProperties()", (self, m, a) -> once(m, "properties", javafx.collections.FXCollections::observableHashMap)); // JavaFX's map itself, as the API types it
        Native.register("Node.hasProperties()", (self, m, a) -> m.values.get("properties") instanceof java.util.Map
                && !((java.util.Map<?, ?>) m.values.get("properties")).isEmpty());
        Native.register("Node.lookupAll(String)", (self, m, a) -> {
            java.util.Set<Object> found = new java.util.LinkedHashSet<>();
            NativeCss.Selector selector = NativeCss.Selector.parse(String.valueOf(a[0]).trim());
            if (selector != null) {
                collect(m, selector, found, 0);
            }
            return found;
        });
        Native.register("TextInputControl.clear()", (self, m, a) -> {
            Native.property(m, "text", String.class).setValue("");
            return null;
        });
        // Handlers are kept on the node, for the native event dispatch to call (not written yet).
        Native.register("Node.addEventFilter(EventType,EventHandler)", (self, m, a) -> handlers(m, "eventFilters", a));
        Native.register("Node.addEventHandler(EventType,EventHandler)", (self, m, a) -> handlers(m, "eventHandlers", a));
        Native.register("Node.removeEventFilter(EventType,EventHandler)", (self, m, a) -> remove(m, "eventFilters", a));
        Native.register("Node.removeEventHandler(EventType,EventHandler)", (self, m, a) -> remove(m, "eventHandlers", a));

        // Objects JavaFX creates with the control: selection models and editors.
        Native.register("ComboBox.getSelectionModel()", (self, m, a) -> Fx.jx(once(m, "selectionModel", () -> new NativeSelection.Single(m, "items", true))));
        Native.register("ChoiceBox.getSelectionModel()", (self, m, a) -> Fx.jx(once(m, "selectionModel", () -> new NativeSelection.Single(m, "items", true))));
        Native.register("TabPane.getSelectionModel()", (self, m, a) -> Fx.jx(NativeRuntime.tabSelection(m)));
        Native.register("ListView.getSelectionModel()", (self, m, a) -> Fx.jx(once(m, "selectionModel", () -> new NativeSelection.Multiple(m, "items"))));
        for (String control : new String[]{"Spinner", "ComboBox", "DatePicker"}) {
            Native.register(control + ".getEditor()", (self, m, a) -> once(m, "editor", com.jxparallel.fx.scene.control.TextField::new));
        }

        // A spinner's value lives in its value factory, as in JavaFX.
        Native.register("Spinner.getValue()", (self, m, a) -> {
            javafx.scene.control.SpinnerValueFactory<?> factory = factory(m);
            return factory == null ? null : Fx.jx(factory.getValue());
        });
        Native.register("Spinner.valueProperty()", (self, m, a) -> {
            javafx.scene.control.SpinnerValueFactory<?> factory = factory(m);
            return factory == null ? null : Fx.jx(factory.valueProperty());
        });
        Native.register("Spinner.increment(int)", (self, m, a) -> step(m, (Integer) a[0]));
        Native.register("Spinner.decrement(int)", (self, m, a) -> step(m, -(Integer) a[0]));
        Native.register("Spinner.increment()", (self, m, a) -> step(m, 1));
        Native.register("Spinner.decrement()", (self, m, a) -> step(m, -1));

        // Toggle groups: joining adds the toggle to the group; selecting one clears the others.
        // (radio menu items are toggles too)
        for (String toggle : new String[]{"ToggleButton", "RadioMenuItem"}) {
            Native.register(toggle + ".setToggleGroup(ToggleGroup)", (self, m, a) -> {
                Object group = Fx.fx(a[0]);
                Object previous = Native.value(m, "toggleGroup");
                if (previous instanceof NativeModel) {
                    Native.list((NativeModel) previous, "toggles").remove(m);
                }
                Native.property(m, "toggleGroup", Object.class).setValue(group);
                if (group instanceof NativeModel) {
                    Native.list((NativeModel) group, "toggles").add(m);
                }
                return null;
            });
        }
        Native.register("ToggleGroup.selectToggle(Toggle)", (self, m, a) -> {
            Object chosen = Fx.fx(a[0]);
            for (Object toggle : Native.list(m, "toggles")) {
                Native.property((NativeModel) toggle, "selected", boolean.class).setValue(toggle == chosen);
            }
            Native.property(m, "selectedToggle", Object.class).setValue(chosen);
            return null;
        });
    }

    private static Object gridAdd(NativeModel grid, Object jxChild, int column, int row, int columnSpan, int rowSpan) {
        NativeModel child = NativeElements.model(jxChild);
        if (child == null) {
            return null;
        }
        constraints(jxChild, column, row, columnSpan, rowSpan);
        grid.children.add(child);
        return null;
    }

    private static Object constraints(Object jxChild, int column, int row, int columnSpan, int rowSpan) {
        NativeModel child = NativeElements.model(jxChild);
        if (child == null) {
            return null;
        }
        child.constraints.put("GridPane.columnIndex", column);
        child.constraints.put("GridPane.rowIndex", row);
        if (columnSpan != 1) {
            child.constraints.put("GridPane.columnSpan", columnSpan);
        }
        if (rowSpan != 1) {
            child.constraints.put("GridPane.rowSpan", rowSpan);
        }
        Native.changed(child);
        return null;
    }

    /** GridPane.addRow: after the last column taken in that row, spans included (REMAINING counts its start). */
    private static int firstFreeColumn(NativeModel grid, int row) {
        return firstFree(grid, row, "rowIndex", "rowSpan", "columnIndex", "columnSpan");
    }

    /** GridPane.addColumn: after the last row taken in that column, spans included. */
    private static int firstFreeRow(NativeModel grid, int column) {
        return firstFree(grid, column, "columnIndex", "columnSpan", "rowIndex", "rowSpan");
    }

    private static int firstFree(NativeModel grid, int line, String along, String alongSpan, String across, String acrossSpan) {
        int next = 0;
        for (Object o : grid.children) {
            NativeModel c = NativeElements.model(o);
            if (c == null) {
                continue;
            }
            int start = index(c, along);
            int span = span(c, alongSpan);
            boolean inLine = line >= start && (span == Integer.MAX_VALUE || line <= start + span - 1);
            if (inLine) {
                int at = index(c, across);
                int size = span(c, acrossSpan);
                next = Math.max(next, (size == Integer.MAX_VALUE ? at : at + size - 1) + 1);
            }
        }
        return next;
    }

    private static int index(NativeModel c, String name) {
        Object v = c.constraints.get("GridPane." + name);
        return v instanceof Integer ? (Integer) v : 0;
    }

    private static int span(NativeModel c, String name) {
        Object v = c.constraints.get("GridPane." + name);
        return v instanceof Integer && (Integer) v > 0 ? (Integer) v : 1;
    }

    private static void collect(NativeModel m, NativeCss.Selector selector, java.util.Set<Object> found, int depth) {
        if (depth > 200) {
            return;
        }
        if (selector.matches(m, new NativeCss(null))) {
            found.add(Fx.jx(m));
        }
        for (NativeModel child : NativeRuntime.childrenOf(m)) {
            collect(child, selector, found, depth + 1);
        }
    }

    /** A value created on first use and kept on the node (JavaFX creates these with the control). */
    private static Object once(NativeModel m, String name, java.util.function.Supplier<Object> create) {
        Object v = m.values.get(name);
        if (v == null) {
            v = create.get();
            m.values.put(name, v);
        }
        return v;
    }

    static javafx.scene.control.SpinnerValueFactory<?> factory(NativeModel m) {
        Object f = Native.value(m, "valueFactory");
        return f instanceof javafx.scene.control.SpinnerValueFactory ? (javafx.scene.control.SpinnerValueFactory<?>) f : null;
    }

    private static Object step(NativeModel m, int steps) {
        javafx.scene.control.SpinnerValueFactory<?> factory = factory(m);
        if (factory != null) {
            if (steps >= 0) {
                factory.increment(steps);
            } else {
                factory.decrement(-steps);
            }
            NativeText.syncSpinner(m, NativeText.editor(m), true); // like Spinner, the editor shows it at once
        }
        return null;
    }

    private static Object handlers(NativeModel m, String kind, Object[] a) {
        ObservableList<Object> list = Native.list(m, kind);
        list.add(new Object[]{Fx.fx(a[0]), Fx.fx(a[1])});
        return null;
    }

    private static Object remove(NativeModel m, String kind, Object[] a) {
        Object type = Fx.fx(a[0]);
        Object handler = Fx.fx(a[1]);
        Native.list(m, kind).removeIf(o -> ((Object[]) o)[0] == type && ((Object[]) o)[1] == handler);
        return null;
    }
}
