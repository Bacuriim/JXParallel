# JXParallel roadmap

Each phase below says what exists in the repository now and what is still open. The project is
pre-1.0. The overview table is in the README ("Project status"); the JXForge widget checklist is in
[jxforge-1.0.md](jxforge-1.0.md).

## Phase 1: core runtime (done)

`jxparallel-core`: an adaptive worker pool (`AdaptiveWorkerPool`) with a bounded queue and four queue
policies (`QueuePolicy`), `start`/`shutdown`/`shutdownNow`, task priorities, timeouts and cancellation
(`Task`, `TaskPriority`, `CancellationToken`), configuration with a fixed precedence and a
`jx-parallel.config` file ([configuration.md](configuration.md)), and metrics (`RuntimeMetrics`).

## Phase 2: lightweight infrastructure (done)

Properties (`JXProperty` and its typed subclasses), `JXObservableList`, events (`JXEvent`,
`JXEventDispatcher`) and the declarative model (`JXElement`, `JXComponent`, `JXState`, `JXLayouts`,
`JXControls`; [ui-model.md](ui-model.md)), all in `jxparallel-core`.

## Phase 3: JavaFX bridge (done)

`jxparallel-javafx`: `JXFxDispatcher` and `JXParallelFx` send work to the one JavaFX application
thread and background work to the pool. Built with `-Plegacy-javafx`.

## Phase 4: controls and regions (done, in three forms)

- JavaFX wrappers in `jxparallel-javafx`: fifteen controls, `JXListView` and seven layouts, each
  holding a real JavaFX node ([compatibility.md](compatibility.md)). The visual layer is in
  [modern-ui.md](modern-ui.md).
- Native components in `jxparallel-ui`: `JXLabel`, `JXButton`, `JXTextField`, `JXTextArea`,
  `JXPasswordField`, `JXCheckBox`, `JXComboBox`, `JXSlider`, `JXProgressBar`, `JXToggleButton`
  and `JXPane`, which return elements the native renderer draws.
- The native toolkit itself and JXML, which carry the widgets in practice: the JXForge schema has
  95 tags, among them layouts, text and input controls, buttons, pickers, lists, tables, trees, menus and
  dialogs ([native-ui.md](native-ui.md), [jxforge.md](jxforge.md)). The planned `JXParent` and `JXRegion`
  classes were not created; the element model replaced them.

## Phase 5: complex controls and virtualization (done for JXML and the native toolkit)

Native `list`, `table`, `Tree` and `TreeTable` show only a window of rows (`first`, `rows`), with
`Spinner`, `DatePicker`, `TimePicker`, `ColorPicker`, scroll panes, context menus, menu bars, dialogs,
drawers and snack bars (overlays). The same controls for the JavaFX-compatible API (`jxparallel-fx`) are
tracked in [native-ui-progress.md](native-ui-progress.md).

Open: a `ListView` with columns and rows of different heights ([jxforge.md](jxforge.md), "Not done yet").

## Phase 6: FXML loading and the resource cache (done, one item open)

`FXMLLoaderService` in `jxparallel-fxml` loads FXML in parallel and caches each file as a pre-parsed
template (`FxmlTemplate`), with `FXMLLoader` as the fallback and the cache strategies `NONE`, `LRU`, `TTL`
and `LRU_TTL`. Every load builds a new scene graph, so nodes are never shared between parents.

Open: preloading (there is no preload API), and compiling FXML files with JXForge so a JavaFX
application moving to the native UI gets compiled screens without rewriting them.

## Phase 7: testing and compatibility (in place)

JUnit 4 rule and JUnit 5 extension, Mockito and PowerMock helpers, jcstress thread-safety tests (profile
`jcstress`), ArchUnit rules, property-based tests (jqwik), golden-image and paint-recording tests of
the renderer, differential tests against JavaFX 21 and `FXMLLoader`, and a PIT mutation profile
([testing-strategy.md](testing-strategy.md)). CI runs on Java 8 to 25, 32-bit and 64-bit
([compatibility.md](compatibility.md#java-versions)).

Open: the JavaFX-compatible API has not been run on a large real application, and the golden images are
drawn with the UI font of 64-bit Windows.

## Phase 8: performance and maturity (in progress)

JMH benchmarks and a regression gate on ratios between benchmarks of the same run run in CI;
the measurement reports with their data are in this folder (`*-2026-*.md` and the CSV files; the README
links the main ones). A `release` profile builds sources, javadoc and signatures.

Open: a 1.0 release of JXForge (the README's quick start still builds it locally).

## JXForge (done for the 1.0 widget checklist, further work open)

The JXML language, interpreter, compiler, live view, command line, Maven plugin goals `generate`, `xsd` and
`fxml`, the Gradle plugin, the FXML converter, and the widget checklist of [jxforge-1.0.md](jxforge-1.0.md) are implemented and tested. The
XSD for IDEs is described in [jxforge-xsd.md](jxforge-xsd.md).

Open ([jxforge.md](jxforge.md), "Not done yet"): events the renderer does not report, SVG images, exit
animations, `<For>` over a `Map`, `fx:id` injection when converting FXML, and publishing the Gradle plugin.

## Not planned in this repository's current scope

Screen-reader adapters for Linux (AT-SPI) and macOS (NSAccessibility). Windows has an opt-in UI Automation
adapter ([accessibility.md](accessibility.md)).

## Design principle

Compatibility first. Correctness first. Performance second.
