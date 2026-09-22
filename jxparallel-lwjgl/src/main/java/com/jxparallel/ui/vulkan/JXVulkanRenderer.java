package com.jxparallel.ui.vulkan;

import java.awt.Rectangle;
import java.nio.FloatBuffer;

import com.jxparallel.ui.JXComponent;
import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.native2d.JXNativeNode;

import org.lwjgl.system.MemoryStack;

public final class JXVulkanRenderer {
    private JXVulkanRenderer() {
    }

    public static JXNativeNode mount(JXElement element) {
        if (element == null) {
            throw new IllegalArgumentException("Element cannot be null");
        }
        return JXNativeNode.createBackendNode(element);
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

    static int vertexCount(JXNativeNode root, int width, int height) {
        return buildVertices(root, width, height, null);
    }

    static void writeVertices(JXNativeNode root, int width, int height, FloatBuffer target) {
        buildVertices(root, width, height, target);
    }

    private static int buildVertices(JXNativeNode node, int width, int height, FloatBuffer target) {
        layout(node, width, height);
        return appendNode(node, width, height, target);
    }

    private static int appendNode(JXNativeNode node, int width, int height, FloatBuffer target) {
        Rectangle bounds = node.getBounds();
        String type = node.getType();
        int count = 0;
        if ("button".equals(type) || "toggle".equals(type)) {
            count += rectangle(bounds, width, height, 0.18f, 0.42f, 0.87f, target);
        } else if ("checkbox".equals(type)) {
            count += rectangle(new Rectangle(bounds.x, bounds.y, 18, 18), width, height,
                    0.96f, 0.96f, 0.96f, target);
            count += outline(bounds.x, bounds.y, 18, 18, width, height,
                    0.35f, 0.35f, 0.35f, target);
        } else if ("input".equals(type) || "textarea".equals(type)
                || "password".equals(type) || "select".equals(type)) {
            count += rectangle(bounds, width, height, 1.0f, 1.0f, 1.0f, target);
            count += outline(bounds.x, bounds.y, bounds.width, bounds.height, width, height,
                    0.55f, 0.55f, 0.55f, target);
        } else if ("progress".equals(type)) {
            count += rectangle(bounds, width, height, 0.88f, 0.88f, 0.88f, target);
            Rectangle progress = new Rectangle(bounds);
            progress.width = (int) (bounds.width * clamp(propertyAsDouble(node, "progress", 0.0)));
            count += rectangle(progress, width, height, 0.18f, 0.42f, 0.87f, target);
        } else if ("slider".equals(type)) {
            count += rectangle(new Rectangle(bounds.x, bounds.y + bounds.height / 2 - 1,
                    bounds.width, 2), width, height, 0.70f, 0.70f, 0.70f, target);
            double min = propertyAsDouble(node, "min", 0.0);
            double max = propertyAsDouble(node, "max", 100.0);
            double value = propertyAsDouble(node, "value", min);
            double fraction = max <= min ? 0.0 : (value - min) / (max - min);
            int knobX = bounds.x + (int) (bounds.width * clamp(fraction));
            count += rectangle(new Rectangle(knobX - 6, bounds.y + bounds.height / 2 - 6, 12, 12),
                    width, height, 0.18f, 0.42f, 0.87f, target);
        }
        for (JXNativeNode child : node.getChildren()) {
            count += appendNode(child, width, height, target);
        }
        return count;
    }

    private static int rectangle(Rectangle bounds, int width, int height,
            float red, float green, float blue, FloatBuffer target) {
        if (bounds.width <= 0 || bounds.height <= 0) {
            return 0;
        }
        vertex(target, bounds.x, bounds.y, width, height, red, green, blue);
        vertex(target, bounds.x + bounds.width, bounds.y, width, height, red, green, blue);
        vertex(target, bounds.x + bounds.width, bounds.y + bounds.height, width, height, red, green, blue);
        vertex(target, bounds.x, bounds.y, width, height, red, green, blue);
        vertex(target, bounds.x + bounds.width, bounds.y + bounds.height, width, height, red, green, blue);
        vertex(target, bounds.x, bounds.y + bounds.height, width, height, red, green, blue);
        return 6;
    }

    private static int outline(int x, int y, int width, int height, int targetWidth, int targetHeight,
            float red, float green, float blue, FloatBuffer target) {
        int count = 0;
        count += rectangle(new Rectangle(x, y, width, 1), targetWidth, targetHeight, red, green, blue, target);
        count += rectangle(new Rectangle(x, y + height - 1, width, 1),
                targetWidth, targetHeight, red, green, blue, target);
        count += rectangle(new Rectangle(x, y, 1, height), targetWidth, targetHeight, red, green, blue, target);
        count += rectangle(new Rectangle(x + width - 1, y, 1, height),
                targetWidth, targetHeight, red, green, blue, target);
        return count;
    }

    private static void vertex(FloatBuffer target, int x, int y, int width, int height,
            float red, float green, float blue) {
        if (target == null) {
            return;
        }
        target.put((x / (float) width) * 2.0f - 1.0f);
        target.put(1.0f - (y / (float) height) * 2.0f);
        target.put(red).put(green).put(blue);
    }

    private static double propertyAsDouble(JXNativeNode node, String name, double fallback) {
        Object value = node.getProperty(name);
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static void requireNode(JXNativeNode node) {
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
    }
}
