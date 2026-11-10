package com.jxparallel.examples.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jxparallel.forge.JxmlTemplate;
import com.jxparallel.forge.JxmlTrees;
import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.native2d.JXNativeNode;
import com.jxparallel.ui.resource.JXImage;
import com.jxparallel.ui.resource.JXImages;

/**
 * Every screen of JX Orders, built from its controller, mounted on the native renderer without a window and laid out:
 * the nodes the screen promises exist with a size, the virtual table holds all 10,000 orders and builds a window of
 * them, and the compiled classes build the same trees as the interpreter reading the same .jxml files.
 */
class OrdersScreensTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final int W = 1200;
    private static final int H = 760;

    private TestHost host;
    private Session session;
    private ShellController shell;

    @BeforeEach
    void setUp() throws Exception {
        host = new TestHost();
        session = new Session(host, () -> TODAY);
        shell = new ShellController(session);
        shell.loadSample(10_000).get(60, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() {
        host.ui.shutdownNow();
    }

    private static JXNativeNode mount(JXElement e) {
        JXNativeNode n = JXNativeNode.createBackendNode(e);
        n.layoutForBackend(W, H);
        return n;
    }

    private static List<JXNativeNode> all(JXNativeNode root, String type) {
        List<JXNativeNode> out = new ArrayList<JXNativeNode>();
        collect(root, type, out);
        return out;
    }

    private static void collect(JXNativeNode n, String type, List<JXNativeNode> out) {
        if (type.equals(n.getType())) {
            out.add(n);
        }
        for (JXNativeNode c : n.getChildren()) {
            collect(c, type, out);
        }
    }

    private static void assertSized(JXNativeNode n, String where) {
        assertTrue(n.getWidth() >= 0 && n.getHeight() >= 0, where + " " + n.getType() + " has a negative size");
        for (JXNativeNode c : n.getChildren()) {
            assertSized(c, where + "/" + n.getType());
        }
    }

    private static boolean hasText(JXNativeNode root, String text) {
        for (JXNativeNode t : all(root, "#text")) {
            if (text.equals(t.getProperty("value"))) {
                return true;
            }
        }
        return false;
    }

    // ---- each screen, in both themes --------------------------------------------------------------------------------

    @Test
    void everyScreenMountsInBothThemes() {
        for (String theme : new String[] {"light", "dark"}) {
            shell.setTheme(theme);
            for (ShellController.Screen screen : ShellController.Screen.values()) {
                shell.show(screen);
                JXElement element = shell.view();
                assertEquals(theme, element.getProps().get("theme"));
                JXNativeNode root = mount(element);
                String where = theme + "/" + screen;
                assertEquals("stack", root.getType(), where);
                assertTrue(root.getWidth() == W && root.getHeight() == H, where + " fills the window");
                assertSized(root, where);
                assertEquals(1, all(root, "menubar").size(), where);
                assertEquals(3, all(root, "menu").size(), where + ": File, View, Help");
                assertTrue(hasText(root, "JX Orders"), where);
                assertTrue(hasText(root, "10,000 orders"), where + ": the status bar's count");
            }
        }
    }

    @Test
    void theMenuHasTheFileViewAndHelpItems() {
        JXNativeNode root = mount(shell.view());
        List<JXNativeNode> menus = all(root, "menu");
        List<String> titles = new ArrayList<String>();
        for (JXNativeNode m : menus) {
            titles.add(String.valueOf(m.getProperty("label")));
        }
        assertEquals(Arrays.asList("File", "View", "Help"), titles);
        assertEquals(6, menus.get(0).getChildren().size(), "New order, Open CSV, Export CSV, Exit and two separators");
        List<String> items = new ArrayList<String>();
        for (JXNativeNode item : all(root, "menuitem")) {
            items.add(String.valueOf(item.getProperty("label")));
        }
        assertTrue(items.containsAll(Arrays.asList("New order", "Open CSV…", "Export CSV…", "Exit", "Light theme", "Dark theme", "About")), items.toString());
    }

    @Test
    void theOrdersScreenHoldsTenThousandRowsAndBuildsAWindowOfThem() {
        shell.show(ShellController.Screen.ORDERS);
        JXNativeNode root = mount(shell.view());
        List<JXNativeNode> tables = all(root, "table");
        assertEquals(1, tables.size());
        JXNativeNode table = tables.get(0);
        assertEquals(10000.0, ((Number) table.getProperty("itemCount")).doubleValue());
        assertEquals(40, table.getChildren().size(), "rows=40: only the window is built");
        assertEquals(5, table.getChildren().get(0).getChildren().size(), "id, customer, date, status, total");
        assertEquals("1", String.valueOf(table.getChildren().get(0).getChildren().get(0).getProperty("value")));
        assertEquals("multiple", table.getProperty("selectionMode"));
        assertEquals(1, all(root, "input").size(), "the search box only: the editor is closed, so its four inputs are not mounted");
        assertEquals(2, all(root, "select").size(), "status filter and sort; the editor's status is in the closed editor");
        assertEquals(1, all(root, "checkbox").size());
        assertTrue(table.getWidth() > 400 && table.getHeight() > 300, table.getWidth() + "x" + table.getHeight());
        assertTrue(hasText(root, "No order selected"));
    }

    @Test
    void theTableWindowFollowsTheScrollPositionOverAllTheOrders() {
        shell.orders().setTop(5000);
        JXNativeNode table = all(mount(shell.view()), "table").get(0);
        assertEquals("5001", String.valueOf(table.getChildren().get(0).getChildren().get(0).getProperty("value")));
        assertEquals(5000.0, ((Number) table.getProperty("first")).doubleValue());
        shell.orders().setTop(9990);
        table = all(mount(shell.view()), "table").get(0);
        assertEquals(10, table.getChildren().size(), "the last rows: fewer than the window");
        assertEquals("10000", String.valueOf(table.getChildren().get(9).getChildren().get(0).getProperty("value")));
    }

    @Test
    void theDetailsPanelShowsTheSelectedOrder() {
        shell.orders().select(77);
        JXNativeNode root = mount(shell.view());
        Order o = session.store().find(77);
        assertTrue(hasText(root, "Order #77"));
        assertTrue(hasText(root, o.getCustomer()));
        assertTrue(hasText(root, o.getEmail()));
        assertTrue(hasText(root, o.getTotalText()));
        for (LineItem l : o.getLines()) {
            assertTrue(hasText(root, l.getSummary()), l.getSummary());
        }
    }

    @Test
    void theCustomersScreenListsTwelveWithAvatars() {
        shell.show(ShellController.Screen.CUSTOMERS);
        JXNativeNode root = mount(shell.view());
        assertEquals(12.0, ((Number) all(root, "list").get(0).getProperty("itemCount")).doubleValue());
        List<JXNativeNode> images = all(root, "image");
        assertTrue(images.size() >= 13, "twelve rows and the big avatar: " + images.size());
        for (JXNativeNode image : images) {
            assertTrue(String.valueOf(image.getProperty("src")).startsWith("classpath:orders/avatars/avatar"), String.valueOf(image.getProperty("src")));
            assertTrue(image.getWidth() > 0 && image.getHeight() > 0, "an avatar has a size");
        }
        assertTrue(hasText(root, "Aurelia Voss"));
        assertTrue(hasText(root, "Latest orders"));
        assertTrue(hasText(root, "Average order"));
    }

    @Test
    void everyBundledPictureLoads() throws Exception {
        for (int i = 1; i <= CustomerSummary.AVATARS; i++) {
            JXImage image = JXImages.load("classpath:orders/avatars/avatar" + i + ".png");
            assertNotNull(image, "avatar" + i);
            assertEquals(96, image.getWidth());
            assertEquals(96, image.getHeight());
        }
        for (int size : new int[] {32, 64, 128}) {
            assertEquals(size, JXImages.load("classpath:orders/app-icon-" + size + ".png").getWidth());
        }
    }

    @Test
    void theDashboardShowsFourFiguresAndTwoBarVisuals() {
        shell.show(ShellController.Screen.DASHBOARD);
        JXNativeNode root = mount(shell.view());
        assertTrue(hasText(root, "10,000"));
        assertTrue(hasText(root, "Orders by status"));
        assertTrue(hasText(root, "Revenue, last 6 months"));
        for (String month : new String[] {"Jun", "Jul", "Aug", "Sep", "Oct", "Nov"}) {
            assertTrue(hasText(root, month), month);
        }
        // the bars are boxes of the sizes the controller computed
        List<Integer> widths = new ArrayList<Integer>();
        List<Integer> heights = new ArrayList<Integer>();
        for (JXNativeNode stack : all(root, "stack")) {
            if (stack.getChildren().isEmpty() && stack.getHeight() == 18) {
                widths.add(stack.getWidth());
            } else if (stack.getChildren().isEmpty() && stack.getWidth() == 40) {
                heights.add(stack.getHeight());
            }
        }
        assertEquals(4, widths.size(), "a horizontal bar for each status");
        assertEquals(6, heights.size(), "a column for each month");
        int max = 0;
        for (int w : widths) {
            max = Math.max(max, w);
        }
        assertEquals(320, max, "the longest bar is the full length");
        int maxHeight = 0;
        for (int h : heights) {
            maxHeight = Math.max(maxHeight, h);
        }
        assertEquals(150, maxHeight);
    }

    @Test
    void theEditorIsBuiltClosedAndTakesNoRoom() {
        JXNativeNode closed = mount(shell.view());
        List<JXNativeNode> dialogs = all(closed, "dialog");
        assertEquals(3, dialogs.size(), "delete confirmation, the editor and About");
        for (JXNativeNode d : dialogs) {
            assertEquals(Boolean.FALSE, d.getProperty("showing"));
        }
        shell.editor().openNew();
        JXNativeNode open = mount(shell.view());
        assertEquals(Boolean.TRUE, all(open, "dialog").get(1).getProperty("showing"));
        assertEquals(1, all(open, "datepicker").size());
        assertEquals(1, all(open, "spinner").size());
        assertEquals(3, all(open, "select").size(), "two filters and the status");
    }

    // ---- the compiled views against the interpreter -------------------------------------------------------------------

    private static JXElement interpreted(String file, Object controller) throws Exception {
        return JxmlTemplate.readResource(OrdersScreensTest.class.getClassLoader(), "orders/" + file + ".jxml").build(controller);
    }

    @Test
    void compiledViewsBuildTheSameTreesAsTheInterpreter() throws Exception {
        shell.orders().select(5);
        shell.editor().openFor(session.store().find(5));
        shell.customers().setCurrent(2);
        assertNull(JxmlTrees.diff(interpreted("orders", shell.orders()), new com.jxparallel.examples.orders.gen.OrdersView().build(shell.orders())), "orders");
        assertNull(JxmlTrees.diff(interpreted("editor", shell.editor()), new com.jxparallel.examples.orders.gen.EditorView().build(shell.editor())), "editor");
        assertNull(JxmlTrees.diff(interpreted("customers", shell.customers()), new com.jxparallel.examples.orders.gen.CustomersView().build(shell.customers())), "customers");
        assertNull(JxmlTrees.diff(interpreted("dashboard", shell.dashboard()), new com.jxparallel.examples.orders.gen.DashboardView().build(shell.dashboard())), "dashboard");
        assertNull(JxmlTrees.diff(interpreted("shell", shell), new com.jxparallel.examples.orders.gen.ShellView().build(shell)), "shell");
        shell.setDark();
        shell.show(ShellController.Screen.DASHBOARD);
        assertNull(JxmlTrees.diff(interpreted("shell", shell), new com.jxparallel.examples.orders.gen.ShellView().build(shell)), "shell, dark");
        assertNull(JxmlTrees.diff(interpreted("dashboard", shell.dashboard()), new com.jxparallel.examples.orders.gen.DashboardView().build(shell.dashboard())), "dashboard, dark");
    }

    @Test
    void anEmptyFileStillMountsEveryScreen() {
        Session empty = new Session(new TestHost(), () -> TODAY);
        ShellController s = new ShellController(empty);
        for (ShellController.Screen screen : ShellController.Screen.values()) {
            s.show(screen);
            JXNativeNode root = mount(s.view());
            assertTrue(root.getWidth() > 0);
        }
        s.show(ShellController.Screen.CUSTOMERS);
        assertTrue(hasText(mount(s.view()), "No orders yet, so no customers."));
        s.show(ShellController.Screen.DASHBOARD);
        assertTrue(hasText(mount(s.view()), "No orders loaded."));
        s.show(ShellController.Screen.ORDERS);
        assertTrue(hasText(mount(s.view()), "0 orders"));
    }
}
