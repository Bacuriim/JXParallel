# JXParallel configuration

## Configuration precedence

The config precedence is defined as follows:

1. built-in defaults
2. `jx-parallel.config` in the application root
3. environment variables
4. Java system properties
5. programmatic configuration via `JXParallelConfig.Builder`

## Example configuration

```properties
threads.min=2
threads.max=8
threads.auto-scale=true
threads.daemon=true
threads.priority=5
queue.capacity=500
queue.policy=BLOCK
jxparallel.mode=BALANCED
fxml.cache.enabled=true
fxml.cache.max-size=100
fxml.cache.ttl=30m
fxml.cache.strategy=LRU
metrics.enabled=false
debug=false
```

`threads.daemon` controls whether worker threads prevent JVM termination. The default is `true`.
`threads.priority` is clamped to the Java thread priority range.

`queue.capacity` limits queued work in addition to the configured worker limit. The policies are:

- `BLOCK`: wait for an available worker or queue slot
- `REJECT`: complete the new task exceptionally with `RejectedExecutionException`
- `DISCARD`: cancel the new task without executing it
- `DISCARD_OLDEST`: cancel the oldest queued task and accept the new task when possible

`Task.withTimeout(...)` is enforced by the runtime. Timeout completion is exceptional and the
running thread is interrupted; task code should still cooperate with interruption to release
resources promptly.

## Runtime modes

- `COMPATIBLE`: conservative runtime, maximum compatibility with JavaFX semantics
- `BALANCED`: recommended default mode
- `LIGHT`: lower overhead and more aggressive lazy creation
- `PERFORMANCE`: optimized for throughput after profiling
- `DEBUG`: enables extra diagnostics and metrics

## Programmatic example

```java
JXParallelConfig config = JXParallelConfig.builder()
    .minThreads(2)
    .maxThreads(8)
    .autoScale(true)
    .mode(JXParallelMode.BALANCED)
    .build();

JXParallel.applyConfiguration(config);
```
