package com.jxparallel.examples.nativeui;

import static com.jxparallel.examples.nativeui.JvmMetrics.metric;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.jxparallel.forge.JxmlTemplate;
import com.jxparallel.forge.JxmlTrees;
import com.jxparallel.forge.JxmlView;
import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.native2d.JXNativeNode;

/**
 * The JXForge counterpart of {@code FxmlLoadBenchmark}: builds the 20 generated form screens
 * (the same layout as the FXML ones) in {@code PASSES} passes within one JVM, sequentially on
 * the main thread. Pass 1 is cold (class loading, interpreter), later passes are warm.
 * <ul>
 *   <li>{@code interpreted}: reads and checks the JXML file on every load, then builds by
 *       reflection, as {@code FXMLLoader} reads its file on every load.</li>
 *   <li>{@code template}: the same, with each file read once and the template reused.</li>
 *   <li>{@code compiled}: the classes JXForge generated at build time, with constant hoisting.</li>
 *   <li>{@code compiled-plain}: the same without hoisting.</li>
 * </ul>
 * With {@code -Djx.forge.ready=true} each screen is also made ready to paint: native nodes
 * created, laid out at 1280x800, and the two other tabs mounted ahead, as JavaFX builds every
 * tab's content. That is the counterpart of {@code FXMLLoader.load} plus {@code Scene},
 * {@code applyCss} and {@code layout}. Emits {@code JX_METRIC key=value} lines.
 */
public final class ForgeLoadBenchmark {
    private static final int SCREENS = Integer.getInteger("jx.forge.screens", 20);
    private static final int PASSES = Integer.getInteger("jx.forge.passes", 3);
    private static final boolean READY = Boolean.getBoolean("jx.forge.ready");

    private ForgeLoadBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "compiled";
        JvmMetrics.startSampler();
        metric("screens", SCREENS);
        metric("ready_stage", READY ? 1 : 0);
        JxmlTemplate[] templates = new JxmlTemplate[SCREENS + 1];
        @SuppressWarnings("rawtypes")
        JxmlView[] views = new JxmlView[SCREENS + 1];
        List<Object> keep = new ArrayList<Object>(SCREENS);
        for (int pass = 1; pass <= PASSES; pass++) {
            keep.clear();
            long cpu = JvmMetrics.cpuNanos();
            long allocated = JvmMetrics.allocatedBytes();
            long start = System.nanoTime();
            long firstReady = 0;
            long[] shape = new long[4];
            for (int i = 1; i <= SCREENS; i++) {
                JXElement root = build(mode, i, templates, views);
                Object screen = READY ? ready(root) : root;
                keep.add(screen);
                if (i == 1) {
                    firstReady = System.nanoTime();
                }
                shape(root, shape);
            }
            long end = System.nanoTime();
            metric("pass" + pass + "_total_ns", end - start);
            metric("pass" + pass + "_first_screen_ns", firstReady - start);
            metric("pass" + pass + "_cpu_ns", JvmMetrics.cpuNanos() - cpu);
            metric("pass" + pass + "_allocated_bytes", JvmMetrics.allocatedBytes() - allocated);
            metric("pass" + pass + "_initialized_screens", shape[3]);
            metric("pass" + pass + "_nodes", shape[0]);
            metric("pass" + pass + "_spinner_value_sum", shape[1]);
            metric("pass" + pass + "_grid_row_index_sum", shape[2]);
        }
        JvmMetrics.emitProcessMetrics();
        System.exit(0);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static JXElement build(String mode, int i, JxmlTemplate[] templates, JxmlView[] views) throws Exception {
        if ("interpreted".equals(mode) || "template".equals(mode)) {
            JxmlTemplate template = templates[i];
            if (template == null || "interpreted".equals(mode)) {
                String path = String.format("/forgebench/screen%02d.jxml", i);
                InputStream in = ForgeLoadBenchmark.class.getResourceAsStream(path);
                try {
                    template = JxmlTemplate.read(in, path.substring(1));
                } finally {
                    in.close();
                }
                templates[i] = template;
            }
            // the controller named in the file, made by reflection as an interpreter must
            Object controller = Class.forName(template.getController()).getDeclaredConstructor().newInstance();
            return template.build(controller);
        }
        boolean plain = "compiled-plain".equals(mode);
        if (!plain && !"compiled".equals(mode)) {
            throw new IllegalArgumentException("unknown mode " + mode);
        }
        if (views[i] == null) {
            views[i] = ForgeBenchViews.view(i, plain);
        }
        return views[i].build(ForgeBenchViews.controller(i));
    }

    /** Native nodes laid out at window size, the other tabs mounted ahead. */
    static JXNativeNode ready(JXElement root) {
        JXNativeNode node = JXNativeNode.createBackendNode(root);
        node.layoutForBackend(1280, 800);
        mountDeferred(node);
        return node;
    }

    private static void mountDeferred(JXNativeNode node) {
        Object deferred = node.getProperty("deferredTabs");
        if (deferred instanceof JXElement[]) {
            for (JXElement tab : (JXElement[]) deferred) {
                node.retainAhead(tab);
            }
        }
        for (JXNativeNode child : node.getChildren()) {
            mountDeferred(child);
        }
    }

    /** Elements (with the deferred tabs), spinner values, grid rows, and screens whose status says Loaded. */
    static void shape(JXElement e, long[] shape) {
        shape[0] += JxmlTrees.count(e);
        walk(e, shape);
    }

    private static void walk(JXElement e, long[] shape) {
        if (e == null) {
            return;
        }
        if ("spinner".equals(e.getType())) {
            shape[1] += Long.parseLong(e.getProps().getString("value"));
        }
        Object row = e.getProps().get("row");
        if (row instanceof Integer) {
            shape[2] += (Integer) row;
        }
        if ("#text".equals(e.getType()) && "Loaded".equals(e.getProps().get("value"))) {
            shape[3]++;
        }
        for (JXElement child : e.getChildren()) {
            walk(child, shape);
        }
        Object deferred = e.getProps().get("deferredTabs");
        if (deferred instanceof JXElement[]) {
            for (JXElement d : (JXElement[]) deferred) {
                walk(d, shape);
            }
        }
    }
}
