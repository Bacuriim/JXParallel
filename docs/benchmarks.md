# Benchmark baseline

The `jxparallel-benchmarks` module uses JMH. Results must be collected on a fixed machine and
reported with the Java version, JavaFX version where applicable, operating system, and JVM flags.

The initial benchmark measures end-to-end scheduler submission and completion:

```text
SchedulerBenchmark.measureTaskScheduling
```

This is a baseline, not proof that JXParallel is faster than JavaFX. Component claims require
separate measurements for creation time, allocations, layout, interaction, and retained memory.

Run the benchmark module with:

```text
mvn -pl jxparallel-benchmarks -am clean package
```

The benchmark intentionally uses a small bounded configuration so queue and worker behavior are
repeatable. Production configuration must not be inferred from this benchmark.
