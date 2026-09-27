package com.jxparallel.ui.native2d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.text.JXTextEngine;

/**
 * A node of the native scene graph. Like a JavaFX Region, it has a minimum, preferred and maximum
 * size; containers ({@code column}, {@code row}, {@code stack}) lay out their children with the
 * JavaFX VBox, HBox and StackPane algorithms ({@link JXBoxLayout}).
 *
 * <p>Layout props: {@code gap} (spacing), {@code alignment} (a JavaFX Pos name such as
 * {@code "CENTER"} or {@code "top-right"}), {@code fillWidth} and {@code fillHeight} on containers;
 * {@code vgrow} and {@code hgrow} ({@code "always"}, {@code "sometimes"}) on children; and
 * {@code minWidth}, {@code prefWidth}, {@code maxWidth}, {@code minHeight}, {@code prefHeight},
 * {@code maxHeight} on any node, which override the computed sizes like JavaFX's setters.
 */
public final class JXNativeNode {
    /** Insets of labeled controls in JavaFX's Modena theme: 0.333333em by 0.666667em at 12 px. */
    static final int PAD_X = 8;
    static final int PAD_Y = 4;
    /** Modena text-field insets: 0.333333em by 0.583em. */
    static final int FIELD_PAD_X = 7;
    static final int CHECK_BOX = 14;
    static final int CHECK_GAP = 6;
    static final int ARROW = 22;
    /** JavaFX defaults: TextField.prefColumnCount 12, TextArea 40 columns by 10 rows. */
    static final int FIELD_COLUMNS = 12;
    static final int AREA_COLUMNS = 40;
    static final int AREA_ROWS = 10;

    private static final double MAX = JXBoxLayout.MAX;
    static final double[] NO_INSETS = {0, 0, 0, 0};
    private static final int MIN_W = 0;
    private static final int PREF_W = 1;
    private static final int MAX_W = 2;
    private static final int MIN_H = 3;
    private static final int PREF_H = 4;
    private static final int MAX_H = 5;
    /** Width of "..." at the default size; one font per process, so computed once. */
    private static volatile double ellipsisWidth = -1;
    /** Props that change a node's size or how its parent places it. */
    private static final String[] LAYOUT_KEYS = {"gap", "alignment", "fillWidth", "fillHeight", "vgrow", "hgrow",
            "minWidth", "prefWidth", "maxWidth", "minHeight", "prefHeight", "maxHeight", "padding", "margin",
            "position", "halignment", "valignment", "column", "row", "columnSpan", "rowSpan", "cellFillWidth",
            "cellFillHeight", "hgap", "vgap", "columns", "rows", "layoutX", "layoutY", "leftAnchor", "rightAnchor",
            "topAnchor", "bottomAnchor", "orientation", "prefWrapLength", "columnHalignment", "rowValignment",
            "prefColumns", "prefRows", "prefTileWidth", "prefTileHeight", "tileAlignment"};

    private static final java.util.Set<String> LAYOUT_KEY_SET =
            new java.util.HashSet<String>(java.util.Arrays.asList(LAYOUT_KEYS));

    /** Container kind, so layout dispatches on an int instead of comparing type strings. */
    static final int LEAF = 0;
    static final int COLUMN = 1;
    static final int ROW = 2;
    static final int STACK = 3;
    static final int BORDER = 4;
    static final int GRID = 5;
    static final int PANE = 6;
    static final int ANCHOR = 7;
    static final int FLOW = 8;
    static final int TILE = 9;
    private final String type;
    private final int kind;
    private Map<String, Object> props;
    private JXElement source;
    private final List<JXNativeNode> children = new ArrayList<JXNativeNode>();
    private final List<JXNativeNode> unmodifiableChildren;
    private int x;
    private int y;
    private int width;
    private int height;
    /** min, pref and max width, then height; null until needed. */
    private double[] sizes;
    private boolean layoutDirty = true;
    /** A descendant needs layout at its current bounds; this node's own layout is still valid. */
    private boolean childNeedsLayout;
    /* Layout props, read once per props change so layout does no map lookups. */
    /** Grow priorities: 0 never, 1 sometimes, 2 always. */
    private int vgrow;
    private int hgrow;
    private double gap;
    private String alignment;
    private boolean fillWidth;
    private boolean fillHeight;
    /** Size overrides (minWidth, prefWidth, maxWidth, minHeight, prefHeight, maxHeight), NaN when not set. */
    private double[] overrides;
    /** Padding of this node and its margin in the parent: top, right, bottom, left. */
    private double[] padding = NO_INSETS;
    private double[] margin = NO_INSETS;
    /** Slot in a border parent (top, bottom, left, right, center) and own alignment in a cell: 0 when not set. */
    private String position;
    private char halignment;
    private char valignment;
    /** Grid cell of this node and, on a grid, the gaps between cells. Spans are at least 1. */
    private int column;
    private int row;
    private int columnSpan = 1;
    private int rowSpan = 1;
    private Boolean cellFillWidth;
    private Boolean cellFillHeight;
    private double hgap;
    private double vgap;
    /** Position in a pane parent, and anchors in an anchor parent (NaN when not set). */
    private double layoutX;
    private double layoutY;
    private double leftAnchor = Double.NaN;
    private double rightAnchor = Double.NaN;
    private double topAnchor = Double.NaN;
    private double bottomAnchor = Double.NaN;

    JXNativeNode(JXElement element) {
        this.source = element;
        this.type = element.getType();
        this.kind = kindOf(type);
        setProps(element.getProps().asMap());
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

    /** Sizes the root to the window, like a JavaFX Scene does with its root, then lays out the tree. */
    public void layoutForBackend(int width, int height) {
        layout(0, 0, width, height);
    }

    /**
     * Updates this tree in place to match {@code element}: nodes of the same type are reused and
     * only get their props replaced. Layout is invalidated only where a size can change: children
     * added, removed or of another type, a new text on a label or button, or a layout prop.
     * Returns {@code false} when the root type differs; the caller must then mount a new tree.
     */
    public boolean reconcile(JXElement element) {
        if (element == null || !type.equals(element.getType())) {
            return false;
        }
        reconcileInPlace(element);
        return true;
    }

    private static final int UNCHANGED = 0;
    /** Something inside needs layout, but this node's sizes and placement are the same. */
    private static final int SUBTREE = 1;
    /** This node's min/pref/max size or grow priority changed: the parent must lay out again. */
    private static final int RESIZED = 2;

    /**
     * Unlike JavaFX, where requestLayout() always climbs to the root, a change stops at the first
     * node whose min/pref/max sizes come out the same: its parent keeps its layout and only the
     * changed subtree is laid out again.
     */
    private int reconcileInPlace(JXElement element) {
        if (element == source) {
            return UNCHANGED; // same immutable element (memoized render): nothing below changed
        }
        source = element;
        Map<String, Object> next = element.getProps().asMap();
        boolean placement = vgrow != priority(next.get("vgrow")) || hgrow != priority(next.get("hgrow"));
        boolean relayout = sizeAffected(next);
        setProps(next);
        boolean subtree = false;
        List<JXElement> nextChildren = element.getChildren();
        int common = Math.min(children.size(), nextChildren.size());
        for (int i = 0; i < common; i++) {
            JXElement childElement = nextChildren.get(i);
            JXNativeNode child = children.get(i);
            if (child.type.equals(childElement.getType())) {
                int result = child.reconcileInPlace(childElement);
                relayout |= result == RESIZED;
                subtree |= result == SUBTREE;
            } else {
                children.set(i, new JXNativeNode(childElement));
                relayout = true;
            }
        }
        for (int i = common; i < nextChildren.size(); i++) {
            children.add(new JXNativeNode(nextChildren.get(i)));
            relayout = true;
        }
        while (children.size() > nextChildren.size()) {
            children.remove(children.size() - 1);
            relayout = true;
        }
        if (relayout) {
            double[] old = sizes;
            invalidateLayout();
            boolean sameSize = old != null && Arrays.equals(old, sizes());
            return placement || !sameSize ? RESIZED : SUBTREE;
        }
        if (subtree) {
            childNeedsLayout = true;
            return placement ? RESIZED : SUBTREE;
        }
        return placement ? RESIZED : UNCHANGED;
    }

    private static int kindOf(String type) {
        switch (type) {
            case "column":
                return COLUMN;
            case "row":
                return ROW;
            case "stack":
                return STACK;
            case "border":
                return BORDER;
            case "grid":
                return GRID;
            case "pane":
                return PANE;
            case "anchor":
                return ANCHOR;
            case "flow":
                return FLOW;
            case "tile":
                return TILE;
            default:
                return LEAF;
        }
    }

    int kind() {
        return kind;
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
        sizes = null;
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

    /**
     * Places this node at an absolute position and size, then lays out its children. Like JavaFX,
     * a size can be negative (a child stretched between anchors of a pane smaller than the anchors).
     */
    void layout(int x, int y, int width, int height) {
        if (!layoutDirty && this.x == x && this.y == y && this.width == width && this.height == height) {
            if (childNeedsLayout) {
                childNeedsLayout = false;
                for (JXNativeNode child : children) {
                    if (child.layoutDirty || child.childNeedsLayout) {
                        child.layout(child.x, child.y, child.width, child.height);
                    }
                }
            }
            return;
        }
        childNeedsLayout = false;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        layoutDirty = false;
        JXBoxLayout.layoutChildren(this);
    }

    double minWidth() {
        return sizes()[MIN_W];
    }

    double prefWidth() {
        return sizes()[PREF_W];
    }

    double maxWidth() {
        return sizes()[MAX_W];
    }

    double minHeight() {
        return sizes()[MIN_H];
    }

    double prefHeight() {
        return sizes()[PREF_H];
    }

    double maxHeight() {
        return sizes()[MAX_H];
    }

    double spacing() {
        return gap;
    }

    /** Two letters, vertical T/C/B then horizontal L/C/R. Stacks centre by default, like StackPane. */
    String alignment() {
        return alignment;
    }

    boolean fillWidth() {
        return fillWidth;
    }

    boolean fillHeight() {
        return fillHeight;
    }

    static final int SOMETIMES = 1;
    static final int ALWAYS = 2;

    /** JavaFX Priority from the hgrow (in a row) or vgrow (in a column) prop. */
    int growPriority(boolean horizontal) {
        return horizontal ? hgrow : vgrow;
    }

    private void setProps(Map<String, Object> next) {
        props = next;
        vgrow = 0;
        hgrow = 0;
        gap = 0;
        fillWidth = true;
        fillHeight = true;
        overrides = null;
        alignment = null;
        padding = NO_INSETS;
        margin = NO_INSETS;
        position = null;
        halignment = 0;
        valignment = 0;
        column = 0;
        row = 0;
        columnSpan = 1;
        rowSpan = 1;
        cellFillWidth = null;
        cellFillHeight = null;
        hgap = 0;
        vgap = 0;
        layoutX = 0;
        layoutY = 0;
        leftAnchor = Double.NaN;
        rightAnchor = Double.NaN;
        topAnchor = Double.NaN;
        bottomAnchor = Double.NaN;
        next.forEach(this::readLayoutProp); // Map.forEach: no entry objects, unlike entrySet() on an unmodifiable map
        alignment = JXBoxLayout.parseAlignment(alignment, kind == STACK ? "CC" : "TL");
    }

    private void readLayoutProp(String key, Object value) {
        switch (key) {
            case "vgrow":
                vgrow = priority(value);
                break;
            case "hgrow":
                hgrow = priority(value);
                break;
            case "gap":
                gap = value instanceof Number ? Math.max(0, ((Number) value).doubleValue()) : 0;
                break;
            case "alignment":
                alignment = value instanceof String ? (String) value : null;
                break;
            case "fillWidth":
                fillWidth = !Boolean.FALSE.equals(value);
                break;
            case "fillHeight":
                fillHeight = !Boolean.FALSE.equals(value);
                break;
            case "minWidth":
                override(0, value);
                break;
            case "prefWidth":
                override(1, value);
                break;
            case "maxWidth":
                override(2, value);
                break;
            case "minHeight":
                override(3, value);
                break;
            case "prefHeight":
                override(4, value);
                break;
            case "maxHeight":
                override(5, value);
                break;
            case "padding":
                padding = insets(value);
                break;
            case "margin":
                margin = insets(value);
                break;
            case "position":
                position = value instanceof String ? ((String) value).toLowerCase(java.util.Locale.ROOT) : null;
                break;
            case "halignment":
                halignment = value instanceof String && !((String) value).isEmpty()
                        ? Character.toUpperCase(((String) value).charAt(0)) : 0;
                break;
            case "valignment":
                valignment = value instanceof String && !((String) value).isEmpty()
                        ? Character.toUpperCase(((String) value).charAt(0)) : 0;
                break;
            case "column":
                column = value instanceof Number ? Math.max(0, ((Number) value).intValue()) : 0;
                break;
            case "row":
                row = value instanceof Number ? Math.max(0, ((Number) value).intValue()) : 0;
                break;
            case "columnSpan":
                columnSpan = value instanceof Number ? Math.max(1, ((Number) value).intValue()) : 1;
                break;
            case "rowSpan":
                rowSpan = value instanceof Number ? Math.max(1, ((Number) value).intValue()) : 1;
                break;
            case "cellFillWidth":
                cellFillWidth = value instanceof Boolean ? (Boolean) value : null;
                break;
            case "cellFillHeight":
                cellFillHeight = value instanceof Boolean ? (Boolean) value : null;
                break;
            case "hgap":
                hgap = value instanceof Number ? Math.max(0, ((Number) value).doubleValue()) : 0;
                break;
            case "vgap":
                vgap = value instanceof Number ? Math.max(0, ((Number) value).doubleValue()) : 0;
                break;
            case "layoutX":
                layoutX = value instanceof Number ? ((Number) value).doubleValue() : 0;
                break;
            case "layoutY":
                layoutY = value instanceof Number ? ((Number) value).doubleValue() : 0;
                break;
            case "leftAnchor":
                leftAnchor = value instanceof Number ? ((Number) value).doubleValue() : Double.NaN;
                break;
            case "rightAnchor":
                rightAnchor = value instanceof Number ? ((Number) value).doubleValue() : Double.NaN;
                break;
            case "topAnchor":
                topAnchor = value instanceof Number ? ((Number) value).doubleValue() : Double.NaN;
                break;
            case "bottomAnchor":
                bottomAnchor = value instanceof Number ? ((Number) value).doubleValue() : Double.NaN;
                break;
            default:
                break;
        }
    }

    /** One number for all sides, or top, right, bottom, left like CSS and JavaFX Insets. */
    private static double[] insets(Object value) {
        if (value instanceof Number) {
            double all = Math.max(0, ((Number) value).doubleValue());
            return new double[] {all, all, all, all};
        }
        double[] sides = new double[4];
        if (value instanceof int[] && ((int[]) value).length == 4) {
            for (int i = 0; i < 4; i++) {
                sides[i] = Math.max(0, ((int[]) value)[i]);
            }
            return sides;
        }
        if (value instanceof double[] && ((double[]) value).length == 4) {
            for (int i = 0; i < 4; i++) {
                sides[i] = Math.max(0, ((double[]) value)[i]);
            }
            return sides;
        }
        if (value instanceof List && ((List<?>) value).size() == 4) {
            for (int i = 0; i < 4; i++) {
                Object side = ((List<?>) value).get(i);
                sides[i] = side instanceof Number ? Math.max(0, ((Number) side).doubleValue()) : 0;
            }
            return sides;
        }
        return NO_INSETS;
    }

    double[] padding() {
        return padding;
    }

    double[] margin() {
        return margin;
    }

    String position() {
        return position;
    }

    /** 'L', 'C' or 'R'; 0 when the parent decides. */
    char halignment() {
        return halignment;
    }

    /** 'T', 'C' or 'B'; 0 when the parent decides. */
    char valignment() {
        return valignment;
    }

    int column() {
        return column;
    }

    int row() {
        return row;
    }

    int columnSpan() {
        return columnSpan;
    }

    int rowSpan() {
        return rowSpan;
    }

    /** GridPane.fillWidth of this child, or null to use the column's. */
    Boolean cellFillWidth() {
        return cellFillWidth;
    }

    Boolean cellFillHeight() {
        return cellFillHeight;
    }

    double hgap() {
        return hgap;
    }

    double layoutX() {
        return layoutX;
    }

    double layoutY() {
        return layoutY;
    }

    /** Anchors of this node in an anchor parent, NaN when not set. */
    double leftAnchor() {
        return leftAnchor;
    }

    double rightAnchor() {
        return rightAnchor;
    }

    double topAnchor() {
        return topAnchor;
    }

    double bottomAnchor() {
        return bottomAnchor;
    }

    double vgap() {
        return vgap;
    }

    /** JavaFX's Region.USE_COMPUTED_SIZE (-1) leaves the size computed; USE_PREF_SIZE (-Infinity) means "the pref size". */
    private void override(int index, Object value) {
        if (value instanceof Number) {
            double v = ((Number) value).doubleValue();
            if (v == -1) {
                return;
            }
            if (overrides == null) {
                overrides = new double[] {Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN};
            }
            overrides[index] = v == Double.NEGATIVE_INFINITY ? v : Math.max(0, v);
        }
    }

    private static int priority(Object value) {
        if (!(value instanceof String)) {
            return 0;
        }
        return "always".equalsIgnoreCase((String) value) ? ALWAYS : "sometimes".equalsIgnoreCase((String) value) ? SOMETIMES : 0;
    }

    private double[] sizes() {
        double[] current = sizes;
        if (current == null) {
            current = intrinsicSizes();
            if (overrides != null) {
                // Controls whose max is their pref (buttons, combo boxes...) keep that after a pref override.
                boolean maxIsPrefWidth = current[2] == current[1];
                boolean maxIsPrefHeight = current[5] == current[4];
                for (int i = 0; i < overrides.length; i++) {
                    if (!Double.isNaN(overrides[i]) && overrides[i] != Double.NEGATIVE_INFINITY) {
                        current[i] = overrides[i];
                    }
                }
                if (maxIsPrefWidth && Double.isNaN(overrides[2])) {
                    current[2] = current[1];
                }
                if (maxIsPrefHeight && Double.isNaN(overrides[5])) {
                    current[5] = current[4];
                }
                for (int i = 0; i < overrides.length; i++) {
                    if (overrides[i] == Double.NEGATIVE_INFINITY) {
                        current[i] = current[i < 3 ? 1 : 4];
                    }
                }
            }
            sizes = current;
        }
        return current;
    }

    /** Computed sizes before overrides; controls follow JavaFX 21 with the Modena theme. */
    private double[] intrinsicSizes() {
        if (kind == GRID) {
            return JXGridLayout.sizes(this);
        }
        if (kind == FLOW || kind == TILE) {
            return JXFlowLayout.sizes(this);
        }
        if (kind == COLUMN || kind == ROW || kind == STACK || kind == BORDER
                || kind == PANE || kind == ANCHOR || !children.isEmpty()) {
            return JXBoxLayout.containerSizes(this);
        }
        JXTextEngine text = JXTextEngine.get();
        float size = JXTextEngine.DEFAULT_SIZE;
        double line = text.lineHeight(size);
        if ("button".equals(type) || "toggle".equals(type)) {
            return labeled(text.width(string("label"), size), 2 * PAD_X, line + 2 * PAD_Y);
        }
        if ("#text".equals(type)) {
            return labeled(text.width(string("value"), size), 0, line);
        }
        if ("checkbox".equals(type)) {
            return labeled(text.width(string("label"), size), CHECK_BOX + CHECK_GAP, Math.max(CHECK_BOX, line));
        }
        if ("input".equals(type) || "password".equals(type)) {
            // Like TextField: width from the column count, not the content, so typing never relayouts.
            double fieldHeight = line + 2 * PAD_Y;
            return new double[] {2 * FIELD_PAD_X, FIELD_COLUMNS * text.width("W", size) + 2 * FIELD_PAD_X, MAX,
                    fieldHeight, fieldHeight, fieldHeight};
        }
        if ("textarea".equals(type)) {
            double w = text.width("W", size);
            return new double[] {w + 2 * FIELD_PAD_X, AREA_COLUMNS * w + 2 * FIELD_PAD_X, MAX,
                    line + 2 * PAD_Y, AREA_ROWS * line + 2 * PAD_Y, MAX};
        }
        if ("select".equals(type)) {
            double widest = text.width(string("value"), size);
            Object options = props.get("options");
            if (options instanceof Object[]) {
                for (Object option : (Object[]) options) {
                    widest = Math.max(widest, text.width(String.valueOf(option), size));
                }
            }
            double w = widest + 2 * PAD_X + ARROW;
            double h = line + 2 * PAD_Y;
            return new double[] {w, w, w, h, h, h};
        }
        if ("progress".equals(type)) {
            return new double[] {0, 100, 100, 0, 1.5 * size, 1.5 * size};
        }
        if ("slider".equals(type)) {
            double h = 14 * size / 12;
            return new double[] {42, 140, MAX, h, h, h};
        }
        double w = padding[1] + padding[3];
        double h = padding[0] + padding[2];
        return new double[] {w, w, MAX, h, h, MAX}; // like an empty Region: its padding
    }

    /** Labeled: max = pref; the width can shrink to an ellipsis, like JavaFX's Labeled. */
    private static double[] labeled(double textWidth, double extraWidth, double height) {
        double ellipsis = ellipsisWidth;
        if (ellipsis < 0) {
            ellipsis = JXTextEngine.get().width("...", JXTextEngine.DEFAULT_SIZE);
            ellipsisWidth = ellipsis;
        }
        double pref = textWidth + extraWidth;
        return new double[] {Math.min(textWidth, ellipsis) + extraWidth, pref, pref, height, height, height};
    }

    private String string(String name) {
        Object value = props.get(name);
        return value == null ? "" : String.valueOf(value);
    }

    /** True if the new props can change this node's size or its placement in the parent. */
    private boolean sizeAffected(Map<String, Object> next) {
        // Walk the keys the two maps have (a few) instead of every layout key (dozens).
        for (String key : props.keySet()) {
            if (LAYOUT_KEY_SET.contains(key) && !Objects.equals(props.get(key), next.get(key))) {
                return true;
            }
        }
        for (String key : next.keySet()) {
            if (LAYOUT_KEY_SET.contains(key) && !props.containsKey(key)) {
                return true;
            }
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
}
