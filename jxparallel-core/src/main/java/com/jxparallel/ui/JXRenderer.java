package com.jxparallel.ui;

public final class JXRenderer {
    private JXRenderer() {
    }

    public static JXNode mount(JXComponent component) {
        if (component == null) {
            throw new IllegalArgumentException("Component cannot be null");
        }
        return JXNode.mount(component.render());
    }

    public static void update(JXNode node, JXElement previous, JXComponent component) {
        if (node == null || component == null) {
            throw new IllegalArgumentException("Node and component cannot be null");
        }
        node.patch(previous, component.render());
    }
}
