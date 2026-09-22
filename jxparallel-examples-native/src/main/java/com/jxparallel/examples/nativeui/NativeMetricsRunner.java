package com.jxparallel.examples.nativeui;

import com.jxparallel.core.JXParallel;
import com.jxparallel.ui.controls.JXButton;
import com.jxparallel.ui.controls.JXLabel;
import com.jxparallel.ui.controls.JXTextField;
import com.jxparallel.ui.layout.JXPane;
import com.jxparallel.ui.native2d.JXWindow;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Locale;

public final class NativeMetricsRunner {
    private static final long WORK_MILLIS = 350L;
    private static final long PROCESS_START = System.nanoTime();
    private static final Runtime RUNTIME = Runtime.getRuntime();
    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();

    private NativeMetricsRunner() {
    }

    public static void main(String[] args) {
        metric("process_start_ns", PROCESS_START);
        JXParallel.start();
        final long beforeHeap = usedHeap();
        final long beforeCpu = processCpuNanos();
        final int beforeThreads = THREADS.getThreadCount();
        final JXTextField input = new JXTextField();
        input.setPromptText("Your name");
        final JXButton button = new JXButton("Continue");
        final JXLabel status = new JXLabel("Ready");
        final JXPane content = new JXPane(12);
        content.add(input);
        content.add(button);
        content.add(status);

        final JXWindow window = new JXWindow("JXParallel metrics");
        window.setContent(content.render());
        window.setOnFirstPaint(new Runnable() {
            @Override
            public void run() {
                metric("first_paint_ns", System.nanoTime());
                window.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        button.fire();
                    }
                });
            }
        });
        button.setOnAction(new Runnable() {
            @Override
            public void run() {
                final long interactionStart = System.nanoTime();
                button.setDisable(true);
                JXParallel.background(() -> {
                    Thread.sleep(WORK_MILLIS);
                    return "Hello, JXParallel";
                }).whenComplete((value, error) -> {
                    long interactionEnd = System.nanoTime();
                    metric("interaction_start_ns", interactionStart);
                    metric("interaction_end_ns", interactionEnd);
                    metric("heap_delta_bytes", usedHeap() - beforeHeap);
                    metric("process_cpu_ns", Math.max(0L, processCpuNanos() - beforeCpu));
                    metric("thread_delta", THREADS.getThreadCount() - beforeThreads);
                    window.dispose();
                    JXParallel.shutdownNow();
                    System.exit(error == null ? 0 : 1);
                });
            }
        });
        window.show();
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
