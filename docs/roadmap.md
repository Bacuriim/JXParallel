# JXParallel roadmap

## Phase 1 — Core runtime

Focus: scheduler, workers, queue, configuration, lifecycle, metrics.

Status: completed as a working baseline.

### Goals
- adaptive worker pool with bounded queue
- lifecycle with start/shutdown/shutdownNow
- task priorities and cancellation token
- config precedence and file-based defaults
- observability via metrics

## Phase 2 — Lightweight infrastructure

Focus: property system, events, observable collections, layout primitives.

Status: implemented in the foundation package.

### Goals
- JXProperty family
- listener registration with low overhead
- observable collection support
- minimal allocations and lazy creation

## Phase 3 — JavaFX bridge

Focus: FX thread dispatch, scene integration, compatibility layer.

Status: implemented as the JavaFX dispatcher bridge.

### Goals
- keep one JavaFX Application Thread only
- background work routed to worker pool
- UI updates routed via FX dispatcher
- compatibility matrix for controls and behaviors

## Phase 4 — Controls and regions

Focus: first components and reusable base classes.

Status: broad initial compatibility bridge implemented.

### Planned set
- JXNode
- JXParent
- JXRegion
- JXPane
- JXLabel
- JXButton
- JXTextField
- JXTextArea
- JXComboBox
- JXListView

The initial bridge currently covers `JXLabel`, `JXButton`, `JXTextField`, and `JXTextArea`.
These are documented as PARTIAL until an independent scene-graph implementation exists.

The first layout bridges (`JXPane`, `JXHBox`, and `JXVBox`) and the `JXListView` bridge are now
available. `JXListView` relies on JavaFX's native virtualized cell implementation.

The common control bridge now also covers check boxes, combo boxes, password fields, progress
controls, radio/toggle buttons, sliders, tabs, scroll panes, and separators.

## Phase 5 — Complex controls and virtualisation

Focus: list/table/tree-heavy controls.

Status: configurable source cache implemented.

### Planned set
- JXTableView
- JXTreeView
- JXTreeTableView
- JXSpinner
- JXDatePicker
- scroll and menu components

## Phase 6 — FXML and resource cache

Focus: safe concurrent FXML loading and cache invalidation.

Status: planned.

### Goals
- JXFXMLLoader
- LRU / TTL cache
- preload support
- resource cache and memory-safe invalidation

The loader caches FXML source bytes rather than `Node` instances. Every load creates a
new Scene Graph instance, avoiding illegal sharing of JavaFX nodes between parents.

## Phase 7 — Testing and compatibility

Focus: JUnit 4/5, Mockito, PowerMock, thread-safety and compatibility checks.

Status: baseline in place; core, bridge, controls, cache, lifecycle, Mockito, JUnit, and API-shape tests are present.

CI currently verifies Java 17 and Java 21. Java 8 remains a core-runtime compatibility target
until the JavaFX dependency profile is separated.

## Phase 8 — Performance and open source maturity

Focus: JMH benchmarks, docs, CI/CD, release readiness.

Status: initial JMH scheduler baseline and CI workflow added.

## Design principle

Compatibility first. Correctness first. Performance second.
