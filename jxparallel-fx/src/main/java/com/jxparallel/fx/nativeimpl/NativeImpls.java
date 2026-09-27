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
        Native.register("TabPane.getSelectionModel()", (self, m, a) -> Fx.jx(once(m, "selectionModel", () -> new NativeSelection.Single(m, "tabs", false))));
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
        Native.register("ToggleButton.setToggleGroup(ToggleGroup)", (self, m, a) -> {
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
        Native.register("ToggleGroup.selectToggle(Toggle)", (self, m, a) -> {
            Object chosen = Fx.fx(a[0]);
            for (Object toggle : Native.list(m, "toggles")) {
                Native.property((NativeModel) toggle, "selected", boolean.class).setValue(toggle == chosen);
            }
            Native.property(m, "selectedToggle", Object.class).setValue(chosen);
            return null;
        });
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
