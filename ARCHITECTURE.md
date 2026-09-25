# JXParallel architecture

## Goals

- provide an independent Skia-based UI runtime through Skija
- preserve familiar JavaFX concepts without depending on JavaFX
- improve runtime efficiency under the hood
- reduce allocation pressure and lifecycle overhead
- keep runtime behavior safe and deterministic

## Runtime layers

1. `jxparallel-core`
   - worker pool
   - task execution
   - configuration
   - metrics
   - lifecycle
2. `jxparallel-ui`
   - native scene graph
   - GLFW/OpenGL window (LWJGL): Skia (Skija) on 64-bit JVMs, NanoVG on 32-bit JVMs
   - controls, layout, input, accessibility, styling, animation
3. `jxparallel-properties`
   - property system for future control implementations
3. `jxparallel-events`
   - event dispatching and listener management
4. `jxparallel-layout`
   - dirty-flag and incremental layout
5. `jxparallel-fxml` (legacy optional profile)
   - JavaFX loader, cache, preload, resource management
6. `jxparallel-javafx` (legacy optional profile)
   - JavaFX adapter and compatibility controls
7. `jxparallel-benchmarks`
   - JMH-based performance evaluation

The default Maven reactor does not include or resolve OpenJFX. JavaFX modules are migration
adapters and are built only with `-Plegacy-javafx`. All modules compile to Java 8 bytecode. Skija ships no 32-bit natives, so on
32-bit JVMs (Java 8 to 21) the window paints with NanoVG; `-Djx.renderer=skia|nanovg` forces one.

## Compatibility rule

If an optimization changes the observable behavior of a native JXParallel concept, it must be
opt-in, documented, and tested. JavaFX comparison tests are evidence for migration, not an
implementation dependency.
