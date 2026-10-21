package com.jxparallel.forge;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The JXML vocabulary: each tag, the native element it becomes, its attributes and their prop
 * names and types. The interpreter and the compiler both read this table and nothing else, so
 * a screen means the same thing in both. Prop names and types follow what the native renderer
 * reads ({@code JXNativeNode}, {@code JXControlLayout}) and what the JavaFX-compatible layer sends it.
 */
public final class JxmlSchema {
    /** How many children a tag takes. */
    public enum Children { NONE, ONE, MANY, TABS, TAB }

    /** One attribute: its JXML name, the prop it sets, its type, and whether {@code ${...}} may bind it. */
    public static final class Attr {
        public final String name;
        public final String prop;
        public final AttrType type;
        public final boolean bindable;

        Attr(String name, String prop, AttrType type, boolean bindable) {
            this.name = name;
            this.prop = prop;
            this.type = type;
            this.bindable = bindable;
        }
    }

    /** One tag. {@code nativeType} is null for {@code Tab}, which only exists inside {@code Tabs}. */
    public static final class Tag {
        public final String name;
        public final String nativeType;
        public final Children children;
        public final Map<String, Attr> attrs;
        /** Props every element of this tag gets unless an attribute sets them, as the JavaFX layer sends them. */
        public final Map<String, Object> defaults;
        /** For, If and Else: part of the language, not an element; they take only their own attributes. */
        public final boolean control;
        /** The attribute that @{...} binds two ways and onChange reports, or null. */
        public final String changeAttr;
        /** The Java type of the value onChange passes: STRING, BOOLEAN or DOUBLE. */
        public final AttrType changeType;

        Tag(String name, String nativeType, Children children, Map<String, Attr> attrs, Map<String, Object> defaults,
            boolean control, String changeAttr, AttrType changeType) {
            this.name = name;
            this.nativeType = nativeType;
            this.children = children;
            this.attrs = Collections.unmodifiableMap(attrs);
            this.defaults = Collections.unmodifiableMap(defaults);
            this.control = control;
            this.changeAttr = changeAttr;
            this.changeType = changeType;
        }

        /** The tag's own attribute, or a layout attribute every tag accepts; null if neither. */
        public Attr attr(String name) {
            Attr own = attrs.get(name);
            return own != null || control ? own : LAYOUT.get(name);
        }
    }

    /** Placement and sizing, valid on every tag: read by the parent's layout. */
    static final Map<String, Attr> LAYOUT = new LinkedHashMap<String, Attr>();
    private static final Map<String, Tag> TAGS = new LinkedHashMap<String, Tag>();

    static {
        layout("key", "key", AttrType.STRING, false);
        layout("margin", "margin", AttrType.INSETS, false);
        layout("hgrow", "hgrow", AttrType.STRING, false);
        layout("vgrow", "vgrow", AttrType.STRING, false);
        for (String size : new String[] {"minWidth", "prefWidth", "maxWidth", "minHeight", "prefHeight", "maxHeight"}) {
            layout(size, size, AttrType.DOUBLE, true);
        }
        layout("row", "row", AttrType.INT, false);
        layout("column", "column", AttrType.INT, false);
        layout("rowSpan", "rowSpan", AttrType.INT, false);
        layout("columnSpan", "columnSpan", AttrType.INT, false);
        layout("halignment", "halignment", AttrType.STRING, false);
        layout("valignment", "valignment", AttrType.STRING, false);
        layout("slot", "position", AttrType.STRING, false); // top, left, center, right, bottom of a Border

        tag("Column", "column", Children.MANY)
                .attr("gap", "gap", AttrType.DOUBLE, true).attr("padding", "padding", AttrType.INSETS, false)
                .attr("alignment", "alignment", AttrType.STRING, false).attr("fillWidth", "fillWidth", AttrType.BOOLEAN, false)
                .done();
        tag("Row", "row", Children.MANY)
                .attr("gap", "gap", AttrType.DOUBLE, true).attr("padding", "padding", AttrType.INSETS, false)
                .attr("alignment", "alignment", AttrType.STRING, false).attr("fillHeight", "fillHeight", AttrType.BOOLEAN, false)
                .done();
        tag("Stack", "stack", Children.MANY)
                .attr("padding", "padding", AttrType.INSETS, false).attr("alignment", "alignment", AttrType.STRING, false)
                .done();
        tag("Grid", "grid", Children.MANY)
                .attr("hgap", "hgap", AttrType.DOUBLE, false).attr("vgap", "vgap", AttrType.DOUBLE, false)
                .attr("padding", "padding", AttrType.INSETS, false).attr("alignment", "alignment", AttrType.STRING, false)
                .done();
        tag("Border", "border", Children.MANY)
                .attr("padding", "padding", AttrType.INSETS, false)
                .done();
        tag("Scroll", "scroll", Children.ONE)
                .attr("fitToWidth", "fitToWidth", AttrType.BOOLEAN, false).attr("fitToHeight", "fitToHeight", AttrType.BOOLEAN, false)
                .done();
        tag("Tabs", "tabs", Children.TABS)
                .attr("selected", "selected", AttrType.INT, true)
                .def("selected", Integer.valueOf(0)).def("retainChildren", Boolean.TRUE).def("closingPolicy", "SELECTED_TAB")
                .done();
        tag("Tab", null, Children.TAB)
                .attr("title", "title", AttrType.STRING, false).attr("closable", "closable", AttrType.BOOLEAN, false)
                .done();

        // The language: loops and conditions. Their children are placed in the parent, in order.
        tag("For", null, Children.MANY).control()
                .attr("items", "items", AttrType.OBJECT, true).attr("var", "var", AttrType.STRING, false)
                .attr("type", "type", AttrType.STRING, false).attr("key", "key", AttrType.OBJECT, true)
                .done();
        tag("If", null, Children.MANY).control()
                .attr("test", "test", AttrType.BOOLEAN, true)
                .done();
        tag("Else", null, Children.MANY).control().done();

        // Replaced by what the binding returns: a JXElement or JXComponent written in Java.
        tag("Slot", null, Children.NONE)
                .attr("content", "content", AttrType.ELEMENT, true)
                .done();

        tag("Label", "#text", Children.NONE)
                .attr("text", "value", AttrType.STRING, true).attr("wrapText", "wrapText", AttrType.BOOLEAN, false)
                .def("value", "")
                .done();
        tag("Button", "button", Children.NONE)
                .attr("text", "label", AttrType.STRING, true).attr("onAction", "onAction", AttrType.HANDLER, false)
                .attr("disabled", "disabled", AttrType.BOOLEAN, true).attr("defaultButton", "defaultButton", AttrType.BOOLEAN, false)
                .def("label", "")
                .done();
        tag("TextField", "input", Children.NONE)
                .attr("text", "value", AttrType.STRING, true).attr("prompt", "prompt", AttrType.STRING, true)
                .attr("columns", "prefColumnCount", AttrType.DOUBLE, false)
                .def("value", "").def("prompt", "").def("prefColumnCount", Double.valueOf(12))
                .change("text", AttrType.STRING)
                .done();
        tag("PasswordField", "password", Children.NONE)
                .attr("text", "value", AttrType.STRING, true).attr("prompt", "prompt", AttrType.STRING, true)
                .attr("columns", "prefColumnCount", AttrType.DOUBLE, false)
                .def("value", "").def("prompt", "").def("prefColumnCount", Double.valueOf(12))
                .change("text", AttrType.STRING)
                .done();
        tag("TextArea", "textarea", Children.NONE)
                .attr("text", "value", AttrType.STRING, true).attr("prompt", "prompt", AttrType.STRING, true)
                .attr("wrapText", "wrapText", AttrType.BOOLEAN, false)
                .attr("columns", "prefColumnCount", AttrType.DOUBLE, false).attr("rows", "prefRowCount", AttrType.DOUBLE, false)
                .def("value", "").def("prompt", "").def("wrapText", Boolean.FALSE).def("scrollTop", Double.valueOf(0))
                .def("prefColumnCount", Double.valueOf(40)).def("prefRowCount", Double.valueOf(10))
                .change("text", AttrType.STRING)
                .done();
        tag("CheckBox", "checkbox", Children.NONE)
                .attr("text", "label", AttrType.STRING, true).attr("selected", "checked", AttrType.BOOLEAN, true)
                .def("label", "").def("checked", Boolean.FALSE).def("indeterminate", Boolean.FALSE)
                .change("selected", AttrType.BOOLEAN)
                .done();
        tag("ComboBox", "select", Children.NONE)
                .attr("value", "value", AttrType.STRING, true).attr("prompt", "prompt", AttrType.STRING, true)
                .attr("items", "options", AttrType.STRINGS, true)
                .def("value", "").def("prompt", "").def("options", new String[0]).def("showing", Boolean.FALSE)
                .change("value", AttrType.STRING)
                .done();
        tag("Spinner", "spinner", Children.NONE)
                .attr("value", "value", AttrType.STRING, true)
                .attr("min", "min", AttrType.DOUBLE, false).attr("max", "max", AttrType.DOUBLE, false)
                .def("value", "")
                .change("value", AttrType.DOUBLE)
                .done();
        tag("DatePicker", "datepicker", Children.NONE)
                .attr("value", "value", AttrType.STRING, true).attr("prompt", "prompt", AttrType.STRING, true)
                .def("value", "").def("prompt", "").def("showing", Boolean.FALSE)
                .change("value", AttrType.STRING)
                .done();
        tag("Separator", "separator", Children.NONE)
                .attr("orientation", "orientation", AttrType.STRING, false)
                .def("orientation", "horizontal")
                .done();
        tag("ProgressBar", "progress", Children.NONE)
                .attr("progress", "progress", AttrType.DOUBLE, true)
                .def("progress", Double.valueOf(-1))
                .done();
        tag("Slider", "slider", Children.NONE)
                .attr("min", "min", AttrType.DOUBLE, false).attr("max", "max", AttrType.DOUBLE, false)
                .attr("value", "value", AttrType.DOUBLE, true)
                .def("min", Double.valueOf(0)).def("max", Double.valueOf(100)).def("value", Double.valueOf(0))
                .change("value", AttrType.DOUBLE)
                .done();
    }

    private JxmlSchema() {
    }

    /** The tag of this name, or null. */
    public static Tag tag(String name) {
        return TAGS.get(name);
    }

    public static Map<String, Tag> tags() {
        return Collections.unmodifiableMap(TAGS);
    }

    private static void layout(String name, String prop, AttrType type, boolean bindable) {
        LAYOUT.put(name, new Attr(name, prop, type, bindable));
    }

    private static TagBuilder tag(String name, String nativeType, Children children) {
        return new TagBuilder(name, nativeType, children);
    }

    private static final class TagBuilder {
        private final String name;
        private final String nativeType;
        private final Children children;
        private final Map<String, Attr> attrs = new LinkedHashMap<String, Attr>();
        private final Map<String, Object> defaults = new LinkedHashMap<String, Object>();
        private boolean control;
        private String changeAttr;
        private AttrType changeType;

        TagBuilder(String name, String nativeType, Children children) {
            this.name = name;
            this.nativeType = nativeType;
            this.children = children;
        }

        TagBuilder attr(String attr, String prop, AttrType type, boolean bindable) {
            attrs.put(attr, new Attr(attr, prop, type, bindable));
            return this;
        }

        TagBuilder def(String prop, Object value) {
            defaults.put(prop, value);
            return this;
        }

        TagBuilder control() {
            control = true;
            return this;
        }

        /** Adds onChange, which gets the new value of attr as a type; @{...} binds attr two ways. */
        TagBuilder change(String attr, AttrType type) {
            attrs.put("onChange", new Attr("onChange", "onChange", AttrType.CHANGE, false));
            changeAttr = attr;
            changeType = type;
            return this;
        }

        void done() {
            TAGS.put(name, new Tag(name, nativeType, children, attrs, defaults, control, changeAttr, changeType));
        }
    }
}
