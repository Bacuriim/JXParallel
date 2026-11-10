package com.jxparallel.examples.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The controllers of JX Orders with a real worker pool and a thread standing for the UI thread ({@link TestHost}):
 * where the work runs, progress, cancel, files, and the state the JXML bindings read.
 */
class OrdersControllersTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    @TempDir
    File tmp;

    private TestHost host;
    private Session session;
    private ShellController shell;

    @BeforeEach
    void setUp() {
        host = new TestHost();
        session = new Session(host, () -> TODAY);
        shell = new ShellController(session);
    }

    @AfterEach
    void tearDown() {
        host.ui.shutdownNow();
    }

    private static void await(CompletableFuture<?> f) throws Exception {
        f.get(60, TimeUnit.SECONDS);
    }

    private void load(int count) throws Exception {
        await(shell.loadSample(count));
    }

    // ---- work on the pool -----------------------------------------------------------------------------------------

    @Test
    void loadingRunsOnAWorkerAndAppliesTheResultOnTheUiThread() throws Exception {
        String testThread = Thread.currentThread().getName();
        host.onUi(() -> { });
        CompletableFuture<Void> f = shell.loadSample(4000);
        assertTrue(shell.getBusy(), "busy as soon as the work is submitted");
        await(f);
        assertFalse(shell.getBusy());
        assertEquals(4000, session.store().size());
        assertEquals(4000, shell.orders().getVisible().size());
        assertTrue(shell.getStatus().startsWith("Generated 4,000 orders in "), shell.getStatus());
        assertFalse(host.workerThreads.isEmpty());
        assertFalse(host.workerThreads.contains(testThread), "not on the calling thread");
        assertFalse(host.workerThreads.contains("test-ui"), "never on the UI thread");
        assertTrue(host.renders.get() >= 3, "a render when it starts, for the progress, and when it is done");
    }

    @Test
    void progressIsReportedWhileWorking() throws Exception {
        List<Double> seen = Collections.synchronizedList(new ArrayList<Double>());
        // the same job ShellController runs, watched through the renders: progress is the last value the UI thread saw
        await(shell.loadSample(5000));
        host.onUi(() -> seen.add(shell.getProgress()));
        assertEquals(0.0, seen.get(0), "reset when the work is done");
        assertTrue(host.renders.get() >= 12, "ten chunks of 500 report, plus the start and the end: " + host.renders.get());
    }

    @Test
    void cancelStopsTheWorkAndKeepsTheOrders() throws Exception {
        load(500);
        int before = session.store().size();
        CompletableFuture<Void> f = shell.loadSample(50_000_000);
        assertTrue(shell.getBusy());
        shell.cancelWork();
        await(f);
        assertFalse(shell.getBusy());
        assertEquals("Generating 50,000,000 orders cancelled", shell.getStatus());
        assertEquals(before, session.store().size(), "the cancelled job changed nothing");
    }

    @Test
    void onlyOneJobRunsAtATime() throws Exception {
        CompletableFuture<Void> big = shell.loadSample(50_000_000);
        CompletableFuture<Void> second = shell.loadSample(10);
        await(second);
        assertTrue(shell.getStatus().startsWith("Busy with Generating 50,000,000 orders"), shell.getStatus());
        shell.cancelWork();
        await(big);
        assertEquals(0, session.store().size());
    }

    @Test
    void exportAndImportRoundTripThroughAFile() throws Exception {
        load(1200);
        List<Order> original = session.store().getOrders();
        File file = new File(tmp, "orders.csv");
        await(shell.exportTo(file));
        assertEquals("Exported 1,200 orders to orders.csv", shell.getStatus());
        assertTrue(file.length() > 50_000);
        assertTrue(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8).get(0).startsWith("id,customer,email,date,status"));

        Host other = new TestHost();
        Session s2 = new Session(other, () -> TODAY);
        ShellController shell2 = new ShellController(s2);
        await(shell2.importFile(file));
        assertEquals(1200, s2.store().size());
        for (int i = 0; i < original.size(); i++) {
            assertTrue(original.get(i).sameAs(s2.store().getOrders().get(i)), "order " + (i + 1));
        }
        assertEquals("Imported 1,200 orders from orders.csv", shell2.getStatus());
        assertEquals(1200, shell2.orders().getVisible().size());
        ((TestHost) other).ui.shutdownNow();
    }

    @Test
    void importSkipsBadRowsAndSaysSo() throws Exception {
        File file = new File(tmp, "mixed.csv");
        Files.write(file.toPath(), Arrays.asList(
                "id,customer,email,date,status,description,quantity,unit_price",
                "1,Ada Lovelace,ada@example.com,2026-01-05,New,Cable,2,3.50",
                "2,Grace Hopper,grace@example.com,not-a-date,Paid,Cable,1,1.00",
                "3,Alan Turing,alan@example.com,2026-02-02,Shipped,Cable,1,9.99"), StandardCharsets.UTF_8);
        await(shell.importFile(file));
        assertEquals(2, session.store().size());
        assertTrue(shell.getStatus().contains("Imported 2 orders from mixed.csv, 1 rows skipped (row 3: "), shell.getStatus());
        assertEquals(1, shell.getLastImportErrors().size());
    }

    @Test
    void importOfAMissingFileFailsWithoutHangingTheUi() throws Exception {
        load(100);
        await(shell.importFile(new File(tmp, "nope.csv")));
        assertFalse(shell.getBusy());
        assertTrue(shell.getStatus().startsWith("Reading nope.csv failed"), shell.getStatus());
        assertEquals(100, session.store().size(), "the orders on screen are kept");
    }

    @Test
    void anEmptyFileImportsNothingAndKeepsTheOrders() throws Exception {
        load(100);
        File file = new File(tmp, "empty.csv");
        Files.write(file.toPath(), new byte[0]);
        await(shell.importFile(file));
        assertEquals("empty.csv: no orders found", shell.getStatus());
        assertEquals(100, session.store().size());
    }

    @Test
    void aCancelledExportLeavesNoFile() throws Exception {
        load(3000);
        File file = new File(tmp, "cancelled.csv");
        CompletableFuture<Void> f = shell.exportTo(file);
        shell.cancelWork();
        await(f);
        assertFalse(file.exists());
        assertEquals("Writing cancelled.csv cancelled", shell.getStatus());
    }

    @Test
    void theFileDialogsRunOnAWorkerAndTheirFilesAreUsed() throws Exception {
        load(300);
        File out = new File(tmp, "export.csv");
        host.csvToSave = out;
        await(shell.exportCsv());
        assertTrue(out.exists());
        host.csvToOpen = out;
        await(shell.openCsv());
        assertEquals("Imported 300 orders from export.csv", shell.getStatus());
        assertFalse(host.dialogThreads.contains("test-ui"), "a blocking dialog never runs on the UI thread");
        assertFalse(host.dialogThreads.contains(Thread.currentThread().getName()));

        host.csvToOpen = null;
        host.csvToSave = null;
        String status = shell.getStatus();
        await(shell.openCsv());
        await(shell.exportCsv());
        assertEquals(status, shell.getStatus(), "cancelling a dialog does nothing");
        assertFalse(shell.getBusy());
    }

    @Test
    void droppedFilesImportTheFirstCsv() throws Exception {
        load(200);
        File csv = new File(tmp, "dropped.CSV");
        await(shell.exportTo(csv));
        await(shell.dropFiles(Arrays.asList(new File(tmp, "notes.txt").getPath(), csv.getPath())));
        assertEquals("Imported 200 orders from dropped.CSV", shell.getStatus());
        await(shell.dropFiles(Collections.singletonList("photo.png")));
        assertEquals("Drop a .csv file to import it", shell.getStatus());
    }

    @Test
    void exitCancelsTheWorkAndClosesTheWindow() throws Exception {
        CompletableFuture<Void> f = shell.loadSample(50_000_000);
        shell.exit();
        await(f);
        assertTrue(host.exited);
    }

    // ---- the orders screen -----------------------------------------------------------------------------------------

    @Test
    void searchFilterAndSortChangeWhatTheTableShows() throws Exception {
        load(3000);
        OrdersController o = shell.orders();
        assertEquals("3,000 orders", o.getCountText());
        o.setQuery("turing");
        assertTrue(o.getVisible().size() > 100 && o.getVisible().size() < 1000);
        for (Order x : o.getVisible()) {
            assertEquals("Alan Turing", x.getCustomer());
        }
        assertEquals(OrdersController.count(o.getVisible().size()) + " of 3,000 orders", o.getCountText());
        o.setStatusFilter("Cancelled");
        for (Order x : o.getVisible()) {
            assertEquals(Status.CANCELLED, x.getStatus());
        }
        o.setStatusFilter(OrderQuery.ALL_STATUSES);
        o.setSortLabel(SortKey.TOTAL_HIGH.label());
        List<Order> v = o.getVisible();
        for (int i = 1; i < v.size(); i++) {
            assertTrue(v.get(i - 1).getTotalCents() >= v.get(i).getTotalCents());
        }
        o.setQuery("");
        assertEquals(3000, o.getVisible().size());
        o.setTop(5000);
        assertEquals(2999, o.getTop(), "the first row in view stays inside the list");
        o.setQuery("zzzz-no-such-customer");
        assertEquals(0, o.getVisible().size());
        assertEquals(0, o.getTop());
    }

    @Test
    void singleSelectionFollowsTheTablesIndices() throws Exception {
        load(500);
        OrdersController o = shell.orders();
        o.setMultiple(false);
        assertEquals("single", o.getSelectionMode());
        assertTrue(o.getNoSelection());
        assertEquals(-1, o.getCurrent());
        o.setCurrent(10);
        assertEquals(10, o.getCurrent());
        assertTrue(o.getOneSelected());
        assertEquals(11, o.getOne().getId());
        assertEquals("Order #11", o.getDetailTitle());
        o.setCurrent(3);
        assertEquals(1, o.getSelection().size(), "a new click replaces the selection");
        assertEquals(4, o.getOne().getId());
        o.setCurrent(-1);
        assertTrue(o.getNoSelection());
        assertEquals("No order selected", o.getDetailTitle());
    }

    @Test
    void severalRowsCanBeSelectedAndTheDetailsSumThem() throws Exception {
        load(500);
        OrdersController o = shell.orders();
        assertEquals("multiple", o.getSelectionMode());
        o.setPicked(new double[] {2, 4, 6});
        o.setCurrent(6);
        assertTrue(o.getSeveralSelected());
        assertEquals("3 orders selected", o.getDetailTitle());
        assertArrayEqualsSorted(new double[] {2, 4, 6}, o.getPicked());
        assertEquals(6, o.getCurrent());
        long sum = 0;
        for (Order x : o.getSelection()) {
            sum += x.getTotalCents();
        }
        assertEquals(Money.format(sum), o.getSelectedTotalText());
        o.setPicked(new double[] {4});
        assertTrue(o.getOneSelected());
        assertEquals(4, o.getCurrent(), "the last row clicked left the selection: the remaining one is current");
        // switching to single mode keeps one row
        o.setPicked(new double[] {1, 2, 3});
        o.setCurrent(2);
        o.setMultiple(false);
        assertEquals(1, o.getSelection().size());
        assertEquals(3, o.getOne().getId());
    }

    private static void assertArrayEqualsSorted(double[] expected, double[] actual) {
        assertEquals(Arrays.toString(expected), Arrays.toString(actual));
    }

    @Test
    void theSelectionSurvivesASearchForTheRowsThatAreStillListed() throws Exception {
        load(1000);
        OrdersController o = shell.orders();
        Order a = session.store().getOrders().get(0);
        Order b = session.store().getOrders().get(1);
        o.select(a.getId());
        o.setPicked(new double[] {o.getCurrent(), indexOf(o, b)});
        assertEquals(2, o.getSelection().size());
        o.setQuery(a.getCustomer());
        List<Integer> kept = new ArrayList<Integer>();
        for (Order x : o.getSelection()) {
            kept.add(x.getId());
        }
        assertTrue(kept.contains(a.getId()));
        assertEquals(b.getCustomer().equals(a.getCustomer()), kept.contains(b.getId()));
        o.setQuery("zzz");
        assertTrue(o.getNoSelection(), "rows that are no longer listed drop out");
        assertEquals(-1, o.getCurrent());
    }

    private static int indexOf(OrdersController o, Order x) {
        return o.getVisible().indexOf(x);
    }

    @Test
    void deleteAsksFirstThenRemovesTheSelectedOrders() throws Exception {
        load(200);
        OrdersController o = shell.orders();
        o.askDelete();
        assertFalse(o.getConfirmDelete(), "nothing selected: nothing to confirm");
        o.setPicked(new double[] {0, 1, 2});
        o.setCurrent(2);
        o.askDelete();
        assertTrue(o.getConfirmDelete());
        assertEquals("3 orders will be removed from this file.", o.getDeleteMessage());
        o.keepOrders();
        assertEquals(200, session.store().size());
        o.askDelete();
        o.delete();
        assertFalse(o.getConfirmDelete());
        assertEquals(197, session.store().size());
        assertEquals(197, o.getVisible().size());
        assertTrue(o.getNoSelection());
        assertEquals(4, o.getVisible().get(0).getId());
    }

    // ---- the editor ------------------------------------------------------------------------------------------------

    @Test
    void savingAnInvalidOrderShowsEveryMessageAndStaysOpen() throws Exception {
        load(50);
        EditorController e = shell.editor();
        e.openNew();
        assertTrue(e.getOpen());
        assertEquals("New order", e.getTitle());
        assertEquals("", e.getCustomerError(), "a blank form is not red before the user did anything");
        e.save();
        assertTrue(e.getOpen());
        assertEquals("Customer is required", e.getCustomerError());
        assertEquals("E-mail is required", e.getEmailError());
        assertEquals("Fix the items marked below", e.getLinesError());
        assertEquals("Describe the item", e.getDraft().getLines().get(0).getShownError());
        assertEquals(50, session.store().size());
    }

    @Test
    void aFieldShowsItsMessageOnceTouched() {
        EditorController e = shell.editor();
        e.openNew();
        e.setEmail("nope");
        assertEquals("That is not an e-mail address", e.getEmailError());
        assertEquals("", e.getCustomerError(), "the other fields are untouched");
        e.setEmail("ada@example.com");
        assertEquals("", e.getEmailError());
    }

    @Test
    void aValidOrderIsSavedWithTheNextIdAndSelected() throws Exception {
        load(50);
        EditorController e = shell.editor();
        e.openNew();
        e.setCustomer("Katherine Johnson");
        e.setEmail("katherine@example.com");
        e.setDate("2026-11-01");
        e.setStatus("Paid");
        OrderDraft.DraftLine line = e.getDraft().getLines().get(0);
        e.setDescription(line, "Orbit calculator");
        e.setQuantity(line, 3);
        e.setPrice(line, "19.90");
        e.addLine();
        OrderDraft.DraftLine second = e.getDraft().getLines().get(1);
        e.setDescription(second, "Slide rule");
        e.setQuantity(second, 1);
        e.setPrice(second, "5");
        assertEquals("$64.70", e.getTotalText());
        e.save();
        assertFalse(e.getOpen());
        assertEquals(51, session.store().size());
        Order saved = session.store().find(51);
        assertNotNull(saved);
        assertEquals("Katherine Johnson", saved.getCustomer());
        assertEquals(Status.PAID, saved.getStatus());
        assertEquals(6470, saved.getTotalCents());
        assertEquals("Saved order #51", shell.getStatus());
        assertTrue(shell.getSnackOpen());
        assertEquals("Order #51 saved", shell.getSnackMessage());
        assertEquals(51, shell.orders().getOne().getId(), "the new order is selected in the table");
        assertEquals(50, shell.orders().getCurrent(), "and it is the last row");
    }

    @Test
    void editingAnOrderReplacesIt() throws Exception {
        load(50);
        Order o = session.store().find(9);
        EditorController e = shell.editor();
        e.openFor(o);
        assertEquals("Edit order #9", e.getTitle());
        assertEquals(o.getCustomer(), e.getDraft().getCustomer());
        e.setCustomer("Renamed Customer");
        OrderDraft.DraftLine line = e.getDraft().getLines().get(0);
        e.setQuantity(line, 7);
        e.save();
        assertEquals(50, session.store().size());
        assertEquals("Renamed Customer", session.store().find(9).getCustomer());
        assertEquals(7, session.store().find(9).getLines().get(0).getQuantity());
        assertEquals(o.getLines().size(), session.store().find(9).getLines().size());
    }

    @Test
    void cancelAndEscapeCloseWithoutSaving() throws Exception {
        load(20);
        EditorController e = shell.editor();
        e.openFor(session.store().find(2));
        e.setCustomer("Changed");
        e.cancel();
        assertFalse(e.getOpen());
        assertFalse("Changed".equals(session.store().find(2).getCustomer()));
        e.openNew();
        e.setOpen(false);
        assertFalse(e.getOpen(), "Escape reaches the controller as onShowing(false)");
        e.openNew();
        assertTrue(e.getOpen());
        e.removeLine(e.getDraft().getLines().get(0));
        assertEquals("Add at least one item", e.getLinesError());
    }

    // ---- the other screens and the theme ---------------------------------------------------------------------------

    @Test
    void newOrderFromTheMenuSwitchesToTheOrdersScreen() {
        shell.showDashboard();
        shell.newOrder();
        assertEquals(ShellController.Screen.ORDERS, shell.getScreen());
        assertTrue(shell.editor().getOpen());
    }

    @Test
    void sidebarSwitchesScreens() {
        shell.showCustomers();
        assertTrue(shell.getOnCustomers());
        assertFalse(shell.getOnOrders());
        shell.showDashboard();
        assertTrue(shell.getOnDashboard());
        shell.showOrders();
        assertTrue(shell.getOnOrders());
    }

    @Test
    void themeSwitchChangesThePaletteAndTheRootProp() {
        assertEquals("light", shell.getThemeName());
        assertEquals("light", shell.view().getProps().get("theme"));
        shell.setDark();
        assertEquals("dark", shell.getThemeName());
        assertEquals("dark", shell.view().getProps().get("theme"));
        assertEquals(Palette.of("dark").getBackground(), shell.getPalette().getBackground());
        shell.setLight();
        assertEquals("light", shell.view().getProps().get("theme"));
    }

    @Test
    void customersScreenSelectsAndLiftsCards() throws Exception {
        CustomersController c = shell.customers();
        assertTrue(c.getNoCustomers());
        assertFalse(c.getHasSelected());
        load(2000);
        assertEquals(12, c.getCustomers().size());
        assertEquals(0, c.getCurrent());
        assertEquals("Ada Lovelace", c.getSelected().getName());
        c.setCurrent(3);
        assertEquals(c.getCustomers().get(3).getName(), c.getSelected().getName());
        c.setCurrent(99);
        assertEquals(11, c.getCurrent(), "an index past the end is the last customer");
        assertEquals(4, c.getFigures().size());
        assertEquals(0.0, c.getFigures().get(1).getRise());
        c.lift(c.getFigures().get(1));
        assertEquals(-4.0, c.getFigures().get(1).getRise());
        assertEquals(0.0, c.getFigures().get(0).getRise());
        c.lift(c.getFigures().get(1));
        assertEquals(0.0, c.getFigures().get(1).getRise(), "a second click puts it back");
        assertEquals(5, c.getRecentOrders().size());
    }

    @Test
    void dashboardBarsHaveALengthAndAColour() throws Exception {
        load(2000);
        DashboardController d = shell.dashboard();
        assertEquals(4, d.getKpis().size());
        assertEquals("2,000", d.getKpis().get(0).getValue());
        assertEquals(4, d.getStatusBars().size());
        double max = 0;
        for (DashboardController.BarView b : d.getStatusBars()) {
            max = Math.max(max, b.getSize());
        }
        assertEquals(DashboardController.BAR_LENGTH, max, 1e-9);
        assertEquals(6, d.getMonthBars().size());
        for (DashboardController.BarView b : d.getMonthBars()) {
            assertTrue(b.getSize() >= 2 && b.getSize() <= DashboardController.COLUMN_HEIGHT);
        }
        shell.setDark();
        assertEquals(Palette.of("dark").series(0), d.getMonthBars().get(0).getColor());
        assertFalse(d.getNoOrders());
    }

    @Test
    void aSecondLoadReplacesTheFirstOne() throws Exception {
        load(100);
        load(250);
        assertEquals(250, session.store().size());
        assertEquals(250, shell.orders().getVisible().size());
        // a file written by hand is read like any other
        File f = new File(tmp, "one.csv");
        Files.write(f.toPath(), "1,Ada Lovelace,ada@example.com,2026-01-05,New,Cable,2,3.50\n".getBytes(StandardCharsets.UTF_8));
        await(shell.importFile(f));
        assertEquals(1, session.store().size());
        assertEquals(700, session.store().find(1).getTotalCents());
    }

    @Test
    void ioErrorsOfTheFileSystemAreReported() throws Exception {
        load(10);
        File dir = new File(tmp, "folder");
        assertTrue(dir.mkdir());
        await(shell.exportTo(dir));
        assertTrue(shell.getStatus().startsWith("Writing folder failed"), shell.getStatus());
        assertFalse(shell.getBusy());
        try {
            Files.readAllBytes(dir.toPath());
        } catch (IOException expected) {
            // the folder is still a folder
        }
    }
}
