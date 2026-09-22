# Java 8 x86 GUI metrics

## Environment

| Item | Value |
|---|---|
| JDK | `C:\Program Files (x86)\Java\jdk1.8.0_51` |
| JVM | Java HotSpot Client VM 1.8.0_51 |
| JavaFX | JavaFX 8 from `jre\lib\ext\jfxrt.jar` |
| Runs | 5 independent process runs per implementation |
| Workload | create scene, show stage, fire Continue, wait for 50 ms background operation, update label |

## Averages

| Metric | Traditional JavaFX | JXParallel | Difference |
|---|---:|---:|---:|
| startup to `Stage.show()` | 240.282 ms | 267.408 ms | JXParallel +11.3% |
| click to visible result | 51.134 ms | 54.725 ms | JXParallel +7.0% |
| observed heap delta | 2,309,547 bytes | 1,251,810 bytes | JXParallel -45.8% |
| process CPU during run | 328.125 ms | 378.125 ms | JXParallel +15.2% |
| thread-count delta | +1 | +1 | equal |

## Interpretation

This workload demonstrates a meaningful memory difference, but it does not show a latency or CPU
win for the current JXParallel bridge. The JXParallel variant adds wrapper properties, styling,
and scheduler dispatch, so its startup and response path are slightly slower in this small
single-interaction test.

The result is not a failure of the architecture; it identifies the optimization boundary. The
current controls still use JavaFX nodes and therefore cannot remove JavaFX control creation cost.
The strongest expected gains require repeated background work, larger task batches, resource
loading, or the future independent renderer.

Heap delta is an observed difference between two samples, not retained memory or total allocation.
CPU time is process CPU time, not wall time. Each value should be re-collected on the target
machine before release claims.

Raw runs are stored in `metrics-java8-x86-2026-09-22.csv`.
