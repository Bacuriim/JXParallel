package com.jxparallel.examples;

import com.jxparallel.core.JXParallel;
import com.jxparallel.javafx.JXParallelFx;
import com.jxparallel.javafx.controls.JXButton;
import com.jxparallel.javafx.controls.JXTextField;
import com.jxparallel.javafx.controls.JXVisualVariant;
import javafx.application.Application;
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
                    Thread.sleep(50L);
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
        Platform.runLater(() -> {
            button.fire();
        });
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
                Thread.sleep(50L);
                return "Hello, JXParallel";
            }).thenAccept(value -> JXParallelFx.ui(() -> finish(button.node(), value)));
        });
        show(new VBox(12, input.node(), button.node(), status), "JXParallel metrics");
        Platform.runLater(() -> {
            button.node().fire();
        });
    }

    private void show(VBox root, String title) {
        root.setPadding(new Insets(24));
        root.setPrefWidth(360);
        stage.setTitle(title);
        stage.setScene(new Scene(root));
        stage.show();
        System.out.println("startup_ms=" + nanosToMillis(System.nanoTime() - processStart));
    }

    private void finish(Button button, String value) {
        status.setText(value);
        button.setDisable(false);
        long responseNanos = System.nanoTime() - clickTime;
        System.out.printf(Locale.ROOT,
                "implementation=%s,response_ms=%.3f,heap_delta_bytes=%d,process_cpu_ms=%.3f,thread_delta=%d%n",
                IMPLEMENTATION,
                nanosToMillis(responseNanos),
                usedHeap() - beforeHeap,
                nanosToMillis(processCpuNanos() - beforeCpu),
                threads.getThreadCount() - beforeThreads);
        Platform.exit();
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
