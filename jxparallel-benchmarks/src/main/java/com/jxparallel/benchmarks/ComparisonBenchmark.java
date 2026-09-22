package com.jxparallel.benchmarks;

import com.jxparallel.core.JXParallel;
import com.jxparallel.core.JXParallelConfig;
import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXNode;
import com.jxparallel.ui.controls.JXControls;
import com.jxparallel.ui.layout.JXLayouts;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.util.concurrent.CompletableFuture;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
public class ComparisonBenchmark {
    private JXElement tree;

    @Setup
    public void setup() {
        JXParallel.applyConfiguration(JXParallelConfig.builder()
                .minThreads(1)
                .maxThreads(2)
                .queueCapacity(64)
                .build());
        tree = JXLayouts.column(8,
                JXControls.input("", "Name"),
                JXControls.button("Continue", null));
    }

    @Benchmark
    public int traditionalAsyncCompletion() {
        return CompletableFuture.supplyAsync(() -> 42).join();
    }

    @Benchmark
    public int jxParallelAsyncCompletion() {
        return JXParallel.background(() -> 42).join();
    }

    @Benchmark
    public JXNode independentTreeMount() {
        return JXNode.mount(tree);
    }

    @TearDown
    public void tearDown() {
        JXParallel.shutdownNow();
    }
}
