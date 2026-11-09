package com.jxparallel.forge;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes an XML Schema (XSD) for JXML from {@link JxmlSchema}, so an editor can complete and check the tags and
 * attributes of a {@code .jxml} file. The schema table is the only source: a tag or attribute added there appears
 * here on the next run, and nothing is listed by hand except what the language adds around the table
 * ({@code <Include>}, {@code <Style>}, and the {@code controller} and {@code style} attributes).
 *
 * <p>The schema has no namespace, because JXML files have none; a file points to it with
 * {@code xsi:noNamespaceSchemaLocation="jxml.xsd"} on its root element, which the JXML reader ignores.
 *
 * <p>Attribute values are typed loosely. Every attribute that {@code ${...}} may bind accepts a binding as well as
 * a literal of its type; a handler is {@code #name} or {@code #name(args)}; a colour is {@code #RGB}, {@code #RRGGBB},
 * {@code #AARRGGBB} or a theme token. Text is any string. The schema checks the shape, not what the controller has:
 * that is checked when the file is read or compiled.
 *
 * <p>Command line: {@code java -cp jxparallel-forge.jar;jxparallel-core.jar com.jxparallel.forge.JxmlXsd [--out target/jxml.xsd]}
 */
public final class JxmlXsd {
    /** The default file name of the schema. */
    public static final String FILE_NAME = "jxml.xsd";

    private static final String XS = "http://www.w3.org/2001/XMLSchema";
    /** A binding: {@code ${a.b}} reads, {@code @{a.b}} reads and writes. */
    private static final String BINDING = "[$@]\\{.*\\}";
    private static final String NUMBER = "[-+]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)([eE][-+]?[0-9]+)?";
    private static final String SEPARATOR = "[\\s,]+";
    private static final String HEX = "[0-9A-Fa-f]";

    private final StringBuilder out = new StringBuilder(64 * 1024);
    private int depth;

    private JxmlXsd() {
    }

    /** The schema as XML text. */
    public static String generate() {
        JxmlXsd xsd = new JxmlXsd();
        xsd.document();
        return xsd.out.toString();
    }

    /** Writes the schema to {@code file} (UTF-8), creating its folder. */
    public static void write(File file) throws IOException {
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Cannot create " + parent);
        }
        Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
        try {
            writer.write(generate());
        } finally {
            writer.close();
        }
    }

    public static void main(String[] args) throws IOException {
        String target = "target/" + FILE_NAME;
        for (int i = 0; i < args.length; i++) {
            if ("--out".equals(args[i]) && i + 1 < args.length) {
                target = args[++i];
            } else {
                System.err.println("usage: JxmlXsd [--out " + target + "]");
                System.exit(2);
            }
        }
        write(new File(target));
        System.out.println("JXML schema: " + JxmlSchema.tags().size() + " tags written to " + target);
    }

    // ---- document ----------------------------------------------------------------------------

    private void document() {
        line("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        line("<!-- Generated from JxmlSchema by com.jxparallel.forge.JxmlXsd. Do not edit; generate it again. -->");
        open("xs:schema", "xmlns:xs=\"" + XS + "\" elementFormDefault=\"unqualified\" attributeFormDefault=\"unqualified\"");
        documentation("JXML: the tags and attributes of JXForge screens. A file points to this schema with "
                + "xsi:noNamespaceSchemaLocation on its root element. Values may be literals, ${...} bindings, "
                + "@{...} two-way bindings or #handler references, as each attribute allows.");
        simpleTypes();
        groups();
        layoutGroup();
        for (JxmlSchema.Tag tag : JxmlSchema.tags().values()) {
            element(tag);
        }
        includeElement();
        styleElement();
        close("xs:schema");
    }

    private void simpleTypes() {
        pattern("binding", "A binding to the controller or a loop variable: ${name}, ${a.b}, ${a.b()}, or @{name} for two-way.", BINDING);
        pattern("handler", "A controller method: #save, or #open(item) with loop variables passed first.",
                "#\\s*[\\p{L}_$][\\p{L}\\p{N}_$]*\\s*(\\([^)]*\\))?\\s*");
        literalAndValue("boolean", "true or false", "\\s*(true|false)\\s*");
        literalAndValue("int", "A whole number.", "\\s*[-+]?[0-9]+\\s*");
        literalAndValue("double", "A number.", "\\s*" + NUMBER + "\\s*");
        literalAndValue("insets", "One number for all sides, two (vertical horizontal) or four (top right bottom left).",
                "\\s*" + NUMBER + "(" + SEPARATOR + NUMBER + "(" + SEPARATOR + NUMBER + SEPARATOR + NUMBER + ")?)?\\s*");
        literalAndValue("doubles", "Numbers separated by commas.", "\\s*(" + NUMBER + "(\\s*,\\s*" + NUMBER + ")*)?\\s*");
        StringBuilder tokens = new StringBuilder();
        for (String token : JxmlThemes.TOKENS) {
            tokens.append('|').append(token);
        }
        literalAndValue("color", "#RGB, #RRGGBB, #AARRGGBB or a theme token (" + String.join(", ", JxmlThemes.TOKENS) + ").",
                "\\s*(#(" + HEX + "{3}|" + HEX + "{6}|" + HEX + "{8})" + tokens + ")\\s*");
    }

    private void pattern(String name, String doc, String regex) {
        open("xs:simpleType", "name=\"" + name + "\"");
        documentation(doc);
        open("xs:restriction", "base=\"xs:string\"");
        line("<xs:pattern value=\"" + escape(regex) + "\"/>");
        close("xs:restriction");
        close("xs:simpleType");
    }

    /** {@code <name>Literal}, and {@code <name>Value}: the literal or a binding. */
    private void literalAndValue(String name, String doc, String regex) {
        pattern(name + "Literal", doc, regex);
        open("xs:simpleType", "name=\"" + name + "Value\"");
        documentation(doc + " May also be a binding.");
        line("<xs:union memberTypes=\"" + name + "Literal binding\"/>");
        close("xs:simpleType");
    }

    // ---- content models ----------------------------------------------------------------------

    /** The tags a parent in {@link JxmlSchema.Tag#parents} restricts; an alias is restricted like its target. */
    private static List<String> parentsOf(JxmlSchema.Tag tag) {
        if (tag.parents != null) {
            return tag.parents;
        }
        JxmlSchema.Tag target = tag.alias == null ? null : JxmlSchema.tag(tag.alias);
        return target == null ? null : target.parents;
    }

    /** True when {@code tag} may stand in a parent whose children are only {@code only}. */
    private static boolean fits(JxmlSchema.Tag tag, List<String> only) {
        return only.contains(tag.name) || tag.alias != null && only.contains(tag.alias);
    }

    private void groups() {
        // elements that stand anywhere a child is allowed
        open("xs:group", "name=\"nodes\"");
        open("xs:choice");
        for (JxmlSchema.Tag tag : JxmlSchema.tags().values()) {
            if (!tag.control && tag.children != JxmlSchema.Children.TAB && parentsOf(tag) == null) {
                ref(tag.name);
            }
        }
        ref("Include");
        close("xs:choice");
        close("xs:group");
        // elements that only stand inside certain parents (menu items, tree items): the parent lists them itself
        open("xs:group", "name=\"restrictedNodes\"");
        open("xs:choice");
        for (JxmlSchema.Tag tag : JxmlSchema.tags().values()) {
            if (parentsOf(tag) != null) {
                ref(tag.name);
            }
        }
        close("xs:choice");
        close("xs:group");
    }

    private void ref(String element) {
        line("<xs:element ref=\"" + element + "\"/>");
    }

    private void content(JxmlSchema.Tag tag) {
        if (tag.control) {
            // a loop or a condition holds whatever its parent would take: the schema cannot know the parent
            open("xs:choice", "minOccurs=\"0\" maxOccurs=\"unbounded\"");
            line("<xs:group ref=\"nodes\"/>");
            line("<xs:group ref=\"restrictedNodes\"/>");
            for (JxmlSchema.Tag control : JxmlSchema.tags().values()) {
                if (control.control) {
                    ref(control.name);
                }
            }
            close("xs:choice");
            return;
        }
        switch (tag.children) {
            case NONE:
                return;
            case ONE:
                open("xs:sequence");
                line("<xs:element ref=\"Style\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>");
                open("xs:choice", "minOccurs=\"0\"");
                line("<xs:group ref=\"nodes\"/>");
                close("xs:choice");
                close("xs:sequence");
                return;
            case TAB:
                open("xs:choice");
                line("<xs:group ref=\"nodes\"/>");
                close("xs:choice");
                return;
            case TABS:
                open("xs:sequence");
                line("<xs:element ref=\"Style\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>");
                line("<xs:element ref=\"Tab\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>");
                close("xs:sequence");
                return;
            default:
                open("xs:choice", "minOccurs=\"0\" maxOccurs=\"unbounded\"");
                if (tag.only == null) {
                    ref("Style");
                    line("<xs:group ref=\"nodes\"/>");
                } else {
                    for (JxmlSchema.Tag candidate : JxmlSchema.tags().values()) {
                        if (!candidate.control && fits(candidate, tag.only)) {
                            ref(candidate.name);
                        }
                    }
                    ref("Include");
                }
                for (JxmlSchema.Tag control : JxmlSchema.tags().values()) {
                    if (control.control) {
                        ref(control.name);
                    }
                }
                close("xs:choice");
        }
    }

    // ---- elements ----------------------------------------------------------------------------

    /** Layout attributes a tag may declare itself (a tag's own attribute wins, so XSD cannot take both), and the root's theme. */
    private static List<String> individualLayout() {
        List<String> names = new ArrayList<String>();
        names.add("theme"); // root only: not part of the group that Include also takes
        for (JxmlSchema.Tag tag : JxmlSchema.tags().values()) {
            if (!tag.control) {
                for (String name : tag.attrs.keySet()) {
                    if (JxmlSchema.LAYOUT.containsKey(name) && !names.contains(name)) {
                        names.add(name);
                    }
                }
            }
        }
        return names;
    }

    private void layoutGroup() {
        open("xs:attributeGroup", "name=\"layout\"");
        documentation("Placement and sizing, valid on every element: read by the layout of the parent.");
        List<String> individual = individualLayout();
        for (JxmlSchema.Attr attr : JxmlSchema.LAYOUT.values()) {
            if (!individual.contains(attr.name)) {
                attribute(null, attr);
            }
        }
        close("xs:attributeGroup");
    }

    private void element(JxmlSchema.Tag tag) {
        open("xs:element", "name=\"" + tag.name + "\"");
        documentation(describe(tag));
        open("xs:complexType");
        content(tag);
        Map<String, JxmlSchema.Attr> attrs = new LinkedHashMap<String, JxmlSchema.Attr>(tag.attrs);
        if (!tag.control) {
            // layout attributes some tag writes again (onAction) are declared by each tag; the others come from one group
            for (String name : individualLayout()) {
                if (!attrs.containsKey(name)) {
                    attrs.put(name, JxmlSchema.LAYOUT.get(name));
                }
            }
        }
        for (JxmlSchema.Attr attr : attrs.values()) {
            attribute(tag, attr);
        }
        if (!tag.control) {
            line("<xs:attributeGroup ref=\"layout\"/>");
            open("xs:attribute", "name=\"controller\" type=\"xs:string\"");
            documentation("The class of the controller the bindings and handlers resolve against. Only on the root element.");
            close("xs:attribute");
            open("xs:attribute", "name=\"style\" type=\"xs:string\"");
            documentation("The names of one or more <Style> elements of the file, separated by spaces. "
                    + "An attribute written on the element wins over the style.");
            close("xs:attribute");
        }
        close("xs:complexType");
        close("xs:element");
    }

    private void includeElement() {
        open("xs:element", "name=\"Include\"");
        documentation("Replaced by the root element of the file named by src, read the same way. "
                + "The other attributes (slot, hgrow, row, column...) are put on that root element.");
        open("xs:complexType");
        open("xs:attribute", "name=\"src\" type=\"xs:string\" use=\"required\"");
        documentation("The file to include, relative to this one: parts/header.jxml. It cannot be bound.");
        close("xs:attribute");
        for (String name : individualLayout()) {
            if (!"theme".equals(name)) { // the theme is the including file's
                attribute(null, JxmlSchema.LAYOUT.get(name));
            }
        }
        line("<xs:attributeGroup ref=\"layout\"/>");
        close("xs:complexType");
        close("xs:element");
    }

    private void styleElement() {
        open("xs:element", "name=\"Style\"");
        documentation("A set of attributes a file defines once and elements use with style=\"name\". "
                + "Allowed only directly inside the root element. Its values are literals; a handler, a binding "
                + "or a structural attribute (items, var, test...) cannot be style.");
        open("xs:complexType");
        open("xs:attribute", "name=\"name\" type=\"xs:string\" use=\"required\"");
        documentation("The name elements use in style=\"...\".");
        close("xs:attribute");
        // the attribute names JxmlPrepare accepts in a style: any literal attribute of any tag but the structural ones
        List<String> skip = java.util.Arrays.asList("name", "theme", "key", "content", "items", "var", "type", "from", "limit",
                "memo", "test", "pixels");
        Map<String, Boolean> names = new LinkedHashMap<String, Boolean>();
        List<JxmlSchema.Attr> all = new ArrayList<JxmlSchema.Attr>(JxmlSchema.LAYOUT.values());
        for (JxmlSchema.Tag tag : JxmlSchema.tags().values()) {
            if (!tag.control) {
                all.addAll(tag.attrs.values());
            }
        }
        for (JxmlSchema.Attr attr : all) {
            if (attr.type != AttrType.HANDLER && attr.type != AttrType.CHANGE && !skip.contains(attr.name)) {
                names.put(attr.name, Boolean.TRUE);
            }
        }
        for (String name : names.keySet()) {
            line("<xs:attribute name=\"" + name + "\" type=\"xs:string\"/>");
        }
        close("xs:complexType");
        close("xs:element");
    }

    private void attribute(JxmlSchema.Tag tag, JxmlSchema.Attr attr) {
        boolean required = tag != null && (tag.required.contains(attr.name)
                // checked by the reader, not declared in the table: <For items var> and <If test>
                || "For".equals(tag.name) && ("items".equals(attr.name) || "var".equals(attr.name))
                || "If".equals(tag.name) && "test".equals(attr.name));
        String use = required ? " use=\"required\"" : "";
        if (attr.choices != null) {
            open("xs:attribute", "name=\"" + attr.name + "\"" + use);
            documentation(describe(tag, attr));
            open("xs:simpleType");
            open("xs:restriction", "base=\"xs:string\"");
            for (String choice : attr.choices) {
                line("<xs:enumeration value=\"" + escape(choice) + "\"/>");
            }
            close("xs:restriction");
            close("xs:simpleType");
            close("xs:attribute");
            return;
        }
        open("xs:attribute", "name=\"" + attr.name + "\" type=\"" + typeOf(tag, attr) + "\"" + use);
        documentation(describe(tag, attr));
        close("xs:attribute");
    }

    /** The simple type of an attribute: a literal of its type, plus a binding where {@code ${...}} may bind it. */
    private static String typeOf(JxmlSchema.Tag tag, JxmlSchema.Attr attr) {
        boolean twoWay = tag != null && attr.name.equals(tag.changeAttr);
        String suffix = attr.bindable || twoWay ? "Value" : "Literal";
        switch (attr.type) {
            case BOOLEAN:
                return "boolean" + suffix;
            case INT:
                return "int" + suffix;
            case DOUBLE:
                return "double" + suffix;
            case INSETS:
                return "insets" + suffix;
            case DOUBLES:
                return "doubles" + suffix;
            case COLOR:
                return "color" + suffix;
            case HANDLER:
            case CHANGE:
                return "handler";
            case ELEMENT:
            case OBJECT:
            case COUNT:
            case PIXELS:
                return "binding"; // the reader accepts only a binding for these
            default:
                return "xs:string"; // STRING, STRINGS: any text
        }
    }

    // ---- documentation -----------------------------------------------------------------------

    private static String describe(JxmlSchema.Tag tag) {
        StringBuilder text = new StringBuilder("<").append(tag.name).append('>');
        if (tag.control) {
            text.append(" is part of the language, not an element.");
        } else if (tag.alias != null) {
            text.append(" is <").append(tag.alias).append(">");
            if (!tag.aliasAttrs.isEmpty()) {
                text.append(" with ").append(tag.aliasAttrs.toString().replace("{", "").replace("}", "")).append(" unless written");
            }
            text.append('.');
        } else if (tag.composite) {
            text.append(" is written out as other tags before the screen is built.");
        } else {
            text.append(" becomes the native element \"").append(tag.nativeType == null ? "tab" : tag.nativeType).append("\".");
        }
        switch (tag.children) {
            case NONE:
                text.append(" Takes no children.");
                break;
            case ONE:
                text.append(" Takes one child.");
                break;
            case TABS:
                text.append(" Takes <Tab> children.");
                break;
            case TAB:
                text.append(" Takes one child, its content. Only inside <Tabs>.");
                break;
            case TEMPLATE:
                text.append(" Takes the template of one row; the rows come from items.");
                break;
            default:
                text.append(" Takes children.");
        }
        if (tag.only != null) {
            text.append(" Children: ").append(String.join(", ", tag.only)).append('.');
        }
        if (tag.parents != null) {
            text.append(" Only inside: ").append(String.join(", ", tag.parents)).append('.');
        }
        if (tag.changeAttr != null) {
            text.append(" ").append(tag.changeAttr).append(" can be bound both ways with @{...}; onChange gets the new value.");
        }
        return text.toString();
    }

    private static String describe(JxmlSchema.Tag tag, JxmlSchema.Attr attr) {
        StringBuilder text = new StringBuilder();
        switch (attr.type) {
            case HANDLER:
                text.append("Handler: #method of the controller, called with no arguments.");
                break;
            case CHANGE:
                text.append("Handler: #method of the controller, called with the new value (")
                        .append(attr.valueType == null ? "?" : attr.valueType.name().toLowerCase()).append(").");
                break;
            case ELEMENT:
            case OBJECT:
            case COUNT:
            case PIXELS:
                text.append("Type ").append(attr.type.name().toLowerCase()).append("; a ${...} binding only.");
                break;
            default:
                text.append("Type ").append(attr.type.name().toLowerCase()).append('.');
                if (attr.bindable) {
                    text.append(" May be bound with ${...}.");
                } else if (attr.choices == null) {
                    text.append(" Written as a literal; it cannot be bound.");
                }
        }
        if (attr.choices != null) {
            text.append(" One of: ").append(String.join(", ", attr.choices)).append('.');
        }
        if (tag != null && attr.name.equals(tag.changeAttr)) {
            text.append(" May be bound both ways with @{...}.");
        }
        if (tag != null && !tag.control && !attr.prop.equals(attr.name) && attr.type != AttrType.HANDLER
                && attr.type != AttrType.CHANGE) {
            text.append(" Sets the property \"").append(attr.prop).append("\".");
        }
        return text.toString();
    }

    // ---- writing -----------------------------------------------------------------------------

    private void documentation(String text) {
        open("xs:annotation");
        line("<xs:documentation>" + escape(text) + "</xs:documentation>");
        close("xs:annotation");
    }

    private void open(String name) {
        open(name, null);
    }

    private void open(String name, String attributes) {
        line("<" + name + (attributes == null ? "" : " " + attributes) + ">");
        depth++;
    }

    private void close(String name) {
        depth--;
        line("</" + name + ">");
    }

    private void line(String text) {
        for (int i = 0; i < depth; i++) {
            out.append("  ");
        }
        out.append(text).append('\n');
    }

    private static String escape(String text) {
        StringBuilder b = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&':
                    b.append("&amp;");
                    break;
                case '<':
                    b.append("&lt;");
                    break;
                case '>':
                    b.append("&gt;");
                    break;
                case '"':
                    b.append("&quot;");
                    break;
                default:
                    b.append(c);
            }
        }
        return b.toString();
    }
}
