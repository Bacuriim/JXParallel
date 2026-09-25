package com.jxparallel.ui.native2d;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.controls.JXControls;
import com.jxparallel.ui.layout.JXLayouts;


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
        assertEquals(24, text.getHeight());
        assertEquals(24 + 8, button.getY());
        assertEquals(32, button.getHeight());
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

    /** Known answers, worked out by hand: reconcile-vs-mount properties cannot catch a wrong size formula. */
    @Test
    void nestedRowsAndColumnsUseTheirPreferredSizes() {
        JXNativeNode root = JXSkiaRenderer.mount(JXLayouts.row(8,
                JXControls.button("A", null),
                JXLayouts.row(4, JXControls.button("B", null), JXControls.checkbox("C", false)),
                JXLayouts.column(6, JXElement.text("D"), JXElement.of("textarea"))));
        JXSkiaRenderer.layout(root, 1000, 300);

        JXNativeNode innerRow = root.getChildren().get(1);
        JXNativeNode innerColumn = root.getChildren().get(2);
        assertEquals(128, innerRow.getX());
        assertEquals(120 + 4 + 160, innerRow.getWidth());
        assertEquals(128 + 120 + 4, innerRow.getChildren().get(1).getX());
        assertEquals(128 + 284 + 8, innerColumn.getX());
        assertEquals(240, innerColumn.getWidth(), "widest child");
        assertEquals(300, innerColumn.getHeight(), "row children fill the cross axis");
        assertEquals(24 + 6, innerColumn.getChildren().get(1).getY());

        JXNativeNode column = JXSkiaRenderer.mount(JXLayouts.column(10,
                JXLayouts.column(6, JXElement.text("D"), JXElement.of("textarea")),
                JXControls.button("E", null)));
        JXSkiaRenderer.layout(column, 400, 400);
        assertEquals(24 + 6 + 96 + 10, column.getChildren().get(1).getY());
    }

    @Test
    void hitTestIncludesTheLeftEdgeAndExcludesTheRightEdge() {
        JXNativeNode root = JXSkiaRenderer.mount(JXLayouts.row(8, JXControls.button("A", null)));
        JXSkiaRenderer.layout(root, 300, 40);
        JXNativeNode button = root.getChildren().get(0);

        assertEquals(button, root.hitTest(0, 0));
        assertEquals(button, root.hitTest(119, 39));
        assertEquals(root, root.hitTest(120, 5), "x == width is outside the button");
        assertEquals(null, root.hitTest(300, 5));
        assertEquals(null, root.hitTest(-1, 5));
    }
}
