# Native UI implementation progress

The native UI path is JavaFX-free: `jxparallel-ui` draws with Skia (Skija) on 64-bit Java 9+ and
NanoVG on 32-bit or Java 8, both on a GLFW/OpenGL window (LWJGL 3.3.3), and shapes text with
HarfBuzz. ArchUnit keeps JavaFX and AWT out of the module.

## Native mode of the JavaFX-compatible API (phase 2b, complete)

`-Djx.backend=native` makes every JX node of `jxparallel-fx` (the `com.jxparallel.fx` API
DeviceConfig imports) a `NativeModel` instead of a JavaFX node; the native layer draws it. The
JavaFX application thread stays the event loop, so application code, `Platform.runLater`,
properties, bindings and animations behave as before; only the scene graph is not JavaFX's.

- **Architecture.** One display thread (`JXDisplay`) owns GLFW and paints; the tree is built and
  laid out on the JavaFX thread under a lock and painted under the same lock. Input goes from the
  display thread to `Platform.runLater`, with pointer moves coalesced. Every element is painted
  by one piece of code (`JXPaint`) through the `JXPainter` primitives both renderers implement.
- **Incremental rendering.** Like JavaFX syncing its dirty nodes, a frame rebuilds only the
  elements of nodes that changed (and their ancestors; whole subtrees for changes CSS or
  inheritance sees) and reuses the others as the same instances, which the reconcile and the
  layout skip.
- **Controls.** Every control DeviceConfig uses has native layout, look (Modena) and behaviour:
  labels, buttons, toggle/radio/check boxes, hyperlinks, text fields, password fields, text
  areas, combo boxes (editable or not, with popup), choice boxes, spinners, date pickers (with
  calendar popup), sliders, progress bars and indicators, separators, scroll panes, list and
  table views (virtualized, cell factories, sorting, single and multiple selection, constrained
  columns), tab panes, titled panes, accordions, pagination, button bars, text flows, images,
  tooltips, context menus, menu buttons, dialogs (`Alert`, `Dialog`, `TextInputDialog`, `ChoiceDialog`,
  with the Windows button order), file and directory choosers (tinyfd), ControlsFX
  `Notifications` and `CustomTextField`.
- **Events.** Real JavaFX event objects with JavaFX semantics: filters from the root down,
  handlers from the target up, `consume()` stops, `onXxx` after added handlers; mouse, key,
  scroll, action, window and drag-and-drop events (custom dragboard with drag views);
  accelerators on the scene (no mnemonics yet); focus traversal with Tab; default and cancel buttons.
- **CSS subset.** Stylesheets and inline styles with selector specificity, pseudo-classes,
  default style classes, looked-up colors from `.root`, `derive()` and gradients (first stop);
  stylesheets override values set in code and inline styles override both, like JavaFX.
- **Text.** Caret, selection, clipboard and `TextFormatter` filters; JavaFX's own line metrics
  and text widths are measured once from the running JavaFX, so native labels size exactly like
  JavaFX labels (hinted DirectWrite metrics cannot be derived from the font units).
- **Animations.** `FadeTransition`, `TranslateTransition`, `ScaleTransition`,
  `RotateTransition`, `ParallelTransition`, `SequentialTransition` and `PauseTransition` run on
  a real JavaFX animation that writes the native node's properties (`NativeAnimations`).
- **Windows.** Stages and modality (a modal window blocks its owner), `showAndWait` and dialogs
  in a nested event loop, sizes, positions, titles, close requests, snapshots.

## Verification

- JavaFX 21 differential tests (jqwik, random trees): VBox/HBox/StackPane, BorderPane, GridPane,
  AnchorPane, FlowPane, TilePane, titled panes and accordions, control sizes and wrapped labels.
- Raster golden images (`controls-skia.png`, `showcase-skia.png`) and exact recordings of every
  drawing primitive for three scenes of controls in all their states (`*-paint.txt`).
- Headless native-mode tests of `jxparallel-fx` (`-Djx.headless=true`): controls, interaction,
  dialogs, CSS, drag and drop, runtime, animations, and a guard that every registered native
  method matches a generated API method.
- Mutation testing (PIT): 95% of the mutants of the new layout, text, hit-testing and painting
  code and 78% of the native runtime's are killed; details in docs/testing-strategy.md.

## Implemented (renderer)

- retained native node tree with bounds and children
- row, column, stack, border, grid, pane, anchor, flow and tile layouts with the JavaFX 21
  algorithms and pixel snapping
- dirty layout state and preferred-size caching to avoid repeated full calculations
- GLFW window, input, clipboard, multi-click detection, screen bounds
- pointer hit testing (skipping hidden and mouse-transparent nodes, respecting clips)
- text document with caret, selection, insert/delete, select-all, copy, cut, and paste
- accessibility tree with roles, names, descendants, and deterministic flattening
- bounded LRU resource byte cache
- independent XML markup loader with external-entity protection

## Current limitations

- translate, scale and rotate are kept as values but not drawn (only opacity is); shape
  transitions (`FillTransition`, `StrokeTransition`, `PathTransition`) are not supported
- CSS: no combinators beyond descendant and child, no media queries, gradients use their first stop
- a child spanning a fixed-size (`USE_PREF_SIZE`) track in a centred GridPane can be placed 1 px
  off JavaFX (documented in `JXGridLayout`)
- wrapped labels take the width set on them; JavaFX's content bias (height for the width the
  parent gives) is not modelled in the box layouts
- no `MenuBar`, `TreeView` or window icons yet (DeviceConfig does not use them)
- platform screen-reader adapters are not implemented
- `jxparallel-fx` builds with JDK 8 (bundled JavaFX 8); `jxparallel-ui` tests need JDK 9+ for
  Skia (Skija does not load on Java 8, where NanoVG draws)

## Scalability guarantees

The native UI does not create one thread per animation scheduler. It uses a shared daemon timing
executor and each scheduler owns only its animation list and cancellation handle.

Layout keeps the last bounds and preferred size for a node. Repeating a render with unchanged
dimensions does not recalculate that subtree's layout. A future mutable scene API must call
`JXNativeNode.invalidateLayout()` after changing geometry-affecting state.

Resource caching is bounded both by entry count and byte size:

```java
JXResourceCache cache = new JXResourceCache(1000, 16 * 1024 * 1024);
```

The cache exposes `getHits()`, `getMisses()`, `getEvictions()`, `size()`, and
`getCurrentBytes()` for operational monitoring.

## Example markup

```xml
<column gap="8">
    <button label="Save"/>
    <input value="Customer"/>
</column>
```

```java
JXMarkupLoader loader = new JXMarkupLoader();
JXElement screen = loader.load(stream);

JXWindow window = new JXWindow("Customers");
window.setContent(screen);
window.show();
```

The loader is intentionally independent of FXML and JavaFX. It produces JXParallel elements,
which are then mounted by `JXSkiaRenderer`.

## Text editing

```java
JXTextDocument document = new JXTextDocument("customer");
document.setCaret(8, false);
document.insert(" profile");
document.selectAll();
document.copy(clipboard);
```

The document is thread-safe for individual operations and keeps selection/caret state separate
from rendering.

## Accessibility

```java
JXAccessibleNode tree = JXAccessibilityTree.from(screen);
List<JXAccessibleNode> ordered = tree.flatten();
```

The tree exposes stable roles and accessible names to future platform adapters.

## Styling and animation

```java
JXStyleSheet sheet = new JXStyleSheet()
    .add("button", JXStyle.builder().set("radius", 8).build())
    .add("#save", JXStyle.builder().set("primary", true).build());

JXAnimationScheduler scheduler = new JXAnimationScheduler();
scheduler.add(new JXAnimation(200, progress -> updateOpacity(progress)));
scheduler.tick(16);
```
