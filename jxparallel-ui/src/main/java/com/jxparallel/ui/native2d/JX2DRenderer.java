package com.jxparallel.ui.native2d;

import java.awt.Dimension;
import java.awt.Graphics2D;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXComponent;

public final class JX2DRenderer {
    private JX2DRenderer() {
    }

    public static JXNativeNode mount(JXElement element) {
        if (element == null) {
            throw new IllegalArgumentException("Element cannot be null");
        }
        return new JXNativeNode(element);
    }

    public static JXNativeNode mount(JXComponent component) {
        if (component == null) {
            throw new IllegalArgumentException("Component cannot be null");
        }
        return mount(component.render());
    }

    public static void layout(JXNativeNode node, int width, int height) {
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        node.layout(0, 0, width, height);
    }

    public static void paint(JXNativeNode node, Graphics2D graphics) {
        if (node == null || graphics == null) {
            throw new IllegalArgumentException("Node and graphics cannot be null");
        }
        node.paint(graphics);
    }

    public static Dimension preferredSize(JXNativeNode node) {
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        return node.preferredSize();
    }
}
