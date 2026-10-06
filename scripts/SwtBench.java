import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DateTime;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.ExpandBar;
import org.eclipse.swt.widgets.ExpandItem;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/**
 * FxBackendBench's application in SWT (native Windows controls): the same large-form screen,
 * the same phases and the same "JX_METRIC key=value" lines.
 *
 * Toolkit equivalents: the accordion is an ExpandBar with one item open at a time; the date picker is
 * a DateTime drop-down; the table is a virtual Table (rows given on demand, as JavaFX's cells are).
 * SWT has no frame callback: "a frame containing the change" is Shell.update(), which paints the
 * pending damage synchronously, done in two later events, as the other versions wait two frames.
 * No vertical sync, so the sustained phase's frame rate is not comparable; CPU and allocation are.
 */
public class SwtBench {
    private static final int PANES = Integer.getInteger("bench.panes", 6);
    private static final int ROWS = Integer.getInteger("bench.rows", 10);
    private static final int TABLE = Integer.getInteger("bench.table", 10_000);
    private static final int BIG = Integer.getInteger("bench.big", 100_000);
    private static final int RECORDS = Integer.getInteger("bench.records", 50);
    private static final int TAB_SWITCHES = Integer.getInteger("bench.tabs", 30);
    private static final int SCROLLS = Integer.getInteger("bench.scrolls", 200);
    private static final int FRAMES = Integer.getInteger("bench.frames", 600);
    private static final MemoryMXBean MEMORY = ManagementFactory.getMemoryMXBean();
    private static volatile long peakHeap;

    private final List<Control> fields = new ArrayList<>();
    private Display display;
    private Shell shell;
    private Label status;
    private ProgressBar progress;
    private TabFolder tabs;
    private Table table;
    private List<Row> rows;
    private int nodes;

    static final class Row {
        final int id;
        final String name;
        final String city;
        final String status;
        final int value;

        Row(int i) {
            id = i;
            name = "Equipamento " + i;
            city = i % 3 == 0 ? "Fortaleza" : i % 3 == 1 ? "Recife" : "Natal";
            status = i % 7 == 0 ? "Inativo" : "Ativo";
            value = (i * 37) % 1000;
        }

        String[] texts() {
            return new String[]{String.valueOf(id), name, city, status, String.valueOf(value)};
        }
    }

    public static void main(String[] args) {
        metric("backend_native", 0);
        Thread sampler = new Thread(() -> {
            while (true) {
                peakHeap = Math.max(peakHeap, MEMORY.getHeapMemoryUsage().getUsed());
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "bench-sampler");
        sampler.setDaemon(true);
        sampler.start();
        new SwtBench().run();
    }

    private void run() {
        metric("start_uptime_ms", ManagementFactory.getRuntimeMXBean().getUptime());
        long cpu = cpuNanos();
        long alloc = allocatedBytes();
        long t0 = System.nanoTime();
        display = new Display();
        shell = new Shell(display);
        shell.setText("Large-form bench (SWT)");
        screen();
        shell.setSize(1100, 760);
        shell.open();
        long shown = System.nanoTime();
        metric("fields", fields.size());
        metric("nodes_created", nodes + TABLE);
        afterFrames(2, () -> {
            long now = System.nanoTime();
            metric("build_show_ns", shown - t0);
            metric("build_to_frame_ns", now - t0);
            metric("build_cpu_ns", cpuNanos() - cpu);
            metric("build_allocated_bytes", allocatedBytes() - alloc);
            metric("first_frame_uptime_ms", ManagementFactory.getRuntimeMXBean().getUptime());
            metric("first_frame_process_cpu_ns", cpuNanos());
            records();
        });
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
    }

    // ---- the screen ------------------------------------------------------------------------------

    private void screen() {
        shell.setLayout(new GridLayout(1, false));
        Composite toolbar = new Composite(shell, SWT.NONE);
        toolbar.setLayout(new RowLayout(SWT.HORIZONTAL));
        new Button(toolbar, SWT.PUSH).setText("Novo");
        Button salvar = new Button(toolbar, SWT.PUSH);
        salvar.setText("Salvar");
        shell.setDefaultButton(salvar);
        Text busca = new Text(toolbar, SWT.BORDER);
        busca.setMessage("Buscar equipamento");
        Combo filtro = new Combo(toolbar, SWT.READ_ONLY);
        filtro.setItems("Todos", "Ativos", "Inativos");
        filtro.select(0);

        tabs = new TabFolder(shell, SWT.NONE);
        tabs.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        ExpandBar accordion = new ExpandBar(tabs, SWT.V_SCROLL);
        List<ExpandItem> items = new ArrayList<>();
        for (int p = 0; p < PANES; p++) {
            Composite grid = new Composite(accordion, SWT.NONE);
            GridLayout layout = new GridLayout(2, false);
            layout.horizontalSpacing = 8;
            layout.verticalSpacing = 6;
            grid.setLayout(layout);
            for (int r = 0; r < ROWS; r++) {
                Label label = new Label(grid, SWT.NONE);
                label.setText("Parâmetro " + (p * ROWS + r));
                GridData l = new GridData();
                l.widthHint = 140;
                label.setLayoutData(l);
                Control field = field(grid, p * ROWS + r);
                field.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
                fields.add(field);
                nodes += 2;
            }
            ExpandItem item = new ExpandItem(accordion, SWT.NONE);
            item.setText("Grupo " + (p + 1));
            item.setControl(grid);
            item.setHeight(grid.computeSize(SWT.DEFAULT, SWT.DEFAULT).y);
            item.setExpanded(p == 0);
            items.add(item);
            nodes += 2;
        }
        accordion.addListener(SWT.Expand, e -> { // one group open at a time, like the accordion
            for (ExpandItem other : items) {
                if (other != e.item) {
                    other.setExpanded(false);
                }
            }
        });
        TabItem cadastro = new TabItem(tabs, SWT.NONE);
        cadastro.setText("Cadastro");
        cadastro.setControl(accordion);

        rows = rows(TABLE);
        table = new Table(tabs, SWT.VIRTUAL | SWT.BORDER | SWT.FULL_SELECTION);
        table.setHeaderVisible(true);
        for (String name : new String[]{"ID", "Nome", "Cidade", "Situação", "Valor"}) {
            TableColumn c = new TableColumn(table, SWT.NONE);
            c.setText(name);
            c.setWidth(150);
        }
        table.addListener(SWT.SetData, e -> {
            TableItem item = (TableItem) e.item;
            item.setText(rows.get(table.indexOf(item)).texts());
        });
        table.setItemCount(TABLE);
        TabItem registros = new TabItem(tabs, SWT.NONE);
        registros.setText("Registros");
        registros.setControl(table);

        org.eclipse.swt.widgets.List list = new org.eclipse.swt.widgets.List(tabs, SWT.V_SCROLL | SWT.BORDER);
        String[] events = new String[1000];
        for (int i = 0; i < events.length; i++) {
            events[i] = "Evento " + i;
        }
        list.setItems(events);
        TabItem eventos = new TabItem(tabs, SWT.NONE);
        eventos.setText("Eventos");
        eventos.setControl(list);

        Composite statusBar = new Composite(shell, SWT.NONE);
        statusBar.setLayout(new FillLayout(SWT.HORIZONTAL));
        status = new Label(statusBar, SWT.NONE);
        status.setText("Pronto");
        progress = new ProgressBar(statusBar, SWT.HORIZONTAL);
        progress.setMaximum(100);
        nodes += 16;
    }

    private static Control field(Composite parent, int i) {
        switch (i % 6) {
            case 0: {
                Text t = new Text(parent, SWT.BORDER);
                t.setText("valor " + i);
                return t;
            }
            case 1: {
                Combo c = new Combo(parent, SWT.READ_ONLY);
                c.setItems("A", "B", "C", "D", "E", "F", "G", "H", "I", "J");
                c.select(0);
                return c;
            }
            case 2: {
                Button b = new Button(parent, SWT.CHECK);
                b.setText("Habilitado");
                return b;
            }
            case 3: {
                Spinner s = new Spinner(parent, SWT.BORDER);
                s.setValues(i, 0, 1000, 0, 1, 10);
                return s;
            }
            case 4: {
                DateTime d = new DateTime(parent, SWT.DATE | SWT.DROP_DOWN);
                setDate(d, i);
                return d;
            }
            default: {
                Text t = new Text(parent, SWT.BORDER);
                t.setMessage("opcional");
                return t;
            }
        }
    }

    private static void setDate(DateTime d, int plusDays) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(2026, Calendar.JANUARY, 1);
        c.add(Calendar.DAY_OF_MONTH, plusDays);
        d.setDate(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
    }

    private static List<Row> rows(int n) {
        List<Row> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new Row(i));
        }
        return out;
    }

    private void loadRecord(int record) {
        for (int i = 0; i < fields.size(); i++) {
            Control f = fields.get(i);
            int v = record * 31 + i;
            if (f instanceof Text) {
                ((Text) f).setText("registro " + record + " campo " + i);
            } else if (f instanceof Combo) {
                ((Combo) f).select(v % 10);
            } else if (f instanceof Button) {
                ((Button) f).setSelection(v % 2 == 0);
            } else if (f instanceof Spinner) {
                ((Spinner) f).setSelection(v % 1000);
            } else if (f instanceof DateTime) {
                setDate((DateTime) f, v % 365);
            }
        }
        status.setText("Registro " + record);
    }

    /** The fields' values, toolkit-neutral (text, chosen item, selected, number, ISO date): equal in every version. */
    private long stateHash() {
        StringBuilder sb = new StringBuilder();
        for (Control f : fields) {
            if (f instanceof Text) {
                sb.append(((Text) f).getText());
            } else if (f instanceof Combo) {
                sb.append(((Combo) f).getText());
            } else if (f instanceof Button) {
                sb.append(((Button) f).getSelection());
            } else if (f instanceof Spinner) {
                sb.append(((Spinner) f).getSelection());
            } else if (f instanceof DateTime) {
                DateTime d = (DateTime) f;
                sb.append(String.format("%04d-%02d-%02d", d.getYear(), d.getMonth() + 1, d.getDay()));
            }
            sb.append('|');
        }
        sb.append(status.getText());
        return sb.toString().hashCode() & 0xffffffffL;
    }

    // ---- phases ----------------------------------------------------------------------------------

    private void records() {
        long cpu = cpuNanos();
        long alloc = allocatedBytes();
        long t0 = System.nanoTime();
        long[] work = new long[1];
        step(0, RECORDS, i -> {
            long w = System.nanoTime();
            loadRecord(i);
            work[0] += System.nanoTime() - w;
        }, () -> {
            metric("records_ns", System.nanoTime() - t0);
            metric("records_work_ns", work[0]);
            metric("records_cpu_ns", cpuNanos() - cpu);
            metric("records_allocated_bytes", allocatedBytes() - alloc);
            metric("records_state_hash", stateHash());
            tabs();
        });
    }

    private void tabs() {
        long cpu = cpuNanos();
        long alloc = allocatedBytes();
        long t0 = System.nanoTime();
        step(0, TAB_SWITCHES, i -> {
            tabs.setSelection((i + 1) % 3);
            status.setText("Aba " + i);
        }, () -> {
            metric("tabs_ns", System.nanoTime() - t0);
            metric("tabs_cpu_ns", cpuNanos() - cpu);
            metric("tabs_allocated_bytes", allocatedBytes() - alloc);
            tabs.setSelection(1);
            afterFrames(2, this::scroll);
        });
    }

    private void scroll() {
        long threads = threadCpuNanos();
        long cpu = cpuNanos();
        long alloc = allocatedBytes();
        long t0 = System.nanoTime();
        int stride = Math.max(1, TABLE / SCROLLS);
        step(0, SCROLLS, i -> {
            table.setTopIndex(i * stride);
            status.setText("Linha " + i * stride);
        }, () -> {
            metric("scroll_ns", System.nanoTime() - t0);
            metric("scroll_cpu_ns", cpuNanos() - cpu);
            metric("scroll_thread_cpu_ns", threadCpuNanos() - threads);
            metric("scroll_allocated_bytes", allocatedBytes() - alloc);
            metric("scroll_app_allocated_bytes", allocatedBytes() - alloc); // SWT paints on the UI thread
            metric("scroll_render_allocated_bytes", 0);
            tabs.setSelection(0);
            afterFrames(2, this::sustained);
        });
    }

    private void sustained() {
        long threads = threadCpuNanos();
        long cpu = cpuNanos();
        long alloc = allocatedBytes();
        long t0 = System.nanoTime();
        Text first = (Text) fields.get(0);
        everyFrame(FRAMES, i -> {
            status.setText("Quadro " + i);
            progress.setSelection(i % 100);
            first.setText("quadro " + i);
        }, frames -> {
            long elapsed = System.nanoTime() - t0;
            metric("sustained_ns", elapsed);
            metric("sustained_cpu_ns", cpuNanos() - cpu);
            metric("sustained_thread_cpu_ns", threadCpuNanos() - threads);
            metric("sustained_render_thread_cpu_ns", 0);
            metric("sustained_allocated_bytes", allocatedBytes() - alloc);
            metric("sustained_unpaced", 1);
            frameStats(frames, elapsed);
            bigTable();
        });
    }

    private void bigTable() {
        long cpu = cpuNanos();
        long alloc = allocatedBytes();
        long t0 = System.nanoTime();
        tabs.setSelection(1);
        rows = rows(BIG);
        table.clearAll();
        table.setItemCount(BIG);
        table.setTopIndex(BIG / 2);
        long set = System.nanoTime();
        afterFrames(2, () -> {
            metric("big_items_ns", set - t0);
            metric("big_to_frame_ns", System.nanoTime() - t0);
            metric("big_cpu_ns", cpuNanos() - cpu);
            metric("big_allocated_bytes", allocatedBytes() - alloc);
            finish();
        });
    }

    private void finish() {
        long gcCount = 0;
        long gcMillis = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += Math.max(0, gc.getCollectionCount());
            gcMillis += Math.max(0, gc.getCollectionTime());
        }
        metric("gc_count", gcCount);
        metric("gc_time_ms", gcMillis);
        metric("heap_peak_bytes", peakHeap);
        metric("nonheap_bytes", MEMORY.getNonHeapMemoryUsage().getUsed());
        metric("allocated_total_bytes", allocatedBytes());
        metric("process_cpu_total_ns", cpuNanos());
        metric("thread_cpu_total_ns", threadCpuNanos());
        metric("classes_loaded", ManagementFactory.getClassLoadingMXBean().getTotalLoadedClassCount());
        metric("thread_count", ManagementFactory.getThreadMXBean().getThreadCount());
        System.gc();
        System.gc();
        metric("heap_live_after_gc_bytes", MEMORY.getHeapMemoryUsage().getUsed());
        metric("done_uptime_ms", ManagementFactory.getRuntimeMXBean().getUptime());
        System.out.flush();
        shell.dispose();
        display.dispose();
        System.exit(0);
    }

    private void step(int i, int to, IntConsumer action, Runnable done) {
        if (i >= to) {
            done.run();
            return;
        }
        action.accept(i);
        afterFrames(2, () -> step(i + 1, to, action, done));
    }

    // ---- frames ----------------------------------------------------------------------------------

    /** One frame: the pending damage painted now (SWT lays out only when asked, as the controls keep their size). */
    private void paintFrame() {
        shell.update();
    }

    private void afterFrames(int count, Runnable then) {
        display.asyncExec(() -> {
            paintFrame();
            if (count <= 1) {
                then.run();
            } else {
                afterFrames(count - 1, then);
            }
        });
    }

    private void everyFrame(int count, IntConsumer update, Consumer<long[]> done) {
        long[] frames = new long[count];
        int[] i = new int[1];
        Runnable[] next = new Runnable[1];
        next[0] = () -> {
            paintFrame();
            frames[i[0]] = System.nanoTime();
            if (++i[0] == count) {
                done.accept(frames);
                return;
            }
            update.accept(i[0]);
            display.asyncExec(next[0]);
        };
        update.accept(0);
        display.asyncExec(next[0]);
    }

    // ---- metrics (as FxBackendBench) -------------------------------------------------------------

    static void metric(String name, long value) {
        System.out.println("JX_METRIC " + name + "=" + value);
    }

    static long cpuNanos() {
        java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        return os instanceof com.sun.management.OperatingSystemMXBean
                ? ((com.sun.management.OperatingSystemMXBean) os).getProcessCpuTime() : -1;
    }

    static long threadCpuNanos() {
        java.lang.management.ThreadMXBean t = ManagementFactory.getThreadMXBean();
        long total = 0;
        for (long id : t.getAllThreadIds()) {
            long c = t.getThreadCpuTime(id);
            if (c > 0) {
                total += c;
            }
        }
        return total;
    }

    static long allocatedBytes() {
        com.sun.management.ThreadMXBean t = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long total = 0;
        for (long b : t.getThreadAllocatedBytes(t.getAllThreadIds())) {
            total += Math.max(0, b);
        }
        return total;
    }

    static void frameStats(long[] frames, long elapsed) {
        long[] intervals = new long[frames.length - 1];
        int worst = 0;
        for (int i = 1; i < frames.length; i++) {
            intervals[i - 1] = frames[i] - frames[i - 1];
            if (intervals[i - 1] > intervals[worst]) {
                worst = i - 1;
            }
        }
        metric("frame_max_index", worst);
        Arrays.sort(intervals);
        long jank = Arrays.stream(intervals).filter(v -> v > 25_000_000L).count();
        metric("sustained_frames", frames.length);
        metric("sustained_fps_x100", frames.length * 100_000_000_000L / Math.max(1, elapsed));
        metric("frame_p50_us", intervals[intervals.length / 2] / 1000);
        metric("frame_p95_us", intervals[(int) Math.ceil(intervals.length * 0.95) - 1] / 1000);
        metric("frame_p99_us", intervals[(int) Math.ceil(intervals.length * 0.99) - 1] / 1000);
        metric("frame_max_us", intervals[intervals.length - 1] / 1000);
        metric("jank_frames", jank);
    }
}
