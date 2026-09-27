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

    /** Facts measured on JavaFX 21: in a VBox, Label and Button keep their width, TextField and TextArea stretch. */
    @Test
    void columnKeepsButtonsAtPreferredWidthAndStretchesFieldsLikeJavaFx() {
        JXNativeNode node = JXSkiaRenderer.mount(JXLayouts.column(8,
                JXElement.text("Customers"),
                JXControls.button("Load", null),
                JXControls.input("Ada", "Name"),
                JXElement.of("textarea")));

        JXSkiaRenderer.layout(node, 320, 600);

        assertEquals(size(text("Customers")), node.getChildren().get(0).getWidth());
        assertEquals(size(line()), node.getChildren().get(0).getHeight());
        assertEquals(size(line()) + 8, node.getChildren().get(1).getY());
        assertEquals(size(text("Load") + 2 * JXNativeNode.PAD_X), node.getChildren().get(1).getWidth());
        assertEquals(size(line() + 2 * JXNativeNode.PAD_Y), node.getChildren().get(1).getHeight());
        assertEquals(320, node.getChildren().get(2).getWidth());
        assertEquals(320, node.getChildren().get(3).getWidth());
    }

    /** In an HBox, JavaFX keeps a Button at its preferred height; a TextArea fills the height. */
    @Test
    void rowStretchesOnlyWhatJavaFxStretches() {
        JXNativeNode row = JXSkiaRenderer.mount(JXLayouts.row(0, JXControls.button("Load", null), JXElement.of("textarea")));

        JXSkiaRenderer.layout(row, 2000, 300);

        assertEquals(size(line() + 2 * JXNativeNode.PAD_Y), row.getChildren().get(0).getHeight());
        assertEquals(300, row.getChildren().get(1).getHeight());
    }

    @Test
    void shouldMountAllNativeControlShapes() {
        JXNativeNode node = JXSkiaRenderer.mount(JXControls.button("Load", null));

        JXSkiaRenderer.layout(node, 160, 40);

        assertEquals("button", node.getType());
        assertEquals(160, node.getWidth(), "the root takes the window size, like a Scene root");
        assertEquals(40, node.getHeight());
        assertNotNull(node.getProperty("label"));
    }

    @Test
    void controlsAreSizedFromTheirMeasuredText() {
        JXNativeNode row = JXSkiaRenderer.mount(JXLayouts.row(0,
                JXControls.button("Save", null), JXControls.button("Save all changes", null),
                JXControls.checkbox("Active", true), JXElement.text("Customers")));
        JXSkiaRenderer.layout(row, 2000, 100);

        assertEquals(size(text("Save") + 2 * JXNativeNode.PAD_X), row.getChildren().get(0).getWidth());
        assertEquals(size(text("Save all changes") + 2 * JXNativeNode.PAD_X), row.getChildren().get(1).getWidth());
        assertEquals(size(text("Active") + JXNativeNode.CHECK_BOX + JXNativeNode.CHECK_GAP), row.getChildren().get(2).getWidth());
        assertEquals(size(text("Customers")), row.getChildren().get(3).getWidth());
    }

    @Test
    void fieldsUseJavaFxColumnCountsNotTheirContent() {
        JXNativeNode row = JXSkiaRenderer.mount(JXLayouts.row(0,
                JXControls.input("", "Name"), JXControls.input("a much longer value than fits", "Name")));
        JXSkiaRenderer.layout(row, 2000, 100);

        int expected = size(JXNativeNode.FIELD_COLUMNS * text("W") + 2 * JXNativeNode.FIELD_PAD_X);
        assertEquals(expected, row.getChildren().get(0).getWidth());
        assertEquals(expected, row.getChildren().get(1).getWidth());
    }

    @Test
    void hitTestIncludesTheLeftEdgeAndExcludesTheRightEdge() {
        JXNativeNode root = JXSkiaRenderer.mount(JXLayouts.row(8, JXControls.button("A", null)));
        JXSkiaRenderer.layout(root, 300, 40);
        JXNativeNode button = root.getChildren().get(0);
        int right = button.getWidth();
        int bottom = button.getHeight();

        assertEquals(button, root.hitTest(0, 0));
        assertEquals(button, root.hitTest(right - 1, bottom - 1));
        assertEquals(root, root.hitTest(right, 5), "x == width is outside the button");
        assertEquals(root, root.hitTest(5, bottom), "y == height is outside the button");
        assertEquals(null, root.hitTest(300, 5));
        assertEquals(null, root.hitTest(-1, 5));
    }

    private static double text(String value) {
        return JXTextEngine.get().width(value, JXTextEngine.DEFAULT_SIZE);
    }

    private static double line() {
        return JXTextEngine.get().lineHeight(JXTextEngine.DEFAULT_SIZE);
    }

    /** JavaFX snaps sizes up to whole pixels. */
    private static int size(double value) {
        return (int) Math.ceil(value);
    }
}
