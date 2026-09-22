package com.jxparallel.examples;

import com.jxparallel.core.JXParallel;
import com.jxparallel.javafx.JXParallelFx;
import com.jxparallel.javafx.controls.JXButton;
import com.jxparallel.javafx.controls.JXTextField;
import com.jxparallel.javafx.controls.JXVisualVariant;
import javafx.application.Application;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Locale;

public final class JavaFxMetricsRunner extends Application {
    private static final long WORK_MILLIS = 350L;
    private static final String IMPLEMENTATION = System.getProperty("jx.metrics.implementation", "traditional");
    private final Runtime runtime = Runtime.getRuntime();
    private final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
    private static long processStart;
    private long beforeHeap;
    private long beforeCpu;
    private long beforeThreads;
    private long clickTime;
    private Label status;
    private Stage stage;

    public static void main(String[] args) {
        processStart = System.nanoTime();
        metric("process_start_ns", processStart);
        if ("jxparallel".equalsIgnoreCase(IMPLEMENTATION)) {
            JXParallel.start();
        }
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;
        beforeHeap = usedHeap();
        beforeCpu = processCpuNanos();
        beforeThreads = threads.getThreadCount();

        if ("jxparallel".equalsIgnoreCase(IMPLEMENTATION)) {
            startJxParallel();
        } else {
            startTraditional();
        }
    }

    private void startTraditional() {
        TextField input = new TextField();
        input.setPromptText("Your name");
        Button button = new Button("Continue");
        status = new Label("Ready");
        button.setOnAction(event -> {
            clickTime = System.nanoTime();
            button.setDisable(true);
            status.setText("Loading...");
            Thread worker = new Thread(() -> {
                try {
                    Thread.sleep(WORK_MILLIS);
                    Platform.runLater(() -> finish(button, "Hello, JavaFX"));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    Platform.runLater(() -> finish(button, "Interrupted"));
                }
            }, "metrics-traditional-worker");
            worker.setDaemon(true);
            worker.start();
        });
        show(new VBox(12, input, button, status), "JavaFX metrics");
        reportFirstFrameAndRun(button);
    }

    private void startJxParallel() {
        JXTextField input = new JXTextField();
        input.setPromptText("Your name");
        JXButton button = new JXButton("Continue");
        button.setVariant(JXVisualVariant.PRIMARY);
        status = new Label("Ready");
        button.setOnAction(event -> {
            clickTime = System.nanoTime();
            button.setDisable(true);
            status.setText("Loading...");
            JXParallel.background(() -> {
                Thread.sleep(WORK_MILLIS);
                return "Hello, JXParallel";
            }).thenAccept(value -> JXParallelFx.ui(() -> finish(button.node(), value)));
        });
        show(new VBox(12, input.node(), button.node(), status), "JXParallel metrics");
        reportFirstFrameAndRun(button.node());
    }

    private void show(VBox root, String title) {
        root.setPadding(new Insets(24));
        root.setPrefWidth(360);
        stage.setTitle(title);
        stage.setScene(new Scene(root));
        stage.show();
    }

    private void reportFirstFrameAndRun(Button button) {
        new AnimationTimer() {
            private boolean reported;

            @Override
            public void handle(long now) {
                if (!reported) {
                    reported = true;
                    stop();
                    metric("first_paint_ns", System.nanoTime());
                    Platform.runLater(button::fire);
                }
            }
        }.start();
    }

    private void finish(Button button, String value) {
        status.setText(value);
        button.setDisable(false);
        long responseNanos = System.nanoTime() - clickTime;
        metric("interaction_start_ns", clickTime);
        metric("interaction_end_ns", System.nanoTime());
        metric("heap_delta_bytes", usedHeap() - beforeHeap);
        metric("process_cpu_ns", Math.max(0L, processCpuNanos() - beforeCpu));
        metric("thread_delta", threads.getThreadCount() - beforeThreads);
        Platform.exit();
    }

    private static void metric(String name, long value) {
        System.out.printf(Locale.ROOT, "JX_METRIC %s=%d%n", name, value);
        System.out.flush();
    }

    private long usedHeap() {
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private long processCpuNanos() {
        java.lang.management.OperatingSystemMXBean bean =
                ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean) {
            long value = ((com.sun.management.OperatingSystemMXBean) bean).getProcessCpuTime();
            return value < 0 ? 0 : value;
        }
        return 0;
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }
}
