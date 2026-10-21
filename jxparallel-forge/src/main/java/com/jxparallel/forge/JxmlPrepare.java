package com.jxparallel.forge;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The steps taken on the parsed tree of a file before its tags are resolved, so the interpreter and the compiler
 * (which both read the file through {@link JxmlTemplate}) see the same tree:
 * <ol>
 * <li>{@code <Include src="...">} is replaced by the root of the file it names (read the same way, its own
 * includes replaced in turn); a cycle is an error;</li>
 * <li>the {@code <Style name="..." attr="...">} children of the root of each file are collected and removed;
 * a style used by {@code style="name"} on any element is merged into that element's attributes, where an
 * attribute written on the element wins over the style.</li>
 * </ol>
 * Styles are merged before composites expand, so a style also beats the defaults of a composite
 * ({@code <Card style="plain"/>}).
 */
final class JxmlPrepare {
    private JxmlPrepare() {
    }

    /** Prepares {@code root}; returns its {@code theme} (light or dark), or null when it writes none. */
    static String run(JxmlParser.Node root, String controller, JxmlLoader loader) {
        String theme = root.attrs.get("theme");
        if (theme != null && !JxmlThemes.THEMES.contains(theme)) {
            throw new JxmlException(root.source, root.line, "theme=\"" + theme + "\" is not one of " + JxmlThemes.THEMES);
        }
        if ("Include".equals(root.tag)) {
            throw new JxmlException(root.source, root.line, "<Include> cannot be the root element");
        }
        Map<String, JxmlParser.Node> styles = new LinkedHashMap<String, JxmlParser.Node>();
        collectStyles(root, styles);
        List<String> stack = new ArrayList<String>();
        stack.add(root.source);
        includes(root, controller, loader, styles, stack);
        applyStyles(root, styles);
        return theme;
    }

    // ---- includes ----------------------------------------------------------------------------

    private static void includes(JxmlParser.Node node, String controller, JxmlLoader loader,
                                 Map<String, JxmlParser.Node> styles, List<String> stack) {
        for (int i = 0; i < node.children.size(); i++) {
            JxmlParser.Node child = node.children.get(i);
            if ("Include".equals(child.tag)) {
                JxmlParser.Node part = load(child, controller, loader, styles, stack);
                node.children.set(i, part);
            } else {
                includes(child, controller, loader, styles, stack);
            }
        }
    }

    private static JxmlParser.Node load(JxmlParser.Node include, String controller, JxmlLoader loader,
                                        Map<String, JxmlParser.Node> styles, List<String> stack) {
        String src = include.attrs.get("src");
        if (src == null || src.trim().isEmpty()) {
            throw new JxmlException(include.source, include.line, "<Include> needs src=\"parts/name.jxml\", the file to include");
        }
        if (src.contains("${") || src.contains("@{")) {
            throw new JxmlException(include.source, include.line, "<Include> src cannot be bound: the file is read when this one is");
        }
        if (!include.children.isEmpty()) {
            throw new JxmlException(include.source, include.line, "<Include> takes no children");
        }
        if (include.attrs.containsKey("controller") || include.attrs.containsKey("theme") || include.attrs.containsKey("style")) {
            String name = include.attrs.containsKey("controller") ? "controller" : include.attrs.containsKey("theme") ? "theme" : "style";
            throw new JxmlException(include.source, include.line, "<Include> has no attribute " + name);
        }
        if (loader == null) {
            throw new JxmlException(include.source, include.line, "<Include> needs to find " + src + ", but this file was read from a "
                    + "stream; read it with JxmlTemplate.read(File), readResource(...) or give read(...) a JxmlLoader");
        }
        String name = JxmlLoader.join(include.source, src.trim());
        if (name == null) {
            throw new JxmlException(include.source, include.line, "<Include> src=\"" + src + "\" leaves the folder the files are read from");
        }
        if (stack.contains(name)) {
            List<String> cycle = new ArrayList<String>(stack.subList(stack.indexOf(name), stack.size()));
            cycle.add(name);
            throw new JxmlException(include.source, include.line, "include cycle: " + String.join(" -> ", cycle));
        }
        JxmlParser.Node part;
        try {
            InputStream in = loader.open(name);
            try {
                part = JxmlParser.parse(in, name);
            } finally {
                in.close();
            }
        } catch (IOException e) {
            throw new JxmlException(include.source, include.line, "<Include> cannot read " + name + " (src=\"" + src + "\"): "
                    + e.getMessage(), e);
        }
        if ("Include".equals(part.tag)) {
            throw new JxmlException(part.source, part.line, "<Include> cannot be the root element");
        }
        String partController = part.attrs.remove("controller");
        if (partController != null && controller != null && !partController.equals(controller)) {
            throw new JxmlException(include.source, include.line, name + " declares controller " + partController
                    + ", but it is included in a file whose controller is " + controller
                    + "; an included file's bindings resolve against the controller of the file that includes it");
        }
        part.attrs.remove("theme"); // the theme is the including file's
        collectStyles(part, styles);
        for (Map.Entry<String, String> entry : include.attrs.entrySet()) {
            if (!"src".equals(entry.getKey())) {
                part.attrs.put(entry.getKey(), entry.getValue()); // placement written on the include: slot, hgrow, row...
            }
        }
        stack.add(name);
        includes(part, controller, loader, styles, stack);
        stack.remove(stack.size() - 1);
        return part;
    }

    // ---- styles ------------------------------------------------------------------------------

    /** Attribute names a style may set: any attribute of any tag, but no handler and none of the structural ones. */
    private static Set<String> styleable() {
        Set<String> names = new LinkedHashSet<String>();
        List<JxmlSchema.Attr> all = new ArrayList<JxmlSchema.Attr>(JxmlSchema.LAYOUT.values());
        for (JxmlSchema.Tag tag : JxmlSchema.tags().values()) {
            if (!tag.control) {
                all.addAll(tag.attrs.values());
            }
        }
        for (JxmlSchema.Attr attr : all) {
            if (attr.type != AttrType.HANDLER && attr.type != AttrType.CHANGE) {
                names.add(attr.name);
            }
        }
        names.removeAll(java.util.Arrays.asList("theme", "key", "content", "items", "var", "type", "from", "limit", "test", "pixels"));
        return names;
    }

    /** Removes the {@code <Style>} children of a file's root and adds them to {@code styles} (the first definition of a name wins). */
    private static void collectStyles(JxmlParser.Node root, Map<String, JxmlParser.Node> styles) {
        Set<String> known = styleable();
        Set<String> defined = new LinkedHashSet<String>();
        List<JxmlParser.Node> kept = new ArrayList<JxmlParser.Node>();
        for (JxmlParser.Node child : root.children) {
            if (!"Style".equals(child.tag)) {
                kept.add(child);
                continue;
            }
            String name = child.attrs.get("name");
            if (name == null || !JxmlTemplate.isIdentifier(name.replace('-', '_'))) {
                throw new JxmlException(child.source, child.line, "<Style> needs name=\"identifier\", the name used by style=\"...\"");
            }
            if (!child.children.isEmpty()) {
                throw new JxmlException(child.source, child.line, "<Style> takes no children");
            }
            for (Map.Entry<String, String> entry : child.attrs.entrySet()) {
                String attr = entry.getKey();
                if ("name".equals(attr)) {
                    continue;
                }
                if (!known.contains(attr)) {
                    throw new JxmlException(child.source, child.line, "<Style> cannot set " + attr
                            + " (handlers, bindings and structure are not style); it can set: " + known);
                }
                if (entry.getValue().contains("${") || entry.getValue().contains("@{")) {
                    throw new JxmlException(child.source, child.line, "<Style> " + attr + " cannot be bound: a style is resolved when "
                            + "the file is read");
                }
            }
            if (!defined.add(name)) {
                throw new JxmlException(child.source, child.line, "two <Style> named " + name + " in this file");
            }
            if (!styles.containsKey(name)) {
                styles.put(name, child);
            }
        }
        root.children.clear();
        root.children.addAll(kept);
    }

    private static void applyStyles(JxmlParser.Node node, Map<String, JxmlParser.Node> styles) {
        if ("Style".equals(node.tag)) {
            throw new JxmlException(node.source, node.line, "<Style> is only allowed directly inside the root element of a file");
        }
        String use = node.attrs.remove("style");
        if (use != null) {
            JxmlSchema.Tag tag = JxmlSchema.tag(node.tag);
            Map<String, String> merged = new LinkedHashMap<String, String>();
            for (String name : use.trim().split("\\s+")) {
                JxmlParser.Node style = styles.get(name);
                if (style == null) {
                    throw new JxmlException(node.source, node.line, "unknown style \"" + name + "\"; styles: " + styles.keySet());
                }
                for (Map.Entry<String, String> entry : style.attrs.entrySet()) {
                    // an attribute the tag does not have is not an error: one style serves tags of different kinds
                    if (!"name".equals(entry.getKey()) && tag != null && tag.attr(entry.getKey()) != null) {
                        merged.put(entry.getKey(), entry.getValue()); // a later style wins over an earlier one
                    }
                }
            }
            for (Map.Entry<String, String> entry : merged.entrySet()) {
                if (!node.attrs.containsKey(entry.getKey())) {
                    node.attrs.put(entry.getKey(), entry.getValue()); // written on the element: the element wins
                }
            }
        }
        for (JxmlParser.Node child : node.children) {
            applyStyles(child, styles);
        }
    }
}
