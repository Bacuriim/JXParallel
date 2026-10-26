package com.jxparallel.examples.nativeui;

import static com.jxparallel.examples.nativeui.JvmMetrics.metric;

import java.io.InputStream;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.jxparallel.examples.nativeui.components.ComponentController;
import com.jxparallel.examples.nativeui.components.ComponentTable;
import com.jxparallel.forge.JxmlLive;
import com.jxparallel.forge.JxmlTemplate;
import com.jxparallel.forge.JxmlTrees;
import com.jxparallel.forge.JxmlView;
import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.native2d.JXNativeNode;
import com.jxparallel.ui.native2d.JXWindow;

/**
 * Measures ONE JXML component in ONE mode in this JVM (the counterpart of {@code ComponentBenchFx}): a screen of N
 * instances of the component (components/NAME.jxml, N from components.csv), in these stages, in this order, each in
 * {@code -Djx.comp.passes} passes (pass 1 cold, the last warm), emitting {@code JX_METRIC key=value} lines:
 * <ol>
 *   <li>{@code build}: the element tree of the screen. {@code interpreted} reads and checks the file on every load,
 *       {@code template} reads it once, {@code compiled} calls the generated class, {@code live} (research) is the
 *       compiled build plus the recording of what depends on the controller ({@code JxmlLive}).</li>
 *   <li>{@code ready}: from a fresh tree (built before the clock starts), the native nodes created and laid out at
 *       1280x800 and the tabs mounted ahead.</li>
 *   <li>{@code update}: every instance gets a new value of one prop: the tree is built again with it and reconciled
 *       into the mounted node, then laid out. The two value sets alternate, so each pass changes every instance.</li>
 *   <li>{@code paint}: the laid-out tree painted by the software painter into a pixel buffer that is reused.</li>
 *   <li>{@code retained}: the live heap after GC with the instances held, and after releasing them, against the live
 *       heap before the first build.</li>
 * </ol>
 * Modes {@code verify} (once per component and JVM: the three ways of building must give the same tree), {@code list}
 * and {@code pilot} (finds N) are for the harness.
 */
public final class ComponentBench {
    private static final int PASSES = Integer.getInteger("jx.comp.passes", 5);
    static final int WIDTH = 1280;
    static final int HEIGHT = 800;
    private static final double PILOT_TARGET_MS = 50;
    private static final double PILOT_STAGE_MAX_MS = 1000;

    /** Held in statics so that no stage's live objects are collected before the retained stage reads the heap. */
    private static JXElement keptElement;
    private static JXNativeNode keptNode;
    /** Live mode: the recording stays alive with the instances, so the retained stage counts its cost. */
    private static JxmlLive keptLive;
    private static int[] pixels;

    private ComponentBench() {
    }

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "compiled";
        if ("list".equals(mode)) {
            for (ComponentTable.Entry e : ComponentTable.all().values()) {
                System.out.println(e.name + "," + ComponentTable.n(e.name) + "," + (e.fx.isEmpty() ? "-" : e.fx));
            }
            return;
        }
        if ("pilot".equals(mode)) {
            pilot(args.length > 1 ? Arrays.asList(args[1].split(",")) : ComponentTable.names());
            System.exit(0);
        }
        String name = args[1];
        if ("verify".equals(mode)) {
            System.exit(verify(name) ? 0 : 1);
        }
        measure(mode, name);
        System.exit(0);
    }

    // ---- building the screen in a mode -----------------------------------------------------------------------

    /** Builds the screen the way a mode does; {@code cache} holds the template or the view of the mode. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static JXElement build(String mode, String name, Object controller, Object[] cache) throws Exception {
        if ("interpreted".equals(mode) || "template".equals(mode)) {
            JxmlTemplate template = (JxmlTemplate) cache[0];
            if (template == null || "interpreted".equals(mode)) {
                template = readTemplate(name);
                cache[0] = template;
            }
            return template.build(controller);
        }
        if (!"compiled".equals(mode) && !"live".equals(mode)) {
            throw new IllegalArgumentException("unknown mode " + mode);
        }
        if (cache[0] == null) {
            cache[0] = ComponentBenchViews.view(name);
        }
        if ("live".equals(mode)) { // the same tree, and the recording of what depends on the controller
            return ((JxmlView) cache[0]).live(controller).element();
        }
        return ((JxmlView) cache[0]).build(controller);
    }

    /** The generated view of the component (kept in {@code cache[0]}, as {@link #build} keeps it). */
    @SuppressWarnings("rawtypes")
    private static JxmlView viewOf(String name, Object[] cache) {
        if (cache[0] == null) {
            cache[0] = ComponentBenchViews.view(name);
        }
        return (JxmlView) cache[0];
    }

    static JxmlTemplate readTemplate(String name) throws Exception {
        String path = "components/" + name + ".jxml";
        InputStream in = ComponentBench.class.getResourceAsStream("/" + path);
        if (in == null) {
            throw new IllegalStateException(path + " is not on the classpath");
        }
        try {
            return JxmlTemplate.read(in, path);
        } finally {
            in.close();
        }
    }

    // ---- measuring -------------------------------------------------------------------------------------------

    /** One pass: time, bytes allocated, CPU time of the live threads and of the process. */
    private static final class Rec {
        long ns;
        long alloc;
        long threadCpu;
        long processCpu;
    }

    private interface Task {
        void run() throws Exception;
    }

    private static Rec time(Task task) throws Exception {
        long alloc = JvmMetrics.allocatedBytes();
        long tcpu = JvmMetrics.threadCpuNanos();
        long pcpu = JvmMetrics.cpuNanos();
        long start = System.nanoTime();
        task.run();
        Rec r = new Rec();
        r.ns = System.nanoTime() - start;
        r.processCpu = JvmMetrics.cpuNanos() - pcpu;
        r.threadCpu = JvmMetrics.threadCpuNanos() - tcpu;
        r.alloc = JvmMetrics.allocatedBytes() - alloc;
        return r;
    }

    /** Cold (pass 1), warm (the last), the median of the passes after the first, and the sums over the passes. */
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

    private static void measure(String mode, String name) throws Exception {
        JvmMetrics.startSampler();
        ComponentController controller = (ComponentController) ComponentBenchViews.controller(name);
        controller.setPhase(0);
        metric("n", controller.getCount());
        metric("passes", PASSES);
        metric("has_javafx", ComponentTable.get(name).fx.isEmpty() ? 0 : 1);
        long liveBefore = JvmMetrics.liveHeapAfterGc();
        metric("heap_live_before_bytes", liveBefore);
        stages(mode, name, controller); // its locals are gone when it returns: only the statics hold the last instances
        retained();
    }

    private static void stages(String mode, String name, ComponentController controller) throws Exception {
        ComponentTable.Entry entry = ComponentTable.get(name);
        final Object[] cache = new Object[1];
        final Object[] box = new Object[2];
        final boolean live = "live".equals(mode);
        final long[] attachNs = new long[1];

        // 1. build
        List<Rec> build = new ArrayList<Rec>();
        for (int p = 0; p < PASSES; p++) {
            build.add(time(() -> box[0] = build(mode, name, controller, cache)));
        }
        emit("build", build);
        JXElement root = (JXElement) box[0];
        metric("fp_instances", root.getChildren().size());
        metric("fp_nodes", JxmlTrees.count(root));

        // 2. ready: each pass starts from a tree built before the clock (in live mode, with its recording, which the
        // clock pairs with the mounted nodes: that is part of the cost of being live)
        List<Rec> ready = new ArrayList<Rec>();
        JXNativeNode node = null;
        JxmlLive session = null;
        for (int p = 0; p < PASSES; p++) {
            final JxmlLive recorded = live ? ((JxmlView) viewOf(name, cache)).live(controller) : null;
            final JXElement tree = live ? recorded.element() : build(mode, name, controller, cache);
            box[0] = tree;
            ready.add(time(() -> {
                box[1] = ForgeLoadBenchmark.ready((JXElement) box[0]);
                if (recorded != null) {
                    long start = System.nanoTime();
                    if (!recorded.attach((JXNativeNode) box[1])) {
                        throw new IllegalStateException(name + ": the recording does not pair with the mounted tree: " + recorded.problem());
                    }
                    attachNs[0] = System.nanoTime() - start;
                }
            }));
            node = (JXNativeNode) box[1];
            session = recorded;
            root = tree;
        }
        emit("ready", ready);
        if (live) {
            metric("live_attach_last_ns", attachNs[0]);
        }
        Fingerprint before = fingerprint(node, entry);
        metric("fp_probe", before.sum);
        metric("fp_probe_n", before.count);
        metric("fp_visible", visible(node));

        // 3. update: the two value sets alternate; the first pass changes phase 0 to 1
        List<Rec> update = new ArrayList<Rec>();
        final JXNativeNode mounted = node;
        final JxmlLive liveSession = session;
        for (int p = 1; p <= PASSES; p++) {
            final int phase = p & 1;
            update.add(time(() -> {
                controller.setPhase(phase);
                if (liveSession != null) { // only what changed is written into the mounted nodes
                    if (!liveSession.refresh()) {
                        throw new IllegalStateException(name + ": the root type changed");
                    }
                    mounted.layoutForBackend(WIDTH, HEIGHT);
                    return;
                }
                JXElement next = build(mode, name, controller, cache);
                if (!mounted.reconcile(next)) {
                    throw new IllegalStateException(name + ": the root type changed");
                }
                mounted.layoutForBackend(WIDTH, HEIGHT);
                box[0] = next;
            }));
            if (liveSession == null) {
                root = (JXElement) box[0];
            }
        }
        emit("update", update);
        if ((PASSES & 1) == 0) { // end on the second value set, the one the fingerprint is defined for
            controller.setPhase(1);
            if (liveSession != null) {
                liveSession.refresh();
            } else {
                root = build(mode, name, controller, cache);
                mounted.reconcile(root);
            }
            mounted.layoutForBackend(WIDTH, HEIGHT);
        }
        if (liveSession != null) {
            metric("live_refreshes", liveSession.refreshes());
            metric("live_fallbacks", liveSession.fallbacks());
            metric("live_opaque_builds", liveSession.opaqueBuilds());
            metric("live_changes", liveSession.changes());
            keptLive = liveSession; // its recording is part of what the retained stage weighs
            root = liveSession.element(); // and the element tree is the session's, not the first one built
        }
        Fingerprint after = fingerprint(mounted, entry);
        metric("fp_probe_updated", after.sum);
        metric("fp_probe_updated_n", after.count);

        // 4. paint
        pixels = new int[WIDTH * HEIGHT];
        List<Rec> paint = new ArrayList<Rec>();
        for (int p = 0; p < PASSES; p++) {
            paint.add(time(() -> JXWindow.paintSoftware(mounted, pixels, WIDTH, HEIGHT)));
        }
        emit("paint", paint);
        long painted = 0;
        for (int i = 0; i < pixels.length; i += 97) {
            painted += pixels[i] & 0xFFFFFF;
        }
        metric("paint_checksum", painted);
        pixels = null;

        keptElement = root;
        keptNode = mounted;
    }

    /** 5. retained: the instances of the last pass stay alive in statics; the live heap with and without them. */
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
        keptElement = null;
        keptNode = null;
        keptLive = null;
        metric("heap_live_released_bytes", JvmMetrics.liveHeapAfterGc());
        JvmMetrics.emitProcessMetrics();
    }

    // ---- fingerprint -----------------------------------------------------------------------------------------

    /** The sum over the probed nodes of the hash of the value, and how many nodes were probed. */
    static final class Fingerprint {
        long sum;
        long count;
    }

    /** Reads the probe prop of every node of the probe type, the same way the JavaFX runner reads its controls. */
    static Fingerprint fingerprint(JXNativeNode root, ComponentTable.Entry e) {
        Fingerprint f = new Fingerprint();
        probe(root, e, f);
        return f;
    }

    private static void probe(JXNativeNode node, ComponentTable.Entry e, Fingerprint f) {
        if (e.probeType.equals(node.getType())) {
            Object v = node.getProperty(e.probeProp);
            f.count++;
            f.sum += hash(v, e.probeKind) * f.count; // weighted by the place, so a shifted set of values differs
        }
        for (JXNativeNode child : node.getChildren()) {
            probe(child, e, f);
        }
    }

    /** The same function in {@code ComponentFxData.hash}: strings by their hash code, numbers by thousandths. */
    static long hash(Object v, String kind) {
        if (v == null) {
            return 0;
        }
        switch (kind) {
            case "s": return v.toString().hashCode();
            case "d": return Math.round(((Number) v).doubleValue() * 1000.0);
            case "i": return ((Number) v).longValue();
            default: return Boolean.TRUE.equals(v) ? 1 : 0;
        }
    }

    /** Instances whose box meets the window: the painter draws all of them, whether seen or not. */
    static int visible(JXNativeNode root) {
        int seen = 0;
        for (JXNativeNode child : root.getChildren()) {
            if (child.getY() < HEIGHT && child.getY() + child.getHeight() > 0 && child.getX() < WIDTH) {
                seen++;
            }
        }
        return seen;
    }

    // ---- verify ----------------------------------------------------------------------------------------------

    /**
     * Builds the screen in the three ways, in both value sets, and checks the trees are the same
     * ({@code JxmlTrees.diff}); mounts the compiled one and emits the fingerprint the other runners are compared with.
     */
    private static boolean verify(String name) throws Exception {
        ComponentTable.Entry entry = ComponentTable.get(name);
        ComponentController controller = (ComponentController) ComponentBenchViews.controller(name);
        Object[] interpretedCache = new Object[1];
        Object[] templateCache = new Object[1];
        Object[] compiledCache = new Object[1];
        boolean ok = true;
        JXNativeNode node = null;
        Fingerprint updated = null;
        for (int phase = 0; phase <= 1; phase++) {
            controller.setPhase(phase);
            JXElement interpreted = build("interpreted", name, controller, interpretedCache);
            JXElement template = build("template", name, controller, templateCache);
            JXElement compiled = build("compiled", name, controller, compiledCache);
            for (String problem : new String[] {JxmlTrees.diff(interpreted, compiled), JxmlTrees.diff(interpreted, template)}) {
                if (problem != null) {
                    System.err.println(name + " phase " + phase + ": " + problem);
                    ok = false;
                }
            }
            if (phase == 0) {
                metric("fp_instances", compiled.getChildren().size());
                metric("fp_nodes", JxmlTrees.count(compiled));
                node = ForgeLoadBenchmark.ready(compiled);
                Fingerprint f = fingerprint(node, entry);
                metric("fp_probe", f.sum);
                metric("fp_probe_n", f.count);
                metric("fp_visible", visible(node));
                ok &= f.count > 0;
            } else {
                node.reconcile(compiled);
                node.layoutForBackend(WIDTH, HEIGHT);
                Fingerprint f = fingerprint(node, entry);
                metric("fp_probe_updated", f.sum);
                metric("fp_probe_updated_n", f.count);
                updated = f;
            }
        }
        // live: the same tree, and a screen mounted once and refreshed to the second value set reaches the same fingerprint
        controller.setPhase(0);
        JxmlLive session = viewOf(name, new Object[1]).live(controller);
        String liveProblem = JxmlTrees.diff(build("compiled", name, controller, compiledCache), session.element());
        if (liveProblem != null) {
            System.err.println(name + " live: " + liveProblem);
            ok = false;
        }
        JXNativeNode liveNode = ForgeLoadBenchmark.ready(session.element());
        ok &= session.attach(liveNode);
        controller.setPhase(1);
        ok &= session.refresh();
        liveNode.layoutForBackend(WIDTH, HEIGHT);
        Fingerprint liveFingerprint = fingerprint(liveNode, entry);
        if (updated == null || liveFingerprint.sum != updated.sum || liveFingerprint.count != updated.count) {
            System.err.println(name + " live: the fingerprint after refresh differs from the compiled one");
            ok = false;
        }
        metric("live_verified_fallbacks", session.fallbacks());
        metric("n", controller.getCount());
        metric("verified", ok ? 1 : 0);
        return ok;
    }

    // ---- pilot -----------------------------------------------------------------------------------------------

    /** For each component: the N at which a warm {@code interpreted} build pass (the slowest JXML mode) lasts about 50 ms. */
    private static void pilot(List<String> names) throws Exception {
        for (String name : names) {
            int n = ComponentTable.n(name);
            double ms = 0;
            for (int round = 0; round < 8; round++) {
                ComponentTable.override(name, n);
                ComponentController controller = (ComponentController) ComponentBenchViews.controller(name);
                Object[] cache = new Object[1];
                for (int p = 0; p < 4; p++) {
                    long start = System.nanoTime();
                    build("interpreted", name, controller, cache);
                    ms = (System.nanoTime() - start) / 1e6;
                }
                if (ms >= PILOT_TARGET_MS || n >= 20000) {
                    break;
                }
                n = (int) Math.min(20000, Math.max(n + 1, Math.ceil(n * Math.min(8.0, Math.max(1.2, PILOT_TARGET_MS * 1.1 / Math.max(ms, 0.01))))));
            }
            // the stages after the build cost more per instance: lower N until a ready pass and a paint each take at most 1 s
            int sizedByBuild = n;
            double readyMs = 0;
            double paintMs = 0;
            int[] buffer = new int[WIDTH * HEIGHT];
            for (int round = 0; round < 12; round++) {
                ComponentTable.override(name, n);
                ComponentController sized = (ComponentController) ComponentBenchViews.controller(name);
                Object[] cache = new Object[1];
                for (int p = 0; p < 3; p++) {
                    JXElement tree = build("compiled", name, sized, cache);
                    long start = System.nanoTime();
                    JXNativeNode node = ForgeLoadBenchmark.ready(tree);
                    readyMs = (System.nanoTime() - start) / 1e6;
                    start = System.nanoTime();
                    JXWindow.paintSoftware(node, buffer, WIDTH, HEIGHT);
                    paintMs = (System.nanoTime() - start) / 1e6;
                }
                double worst = Math.max(readyMs, paintMs);
                if (worst <= PILOT_STAGE_MAX_MS || n <= 10) {
                    break;
                }
                n = Math.max(10, (int) (n * PILOT_STAGE_MAX_MS * 0.9 / worst));
            }
            System.out.println("PILOT " + name + "," + sizedByBuild + "," + n + ","
                    + String.format(java.util.Locale.ROOT, "%.1f,%.1f,%.1f", ms, readyMs, paintMs));
            System.out.flush();
        }
    }
}
