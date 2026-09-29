import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.jxparallel.fx.Fx;
import com.jxparallel.fx.application.Application;
import com.jxparallel.fx.beans.property.SimpleIntegerProperty;
import com.jxparallel.fx.beans.property.SimpleStringProperty;
import com.jxparallel.fx.beans.property.IntegerProperty;
import com.jxparallel.fx.beans.property.StringProperty;
import com.jxparallel.fx.collections.FXCollections;
import com.jxparallel.fx.collections.ObservableList;
import com.jxparallel.fx.geometry.Insets;
import com.jxparallel.fx.scene.Parent;
import com.jxparallel.fx.scene.Scene;
import com.jxparallel.fx.scene.control.*;
import com.jxparallel.fx.scene.control.cell.PropertyValueFactory;
import com.jxparallel.fx.scene.layout.*;
import com.jxparallel.fx.stage.Stage;

/**
 * One application, written against the JavaFX-compatible API DeviceConfig imports (com.jxparallel.fx),
 * run unchanged on both backends: JavaFX (default) and native (-Djx.backend=native). The screen is
 * shaped like DeviceConfig's: an accordion of titled panes holding grids of labelled fields (text
 * fields, combo boxes, check boxes, spinners, date pickers), a table of records and a list, in tabs.
 *
 * Phases, each ended by waiting for frames that contain its changes:
 *   build      create the scene graph and show it (to the first complete frame)
 *   records    load RECORDS records into every field (like opening a record in DeviceConfig)
 *   tabs       switch between the three tabs
 *   scroll     scroll the table through its rows
 *   sustained  update the status bar and a field on every frame for FRAMES frames
 *   bigTable   replace the table's items with BIG rows
 * Prints "JX_METRIC key=value" lines; process memory is sampled outside (measure-fx-backends.ps1).
 *
 * A frame "containing" a change is the second frame presented after it: JavaFX's AnimationTimer
 * runs at the start of a pulse and the native display thread may be presenting an older frame, so
 * waiting two frames is certain on both sides and costs both the same.
 */
public class FxBackendBench extends Application {
    private static final long PROCESS_START = System.nanoTime();
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
    private Label status;
    private ProgressBar progress;
    private TabPane tabs;
    private TableView<Row> table;
    private FrameClock clock;
    private int nodes;

    /** A record of the table, with JX properties like DeviceConfig's models. */
    public static class Row {
        private final IntegerProperty id = new SimpleIntegerProperty();
        private final StringProperty name = new SimpleStringProperty();
        private final StringProperty city = new SimpleStringProperty();
        private final StringProperty status = new SimpleStringProperty();
        private final IntegerProperty value = new SimpleIntegerProperty();

        Row(int i) {
            id.set(i);
            name.set("Equipamento " + i);
            city.set(i % 3 == 0 ? "Fortaleza" : i % 3 == 1 ? "Recife" : "Natal");
            status.set(i % 7 == 0 ? "Inativo" : "Ativo");
            value.set((i * 37) % 1000);
        }

        public IntegerProperty idProperty() {
            return id;
        }

        public StringProperty nameProperty() {
            return name;
        }

        public StringProperty cityProperty() {
            return city;
        }

        public StringProperty statusProperty() {
            return status;
        }

        public IntegerProperty valueProperty() {
            return value;
        }
    }

    public static void main(String[] args) {
        metric("backend_native", Fx.NATIVE ? 1 : 0);
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
        launch(FxBackendBench.class, args);
    }

    // ---- the screen ------------------------------------------------------------------------------

    private Parent screen() {
        Button novo = new Button("Novo");
        Button salvar = new Button("Salvar");
        salvar.setDefaultButton(true);
        TextField busca = new TextField();
        busca.setPromptText("Buscar equipamento");
        ComboBox<String> filtro = new ComboBox<>(FXCollections.observableArrayList("Todos", "Ativos", "Inativos"));
        filtro.setValue("Todos");
        HBox toolbar = new HBox(8, novo, salvar, busca, filtro);
        toolbar.setPadding(new Insets(8));

        Accordion accordion = new Accordion();
        for (int p = 0; p < PANES; p++) {
            GridPane grid = new GridPane();
            grid.setHgap(8);
            grid.setVgap(6);
            grid.setPadding(new Insets(8));
            ColumnConstraints labels = new ColumnConstraints(140);
            ColumnConstraints values = new ColumnConstraints();
            values.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().addAll(labels, values);
            for (int r = 0; r < ROWS; r++) {
                grid.add(new Label("Parâmetro " + (p * ROWS + r)), 0, r);
                Control field = field(p * ROWS + r);
                fields.add(field);
                grid.add(field, 1, r);
                nodes += 2;
            }
            TitledPane pane = new TitledPane("Grupo " + (p + 1), grid);
            accordion.getPanes().add(pane);
            nodes += 2;
        }
        accordion.setExpandedPane(accordion.getPanes().get(0));
        ScrollPane cadastro = new ScrollPane(accordion);
        cadastro.setFitToWidth(true);

        table = new TableView<>(rows(TABLE));
        String[][] columns = {{"ID", "id"}, {"Nome", "name"}, {"Cidade", "city"}, {"Situação", "status"}, {"Valor", "value"}};
        for (String[] c : columns) {
            TableColumn<Row, Object> column = new TableColumn<>(c[0]);
            column.setCellValueFactory(new PropertyValueFactory<>(c[1]));
            table.getColumns().add(column);
        }
        ObservableList<String> listItems = FXCollections.observableArrayList();
        for (int i = 0; i < 1000; i++) {
            listItems.add("Evento " + i);
        }
        ListView<String> list = new ListView<>(listItems);

        tabs = new TabPane(new Tab("Cadastro", cadastro), new Tab("Registros", table), new Tab("Eventos", list));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        status = new Label("Pronto");
        progress = new ProgressBar(0);
        HBox statusBar = new HBox(12, status, progress);
        statusBar.setPadding(new Insets(4, 8, 4, 8));
        BorderPane root = new BorderPane(tabs, toolbar, null, statusBar, null);
        nodes += 16;
        return root;
    }

    private static Control field(int i) {
        switch (i % 6) {
            case 0:
                return new TextField("valor " + i);
            case 1: {
                ComboBox<String> c = new ComboBox<>(FXCollections.observableArrayList("A", "B", "C", "D", "E", "F", "G", "H", "I", "J"));
                c.setValue("A");
                return c;
            }
            case 2:
                return new CheckBox("Habilitado");
            case 3:
                return new Spinner<Integer>(0, 1000, i);
            case 4:
                return new DatePicker(java.time.LocalDate.of(2026, 1, 1).plusDays(i));
            default: {
                TextField t = new TextField();
                t.setPromptText("opcional");
                return t;
            }
        }
    }

    private static ObservableList<Row> rows(int n) {
        List<Row> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new Row(i));
        }
        return FXCollections.observableArrayList(out);
    }

    @SuppressWarnings("unchecked")
    private void loadRecord(int record) {
        for (int i = 0; i < fields.size(); i++) {
            Control f = fields.get(i);
            int v = record * 31 + i;
            if (f instanceof TextField) {
                ((TextField) f).setText("registro " + record + " campo " + i);
            } else if (f instanceof ComboBox) {
                ((ComboBox<String>) f).setValue(String.valueOf((char) ('A' + v % 10)));
            } else if (f instanceof CheckBox) {
                ((CheckBox) f).setSelected(v % 2 == 0);
            } else if (f instanceof Spinner) {
                ((Spinner<Integer>) f).getValueFactory().setValue(v % 1000);
            } else if (f instanceof DatePicker) {
                ((DatePicker) f).setValue(java.time.LocalDate.of(2026, 1, 1).plusDays(v % 365));
            }
        }
        status.setText("Registro " + record);
    }

    // ---- phases ----------------------------------------------------------------------------------

    @Override
    public void start(Stage stage) {
        metric("start_uptime_ms", ManagementFactory.getRuntimeMXBean().getUptime());
        clock = Fx.NATIVE ? new NativeClock() : new PulseClock();
        java.util.Map<String, Long> threadsBefore = threadCpuByName();
        long cpu = cpuNanos();
        long alloc = allocatedBytes();
        long t0 = System.nanoTime();
        Scene scene = new Scene(screen(), 1100, 760);
        stage.setTitle("DeviceConfig-like bench (" + (Fx.NATIVE ? "native" : "JavaFX") + ")");
        stage.setScene(scene);
        stage.show();
        long shown = System.nanoTime();
        metric("fields", fields.size());
        metric("nodes_created", nodes + TABLE);
        clock.afterFrames(2, () -> {
            long now = System.nanoTime();
            metric("build_show_ns", shown - t0);
            metric("build_to_frame_ns", now - t0);
            metric("build_cpu_ns", cpuNanos() - cpu);
            metric("build_allocated_bytes", allocatedBytes() - alloc);
            if (Boolean.getBoolean("bench.threads")) { // where the build's CPU went, by thread
                java.util.Map<String, Long> after = threadCpuByName();
                after.entrySet().stream()
                        .map(e -> new java.util.AbstractMap.SimpleEntry<>(e.getKey(), e.getValue() - threadsBefore.getOrDefault(e.getKey(), 0L)))
                        .filter(e -> e.getValue() > 0)
                        .sorted((x, y) -> Long.compare(y.getValue(), x.getValue()))
                        .forEach(e -> System.out.println("JX_THREAD build " + e.getValue() / 1_000_000 + " ms " + e.getKey()));
                after.entrySet().stream().filter(e -> e.getValue() > 0)
                        .sorted((x, y) -> Long.compare(y.getValue(), x.getValue()))
                        .forEach(e -> System.out.println("JX_THREAD sinceStart " + e.getValue() / 1_000_000 + " ms " + e.getKey()));
            }
            metric("first_frame_uptime_ms", ManagementFactory.getRuntimeMXBean().getUptime());
            metric("first_frame_process_cpu_ns", cpuNanos()); // everything since the JVM started, toolkit start-up included
            records(stage);
        });
    }

    private void records(Stage stage) {
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
            tabs(stage);
        });
    }

    private void tabs(Stage stage) {
        long cpu = cpuNanos();
        long alloc = allocatedBytes();
        long t0 = System.nanoTime();
        step(0, TAB_SWITCHES, i -> {
            tabs.getSelectionModel().select((i + 1) % 3);
            status.setText("Aba " + i);
        }, () -> {
            metric("tabs_ns", System.nanoTime() - t0);
            metric("tabs_cpu_ns", cpuNanos() - cpu);
            metric("tabs_allocated_bytes", allocatedBytes() - alloc);
            tabs.getSelectionModel().select(1);
            clock.afterFrames(2, () -> scroll(stage));
        });
    }

    private void scroll(Stage stage) {
        java.util.Map<String, Long> allocBefore = allocatedByName();
        long threads = threadCpuNanos();
        long cpu = cpuNanos();
        long alloc = allocatedBytes();
        long t0 = System.nanoTime();
        int stride = Math.max(1, TABLE / SCROLLS);
        step(0, SCROLLS, i -> {
            table.scrollTo(i * stride);
            status.setText("Linha " + i * stride);
        }, () -> {
            metric("scroll_ns", System.nanoTime() - t0);
            metric("scroll_cpu_ns", cpuNanos() - cpu);
            metric("scroll_thread_cpu_ns", threadCpuNanos() - threads);
            metric("scroll_allocated_bytes", allocatedBytes() - alloc);
            java.util.Map<String, Long> allocAfter = allocatedByName();
            long app = 0;
            long render = 0;
            for (java.util.Map.Entry<String, Long> e : allocAfter.entrySet()) {
                long d = e.getValue() - allocBefore.getOrDefault(e.getKey(), 0L);
                String n = e.getKey();
                if (n.contains("JavaFX Application")) {
                    app += d;
                } else if (n.contains("QuantumRenderer") || n.toLowerCase().contains("display")) {
                    render += d;
                }
            }
            metric("scroll_app_allocated_bytes", app);
            metric("scroll_render_allocated_bytes", render);
            tabs.getSelectionModel().select(0);
            clock.afterFrames(2, () -> sustained(stage));
        });
    }

    private void sustained(Stage stage) {
        java.util.Map<String, Long> perThread = threadCpuByName();
        long threads = threadCpuNanos();
        long cpu = cpuNanos();
        long alloc = allocatedBytes();
        long t0 = System.nanoTime();
        TextField first = (TextField) fields.get(0);
        clock.everyFrame(FRAMES, i -> {
            status.setText("Quadro " + i);
            progress.setProgress((i % 100) / 100.0);
            first.setText("quadro " + i);
        }, frames -> {
            long elapsed = System.nanoTime() - t0;
            metric("sustained_ns", elapsed);
            metric("sustained_cpu_ns", cpuNanos() - cpu);
            metric("sustained_thread_cpu_ns", threadCpuNanos() - threads);
            java.util.Map<String, Long> after = threadCpuByName();
            long app = 0;
            long render = 0;
            for (java.util.Map.Entry<String, Long> e : after.entrySet()) {
                long d = e.getValue() - perThread.getOrDefault(e.getKey(), 0L);
                String n = e.getKey();
                if (n.contains("JavaFX Application")) {
                    app += d;
                } else if (n.contains("QuantumRenderer") || n.contains("JX display") || n.toLowerCase().contains("display")) {
                    render += d;
                }
            }
            metric("sustained_app_thread_cpu_ns", app);
            metric("sustained_render_thread_cpu_ns", render);
            metric("sustained_allocated_bytes", allocatedBytes() - alloc);
            frameStats(frames, elapsed);
            bigTable(stage);
        });
    }

    private void bigTable(Stage stage) {
        long cpu = cpuNanos();
        long alloc = allocatedBytes();
        long t0 = System.nanoTime();
        tabs.getSelectionModel().select(1);
        table.setItems(rows(BIG));
        table.scrollTo(BIG / 2);
        long set = System.nanoTime();
        clock.afterFrames(2, () -> {
            metric("big_items_ns", set - t0);
            metric("big_to_frame_ns", System.nanoTime() - t0);
            metric("big_cpu_ns", cpuNanos() - cpu);
            metric("big_allocated_bytes", allocatedBytes() - alloc);
            finish(stage);
        });
    }

    private void finish(Stage stage) {
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
        stage.close();
        System.exit(0);
    }

    /** Runs action(i) for i in [from, to), each followed by frames that show it, then done. */
    private void step(int i, int to, java.util.function.IntConsumer action, Runnable done) {
        if (i >= to) {
            done.run();
            return;
        }
        action.accept(i);
        clock.afterFrames(2, () -> step(i + 1, to, action, done));
    }

    // ---- frames ----------------------------------------------------------------------------------

    interface FrameClock {
        /** Runs {@code then} on the application thread once {@code count} frames were presented. */
        void afterFrames(int count, Runnable then);

        /** Calls {@code update(i)} on the application thread once per frame for {@code count} frames. */
        void everyFrame(int count, java.util.function.IntConsumer update, java.util.function.Consumer<long[]> done);
    }

    /** JavaFX: pulses, observed by an AnimationTimer. */
    static final class PulseClock implements FrameClock {
        @Override
        public void afterFrames(int count, Runnable then) {
            new javafx.animation.AnimationTimer() {
                private int seen;

                @Override
                public void handle(long now) {
                    if (++seen >= count) {
                        stop();
                        then.run();
                    }
                }
            }.start();
        }

        @Override
        public void everyFrame(int count, java.util.function.IntConsumer update, java.util.function.Consumer<long[]> done) {
            long[] frames = new long[count];
            new javafx.animation.AnimationTimer() {
                private int i;

                @Override
                public void handle(long now) {
                    frames[i] = System.nanoTime();
                    if (++i == count) {
                        stop();
                        done.accept(frames);
                        return;
                    }
                    update.accept(i);
                }
            }.start();
        }
    }

    /** Native: presented frames of the native window (after the buffer swap), on the display thread. */
    static final class NativeClock implements FrameClock {
        @Override
        public void afterFrames(int count, Runnable then) {
            // queued after the render the changes asked for, so the frames counted are later ones
            javafx.application.Platform.runLater(() -> {
                int[] seen = new int[1];
                com.jxparallel.fx.nativeimpl.NativeFrames.setListener(() -> {
                    if (++seen[0] == count) {
                        com.jxparallel.fx.nativeimpl.NativeFrames.setListener(null);
                        javafx.application.Platform.runLater(then);
                    } else {
                        com.jxparallel.fx.nativeimpl.NativeFrames.requestFrame();
                    }
                });
                com.jxparallel.fx.nativeimpl.NativeFrames.requestFrame();
            });
        }

        @Override
        public void everyFrame(int count, java.util.function.IntConsumer update, java.util.function.Consumer<long[]> done) {
            long[] frames = new long[count];
            int[] i = new int[1];
            com.jxparallel.fx.nativeimpl.NativeFrames.setListener(() -> {
                int n = i[0];
                if (n >= count) {
                    return;
                }
                frames[n] = System.nanoTime();
                i[0] = n + 1;
                if (n + 1 == count) {
                    com.jxparallel.fx.nativeimpl.NativeFrames.setListener(null);
                    javafx.application.Platform.runLater(() -> done.accept(frames));
                } else {
                    javafx.application.Platform.runLater(() -> update.accept(n + 1));
                }
            });
            javafx.application.Platform.runLater(() -> update.accept(0));
        }
    }

    // ---- metrics ---------------------------------------------------------------------------------

    static void metric(String name, long value) {
        System.out.println("JX_METRIC " + name + "=" + value);
    }

    static long cpuNanos() {
        java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        return os instanceof com.sun.management.OperatingSystemMXBean
                ? ((com.sun.management.OperatingSystemMXBean) os).getProcessCpuTime() : -1;
    }

    /** CPU of the JVM's own threads (the display thread's OpenGL calls included, the driver's threads not). */
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

    static java.util.Map<String, Long> threadCpuByName() {
        java.lang.management.ThreadMXBean t = ManagementFactory.getThreadMXBean();
        java.util.Map<String, Long> out = new java.util.HashMap<>();
        for (java.lang.management.ThreadInfo info : t.getThreadInfo(t.getAllThreadIds())) {
            if (info != null) {
                long c = t.getThreadCpuTime(info.getThreadId());
                out.merge(info.getThreadName(), Math.max(0, c), Long::sum);
            }
        }
        return out;
    }

    /** Bytes allocated so far by each live thread, by name (application thread, render thread...). */
    static java.util.Map<String, Long> allocatedByName() {
        java.util.Map<String, Long> out = new java.util.HashMap<>();
        java.lang.management.ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        if (!(threads instanceof com.sun.management.ThreadMXBean)) {
            return out;
        }
        com.sun.management.ThreadMXBean t = (com.sun.management.ThreadMXBean) threads;
        for (java.lang.management.ThreadInfo info : t.getThreadInfo(t.getAllThreadIds())) {
            if (info != null) {
                out.merge(info.getThreadName(), Math.max(0, t.getThreadAllocatedBytes(info.getThreadId())), Long::sum);
            }
        }
        return out;
    }

    static long allocatedBytes() {
        java.lang.management.ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        if (!(threads instanceof com.sun.management.ThreadMXBean)) {
            return -1;
        }
        com.sun.management.ThreadMXBean t = (com.sun.management.ThreadMXBean) threads;
        long[] ids = t.getAllThreadIds();
        long total = 0;
        for (long b : t.getThreadAllocatedBytes(ids)) {
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
