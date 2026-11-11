package com.jxparallel.examples.orders;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

import com.jxparallel.core.CancellationToken;
import com.jxparallel.examples.orders.gen.CustomersView;
import com.jxparallel.examples.orders.gen.DashboardView;
import com.jxparallel.examples.orders.gen.EditorView;
import com.jxparallel.examples.orders.gen.OrdersView;
import com.jxparallel.examples.orders.gen.ShellView;
import com.jxparallel.ui.JXElement;

/**
 * The main window ({@code shell.jxml}): the menu, the sidebar, the status bar, and the screen in the middle, which
 * is another JXML view built with its own controller and placed with a {@code <Slot>}. It also runs the slow work
 * (generating orders, reading and writing CSV files) on the JXParallel worker pool, one job at a time, with a progress
 * bar and a Cancel button; the UI thread only applies the result.
 */
public final class ShellController {
    /** The screens of the sidebar. */
    public enum Screen {
        DASHBOARD, ORDERS, CUSTOMERS
    }

    /** The seed of the demo data. */
    public static final long SEED = 2026;

    private final Session session;
    private final OrdersController orders;
    private final CustomersController customers;
    private final DashboardController dashboard;
    private final EditorController editor;
    private final ShellView shellView = new ShellView();
    private final OrdersView ordersView = new OrdersView();
    private final CustomersView customersView = new CustomersView();
    private final DashboardView dashboardView = new DashboardView();
    private final EditorView editorView = new EditorView();

    private Screen screen = Screen.ORDERS;
    private String status = "Ready";
    private boolean busy;
    private double progress;
    private String busyLabel = "";
    private CancellationToken token = new CancellationToken();
    private boolean aboutOpen;
    private boolean snackOpen;
    private String snackMessage = "";
    private List<String> lastImportErrors = java.util.Collections.emptyList();

    public ShellController(Session session) {
        this.session = session;
        this.editor = new EditorController(session, this::orderSaved);
        this.orders = new OrdersController(session, editor::openNew, editor::openFor, this::ordersChanged);
        this.customers = new CustomersController(session);
        this.dashboard = new DashboardController(session);
    }

    // ---- the parts -----------------------------------------------------------------------------------------

    public OrdersController orders() {
        return orders;
    }

    public EditorController editor() {
        return editor;
    }

    public CustomersController customers() {
        return customers;
    }

    public DashboardController dashboard() {
        return dashboard;
    }

    public Palette getPalette() {
        return session.palette();
    }

    /** The root's {@code theme}: {@code light} or {@code dark}. */
    public String getThemeName() {
        return session.palette().getName();
    }

    /** The whole window: the compiled shell view; its root's {@code theme="${themeName}"} follows the current theme. */
    public JXElement view() {
        return shellView.build(this);
    }

    /** The screen in the middle, built from the state of its own controller. */
    public JXElement getContent() {
        switch (screen) {
            case DASHBOARD:
                return dashboardView.build(dashboard);
            case CUSTOMERS:
                return customersView.build(customers);
            default:
                return ordersView.build(orders);
        }
    }

    /** The order dialog, always built; it takes no room and opens when the editor's state says so. */
    public JXElement getEditorLayer() {
        return editorView.build(editor);
    }

    public Screen getScreen() {
        return screen;
    }

    // ---- sidebar ---------------------------------------------------------------------------------------------

    public void showDashboard() {
        show(Screen.DASHBOARD);
    }

    public void showOrders() {
        show(Screen.ORDERS);
    }

    public void showCustomers() {
        show(Screen.CUSTOMERS);
    }

    /** The screen to open on, before the window exists (nothing is rendered). */
    public void start(Screen first) {
        screen = first;
    }

    public void show(Screen next) {
        screen = next;
        session.host().requestRender();
    }

    public boolean getOnDashboard() {
        return screen == Screen.DASHBOARD;
    }

    public boolean getOnOrders() {
        return screen == Screen.ORDERS;
    }

    public boolean getOnCustomers() {
        return screen == Screen.CUSTOMERS;
    }

    /** The sidebar button of the current screen is the tonal one, the others are plain text (the buttons' {@code variant}). */
    public String getDashboardVariant() {
        return screen == Screen.DASHBOARD ? "tonal" : "text";
    }

    public String getOrdersVariant() {
        return screen == Screen.ORDERS ? "tonal" : "text";
    }

    public String getCustomersVariant() {
        return screen == Screen.CUSTOMERS ? "tonal" : "text";
    }

    // ---- menu --------------------------------------------------------------------------------------------------

    public void newOrder() {
        screen = Screen.ORDERS;
        editor.openNew();
    }

    public void setLight() {
        setTheme(Palette.LIGHT);
    }

    public void setDark() {
        setTheme(Palette.DARK);
    }

    public void setTheme(String name) {
        session.setTheme(name);
        session.host().requestRender();
    }

    public void exit() {
        token.cancel();
        session.host().exit();
    }

    public boolean getAboutOpen() {
        return aboutOpen;
    }

    public void setAboutOpen(boolean v) {
        aboutOpen = v;
        session.host().requestRender();
    }

    public void about() {
        setAboutOpen(true);
    }

    public void closeAbout() {
        setAboutOpen(false);
    }

    public String getAboutText() {
        return "A sample of JXParallel: JXML screens compiled to Java, a virtual table of "
                + "thousands of rows, and CSV work on the worker pool.";
    }

    // ---- status bar --------------------------------------------------------------------------------------------

    public String getStatus() {
        return status;
    }

    public boolean getBusy() {
        return busy;
    }

    public double getProgress() {
        return progress;
    }

    public String getBusyLabel() {
        return busyLabel;
    }

    public String getCountText() {
        return orders.getCountText();
    }

    public boolean getSnackOpen() {
        return snackOpen;
    }

    public void setSnackOpen(boolean v) {
        snackOpen = v;
        session.host().requestRender();
    }

    public String getSnackMessage() {
        return snackMessage;
    }

    public List<String> getLastImportErrors() {
        return lastImportErrors;
    }

    // ---- changes of the orders -----------------------------------------------------------------------------------

    /** The store changed outside the orders screen (load, import, delete): every screen reads it again. */
    void ordersChanged() {
        orders.refresh();
        session.host().requestRender();
    }

    private void orderSaved(Order order) {
        orders.refresh();
        orders.select(order.getId());
        status = "Saved order #" + order.getId();
        snackMessage = "Order #" + order.getId() + " saved";
        snackOpen = true;
        session.host().requestRender();
    }

    // ---- work on the pool ----------------------------------------------------------------------------------------

    private interface Work<T> {
        T run(CancellationToken token, DoubleConsumer progress) throws Exception;
    }

    /**
     * Runs {@code work} on the worker pool and {@code done} with its result on the UI thread. One job at a time: while
     * one runs, another request only says so in the status bar. The returned future completes after {@code done}.
     */
    private <T> CompletableFuture<Void> runWork(String label, Work<T> work, Consumer<T> done) {
        final CompletableFuture<Void> finished = new CompletableFuture<Void>();
        if (busy) {
            status = "Busy with " + busyLabel + ": wait for it, or cancel it";
            session.host().requestRender();
            finished.complete(null);
            return finished;
        }
        final CancellationToken mine = new CancellationToken();
        token = mine;
        busy = true;
        progress = 0;
        busyLabel = label;
        status = label + "...";
        session.host().requestRender();
        final Host host = session.host();
        final DoubleConsumer report = p -> host.runOnUi(() -> {
            if (token == mine) {
                progress = p;
                host.requestRender();
            }
        });
        host.background(() -> work.run(mine, report), mine).whenComplete((result, error) -> host.runOnUi(() -> {
            busy = false;
            progress = 0;
            Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
            if (cause == null) {
                done.accept(result);
            } else if (cause instanceof CancellationException || mine.isCancelled()) {
                status = label + " cancelled";
            } else {
                status = label + " failed: " + (cause.getMessage() == null ? cause.toString() : cause.getMessage());
            }
            host.requestRender();
            finished.complete(null);
        }));
        return finished;
    }

    public void cancelWork() {
        if (busy) {
            token.cancel();
            status = "Cancelling " + busyLabel + "...";
            session.host().requestRender();
        }
    }

    /** Generates {@code count} demo orders on the pool and shows them. */
    public CompletableFuture<Void> loadSample(int count) {
        final long start = System.nanoTime();
        return runWork("Generating " + OrdersController.count(count) + " orders",
                (t, p) -> OrderGenerator.generate(count, SEED, session.today(), p, t::isCancelled),
                list -> {
                    session.store().replaceAll(list);
                    orders.refresh();
                    status = "Generated " + OrdersController.count(list.size()) + " orders in "
                            + (System.nanoTime() - start) / 1_000_000 + " ms";
                });
    }

    /** File, Open CSV: the dialog runs on a pool thread so the window stays alive, then the file is read. */
    public CompletableFuture<Void> openCsv() {
        Host host = session.host();
        CompletableFuture<Void> finished = new CompletableFuture<Void>();
        host.background(() -> host.chooseCsvToOpen(), new CancellationToken()).whenComplete((file, error) -> host.runOnUi(() -> {
            if (error != null || file == null) {
                finished.complete(null);
            } else {
                importFile(file).whenComplete((v, e) -> finished.complete(null));
            }
        }));
        return finished;
    }

    /** Reads a CSV file on the pool; its orders replace the ones shown. */
    public CompletableFuture<Void> importFile(File file) {
        return runWork("Reading " + file.getName(), (t, p) -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8))) {
                return OrderCsv.read(in, file.length(), p, t::isCancelled);
            }
        }, result -> {
            lastImportErrors = result.getErrors();
            if (result.getOrders().isEmpty()) {
                status = file.getName() + ": no orders found" + (result.getErrors().isEmpty() ? "" : " (" + result.getErrors().get(0) + ")");
                return;
            }
            session.store().replaceAll(result.getOrders());
            orders.refresh();
            status = "Imported " + OrdersController.count(result.getOrders().size()) + " orders from " + file.getName()
                    + (result.getSkippedRows() == 0 ? "" : ", " + result.getSkippedRows() + " rows skipped (" + result.getErrors().get(0) + ")");
        });
    }

    /** File, Export CSV: the save dialog on a pool thread, then the file is written. */
    public CompletableFuture<Void> exportCsv() {
        Host host = session.host();
        CompletableFuture<Void> finished = new CompletableFuture<Void>();
        host.background(() -> host.chooseCsvToSave("orders.csv"), new CancellationToken()).whenComplete((file, error) -> host.runOnUi(() -> {
            if (error != null || file == null) {
                finished.complete(null);
            } else {
                exportTo(file).whenComplete((v, e) -> finished.complete(null));
            }
        }));
        return finished;
    }

    /** Writes every order of the file to {@code file} on the pool. */
    public CompletableFuture<Void> exportTo(File file) {
        final List<Order> snapshot = session.store().getOrders(); // immutable: the worker reads it while the UI goes on
        return runWork("Writing " + file.getName(), (t, p) -> {
            OutputStream stream = Files.newOutputStream(file.toPath()); // fails here: nothing was created, nothing to clean
            boolean complete = false;
            try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8))) {
                OrderCsv.write(out, snapshot, p, t::isCancelled);
                complete = true;
            } finally {
                if (!complete) {
                    Files.deleteIfExists(file.toPath()); // cancelled or failed half way: no half-written file is left behind
                }
            }
            return snapshot.size();
        }, n -> status = "Exported " + OrdersController.count(n) + " orders to " + file.getName());
    }

    /** Files dropped on the window: the first CSV is imported. */
    public CompletableFuture<Void> dropFiles(List<String> paths) {
        // a drop over an open dialog (the order editor) never gets here: only the dialog's own onDrop takes it
        for (String p : paths) {
            if (p.toLowerCase().endsWith(".csv")) {
                return importFile(new File(p));
            }
        }
        status = "Drop a .csv file to import it";
        session.host().requestRender();
        return CompletableFuture.completedFuture(null);
    }
}
