package com.jxparallel.ui.native2d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.text.JXTextEngine;

public final class JXNativeNode {
    private static final int UNKNOWN = -1;
    /** Insets around text in buttons and fields, close to JavaFX's Modena theme at 13 px. */
    static final int PAD_X = 12;
    static final int PAD_Y = 6;
    static final int CHECK_BOX = 18;
    static final int CHECK_GAP = 6;
    static final int ARROW = 20;
    /** JavaFX defaults: TextField.prefColumnCount 12, TextArea 40 columns by 10 rows. */
    static final int FIELD_COLUMNS = 12;
    static final int AREA_COLUMNS = 40;
    static final int AREA_ROWS = 10;

    private final String type;
    private Map<String, Object> props;
    private JXElement source;
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
        this.source = element;
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

    /**
     * Updates this tree in place to match {@code element}: nodes of the same type are reused and
     * only get their props replaced, which keeps their layout and preferred-size caches. Layout is
     * invalidated only where the structure changed (children added, removed or of another type, or
     * a different {@code gap}). Returns {@code false} when the root type differs; the caller must
     * then mount a new tree.
     */
    public boolean reconcile(JXElement element) {
        if (element == null || !type.equals(element.getType())) {
            return false;
        }
        reconcileInPlace(element);
        return true;
    }

    /** Returns true if this node's preferred size may have changed. */
    private boolean reconcileInPlace(JXElement element) {
        if (element == source) {
            return false; // same immutable element (memoized render): nothing below changed
        }
        source = element;
        Map<String, Object> next = element.getProps().asMap();
        boolean changed = sizeAffected(next);
        props = next;
        List<JXElement> nextChildren = element.getChildren();
        int common = Math.min(children.size(), nextChildren.size());
        for (int i = 0; i < common; i++) {
            JXElement childElement = nextChildren.get(i);
            JXNativeNode child = children.get(i);
            if (child.type.equals(childElement.getType())) {
                changed |= child.reconcileInPlace(childElement);
            } else {
                children.set(i, new JXNativeNode(childElement));
                changed = true;
            }
        }
        for (int i = common; i < nextChildren.size(); i++) {
            children.add(new JXNativeNode(nextChildren.get(i)));
            changed = true;
        }
        while (children.size() > nextChildren.size()) {
            children.remove(children.size() - 1);
            changed = true;
        }
        if (changed) {
            invalidateLayout();
        }
        return changed;
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

    boolean isLayoutDirty() {
        return layoutDirty;
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
        if (Boolean.TRUE.equals(props.get("disabled"))) {
            return; // like JavaFX: disabled nodes get no mouse events
        }
        // Controls set onAction (JXButton, JXControls.button); onClick is for hand-built elements.
        Object handler = props.containsKey("onClick") ? props.get("onClick") : props.get("onAction");
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
        JXTextEngine text = JXTextEngine.get();
        float size = JXTextEngine.DEFAULT_SIZE;
        int line = ceil(text.lineHeight(size));
        if ("button".equals(type) || "toggle".equals(type)) {
            setPreferred(ceil(text.width(string("label"), size)) + 2 * PAD_X, line + 2 * PAD_Y);
        } else if ("checkbox".equals(type)) {
            setPreferred(CHECK_BOX + CHECK_GAP + ceil(text.width(string("label"), size)), Math.max(CHECK_BOX, line));
        } else if ("input".equals(type) || "password".equals(type)) {
            // Like JavaFX TextField: width from the column count, not the content, so typing never relayouts.
            setPreferred(FIELD_COLUMNS * ceil(text.width("W", size)) + 2 * PAD_X, line + 2 * PAD_Y);
        } else if ("textarea".equals(type)) {
            setPreferred(AREA_COLUMNS * ceil(text.width("W", size)) + 2 * PAD_X, AREA_ROWS * line + 2 * PAD_Y);
        } else if ("select".equals(type)) {
            int widest = ceil(text.width(string("value"), size));
            Object options = props.get("options");
            if (options instanceof Object[]) {
                for (Object option : (Object[]) options) {
                    widest = Math.max(widest, ceil(text.width(String.valueOf(option), size)));
                }
            }
            setPreferred(widest + 2 * PAD_X + ARROW, line + 2 * PAD_Y);
        } else if ("progress".equals(type) || "slider".equals(type)) {
            setPreferred(180, 24);
        } else if ("#text".equals(type)) {
            setPreferred(ceil(text.width(string("value"), size)), line);
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

    private String string(String name) {
        Object value = props.get(name);
        return value == null ? "" : String.valueOf(value);
    }

    private static int ceil(float value) {
        return (int) Math.ceil(value);
    }

    /** True if the new props can change this node's preferred size (text, options or gap). */
    private boolean sizeAffected(Map<String, Object> next) {
        if (propertyAsInt(next, "gap", 0) != propertyAsInt(props, "gap", 0)) {
            return true;
        }
        if ("#text".equals(type)) {
            return !Objects.equals(props.get("value"), next.get("value"));
        }
        if ("button".equals(type) || "toggle".equals(type) || "checkbox".equals(type)) {
            return !Objects.equals(props.get("label"), next.get("label"));
        }
        if ("select".equals(type)) {
            return !Objects.equals(props.get("value"), next.get("value"))
                    || !Objects.deepEquals(props.get("options"), next.get("options"));
        }
        return false;
    }

    private int propertyAsInt(String name, int fallback) {
        return propertyAsInt(props, name, fallback);
    }

    private static int propertyAsInt(Map<String, Object> props, String name, int fallback) {
        Object value = props.get(name);
        if (value instanceof Number) {
            return Math.max(0, ((Number) value).intValue());
        }
        return fallback;
    }
}
