package com.jxparallel.forge;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns a {@link JxmlTemplate} into the source of a plain Java class that builds the same tree:
 * direct calls instead of reflection, no XML at runtime. Controller methods are called by name,
 * so javac checks them; every generated line ends with the JXML file and line it comes from.
 *
 * <p>Literal values become static constants, so a build allocates no more than the interpreter.
 * With {@code hoist}, subtrees without bindings or handlers are built once, when the class loads,
 * and shared by every build: elements are immutable, so this is safe.
 *
 * <p>A {@code <For>} becomes a Java {@code for} loop over a variable of the class named by its
 * {@code type}, so javac checks what the body calls on each item; an {@code <If>} becomes an
 * {@code if}. The container holding them collects its children in a list, in order.
 */
public final class JxmlCompiler {
    /** What to generate. */
    public static final class Options {
        public final String packageName;
        public final String className;
        public final boolean hoist;

        public Options(String packageName, String className, boolean hoist) {
            this.packageName = packageName;
            this.className = className;
            this.hoist = hoist;
        }
    }

    /** A loop variable in scope: its JXML name, the Java variable that holds it and that variable's type. */
    private static final class Local {
        final String name;
        final String javaName;
        final String javaType;

        Local(String name, String javaName, String javaType) {
            this.name = name;
            this.javaName = javaName;
            this.javaType = javaType;
        }
    }

    private final JxmlTemplate template;
    private final Options options;
    private final String controllerType;
    private final StringBuilder fields = new StringBuilder();
    private final List<String> methods = new ArrayList<String>();
    private final Map<Object, String> constants = new IdentityHashMap<Object, String>();
    private final List<Local> scope = new ArrayList<Local>();
    private int nextConstant;
    private int nextMethod;
    private int nextVariable;
    private int nextMemo;
    /** The expression that builds each opaque element (see {@link #opaque}) and the loop variables it reads: the live plan calls it again. */
    private final Map<JxmlTemplate.Element, Object[]> opaqueBuilt = new IdentityHashMap<JxmlTemplate.Element, Object[]>();
    private final StringBuilder liveMembers = new StringBuilder();
    private final Map<String, String> liveGetters = new LinkedHashMap<String, String>();
    private final Map<String, String> liveGetterNames = new java.util.HashMap<String, String>();
    /** The row templates of the virtual lists a live session keeps live (see {@link #virtualList}), and the expression that builds each. */
    private final java.util.Set<JxmlTemplate.Element> rowRoots = java.util.Collections.newSetFromMap(new IdentityHashMap<JxmlTemplate.Element, Boolean>());
    private final Map<JxmlTemplate.Element, Object[]> rowBuilt = new IdentityHashMap<JxmlTemplate.Element, Object[]>();
    private int nextPlan;
    /** The screen has a translated prop: its live session watches the locale. */
    private boolean liveLocale;

    private JxmlCompiler(JxmlTemplate template, Options options) {
        this.template = template;
        this.options = options;
        this.controllerType = template.controller == null ? "Object" : template.controller;
    }

    /** The Java source of the class for {@code template}. */
    public static String compile(JxmlTemplate template, Options options) {
        return new JxmlCompiler(template, options).generate();
    }

    /** {@code customer-list.jxml} becomes {@code CustomerListView}. */
    public static String className(String fileName, String suffix) {
        String base = fileName.endsWith(".jxml") || fileName.endsWith(".fxml") ? fileName.substring(0, fileName.length() - 5) : fileName;
        StringBuilder name = new StringBuilder();
        boolean upper = true;
        for (char ch : base.toCharArray()) {
            if (!Character.isJavaIdentifierPart(ch)) {
                upper = true;
            } else {
                name.append(upper ? Character.toUpperCase(ch) : ch);
                upper = false;
            }
        }
        if (name.length() == 0 || !Character.isJavaIdentifierStart(name.charAt(0))) {
            name.insert(0, 'V');
        }
        return name + suffix;
    }

    private String generate() {
        if (template.controller == null && template.root.needsController()) {
            throw new JxmlException(template.source, template.root.line,
                    "bindings and handlers need controller=\"fully.qualified.Class\" on the root element");
        }
        findRowRoots(template.root);
        String root = note(template.root, element(template.root, null, false, "            "));
        String live = livePlan();
        StringBuilder out = new StringBuilder();
        out.append("// Generated by JXForge from ").append(template.source).append(". Do not edit.\n");
        if (options.packageName != null && !options.packageName.isEmpty()) {
            out.append("package ").append(options.packageName).append(";\n\n");
        }
        if (nextMemo > 0) {
            out.append("import com.jxparallel.forge.JxmlMemo;\n");
        }
        out.append("import com.jxparallel.forge.JxmlLive;\n")
                .append("import com.jxparallel.forge.JxmlRuntime;\n")
                .append("import com.jxparallel.forge.JxmlValues;\n")
                .append("import com.jxparallel.forge.JxmlView;\n")
                .append("import com.jxparallel.ui.JXElement;\n")
                .append("import com.jxparallel.ui.JXProps;\n\n")
                .append("public final class ").append(options.className)
                .append(" implements JxmlView<").append(controllerType).append("> {\n")
                .append("    public static final String SOURCE = ").append(quote(template.source)).append(";\n")
                .append(fields)
                .append("\n    @Override\n")
                .append("    public JXElement build(final ").append(controllerType).append(" c) {\n")
                .append("        return ").append(root).append(";\n")
                .append("    }\n")
                .append(live);
        for (String method : methods) {
            out.append('\n').append(method);
        }
        out.append("}\n");
        return out.toString();
    }

    /**
     * An expression for {@code e}, which is a plain element. In a constant context (inside a hoisted
     * subtree) there is no controller and nothing is hoisted again. {@code key} is a Java expression
     * for the element's key prop, or null; only a string literal can be hoisted with the element.
     */
    private String element(JxmlTemplate.Element e, String key, boolean constantContext, String indent) {
        if (options.hoist && !constantContext && e.constant() && (key == null || key.startsWith("\""))) {
            String name = "K" + (nextConstant++);
            String body = construct(e, key, true, "            ");
            fields.append("    private static final JXElement ").append(name).append(" = ").append(body)
                    .append("; // ").append(where(e.source, e.line)).append('\n');
            return name;
        }
        return construct(e, key, constantContext, indent);
    }

    /** Leaves inline; containers in their own method, which keeps methods small and lines traceable. */
    private String construct(JxmlTemplate.Element e, String key, boolean constantContext, String indent) {
        if (e.children.isEmpty()) {
            return inline(e, key, constantContext, indent + "    ", false);
        }
        String name = (constantContext ? "k" : "e") + (nextMethod++);
        StringBuilder m = new StringBuilder();
        m.append("    private static JXElement ").append(name).append('(')
                .append(constantContext ? "" : "final " + controllerType + " c" + scopeParameters()).append(") { // ")
                .append(where(e.source, e.line)).append('\n');
        if (dynamic(e)) {
            m.append("        final java.util.List<JXElement> k = new java.util.ArrayList<JXElement>();\n");
            emit(e.children, null, "k", m, "        ");
            m.append("        return ").append(inline(e, key, constantContext, "                ", true)).append(";\n");
        } else {
            m.append("        return ").append(inline(e, key, constantContext, "                ", false)).append(";\n");
        }
        m.append("    }\n");
        methods.add(m.toString());
        return name + (constantContext ? "()" : "(c" + scopeArguments() + ")");
    }

    private static boolean dynamic(JxmlTemplate.Element e) {
        for (JxmlTemplate.Element child : e.children) {
            if (child.tag.control) {
                return true;
            }
        }
        return false;
    }

    /** Statements adding the elements of {@code nodes} to the list {@code k}; loops and conditions open blocks. */
    private void emit(List<JxmlTemplate.Element> nodes, String key, String list, StringBuilder out, String indent) {
        for (JxmlTemplate.Element n : nodes) {
            if ("For".equals(n.tag.name)) {
                String type = n.type == null ? "Object" : n.type;
                String itemsValue = reference(n.prop("items"));
                String items = itemsValue;
                JxmlTemplate.Prop from = n.prop("from");
                JxmlTemplate.Prop limit = n.prop("limit");
                if (from != null || limit != null) { // a window of the items: a virtual list's rows
                    items += ", " + (from == null ? "null" : value(from, n.tag)) + ", " + (limit == null ? "null" : value(limit, n.tag));
                }
                String into = list;
                String inner = indent;
                if (n.memo != null) { // the elements of the last build, while the controller, the list and the window are the same
                    String memo = "M" + (nextMemo++);
                    into = "k" + memo;
                    inner = indent + "    ";
                    fields.append("    private static final JxmlMemo ").append(memo).append(" = new JxmlMemo(); // ")
                            .append(where(n.source, n.line)).append('\n');
                    out.append(indent).append("{ // ").append(where(n.source, n.line)).append(": memo\n")
                            .append(inner).append("final Object s").append(memo).append(" = ").append(itemsValue).append(";\n")
                            .append(inner).append("final Integer f").append(memo).append(" = ").append(from == null ? "null" : value(from, n.tag)).append(";\n")
                            .append(inner).append("final Integer l").append(memo).append(" = ").append(limit == null ? "null" : value(limit, n.tag)).append(";\n")
                            .append(inner).append("JXElement[] w").append(memo).append(" = ").append(memo).append(".get(c, s").append(memo)
                            .append(", f").append(memo).append(", l").append(memo).append(");\n")
                            .append(inner).append("if (w").append(memo).append(" == null) {\n")
                            .append(inner).append("    final java.util.List<JXElement> ").append(into).append(" = new java.util.ArrayList<JXElement>();\n");
                    inner += "    ";
                    items = "s" + memo + ", f" + memo + ", l" + memo;
                }
                Local local = new Local(n.var, n.var + "_" + (nextVariable++), type);
                out.append(inner).append("for (").append(type).append(' ').append(local.javaName)
                        .append(" : JxmlRuntime.<").append(type).append(">items(").append(items).append(")) { // ")
                        .append(where(n.source, n.line)).append('\n');
                scope.add(local);
                JxmlTemplate.Prop keyProp = n.prop("key");
                emit(n.children, keyProp == null ? null : reference(keyProp), into, out, inner + "    ");
                scope.remove(scope.size() - 1);
                out.append(inner).append("}\n");
                if (n.memo != null) {
                    String memo = into.substring(1);
                    String close = inner.substring(4);
                    out.append(close).append("    w").append(memo).append(" = ").append(memo).append(".put(c, s").append(memo).append(", f")
                            .append(memo).append(", l").append(memo).append(", ").append(into).append(".toArray(new JXElement[0]));\n")
                            .append(close).append("}\n")
                            .append(close).append("java.util.Collections.addAll(").append(list).append(", w").append(memo).append(");\n")
                            .append(indent).append("}\n");
                }
            } else if ("If".equals(n.tag.name)) {
                out.append(indent).append("if (").append(value(n.prop("test"), n.tag)).append(".booleanValue()) { // ")
                        .append(where(n.source, n.line)).append('\n');
                emit(n.children, null, list, out, indent + "    ");
                if (n.otherwise.isEmpty()) {
                    out.append(indent).append("}\n");
                } else {
                    out.append(indent).append("} else {\n");
                    emit(n.otherwise, null, list, out, indent + "    ");
                    out.append(indent).append("}\n");
                }
            } else {
                out.append(indent).append(list).append(".add(").append(note(n, element(n, key, false, indent + "        "))).append(");\n");
            }
        }
    }

    /** Declarations and arguments that hand the loop variables in scope to a method of their own. */
    private String scopeParameters() {
        StringBuilder out = new StringBuilder();
        for (Local local : scope) {
            out.append(", final ").append(local.javaType).append(' ').append(local.javaName);
        }
        return out.toString();
    }

    private String scopeArguments() {
        StringBuilder out = new StringBuilder();
        for (Local local : scope) {
            out.append(", ").append(local.javaName);
        }
        return out.toString();
    }

    /** {@code listed}: the children are in the list {@code k}, which an enclosing statement block filled. */
    private String inline(JxmlTemplate.Element e, String key, boolean constantContext, String indent, boolean listed) {
        if (JxmlTemplate.isSlot(e.tag)) {
            return value(e.props.get(0), e.tag) + " /* " + where(e.source, e.line) + " */";
        }
        boolean tabs = e.tag.children == JxmlSchema.Children.TABS;
        int expected = e.defaults.size() + e.props.size() + (key == null ? 0 : 1);
        // The props in the order the interpreter's builder keeps them: a default first, replaced in its place by a prop
        // of the same name. Names are then distinct, so (but for tabs, which finish a builder) one JXProps.of call
        // makes them: its argument array is the props, no builder is made.
        Map<String, String[]> pairs = new LinkedHashMap<String, String[]>();
        for (Map.Entry<String, Object> d : e.defaults.entrySet()) {
            pairs.put(d.getKey(), new String[] {literal(d.getValue()), null});
        }
        String selected = "JxmlValues.asInt(Integer.valueOf(0))";
        for (JxmlTemplate.Prop p : e.props) {
            String value = value(p, e.tag);
            if (tabs && "selected".equals(p.attr.prop)) {
                selected = value;
                continue;
            }
            pairs.put(p.attr.prop, new String[] {value, where(p.source, p.line)});
        }
        if (key != null) {
            pairs.put("key", new String[] {key, null});
        }
        StringBuilder props = new StringBuilder();
        if (tabs) {
            props.append("JXProps.builder(").append(expected).append(')');
            for (Map.Entry<String, String[]> pair : pairs.entrySet()) {
                props.append('\n').append(indent).append(".set(").append(quote(pair.getKey())).append(", ")
                        .append(pair.getValue()[0]).append(')');
                if (pair.getValue()[1] != null) {
                    props.append(" // ").append(pair.getValue()[1]);
                }
            }
        } else if (pairs.isEmpty()) {
            props.append("JXProps.empty()");
        } else {
            props.append("JXProps.of(");
            int left = pairs.size();
            for (Map.Entry<String, String[]> pair : pairs.entrySet()) {
                props.append('\n').append(indent).append(quote(pair.getKey())).append(", ").append(pair.getValue()[0]);
                if (--left > 0) {
                    props.append(',');
                }
                if (pair.getValue()[1] != null) {
                    props.append(" // ").append(pair.getValue()[1]);
                }
            }
            props.append('\n').append(indent).append(')');
        }
        List<String> children = new ArrayList<String>();
        if (!listed) {
            for (int i = 0; i < e.children.size(); i++) {
                children.add(note(e.children.get(i), element(e.children.get(i), tabs ? quote("tab" + i) : null, constantContext, indent)));
            }
        }
        if (tabs) {
            String titles = null;
            if (e.tabTitles().isEmpty()) {
                titles = literal(e.titles);
            } else { // a title that is a %{translation} or a ${binding} is read each time the screen is built
                List<String> each = new ArrayList<String>();
                for (int i = 0; i < e.titles.length; i++) {
                    each.add(e.titleProps[i] == null ? quote(e.titles[i]) : value(e.titleProps[i], e.tag) + " /* "
                            + where(e.titleProps[i].source, e.titleProps[i].line) + " */");
                }
                titles = "new String[] {" + String.join(", ", each) + "}";
            }
            return "JxmlRuntime.tabs(" + props + "\n" + indent + ", " + selected + ", " + titles + ", "
                    + literal(e.closable) + ",\n" + indent + "new JXElement[] {" + String.join(", ", children) + "})";
        }
        String built = props.toString();
        if (e.tag.finish) {
            built = "JxmlRuntime.finish(" + quote(e.tag.name) + ", " + built + ")";
        }
        StringBuilder out = e.tag.tree
                ? new StringBuilder("JxmlRuntime.tree(").append(quote(e.tag.name)).append(", ").append(built)
                : new StringBuilder("JXElement.of(").append(quote(e.tag.nativeType)).append(", ").append(built);
        if (listed) {
            out.append(",\n").append(indent).append("k.toArray(new JXElement[0])");
        }
        for (String child : children) {
            out.append(",\n").append(indent).append(child);
        }
        return out.append(')').toString();
    }

    // ---- the live plan ---------------------------------------------------------------------------------------------

    /**
     * An element a live screen builds again, as a whole, on every refresh, instead of binding its props: a tab pane, a tree,
     * a virtual list ({@code ListView}, {@code TableView}), a radio button or toggle in a group. The runtime derives their
     * props and rows from the bound ones, so a changed binding is not a change of one prop in the node.
     */
    private boolean opaque(JxmlTemplate.Element e) {
        if (e.tag.control || JxmlTemplate.isSlot(e.tag) || e.constant()) {
            return false;
        }
        if (e.tag.children == JxmlSchema.Children.TABS || e.tag.tree) {
            return true;
        }
        if (e.tag.finish) {
            if ("RadioButton".equals(e.tag.name) || "ToggleButton".equals(e.tag.name)) {
                for (JxmlTemplate.Prop p : e.props) {
                    if ("group".equals(p.attr.prop)) {
                        return true;
                    }
                }
                return false; // no group: finish hands the props back as they are
            }
            return !virtualList(e);
        }
        return false;
    }

    /**
     * A {@code ListView} or {@code TableView} whose rows a live session keeps live: one {@code <For>} (not memoized) whose body
     * is one element that is made of plain elements only (bindings and static children: no loop, condition, slot, element-valued
     * prop, tab pane, tree, list or group button below it). The session then keeps a record of each row, and a change of the
     * window (the first row) or of the items keeps the rows that stay, with their nodes, and builds the rows that come in.
     */
    private boolean virtualList(JxmlTemplate.Element e) {
        if (!e.tag.virtual || e.tag.control || e.constant() || e.children.size() != 1) {
            return false;
        }
        JxmlTemplate.Element loop = e.children.get(0);
        if (!"For".equals(loop.tag.name) || loop.memo != null || loop.children.size() != 1 || loop.prop("items") == null) {
            return false;
        }
        return plainRow(loop.children.get(0));
    }

    private boolean plainRow(JxmlTemplate.Element e) {
        if (e.tag.control || JxmlTemplate.isSlot(e.tag) || e.tag.virtual || e.tag.tree || e.tag.finish
                || e.tag.children == JxmlSchema.Children.TABS || !e.otherwise.isEmpty()) {
            return false;
        }
        for (JxmlTemplate.Prop p : e.props) {
            if (p.attr.type == AttrType.ELEMENT) {
                return false;
            }
        }
        for (JxmlTemplate.Element child : e.children) {
            if (!plainRow(child)) {
                return false;
            }
        }
        return true;
    }

    private void findRowRoots(JxmlTemplate.Element e) {
        if (virtualList(e)) {
            rowRoots.add(e.children.get(0).children.get(0));
        }
        for (JxmlTemplate.Element child : e.children) {
            findRowRoots(child);
        }
        for (JxmlTemplate.Element child : e.otherwise) {
            findRowRoots(child);
        }
    }

    /** Remembers the expression that builds an opaque element or a live row, with the loop variables in scope, for its rebuilder. */
    private String note(JxmlTemplate.Element e, String expression) {
        if (opaque(e)) {
            opaqueBuilt.put(e, new Object[] {expression, new ArrayList<Local>(scope)});
        }
        if (rowRoots.contains(e)) {
            rowBuilt.put(e, new Object[] {expression, new ArrayList<Local>(scope)});
        }
        return expression;
    }

    /** The class with the plan of the screen, and the method that makes the session. */
    private String livePlan() {
        List<String> root = planList(java.util.Collections.singletonList(template.root), null);
        StringBuilder out = new StringBuilder();
        out.append("\n    @Override\n")
                .append("    public JxmlLive<").append(controllerType).append("> live(final ").append(controllerType).append(" c) {\n")
                .append("        return JxmlLive.of(this, Live.PLAN, c").append(liveLocale ? ", " + JxmlCatalog.STRINGS + "::locale" : "").append(");\n")
                .append("    }\n")
                .append("\n    /** Loaded when a screen is first made live: a screen that is only built never reads it. */\n")
                .append("    private static final class Live {\n");
        for (String getter : liveGetters.values()) {
            out.append(getter);
        }
        out.append(liveMembers);
        out.append("        static final JxmlLive.Plan PLAN = ").append(root.get(0)).append(";\n")
                .append("    }\n");
        return out.toString();
    }

    private List<String> planList(List<JxmlTemplate.Element> nodes, JxmlTemplate.Prop keyProp) {
        List<String> out = new ArrayList<String>();
        for (JxmlTemplate.Element n : nodes) {
            if ("For".equals(n.tag.name)) {
                out.add(planEach(n));
            } else if ("If".equals(n.tag.name)) {
                String test = getter(value(n.prop("test"), n.tag), scope);
                out.add(planMethod("JxmlLive.when(" + test + ", " + array(planList(n.children, null)) + ", "
                        + array(planList(n.otherwise, null)) + ")"));
            } else {
                out.add(planElement(n, keyProp));
            }
        }
        return out;
    }

    private static String array(List<String> plans) {
        return "new JxmlLive.Plan[] {" + String.join(", ", plans) + "}";
    }

    private String planEach(JxmlTemplate.Element n) {
        String type = n.type == null ? "Object" : n.type;
        String items = getter(reference(n.prop("items")), scope);
        JxmlTemplate.Prop from = n.prop("from");
        JxmlTemplate.Prop limit = n.prop("limit");
        String fromGetter = from == null ? "null" : getter(value(from, n.tag), scope);
        String limitGetter = limit == null ? "null" : getter(value(limit, n.tag), scope);
        Local local = new Local(n.var, n.var + "_" + (nextVariable++), type);
        scope.add(local);
        if (n.children.size() == 1 && rowBuilt.containsKey(n.children.get(0))) { // the rows of a virtual list: kept by key
            JxmlTemplate.Prop keyProp = n.prop("key");
            Object[] built = rowBuilt.get(n.children.get(0));
            @SuppressWarnings("unchecked")
            List<Local> own = (List<Local>) built[1];
            String key = keyProp == null ? "null" : getter(reference(keyProp), scope);
            List<String> row = planList(n.children, null);
            scope.remove(scope.size() - 1);
            return planMethod("JxmlLive.rows(" + items + ", " + fromGetter + ", " + limitGetter + ", " + key + ", "
                    + captures(n.children, n.var) + ", " + getter((String) built[0], own) + ", " + array(row) + ")");
        }
        List<String> body = planList(n.children, n.prop("key"));
        scope.remove(scope.size() - 1);
        return planMethod("JxmlLive.each(" + items + ", " + fromGetter + ", " + limitGetter + ", " + (n.memo != null) + ", "
                + captures(n.children, n.var) + ", " + array(body) + ")");
    }

    private String planElement(JxmlTemplate.Element n, JxmlTemplate.Prop keyProp) {
        if (JxmlTemplate.isSlot(n.tag)) {
            return planMethod("JxmlLive.hole(" + getter(value(n.props.get(0), n.tag), scope) + ")");
        }
        if (opaque(n)) {
            Object[] built = opaqueBuilt.get(n);
            @SuppressWarnings("unchecked")
            List<Local> own = (List<Local>) built[1];
            String key = keyProp == null ? "null" : getter(reference(keyProp), scope);
            return planMethod("JxmlLive.opaque(" + getter((String) built[0], own) + ", " + key + ")");
        }
        if (n.constant() && keyProp == null) {
            return "JxmlLive.skip()";
        }
        // the props as the build puts them: the last attribute of a name wins, and a key replaces a prop of that name
        Map<String, JxmlTemplate.Prop> byName = new LinkedHashMap<String, JxmlTemplate.Prop>();
        for (JxmlTemplate.Prop p : n.props) {
            byName.put(p.attr.prop, p);
        }
        if (keyProp != null) {
            byName.remove("key");
        }
        List<String> binds = new ArrayList<String>();
        boolean virtualList = virtualList(n);
        for (JxmlTemplate.Prop p : byName.values()) {
            if (virtualList && "first".equals(p.attr.prop)) {
                continue; // read as finish derives it, below
            }
            if (p.kind == JxmlTemplate.Kind.TRANSLATION) {
                binds.add(translatedBind(p, n.tag));
            } else if (p.kind == JxmlTemplate.Kind.BINDING) {
                boolean guard = p.attr.type == AttrType.ELEMENT;
                binds.add("JxmlLive." + (guard ? "guard" : "bind") + "(" + quote(p.attr.prop) + ", " + getter(value(p, n.tag), scope) + ")");
            }
        }
        if (keyProp != null) {
            binds.add("JxmlLive.guard(\"key\", " + getter(reference(keyProp), scope) + ")");
        }
        if (virtualList) { // what JxmlRuntime.finish makes of itemCount, first and the row height
            String count = propValue(n, "itemCount");
            String first = propValue(n, "first");
            String height = propValue(n, "ListView".equals(n.tag.name) ? "cellHeight" : "rowHeight");
            binds.add("JxmlLive.bind(\"first\", " + getter("JxmlRuntime.virtualFirst(" + count + ", " + first + ")", scope) + ")");
            binds.add("JxmlLive.bind(\"scrollY\", " + getter("JxmlRuntime.virtualScroll(" + count + ", " + first + ", " + height + ")", scope) + ")");
        }
        List<String> below = planList(n.children, null);
        boolean dynamic = false;
        for (String plan : below) {
            dynamic |= !"JxmlLive.skip()".equals(plan);
        }
        if (binds.isEmpty() && !dynamic) {
            return "JxmlLive.skip()";
        }
        return planMethod("JxmlLive.elem(new JxmlLive.Bind[] {" + String.join(", ", binds) + "}, " + (dynamic ? array(below) : "null") + ")");
    }

    /** The expression of the prop {@code name} of {@code e} as the build writes it: its attribute, or the tag's default. */
    private String propValue(JxmlTemplate.Element e, String name) {
        for (JxmlTemplate.Prop p : e.props) {
            if (name.equals(p.attr.prop)) {
                return value(p, e.tag);
            }
        }
        return literal(e.defaults.get(name));
    }

    /**
     * A translated prop: re-read when the locale changes and when a bound argument of one of its messages does. An argument
     * that can change without being another object (a date) makes it re-read always.
     */
    private String translatedBind(JxmlTemplate.Prop p, JxmlSchema.Tag tag) {
        liveLocale = true;
        List<String> deps = new ArrayList<String>();
        boolean always = false;
        for (JxmlTemplate.Translation t : p.translations) {
            if (t.key == null) {
                continue;
            }
            for (int i = 0; i < t.args.size(); i++) {
                JxmlTemplate.Arg a = t.args.get(i);
                if (a.binding != null) {
                    always |= "DateTime".equals(t.signature.types.get(i));
                    deps.add(getter(reference(a.binding), scope));
                }
            }
        }
        return "JxmlLive.translated(" + quote(p.attr.prop) + ", " + getter(value(p, tag), scope) + ", "
                + (always ? "null" : "new JxmlLive.Getter<?>[] {" + String.join(", ", deps) + "}") + ")";
    }

    private String planMethod(String expression) {
        String name = "p" + (nextPlan++);
        liveMembers.append("        private static JxmlLive.Plan ").append(name).append("() {\n")
                .append("            return ").append(expression).append(";\n        }\n");
        return name + "()";
    }

    /** A static getter of the plan: the loop variables in scope are read from the iteration's scope object. */
    private String getter(String expression, List<Local> locals) {
        StringBuilder id = new StringBuilder(expression);
        for (Local local : locals) {
            id.append('\u0001').append(local.javaName);
        }
        String key = id.toString();
        String name = liveGetterNames.get(key);
        if (name != null) {
            return name;
        }
        name = "G" + liveGetters.size();
        liveGetterNames.put(key, name);
        StringBuilder text = new StringBuilder("        static final JxmlLive.Getter<").append(controllerType).append("> ").append(name).append(" = (c, s) -> ");
        if (locals.isEmpty()) {
            text.append(expression).append(";\n");
        } else {
            text.append("{\n");
            for (int i = 0; i < locals.size(); i++) {
                Local local = locals.get(i);
                StringBuilder from = new StringBuilder("s");
                for (int up = locals.size() - 1 - i; up > 0; up--) {
                    from.append(".parent");
                }
                text.append("            final ").append(local.javaType).append(' ').append(local.javaName).append(" = (")
                        .append(local.javaType).append(") ").append(from).append(".item;\n");
            }
            text.append("            return ").append(expression).append(";\n        };\n");
        }
        liveGetters.put(key, text.toString());
        return name;
    }

    /** Whether a handler below holds the loop variable {@code var} (rather than only the controller): see {@code JxmlLive}. */
    private boolean captures(List<JxmlTemplate.Element> nodes, String var) {
        for (JxmlTemplate.Element n : nodes) {
            if (opaque(n)) {
                continue; // rebuilt on every refresh: its handlers are made again with the current items
            }
            for (JxmlTemplate.Prop p : n.props) {
                if (p.kind == JxmlTemplate.Kind.HANDLER || p.kind == JxmlTemplate.Kind.CHANGE) {
                    for (String arg : p.args) {
                        if (var.equals(arg)) {
                            return true;
                        }
                    }
                } else if (p.kind == JxmlTemplate.Kind.SETTER && var.equals(p.root)) {
                    return true;
                }
            }
            if (captures(n.children, var) || captures(n.otherwise, var)) {
                return true;
            }
        }
        return false;
    }

    /** The getters of a binding, called from its loop variable or from the controller {@code c}. */
    private String reference(JxmlTemplate.Prop p) {
        String start = "c";
        if (p.root != null) {
            for (Local local : scope) {
                if (local.name.equals(p.root)) {
                    start = local.javaName; // the last one declared: it shadows the ones before
                }
            }
        }
        StringBuilder call = new StringBuilder(start);
        for (String segment : p.path) {
            call.append('.').append(JxmlTemplate.getter(segment)).append("()");
        }
        return call.toString();
    }

    private String value(JxmlTemplate.Prop p, JxmlSchema.Tag tag) {
        switch (p.kind) {
            case LITERAL:
                return literal(p.literal);
            case HANDLER:
                if (p.args.length == 0) {
                    return "(Runnable) c::" + p.method;
                }
                return "(Runnable) () -> c." + p.method + "(" + String.join(", ", arguments(p)) + ")";
            case CHANGE:
                List<String> args = arguments(p);
                args.add("v");
                return consumer(p.attr.valueType) + " v -> c." + p.method + "(" + String.join(", ", args) + ")";
            case SETTER:
                return consumer(p.attr.valueType) + " v -> " + reference(p) + "." + p.method + "(v)";
            case TRANSLATION:
                List<String> texts = new ArrayList<String>();
                for (JxmlTemplate.Translation t : p.translations) {
                    texts.add(translation(t));
                }
                return p.attr.type == AttrType.STRINGS ? "new String[] {" + String.join(", ", texts) + "}" : texts.get(0);
            case BINDING:
                String call = reference(p);
                switch (p.attr.type) {
                    case STRING:
                        return "JxmlValues.asString(" + call + ")";
                    case DOUBLE:
                        return "JxmlValues.asDouble(" + call + ")";
                    case INT:
                        return "JxmlValues.asInt(" + call + ")";
                    case BOOLEAN:
                        return p.negate ? "Boolean.valueOf(!JxmlValues.asBoolean(" + call + ").booleanValue())"
                                : "JxmlValues.asBoolean(" + call + ")";
                    case STRINGS:
                        return "JxmlValues.asStrings(" + call + ")";
                    case ELEMENT:
                        return "JxmlValues.asElement(" + call + ")";
                    case OBJECT:
                        return call;
                    default:
                        return "JxmlValues.bound(com.jxparallel.forge.AttrType." + p.attr.type.name() + ", " + call + ")";
                }
            default:
                throw new IllegalStateException();
        }
    }

    /** A call of the generated {@code Strings} class (same package), checked by javac, or a plain text of a list. */
    private String translation(JxmlTemplate.Translation t) {
        if (t.key == null) {
            return quote(t.literal);
        }
        StringBuilder call = new StringBuilder(JxmlCatalog.STRINGS).append('.').append(t.signature.method).append('(');
        for (int i = 0; i < t.args.size(); i++) {
            JxmlTemplate.Arg a = t.args.get(i);
            String type = t.signature.types.get(i);
            String arg;
            if (a.binding == null) {
                arg = a.literal instanceof String ? quote((String) a.literal) : a.literal instanceof Integer
                        ? a.literal.toString() : number((Double) a.literal);
            } else {
                String ref = reference(a.binding);
                switch (type) {
                    case "int":
                        arg = "JxmlValues.asInt(" + ref + ").intValue()";
                        break;
                    case "double":
                    case "num":
                        arg = "JxmlValues.asDouble(" + ref + ").doubleValue()";
                        break;
                    case "DateTime":
                        arg = "(java.util.Date) " + ref;
                        break;
                    default:
                        arg = "JxmlValues.asString(" + ref + ")";
                }
            }
            call.append(i == 0 ? "" : ", ").append(arg);
        }
        return call.append(')').toString();
    }

    /** The loop variables a handler passes, as the Java variables that hold them. */
    private List<String> arguments(JxmlTemplate.Prop p) {
        List<String> out = new ArrayList<String>();
        for (String arg : p.args) {
            String name = null;
            for (Local local : scope) {
                if (local.name.equals(arg)) {
                    name = local.javaName;
                }
            }
            out.add(name);
        }
        return out;
    }

    /** The cast that gives a lambda the type of the value its input passes. */
    private static String consumer(AttrType valueType) {
        String type = valueType == AttrType.BOOLEAN ? "Boolean" : valueType == AttrType.DOUBLE ? "Double" : valueType == AttrType.INT ? "Integer" : valueType == AttrType.DOUBLES ? "double[]" : "String";
        return "(java.util.function.Consumer<" + type + ">)";
    }

    /** Strings and booleans inline; everything else a static constant, built once. */
    private String literal(Object value) {
        if (value instanceof String) {
            return quote((String) value);
        }
        if (value instanceof Boolean) {
            return ((Boolean) value) ? "Boolean.TRUE" : "Boolean.FALSE";
        }
        String name = constants.get(value);
        if (name != null) {
            return name;
        }
        String type;
        String init;
        if (value instanceof Double) {
            type = "Double";
            init = "Double.valueOf(" + number((Double) value) + ")";
        } else if (value instanceof Integer) {
            type = "Integer";
            init = "Integer.valueOf(" + value + ")";
        } else if (value instanceof double[]) {
            type = "double[]";
            StringBuilder s = new StringBuilder("{");
            for (double d : (double[]) value) {
                s.append(s.length() > 1 ? ", " : "").append(number(d));
            }
            init = s.append('}').toString();
        } else if (value instanceof boolean[]) {
            type = "boolean[]";
            StringBuilder s = new StringBuilder("{");
            for (boolean b : (boolean[]) value) {
                s.append(s.length() > 1 ? ", " : "").append(b);
            }
            init = s.append('}').toString();
        } else if (value instanceof String[]) {
            type = "String[]";
            StringBuilder s = new StringBuilder("{");
            for (String item : (String[]) value) {
                s.append(s.length() > 1 ? ", " : "").append(quote(item));
            }
            init = s.append('}').toString();
        } else {
            throw new IllegalStateException("no literal for " + value.getClass());
        }
        name = "L" + constants.size();
        constants.put(value, name);
        fields.append("    private static final ").append(type).append(' ').append(name).append(" = ")
                .append(init).append(";\n");
        return name;
    }

    private static String number(double d) {
        if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
            return String.format(Locale.ROOT, "%.1f", d);
        }
        return Double.toString(d);
    }

    private static String where(String file, int line) {
        // (the file of the element, which is not the template's own for an included part)
        int slash = Math.max(file.lastIndexOf('/'), file.lastIndexOf('\\'));
        return file.substring(slash + 1) + ":" + line;
    }

    static String quote(String s) {
        StringBuilder out = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (ch < 0x20 || ch > 0x7e) {
                        out.append(String.format("\\u%04x", (int) ch));
                    } else {
                        out.append(ch);
                    }
            }
        }
        return out.append('"').toString();
    }
}
