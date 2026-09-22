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
   - Skia renderer through Skija
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
   - experimental GLFW/OpenGL and GLFW/Vulkan backends

The default Maven reactor does not include or resolve OpenJFX. JavaFX modules are migration
adapters and are built only with `-Plegacy-javafx`. The native UI requires Java 11 or newer
because current Skija artifacts target Java 11; the core runtime remains Java 8 compatible.
The LWJGL backends are isolated behind `-Plwjgl-opengl` or `-Plwjgl-vulkan` and are not part of
the default build. Both reuse the same native scene graph and components.

## Compatibility rule

If an optimization changes the observable behavior of a native JXParallel concept, it must be
opt-in, documented, and tested. JavaFX comparison tests are evidence for migration, not an
implementation dependency.
