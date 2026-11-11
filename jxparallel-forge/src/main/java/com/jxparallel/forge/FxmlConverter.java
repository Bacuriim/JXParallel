package com.jxparallel.forge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.regex.Pattern;

import com.jxparallel.forge.FxmlParser.FxNode;
import com.jxparallel.forge.FxmlReport.Level;

/**
 * Turns one parsed FXML file into the {@link JxmlParser.Node} tree {@code JxmlParser} would give for the equivalent
 * JXML file. Every FXML element, attribute and property element is either mapped or reported ({@link FxmlReport}).
 * The tables at the top say what maps to what; the rest is the rules for values, placement and the few elements
 * that need more than a rename (table columns, grids, tab panes, radio groups, trees).
 */
final class FxmlConverter {
    /** An FXML class and the JXML tag it becomes. */
    private static final class Spec {
        final String jx;
        /** The property that holds the children (the default property in FXML), or null. */
        final String list;
        /** The list holds exactly one child. */
        final boolean single;
        /** When not null, the mapping is close but not the same: reported as {@link Level#APPROXIMATE}. */
        final String approx;

        Spec(String jx, String list, boolean single, String approx) {
            this.jx = jx;
            this.list = list;
            this.single = single;
            this.approx = approx;
        }
    }

    private static final Map<String, Spec> SPECS = new HashMap<String, Spec>();
    /** FXML property name to JXML attribute name, per FXML class. */
    private static final Map<String, Map<String, String>> RENAMES = new HashMap<String, Map<String, String>>();
    private static final Pattern PATH = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*(\\(\\))?");
    private static final Pattern STYLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_-]*");

    private static void spec(String fx, String jx, String list, boolean single, String approx) {
        SPECS.put(fx, new Spec(jx, list, single, approx));
    }

    private static void ren(String fx, String... pairs) {
        Map<String, String> map = new HashMap<String, String>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        RENAMES.put(fx, map);
    }

    static {
        spec("VBox", "Column", "children", false, null);
        spec("HBox", "Row", "children", false, null);
        spec("StackPane", "Stack", "children", false, null);
        spec("BorderPane", "Border", "center", false, null);
        spec("GridPane", "Grid", "children", false, null);
        spec("AnchorPane", "Anchor", "children", false, null);
        spec("FlowPane", "Flow", "children", false, null);
        spec("TilePane", "Tile", "children", false, null);
        spec("Pane", "Pane", "children", false, null);
        spec("Region", "Pane", "children", false, null);
        spec("ScrollPane", "Scroll", "content", true, null);
        spec("SplitPane", "SplitPane", "items", false, null);
        spec("TabPane", "Tabs", "tabs", false, null);
        spec("Tab", "Tab", "content", true, null);
        spec("TitledPane", "TitledPane", "content", true, null);
        spec("Accordion", "Accordion", "panes", false, null);
        spec("ToolBar", "ToolBar", "items", false, null);
        spec("ButtonBar", "ButtonBar", "buttons", false, null);
        spec("TextFlow", "TextFlow", "children", false, null);
        spec("Text", "Label", null, false, null);
        spec("Label", "Label", null, false, null);
        spec("Button", "Button", null, false, null);
        spec("ToggleButton", "ToggleButton", null, false, null);
        spec("RadioButton", "RadioButton", null, false, null);
        spec("CheckBox", "CheckBox", null, false, null);
        spec("Hyperlink", "Hyperlink", null, false, null);
        spec("TextField", "TextField", null, false, null);
        spec("PasswordField", "PasswordField", null, false, null);
        spec("TextArea", "TextArea", null, false, null);
        spec("ComboBox", "ComboBox", null, false, null);
        spec("ChoiceBox", "ChoiceBox", null, false, null);
        spec("Spinner", "Spinner", null, false, null);
        spec("Slider", "Slider", null, false, null);
        spec("DatePicker", "DatePicker", null, false, null);
        spec("ColorPicker", "ColorPicker", null, false, null);
        spec("ProgressBar", "ProgressBar", null, false, null);
        spec("ProgressIndicator", "ProgressIndicator", null, false, null);
        spec("Separator", "Separator", null, false, null);
        spec("ScrollBar", "ScrollBar", null, false, null);
        spec("ListView", "ListView", null, false, null);
        spec("TableView", "TableView", null, false, null);
        spec("TreeView", "Tree", "root", true, null);
        spec("TreeItem", "TreeItem", "children", false, null);
        spec("MenuBar", "MenuBar", "menus", false, null);
        spec("Menu", "Menu", "items", false, null);
        spec("MenuButton", "MenuButton", "items", false, null);
        spec("MenuItem", "MenuItem", null, false, null);
        spec("SeparatorMenuItem", "MenuSeparator", null, false, null);
        spec("CheckMenuItem", "MenuItem", null, false, "a check menu item has no check state in JXML; it is a plain MenuItem");
        spec("RadioMenuItem", "MenuItem", null, false, "a radio menu item has no selected state in JXML; it is a plain MenuItem");
        spec("ImageView", "Image", null, false, null);

        ren("VBox", "spacing", "gap", "alignment", "alignment", "padding", "padding", "fillWidth", "fillWidth");
        ren("HBox", "spacing", "gap", "alignment", "alignment", "padding", "padding", "fillHeight", "fillHeight");
        ren("StackPane", "alignment", "alignment", "padding", "padding");
        ren("BorderPane", "padding", "padding");
        ren("GridPane", "hgap", "hgap", "vgap", "vgap", "alignment", "alignment", "padding", "padding");
        ren("AnchorPane", "padding", "padding");
        ren("Pane", "padding", "padding");
        ren("Region", "padding", "padding");
        ren("FlowPane", "hgap", "hgap", "vgap", "vgap", "alignment", "alignment", "padding", "padding",
                "orientation", "orientation", "prefWrapLength", "prefWrapLength", "columnHalignment", "columnHalignment",
                "rowValignment", "rowValignment");
        ren("TilePane", "hgap", "hgap", "vgap", "vgap", "alignment", "alignment", "padding", "padding",
                "orientation", "orientation", "prefColumns", "prefColumns", "prefRows", "prefRows",
                "prefTileWidth", "prefTileWidth", "prefTileHeight", "prefTileHeight", "tileAlignment", "tileAlignment");
        ren("ScrollPane", "fitToWidth", "fitToWidth", "fitToHeight", "fitToHeight", "hvalue", "hvalue", "vvalue", "vvalue");
        ren("SplitPane", "orientation", "orientation", "dividerPositions", "dividerPositions");
        ren("TitledPane", "text", "text", "expanded", "expanded", "collapsible", "collapsible");
        ren("Tab", "text", "title", "closable", "closable");
        ren("ToolBar", "padding", "padding");
        ren("Label", "text", "text", "wrapText", "wrapText");
        ren("Text", "text", "text");
        ren("Button", "text", "text", "defaultButton", "defaultButton");
        ren("ToggleButton", "text", "text", "selected", "selected");
        ren("RadioButton", "text", "text", "selected", "selected");
        ren("CheckBox", "text", "text", "selected", "selected");
        ren("Hyperlink", "text", "text", "visited", "visited");
        ren("TextField", "text", "text", "promptText", "prompt", "prefColumnCount", "columns");
        ren("PasswordField", "text", "text", "promptText", "prompt", "prefColumnCount", "columns");
        ren("TextArea", "text", "text", "promptText", "prompt", "wrapText", "wrapText", "prefColumnCount", "columns",
                "prefRowCount", "rows");
        ren("ComboBox", "value", "value", "promptText", "prompt");
        ren("ChoiceBox", "value", "value");
        ren("Spinner", "min", "min", "max", "max", "initialValue", "value", "amountToStepBy", "step");
        ren("Slider", "min", "min", "max", "max", "value", "value", "blockIncrement", "blockIncrement");
        ren("DatePicker", "promptText", "prompt");
        ren("ProgressBar", "progress", "progress");
        ren("ProgressIndicator", "progress", "progress");
        ren("Separator", "orientation", "orientation");
        ren("ScrollBar", "min", "min", "max", "max", "value", "value", "visibleAmount", "visibleAmount",
                "orientation", "orientation");
        ren("Menu", "text", "text");
        ren("MenuItem", "text", "text");
        ren("CheckMenuItem", "text", "text");
        ren("RadioMenuItem", "text", "text");
        ren("MenuButton", "text", "text");
        ren("ImageView", "fitWidth", "fitWidth", "fitHeight", "fitHeight", "preserveRatio", "preserveRatio");
        ren("TreeItem", "value", "text", "expanded", "expanded");
        ren("TableView", "fixedCellSize", "rowHeight");
        ren("ListView", "fixedCellSize", "cellHeight");
    }

    private static final Map<String, String> NAMED_COLORS = new HashMap<String, String>();

    static {
        String[][] colors = {{"white", "#FFFFFF"}, {"black", "#000000"}, {"red", "#FF0000"}, {"green", "#008000"},
            {"blue", "#0000FF"}, {"yellow", "#FFFF00"}, {"orange", "#FFA500"}, {"gray", "#808080"}, {"grey", "#808080"},
            {"lightgray", "#D3D3D3"}, {"lightgrey", "#D3D3D3"}, {"darkgray", "#A9A9A9"}, {"darkgrey", "#A9A9A9"},
            {"cyan", "#00FFFF"}, {"magenta", "#FF00FF"}, {"pink", "#FFC0CB"}, {"purple", "#800080"}, {"brown", "#A52A2A"},
            {"silver", "#C0C0C0"}, {"navy", "#000080"}, {"teal", "#008080"}, {"lime", "#00FF00"}, {"maroon", "#800000"},
            {"gold", "#FFD700"}, {"transparent", "#00000000"}};
        for (String[] c : colors) {
            NAMED_COLORS.put(c[0], c[1]);
        }
    }

    private final FxmlToJxml.Options options;
    private final FxmlReport report;
    private final String file;
    private final FxmlParser.Document doc;
    private final boolean runtime;
    private final Map<String, String> ids = new LinkedHashMap<String, String>();
    private final Map<String, String> groups = new HashMap<String, String>();
    private final Set<String> styleClasses = new LinkedHashSet<String>();
    /** Handler method name to {line, element, attribute} of its first use. */
    private final Map<String, Object[]> handlers = new LinkedHashMap<String, Object[]>();
    private final Map<JxmlParser.Node, List<String>> wraps = new IdentityHashMap<JxmlParser.Node, List<String>>();
    private final Set<String> seen = new HashSet<String>();
    private String controller;

    FxmlConverter(FxmlParser.Document doc, String file, FxmlToJxml.Options options, FxmlReport report, boolean runtime) {
        this.doc = doc;
        this.file = file;
        this.options = options;
        this.report = report;
        this.runtime = runtime;
    }

    // ---- reporting -------------------------------------------------------------------------------

    private void add(Level level, FxNode n, String attribute, String reason) {
        String key = level + "|" + n.line + "|" + n.name + "|" + attribute + "|" + reason;
        if (seen.add(key)) {
            report.add(level, file, n.line, n.name, attribute, reason);
        }
    }

    private void unsupported(FxNode n, String attribute, String reason) {
        add(Level.UNSUPPORTED, n, attribute, reason);
    }

    private void approximate(FxNode n, String attribute, String reason) {
        add(Level.APPROXIMATE, n, attribute, reason);
    }

    private void note(FxNode n, String attribute, String reason) {
        add(Level.NOTE, n, attribute, reason);
    }

    // ---- the file --------------------------------------------------------------------------------

    /** The JXML root, or null when the root element itself cannot be converted. */
    JxmlParser.Node convert() {
        FxNode root = doc.root;
        collectIds(root);
        JxmlParser.Node out = object(root);
        if (out == null) {
            return null;
        }
        out.attrs.remove("\u0000x");
        out.attrs.remove("\u0000y");
        if (controller != null) {
            Map<String, String> attrs = new LinkedHashMap<String, String>(out.attrs);
            out.attrs.clear();
            out.attrs.put("controller", controller);
            out.attrs.putAll(attrs);
        }
        List<JxmlParser.Node> styles = new ArrayList<JxmlParser.Node>();
        for (String name : styleClasses) {
            JxmlParser.Node style = new JxmlParser.Node("Style", root.line, file);
            style.attrs.put("name", name);
            styles.add(style);
        }
        out.children.addAll(0, styles);
        for (Map.Entry<String, Object[]> handler : handlers.entrySet()) {
            Object[] at = handler.getValue();
            report.add(Level.NOTE, file, ((Integer) at[0]).intValue(), (String) at[1], (String) at[2],
                    "JXML calls controller." + handler.getKey() + "() with no arguments; a JavaFX handler method that takes an event "
                    + "(ActionEvent, MouseEvent...) needs a no-argument version");
        }
        return out;
    }

    private void collectIds(FxNode n) {
        String id = n.attrs.get("fx:id");
        if (id != null && "ToggleGroup".equals(simpleClass(n.name))) {
            groups.put(id, id); // known before the buttons that use it, wherever fx:define stands
        } else if (id != null) {
            ids.put(id, n.name);
        }
        for (FxNode child : n.children) {
            collectIds(child);
        }
    }

    // ---- objects ---------------------------------------------------------------------------------

    /** Per element state while its attributes and children are read. */
    private final class S {
        final FxNode n;
        final String cls;
        final Spec spec;
        final JxmlParser.Node out;
        final List<JxmlParser.Node> kids = new ArrayList<JxmlParser.Node>();
        String id;
        String typeArgs;
        String toggleGroup;
        String userData;
        String visibleBinding;
        String managedBinding;
        boolean showRoot = true;
        boolean constrained;
        String closingPolicy;
        final List<String> useSize = new ArrayList<String>();
        final List<FxNode> columns = new ArrayList<FxNode>();
        final List<Map<String, String>> columnConstraints = new ArrayList<Map<String, String>>();
        final List<Map<String, String>> rowConstraints = new ArrayList<Map<String, String>>();
        String inlineStyle;

        S(FxNode n, String cls, Spec spec, JxmlParser.Node out) {
            this.n = n;
            this.cls = cls;
            this.spec = spec;
            this.out = out;
        }
    }

    private String simpleClass(String name) {
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            return name.startsWith("javafx.") ? name.substring(dot + 1) : null;
        }
        String imported = doc.imports.get(name);
        if (imported != null && !imported.startsWith("javafx.")) {
            return null;
        }
        return name;
    }

    private JxmlParser.Node object(FxNode n) {
        String cls;
        if ("fx:root".equals(n.name)) {
            String type = n.attrs.get("type");
            cls = type == null ? null : simpleClass(type);
            note(n, "type", "fx:root is the root of a custom component; it is converted as a plain " + type
                    + ", without the component class");
        } else {
            cls = simpleClass(n.name);
        }
        Spec spec = cls == null ? null : SPECS.get(cls);
        if (spec == null) {
            unsupported(n, null, cls == null ? n.name + " is not a JavaFX class this converter knows (a custom class); the element "
                    + "and everything inside it were left out" : n.name + " has no JXML tag; the element and everything inside it "
                    + "were left out");
            return null;
        }
        JxmlParser.Node out = new JxmlParser.Node(spec.jx, n.line, file);
        S s = new S(n, cls, spec, out);
        if (spec.approx != null) {
            approximate(n, null, spec.approx);
        }
        for (Map.Entry<String, String> attr : n.attrs.entrySet()) {
            attribute(s, attr.getKey(), attr.getValue());
        }
        for (FxNode child : n.children) {
            child(s, child);
        }
        finish(s);
        return out;
    }

    private void child(S s, FxNode c) {
        String name = c.name;
        if ("fx:include".equals(name)) {
            addChild(s, include(c));
        } else if ("fx:define".equals(name)) {
            define(c);
        } else if (name.startsWith("fx:")) {
            unsupported(c, null, name + " is not supported; the element was left out");
        } else if (c.isStaticProperty()) {
            staticElement(s, c);
        } else if (c.isProperty()) {
            property(s, c);
        } else if (s.spec.list == null) {
            unsupported(c, null, "<" + s.n.name + "> takes no child elements; the element was left out");
        } else if (s.cls.equals("BorderPane")) {
            JxmlParser.Node node = object(c);
            if (node != null) {
                node.attrs.put("slot", "center");
                s.kids.add(node);
            }
        } else {
            addChild(s, object(c));
        }
    }

    private void addChild(S s, JxmlParser.Node node) {
        if (node == null) {
            return;
        }
        if (s.spec.single && !s.kids.isEmpty()) {
            unsupported(s.n, null, "<" + s.spec.jx + "> takes one child; an extra child at line " + node.line + " was left out");
            return;
        }
        s.kids.add(node);
    }

    private void define(FxNode define) {
        for (FxNode c : define.children) {
            String cls = simpleClass(c.name);
            String id = c.attrs.get("fx:id");
            if ("ToggleGroup".equals(cls) && id != null) {
                groups.put(id, id);
                note(c, "fx:id", "ToggleGroup \"" + id + "\": the selected radio button is read from controller.get"
                        + Character.toUpperCase(id.charAt(0)) + id.substring(1) + "() (a String, the chosen button's value)");
            } else {
                unsupported(c, null, "fx:define of " + c.name + " is not supported; references to it are reported where used");
            }
        }
    }

    private JxmlParser.Node include(FxNode n) {
        JxmlParser.Node out = new JxmlParser.Node("Include", n.line, file);
        String source = n.attrs.get("source");
        if (source == null || source.trim().isEmpty()) {
            unsupported(n, "source", "fx:include without source; left out");
            return null;
        }
        if (source.startsWith("/") || source.contains("${") || source.startsWith("%") || source.startsWith("@")) {
            unsupported(n, "source", "source=\"" + source + "\" is absolute, bound or a resource key; only a path relative to this "
                    + "file converts; the include was left out");
            return null;
        }
        String src = source.trim();
        if (!runtime && src.endsWith(".fxml")) {
            src = src.substring(0, src.length() - ".fxml".length()) + ".jxml";
        }
        out.attrs.put("src", src);
        S s = new S(n, "fx:include", new Spec("Include", null, false, null), out);
        for (Map.Entry<String, String> attr : n.attrs.entrySet()) {
            String name = attr.getKey();
            if ("source".equals(name) || name.startsWith("xmlns")) {
                continue;
            }
            if ("fx:id".equals(name)) {
                id(s, attr.getValue());
                note(n, "fx:id", "the included screen's controller is not injected as " + attr.getValue()
                        + "Controller; bindings and handlers of the included file resolve against this file's controller");
            } else if (name.indexOf('.') > 0) {
                staticProp(s, name.substring(0, name.indexOf('.')), name.substring(name.indexOf('.') + 1), attr.getValue());
            } else {
                unsupported(n, name, "attribute of fx:include not supported");
            }
        }
        for (FxNode c : n.children) {
            if (c.isStaticProperty()) {
                staticElement(s, c);
            } else {
                unsupported(c, null, "child of fx:include not supported");
            }
        }
        return out;
    }

    // ---- attributes ------------------------------------------------------------------------------

    private JxmlSchema.Attr schemaAttr(String tag, String name) {
        if ("Include".equals(tag)) {
            return JxmlSchema.LAYOUT.get(name);
        }
        JxmlSchema.Tag t = JxmlSchema.tag(tag);
        return t == null ? null : t.attr(name);
    }

    private void attribute(S s, String name, String raw) {
        if (name.startsWith("xmlns")) {
            return;
        }
        if (name.startsWith("fx:")) {
            fxAttribute(s, name, raw);
            return;
        }
        int dot = name.indexOf('.');
        if (dot > 0) {
            staticProp(s, name.substring(0, dot), name.substring(dot + 1), raw);
            return;
        }
        if (name.startsWith("on") && name.length() > 2 && Character.isUpperCase(name.charAt(2))) {
            handler(s, name, raw);
            return;
        }
        Map<String, String> renames = RENAMES.get(s.cls);
        switch (name) {
            case "prefWidth": case "prefHeight": case "minWidth": case "minHeight": case "maxWidth": case "maxHeight":
                size(s, name, raw);
                return;
            case "visible":
                visible(s, name, raw, true);
                return;
            case "managed":
                visible(s, name, raw, false);
                return;
            case "disable":
                disable(s, raw);
                return;
            case "opacity":
                put(s, name, "opacity", raw);
                return;
            case "style":
                s.inlineStyle = raw;
                return;
            case "styleClass":
                styleClass(s, raw);
                return;
            case "layoutX": case "layoutY":
                layoutXY(s, name, raw);
                return;
            case "toggleGroup":
                toggleGroup(s, raw);
                return;
            case "userData":
                s.userData = raw;
                if (!"RadioButton".equals(s.cls) && !"ToggleButton".equals(s.cls)) {
                    unsupported(s.n, name, "userData has no JXML equivalent");
                }
                return;
            case "showRoot":
                s.showRoot = !"false".equals(raw);
                return;
            case "tabClosingPolicy":
                s.closingPolicy = raw;
                return;
            case "textFill":
                color(s, name, "textFill", raw);
                return;
            case "columnResizePolicy":
                unsupported(s.n, name, "only the CONSTRAINED_RESIZE_POLICY property element converts");
                return;
            case "id":
                unsupported(s.n, name, "id is the CSS id; JXML has no CSS selectors");
                return;
            case "mnemonicParsing": case "pickOnBounds":
                return; // no mnemonics or pick-on-bounds in the native UI; not worth a line per element
            case "orientation":
                if (renames == null || !renames.containsKey(name)) {
                    unsupported(s.n, name, "orientation has no JXML equivalent on <" + s.spec.jx + ">");
                    return;
                }
                break;
            default:
                break;
        }
        String target = renames == null ? null : renames.get(name);
        if (target == null) {
            unsupported(s.n, name, "no JXML equivalent on <" + s.spec.jx + ">");
            return;
        }
        String value = raw;
        if ("orientation".equals(name)) {
            value = raw.toLowerCase(Locale.ROOT);
        }
        put(s, name, target, value);
    }

    private void fxAttribute(S s, String name, String raw) {
        switch (name) {
            case "fx:id":
                id(s, raw);
                break;
            case "fx:controller":
                if (s.n == doc.root) {
                    controller = raw;
                } else {
                    unsupported(s.n, name, "a controller on a nested element has no JXML equivalent; JXML has one controller per file");
                }
                break;
            case "fx:typeArguments":
                s.typeArgs = raw;
                break;
            default:
                unsupported(s.n, name, name + " is not supported on <" + s.n.name + ">");
                break;
        }
    }

    private void id(S s, String id) {
        s.id = id;
        JxmlSchema.Tag tag = JxmlSchema.tag(s.out.tag);
        boolean keyed = !s.out.tag.equals("Tab") && (tag == null || !tag.virtual); // a Tab's content is keyed tab0.., a list's key is its rows'
        if (keyed) {
            s.out.attrs.put("key", id);
        }
        note(s.n, "fx:id", "fx:id=\"" + id + "\" " + (keyed ? "is kept as key=\"" + id + "\"" : "has no key in JXML here")
                + "; JavaFX injects this " + s.n.name
                + " into the controller field \"" + id + "\", the native UI has no node objects, so controller code that calls methods "
                + "on it has to move to bindings");
    }

    /**
     * Sets a JXML attribute from an FXML value: resolves {@code %key}, {@code ${path}} and escapes, checks the attribute
     * exists on the tag, may be bound and takes the value; reports and returns false when it does not.
     */
    private boolean put(S s, String fxName, String jxName, String raw) {
        return put(s.n, s.out, fxName, jxName, raw);
    }

    private boolean put(FxNode n, JxmlParser.Node out, String fxName, String jxName, String raw) {
        JxmlSchema.Attr attr = schemaAttr(out.tag, jxName);
        if (attr == null) {
            unsupported(n, fxName, "<" + out.tag + "> has no attribute " + jxName);
            return false;
        }
        String value = value(n, fxName, raw, attr);
        if (value == null) {
            return false;
        }
        if (out.attrs.containsKey(jxName) && !jxName.equals("key")) {
            out.attrs.remove(jxName);
        }
        out.attrs.put(jxName, value);
        return true;
    }

    /** The JXML text for an FXML attribute value, or null (reported) when it cannot be written. */
    private String value(FxNode n, String fxName, String raw, JxmlSchema.Attr attr) {
        String text = raw;
        boolean literal = true;
        if (raw.startsWith("\\")) {
            text = raw.substring(1);
        } else if (raw.startsWith("%")) {
            String key = raw.substring(1);
            if (options.bundle() == null) {
                unsupported(n, fxName, "resource key " + raw + ": no ResourceBundle was given and JXML has no i18n lookup; "
                        + "the key is left as the text");
            } else {
                try {
                    text = options.bundle().getString(key);
                } catch (MissingResourceException e) {
                    unsupported(n, fxName, "resource key " + raw + " is not in the ResourceBundle; the key is left as the text");
                }
            }
        } else if (raw.startsWith("${")) {
            literal = false;
        } else if (raw.startsWith("@") && attr.type != AttrType.IMAGE_SOURCE) {
            unsupported(n, fxName, "location value " + raw + " only converts as a picture source");
            return null;
        } else if (raw.startsWith("$")) {
            unsupported(n, fxName, "reference " + raw + " has no JXML equivalent here");
            return null;
        }
        if (!literal) {
            return binding(n, fxName, raw, attr);
        }
        if (attr.type == AttrType.HANDLER || attr.type == AttrType.CHANGE) {
            unsupported(n, fxName, "a handler is not a value");
            return null;
        }
        if (attr.choices != null && !attr.choices.contains(text.trim())) {
            unsupported(n, fxName, "\"" + text + "\" is not one of " + attr.choices);
            return null;
        }
        if (attr.type != AttrType.STRING && attr.type != AttrType.IMAGE_SOURCE && attr.type != AttrType.COLOR) {
            try {
                JxmlValues.literal(attr.type, text);
            } catch (IllegalArgumentException e) {
                unsupported(n, fxName, "\"" + text + "\" is not a valid " + attr.type.name().toLowerCase(Locale.ROOT) + " for JXML");
                return null;
            }
        }
        if (attr.type == AttrType.COLOR) {
            String color = color(text);
            if (color == null) {
                unsupported(n, fxName, "color \"" + text + "\" is not #RGB, #RRGGBB, rgb(), 0x.. or a basic color name");
                return null;
            }
            return color;
        }
        if (attr.type == AttrType.STRING || attr.type == AttrType.IMAGE_SOURCE) {
            if (text.startsWith("\\") || text.startsWith("#") || text.startsWith("$") || text.startsWith("@{")) {
                return "\\" + text;
            }
        }
        return text;
    }

    private String binding(FxNode n, String fxName, String raw, JxmlSchema.Attr attr) {
        if (!raw.endsWith("}")) {
            unsupported(n, fxName, "expression " + raw + " is not ${a.b}");
            return null;
        }
        String expr = raw.substring(2, raw.length() - 1).trim();
        boolean ofController = expr.startsWith("controller.");
        if (ofController) {
            expr = expr.substring("controller.".length());
        }
        if (!PATH.matcher(expr).matches()) {
            unsupported(n, fxName, "expression " + raw + " is not a plain property path (${name}, ${a.b}); JXML bindings read "
                    + "controller getters only");
            return null;
        }
        String first = expr.contains(".") ? expr.substring(0, expr.indexOf('.')) : expr;
        if (!ofController && (ids.containsKey(first) || groups.containsKey(first))) {
            unsupported(n, fxName, "expression " + raw + " reads a property of the node fx:id=\"" + first + "\"; the native UI has "
                    + "no node objects, so bind to the model the node shows");
            return null;
        }
        if (!attr.bindable) {
            unsupported(n, fxName, "JXML attribute " + attr.name + " cannot be bound; expression " + raw + " was left out");
            return null;
        }
        return "${" + expr + "}";
    }

    private void size(S s, String name, String raw) {
        String v = raw.trim();
        boolean min = name.startsWith("min");
        boolean max = name.startsWith("max");
        if ("-1".equals(v) || "-1.0".equals(v) || "USE_COMPUTED_SIZE".equals(v)) {
            return; // the default
        }
        if (max && (v.equals("Infinity") || v.equals("MAX_VALUE") || isHuge(v))) {
            return; // unbounded is the default
        }
        if ("-Infinity".equals(v) || "USE_PREF_SIZE".equals(v)) {
            if (min || max) {
                s.useSize.add(name);
            } else {
                unsupported(s.n, name, "USE_PREF_SIZE on a preferred size has no meaning");
            }
            return;
        }
        put(s, name, name, raw);
    }

    private static boolean isHuge(String v) {
        try {
            return Double.parseDouble(v) >= 1e300;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void visible(S s, String name, String raw, boolean visible) {
        String v = raw.trim();
        if (raw.startsWith("${")) {
            String b = binding(s.n, name, raw, new JxmlSchema.Attr("test", "test", AttrType.BOOLEAN, true));
            if (b != null) {
                if (visible) {
                    s.visibleBinding = b;
                } else {
                    s.managedBinding = b;
                }
                approximate(s.n, name, name + "=" + raw + " becomes <If test=\"" + b + "\"> around the element: when false the "
                        + "element is removed from the layout, not just hidden");
            }
            return;
        }
        if ("true".equals(v)) {
            return;
        }
        if (visible && "false".equals(v)) {
            put(s, name, "hidden", "true");
        } else {
            unsupported(s.n, name, "managed=\"" + raw + "\": JXML has no node that is painted but left out of the layout (or "
                    + "the reverse); use a bound visible (an <If>) instead");
        }
    }

    private void disable(S s, String raw) {
        if ("false".equals(raw.trim())) {
            return;
        }
        if (schemaAttr(s.out.tag, "disabled") == null) {
            unsupported(s.n, "disable", "<" + s.out.tag + "> has no attribute disabled");
            return;
        }
        put(s, "disable", "disabled", raw);
    }

    private void layoutXY(S s, String name, String raw) {
        String jx = "layoutX".equals(name) ? "x" : "y";
        if ("0".equals(raw) || "0.0".equals(raw)) {
            return;
        }
        // JavaFX only reads layoutX/layoutY in a pane that does not manage its children; in any other parent they are ignored there too
        parentUses(s, name, jx);
    }

    /** layoutX/Y are decided when the parent is known: kept in the element until then. */
    private void parentUses(S s, String fxName, String jx) {
        s.out.attrs.put("\u0000" + jx, s.n.attrs.get(fxName));
    }

    private void handler(S s, String name, String raw) {
        String v = raw.trim();
        if (!v.startsWith("#") || !JxmlTemplate.isIdentifier(v.substring(1))) {
            unsupported(s.n, name, name + "=\"" + raw + "\" is not #method; scripts and expressions are not converted");
            return;
        }
        String target;
        String tag = s.out.tag;
        switch (name) {
            case "onAction":
                if ("TextField".equals(tag) || "PasswordField".equals(tag) || "TextArea".equals(tag)) {
                    unsupported(s.n, name, "onAction on a text input fires on Enter; JXML onAction is a click, and an input reports "
                            + "edits with onChange (new text) instead");
                    return;
                }
                if ("ComboBox".equals(tag) || "ChoiceBox".equals(tag) || "DatePicker".equals(tag) || "ColorPicker".equals(tag)) {
                    target = "onChange";
                    approximate(s.n, name, "onAction becomes onChange: the method is called with the new value when the choice changes");
                } else if ("Menu".equals(tag)) {
                    unsupported(s.n, name, "a Menu has no action in JXML");
                    return;
                } else {
                    target = "onAction";
                }
                break;
            case "onMouseClicked":
                if (s.n.attrs.containsKey("onAction") || s.out.attrs.containsKey("onAction")) {
                    unsupported(s.n, name, "the element already has an onAction; JXML has one click handler");
                    return;
                }
                target = "onAction";
                approximate(s.n, name, "onMouseClicked becomes onAction: a click of the main button, called without the MouseEvent");
                break;
            default:
                unsupported(s.n, name, name + " has no JXML equivalent (only click and value-change handlers exist)");
                return;
        }
        if (schemaAttr(tag, target) == null) {
            unsupported(s.n, name, "<" + tag + "> has no attribute " + target);
            return;
        }
        s.out.attrs.put(target, v);
        if (!handlers.containsKey(v.substring(1))) {
            handlers.put(v.substring(1), new Object[] {Integer.valueOf(s.n.line), s.n.name, name});
        }
    }

    private void toggleGroup(S s, String raw) {
        if (raw.startsWith("$") && groups.containsKey(raw.substring(1))) {
            s.toggleGroup = raw.substring(1);
        } else if (raw.startsWith("$")) {
            unsupported(s.n, "toggleGroup", "group " + raw + " is not a ToggleGroup defined with fx:define in this file");
        } else {
            unsupported(s.n, "toggleGroup", "toggleGroup=\"" + raw + "\" is not a reference");
        }
    }

    private void styleClass(S s, String raw) {
        if ("Include".equals(s.out.tag)) {
            unsupported(s.n, "styleClass", "an included file cannot take a style");
            return;
        }
        List<String> used = new ArrayList<String>();
        for (String name : raw.trim().split("[\\s,]+")) {
            if (name.isEmpty()) {
                continue;
            }
            if (!STYLE_NAME.matcher(name).matches()) {
                unsupported(s.n, "styleClass", "style class \"" + name + "\" is not a valid JXML style name");
                continue;
            }
            used.add(name);
            if (styleClasses.add(name)) {
                approximate(s.n, "styleClass", "CSS class \"" + name + "\" is kept as an empty <Style name=\"" + name + "\"/>; its "
                        + "CSS rules are not converted, put the attributes in the <Style>");
            }
        }
        if (!used.isEmpty()) {
            String existing = s.out.attrs.get("style");
            s.out.attrs.put("style", (existing == null ? "" : existing + " ") + String.join(" ", used));
        }
    }

    private void color(S s, String fxName, String jx, String raw) {
        put(s, fxName, jx, raw);
    }

    /** #RGB, #RRGGBB, #RRGGBBAA, 0x.., rgb(), rgba() and a few names, as the JXML #RGB, #RRGGBB or #AARRGGBB; null if unknown. */
    static String color(String raw) {
        String v = raw.trim().toLowerCase(Locale.ROOT);
        String named = NAMED_COLORS.get(v);
        if (named != null) {
            return named;
        }
        String hex = null;
        if (v.startsWith("#")) {
            hex = v.substring(1);
        } else if (v.startsWith("0x")) {
            hex = v.substring(2);
        }
        if (hex != null) {
            if (!hex.matches("[0-9a-f]+")) {
                return null;
            }
            if (hex.length() == 3 || hex.length() == 6) {
                return "#" + hex.toUpperCase(Locale.ROOT);
            }
            if (hex.length() == 8) { // web colors are RRGGBBAA, JXML is AARRGGBB
                return "#" + (hex.substring(6) + hex.substring(0, 6)).toUpperCase(Locale.ROOT);
            }
            return null;
        }
        if (v.startsWith("rgb(") && v.endsWith(")") || v.startsWith("rgba(") && v.endsWith(")")) {
            String[] parts = v.substring(v.indexOf('(') + 1, v.length() - 1).split(",");
            if (parts.length != (v.startsWith("rgba") ? 4 : 3)) {
                return null;
            }
            try {
                int[] c = new int[3];
                for (int i = 0; i < 3; i++) {
                    String p = parts[i].trim();
                    c[i] = p.endsWith("%") ? (int) Math.round(Double.parseDouble(p.substring(0, p.length() - 1)) * 2.55)
                            : Integer.parseInt(p);
                    if (c[i] < 0 || c[i] > 255) {
                        return null;
                    }
                }
                String rgb = String.format("%02X%02X%02X", c[0], c[1], c[2]);
                if (parts.length == 4) {
                    int a = (int) Math.round(Double.parseDouble(parts[3].trim()) * 255);
                    return String.format("#%02X%s", Math.max(0, Math.min(255, a)), rgb);
                }
                return "#" + rgb;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    // ---- static properties: placement ------------------------------------------------------------

    private void staticProp(S s, String owner, String prop, String raw) {
        switch (prop) {
            case "rowIndex":
                put(s, owner + "." + prop, "row", raw);
                break;
            case "columnIndex":
                put(s, owner + "." + prop, "column", raw);
                break;
            case "rowSpan": case "columnSpan":
                put(s, owner + "." + prop, prop, raw);
                break;
            case "hgrow": case "vgrow": {
                String v = raw.trim().toUpperCase(Locale.ROOT);
                if ("ALWAYS".equals(v) || "SOMETIMES".equals(v)) {
                    put(s, owner + "." + prop, prop, v.toLowerCase(Locale.ROOT));
                } else if (!"NEVER".equals(v)) {
                    unsupported(s.n, owner + "." + prop, "priority \"" + raw + "\" is not ALWAYS, SOMETIMES or NEVER");
                }
                break;
            }
            case "margin": {
                String insets = insetsText(s.n, owner + "." + prop, raw);
                if (insets != null) {
                    put(s, owner + "." + prop, "margin", insets);
                }
                break;
            }
            case "halignment": case "valignment": {
                String v = raw.trim().toUpperCase(Locale.ROOT);
                if ("BASELINE".equals(v)) {
                    unsupported(s.n, owner + "." + prop, "baseline alignment is not supported in JXML");
                } else {
                    put(s, owner + "." + prop, prop, v);
                }
                break;
            }
            case "alignment":
                posAlignment(s, owner + "." + prop, raw);
                break;
            case "topAnchor": case "rightAnchor": case "bottomAnchor": case "leftAnchor":
                put(s, owner + "." + prop, prop.substring(0, prop.length() - "Anchor".length()), raw);
                break;
            default:
                unsupported(s.n, owner + "." + prop, "static property " + owner + "." + prop + " has no JXML equivalent");
                break;
        }
    }

    /** {@code TOP_LEFT} and the like, set as the child's halignment and valignment (BorderPane.alignment, StackPane.alignment). */
    private void posAlignment(S s, String fxName, String raw) {
        String v = raw.trim().toUpperCase(Locale.ROOT);
        String vertical;
        String horizontal;
        if ("CENTER".equals(v)) {
            vertical = "CENTER";
            horizontal = "CENTER";
        } else if (v.indexOf('_') > 0) {
            vertical = v.substring(0, v.indexOf('_'));
            horizontal = v.substring(v.indexOf('_') + 1);
        } else {
            unsupported(s.n, fxName, "alignment \"" + raw + "\" is not a Pos value");
            return;
        }
        if ("BASELINE".equals(vertical) || !Arrays.asList("TOP", "CENTER", "BOTTOM").contains(vertical)
                || !Arrays.asList("LEFT", "CENTER", "RIGHT").contains(horizontal)) {
            unsupported(s.n, fxName, "alignment \"" + raw + "\" is not supported (baseline or unknown)");
            return;
        }
        put(s, fxName, "halignment", horizontal);
        put(s, fxName, "valignment", vertical);
    }

    private void staticElement(S s, FxNode c) {
        int dot = c.name.lastIndexOf('.');
        String owner = c.name.substring(0, dot);
        String prop = c.name.substring(dot + 1);
        if ("margin".equals(prop)) {
            String insets = insetsOf(c);
            if (insets != null) {
                put(s, c.name, "margin", insets);
            }
        } else if (c.children.isEmpty() && !c.trimmedText().isEmpty()) {
            staticProp(s, owner, prop, c.trimmedText());
        } else {
            unsupported(c, null, "static property element " + c.name + " is not supported");
        }
    }

    // ---- insets ----------------------------------------------------------------------------------

    private String insetsOf(FxNode property) {
        if (property.children.size() == 1 && property.children.get(0).name.endsWith("Insets")) {
            FxNode i = property.children.get(0);
            if ("EMPTY".equals(i.attrs.get("fx:constant"))) {
                return "0";
            }
            String all = i.attrs.get("topRightBottomLeft");
            if (all != null) {
                return insetsText(i, "topRightBottomLeft", all);
            }
            String[] sides = new String[4];
            String[] names = {"top", "right", "bottom", "left"};
            for (int k = 0; k < 4; k++) {
                String v = i.attrs.get(names[k]);
                sides[k] = v == null ? "0" : v.trim();
                if (!isNumber(sides[k])) {
                    unsupported(i, names[k], "inset \"" + v + "\" is not a plain number");
                    return null;
                }
            }
            for (String key : i.attrs.keySet()) {
                if (!Arrays.asList(names).contains(key) && !key.startsWith("xmlns") && !key.equals("topRightBottomLeft")
                        && !key.equals("fx:constant")) {
                    unsupported(i, key, "unknown Insets attribute");
                }
            }
            return sidesText(sides);
        }
        if (property.children.isEmpty() && !property.trimmedText().isEmpty()) {
            return insetsText(property, null, property.trimmedText());
        }
        unsupported(property, null, "property " + property.name + " needs one <Insets> element");
        return null;
    }

    private String sidesText(String[] sides) {
        double first = Double.parseDouble(sides[0]);
        boolean same = true;
        for (String side : sides) {
            same &= Double.parseDouble(side) == first;
        }
        return same ? sides[0] : sides[0] + " " + sides[1] + " " + sides[2] + " " + sides[3];
    }

    private String insetsText(FxNode n, String attribute, String raw) {
        String[] parts = raw.trim().split("[\\s,]+");
        if (parts.length != 1 && parts.length != 2 && parts.length != 4) {
            unsupported(n, attribute, "\"" + raw + "\" is not 1, 2 or 4 numbers");
            return null;
        }
        for (String part : parts) {
            if (!isNumber(part)) {
                unsupported(n, attribute, "\"" + raw + "\" is not made of plain numbers");
                return null;
            }
        }
        return String.join(" ", parts);
    }

    private static boolean isNumber(String v) {
        try {
            Double.parseDouble(v);
            return !v.endsWith("d") && !v.endsWith("f") && !v.endsWith("D") && !v.endsWith("F");
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ---- property elements -----------------------------------------------------------------------

    /** The text of a property element: its content, or the value of a single {@code <String fx:value="..."/>}. */
    private String propText(FxNode c) {
        if (c.children.isEmpty()) {
            return c.trimmedText();
        }
        if (c.children.size() == 1 && c.children.get(0).name.equals("String") && c.children.get(0).attrs.containsKey("fx:value")) {
            return c.children.get(0).attrs.get("fx:value");
        }
        return null;
    }

    private void property(S s, FxNode c) {
        String name = c.name;
        Spec spec = s.spec;
        if (name.equals(spec.list)) {
            for (FxNode g : c.children) {
                if ("fx:include".equals(g.name)) {
                    addChild(s, include(g));
                } else if ("fx:define".equals(g.name)) {
                    define(g);
                } else if (g.name.startsWith("fx:")) {
                    unsupported(g, null, g.name + " is not supported inside <" + name + ">");
                } else {
                    JxmlParser.Node node = object(g);
                    if (node != null && s.cls.equals("BorderPane")) {
                        node.attrs.put("slot", "center");
                    }
                    addChild(s, node);
                }
            }
            return;
        }
        if (s.cls.equals("BorderPane") && Arrays.asList("top", "left", "center", "right", "bottom").contains(name)) {
            for (FxNode g : c.children) {
                JxmlParser.Node node = "fx:include".equals(g.name) ? include(g) : object(g);
                if (node != null) {
                    node.attrs.put("slot", name);
                    s.kids.add(node);
                }
            }
            return;
        }
        switch (name) {
            case "padding": {
                String insets = insetsOf(c);
                if (insets != null) {
                    put(s, name, "padding", insets);
                }
                return;
            }
            case "tooltip":
                tooltip(s, c);
                return;
            case "font":
                font(s, c);
                return;
            case "textFill":
                colorElement(s, c, "textFill");
                return;
            case "graphic":
                graphic(s, c);
                return;
            case "image":
                image(s, c);
                return;
            case "columnConstraints": case "rowConstraints":
                constraints(s, c, name.startsWith("column"));
                return;
            case "columns":
                if ("TableView".equals(s.cls)) {
                    for (FxNode g : c.children) {
                        if (g.name.endsWith("TableColumn")) {
                            s.columns.add(g);
                        } else {
                            unsupported(g, null, "only TableColumn converts inside columns");
                        }
                    }
                    return;
                }
                break;
            case "columnResizePolicy": {
                FxNode g = c.children.size() == 1 ? c.children.get(0) : null;
                if (g != null && "CONSTRAINED_RESIZE_POLICY".equals(g.attrs.get("fx:constant"))) {
                    s.constrained = true;
                } else {
                    unsupported(c, null, "only the CONSTRAINED_RESIZE_POLICY constant converts");
                }
                return;
            }
            case "items":
                items(s, c);
                return;
            case "valueFactory":
                valueFactory(s, c);
                return;
            case "toggleGroup": {
                FxNode g = c.children.size() == 1 ? c.children.get(0) : null;
                String ref = g == null ? null : g.attrs.get("source");
                if (g != null && "fx:reference".equals(g.name) && ref != null) {
                    toggleGroup(s, "$" + ref);
                } else {
                    unsupported(c, null, "toggleGroup needs an fx:reference to a ToggleGroup from fx:define");
                }
                return;
            }
            case "styleClass":
                for (FxNode g : c.children) {
                    if ("String".equals(g.name) && g.attrs.containsKey("fx:value")) {
                        styleClass(s, g.attrs.get("fx:value"));
                    } else {
                        unsupported(g, null, "styleClass takes <String fx:value=\"...\"/> elements");
                    }
                }
                return;
            case "accelerator":
                unsupported(c, null, "keyboard accelerators are not converted; the MenuItem accelerator attribute takes text such as Ctrl+O");
                return;
            case "stylesheets":
                unsupported(c, null, "CSS files are not converted");
                return;
            case "cellValueFactory": case "cellFactory":
                return; // read with its TableColumn
            default:
                break;
        }
        String text = propText(c);
        if (text != null && !text.isEmpty() && (RENAMES.containsKey(s.cls) && RENAMES.get(s.cls).containsKey(name)
                || Arrays.asList("prefWidth", "prefHeight", "minWidth", "minHeight", "maxWidth", "maxHeight", "opacity", "visible",
                "disable", "managed", "style", "userData").contains(name))) {
            attribute(s, name, text);
            return;
        }
        unsupported(c, null, "property element <" + name + "> is not converted");
    }

    private void tooltip(S s, FxNode c) {
        FxNode t = c.children.size() == 1 ? c.children.get(0) : null;
        if (t == null || !"Tooltip".equals(simpleClass(t.name))) {
            unsupported(c, null, "tooltip needs one <Tooltip>");
            return;
        }
        for (Map.Entry<String, String> a : t.attrs.entrySet()) {
            if ("text".equals(a.getKey())) {
                put(t, s.out, "tooltip.text", "tooltip", a.getValue());
            } else if (!a.getKey().startsWith("xmlns")) {
                unsupported(t, a.getKey(), "Tooltip attribute not converted");
            }
        }
    }

    private void font(S s, FxNode c) {
        FxNode f = c.children.size() == 1 ? c.children.get(0) : null;
        if (f == null || !"Font".equals(simpleClass(f.name))) {
            unsupported(c, null, "font needs one <Font>");
            return;
        }
        for (Map.Entry<String, String> a : f.attrs.entrySet()) {
            String key = a.getKey();
            if ("size".equals(key)) {
                put(f, s.out, "font.size", "fontSize", a.getValue());
            } else if ("name".equals(key)) {
                String lower = a.getValue().toLowerCase(Locale.ROOT);
                if (lower.contains("bold")) {
                    put(f, s.out, "font.name", "bold", "true");
                }
                if (!lower.equals("system") && !lower.equals("system bold") && !lower.equals("system regular")) {
                    unsupported(f, key, "font family \"" + a.getValue() + "\" is not converted (only the bold flag and the size are)");
                }
            } else if (!key.startsWith("xmlns")) {
                unsupported(f, key, "Font attribute not converted");
            }
        }
    }

    private void colorElement(S s, FxNode c, String jx) {
        FxNode col = c.children.size() == 1 ? c.children.get(0) : null;
        String hex = null;
        if (col != null && "Color".equals(simpleClass(col.name))) {
            if (col.attrs.containsKey("fx:constant")) {
                hex = color(col.attrs.get("fx:constant"));
            } else if (col.attrs.containsKey("red") || col.attrs.containsKey("green") || col.attrs.containsKey("blue")) {
                try {
                    int r = channel(col.attrs.get("red"));
                    int g = channel(col.attrs.get("green"));
                    int b = channel(col.attrs.get("blue"));
                    String op = col.attrs.get("opacity");
                    hex = op == null || Double.parseDouble(op) >= 1 ? String.format("#%02X%02X%02X", r, g, b)
                            : String.format("#%02X%02X%02X%02X", channel(op), r, g, b);
                } catch (NumberFormatException e) {
                    hex = null;
                }
            }
        }
        if (hex == null) {
            unsupported(c, null, "color element not converted (a <Color red green blue/> or fx:constant is)");
        } else {
            put(c, s.out, c.name, jx, hex);
        }
    }

    private static int channel(String v) {
        return v == null ? 0 : (int) Math.round(Double.parseDouble(v) * 255);
    }

    private void graphic(S s, FxNode c) {
        if (!"Button".equals(s.cls)) {
            unsupported(c, null, "graphic is only converted on a Button (its one child)");
            return;
        }
        if (c.children.size() != 1) {
            unsupported(c, null, "graphic needs one element");
            return;
        }
        JxmlParser.Node g = object(c.children.get(0));
        if (g != null) {
            s.kids.add(g);
        }
    }

    private void image(S s, FxNode c) {
        if (!"ImageView".equals(s.cls)) {
            unsupported(c, null, "image is only converted on an ImageView");
            return;
        }
        FxNode i = c.children.size() == 1 ? c.children.get(0) : null;
        if (i == null || !"Image".equals(simpleClass(i.name))) {
            unsupported(c, null, "image needs one <Image url=\"@picture.png\"/>");
            return;
        }
        for (Map.Entry<String, String> a : i.attrs.entrySet()) {
            if ("url".equals(a.getKey())) {
                String src = imageSource(i, a.getValue());
                if (src != null) {
                    s.out.attrs.put("src", src);
                }
            } else if (!a.getKey().startsWith("xmlns")) {
                unsupported(i, a.getKey(), "Image attribute not converted (the file is read at its own size; use fitWidth/fitHeight)");
            }
        }
    }

    private String imageSource(FxNode n, String url) {
        if (url.startsWith("@/")) {
            return "classpath:" + url.substring(2);
        }
        if (url.startsWith("@")) {
            return url.substring(1);
        }
        if (url.startsWith("%") || url.startsWith("${")) {
            unsupported(n, "url", "url=\"" + url + "\" is not a plain location");
            return null;
        }
        if (url.startsWith("file:") || url.startsWith("classpath:")) {
            return url;
        }
        unsupported(n, "url", "url=\"" + url + "\": only @relative, @/classpath and file: locations convert");
        return null;
    }

    // ---- items of combo boxes, spinners ----------------------------------------------------------

    private void items(S s, FxNode c) {
        if ("ComboBox".equals(s.cls) || "ChoiceBox".equals(s.cls)) {
            FxNode list = c.children.size() == 1 ? c.children.get(0) : null;
            if (list == null || !list.name.endsWith("FXCollections")) {
                unsupported(c, null, "items needs <FXCollections fx:factory=\"observableArrayList\"> with <String fx:value=\"...\"/>");
                return;
            }
            List<String> values = new ArrayList<String>();
            for (FxNode item : list.children) {
                String v = "String".equals(item.name) ? item.attrs.get("fx:value") : null;
                if (v == null) {
                    unsupported(item, null, "only <String fx:value=\"...\"/> items convert");
                    continue;
                }
                String resolved = v;
                if (v.startsWith("%") && options.bundle() != null) {
                    try {
                        resolved = options.bundle().getString(v.substring(1));
                    } catch (MissingResourceException e) {
                        unsupported(item, "fx:value", "resource key " + v + " is not in the ResourceBundle");
                    }
                } else if (v.startsWith("%")) {
                    unsupported(item, "fx:value", "resource key " + v + ": no ResourceBundle was given");
                }
                if (resolved.contains(",")) {
                    unsupported(item, "fx:value", "item \"" + resolved + "\" contains a comma, which separates the items in JXML; bind "
                            + "items=\"${...}\" instead");
                    continue;
                }
                values.add(resolved);
            }
            if (!values.isEmpty()) {
                s.out.attrs.put("items", String.join(", ", values));
            }
        } else {
            unsupported(c, null, "the rows of a " + s.n.name + " come from a binding (items=\"${...}\"), not from the FXML");
        }
    }

    private void valueFactory(S s, FxNode c) {
        FxNode f = c.children.size() == 1 ? c.children.get(0) : null;
        if (!"Spinner".equals(s.cls) || f == null || !f.name.endsWith("SpinnerValueFactory")
                && !f.name.contains("SpinnerValueFactory.")) {
            unsupported(c, null, "only Integer/Double SpinnerValueFactory on a Spinner converts");
            return;
        }
        for (Map.Entry<String, String> a : f.attrs.entrySet()) {
            String key = a.getKey();
            if ("min".equals(key) || "max".equals(key)) {
                put(f, s.out, key, key, a.getValue());
            } else if ("initialValue".equals(key)) {
                put(f, s.out, key, "value", a.getValue());
            } else if ("amountToStepBy".equals(key)) {
                put(f, s.out, key, "step", a.getValue());
            } else if (!key.startsWith("xmlns")) {
                unsupported(f, key, "value factory attribute not converted");
            }
        }
    }

    // ---- grid constraints ------------------------------------------------------------------------

    private void constraints(S s, FxNode c, boolean columns) {
        List<Map<String, String>> into = columns ? s.columnConstraints : s.rowConstraints;
        for (FxNode g : c.children) {
            Map<String, String> m = new HashMap<String, String>();
            List<String> lost = new ArrayList<String>();
            for (Map.Entry<String, String> a : g.attrs.entrySet()) {
                String key = a.getKey();
                if (key.startsWith("xmlns")) {
                    continue;
                }
                boolean grow = columns ? "hgrow".equals(key) : "vgrow".equals(key);
                boolean align = columns ? "halignment".equals(key) : "valignment".equals(key);
                if (grow || align) {
                    m.put(key, a.getValue());
                } else if (isDefaultConstraint(key, a.getValue())) {
                    continue;
                } else {
                    lost.add(key);
                }
            }
            if (!lost.isEmpty()) {
                approximate(g, String.join(", ", lost), "JXML Grid has no column/row definitions: " + lost + " were not converted; "
                        + "the track is sized from its cells (hgrow/vgrow and alignment are applied to the cells of the track)");
            }
            into.add(m);
        }
    }

    private static boolean isDefaultConstraint(String key, String value) {
        String v = value.trim();
        return ("minWidth".equals(key) || "minHeight".equals(key) || "prefWidth".equals(key) || "prefHeight".equals(key)
                || "maxWidth".equals(key) || "maxHeight".equals(key)) && ("-1".equals(v) || "-1.0".equals(v)
                || "USE_COMPUTED_SIZE".equals(v)) || ("fillWidth".equals(key) || "fillHeight".equals(key)) && "true".equals(v);
    }

    private void applyConstraints(S s) {
        for (int pass = 0; pass < 2; pass++) {
            boolean columns = pass == 0;
            List<Map<String, String>> list = columns ? s.columnConstraints : s.rowConstraints;
            if (list.isEmpty()) {
                continue;
            }
            for (JxmlParser.Node kid : s.kids) {
                JxmlParser.Node leaf = leaf(kid);
                String index = leaf.attrs.get(columns ? "column" : "row");
                int i = index == null ? 0 : Integer.parseInt(index.trim());
                if (i < 0 || i >= list.size()) {
                    continue;
                }
                Map<String, String> m = list.get(i);
                String grow = m.get(columns ? "hgrow" : "vgrow");
                if (grow != null && !leaf.attrs.containsKey(columns ? "hgrow" : "vgrow")) {
                    String v = grow.trim().toLowerCase(Locale.ROOT);
                    if ("always".equals(v) || "sometimes".equals(v)) {
                        leaf.attrs.put(columns ? "hgrow" : "vgrow", v);
                    }
                }
                String align = m.get(columns ? "halignment" : "valignment");
                String key = columns ? "halignment" : "valignment";
                if (align != null && !leaf.attrs.containsKey(key) && !"BASELINE".equalsIgnoreCase(align.trim())) {
                    leaf.attrs.put(key, align.trim().toUpperCase(Locale.ROOT));
                }
            }
        }
    }

    /** The element a condition wraps (conditions pass their children through to the grid). */
    private JxmlParser.Node leaf(JxmlParser.Node node) {
        JxmlParser.Node n = node;
        while ("If".equals(n.tag) && n.children.size() == 1) {
            n = n.children.get(0);
        }
        return n;
    }

    // ---- finishing an element --------------------------------------------------------------------

    private void finish(S s) {
        JxmlParser.Node out = s.out;
        FxNode n = s.n;
        // USE_PREF_SIZE on a min or max: the size is the preferred size, which JXML says by giving the number
        for (String name : s.useSize) {
            String pref = name.endsWith("Width") ? "prefWidth" : "prefHeight";
            String value = out.attrs.get(pref);
            if (value != null && !value.startsWith("${")) {
                out.attrs.put(name, value);
            } else {
                approximate(n, name, name + "=USE_PREF_SIZE without a literal " + pref + ": the size stays flexible in JXML");
            }
        }
        // radio and toggle buttons of a ToggleGroup
        if (s.toggleGroup != null) {
            groupMember(s);
        }
        if ("TabPane".equals(s.cls)) {
            closingPolicy(s);
        }
        if ("TreeView".equals(s.cls) && !s.showRoot && s.kids.size() == 1) {
            List<JxmlParser.Node> top = new ArrayList<JxmlParser.Node>(s.kids.get(0).children);
            s.kids.clear();
            s.kids.addAll(top);
        }
        if ("GridPane".equals(s.cls)) {
            applyConstraints(s);
        }
        if (s.inlineStyle != null) {
            inlineStyle(s);
        }
        if ("ListView".equals(s.cls)) {
            listTemplate(s);
        } else if ("TableView".equals(s.cls)) {
            tableTemplate(s);
        }
        for (JxmlParser.Node kid : s.kids) {
            placeByLayout(s, kid);
            out.children.add(wrapped(kid));
        }
        if (s.visibleBinding != null || s.managedBinding != null) {
            List<String> tests = new ArrayList<String>();
            if (s.managedBinding != null) {
                tests.add(s.managedBinding);
            }
            if (s.visibleBinding != null) {
                tests.add(s.visibleBinding);
            }
            if ("Tab".equals(out.tag) || "Menu".equals(out.tag) || "MenuItem".equals(out.tag) || "TreeItem".equals(out.tag)) {
                unsupported(n, "visible", "a bound visible/managed cannot wrap a <" + out.tag + ">");
            } else {
                wraps.put(out, tests);
            }
        }
        if (n.attrs.containsKey("fx:controller") && n != doc.root) {
            return;
        }
    }

    /** layoutX/layoutY count only in a parent that does not manage its children: a Pane (x, y) or an AnchorPane (left, top). */
    private void placeByLayout(S parent, JxmlParser.Node kid) {
        JxmlParser.Node leaf = leaf(kid);
        for (String axis : new String[] {"x", "y"}) {
            String value = leaf.attrs.remove("\u0000" + axis);
            if (value == null) {
                continue;
            }
            String cls = parent.cls;
            if ("Pane".equals(cls) || "Region".equals(cls)) {
                leaf.attrs.put(axis, value);
            } else if ("AnchorPane".equals(cls)) {
                String anchor = "x".equals(axis) ? "left" : "top";
                String opposite = "x".equals(axis) ? "right" : "bottom";
                if (!leaf.attrs.containsKey(anchor) && !leaf.attrs.containsKey(opposite)) {
                    leaf.attrs.put(anchor, value);
                }
            }
        }
    }

    private JxmlParser.Node wrapped(JxmlParser.Node node) {
        List<String> tests = wraps.get(node);
        if (tests == null) {
            return node;
        }
        JxmlParser.Node current = node;
        for (int i = 0; i < tests.size(); i++) {
            JxmlParser.Node cond = new JxmlParser.Node("If", node.line, file);
            cond.attrs.put("test", tests.get(i));
            cond.children.add(current);
            current = cond;
        }
        return current;
    }

    private void groupMember(S s) {
        String group = groups.get(s.toggleGroup);
        String source = s.userData != null ? s.userData : s.n.attrs.get("text");
        if (!"RadioButton".equals(s.cls) && !"ToggleButton".equals(s.cls)) {
            unsupported(s.n, "toggleGroup", "only RadioButton and ToggleButton join a group");
            return;
        }
        if (source == null) {
            unsupported(s.n, "toggleGroup", "a button in a group needs a userData or a text: it is the value the group holds");
            return;
        }
        JxmlSchema.Attr valueAttr = schemaAttr(s.out.tag, "value");
        String value = source;
        if (source.startsWith("%") || source.startsWith("\\")) {
            value = value(s.n, "userData", source, valueAttr);
        } else if (source.startsWith("${")) {
            unsupported(s.n, "toggleGroup", "the value of a button in a group must be a literal, not " + source);
            value = null;
        }
        if (value == null) {
            return;
        }
        if (s.n.attrs.containsKey("selected") && "true".equals(s.n.attrs.get("selected").trim())) {
            approximate(s.n, "selected", "the initial choice of group \"" + group + "\" comes from the controller (getter returns this "
                    + "button's value); selected=\"true\" was not copied");
        }
        s.out.attrs.remove("selected");
        s.out.attrs.put("group", "${" + group + "}");
        s.out.attrs.put("value", value);
    }

    private void closingPolicy(S s) {
        String policy = s.closingPolicy;
        if (policy == null || "SELECTED_TAB".equals(policy)) {
            return;
        }
        String closable = "UNAVAILABLE".equals(policy) ? "false" : "ALL_TABS".equals(policy) ? "true" : null;
        if (closable == null) {
            unsupported(s.n, "tabClosingPolicy", "unknown policy " + policy);
            return;
        }
        for (JxmlParser.Node tab : s.kids) {
            JxmlParser.Node t = leaf(tab);
            if ("Tab".equals(t.tag) && !t.attrs.containsKey("closable")) {
                t.attrs.put("closable", closable);
            }
        }
    }

    // ---- inline CSS ------------------------------------------------------------------------------

    private void inlineStyle(S s) {
        for (String declaration : s.inlineStyle.split(";")) {
            String d = declaration.trim();
            if (d.isEmpty()) {
                continue;
            }
            int colon = d.indexOf(':');
            if (colon <= 0) {
                unsupported(s.n, "style", "\"" + d + "\" is not a CSS declaration");
                continue;
            }
            String prop = d.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String v = d.substring(colon + 1).trim();
            String jx;
            String value = v;
            switch (prop) {
                case "-fx-background-color": jx = "background"; break;
                case "-fx-text-fill": case "-fx-fill": jx = "textFill"; break;
                case "-fx-border-color": jx = "borderColor"; break;
                case "-fx-border-width": jx = "borderWidth"; value = firstNumber(v); break;
                case "-fx-border-radius": jx = "borderRadius"; value = firstNumber(v); break;
                case "-fx-background-radius": jx = "backgroundRadius"; value = firstNumber(v); break;
                case "-fx-opacity": jx = "opacity"; break;
                case "-fx-font-size": jx = "fontSize"; value = firstNumber(v); break;
                case "-fx-font-weight":
                    jx = "bold";
                    value = "bold".equals(v) || isHeavy(v) ? "true" : null;
                    if (value == null && !"normal".equals(v)) {
                        unsupported(s.n, "style: " + prop, "font weight \"" + v + "\" is not converted");
                        continue;
                    }
                    if (value == null) {
                        continue;
                    }
                    break;
                case "-fx-padding": jx = "padding"; value = v.replace("px", "").replace(",", " "); break;
                case "-fx-alignment": jx = "alignment"; value = v.toUpperCase(Locale.ROOT).replace('-', '_'); break;
                case "-fx-spacing": jx = "gap"; value = firstNumber(v); break;
                case "-fx-pref-width": jx = "prefWidth"; value = firstNumber(v); break;
                case "-fx-pref-height": jx = "prefHeight"; value = firstNumber(v); break;
                case "-fx-min-width": jx = "minWidth"; value = firstNumber(v); break;
                case "-fx-min-height": jx = "minHeight"; value = firstNumber(v); break;
                case "-fx-max-width": jx = "maxWidth"; value = firstNumber(v); break;
                case "-fx-max-height": jx = "maxHeight"; value = firstNumber(v); break;
                default:
                    unsupported(s.n, "style: " + prop, prop + " has no JXML attribute");
                    continue;
            }
            if (value == null || value.isEmpty()) {
                unsupported(s.n, "style: " + prop, "value \"" + v + "\" is not a single number or color");
                continue;
            }
            if (s.out.attrs.containsKey(jx)) {
                continue; // an attribute written on the element wins over its style
            }
            if (schemaAttr(s.out.tag, jx) == null) {
                unsupported(s.n, "style: " + prop, "<" + s.out.tag + "> has no attribute " + jx);
                continue;
            }
            put(s, "style: " + prop, jx, value);
        }
    }

    private static boolean isHeavy(String v) {
        try {
            return Integer.parseInt(v) >= 600;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** The first number of a CSS value such as {@code 14px}, {@code 4 8} or {@code 12pt} (pt becomes px); null for anything else. */
    private static String firstNumber(String v) {
        String first = v.trim().split("[\\s,]+")[0];
        double factor = 1;
        if (first.endsWith("px")) {
            first = first.substring(0, first.length() - 2);
        } else if (first.endsWith("pt")) {
            first = first.substring(0, first.length() - 2);
            factor = 96.0 / 72.0;
        }
        try {
            double d = Double.parseDouble(first) * factor;
            return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---- lists and tables ------------------------------------------------------------------------

    private String modelName(S s) {
        if (s.id != null) {
            return s.id;
        }
        approximate(s.n, "items", "no fx:id to name the model: items are bound to ${items}; the controller needs getItems()");
        return "items";
    }

    private void listTemplate(S s) {
        s.out.attrs.put("items", "${" + modelName(s) + "}");
        s.out.attrs.put("var", "item");
        JxmlParser.Node cell = new JxmlParser.Node("ListItem", s.n.line, file);
        cell.attrs.put("text", "${item}");
        s.out.children.add(cell);
        note(s.n, "items", "rows come from controller.get" + Character.toUpperCase(modelName(s).charAt(0)) + modelName(s).substring(1)
                + "() (a List), shown with toString(); a cellFactory is not converted");
    }

    private void tableTemplate(S s) {
        String model = modelName(s);
        s.out.attrs.put("items", "${" + model + "}");
        s.out.attrs.put("var", "row");
        String type = s.typeArgs != null ? s.typeArgs.split("[,\\s]+")[0] : options.itemType(s.id);
        if (type == null || type.isEmpty()) {
            unsupported(s.n, "fx:typeArguments", "the row type of this table is unknown; the bindings ${row.x} need type=\"fully.qualified.Row\": "
                    + "give the converter an item type for " + (s.id == null ? "this table" : "\"" + s.id + "\"")
                    + " (Options.itemType, --item-type id=Class, or fx:typeArguments in the FXML)");
        } else {
            s.out.attrs.put("type", type);
        }
        if (s.constrained) {
            s.out.attrs.put("constrained", "true");
        }
        List<String> titles = new ArrayList<String>();
        List<String> widths = new ArrayList<String>();
        JxmlParser.Node row = new JxmlParser.Node("TableRow", s.n.line, file);
        for (FxNode column : s.columns) {
            String title = "";
            String width = "80";
            String property = null;
            for (Map.Entry<String, String> a : column.attrs.entrySet()) {
                String key = a.getKey();
                if ("text".equals(key)) {
                    String t = value(column, key, a.getValue(), new JxmlSchema.Attr("text", "text", AttrType.STRING, false));
                    title = t == null ? "" : t.startsWith("\\") ? t.substring(1) : t;
                } else if ("prefWidth".equals(key)) {
                    width = a.getValue().trim();
                    if (!isNumber(width)) {
                        unsupported(column, key, "width \"" + width + "\" is not a plain number");
                        width = "80";
                    }
                } else if ("fx:id".equals(key)) {
                    note(column, key, "fx:id=\"" + a.getValue() + "\" of a table column: the native UI has no column objects");
                } else if (!key.startsWith("xmlns")) {
                    unsupported(column, key, "TableColumn attribute not converted");
                }
            }
            for (FxNode c : column.children) {
                if ("cellValueFactory".equals(c.name) && c.children.size() == 1
                        && c.children.get(0).name.endsWith("PropertyValueFactory")
                        && c.children.get(0).attrs.containsKey("property")) {
                    property = c.children.get(0).attrs.get("property");
                } else if (c.isProperty() && "text".equals(c.name) && propText(c) != null) {
                    title = propText(c);
                } else {
                    unsupported(c, null, "TableColumn child <" + c.name + "> not converted (only a PropertyValueFactory does)");
                }
            }
            if (title.contains(",")) {
                approximate(column, "text", "column title \"" + title + "\" has a comma, which separates titles in JXML; replaced by a semicolon");
                title = title.replace(',', ';');
            }
            titles.add(title);
            widths.add(width);
            JxmlParser.Node cell = new JxmlParser.Node("TableCell", column.line, file);
            if (property != null && JxmlTemplate.isIdentifier(property)) {
                cell.attrs.put("text", "${row." + property + "}");
            } else {
                unsupported(column, "cellValueFactory", property == null
                        ? "this column has no PropertyValueFactory, so its cells are empty" : "property \"" + property + "\" is not an identifier");
            }
            row.children.add(cell);
        }
        if (!titles.isEmpty()) {
            s.out.attrs.put("columns", String.join(", ", titles));
            s.out.attrs.put("columnWidths", String.join(", ", widths));
        }
        s.out.children.add(row);
        if (type != null && !type.isEmpty()) {
            note(s.n, "items", "rows come from controller.get" + Character.toUpperCase(model.charAt(0)) + model.substring(1)
                    + "() (a List of " + type + "); cells call the getters of the columns' PropertyValueFactory names");
        }
    }
}
