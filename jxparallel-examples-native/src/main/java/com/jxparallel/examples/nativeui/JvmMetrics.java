package com.jxparallel.examples.nativeui;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.Arrays;
import java.util.Locale;

/**
 * JVM-side metrics shared by the UI comparison runners. Emits {@code JX_METRIC key=value}
 * lines parsed by {@code scripts/measure-ui-stress.ps1}.
 */
// ponytail: identical copy in jxparallel-examples (JavaFX side has no JXParallel UI dependency);
// extract to a shared module if a third runner appears.
final class JvmMetrics {
    private static final MemoryMXBean MEMORY = ManagementFactory.getMemoryMXBean();
    private static volatile long peakHeapUsed;
    private static volatile long peakHeapCommitted;
    private static volatile long peakNonHeapUsed;
    private static volatile long peakDirectBytes;

    private JvmMetrics() {
    }

    /** Samples heap, non-heap and direct memory every 10 ms to capture real simultaneous peaks. */
    static void startSampler() {
        Thread sampler = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    sample();
                    try {
                        Thread.sleep(10L);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        }, "jx-metrics-sampler");
        sampler.setDaemon(true);
        sampler.start();
    }

    private static void sample() {
        long heap = MEMORY.getHeapMemoryUsage().getUsed();
        long committed = MEMORY.getHeapMemoryUsage().getCommitted();
        long nonHeap = MEMORY.getNonHeapMemoryUsage().getUsed();
        long direct = directBytes();
        if (heap > peakHeapUsed) peakHeapUsed = heap;
        if (committed > peakHeapCommitted) peakHeapCommitted = committed;
        if (nonHeap > peakNonHeapUsed) peakNonHeapUsed = nonHeap;
        if (direct > peakDirectBytes) peakDirectBytes = direct;
    }

    static void metric(String name, long value) {
        System.out.printf(Locale.ROOT, "JX_METRIC %s=%d%n", name, value);
        System.out.flush();
    }

    static long cpuNanos() {
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean) {
            return Math.max(0L, ((com.sun.management.OperatingSystemMXBean) bean).getProcessCpuTime());
        }
        return 0L;
    }

    /** Bytes allocated by live threads since they started (dead threads are not counted). */
    static long allocatedBytes() {
        java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        if (bean instanceof com.sun.management.ThreadMXBean) {
            long[] sizes = ((com.sun.management.ThreadMXBean) bean).getThreadAllocatedBytes(bean.getAllThreadIds());
            long total = 0L;
            for (long size : sizes) {
                total += Math.max(0L, size);
            }
            return total;
        }
        return 0L;
    }

    private static long directBytes() {
        for (BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
            if ("direct".equals(pool.getName())) {
                return pool.getMemoryUsed();
            }
        }
        return 0L;
    }

    /** Frame pacing from presented-frame timestamps. */
    static void frameStats(long[] frameNanos, int count, long elapsedNanos) {
        if (count < 2) {
            return;
        }
        long[] intervals = new long[count - 1];
        for (int i = 1; i < count; i++) {
            intervals[i - 1] = frameNanos[i] - frameNanos[i - 1];
        }
        Arrays.sort(intervals);
        long jank = 0;
        for (long interval : intervals) {
            if (interval > 25_000_000L) { // > 1.5 x 16.7 ms: a visibly dropped frame at 60 Hz
                jank++;
            }
        }
        metric("sustained_frames", count);
        metric("sustained_fps_x100", count * 100_000_000_000L / Math.max(1L, elapsedNanos));
        metric("frame_p50_us", percentile(intervals, 50) / 1000L);
        metric("frame_p95_us", percentile(intervals, 95) / 1000L);
        metric("frame_p99_us", percentile(intervals, 99) / 1000L);
        metric("frame_max_us", intervals[intervals.length - 1] / 1000L);
        metric("jank_frames", jank);
    }

    private static long percentile(long[] sorted, int p) {
        int index = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }

    /** Process-wide totals; forces a GC at the end to measure the retained live set. */
    static void emitProcessMetrics() {
        sample();
        long gcCount = 0L;
        long gcMillis = 0L;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += Math.max(0L, gc.getCollectionCount());
            gcMillis += Math.max(0L, gc.getCollectionTime());
        }
        metric("gc_count", gcCount);
        metric("gc_time_ms", gcMillis);
        metric("heap_peak_bytes", peakHeapUsed);
        metric("heap_committed_peak_bytes", peakHeapCommitted);
        metric("nonheap_peak_bytes", peakNonHeapUsed);
        metric("direct_peak_bytes", peakDirectBytes);
        metric("allocated_total_bytes", allocatedBytes());
        metric("process_cpu_total_ns", cpuNanos());
        metric("classes_loaded", ManagementFactory.getClassLoadingMXBean().getTotalLoadedClassCount());
        metric("thread_count", ManagementFactory.getThreadMXBean().getThreadCount());
        metric("thread_peak", ManagementFactory.getThreadMXBean().getPeakThreadCount());
        System.gc();
        metric("heap_live_after_gc_bytes", MEMORY.getHeapMemoryUsage().getUsed());
    }
}
