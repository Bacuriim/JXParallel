# Changelog

## Unreleased

- `jxparallel-fx`: JavaFX-compatible API under `com.jxparallel.fx` (same class names as JavaFX): 424 classes generated from JavaFX 8 by `scripts/GenerateFxApi.java` plus the application launcher, `ObservableList` and `FXMLLoader` by hand; each JX object currently runs on a JavaFX peer, and everything crossing the boundary (nodes, lists, enums, events, listeners, lambdas) is converted by `com.jxparallel.fx.Fx`; JavaFX calls methods a JX subclass overrides (`updateItem`, `call`, `toString`...); the `FXMLLoader` parses each file once (neutral `FxmlTemplate`) and builds JX objects from unchanged FXML, including Scene Builder output, `fx:include`, `@` locations and `fx:constant`; `jxparallel-fx-controlsfx` covers ControlsFX `Notifications` and `CustomTextField`; `scripts/migrate-imports.py` rewrites imports and qualified names without touching any other byte
- layout containers: `border` (BorderPane), `grid` (GridPane with column/row constraints and spans), `pane`, `anchor` (AnchorPane), `flow` (FlowPane) and `tile` (TilePane), plus padding and child margins everywhere; each checked against real JavaFX 21 on 1000 random trees; sizes may be negative like in JavaFX
- layout: min/pref/max sizes, grow priorities, alignment and fill flags with the JavaFX 21 VBox, HBox and StackPane algorithms and pixel snapping, checked against real JavaFX on 1000 random trees; controls use JavaFX sizes (buttons keep their width, text fields stretch); default font 12 px like JavaFX; a change that leaves a node's sizes unchanged no longer relayouts its parent
- real text measurement: `JXTextEngine` shapes text with HarfBuzz (LWJGL, 32 and 64-bit); controls are sized from their text with JavaFX defaults (TextField 12 columns, TextArea 40x10) instead of fixed sizes; Skia and NanoVG draw with the same font file and size, Skia draws shaped text
- testing: jqwik property and differential tests, jcstress module, Skia golden image and click tests, JMH ratio gate in CI, ArchUnit rules, leak tests, `coverage` (JaCoCo) and `mutation` (PIT) profiles; see docs/testing-strategy.md
- fixed: native buttons ignored clicks (`onAction` was set, `onClick` read); disabled nodes no longer get clicks
- fixed: `JXProperty`, `JXState` and `JXObservableList` could notify out of order or twice under concurrent changes; changes and notifications are now serialized, readers never block
- fixed: a forgotten `JXProperty` bound with `bind` was never garbage collected; bindings now hold the target weakly, like JavaFX
- fixed: `FxmlTemplate` copied `fx:id` into `id` without `@IDProperty` and resolved nested class names more loosely than `FXMLLoader`

- FXML template cache: `FXMLLoaderService` parses each file once and instantiates from a pre-resolved plan, with `FXMLLoader` fallback
- incremental UI updates: `JXWindow.setContent` reconciles in place, controls memoize `render()`, render requests are coalesced
- NanoVG renderer for 32-bit JVMs; 64-bit keeps Skia (`-Djx.renderer` forces one)
- fixed `AdaptiveWorkerPool` never growing past `threads.min` and leaking queue permits in `poll(timeout)`
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
