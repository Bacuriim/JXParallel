package com.jxparallel.ui.native2d;

import java.util.List;

import com.jxparallel.ui.text.JXTextEngine;

/**
 * TitledPane and Accordion (JavaFX 21 with Modena, measured on real JavaFX). A {@code titled} node
 * has a title bar and at most one child, its content; an {@code accordion} stacks {@code titled}
 * children and gives the expanded one the height the others leave.
 *
 * <p>TitledPane: the title bar is the label height plus 4 px above and below; it has 9 px at each
 * side and, when collapsible, a 15 px arrow button before the text. The content sits below the
 * title inside a 1 px border on the left, right and bottom. Min and pref width are the larger of
 * the title (full text, never an ellipsis) and the content plus its border; the max height is the
 * pref height. A collapsed pane is only its title bar.
 */
final class JXTitledLayout {
    static final int TITLE_PAD_X = 9;
    /** Arrow (8 px) plus the arrow button's right padding (7 px). */
    static final int ARROW_BUTTON = 15;
    /** Content border: left, right and bottom. */
    static final int CONTENT_BORDER = 1;

    private JXTitledLayout() {
    }

    static int titleHeight() {
        JXTextEngine text = JXTextEngine.get();
        return (int) Math.ceil(text.lineHeight(JXTextEngine.DEFAULT_SIZE) + 2 * JXNativeNode.PAD_Y);
    }

    /** x of the title text, from the node's left edge. */
    static int textOffset(JXNativeNode node) {
        return TITLE_PAD_X + (node.collapsible() ? ARROW_BUTTON : 0);
    }

    /**
     * The arrow's three corners (x0, y0, x1, y1, x2, y2) for a pane at (x, y): an 8 by 6 triangle at
     * the start of the title, pointing down when expanded and right (rotated about its centre) when collapsed.
     */
    static float[] arrow(JXNativeNode node, float x, float y) {
        float left = x + TITLE_PAD_X;
        float top = y + Math.round((titleHeight() - 6) / 2.0f);
        if (!node.collapsed()) {
            return new float[] {left, top, left + 8, top, left + 4, top + 6};
        }
        float cx = left + 4;
        float cy = top + 3;
        return new float[] {cx - 3, cy - 4, cx + 3, cy, cx - 3, cy + 4};
    }

    static double[] sizes(JXNativeNode node) {
        return node.kind() == JXNativeNode.ACCORDION ? accordionSizes(node) : titledSizes(node);
    }

    private static double[] titledSizes(JXNativeNode node) {
        double title = titleHeight();
        Object label = node.getProperty("label");
        double titleWidth = Math.ceil(2 * TITLE_PAD_X + (node.collapsible() ? ARROW_BUTTON : 0)
                + JXTextEngine.get().width(label == null ? "" : String.valueOf(label), JXTextEngine.DEFAULT_SIZE));
        JXNativeNode content = content(node);
        double border = 2 * CONTENT_BORDER;
        double minW = Math.max(titleWidth, JXBoxLayout.minAreaWidth(content) + border);
        double prefW = Math.max(titleWidth, JXBoxLayout.prefAreaWidth(content) + border);
        if (node.collapsed()) {
            return new double[] {minW, prefW, JXBoxLayout.MAX, title, title, title};
        }
        double minH = title + JXBoxLayout.minAreaHeight(content) + CONTENT_BORDER;
        double prefH = title + JXBoxLayout.prefAreaHeight(content) + CONTENT_BORDER;
        return new double[] {minW, prefW, JXBoxLayout.MAX, minH, prefH, prefH};
    }

    /** AccordionSkin: widths of the widest pane, heights of all panes stacked. */
    private static double[] accordionSizes(JXNativeNode node) {
        double minW = 0;
        double prefW = 0;
        double minH = 0;
        double prefH = 0;
        for (JXNativeNode pane : node.getChildren()) {
            minW = Math.max(minW, JXBoxLayout.minAreaWidth(pane));
            prefW = Math.max(prefW, JXBoxLayout.prefAreaWidth(pane));
            minH += JXBoxLayout.minAreaHeight(pane);
            prefH += JXBoxLayout.prefAreaHeight(pane);
        }
        return new double[] {minW, prefW, JXBoxLayout.MAX, minH, prefH, JXBoxLayout.MAX};
    }

    static void layoutChildren(JXNativeNode node) {
        if (node.kind() == JXNativeNode.ACCORDION) {
            layoutAccordion(node);
            return;
        }
        JXNativeNode content = content(node);
        if (content == null || node.collapsed()) {
            return; // a collapsed pane's content is neither painted nor hit, so it keeps its old bounds
        }
        int title = titleHeight();
        JXBoxLayout.layoutInArea(node, content, CONTENT_BORDER, title, node.getWidth() - 2 * CONTENT_BORDER,
                node.getHeight() - title - CONTENT_BORDER, true, true, 'C', 'C');
    }

    /**
     * Every pane gets the full width. Collapsed panes get their pref height; the first expanded
     * pane gets what is left, even past its max height, like AccordionSkin.
     */
    private static void layoutAccordion(JXNativeNode node) {
        List<JXNativeNode> panes = node.getChildren();
        JXNativeNode expanded = null;
        double others = 0;
        for (JXNativeNode pane : panes) {
            if (expanded == null && !pane.collapsed()) {
                expanded = pane;
            } else {
                others += JXBoxLayout.prefAreaHeight(pane);
            }
        }
        double y = 0;
        for (JXNativeNode pane : panes) {
            double h = pane == expanded ? Math.max(0, node.getHeight() - others) : JXBoxLayout.prefAreaHeight(pane);
            pane.layout(node.getX(), node.getY() + (int) y, node.getWidth(), (int) h);
            y += h;
        }
    }

    private static JXNativeNode content(JXNativeNode node) {
        List<JXNativeNode> children = node.getChildren();
        return children.isEmpty() ? null : children.get(0);
    }
}
