package com.jxparallel.ui.native2d;

import java.util.ArrayList;
import java.util.List;

/**
 * JavaFX 21 FlowPane ({@code flow}) and TilePane ({@code tile}).
 *
 * <p>Both have a content bias in JavaFX (height depends on width when horizontal). The native
 * containers do not pass that bias down yet, so a flow or tile nested in a VBox, HBox, GridPane or
 * BorderPane gets its unbiased preferred height (the one for {@code prefWrapLength} or
 * {@code prefColumns}); as the window root or inside a pane or stack it matches JavaFX exactly.
 *
 * <p>Props: {@code orientation} ("horizontal" or "vertical"), {@code hgap}, {@code vgap},
 * {@code alignment}, {@code padding}; flow: {@code prefWrapLength} (400), {@code columnHalignment}
 * (LEFT), {@code rowValignment} (CENTER); tile: {@code prefColumns}, {@code prefRows} (5),
 * {@code prefTileWidth}, {@code prefTileHeight}, {@code tileAlignment} (CENTER).
 */
final class JXFlowLayout {
    private static final double MAX = JXBoxLayout.MAX;

    private JXFlowLayout() {
    }

    private static boolean vertical(JXNativeNode node) {
        return "vertical".equalsIgnoreCase(String.valueOf(node.getProperty("orientation")));
    }

    private static double number(JXNativeNode node, String key, double fallback) {
        Object value = node.getProperty(key);
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }

    private static char position(JXNativeNode node, String key, char fallback) {
        Object value = node.getProperty(key);
        return value instanceof String && !((String) value).isEmpty()
                ? Character.toUpperCase(((String) value).charAt(0)) : fallback;
    }

    static double[] sizes(JXNativeNode node) {
        return node.kind() == JXNativeNode.TILE ? tileSizes(node) : flowSizes(node);
    }

    static void layoutChildren(JXNativeNode node) {
        if (node.kind() == JXNativeNode.TILE) {
            layoutTile(node);
        } else {
            layoutFlow(node);
        }
    }

    /* ---------------------------------------------------------------- FlowPane */

    private static final class Rect {
        JXNativeNode node;
        double x;
        double y;
        double width;
        double height;
    }

    private static final class Run {
        final List<Rect> rects = new ArrayList<Rect>();
        double width;
        double height;
    }

    /** FlowPane.getRuns: fill a run until the next child would pass maxRunLength. */
    private static List<Run> runs(JXNativeNode node, double maxRunLength) {
        boolean vertical = vertical(node);
        double vgap = Math.round(number(node, "vgap", 0));
        double hgap = Math.round(number(node, "hgap", 0));
        List<Run> runs = new ArrayList<Run>();
        double runLength = 0;
        double runOffset = 0;
        Run run = new Run();
        for (JXNativeNode child : node.getChildren()) {
            Rect rect = new Rect();
            rect.node = child;
            rect.width = JXBoxLayout.prefAreaWidth(child);
            rect.height = JXBoxLayout.prefAreaHeight(child);
            double length = vertical ? rect.height : rect.width;
            if (runLength + length > maxRunLength && runLength > 0) {
                normalize(run, runOffset, vertical, hgap, vgap);
                runOffset += vertical ? run.width + hgap : run.height + vgap;
                runs.add(run);
                runLength = 0;
                run = new Run();
            }
            if (vertical) {
                rect.y = runLength;
                runLength += rect.height + vgap;
            } else {
                rect.x = runLength;
                runLength += rect.width + hgap;
            }
            run.rects.add(rect);
        }
        normalize(run, runOffset, vertical, hgap, vgap);
        runs.add(run);
        return runs;
    }

    private static void normalize(Run run, double runOffset, boolean vertical, double hgap, double vgap) {
        if (!vertical) {
            run.width = (run.rects.size() - 1) * hgap;
            double height = 0;
            for (Rect rect : run.rects) {
                run.width += rect.width;
                rect.y = runOffset;
                height = Math.max(height, JXBoxLayout.prefAreaHeight(rect.node));
            }
            run.height = height;
        } else {
            run.height = (run.rects.size() - 1) * vgap;
            double width = 0;
            for (Rect rect : run.rects) {
                run.height += rect.height;
                rect.x = runOffset;
                width = Math.max(width, rect.width);
            }
            run.width = width;
        }
    }

    private static double contentWidth(List<Run> runs, boolean vertical, double hgap) {
        double width = vertical ? (runs.size() - 1) * hgap : 0;
        for (Run run : runs) {
            width = vertical ? width + run.width : Math.max(width, run.width);
        }
        return width;
    }

    private static double contentHeight(List<Run> runs, boolean vertical, double vgap) {
        double height = vertical ? 0 : (runs.size() - 1) * vgap;
        for (Run run : runs) {
            height = vertical ? Math.max(height, run.height) : height + run.height;
        }
        return height;
    }

    /** FlowPane.computeMin/PrefWidth/Height for width and height -1 (see the class comment on bias). */
    private static double[] flowSizes(JXNativeNode node) {
        double[] padding = node.padding();
        boolean vertical = vertical(node);
        double wrap = number(node, "prefWrapLength", 400);
        double hgap = Math.round(number(node, "hgap", 0));
        double vgap = Math.round(number(node, "vgap", 0));
        List<Run> runs = runs(node, wrap);
        double prefW;
        double prefH;
        if (!vertical) {
            prefW = padding[3] + Math.ceil(Math.max(wrap, contentWidth(runs, false, hgap))) + padding[1];
            prefH = padding[0] + contentHeight(runs, false, vgap) + padding[2];
        } else {
            prefW = padding[3] + contentWidth(runs, true, hgap) + padding[1];
            prefH = padding[0] + Math.ceil(Math.max(wrap, contentHeight(runs, true, vgap))) + padding[2];
        }
        double maxPrefWidth = 0;
        double maxPrefHeight = 0;
        for (JXNativeNode child : node.getChildren()) {
            maxPrefWidth = Math.max(maxPrefWidth, child.prefWidth());
            maxPrefHeight = Math.max(maxPrefHeight, child.prefHeight());
        }
        double minW = vertical ? prefW : padding[3] + Math.ceil(maxPrefWidth) + padding[1];
        double minH = vertical ? padding[0] + Math.ceil(maxPrefHeight) + padding[2] : prefH;
        return new double[] {minW, prefW, MAX, minH, prefH, MAX};
    }

    private static void layoutFlow(JXNativeNode node) {
        double[] padding = node.padding();
        boolean vertical = vertical(node);
        double hgap = Math.round(number(node, "hgap", 0));
        double vgap = Math.round(number(node, "vgap", 0));
        double insideWidth = node.getWidth() - padding[3] - padding[1];
        double insideHeight = node.getHeight() - padding[0] - padding[2];
        List<Run> runs = runs(node, vertical ? insideHeight : insideWidth);
        String alignment = node.alignment();
        char columnHalign = position(node, "columnHalignment", 'L');
        char rowValign = position(node, "rowValignment", 'C');
        for (Run run : runs) {
            double x = padding[3] + offset(insideWidth, vertical ? contentWidth(runs, true, hgap) : run.width,
                    alignment.charAt(1));
            double y = padding[0] + offset(insideHeight, vertical ? run.height : contentHeight(runs, false, vgap),
                    alignment.charAt(0));
            for (Rect rect : run.rects) {
                JXBoxLayout.layoutInArea(node, rect.node, x + rect.x, y + rect.y, vertical ? run.width : rect.width,
                        vertical ? rect.height : run.height, true, true, columnHalign, rowValign);
            }
        }
    }

    /* ---------------------------------------------------------------- TilePane */

    private static double tileWidth(JXNativeNode node) {
        double pref = number(node, "prefTileWidth", -1);
        if (pref >= 0) {
            return Math.ceil(pref);
        }
        double max = 0;
        for (JXNativeNode child : node.getChildren()) {
            max = Math.max(max, JXBoxLayout.prefAreaWidth(child));
        }
        return Math.ceil(max);
    }

    private static double tileHeight(JXNativeNode node) {
        double pref = number(node, "prefTileHeight", -1);
        if (pref >= 0) {
            return Math.ceil(pref);
        }
        double max = 0;
        for (JXNativeNode child : node.getChildren()) {
            max = Math.max(max, JXBoxLayout.prefAreaHeight(child));
        }
        return Math.ceil(max);
    }

    private static int other(int nodes, int cells) {
        return (int) Math.ceil((double) nodes / (double) Math.max(1, cells));
    }

    private static int count(double length, double tile, double gap) {
        return Math.max(1, (int) ((length + gap) / (tile + gap)));
    }

    private static double content(int cells, double tile, double gap) {
        return cells == 0 ? 0 : cells * tile + (cells - 1) * gap;
    }

    /** TilePane.computeMin/PrefWidth/Height for width and height -1. */
    private static double[] tileSizes(JXNativeNode node) {
        double[] padding = JXBoxLayout.rounded(node.padding());
        boolean vertical = vertical(node);
        int n = node.getChildren().size();
        int prefColumns = (int) number(node, "prefColumns", 5);
        int prefRows = (int) number(node, "prefRows", 5);
        double hgap = Math.round(number(node, "hgap", 0));
        double vgap = Math.round(number(node, "vgap", 0));
        double tileW = tileWidth(node);
        double tileH = tileHeight(node);
        int columns = vertical ? other(n, prefRows) : prefColumns;
        int rows = vertical ? prefRows : other(n, prefColumns);
        double prefW = padding[3] + content(columns, tileW, hgap) + padding[1];
        double prefH = padding[0] + content(rows, tileH, vgap) + padding[2];
        double[] raw = node.padding();
        double minW = vertical ? prefW : raw[3] + tileW + raw[1];
        double minH = vertical ? raw[0] + tileH + raw[2] : prefH;
        return new double[] {minW, prefW, MAX, minH, prefH, MAX};
    }

    private static void layoutTile(JXNativeNode node) {
        List<JXNativeNode> children = node.getChildren();
        double[] padding = JXBoxLayout.rounded(node.padding());
        boolean vertical = vertical(node);
        String alignment = node.alignment();
        char vpos = alignment.charAt(0);
        char hpos = alignment.charAt(1);
        double hgap = Math.round(number(node, "hgap", 0));
        double vgap = Math.round(number(node, "vgap", 0));
        double insideWidth = node.getWidth() - padding[3] - padding[1];
        double insideHeight = node.getHeight() - padding[0] - padding[2];
        double tileW = Math.min(tileWidth(node), insideWidth);
        double tileH = Math.min(tileHeight(node), insideHeight);
        int columns;
        int rows;
        int lastRowRemainder = 0;
        int lastColumnRemainder = 0;
        if (!vertical) {
            columns = count(insideWidth, tileW, hgap);
            rows = other(children.size(), columns);
            lastRowRemainder = hpos != 'L' ? columns - (columns * rows - children.size()) : 0;
        } else {
            rows = count(insideHeight, tileH, vgap);
            columns = other(children.size(), rows);
            lastColumnRemainder = vpos != 'T' ? rows - (columns * rows - children.size()) : 0;
        }
        double rowX = padding[3] + offset(insideWidth, content(columns, tileW, hgap), hpos);
        double columnY = padding[0] + offset(insideHeight, content(rows, tileH, vgap), vpos);
        double lastRowX = lastRowRemainder > 0
                ? padding[3] + offset(insideWidth, content(lastRowRemainder, tileW, hgap), hpos) : rowX;
        double lastColumnY = lastColumnRemainder > 0
                ? padding[0] + offset(insideHeight, content(lastColumnRemainder, tileH, vgap), vpos) : columnY;
        String tileAlignment = JXBoxLayout.parseAlignment(node.getProperty("tileAlignment"), "CC");
        int r = 0;
        int c = 0;
        for (JXNativeNode child : children) {
            double x = (r == rows - 1 ? lastRowX : rowX) + c * (tileW + hgap);
            double y = (c == columns - 1 ? lastColumnY : columnY) + r * (tileH + vgap);
            char h = child.halignment() != 0 ? child.halignment() : tileAlignment.charAt(1);
            char v = child.valignment() != 0 ? child.valignment() : tileAlignment.charAt(0);
            JXBoxLayout.layoutInArea(node, child, x, y, tileW, tileH, true, true, h, v);
            if (!vertical) {
                if (++c == columns) {
                    c = 0;
                    r++;
                }
            } else if (++r == rows) {
                r = 0;
                c++;
            }
        }
    }

    private static double offset(double length, double content, char pos) {
        if (pos == 'C') {
            return (length - content) / 2;
        }
        if (pos == 'R' || pos == 'B') {
            return length - content;
        }
        return 0;
    }
}
