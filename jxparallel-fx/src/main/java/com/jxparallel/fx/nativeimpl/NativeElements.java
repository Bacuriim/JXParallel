package com.jxparallel.fx.nativeimpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

import javafx.geometry.Insets;

/**
 * Turns native-mode JX nodes into the element tree jxparallel-ui renders: each JavaFX class maps to
 * a native element type, and the JavaFX property values the native layout and renderer understand
 * are copied under their native names. Classes without a native element are listed in
 * {@link #unsupported()} and drawn as an empty pane of their preferred size, so the rest of the
 * screen still renders.
 */
public final class NativeElements {
    private static final Set<String> UNSUPPORTED = new ConcurrentSkipListSet<>();

    private NativeElements() {
    }

    public static Set<String> unsupported() {
        return UNSUPPORTED;
    }

    public static JXElement toElement(NativeModel m) {
        String fx = m.fxType.getSimpleName();
        JXProps.Builder p = JXProps.builder();
        common(m, p);
        switch (fx) {
            case "Label":
            case "Text":
                p.set("value", string(m, "text"));
                return JXElement.of("#text", p.build());
            case "Button":
                p.set("label", string(m, "text"));
                return JXElement.of("button", p.build());
            case "ToggleButton":
                p.set("label", string(m, "text"));
                return JXElement.of("toggle", p.build());
            case "CheckBox":
                p.set("label", string(m, "text")).set("checked", Boolean.TRUE.equals(Native.value(m, "selected")));
                return JXElement.of("checkbox", p.build());
            case "TextField":
                p.set("value", string(m, "text"));
                return JXElement.of("input", p.build());
            case "PasswordField":
                p.set("value", string(m, "text"));
                return JXElement.of("password", p.build());
            case "TextArea":
                p.set("value", string(m, "text"));
                return JXElement.of("textarea", p.build());
            case "ComboBox":
            case "ChoiceBox":
                Object value = Native.value(m, "value");
                List<Object> options = new ArrayList<>();
                Object items = m.values.get("items");
                if (items instanceof List) {
                    for (Object o : (List<?>) items) {
                        options.add(String.valueOf(o));
                    }
                }
                // Like JavaFX: the prompt text shows while nothing is selected.
                p.set("value", value != null ? String.valueOf(value) : string(m, "promptText")).set("options", options.toArray());
                return JXElement.of("select", p.build());
            case "ProgressBar":
                p.set("progress", number(m, "progress", 0));
                return JXElement.of("progress", p.build());
            case "Slider":
                p.set("min", number(m, "min", 0)).set("max", number(m, "max", 100)).set("value", number(m, "value", 0));
                return JXElement.of("slider", p.build());
            case "VBox":
                p.set("gap", number(m, "spacing", 0));
                alignment(m, p);
                p.set("fillWidth", Native.value(m, "fillWidth"));
                return container("column", m, p);
            case "HBox":
                p.set("gap", number(m, "spacing", 0));
                alignment(m, p);
                p.set("fillHeight", Native.value(m, "fillHeight"));
                return container("row", m, p);
            case "StackPane":
                alignment(m, p);
                return container("stack", m, p);
            case "BorderPane":
                return container("border", m, p);
            case "GridPane":
                p.set("hgap", number(m, "hgap", 0)).set("vgap", number(m, "vgap", 0));
                return container("grid", m, p);
            case "AnchorPane":
                return container("anchor", m, p);
            case "Pane":
                return container("pane", m, p);
            case "FlowPane":
                p.set("hgap", number(m, "hgap", 0)).set("vgap", number(m, "vgap", 0));
                return container("flow", m, p);
            case "TilePane":
                return container("tile", m, p);
            default:
                UNSUPPORTED.add(m.fxType.getName());
                return container("pane", m, p);
        }
    }

    private static JXElement container(String type, NativeModel m, JXProps.Builder p) {
        List<JXElement> children = new ArrayList<>();
        for (Object child : m.children) {
            // Like JavaFX: an invisible node keeps its place in the layout unless it is also unmanaged.
            if (child instanceof NativeModel && !Boolean.FALSE.equals(Native.value((NativeModel) child, "managed"))) {
                children.add(toElement((NativeModel) child));
            }
        }
        return JXElement.of(type, p.build(), children.toArray(new JXElement[0]));
    }

    /** Sizes, padding and what the parent layout attached to this node. */
    private static void common(NativeModel m, JXProps.Builder p) {
        for (String size : new String[]{"minWidth", "prefWidth", "maxWidth", "minHeight", "prefHeight", "maxHeight"}) {
            double v = number(m, size, -1);
            if (v != -1) {
                // JavaFX USE_PREF_SIZE (-Infinity) means "same as pref"; the native layout reads it the same way.
                p.set(size, v);
            }
        }
        Object padding = Native.value(m, "padding");
        if (padding instanceof Insets) {
            p.set("padding", insets((Insets) padding));
        }
        if (Boolean.FALSE.equals(Native.value(m, "visible"))) {
            p.set("hidden", true);
        }
        if (Boolean.TRUE.equals(Native.value(m, "disable"))) {
            p.set("disabled", true);
        }
        double x = number(m, "layoutX", 0);
        double y = number(m, "layoutY", 0);
        if (x != 0 || y != 0) {
            p.set("layoutX", x).set("layoutY", y);
        }
        for (String anchor : new String[]{"top", "right", "bottom", "left"}) {
            Object v = Native.constraint(m, "AnchorPane." + anchor + "Anchor");
            if (v instanceof Number) {
                p.set(anchor + "Anchor", ((Number) v).doubleValue());
            }
        }
        for (String layout : new String[]{"VBox", "HBox", "StackPane", "BorderPane", "GridPane", "FlowPane", "TilePane"}) {
            Object margin = Native.constraint(m, layout + ".margin");
            if (margin instanceof Insets) {
                p.set("margin", insets((Insets) margin));
            }
        }
        Object vgrow = Native.constraint(m, "VBox.vgrow");
        if (vgrow != null) {
            p.set("vgrow", vgrow.toString().toLowerCase());
        }
        Object hgrow = Native.constraint(m, "HBox.hgrow");
        if (hgrow != null) {
            p.set("hgrow", hgrow.toString().toLowerCase());
        }
        grid(m, p);
    }

    private static void grid(NativeModel m, JXProps.Builder p) {
        Object column = Native.constraint(m, "GridPane.columnIndex");
        Object row = Native.constraint(m, "GridPane.rowIndex");
        if (column instanceof Number) {
            p.set("column", ((Number) column).intValue());
        }
        if (row instanceof Number) {
            p.set("row", ((Number) row).intValue());
        }
        Object columnSpan = Native.constraint(m, "GridPane.columnSpan");
        Object rowSpan = Native.constraint(m, "GridPane.rowSpan");
        if (columnSpan instanceof Number) {
            p.set("columnSpan", ((Number) columnSpan).intValue());
        }
        if (rowSpan instanceof Number) {
            p.set("rowSpan", ((Number) rowSpan).intValue());
        }
    }

    private static void alignment(NativeModel m, JXProps.Builder p) {
        Object a = Native.value(m, "alignment");
        if (a instanceof Enum) {
            p.set("alignment", ((Enum<?>) a).name());
        }
    }

    private static double[] insets(Insets i) {
        return new double[]{i.getTop(), i.getRight(), i.getBottom(), i.getLeft()};
    }

    private static String string(NativeModel m, String name) {
        Object v = Native.value(m, name);
        return v == null ? "" : v.toString();
    }

    private static double number(NativeModel m, String name, double fallback) {
        Object v = Native.value(m, name);
        return v instanceof Number ? ((Number) v).doubleValue() : fallback;
    }
}
