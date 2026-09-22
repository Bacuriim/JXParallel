# JXParallel scalability QA report

## Scope

This review combines:

- black-box scenarios for JavaFX controls and user interaction
- gray-box lifecycle, queue, cache, and FX-dispatch scenarios
- white-box inspection of properties, events, worker pool, and visual styling

The previously available Maven suite passed after the initial regression fixes. In the current
environment Maven was not installed on `PATH`; the modified core was compiled directly with
`javac -source 8 -target 8`, and scheduler smoke tests passed.

```text
javac -source 8 -target 8
RuntimeSmoke: ok
```

## Regression fixes applied

| Area | Result |
|---|---|
| event dispatch | `JXEventType` now compares by name, so equivalent event types dispatch consistently |
| TTL cache | TTL mode now respects `maxSize` instead of growing without a bound |
| post-shutdown submission | submitting after `shutdown()`/`shutdownNow()` recreates the pool |
| forced shutdown | queued task futures are cancelled instead of remaining incomplete |
| observable list writes | indexed reads and mutations are synchronized |
| hover styling | repeated style application no longer installs duplicate hover handlers |
| bounded scheduler | priority queue is bounded and worker growth can reach `maxThreads` |
| queue policies | `BLOCK`, `REJECT`, `DISCARD`, and `DISCARD_OLDEST` now have distinct behavior |
| task timeout | timeout completes the future exceptionally and interrupts the active worker |
| property binding | `JXProperty.unbind()` removes the registered source listener |
| list snapshots | `JXObservableList.snapshot()` and iteration use a stable copy |
| FX dispatch | unavailable toolkit now fails explicitly instead of mutating UI inline |
| configuration diagnostics | invalid default configuration is no longer silently replaced |
| metrics | disabled metrics no longer update counters |

## Findings resolved in this pass

| Severity | Finding | Evidence | Impact |
|---|---|---|---|
| HIGH | Task timeout was metadata only | `Task.java`, `AdaptiveWorkerPool.java` | Resolved with exceptional completion and interruption |
| HIGH | Executor queue was effectively unbounded | `AdaptiveWorkerPool.java` | Resolved with a bounded priority queue |
| MEDIUM | Queue policies converged on rejection | `AdaptiveWorkerPool.java` | Resolved with distinct policy behavior |
| MEDIUM | Property bindings had no release lifecycle | `JXProperty.java` | Resolved with `unbind()` |
| MEDIUM | Compound list consumers had no stable view | `JXObservableList.java` | Resolved with snapshots and snapshot iterators |
| MEDIUM | FX fallback could mutate UI inline | `JXFxDispatcher.java` | Resolved by explicit failure |
| LOW | Invalid default configuration was hidden | `JXParallel.java` | Resolved by propagating configuration errors |
| LOW | Disabled metrics still incremented counters | `RuntimeMetrics.java` | Resolved by honoring `metrics.enabled` |

## Black-box scenarios

1. Submit a task, call `shutdownNow`, and verify every returned future reaches a terminal state.
2. Submit more tasks than `queue.capacity` and verify each queue policy's documented outcome.
3. Recreate a view repeatedly, bind properties, and verify listeners can be released.
4. Apply visual variants and density repeatedly and verify one hover animation per node.
5. Start and stop JavaFX while background tasks dispatch UI updates; verify no off-thread scene mutation.
6. Load the same FXML resource concurrently and verify each result is a distinct scene-graph instance.
7. Exercise `JXListView` with 10,000+ items while mutating the backing collection from a worker.

## Gray-box scenarios

- capture queue depth, active workers, completed/failed tasks, and average duration under a
  bounded burst
- verify forced shutdown cancellation and pool recreation
- verify cache hits/misses/evictions under LRU, TTL, and combined policies
- compare JavaFX dispatcher behavior before toolkit startup, during normal operation, and after
  toolkit shutdown

## Remaining validation

Before claiming production scalability, run the complete Maven test suite on a machine with Maven
and JavaFX dependencies available, then execute JavaFX toolkit tests with a real or virtual
display. The remaining concern is integration coverage, not an unresolved finding in the reviewed
core paths.

The implementation is now a safer foundation and compatibility bridge, but production adoption
still requires the planned stress, leak, and JavaFX integration suites.
