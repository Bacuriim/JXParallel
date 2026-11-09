# JXParallel FAQ

## What is the goal of JXParallel?

JXParallel is a Java UI runtime that does not depend on JavaFX: screens written as markup (JXML) and
compiled to plain Java by JXForge, a native scene graph drawn with Skia, NanoVG or a CPU painter in a GLFW
window, and a bounded, prioritized worker pool so the UI thread only paints. It keeps JavaFX concepts and
behavior where an application can observe the difference. The README has the overview.

## Does JXParallel replace JavaFX?

For new screens it can: JXForge and the native UI use no JavaFX. For an existing JavaFX application it is
adopted in steps (background work, then FXML loading, then native screens), and the JavaFX modules stay
available for that. They are built only with `-Plegacy-javafx` (OpenJFX 21), and `jxparallel-fx` (a
JavaFX-compatible API that can run on the native renderer) only on a JDK 8 that bundles JavaFX. See
[ARCHITECTURE.md](ARCHITECTURE.md) and [docs/compatibility.md](docs/compatibility.md).

## Do I need to learn a new API?

The JXML tags are widget-style (`Column`, `Button`, `TextField`) rather than JavaFX class names, and screens
bind to a plain Java controller. Where the toolkit follows JavaFX (box, grid, border, flow, tile layouts, sizes
of controls, the Modena look) a JavaFX developer finds the same behavior. Code written against the JavaFX API
can use `jxparallel-fx`, which is experimental.

## Can I still use standard JavaFX controls?

Yes, in the JavaFX modules: `jxparallel-javafx` wraps JavaFX controls and exposes the node with `node()`, and
`jxparallel-fxml` loads FXML in parallel. The native controls are not wrappers of JavaFX controls.

## Which Java versions and platforms are supported?

Java 8 to 25, 32-bit and 64-bit, with Skia on 64-bit JVMs from Java 9 and NanoVG on 32-bit JVMs and on
Java 8. The native libraries ship for Windows x64, Windows x86 and Linux x64. The CI matrix and what runs on
each JVM are in [docs/compatibility.md](docs/compatibility.md) and the README ("Platform support").

## What is the current maturity level?

Pre-1.0. The scheduler, properties, events, FXML loading, JXForge and the native widgets are tested; the
JavaFX-compatible API in native mode has not been run on a large real application. The README's "Project
status" table lists what is done and what is not, among them keyboard navigation and hover inside popups, a
screen-reader adapter, compiling FXML with JXForge, and a Gradle plugin.

## Why are there multiple runtime modes?

`JXParallelMode` (`COMPATIBLE`, `BALANCED`, `LIGHT`, `PERFORMANCE`, `DEBUG`) lets a team trade
compatibility, overhead and throughput for the worker pool deliberately instead of forcing one profile on
every application. See [docs/configuration.md](docs/configuration.md).
