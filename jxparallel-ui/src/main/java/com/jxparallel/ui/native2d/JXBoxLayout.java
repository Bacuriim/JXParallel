package com.jxparallel.ui.native2d;

import java.util.List;
import java.util.Locale;

/**
 * JavaFX's VBox, HBox, StackPane and BorderPane algorithms (javafx.scene.layout, JavaFX 21), with
 * the same pixel snapping at scale 1: sizes are rounded up, positions and insets to the nearest
 * pixel, and extra space is handed out in whole-pixel portions, first to children with grow
 * priority "always", then "sometimes". When space is short every child shrinks towards its minimum;
 * what still does not fit overflows, as in JavaFX. Padding and child margins are supported;
 * baseline alignment and content bias (text wrapping) are not yet.
 */
final class JXBoxLayout {
    static final double MAX = Double.MAX_VALUE;
    private static final int TOP = 0;
    private static final int RIGHT = 1;
    private static final int BOTTOM = 2;
    private static final int LEFT = 3;

    private JXBoxLayout() {
    }

    /** min, pref and max width, then min, pref and max height of a column, row, stack or border. */
    static double[] containerSizes(JXNativeNode node) {
        if (node.kind() == JXNativeNode.BORDER) {
            return borderSizes(node);
        }
        if (node.kind() == JXNativeNode.PANE) {
            return paneSizes(node);
        }
        if (node.kind() == JXNativeNode.ANCHOR) {
            return anchorSizes(node);
        }
        List<JXNativeNode> children = node.getChildren();
        boolean column = node.kind() == JXNativeNode.COLUMN;
        boolean row = node.kind() == JXNativeNode.ROW;
        double[] padding = node.padding();
        if (column || row) {
            padding = rounded(padding); // VBox and HBox snap their insets; StackPane does not
        }
        double spacing = Math.round(node.spacing());
        double minW = 0;
        double prefW = 0;
        double minH = 0;
        double prefH = 0;
        for (JXNativeNode child : children) {
            double childMinW = minAreaWidth(child);
            double childPrefW = prefAreaWidth(child);
            double childMinH = minAreaHeight(child);
            double childPrefH = prefAreaHeight(child);
            if (row) {
                minW += childMinW;
                prefW += childPrefW;
                minH = Math.max(minH, childMinH);
                prefH = Math.max(prefH, childPrefH);
            } else if (column) {
                minW = Math.max(minW, childMinW);
                prefW = Math.max(prefW, childPrefW);
                minH += childMinH;
                prefH += childPrefH;
            } else {
                minW = Math.max(minW, childMinW);
                prefW = Math.max(prefW, childPrefW);
                minH = Math.max(minH, childMinH);
                prefH = Math.max(prefH, childPrefH);
            }
        }
        double gaps = (children.size() - 1) * spacing;
        if (row) {
            minW += gaps;
            prefW += gaps;
        } else if (column) {
            minH += gaps;
            prefH += gaps;
        }
        double horizontal = padding[LEFT] + padding[RIGHT];
        double vertical = padding[TOP] + padding[BOTTOM];
        return new double[] {nonNegative(minW + horizontal), nonNegative(prefW + horizontal), MAX,
                nonNegative(minH + vertical), nonNegative(prefH + vertical), MAX};
    }

    /** BorderPane.computeMin/PrefWidth/Height: top and bottom at pref height, left and right at pref width. */
    private static double[] borderSizes(JXNativeNode node) {
        JXNativeNode top = slot(node, "top");
        JXNativeNode bottom = slot(node, "bottom");
        JXNativeNode left = slot(node, "left");
        JXNativeNode right = slot(node, "right");
        JXNativeNode center = slot(node, "center");
        double[] padding = node.padding();
        double horizontal = padding[LEFT] + padding[RIGHT];
        double vertical = padding[TOP] + padding[BOTTOM];
        double minW = horizontal + Math.max(prefAreaWidth(left) + minAreaWidth(center) + prefAreaWidth(right),
                Math.max(minAreaWidth(top), minAreaWidth(bottom)));
        double prefW = horizontal + Math.max(prefAreaWidth(left) + prefAreaWidth(center) + prefAreaWidth(right),
                Math.max(prefAreaWidth(top), prefAreaWidth(bottom)));
        double minH = vertical + prefAreaHeight(top) + prefAreaHeight(bottom)
                + Math.max(minAreaHeight(center), Math.max(minAreaHeight(right), minAreaHeight(left)));
        double prefH = vertical + prefAreaHeight(top) + prefAreaHeight(bottom)
                + Math.max(prefAreaHeight(center), Math.max(prefAreaHeight(right), prefAreaHeight(left)));
        return new double[] {minW, prefW, MAX, minH, prefH, MAX};
    }

    static void layoutChildren(JXNativeNode node) {
        List<JXNativeNode> children = node.getChildren();
        if (children.isEmpty()) {
            return;
        }
        int kind = node.kind();
        if (kind == JXNativeNode.BORDER) {
            layoutBorder(node);
            return;
        }
        if (kind == JXNativeNode.GRID) {
            JXGridLayout.layoutChildren(node);
            return;
        }
        if (kind == JXNativeNode.PANE) {
            // Parent.layoutChildren: each child at its layoutX/layoutY, sized to its bounded pref (autosize)
            for (JXNativeNode child : children) {
                child.layout(node.getX() + (int) Math.round(child.layoutX()), node.getY() + (int) Math.round(child.layoutY()),
                        (int) ceil(bounded(child.minWidth(), child.prefWidth(), child.maxWidth())),
                        (int) ceil(bounded(child.minHeight(), child.prefHeight(), child.maxHeight())));
            }
            return;
        }
        if (kind == JXNativeNode.ANCHOR) {
            layoutAnchor(node);
            return;
        }
        if (kind == JXNativeNode.FLOW || kind == JXNativeNode.TILE) {
            JXFlowLayout.layoutChildren(node);
            return;
        }
        if (kind == JXNativeNode.TITLED || kind == JXNativeNode.ACCORDION) {
            JXTitledLayout.layoutChildren(node);
            return;
        }
        if (kind == JXNativeNode.CONTROL) {
            JXControlLayout.layoutChildren(node);
            return;
        }
        String alignment = node.alignment();
        char vpos = alignment.charAt(0);
        char hpos = alignment.charAt(1);
        double width = node.getWidth();
        double height = node.getHeight();
        if (kind == JXNativeNode.COLUMN) {
            double[] padding = rounded(node.padding());
            double spacing = Math.round(node.spacing());
            double[] areas = new double[children.size()];
            for (int i = 0; i < areas.length; i++) {
                areas[i] = prefAreaHeight(children.get(i));
            }
            double content = adjust(children, areas, height - padding[TOP] - padding[BOTTOM], spacing, false);
            double y = padding[TOP] + offset(height - padding[TOP] - padding[BOTTOM], content, vpos);
            double contentWidth = width - padding[LEFT] - padding[RIGHT];
            for (int i = 0; i < areas.length; i++) {
                layoutInArea(node, children.get(i), padding[LEFT], y, contentWidth, areas[i],
                        node.fillWidth(), true, hpos, vpos);
                y += areas[i] + spacing;
            }
        } else if (kind == JXNativeNode.ROW) {
            double[] padding = rounded(node.padding());
            double spacing = Math.round(node.spacing());
            double[] areas = new double[children.size()];
            for (int i = 0; i < areas.length; i++) {
                areas[i] = prefAreaWidth(children.get(i));
            }
            double content = adjust(children, areas, width - padding[LEFT] - padding[RIGHT], spacing, true);
            double x = padding[LEFT] + offset(width - padding[LEFT] - padding[RIGHT], content, hpos);
            double contentHeight = height - padding[TOP] - padding[BOTTOM];
            for (int i = 0; i < areas.length; i++) {
                layoutInArea(node, children.get(i), x, padding[TOP], areas[i], contentHeight,
                        true, node.fillHeight(), hpos, vpos);
                x += areas[i] + spacing;
            }
        } else {
            double[] padding = node.padding();
            for (JXNativeNode child : children) {
                layoutInArea(node, child, padding[LEFT], padding[TOP], width - padding[LEFT] - padding[RIGHT],
                        height - padding[TOP] - padding[BOTTOM], true, true,
                        child.halignment() != 0 ? child.halignment() : hpos,
                        child.valignment() != 0 ? child.valignment() : vpos);
            }
        }
    }

    /** Region with Parent's defaults: min is the padding, pref spans the children at their layout position. */
    private static double[] paneSizes(JXNativeNode node) {
        double[] padding = node.padding();
        double minX = 0;
        double maxX = 0;
        double minY = 0;
        double maxY = 0;
        for (JXNativeNode child : node.getChildren()) {
            minX = Math.min(minX, child.layoutX());
            maxX = Math.max(maxX, child.layoutX() + bounded(child.minWidth(), child.prefWidth(), child.maxWidth()));
            minY = Math.min(minY, child.layoutY());
            maxY = Math.max(maxY, child.layoutY() + bounded(child.minHeight(), child.prefHeight(), child.maxHeight()));
        }
        double horizontal = padding[LEFT] + padding[RIGHT];
        double vertical = padding[TOP] + padding[BOTTOM];
        return new double[] {horizontal, horizontal + maxX - minX, MAX, vertical, vertical + maxY - minY, MAX};
    }

    /** AnchorPane.computeWidth / computeHeight without content bias. */
    private static double[] anchorSizes(JXNativeNode node) {
        double[] padding = rounded(node.padding());
        double minW = 0;
        double prefW = 0;
        double minH = 0;
        double prefH = 0;
        for (JXNativeNode child : node.getChildren()) {
            double left = anchor(child.leftAnchor());
            double right = anchor(child.rightAnchor());
            double top = anchor(child.topAnchor());
            double bottom = anchor(child.bottomAnchor());
            double x = !Double.isNaN(left) ? left : !Double.isNaN(right) ? 0 : child.layoutX();
            double y = !Double.isNaN(top) ? top : !Double.isNaN(bottom) ? 0 : child.layoutY();
            double r = Double.isNaN(right) ? 0 : right;
            double b = Double.isNaN(bottom) ? 0 : bottom;
            double prefWidth = ceil(bounded(child.minWidth(), child.prefWidth(), child.maxWidth()));
            double prefHeight = ceil(bounded(child.minHeight(), child.prefHeight(), child.maxHeight()));
            boolean stretchX = !Double.isNaN(left) && !Double.isNaN(right);
            boolean stretchY = !Double.isNaN(top) && !Double.isNaN(bottom);
            minW = Math.max(minW, x + (stretchX ? child.minWidth() : prefWidth) + r);
            prefW = Math.max(prefW, x + prefWidth + r);
            minH = Math.max(minH, y + (stretchY ? child.minHeight() : prefHeight) + b);
            prefH = Math.max(prefH, y + prefHeight + b);
        }
        double horizontal = padding[LEFT] + padding[RIGHT];
        double vertical = padding[TOP] + padding[BOTTOM];
        return new double[] {horizontal + minW, horizontal + prefW, MAX, vertical + minH, vertical + prefH, MAX};
    }

    /** AnchorPane.layoutChildren: anchored sides pin the child; both sides of an axis stretch it. */
    private static void layoutAnchor(JXNativeNode node) {
        double[] padding = rounded(node.padding());
        double width = node.getWidth();
        double height = node.getHeight();
        for (JXNativeNode child : node.getChildren()) {
            double left = anchor(child.leftAnchor());
            double right = anchor(child.rightAnchor());
            double top = anchor(child.topAnchor());
            double bottom = anchor(child.bottomAnchor());
            double w = !Double.isNaN(left) && !Double.isNaN(right)
                    ? width - padding[LEFT] - padding[RIGHT] - left - right
                    : ceil(bounded(child.minWidth(), child.prefWidth(), child.maxWidth()));
            double h = !Double.isNaN(top) && !Double.isNaN(bottom)
                    ? height - padding[TOP] - padding[BOTTOM] - top - bottom
                    : ceil(bounded(child.minHeight(), child.prefHeight(), child.maxHeight()));
            double x = !Double.isNaN(left) ? padding[LEFT] + left
                    : !Double.isNaN(right) ? width - padding[RIGHT] - right - w : child.layoutX();
            double y = !Double.isNaN(top) ? padding[TOP] + top
                    : !Double.isNaN(bottom) ? height - padding[BOTTOM] - bottom - h : child.layoutY();
            child.layout(node.getX() + (int) Math.round(x), node.getY() + (int) Math.round(y),
                    (int) Math.ceil(w), (int) Math.ceil(h));
        }
    }

    /** Region.snapPosition at scale 1; NaN (no anchor) stays NaN. */
    private static double anchor(double value) {
        return Double.isNaN(value) ? value : Math.round(value);
    }

    /** BorderPane.layoutChildren without content bias. */
    private static void layoutBorder(JXNativeNode node) {
        double[] padding = node.padding();
        double width = Math.max(node.getWidth(), node.minWidth());
        double height = Math.max(node.getHeight(), node.minHeight());
        double insideX = padding[LEFT];
        double insideY = padding[TOP];
        double insideWidth = width - insideX - padding[RIGHT];
        double insideHeight = height - insideY - padding[BOTTOM];

        double topHeight = 0;
        JXNativeNode top = slot(node, "top");
        if (top != null) {
            double[] m = rounded(top.margin());
            double w = insideWidth - m[LEFT] - m[RIGHT];
            double h = Math.min(ceil(top.prefHeight()), insideHeight - m[TOP] - m[BOTTOM]);
            double childW = ceil(bounded(top.minWidth(), w, top.maxWidth()));
            double childH = ceil(bounded(top.minHeight(), h, top.maxHeight()));
            topHeight = m[BOTTOM] + childH + m[TOP];
            position(node, top, insideX, insideY, insideWidth, topHeight, childW, childH, m, 'L', 'T');
        }
        double bottomHeight = 0;
        JXNativeNode bottom = slot(node, "bottom");
        if (bottom != null) {
            double[] m = rounded(bottom.margin());
            double w = insideWidth - m[LEFT] - m[RIGHT];
            double h = Math.min(ceil(bottom.prefHeight()), insideHeight - topHeight - m[TOP] - m[BOTTOM]);
            double childW = ceil(bounded(bottom.minWidth(), w, bottom.maxWidth()));
            double childH = ceil(bounded(bottom.minHeight(), h, bottom.maxHeight()));
            bottomHeight = m[BOTTOM] + childH + m[TOP];
            position(node, bottom, insideX, insideY + insideHeight - bottomHeight, insideWidth, bottomHeight,
                    childW, childH, m, 'L', 'B');
        }
        double middleHeight = insideHeight - topHeight - bottomHeight;
        double leftWidth = 0;
        JXNativeNode left = slot(node, "left");
        if (left != null) {
            double[] m = rounded(left.margin());
            double w = Math.min(ceil(left.prefWidth()), insideWidth - m[LEFT] - m[RIGHT]);
            double h = middleHeight - m[TOP] - m[BOTTOM];
            double childW = ceil(bounded(left.minWidth(), w, left.maxWidth()));
            double childH = ceil(bounded(left.minHeight(), h, left.maxHeight()));
            leftWidth = m[LEFT] + childW + m[RIGHT];
            position(node, left, insideX, insideY + topHeight, leftWidth, middleHeight, childW, childH, m, 'L', 'T');
        }
        double rightWidth = 0;
        JXNativeNode right = slot(node, "right");
        if (right != null) {
            double[] m = rounded(right.margin());
            double w = Math.min(ceil(right.prefWidth()), insideWidth - leftWidth - m[LEFT] - m[RIGHT]);
            double h = middleHeight - m[TOP] - m[BOTTOM];
            double childW = ceil(bounded(right.minWidth(), w, right.maxWidth()));
            double childH = ceil(bounded(right.minHeight(), h, right.maxHeight()));
            rightWidth = m[LEFT] + childW + m[RIGHT];
            position(node, right, insideX + insideWidth - rightWidth, insideY + topHeight, rightWidth, middleHeight,
                    childW, childH, m, 'R', 'T');
        }
        JXNativeNode center = slot(node, "center");
        if (center != null) {
            layoutInArea(node, center, insideX + leftWidth, insideY + topHeight,
                    insideWidth - leftWidth - rightWidth, middleHeight, true, true, 'C', 'C');
        }
    }

    /** VBox.adjustAreaHeights / HBox.adjustAreaWidths. Returns the content length after growing or shrinking. */
    private static double adjust(List<JXNativeNode> children, double[] areas, double length, double spacing,
                                 boolean horizontal) {
        double content = spacing * (children.size() - 1);
        for (double area : areas) {
            content += area;
        }
        double extra = length - content;
        if (extra != 0) {
            double remaining = growOrShrink(children, areas, JXNativeNode.ALWAYS, extra, horizontal);
            remaining = growOrShrink(children, areas, JXNativeNode.SOMETIMES, remaining, horizontal);
            content += extra - remaining;
        }
        return content;
    }

    /** VBox.growOrShrinkAreaHeights / HBox.growOrShrinkAreaWidths at snap scale 1. */
    private static double growOrShrink(List<JXNativeNode> children, double[] used, int priority, double extra,
                                       boolean horizontal) {
        if (Math.abs(extra) < 1) {
            return extra; // JavaFX's loop would not run either
        }
        boolean shrinking = extra < 0;
        int adjusting = 0;
        double[] limits = new double[used.length];
        for (int i = 0; i < used.length; i++) {
            JXNativeNode child = children.get(i);
            if (shrinking) {
                limits[i] = horizontal ? minAreaWidth(child) : minAreaHeight(child);
                adjusting++;
            } else if (child.growPriority(horizontal) == priority) {
                limits[i] = horizontal ? maxAreaWidth(child) : maxAreaHeight(child);
                adjusting++;
            } else {
                limits[i] = -1;
            }
        }
        double available = extra;
        outer:
        while (Math.abs(available) >= 1 && adjusting > 0) {
            double portion = portion(available / adjusting);
            if (portion == 0) {
                portion = Math.signum(available);
            }
            for (int i = 0; i < used.length; i++) {
                if (limits[i] == -1) {
                    continue;
                }
                double limit = limits[i] - used[i];
                double change = Math.abs(limit) <= Math.abs(portion) ? limit : portion;
                used[i] += change;
                available -= change;
                if (Math.abs(available) < 1) {
                    break outer;
                }
                if (Math.abs(change) < Math.abs(portion)) {
                    limits[i] = -1;
                    adjusting--;
                }
            }
        }
        return available;
    }

    /** Region.layoutInArea without baseline: size within min and max inside the margin, then align and snap. */
    static void layoutInArea(JXNativeNode parent, JXNativeNode child, double areaX, double areaY,
                                     double areaWidth, double areaHeight, boolean fillWidth, boolean fillHeight,
                                     char hpos, char vpos) {
        double[] m = rounded(child.margin());
        double innerWidth = areaWidth - m[LEFT] - m[RIGHT];
        double innerHeight = areaHeight - m[TOP] - m[BOTTOM];
        double w = ceil(bounded(child.minWidth(), fillWidth ? innerWidth : Math.min(innerWidth, child.prefWidth()),
                child.maxWidth()));
        double h = ceil(bounded(child.minHeight(), fillHeight ? innerHeight : Math.min(innerHeight, child.prefHeight()),
                child.maxHeight()));
        position(parent, child, areaX, areaY, areaWidth, areaHeight, w, h, m, hpos, vpos);
    }

    /**
     * Region.positionInArea: place an already sized child inside its area and margin. In a border
     * parent the child's own halignment/valignment (BorderPane.setAlignment) wins over the slot default.
     */
    private static void position(JXNativeNode parent, JXNativeNode child, double areaX, double areaY,
                                 double areaWidth, double areaHeight, double w, double h, double[] m,
                                 char hpos, char vpos) {
        if (parent.kind() == JXNativeNode.BORDER) {
            hpos = child.halignment() != 0 ? child.halignment() : hpos;
            vpos = child.valignment() != 0 ? child.valignment() : vpos;
        }
        double x = Math.round(areaX + m[LEFT] + offset(areaWidth - m[LEFT] - m[RIGHT], w, hpos));
        double y = Math.round(areaY + m[TOP] + offset(areaHeight - m[TOP] - m[BOTTOM], h, vpos));
        child.layout(parent.getX() + (int) x, parent.getY() + (int) y, (int) w, (int) h);
    }

    private static JXNativeNode slot(JXNativeNode border, String position) {
        for (JXNativeNode child : border.getChildren()) {
            if (position.equals(child.position())) {
                return child;
            }
        }
        return null;
    }

    /* Region.computeChild{Min,Pref,Max}Area{Width,Height}: child size snapped up, plus its margin. */

    static double minAreaWidth(JXNativeNode child) {
        if (child == null) {
            return 0;
        }
        double[] m = rounded(child.margin());
        return m[LEFT] + ceil(child.minWidth()) + m[RIGHT];
    }

    static double prefAreaWidth(JXNativeNode child) {
        if (child == null) {
            return 0;
        }
        double[] m = rounded(child.margin());
        return m[LEFT] + ceil(bounded(child.minWidth(), child.prefWidth(), child.maxWidth())) + m[RIGHT];
    }

    private static double maxAreaWidth(JXNativeNode child) {
        if (child.maxWidth() == MAX) {
            return MAX;
        }
        double[] m = rounded(child.margin());
        return m[LEFT] + ceil(bounded(child.minWidth(), child.maxWidth(), MAX)) + m[RIGHT];
    }

    static double minAreaHeight(JXNativeNode child) {
        if (child == null) {
            return 0;
        }
        double[] m = rounded(child.margin());
        return m[TOP] + ceil(child.minHeight()) + m[BOTTOM];
    }

    static double prefAreaHeight(JXNativeNode child) {
        if (child == null) {
            return 0;
        }
        double[] m = rounded(child.margin());
        return m[TOP] + ceil(bounded(child.minHeight(), child.prefHeight(), child.maxHeight())) + m[BOTTOM];
    }

    private static double maxAreaHeight(JXNativeNode child) {
        if (child.maxHeight() == MAX) {
            return MAX;
        }
        double[] m = rounded(child.margin());
        return m[TOP] + ceil(bounded(child.minHeight(), child.maxHeight(), MAX)) + m[BOTTOM];
    }

    /** Region.computeXOffset / computeYOffset: 'L'/'T' start, 'C' center, 'R'/'B' end. */
    private static double offset(double length, double content, char pos) {
        if (pos == 'C') {
            return (length - content) / 2;
        }
        if (pos == 'R' || pos == 'B') {
            return length - content;
        }
        return 0;
    }

    /** Region.boundedSize: pref clamped to [min, max], min wins over max. */
    static double bounded(double min, double pref, double max) {
        double a = pref >= min ? pref : min;
        double b = min >= max ? min : max;
        return a <= b ? a : b;
    }

    /** Region.snapPortion at scale 1: floor for positive values, ceil for negative. */
    private static double portion(double value) {
        return value > 0 ? Math.floor(value) : Math.ceil(value);
    }

    static double ceil(double value) {
        return value == MAX ? MAX : Math.ceil(value);
    }

    /** Region.snapSpace at scale 1. Most insets are whole pixels, so this rarely allocates. */
    static double[] rounded(double[] sides) {
        if (sides == JXNativeNode.NO_INSETS) {
            return sides;
        }
        boolean whole = true;
        for (double side : sides) {
            whole &= side == Math.rint(side);
        }
        if (whole) {
            return sides;
        }
        return new double[] {Math.round(sides[0]), Math.round(sides[1]), Math.round(sides[2]), Math.round(sides[3])};
    }

    private static double nonNegative(double value) {
        return value < 0 ? 0 : value;
    }

    /**
     * Parses a JavaFX Pos name ("TOP_LEFT", "center", "bottom-right", "CENTER_LEFT") into two
     * letters: vertical T/C/B, horizontal L/C/R.
     */
    static String parseAlignment(Object value, String fallback) {
        if (!(value instanceof String)) {
            return fallback;
        }
        String pos = ((String) value).trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if ("CENTER".equals(pos)) {
            return "CC";
        }
        int split = pos.indexOf('_');
        if (split < 0) {
            return fallback;
        }
        String vertical = pos.substring(0, split);
        String horizontal = pos.substring(split + 1);
        char v = "TOP".equals(vertical) ? 'T' : "CENTER".equals(vertical) ? 'C' : "BOTTOM".equals(vertical) ? 'B' : 0;
        char h = "LEFT".equals(horizontal) ? 'L' : "CENTER".equals(horizontal) ? 'C' : "RIGHT".equals(horizontal) ? 'R' : 0;
        return v == 0 || h == 0 ? fallback : new String(new char[] {v, h});
    }
}
