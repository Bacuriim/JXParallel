package com.jxparallel.ui.accessibility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class JXAccessibleNode {
    private final JXAccessibilityRole role;
    private final String name;
    private final List<JXAccessibleNode> children;

    public JXAccessibleNode(JXAccessibilityRole role, String name, List<JXAccessibleNode> children) {
        this.role = role == null ? JXAccessibilityRole.UNKNOWN : role;
        this.name = name == null ? "" : name;
        this.children = Collections.unmodifiableList(new ArrayList<JXAccessibleNode>(
                children == null ? Collections.<JXAccessibleNode>emptyList() : children));
    }

    public JXAccessibilityRole getRole() { return role; }
    public String getName() { return name; }
    public List<JXAccessibleNode> getChildren() { return children; }

    public List<JXAccessibleNode> flatten() {
        List<JXAccessibleNode> result = new ArrayList<JXAccessibleNode>();
        result.add(this);
        for (JXAccessibleNode child : children) result.addAll(child.flatten());
        return result;
    }
}
