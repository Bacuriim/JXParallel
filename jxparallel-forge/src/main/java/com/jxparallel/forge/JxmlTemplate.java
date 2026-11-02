package com.jxparallel.forge;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

/**
 * A JXML file read, checked against {@link JxmlSchema} and resolved: the interpreter. It builds
 * the same tree the generated code builds, finding getters and handlers by reflection instead of
 * calling them directly. Reusable and thread safe; reflective lookups are cached per controller class.
 *
 * <p>Binding rules, the same in the compiler: {@code ${a.b}} calls {@code getA().getB()} on the
 * controller, and {@code ${a.b()}} calls {@code getA().b()} (record components);
 * {@code #save} calls the controller's public no-argument method {@code save()};
 * a value starting with a backslash is a literal without it ({@code \#1}).
 *
 * <p>Inside {@code <For items="${orders}" var="o">} a binding may start with the loop variable
 * ({@code ${o.total}}) and a handler may take loop variables ({@code #open(o)} calls
 * {@code controller.open(o)}). {@code onChange="#setName"} calls the controller with the new value of
 * the input, and {@code text="@{customer.name}"} reads {@code getCustomer().getName()} and calls
 * {@code getCustomer().setName(value)} on change.
 */
public final class JxmlTemplate implements JxmlView<Object> {
    /** How an attribute gets its value. */
    enum Kind { LITERAL, BINDING, HANDLER, CHANGE, SETTER }

    /** One prop of one element. */
    static final class Prop {
        final JxmlSchema.Attr attr;
        final Kind kind;
        final Object literal;
        /** The loop variable a binding starts with, or null when it starts at the controller. */
        final String root;
        /** Getters called one after the other from the root; for a SETTER, the ones that reach the bean. */
        final String[] path;
        /** The controller method of a HANDLER or CHANGE, or the setter name of a SETTER. */
        final String method;
        /** Loop variables passed to a HANDLER or CHANGE method, before the new value. */
        final String[] args;
        /** {@code ${!flag}} in an {@code <If test>}. */
        final boolean negate;
        final String source;
        final int line;

        Prop(JxmlSchema.Attr attr, Kind kind, Object literal, String root, String[] path, String method, String[] args,
             boolean negate, String source, int line) {
            this.source = source;
            this.attr = attr;
            this.kind = kind;
            this.literal = literal;
            this.root = root;
            this.path = path;
            this.method = method;
            this.args = args;
            this.negate = negate;
            this.line = line;
        }
    }

    /** One element, a tab pane with its tabs, or a {@code <For>} / {@code <If>} that stands for several elements. */
    static final class Element {
        final JxmlSchema.Tag tag;
        final String source;
        final int line;
        final List<Prop> props;
        final List<Element> children;
        /** Tabs only: titles and close flags of the tabs, in order; children are the contents. */
        final String[] titles;
        final boolean[] closable;
        /** The tag's default props that no attribute of this element sets. */
        final Map<String, Object> defaults;
        /** For only: the loop variable, and the class of the items (null if not given). */
        final String var;
        final String type;
        /** If only: what is included when the test is false (the {@code <Else>} that follows). */
        final List<Element> otherwise;
        /** For only, with {@code memo="true"}: what the loop built last time; null for any other element. */
        final JxmlMemo memo;

        Element(JxmlSchema.Tag tag, String source, int line, List<Prop> props, List<Element> children, String[] titles,
                boolean[] closable, String var, String type, List<Element> otherwise) {
            this.tag = tag;
            this.source = source;
            this.line = line;
            this.props = props;
            this.children = children;
            this.titles = titles;
            this.closable = closable;
            this.var = var;
            this.type = type;
            this.otherwise = otherwise;
            Prop memoProp = "For".equals(tag.name) ? prop("memo") : null;
            this.memo = memoProp != null && Boolean.TRUE.equals(memoProp.literal) ? new JxmlMemo() : null;
            Map<String, Object> d = new java.util.LinkedHashMap<String, Object>(tag.defaults);
            for (Prop p : props) {
                d.remove(p.attr.prop);
            }
            this.defaults = Collections.unmodifiableMap(d);
        }

        Element withOtherwise(List<Element> elements) {
            return new Element(tag, source, line, props, children, titles, closable, var, type, Collections.unmodifiableList(elements));        }

        /** The prop set by the attribute of this name, or null. */
        Prop prop(String name) {
            for (Prop p : props) {
                if (p.attr.name.equals(name)) {
                    return p;
                }
            }
            return null;
        }

        /** No bindings or handlers anywhere below: the same tree for every controller. */
        boolean constant() {
            if (tag.control) {
                return false;
            }
            for (Prop p : props) {
                if (p.kind != Kind.LITERAL) {
                    return false;
                }
            }
            for (Element child : children) {
                if (!child.constant()) {
                    return false;
                }
            }
            return true;
        }
    }

    /** A loop variable in scope while a file is resolved. */
    private static final class Var {
        final String name;
        final boolean typed;
        final Var parent;

        Var(String name, boolean typed, Var parent) {
            this.name = name;
            this.typed = typed;
            this.parent = parent;
        }

        static Var find(Var scope, String name) {
            for (Var v = scope; v != null; v = v.parent) {
                if (v.name.equals(name)) {
                    return v;
                }
            }
            return null;
        }
    }

    /** The loop variables and their current items while a tree is built; the nearest one wins. */
    private static final class Scope {
        final String name;
        final Object value;
        final Scope parent;

        Scope(String name, Object value, Scope parent) {
            this.name = name;
            this.value = value;
            this.parent = parent;
        }

        static Object get(Scope scope, String name) {
            for (Scope s = scope; s != null; s = s.parent) {
                if (s.name.equals(name)) {
                    return s.value;
                }
            }
            throw new IllegalStateException(name);
        }
    }

    final String source;
    final String controller;
    final Element root;
    private final Map<Class<?>, Map<String, Method>> methods = new ConcurrentHashMap<Class<?>, Map<String, Method>>();

    private JxmlTemplate(String source, String controller, Element root) {
        this.source = source;
        this.controller = controller;
        this.root = root;
    }

    /** Reads and checks a JXML file; {@code source} names it in error messages. */
    public static JxmlTemplate read(InputStream input, String source) {
        return read(input, source, null);
    }

    /**
     * Reads a file whose {@code <Include src>} elements are found by {@code loader} (null: the file has none). The
     * {@code source} is the name of the file for the loader, which resolves each {@code src} against it.
     */
    public static JxmlTemplate read(InputStream input, String source, JxmlLoader loader) {
        JxmlParser.Node node = JxmlParser.parse(input, source);
        String controller = node.attrs.remove("controller");
        String theme = JxmlPrepare.run(node, controller, loader);
        return new JxmlTemplate(source, controller, resolve(node, source, null, null, null, theme));
    }

    /** Reads a file from the file system; its includes are found next to it. */
    public static JxmlTemplate read(java.io.File file) {
        String name = file.getPath().replace('\\', '/');
        try {
            InputStream in = new java.io.FileInputStream(file);
            try {
                return read(in, name, JxmlLoader.files());
            } finally {
                in.close();
            }
        } catch (java.io.IOException e) {
            throw new JxmlException(name, 0, "unreadable: " + e.getMessage(), e);
        }
    }

    /** Reads a resource (such as {@code screens/main.jxml}); its includes are resources too, found next to it. */
    public static JxmlTemplate readResource(ClassLoader loader, String path) {
        JxmlLoader resources = JxmlLoader.classpath(loader);
        try {
            InputStream in = resources.open(path);
            try {
                return read(in, path, resources);
            } finally {
                in.close();
            }
        } catch (java.io.IOException e) {
            throw new JxmlException(path, 0, "unreadable: " + e.getMessage(), e);
        }
    }

    /** The controller class named by the root's {@code controller} attribute, or null. */
    public String getController() {
        return controller;
    }

    public String getSource() {
        return source;
    }

    @Override
    public JXElement build(Object controller) {
        return build(root, controller, null, null);
    }

    // ---- resolution ------------------------------------------------------------------------------

    /**
     * {@code parent} is the tag the node is a child of (a loop or condition included); {@code owner} the nearest tag
     * above it that is an element, which is what a menu item or a tree item must stand inside.
     */
    private static Element resolve(JxmlParser.Node written, String source, JxmlSchema.Tag parent, JxmlSchema.Tag owner, Var vars,
                                   String theme) {
        source = written.source; // an included file's elements report their own file
        JxmlParser.Node node = JxmlComposites.expand(written, source); // composites become primitive tags here, once
        JxmlSchema.Tag tag = JxmlSchema.tag(node.tag);
        if (tag == null) {
            throw new JxmlException(source, node.line, "unknown tag <" + node.tag + ">; known: " + JxmlSchema.tags().keySet());
        }
        if ("Else".equals(tag.name)) {
            throw new JxmlException(source, node.line, "<Else> must directly follow an <If>");
        }
        if (tag.virtual) {
            node = virtualRows(node, tag, source);
        }
        if (tag.control && (parent == null
                || parent.children != JxmlSchema.Children.MANY && parent.children != JxmlSchema.Children.TEMPLATE)) {
            throw new JxmlException(source, node.line, "<" + tag.name + "> is only allowed where a tag takes many children");
        }
        if (tag.children == JxmlSchema.Children.TAB && (parent == null || parent.children != JxmlSchema.Children.TABS)) {
            throw new JxmlException(source, node.line, "<Tab> is only allowed inside <Tabs>");
        }
        if (tag.parents != null && (owner == null || !tag.parents.contains(owner.name))) {
            throw new JxmlException(source, node.line, "<" + tag.name + "> is only allowed inside " + names(tag.parents));
        }
        if (node.attrs.containsKey("controller")) {
            throw new JxmlException(source, node.line, "controller is only allowed on the root element");
        }
        if (node.attrs.containsKey("theme") && parent != null) {
            throw new JxmlException(source, node.line, "theme is only allowed on the root element");
        }
        String var = null;
        String type = null;
        Var inner = vars;
        if ("For".equals(tag.name)) {
            var = node.attrs.get("var");
            if (!node.attrs.containsKey("items")) {
                throw new JxmlException(source, node.line, "<For> needs items=\"${...}\"");
            }
            if (var == null || !isIdentifier(var)) {
                throw new JxmlException(source, node.line, "<For> needs var=\"name\", the name of the loop variable");
            }
            type = node.attrs.get("type");
            if (type != null) {
                for (String part : type.split("\\.", -1)) {
                    if (!isIdentifier(part)) {
                        throw new JxmlException(source, node.line, "type=\"" + type + "\" must be a class name, such as com.acme.Order");
                    }
                }
            }
            inner = new Var(var, type != null, vars);
        } else if ("If".equals(tag.name) && !node.attrs.containsKey("test")) {
            throw new JxmlException(source, node.line, "<If> needs test=\"${...}\"");
        }
        List<Prop> props = new ArrayList<Prop>();
        for (Map.Entry<String, String> entry : node.attrs.entrySet()) {
            JxmlSchema.Attr attr = tag.attr(entry.getKey());
            if (attr == null) {
                throw new JxmlException(source, node.line, "<" + tag.name + "> has no attribute " + entry.getKey());
            }
            if ("For".equals(tag.name) && ("var".equals(attr.name) || "type".equals(attr.name))) {
                continue;
            }
            Var scope = "For".equals(tag.name) && "key".equals(attr.name) ? inner : vars;
            addProps(props, attr, entry.getValue(), source, node.line, tag, scope, theme);
        }
        for (String name : tag.required) {
            if (!node.attrs.containsKey(name)) {
                throw new JxmlException(source, node.line, "<" + tag.name + "> needs " + name + "=\"...\"");
            }
        }
        int changes = 0;
        for (Prop p : props) {
            // onClose and the like also take a value, but they are not the change of the edited attribute
            if ((p.kind == Kind.CHANGE && "onChange".equals(p.attr.name)) || p.kind == Kind.SETTER) {
                changes++;
            }
        }
        if (changes > 1) {
            throw new JxmlException(source, node.line, "<" + tag.name + "> has both onChange and a @{...} binding; use one");
        }
        for (Prop p : props) {
            if ((tag.virtual || tag.tree) && p.kind == Kind.LITERAL && p.literal instanceof Double && !(((Double) p.literal) > 0)
                    && ("cellHeight".equals(p.attr.name) || "rowHeight".equals(p.attr.name))) {
                throw new JxmlException(source, p.line, "<" + tag.name + "> " + p.attr.name + " must be greater than 0: "
                        + "the rows have a fixed height, which is how the list knows where to scroll");
            }
        }
        if (tag.changeAttr != null && "group".equals(tag.changeAttr)) {
            checkGroup(node, tag, changes, source);
        }
        if (isSlot(tag) && (props.size() != 1 || !"content".equals(props.get(0).attr.name))) {
            throw new JxmlException(source, node.line, "<Slot> takes exactly one attribute: content=\"${...}\"");
        }
        List<Element> children = new ArrayList<Element>();
        String[] titles = null;
        boolean[] closable = null;
        JxmlSchema.Tag below = tag.control ? owner : tag; // a loop or condition does not change what its children stand inside
        switch (tag.children) {
            case NONE:
                if (!node.children.isEmpty()) {
                    throw new JxmlException(source, node.line, "<" + tag.name + "> takes no children");
                }
                break;
            case ONE:
            case TAB:
                if (node.children.size() > 1) {
                    throw new JxmlException(source, node.line, "<" + tag.name + "> takes one child");
                }
                for (JxmlParser.Node child : node.children) {
                    children.add(resolve(child, source, tag, below, vars, theme));
                }
                break;
            case MANY:
            case TEMPLATE:
                children = resolveAll(node.children, source, tag, below, inner, theme);
                break;
            case TABS:
                titles = new String[node.children.size()];
                closable = new boolean[node.children.size()];
                for (int i = 0; i < node.children.size(); i++) {
                    JxmlParser.Node tabNode = node.children.get(i);
                    if (!"Tab".equals(tabNode.tag)) {
                        throw new JxmlException(source, tabNode.line, "<Tabs> takes only <Tab> children");
                    }
                    Element tab = resolve(tabNode, source, tag, below, vars, theme);
                    titles[i] = "";
                    closable[i] = true;
                    for (Prop p : tab.props) {
                        if ("title".equals(p.attr.name)) {
                            titles[i] = (String) p.literal;
                        } else if ("closable".equals(p.attr.name)) {
                            closable[i] = (Boolean) p.literal;
                        }
                    }
                    if (tab.children.isEmpty()) {
                        throw new JxmlException(source, tabNode.line, "<Tab> needs one child, its content");
                    }
                    if (isSlot(tab.children.get(0).tag)) {
                        throw new JxmlException(source, tabNode.line, "a tab's content cannot be a <Slot>; wrap it in a <Column>");
                    }
                    children.add(tab.children.get(0));
                }
                break;
            default:
                throw new IllegalStateException();
        }
        if (tag.only != null) {
            checkChildren(tag, children, source);
        }
        if ("Tree".equals(tag.name)) {
            checkNoValues(children);
        }
        if (var != null && node.attrs.containsKey("key")) {
            if (children.size() != 1 || children.get(0).tag.control || isSlot(children.get(0).tag)) {
                throw new JxmlException(source, node.line,
                        "<For key> needs exactly one element in the loop, which gets the key (not a <Slot>, <For> or <If>)");
            }
            if (children.get(0).prop("key") != null) {
                throw new JxmlException(source, children.get(0).line, "the element already has a key; remove it or the key of the <For>");
            }
        }
        Element element = new Element(tag, source, node.line, Collections.unmodifiableList(props),
                Collections.unmodifiableList(children), titles, closable, var, type, Collections.<Element>emptyList());
        if (element.memo != null) {
            checkMemoBody(element.children, Collections.singleton(var));
        }
        return element;
    }

    /**
     * The body of a {@code <For memo="true">} is built once for as long as the list stays the same, so it may read
     * nothing but its own loop variables (and those of the loops inside it): a value of the controller or of a loop
     * around the {@code memo} loop would go stale. Handlers are fine, the controller they call is part of what the
     * loop remembers.
     */
    private static void checkMemoBody(List<Element> elements, java.util.Set<String> roots) {
        for (Element e : elements) {
            java.util.Set<String> inner = roots;
            if (e.var != null) {
                inner = new java.util.HashSet<String>(roots);
                inner.add(e.var);
            }
            for (Prop p : e.props) {
                if ((p.kind == Kind.BINDING || p.kind == Kind.SETTER) && (p.root == null || !inner.contains(p.root))) {
                    throw new JxmlException(p.source, p.line, "<For memo> builds its elements once for as long as the list is the same, "
                            + "so \"" + p.attr.name + "\" can read only the loop variable " + roots + ", not "
                            + (p.root == null ? "the controller" : "\"" + p.root + "\", a variable of an outer loop"));
                }
                if ((p.kind == Kind.HANDLER || p.kind == Kind.CHANGE) && p.args != null) {
                    for (String arg : p.args) {
                        if (!inner.contains(arg)) {
                            throw new JxmlException(p.source, p.line, "<For memo> builds its elements once for as long as the list is the "
                                    + "same, so \"" + p.attr.name + "\" can pass only the loop variable " + roots + ", not \"" + arg + "\"");
                        }
                    }
                }
            }
            checkMemoBody(e.children, inner);
            checkMemoBody(e.otherwise, roots);
        }
    }

    /** A tag that takes only some tags as children: loops and conditions are looked through, anything else is an error. */
    private static void checkChildren(JxmlSchema.Tag tag, List<Element> children, String source) {
        for (Element child : children) {
            if (child.tag.control) {
                checkChildren(tag, child.children, source);
                checkChildren(tag, child.otherwise, source);
            } else if (!tag.only.contains(child.tag.name)) {
                throw new JxmlException(child.source, child.line, "<" + tag.name + "> takes only " + names(tag.only)
                        + " as children, not <" + child.tag.name + ">");
            }
        }
    }

    /** A {@code Tree} has one column: {@code values} (the cells after the text) belong to a {@code TreeTable}. */
    private static void checkNoValues(List<Element> items) {
        for (Element item : items) {
            Prop values = item.tag.control ? null : item.prop("values");
            if (values != null) {
                throw new JxmlException(item.source, values.line,
                        "a <TreeItem> has values only in a <TreeTable>; a <Tree> has one column, its text");
            }
            checkNoValues(item.children);
            checkNoValues(item.otherwise);
        }
    }

    private static String names(List<String> tags) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < tags.size(); i++) {
            out.append(i == 0 ? "" : i == tags.size() - 1 ? " or " : ", ").append('<').append(tags.get(i)).append('>');
        }
        return out.toString();
    }

    /** Rows a virtual list builds when {@code rows} is not written: well above what a window shows. */
    static final int DEFAULT_ROWS = 30;

    /**
     * A {@code ListView} or {@code TableView} as the list element with one child, a {@code <For>} over its
     * {@code items} that builds {@code rows} rows from {@code first}; the template becomes the loop body.
     * {@code var}, {@code type}, {@code key} and {@code rows} belong to the loop, not to the list.
     */
    private static JxmlParser.Node virtualRows(JxmlParser.Node node, JxmlSchema.Tag tag, String source) {
        String items = node.attrs.get("items");
        if (items == null) {
            throw new JxmlException(source, node.line, "<" + tag.name + "> needs items=\"${...}\", the rows' model");
        }
        String var = node.attrs.get("var");
        if (var == null || !isIdentifier(var)) {
            throw new JxmlException(source, node.line, "<" + tag.name + "> needs var=\"name\", the name of a row's item");
        }
        if (node.children.size() != 1) {
            throw new JxmlException(source, node.line, "<" + tag.name + "> takes one child, the template of a row");
        }
        JxmlParser.Node template = node.children.get(0);
        JxmlSchema.Tag templateTag = JxmlSchema.tag(template.tag);
        if ("TableView".equals(tag.name) && !"TableRow".equals(template.tag)) {
            throw new JxmlException(source, template.line, "<TableView> takes one <TableRow>, the template of a row");
        }
        if (templateTag != null && (templateTag.control || isSlot(templateTag))) {
            throw new JxmlException(source, template.line, "the row template of <" + tag.name + "> is one element, not <"
                    + template.tag + ">");
        }
        JxmlParser.Node loop = new JxmlParser.Node("For", node.line, node.source);
        loop.attrs.put("items", items);
        loop.attrs.put("var", var);
        if (node.attrs.containsKey("type")) {
            loop.attrs.put("type", node.attrs.get("type"));
        }
        if (node.attrs.containsKey("key")) {
            loop.attrs.put("key", node.attrs.get("key"));
        }
        loop.attrs.put("from", node.attrs.containsKey("first") ? node.attrs.get("first") : "0");
        loop.attrs.put("limit", node.attrs.containsKey("rows") ? node.attrs.get("rows") : String.valueOf(DEFAULT_ROWS));
        loop.children.add(template);
        JxmlParser.Node list = new JxmlParser.Node(node.tag, node.line, node.source);
        for (Map.Entry<String, String> entry : node.attrs.entrySet()) {
            String name = entry.getKey();
            if (!"var".equals(name) && !"type".equals(name) && !"key".equals(name) && !"rows".equals(name)) {
                list.attrs.put(name, entry.getValue());
            }
        }
        list.children.add(loop);
        return list;
    }

    /** A radio or toggle button in a group: {@code group} is what the group holds, {@code value} what this one chooses. */
    private static void checkGroup(JxmlParser.Node node, JxmlSchema.Tag tag, int changes, String source) {
        boolean group = node.attrs.containsKey("group");
        String name = "<" + tag.name + ">";
        if (group && !node.attrs.containsKey("value")) {
            throw new JxmlException(source, node.line, name + " with group needs value=\"...\", what the group holds when this one is chosen");
        }
        if (!group && node.attrs.containsKey("value")) {
            throw new JxmlException(source, node.line, name + " value only means something with group=\"${...}\" or group=\"@{...}\"");
        }
        if (!group && changes > 0) {
            throw new JxmlException(source, node.line, name + " reports a choice only with a group; add group=\"${...}\"");
        }
        if (group && node.attrs.containsKey("selected")) {
            throw new JxmlException(source, node.line, name + " has both group and selected; the group decides");
        }
        if (changes > 0 && node.attrs.containsKey("onAction")) {
            throw new JxmlException(source, node.line, name + " has both onAction and a group choice (onChange or @{...}); use one");
        }
    }

    /** Children of a tag that takes many: an {@code <Else>} joins the {@code <If>} just before it. */
    private static List<Element> resolveAll(List<JxmlParser.Node> nodes, String source, JxmlSchema.Tag parent, JxmlSchema.Tag owner,
                                            Var vars, String theme) {
        List<Element> out = new ArrayList<Element>();
        boolean openIf = false;
        for (JxmlParser.Node node : nodes) {
            if ("Else".equals(node.tag)) {
                if (!openIf) {
                    throw new JxmlException(source, node.line, "<Else> must directly follow an <If>");
                }
                if (!node.attrs.isEmpty()) {
                    throw new JxmlException(source, node.line, "<Else> has no attribute " + node.attrs.keySet().iterator().next());
                }
                int last = out.size() - 1;
                out.set(last, out.get(last).withOtherwise(resolveAll(node.children, source, JxmlSchema.tag("Else"), owner, vars, theme)));
                openIf = false;
                continue;
            }
            Element e = resolve(node, source, parent, owner, vars, theme);
            out.add(e);
            openIf = "If".equals(e.tag.name);
        }
        return out;
    }

    private static void addProps(List<Prop> out, JxmlSchema.Attr attr, String value, String source, int line,
                                 JxmlSchema.Tag tag, Var vars, String theme) {
        if (attr.type == AttrType.HANDLER || attr.type == AttrType.CHANGE) {
            out.add(handler(attr, value, source, line, vars));
            return;
        }
        if (value.startsWith("@{") && value.endsWith("}")) {
            if (!attr.name.equals(tag.changeAttr)) {
                throw new JxmlException(source, line, "<" + tag.name + "> " + attr.name + " cannot be bound two ways with @{...}"
                        + (tag.changeAttr == null ? "" : "; use " + tag.changeAttr + "=\"@{...}\""));
            }
            String[] segments = segments(value.substring(2, value.length() - 1), value, source, line);
            String last = segments[segments.length - 1];
            if (last.endsWith("()")) {
                throw new JxmlException(source, line, value + " needs a bean property with a setter; "
                        + last + " is a record accessor, which has none");
            }
            Prop read = reference(attr, segments, false, value, source, line, vars);
            if (read.path.length == 0) {
                throw new JxmlException(source, line, "bad binding " + value + "; expected @{name} or @{a.b}, a property to set");
            }
            JxmlSchema.Attr change = tag.attr("onChange");
            out.add(read);
            out.add(new Prop(change, Kind.SETTER, null, read.root, java.util.Arrays.copyOf(read.path, read.path.length - 1),
                    "set" + Character.toUpperCase(last.charAt(0)) + last.substring(1), new String[0], false, source, line));
            return;
        }
        if (value.startsWith("${") && value.endsWith("}")) {
            if (!attr.bindable) {
                throw new JxmlException(source, line, "<" + tag.name + "> " + attr.name + " cannot be bound");
            }
            String inner = value.substring(2, value.length() - 1).trim();
            boolean negate = inner.startsWith("!");
            if (negate) {
                if (!"If".equals(tag.name)) {
                    throw new JxmlException(source, line, "${!...} is only allowed in <If test>");
                }
                inner = inner.substring(1).trim();
            }
            out.add(reference(attr, segments(inner, value, source, line), negate, value, source, line, vars));
            return;
        }
        String text = value.startsWith("\\") ? value.substring(1) : value;
        if (attr.choices != null && !attr.choices.contains(text.trim())) {
            throw new JxmlException(source, line, attr.name + "=\"" + value + "\" is not one of " + attr.choices);
        }
        if (attr.type == AttrType.COLOR && JxmlThemes.isToken(text)) {
            text = String.format("#%08X", JxmlThemes.color(theme, text)); // a theme token, resolved now: both modes get the same color
        }
        try {
            out.add(new Prop(attr, Kind.LITERAL, JxmlValues.literal(attr.type, text), null, null, null, null, false, source, line));
        } catch (IllegalArgumentException e) {
            throw new JxmlException(source, line, attr.name + "=\"" + value + "\" is not a valid "
                    + attr.type.name().toLowerCase() + ": " + e.getMessage());
        }
    }

    private static String[] segments(String path, String value, String source, int line) {
        String[] segments = path.trim().split("\\.", -1);
        for (String segment : segments) {
            String name = segment.endsWith("()") ? segment.substring(0, segment.length() - 2) : segment;
            if (!isIdentifier(name)) {
                throw new JxmlException(source, line, "bad binding " + value
                        + "; expected ${name}, ${a.b} or, for records, ${a.b()}");
            }
        }
        return segments;
    }

    /** A binding: the first segment names a loop variable in scope, or else a getter of the controller. */
    private static Prop reference(JxmlSchema.Attr attr, String[] segments, boolean negate, String value, String source,
                                  int line, Var vars) {
        Var v = Var.find(vars, segments[0]);
        if (v == null) {
            return new Prop(attr, Kind.BINDING, null, null, segments, null, null, negate, source, line);
        }
        if (segments.length > 1 && !v.typed) {
            throw untyped(v.name, source, line);
        }
        return new Prop(attr, Kind.BINDING, null, v.name, java.util.Arrays.copyOfRange(segments, 1, segments.length), null,
                null, negate, source, line);
    }

    private static JxmlException untyped(String name, String source, int line) {
        return new JxmlException(source, line, "the loop variable " + name + " is used with getters or handlers, so its <For> needs "
                + "type=\"fully.qualified.Item\"; the generated code is checked by javac against it");
    }

    /** {@code #save}, or {@code #open(item)} with loop variables passed first. */
    private static Prop handler(JxmlSchema.Attr attr, String value, String source, int line, Var vars) {
        String message = attr.name + " must name a controller method, such as #save";
        if (!value.startsWith("#")) {
            throw new JxmlException(source, line, message);
        }
        String name = value.substring(1).trim();
        String[] args = new String[0];
        int open = name.indexOf('(');
        if (open >= 0) {
            if (!name.endsWith(")")) {
                throw new JxmlException(source, line, message);
            }
            String list = name.substring(open + 1, name.length() - 1).trim();
            name = name.substring(0, open).trim();
            args = list.isEmpty() ? args : list.split(",", -1);
            for (int i = 0; i < args.length; i++) {
                args[i] = args[i].trim();
                Var v = Var.find(vars, args[i]);
                if (v == null) {
                    throw new JxmlException(source, line, args[i] + " in " + value + " is not a loop variable in scope");
                }
                if (!v.typed) {
                    throw untyped(v.name, source, line);
                }
            }
        }
        if (!isIdentifier(name)) {
            throw new JxmlException(source, line, message);
        }
        return new Prop(attr, attr.type == AttrType.CHANGE ? Kind.CHANGE : Kind.HANDLER, null, null, null, name, args, false, source, line);
    }

    static boolean isSlot(JxmlSchema.Tag tag) {
        return tag.attrs.containsKey("content") && tag.nativeType == null;
    }

    static boolean isIdentifier(String s) {
        if (s.isEmpty() || !Character.isJavaIdentifierStart(s.charAt(0))) {
            return false;
        }
        for (int i = 1; i < s.length(); i++) {
            if (!Character.isJavaIdentifierPart(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    // ---- interpretation --------------------------------------------------------------------------

    private JXElement build(Element e, Object controller, Scope scope, Object key) {
        if (isSlot(e.tag)) {
            return (JXElement) value(e.props.get(0), controller, scope);
        }
        JXProps.Builder props = JXProps.builder(e.defaults.size() + e.props.size() + (key == null ? 0 : 1));
        for (Map.Entry<String, Object> d : e.defaults.entrySet()) {
            props.set(d.getKey(), d.getValue());
        }
        Integer selected = Integer.valueOf(0);
        for (Prop p : e.props) {
            Object value = value(p, controller, scope);
            if (e.tag.children == JxmlSchema.Children.TABS && "selected".equals(p.attr.prop)) {
                selected = (Integer) value;
            } else {
                props.set(p.attr.prop, value);
            }
        }
        if (key != null) {
            props.set("key", key);
        }
        List<JXElement> built = new ArrayList<JXElement>(e.children.size());
        if (e.tag.children == JxmlSchema.Children.TABS) {
            for (int i = 0; i < e.children.size(); i++) {
                built.add(build(e.children.get(i), controller, scope, JxmlRuntime.tabKey(i)));
            }
        } else {
            addAll(e.children, controller, scope, null, built);
        }
        JXElement[] children = built.toArray(new JXElement[0]);
        if (e.tag.children == JxmlSchema.Children.TABS) {
            return JxmlRuntime.tabs(props, selected, e.titles, e.closable, children);
        }
        JXProps finished = props.build();
        if (e.tag.finish) {
            try {
                finished = JxmlRuntime.finish(e.tag.name, finished);
            } catch (IllegalArgumentException ex) {
                throw new JxmlException(e.source, e.line, "<" + e.tag.name + "> " + ex.getMessage(), ex);
            }
        }
        if (e.tag.tree) {
            try {
                return JxmlRuntime.tree(e.tag.name, finished, children);
            } catch (IllegalArgumentException ex) {
                throw new JxmlException(e.source, e.line, "<" + e.tag.name + "> " + ex.getMessage(), ex);
            }
        }
        return JXElement.of(e.tag.nativeType, finished, children);
    }

    /** Builds {@code nodes} into {@code out}; a loop or condition puts its elements here, in place. */
    private void addAll(List<Element> nodes, Object controller, Scope scope, Object key, List<JXElement> out) {
        for (Element n : nodes) {
            if ("For".equals(n.tag.name)) {
                Prop items = n.prop("items");
                Prop keyProp = n.prop("key");
                Prop from = n.prop("from");
                Prop limit = n.prop("limit");
                Object source = value(items, controller, scope);
                Integer fromValue = from == null ? null : (Integer) value(from, controller, scope);
                Integer limitValue = limit == null ? null : (Integer) value(limit, controller, scope);
                JXElement[] kept = n.memo == null ? null : n.memo.get(controller, source, fromValue, limitValue);
                if (kept != null) {
                    Collections.addAll(out, kept);
                    continue;
                }
                Iterable<Object> each;
                try {
                    each = JxmlRuntime.items(source, fromValue, limitValue);
                } catch (IllegalArgumentException ex) {
                    throw new JxmlException(items.source, items.line, ex.getMessage());
                }
                List<JXElement> into = n.memo == null ? out : new ArrayList<JXElement>();
                for (Object item : each) {
                    Scope inner = new Scope(n.var, item, scope);
                    addAll(n.children, controller, inner, keyProp == null ? null : value(keyProp, controller, inner), into);
                }
                if (n.memo != null) {
                    Collections.addAll(out, n.memo.put(controller, source, fromValue, limitValue, into.toArray(new JXElement[0])));
                }
            } else if ("If".equals(n.tag.name)) {
                boolean test = ((Boolean) value(n.prop("test"), controller, scope)).booleanValue();
                addAll(test ? n.children : n.otherwise, controller, scope, null, out);
            } else {
                out.add(build(n, controller, scope, key));
            }
        }
    }

    private Object value(Prop p, Object controller, Scope scope) {
        switch (p.kind) {
            case LITERAL:
                return p.literal;
            case HANDLER:
                final Object[] fixed = loopValues(p, scope);
                final Method method = method(controller, p, p.method, fixed.length, null);
                final Object target = controller;
                return new Runnable() {
                    @Override
                    public void run() {
                        invoke(method, target, fixed, p);
                    }
                };
            case CHANGE:
                return change(p, controller, loopValues(p, scope));
            case SETTER:
                return setter(p, controller, scope);
            case BINDING:
                Object value = walk(p, controller, scope, p.path.length);
                Object bound;
                try {
                    bound = JxmlValues.bound(p.attr.type, value);
                } catch (IllegalArgumentException ex) {
                    throw new JxmlException(p.source, p.line, p.attr.name + "=\"${" + String.join(".", p.path) + "}\": " + ex.getMessage(), ex);
                }
                return p.negate ? Boolean.valueOf(!((Boolean) bound).booleanValue()) : bound;
            default:
                throw new IllegalStateException();
        }
    }

    private static Object[] loopValues(Prop p, Scope scope) {
        Object[] values = new Object[p.args.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = Scope.get(scope, p.args[i]);
        }
        return values;
    }

    /** The value of the first {@code count} getters of {@code p}'s path, from its loop variable or the controller. */
    private Object walk(Prop p, Object controller, Scope scope, int count) {
        Object value = p.root == null ? controller : Scope.get(scope, p.root);
        for (int i = 0; i < count; i++) {
            if (value == null) {
                throw new JxmlException(p.source, p.line, "${" + String.join(".", p.path) + "}: null before " + p.path[i]);
            }
            value = invoke(method(value, p, getter(p.path[i]), 0, null), value, new Object[0], p);
        }
        return value;
    }

    /** The method an {@code onChange} handler names, called with the loop variables and then the new value. */
    private Consumer<Object> change(final Prop p, final Object controller, final Object[] fixed) {
        final Class<?> valueType = valueClass(p);
        final Method method = method(controller, p, p.method, fixed.length + 1, valueType);
        return new Consumer<Object>() {
            @Override
            public void accept(Object newValue) {
                Object[] args = java.util.Arrays.copyOf(fixed, fixed.length + 1);
                args[fixed.length] = newValue;
                invoke(method, controller, args, p);
            }
        };
    }

    /** {@code @{a.b}}: the setter {@code setB} of the bean {@code getA()} returns, found when the value arrives. */
    private Consumer<Object> setter(final Prop p, final Object controller, final Scope scope) {
        final Class<?> valueType = valueClass(p);
        return new Consumer<Object>() {
            @Override
            public void accept(Object newValue) {
                Object bean = walk(p, controller, scope, p.path.length);
                invoke(method(bean, p, p.method, 1, valueType), bean, new Object[] {newValue}, p);
            }
        };
    }

    /** What the input passes to onChange: String, Boolean or Double, by tag. */
    private Class<?> valueClass(Prop p) {
        switch (p.attr.valueType) {
            case BOOLEAN:
                return Boolean.class;
            case DOUBLE:
                return Double.class;
            case INT:
                return Integer.class;
            case DOUBLES:
                return double[].class;
            default:
                return String.class;
        }
    }

    /**
     * A public method of the target's class: with no arguments for a getter or handler, otherwise the
     * first of that name with {@code arity} parameters whose last takes {@code last} (boxed or primitive).
     */
    private Method method(Object target, Prop p, String name, int arity, Class<?> last) {
        if (target == null) {
            throw new JxmlException(p.source, p.line, p.attr.name + " needs a controller");
        }
        Class<?> type = target.getClass();
        Map<String, Method> byName = methods.computeIfAbsent(type, k -> new ConcurrentHashMap<String, Method>());
        String id = name + "/" + arity + "/" + (last == null ? "" : last.getSimpleName());
        Method m = byName.get(id);
        if (m == null) {
            for (Method candidate : type.getMethods()) {
                Class<?>[] params = candidate.getParameterTypes();
                if (candidate.getName().equals(name) && params.length == arity
                        && (last == null || accepts(params[arity - 1], last))) {
                    m = candidate;
                    break;
                }
            }
            if (m == null) {
                throw new JxmlException(p.source, p.line, type.getName() + " has no public method " + name + "("
                        + (arity == 0 ? "" : "...") + ")" + (last == null ? "" : " taking a " + last.getSimpleName() + " last"));
            }
            if (!Modifier.isPublic(m.getDeclaringClass().getModifiers())) {
                m.setAccessible(true); // a public method of a package-private class, as the generated code calls it
            }
            byName.put(id, m);
        }
        return m;
    }

    /** Whether javac accepts a value of boxed type {@code value} for a parameter of type {@code param}. */
    private static boolean accepts(Class<?> param, Class<?> value) {
        if (param.isPrimitive()) {
            return param == boolean.class && value == Boolean.class || param == double.class && value == Double.class
                    || param == int.class && value == Integer.class;
        }
        return param.isAssignableFrom(value);
    }

    private Object invoke(Method m, Object target, Object[] args, Prop p) {
        try {
            return m.invoke(target, args);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            throw new JxmlException(p.source, p.line, "cannot call " + m, e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new JxmlException(p.source, p.line, m.getName() + " failed", cause);
        }
    }

    /**
     * The method a binding segment calls: {@code name} is the bean getter {@code getName()};
     * {@code name()} is called as written, which is how records (Java 16+) expose their components.
     */
    static String getter(String segment) {
        if (segment.endsWith("()")) {
            return segment.substring(0, segment.length() - 2);
        }
        return "get" + Character.toUpperCase(segment.charAt(0)) + segment.substring(1);
    }
}

