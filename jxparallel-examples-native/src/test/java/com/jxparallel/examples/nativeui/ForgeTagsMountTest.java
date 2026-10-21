package com.jxparallel.examples.nativeui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

import com.jxparallel.forge.JxmlLiveView;
import com.jxparallel.forge.JxmlSchema;
import com.jxparallel.forge.JxmlThemes;
import com.jxparallel.forge.JxmlTemplate;
import com.jxparallel.forge.samples.SampleController;
import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.native2d.JXControlLayout;
import com.jxparallel.ui.native2d.JXIcons;
import com.jxparallel.ui.native2d.JXKeyEvent;
import com.jxparallel.ui.native2d.JXTheme;
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
        // by resource, so the includes of a sample (samples/parts/...) are found next to it
        return JxmlTemplate.readResource(ForgeTagsMountTest.class.getClassLoader(), "samples/" + name + ".jxml");
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
        stubs.put("IconButton", new Object[] {"<IconButton icon=\"add\"/>", "button", 1, 0});
        stubs.put("Fab", new Object[] {"<Fab icon=\"add\" text=\"New\"/>", "button", 1, 0});
        stubs.put("Scaffold", new Object[] {"<Scaffold><AppBar title=\"t\"/><Label text=\"body\"/>"
            + "<BottomAppBar><Label text=\"s\"/></BottomAppBar></Scaffold>", "border", 3, 2});
        // an open dialog is a column over the window: its title, its content and its row of buttons
        stubs.put("Dialog", new Object[] {"<Dialog title=\"t\" showing=\"true\"><Label text=\"c\"/><Button slot=\"actions\" text=\"b\"/></Dialog>",
            "dialog", 3, 2});
        stubs.put("AlertDialog", new Object[] {"<AlertDialog title=\"t\" message=\"m\" showing=\"true\"/>", "dialog", 3, 2});
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

    // ---- Material controls, themes and includes ---------------------------------------------------------

    private static JXNativeNode tall(String sample, SampleController c) throws Exception {
        JXNativeNode node = JXNativeNode.createBackendNode(template(sample).build(c));
        node.layoutForBackend(WIDTH, 3000); // room for every row: nothing is squeezed
        return node;
    }

    private static List<JXNativeNode> all(JXNativeNode root, String type) {
        Map<String, List<JXNativeNode>> byType = new LinkedHashMap<String, List<JXNativeNode>>();
        collect(root, byType);
        List<JXNativeNode> found = byType.get(type);
        assertNotNull(found, type);
        return found;
    }

    private static void click(JXNativeNode root, JXNativeNode node, int dx, int dy) {
        JXNativeNode hit = root.hitTest(node.getX() + dx, node.getY() + dy);
        assertNotNull(hit);
        hit.dispatchPointer(new JXPointerEvent(node.getX() + dx, node.getY() + dy, 0));
    }

    @Test
    void theRenderersTokensAndIconsAreTheOnesJxmlNames() {
        for (String theme : JxmlThemes.THEMES) {
            for (String token : JxmlThemes.TOKENS) {
                assertEquals(JXTheme.named(theme).token(token), JxmlThemes.color(theme, token), theme + " " + token);
            }
        }
        assertEquals(JxmlThemes.TOKENS, JXTheme.tokens());
        assertEquals(JxmlThemes.ICONS, JXIcons.names());
    }

    @Test
    void theMaterialSampleMountsWithSizesAndItsControlsAnswerToTheMouse() throws Exception {
        SampleController c = new SampleController();
        JXNativeNode root = tall("material", c);
        for (String type : new String[] {"switch", "icon", "chip", "badge", "avatar", "stepper", "scrollbar", "splitpane", "tabbar"}) {
            for (JXNativeNode node : all(root, type)) {
                assertTrue(node.getWidth() > 0 && node.getHeight() > 0, type + " has a size");
            }
        }
        // a switch
        JXNativeNode sw = all(root, "switch").get(0);
        click(root, sw, 3, 3);
        assertTrue(c.agree);
        // a filter chip: the body selects, the close icon only closes
        JXNativeNode chip = all(root, "chip").get(1);
        float[] parts = JXControlLayout.chipParts(chip);
        c.agree = false;
        click(root, chip, 3, chip.getHeight() / 2);
        assertTrue(c.agree);
        assertTrue(c.calls.isEmpty());
        click(root, chip, (int) (parts[4] + parts[5] / 2), chip.getHeight() / 2);
        assertEquals(Arrays.asList("save"), c.calls);
        // a tab bar: another tab, then a close icon
        JXNativeNode bar = all(root, "tabbar").get(0);
        float[] edges = JXControlLayout.tabbarEdges(bar);
        click(root, bar, (int) (edges[0] + 4), 10);
        assertEquals(0, c.tabIndex);
        click(root, bar, (int) (edges[2] - 6), 10);
        assertEquals(Arrays.asList(Integer.valueOf(1)), c.closed);
        // a scroll bar: grab the thumb and drag
        JXNativeNode scrollbar = all(root, "scrollbar").get(0);
        float[] g = JXControlLayout.scrollBarGeometry(scrollbar);
        c.scroll = 20;
        scrollbar.dispatchPointer(new JXPointerEvent((int) (g[2] + 2), scrollbar.getY() + 3, 0));
        scrollbar.dispatchDrag(new JXPointerEvent((int) (g[2] + 2 + 50), scrollbar.getY() + 3, 0));
        assertTrue(c.scroll > 20, "the thumb followed the pointer: " + c.scroll);
        // a split pane: grab the first divider and drag it
        JXNativeNode split = all(root, "splitpane").get(0);
        float[] divider = JXControlLayout.splitRects(split)[3];
        split.dispatchPointer(new JXPointerEvent((int) (divider[0] + 2), split.getY() + 5, 0));
        split.dispatchDrag(new JXPointerEvent((int) (divider[0] + 2 + 40), split.getY() + 5, 0));
        assertEquals(2, c.positions.length);
        assertTrue(c.positions[0] > 0.3 && c.positions[1] == 0.7, Arrays.toString(c.positions));
    }

    @Test
    void thePanesOfASplitPaneAndTheChildOfABadgeAreLaidOutInOrder() throws Exception {
        JXNativeNode root = tall("material", new SampleController());
        JXNativeNode split = all(root, "splitpane").get(0);
        JXNativeNode a = split.getChildren().get(0);
        JXNativeNode b = split.getChildren().get(1);
        JXNativeNode d = split.getChildren().get(2);
        assertTrue(a.getX() + a.getWidth() < b.getX() && b.getX() + b.getWidth() < d.getX(), "dividers between the panes");
        JXNativeNode stacked = all(root, "splitpane").get(1);
        assertTrue(stacked.getChildren().get(0).getY() < stacked.getChildren().get(1).getY());
        JXNativeNode badge = all(root, "badge").get(0);
        assertEquals(badge.getX(), badge.getChildren().get(0).getX());
        assertTrue(badge.getWidth() >= badge.getChildren().get(0).getWidth());
    }

    // ---- overlays: popups, menus, layers ----------------------------------------------------------------

    private static JXNativeNode mount(String sample, SampleController c, int width, int height) throws Exception {
        JXNativeNode node = JXNativeNode.createBackendNode(template(sample).build(c));
        node.layoutForBackend(width, height);
        return node;
    }

    private static int[] middle(int[] rect) {
        return new int[] {rect[0] + rect[2] / 2, rect[1] + rect[3] / 2};
    }

    private static JXNativeNode press(JXNativeNode root, int[] point) {
        return root.routePointer(new JXPointerEvent(point[0], point[1], 0));
    }

    private static SampleController closed() {
        SampleController c = new SampleController();
        c.comboOpen = false;
        c.menuOpen = false;
        c.contextOpen = false;
        c.dialogOpen = false;
        c.drawerOpen = false;
        c.snackOpen = false;
        return c;
    }

    @Test
    void theComboBoxOfThePickersSampleListsItsOptionsBelowItAndAPressOnOneChoosesIt() throws Exception {
        SampleController c = closed();
        c.comboOpen = true;
        JXNativeNode root = mount("pickers", c, WIDTH, HEIGHT);
        JXNativeNode combo = find(root, "select");
        int[] popup = combo.getPopupBounds();
        assertNotNull(popup);
        assertEquals(combo.getY() + combo.getHeight(), popup[1], "right below the combo box");
        int[][] rows = combo.getPopupCells();
        assertEquals(2, rows.length, "Fortaleza and Recife");
        assertTrue(rows[1][1] > rows[0][1] && rows[0][2] > 0 && rows[0][3] > 0);
        assertSame(combo, press(root, middle(rows[0])));
        assertEquals("Fortaleza", c.city, "onChange reached setCity");
        assertEquals(Arrays.asList("showing false"), c.calls, "and the popup asked to close through onShowing");
        // a press that is not on the popup closes it and reaches nothing under it
        c.calls.clear();
        assertNull(press(root, new int[] {WIDTH - 5, HEIGHT - 5}));
        assertEquals(Arrays.asList("showing false"), c.calls);
        // a closed combo box has no popup, and a press on it asks to open
        JXNativeNode shut = find(mount("pickers", closed(), WIDTH, HEIGHT), "select");
        assertNull(shut.getPopupBounds());
    }

    @Test
    void theDateTimeAndColorPickersOpenTheirPopupsAndReportWhatIsPressed() throws Exception {
        SampleController c = closed();
        c.timeOpen = true;
        JXNativeNode root = mount("pickers", c, WIDTH, HEIGHT);
        JXNativeNode time = all(root, "timepicker").get(0);
        int[][] cells = time.getPopupCells();
        assertEquals(24 + 4, cells.length, "24 hours and the minutes of a 15 minute step");
        press(root, middle(cells[14]));
        assertEquals("14:30", c.time, "an hour keeps the minute of the value");
        press(root, middle(cells[24 + 3]));
        assertEquals("09:45", c.time, "a minute keeps the hour of the value shown (the screen was not built again)");
        assertTrue(c.calls.isEmpty(), "the popup stays open for the next choice");
        SampleController colors = closed();
        colors.colorOpen = true;
        JXNativeNode colorRoot = mount("pickers", colors, WIDTH, HEIGHT);
        JXNativeNode color = all(colorRoot, "colorpicker").get(0);
        assertEquals(40, color.getPopupCells().length);
        press(colorRoot, middle(color.getPopupCells()[0]));
        assertEquals("#000000", colors.accent);
        SampleController dates = closed();
        dates.dateOpen = true;
        JXNativeNode dateRoot = mount("pickers", dates, WIDTH, HEIGHT);
        JXNativeNode date = find(dateRoot, "datepicker");
        int[] calendar = date.getPopupBounds();
        assertNotNull(calendar);
        assertEquals(date.getY() + date.getHeight(), calendar[1]);
        assertTrue(calendar[2] > 0 && calendar[3] > calendar[2] / 2);
        // the first day of the grid is a hit: a press on it reports an ISO date
        press(dateRoot, new int[] {calendar[0] + 10, calendar[1] + calendar[3] - 10});
        assertTrue(dates.date.matches("\\d{4}-\\d{2}-\\d{2}"), dates.date);
        assertNotEquals("2026-10-21", dates.date);
    }

    @Test
    void theMenusSampleHasAMenuBarAMenuButtonAndAContextMenuThatOpenAndRunTheirItems() throws Exception {
        SampleController c = closed();
        c.menuOpen = true;
        JXNativeNode root = mount("menus", c, WIDTH, HEIGHT);
        JXNativeNode bar = find(root, "menubar");
        assertTrue(bar.getWidth() > 0 && bar.getHeight() > 0);
        JXNativeNode file = bar.getChildren().get(0);
        JXNativeNode edit = bar.getChildren().get(1);
        assertTrue(file.getWidth() > 0 && edit.getX() > file.getX());
        // the open menu button lists its items below it, in order
        JXNativeNode button = all(root, "menubutton").get(0);
        int[] panel = button.getPopupBounds();
        assertNotNull(panel);
        assertEquals(button.getY() + button.getHeight(), panel[1]);
        int previous = panel[1];
        for (JXNativeNode row : button.getChildren()) {
            assertTrue(row.getWidth() > 0 && row.getHeight() > 0 && row.getY() >= previous);
            previous = row.getY();
        }
        JXNativeNode open = button.getChildren().get(0);
        assertSame(open, press(root, middle(new int[] {open.getX(), open.getY(), open.getWidth(), open.getHeight()})));
        assertEquals(Arrays.asList("save", "showing false"), c.calls, "the item runs, then the menu asks to close (the sample holds showing)");
        // a title of the bar opens its menu; its disabled item and its separator do nothing; a submenu opens to the right
        c.calls.clear();
        c.menuOpen = false;
        root = mount("menus", c, WIDTH, HEIGHT);
        bar = find(root, "menubar");
        file = bar.getChildren().get(0);
        press(root, new int[] {file.getX() + 3, file.getY() + 3});
        root.layoutForBackend(WIDTH, HEIGHT);
        assertNotNull(bar.getPopupBounds());
        JXNativeNode export = file.getChildren().get(3);
        press(root, new int[] {export.getX() + 3, export.getY() + 3});
        root.layoutForBackend(WIDTH, HEIGHT);
        JXNativeNode pdf = export.getChildren().get(0);
        assertTrue(pdf.getWidth() > 0 && pdf.getX() >= export.getX() + export.getWidth() - 4, "the submenu is to the right of its row");
        press(root, new int[] {pdf.getX() + 3, pdf.getY() + 3});
        assertEquals(Arrays.asList("save"), c.calls);
        root.layoutForBackend(WIDTH, HEIGHT);
        assertNull(bar.getPopupBounds(), "a choice closes the menu");
    }

    @Test
    void theContextMenuOpensOnASecondaryPressOnItsParentAndAsksThroughOnShowing() throws Exception {
        SampleController c = closed();
        JXNativeNode root = mount("menus", c, WIDTH, HEIGHT);
        JXNativeNode context = find(root, "contextmenu");
        assertEquals(0, context.getWidth() * context.getHeight(), "closed, it takes no room");
        JXNativeNode stack = null;
        for (JXNativeNode n : all(root, "stack")) {
            if (n.getChildren().contains(context)) {
                stack = n;
            }
        }
        assertNotNull(stack);
        root.routePointer(new JXPointerEvent(stack.getX() + 5, stack.getY() + 5, 1));
        assertEquals(Arrays.asList("showing true"), c.calls, "the sample's showing is the application's: the press asks");
        c.contextOpen = true;
        root = mount("menus", c, WIDTH, HEIGHT);
        context = find(root, "contextmenu");
        assertTrue(context.getWidth() > 0 && context.getHeight() > 0, "open, it is its panel");
        assertEquals(2, context.getChildren().size());
        assertTrue(context.getChildren().get(1).getY() > context.getChildren().get(0).getY());
    }

    @Test
    void theLayersSampleDrawsADialogADrawerAndASnackBarOverTheScreenAndTheyTakeThePointerFirst() throws Exception {
        SampleController c = closed();
        c.snackOpen = true;
        JXNativeNode root = mount("layers", c, WIDTH, HEIGHT);
        JXNativeNode snack = find(root, "snackbar");
        assertTrue(snack.getWidth() > 0 && snack.getY() + snack.getHeight() < HEIGHT);
        assertEquals((WIDTH - snack.getWidth()) / 2, snack.getX());
        press(root, new int[] {snack.getX() + snack.getWidth() - 8, snack.getY() + 8});
        assertEquals(Arrays.asList("save", "showing false"), c.calls, "the action, then the snack bar asked to close");
        c.calls.clear();
        c.dialogOpen = true;
        root = mount("layers", c, WIDTH, HEIGHT);
        JXNativeNode dialog = find(root, "dialog");
        assertTrue(dialog.getWidth() >= 280 && dialog.getHeight() > 0);
        assertEquals((WIDTH - dialog.getWidth()) / 2, dialog.getX());
        JXNativeNode under = null;
        for (JXNativeNode n : all(root, "button")) {
            if ("Under".equals(n.getProperty("label"))) {
                under = n;
            }
        }
        assertNotNull(under);
        assertNull(press(root, new int[] {under.getX() + 2, under.getY() + 2}), "the scrim takes the press that was on the button");
        assertEquals(Arrays.asList("showing false"), c.calls, "and asks the dialog to close");
        c.calls.clear();
        JXNativeNode bar = dialog.getChildren().get(dialog.getChildren().size() - 1);
        assertEquals("buttonbar", bar.getType());
        JXNativeNode delete = bar.getChildren().get(1);
        press(root, middle(new int[] {delete.getX(), delete.getY(), delete.getWidth(), delete.getHeight()}));
        assertEquals(Arrays.asList("save"), c.calls);
        c.calls.clear();
        root.routeKey(new JXKeyEvent(257, '\0'), null);
        assertEquals(Arrays.asList("save"), c.calls, "Enter presses the default button");
        c.calls.clear();
        root.routeKey(new JXKeyEvent(256, '\0'), null);
        assertEquals(Arrays.asList("showing false"), c.calls, "Escape asks to close");
        c.dialogOpen = false;
        c.drawerOpen = true;
        root = mount("layers", c, WIDTH, HEIGHT);
        JXNativeNode drawer = find(root, "drawer");
        assertEquals(WIDTH, drawer.getX() + drawer.getWidth(), "side=right");
        assertEquals(HEIGHT, drawer.getHeight());
        assertTrue(drawer.getChildren().get(1).getY() > drawer.getChildren().get(0).getY());
    }

    @Test
    void theTreesSampleLaysOutOnlyTheRowsOfItsWindowWithTheirIndentation() throws Exception {
        SampleController c = new SampleController();
        c.top = 0;
        JXNativeNode root = mount("trees", c, WIDTH, 1000);
        JXNativeNode tree = all(root, "list").get(0);
        // src (expanded), main (open), java, resources, test, then the folders: src, docs, README.md (docs closed)
        assertEquals(12, tree.getChildren().size(), "visible rows: " + describe(tree));
        assertEquals(12, ((Integer) tree.getProperty("itemCount")).intValue());
        JXControlLayout.ScrollGeometry g = JXControlLayout.scrollGeometry(tree);
        JXNativeNode previous = null;
        for (JXNativeNode row : tree.getChildren()) {
            assertEquals("cell", row.getType());
            assertEquals(22, row.getHeight());
            assertTrue(row.getWidth() > 0);
            if (previous != null) {
                assertEquals(previous.getY() + 22, row.getY());
            }
            previous = row;
        }
        assertEquals(Math.round(g.viewY), tree.getChildren().get(0).getY());
        JXNativeNode src = tree.getChildren().get(0);
        JXNativeNode main = tree.getChildren().get(1);
        JXNativeNode java = tree.getChildren().get(2);
        assertEquals("expanded", src.getProperty("disclosure"));
        assertEquals("expanded", main.getProperty("disclosure"));
        assertEquals("leaf", java.getProperty("disclosure"));
        assertEquals(Double.valueOf(2), java.getProperty("indent"));
        assertEquals(JXControlLayout.treeLeft(java), JXControlLayout.treeLeft(src) + 2 * JXControlLayout.TREE_INDENT, 0);
        // a press on the arrow of src toggles it; a press on its text selects it
        press(root, new int[] {src.getX() + 7 + 4, src.getY() + 5});
        press(root, new int[] {src.getX() + 200, src.getY() + 5});
        assertEquals(Arrays.asList("save", "save"), c.calls, "the toggle and the select handlers of the first item are both #save");
        // a window: three rows from the second
        c.top = 1;
        c.calls.clear();
        JXNativeNode scrolled = mount("trees", c, WIDTH, 1000);
        JXNativeNode list = all(scrolled, "list").get(0);
        assertEquals(1, ((Integer) list.getProperty("first")).intValue());
        assertEquals(11, list.getChildren().size(), "the rows after the first");
        assertEquals(Double.valueOf(22), list.getProperty("scrollY"));
        JXControlLayout.ScrollGeometry gs = JXControlLayout.scrollGeometry(list);
        assertEquals(22, gs.scrollY, 0);
    }

    private static String describe(JXNativeNode tree) {
        StringBuilder s = new StringBuilder();
        for (JXNativeNode row : tree.getChildren()) {
            s.append(row.getProperty("value")).append(' ');
        }
        return s.toString();
    }

    @Test
    void scrollingATreeWhoseItemsHaveKeysKeepsTheNodesOfTheRowsThatStay() throws Exception {
        StringBuilder xml = new StringBuilder("<Tree controller=\"com.jxparallel.forge.samples.SampleController\" first=\"${top}\" "
                + "rows=\"6\" rowHeight=\"20\" prefHeight=\"100\">");
        for (int i = 0; i < 10; i++) {
            xml.append("<TreeItem text=\"item").append(i).append("\" key=\"k").append(i).append("\"/>");
        }
        xml.append("</Tree>");
        JxmlTemplate template = JxmlTemplate.read(new java.io.ByteArrayInputStream(xml.toString().getBytes("UTF-8")), "keyed.jxml");
        SampleController c = new SampleController();
        c.top = 2;
        JXNativeNode root = JXNativeNode.createBackendNode(template.build(c));
        root.layoutForBackend(WIDTH, 100);
        List<JXNativeNode> rows = new ArrayList<JXNativeNode>(root.getChildren()); // items 2..7
        assertEquals(6, rows.size());
        c.top = 3;
        assertTrue(root.reconcile(template.build(c)));
        root.layoutForBackend(WIDTH, 100);
        assertEquals(6, root.getChildren().size()); // items 3..8
        for (int i = 1; i < 6; i++) {
            assertSame(rows.get(i), root.getChildren().get(i - 1), "the node of item " + (2 + i) + " is reused, not rebuilt");
        }
        assertEquals(Math.round(JXControlLayout.scrollGeometry(root).viewY), root.getChildren().get(0).getY());
        assertEquals(3, JXControlLayout.itemAt(root, JXControlLayout.scrollGeometry(root).viewY + 1));
    }

    @Test
    void aLiveViewReplacesWhatIsMountedWhenTheFileIsSavedAndAMistakeKeepsTheScreen() throws Exception {
        java.nio.file.Path file = java.nio.file.Files.createTempFile("live", ".jxml");
        try {
            java.nio.file.Files.write(file, "<Column><Label text=\"one\"/></Column>".getBytes("UTF-8"));
            List<String> errors = new ArrayList<String>();
            try (JxmlLiveView live = JxmlLiveView.open(file.toFile()).onError(e -> errors.add(e.getMessage()))) {
                // what a window does: build the screen, mount it; on a reload, build again and reconcile in place
                JXNativeNode root = mount(live.build(null));
                assertEquals("one", root.getChildren().get(0).getProperty("value"));
                java.nio.file.Files.write(file, "<Column><Label text=\"two\"/><Button text=\"new\"/></Column>".getBytes("UTF-8"));
                assertTrue(live.reload());
                assertTrue(root.reconcile(live.build(null)));
                root.layoutForBackend(WIDTH, HEIGHT);
                assertEquals(2, root.getChildren().size());
                assertEquals("two", root.getChildren().get(0).getProperty("value"));
                assertTrue(root.getChildren().get(1).getWidth() > 0);
                java.nio.file.Files.write(file, "<Column><Label text=".getBytes("UTF-8"));
                assertTrue(!live.reload());
                assertEquals(1, errors.size());
                assertTrue(root.reconcile(live.build(null)));
                assertEquals(2, root.getChildren().size(), "the last good screen stays");
            }
        } finally {
            java.nio.file.Files.deleteIfExists(file);
        }
    }

    @Test
    void theTreeTableOfTheTreesSampleHasItsColumnsAndRowsOfCells() throws Exception {
        JXNativeNode root = mount("trees", new SampleController(), WIDTH, 1000);
        JXNativeNode table = all(root, "table").get(0);
        float[] widths = JXControlLayout.columnWidths(table);
        assertEquals(2, widths.length);
        assertTrue(widths[0] > 0 && widths[1] > 0);
        assertEquals(4, ((Integer) table.getProperty("itemCount")).intValue(), "docs, guide.md, the item with the title, README.md");
        assertEquals(4, table.getChildren().size());
        JXNativeNode docs = table.getChildren().get(0);
        assertEquals("tablerow", docs.getType());
        assertEquals(2, docs.getChildren().size());
        assertEquals("expanded", docs.getChildren().get(0).getProperty("disclosure"));
        JXNativeNode guide = table.getChildren().get(1);
        assertEquals(Double.valueOf(1), guide.getChildren().get(0).getProperty("indent"));
        assertTrue(guide.getChildren().get(0).getWidth() > 0 && guide.getChildren().get(1).getWidth() > 0);
        assertTrue(guide.getChildren().get(0).getX() < guide.getChildren().get(1).getX());
        assertEquals(3, table.getChildren().get(2).getChildren().size(), "the item whose values are the two cities");
        // a press on the arrow cell toggles, a press on the size cell selects the row
        SampleController c = new SampleController();
        root = mount("trees", c, WIDTH, 1000);
        table = all(root, "table").get(0);
        docs = table.getChildren().get(0);
        JXNativeNode name = docs.getChildren().get(0);
        press(root, new int[] {name.getX() + 2 + 4, name.getY() + 5});
        press(root, new int[] {docs.getChildren().get(1).getX() + 3, docs.getChildren().get(1).getY() + 5});
        assertEquals(Arrays.asList("save", "save"), c.calls);
    }

    @Test
    void aDarkRootCarriesItsThemeAndAStyledScreenKeepsItsPartsInPlace() throws Exception {
        SampleController c = new SampleController();
        JXNativeNode root = tall("includes", c);
        assertEquals("dark", root.getProperty("theme"));
        JXNativeNode header = root.getChildren().get(0);
        assertEquals("row", header.getType());
        assertEquals("always", header.getProperty("hgrow"));
        assertEquals(2, header.getChildren().size());
        assertEquals(Integer.valueOf(0xFF90CAF9), header.getChildren().get(0).getProperty("textFill"));
        assertTrue(header.getChildren().get(1).getWidth() == 16 && header.getChildren().get(1).getHeight() == 16, "the icon of a part");
    }}
