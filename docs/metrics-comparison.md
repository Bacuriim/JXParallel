# Comparison metrics

The two example applications have the same user flow, but a complete comparison must separate
measurable runtime facts from visual quality and platform-dependent behavior.

## Metrics collected by the benchmark module

`ComparisonBenchmark` measures:

| Area | Measurement |
|---|---|
| asynchronous completion | `CompletableFuture.supplyAsync` versus `JXParallel.background` |
| independent UI model | mount time for a minimal column/input/button tree |
| scheduler behavior | existing `SchedulerBenchmark` |
| generated artifacts | Maven JAR size and source line counts |

`RuntimeComparisonRunner` measures a repeatable runtime proxy for the requested aspects:

```text
implementation,iterations,total_ms,avg_response_us,process_cpu_ms,heap_delta_bytes
```

Run it after building:

```powershell
mvn -pl jxparallel-benchmarks -am package
java -cp "jxparallel-benchmarks\target\classes;jxparallel-core\target\classes" com.jxparallel.benchmarks.RuntimeComparisonRunner
```

Interpretation:

- `avg_response_us` is the average completion latency of a background operation.
- `process_cpu_ms` is process CPU time consumed during the measured workload.
- `heap_delta_bytes` is the heap difference before and after the workload, not total allocation.
  It is sensitive to garbage collection and must not be treated as retained memory.
- `total_ms` is wall-clock elapsed time for all iterations.

The first collection in this workspace is recorded in
`docs/metrics-results-2026-09-22.csv`. It showed:

| Scenario | Result |
|---|---|
| 1,000 single tasks | JXParallel used about 75% less process CPU time in the recorded run |
| 1,000 single tasks | JXParallel showed about 67% lower observed heap delta |
| 1,000 single tasks | JXParallel completed faster in the recorded run |
| 64-task burst | JXParallel showed about 61% lower observed heap delta, but higher wall latency |

This is an important result: the implementation does not automatically win every metric.
The worker configuration and workload materially affect latency. The burst scenario in the
runner is intended to measure the case where bounded parallel workers can improve total
completion time.

Run:

```text
mvn -pl jxparallel-benchmarks -am clean package
```

The JMH methods are:

```text
ComparisonBenchmark.traditionalAsyncCompletion
ComparisonBenchmark.jxParallelAsyncCompletion
ComparisonBenchmark.independentTreeMount
SchedulerBenchmark.measureTaskScheduling
```

## Metrics requiring a real JavaFX process

These must be collected by launching both applications with the same JavaFX runtime, display
server, screen scale, JVM flags, and architecture:

- process startup time
- Java heap and native memory
- JavaFX node creation time
- first rendered frame
- layout pulse duration
- animation frame consistency
- input-to-handler latency
- input-to-visible-update latency
- CPU usage during idle and interaction
- thread count and worker lifetime
- GC pauses
- application shutdown time

The development build uses Java 21/OpenJFX 21, but Java 8 x86 GUI measurements were collected
separately with the JDK at `C:\Program Files (x86)\Java\jdk1.8.0_51` and its JavaFX 8 runtime.
Those results are in `docs/metrics-java8-x86-report.md`. Re-run the procedure on the target
machine before making release or performance claims.

## Interpretation

The JXParallel application currently changes scheduling and visual defaults while still using
JavaFX nodes in its bridge controls. It is not valid to claim lower native scene-graph memory or
faster rendering until the same GUI harness measures those values on the target runtime.
