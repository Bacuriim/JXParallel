package com.jxparallel.stress;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Locale;

/**
 * Swing-only UI stress baseline runner.
 *
 * <p>No JavaFX. No JXParallel. Uses only {@code java.awt} and {@code javax.swing}
 * from the standard JDK.
 *
 * <p>Drives the same four scenarios as {@code NativeStressRunner}:
 * <ol>
 *   <li>JLabel text refresh x {@code REFRESH_COUNT}.</li>
 *   <li>JButton enable/disable + text update x {@code REFRESH_COUNT}.</li>
 *   <li>JList item refresh (swap entire model) x {@code REFRESH_COUNT}.</li>
 *   <li>JTextField prompt + text refresh x {@code REFRESH_COUNT}.</li>
 * </ol>
 *
 * <p>Emits {@code JX_METRIC key=value} lines on stdout compatible with the
 * {@code measure-ui-stress.ps1} harness.
 */
public final class SwingStressRunner {

    private static final int REFRESH_COUNT = Integer.getInteger("jx.stress.refreshCount", 500);
    private static final long PROCESS_START = System.nanoTime();
    private static final Runtime RUNTIME = Runtime.getRuntime();
    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();

    private SwingStressRunner() {
    }

    public static void main(String[] args) throws Exception {
        metric("process_start_ns", PROCESS_START);

        final long beforeHeap = usedHeap();
        final long beforeCpu = processCpuNanos();

        // Build UI on the Event Dispatch Thread
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                buildAndRun(beforeHeap, beforeCpu);
            }
        });
    }

    private static void buildAndRun(final long beforeHeap, final long beforeCpu) {
        final JLabel statusLabel = new JLabel("Ready");
        final JButton actionButton = new JButton("Action");
        final JTextField inputField = new JTextField("Type here...");
        final DefaultListModel<String> listModel = new DefaultListModel<String>();
        final JList<String> listView = new JList<String>(listModel);

        JPanel panel = new JPanel(new GridLayout(4, 1, 4, 4));
        panel.add(statusLabel);
        panel.add(actionButton);
        panel.add(inputField);
        panel.add(new JScrollPane(listView));

        JFrame frame = new JFrame("Swing Stress Runner");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.CENTER);
        frame.setSize(360, 260);
        frame.setVisible(true);

        // Measure first paint after the frame becomes visible
        metric("first_paint_ns", System.nanoTime());

        long stressStart = System.nanoTime();

        // --- JLabel refresh ---
        long labelStart = System.nanoTime();
        for (int i = 0; i < REFRESH_COUNT; i++) {
            statusLabel.setText("Label refresh #" + i);
        }
        long labelEnd = System.nanoTime();

        // --- JButton toggle ---
        long btnStart = System.nanoTime();
        for (int i = 0; i < REFRESH_COUNT; i++) {
            actionButton.setEnabled(i % 2 != 0);
            actionButton.setText("Action #" + i);
        }
        actionButton.setEnabled(true);
        long btnEnd = System.nanoTime();

        // --- JList refresh ---
        long listStart = System.nanoTime();
        for (int i = 0; i < REFRESH_COUNT; i++) {
            listModel.clear();
            for (int j = 0; j < 5; j++) {
                listModel.addElement("Item " + i + "." + j);
            }
        }
        long listEnd = System.nanoTime();

        // --- JTextField refresh ---
        long inputStart = System.nanoTime();
        for (int i = 0; i < REFRESH_COUNT; i++) {
            inputField.setText("Value " + i);
        }
        long inputEnd = System.nanoTime();

        long stressEnd = System.nanoTime();

        metric("stress_label_ns",  labelEnd  - labelStart);
        metric("stress_button_ns", btnEnd    - btnStart);
        metric("stress_list_ns",   listEnd   - listStart);
        metric("stress_input_ns",  inputEnd  - inputStart);
        metric("stress_total_ns",  stressEnd - stressStart);
        metric("heap_delta_bytes", usedHeap() - beforeHeap);
        metric("process_cpu_ns",   Math.max(0L, processCpuNanos() - beforeCpu));
        metric("thread_count",     (long) THREADS.getThreadCount());
        metric("refresh_count",    (long) REFRESH_COUNT);

        frame.dispose();
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
