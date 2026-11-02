package com.jxparallel.examples;

import static com.jxparallel.examples.JvmMetrics.metric;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;

/**
 * The JavaFX reference of {@code ComponentBench}: builds the same N instances of a component with plain JavaFX classes in
 * code and measures the same stages on the FX thread, emitting the same {@code JX_METRIC} keys:
 * <ol>
 *   <li>{@code build}: the N instances in a VBox.</li>
 *   <li>{@code ready}: from a fresh VBox (built before the clock starts), {@code new Scene(root, 1280, 800)},
 *       {@code applyCss()} and {@code layout()}.</li>
 *   <li>{@code update}: the same prop of every control is set to the next value ({@code layout()} after); the two value
 *       sets alternate.</li>
 *   <li>{@code paint}: {@code Scene.snapshot} into a reused 1280x800 {@code WritableImage}: JavaFX's own pipeline
 *       (Prism, hardware unless started with {@code -Dprism.order=sw}), so indicative, not equivalent to the software
 *       painter of the JXML side.</li>
 *   <li>{@code retained}: live heap after GC with the last instances held and after releasing them.</li>
 * </ol>
 * Modes: {@code javafx NAME}, {@code list}, {@code pilot [NAME,NAME]}. Java 8 API only.
 */
public final class ComponentBenchFx {
    private static final int PASSES = Integer.getInteger("jx.comp.passes", 5);
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 800;
    private static final double PILOT_TARGET_MS = 50;
    private static final double PILOT_STAGE_MAX_MS = 1000;

    private static ComponentFxFactories.FxCase keptCase;
    private static Scene keptScene;
    private static WritableImage image;

    private ComponentBenchFx() {
    }

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "javafx";
        Map<String, ComponentCsv.Entry> table = ComponentCsv.read();
        if ("list".equals(mode)) {
            for (ComponentCsv.Entry e : table.values()) {
                if (!e.fx.isEmpty()) {
                    System.out.println(e.name + "," + e.n + "," + e.fx);
                }
            }
            return;
        }
        startToolkit();
        if ("pilot".equals(mode)) {
            List<String> names = new ArrayList<String>();
            if (args.length > 1) {
                names.addAll(Arrays.asList(args[1].split(",")));
            } else {
                for (ComponentCsv.Entry e : table.values()) {
                    if (!e.fx.isEmpty()) {
                        names.add(e.name);
                    }
                }
            }
            for (String name : names) {
                final ComponentCsv.Entry entry = table.get(name);
                onFx(() -> pilot(entry));
            }
            System.exit(0);
        }
        ComponentCsv.Entry entry = table.get(args[1]);
        if (entry == null || entry.fx.isEmpty()) {
            System.err.println(args[1] + " has no JavaFX mode");
            System.exit(3);
        }
        measure(entry);
        Platform.exit();
        System.exit(0);
    }

    /** {@code Platform.startup} is Java 9+; JavaFX 8 has the same entry point in PlatformImpl. */
    private static void startToolkit() throws Exception {
        CountDownLatch toolkit = new CountDownLatch(1);
        Runnable ready = toolkit::countDown;
        try {
            Platform.class.getMethod("startup", Runnable.class).invoke(null, ready);
        } catch (NoSuchMethodException javaFx8) {
            Class.forName("com.sun.javafx.application.PlatformImpl").getMethod("startup", Runnable.class).invoke(null, ready);
        }
        toolkit.await();
    }

    private interface Task {
        void run() throws Exception;
    }

    /** Runs on the FX thread and waits. */
    private static void onFx(Task task) throws Exception {
        CompletableFuture<Void> done = new CompletableFuture<Void>();
        Platform.runLater(() -> {
            try {
                task.run();
                done.complete(null);
            } catch (Throwable e) {
                done.completeExceptionally(e);
            }
        });
        try {
            done.get();
        } catch (java.util.concurrent.ExecutionException e) {
            if (e.getCause() instanceof Exception) {
                throw (Exception) e.getCause();
            }
            throw e;
        }
    }

    private static final class Rec {
        long ns;
        long alloc;
        long threadCpu;
        long processCpu;
    }

    /** Times a task on the FX thread (the clock runs inside it). */
    private static Rec time(Task task) throws Exception {
        final Rec r = new Rec();
        onFx(() -> {
            long alloc = JvmMetrics.allocatedBytes();
            long tcpu = JvmMetrics.threadCpuNanos();
            long pcpu = JvmMetrics.cpuNanos();
            long start = System.nanoTime();
            task.run();
            r.ns = System.nanoTime() - start;
            r.processCpu = JvmMetrics.cpuNanos() - pcpu;
            r.threadCpu = JvmMetrics.threadCpuNanos() - tcpu;
            r.alloc = JvmMetrics.allocatedBytes() - alloc;
        });
        return r;
    }

    private static void emit(String stage, List<Rec> recs) {
        Rec cold = recs.get(0);
        Rec warm = recs.get(recs.size() - 1);
        metric(stage + "_cold_ns", cold.ns);
        metric(stage + "_warm_ns", warm.ns);
        long[] rest = new long[Math.max(1, recs.size() - 1)];
        for (int i = 1; i < recs.size(); i++) {
            rest[i - 1] = recs.get(i).ns;
        }
        if (recs.size() == 1) {
            rest[0] = cold.ns;
        }
        Arrays.sort(rest);
        metric(stage + "_median_ns", rest[(rest.length - 1) / 2]);
        metric(stage + "_cold_alloc_bytes", cold.alloc);
        metric(stage + "_warm_alloc_bytes", warm.alloc);
        metric(stage + "_cold_thread_cpu_ns", cold.threadCpu);
        metric(stage + "_warm_thread_cpu_ns", warm.threadCpu);
        metric(stage + "_cold_process_cpu_ns", cold.processCpu);
        metric(stage + "_warm_process_cpu_ns", warm.processCpu);
        long ns = 0;
        long tcpu = 0;
        long pcpu = 0;
        for (Rec r : recs) {
            ns += r.ns;
            tcpu += r.threadCpu;
            pcpu += r.processCpu;
        }
        metric(stage + "_total_ns", ns);
        metric(stage + "_total_thread_cpu_ns", tcpu);
        metric(stage + "_total_process_cpu_ns", pcpu);
    }

    private static void measure(ComponentCsv.Entry entry) throws Exception {
        JvmMetrics.startSampler();
        ComponentFxFactories.create(entry); // makes the shared models (the JXML controller holds its own), outside the baseline
        metric("n", entry.n);
        metric("passes", PASSES);
        metric("has_javafx", 1);
        metric("pipeline_sw", "sw".equals(System.getProperty("prism.order")) ? 1 : 0);
        long liveBefore = JvmMetrics.liveHeapAfterGc();
        metric("heap_live_before_bytes", liveBefore);
        stages(entry); // its locals are gone when it returns: only the statics hold the last instances
        retained();
    }

    private static void stages(ComponentCsv.Entry entry) throws Exception {
        final ComponentFxFactories.FxCase kase = ComponentFxFactories.create(entry);
        final Object[] box = new Object[2];

        // 1. build
        List<Rec> build = new ArrayList<Rec>();
        for (int p = 0; p < PASSES; p++) {
            build.add(time(() -> box[0] = kase.build()));
        }
        emit("build", build);
        final Parent builtRoot = (Parent) box[0];
        final long[] shape = new long[1];
        onFx(() -> shape[0] = ComponentFxFactories.nodes(builtRoot));
        metric("fp_instances", kase.insts.size());
        metric("fp_nodes", shape[0]);

        // 2. ready: each pass starts from a tree built before the clock
        List<Rec> ready = new ArrayList<Rec>();
        for (int p = 0; p < PASSES; p++) {
            onFx(() -> box[0] = kase.build());
            ready.add(time(() -> {
                Parent root = (Parent) box[0];
                Scene scene = new Scene(root, WIDTH, HEIGHT);
                root.applyCss();
                root.layout();
                box[1] = scene;
            }));
        }
        emit("ready", ready);
        final Scene scene = (Scene) box[1];
        final Parent root = (Parent) box[0];
        onFx(() -> {
            kase.probe();
            int visible = 0;
            for (javafx.scene.Node child : root.getChildrenUnmodifiable()) {
                if (child.getLayoutY() < HEIGHT && child.getLayoutY() + child.getLayoutBounds().getHeight() > 0
                        && child.getLayoutX() < WIDTH) {
                    visible++;
                }
            }
            box[0] = Integer.valueOf(visible);
        });
        metric("fp_probe", kase.sum);
        metric("fp_probe_n", kase.count);
        metric("fp_visible", ((Integer) box[0]).intValue());

        // 3. update: the first pass changes the first value set to the second
        List<Rec> update = new ArrayList<Rec>();
        for (int p = 1; p <= PASSES; p++) {
            final int phase = p & 1;
            update.add(time(() -> {
                kase.update(phase);
                root.layout();
            }));
        }
        emit("update", update);
        onFx(() -> {
            if ((PASSES & 1) == 0) { // end on the second value set, the one the fingerprint is defined for
                kase.update(1);
                root.layout();
            }
            kase.probe();
        });
        metric("fp_probe_updated", kase.sum);
        metric("fp_probe_updated_n", kase.count);

        // 4. paint: Scene.snapshot into a reused image
        onFx(() -> image = new WritableImage(WIDTH, HEIGHT));
        List<Rec> paint = new ArrayList<Rec>();
        for (int p = 0; p < PASSES; p++) {
            paint.add(time(() -> scene.snapshot(image)));
        }
        emit("paint", paint);
        final long[] checksum = new long[1];
        onFx(() -> {
            for (int y = 0; y < HEIGHT; y += 7) {
                for (int x = 0; x < WIDTH; x += 97) {
                    checksum[0] += image.getPixelReader().getArgb(x, y) & 0xFFFFFF;
                }
            }
        });
        metric("paint_checksum", checksum[0]);
        image = null;

        keptCase = kase;
        keptScene = scene;
    }

    /** 5. retained: the live heap with the last instances held (in statics) and after releasing them. */
    private static void retained() {
        long gcCount = 0;
        long gcMillis = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += Math.max(0L, gc.getCollectionCount());
            gcMillis += Math.max(0L, gc.getCollectionTime());
        }
        metric("gc_count_stages", gcCount);
        metric("gc_time_ms_stages", gcMillis);
        metric("heap_live_with_bytes", JvmMetrics.liveHeapAfterGc());
        keptCase = null;
        keptScene = null;
        metric("heap_live_released_bytes", JvmMetrics.liveHeapAfterGc());
        JvmMetrics.emitProcessMetrics();
    }

    /** The N at which a warm build pass lasts about 50 ms (the build is the stage the pilot sizes). */
    private static void pilot(ComponentCsv.Entry entry) {
        int n = entry.n;
        double ms = 0;
        for (int round = 0; round < 8; round++) {
            entry.n = n;
            ComponentFxFactories.FxCase kase = ComponentFxFactories.create(entry);
            for (int p = 0; p < 4; p++) {
                long start = System.nanoTime();
                kase.build();
                ms = (System.nanoTime() - start) / 1e6;
            }
            if (ms >= PILOT_TARGET_MS || n >= 20000) {
                break;
            }
            n = (int) Math.min(20000, Math.max(n + 1, Math.ceil(n * Math.min(8.0, Math.max(1.2, PILOT_TARGET_MS * 1.1 / Math.max(ms, 0.01))))));
        }
        // the stages after the build cost more per instance (skins are made there): lower N until a ready pass and a
        // snapshot each take at most 1 s and the heap of 512 MB holds the scene
        int sizedByBuild = n;
        double readyMs = 0;
        double paintMs = 0;
        for (int round = 0; round < 14; round++) {
            entry.n = n;
            boolean memory = false;
            try {
                for (int p = 0; p < 3; p++) {
                    ComponentFxFactories.FxCase kase = ComponentFxFactories.create(entry);
                    Parent root = kase.build();
                    long start = System.nanoTime();
                    Scene scene = new Scene(root, WIDTH, HEIGHT);
                    root.applyCss();
                    root.layout();
                    readyMs = (System.nanoTime() - start) / 1e6;
                    WritableImage shot = new WritableImage(WIDTH, HEIGHT);
                    start = System.nanoTime();
                    scene.snapshot(shot);
                    paintMs = (System.nanoTime() - start) / 1e6;
                }
            } catch (OutOfMemoryError e) {
                memory = true;
            }
            double worst = Math.max(readyMs, paintMs);
            if (!memory && (worst <= PILOT_STAGE_MAX_MS || n <= 10)) {
                break;
            }
            n = memory ? Math.max(10, n / 2) : Math.max(10, (int) (n * PILOT_STAGE_MAX_MS * 0.9 / worst));
            System.gc();
        }
        System.out.println("PILOT " + entry.name + "," + sizedByBuild + "," + n + ","
                + String.format(java.util.Locale.ROOT, "%.1f,%.1f,%.1f", ms, readyMs, paintMs));
        System.out.flush();
    }
}
