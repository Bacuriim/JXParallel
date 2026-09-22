# JXParallel architecture

The runtime is intentionally split into independent modules:

- `jxparallel-core` keeps the scheduling logic, metrics, queue behavior, and public facade.
- `jxparallel-properties` is the lightweight property and binding layer for future control implementations.
- `jxparallel-events` contains the event dispatch model and listener registry.
- `jxparallel-layout` provides dirty-flag layout, lazy measurements, and incremental updates.
- `jxparallel-scene` offers the scene-graph bridge and dispatcher layer.
- `jxparallel-controls` will contain the first-generation lightweight controls.
- `jxparallel-javafx` handles JavaFX bridge and UI orchestration without creating multiple FX Application Threads.
- `jxparallel-fxml` manages FXML loading and cache behavior.
- `jxparallel-core/com.jxparallel.ui` provides the independent declarative scene-graph model.
- `jxparallel-junit4` and `jxparallel-junit5` hook into the test frameworks without replacing them.
- `jxparallel-mockito` and `jxparallel-powermock` provide explicit compatibility layers for legacy stacks.

## Phase 2 and Phase 3 baseline

The foundation now includes:

- `JXProperty` and property subclasses for lightweight observable values
- `JXObservableList` with scheduled change notifications
- `JXEvent`, `JXEventType`, `JXEventHandler`, `JXEventDispatcher`
- `JXFxDispatcher` for deferring work to the FX thread and handling toolkit absence safely
- `JXParallelFx` as a compatibility facade for UI/background execution

These primitives are intentionally small and intentionally compatible in spirit with JavaFX, without trying to rewrite the full UI stack before the runtime is stable.

## Independent UI direction

The new UI model is inspired by React/Vue's declarative composition, not by their JavaScript
runtime. `JXElement` is immutable, `JXState` is explicit, and `JXNode` is a small mutable mount
tree that can later be connected to JavaFX, a native renderer, or another host. The first
implementation includes only tree reconciliation, basic layouts, form elements, and theme tokens.

## Core principle

Keep the JavaFX developer experience and replace the internals with a lighter runtime model:

- same concepts as JavaFX
- same names where possible
- better thread isolation
- better resource control
- lower allocation pressure
- lazy creation and caching where beneficial

The main runtime rule remains:

- background work -> worker pool
- UI work -> JavaFX Application Thread
- layout and scene updates -> safe, isolated, deterministic flow
