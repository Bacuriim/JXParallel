package com.jxparallel.ui.accessibility;

import java.util.ArrayList;
import java.util.List;

import com.jxparallel.ui.JXElement;

/**
 * Builds accessibility trees and writes them as text.
 *
 * <p>{@link #from(JXElement)} reads an element tree (roles and names only: no layout, no focus). The tree of a live window
 * comes from {@code JXNativeAccessibility.snapshot}, or from {@code JXWindow.getAccessibleRoot}.
 */
public final class JXAccessibilityTree {
    private JXAccessibilityTree() {}

    public static JXAccessibleNode from(JXElement element) {
        if (element == null) throw new IllegalArgumentException("Element cannot be null");
        List<JXAccessibleNode> children = new ArrayList<JXAccessibleNode>();
        for (JXElement child : element.getChildren()) children.add(from(child));
        return new JXAccessibleNode(roleFor(element.getType()),
                nameFor(element), children);
    }

    private static JXAccessibilityRole roleFor(String type) {
        if ("button".equals(type)) return JXAccessibilityRole.BUTTON;
        if ("toggle".equals(type)) return JXAccessibilityRole.TOGGLE_BUTTON;
        if ("input".equals(type) || "textarea".equals(type) || "password".equals(type)) {
            return JXAccessibilityRole.TEXT_FIELD;
        }
        if ("checkbox".equals(type)) return JXAccessibilityRole.CHECK_BOX;
        if ("#text".equals(type)) return JXAccessibilityRole.TEXT;
        if ("row".equals(type) || "column".equals(type) || "stack".equals(type)
                || "titled".equals(type) || "accordion".equals(type)) {
            return JXAccessibilityRole.GROUP;
        }
        return JXAccessibilityRole.UNKNOWN;
    }

    private static String nameFor(JXElement element) {
        String given = element.getProps().getString("accessibleName");
        if (given != null && !given.isEmpty()) return given;
        String label = element.getProps().getString("label");
        if (label != null) return label;
        String value = element.getProps().getString("value");
        return value == null ? element.getType() : value;
    }

    /** The tree as indented text with bounds: one line per node, two spaces per level. */
    public static String dump(JXAccessibleNode root) {
        return dump(root, true);
    }

    /**
     * The tree as indented text, for tests and for auditing a screen. A line reads
     * {@code ROLE "name" [STATES] value="..." range=min..max (now) desc="..." item=i/n row=r/n col=c/n level=n @x,y wxh},
     * leaving out what does not apply. Without {@code withBounds} the screen is described independent of layout and fonts.
     */
    public static String dump(JXAccessibleNode root, boolean withBounds) {
        StringBuilder out = new StringBuilder();
        write(out, root, 0, withBounds);
        return out.toString();
    }

    private static void write(StringBuilder out, JXAccessibleNode n, int depth, boolean withBounds) {
        for (int i = 0; i < depth; i++) out.append("  ");
        out.append(n.getRole()).append(" \"").append(n.getName().replace("\"", "'").replace("\n", " ")).append('"');
        if (!n.getStates().isEmpty()) {
            out.append(" [");
            boolean first = true;
            for (JXAccessibleState s : n.getStates()) {
                if (!first) out.append(',');
                out.append(s);
                first = false;
            }
            out.append(']');
        }
        if (!n.getValue().isEmpty()) out.append(" value=\"").append(n.getValue().replace("\"", "'").replace("\n", " ")).append('"');
        if (n.hasRange()) out.append(" range=").append(num(n.getMin())).append("..").append(num(n.getMax())).append(" (").append(num(n.getNow())).append(')');
        if (!n.getDescription().isEmpty()) out.append(" desc=\"").append(n.getDescription().replace("\"", "'")).append('"');
        if (n.getItemIndex() >= 0 || n.getItemCount() >= 0) out.append(" item=").append(n.getItemIndex()).append('/').append(n.getItemCount());
        if (n.getRowCount() >= 0 || n.getRowIndex() >= 0) out.append(" row=").append(n.getRowIndex()).append('/').append(n.getRowCount());
        if (n.getColumnCount() >= 0 || n.getColumnIndex() >= 0) out.append(" col=").append(n.getColumnIndex()).append('/').append(n.getColumnCount());
        if (n.getLevel() > 0) out.append(" level=").append(n.getLevel());
        if (withBounds) {
            int[] b = n.getBounds();
            out.append(" @").append(b[0]).append(',').append(b[1]).append(' ').append(b[2]).append('x').append(b[3]);
        }
        out.append('\n');
        for (JXAccessibleNode child : n.getChildren()) write(out, child, depth + 1, withBounds);
    }

    private static String num(double v) {
        return v == Math.rint(v) && Math.abs(v) < 1e15 ? String.valueOf((long) v) : String.valueOf(v);
    }
}
