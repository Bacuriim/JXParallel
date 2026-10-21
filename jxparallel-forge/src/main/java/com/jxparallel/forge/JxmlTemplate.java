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

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

/**
 * A JXML file read, checked against {@link JxmlSchema} and resolved: the interpreter. It builds
 * the same tree the generated code builds, finding getters and handlers by reflection instead of
 * calling them directly. Reusable and thread safe; reflective lookups are cached per controller class.
 *
 * <p>Binding rules, the same in the compiler: {@code ${a.b}} calls {@code getA().getB()} on the
 * controller; {@code #save} calls the controller's public no-argument method {@code save()};
 * a value starting with a backslash is a literal without it ({@code \#1}).
 */
public final class JxmlTemplate implements JxmlView<Object> {
    /** How an attribute gets its value. */
    enum Kind { LITERAL, BINDING, HANDLER }

    /** One prop of one element. */
    static final class Prop {
        final JxmlSchema.Attr attr;
        final Kind kind;
        final Object literal;
        final String[] path;
        final String method;
        final int line;

        Prop(JxmlSchema.Attr attr, Kind kind, Object literal, String[] path, String method, int line) {
            this.attr = attr;
            this.kind = kind;
            this.literal = literal;
            this.path = path;
            this.method = method;
            this.line = line;
        }
    }

    /** One element, or a tab pane with its tabs. */
    static final class Element {
        final JxmlSchema.Tag tag;
        final int line;
        final List<Prop> props;
        final List<Element> children;
        /** Tabs only: titles and close flags of the tabs, in order; children are the contents. */
        final String[] titles;
        final boolean[] closable;
        /** The tag's default props that no attribute of this element sets. */
        final Map<String, Object> defaults;

        Element(JxmlSchema.Tag tag, int line, List<Prop> props, List<Element> children, String[] titles, boolean[] closable) {
            this.tag = tag;
            this.line = line;
            this.props = props;
            this.children = children;
            this.titles = titles;
            this.closable = closable;
            Map<String, Object> d = new java.util.LinkedHashMap<String, Object>(tag.defaults);
            for (Prop p : props) {
                d.remove(p.attr.prop);
            }
            this.defaults = Collections.unmodifiableMap(d);
        }

        /** No bindings or handlers anywhere below: the same tree for every controller. */
        boolean constant() {
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
        JxmlParser.Node node = JxmlParser.parse(input, source);
        String controller = node.attrs.remove("controller");
        return new JxmlTemplate(source, controller, resolve(node, source, null));
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
        return build(root, controller, null);
    }

    // ---- resolution ------------------------------------------------------------------------------

    private static Element resolve(JxmlParser.Node node, String source, JxmlSchema.Tag parent) {
        JxmlSchema.Tag tag = JxmlSchema.tag(node.tag);
        if (tag == null) {
            throw new JxmlException(source, node.line, "unknown tag <" + node.tag + ">; known: " + JxmlSchema.tags().keySet());
        }
        if (tag.children == JxmlSchema.Children.TAB && (parent == null || parent.children != JxmlSchema.Children.TABS)) {
            throw new JxmlException(source, node.line, "<Tab> is only allowed inside <Tabs>");
        }
        if (node.attrs.containsKey("controller")) {
            throw new JxmlException(source, node.line, "controller is only allowed on the root element");
        }
        List<Prop> props = new ArrayList<Prop>();
        for (Map.Entry<String, String> entry : node.attrs.entrySet()) {
            JxmlSchema.Attr attr = tag.attr(entry.getKey());
            if (attr == null) {
                throw new JxmlException(source, node.line, "<" + tag.name + "> has no attribute " + entry.getKey());
            }
            props.add(prop(attr, entry.getValue(), source, node.line, tag));
        }
        if (isSlot(tag) && (props.size() != 1 || !"content".equals(props.get(0).attr.name))) {
            throw new JxmlException(source, node.line, "<Slot> takes exactly one attribute: content=\"${...}\"");
        }
        List<Element> children = new ArrayList<Element>();
        String[] titles = null;
        boolean[] closable = null;
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
                    children.add(resolve(child, source, tag));
                }
                break;
            case MANY:
                for (JxmlParser.Node child : node.children) {
                    children.add(resolve(child, source, tag));
                }
                break;
            case TABS:
                titles = new String[node.children.size()];
                closable = new boolean[node.children.size()];
                for (int i = 0; i < node.children.size(); i++) {
                    JxmlParser.Node tabNode = node.children.get(i);
                    if (!"Tab".equals(tabNode.tag)) {
                        throw new JxmlException(source, tabNode.line, "<Tabs> takes only <Tab> children");
                    }
                    Element tab = resolve(tabNode, source, tag);
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
        return new Element(tag, node.line, Collections.unmodifiableList(props), Collections.unmodifiableList(children),
                titles, closable);
    }

    private static Prop prop(JxmlSchema.Attr attr, String value, String source, int line, JxmlSchema.Tag tag) {
        if (attr.type == AttrType.HANDLER) {
            if (!value.startsWith("#") || !isIdentifier(value.substring(1))) {
                throw new JxmlException(source, line, attr.name + " must name a controller method, such as #save");
            }
            return new Prop(attr, Kind.HANDLER, null, null, value.substring(1), line);
        }
        if (value.startsWith("${") && value.endsWith("}")) {
            if (!attr.bindable) {
                throw new JxmlException(source, line, "<" + tag.name + "> " + attr.name + " cannot be bound");
            }
            String[] path = value.substring(2, value.length() - 1).trim().split("\\.", -1);
            for (String segment : path) {
                if (!isIdentifier(segment)) {
                    throw new JxmlException(source, line, "bad binding " + value + "; expected ${name} or ${a.b}");
                }
            }
            return new Prop(attr, Kind.BINDING, null, path, null, line);
        }
        String text = value.startsWith("\\") ? value.substring(1) : value;
        try {
            return new Prop(attr, Kind.LITERAL, JxmlValues.literal(attr.type, text), null, null, line);
        } catch (IllegalArgumentException e) {
            throw new JxmlException(source, line, attr.name + "=\"" + value + "\" is not a valid "
                    + attr.type.name().toLowerCase() + ": " + e.getMessage());
        }
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

    private JXElement build(Element e, Object controller, String key) {
        if (isSlot(e.tag)) {
            return (JXElement) value(e.props.get(0), controller);
        }
        JXProps.Builder props = JXProps.builder();
        for (Map.Entry<String, Object> d : e.defaults.entrySet()) {
            props.set(d.getKey(), d.getValue());
        }
        Integer selected = Integer.valueOf(0);
        for (Prop p : e.props) {
            Object value = value(p, controller);
            if (e.tag.children == JxmlSchema.Children.TABS && "selected".equals(p.attr.prop)) {
                selected = (Integer) value;
            } else {
                props.set(p.attr.prop, value);
            }
        }
        if (key != null) {
            props.set("key", key);
        }
        int n = e.children.size();
        JXElement[] children = new JXElement[n];
        for (int i = 0; i < n; i++) {
            children[i] = build(e.children.get(i), controller,
                    e.tag.children == JxmlSchema.Children.TABS ? JxmlRuntime.tabKey(i) : null);
        }
        if (e.tag.children == JxmlSchema.Children.TABS) {
            return JxmlRuntime.tabs(props, selected, e.titles, e.closable, children);
        }
        return JXElement.of(e.tag.nativeType, props.build(), children);
    }

    private Object value(Prop p, Object controller) {
        switch (p.kind) {
            case LITERAL:
                return p.literal;
            case HANDLER:
                final Method method = method(controller, p, p.method, true);
                final Object target = controller;
                return new Runnable() {
                    @Override
                    public void run() {
                        invoke(method, target, p);
                    }
                };
            case BINDING:
                Object value = controller;
                for (String segment : p.path) {
                    if (value == null) {
                        throw new JxmlException(source, p.line, "${" + String.join(".", p.path) + "}: null before " + segment);
                    }
                    value = invoke(method(value, p, getter(segment), false), value, p);
                }
                return JxmlValues.bound(p.attr.type, value);
            default:
                throw new IllegalStateException();
        }
    }

    private Method method(Object target, Prop p, String name, boolean handler) {
        if (target == null) {
            throw new JxmlException(source, p.line, p.attr.name + " needs a controller");
        }
        Class<?> type = target.getClass();
        Map<String, Method> byName = methods.get(type);
        if (byName == null) {
            byName = new ConcurrentHashMap<String, Method>();
            methods.put(type, byName);
        }
        Method m = byName.get(name);
        if (m == null) {
            try {
                m = type.getMethod(name);
            } catch (NoSuchMethodException e) {
                throw new JxmlException(source, p.line, type.getName() + " has no public method " + name + "()");
            }
            if (!Modifier.isPublic(m.getDeclaringClass().getModifiers())) {
                m.setAccessible(true); // a public method of a package-private class, as the generated code calls it
            }
            byName.put(name, m);
        }
        return m;
    }

    private Object invoke(Method m, Object target, Prop p) {
        try {
            return m.invoke(target);
        } catch (IllegalAccessException e) {
            throw new JxmlException(source, p.line, "cannot call " + m, e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new JxmlException(source, p.line, m.getName() + " failed", cause);
        }
    }

    static String getter(String property) {
        return "get" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
    }
}
