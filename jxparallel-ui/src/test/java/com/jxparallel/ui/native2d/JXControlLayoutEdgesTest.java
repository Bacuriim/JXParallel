package com.jxparallel.ui.native2d;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;
import com.jxparallel.ui.text.JXTextEngine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Composite controls away from the origin and at their limits: children placed relative to a
 * control that is not at (0, 0), sizes of the less common shapes (graphic only, fixed heights,
 * explicit column widths) and every boundary of the hit tests. Complements {@link JXControlLayoutTest}.
 */
class JXControlLayoutEdgesTest {
    private static final int TOP = 30;
    private static final int LEFT = 40;

    private static JXProps.Builder p() {
        return JXProps.builder();
    }

    private static JXElement e(String type, JXProps.Builder props, JXElement... children) {
        return JXElement.of(type, props.build(), children);
    }

    private static JXElement region(double w, double h) {
        return e("pane", p().set("prefWidth", w).set("prefHeight", h));
    }

    private static double text(String s) {
        return JXTextEngine.get().width(s, JXTextEngine.DEFAULT_SIZE);
    }

    /** The control laid out at (LEFT, TOP) inside a padded row, at its pref size unless the props say otherwise. */
    private static JXNativeNode placed(JXElement element) {
        JXNativeNode root = JXNativeNode.createBackendNode(e("row",
                p().set("padding", new double[]{TOP, 0, 0, LEFT}).set("fillHeight", false), element));
        root.layoutForBackend(900, 900);
        JXNativeNode node = root.getChildren().get(0);
        assertEquals(LEFT, node.getX());
        assertEquals(TOP, node.getY());
        return node;
    }

    private static JXNativeNode sized(String type, double w, double h, JXProps.Builder props, JXElement... children) {
        return placed(e(type, props.set("prefWidth", w).set("prefHeight", h).set("maxWidth", w).set("maxHeight", h)
                .set("minWidth", w).set("minHeight", h), children));
    }

    // ---- buttons -----------------------------------------------------------------------------

    @Test
    void aGraphicOnlyButtonIsItsGraphicPlusPadding() {
        JXNativeNode button = JXNativeNode.createBackendNode(e("button", p(), region(16, 16)));
        assertEquals(16 + 16, button.getPrefWidth(), 0.001, "no text, no gap");
        assertEquals(16 + 8, button.getPrefHeight(), 0.001, "the graphic, not a text line");
        JXNativeNode tall = JXNativeNode.createBackendNode(e("button", p().set("label", "OK"), region(10, 30)));
        assertEquals(30 + 8, tall.getPrefHeight(), 0.001, "a graphic taller than the text line");
    }

    @Test
    void theGraphicIsCentredVerticallyAndNeverTallerThanTheButton() {
        JXNativeNode button = sized("button", 120, 40, p().set("label", "Salvar"), region(16, 16));
        JXNativeNode g = button.getChildren().get(0);
        assertEquals(TOP + 12, g.getY());
        assertEquals(Math.round(JXControlLayout.buttonContent(button)[0]), g.getX());
        JXNativeNode huge = sized("button", 120, 40, p().set("label", "Salvar"), region(16, 60));
        assertEquals(TOP, huge.getChildren().get(0).getY());
        assertEquals(40, huge.getChildren().get(0).getHeight());
    }

    @Test
    void buttonContentEllipsizesTheTextBesideTheGraphic() {
        String label = "A very long label";
        JXNativeNode button = sized("button", 60, 25, p().set("label", label), region(16, 16));
        float available = 60 - 16;
        String shown = JXTextLayout.ellipsize(label, available - 16 - 4, 12, false);
        double group = 16 + 4 + text(shown);
        float[] content = JXControlLayout.buttonContent(button);
        assertEquals(LEFT + 8 + Math.max(0, (available - group) / 2), content[0], 0.01);
        assertEquals(content[0] + 16 + 4, content[1], 0.01);
        JXNativeNode noText = sized("button", 60, 25, p(), region(16, 16));
        float[] alone = JXControlLayout.buttonContent(noText);
        assertEquals(LEFT + 8 + (44 - 16) / 2f, alone[0], 0.01);
        assertEquals(alone[0] + 16, alone[1], 0.01, "no gap without text");
    }

    @Test
    void labeledMinimumWidthIsTheEllipsisOrTheShorterText() {
        JXNativeNode longText = JXNativeNode.createBackendNode(e("hyperlink", p().set("label", "Customers")));
        assertEquals(text("...") + 8, longText.getMinWidth(), 0.001);
        JXNativeNode shortText = JXNativeNode.createBackendNode(e("hyperlink", p().set("label", "i")));
        assertEquals(text("i") + 8, shortText.getMinWidth(), 0.001);
        assertEquals(shortText.getPrefWidth(), shortText.getMaxWidth(), 0.001);
    }

    @Test
    void tooltipsAreTheirTextInModenaPadding() {
        JXNativeNode tip = JXNativeNode.createBackendNode(e("tooltip", p().set("label", "Dica")));
        assertEquals(text("Dica") + 18, tip.getPrefWidth(), 0.001);
        assertEquals(17 + 8, tip.getPrefHeight(), 0.001);
    }

    // ---- cells -------------------------------------------------------------------------------

    @Test
    void cellSizesWithoutTextOrWithAFixedHeight() {
        JXNativeNode graphicOnly = JXNativeNode.createBackendNode(e("cell", p().set("value", ""), region(10, 30)));
        assertEquals(7 + 10 + 7, graphicOnly.getPrefWidth(), 0.001, "no gap without text");
        assertEquals(3 + 30 + 3, graphicOnly.getPrefHeight(), 0.001);
        assertEquals(14, graphicOnly.getMinWidth(), 0.001);
        JXNativeNode fixed = JXNativeNode.createBackendNode(e("cell", p().set("value", "x").set("fixedHeight", 50.0)));
        assertEquals(50, fixed.getPrefHeight(), 0.001);
        assertEquals(50, fixed.getMinHeight(), 0.001);
        JXNativeNode zero = JXNativeNode.createBackendNode(e("cell", p().set("value", "x").set("fixedHeight", 0.0)));
        assertEquals(23, zero.getPrefHeight(), 0.001, "0 is no fixed height");
    }

    @Test
    void cellGraphicsSitAfterThePaddingOrFillTheCell() {
        JXNativeNode cell = sized("cell", 100, 40, p().set("value", "abc"), region(10, 12));
        JXNativeNode g = cell.getChildren().get(0);
        assertEquals(LEFT + 7, g.getX());
        assertEquals(TOP + 14, g.getY());
        assertEquals(10, g.getWidth());
        JXNativeNode tall = sized("cell", 100, 40, p().set("value", "abc"), region(10, 90));
        assertEquals(34, tall.getChildren().get(0).getHeight(), "cut to the cell's inner height");
        JXNativeNode filled = sized("cell", 100, 40, p().set("value", "").set("fillGraphic", true), region(10, 12));
        JXNativeNode f = filled.getChildren().get(0);
        assertArrayEquals(new int[]{LEFT + 7, TOP + 3, 100 - 14, 40 - 6}, new int[]{f.getX(), f.getY(), f.getWidth(), f.getHeight()});
        JXNativeNode notAlone = sized("cell", 100, 40, p().set("value", "abc").set("fillGraphic", true), region(10, 12));
        assertEquals(10, notAlone.getChildren().get(0).getWidth(), "with text the graphic keeps its size");
    }

    // ---- images and text fields --------------------------------------------------------------

    private static double[] image(JXProps.Builder props) {
        return JXControlLayout.imageSize(JXNativeNode.createBackendNode(e("image", props)));
    }

    @Test
    void imageFitsEachWay() {
        JXProps.Builder base = p().set("imageWidth", 40.0).set("imageHeight", 20.0).set("preserveRatio", true);
        assertArrayEquals(new double[]{20, 10}, image(p().set("imageWidth", 40.0).set("imageHeight", 20.0)
                .set("preserveRatio", true).set("fitHeight", 10.0)), 0.001);
        assertArrayEquals(new double[]{20, 10}, image(base.set("fitWidth", 20.0)), 0.001);
        assertArrayEquals(new double[]{10, 5}, image(p().set("imageWidth", 40.0).set("imageHeight", 20.0)
                .set("preserveRatio", true).set("fitWidth", 40.0).set("fitHeight", 5.0)), 0.001, "the tighter fit wins");
        assertArrayEquals(new double[]{40, 30}, image(p().set("imageWidth", 40.0).set("imageHeight", 20.0).set("fitHeight", 30.0)), 0.001);
        assertArrayEquals(new double[]{40, 20}, image(p().set("imageWidth", 40.0).set("imageHeight", 20.0)
                .set("fitWidth", 0.0).set("fitHeight", 0.0)), 0.001);
        assertArrayEquals(new double[]{20, 20}, image(p().set("imageWidth", 0.0).set("imageHeight", 20.0)
                .set("preserveRatio", true).set("fitWidth", 20.0)), 0.001, "no ratio without an image width");
        assertArrayEquals(new double[]{20, 0}, image(p().set("imageWidth", 40.0).set("imageHeight", 0.0)
                .set("preserveRatio", true).set("fitWidth", 20.0)), 0.001, "no ratio without an image height");
        JXNativeNode node = JXNativeNode.createBackendNode(e("image", p().set("imageWidth", 40.0).set("imageHeight", 20.0)));
        assertArrayEquals(new double[]{40, 40, 40, 20, 20, 20}, new double[]{node.getMinWidth(), node.getPrefWidth(),
                node.getMaxWidth(), node.getMinHeight(), node.getPrefHeight(), node.getMaxHeight()}, 0.001);
    }

    @Test
    void sideNodesWidenTextFieldsAndStackFromEachEdge() {
        JXElement a = region(12, 30);
        JXElement b = region(6, 10);
        JXElement c = e("pane", p().set("prefWidth", 8.0).set("prefHeight", 8.0).set("side", "right"));
        JXElement d = e("pane", p().set("prefWidth", 5.0).set("prefHeight", 8.0).set("side", "right"));
        JXNativeNode unplaced = JXNativeNode.createBackendNode(e("input", p(), a, b, c, d));
        double extra = 14 + (12 + 4) + (6 + 4) + (8 + 4) + (5 + 4);
        assertEquals(extra, unplaced.getMinWidth(), 0.001);
        assertEquals(12 * text("W") + extra, unplaced.getPrefWidth(), 0.001);
        assertEquals(30, unplaced.getPrefHeight(), 0.001, "the tallest side node");
        JXNativeNode field = sized("input", 200, 40, p(), a, b, c, d);
        JXNativeNode[] kids = field.getChildren().toArray(new JXNativeNode[0]);
        assertEquals(LEFT + 7, kids[0].getX());
        assertEquals(TOP + 5, kids[0].getY(), "centred: (40 - 30) / 2");
        assertEquals(LEFT + 7 + 12 + 4, kids[1].getX());
        assertEquals(TOP + 15, kids[1].getY());
        assertEquals(LEFT + 200 - 7 - 8, kids[2].getX());
        assertEquals(LEFT + 200 - 7 - 8 - 4 - 5, kids[3].getX());
        JXNativeNode small = sized("input", 200, 20, p(), region(12, 30));
        assertEquals(20, small.getChildren().get(0).getHeight(), "never taller than the field");
    }

    // ---- composite sizes ---------------------------------------------------------------------

    @Test
    void textFlowsAreTheirRunsSideBySide() {
        JXElement big = e("#text", p().set("value", "Big").set("fontSize", 18.0));
        JXNativeNode flow = JXNativeNode.createBackendNode(e("textflow", p(), JXElement.text("ab "), big));
        JXNativeNode bigNode = flow.getChildren().get(1);
        assertEquals(flow.getChildren().get(0).getPrefWidth() + bigNode.getPrefWidth(), flow.getPrefWidth(), 0.001);
        assertEquals(bigNode.getPrefHeight(), flow.getPrefHeight(), 0.001, "the tallest run");
        assertTrue(bigNode.getPrefHeight() > 17);
    }

    @Test
    void textFlowRunsWrapOnlyAfterTheFirstAndOnlyWhenTheyOverflow() {
        int a = (int) Math.ceil(text("Hello "));
        int b = (int) Math.ceil(text("world"));
        JXNativeNode exact = sized("textflow", a + b, 60, p(), JXElement.text("Hello "), JXElement.text("world"));
        assertEquals(LEFT + a, exact.getChildren().get(1).getX(), "exactly full: same line");
        assertEquals(TOP, exact.getChildren().get(1).getY());
        JXNativeNode narrow = sized("textflow", 10, 60, p(), JXElement.text("Hello "), JXElement.text("world"));
        assertEquals(LEFT, narrow.getChildren().get(0).getX(), "the first run stays even when too wide");
        assertEquals(TOP, narrow.getChildren().get(0).getY());
        assertEquals(LEFT, narrow.getChildren().get(1).getX());
        assertEquals(TOP + 17, narrow.getChildren().get(1).getY());
    }

    @Test
    void tablePreferredWidthUsesExplicitColumnWidths() {
        JXNativeNode table = JXNativeNode.createBackendNode(e("table", p().set("columns", new String[]{"a", "b", "c", "d", "e"})
                .set("columnWidths", new double[]{-1, 0, 100, 60})));
        assertEquals(2 + 80 + 0 + 100 + 60 + 80, table.getPrefWidth(), 0.001);
        assertEquals(12, table.getMinWidth(), 0.001);
        assertEquals(26, table.getMinHeight(), 0.001);
    }

    @Test
    void tabsAndPaginationGrowWithTheirContent() {
        JXNativeNode empty = JXNativeNode.createBackendNode(e("tabs", p().set("titles", new String[]{"One"})));
        float[] x = JXControlLayout.tabPositions(empty);
        assertEquals(x[1] + 5 + 15, empty.getPrefWidth(), 0.001);
        assertEquals(29, empty.getPrefHeight(), 0.001);
        JXNativeNode wide = JXNativeNode.createBackendNode(e("tabs", p().set("titles", new String[]{"One"}), region(500, 70)));
        assertEquals(500, wide.getPrefWidth(), 0.001);
        assertEquals(29 + 70, wide.getPrefHeight(), 0.001);

        JXNativeNode pages = JXNativeNode.createBackendNode(e("pagination", p().set("pageCount", 3)));
        assertEquals(2 * 26 + 3 * 20, pages.getPrefWidth(), 0.001);
        assertEquals(44, pages.getPrefHeight(), 0.001);
        assertEquals(64, pages.getMinWidth(), 0.001);
        assertEquals(44, pages.getMinHeight(), 0.001);
        JXNativeNode big = JXNativeNode.createBackendNode(e("pagination", p().set("pageCount", 3), region(300, 50)));
        assertEquals(300, big.getPrefWidth(), 0.001);
        assertEquals(44 + 50, big.getPrefHeight(), 0.001);
    }

    @Test
    void buttonBarSizesAndVerticalCentring() {
        JXNativeNode one = JXNativeNode.createBackendNode(e("buttonbar", p(), e("button", p().set("label", "OK"))));
        assertEquals(75 + 2 * 10, one.getPrefWidth(), 0.001);
        assertEquals(25, one.getPrefHeight(), 0.001);
        JXNativeNode none = JXNativeNode.createBackendNode(e("buttonbar", p()));
        assertEquals(0, none.getPrefWidth(), 0.001);
        String wideLabel = "A button wider than seventy five pixels";
        JXNativeNode wide = JXNativeNode.createBackendNode(e("buttonbar", p(), e("button", p().set("label", wideLabel)),
                e("button", p().set("label", "OK"))));
        assertEquals(Math.ceil(text(wideLabel) + 16) + 75 + 10 + 20, wide.getPrefWidth(), 0.001);
        JXNativeNode bar = sized("buttonbar", 300, 45, p(), e("button", p().set("label", "OK")), e("button", p().set("label", "No")));
        JXNativeNode ok = bar.getChildren().get(0);
        JXNativeNode no = bar.getChildren().get(1);
        assertEquals(TOP + 10, ok.getY());
        assertEquals(LEFT + 300 - 75, no.getX());
        assertEquals(LEFT + 300 - 75 - 10 - 75, ok.getX());
    }

    // ---- scrolling ---------------------------------------------------------------------------

    @Test
    void scrolledContentMovesByTheOffsetsFromTheViewport() {
        JXNativeNode scroll = sized("scroll", 150, 150, p().set("hvalue", 0.5).set("vvalue", 0.25), region(400, 500));
        JXControlLayout.ScrollGeometry g = JXControlLayout.scrollGeometry(scroll);
        assertEquals(LEFT + 1, g.viewX, 0.001);
        assertEquals(TOP + 1, g.viewY, 0.001);
        assertEquals(0.5 * (400 - 135), g.scrollX, 0.001);
        assertEquals(0.25 * (500 - 135), g.scrollY, 0.001);
        JXNativeNode content = scroll.getChildren().get(0);
        assertEquals(Math.round(LEFT + 1 - g.scrollX), content.getX());
        assertEquals(Math.round(TOP + 1 - g.scrollY), content.getY());
        assertEquals(400, content.getWidth());
        assertEquals(500, content.getHeight());
        assertEquals(LEFT + 1, g.hx, 0.001);
        assertEquals(TOP + 1 + 148 - 13, g.hy, 0.001);
        assertEquals(135, g.hw, 0.001);
        assertEquals(13, g.hh, 0.001);
        float track = 135 - 22;
        assertEquals(track * 135 / 400, g.hThumbW, 0.01);
        assertEquals(g.hx + 11 + (track - g.hThumbW) * g.scrollX / (400 - 135), g.hThumbX, 0.01);
        assertEquals(TOP + 1, g.vy, 0.001);
        assertEquals(13, g.vw, 0.001);
        assertEquals(135, g.vh, 0.001);
    }

    @Test
    void scrollOffsetsAreClampedAndBarsFollowThePolicies() {
        JXNativeNode past = sized("scroll", 150, 150, p().set("vvalue", 3.0).set("hvalue", -1.0), region(100, 500));
        JXControlLayout.ScrollGeometry g = JXControlLayout.scrollGeometry(past);
        assertEquals(500 - 148, g.scrollY, 0.001);
        assertEquals(0, g.scrollX, 0.001);
        assertEquals(0, g.maxScrollX(), 0.001, "content narrower than the view");
        JXNativeNode never = sized("scroll", 150, 150, p().set("vbarPolicy", "NEVER").set("hbarPolicy", "NEVER"), region(400, 500));
        JXControlLayout.ScrollGeometry n = JXControlLayout.scrollGeometry(never);
        assertFalse(n.vertical);
        assertFalse(n.horizontal);
        assertEquals(148, n.viewW, 0.001);
        JXNativeNode always = sized("scroll", 150, 150, p().set("hbarPolicy", "ALWAYS").set("vbarPolicy", "ALWAYS"), region(10, 10));
        JXControlLayout.ScrollGeometry a = JXControlLayout.scrollGeometry(always);
        assertTrue(a.vertical && a.horizontal);
        assertEquals(a.vy + 11, a.vThumbY, 0.001, "nothing to scroll: the thumb stays at the top");
        assertEquals(a.vh - 22, a.vThumbH, 0.001, "and fills the track");
        assertEquals(a.hx + 11, a.hThumbX, 0.001);
        JXNativeNode fits = sized("scroll", 150, 150, p(), region(148, 148));
        JXControlLayout.ScrollGeometry f = JXControlLayout.scrollGeometry(fits);
        assertFalse(f.vertical || f.horizontal, "content exactly the view size needs no bar");
        JXNativeNode over = sized("scroll", 150, 150, p(), region(100, 149));
        assertTrue(JXControlLayout.scrollGeometry(over).vertical);
        assertFalse(JXControlLayout.scrollGeometry(over).horizontal);
        JXNativeNode tall = sized("scroll", 150, 150, p(), region(100, 100000));
        assertEquals(12, JXControlLayout.scrollGeometry(tall).vThumbH, 0.001, "the thumb never gets smaller than 12");
        JXNativeNode wide = sized("scroll", 150, 150, p(), region(100000, 10));
        assertEquals(12, JXControlLayout.scrollGeometry(wide).hThumbW, 0.001);
    }

    @Test
    void fitToHeightAndEmptyScrollPanes() {
        JXNativeNode fit = sized("scroll", 150, 150, p().set("fitToHeight", true), region(50, 20));
        assertEquals(148, fit.getChildren().get(0).getHeight(), "stretched to the viewport");
        assertEquals(50, fit.getChildren().get(0).getWidth());
        JXNativeNode empty = sized("scroll", 150, 150, p());
        JXControlLayout.ScrollGeometry g = JXControlLayout.scrollGeometry(empty);
        assertFalse(g.vertical || g.horizontal);
        assertEquals(0, g.contentW, 0.001);
        assertEquals(0, g.contentH, 0.001);
        JXNativeNode unsized = JXNativeNode.createBackendNode(e("scroll", p()));
        assertEquals(2, unsized.getPrefWidth(), 0.001);
        assertEquals(2, unsized.getPrefHeight(), 0.001);
        assertEquals(36, unsized.getMinHeight(), 0.001);
    }

    // ---- lists and tables --------------------------------------------------------------------

    @Test
    void cellHeightsAndRowHeights() {
        JXNativeNode fixed = JXNativeNode.createBackendNode(e("list", p().set("cellHeight", 30.0), e("cell", p().set("value", "x"))));
        assertEquals(30, JXControlLayout.cellHeight(fixed), 0.001);
        JXNativeNode zero = JXNativeNode.createBackendNode(e("list", p().set("cellHeight", 0.0), e("cell", p().set("value", "x"))));
        assertEquals(23, JXControlLayout.cellHeight(zero), 0.001, "0 is no fixed height");
        JXNativeNode flat = JXNativeNode.createBackendNode(e("list", p(), region(10, 0)));
        assertEquals(1, JXControlLayout.cellHeight(flat), 0.001, "at least one pixel");
        JXNativeNode empty = JXNativeNode.createBackendNode(e("list", p()));
        assertEquals(23, JXControlLayout.cellHeight(empty), 0.001);
        JXNativeNode table = JXNativeNode.createBackendNode(e("table", p().set("rowHeight", 30.0)));
        assertEquals(30, JXControlLayout.rowHeight(table), 0.001);
        JXNativeNode defaultRows = JXNativeNode.createBackendNode(e("table", p().set("rowHeight", 0.0)));
        assertEquals(24, JXControlLayout.rowHeight(defaultRows), 0.001);
        JXNativeNode row = JXNativeNode.createBackendNode(e("tablerow", p().set("rowHeight", 30.0)));
        assertEquals(24, JXControlLayout.rowHeight(row), 0.001, "a row asks its table, not itself");
    }

    private static JXElement[] rows(int count) {
        JXElement[] rows = new JXElement[count];
        for (int r = 0; r < count; r++) {
            rows[r] = e("tablerow", p(), e("cell", p().set("value", "c" + r)), e("cell", p().set("value", "d" + r)));
        }
        return rows;
    }

    private static JXProps.Builder table() {
        return p().set("columns", new String[]{"A", "B"});
    }

    @Test
    void tableRowsFollowTheScrollAndSpanTheColumns() {
        JXNativeNode table = sized("table", 120, 150, table().set("columnWidths", new double[]{70.5, 80}).set("itemCount", 50)
                .set("rowHeight", 30.0).set("first", 2).set("scrollY", 65.0).set("scrollX", 20.0), rows(3));
        JXControlLayout.ScrollGeometry g = JXControlLayout.scrollGeometry(table);
        assertEquals(20, g.scrollX, 0.001);
        JXNativeNode second = table.getChildren().get(1);
        assertEquals(Math.round(g.viewX - 20), second.getX());
        assertEquals(Math.round(g.viewY + 3 * 30 - 65), second.getY());
        assertEquals(30, second.getHeight());
        assertEquals((int) Math.ceil(Math.max(150.5, g.viewW)), second.getWidth());
        JXNativeNode c0 = second.getChildren().get(0);
        JXNativeNode c1 = second.getChildren().get(1);
        assertEquals(second.getX(), c0.getX());
        assertEquals(Math.round(second.getX() + 70.5) - second.getX(), c0.getWidth());
        assertEquals(Math.round(second.getX() + 70.5), c1.getX());
        assertEquals(Math.round(second.getX() + 150.5) - Math.round(second.getX() + 70.5), c1.getWidth());
        assertEquals(second.getY(), c1.getY());
        assertEquals(150.5, second.getPrefWidth(), 0.001, "a row is as wide as the columns");
        assertEquals((int) Math.ceil(g.viewH / 30) + 1, JXControlLayout.visibleCount(table));
        assertEquals(150 - 2 - 24 - 13, g.viewH, 0.001, "below the header, above the horizontal bar");
    }

    @Test
    void itemAtEdgesOfTheViewportAndTheItems() {
        JXNativeNode list = sized("list", 100, 100, p().set("itemCount", 3).set("scrollY", 0.0),
                e("cell", p().set("value", "a")));
        JXControlLayout.ScrollGeometry g = JXControlLayout.scrollGeometry(list);
        assertEquals(0, JXControlLayout.itemAt(list, g.viewY));
        assertEquals(-1, JXControlLayout.itemAt(list, g.viewY - 0.5));
        assertEquals(2, JXControlLayout.itemAt(list, g.viewY + 3 * 23 - 0.5));
        assertEquals(-1, JXControlLayout.itemAt(list, g.viewY + 3 * 23), "below the last item");
        JXNativeNode scrolled = sized("list", 100, 100, p().set("itemCount", 50).set("scrollY", 40.0),
                e("cell", p().set("value", "a")));
        JXControlLayout.ScrollGeometry s = JXControlLayout.scrollGeometry(scrolled);
        assertEquals(1, JXControlLayout.itemAt(scrolled, s.viewY + 5), "40 + 5 is in item 1");
        assertEquals(5, JXControlLayout.itemAt(scrolled, s.viewY + s.viewH - 0.5), "(97.5 + 40) / 23");
        assertEquals(-1, JXControlLayout.itemAt(scrolled, s.viewY + s.viewH));
    }

    @Test
    void constrainedColumnsLeaveRoomForTheVerticalBarAndKeepTenPixels() {
        JXNativeNode table = sized("table", 202, 100, table().set("constrained", true).set("columnWidths", new double[]{300, 1})
                .set("itemCount", 100), rows(2));
        float[] widths = JXControlLayout.columnWidths(table);
        float view = 202 - 2 - 13;
        assertEquals((float) Math.floor(300 * view / 301), widths[0], 0.001);
        assertEquals(Math.max(10, view - widths[0]), widths[1], 0.001);
        JXNativeNode few = sized("table", 202, 100, table().set("constrained", true).set("columnWidths", new double[]{50, 50})
                .set("itemCount", 1), rows(1));
        assertEquals(200, JXControlLayout.columnWidths(few)[0] + JXControlLayout.columnWidths(few)[1], 0.001, "no bar: all 200");
        JXNativeNode tiny = sized("table", 202, 100, table().set("constrained", true).set("columnWidths", new double[]{1, 300})
                .set("itemCount", 1), rows(1));
        assertEquals(10, JXControlLayout.columnWidths(tiny)[0], 0.001, "never narrower than 10");
        JXNativeNode zero = sized("table", 202, 100, table().set("constrained", true).set("columnWidths", new double[]{0, 0})
                .set("itemCount", 1), rows(1));
        assertArrayEquals(new float[]{0, 0}, JXControlLayout.columnWidths(zero), 0.001f, "nothing to scale");
    }

    @Test
    void autoColumnWidthsCoverShortExplicitListsAndShortRows() {
        JXElement shortRow = e("tablerow", p(), e("cell", p().set("value", "A much longer first cell")));
        JXNativeNode table = sized("table", 400, 100, p().set("columns", new String[]{"A", "B"}).set("columnWidths", new double[]{-1})
                .set("itemCount", 1), shortRow);
        float[] widths = JXControlLayout.columnWidths(table);
        assertEquals(Math.ceil(text("A much longer first cell") + 14 + 10), widths[0], 0.001);
        double header = JXTextEngine.get(true).width("B", JXTextEngine.DEFAULT_SIZE) + 18;
        assertEquals(Math.ceil(header), widths[1], 0.001, "no cell in the row for column B");
    }

    @Test
    void autoColumnWidthsStayWhileTheColumnsStayTheSame() {
        JXNativeNode table = sized("table", 400, 100, table().set("itemCount", 1), rows(1));
        JXElement longer = e("table", p().set("columns", new String[]{"A", "B"}).set("itemCount", 1)
                .set("prefWidth", 400.0).set("prefHeight", 100.0).set("maxWidth", 400.0).set("maxHeight", 100.0),
                e("tablerow", p(), e("cell", p().set("value", "A far longer value than before")), e("cell", p().set("value", "d"))));
        float before = JXControlLayout.columnWidths(table)[0];
        table.reconcile(longer);
        assertEquals(before, JXControlLayout.columnWidths(table)[0], 0.001, "scrolling in new rows does not resize columns");
        JXElement renamed = e("table", p().set("columns", new String[]{"A", "C"}).set("itemCount", 1),
                e("tablerow", p(), e("cell", p().set("value", "A far longer value than before")), e("cell", p().set("value", "d"))));
        table.reconcile(renamed);
        assertTrue(JXControlLayout.columnWidths(table)[0] > before, "new columns are measured again");
    }

    @Test
    void autoColumnWidthsWaitForRowsWhenThereAreItems() {
        JXNativeNode table = sized("table", 400, 100, table().set("itemCount", 1));
        float headerOnly = JXControlLayout.columnWidths(table)[0];
        table.reconcile(e("table", table().set("itemCount", 1),
                e("tablerow", p(), e("cell", p().set("value", "A far longer value than before")))));
        assertTrue(JXControlLayout.columnWidths(table)[0] > headerOnly, "widths measured without the rows were not kept");
    }

    @Test
    void columnAtEdges() {
        JXNativeNode table = sized("table", 300, 100, table().set("columnWidths", new double[]{50, 60}).set("itemCount", 1), rows(1));
        JXControlLayout.ScrollGeometry g = JXControlLayout.scrollGeometry(table);
        assertEquals(0, JXControlLayout.columnAt(table, g.viewX));
        assertEquals(-1, JXControlLayout.columnAt(table, g.viewX - 0.5));
        assertEquals(0, JXControlLayout.columnAt(table, g.viewX + 49.5));
        assertEquals(1, JXControlLayout.columnAt(table, g.viewX + 50));
        assertEquals(-1, JXControlLayout.columnAt(table, g.viewX + 110));
        JXNativeNode scrolled = sized("table", 80, 100, table().set("columnWidths", new double[]{50, 60}).set("itemCount", 1)
                .set("scrollX", 20.0), rows(1));
        JXControlLayout.ScrollGeometry s = JXControlLayout.scrollGeometry(scrolled);
        assertEquals(20, s.scrollX, 0.001);
        assertEquals(1, JXControlLayout.columnAt(scrolled, s.viewX + 30), "50 - 20");
    }

    // ---- tabs --------------------------------------------------------------------------------

    @Test
    void tabHitEdges() {
        JXNativeNode tabs = sized("tabs", 300, 100, p().set("titles", new String[]{"First", "Second"}).set("selected", 0));
        float[] x = JXControlLayout.tabPositions(tabs);
        boolean[] close = new boolean[1];
        assertEquals(0, JXControlLayout.tabAt(tabs, LEFT + x[0], TOP + 5, close));
        assertEquals(-1, JXControlLayout.tabAt(tabs, LEFT + x[0] - 0.5, TOP + 10, close));
        assertEquals(-1, JXControlLayout.tabAt(tabs, LEFT + 20, TOP + 4.5, close), "above the headers");
        assertEquals(0, JXControlLayout.tabAt(tabs, LEFT + 20, TOP + 28.5, close));
        assertEquals(-1, JXControlLayout.tabAt(tabs, LEFT + 20, TOP + 29, close), "the content area");
        assertEquals(1, JXControlLayout.tabAt(tabs, LEFT + x[1], TOP + 10, close));
        assertEquals(1, JXControlLayout.tabAt(tabs, LEFT + x[2] - 0.5, TOP + 10, close));
        assertEquals(-1, JXControlLayout.tabAt(tabs, LEFT + x[2], TOP + 10, close), "after the last tab");
        JXControlLayout.tabAt(tabs, LEFT + x[1] - 6 - 12, TOP + 10, close);
        assertTrue(close[0], "the close button starts 18 px before the tab's end");
        JXControlLayout.tabAt(tabs, LEFT + x[1] - 6 - 12.5, TOP + 10, close);
        assertFalse(close[0]);
        assertEquals(0, JXControlLayout.tabAt(tabs, LEFT + x[1] - 5, TOP + 10, null), "no close flag wanted");
        assertEquals(0, JXControlLayout.tabAt(tabs, LEFT + x[1] - 5, TOP + 10, new boolean[0]));
    }

    @Test
    void tabClosableFlagsShorterThanTheTabs() {
        JXNativeNode tabs = JXNativeNode.createBackendNode(e("tabs", p().set("titles", new String[]{"a", "b"})
                .set("closingPolicy", "ALL_TABS").set("closable", new boolean[]{false})));
        assertFalse(JXControlLayout.tabClosable(tabs, 0));
        assertTrue(JXControlLayout.tabClosable(tabs, 1), "no flag: closable");
        JXNativeNode selected = JXNativeNode.createBackendNode(e("tabs", p().set("titles", new String[]{"a", "b"}).set("selected", 1)));
        assertFalse(JXControlLayout.tabClosable(selected, 0));
        assertTrue(JXControlLayout.tabClosable(selected, 1));
        JXNativeNode refused = JXNativeNode.createBackendNode(e("tabs", p().set("titles", new String[]{"a"})
                .set("closable", new boolean[]{false})));
        assertFalse(JXControlLayout.tabClosable(refused, 0), "the selected tab can refuse too");
    }

    // ---- pagination --------------------------------------------------------------------------

    @Test
    void pageButtonsClampTheirCounts() {
        JXNativeNode none = sized("pagination", 300, 100, p().set("pageCount", 0).set("current", 5));
        float[][] b = JXControlLayout.pageButtons(none);
        assertEquals(3, b.length, "at least one page");
        assertEquals(0, b[1][4], 0);
        JXNativeNode one = sized("pagination", 300, 100, p().set("pageCount", 9).set("current", -4).set("maxPageIndicatorCount", 0));
        float[][] c = JXControlLayout.pageButtons(one);
        assertEquals(3, c.length, "at least one indicator");
        assertEquals(0, c[1][4], 0, "current clamped to the first page");
        JXNativeNode end = sized("pagination", 300, 100, p().set("pageCount", 9).set("current", 8).set("maxPageIndicatorCount", 4));
        float[][] d = JXControlLayout.pageButtons(end);
        assertEquals(5, d[1][4], 0, "the last four pages");
        assertEquals(8, d[4][4], 0);
        float left = LEFT + (300 - (2 * 26 + 4 * 20)) / 2f;
        assertEquals(left, d[0][0], 0.001);
        assertEquals(left + 26 + 20, d[2][0], 0.001);
        assertEquals(left + 26 + 4 * 20, d[5][0], 0.001);
        assertEquals(TOP + 100 - 44 + 5, d[5][1], 0.001);
        assertEquals(26, d[5][2], 0.001);
        assertEquals(25, d[5][3], 0.001);
        assertEquals(20, d[3][2], 0.001);
        JXNativeNode content = sized("pagination", 300, 100, p().set("pageCount", 2),
                e("pane", p().set("prefWidth", 50.0).set("prefHeight", 20.0).set("maxWidth", 50.0).set("maxHeight", 20.0)));
        JXNativeNode page = content.getChildren().get(0);
        assertEquals(TOP + (100 - 44 - 20) / 2, page.getY(), "the page is centred above the buttons");
        assertEquals(LEFT + (300 - 50) / 2, page.getX());
    }

    @Test
    void tabContentIsBelowTheHeaders() {
        JXNativeNode tabs = sized("tabs", 300, 100, p().set("titles", new String[]{"a"}), region(50, 20));
        JXNativeNode content = tabs.getChildren().get(0);
        assertEquals(LEFT, content.getX());
        assertEquals(TOP + 29, content.getY());
        assertEquals(300, content.getWidth());
        assertEquals(100 - 29, content.getHeight());
    }

    // ---- helpers -----------------------------------------------------------------------------

    @Test
    void columnWidthArraysFromObjects() {
        assertArrayEquals(new double[]{50, -1, 2.5}, JXControlLayout.doubles(new Object[]{50, null, 2.5}), 0.001);
        assertArrayEquals(new double[]{1, 2}, JXControlLayout.doubles(new double[]{1, 2}), 0.001);
        assertEquals(0, JXControlLayout.doubles("x").length);
        assertEquals(0, JXControlLayout.doubles(null).length);
    }
}
