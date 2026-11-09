# JXParallel compatibility model

The native UI (`jxparallel-ui`, JXForge) does not wrap JavaFX and is not a JavaFX API. It follows
JavaFX where an application can observe the difference (the layout algorithms, the sizes of controls,
the Modena look, FXML loading), and says where it does not. JavaFX appears only in optional modules,
which the default Maven build leaves out.

## Principle

Behavioral compatibility comes before micro-optimization. If an optimization changes observable
behavior, it must be opt-in and tested. JavaFX comparison tests are evidence for migration, not an
implementation dependency.

## Compatibility levels

| Level | Meaning |
|---|---|
| FULL | API and behavior match closely enough for direct migration; lifecycle and event semantics are preserved. |
| PARTIAL | Most of the API is there; some behavior differs, and the differences are listed. |
| EXPERIMENTAL | Early or feature gated; not recommended for production without validation. |
| UNSUPPORTED | Depends on JavaFX internals or platform behavior that cannot be reproduced safely. |

The levels below are assigned from the code and tests named in the third column, not from a
compatibility suite; none of them has been checked against a large application.

## Current scope

| Area | Level | Where, and what supports it |
|---|---|---|
| Core runtime: worker pool, tasks, priorities, cancellation, metrics, configuration, properties, events, observable lists | FULL | `jxparallel-core`. Not a JavaFX API, so the level means it does what its own documentation says; unit tests in the module and OpenJDK jcstress tests (profile `jcstress`). |
| Native UI toolkit: scene graph, layouts, controls, overlays, renderers | PARTIAL | `jxparallel-ui`. Own element model and props, not JavaFX classes. The box layouts, `BorderPane`, `Separator`, `TitledPane` and `Accordion` are compared with JavaFX 21 by random-tree differential tests (`JXLayoutDifferentialTest`, `JXControlSizesDifferentialTest`, `JXTitledDifferentialTest` in `jxparallel-javafx`, run with `-Plegacy-javafx`); `GridPane`, `FlowPane` and `TilePane` follow the JavaFX algorithms without such a test. Known differences: a flow or tile inside a box layout takes its unbiased height (content bias is not passed down); a child spanning a fixed-size grid track in a centred grid can be a pixel off; baseline alignment in grids is not supported. No CSS engine, no Tab focus traversal, no animation. See [native-ui.md](native-ui.md). |
| JXForge: JXML interpreter and compiler | PARTIAL | `jxparallel-forge`. Its own language, with widget-style tags; it is not FXML. The interpreter and the compiler build the same element tree, which the module's tests check for the sample screens. See [jxforge.md](jxforge.md). |
| JavaFX-compatible API in native mode (`-Djx.backend=native`) | EXPERIMENTAL | `jxparallel-fx` (builds on a JDK 8 with JavaFX). Headless tests for controls, interaction, dialogs, CSS subset, drag and drop, runtime and animations; not run on a large real application. What it covers and what it does not: [native-ui-progress.md](native-ui-progress.md). |
| FXML loading | PARTIAL | `jxparallel-fxml`: `FXMLLoaderService` loads views in parallel and caches each file as a pre-parsed template (`FxmlTemplate`) with `FXMLLoader` as the fallback; `fx:include`, `fx:define`, expressions, `%resources` and scripts fall back to it. The cache strategies are `NONE`, `LRU`, `TTL` and `LRU_TTL`. A differential test (`FxmlTemplateDifferentialTest`) loads random documents with both and compares the object graphs. Each load builds a new `Parent` and controller. FXML is not compiled by JXForge. |
| JavaFX bridge: FX thread dispatch | FULL | `jxparallel-javafx`: `JXParallelFx.ui(...)` and `JXFxDispatcher`, with `JXFxDispatcherTest`. |
| JavaFX bridge: controls and layouts | PARTIAL | `jxparallel-javafx` wrappers around real JavaFX nodes, reached with `node()`; listed below. |
| Test integrations | JUnit 4 rule and JUnit 5 extension: PARTIAL. Mockito adapter: PARTIAL. PowerMock adapter: EXPERIMENTAL | `jxparallel-junit4`, `-junit5`, `-mockito`, `-powermock`. The PowerMock adapter does not claim to work with every instrumentation setup. |

## The JavaFX bridge controls

These are in `jxparallel-javafx` (profile `legacy-javafx`) and are not part of the native UI. They
preserve common JavaFX-style methods and expose the JavaFX node through `node()`.

- Controls, PARTIAL: `JXLabel`, `JXButton`, `JXTextField`, `JXTextArea`, `JXCheckBox`, `JXComboBox`,
  `JXPasswordField`, `JXProgressBar`, `JXProgressIndicator`, `JXRadioButton`, `JXSlider`, `JXTabPane`,
  `JXToggleButton`, `JXScrollPane`, `JXSeparator`, `JXListView` (delegates to JavaFX `ListView`, so it
  has its cell virtualization).
- Layouts, PARTIAL: `JXPane`, `JXHBox`, `JXVBox`, `JXGridPane`, `JXBorderPane`, `JXStackPane`,
  `JXAnchorPane`, each delegating to the JavaFX layout node.
- Visual layer: [modern-ui.md](modern-ui.md).

The native toolkit has its own hand-written components with the same names in package
`com.jxparallel.ui.controls` (`JXButton`, `JXLabel`...); those return `JXElement` values and do not
touch JavaFX.

## Java versions

All modules compile to Java 8 bytecode (the `release-8` profile applies `--release 8` on JDK 9 and
newer). The native UI runs on Java 8 to 25: Skia on 64-bit JVMs from Java 9, NanoVG on 32-bit JVMs and
on Java 8, and a CPU renderer on Windows when asked. CI (`.github/workflows/build.yml`) runs:

| JVM | Runs |
|---|---|
| Java 8 x64, Linux | `jxparallel-core` |
| Java 8 x86, Windows | `jxparallel-core`, `jxparallel-ui` |
| Java 11, 17, 21 x64, Linux; Java 17 and 25 x64, Windows; Java 17 x86, Windows | the whole default reactor (`mvn test`) |
| Java 17, Windows | the JavaFX modules (`-Plegacy-javafx`) |
| Zulu 8 with JavaFX, Windows | `jxparallel-fx` and `jxparallel-fx-controlsfx` |

The JavaFX modules have their own JDK needs: `jxparallel-javafx`, `jxparallel-fxml` and
`jxparallel-examples` use OpenJFX 21 (CI builds them on JDK 17); `jxparallel-fx` extends JavaFX 8
internals, so it builds only on a JDK 8 that bundles JavaFX. Mockito 5 needs Java 11, so JDK 8 builds
use Mockito 4.11.0 for the Mockito and PowerMock modules.
