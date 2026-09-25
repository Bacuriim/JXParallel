package com.jxparallel.ui.native2d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.jxparallel.ui.JXElement;

public final class JXNativeNode {
    private static final int UNKNOWN = -1;

    private final String type;
    private final Map<String, Object> props;
    private final List<JXNativeNode> children = new ArrayList<JXNativeNode>();
    private final List<JXNativeNode> unmodifiableChildren;
    private int x;
    private int y;
    private int width;
    private int height;
    private int preferredWidth = UNKNOWN;
    private int preferredHeight = UNKNOWN;
    private boolean layoutDirty = true;

    JXNativeNode(JXElement element) {
        this.type = element.getType();
        this.props = element.getProps().asMap();
        for (JXElement child : element.getChildren()) {
            children.add(new JXNativeNode(child));
        }
        this.unmodifiableChildren = Collections.unmodifiableList(children);
    }

    public static JXNativeNode createBackendNode(JXElement element) {
        if (element == null) {
            throw new IllegalArgumentException("Element cannot be null");
        }
        return new JXNativeNode(element);
    }

    public void layoutForBackend(int width, int height) {
        layout(0, 0, width, height);
    }

    public String getType() {
        return type;
    }

    public Object getProperty(String name) {
        return props.get(name);
    }

    public List<JXNativeNode> getChildren() {
        return unmodifiableChildren;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void invalidateLayout() {
        layoutDirty = true;
        preferredWidth = UNKNOWN;
        preferredHeight = UNKNOWN;
    }

    public boolean contains(int px, int py) {
        return px >= x && py >= y && px < x + width && py < y + height;
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
        width = Math.max(0, width);
        height = Math.max(0, height);
        if (!layoutDirty && this.x == x && this.y == y && this.width == width && this.height == height) {
            return;
        }
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        layoutDirty = false;
        if (children.isEmpty()) {
            return;
        }
        int gap = propertyAsInt("gap", 0);
        // Like JavaFX HBox/VBox: children keep their preferred size along the main axis and
        // fill the cross axis. Children past the end are laid out with zero size.
        if ("row".equals(type)) {
            int childX = x;
            for (JXNativeNode child : children) {
                int childWidth = Math.min(child.preferredWidth(), Math.max(0, x + width - childX));
                child.layout(childX, y, childWidth, height);
                childX += childWidth + gap;
            }
        } else if ("column".equals(type)) {
            int childY = y;
            for (JXNativeNode child : children) {
                int childHeight = Math.min(child.preferredHeight(), Math.max(0, y + height - childY));
                child.layout(x, childY, width, childHeight);
                childY += childHeight + gap;
            }
        } else {
            for (JXNativeNode child : children) {
                child.layout(x, y, width, height);
            }
        }
    }

    int preferredWidth() {
        computePreferredSize();
        return preferredWidth;
    }

    int preferredHeight() {
        computePreferredSize();
        return preferredHeight;
    }

    private void computePreferredSize() {
        if (preferredWidth != UNKNOWN) {
            return;
        }
        if ("button".equals(type) || "toggle".equals(type) || "input".equals(type)
                || "select".equals(type)) {
            setPreferred(120, 32);
        } else if ("checkbox".equals(type)) {
            setPreferred(160, 24);
        } else if ("textarea".equals(type)) {
            setPreferred(240, 96);
        } else if ("password".equals(type)) {
            setPreferred(180, 32);
        } else if ("progress".equals(type) || "slider".equals(type)) {
            setPreferred(180, 24);
        } else if ("#text".equals(type)) {
            setPreferred(100, 24);
        } else {
            int gap = propertyAsInt("gap", 0);
            int sumWidth = 0;
            int sumHeight = 0;
            for (JXNativeNode child : children) {
                if ("row".equals(type)) {
                    sumWidth += child.preferredWidth();
                    sumHeight = Math.max(sumHeight, child.preferredHeight());
                } else {
                    sumWidth = Math.max(sumWidth, child.preferredWidth());
                    sumHeight += child.preferredHeight();
                }
            }
            if (!children.isEmpty()) {
                if ("row".equals(type)) {
                    sumWidth += gap * (children.size() - 1);
                } else if ("column".equals(type)) {
                    sumHeight += gap * (children.size() - 1);
                }
            }
            setPreferred(Math.max(1, sumWidth), Math.max(1, sumHeight));
        }
    }

    private void setPreferred(int width, int height) {
        preferredWidth = width;
        preferredHeight = height;
    }

    private int propertyAsInt(String name, int fallback) {
        Object value = props.get(name);
        if (value instanceof Number) {
            return Math.max(0, ((Number) value).intValue());
        }
        return fallback;
    }
}
