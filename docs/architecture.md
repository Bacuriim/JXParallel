# Core primitives

The module layout, the dependency rules and the renderers are described in
[ARCHITECTURE.md](../ARCHITECTURE.md). This page keeps what is specific to `jxparallel-core` and the JavaFX
bridge: the primitives that the rest is built on. The packages of `jxparallel-core` are:

| Package | Contents |
|---|---|
| `com.jxparallel.core` | `JXParallel` (the facade: `start`, `background`, `shutdownNow`), `Task`, `TaskPriority`, `CancellationToken`, `AdaptiveWorkerPool`, `TaskQueue`, `BackpressureController`, `QueuePolicy`, `JXParallelConfig`, `JXParallelMode`, `RuntimeMetrics` |
| `com.jxparallel.properties` | `JXProperty` and its boolean, integer, double and string subclasses, `JXObservableList` (add, remove and update events), change events and listeners |
| `com.jxparallel.events` | `JXEvent`, `JXEventType`, `JXEventHandler`, `JXEventDispatcher` |
| `com.jxparallel.ui` | the declarative model: `JXElement`, `JXComponent`, `JXState`, `JXNode`, `JXRenderer`, with `JXControls`, `JXLayouts` and a `JXTheme` of tokens ([ui-model.md](ui-model.md)) |

In the JavaFX bridge (`jxparallel-javafx`, profile `legacy-javafx`), `JXFxDispatcher` runs work on the JavaFX
application thread (`runLater` runs it at once when already there, `runAndWait` blocks the caller) and throws
`IllegalStateException` when the toolkit is not available. `JXParallelFx` is the facade over it
(`ui(...)`, `uiAndWait(...)`, `background(...)`). The dispatcher uses `Platform.runLater`, so there is
only the one FX Application Thread.

The primitives are small on purpose: lazy creation, little allocation, and the same concepts and names as
JavaFX where that costs nothing.

## The thread rule

- background work (I/O, parsing, computation) -> the worker pool, `JXParallel.background(...)`
- scene changes in a JavaFX application -> the FX thread, `JXParallelFx.ui(...)`
- scene changes in the native UI -> `JXWindow.setContent(...)`, which may be called from any thread and
  hands the work to the display thread

Configuration of the pool is in [configuration.md](configuration.md).
