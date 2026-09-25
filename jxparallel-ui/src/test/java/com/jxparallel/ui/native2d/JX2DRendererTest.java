package com.jxparallel.ui.native2d;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.controls.JXControls;
import com.jxparallel.ui.layout.JXLayouts;
import com.jxparallel.ui.text.JXTextEngine;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JX2DRendererTest {
    @Test
    void shouldMountAndLayoutNativeTreeWithoutJavaFx() {
        JXElement element = JXLayouts.column(8,
                JXElement.text("Customers"),
                JXControls.button("Load", null));

        JXNativeNode node = JXSkiaRenderer.mount(element);

        JXSkiaRenderer.layout(node, 320, 200);

        assertNotNull(node);
        assertEquals("column", node.getType());
        assertEquals(2, node.getChildren().size());
        assertEquals(320, node.getWidth());
        assertEquals(200, node.getHeight());
    }

    @Test
    void columnShouldGiveChildrenTheirPreferredHeightAndFullWidth() {
        JXNativeNode node = JXSkiaRenderer.mount(JXLayouts.column(8,
                JXElement.text("Customers"),
                JXControls.button("Load", null)));

        JXSkiaRenderer.layout(node, 320, 200);

        JXNativeNode text = node.getChildren().get(0);
        JXNativeNode button = node.getChildren().get(1);
        assertEquals(line(), text.getHeight());
        assertEquals(line() + 8, button.getY());
        assertEquals(buttonHeight(), button.getHeight());
        assertEquals(320, button.getWidth());
    }

    @Test
    void shouldMountAllNativeControlShapes() {
        JXNativeNode node = JXSkiaRenderer.mount(JXControls.button("Load", null));

        JXSkiaRenderer.layout(node, 160, 40);

        assertEquals("button", node.getType());
        assertEquals(160, node.getWidth());
        assertEquals(40, node.getHeight());
        assertNotNull(node.getProperty("label"));
    }

    @Test
    void controlsAreSizedFromTheirMeasuredText() {
        JXNativeNode row = JXSkiaRenderer.mount(JXLayouts.row(0,
                JXControls.button("Save", null), JXControls.button("Save all changes", null),
                JXControls.checkbox("Active", true), JXElement.text("Customers")));
        JXSkiaRenderer.layout(row, 2000, 100);

        assertEquals(textWidth("Save") + 2 * JXNativeNode.PAD_X, row.getChildren().get(0).getWidth());
        assertEquals(textWidth("Save all changes") + 2 * JXNativeNode.PAD_X, row.getChildren().get(1).getWidth());
        assertEquals(JXNativeNode.CHECK_BOX + JXNativeNode.CHECK_GAP + textWidth("Active"), row.getChildren().get(2).getWidth());
        assertEquals(textWidth("Customers"), row.getChildren().get(3).getWidth());
    }

    @Test
    void fieldsUseJavaFxColumnCountsNotTheirContent() {
        JXNativeNode row = JXSkiaRenderer.mount(JXLayouts.row(0,
                JXControls.input("", "Name"), JXControls.input("a much longer value than fits", "Name")));
        JXSkiaRenderer.layout(row, 2000, 100);

        int expected = JXNativeNode.FIELD_COLUMNS * textWidth("W") + 2 * JXNativeNode.PAD_X;
        assertEquals(expected, row.getChildren().get(0).getWidth());
        assertEquals(expected, row.getChildren().get(1).getWidth());
    }

    /** Known answers, worked out by hand: reconcile-vs-mount properties cannot catch a wrong size formula. */
    @Test
    void nestedRowsAndColumnsUseTheirPreferredSizes() {
        JXNativeNode root = JXSkiaRenderer.mount(JXLayouts.row(8,
                JXControls.button("A", null),
                JXLayouts.row(4, JXControls.button("B", null), JXControls.checkbox("C", false)),
                JXLayouts.column(6, JXElement.text("D"), JXElement.of("textarea"))));
        JXSkiaRenderer.layout(root, 1000, 300);

        int a = buttonWidth("A");
        int b = buttonWidth("B");
        int c = JXNativeNode.CHECK_BOX + JXNativeNode.CHECK_GAP + textWidth("C");
        int area = JXNativeNode.AREA_COLUMNS * textWidth("W") + 2 * JXNativeNode.PAD_X;
        JXNativeNode innerRow = root.getChildren().get(1);
        JXNativeNode innerColumn = root.getChildren().get(2);
        assertEquals(a + 8, innerRow.getX());
        assertEquals(b + 4 + c, innerRow.getWidth());
        assertEquals(a + 8 + b + 4, innerRow.getChildren().get(1).getX());
        assertEquals(a + 8 + b + 4 + c + 8, innerColumn.getX());
        assertEquals(Math.min(area, 1000 - innerColumn.getX()), innerColumn.getWidth(), "widest child, cut at the edge");
        assertEquals(300, innerColumn.getHeight(), "row children fill the cross axis");
        assertEquals(line() + 6, innerColumn.getChildren().get(1).getY());

        JXNativeNode column = JXSkiaRenderer.mount(JXLayouts.column(10,
                JXLayouts.column(6, JXElement.text("D"), JXElement.of("textarea")),
                JXControls.button("E", null)));
        JXSkiaRenderer.layout(column, 400, 800);
        int areaHeight = JXNativeNode.AREA_ROWS * line() + 2 * JXNativeNode.PAD_Y;
        assertEquals(line() + 6 + areaHeight + 10, column.getChildren().get(1).getY());
    }

    @Test
    void hitTestIncludesTheLeftEdgeAndExcludesTheRightEdge() {
        JXNativeNode root = JXSkiaRenderer.mount(JXLayouts.row(8, JXControls.button("A", null)));
        JXSkiaRenderer.layout(root, 300, 40);
        JXNativeNode button = root.getChildren().get(0);
        int right = button.getWidth();

        assertEquals(button, root.hitTest(0, 0));
        assertEquals(button, root.hitTest(right - 1, 39));
        assertEquals(root, root.hitTest(right, 5), "x == width is outside the button");
        assertEquals(null, root.hitTest(300, 5));
        assertEquals(null, root.hitTest(-1, 5));
    }

    private static int textWidth(String text) {
        return (int) Math.ceil(JXTextEngine.get().width(text, JXTextEngine.DEFAULT_SIZE));
    }

    private static int line() {
        return (int) Math.ceil(JXTextEngine.get().lineHeight(JXTextEngine.DEFAULT_SIZE));
    }

    private static int buttonWidth(String label) {
        return textWidth(label) + 2 * JXNativeNode.PAD_X;
    }

    private static int buttonHeight() {
        return line() + 2 * JXNativeNode.PAD_Y;
    }
}
