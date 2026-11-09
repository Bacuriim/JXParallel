# JXParallel architecture

## Goals

- a UI runtime that does not depend on JavaFX, drawn with Skia, NanoVG or a CPU painter
- screens written as markup and compiled to plain Java at build time (JXForge)
- a bounded, prioritized worker pool so the UI thread only paints
- JavaFX concepts and behavior kept where an application can observe the difference
- runs on Java 8 to 25, 32-bit and 64-bit; every module compiles to Java 8 bytecode

## Modules

The default Maven build contains the modules without JavaFX. The JavaFX ones are behind profiles.

| Module | Role | Depends on |
|---|---|---|
| `jxparallel-core` | worker pool, tasks, configuration, metrics, lifecycle (`com.jxparallel.core`); properties and observable lists (`properties`); events (`events`); the declarative model `JXElement`, `JXComponent`, `JXState` (`ui`, see [docs/ui-model.md](docs/ui-model.md)) | nothing |
| `jxparallel-ui` | native scene graph, layouts, controls, overlays, text, the GLFW window, and the Skia, NanoVG and software renderers ([docs/native-ui.md](docs/native-ui.md)) | core, LWJGL, Skija |
| `jxparallel-forge` | JXForge: the JXML schema, interpreter, compiler, live view and XSD generator ([docs/jxforge.md](docs/jxforge.md)) | core |
| `jxforge-maven-plugin` | goals `jxforge:generate` and `jxforge:xsd` | forge |
| `jxparallel-examples-native` | native examples, the component benchmark, the UI stress runner | ui, forge |
| `jxparallel-junit4`, `-junit5`, `-mockito`, `-powermock` | test integrations | core |
| `jxparallel-benchmarks` | JMH and runtime comparison runners | core, ui |
| `jxparallel-jcstress` (profile `jcstress`) | thread-safety stress tests | core |
| `jxparallel-fx`, `jxparallel-fx-controlsfx` (profile `javafx8`, on a JDK 8 with JavaFX) | the JavaFX-compatible API, which can run on the native renderer with `-Djx.backend=native` | core, ui |
| `jxparallel-javafx`, `jxparallel-fxml`, `jxparallel-examples` (profile `legacy-javafx`, OpenJFX 21) | FX thread dispatch and wrappers of JavaFX controls; parallel FXML loading with a cache; JavaFX example apps | core, ui, JavaFX |

`ArchitectureTest` in `jxparallel-ui` and in `jxparallel-forge` keeps the rules below from being broken
quietly.

## Rules

- `jxparallel-core`, `jxparallel-ui` and `jxparallel-forge` use neither AWT, Swing nor JavaFX, nor the
  modules built on JavaFX.
- `jxparallel-core` uses no renderer library, so it runs on any JVM without native libraries.
- Only package `com.jxparallel.ui.native2d` talks to GLFW, OpenGL, Skia and NanoVG, and only package
  `com.jxparallel.ui.text` to HarfBuzz and FreeType. The renderer is chosen inside `native2d`:
  Skia on 64-bit JVMs from Java 9, NanoVG on 32-bit JVMs and on Java 8, the CPU painter when asked for
  on Windows; `-Djx.renderer=skia|nanovg|software` forces one.
- Packages have no cycles.
- Threads: background work goes to the worker pool; scene changes go to the thread that owns the
  scene (the JavaFX application thread in the JavaFX modules; in the native UI, `JXDisplay` is the one
  thread that talks to GLFW).
- Compatibility: if an optimization changes the observable behavior of a native JXParallel concept, it
  must be opt-in, documented and tested. JavaFX comparison tests are evidence for migration, not an
  implementation dependency. The levels are in [docs/compatibility.md](docs/compatibility.md).

More: [docs/architecture.md](docs/architecture.md) for the core primitives, and
[docs/testing-strategy.md](docs/testing-strategy.md) for how the rules are tested.
