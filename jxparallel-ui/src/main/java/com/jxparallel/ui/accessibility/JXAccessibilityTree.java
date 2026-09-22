package com.jxparallel.ui.accessibility;

import java.util.ArrayList;
import java.util.List;

import com.jxparallel.ui.JXElement;

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
        if ("button".equals(type) || "toggle".equals(type)) return JXAccessibilityRole.BUTTON;
        if ("input".equals(type) || "textarea".equals(type) || "password".equals(type)) {
            return JXAccessibilityRole.TEXT_FIELD;
        }
        if ("checkbox".equals(type)) return JXAccessibilityRole.CHECK_BOX;
        if ("#text".equals(type)) return JXAccessibilityRole.TEXT;
        if ("row".equals(type) || "column".equals(type) || "stack".equals(type)) {
            return JXAccessibilityRole.GROUP;
        }
        return JXAccessibilityRole.UNKNOWN;
    }

    private static String nameFor(JXElement element) {
        String label = element.getProps().getString("label");
        if (label != null) return label;
        String value = element.getProps().getString("value");
        return value == null ? element.getType() : value;
    }
}
