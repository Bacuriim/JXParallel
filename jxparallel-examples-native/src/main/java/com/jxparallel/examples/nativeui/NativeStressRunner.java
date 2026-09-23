package com.jxparallel.examples.nativeui;

import com.jxparallel.core.JXParallel;
import com.jxparallel.ui.controls.JXButton;
import com.jxparallel.ui.controls.JXComboBox;
import com.jxparallel.ui.controls.JXLabel;
import com.jxparallel.ui.controls.JXTextField;
import com.jxparallel.ui.layout.JXPane;
import com.jxparallel.ui.native2d.JXWindow;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * UI stress test for JXParallel native renderer: rapidly refreshes components
 * (JXLabel, JXButton, JXComboBox, JXTextField) to simulate real usability load
 * and measure CPU, heap, and timing.
 *
 * <p>Emits {@code JX_METRIC key=value} lines on stdout that the
 * measure-ui-stress.ps1 harness parses.
 *
 * <p>Scenarios driven in sequence (same as JavaFxStressRunner):
 * <ol>
 *   <li>JXLabel text refresh x {@code REFRESH_COUNT}.</li>
 *   <li>JXButton enable/disable toggle x {@code REFRESH_COUNT}.</li>
 *   <li>JXComboBox item refresh x {@code REFRESH_COUNT} (swap entire list).</li>
 *   <li>JXTextField prompt refresh x {@code REFRESH_COUNT}.</li>
 * </ol>
 */
public final class NativeStressRunner {

    private static final int REFRESH_COUNT = Integer.getInteger("jx.stress.refreshCount", 500);
    private static final long PROCESS_START = System.nanoTime();
    private static final Runtime RUNTIME = Runtime.getRuntime();
    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();

    private NativeStressRunner() {
    }

    public static void main(String[] args) {
        metric("process_start_ns", PROCESS_START);
        JXParallel.start();

        final long beforeHeap = usedHeap();
        final long beforeCpu = processCpuNanos();

        final JXLabel statusLabel = new JXLabel("Ready");
        final JXButton actionButton = new JXButton("Action");
        final JXTextField inputField = new JXTextField();
        inputField.setPromptText("Type here...");
        final JXComboBox<String> comboBox = new JXComboBox<String>();

        final JXPane content = new JXPane(8);
        content.add(statusLabel);
        content.add(actionButton);
        content.add(inputField);
        content.add(comboBox);

        final JXWindow window = new JXWindow("JXParallel Stress Runner");
        window.setContent(content.render());

        window.setOnFirstPaint(new Runnable() {
            @Override
            public void run() {
                metric("first_paint_ns", System.nanoTime());
                window.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        runStress(window, statusLabel, actionButton, inputField,
                                comboBox, beforeHeap, beforeCpu);
                    }
                });
            }
        });

        window.show();
    }

    private static void runStress(JXWindow window,
                                  JXLabel statusLabel,
                                  JXButton actionButton,
                                  JXTextField inputField,
                                  JXComboBox<String> comboBox,
                                  long beforeHeap,
                                  long beforeCpu) {
        long stressStart = System.nanoTime();

        // --- Label refresh ---
        long labelStart = System.nanoTime();
        for (int i = 0; i < REFRESH_COUNT; i++) {
            statusLabel.setText("Label refresh #" + i);
            window.requestRender();
        }
        long labelEnd = System.nanoTime();

        // --- Button toggle ---
        long btnStart = System.nanoTime();
        for (int i = 0; i < REFRESH_COUNT; i++) {
            actionButton.setDisable(i % 2 == 0);
            actionButton.setText("Action #" + i);
            window.requestRender();
        }
        actionButton.setDisable(false);
        long btnEnd = System.nanoTime();

        // --- ComboBox refresh ---
        long listStart = System.nanoTime();
        for (int i = 0; i < REFRESH_COUNT; i++) {
            comboBox.getItems().clear();
            for (int j = 0; j < 5; j++) {
                comboBox.getItems().add("Item " + i + "." + j);
            }
            comboBox.select(0);
            window.requestRender();
        }
        long listEnd = System.nanoTime();

        // --- TextField prompt refresh ---
        long inputStart = System.nanoTime();
        for (int i = 0; i < REFRESH_COUNT; i++) {
            inputField.setPromptText("Prompt #" + i);
            inputField.setText("Value " + i);
            window.requestRender();
        }
        long inputEnd = System.nanoTime();

        long stressEnd = System.nanoTime();

        metric("stress_label_ns", labelEnd - labelStart);
        metric("stress_button_ns", btnEnd - btnStart);
        metric("stress_list_ns", listEnd - listStart);
        metric("stress_input_ns", inputEnd - inputStart);
        metric("stress_total_ns", stressEnd - stressStart);
        metric("heap_delta_bytes", usedHeap() - beforeHeap);
        metric("process_cpu_ns", Math.max(0L, processCpuNanos() - beforeCpu));
        metric("thread_count", (long) THREADS.getThreadCount());
        metric("refresh_count", (long) REFRESH_COUNT);

        window.dispose();
        JXParallel.shutdownNow();
        System.exit(0);
    }

    private static void metric(String name, long value) {
        System.out.printf(Locale.ROOT, "JX_METRIC %s=%d%n", name, value);
        System.out.flush();
    }

    private static long usedHeap() {
        return RUNTIME.totalMemory() - RUNTIME.freeMemory();
    }

    private static long processCpuNanos() {
        java.lang.management.OperatingSystemMXBean bean =
                ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean) {
            long value = ((com.sun.management.OperatingSystemMXBean) bean).getProcessCpuTime();
            return value < 0L ? 0L : value;
        }
        return 0L;
    }
}
