package com.jxparallel.ui.native2d;

import java.util.List;
import java.util.Locale;

/**
 * The shape of the mouse pointer over a window. {@link #at} decides it from what is under the pointer, without GLFW, so it
 * can be tested; {@link JXWindow} shows it with the matching GLFW standard cursor.
 *
 * <p>An element's {@code cursor} prop (JXML {@code cursor}) wins, the deepest element that has one; otherwise an enabled text
 * input shows the I-beam, an enabled hyperlink the hand, the divider of a split pane that can be dragged (it has an
 * {@code onChange}) the horizontal or vertical resize cursor, and everything else the arrow.
 */
public enum JXCursor {
    ARROW, IBEAM, CROSSHAIR, HAND, RESIZE_H, RESIZE_V;

    /**
     * The shape a {@code cursor} prop names: {@code default}, {@code text}, {@code crosshair}, {@code hand},
     * {@code h-resize} or {@code v-resize}, any case, {@code -} or {@code _}; also the CSS and JavaFX synonyms
     * ({@code arrow}, {@code ibeam}, {@code pointer}, {@code e-resize}, {@code ew-resize}, {@code n-resize}, ...).
     * Null when the text names none.
     */
    public static JXCursor parse(Object name) {
        if (!(name instanceof String)) {
            return null;
        }
        switch (((String) name).trim().toLowerCase(Locale.ROOT).replace('_', '-')) {
            case "default":
            case "arrow":
                return ARROW;
            case "text":
            case "ibeam":
            case "i-beam":
                return IBEAM;
            case "crosshair":
                return CROSSHAIR;
            case "hand":
            case "pointer":
                return HAND;
            case "h-resize":
            case "resize-h":
            case "e-resize":
            case "w-resize":
            case "ew-resize":
            case "col-resize":
                return RESIZE_H;
            case "v-resize":
            case "resize-v":
            case "n-resize":
            case "s-resize":
            case "ns-resize":
            case "row-resize":
                return RESIZE_V;
            default:
                return null;
        }
    }

    /**
     * The shape at window position (x, y), logical pixels: see the class comment. What is open over the window (a menu, a
     * popup, a dialog) is found first, like for a click.
     */
    public static JXCursor at(JXNativeNode root, int x, int y) {
        if (root == null || x < 0 || y < 0) {
            return ARROW;
        }
        return of(root.hitPath(x, y), x, y);
    }

    /** The shape for a path of nodes from the root down to the one under (x, y), as {@link JXNativeNode#hitPath} returns it. */
    static JXCursor of(List<JXNativeNode> path, int x, int y) {
        for (int i = path.size() - 1; i >= 0; i--) {
            JXNativeNode node = path.get(i);
            JXCursor written = parse(node.getProperty("cursor"));
            if (written != null) {
                return written;
            }
            if (Boolean.TRUE.equals(node.getProperty("disabled"))) {
                continue;
            }
            switch (node.getType()) {
                case "input":
                case "password":
                case "textarea":
                    return IBEAM;
                case "hyperlink":
                    return HAND;
                case "splitpane":
                    if (node.getProperty("onChange") instanceof java.util.function.Consumer
                            && JXChangeEvents.dividerAt(node, x, y) >= 0) {
                        return node.vertical() ? RESIZE_V : RESIZE_H;
                    }
                    break;
                default:
                    break;
            }
        }
        return ARROW;
    }
}
