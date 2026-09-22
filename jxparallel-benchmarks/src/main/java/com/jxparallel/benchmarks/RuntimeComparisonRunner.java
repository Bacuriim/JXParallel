package com.jxparallel.benchmarks;

import com.jxparallel.core.JXParallel;
import com.jxparallel.core.JXParallelConfig;
import com.sun.management.OperatingSystemMXBean;

import java.lang.management.ManagementFactory;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class RuntimeComparisonRunner {
    private static final int WARMUP = 100;
    private static final int ITERATIONS = 1000;
    private static final int BURST_TASKS = 64;

    private RuntimeComparisonRunner() {
    }

    public static void main(String[] args) {
        warmup();
        JXParallel.applyConfiguration(JXParallelConfig.builder()
                .minThreads(1)
                .maxThreads(2)
                .queueCapacity(ITERATIONS + 16)
                .build());

        Result traditional = measure("traditional", new Workload() {
            @Override
            public void run() {
                CompletableFuture.supplyAsync(() -> 42).join();
            }
        });
        Result jxparallel = measure("jxparallel", new Workload() {
            @Override
            public void run() {
                JXParallel.background(() -> 42).join();
            }
        });
        JXParallel.applyConfiguration(JXParallelConfig.builder()
                .minThreads(8)
                .maxThreads(8)
                .queueCapacity(BURST_TASKS + 16)
                .build());
        Result traditionalBurst = measureBurst("traditional_burst", false);
        Result jxparallelBurst = measureBurst("jxparallel_burst", true);

        System.out.println("implementation,iterations,total_ms,avg_response_us,process_cpu_ms,heap_delta_bytes");
        traditional.print();
        jxparallel.print();
        traditionalBurst.print();
        jxparallelBurst.print();
        JXParallel.shutdownNow();
    }

    private static void warmup() {
        for (int i = 0; i < WARMUP; i++) {
            CompletableFuture.supplyAsync(() -> 1).join();
        }
    }

    private static Result measure(String name, Workload workload) {
        Runtime runtime = Runtime.getRuntime();
        System.gc();
        long beforeMemory = usedMemory(runtime);
        long beforeCpu = processCpuNanos();
        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            workload.run();
        }
        long elapsed = System.nanoTime() - start;
        long afterCpu = processCpuNanos();
        long afterMemory = usedMemory(runtime);
        return new Result(name, ITERATIONS, elapsed, afterCpu - beforeCpu, afterMemory - beforeMemory);
    }

    private static Result measureBurst(String name, boolean useJxParallel) {
        Runtime runtime = Runtime.getRuntime();
        System.gc();
        long beforeMemory = usedMemory(runtime);
        long beforeCpu = processCpuNanos();
        long start = System.nanoTime();
        CompletableFuture<?>[] tasks = new CompletableFuture<?>[BURST_TASKS];
        for (int i = 0; i < BURST_TASKS; i++) {
            if (useJxParallel) {
                tasks[i] = JXParallel.background(() -> {
                    Thread.sleep(5L);
                    return 42;
                });
            } else {
                tasks[i] = CompletableFuture.supplyAsync(() -> {
                    try {
                        Thread.sleep(5L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                    return 42;
                });
            }
        }
        CompletableFuture.allOf(tasks).join();
        long elapsed = System.nanoTime() - start;
        long afterCpu = processCpuNanos();
        long afterMemory = usedMemory(runtime);
        return new Result(name, BURST_TASKS, elapsed, afterCpu - beforeCpu, afterMemory - beforeMemory);
    }

    private static long usedMemory(Runtime runtime) {
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static long processCpuNanos() {
        java.lang.management.OperatingSystemMXBean bean =
                ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof OperatingSystemMXBean) {
            long value = ((OperatingSystemMXBean) bean).getProcessCpuTime();
            return value < 0 ? 0 : value;
        }
        return 0;
    }

    private interface Workload {
        void run();
    }

    private static final class Result {
        private final String name;
        private final int iterations;
        private final long elapsedNanos;
        private final long cpuNanos;
        private final long memoryDelta;

        private Result(String name, int iterations, long elapsedNanos, long cpuNanos, long memoryDelta) {
            this.name = name;
            this.iterations = iterations;
            this.elapsedNanos = elapsedNanos;
            this.cpuNanos = cpuNanos;
            this.memoryDelta = memoryDelta;
        }

        private void print() {
            double totalMillis = elapsedNanos / 1_000_000.0;
            double averageMicros = elapsedNanos / (double) iterations / 1_000.0;
            double cpuMillis = cpuNanos / 1_000_000.0;
            System.out.printf(Locale.ROOT, "%s,%d,%.3f,%.3f,%.3f,%d%n",
                    name, iterations, totalMillis, averageMicros, cpuMillis, memoryDelta);
        }
    }
}
