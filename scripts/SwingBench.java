import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RepaintManager;
import javax.swing.SpinnerDateModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.AbstractTableModel;

/**
 * FxBackendBench's application in Swing: the same large-form screen (toolbar, accordion of
 * groups with labelled fields, a table of records and a list in tabs, a status bar), the same phases
 * and the same "JX_METRIC key=value" lines, so measure-fx-backends.ps1 runs it as one more case.
 *
 * Differences forced by the toolkit, kept as small as possible:
 *   - Swing has no accordion: each group is a header button over a panel, one group open at a time;
 *   - no date picker: a date spinner (dd/MM/yyyy);
 *   - no frame callback: "a frame containing the change" is painting the dirty regions synchronously
 *     (RepaintManager.paintDirtyRegions, what Swing's own repaint does) and Toolkit.sync(), twice,
 *     as the JavaFX and native versions wait two presented frames;
 *   - no vertical sync: the sustained phase paints as fast as it can (bench.paced=false), so its
 *     frame rate is not comparable; its CPU and allocation per frame are.
 * Look and feel: the platform's (Windows), or -Dbench.laf=<class name> (FlatLaf, for instance).
 */
public class SwingBench {
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

    private final List<JComponent> fields = new ArrayList<>();
    private JLabel status;
    private JProgressBar progress;
    private JTabbedPane tabs;
    private JTable table;
    private RowsModel model;
    private JFrame frame;
    private int nodes;

    /** A record of the table: plain fields (Swing has no properties). */
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
    }

    static final class RowsModel extends AbstractTableModel {
        private static final String[] NAMES = {"ID", "Nome", "Cidade", "Situação", "Valor"};
        private List<Row> rows;

        RowsModel(List<Row> rows) {
            this.rows = rows;
        }

        void setRows(List<Row> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return NAMES.length;
        }

        @Override
        public String getColumnName(int c) {
            return NAMES[c];
        }

        @Override
        public Object getValueAt(int r, int c) {
            Row row = rows.get(r);
            switch (c) {
                case 0: return row.id;
                case 1: return row.name;
                case 2: return row.city;
                case 3: return row.status;
                default: return row.value;
            }
        }
    }

    public static void main(String[] args) throws Exception {
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
        String laf = System.getProperty("bench.laf");
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(laf != null ? laf : UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            new SwingBench().start();
        });
    }

    // ---- the screen ------------------------------------------------------------------------------

    private JComponent screen() {
        JTextField busca = new JTextField(20);
        busca.setToolTipText("Buscar equipamento");
        JComboBox<String> filtro = new JComboBox<>(new String[]{"Todos", "Ativos", "Inativos"});
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        toolbar.add(new JButton("Novo"));
        JButton salvar = new JButton("Salvar");
        toolbar.add(salvar);
        toolbar.add(busca);
        toolbar.add(filtro);

        JPanel accordion = new JPanel();
        accordion.setLayout(new BoxLayout(accordion, BoxLayout.Y_AXIS));
        List<JPanel> bodies = new ArrayList<>();
        for (int p = 0; p < PANES; p++) {
            JPanel grid = new JPanel(new GridBagLayout());
            grid.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            for (int r = 0; r < ROWS; r++) {
                GridBagConstraints l = new GridBagConstraints();
                l.gridx = 0;
                l.gridy = r;
                l.anchor = GridBagConstraints.WEST;
                l.insets = new Insets(3, 0, 3, 8);
                JLabel label = new JLabel("Parâmetro " + (p * ROWS + r));
                label.setPreferredSize(new Dimension(140, label.getPreferredSize().height));
                grid.add(label, l);
                GridBagConstraints v = new GridBagConstraints();
                v.gridx = 1;
                v.gridy = r;
                v.weightx = 1;
                v.fill = GridBagConstraints.HORIZONTAL;
                v.insets = new Insets(3, 0, 3, 0);
                JComponent field = field(p * ROWS + r);
                fields.add(field);
                grid.add(field, v);
                nodes += 2;
            }
            JButton header = new JButton("Grupo " + (p + 1));
            header.setHorizontalAlignment(JButton.LEFT);
            header.setAlignmentX(0f);
            header.setMaximumSize(new Dimension(Integer.MAX_VALUE, header.getPreferredSize().height));
            grid.setAlignmentX(0f);
            grid.setVisible(p == 0); // one group open at a time, like the accordion
            bodies.add(grid);
            header.addActionListener(e -> {
                for (JPanel b : bodies) {
                    b.setVisible(b == grid);
                }
                accordion.revalidate();
            });
            accordion.add(header);
            accordion.add(grid);
            nodes += 2;
        }
        accordion.add(Box.createVerticalGlue());
        JScrollPane cadastro = new JScrollPane(accordion);

        model = new RowsModel(rows(TABLE));
        table = new JTable(model);
        DefaultListModel<String> listItems = new DefaultListModel<>();
        for (int i = 0; i < 1000; i++) {
            listItems.addElement("Evento " + i);
        }
        JList<String> list = new JList<>(listItems);

        tabs = new JTabbedPane();
        tabs.addTab("Cadastro", cadastro);
        tabs.addTab("Registros", new JScrollPane(table));
        tabs.addTab("Eventos", new JScrollPane(list));

        status = new JLabel("Pronto");
        progress = new JProgressBar(0, 100);
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        statusBar.add(status);
        statusBar.add(progress);
        JPanel root = new JPanel(new BorderLayout());
        root.add(toolbar, BorderLayout.NORTH);
        root.add(tabs, BorderLayout.CENTER);
        root.add(statusBar, BorderLayout.SOUTH);
        frame.getRootPane().setDefaultButton(salvar);
        nodes += 16;
        return root;
    }

    private static JComponent field(int i) {
        switch (i % 6) {
            case 0:
                return new JTextField("valor " + i);
            case 1: {
                JComboBox<String> c = new JComboBox<>(new String[]{"A", "B", "C", "D", "E", "F", "G", "H", "I", "J"});
                c.setSelectedItem("A");
                return c;
            }
            case 2:
                return new JCheckBox("Habilitado");
            case 3:
                return new JSpinner(new SpinnerNumberModel(i, 0, 1000, 1));
            case 4: {
                JSpinner date = new JSpinner(new SpinnerDateModel(date(i), null, null, Calendar.DAY_OF_MONTH));
                date.setEditor(new JSpinner.DateEditor(date, "dd/MM/yyyy"));
                return date;
            }
            default:
                return new JTextField();
        }
    }

    private static java.util.Date date(int plusDays) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(2026, Calendar.JANUARY, 1);
        c.add(Calendar.DAY_OF_MONTH, plusDays);
        return c.getTime();
    }

    private static List<Row> rows(int n) {
        List<Row> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new Row(i));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private void loadRecord(int record) {
        for (int i = 0; i < fields.size(); i++) {
            JComponent f = fields.get(i);
            int v = record * 31 + i;
            if (f instanceof JTextField) {
                ((JTextField) f).setText("registro " + record + " campo " + i);
            } else if (f instanceof JComboBox) {
                ((JComboBox<String>) f).setSelectedItem(String.valueOf((char) ('A' + v % 10)));
            } else if (f instanceof JCheckBox) {
                ((JCheckBox) f).setSelected(v % 2 == 0);
            } else if (f instanceof JSpinner && ((JSpinner) f).getModel() instanceof SpinnerNumberModel) {
                ((JSpinner) f).setValue(v % 1000);
            } else if (f instanceof JSpinner) {
                ((JSpinner) f).setValue(date(v % 365));
            }
        }
        status.setText("Registro " + record);
    }

    /** The fields' values, toolkit-neutral (text, chosen item, selected, number, ISO date): equal in every version. */
    @SuppressWarnings("unchecked")
    private long stateHash() {
        StringBuilder sb = new StringBuilder();
        for (JComponent f : fields) {
            if (f instanceof JTextField) {
                sb.append(((JTextField) f).getText());
            } else if (f instanceof JComboBox) {
                sb.append(((JComboBox<String>) f).getSelectedItem());
            } else if (f instanceof JCheckBox) {
                sb.append(((JCheckBox) f).isSelected());
            } else if (((JSpinner) f).getValue() instanceof java.util.Date) {
                sb.append(new java.text.SimpleDateFormat("yyyy-MM-dd").format((java.util.Date) ((JSpinner) f).getValue()));
            } else {
                sb.append(((JSpinner) f).getValue());
            }
            sb.append('|');
        }
        sb.append(status.getText());
        return sb.toString().hashCode() & 0xffffffffL;
    }

    // ---- phases ----------------------------------------------------------------------------------

    private void start() {
        metric("start_uptime_ms", ManagementFactory.getRuntimeMXBean().getUptime());
        long cpu = cpuNanos();
        long alloc = allocatedBytes();
        long t0 = System.nanoTime();
        frame = new JFrame("Large-form bench (Swing)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(screen());
        frame.setSize(1100, 760);
        frame.setVisible(true);
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
    }

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
            tabs.setSelectedIndex((i + 1) % 3);
            status.setText("Aba " + i);
        }, () -> {
            metric("tabs_ns", System.nanoTime() - t0);
            metric("tabs_cpu_ns", cpuNanos() - cpu);
            metric("tabs_allocated_bytes", allocatedBytes() - alloc);
            tabs.setSelectedIndex(1);
            afterFrames(2, this::scroll);
        });
    }

    private void scroll() {
        long threads = threadCpuNanos();
        long cpu = cpuNanos();
        long alloc = allocatedBytes();
        long app0 = allocatedBy("AWT-EventQueue");
        long t0 = System.nanoTime();
        int stride = Math.max(1, TABLE / SCROLLS);
        step(0, SCROLLS, i -> {
            table.scrollRectToVisible(table.getCellRect(i * stride, 0, true));
            status.setText("Linha " + i * stride);
        }, () -> {
            metric("scroll_ns", System.nanoTime() - t0);
            metric("scroll_cpu_ns", cpuNanos() - cpu);
            metric("scroll_thread_cpu_ns", threadCpuNanos() - threads);
            metric("scroll_allocated_bytes", allocatedBytes() - alloc);
            metric("scroll_app_allocated_bytes", allocatedBy("AWT-EventQueue") - app0);
            metric("scroll_render_allocated_bytes", 0); // Swing paints on the event thread
            tabs.setSelectedIndex(0);
            afterFrames(2, this::sustained);
        });
    }

    private void sustained() {
        long threads = threadCpuNanos();
        long cpu = cpuNanos();
        long alloc = allocatedBytes();
        long app0 = threadCpuBy("AWT-EventQueue");
        long t0 = System.nanoTime();
        JTextField first = (JTextField) fields.get(0);
        everyFrame(FRAMES, i -> {
            status.setText("Quadro " + i);
            progress.setValue(i % 100);
            first.setText("quadro " + i);
        }, frames -> {
            long elapsed = System.nanoTime() - t0;
            metric("sustained_ns", elapsed);
            metric("sustained_cpu_ns", cpuNanos() - cpu);
            metric("sustained_thread_cpu_ns", threadCpuNanos() - threads);
            metric("sustained_app_thread_cpu_ns", threadCpuBy("AWT-EventQueue") - app0);
            metric("sustained_render_thread_cpu_ns", 0); // painted on the event thread
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
        tabs.setSelectedIndex(1);
        model.setRows(rows(BIG));
        table.scrollRectToVisible(table.getCellRect(BIG / 2, 0, true));
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
        frame.dispose();
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

    /** One frame: what is pending (layout, then the dirty regions) painted now, and flushed to the screen. */
    private void paintFrame() {
        frame.getRootPane().validate();
        RepaintManager.currentManager(frame.getRootPane()).paintDirtyRegions();
        Toolkit.getDefaultToolkit().sync();
    }

    /** Runs {@code then} on the event thread after {@code count} frames, each a later event. */
    private void afterFrames(int count, Runnable then) {
        SwingUtilities.invokeLater(() -> {
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
            SwingUtilities.invokeLater(next[0]);
        };
        update.accept(0);
        SwingUtilities.invokeLater(next[0]);
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

    static long threadCpuBy(String prefix) {
        java.lang.management.ThreadMXBean t = ManagementFactory.getThreadMXBean();
        long total = 0;
        for (java.lang.management.ThreadInfo info : t.getThreadInfo(t.getAllThreadIds())) {
            if (info != null && info.getThreadName().startsWith(prefix)) {
                total += Math.max(0, t.getThreadCpuTime(info.getThreadId()));
            }
        }
        return total;
    }

    static long allocatedBy(String prefix) {
        com.sun.management.ThreadMXBean t = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long total = 0;
        for (java.lang.management.ThreadInfo info : t.getThreadInfo(t.getAllThreadIds())) {
            if (info != null && info.getThreadName().startsWith(prefix)) {
                total += Math.max(0, t.getThreadAllocatedBytes(info.getThreadId()));
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
