package com.jxparallel.examples;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * JavaFX-only UI stress runner — zero JXParallel dependency.
 *
 * <p>Drives four component-stress scenarios identical to
 * {@code NativeStressRunner} so the results are directly comparable:
 * <ol>
 *   <li>Label text refresh x {@code REFRESH_COUNT}.</li>
 *   <li>Button enable/disable + text update x {@code REFRESH_COUNT}.</li>
 *   <li>ListView item swap (full list replace) x {@code REFRESH_COUNT}.</li>
 *   <li>TextField text + prompt update x {@code REFRESH_COUNT}.</li>
 * </ol>
 *
 * <p>All work is performed on the JavaFX Application Thread — the same
 * execution model JavaFX enforces in real applications.
 *
 * <p>Emits {@code JX_METRIC key=value} lines on stdout parsed by
 * {@code measure-ui-stress.ps1}.
 */
public final class JavaFxStressRunner extends Application {

    private static final int REFRESH_COUNT =
            Integer.getInteger("jx.stress.refreshCount", 500);
    private static final long PROCESS_START = System.nanoTime();
    private static final Runtime RUNTIME = Runtime.getRuntime();
    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();

    // UI nodes — set during start()
    private Label statusLabel;
    private Button actionButton;
    private TextField inputField;
    private ListView<String> listView;

    // Baseline measurements — captured after the FX toolkit starts
    private long beforeHeap;
    private long beforeCpu;

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    public static void main(String[] args) {
        metric("process_start_ns", PROCESS_START);
        // No JXParallel.start() — pure JavaFX only
        launch(args);
    }

    // -----------------------------------------------------------------------
    // JavaFX lifecycle
    // -----------------------------------------------------------------------

    @Override
    public void start(Stage stage) {
        beforeHeap = usedHeap();
        beforeCpu  = processCpuNanos();

        statusLabel  = new Label("Ready");
        actionButton = new Button("Action");
        inputField   = new TextField();
        inputField.setPromptText("Type here...");
        listView = new ListView<String>();
        listView.setPrefHeight(120);

        VBox root = new VBox(8, statusLabel, actionButton, inputField, listView);
        root.setPadding(new Insets(16));
        root.setPrefWidth(360);

        stage.setTitle("JavaFX Stress Runner");
        stage.setScene(new Scene(root));
        stage.show();

        // Report first rendered frame, then kick off the stress loop
        new AnimationTimer() {
            private boolean done;
            @Override
            public void handle(long now) {
                if (!done) {
                    done = true;
                    stop();
                    metric("first_paint_ns", System.nanoTime());
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            runStress();
                        }
                    });
                }
            }
        }.start();
    }

    // -----------------------------------------------------------------------
    // Stress scenarios
    // -----------------------------------------------------------------------

    private void runStress() {
        long stressStart = System.nanoTime();

        // 1. Label refresh ------------------------------------------------
        long labelStart = System.nanoTime();
        for (int i = 0; i < REFRESH_COUNT; i++) {
            statusLabel.setText("Label refresh #" + i);
        }
        long labelEnd = System.nanoTime();

        // 2. Button enable/disable + text ---------------------------------
        long btnStart = System.nanoTime();
        for (int i = 0; i < REFRESH_COUNT; i++) {
            actionButton.setDisable(i % 2 == 0);
            actionButton.setText("Action #" + i);
        }
        actionButton.setDisable(false);
        long btnEnd = System.nanoTime();

        // 3. ListView full-list swap --------------------------------------
        long listStart = System.nanoTime();
        for (int i = 0; i < REFRESH_COUNT; i++) {
            List<String> items = new ArrayList<String>(5);
            for (int j = 0; j < 5; j++) {
                items.add("Item " + i + "." + j);
            }
            listView.setItems(FXCollections.observableArrayList(items));
        }
        long listEnd = System.nanoTime();

        // 4. TextField text + prompt refresh ------------------------------
        long inputStart = System.nanoTime();
        for (int i = 0; i < REFRESH_COUNT; i++) {
            inputField.setPromptText("Prompt #" + i);
            inputField.setText("Value " + i);
        }
        long inputEnd = System.nanoTime();

        long stressEnd = System.nanoTime();

        // Emit metrics
        metric("stress_label_ns",  labelEnd  - labelStart);
        metric("stress_button_ns", btnEnd    - btnStart);
        metric("stress_list_ns",   listEnd   - listStart);
        metric("stress_input_ns",  inputEnd  - inputStart);
        metric("stress_total_ns",  stressEnd - stressStart);
        metric("heap_delta_bytes", usedHeap() - beforeHeap);
        metric("process_cpu_ns",   Math.max(0L, processCpuNanos() - beforeCpu));
        metric("thread_count",     (long) THREADS.getThreadCount());
        metric("refresh_count",    (long) REFRESH_COUNT);

        Platform.exit();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

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
            long v = ((com.sun.management.OperatingSystemMXBean) bean).getProcessCpuTime();
            return v < 0L ? 0L : v;
        }
        return 0L;
    }
}
