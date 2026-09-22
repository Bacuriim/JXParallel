package com.jxparallel.ui.native2d;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.jxparallel.ui.JXElement;

public final class JXNativeNode {
    private final String type;
    private final Map<String, Object> props;
    private final List<JXNativeNode> children = new ArrayList<JXNativeNode>();
    private Rectangle bounds = new Rectangle();
    private Dimension preferredSize;
    private boolean layoutDirty = true;

    JXNativeNode(JXElement element) {
        this.type = element.getType();
        this.props = element.getProps().asMap();
        for (JXElement child : element.getChildren()) {
            children.add(new JXNativeNode(child));
        }
    }

    public String getType() {
        return type;
    }

    public Object getProperty(String name) {
        return props.get(name);
    }

    public List<JXNativeNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public Rectangle getBounds() {
        return new Rectangle(bounds);
    }

    public void invalidateLayout() {
        layoutDirty = true;
        preferredSize = null;
    }

    public boolean contains(int x, int y) {
        return bounds.contains(x, y);
    }

    public JXNativeNode hitTest(int x, int y) {
        for (int index = children.size() - 1; index >= 0; index--) {
            JXNativeNode hit = children.get(index).hitTest(x, y);
            if (hit != null) {
                return hit;
            }
        }
        return contains(x, y) ? this : null;
    }

    @SuppressWarnings("unchecked")
    public void dispatchPointer(JXPointerEvent event) {
        Object handler = props.get("onClick");
        if (handler instanceof JXNativeEventHandler) {
            ((JXNativeEventHandler<JXPointerEvent>) handler).handle(event);
        } else if (handler instanceof Runnable) {
            ((Runnable) handler).run();
        }
    }

    @SuppressWarnings("unchecked")
    public void dispatchKey(JXKeyEvent event) {
        Object handler = props.get("onKeyPressed");
        if (handler instanceof JXNativeEventHandler) {
            ((JXNativeEventHandler<JXKeyEvent>) handler).handle(event);
        }
    }

    void layout(int x, int y, int width, int height) {
        if (!layoutDirty && bounds.x == x && bounds.y == y
                && bounds.width == Math.max(0, width) && bounds.height == Math.max(0, height)) {
            return;
        }
        bounds = new Rectangle(x, y, Math.max(0, width), Math.max(0, height));
        layoutDirty = false;
        if (children.isEmpty()) {
            return;
        }
        int gap = propertyAsInt("gap", 0);
        if ("row".equals(type)) {
            int childWidth = Math.max(0, (width - gap * (children.size() - 1)) / children.size());
            int childX = x;
            for (JXNativeNode child : children) {
                child.layout(childX, y, childWidth, height);
                childX += childWidth + gap;
            }
        } else if ("column".equals(type)) {
            int childHeight = Math.max(0, (height - gap * (children.size() - 1)) / children.size());
            int childY = y;
            for (JXNativeNode child : children) {
                child.layout(x, childY, width, childHeight);
                childY += childHeight + gap;
            }
        } else {
            for (JXNativeNode child : children) {
                child.layout(x, y, width, height);
            }
        }
    }

    Dimension preferredSize() {
        if (preferredSize != null) {
            return new Dimension(preferredSize);
        }
        if ("button".equals(type) || "toggle".equals(type) || "input".equals(type)
                || "select".equals(type)) {
            return preferredSize = new Dimension(120, 32);
        }
        if ("checkbox".equals(type)) {
            return preferredSize = new Dimension(160, 24);
        }
        if ("textarea".equals(type)) {
            return preferredSize = new Dimension(240, 96);
        }
        if ("password".equals(type)) {
            return preferredSize = new Dimension(180, 32);
        }
        if ("progress".equals(type) || "slider".equals(type)) {
            return preferredSize = new Dimension(180, 24);
        }
        if ("#text".equals(type)) {
            return preferredSize = new Dimension(100, 24);
        }
        int gap = propertyAsInt("gap", 0);
        int width = 0;
        int height = 0;
        for (JXNativeNode child : children) {
            Dimension size = child.preferredSize();
            if ("row".equals(type)) {
                width += size.width;
                height = Math.max(height, size.height);
            } else {
                width = Math.max(width, size.width);
                height += size.height;
            }
        }
        if (!children.isEmpty()) {
            if ("row".equals(type)) {
                width += gap * (children.size() - 1);
            } else if ("column".equals(type)) {
                height += gap * (children.size() - 1);
            }
        }
        preferredSize = new Dimension(Math.max(1, width), Math.max(1, height));
        return new Dimension(preferredSize);
    }

    private Object propertyOrDefault(String name, Object fallback) {
        Object value = props.get(name);
        return value == null ? fallback : value;
    }

    private int propertyAsInt(String name, int fallback) {
        Object value = props.get(name);
        if (value instanceof Number) {
            return Math.max(0, ((Number) value).intValue());
        }
        return fallback;
    }

    private double propertyAsDouble(String name, double fallback) {
        Object value = props.get(name);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return fallback;
    }
}
