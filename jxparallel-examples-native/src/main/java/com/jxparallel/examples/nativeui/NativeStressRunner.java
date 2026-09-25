package com.jxparallel.examples.nativeui;

import static com.jxparallel.examples.nativeui.JvmMetrics.metric;

import java.lang.management.ManagementFactory;

import com.jxparallel.core.JXParallel;
import com.jxparallel.ui.controls.JXButton;
import com.jxparallel.ui.controls.JXComboBox;
import com.jxparallel.ui.controls.JXLabel;
import com.jxparallel.ui.controls.JXTextField;
import com.jxparallel.ui.layout.JXPane;
import com.jxparallel.ui.native2d.JXWindow;

/**
 * UI stress test for the JXParallel native renderer (Skia on OpenGL). Same scenarios as
 * {@code JavaFxStressRunner}:
 * <ol>
 *   <li>Burst: label, button, combo and text field refreshed {@code REFRESH_COUNT} times each,
 *       every change pushed to the renderer tree, no wait for frames.</li>
 *   <li>Sustained: every presented frame updates all components, for {@code SUSTAINED_FRAMES}
 *       frames (vsync paced), measuring frame pacing, CPU and allocation.</li>
 * </ol>
 * Emits {@code JX_METRIC key=value} lines parsed by {@code measure-ui-stress.ps1}.
 */
public final class NativeStressRunner {
    private static final int REFRESH_COUNT = Integer.getInteger("jx.stress.refreshCount", 500);
    private static final int SUSTAINED_FRAMES = Integer.getInteger("jx.sustained.frames", 600);
    private static final long PROCESS_START = System.nanoTime();

    private final JXLabel statusLabel = new JXLabel("Ready");
    private final JXButton actionButton = new JXButton("Action");
    private final JXTextField inputField = new JXTextField();
    private final JXComboBox<String> comboBox = new JXComboBox<String>();
    private final JXPane content = new JXPane(8);
    private final JXWindow window = new JXWindow("JXParallel Stress Runner");

    private NativeStressRunner() {
        inputField.setPromptText("Type here...");
        content.add(statusLabel);
        content.add(actionButton);
        content.add(inputField);
        content.add(comboBox);
    }

    public static void main(String[] args) {
        metric("process_start_ns", PROCESS_START);
        JvmMetrics.startSampler();
        JXParallel.start();
        new NativeStressRunner().run();
    }

    private void run() {
        window.setContent(content.render());
        window.setOnFirstPaint(() -> {
            metric("first_paint_ns", System.nanoTime());
            metric("first_paint_uptime_ms", ManagementFactory.getRuntimeMXBean().getUptime());
            window.invokeLater(this::burst);
        });
        window.show();
    }

    private void push() {
        window.setContent(content.render());
    }

    private void burst() {
        long cpu = JvmMetrics.cpuNanos();
        long allocated = JvmMetrics.allocatedBytes();
        long stressStart = System.nanoTime();

        long labelStart = System.nanoTime();
        for (int i = 0; i < REFRESH_COUNT; i++) {
            statusLabel.setText("Label refresh #" + i);
            push();
        }
        long btnStart = System.nanoTime();
        for (int i = 0; i < REFRESH_COUNT; i++) {
            actionButton.setDisable(i % 2 == 0);
            actionButton.setText("Action #" + i);
            push();
        }
        actionButton.setDisable(false);
        long listStart = System.nanoTime();
        for (int i = 0; i < REFRESH_COUNT; i++) {
            replaceItems(i);
            push();
        }
        long inputStart = System.nanoTime();
        for (int i = 0; i < REFRESH_COUNT; i++) {
            inputField.setPromptText("Prompt #" + i);
            inputField.setText("Value " + i);
            push();
        }
        long stressEnd = System.nanoTime();

        metric("stress_label_ns", btnStart - labelStart);
        metric("stress_button_ns", listStart - btnStart);
        metric("stress_list_ns", inputStart - listStart);
        metric("stress_input_ns", stressEnd - inputStart);
        metric("stress_total_ns", stressEnd - stressStart);
        metric("stress_cpu_ns", JvmMetrics.cpuNanos() - cpu);
        metric("stress_allocated_bytes", JvmMetrics.allocatedBytes() - allocated);
        metric("refresh_count", REFRESH_COUNT);
        sustained();
    }

    private void sustained() {
        final long[] frames = new long[SUSTAINED_FRAMES];
        final int[] count = {0};
        final long cpu = JvmMetrics.cpuNanos();
        final long allocated = JvmMetrics.allocatedBytes();
        final long start = System.nanoTime();
        window.setOnFrame(() -> {
            int i = count[0];
            frames[i] = System.nanoTime();
            count[0] = i + 1;
            if (count[0] == SUSTAINED_FRAMES) {
                window.setOnFrame(null);
                metric("sustained_ns", frames[i] - start);
                metric("sustained_cpu_ns", JvmMetrics.cpuNanos() - cpu);
                metric("sustained_allocated_bytes", JvmMetrics.allocatedBytes() - allocated);
                JvmMetrics.frameStats(frames, count[0], frames[i] - start);
                finish();
                return;
            }
            statusLabel.setText("Frame #" + i);
            actionButton.setDisable(i % 2 == 0);
            actionButton.setText("Action #" + i);
            inputField.setText("Value " + i);
            replaceItems(i);
            push();
        });
        push();
    }

    private void replaceItems(int i) {
        comboBox.getItems().clear();
        for (int j = 0; j < 5; j++) {
            comboBox.getItems().add("Item " + i + "." + j);
        }
        comboBox.select(0);
    }

    private void finish() {
        JvmMetrics.emitProcessMetrics();
        window.dispose();
        JXParallel.shutdownNow();
        System.exit(0);
    }
}
