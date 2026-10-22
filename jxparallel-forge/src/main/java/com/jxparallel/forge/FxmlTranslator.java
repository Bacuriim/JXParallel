package com.jxparallel.forge;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads an FXML file (JavaFX markup) as plain XML and translates it into the tree a {@code .jxml} file parses to, with
 * the FXML file and line on every node, so the rest of JXForge (includes, styles, the interpreter, the compiler) works
 * on it unchanged and its errors point at FXML lines. Nothing of JavaFX is used: the classes are names in a table.
 *
 * <p>The translation is all or nothing: anything that has no JXML equivalent (scripts, expressions, resource keys,
 * custom classes, an attribute the target tag lacks...) is a {@link JxmlException} with the file and line. What can
 * be carried over but changes meaning (the {@code fx:id}s, which JXML does not inject; a handler that takes an
 * {@code ActionEvent}) is a warning.
 */
final class FxmlTranslator {
    /** How an FXML attribute value becomes a JXML one. */
    enum Conv {
        /** Free text; a leading backslash, {@code ${} or {@code @{} is escaped for JXML. */
        TEXT,
        /** Enum names and other words, as written. */
        KEEP,
        /** The word in lower case: {@code HORIZONTAL} is {@code horizontal}, {@code ALWAYS} is {@code always}. */
        LOWER,
        BOOL,
        NUM,
        INT,
        /** A list of numbers. */
        NUMS,
        COLOR,
        /** One number, or four (top right bottom left), comma or space separated. */
        INSETS,
        /** A size: {@code USE_COMPUTED_SIZE} and negative numbers are the default, so the attribute is not written. */
        SIZE,
        SIZE_MIN,
        SIZE_MAX,
        /** {@code #method}. */
        HANDLER,
        /** {@code a, b} becomes {@code a b}. */
        STYLE,
        /** {@code visible="false"} is {@code hidden="true"}; {@code true} is the default. */
        VISIBLE,
        /** Only {@code true}, the default, can be dropped: JXML has no node that takes no room. */
        MANAGED,
        /** Only {@code false} can be dropped. */
        NO_MNEMONICS,
        /** Only {@code HORIZONTAL}, the default, can be dropped. */
        HORIZONTAL_ONLY,
        /** {@code UNAVAILABLE} makes the tabs of a TabPane not closable. */
        TAB_POLICY
    }

    /** An attribute: the JXML name it becomes and how its value is converted. */
    static final class Attr {
        final String jxml;
        final Conv conv;

        Attr(String jxml, Conv conv) {
            this.jxml = jxml;
            this.conv = conv;
        }
    }

    /** What a property element ({@code <top>}, {@code <children>}...) of a class does. */
    enum Prop {
        /** Its elements are children of the owner, in place ({@code <children>}, {@code <items>}, {@code <tabs>}...). */
        CHILDREN,
        /** As CHILDREN, but at most one element ({@code <content>}, {@code <graphic>}, {@code <root>}). */
        ONE,
        /** Its elements are children of the owner with {@code slot=} the name of the property ({@code <top>}...). */
        SLOT,
        /** The {@code <TableColumn>}s of a TableView. */
        COLUMNS,
        /** The {@code <ColumnConstraints>} and {@code <RowConstraints>} of a GridPane. */
        COLUMN_CONSTRAINTS,
        ROW_CONSTRAINTS
    }

    /** An FXML class: the JXML tag it becomes, its attributes and its property elements. */
    static final class Spec {
        final String fxml;
        final String tag;
        final Map<String, Attr> attrs = new LinkedHashMap<String, Attr>();
        final Map<String, Prop> props = new LinkedHashMap<String, Prop>();
        String defaultProp;
        /** Attributes the tag always gets (the separator of a menu). */
        final Map<String, String> fixed = new LinkedHashMap<String, String>();
        /** ListView and TableView: the rows come from the controller property named by the {@code fx:id}. */
        boolean virtual;

        Spec(String fxml, String tag) {
            this.fxml = fxml;
            this.tag = tag;
        }

        Spec a(String name, String jxml, Conv conv) {
            attrs.put(name, new Attr(jxml, conv));
            return this;
        }

        Spec a(String name, Conv conv) {
            return a(name, name, conv);
        }

        Spec prop(String name, Prop kind) {
            props.put(name, kind);
            return this;
        }

        /** The property that direct child elements go to. */
        Spec dflt(String name, Prop kind) {
            props.put(name, kind);
            defaultProp = name;
            return this;
        }
    }

    /** The FXML classes, by the simple name written in the file. */
    static final Map<String, Spec> CLASSES = new LinkedHashMap<String, Spec>();
    /** Attributes of every class ({@code id}, sizes, {@code disable}...); the target tag must have the JXML attribute. */
    static final Map<String, Attr> COMMON = new LinkedHashMap<String, Attr>();
    /** Attributes a parent gives its children ({@code GridPane.rowIndex}...), by the written name. */
    static final Map<String, Attr> STATIC = new LinkedHashMap<String, Attr>();
    /** The parents whose children may write a {@code <Owner.margin>} element. */
    static final List<String> MARGIN_OWNERS = Collections.unmodifiableList(
            Arrays.asList("VBox", "HBox", "StackPane", "BorderPane", "GridPane", "FlowPane", "TilePane"));
    /** The FXML names that are not classes of the table and are still understood. */
    static final List<String> OTHER_CLASSES = Collections.unmodifiableList(
            Arrays.asList("Insets", "TableColumn", "ContextMenu", "ColumnConstraints", "RowConstraints"));
    /** The JXML attributes that show text to the user, the only ones that take a resource key ({@code %key}). */
    private static final List<String> TRANSLATABLE = Collections.unmodifiableList(Arrays.asList("text", "prompt", "title"));
    /** The JXML containers that take a {@code ContextMenu} as a child, which opens on a secondary press on them. */
    private static final List<String> CONTEXT_MENU_OWNERS = Collections.unmodifiableList(
            Arrays.asList("Column", "Row", "Stack", "Pane", "Anchor", "Flow", "Tile"));
    /** FXML's own spec for the ContextMenu of a {@code <contextMenu>} property: its items are menu items and menus. */
    private static final Spec CONTEXT_MENU = new Spec("ContextMenu", "ContextMenu").dflt("items", Prop.CHILDREN);
    /** The slots of a BorderPane. */
    static final List<String> SLOTS = Collections.unmodifiableList(Arrays.asList("top", "bottom", "left", "right", "center"));

    private static final List<String> NO_EQUIVALENT_FX = Collections.unmodifiableList(
            Arrays.asList("fx:define", "fx:script", "fx:reference", "fx:copy", "fx:root"));

    static {
        common("id", "key", Conv.TEXT);
        for (String size : new String[] {"minWidth", "minHeight"}) {
            common(size, size, Conv.SIZE_MIN);
        }
        for (String size : new String[] {"maxWidth", "maxHeight"}) {
            common(size, size, Conv.SIZE_MAX);
        }
        for (String size : new String[] {"prefWidth", "prefHeight"}) {
            common(size, size, Conv.SIZE);
        }
        common("padding", "padding", Conv.INSETS);
        common("opacity", "opacity", Conv.NUM);
        common("disable", "disabled", Conv.BOOL);
        common("visible", "hidden", Conv.VISIBLE);
        common("managed", "managed", Conv.MANAGED);
        common("styleClass", "style", Conv.STYLE);
        common("layoutX", "x", Conv.NUM);
        common("layoutY", "y", Conv.NUM);
        common("mnemonicParsing", "mnemonicParsing", Conv.NO_MNEMONICS);

        statik("GridPane.rowIndex", "row", Conv.INT);
        statik("GridPane.columnIndex", "column", Conv.INT);
        statik("GridPane.rowSpan", "rowSpan", Conv.INT);
        statik("GridPane.columnSpan", "columnSpan", Conv.INT);
        statik("GridPane.hgrow", "hgrow", Conv.LOWER);
        statik("GridPane.vgrow", "vgrow", Conv.LOWER);
        statik("GridPane.halignment", "halignment", Conv.KEEP);
        statik("GridPane.valignment", "valignment", Conv.KEEP);
        statik("AnchorPane.topAnchor", "top", Conv.NUM);
        statik("AnchorPane.rightAnchor", "right", Conv.NUM);
        statik("AnchorPane.bottomAnchor", "bottom", Conv.NUM);
        statik("AnchorPane.leftAnchor", "left", Conv.NUM);
        statik("HBox.hgrow", "hgrow", Conv.LOWER);
        statik("VBox.vgrow", "vgrow", Conv.LOWER);

        // layout containers
        cls("VBox", "Column").a("spacing", "gap", Conv.NUM).a("alignment", Conv.KEEP).a("fillWidth", Conv.BOOL)
                .dflt("children", Prop.CHILDREN);
        cls("HBox", "Row").a("spacing", "gap", Conv.NUM).a("alignment", Conv.KEEP).a("fillHeight", Conv.BOOL)
                .dflt("children", Prop.CHILDREN);
        cls("StackPane", "Stack").a("alignment", Conv.KEEP).dflt("children", Prop.CHILDREN);
        Spec border = cls("BorderPane", "Border");
        for (String slot : SLOTS) {
            border.prop(slot, Prop.SLOT);
        }
        cls("GridPane", "Grid").a("hgap", Conv.NUM).a("vgap", Conv.NUM).a("alignment", Conv.KEEP).dflt("children", Prop.CHILDREN)
                .prop("columnConstraints", Prop.COLUMN_CONSTRAINTS).prop("rowConstraints", Prop.ROW_CONSTRAINTS);
        cls("AnchorPane", "Anchor").dflt("children", Prop.CHILDREN);
        cls("FlowPane", "Flow").a("hgap", Conv.NUM).a("vgap", Conv.NUM).a("alignment", Conv.KEEP).a("orientation", Conv.LOWER)
                .a("prefWrapLength", Conv.NUM).a("columnHalignment", Conv.KEEP).a("rowValignment", Conv.KEEP)
                .dflt("children", Prop.CHILDREN);
        cls("TilePane", "Tile").a("hgap", Conv.NUM).a("vgap", Conv.NUM).a("alignment", Conv.KEEP).a("orientation", Conv.LOWER)
                .a("prefColumns", Conv.INT).a("prefRows", Conv.INT).a("prefTileWidth", Conv.NUM).a("prefTileHeight", Conv.NUM)
                .a("tileAlignment", Conv.KEEP).dflt("children", Prop.CHILDREN);
        cls("Pane", "Pane").dflt("children", Prop.CHILDREN);
        cls("ScrollPane", "Scroll").a("fitToWidth", Conv.BOOL).a("fitToHeight", Conv.BOOL).dflt("content", Prop.ONE);
        cls("SplitPane", "SplitPane").a("orientation", Conv.LOWER).a("dividerPositions", Conv.NUMS).dflt("items", Prop.CHILDREN);
        cls("TabPane", "Tabs").a("tabClosingPolicy", "tabClosingPolicy", Conv.TAB_POLICY).dflt("tabs", Prop.CHILDREN);
        cls("Tab", "Tab").a("text", "title", Conv.TEXT).a("closable", Conv.BOOL).dflt("content", Prop.ONE);
        cls("TitledPane", "TitledPane").a("text", Conv.TEXT).a("expanded", Conv.BOOL).a("collapsible", Conv.BOOL)
                .dflt("content", Prop.ONE);
        cls("Accordion", "Accordion").dflt("panes", Prop.CHILDREN);
        cls("ToolBar", "ToolBar").dflt("items", Prop.CHILDREN);
        cls("ButtonBar", "ButtonBar").dflt("buttons", Prop.CHILDREN);

        // controls
        cls("Label", "Label").a("text", Conv.TEXT).a("wrapText", Conv.BOOL).a("textFill", Conv.COLOR);
        cls("Button", "Button").a("text", Conv.TEXT).a("onAction", Conv.HANDLER).a("defaultButton", Conv.BOOL)
                .a("textFill", Conv.COLOR).prop("graphic", Prop.ONE);
        cls("TextField", "TextField").a("text", Conv.TEXT).a("promptText", "prompt", Conv.TEXT).a("prefColumnCount", "columns", Conv.NUM);
        cls("PasswordField", "PasswordField").a("text", Conv.TEXT).a("promptText", "prompt", Conv.TEXT)
                .a("prefColumnCount", "columns", Conv.NUM);
        cls("TextArea", "TextArea").a("text", Conv.TEXT).a("promptText", "prompt", Conv.TEXT).a("wrapText", Conv.BOOL)
                .a("prefColumnCount", "columns", Conv.NUM).a("prefRowCount", "rows", Conv.NUM);
        cls("CheckBox", "CheckBox").a("text", Conv.TEXT).a("selected", Conv.BOOL).a("onAction", Conv.HANDLER);
        cls("RadioButton", "RadioButton").a("text", Conv.TEXT).a("selected", Conv.BOOL).a("onAction", Conv.HANDLER);
        cls("ToggleButton", "ToggleButton").a("text", Conv.TEXT).a("selected", Conv.BOOL).a("onAction", Conv.HANDLER);
        cls("ComboBox", "ComboBox").a("promptText", "prompt", Conv.TEXT).a("value", Conv.TEXT);
        cls("ChoiceBox", "ChoiceBox").a("value", Conv.TEXT);
        cls("Spinner", "Spinner").a("min", Conv.NUM).a("max", Conv.NUM).a("initialValue", "value", Conv.TEXT);
        cls("DatePicker", "DatePicker").a("promptText", "prompt", Conv.TEXT).a("value", Conv.TEXT);
        cls("ColorPicker", "ColorPicker").a("value", Conv.TEXT);
        cls("Slider", "Slider").a("min", Conv.NUM).a("max", Conv.NUM).a("value", Conv.NUM).a("orientation", "orientation", Conv.HORIZONTAL_ONLY);
        cls("ProgressBar", "ProgressBar").a("progress", Conv.NUM);
        cls("ProgressIndicator", "ProgressIndicator").a("progress", Conv.NUM);
        cls("Separator", "Separator").a("orientation", Conv.LOWER);
        cls("Hyperlink", "Hyperlink").a("text", Conv.TEXT).a("visited", Conv.BOOL).a("onAction", Conv.HANDLER);
        cls("ImageView", "Image").a("fitWidth", Conv.NUM).a("fitHeight", Conv.NUM).a("preserveRatio", Conv.BOOL);
        cls("Pagination", "Pagination").a("pageCount", Conv.INT).a("currentPageIndex", "current", Conv.INT)
                .a("maxPageIndicatorCount", Conv.INT);
        cls("TextFlow", "TextFlow").dflt("children", Prop.CHILDREN);
        cls("Text", "Label").a("text", Conv.TEXT).a("fill", "textFill", Conv.COLOR);

        // lists, tables and trees: the rows come from a controller property named by the fx:id
        Spec list = cls("ListView", "ListView").a("fixedCellSize", "cellHeight", Conv.NUM);
        list.virtual = true;
        Spec table = cls("TableView", "TableView").a("fixedCellSize", "rowHeight", Conv.NUM).prop("columns", Prop.COLUMNS);
        table.virtual = true;
        cls("TreeView", "Tree").prop("root", Prop.ONE);
        cls("TreeItem", "TreeItem").a("value", "text", Conv.TEXT).a("expanded", Conv.BOOL).dflt("children", Prop.CHILDREN);

        // menus
        cls("MenuBar", "MenuBar").dflt("menus", Prop.CHILDREN);
        cls("Menu", "Menu").a("text", Conv.TEXT).dflt("items", Prop.CHILDREN);
        cls("MenuItem", "MenuItem").a("text", Conv.TEXT).a("accelerator", Conv.TEXT).a("onAction", Conv.HANDLER);
        Spec separator = cls("SeparatorMenuItem", "MenuItem");
        separator.fixed.put("separator", "true");
        cls("MenuButton", "MenuButton").a("text", Conv.TEXT).dflt("items", Prop.CHILDREN);
    }

    private FxmlTranslator() {
    }

    private static Spec cls(String fxml, String tag) {
        Spec spec = new Spec(fxml, tag);
        CLASSES.put(fxml, spec);
        return spec;
    }

    private static void common(String name, String jxml, Conv conv) {
        COMMON.put(name, new Attr(jxml, conv));
    }

    private static void statik(String name, String jxml, Conv conv) {
        STATIC.put(name, new Attr(jxml, conv));
    }

    // ---- the translation ------------------------------------------------------------------------

    /** What one translation keeps while it walks the file. */
    private static final class Run {
        final String source;
        /** The translations a {@code %key} is checked against, or null: the key is written as it is. */
        final JxmlCatalog catalog;
        final List<String> warnings = new ArrayList<String>();
        final Set<String> handlers = new LinkedHashSet<String>();

        Run(String source, JxmlCatalog catalog) {
            this.source = source;
            this.catalog = catalog;
        }

        JxmlException error(int line, String message) {
            return new JxmlException(source, line, message);
        }

        void warn(int line, String message) {
            warnings.add(source + ":" + line + ": " + message);
        }
    }

    /** What is learned about one element while its attributes and children are read. */
    private static final class Pending {
        String id;
        String tabPolicy;
        final List<String> titles = new ArrayList<String>();
        final List<String> widths = new ArrayList<String>();
        boolean widthsGiven;
        /** The ContextMenus of the element: added after its other children. */
        final List<JxmlParser.Node> menus = new ArrayList<JxmlParser.Node>();
        /** GridPane: the constraints of each column and row, by index. */
        final List<Constraint> columnConstraints = new ArrayList<Constraint>();
        final List<Constraint> rowConstraints = new ArrayList<Constraint>();
    }

    /** One {@code <ColumnConstraints>} or {@code <RowConstraints>}: what it sets, for the cells of its column or row. */
    private static final class Constraint {
        final int line;
        final Map<String, String> grow = new LinkedHashMap<String, String>();   // hgrow / vgrow
        final Map<String, String> align = new LinkedHashMap<String, String>();  // halignment / valignment
        final Map<String, String> size = new LinkedHashMap<String, String>();   // prefWidth, minWidth / prefHeight, minHeight

        Constraint(int line) {
            this.line = line;
        }

        boolean needsCells() {
            return !grow.isEmpty() && !"never".equals(grow.values().iterator().next()) || !align.isEmpty() || !size.isEmpty();
        }
    }

    /** The JXML tree of an FXML file; its warnings are added to {@code warnings} (may be null). */
    static JxmlParser.Node translate(InputStream input, String source, List<String> warnings) {
        return translate(input, source, warnings, null);
    }

    /**
     * As above, with the translations the resource keys ({@code %key}) are checked against: a key that the default locale
     * does not define, or that takes arguments, is a {@link JxmlException} with the FXML line. Null: keys are not checked.
     */
    static JxmlParser.Node translate(InputStream input, String source, List<String> warnings, JxmlCatalog catalog) {
        JxmlParser.Node raw = JxmlParser.parseXml(input, source);
        Run run = new Run(source, catalog);
        JxmlParser.Node root = root(run, raw);
        if (warnings != null) {
            warnings.addAll(run.warnings);
        }
        return root;
    }

    private static JxmlParser.Node root(Run run, JxmlParser.Node raw) {
        if ("fx:root".equals(raw.tag)) {
            throw run.error(raw.line, "fx:root has no JXML equivalent: it is the root of a custom component whose controller is "
                    + "the root itself (set in code with setRoot(this) and setController(this)), and a JXML screen is a layout "
                    + "with a separate controller. Write the layout of type=\"" + raw.attrs.get("type") + "\" as the root element "
                    + "with fx:controller=\"...\" (a controller class that holds the state and the handlers), or build the "
                    + "component in Java");
        }
        if (raw.tag.startsWith("fx:")) {
            throw run.error(raw.line, raw.tag + " is not supported as the root of a screen; the root is a layout such as <VBox>");
        }
        if (isProperty(raw.tag) || isStatic(raw.tag)) {
            throw run.error(raw.line, "<" + raw.tag + "> cannot be the root of a screen");
        }
        return element(run, raw, null, true);
    }

    private static boolean isProperty(String tag) {
        return !tag.isEmpty() && Character.isLowerCase(tag.charAt(0)) && tag.indexOf('.') < 0 && tag.indexOf(':') < 0;
    }

    /** {@code GridPane.margin}: an owner class and a lower case property. */
    private static boolean isStatic(String tag) {
        int dot = tag.lastIndexOf('.');
        return dot > 0 && dot < tag.length() - 1 && Character.isLowerCase(tag.charAt(dot + 1))
                && Character.isUpperCase(tag.charAt(tag.lastIndexOf('.', dot - 1) + 1));
    }

    /** The simple class name of a tag; {@code javafx.scene.control.Button} is Button, a class of another package is an error. */
    private static String className(Run run, JxmlParser.Node raw) {
        String tag = raw.tag;
        int dot = tag.lastIndexOf('.');
        String simple = tag.substring(dot + 1);
        if (dot > 0 && !tag.startsWith("javafx.")) {
            throw run.error(raw.line, "custom class " + tag + " has no JXML equivalent; only JavaFX controls and layouts are translated");
        }
        return simple;
    }

    private static JxmlParser.Node element(Run run, JxmlParser.Node raw, String parentClass, boolean root) {
        String cls = className(run, raw);
        Spec spec = CLASSES.get(cls);
        if (spec == null) {
            throw run.error(raw.line, "<" + cls + "> has no JXML equivalent (custom or unsupported class); known: " + CLASSES.keySet());
        }
        return build(run, raw, spec, cls, parentClass, root);
    }

    private static JxmlParser.Node build(Run run, JxmlParser.Node raw, Spec spec, String cls, String parentClass, boolean root) {
        JxmlParser.Node out = new JxmlParser.Node(spec.tag, raw.line, run.source);
        Pending pending = new Pending();
        if (root && raw.attrs.containsKey("fx:controller")) {
            out.attrs.put("controller", rawValue(run, raw, "fx:controller", raw.attrs.get("fx:controller")));
        }
        for (Map.Entry<String, String> entry : raw.attrs.entrySet()) {
            attribute(run, out, raw, spec, pending, parentClass, entry.getKey(), entry.getValue(), root);
        }
        for (Map.Entry<String, String> fixed : spec.fixed.entrySet()) {
            out.attrs.put(fixed.getKey(), fixed.getValue());
        }
        children(run, out, raw, spec, pending, cls);
        out.children.addAll(pending.menus);
        if (!pending.columnConstraints.isEmpty() || !pending.rowConstraints.isEmpty()) {
            constraints(run, out, pending);
        }
        if (spec.virtual) {
            virtual(run, out, raw, spec, pending);
        }
        if ("Tabs".equals(spec.tag) && "UNAVAILABLE".equals(pending.tabPolicy)) {
            for (JxmlParser.Node tab : out.children) {
                if (!tab.attrs.containsKey("closable")) {
                    tab.attrs.put("closable", "false");
                }
            }
        }
        return out;
    }

    // ---- attributes -----------------------------------------------------------------------------

    private static void attribute(Run run, JxmlParser.Node out, JxmlParser.Node raw, Spec spec, Pending pending,
                                  String parentClass, String name, String value, boolean root) {
        if ("fx:controller".equals(name)) {
            if (!root) {
                throw run.error(raw.line, "fx:controller is only allowed on the root element");
            }
            return; // read before the other attributes, so it comes first
        }
        if ("fx:id".equals(name)) {
            pending.id = value;
            if (!spec.virtual) {
                run.warn(raw.line, "fx:id=\"" + value + "\" on <" + spec.fxml + "> has no JXML equivalent: the controller is not "
                        + "given this node; show state with ${...} bindings and react with #handlers");
            }
            return;
        }
        if (name.startsWith("fx:")) {
            throw run.error(raw.line, name + " on <" + spec.fxml + "> has no JXML equivalent");
        }
        // %key is a key of the resource bundle: in JXML it is %{key}, resolved against the l10n files (text attributes only)
        boolean resource = value.startsWith("%");
        String value1 = resource ? value : literal(run, raw, name, value); // an expression or reference is refused whatever the attribute
        Attr attr;
        if (name.indexOf('.') > 0) {
            attr = STATIC.get(name);
            if (attr == null) {
                throw run.error(raw.line, "attribute " + name + " of <" + spec.fxml + "> has no JXML equivalent");
            }
            String owner = name.substring(0, name.indexOf('.'));
            if (!owner.equals(parentClass)) {
                throw run.error(raw.line, "attribute " + name + " of <" + spec.fxml + "> needs a <" + owner + "> parent"
                        + (parentClass == null ? "" : ", not <" + parentClass + ">"));
            }
        } else {
            attr = spec.attrs.get(name);
            if (attr == null) {
                attr = COMMON.get(name);
            }
            if (attr == null) {
                throw run.error(raw.line, "attribute " + name + " of <" + spec.fxml + "> has no JXML equivalent"
                        + (name.startsWith("on") ? " (only onAction on a button, link, menu item, check box or toggle is translated)" : "")
                        + (name.equals("stylesheets") ? " (a stylesheet; use <Style> elements in JXML)" : ""));
            }
        }
        String converted;
        if (resource) {
            if (attr.conv != Conv.TEXT || !TRANSLATABLE.contains(attr.jxml)) {
                throw run.error(raw.line, "the resource key " + name + "=\"" + value + "\" is only translated in a text attribute "
                        + "(text, promptText); <" + spec.fxml + "> " + name + " is not one");
            }
            converted = resourceKey(run, raw.line, name, value);
        } else {
            converted = convert(run, raw, spec, pending, name, attr, value1);
        }
        if (converted == null) {
            return; // the default: not written
        }
        JxmlSchema.Tag tag = JxmlSchema.tag(spec.tag);
        // style is read by the file preparation for any tag, and tabClosingPolicy is not written at all
        if (!attr.jxml.equals("tabClosingPolicy") && !attr.jxml.equals("style") && tag.attr(attr.jxml) == null) {
            throw run.error(raw.line, "attribute " + name + " of <" + spec.fxml + "> has no JXML equivalent (<" + spec.tag
                    + "> has no " + attr.jxml + ")");
        }
        if (out.attrs.containsKey(attr.jxml)) {
            throw run.error(raw.line, "attribute " + name + " of <" + spec.fxml + "> and another attribute both set " + attr.jxml);
        }
        if (attr.jxml.equals("tabClosingPolicy")) {
            return;
        }
        out.attrs.put(attr.jxml, converted);
    }

    /**
     * {@code %key} as the JXML translation {@code %{key}}. With a catalog the key must be defined in the default locale and
     * take no arguments (an FXML resource key has none).
     */
    private static String resourceKey(Run run, int line, String name, String value) {
        String key = value.substring(1);
        if (!JxmlCatalog.isKey(key)) {
            throw run.error(line, "the resource key " + name + "=\"" + value + "\" is not a key (letters, digits, _, . and -)");
        }
        if (run.catalog != null) {
            JxmlCatalog.Signature sig = run.catalog.signature(key);
            if (sig == null) {
                throw run.error(line, "the resource key " + name + "=\"" + value + "\" is not defined in the default locale "
                        + run.catalog.getDefaultTag() + " (" + run.catalog.fileOf(run.catalog.getDefaultTag()) + "); keys: "
                        + run.catalog.keys());
            }
            if (!sig.names.isEmpty()) {
                throw run.error(line, "the resource key " + name + "=\"" + value + "\" takes arguments (" + sig.describe()
                        + ", defined at " + sig.location + "), which an FXML resource key cannot pass: write %{" + key
                        + "(...)} in JXML");
            }
        }
        return "%{" + key + "}";
    }

    /** The value of {@code fx:controller} and the like: no expressions. */
    private static String rawValue(Run run, JxmlParser.Node raw, String name, String value) {
        return literal(run, raw, name, value);
    }

    /** The literal an attribute value stands for: {@code ${...}}, {@code $x} and {@code @file} are errors (a {@code %key} is handled before). */
    private static String literal(Run run, JxmlParser.Node raw, String name, String value) {
        if (value.startsWith("\\")) {
            return value.substring(1); // the FXML escape: the rest is a literal
        }
        if (value.startsWith("${")) {
            throw run.error(raw.line, "the expression " + name + "=\"" + value + "\" has no JXML equivalent; "
                    + "bind to a controller property with ${property} instead");
        }
        if (value.startsWith("$")) {
            throw run.error(raw.line, "the reference " + name + "=\"" + value + "\" has no JXML equivalent (fx:id objects are not shared)");
        }
        if (value.startsWith("%")) {
            throw run.error(raw.line, "the resource key " + name + "=\"" + value + "\" has no JXML equivalent here: a key is "
                    + "translated only in the text of a control, a prompt, a tab or a table column (it names no text in <"
                    + raw.tag + "> " + name + "); write the value, or bind it with ${...}");
        }
        if (value.startsWith("@")) {
            throw run.error(raw.line, "the location " + name + "=\"" + value + "\" has no JXML equivalent");
        }
        return value;
    }

    /** The JXML value, or null when it is the default and the attribute is not written. */
    private static String convert(Run run, JxmlParser.Node raw, Spec spec, Pending pending, String name, Attr attr, String v) {
        String t = v.trim();
        switch (attr.conv) {
            case TEXT:
                return v.startsWith("\\") || v.startsWith("${") || v.startsWith("@{") || v.startsWith("%{") ? "\\" + v : v;
            case KEEP:
            case NUM:
            case INT:
            case COLOR:
            case NUMS:
                return t;
            case LOWER:
                if (name.endsWith("hgrow") || name.endsWith("vgrow")) {
                    if (!Arrays.asList("ALWAYS", "SOMETIMES", "NEVER").contains(t)) {
                        throw run.error(raw.line, name + "=\"" + v + "\" is not ALWAYS, SOMETIMES or NEVER");
                    }
                }
                return t.toLowerCase(java.util.Locale.ROOT);
            case BOOL:
                return "true".equalsIgnoreCase(t) ? "true" : "false".equalsIgnoreCase(t) ? "false" : t;
            case INSETS:
                return insets(run, raw, name, t);
            case SIZE:
            case SIZE_MIN:
            case SIZE_MAX:
                return size(run, raw, name, attr.conv, t);
            case HANDLER:
                return handler(run, raw, name, t);
            case STYLE:
                return t.replace(',', ' ').trim().replaceAll("\\s+", " ");
            case VISIBLE:
                if ("true".equalsIgnoreCase(t)) {
                    return null;
                }
                if ("false".equalsIgnoreCase(t)) {
                    return "true";
                }
                throw run.error(raw.line, name + "=\"" + v + "\" is not true or false");
            case MANAGED:
                if ("true".equalsIgnoreCase(t)) {
                    return null;
                }
                throw run.error(raw.line, "attribute managed=\"" + v + "\" of <" + spec.fxml + "> has no JXML equivalent: a node always "
                        + "takes its place in the layout (hidden=\"true\" keeps the place, as visible=\"false\" does)");
            case NO_MNEMONICS:
                if ("false".equalsIgnoreCase(t)) {
                    return null;
                }
                throw run.error(raw.line, "attribute mnemonicParsing=\"" + v + "\" of <" + spec.fxml + "> has no JXML equivalent");
            case HORIZONTAL_ONLY:
                if ("HORIZONTAL".equals(t)) {
                    return null;
                }
                throw run.error(raw.line, "attribute " + name + "=\"" + v + "\" of <" + spec.fxml + "> has no JXML equivalent "
                        + "(<" + spec.tag + "> is horizontal)");
            case TAB_POLICY:
                if (!Arrays.asList("UNAVAILABLE", "SELECTED_TAB", "ALL_TABS").contains(t)) {
                    throw run.error(raw.line, "tabClosingPolicy=\"" + v + "\" is not UNAVAILABLE, SELECTED_TAB or ALL_TABS");
                }
                if (!"UNAVAILABLE".equals(t)) {
                    throw run.error(raw.line, "attribute tabClosingPolicy=\"" + v + "\" of <TabPane> has no JXML equivalent "
                            + "(write closable=\"true\" or \"false\" on each <Tab>); only UNAVAILABLE is translated");
                }
                pending.tabPolicy = t;
                return "";
            default:
                throw new IllegalStateException(attr.conv.name());
        }
    }

    private static String insets(Run run, JxmlParser.Node raw, String name, String t) {
        String[] parts = t.split("[\\s,]+");
        if (parts.length != 1 && parts.length != 4) {
            throw run.error(raw.line, name + "=\"" + t + "\" must be one number, or four (top, right, bottom, left)");
        }
        return String.join(" ", parts);
    }

    private static String size(Run run, JxmlParser.Node raw, String name, Conv conv, String t) {
        if ("USE_COMPUTED_SIZE".equals(t)) {
            return null;
        }
        if ("USE_PREF_SIZE".equals(t)) {
            throw run.error(raw.line, name + "=\"USE_PREF_SIZE\" has no JXML equivalent; write the size");
        }
        if ("Infinity".equals(t) || "-Infinity".equals(t)) {
            if (conv == Conv.SIZE_MAX && "Infinity".equals(t) || conv == Conv.SIZE_MIN && "-Infinity".equals(t)) {
                return null; // no limit: the default
            }
            throw run.error(raw.line, name + "=\"" + t + "\" is not a size");
        }
        try {
            if (Double.parseDouble(t) < 0) {
                return null; // -1 stands for USE_COMPUTED_SIZE
            }
        } catch (NumberFormatException e) {
            throw run.error(raw.line, name + "=\"" + t + "\" is not a number");
        }
        return t;
    }

    private static String handler(Run run, JxmlParser.Node raw, String name, String t) {
        if (!t.startsWith("#") || !JxmlTemplate.isIdentifier(t.substring(1))) {
            throw run.error(raw.line, name + "=\"" + t + "\" must name a controller method, such as #save");
        }
        if (run.handlers.add(t)) {
            String method = t.substring(1);
            run.warn(raw.line, "handler " + t + " is called as " + method + "() with no arguments, so the controller needs "
                    + "exactly that method. If it only has " + method + "(ActionEvent e), add this next to it, moving the body "
                    + "of " + method + "(ActionEvent) here without the event (a bridge that passes null would break a handler "
                    + "that reads the event): public void " + method + "() { /* body of " + method
                    + "(ActionEvent) without the event */ }");
        }
        return t;
    }

    // ---- children -------------------------------------------------------------------------------

    private static void children(Run run, JxmlParser.Node out, JxmlParser.Node raw, Spec spec, Pending pending, String cls) {
        for (JxmlParser.Node child : raw.children) {
            String tag = child.tag;
            if (tag.startsWith("fx:")) {
                out.children.add(fxElement(run, child, cls));
            } else if (isStatic(tag)) {
                staticProperty(run, out, child, cls);
            } else if (isProperty(tag)) {
                property(run, out, child, spec, pending, cls);
            } else if (spec.defaultProp == null) {
                throw run.error(child.line, "<" + cls + "> takes its children in property elements"
                        + (spec.props.isEmpty() ? "" : " " + spec.props.keySet()) + ", not directly <" + tag + ">"
                        + (spec.props.isEmpty() ? "; JXML has no children for it" : ""));
            } else {
                Prop kind = spec.props.get(spec.defaultProp);
                if (kind == Prop.ONE && !out.children.isEmpty()) {
                    throw run.error(child.line, "<" + cls + "> takes one child, its " + spec.defaultProp);
                }
                out.children.add(element(run, child, cls, false));
            }
        }
    }

    private static void property(Run run, JxmlParser.Node out, JxmlParser.Node prop, Spec spec, Pending pending, String cls) {
        String name = prop.tag;
        if (!prop.attrs.isEmpty()) {
            throw run.error(prop.line, "<" + name + "> takes no attributes");
        }
        if ("padding".equals(name)) {
            padding(run, out, prop, "padding", cls);
            return;
        }
        if ("contextMenu".equals(name)) {
            pending.menus.add(contextMenu(run, out, prop, cls));
            return;
        }
        if ("tooltip".equals(name)) {
            throw run.error(prop.line, "the <tooltip> of <" + cls + "> has no JXML equivalent: a JXML <Tooltip> is an element that "
                    + "is drawn where it is written (a box with its text), and the renderer does not report the hover that would "
                    + "show it on a control, so there is nothing to attach it to. Remove it, or show the text in a <Label>");
        }
        Prop kind = spec.props.get(name);
        if (kind == null) {
            throw run.error(prop.line, "property <" + name + "> of <" + cls + "> has no JXML equivalent"
                    + (name.equals("stylesheets") ? " (a stylesheet; use <Style> elements in JXML)" : ""));
        }
        switch (kind) {
            case CHILDREN:
            case ONE:
            case SLOT:
                int count = 0;
                for (JxmlParser.Node child : prop.children) {
                    count++;
                    if (kind != Prop.CHILDREN && count > 1) {
                        throw run.error(child.line, "<" + name + "> of <" + cls + "> takes one element");
                    }
                    JxmlParser.Node node = child.tag.startsWith("fx:") ? fxElement(run, child, cls) : element(run, child, cls, false);
                    if (kind == Prop.SLOT) {
                        node.attrs.put("slot", name);
                    }
                    out.children.add(node);
                }
                break;
            case COLUMNS:
                for (JxmlParser.Node column : prop.children) {
                    column(run, column, pending);
                }
                break;
            case COLUMN_CONSTRAINTS:
            case ROW_CONSTRAINTS:
                boolean columns = kind == Prop.COLUMN_CONSTRAINTS;
                for (JxmlParser.Node item : prop.children) {
                    (columns ? pending.columnConstraints : pending.rowConstraints).add(constraint(run, item, columns));
                }
                break;
            default:
                throw new IllegalStateException(kind.name());
        }
    }

    /** {@code <padding><Insets .../></padding>} (or the margin) becomes an attribute of the owner. */
    private static void padding(Run run, JxmlParser.Node out, JxmlParser.Node prop, String attr, String cls) {
        if (prop.children.size() != 1 || !"Insets".equals(simple(prop.children.get(0).tag))) {
            throw run.error(prop.line, "<" + prop.tag + "> must hold one <Insets>");
        }
        JxmlParser.Node insets = prop.children.get(0);
        if (!insets.children.isEmpty()) {
            throw run.error(insets.line, "<Insets> takes no children");
        }
        String[] sides = {"0", "0", "0", "0"};
        String all = null;
        for (Map.Entry<String, String> side : insets.attrs.entrySet()) {
            String key = side.getKey();
            String value = literal(run, insets, key, side.getValue()).trim();
            int i = Arrays.asList("top", "right", "bottom", "left").indexOf(key);
            if (i >= 0) {
                sides[i] = value;
            } else if ("topRightBottomLeft".equals(key)) {
                all = value;
            } else {
                throw run.error(insets.line, "attribute " + key + " of <Insets> has no JXML equivalent");
            }
        }
        if (all != null) {
            java.util.Arrays.fill(sides, all);
        }
        if (JxmlSchema.tag(out.tag).attr(attr) == null) {
            throw run.error(prop.line, "<" + prop.tag + "> of <" + cls + "> has no JXML equivalent (<" + out.tag + "> has no " + attr + ")");
        }
        if (out.attrs.containsKey(attr)) {
            throw run.error(prop.line, "<" + prop.tag + "> of <" + cls + "> and an attribute both set " + attr);
        }
        out.attrs.put(attr, String.join(" ", sides));
    }

    private static String simple(String tag) {
        return tag.substring(tag.lastIndexOf('.') + 1);
    }

    /** {@code <VBox.margin><Insets/></VBox.margin>} inside a child: its margin. */
    private static void staticProperty(Run run, JxmlParser.Node out, JxmlParser.Node prop, String cls) {
        String owner = prop.tag.substring(0, prop.tag.lastIndexOf('.'));
        String name = prop.tag.substring(prop.tag.lastIndexOf('.') + 1);
        if (!"margin".equals(name) || !MARGIN_OWNERS.contains(owner)) {
            throw run.error(prop.line, "property <" + prop.tag + "> of <" + cls + "> has no JXML equivalent");
        }
        padding(run, out, prop, "margin", cls);
    }

    /** The rows of a TableView: the title and width of each column. */
    private static void column(Run run, JxmlParser.Node column, Pending pending) {
        if (!"TableColumn".equals(simple(column.tag))) {
            throw run.error(column.line, "<" + column.tag + "> is not a <TableColumn>");
        }
        String title = "";
        String width = "-1";
        for (Map.Entry<String, String> entry : column.attrs.entrySet()) {
            String key = entry.getKey();
            if ("text".equals(key) && entry.getValue().startsWith("%")) {
                title = resourceKey(run, column.line, key, entry.getValue()); // %{key}: the titles are a list, so it is one of them
                continue;
            }
            String value = literal(run, column, key, entry.getValue()).trim();
            if ("text".equals(key)) {
                if (value.indexOf(',') >= 0) {
                    throw run.error(column.line, "the column title \"" + value + "\" has a comma, which separates the titles in JXML");
                }
                title = value.startsWith("%{") || value.startsWith("\\") ? "\\" + value : value;
            } else if ("prefWidth".equals(key)) {
                String w = size(run, column, key, Conv.SIZE, value);
                width = w == null ? "-1" : w;
                pending.widthsGiven |= w != null;
            } else if ("fx:id".equals(key)) {
                run.warn(column.line, "fx:id=\"" + value + "\" on <TableColumn> has no JXML equivalent");
            } else {
                throw run.error(column.line, "attribute " + key + " of <TableColumn> has no JXML equivalent");
            }
        }
        if (!column.children.isEmpty()) {
            JxmlParser.Node first = column.children.get(0);
            throw run.error(first.line, "<" + first.tag + "> in a <TableColumn> has no JXML equivalent: JXML writes the cells of a row "
                    + "in a <TableCell>");
        }
        pending.titles.add(title);
        pending.widths.add(width);
    }

    /** ListView and TableView: the rows come from the controller property named by the {@code fx:id}. */
    private static void virtual(Run run, JxmlParser.Node out, JxmlParser.Node raw, Spec spec, Pending pending) {
        if (pending.id == null) {
            throw run.error(raw.line, "<" + spec.fxml + "> needs an fx:id: JXML shows the rows of the controller property of that name "
                    + "(items=\"${" + "name}\"), which FXML fills in code");
        }
        out.attrs.put("items", "${" + pending.id + "}");
        if (spec.tag.equals("ListView")) {
            out.attrs.put("var", "item");
            JxmlParser.Node item = new JxmlParser.Node("ListItem", raw.line, run.source);
            item.attrs.put("text", "${item}");
            out.children.add(item);
            run.warn(raw.line, "<ListView fx:id=\"" + pending.id + "\"> shows controller.get" + capitalize(pending.id)
                    + "() with each row as the text of its item (${item}); edit the translated <ListItem> for another look");
            return;
        }
        out.attrs.put("var", "row");
        if (!pending.titles.isEmpty()) {
            out.attrs.put("columns", String.join(", ", pending.titles));
            if (pending.widthsGiven) {
                out.attrs.put("columnWidths", String.join(", ", pending.widths));
            }
        }
        JxmlParser.Node row = new JxmlParser.Node("TableRow", raw.line, run.source);
        for (int i = 0; i < pending.titles.size(); i++) {
            JxmlParser.Node cell = new JxmlParser.Node("TableCell", raw.line, run.source);
            cell.attrs.put("text", "${row}");
            row.children.add(cell);
        }
        out.children.add(row);
        run.warn(raw.line, "<TableView fx:id=\"" + pending.id + "\"> shows controller.get" + capitalize(pending.id)
                + "() with every cell showing the row itself (${row}); FXML sets the cells in code, so edit the translated "
                + "<TableCell>s (and give the <TableView> a type=\"...\") to show its fields");
    }

    private static String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---- context menus and grid constraints ---------------------------------------------------------

    /**
     * {@code <contextMenu><ContextMenu>...</ContextMenu></contextMenu>}: JXML opens a {@code ContextMenu} on a secondary
     * press on its parent, so it becomes the last child of a container. A control that takes no children cannot carry one.
     */
    private static JxmlParser.Node contextMenu(Run run, JxmlParser.Node out, JxmlParser.Node prop, String cls) {
        if (prop.children.size() != 1 || !"ContextMenu".equals(simple(prop.children.get(0).tag))) {
            throw run.error(prop.line, "<contextMenu> of <" + cls + "> must hold one <ContextMenu>");
        }
        if (!CONTEXT_MENU_OWNERS.contains(out.tag)) {
            throw run.error(prop.line, "the <contextMenu> of <" + cls + "> has no JXML equivalent: JXML opens a ContextMenu on a "
                    + "secondary press on its parent, which must be one of " + CONTEXT_MENU_OWNERS + " (<" + out.tag
                    + "> cannot hold it); put the control in a <StackPane> or <VBox> and the <contextMenu> on that");
        }
        return build(run, prop.children.get(0), CONTEXT_MENU, "ContextMenu", cls, false);
    }

    /** One {@code <ColumnConstraints>} or {@code <RowConstraints>}: only what the cells of the column can say is kept. */
    private static Constraint constraint(Run run, JxmlParser.Node node, boolean columns) {
        String cls = columns ? "ColumnConstraints" : "RowConstraints";
        if (!cls.equals(simple(node.tag))) {
            throw run.error(node.line, "<" + node.tag + "> is not a <" + cls + ">");
        }
        if (!node.children.isEmpty()) {
            throw run.error(node.children.get(0).line, "<" + cls + "> takes no children");
        }
        String grow = columns ? "hgrow" : "vgrow";
        String align = columns ? "halignment" : "valignment";
        String pref = columns ? "prefWidth" : "prefHeight";
        String min = columns ? "minWidth" : "minHeight";
        String max = columns ? "maxWidth" : "maxHeight";
        String percent = columns ? "percentWidth" : "percentHeight";
        String fill = columns ? "fillWidth" : "fillHeight";
        Constraint c = new Constraint(node.line);
        for (Map.Entry<String, String> entry : node.attrs.entrySet()) {
            String key = entry.getKey();
            if ("fx:id".equals(key)) {
                run.warn(node.line, "fx:id=\"" + entry.getValue() + "\" on <" + cls + "> has no JXML equivalent");
                continue;
            }
            String value = literal(run, node, key, entry.getValue()).trim();
            if (key.equals(grow)) {
                c.grow.put(grow, convert(run, node, null, null, grow, new Attr(grow, Conv.LOWER), value));
            } else if (key.equals(align)) {
                c.align.put(align, value);
            } else if (key.equals(pref) || key.equals(min)) {
                if ("-Infinity".equals(value)) {
                    value = "USE_PREF_SIZE";
                }
                String size = size(run, node, key, key.equals(pref) ? Conv.SIZE : Conv.SIZE_MIN, value);
                if (size != null) {
                    c.size.put(key, size);
                }
            } else if (key.equals(max)) {
                if (size(run, node, key, Conv.SIZE_MAX, value) != null) {
                    throw run.error(node.line, "attribute " + key + "=\"" + value + "\" of <" + cls + "> has no JXML equivalent: "
                            + "a cell can be given a maximum size but the column is not limited by it");
                }
            } else if (key.equals(percent)) {
                double p;
                try {
                    p = Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    throw run.error(node.line, key + "=\"" + value + "\" is not a number");
                }
                if (p >= 0) {
                    throw run.error(node.line, "attribute " + key + "=\"" + value + "\" of <" + cls + "> has no JXML equivalent: "
                            + "JXML has no share of the grid for a column; use " + pref + " or " + grow + "=\"ALWAYS\"");
                }
            } else if (key.equals(fill)) {
                if (!"true".equalsIgnoreCase(value)) {
                    throw run.error(node.line, "attribute " + key + "=\"" + value + "\" of <" + cls + "> has no JXML equivalent: "
                            + "the cells of a Grid always fill their column");
                }
            } else {
                throw run.error(node.line, "attribute " + key + " of <" + cls + "> has no JXML equivalent");
            }
        }
        return c;
    }

    /**
     * Writes the constraints of the GridPane on its cells: JXML sets {@code hgrow}, {@code vgrow}, {@code halignment},
     * {@code valignment}, {@code prefWidth}/{@code minWidth} and {@code prefHeight}/{@code minHeight} on a cell, and
     * the grid reads them from the cells that sit in only one column (row). The constraint of the column wins over the
     * cell for the grow priority and the sizes (as in JavaFX), the cell's own alignment wins over the column's.
     */
    private static void constraints(Run run, JxmlParser.Node out, Pending pending) {
        apply(run, out, pending.columnConstraints, true);
        apply(run, out, pending.rowConstraints, false);
    }

    private static void apply(Run run, JxmlParser.Node out, List<Constraint> list, boolean columns) {
        String cls = columns ? "ColumnConstraints" : "RowConstraints";
        String place = columns ? "column" : "row";
        String span = columns ? "columnSpan" : "rowSpan";
        for (int i = 0; i < list.size(); i++) {
            Constraint c = list.get(i);
            List<JxmlParser.Node> cells = new ArrayList<JxmlParser.Node>();
            for (JxmlParser.Node child : out.children) {
                if (!"ContextMenu".equals(child.tag) && intAttribute(child, place, 0) == i && intAttribute(child, span, 1) == 1) {
                    cells.add(child);
                }
            }
            if (cells.isEmpty()) {
                if (c.needsCells()) {
                    throw run.error(c.line, "<" + cls + "> number " + (i + 1) + " (" + place + " " + i + ") sets " + describe(c)
                            + ", but no cell of the <GridPane> sits only in " + place + " " + i + ": JXML sets these on the cells, "
                            + "not on the " + place);
                }
                continue;
            }
            for (JxmlParser.Node cell : cells) {
                cell.attrs.putAll(c.grow);
                for (Map.Entry<String, String> a : c.align.entrySet()) {
                    if (!cell.attrs.containsKey(a.getKey())) {
                        cell.attrs.put(a.getKey(), a.getValue());
                    }
                }
                cell.attrs.putAll(c.size);
            }
            if (!c.size.isEmpty()) {
                run.warn(c.line, "<" + cls + "> number " + (i + 1) + " sets " + c.size + ": JXML sizes cells, so it is written on "
                        + "the " + cells.size() + " cell(s) of " + place + " " + i);
            }
        }
    }

    private static String describe(Constraint c) {
        Map<String, String> all = new LinkedHashMap<String, String>();
        all.putAll(c.grow);
        all.putAll(c.align);
        all.putAll(c.size);
        return all.toString();
    }

    private static int intAttribute(JxmlParser.Node node, String name, int fallback) {
        String value = node.attrs.get(name);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ---- fx: elements -----------------------------------------------------------------------------

    private static JxmlParser.Node fxElement(Run run, JxmlParser.Node raw, String parentClass) {
        if ("fx:include".equals(raw.tag)) {
            return include(run, raw, parentClass);
        }
        if (NO_EQUIVALENT_FX.contains(raw.tag)) {
            throw run.error(raw.line, raw.tag + " has no JXML equivalent" + (raw.tag.equals("fx:define")
                    ? " (the objects it defines, such as a ToggleGroup, are not shared: use group=/value= on the buttons)"
                    : raw.tag.equals("fx:script") ? " (JXML has no scripts: call controller methods with #handlers)" : ""));
        }
        throw run.error(raw.line, raw.tag + " has no JXML equivalent");
    }

    private static JxmlParser.Node include(Run run, JxmlParser.Node raw, String parentClass) {
        JxmlParser.Node out = new JxmlParser.Node("Include", raw.line, run.source);
        if (!raw.children.isEmpty()) {
            throw run.error(raw.children.get(0).line, "<fx:include> takes no children");
        }
        if (!raw.attrs.containsKey("source")) {
            throw run.error(raw.line, "<fx:include> needs source=\"file.fxml\"");
        }
        for (Map.Entry<String, String> entry : raw.attrs.entrySet()) {
            String name = entry.getKey();
            if ("source".equals(name)) {
                out.attrs.put("src", literal(run, raw, name, entry.getValue()).trim());
            } else if ("fx:id".equals(name)) {
                run.warn(raw.line, "fx:id=\"" + entry.getValue() + "\" on <fx:include> has no JXML equivalent: the controller of the "
                        + "included file is not given to this controller");
            } else if ("resources".equals(name)) {
                throw run.error(raw.line, "attribute resources=\"" + entry.getValue() + "\" of <fx:include> has no JXML equivalent: "
                        + "the texts of every screen come from the one set of l10n files (src/main/l10n), through %key in a text "
                        + "attribute; remove it");
            } else if (STATIC.containsKey(name)) {
                Attr attr = STATIC.get(name);
                if (!name.substring(0, name.indexOf('.')).equals(parentClass)) {
                    throw run.error(raw.line, "attribute " + name + " of <fx:include> needs a <" + name.substring(0, name.indexOf('.'))
                            + "> parent");
                }
                out.attrs.put(attr.jxml, convert(run, raw, null, null, name, attr, literal(run, raw, name, entry.getValue())));
            } else {
                throw run.error(raw.line, "attribute " + name + " of <fx:include> has no JXML equivalent");
            }
        }
        return out;
    }

    // ---- printing -----------------------------------------------------------------------------

    /** The tree as JXML text, for migrating a file by hand: one element per line, two spaces per level. */
    static String print(JxmlParser.Node root) {
        StringBuilder out = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        print(root, 0, out);
        return out.toString();
    }

    private static void print(JxmlParser.Node node, int depth, StringBuilder out) {
        for (int i = 0; i < depth; i++) {
            out.append("  ");
        }
        out.append('<').append(node.tag);
        for (Map.Entry<String, String> attr : node.attrs.entrySet()) {
            String value = attr.getValue();
            if ("Include".equals(node.tag) && "src".equals(attr.getKey()) && value.endsWith(".fxml")) {
                value = value.substring(0, value.length() - 5) + ".jxml"; // the migrated file will be a .jxml too
            }
            out.append(' ').append(attr.getKey()).append("=\"").append(escape(value)).append('"');
        }
        if (node.children.isEmpty()) {
            out.append("/>\n");
            return;
        }
        out.append(">\n");
        for (JxmlParser.Node child : node.children) {
            print(child, depth + 1, out);
        }
        for (int i = 0; i < depth; i++) {
            out.append("  ");
        }
        out.append("</").append(node.tag).append(">\n");
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                .replace("\n", "&#10;").replace("\t", "&#9;");
    }
}
