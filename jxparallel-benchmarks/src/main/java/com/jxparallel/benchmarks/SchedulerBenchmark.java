package com.jxparallel.benchmarks;

import com.jxparallel.core.JXParallel;
import com.jxparallel.core.JXParallelConfig;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
public class SchedulerBenchmark {
    @Setup
    public void setup() {
        JXParallel.applyConfiguration(JXParallelConfig.builder()
                .minThreads(1)
                .maxThreads(2)
                .queueCapacity(32)
                .build());
    }

    @Benchmark
    public long measureTaskScheduling() {
        return JXParallel.background(() -> 42L).join();
    }

    @TearDown
    public void tearDown() {
        JXParallel.shutdownNow();
    }
}
