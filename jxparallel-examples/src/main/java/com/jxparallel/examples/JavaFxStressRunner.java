package com.jxparallel.examples;

import static com.jxparallel.examples.JvmMetrics.metric;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

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
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * JavaFX-only UI stress runner, no JXParallel dependency. Same scenarios as
 * {@code NativeStressRunner}:
 * <ol>
 *   <li>Burst: label, button, list and text field refreshed {@code REFRESH_COUNT} times each
 *       on the FX thread, no wait for pulses.</li>
 *   <li>Sustained: every pulse updates all components, for {@code SUSTAINED_FRAMES} pulses
 *       (vsync paced), measuring frame pacing, CPU and allocation.</li>
 * </ol>
 * Emits {@code JX_METRIC key=value} lines parsed by {@code measure-ui-stress.ps1}.
 */
public final class JavaFxStressRunner extends Application {
    private static final int REFRESH_COUNT = Integer.getInteger("jx.stress.refreshCount", 500);
    private static final int SUSTAINED_FRAMES = Integer.getInteger("jx.sustained.frames", 600);
    private static final long PROCESS_START = System.nanoTime();

    private Label statusLabel;
    private Button actionButton;
    private TextField inputField;
    private ListView<String> listView;

    public static void main(String[] args) {
        metric("process_start_ns", PROCESS_START);
        JvmMetrics.startSampler();
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        statusLabel = new Label("Ready");
        actionButton = new Button("Action");
        inputField = new TextField();
        inputField.setPromptText("Type here...");
        listView = new ListView<String>();
        listView.setPrefHeight(120);

        VBox root = new VBox(8, statusLabel, actionButton, inputField, listView);
        root.setPadding(new Insets(16));
        root.setPrefWidth(360);

        stage.setTitle("JavaFX Stress Runner");
        stage.setScene(new Scene(root));
        Integer monitor = Integer.getInteger("jx.monitor"); // same option as JXWindow
        if (monitor != null && monitor >= 0 && monitor < Screen.getScreens().size()) {
            Rectangle2D bounds = Screen.getScreens().get(monitor).getBounds();
            stage.setX(bounds.getMinX() + 100);
            stage.setY(bounds.getMinY() + 100);
        }
        stage.show();

        new AnimationTimer() {
            @Override
            public void handle(long now) {
                stop();
                metric("first_paint_ns", System.nanoTime());
                metric("first_paint_uptime_ms", ManagementFactory.getRuntimeMXBean().getUptime());
                Platform.runLater(JavaFxStressRunner.this::burst);
            }
        }.start();
    }

    private void burst() {
        long cpu = JvmMetrics.cpuNanos();
        long allocated = JvmMetrics.allocatedBytes();
        long stressStart = System.nanoTime();

        long labelStart = System.nanoTime();
        for (int i = 0; i < REFRESH_COUNT; i++) {
            statusLabel.setText("Label refresh #" + i);
        }
        long btnStart = System.nanoTime();
        for (int i = 0; i < REFRESH_COUNT; i++) {
            actionButton.setDisable(i % 2 == 0);
            actionButton.setText("Action #" + i);
        }
        actionButton.setDisable(false);
        long listStart = System.nanoTime();
        for (int i = 0; i < REFRESH_COUNT; i++) {
            replaceItems(i);
        }
        long inputStart = System.nanoTime();
        for (int i = 0; i < REFRESH_COUNT; i++) {
            inputField.setPromptText("Prompt #" + i);
            inputField.setText("Value " + i);
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
        final long cpu = JvmMetrics.cpuNanos();
        final long allocated = JvmMetrics.allocatedBytes();
        final long start = System.nanoTime();
        new AnimationTimer() {
            private int count;

            @Override
            public void handle(long now) {
                int i = count;
                frames[i] = System.nanoTime();
                count = i + 1;
                if (count == SUSTAINED_FRAMES) {
                    stop();
                    metric("sustained_ns", frames[i] - start);
                    metric("sustained_cpu_ns", JvmMetrics.cpuNanos() - cpu);
                    metric("sustained_allocated_bytes", JvmMetrics.allocatedBytes() - allocated);
                    JvmMetrics.frameStats(frames, count, frames[i] - start);
                    JvmMetrics.emitProcessMetrics();
                    Platform.exit();
                    return;
                }
                statusLabel.setText("Frame #" + i);
                actionButton.setDisable(i % 2 == 0);
                actionButton.setText("Action #" + i);
                inputField.setText("Value " + i);
                replaceItems(i);
            }
        }.start();
    }

    private void replaceItems(int i) {
        List<String> items = new ArrayList<String>(5);
        for (int j = 0; j < 5; j++) {
            items.add("Item " + i + "." + j);
        }
        listView.setItems(FXCollections.observableArrayList(items));
    }
}
