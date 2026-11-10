# Native UI implementation progress

The native UI path is JavaFX-free: `jxparallel-ui` draws with Skia (Skija) on 64-bit Java 9+ and
NanoVG on 32-bit or Java 8, both on a GLFW/OpenGL window (LWJGL 3.3.3), and shapes text with
HarfBuzz. ArchUnit keeps JavaFX and AWT out of the module.

## Native mode of the JavaFX-compatible API (phase 2b, complete)

`-Djx.backend=native` makes every JX node of `jxparallel-fx` (the `com.jxparallel.fx` API
an application imports) a `NativeModel` instead of a JavaFX node; the native layer draws it. The
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
- **Controls.** Every common JavaFX control has native layout, look (Modena) and behaviour:
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
  default style classes, looked-up colors from `.root`, `derive()` and gradients (all stops);
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

## HiDPI

`JXWindow` is DPI-aware. Layout, hit-testing and everything the application sees (`getWidth`, `getHeight`,
`setSize`, `setMinSize`, pointer coordinates, `RESIZE` events, `screenBounds`) are in logical pixels; the
framebuffer is in physical pixels. `JXWindow.getScale()` is physical pixels per logical pixel (1.5 at 150%).

- the scale is the window's monitor content scale (`glfwGetWindowContentScale`), re-read when the window
  moves to another monitor (`glfwSetWindowContentScaleCallback`), or `-Djx.scale=1.5` (also `150%`; 0.25 to 8)
  to force it for every window; the logic is in `JXScale`, free of GLFW and unit-tested
- logical size is `ceil(framebuffer / scale)`; the cursor is converted with the measured
  `framebuffer / window` ratio, so the same code serves Windows and X11 (window units are physical pixels) and
  macOS and Wayland (window units are points); `GLFW_SCALE_TO_MONITOR` is not used, the window size is
  converted explicitly after the target monitor is known (also for `-Djx.monitor` and `-Djx.scale`)
- Skia scales the canvas (`JXSkiaRenderer.paint(root, canvas, w, h, scale)`), NanoVG passes the scale as
  `devicePixelRatio` to `nvgBeginFrame`, the software painter multiplies coordinates and rasterizes glyphs at
  `size * scale`; text and shapes are drawn at the physical resolution, not enlarged
- at scale 1 nothing changes: the goldens (`*-paint.txt`, `controls-skia.png`) are untouched
- pixel snapping stays in logical pixels (JavaFX 21 algorithms at snap scale 1, in `JXBoxLayout` and
  `JXGridLayout`): at 150% a border can sit on a half physical pixel. Snapping to physical pixels would change
  layout results and the text measures that depend on them, and was left out
- not verified on a real HiDPI display or with a monitor change (see the changelog entry)

## Desktop window behaviours

What a user expects of a desktop window, in `JXWindow` without an input listener. The decisions are made in code free of
GLFW and covered by tests (`JXOverlaysTest`, `JxmlComponentBehaviorTest`); the GLFW calls are compiled, not yet run in
a real window (see the changelog entry of each).

- **Mouse cursor.** `JXCursor.at(root, x, y)` (also `JXNativeNode.cursorAt`) decides the shape: the `cursor` prop of the
  deepest element that has one (`default`, `text`, `hand`, `crosshair`, `h-resize`, `v-resize`), else the I-beam over an
  enabled text field, password field or text area, the hand over an enabled hyperlink, the horizontal or vertical resize
  cursor over a split pane divider that can be dragged (it has an `onChange`), else the arrow; what is open over the window
  (a menu, a popup) is asked first. `JXWindow` shows it with the GLFW standard cursors, each created the first time it is
  needed and destroyed with the window; it follows the pointer while no button is held and is recomputed on the release.
  Table column borders are not resizable by the pointer today, so they have no cursor.
- **Window icon and state.** `setIcon(w, h, argb)`, `setIcons(List<JXWindowIcon>)` and `setIconFile` / `setIconFiles`
  (pictures decoded by `JXImages`, one per size) set the icon when the window opens or at once. `setResizable`,
  `setDecorated`, `setFloating` (`setAlwaysOnTop`) now also work on an open window; new: `setMaximized`, `setIconified`
  / `minimize`, `restore`, `setFullScreen` (primary monitor, back to the windowed bounds), `requestFocus`, and the
  getters `isMaximized`, `isIconified`, `isFullScreen`, `isResizable`, `isDecorated`, `isFloating`, `getX`, `getY`
  (kept up to date by the GLFW callbacks). Every setter posts to the display thread like `setTitle`.
- **File drop.** `glfwSetDropCallback` hands the paths to `JXNativeNode.dispatchDrop(x, y, paths)` (logical pixels, the
  pointer position at the drop): the nearest node at or above the point with an `onDrop` `Consumer` gets an unmodifiable
  `List<String>`; disabled nodes are skipped, and under an open dialog or drawer only its content is a target. A window with
  an input listener gets no drop (there is no drop event in `JXInputEvent`).
- **Menus by keyboard.** F10, or Alt pressed and released alone (`JXAltTap` tells a tap from Alt+key or Alt+click),
  opens the first enabled menu of the first `MenuBar` with its first row highlighted (`JXNativeNode.toggleMenuBar()`); the
  popup navigation of `JXOverlays` does the rest. The Menu key and Shift+F10 open the `ContextMenu` of the focused node.
  There is no mnemonic notion in the menu titles, so no Alt+letter.
- **Accelerators.** `accelerator="Ctrl+S"` on a `menuitem` or `button` (`JXAccelerator` parses it, `JXAccelerators.run`
  finds the item): called from `JXOverlays.key` after an open menu or popup and a selecting list, and unless a focused text
  field edits with the key; the action runs from anywhere in the window. The menu draws the text at the right of its
  row (`JXOverlayPaint`), as before.
- **Picker popups.** The calendar, the time cells and the color swatches highlight what is under the pointer
  (`JXOverlays.hover`; the node's `highlight` is the cell index, `highlightDate` the epoch day of the calendar) and take the
  arrows, Home, End, Page Up / Down, Enter and Space while open (`navigatePicker`; cells move to the nearest neighbour in
  the direction, `JXOverlays.neighbour`). The calendar draws the highlighted day (`JXOverlayPaint.HOVER`), the time grid
  as the combo list does, the swatches with a dark frame.

## Current limitations

- translate, scale and rotate are drawn by Skia and NanoVG (the software renderer moves and scales uniformly, no rotation); shape
  transitions (`FillTransition`, `StrokeTransition`, `PathTransition`) are not supported
- CSS: no combinators beyond descendant and child, no media queries, gradients keep all stops (repeat/reflect cycles and a radial focus are ignored)
- a child spanning a fixed-size (`USE_PREF_SIZE`) track in a centred GridPane can be placed 1 px
  off JavaFX (documented in `JXGridLayout`)
- wrapped labels take the width set on them; JavaFX's content bias (height for the width the
  parent gives) is not modelled in the box layouts
- no `TreeView` (use `Tree`)
- platform screen-reader adapters are not implemented
- `jxparallel-fx` builds with JDK 8 (bundled JavaFX 8); `jxparallel-ui` tests need JDK 9+ for
  Skia (Skija does not load on Java 8, where NanoVG draws)

## Scalability guarantees

The native UI creates no thread for animations: `transition` and `enter` are advanced by the window on the
display thread, at the time of each frame, and the window stops asking for frames when none is left (the deprecated
`JXAnimationScheduler` keeps its shared daemon timing executor, one per process).

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

## Fonts and text

Text goes through one pipeline: `JXTextEngine.shape(text)` splits the string into runs (a bidi level from
`java.text.Bidi`, a script, and the font face that has each character), HarfBuzz shapes each run, and the runs come back in
visual order as a `JXShapedText`. `JXTextEngine.width` is the sum of the run advances and the painters draw those same
runs, so a label measures what it draws.

- families: `JXFonts.register(family, File | InputStream)`, `registerResource(family, "/fonts/x.ttf")`; installed fonts are
  found by family name in the system font folders (scan once, cached; `-Djx.font.dirs=a;b` adds folders). A request is
  `JXFont(family, weight, italic)`; the nearest face wins (italic first, then weight). No synthesized bold or italic, no
  variable-font axes (the default instance is used)
- props: `fontFamily`, `fontWeight`, `italic` next to `bold` and `fontSize`, per node, **not inherited** (neither are
  `fontSize` and `textFill`; there is no inheritance mechanism for text props in the renderer)
- Skia: simple text (one left-to-right run in the requested face) is drawn exactly as before; other text is drawn from
  the HarfBuzz glyph ids and positions, one font per run; colour emoji work where the system has a colour font (Segoe UI
  Emoji on Windows seen)
- software renderer: the same runs through FreeType; outlines only, so bitmap-only emoji fonts (Noto Color Emoji) draw nothing
- NanoVG: one font per run placed at the HarfBuzz pen position; it does not shape, so ligatures and Arabic joining are
  missing, and a right-to-left run is fed in reverse character order (right for Hebrew, unjoined for Arabic). Not run on
  32-bit
- fallback: registered fonts first, then well-known system families for the kind of character (emoji, CJK, other), then one
  face of every installed family; the result is cached per code point, bold and italic. Marks, joiners, variation selectors
  and skin tone modifiers stay with the character before them
- right-to-left: the base direction is the first strong character; the text is not aligned differently. Caret movement,
  selection and hit testing inside right-to-left runs still follow the logical order (left out)

## Text input from the operating system (IME)

`JXWindow` gets characters from GLFW 3.3's `glfwSetCharModsCallback`, one call per Unicode code point the OS delivers.
What that means, plainly:

- **supported**: text the OS has already composed and committed reaches the focused field as characters: dead keys and
  compose sequences (`'` then `e` gives one `é`), AltGr characters, and the committed result of an input method (a
  Chinese, Japanese or Korean IME inserts its final text when the user picks a candidate); supplementary code points
  (emoji, rare CJK) arrive as one event and are inserted, deleted, moved over, selected and clicked as one character
  (`JXKeyEvent.typed`, `getKeyCodePoint`; `JXChangeEvents.edit`, `JXTextDocument`, `JXTextHit`, wrapping and ellipsis).
  Nothing in the window drops a character, except the one an open menu or popup took with its key
- **not supported**: inline composition. GLFW 3.3 has no pre-edit API, so the text the user is still composing (the
  underlined pre-edit string, the clause being converted) is shown by nobody: the IME's own window may show it, and the
  field stays unchanged until the text is committed
- **not supported**: the IME candidate window is not positioned at the caret (GLFW 3.3 has no function for it; it
  appears where the OS puts it)
- **not supported**: reconversion, and the IME state (on/off, mode) is not read or changed
- backspace, delete and the arrow keys move by code point, not by grapheme cluster: an emoji made of several code points
  (a flag, a skin tone, a ZWJ family) or a letter with separate combining marks takes more than one key press; a password
  field shows one bullet per UTF-16 unit, so an emoji is two bullets
- not verified in a real window with an IME: only the code path from the callback to the edit is tested, with events
  built in the tests

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

// animation is declared on the element: a kept node moves from the value it shows to the new prop,
// a new node from its entry values (JXAnimationScheduler and JXAnimation are deprecated)
JXElement.of("label", JXProps.builder()
    .set("opacity", visible ? 1.0 : 0.0)
    .set("transition", "opacity 200ms ease-out")
    .set("enter", "opacity 0 200ms, translateY 12 200ms")
    .build());
```

`transition` and `enter` animate `opacity`, `translateX`, `translateY`, `scaleX`, `scaleY`, `rotate`, `background` and
`textFill` (JXML: `transition="opacity 200ms ease-out"`, `enter="opacity 0 200ms"`; see `jxforge.md`). `JXWindow`
advances them on the display thread at each frame and asks for another frame only while one runs;
`-Djx.animations=false` makes every one jump. Exit animations are not provided.
