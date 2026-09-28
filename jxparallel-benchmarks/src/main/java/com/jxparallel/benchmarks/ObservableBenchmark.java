package com.jxparallel.benchmarks;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import com.jxparallel.properties.JXObservableList;
import com.jxparallel.properties.JXProperty;
import com.jxparallel.ui.JXState;

/** Cost of a change plus one listener call, single thread (the common UI case). */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class ObservableBenchmark {
    private JXProperty<Integer> property;
    private JXState<Integer> state;
    private JXObservableList<Integer> list;
    private int counter;

    @Setup
    public void setup(Blackhole blackhole) {
        property = new JXProperty<Integer>(0);
        property.addListener(event -> blackhole.consume(event));
        state = new JXState<Integer>(0);
        state.subscribe(blackhole::consume);
        list = new JXObservableList<Integer>();
        list.addListener(event -> blackhole.consume(event));
    }

    @Benchmark
    public void propertySet() {
        property.set(++counter);
    }

    @Benchmark
    public void stateSet() {
        state.set(++counter);
    }

    @Benchmark
    public void listAddThenRemove() {
        list.add(++counter);
        list.remove(0);
    }
}
