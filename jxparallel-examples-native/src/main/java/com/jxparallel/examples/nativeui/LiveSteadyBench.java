package com.jxparallel.examples.nativeui;

import java.util.Arrays;

import com.jxparallel.examples.nativeui.components.ComponentController;
import com.jxparallel.forge.JxmlLive;
import com.jxparallel.forge.JxmlView;
import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.native2d.JXNativeNode;

/**
 * Research (live bindings): the update of one component in steady state, in this JVM, compiled and live side by side. The
 * two screens are mounted from the same controller and updated in turns, {@code rounds} times, the value sets alternating;
 * each round times the pieces of both ways: compiled (build the element tree, reconcile it, lay out) and live (refresh, lay
 * out), and the bytes the thread allocated in each. {@code ComponentBench} measures the 8th pass of a fresh JVM, the way the
 * earlier series did; this is the same work after the JIT has settled (the first half of the rounds is not counted), and
 * answers what the live path costs once it is compiled. Prints one {@code STEADY} line of medians (microseconds, bytes):
 *
 * <pre>
 * STEADY name,n,rounds,c_build,c_reconcile,c_layout,c_total,l_refresh,l_layout,l_total,c_alloc,l_alloc,fallbacks
 * </pre>
 *
 * {@code java ... LiveSteadyBench Separator,Slider [rounds]}
 */
public final class LiveSteadyBench {
    private LiveSteadyBench() {
    }

    public static void main(String[] args) throws Exception {
        int rounds = args.length > 1 ? Integer.parseInt(args[1]) : 400;
        for (String name : args[0].split(",")) {
            run(name, rounds);
        }
        System.exit(0);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void run(String name, int rounds) {
        ComponentController c = (ComponentController) ComponentBenchViews.controller(name);
        JxmlView view = ComponentBenchViews.view(name);
        c.setPhase(0);
        JXNativeNode compiled = JXNativeNode.createBackendNode(view.build(c));
        compiled.layoutForBackend(ComponentBench.WIDTH, ComponentBench.HEIGHT);
        JxmlLive live = view.live(c);
        JXNativeNode liveNode = JXNativeNode.createBackendNode(live.element());
        liveNode.layoutForBackend(ComponentBench.WIDTH, ComponentBench.HEIGHT);
        if (!live.attach(liveNode)) {
            throw new IllegalStateException(name + ": " + live.problem());
        }
        int counted = rounds - rounds / 2;
        long[] build = new long[counted];
        long[] reconcile = new long[counted];
        long[] layoutC = new long[counted];
        long[] refresh = new long[counted];
        long[] layoutL = new long[counted];
        long[] allocC = new long[counted];
        long[] allocL = new long[counted];
        for (int i = 0; i < rounds; i++) {
            int phase = (i + 1) & 1;
            c.setPhase(phase);
            long a0 = JvmMetrics.allocatedBytes();
            long t0 = System.nanoTime();
            JXElement next = view.build(c);
            long t1 = System.nanoTime();
            compiled.reconcile(next);
            long t2 = System.nanoTime();
            compiled.layoutForBackend(ComponentBench.WIDTH, ComponentBench.HEIGHT);
            long t3 = System.nanoTime();
            long a1 = JvmMetrics.allocatedBytes();
            long u0 = System.nanoTime();
            live.refresh();
            long u1 = System.nanoTime();
            liveNode.layoutForBackend(ComponentBench.WIDTH, ComponentBench.HEIGHT);
            long u2 = System.nanoTime();
            long a2 = JvmMetrics.allocatedBytes();
            if (i >= rounds / 2) {
                int k = i - rounds / 2;
                build[k] = t1 - t0;
                reconcile[k] = t2 - t1;
                layoutC[k] = t3 - t2;
                refresh[k] = u1 - u0;
                layoutL[k] = u2 - u1;
                allocC[k] = a1 - a0;
                allocL[k] = a2 - a1;
            }
        }
        long cTotal = median(sum(build, reconcile, layoutC));
        long lTotal = median(sum(refresh, layoutL));
        System.out.println(String.format(java.util.Locale.ROOT, "STEADY %s,%d,%d,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%d,%d,%d", name, c.getCount(), counted,
                median(build) / 1e3, median(reconcile) / 1e3, median(layoutC) / 1e3, cTotal / 1e3, median(refresh) / 1e3,
                median(layoutL) / 1e3, lTotal / 1e3, median(allocC), median(allocL), live.fallbacks()));
    }

    private static long[] sum(long[]... parts) {
        long[] out = new long[parts[0].length];
        for (long[] part : parts) {
            for (int i = 0; i < out.length; i++) {
                out[i] += part[i];
            }
        }
        return out;
    }

    private static long median(long[] values) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }
}
