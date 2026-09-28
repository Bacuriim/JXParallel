package com.jxparallel.benchmarks;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.controls.JXControls;
import com.jxparallel.ui.layout.JXLayouts;
import com.jxparallel.ui.native2d.JXNativeNode;

/**
 * One frame of a 1001-node screen (250 rows of label, button, input, checkbox): mounting from
 * scratch versus updating in place after one label changed, and after nothing changed (memoized
 * render). scripts/check-jmh.py fails CI when the ratios drop.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class ReconcileBenchmark {
    private static final int ROWS = 250;
    private JXElement[] rows;
    private JXElement screenA;
    private JXElement screenB;
    private JXNativeNode mounted;
    private boolean toggle;

    @Setup
    public void setup() {
        rows = new JXElement[ROWS];
        for (int i = 0; i < ROWS; i++) {
            rows[i] = row(i, "Row " + i);
        }
        screenA = JXLayouts.column(4, rows.clone());
        JXElement[] changed = rows.clone();
        changed[ROWS / 2] = row(ROWS / 2, "Changed");
        screenB = JXLayouts.column(4, changed);
        mounted = JXNativeNode.createBackendNode(screenA);
        mounted.layoutForBackend(1280, 20000);
    }

    private static JXElement row(int i, String label) {
        return JXLayouts.row(8, JXElement.text(label), JXControls.button("Edit", null),
                JXControls.input("value " + i, "Value"), JXControls.checkbox("On", i % 2 == 0));
    }

    @Benchmark
    public JXNativeNode mountFresh() {
        JXNativeNode node = JXNativeNode.createBackendNode(toggle() ? screenA : screenB);
        node.layoutForBackend(1280, 20000);
        return node;
    }

    @Benchmark
    public JXNativeNode reconcileOneLabel() {
        mounted.reconcile(toggle() ? screenA : screenB);
        mounted.layoutForBackend(1280, 20000);
        return mounted;
    }

    @Benchmark
    public JXNativeNode reconcileUnchanged() {
        mounted.reconcile(screenA);
        mounted.layoutForBackend(1280, 20000);
        return mounted;
    }

    private boolean toggle() {
        toggle = !toggle;
        return toggle;
    }
}
