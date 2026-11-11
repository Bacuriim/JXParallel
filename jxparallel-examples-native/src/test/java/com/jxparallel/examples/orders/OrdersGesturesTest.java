package com.jxparallel.examples.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jxparallel.ui.native2d.JXNativeNode;

/**
 * What a user does in JX Orders, as the window's input calls (press, release, key, character, drop) on the mounted
 * native tree: search as you type, filters, selection, keyboard navigation, the editor, the sidebar, the menu, the
 * theme and a dropped file. A failure here is a screen that does not respond to a gesture.
 */
class OrdersGesturesTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    @TempDir
    File tmp;

    private TestHost host;
    private Session session;
    private ShellController shell;
    private OrdersUi ui;

    @BeforeEach
    void setUp() throws Exception {
        host = new TestHost();
        session = new Session(host, () -> TODAY);
        shell = new ShellController(session);
        shell.loadSample(600).get(30, TimeUnit.SECONDS);
        ui = new OrdersUi(shell, 1200, 760);
    }

    @AfterEach
    void tearDown() {
        host.ui.shutdownNow();
    }

    private List<String> column(int index) {
        List<String> out = new ArrayList<String>();
        for (JXNativeNode row : ui.find("table").getChildren()) {
            out.add(String.valueOf(row.getChildren().get(index).getProperty("value")));
        }
        return out;
    }

    private JXNativeNode row(int visibleIndex) {
        return ui.find("table").getChildren().get(visibleIndex);
    }

    private void clickRow(int visibleIndex, int modifiers) {
        int[] c = OrdersUi.center(row(visibleIndex).getChildren().get(1));
        ui.click(c[0], c[1], modifiers);
    }

    private JXNativeNode button(String text) {
        for (JXNativeNode b : ui.findAll("button")) {
            for (JXNativeNode child : b.getChildren()) {
                if (text.equals(child.getProperty("value"))) {
                    return b;
                }
            }
            if (text.equals(b.getProperty("text")) || text.equals(b.getProperty("label"))) {
                return b;
            }
        }
        throw new AssertionError("no button '" + text + "'");
    }

    // ---- search and filters -------------------------------------------------------------------------------------

    @Test
    void theTableIsFilteredAsYouType() {
        assertEquals(600, shell.orders().getVisible().size());
        ui.click(ui.find("input", 0));
        ui.type("marlowe");
        assertEquals("marlowe", shell.orders().getQuery());
        int n = shell.orders().getVisible().size();
        assertTrue(n > 20 && n < 200, "n = " + n);
        assertTrue(ui.hasText(OrdersController.count(n) + " of 600 orders"), ui.texts("#text").toString());
        for (String customer : column(1)) {
            assertEquals("Beto Marlowe", customer);
        }
        assertEquals(Math.min(n, 40), ui.find("table").getChildren().size(), "only the rows in view are built");
        for (int i = 0; i < "marlowe".length(); i++) {
            ui.key(OrdersUi.BACKSPACE);
        }
        assertEquals("", shell.orders().getQuery());
        assertEquals(600, shell.orders().getVisible().size());
        assertTrue(ui.hasText("600 orders"));
    }

    @Test
    void searchNarrowsByAnyColumnAndShowsAnEmptyTable() {
        ui.click(ui.find("input", 0));
        ui.type("shipped 2026");
        for (Order o : shell.orders().getVisible()) {
            assertEquals(Status.SHIPPED, o.getStatus());
            assertTrue(o.getDateText().startsWith("2026"));
        }
        ui.type(" nobody-has-this");
        assertEquals(0, shell.orders().getVisible().size());
        assertEquals(0, ui.find("table").getChildren().size());
        assertTrue(ui.hasText("0 of 600 orders"));
    }

    @Test
    void theStatusFilterListOpensAndAChoiceFiltersTheTable() {
        JXNativeNode status = ui.find("select", 0);
        ui.click(status);
        assertTrue(ui.root.capturesKeys(), "a press opens the list (the node keeps the open state: no showing binding)");
        ui.key(OrdersUi.DOWN); // New
        ui.key(OrdersUi.DOWN); // Paid
        ui.key(OrdersUi.ENTER);
        assertFalse(ui.root.capturesKeys(), "Enter chooses and closes");
        assertEquals("Paid", shell.orders().getStatusFilter());
        assertTrue(shell.orders().getVisible().size() > 0);
        for (String s : column(3)) {
            assertEquals("Paid", s);
        }
    }

    @Test
    void aClosedComboBoxChangesItsValueWithTheArrowKeys() {
        ui.click(ui.find("select", 0));
        ui.key(OrdersUi.ESCAPE); // close the list; the control keeps the focus
        assertFalse(ui.root.capturesKeys());
        ui.key(OrdersUi.DOWN);
        assertEquals("New", shell.orders().getStatusFilter());
        for (String s : column(3)) {
            assertEquals("New", s);
        }
        ui.key(OrdersUi.DOWN);
        assertEquals("Paid", shell.orders().getStatusFilter());
    }

    // ---- sorting by the column headers ------------------------------------------------------------------------------

    /** A press and release on the header of column {@code index}: the columns share the table's width as 70:220:110:100:120. */
    private void clickHeader(int index) {
        JXNativeNode table = ui.find("table");
        double[] weights = {70, 220, 110, 100, 120};
        double sum = 0;
        for (double w : weights) {
            sum += w;
        }
        double before = 0;
        for (int i = 0; i < index; i++) {
            before += weights[i];
        }
        double usable = table.getWidth() - 2;
        int x = table.getX() + 1 + (int) (usable * (before + weights[index] / 2) / sum);
        ui.click(x, table.getY() + 8);
    }

    @Test
    void aPressOnAColumnHeaderSortsAndCyclesAscendingDescendingUnsorted() {
        assertEquals(0.0, ((Number) ui.find("table").getProperty("sortColumn")).doubleValue(), "sorted by id to begin with");
        clickHeader(0);
        assertEquals(SortKey.ID_DESC, shell.orders().getSortKey());
        assertEquals("600", column(0).get(0), "highest id first now");
        clickHeader(0);
        assertEquals(-1, shell.orders().getSortColumn(), "the third press: unsorted");
        assertEquals("1", column(0).get(0), "unsorted lists the orders by id");
        assertEquals(-1.0, ((Number) ui.find("table").getProperty("sortColumn")).doubleValue(), "no header draws an arrow");

        clickHeader(1);
        assertEquals(SortKey.CUSTOMER, shell.orders().getSortKey());
        List<String> names = column(1);
        List<String> sorted = new ArrayList<String>(names);
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        assertEquals(sorted, names);
        clickHeader(1);
        assertEquals(SortKey.CUSTOMER_DESC, shell.orders().getSortKey());
        names = column(1);
        sorted = new ArrayList<String>(names);
        Collections.sort(sorted, Collections.reverseOrder(String.CASE_INSENSITIVE_ORDER));
        assertEquals(sorted, names);

        clickHeader(4); // another column starts ascending: the lowest total first
        assertEquals(SortKey.TOTAL_LOW, shell.orders().getSortKey());
        clickHeader(4);
        assertEquals(SortKey.TOTAL_HIGH, shell.orders().getSortKey());
        List<Order> v = shell.orders().getVisible();
        for (int i = 1; i < v.size(); i++) {
            assertTrue(v.get(i - 1).getTotalCents() >= v.get(i).getTotalCents());
        }
        clickHeader(2);
        assertEquals(SortKey.DATE_OLD, shell.orders().getSortKey());
        clickHeader(3);
        assertEquals(SortKey.STATUS_ASC, shell.orders().getSortKey());
    }

    @Test
    void sortingKeepsTheSelectionAndTheSearch() {
        ui.click(ui.find("input", 0));
        ui.type("marlowe");
        clickRow(2, 0);
        int id = shell.orders().getOne().getId();
        clickHeader(4);
        assertEquals(id, shell.orders().getOne().getId(), "a header press does not change the selection");
        assertEquals("marlowe", shell.orders().getQuery());
        for (String customer : column(1)) {
            assertEquals("Beto Marlowe", customer);
        }
    }

    // ---- selection ----------------------------------------------------------------------------------------------------

    @Test
    void clickingARowShowsItsDetailsAndCtrlAndShiftClickSelectSeveral() {
        assertTrue(ui.hasText("No order selected"));
        clickRow(2, 0);
        assertEquals(2, shell.orders().getCurrent());
        assertTrue(ui.hasText("Order #3"), ui.texts("#text").toString());
        assertTrue(ui.hasText(shell.orders().getVisible().get(2).getCustomer()));

        clickRow(5, OrdersUi.SHORTCUT);
        assertEquals(2, shell.orders().getSelection().size());
        assertTrue(ui.hasText("2 orders selected"));
        assertEquals(5, shell.orders().getCurrent());

        clickRow(8, OrdersUi.SHIFT);
        assertEquals(4, shell.orders().getSelection().size(), "Shift+click selects the range from the row clicked last (5) to 8");
        assertEquals("[5.0, 6.0, 7.0, 8.0]", Arrays.toString(shell.orders().getPicked()));
        assertTrue(ui.hasText("4 orders selected"));

        clickRow(1, 0);
        assertEquals(1, shell.orders().getSelection().size(), "a plain click selects only that row");
        assertTrue(ui.hasText("Order #2"));
    }

    @Test
    void theArrowKeysMoveTheSelectionAndTheDetailsFollow() {
        clickRow(0, 0);
        assertTrue(ui.hasText("Order #1"));
        ui.key(OrdersUi.DOWN);
        ui.key(OrdersUi.DOWN);
        assertEquals(2, shell.orders().getCurrent());
        assertTrue(ui.hasText("Order #3"));
        ui.key(OrdersUi.UP);
        assertTrue(ui.hasText("Order #2"));
        ui.key(OrdersUi.END);
        assertEquals(599, shell.orders().getCurrent(), "End goes to the last of all 600 rows, not the last built");
        assertTrue(ui.hasText("Order #600"));
        assertTrue(column(0).contains("600"), "the window moved to show it");
        ui.key(OrdersUi.HOME);
        assertEquals(0, shell.orders().getCurrent());
        assertEquals(0, shell.orders().getTop());
        ui.key(OrdersUi.DOWN, OrdersUi.SHIFT);
        ui.key(OrdersUi.DOWN, OrdersUi.SHIFT);
        assertEquals(3, shell.orders().getSelection().size(), "Shift+Down extends the selection");
    }

    @Test
    void theCheckBoxSwitchesToSingleSelection() {
        assertEquals("multiple", ui.find("table").getProperty("selectionMode"));
        ui.click(ui.find("checkbox"));
        assertFalse(shell.orders().getMultiple());
        assertEquals("single", ui.find("table").getProperty("selectionMode"));
        clickRow(1, 0);
        clickRow(3, OrdersUi.SHORTCUT);
        assertEquals(1, shell.orders().getSelection().size(), "single selection: Ctrl+click replaces");
        assertEquals(3, shell.orders().getCurrent());
        ui.click(ui.find("checkbox"));
        assertTrue(shell.orders().getMultiple());
    }

    @Test
    void enterOnARowOpensTheEditorAndEscapeClosesIt() {
        clickRow(4, 0);
        ui.key(OrdersUi.ENTER);
        assertTrue(shell.editor().getOpen());
        assertTrue(editorShowing());
        assertTrue(ui.hasText("Edit order #5"));
        ui.key(OrdersUi.ESCAPE);
        assertFalse(shell.editor().getOpen());
        assertFalse(editorShowing(), "the dialog is closed");
    }

    /** The editor is the second dialog of the tree (the orders screen's delete confirmation comes first). */
    private boolean editorShowing() {
        return Boolean.TRUE.equals(ui.findAll("dialog").get(1).getProperty("showing"));
    }

    @Test
    void deleteConfirmsThroughADialog() {
        clickRow(0, 0);
        clickRow(1, OrdersUi.SHORTCUT);
        ui.click(button("Delete"));
        assertTrue(shell.orders().getConfirmDelete());
        assertTrue(ui.hasText("2 orders will be removed from this file."), ui.texts("#text").toString());
        ui.key(OrdersUi.ESCAPE);
        assertFalse(shell.orders().getConfirmDelete(), "Escape keeps the orders");
        assertEquals(600, session.store().size());
        ui.click(button("Delete"));
        ui.key(OrdersUi.ENTER); // the confirm button is the dialog's default
        assertEquals(598, session.store().size());
        assertTrue(ui.hasText("598 orders"));
    }

    // ---- the editor -------------------------------------------------------------------------------------------------

    @Test
    void ctrlNOpensTheEditorAndSaveWithEnterShowsTheMessages() {
        ui.key('N', OrdersUi.CONTROL);
        assertTrue(shell.editor().getOpen());
        assertTrue(ui.hasText("New order"));
        ui.click(ui.find("input", 0)); // the customer field is the first input inside the dialog
        assertEquals("", shell.editor().getCustomerError());
        ui.key(OrdersUi.ENTER); // Enter in a field presses the default button, Save
        assertTrue(shell.editor().getOpen(), "invalid: still open");
        assertTrue(ui.hasText("Customer is required"), ui.texts("#text").toString());
        assertTrue(ui.hasText("E-mail is required"));
        assertTrue(ui.hasText("Describe the item"));
        assertEquals(600, session.store().size());
    }

    @Test
    void theEditorFillsInSavesWithCtrlSAndSelectsTheNewOrder() {
        ui.key('N', OrdersUi.CONTROL);
        List<JXNativeNode> inputs = ui.findAll("input");
        // the editor's inputs are the last ones of the tree: customer, e-mail, description, price
        int n = inputs.size();
        ui.click(inputs.get(n - 4));
        ui.type("Mary Jackson");
        ui.click(ui.findAll("input").get(n - 3));
        ui.type("mary@nasa");
        assertTrue(ui.hasText("That is not an e-mail address"), "a field shows its message as soon as it was edited");
        ui.type(".gov");
        assertFalse(ui.hasText("That is not an e-mail address"));
        ui.click(ui.findAll("input").get(n - 2));
        ui.type("Wind tunnel model");
        ui.click(ui.findAll("input").get(n - 1));
        ui.type("12.50");
        assertEquals("$12.50", shell.editor().getTotalText());
        assertTrue(ui.hasText("$12.50"));

        JXNativeNode spinner = ui.find("spinner");
        ui.click(spinner);
        ui.key(OrdersUi.UP);
        ui.key(OrdersUi.UP);
        assertEquals(3, shell.editor().getDraft().getLines().get(0).getQuantity());
        assertEquals("$37.50", shell.editor().getTotalText(), "the total follows the quantity");

        ui.key('S', OrdersUi.CONTROL);
        assertFalse(shell.editor().getOpen());
        assertEquals(601, session.store().size());
        Order saved = session.store().find(601);
        assertEquals("Mary Jackson", saved.getCustomer());
        assertEquals(3750, saved.getTotalCents());
        assertTrue(ui.hasText("Order #601"), "selected, and shown in the details panel");
        assertTrue(ui.hasText("Saved order #601"));
    }

    @Test
    void addAndRemoveItemsInTheEditor() {
        ui.key('N', OrdersUi.CONTROL);
        assertEquals(1, shell.editor().getDraft().getLines().size());
        ui.click(button("Add item"));
        ui.click(button("Add item"));
        assertEquals(3, shell.editor().getDraft().getLines().size());
        assertEquals(3, ui.findAll("spinner").size(), "one Spinner for each line");
        OrderDraft.DraftLine keep = shell.editor().getDraft().getLines().get(2);
        // one icon-only Remove button for each line
        List<JXNativeNode> removers = new ArrayList<JXNativeNode>();
        for (JXNativeNode b : ui.findAll("button")) {
            if ("Remove this item".equals(b.getProperty("tooltip"))) {
                assertEquals("", String.valueOf(b.getProperty("label") == null ? "" : b.getProperty("label")), "an icon, no text");
                removers.add(b);
            }
        }
        assertEquals(3, removers.size());
        ui.click(removers.get(0));
        assertEquals(2, shell.editor().getDraft().getLines().size());
        assertEquals(keep, shell.editor().getDraft().getLines().get(1), "the other lines are kept as they were");
        assertEquals(2, ui.findAll("spinner").size());
    }

    @Test
    void theDatePickerAndStatusListAreInTheEditor() {
        ui.key('N', OrdersUi.CONTROL);
        JXNativeNode date = ui.find("datepicker");
        assertEquals(TODAY.toString(), date.getProperty("value"));
        ui.click(date);
        // no showing binding: the picker keeps its own open state, and Escape closes the calendar before the dialog
        ui.key(OrdersUi.ESCAPE);
        assertTrue(shell.editor().getOpen(), "Escape closed the calendar only, not the dialog");
        ui.key(OrdersUi.DOWN); // closed: a day back
        assertEquals(TODAY.minusDays(1).toString(), shell.editor().getDraft().getDateText());
        JXNativeNode status = ui.findAll("select").get(1); // after the status filter of the orders screen
        ui.click(status);
        assertTrue(ui.root.capturesKeys(), "the list opens without a showing binding");
        ui.key(OrdersUi.DOWN);
        ui.key(OrdersUi.ENTER);
        assertEquals("Paid", shell.editor().getDraft().getStatusLabel());
    }

    @Test
    void tabMovesThroughTheEditorOnly() {
        ui.key('N', OrdersUi.CONTROL);
        ui.focused = null;
        List<String> stops = new ArrayList<String>();
        for (int i = 0; i < 12; i++) {
            ui.key(OrdersUi.TAB);
            stops.add(ui.focused.getType());
            assertTrue(ui.focused.getX() > 250, "inside the dialog: " + ui.focused.getType() + " at " + ui.focused.getX());
        }
        assertEquals("input", stops.get(0));
        assertEquals("input", stops.get(1));
        assertTrue(stops.contains("datepicker") && stops.contains("select") && stops.contains("spinner"), stops.toString());
    }

    // ---- keyboard order, sidebar, menu, theme -------------------------------------------------------------------------

    @Test
    void tabOrderFollowsTheFile() {
        ui.focused = null;
        List<String> stops = new ArrayList<String>();
        for (int i = 0; i < 8; i++) {
            ui.key(OrdersUi.TAB);
            stops.add(ui.focused.getType());
        }
        // the three sidebar buttons, the search box, the status filter, the check box, New order (Edit and Delete are
        // disabled, so they are skipped), and the table
        assertEquals(Arrays.asList("button", "button", "button", "input", "select", "checkbox", "button", "table"), stops);
        ui.key(OrdersUi.TAB, OrdersUi.SHIFT);
        assertEquals("button", ui.focused.getType());
        assertEquals(508, ui.focused.getX() < 600 ? 508 : -1, "Shift+Tab went back to New order");
    }

    @Test
    void figureCardsRiseWhileThePointerIsOverThem() {
        shell.show(ShellController.Screen.CUSTOMERS);
        ui.refresh();
        CustomersController c = shell.customers();
        assertEquals(0.0, c.getFigures().get(1).getRise());
        int[] over = OrdersUi.center(ui.findText("#text", "Spent")); // the title label inside the card
        ui.root.hoverPointer(over[0], over[1]);
        ui.refresh();
        assertEquals(-4.0, c.getFigures().get(1).getRise(), "the pointer is over the Spent card");
        assertEquals(0.0, c.getFigures().get(0).getRise());
        int[] next = OrdersUi.center(ui.findText("#text", "Average order"));
        ui.root.hoverPointer(next[0], next[1]);
        ui.refresh();
        assertEquals(0.0, c.getFigures().get(1).getRise(), "left the Spent card");
        assertEquals(-4.0, c.getFigures().get(2).getRise());
        ui.click(next[0], next[1]);
        assertEquals(-4.0, c.getFigures().get(2).getRise(), "a click changes nothing");
        ui.root.hoverPointer(-1, -1); // the pointer left the window
        ui.refresh();
        assertEquals(0.0, c.getFigures().get(2).getRise());
    }

    @Test
    void theSidebarSwitchesScreens() {
        List<JXNativeNode> nav = ui.findAll("button");
        // a click on the picture of a button counts as a click on the button (the Icon has no mouseTransparent in the JXML:
        // the button takes the click the Icon was hit by)
        ui.click(nav.get(2).getChildren().get(0));
        assertEquals(ShellController.Screen.CUSTOMERS, shell.getScreen());
        ui.click(ui.findAll("button").get(1));
        assertEquals(ShellController.Screen.ORDERS, shell.getScreen());
        ui.click(ui.findAll("button").get(2)); // Customers
        assertEquals(ShellController.Screen.CUSTOMERS, shell.getScreen());
        assertEquals(0, ui.findAll("table").size());
        JXNativeNode list = ui.find("list");
        assertEquals(12.0, ((Number) list.getProperty("itemCount")).doubleValue());
        assertTrue(ui.findAll("image").size() >= 12, "an avatar for each row and the big one");
        // the list takes the arrows: select the 3rd customer
        ui.click(ui.find("list").getChildren().get(2));
        assertEquals(2, shell.customers().getCurrent());
        assertTrue(ui.hasText(shell.customers().getSelected().getName()));
        ui.key(OrdersUi.DOWN);
        assertEquals(3, shell.customers().getCurrent());
        // the figure cards rise under the pointer, not when clicked (see figureCardsRiseWhileThePointerIsOverThem)
        List<JXNativeNode> before = ui.findAll("stack");
        assertFalse(before.isEmpty());
        ui.click(ui.findAll("button").get(0)); // Dashboard
        assertEquals(ShellController.Screen.DASHBOARD, shell.getScreen());
        assertTrue(ui.hasText("Dashboard"));
        assertTrue(ui.hasText("Orders by status"));
        ui.click(ui.findAll("button").get(1)); // Orders
        assertEquals(1, ui.findAll("table").size());
    }

    /** A point in the right half of the Customer cell of the first row, where there is no text. */
    private int[] emptySpotOfARow() {
        JXNativeNode cell = row(1).getChildren().get(1);
        return new int[] {cell.getX() + cell.getWidth() - 10, cell.getY() + cell.getHeight() / 2};
    }

    @Test
    void theMenuBarOpensWithTheMouseAndRunsAnItemWithTheKeyboard() {
        JXNativeNode view = ui.find("menu", 1);
        ui.click(view);
        int[] bounds = ui.find("menubar").getPopupBounds();
        assertTrue(bounds != null && bounds.length >= 4 && bounds[2] > 0, "the View menu is open");
        ui.key(OrdersUi.DOWN); // Light theme
        ui.key(OrdersUi.DOWN); // Dark theme
        ui.key(OrdersUi.ENTER);
        assertEquals("dark", shell.getThemeName());
        assertEquals("dark", ui.root.getProperty("theme"));
        assertEquals(0xFF23252B, ui.pixel(5, 600), "the sidebar is painted with the dark palette");
        int[] spot = emptySpotOfARow();
        assertTrue((ui.pixel(spot[0], spot[1]) & 0xFF) < 0x60, "the orders table is dark too (the classic controls follow the theme)");
        ui.click(ui.find("menu", 1));
        ui.key(OrdersUi.DOWN);
        ui.key(OrdersUi.ENTER);
        assertEquals("light", shell.getThemeName());
        assertEquals(0xFFE8EEF8, ui.pixel(5, 600));
        assertTrue((ui.pixel(spot[0], spot[1]) & 0xFF) > 0xC0, "and light again");
    }

    @Test
    void f10AndTheAcceleratorsWorkWithoutTheMouse() {
        ui.key('N', OrdersUi.CONTROL);
        assertTrue(shell.editor().getOpen());
        ui.key(OrdersUi.ESCAPE);
        ui.key(290); // GLFW_KEY_F1: Help, About
        assertTrue(shell.getAboutOpen());
        assertTrue(ui.hasText("About JX Orders"));
        ui.key(OrdersUi.ENTER);
        assertFalse(shell.getAboutOpen());
    }

    // ---- drag and drop --------------------------------------------------------------------------------------------------

    @Test
    void aCsvFileDroppedAnywhereOnTheWindowIsImported() throws Exception {
        File csv = new File(tmp, "three.csv");
        Files.write(csv.toPath(), Arrays.asList(
                "id,customer,email,date,status,description,quantity,unit_price",
                "1,Aurelia Voss,aurelia@example.com,2026-01-05,New,Cable,2,3.50",
                "2,Greta Quenby,greta@example.com,2026-01-06,Paid,Cable,1,1.00",
                "3,Beto Marlowe,beto@example.com,2026-02-02,Shipped,Cable,1,9.99"), StandardCharsets.UTF_8);
        assertTrue(ui.drop(600, 400, Collections.singletonList(csv.getPath())), "an element at or above the pointer takes the drop");
        long deadline = System.currentTimeMillis() + 15000;
        while (session.store().size() != 3 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        host.onUi(() -> { }); // the UI thread has run what the worker handed over
        ui.refresh();
        assertEquals(3, session.store().size());
        assertEquals(Arrays.asList("1", "2", "3"), column(0));
        assertTrue(ui.hasText("Imported 3 orders from three.csv"));
        assertNotEquals(600, shell.orders().getVisible().size());
    }

    @Test
    void aDropWithTheEditorOpenImportsNothing() throws Exception {
        ui.key('N', OrdersUi.CONTROL);
        File csv = new File(tmp, "x.csv");
        Files.write(csv.toPath(), "1,Aurelia Voss,aurelia@example.com,2026-01-05,New,Cable,2,3.50\n".getBytes(StandardCharsets.UTF_8));
        // only what is in the dialog can take a drop (docs/jxforge.md): over the scrim or the form, the screen's onDrop
        // behind it is never reached, so nothing is imported and nothing is said
        String status = shell.getStatus();
        ui.drop(20, 300, Collections.singletonList(csv.getPath()));
        ui.drop(600, 400, Collections.singletonList(csv.getPath()));
        Thread.sleep(200);
        host.onUi(() -> { });
        assertEquals(600, session.store().size());
        assertEquals(status, shell.getStatus());
    }
}
