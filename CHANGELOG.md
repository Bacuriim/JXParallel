# Changelog

## Unreleased

- removed the Vulkan backend; `JXWindow` now renders with Skia (Skija) on a GLFW/OpenGL context
- added independent Skia native UI module through Skija and executable native example
- added optional experimental LWJGL/OpenGL backend with GLFW window lifecycle
- added optional experimental direct LWJGL/Vulkan backend reusing the existing native components
- moved JavaFX integration to the optional `legacy-javafx` Maven profile
- added native input, text editing, accessibility, styling, animation, and resource cache APIs
- added Java 8 native-core CI coverage and Windows Java 17 CI coverage
- created the initial multi-module Maven project structure
- implemented a core adaptive runtime
- added config loading and runtime mode handling
- documented roadmap and compatibility strategy
- added initial test coverage for config, task execution, and runtime configuration
- added the Phase 2 lightweight property/event infrastructure
- added the Phase 3 JavaFX bridge layer for UI-thread dispatch and compatibility wrappers
- added initial layout bridges and a JavaFX-backed `JXListView` with native cell virtualization
- added the initial JMH benchmark baseline and Java 17/21 CI workflow
- added configurable FXML cache strategies, cache metrics, additional controls, and restart-safe lifecycle handling
- added explicit Mockito reset support and documented PowerMock instrumentation limitations
- added modern visual variants, density controls, shared styling, hover transitions, and JavaFX-like common control methods
- added equivalent traditional JavaFX and JXParallel comparison applications with Java 8 x86 setup documentation
