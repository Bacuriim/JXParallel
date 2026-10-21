package com.jxparallel.examples.nativeui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jxparallel.forge.JxmlSchema;
import com.jxparallel.forge.JxmlTemplate;
import com.jxparallel.forge.samples.SampleController;
import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.native2d.JXControlLayout;
import com.jxparallel.ui.native2d.JXNativeNode;
import com.jxparallel.ui.native2d.JXPointerEvent;

/**
 * The sample screens of the forge tests (one set for the interpreter, the compiler and the renderer), mounted
 * on the native renderer without a window: every tag becomes a node of the type the schema promises, the layout
 * runs, and lists, tables and clicks behave. Nothing here depends on the system font: it checks types, counts,
 * order, non-zero sizes and the sizes the screens write.
 */
class ForgeTagsMountTest {
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;

    private static List<File> samples() throws Exception {
        File dir = new File(ForgeTagsMountTest.class.getResource("/samples").toURI());
        File[] files = dir.listFiles();
        assertNotNull(files);
        List<File> out = new ArrayList<File>();
        for (File f : files) {
            if (f.getName().endsWith(".jxml")) {
                out.add(f);
            }
        }
        Collections.sort(out);
        assertTrue(out.size() >= 8, "the shared samples are on the classpath: " + out);
        return out;
    }

    private static JxmlTemplate template(String name) throws Exception {
        InputStream in = ForgeTagsMountTest.class.getResourceAsStream("/samples/" + name + ".jxml");
        try {
            return JxmlTemplate.read(in, name + ".jxml");
        } finally {
            in.close();
        }
    }

    private static JXNativeNode mount(JXElement element) {
        JXNativeNode node = JXNativeNode.createBackendNode(element);
        node.layoutForBackend(WIDTH, HEIGHT);
        return node;
    }

    private static JXNativeNode mount(String sample, SampleController c) throws Exception {
        return mount(template(sample).build(c));
    }

    /** The node tree has the element tree's types and child counts; nothing has a negative size. */
    private static void assertMirrors(JXElement element, JXNativeNode node, String where) {
        assertEquals(element.getType(), node.getType(), where);
        assertEquals(element.getChildren().size(), node.getChildren().size(), where + element.getType());
        assertTrue(node.getWidth() >= 0 && node.getHeight() >= 0, where + element.getType() + " has a negative size");
        for (int i = 0; i < element.getChildren().size(); i++) {
            assertMirrors(element.getChildren().get(i), node.getChildren().get(i), where + element.getType() + "/");
        }
    }

    private static void collect(JXNativeNode node, Map<String, List<JXNativeNode>> byType) {
        List<JXNativeNode> list = byType.get(node.getType());
        if (list == null) {
            list = new ArrayList<JXNativeNode>();
            byType.put(node.getType(), list);
        }
        list.add(node);
        for (JXNativeNode child : node.getChildren()) {
            collect(child, byType);
        }
    }

    private static JXNativeNode find(JXNativeNode node, String type) {
        Map<String, List<JXNativeNode>> byType = new LinkedHashMap<String, List<JXNativeNode>>();
        collect(node, byType);
        List<JXNativeNode> found = byType.get(type);
        assertNotNull(found, type + " in the tree");
        return found.get(0);
    }

    // ---- every tag -----------------------------------------------------------------------------------

    @Test
    void everySampleMountsAndEveryTagBecomesANodeWithASize() throws Exception {
        Map<String, List<JXNativeNode>> byType = new LinkedHashMap<String, List<JXNativeNode>>();
        for (File sample : samples()) {
            String name = sample.getName().substring(0, sample.getName().length() - ".jxml".length());
            JXElement element = template(name).build(new SampleController());
            JXNativeNode node = mount(element);
            assertMirrors(element, node, name + ":");
            assertTrue(node.getWidth() > 0 && node.getHeight() > 0, name + " root has a size");
            collect(node, byType);
            mountDeferredTabs(element, name, byType);
        }
        for (JxmlSchema.Tag tag : JxmlSchema.tags().values()) {
            String type = tag.nativeType;
            if (tag.alias != null) {
                type = JxmlSchema.tag(tag.alias).nativeType;
            }
            if (type == null) {
                continue; // loops, slots, tabs' tab and the structural composites: their primitives are checked by their own types
            }
            List<JXNativeNode> nodes = byType.get(type);
            assertNotNull(nodes, "<" + tag.name + "> mounted as a " + type);
            boolean sized = false;
            for (JXNativeNode n : nodes) {
                sized |= n.getWidth() > 0 && n.getHeight() > 0;
            }
            assertTrue(sized, "<" + tag.name + "> (" + type + ") has a node with a size");
        }
    }

    /** A minimal use of each structural composite (one that is not just a renamed tag), and what it must mount as. */
    private static Map<String, Object[]> structuralStubs() {
        Map<String, Object[]> stubs = new LinkedHashMap<String, Object[]>();
        // name -> {xml, native type of the stub's root, its child count, index of the child that must have a size (or -1)}
        stubs.put("SizedBox", new Object[] {"<SizedBox width=\"30\" height=\"20\"><Label text=\"x\"/></SizedBox>", "stack", 1, -1});
        stubs.put("Spacer", new Object[] {"<Row><Label text=\"a\"/><Spacer/><Label text=\"b\"/></Row>", "row", 3, 1});
        stubs.put("Expanded", new Object[] {"<Row><Label text=\"a\"/><Expanded><Button text=\"x\"/></Expanded></Row>", "row", 2, 1});
        stubs.put("Flexible", new Object[] {"<Row><Label text=\"a\"/><Flexible><Button text=\"x\"/></Flexible></Row>", "row", 2, 1});
        stubs.put("ListTile", new Object[] {"<ListTile title=\"t\" subtitle=\"s\"><Label slot=\"leading\" text=\"l\"/></ListTile>",
            "row", 2, 1});
        stubs.put("AppBar", new Object[] {"<AppBar title=\"t\"><Button text=\"b\"/></AppBar>", "row", 2, 1});
        stubs.put("NavigationBar", new Object[] {"<NavigationBar><Button text=\"a\"/><Button text=\"b\"/></NavigationBar>", "row", 2, 1});
        stubs.put("Scaffold", new Object[] {"<Scaffold><AppBar title=\"t\"/><Label text=\"body\"/>"
            + "<BottomAppBar><Label text=\"s\"/></BottomAppBar></Scaffold>", "border", 3, 2});
        return stubs;
    }

    @Test
    void everyStructuralCompositeMountsAsWhatItExpandsTo() throws Exception {
        Map<String, Object[]> stubs = structuralStubs();
        for (JxmlSchema.Tag tag : JxmlSchema.tags().values()) {
            if (tag.composite && tag.alias == null) {
                assertNotNull(stubs.get(tag.name), "<" + tag.name + "> needs a minimal use in structuralStubs()");
            }
        }
        for (Map.Entry<String, Object[]> stub : stubs.entrySet()) {
            Object[] s = stub.getValue();
            JXNativeNode column = mount(JxmlTemplate.read(new java.io.ByteArrayInputStream(("<Column>" + s[0] + "</Column>")
                    .getBytes("UTF-8")), stub.getKey() + ".jxml").build(null));
            JXNativeNode node = column.getChildren().get(0);
            assertEquals(s[1], node.getType(), stub.getKey());
            assertEquals(((Integer) s[2]).intValue(), node.getChildren().size(), stub.getKey());
            assertTrue(node.getWidth() > 0 && node.getHeight() > 0, stub.getKey() + " has a size");
            int at = ((Integer) s[3]).intValue();
            if (at >= 0) {
                JXNativeNode part = node.getChildren().get(at);
                assertTrue(part.getWidth() > 0 && part.getHeight() > 0, stub.getKey() + " part " + at + " has a size");
            }
        }
        // sizes the file writes are the sizes laid out
        JXNativeNode sized = mount(JxmlTemplate.read(new java.io.ByteArrayInputStream(
                "<Column><SizedBox width=\"30\" height=\"20\"><Label text=\"x\"/></SizedBox></Column>".getBytes("UTF-8")), "s.jxml")
                .build(null)).getChildren().get(0);
        assertEquals(30, sized.getWidth());
        assertEquals(20, sized.getHeight());
    }

    /** The tabs a tab pane does not show are built too (deferredTabs): mount each as a screen of its own. */
    private static void mountDeferredTabs(JXElement element, String where, Map<String, List<JXNativeNode>> byType) {
        Object deferred = element.getProps().get("deferredTabs");
        if (deferred instanceof JXElement[]) {
            for (JXElement tab : (JXElement[]) deferred) {
                JXNativeNode node = mount(tab);
                assertMirrors(tab, node, where + ":deferred:");
                collect(node, byType);
                mountDeferredTabs(tab, where, byType);
            }
        }
        for (JXElement child : element.getChildren()) {
            mountDeferredTabs(child, where, byType);
        }
    }

    @Test
    void childrenKeepTheirOrderInTheLayout() throws Exception {
        JXNativeNode column = mount("controls", new SampleController());
        int previous = -1;
        for (JXNativeNode child : column.getChildren()) {
            assertTrue(child.getY() >= previous, child.getType() + " is below the one before");
            previous = child.getY();
        }
        JXNativeNode scaffold = mount("bars", new SampleController());
        assertEquals("border", scaffold.getType());
        JXNativeNode top = scaffold.getChildren().get(0);
        JXNativeNode body = scaffold.getChildren().get(1);
        JXNativeNode bottom = scaffold.getChildren().get(2);
        assertTrue(top.getY() < body.getY() && body.getY() < bottom.getY(), "app bar, body, bottom bar from top to bottom");
        assertTrue(top.getWidth() > 0 && body.getWidth() > 0 && bottom.getWidth() > 0);
    }

    // ---- ListView ------------------------------------------------------------------------------------

    @Test
    void aListLaysOutOnlyItsWindowAtTheScrollOffset() throws Exception {
        SampleController c = new SampleController(); // top = 2, rows = 6, cellHeight = 24, 40 items
        JXNativeNode list = find(mount("list", c), "list");
        assertEquals(6, list.getChildren().size());
        JXControlLayout.ScrollGeometry g = JXControlLayout.scrollGeometry(list);
        assertEquals(40 * 24, g.contentH, 0, "the scroll range is that of the whole model");
        assertEquals(2 * 24, g.scrollY, 0);
        JXNativeNode first = list.getChildren().get(0);
        assertEquals(Math.round(g.viewY), first.getY(), "the first row of the window is at the top of the viewport");
        for (int i = 0; i < 6; i++) {
            JXNativeNode cell = list.getChildren().get(i);
            assertEquals(24, cell.getHeight());
            assertTrue(cell.getWidth() > 0);
            assertEquals(first.getY() + 24 * i, cell.getY());
        }
        assertEquals(2, JXControlLayout.itemAt(list, g.viewY + 1), "the item under the top of the viewport is the first row");
        assertEquals(3, JXControlLayout.itemAt(list, g.viewY + 25));
    }

    @Test
    void scrollingAListKeepsTheNodesOfTheRowsThatStay() throws Exception {
        SampleController c = new SampleController();
        JxmlTemplate template = template("list");
        JXNativeNode root = mount(template.build(c));
        JXNativeNode list = find(root, "list");
        List<JXNativeNode> nodes = new ArrayList<JXNativeNode>(list.getChildren()); // rows 2..7
        c.top = 3;
        JXElement after = template.build(c);
        assertTrue(root.reconcile(after));
        root.layoutForBackend(WIDTH, HEIGHT);
        list = find(root, "list");
        assertEquals(6, list.getChildren().size()); // rows 3..8
        for (int i = 1; i < 6; i++) {
            assertSame(nodes.get(i), list.getChildren().get(i - 1), "the node of row " + (2 + i) + " is reused, not rebuilt");
        }
        assertEquals(Math.round(JXControlLayout.scrollGeometry(list).viewY), list.getChildren().get(0).getY());
        assertEquals(3, JXControlLayout.itemAt(list, JXControlLayout.scrollGeometry(list).viewY + 1));
    }

    @Test
    void aClickOnARowReachesItsHandlerWithItsItem() throws Exception {
        SampleController c = new SampleController();
        JXNativeNode list = find(mount("list", c), "list");
        JXNativeNode cell = list.getChildren().get(2); // item 4
        JXNativeNode hit = list.hitTest(cell.getX() + 5, cell.getY() + 5);
        assertSame(cell, hit);
        hit.dispatchPointer(new JXPointerEvent(cell.getX() + 5, cell.getY() + 5, 0));
        assertEquals(Arrays.asList("select Item 4"), c.calls);
    }

    // ---- TableView -----------------------------------------------------------------------------------

    @Test
    void aTableLaysOutRowsOfCellsUnderColumns() throws Exception {
        JXNativeNode table = find(mount("table", new SampleController()), "table"); // 8 rows from item 2
        assertEquals(8, table.getChildren().size());
        float[] widths = JXControlLayout.columnWidths(table);
        assertEquals(3, widths.length);
        float sum = 0;
        for (float w : widths) {
            assertTrue(w > 0);
            sum += w;
        }
        JXNativeNode previous = null;
        for (JXNativeNode row : table.getChildren()) {
            assertEquals("tablerow", row.getType());
            assertEquals(3, row.getChildren().size());
            assertEquals(24, row.getHeight());
            if (previous != null) {
                assertEquals(previous.getY() + 24, row.getY());
            }
            previous = row;
            int x = row.getChildren().get(0).getX();
            for (JXNativeNode cell : row.getChildren()) {
                assertEquals("cell", cell.getType());
                assertTrue(cell.getWidth() > 0 && cell.getHeight() > 0);
                assertTrue(cell.getX() >= x);
                x = cell.getX();
            }
        }
        assertEquals(40 * 24, JXControlLayout.scrollGeometry(table).contentH, 0);
        assertEquals(2, JXControlLayout.itemAt(table, JXControlLayout.scrollGeometry(table).viewY + 1));
    }

    // ---- groups, tiles, anchors ------------------------------------------------------------------------

    @Test
    void aClickOnARadioButtonOrToggleChoosesItsValue() throws Exception {
        SampleController c = new SampleController(); // the group holds "b"
        JXNativeNode column = mount("controls", c);
        List<JXNativeNode> radios = new ArrayList<JXNativeNode>();
        List<JXNativeNode> toggles = new ArrayList<JXNativeNode>();
        for (JXNativeNode child : column.getChildren()) {
            if ("radio".equals(child.getType())) {
                radios.add(child);
            } else if ("toggle".equals(child.getType())) {
                toggles.add(child);
            }
        }
        JXNativeNode a = radios.get(0);
        JXNativeNode b = radios.get(1);
        assertEquals(Boolean.FALSE, a.getProperty("checked"));
        assertEquals(Boolean.TRUE, b.getProperty("checked"));
        assertTrue(a.getWidth() > 0 && a.getHeight() > 0);
        a.dispatchPointer(new JXPointerEvent(a.getX() + 2, a.getY() + 2, 0));
        assertEquals("a", c.choice);
        c.choice = "b";
        toggles.get(2).dispatchPointer(new JXPointerEvent(0, 0, 0)); // "Left": group="@{choice}" value="a"
        assertEquals("a", c.choice);
    }

    @Test
    void aClickOnAListTileReachesItsRowNotItsLabels() throws Exception {
        SampleController c = new SampleController();
        JXNativeNode column = mount("composites", c);
        JXNativeNode tile = null;
        for (JXNativeNode child : column.getChildren()) {
            if ("row".equals(child.getType()) && child.getChildren().size() == 3
                    && "column".equals(child.getChildren().get(1).getType())) {
                tile = child;
            }
        }
        assertNotNull(tile, "the ListTile with leading and trailing");
        JXNativeNode texts = tile.getChildren().get(1);
        assertTrue(texts.getWidth() > 0 && texts.getHeight() > 0, "the texts take the free width");
        int x = texts.getX() + texts.getWidth() / 2;
        int y = texts.getY() + 2;
        JXNativeNode hit = tile.hitTest(x, y);
        assertSame(tile, hit, "the labels do not pick the pointer");
        hit.dispatchPointer(new JXPointerEvent(x, y, 0));
        assertEquals(Arrays.asList("save"), c.calls);
    }

    @Test
    void tilesFlowsAndAnchorsPlaceTheirChildren() throws Exception {
        JXNativeNode column = mount("containers", new SampleController());
        JXNativeNode flow = find(column, "flow");
        assertEquals(3, flow.getChildren().size());
        for (JXNativeNode child : flow.getChildren()) {
            assertTrue(child.getWidth() > 0 && child.getHeight() > 0);
        }
        JXNativeNode tile = find(column, "tile");
        assertEquals(4, tile.getChildren().size());
        assertEquals(tile.getChildren().get(0).getWidth(), tile.getChildren().get(1).getWidth(), "tiles are one size");
        JXNativeNode anchor = find(column, "anchor");
        JXNativeNode topLeft = anchor.getChildren().get(0);
        JXNativeNode bottomRight = anchor.getChildren().get(1);
        assertEquals(anchor.getX() + 2 + 4, topLeft.getX(), "left=4 inside 2 of padding");
        assertEquals(anchor.getY() + 2 + 4, topLeft.getY());
        assertTrue(bottomRight.getX() > topLeft.getX() && bottomRight.getY() > topLeft.getY(), "right and bottom anchors");
        JXNativeNode pane = find(column, "pane");
        assertEquals(pane.getX() + 10, pane.getChildren().get(0).getX(), "x=10 in a pane");
        assertTrue(pane.getWidth() >= 120, "prefWidth=120, and the column stretches it");
        assertEquals(60, pane.getHeight());
    }

    @Test
    void anAccordionStacksItsPanesAndACollapsedPaneIsOnlyItsTitle() throws Exception {
        SampleController c = new SampleController();
        c.open = false;
        JXNativeNode column = mount("panes", c);
        JXNativeNode accordion = find(column, "accordion");
        assertEquals(2, accordion.getChildren().size());
        JXNativeNode one = accordion.getChildren().get(0);
        JXNativeNode two = accordion.getChildren().get(1);
        assertEquals("titled", one.getType());
        assertTrue(one.getHeight() > 0 && two.getHeight() > 0 && two.getY() > one.getY());
        assertTrue(two.collapsed());
        assertTrue(!one.collapsed());
        // a click on the title bar of the first pane reaches its onAction
        int x = one.getX() + 5;
        int y = one.getY() + 5;
        assertSame(one, one.hitTest(x, y));
        one.dispatchPointer(new JXPointerEvent(x, y, 0));
        assertEquals(Arrays.asList("save"), c.calls);
        JXNativeNode pagination = find(column, "pagination");
        assertEquals(1, pagination.getChildren().size());
        assertTrue(pagination.getHeight() > JXControlLayout.TABLE_HEADER, "the page and the page buttons");
    }

    @Test
    void hiddenAndFadedNodesKeepTheirPlace() throws Exception {
        SampleController c = new SampleController();
        c.hidden = true;
        JXNativeNode column = mount("containers", c);
        JXNativeNode styled = column.getChildren().get(column.getChildren().size() - 1);
        assertEquals(Boolean.TRUE, styled.getProperty("hidden"));
        assertEquals(Double.valueOf(0.5), styled.getProperty("opacity"));
        assertEquals(Integer.valueOf(0xFFFFEEDD), styled.getProperty("background"));
        assertTrue(styled.getWidth() > 0 && styled.getHeight() > 0, "hidden keeps its place in the layout");
    }
}
