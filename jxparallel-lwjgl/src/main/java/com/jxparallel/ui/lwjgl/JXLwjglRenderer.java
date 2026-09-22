package com.jxparallel.ui.lwjgl;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXComponent;
import com.jxparallel.ui.native2d.JXNativeNode;

import org.lwjgl.opengl.GL11;

public final class JXLwjglRenderer {
    private JXLwjglRenderer() {
    }

    public static JXNativeNode mount(JXElement element) {
        if (element == null) {
            throw new IllegalArgumentException("Element cannot be null");
        }
        return new JXNativeNodeAdapter(element).node();
    }

    public static JXNativeNode mount(JXComponent component) {
        if (component == null) {
            throw new IllegalArgumentException("Component cannot be null");
        }
        return mount(component.render());
    }

    public static void layout(JXNativeNode root, int width, int height) {
        requireNode(root);
        root.layoutForBackend(width, height);
    }

    public static void paint(JXNativeNode root, int width, int height) {
        requireNode(root);
        layout(root, width, height);
        GL11.glClearColor(0.97f, 0.97f, 0.97f, 1.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0, width, height, 0.0, -1.0, 1.0);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        paintNode(root);
    }

    private static void paintNode(JXNativeNode node) {
        java.awt.Rectangle bounds = node.getBounds();
        String type = node.getType();
        if ("button".equals(type) || "toggle".equals(type)) {
            rectangle(bounds, 0.18f, 0.42f, 0.87f);
        } else if ("input".equals(type) || "textarea".equals(type)
                || "password".equals(type) || "select".equals(type)) {
            rectangle(bounds, 1.0f, 1.0f, 1.0f);
            outline(bounds, 0.55f, 0.55f, 0.55f);
        } else if ("progress".equals(type)) {
            rectangle(bounds, 0.88f, 0.88f, 0.88f);
            java.awt.Rectangle progress = new java.awt.Rectangle(bounds);
            progress.width = (int) (bounds.width * propertyAsDouble(node, "progress", 0.0));
            rectangle(progress, 0.18f, 0.42f, 0.87f);
        } else if ("#text".equals(type)) {
            outline(bounds, 0.25f, 0.25f, 0.25f);
        }
        for (JXNativeNode child : node.getChildren()) {
            paintNode(child);
        }
    }

    private static void rectangle(java.awt.Rectangle bounds, float red, float green, float blue) {
        GL11.glColor3f(red, green, blue);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2i(bounds.x, bounds.y);
        GL11.glVertex2i(bounds.x + bounds.width, bounds.y);
        GL11.glVertex2i(bounds.x + bounds.width, bounds.y + bounds.height);
        GL11.glVertex2i(bounds.x, bounds.y + bounds.height);
        GL11.glEnd();
    }

    private static void outline(java.awt.Rectangle bounds, float red, float green, float blue) {
        GL11.glColor3f(red, green, blue);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2i(bounds.x, bounds.y);
        GL11.glVertex2i(bounds.x + bounds.width, bounds.y);
        GL11.glVertex2i(bounds.x + bounds.width, bounds.y + bounds.height);
        GL11.glVertex2i(bounds.x, bounds.y + bounds.height);
        GL11.glEnd();
    }

    private static double propertyAsDouble(JXNativeNode node, String name, double fallback) {
        Object value = node.getProperty(name);
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }

    private static void requireNode(JXNativeNode node) {
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
    }

    private static final class JXNativeNodeAdapter {
        private final JXNativeNode node;

        private JXNativeNodeAdapter(JXElement element) {
            node = JXNativeNode.createBackendNode(element);
        }

        private JXNativeNode node() {
            return node;
        }
    }
}
