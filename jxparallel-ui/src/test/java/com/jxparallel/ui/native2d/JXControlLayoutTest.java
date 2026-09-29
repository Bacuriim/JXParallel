package com.jxparallel.ui.native2d;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;
import com.jxparallel.ui.text.JXTextEngine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Composite controls with hand-computed answers (JavaFX 21 / Modena measurements in comments). */
class JXControlLayoutTest {
    private static JXElement region(double w, double h) {
        return JXElement.of("pane", JXProps.builder().set("prefWidth", w).set("prefHeight", h).build());
    }

    private static JXNativeNode laidOut(JXElement e, int w, int h) {
        JXNativeNode node = JXNativeNode.createBackendNode(e);
        node.layoutForBackend(w, h);
        return node;
    }

    private static double text(String s) {
        return JXTextEngine.get().width(s, JXTextEngine.DEFAULT_SIZE);
    }

    // ---- scroll pane -------------------------------------------------------------------------

    @Test
    void scrollPaneShowsBarsOnlyWhenTheContentOverflows() {
        // JavaFX: 150x150 pane, 300x400 content -> both bars, viewport 135x135
        JXNativeNode both = laidOut(JXElement.of("scroll", JXProps.empty(), region(300, 400)), 150, 150);
        JXControlLayout.ScrollGeometry g = JXControlLayout.scrollGeometry(both);
        assertTrue(g.vertical);
        assertTrue(g.horizontal);
        assertEquals(135, g.viewW, 0.001);
        assertEquals(135, g.viewH, 0.001);
        assertEquals(1 + 150 - 2 - 13, g.vx, 0.001);
        assertEquals(265, g.maxScrollY(), 0.001);

        JXNativeNode none = laidOut(JXElement.of("scroll", JXProps.empty(), region(100, 80)), 150, 150);
        JXControlLayout.ScrollGeometry n = JXControlLayout.scrollGeometry(none);
        assertFalse(n.vertical);
        assertFalse(n.horizontal);
        assertEquals(148, n.viewW, 0.001);
    }

    @Test
    void oneBarCanCauseTheOther() {
        // content 140 wide fits 148 but not 135: the vertical bar (needed for 400 high) forces the horizontal one
        JXNativeNode node = laidOut(JXElement.of("scroll", JXProps.empty(), region(140, 400)), 150, 150);
        JXControlLayout.ScrollGeometry g = JXControlLayout.scrollGeometry(node);
        assertTrue(g.vertical);
        assertTrue(g.horizontal);
    }

    @Test
    void policiesAndFitToWidth() {
        JXProps props = JXProps.builder().set("vbarPolicy", "ALWAYS").set("hbarPolicy", "NEVER").set("fitToWidth", true).build();
        JXNativeNode node = laidOut(JXElement.of("scroll", props, region(500, 10)), 150, 150);
        JXControlLayout.ScrollGeometry g = JXControlLayout.scrollGeometry(node);
        assertTrue(g.vertical);
        assertFalse(g.horizontal);
        assertEquals(135, node.getChildren().get(0).getWidth(), "fit to the viewport, not the pref width");
    }

    @Test
    void scrollOffsetsMoveTheContentAndTheThumb() {
        JXProps props = JXProps.builder().set("vvalue", 0.5).build();
        JXNativeNode node = laidOut(JXElement.of("scroll", props, region(100, 1000)), 200, 200);
        JXControlLayout.ScrollGeometry g = JXControlLayout.scrollGeometry(node);
        assertEquals(0.5 * (1000 - 198), g.scrollY, 0.001);
        assertEquals(1 - 401, node.getChildren().get(0).getY());
        float track = g.vh - 2 * 11;
        assertEquals(g.vy + 11 + (track - g.vThumbH) / 2, g.vThumbY, 0.01);
        assertEquals(Math.max(12, track * 198 / 1000), g.vThumbH, 0.01);
    }

    @Test
    void scrollPaneSizesFollowItsContent() {
        JXNativeNode node = JXNativeNode.createBackendNode(JXElement.of("scroll", JXProps.empty(), region(300, 400)));
        assertEquals(302, node.getPrefWidth(), 0.001);
        assertEquals(402, node.getPrefHeight(), 0.001);
        assertEquals(36, node.getMinWidth(), 0.001);
    }

    // ---- list and table ----------------------------------------------------------------------

    private static JXElement cells(int first, int count) {
        JXElement[] cells = new JXElement[count];
        for (int i = 0; i < count; i++) {
            cells[i] = JXElement.of("cell", JXProps.builder().set("value", "item " + (first + i)).build());
        }
        return JXElement.of("list", JXProps.builder().set("itemCount", 100).set("first", first)
                .set("scrollY", first * 23.0 + 5).build(), cells);
    }

    @Test
    void listPlacesItsVirtualCellsAtTheirIndex() {
        JXNativeNode list = laidOut(cells(10, 5), 200, 102);
        assertEquals(23.0, JXControlLayout.cellHeight(list), "Modena list cell: 17 px text + 3 + 3");
        // cell 10 starts at the viewport top minus the 5 px scrolled into it
        assertEquals(1 - 5, list.getChildren().get(0).getY());
        assertEquals(1 - 5 + 23, list.getChildren().get(1).getY());
        assertEquals(200 - 2 - 13, list.getChildren().get(0).getWidth(), "the vertical bar takes 13 px");
        assertEquals(6, JXControlLayout.visibleCount(list), "ceil(100 / 23) + 1");
        assertEquals(10, JXControlLayout.itemAt(list, 1 + 2));
        assertEquals(11, JXControlLayout.itemAt(list, 1 + 23));
        assertEquals(-1, JXControlLayout.itemAt(list, 0));
    }

    @Test
    void listAndTablePreferredSizesAreJavaFxsGoldenRatio() {
        JXNativeNode list = JXNativeNode.createBackendNode(JXElement.of("list", JXProps.empty()));
        assertEquals(400 * 0.618033987, list.getPrefWidth(), 0.0001);
        assertEquals(400, list.getPrefHeight(), 0.0001);
        JXNativeNode table = JXNativeNode.createBackendNode(JXElement.of("table",
                JXProps.builder().set("columns", new String[]{"a", "b", "c", "d"}).build()));
        assertEquals(2 + 4 * 80, table.getPrefWidth(), 0.0001, "four default 80 px columns beat the golden ratio");
    }

    private static JXElement table(double[] widths, boolean constrained) {
        JXElement[] rows = new JXElement[2];
        for (int r = 0; r < 2; r++) {
            rows[r] = JXElement.of("tablerow", JXProps.empty(),
                    JXElement.of("cell", JXProps.builder().set("value", "Fortaleza").build()),
                    JXElement.of("cell", JXProps.builder().set("value", "x").build()));
        }
        return JXElement.of("table", JXProps.builder().set("columns", new String[]{"Nome", "Cidade"})
                .set("columnWidths", widths).set("constrained", constrained).set("itemCount", 2).build(), rows);
    }

    @Test
    void tableColumnsFitHeaderAndCellsOrUseTheirWidth() {
        JXNativeNode t = laidOut(table(new double[]{-1, 60}, false), 400, 200);
        float[] widths = JXControlLayout.columnWidths(t);
        double cell = text("Fortaleza") + 2 * 7 + 10; // a cell's default padding (3, 7) plus JavaFX's 10 px fit margin
        double header = JXTextEngine.get(true).width("Nome", JXTextEngine.DEFAULT_SIZE) + 18;
        assertEquals(Math.ceil(Math.max(cell, header)), widths[0], 0.001);
        assertEquals(60, widths[1], 0.001);
        JXNativeNode row = t.getChildren().get(1);
        assertEquals(1 + 24 + 24, row.getY(), "second row below the 24 px header");
        assertEquals((int) widths[0], row.getChildren().get(1).getX() - row.getX());
        assertEquals(0, JXControlLayout.columnAt(t, t.getX() + 5));
        assertEquals(1, JXControlLayout.columnAt(t, t.getX() + 1 + widths[0] + 5));
        assertEquals(-1, JXControlLayout.columnAt(t, t.getX() + 1 + widths[0] + 60 + 5));
    }

    @Test
    void constrainedColumnsFillTheTable() {
        JXNativeNode t = laidOut(table(new double[]{100, 100}, true), 402, 200);
        float[] widths = JXControlLayout.columnWidths(t);
        assertEquals(400, widths[0] + widths[1], 0.001);
        assertEquals(200, widths[0], 0.001);
    }

    // ---- tabs, pagination, button bar --------------------------------------------------------

    @Test
    void tabHeadersAreTextPlusPaddingAndTheSelectedTabHasACloseButton() {
        JXNativeNode tabs = laidOut(JXElement.of("tabs", JXProps.builder().set("titles", new String[]{"First", "Second"})
                .set("selected", 0).build(), region(100, 50)), 300, 200);
        float[] x = JXControlLayout.tabPositions(tabs);
        assertEquals(5, x[0], 0.001);
        assertEquals(5 + Math.ceil(text("First") + 12 + 17), x[1], 0.001);
        assertEquals(x[1] + Math.ceil(text("Second") + 12), x[2], 0.001);
        assertEquals(29, tabs.getChildren().get(0).getY(), "content below the 29 px header");
        assertEquals(171, tabs.getChildren().get(0).getHeight());
        boolean[] close = new boolean[1];
        assertEquals(1, JXControlLayout.tabAt(tabs, x[1] + 3, 15, close));
        assertFalse(close[0]);
        assertEquals(0, JXControlLayout.tabAt(tabs, x[1] - 4, 15, close));
        assertTrue(close[0], "the right end of the selected tab is its close button");
        assertEquals(-1, JXControlLayout.tabAt(tabs, 50, 2, close), "above the tabs");
    }

    @Test
    void closingPolicies() {
        JXNativeNode none = laidOut(JXElement.of("tabs", JXProps.builder().set("titles", new String[]{"a", "b"})
                .set("closingPolicy", "UNAVAILABLE").build()), 300, 100);
        assertFalse(JXControlLayout.tabClosable(none, 0));
        JXNativeNode all = laidOut(JXElement.of("tabs", JXProps.builder().set("titles", new String[]{"a", "b"})
                .set("closingPolicy", "ALL_TABS").set("closable", new boolean[]{true, false}).build()), 300, 100);
        assertTrue(JXControlLayout.tabClosable(all, 0));
        assertFalse(JXControlLayout.tabClosable(all, 1), "a tab can refuse to be closed");
    }

    @Test
    void paginationButtonsAreCentredAndShowTheCurrentPages() {
        JXNativeNode pages = laidOut(JXElement.of("pagination", JXProps.builder().set("pageCount", 20).set("current", 15)
                .set("maxPageIndicatorCount", 5).build()), 400, 200);
        float[][] b = JXControlLayout.pageButtons(pages);
        assertEquals(7, b.length, "previous, five pages, next");
        assertEquals(-1, b[0][4], 0);
        assertEquals(13, b[1][4], 0, "current page in the middle");
        assertEquals(17, b[5][4], 0);
        assertEquals(-2, b[6][4], 0);
        float total = 2 * 26 + 5 * 20;
        assertEquals((400 - total) / 2, b[0][0], 0.001);
        assertEquals(200 - 44 + 5, b[0][1], 0.001);
    }

    @Test
    void buttonBarRightAlignsButtonsAtLeast75Wide() {
        JXNativeNode bar = laidOut(JXElement.of("buttonbar", JXProps.empty(),
                JXElement.of("button", JXProps.builder().set("label", "OK").build()),
                JXElement.of("button", JXProps.builder().set("label", "Cancel").build())), 400, 100);
        JXNativeNode ok = bar.getChildren().get(0);
        JXNativeNode cancel = bar.getChildren().get(1);
        assertEquals(75, ok.getWidth(), "JavaFX: 75 x 25 at 240 and 325 in a 400 px bar");
        assertEquals(325, cancel.getX());
        assertEquals(240, ok.getX());
        assertEquals(180, bar.getPrefWidth(), 0.001);
    }

    // ---- small controls ----------------------------------------------------------------------

    @Test
    void buttonWithGraphicCentresGraphicAndTextTogether() {
        JXElement graphic = JXElement.of("pane", JXProps.builder().set("prefWidth", 16.0).set("prefHeight", 16.0).build());
        JXNativeNode button = JXNativeNode.createBackendNode(JXElement.of("button",
                JXProps.builder().set("label", "Salvar").build(), graphic));
        assertEquals(text("Salvar") + 16 + 4 + 16, button.getPrefWidth(), 0.001);
        assertEquals(25, button.getPrefHeight(), 0.001);
        button.layoutForBackend(200, 40);
        JXNativeNode g = button.getChildren().get(0);
        double group = 16 + 4 + text("Salvar");
        assertEquals(Math.round(8 + (200 - 16 - group) / 2), g.getX());
        assertEquals(12, g.getY());
    }

    @Test
    void radioAndHyperlinkSizesMatchModena() {
        JXNativeNode radio = JXNativeNode.createBackendNode(JXElement.of("radio", JXProps.builder().set("label", "Active").build()));
        assertEquals(text("Active") + 21, radio.getPrefWidth(), 0.001, "JavaFX 53.28 = 32.28 + 16 + 5");
        assertEquals(17, radio.getPrefHeight(), 0.001);
        JXNativeNode link = JXNativeNode.createBackendNode(JXElement.of("hyperlink", JXProps.builder().set("label", "Open").build()));
        assertEquals(text("Open") + 8, link.getPrefWidth(), 0.001, "JavaFX 37.17 = 29.17 + 8");
        assertEquals(23, link.getPrefHeight(), 0.001);
    }

    @Test
    void spinnerAndDatePickerAreTwelveColumnsWide() {
        double w = 12 * text("W");
        JXNativeNode spinner = JXNativeNode.createBackendNode(JXElement.of("spinner", JXProps.empty()));
        assertEquals(w + 14, spinner.getPrefWidth(), 0.001, "JavaFX 148.51, like a TextField");
        assertEquals(spinner.getPrefWidth(), spinner.getMaxWidth(), 0.001);
        JXNativeNode picker = JXNativeNode.createBackendNode(JXElement.of("datepicker", JXProps.empty()));
        assertEquals(w + 25, picker.getPrefWidth(), 0.001);
    }

    @Test
    void imageSizesFitAndPreserveTheRatio() {
        JXProps base = JXProps.builder().set("imageWidth", 40.0).set("imageHeight", 20.0).build();
        assertArrayEquals(new double[]{40, 20}, JXControlLayout.imageSize(JXNativeNode.createBackendNode(JXElement.of("image", base))), 0.001);
        JXProps fit = JXProps.builder().set("imageWidth", 40.0).set("imageHeight", 20.0).set("fitWidth", 20.0)
                .set("fitHeight", 20.0).set("preserveRatio", true).build();
        assertArrayEquals(new double[]{20, 10}, JXControlLayout.imageSize(JXNativeNode.createBackendNode(JXElement.of("image", fit))), 0.001);
        JXProps stretch = JXProps.builder().set("imageWidth", 40.0).set("imageHeight", 20.0).set("fitWidth", 20.0).build();
        assertArrayEquals(new double[]{20, 20}, JXControlLayout.imageSize(JXNativeNode.createBackendNode(JXElement.of("image", stretch))), 0.001);
    }

    @Test
    void cellWithGraphicAndTextAndTextFieldWithSideNodes() {
        JXElement icon = JXElement.of("pane", JXProps.builder().set("prefWidth", 10.0).set("prefHeight", 30.0).build());
        JXNativeNode cell = JXNativeNode.createBackendNode(JXElement.of("cell", JXProps.builder().set("value", "abc").build(), icon));
        assertEquals(7 + 10 + 4 + text("abc") + 7, cell.getPrefWidth(), 0.001);
        assertEquals(3 + 30 + 3, cell.getPrefHeight(), 0.001);

        JXElement left = JXElement.of("pane", JXProps.builder().set("prefWidth", 12.0).set("prefHeight", 12.0).build());
        JXElement right = JXElement.of("pane", JXProps.builder().set("prefWidth", 8.0).set("prefHeight", 8.0).set("side", "right").build());
        JXNativeNode field = laidOut(JXElement.of("input", JXProps.empty(), left, right), 200, 25);
        assertEquals(7, field.getChildren().get(0).getX());
        assertEquals(200 - 7 - 8, field.getChildren().get(1).getX());
        assertArrayEquals(new float[]{16, 12}, JXControlLayout.sideWidths(field), 0.001f);
    }

    @Test
    void textFlowWrapsRunsThatDoNotFit() {
        JXNativeNode flow = laidOut(JXElement.of("textflow", JXProps.empty(), JXElement.text("Hello "), JXElement.text("world")),
                (int) Math.ceil(text("Hello ")) + 5, 100);
        assertEquals(0, flow.getChildren().get(1).getX(), "the second run starts a new line");
        assertEquals(17, flow.getChildren().get(1).getY());
    }

    @Test
    void indicatorSizesDependOnDeterminacy() {
        assertEquals(49, JXNativeNode.createBackendNode(JXElement.of("indicator", JXProps.builder().set("progress", 0.5).build())).getPrefWidth(), 0.001);
        assertEquals(53, JXNativeNode.createBackendNode(JXElement.of("indicator", JXProps.empty())).getPrefHeight(), 0.001);
    }
}
