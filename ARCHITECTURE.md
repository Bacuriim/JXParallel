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
   - Skia renderer through Skija on a GLFW/OpenGL window (LWJGL)
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
8. `jxparallel-lwjgl` (optional profile)
   - experimental raw GLFW/OpenGL backend (no Skia)

The default Maven reactor does not include or resolve OpenJFX. JavaFX modules are migration
adapters and are built only with `-Plegacy-javafx`. All modules compile to Java 8 bytecode, but the native
window needs a 64-bit JVM because Skija ships no 32-bit natives; the core runtime runs on Java 8 32-bit.
The raw OpenGL backend is isolated behind `-Plwjgl-opengl` and is not part of the default build.
It reuses the same native scene graph and components.

## Compatibility rule

If an optimization changes the observable behavior of a native JXParallel concept, it must be
opt-in, documented, and tested. JavaFX comparison tests are evidence for migration, not an
implementation dependency.
