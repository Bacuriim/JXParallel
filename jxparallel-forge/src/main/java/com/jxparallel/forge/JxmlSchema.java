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
    public enum Children { NONE, ONE, MANY, TABS, TAB, TEMPLATE }

    /** One attribute: its JXML name, the prop it sets, its type, and whether {@code ${...}} may bind it. */
    public static final class Attr {
        public final String name;
        public final String prop;
        public final AttrType type;
        public final boolean bindable;
        /** For a STRING written as a literal: the only values allowed (checked when the file is read), or null for any. */
        public final java.util.List<String> choices;
        /** For a CHANGE attribute: the type of the value the handler gets (STRING, BOOLEAN, DOUBLE, INT or DOUBLES). */
        public final AttrType valueType;

        Attr(String name, String prop, AttrType type, boolean bindable) {
            this(name, prop, type, bindable, null, null);
        }

        Attr(String name, String prop, AttrType type, boolean bindable, java.util.List<String> choices, AttrType valueType) {
            this.name = name;
            this.prop = prop;
            this.type = type;
            this.bindable = bindable;
            this.choices = choices;
            this.valueType = valueType;
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
        /**
         * A tag that stands for other tags: {@code JxmlComposites} rewrites it into primitive tags before the
         * interpreter and the compiler see it, so it has no native element of its own.
         */
        public final boolean composite;
        /** For a composite that only renames a primitive tag: that tag, and the attributes it gets unless written. */
        public final String alias;
        public final Map<String, String> aliasAttrs;
        /**
         * ListView and TableView: one child, the template of a row; the rows come from {@code items}, a window of
         * {@code rows} of them from {@code first} (a {@code <For from limit>} the resolver writes around the template).
         */
        public final boolean virtual;
        /** The built props go through {@code JxmlRuntime.finish}, which derives some of them from others. */
        public final boolean finish;
        /** Attributes that must be written. */
        public final java.util.List<String> required;
        /**
         * Tree and TreeTable: the children are tree items (nested, with loops and conditions); the built props and the
         * items go through {@code JxmlRuntime.tree}, which flattens the visible items into the rows the renderer draws.
         */
        public final boolean tree;
        /** The tags allowed as children (loops and conditions only hold them), or null for any. */
        public final java.util.List<String> only;
        /** The tags this tag may stand directly inside (loops and conditions do not count), or null for any. */
        public final java.util.List<String> parents;

        Tag(String name, String nativeType, Children children, Map<String, Attr> attrs, Map<String, Object> defaults,
            boolean control, String changeAttr, AttrType changeType, boolean composite, String alias,
            Map<String, String> aliasAttrs, boolean virtual, boolean finish, java.util.List<String> required,
            boolean tree, java.util.List<String> only, java.util.List<String> parents) {
            this.tree = tree;
            this.only = only == null ? null : Collections.unmodifiableList(only);
            this.parents = parents == null ? null : Collections.unmodifiableList(parents);
            this.required = Collections.unmodifiableList(required);
            this.composite = composite;
            this.alias = alias;
            this.aliasAttrs = Collections.unmodifiableMap(aliasAttrs);
            this.virtual = virtual;
            this.finish = finish;
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
    /** The looks of a Button; {@code fab} is what a Fab expands to. */
    static final java.util.List<String> VARIANTS = Collections.unmodifiableList(
            java.util.Arrays.asList("filled", "tonal", "outlined", "text", "elevated", "fab"));
    static final java.util.List<String> ORIENTATIONS = Collections.unmodifiableList(java.util.Arrays.asList("horizontal", "vertical"));
    static final java.util.List<String> CHIP_KINDS = Collections.unmodifiableList(
            java.util.Arrays.asList("assist", "filter", "choice", "input"));
    static final java.util.List<String> SIDES = Collections.unmodifiableList(java.util.Arrays.asList("left", "right"));

    static {
        layout("theme", "theme", AttrType.STRING, false); // root only: light or dark, inherited by the renderer
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
        // placement in an Anchor (the renderer's topAnchor... props) and in a Pane (layoutX, layoutY)
        layout("top", "topAnchor", AttrType.DOUBLE, true);
        layout("right", "rightAnchor", AttrType.DOUBLE, true);
        layout("bottom", "bottomAnchor", AttrType.DOUBLE, true);
        layout("left", "leftAnchor", AttrType.DOUBLE, true);
        layout("x", "layoutX", AttrType.DOUBLE, true);
        layout("y", "layoutY", AttrType.DOUBLE, true);
        // what the renderer paints for any node (JXPaint.paint and JXPaint.region) and picks (hitPath)
        layout("opacity", "opacity", AttrType.DOUBLE, true);
        layout("hidden", "hidden", AttrType.BOOLEAN, true);
        layout("mouseTransparent", "mouseTransparent", AttrType.BOOLEAN, false);
        // a click: the renderer calls the handler of the deepest element under the pointer (JXNativeNode.dispatchPointer)
        layout("onAction", "onAction", AttrType.HANDLER, false);
        layout("background", "background", AttrType.COLOR, true);
        layout("backgroundRadius", "backgroundRadius", AttrType.DOUBLE, true);
        layout("borderColor", "borderColor", AttrType.COLOR, true);
        layout("borderWidth", "borderWidth", AttrType.DOUBLE, true);
        layout("borderRadius", "borderRadius", AttrType.DOUBLE, true);

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
                .attr("title", "title", AttrType.STRING, true).attr("closable", "closable", AttrType.BOOLEAN, false)
                .done();

        // The language: loops and conditions. Their children are placed in the parent, in order.
        tag("For", null, Children.MANY).control()
                .attr("items", "items", AttrType.OBJECT, true).attr("var", "var", AttrType.STRING, false)
                .attr("type", "type", AttrType.STRING, false).attr("key", "key", AttrType.OBJECT, true)
                .attr("from", "from", AttrType.INT, true).attr("limit", "limit", AttrType.INT, true)
                .attr("memo", "memo", AttrType.BOOLEAN, false)
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
                .attr("bold", "bold", AttrType.BOOLEAN, false).attr("fontSize", "fontSize", AttrType.DOUBLE, false)
                .attr("textFill", "textFill", AttrType.COLOR, true)
                .def("value", "")
                .done();
        tag("Button", "button", Children.ONE) // an optional child is its graphic, left of the text
                .attr("text", "label", AttrType.STRING, true).attr("onAction", "onAction", AttrType.HANDLER, false)
                .attr("disabled", "disabled", AttrType.BOOLEAN, true).attr("defaultButton", "defaultButton", AttrType.BOOLEAN, false)
                .choice("variant", "variant", VARIANTS)
                .attr("round", "round", AttrType.BOOLEAN, false).attr("padding", "padding", AttrType.INSETS, false)
                .attr("textFill", "textFill", AttrType.COLOR, true)
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
                .def("overlay", Boolean.TRUE) // the node draws its own popup over the window (JXOverlays)
                .overlay()
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
                .def("value", "").def("prompt", "").def("showing", Boolean.FALSE).def("overlay", Boolean.TRUE)
                .overlay()
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

        // Material-style controls drawn by the renderer (JXControlLayout, JXPaint) with the theme's tokens.
        tag("Switch", "switch", Children.NONE)
                .attr("text", "label", AttrType.STRING, true).attr("selected", "checked", AttrType.BOOLEAN, true)
                .attr("disabled", "disabled", AttrType.BOOLEAN, true)
                .def("label", "").def("checked", Boolean.FALSE)
                .change("selected", AttrType.BOOLEAN)
                .done();
        tag("Icon", "icon", Children.NONE).required("name")
                .choice("name", "name", JxmlThemes.ICONS)
                .attr("size", "size", AttrType.DOUBLE, true).attr("color", "color", AttrType.COLOR, true)
                .def("size", Double.valueOf(24))
                .done();
        tag("Chip", "chip", Children.NONE)
                .attr("text", "label", AttrType.STRING, true).choice("kind", "kind", CHIP_KINDS)
                .choice("icon", "icon", JxmlThemes.ICONS)
                .attr("selected", "selected", AttrType.BOOLEAN, true).attr("closable", "closable", AttrType.BOOLEAN, false)
                .attr("onClose", "onClose", AttrType.HANDLER, false).attr("disabled", "disabled", AttrType.BOOLEAN, true)
                .def("label", "").def("kind", "assist").def("selected", Boolean.FALSE)
                .change("selected", AttrType.BOOLEAN)
                .done();
        tag("Badge", "badge", Children.ONE)
                .attr("count", "count", AttrType.INT, true).attr("text", "text", AttrType.STRING, true)
                .attr("dot", "dot", AttrType.BOOLEAN, true).attr("max", "max", AttrType.INT, false)
                .attr("color", "badgeColor", AttrType.COLOR, true)
                .done();
        tag("Avatar", "avatar", Children.NONE)
                .attr("size", "size", AttrType.DOUBLE, true).attr("initials", "initials", AttrType.STRING, true)
                .attr("pixels", "pixels", AttrType.PIXELS, true)
                .attr("imageWidth", "imageWidth", AttrType.DOUBLE, true).attr("imageHeight", "imageHeight", AttrType.DOUBLE, true)
                .attr("color", "fill", AttrType.COLOR, true).attr("textFill", "textFill", AttrType.COLOR, true)
                .def("size", Double.valueOf(40))
                .done();
        tag("Stepper", "stepper", Children.NONE)
                .attr("titles", "titles", AttrType.STRINGS, true).attr("steps", "steps", AttrType.INT, true)
                .attr("current", "current", AttrType.INT, true)
                .def("current", Integer.valueOf(0))
                .done();
        tag("ScrollBar", "scrollbar", Children.NONE)
                .attr("min", "min", AttrType.DOUBLE, false).attr("max", "max", AttrType.DOUBLE, false)
                .attr("value", "value", AttrType.DOUBLE, true).attr("visibleAmount", "visibleAmount", AttrType.DOUBLE, false)
                .choice("orientation", "orientation", ORIENTATIONS)
                .def("min", Double.valueOf(0)).def("max", Double.valueOf(100)).def("value", Double.valueOf(0))
                .def("visibleAmount", Double.valueOf(10)).def("orientation", "horizontal")
                .change("value", AttrType.DOUBLE)
                .done();
        tag("SplitPane", "splitpane", Children.MANY)
                .choice("orientation", "orientation", ORIENTATIONS)
                .attr("dividerPositions", "dividerPositions", AttrType.DOUBLES, true)
                .def("orientation", "horizontal")
                .change("dividerPositions", AttrType.DOUBLES)
                .done();
        tag("TabBar", "tabbar", Children.NONE)
                .attr("titles", "titles", AttrType.STRINGS, true).attr("selected", "selected", AttrType.INT, true)
                .attr("closable", "closable", AttrType.BOOLEAN, false)
                .handler("onClose", AttrType.INT)
                .def("titles", new String[0]).def("selected", Integer.valueOf(0)).def("closable", Boolean.FALSE)
                .change("selected", AttrType.INT)
                .done();
        // Overlays (JXOverlays in the renderer): popups of pickers, menus, and layers over the window.
        tag("TimePicker", "timepicker", Children.NONE)
                .attr("value", "value", AttrType.STRING, true).attr("prompt", "prompt", AttrType.STRING, true)
                .attr("hour24", "hour24", AttrType.BOOLEAN, true).attr("minuteStep", "minuteStep", AttrType.INT, true)
                .attr("disabled", "disabled", AttrType.BOOLEAN, true)
                .def("value", "").def("prompt", "").def("hour24", Boolean.TRUE).def("minuteStep", Integer.valueOf(5))
                .def("showing", Boolean.FALSE)
                .overlay()
                .change("value", AttrType.STRING)
                .done();
        tag("ColorPicker", "colorpicker", Children.NONE)
                .attr("value", "value", AttrType.STRING, true).attr("prompt", "prompt", AttrType.STRING, true)
                .attr("disabled", "disabled", AttrType.BOOLEAN, true)
                .def("value", "").def("prompt", "").def("showing", Boolean.FALSE)
                .overlay()
                .change("value", AttrType.STRING)
                .done();
        tag("MenuButton", "menubutton", Children.MANY).only("MenuItem", "Menu")
                .attr("text", "label", AttrType.STRING, true).attr("disabled", "disabled", AttrType.BOOLEAN, true)
                .def("label", "")
                .overlay()
                .done();
        tag("ContextMenu", "contextmenu", Children.MANY).only("MenuItem", "Menu")
                .attr("disabled", "disabled", AttrType.BOOLEAN, true)
                .overlay()
                .done();
        tag("MenuBar", "menubar", Children.MANY).only("Menu")
                .done();
        tag("Menu", "menu", Children.MANY).only("MenuItem", "Menu").parents("MenuBar", "MenuButton", "ContextMenu", "Menu")
                .attr("text", "label", AttrType.STRING, true).attr("disabled", "disabled", AttrType.BOOLEAN, true)
                .def("label", "")
                .done();
        tag("MenuItem", "menuitem", Children.NONE).parents("MenuButton", "ContextMenu", "Menu")
                .attr("text", "label", AttrType.STRING, true).attr("accelerator", "accelerator", AttrType.STRING, true)
                .attr("disabled", "disabled", AttrType.BOOLEAN, true).attr("onAction", "onAction", AttrType.HANDLER, false)
                .attr("separator", "separator", AttrType.BOOLEAN, false)
                .def("label", "")
                .done();
        tag("Modal", "dialog", Children.MANY)
                .attr("dismissible", "dismissible", AttrType.BOOLEAN, true)
                .attr("padding", "padding", AttrType.INSETS, false).attr("gap", "gap", AttrType.DOUBLE, true)
                .attr("alignment", "alignment", AttrType.STRING, false).attr("fillWidth", "fillWidth", AttrType.BOOLEAN, false)
                .def("showing", Boolean.FALSE).def("dismissible", Boolean.TRUE)
                .overlay()
                .done();
        tag("Drawer", "drawer", Children.MANY)
                .choice("side", "side", SIDES)
                .attr("padding", "padding", AttrType.INSETS, false).attr("gap", "gap", AttrType.DOUBLE, true)
                .attr("alignment", "alignment", AttrType.STRING, false).attr("fillWidth", "fillWidth", AttrType.BOOLEAN, false)
                .def("showing", Boolean.FALSE).def("side", "left")
                .overlay()
                .done();
        tag("SnackBar", "snackbar", Children.NONE)
                .attr("message", "message", AttrType.STRING, true).attr("action", "action", AttrType.STRING, true)
                .attr("onAction", "onAction", AttrType.HANDLER, false).attr("duration", "duration", AttrType.DOUBLE, true)
                .def("message", "").def("showing", Boolean.FALSE)
                .overlay()
                .done();
        tag("Dialog", null, Children.MANY).composite()
                .attr("title", "title", AttrType.STRING, true).attr("dismissible", "dismissible", AttrType.BOOLEAN, true)
                .overlay()
                .done();
        tag("AlertDialog", null, Children.NONE).composite()
                .attr("title", "title", AttrType.STRING, true).attr("message", "message", AttrType.STRING, true)
                .attr("confirmText", "confirmText", AttrType.STRING, true).attr("cancelText", "cancelText", AttrType.STRING, true)
                .attr("onConfirm", "onConfirm", AttrType.HANDLER, false).attr("onCancel", "onCancel", AttrType.HANDLER, false)
                .attr("dismissible", "dismissible", AttrType.BOOLEAN, true)
                .overlay()
                .done();
        alias("MenuSeparator", "MenuItem", "separator", "true");

        // Trees: nested items flattened into the rows of a list or a table (JxmlRuntime.tree).
        tag("Tree", "list", Children.MANY).tree().only("TreeItem")
                .attr("rowHeight", "cellHeight", AttrType.DOUBLE, true).attr("first", "first", AttrType.INT, true)
                .attr("rows", "rows", AttrType.INT, true)
                .def("first", Integer.valueOf(0)).def("cellHeight", Double.valueOf(24))
                .done();
        tag("TreeTable", "table", Children.MANY).tree().only("TreeItem")
                .attr("rowHeight", "rowHeight", AttrType.DOUBLE, true).attr("first", "first", AttrType.INT, true)
                .attr("rows", "rows", AttrType.INT, true)
                .attr("columns", "columns", AttrType.STRINGS, true).attr("columnWidths", "columnWidths", AttrType.DOUBLES, true)
                .attr("constrained", "constrained", AttrType.BOOLEAN, false)
                .def("first", Integer.valueOf(0)).def("rowHeight", Double.valueOf(24)).def("columns", new String[0])
                .def("constrained", Boolean.FALSE)
                .done();
        tag("TreeItem", "cell", Children.MANY).only("TreeItem").parents("Tree", "TreeTable", "TreeItem")
                .attr("text", "value", AttrType.STRING, true).attr("expanded", "expanded", AttrType.BOOLEAN, true)
                .attr("selected", "selected", AttrType.BOOLEAN, true).attr("listFocused", "listFocused", AttrType.BOOLEAN, true)
                .attr("values", "values", AttrType.STRINGS, true)
                .attr("onAction", "onAction", AttrType.HANDLER, false).attr("onToggle", "onToggle", AttrType.HANDLER, false)
                .def("value", "").def("indent", Double.valueOf(0)).def("disclosure", "leaf")
                .done();

        // composites that become a Button with an Icon (JxmlComposites)
        tag("IconButton", null, Children.NONE).composite()
                .choice("icon", "icon", JxmlThemes.ICONS).required("icon")
                .attr("size", "size", AttrType.DOUBLE, false).attr("color", "color", AttrType.COLOR, false)
                .choice("variant", "variant", VARIANTS)
                .attr("disabled", "disabled", AttrType.BOOLEAN, true)
                .done();
        tag("Fab", null, Children.NONE).composite()
                .choice("icon", "icon", JxmlThemes.ICONS).attr("text", "text", AttrType.STRING, true)
                .attr("disabled", "disabled", AttrType.BOOLEAN, true)
                .done();

        // Containers the renderer already lays out (JXFlowLayout, JXBoxLayout).
        tag("Flow", "flow", Children.MANY)
                .attr("hgap", "hgap", AttrType.DOUBLE, true).attr("vgap", "vgap", AttrType.DOUBLE, true)
                .attr("padding", "padding", AttrType.INSETS, false).attr("alignment", "alignment", AttrType.STRING, false)
                .attr("orientation", "orientation", AttrType.STRING, false)
                .attr("prefWrapLength", "prefWrapLength", AttrType.DOUBLE, false)
                .attr("columnHalignment", "columnHalignment", AttrType.STRING, false)
                .attr("rowValignment", "rowValignment", AttrType.STRING, false)
                .done();
        tag("Tile", "tile", Children.MANY)
                .attr("hgap", "hgap", AttrType.DOUBLE, true).attr("vgap", "vgap", AttrType.DOUBLE, true)
                .attr("padding", "padding", AttrType.INSETS, false).attr("alignment", "alignment", AttrType.STRING, false)
                .attr("orientation", "orientation", AttrType.STRING, false)
                .attr("prefColumns", "prefColumns", AttrType.INT, false).attr("prefRows", "prefRows", AttrType.INT, false)
                .attr("prefTileWidth", "prefTileWidth", AttrType.DOUBLE, false)
                .attr("prefTileHeight", "prefTileHeight", AttrType.DOUBLE, false)
                .attr("tileAlignment", "tileAlignment", AttrType.STRING, false)
                .done();
        tag("Anchor", "anchor", Children.MANY)
                .attr("padding", "padding", AttrType.INSETS, false)
                .done();
        tag("Pane", "pane", Children.MANY)
                .attr("padding", "padding", AttrType.INSETS, false)
                .done();
        tag("TextFlow", "textflow", Children.MANY)
                .done();
        tag("ButtonBar", "buttonbar", Children.MANY)
                .done();
        tag("Accordion", "accordion", Children.MANY)
                .done();
        tag("TitledPane", "titled", Children.ONE)
                .attr("text", "label", AttrType.STRING, true).attr("expanded", "expanded", AttrType.BOOLEAN, true)
                .attr("collapsible", "collapsible", AttrType.BOOLEAN, false).attr("onAction", "onAction", AttrType.HANDLER, false)
                .def("label", "")
                .done();
        tag("Pagination", "pagination", Children.ONE)
                .attr("pageCount", "pageCount", AttrType.INT, true).attr("current", "current", AttrType.INT, true)
                .attr("maxPageIndicatorCount", "maxPageIndicatorCount", AttrType.INT, false)
                .def("pageCount", Integer.valueOf(1)).def("current", Integer.valueOf(0))
                .def("maxPageIndicatorCount", Integer.valueOf(10))
                .done();

        // Controls the renderer already draws (JXPaint.self, JXControlLayout.sizes).
        tag("ToggleButton", "toggle", Children.NONE).finish()
                .attr("text", "label", AttrType.STRING, true).attr("selected", "selected", AttrType.BOOLEAN, true)
                .attr("onAction", "onAction", AttrType.HANDLER, false).attr("disabled", "disabled", AttrType.BOOLEAN, true)
                .attr("group", "group", AttrType.STRING, true).attr("value", "value", AttrType.STRING, false)
                .def("label", "").def("selected", Boolean.FALSE)
                .change("group", AttrType.STRING)
                .done();
        tag("RadioButton", "radio", Children.NONE).finish()
                .attr("text", "label", AttrType.STRING, true).attr("selected", "checked", AttrType.BOOLEAN, true)
                .attr("onAction", "onAction", AttrType.HANDLER, false).attr("disabled", "disabled", AttrType.BOOLEAN, true)
                .attr("group", "group", AttrType.STRING, true).attr("value", "value", AttrType.STRING, false)
                .def("label", "").def("checked", Boolean.FALSE)
                .change("group", AttrType.STRING)
                .done();
        tag("Hyperlink", "hyperlink", Children.NONE)
                .attr("text", "label", AttrType.STRING, true).attr("visited", "visited", AttrType.BOOLEAN, true)
                .attr("onAction", "onAction", AttrType.HANDLER, false).attr("disabled", "disabled", AttrType.BOOLEAN, true)
                .def("label", "")
                .done();
        tag("ProgressIndicator", "indicator", Children.NONE)
                .attr("progress", "progress", AttrType.DOUBLE, true)
                .def("progress", Double.valueOf(-1))
                .done();
        tag("Tooltip", "tooltip", Children.NONE)
                .attr("text", "label", AttrType.STRING, true)
                .def("label", "")
                .done();
        tag("Image", "image", Children.NONE)
                .attr("pixels", "pixels", AttrType.PIXELS, true)
                .attr("imageWidth", "imageWidth", AttrType.DOUBLE, true).attr("imageHeight", "imageHeight", AttrType.DOUBLE, true)
                .attr("fitWidth", "fitWidth", AttrType.DOUBLE, true).attr("fitHeight", "fitHeight", AttrType.DOUBLE, true)
                .attr("preserveRatio", "preserveRatio", AttrType.BOOLEAN, false)
                .done();

        // Rows of virtual lists and tables (JXControlLayout: list, table, tablerow, cell).
        tag("ListView", "list", Children.TEMPLATE).virtual().finish()
                .attr("items", "itemCount", AttrType.COUNT, true).attr("var", "var", AttrType.STRING, false)
                .attr("type", "type", AttrType.STRING, false).attr("rows", "rows", AttrType.INT, true)
                .attr("first", "first", AttrType.INT, true).attr("cellHeight", "cellHeight", AttrType.DOUBLE, true)
                .def("first", Integer.valueOf(0)).def("cellHeight", Double.valueOf(24))
                .done();
        tag("TableView", "table", Children.TEMPLATE).virtual().finish()
                .attr("items", "itemCount", AttrType.COUNT, true).attr("var", "var", AttrType.STRING, false)
                .attr("type", "type", AttrType.STRING, false).attr("rows", "rows", AttrType.INT, true)
                .attr("first", "first", AttrType.INT, true).attr("rowHeight", "rowHeight", AttrType.DOUBLE, true)
                .attr("columns", "columns", AttrType.STRINGS, true).attr("columnWidths", "columnWidths", AttrType.DOUBLES, true)
                .attr("constrained", "constrained", AttrType.BOOLEAN, false)
                .def("first", Integer.valueOf(0)).def("rowHeight", Double.valueOf(24)).def("columns", new String[0])
                .def("constrained", Boolean.FALSE)
                .done();
        tag("TableRow", "tablerow", Children.MANY)
                .attr("selected", "selected", AttrType.BOOLEAN, true).attr("odd", "odd", AttrType.BOOLEAN, true)
                .attr("listFocused", "listFocused", AttrType.BOOLEAN, true)
                .done();
        tag("ListItem", "cell", Children.ONE)
                .attr("text", "value", AttrType.STRING, true).attr("selected", "selected", AttrType.BOOLEAN, true)
                .attr("odd", "odd", AttrType.BOOLEAN, true).attr("listFocused", "listFocused", AttrType.BOOLEAN, true)
                .attr("alignment", "alignment", AttrType.STRING, false).attr("onAction", "onAction", AttrType.HANDLER, false)
                .def("value", "")
                .done();
        // A cell of a table row: no background of its own (the row paints it), a column line, tighter padding.
        tag("TableCell", "cell", Children.ONE)
                .attr("text", "value", AttrType.STRING, true).attr("selected", "selected", AttrType.BOOLEAN, true)
                .attr("odd", "odd", AttrType.BOOLEAN, true).attr("listFocused", "listFocused", AttrType.BOOLEAN, true)
                .attr("alignment", "alignment", AttrType.STRING, false).attr("onAction", "onAction", AttrType.HANDLER, false)
                .def("value", "").def("tableCell", Boolean.TRUE).def("paintBackground", Boolean.FALSE)
                .def("padding", new double[] {2, 3, 2, 3})
                .done();

        // Composites: JxmlComposites rewrites them into the tags above before anything else reads the file.
        alias("Wrap", "Flow");
        alias("Positioned", "Stack");
        alias("Container", "Stack");
        alias("Box", "Stack");
        alias("Padding", "Stack");
        alias("Opacity", "Stack");
        alias("Visibility", "Stack");
        alias("Center", "Stack", "alignment", "CENTER", "hgrow", "always", "vgrow", "always");
        alias("Align", "Stack", "alignment", "CENTER", "hgrow", "always", "vgrow", "always");
        alias("ChoiceBox", "ComboBox");
        alias("Divider", "Separator");
        alias("Card", "Column", "gap", "8", "padding", "12", "fillWidth", "true", "background", "#FFFFFF",
                "backgroundRadius", "6", "borderColor", "#D9D9D9", "borderRadius", "6", "borderWidth", "1");
        alias("ToolBar", "Row", "gap", "4", "padding", "4 8", "alignment", "CENTER_LEFT", "background", "#EDEDED");
        alias("BottomAppBar", "Row", "gap", "8", "padding", "8 12", "alignment", "CENTER_LEFT", "background", "#EDEDED");
        alias("BottomBar", "Row", "gap", "8", "padding", "8 12", "alignment", "CENTER_LEFT", "background", "#EDEDED");
        alias("NavigationRail", "Column", "gap", "8", "padding", "8", "alignment", "TOP_CENTER", "background", "#EDEDED");
        tag("SizedBox", null, Children.ONE).composite()
                .attr("width", "width", AttrType.DOUBLE, true).attr("height", "height", AttrType.DOUBLE, true)
                .done();
        tag("Spacer", null, Children.NONE).composite().done();
        tag("Expanded", null, Children.ONE).composite().done();
        tag("Flexible", null, Children.ONE).composite().done();
        tag("ListTile", null, Children.MANY).composite()
                .attr("title", "title", AttrType.STRING, true).attr("subtitle", "subtitle", AttrType.STRING, true)
                .attr("onAction", "onAction", AttrType.HANDLER, false)
                .attr("gap", "gap", AttrType.DOUBLE, true).attr("padding", "padding", AttrType.INSETS, false)
                .attr("alignment", "alignment", AttrType.STRING, false)
                .done();
        tag("AppBar", null, Children.MANY).composite()
                .attr("title", "title", AttrType.STRING, true)
                .attr("gap", "gap", AttrType.DOUBLE, true).attr("padding", "padding", AttrType.INSETS, false)
                .attr("alignment", "alignment", AttrType.STRING, false)
                .done();
        tag("NavigationBar", null, Children.MANY).composite()
                .attr("gap", "gap", AttrType.DOUBLE, true).attr("padding", "padding", AttrType.INSETS, false)
                .done();
        tag("Scaffold", null, Children.MANY).composite()
                .attr("padding", "padding", AttrType.INSETS, false)
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

    /**
     * A composite that only renames {@code target}: it takes the same attributes and children, and gets
     * {@code defaults} (attribute, value, attribute, value...) unless the file writes them.
     */
    private static void alias(String name, String target, String... defaults) {
        Tag of = TAGS.get(target);
        TagBuilder b = new TagBuilder(name, null, of.children);
        b.composite = true;
        b.alias = target;
        b.attrs.putAll(of.attrs);
        b.changeAttr = of.changeAttr;
        b.changeType = of.changeType;
        for (int i = 0; i < defaults.length; i += 2) {
            if (of.attr(defaults[i]) == null) {
                throw new IllegalStateException(name + " defaults an attribute " + target + " lacks: " + defaults[i]);
            }
            b.aliasAttrs.put(defaults[i], defaults[i + 1]);
        }
        b.done();
    }

    private static final class TagBuilder {
        private final String name;
        private final String nativeType;
        private final Children children;
        private final Map<String, Attr> attrs = new LinkedHashMap<String, Attr>();
        private final Map<String, Object> defaults = new LinkedHashMap<String, Object>();
        private boolean control;
        private boolean composite;
        private String alias;
        private final Map<String, String> aliasAttrs = new LinkedHashMap<String, String>();
        private boolean virtual;
        private boolean finish;
        private String changeAttr;
        private AttrType changeType;
        private final java.util.List<String> required = new java.util.ArrayList<String>();
        private boolean tree;
        private java.util.List<String> only;
        private java.util.List<String> parents;

        TagBuilder(String name, String nativeType, Children children) {
            this.name = name;
            this.nativeType = nativeType;
            this.children = children;
        }

        TagBuilder tree() {
            tree = true;
            return this;
        }

        /** The children are only these tags (or loops and conditions around them). */
        TagBuilder only(String... tags) {
            only = new java.util.ArrayList<String>(java.util.Arrays.asList(tags));
            return this;
        }

        /** The tag stands only directly inside one of these. */
        TagBuilder parents(String... tags) {
            parents = new java.util.ArrayList<String>(java.util.Arrays.asList(tags));
            return this;
        }

        /**
         * An overlay's {@code showing} (written, or bound with ${...}) and the handler that gets the open state asked for.
         * Without a {@code showing} default the node keeps its own state until the file writes one.
         */
        TagBuilder overlay() {
            attr("showing", "showing", AttrType.BOOLEAN, true);
            handler("onShowing", AttrType.BOOLEAN);
            return this;
        }

        TagBuilder attr(String attr, String prop, AttrType type, boolean bindable) {
            attrs.put(attr, new Attr(attr, prop, type, bindable));
            return this;
        }

        /** A STRING attribute written as a literal that must be one of {@code values}. */
        TagBuilder choice(String attr, String prop, java.util.List<String> values) {
            attrs.put(attr, new Attr(attr, prop, AttrType.STRING, false, values, null));
            return this;
        }

        /** A handler that gets a value of {@code valueType}: {@code onClose="#closeTab"} calls {@code closeTab(int)}. */
        TagBuilder handler(String attr, AttrType valueType) {
            attrs.put(attr, new Attr(attr, attr, AttrType.CHANGE, false, null, valueType));
            return this;
        }

        TagBuilder required(String... names) {
            required.addAll(java.util.Arrays.asList(names));
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

        TagBuilder composite() {
            composite = true;
            return this;
        }

        TagBuilder virtual() {
            virtual = true;
            return this;
        }

        TagBuilder finish() {
            finish = true;
            return this;
        }

        /** Adds onChange, which gets the new value of attr as a type; @{...} binds attr two ways. */
        TagBuilder change(String attr, AttrType type) {
            attrs.put("onChange", new Attr("onChange", "onChange", AttrType.CHANGE, false, null, type));
            changeAttr = attr;
            changeType = type;
            return this;
        }

        void done() {
            TAGS.put(name, new Tag(name, nativeType, children, attrs, defaults, control, changeAttr, changeType,
                    composite, alias, aliasAttrs, virtual, finish, required, tree, only, parents));
        }
    }
}
