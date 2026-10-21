package com.jxparallel.forge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Composite tags (the ones {@code JxmlSchema} marks {@code composite}), rewritten into the primitive
 * tags they stand for. This is the only place that knows what a composite means: the resolver calls
 * {@link #expand} on every node first, so the interpreter and the compiler only ever see primitives
 * and need no code of their own for a new composite. Rewritten nodes keep the line of the composite,
 * so an error in an expansion points at the line the author wrote.
 *
 * <p>The expansions are listed in {@code docs/jxforge.md}.
 */
final class JxmlComposites {
    private static final String ALWAYS = "always";

    private JxmlComposites() {
    }

    /** The primitive nodes a composite stands for; any other node is returned as it is. */
    static JxmlParser.Node expand(JxmlParser.Node node, String source) {
        JxmlSchema.Tag tag = JxmlSchema.tag(node.tag);
        if (tag == null || !tag.composite) {
            return node;
        }
        for (String name : node.attrs.keySet()) {
            if (!"controller".equals(name) && tag.attr(name) == null) {
                throw new JxmlException(source, node.line, "<" + tag.name + "> has no attribute " + name);
            }
        }
        if (tag.alias != null) {
            JxmlParser.Node out = node(tag.alias, node);
            out.attrs.putAll(node.attrs);
            for (Map.Entry<String, String> entry : tag.aliasAttrs.entrySet()) {
                if (!out.attrs.containsKey(entry.getKey())) {
                    out.attrs.put(entry.getKey(), entry.getValue());
                }
            }
            out.children.addAll(node.children);
            return out;
        }
        switch (tag.name) {
            case "Spacer":
                return spacer(node, source);
            case "Expanded":
                return grow(node, source, ALWAYS);
            case "Flexible":
                return grow(node, source, "sometimes");
            case "SizedBox":
                return sizedBox(node, source);
            case "ListTile":
                return listTile(node, source);
            case "AppBar":
                return appBar(node);
            case "NavigationBar":
                return navigationBar(node, source);
            case "Scaffold":
                return scaffold(node, source);
            default:
                throw new IllegalStateException("no expansion for <" + tag.name + ">");
        }
    }

    /** {@code <Spacer/>}: an empty pane that takes the space the siblings leave. */
    private static JxmlParser.Node spacer(JxmlParser.Node node, String source) {
        noChildren(node, source);
        JxmlParser.Node out = node("Pane", node);
        out.attrs.putAll(node.attrs);
        putIfAbsent(out, "hgrow", ALWAYS);
        putIfAbsent(out, "vgrow", ALWAYS);
        return out;
    }

    /** {@code <Expanded>} / {@code <Flexible>}: the child itself, with the grow priority set. */
    private static JxmlParser.Node grow(JxmlParser.Node node, String source, String priority) {
        if (node.children.size() != 1) {
            throw new JxmlException(source, node.line, "<" + node.tag + "> takes one child, the element that grows");
        }
        JxmlParser.Node child = expand(node.children.get(0), source);
        JxmlSchema.Tag childTag = JxmlSchema.tag(child.tag);
        if (childTag != null && (childTag.control || JxmlTemplate.isSlot(childTag))) {
            throw new JxmlException(source, child.line, "<" + node.tag + "> needs an element as its child, not <" + child.tag
                    + ">; wrap it in a <Column>");
        }
        JxmlParser.Node out = node(child.tag, child);
        out.attrs.putAll(child.attrs);
        for (Map.Entry<String, String> entry : node.attrs.entrySet()) {
            if (out.attrs.containsKey(entry.getKey()) && !"hgrow".equals(entry.getKey()) && !"vgrow".equals(entry.getKey())) {
                throw new JxmlException(source, node.line, "<" + node.tag + "> " + entry.getKey()
                        + " is also written on its child; write it once");
            }
            out.attrs.put(entry.getKey(), entry.getValue());
        }
        putIfAbsent(out, "hgrow", priority);
        putIfAbsent(out, "vgrow", priority);
        out.children.addAll(child.children);
        return out;
    }

    /** {@code <SizedBox width height>}: a stack that is exactly that size. */
    private static JxmlParser.Node sizedBox(JxmlParser.Node node, String source) {
        if (node.children.size() > 1) {
            throw new JxmlException(source, node.line, "<SizedBox> takes one child");
        }
        JxmlParser.Node out = node("Stack", node);
        for (Map.Entry<String, String> entry : node.attrs.entrySet()) {
            String key = entry.getKey();
            if ("width".equals(key)) {
                out.attrs.put("minWidth", entry.getValue());
                out.attrs.put("prefWidth", entry.getValue());
                out.attrs.put("maxWidth", entry.getValue());
            } else if ("height".equals(key)) {
                out.attrs.put("minHeight", entry.getValue());
                out.attrs.put("prefHeight", entry.getValue());
                out.attrs.put("maxHeight", entry.getValue());
            } else {
                out.attrs.put(key, entry.getValue());
            }
        }
        out.children.addAll(node.children);
        return out;
    }

    /**
     * {@code <ListTile title subtitle onAction>} with optional children {@code slot="leading"} and
     * {@code slot="trailing"}: a row of the leading element, a column of one or two labels that takes the
     * remaining width, and the trailing element. The labels do not pick the pointer, so a click on the
     * tile reaches its row, which carries {@code onAction}.
     */
    private static JxmlParser.Node listTile(JxmlParser.Node node, String source) {
        String title = node.attrs.get("title");
        if (title == null) {
            throw new JxmlException(source, node.line, "<ListTile> needs title=\"...\"");
        }
        JxmlParser.Node leading = null;
        JxmlParser.Node trailing = null;
        for (JxmlParser.Node child : node.children) {
            String slot = child.attrs.get("slot");
            boolean isLeading = "leading".equals(slot);
            if (!isLeading && !"trailing".equals(slot)) {
                throw new JxmlException(source, child.line, "<ListTile> children need slot=\"leading\" or slot=\"trailing\"");
            }
            JxmlSchema.Tag childTag = JxmlSchema.tag(child.tag);
            if (childTag != null && childTag.control) {
                throw new JxmlException(source, child.line, "the " + slot + " of a <ListTile> is one element, not <" + child.tag + ">");
            }
            if (isLeading ? leading != null : trailing != null) {
                throw new JxmlException(source, child.line, "<ListTile> has two children with slot=\"" + slot + "\"");
            }
            JxmlParser.Node copy = node(child.tag, child);
            copy.attrs.putAll(child.attrs);
            copy.attrs.remove("slot");
            copy.children.addAll(child.children);
            if (isLeading) {
                leading = copy;
            } else {
                trailing = copy;
            }
        }
        JxmlParser.Node row = node("Row", node);
        for (Map.Entry<String, String> entry : node.attrs.entrySet()) {
            String key = entry.getKey();
            if (!"title".equals(key) && !"subtitle".equals(key)) {
                row.attrs.put(key, entry.getValue());
            }
        }
        putIfAbsent(row, "gap", "12");
        putIfAbsent(row, "padding", "8 12");
        putIfAbsent(row, "alignment", "CENTER_LEFT");
        JxmlParser.Node texts = node("Column", node);
        texts.attrs.put("gap", "2");
        texts.attrs.put("alignment", "CENTER_LEFT");
        texts.attrs.put("hgrow", ALWAYS);
        texts.attrs.put("mouseTransparent", "true");
        JxmlParser.Node titleLabel = node("Label", node);
        titleLabel.attrs.put("text", title);
        titleLabel.attrs.put("mouseTransparent", "true");
        texts.children.add(titleLabel);
        String subtitle = node.attrs.get("subtitle");
        if (subtitle != null) {
            JxmlParser.Node subtitleLabel = node("Label", node);
            subtitleLabel.attrs.put("text", subtitle);
            subtitleLabel.attrs.put("fontSize", "11");
            subtitleLabel.attrs.put("textFill", "#666666");
            subtitleLabel.attrs.put("mouseTransparent", "true");
            texts.children.add(subtitleLabel);
        }
        if (leading != null) {
            row.children.add(leading);
        }
        row.children.add(texts);
        if (trailing != null) {
            row.children.add(trailing);
        }
        return row;
    }

    /** {@code <AppBar title>}: a row with the title in bold, taking the free width, then the children. */
    private static JxmlParser.Node appBar(JxmlParser.Node node) {
        JxmlParser.Node row = node("Row", node);
        for (Map.Entry<String, String> entry : node.attrs.entrySet()) {
            if (!"title".equals(entry.getKey())) {
                row.attrs.put(entry.getKey(), entry.getValue());
            }
        }
        putIfAbsent(row, "gap", "8");
        putIfAbsent(row, "padding", "8 12");
        putIfAbsent(row, "alignment", "CENTER_LEFT");
        putIfAbsent(row, "background", "#EDEDED");
        String title = node.attrs.get("title");
        if (title != null) {
            JxmlParser.Node label = node("Label", node);
            label.attrs.put("text", title);
            label.attrs.put("bold", "true");
            label.attrs.put("hgrow", ALWAYS);
            row.children.add(label);
        }
        row.children.addAll(node.children);
        return row;
    }

    /** {@code <NavigationBar>}: a row whose elements share the width equally. */
    private static JxmlParser.Node navigationBar(JxmlParser.Node node, String source) {
        JxmlParser.Node row = node("Row", node);
        row.attrs.putAll(node.attrs);
        putIfAbsent(row, "gap", "0");
        putIfAbsent(row, "padding", "4");
        putIfAbsent(row, "alignment", "CENTER");
        putIfAbsent(row, "background", "#EDEDED");
        for (JxmlParser.Node child : node.children) {
            JxmlParser.Node item = expand(child, source);
            JxmlSchema.Tag itemTag = JxmlSchema.tag(item.tag);
            if (itemTag != null && !itemTag.control && !JxmlTemplate.isSlot(itemTag)) {
                putIfAbsent(item, "hgrow", ALWAYS);
            }
            row.children.add(item);
        }
        return row;
    }

    /**
     * {@code <Scaffold>}: a border. An {@code AppBar} goes to the top, a {@code BottomAppBar} or
     * {@code BottomBar} to the bottom, and the one other child is the body, in the center; a child
     * that writes its own {@code slot} keeps it.
     */
    private static JxmlParser.Node scaffold(JxmlParser.Node node, String source) {
        JxmlParser.Node border = node("Border", node);
        border.attrs.putAll(node.attrs);
        List<String> used = new ArrayList<String>();
        for (JxmlParser.Node child : node.children) {
            JxmlSchema.Tag childTag = JxmlSchema.tag(child.tag);
            if (childTag != null && (childTag.control || JxmlTemplate.isSlot(childTag)) && !child.attrs.containsKey("slot")) {
                throw new JxmlException(source, child.line, "a <Scaffold> child is an element, not <" + child.tag
                        + ">; wrap it in a <Column>");
            }
            JxmlParser.Node item = expand(child, source);
            if (!item.attrs.containsKey("slot")) {
                String slot = "AppBar".equals(child.tag) ? "top"
                        : "BottomAppBar".equals(child.tag) || "BottomBar".equals(child.tag) ? "bottom" : "center";
                item.attrs.put("slot", slot);
            }
            String slot = item.attrs.get("slot");
            if (used.contains(slot)) {
                throw new JxmlException(source, child.line, "<Scaffold> has two children in slot " + slot
                        + (slot.equals("center") ? "; the body is one element" : ""));
            }
            used.add(slot);
            border.children.add(item);
        }
        return border;
    }

    private static void noChildren(JxmlParser.Node node, String source) {
        if (!node.children.isEmpty()) {
            throw new JxmlException(source, node.line, "<" + node.tag + "> takes no children");
        }
    }

    private static void putIfAbsent(JxmlParser.Node node, String attr, String value) {
        if (!node.attrs.containsKey(attr)) {
            node.attrs.put(attr, value);
        }
    }

    private static JxmlParser.Node node(String tag, JxmlParser.Node at) {
        return new JxmlParser.Node(tag, at.line);
    }
}
