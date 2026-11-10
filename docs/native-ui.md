# Native UI architecture

`jxparallel-ui` is a UI toolkit that does not use JavaFX, AWT or Swing. It keeps a scene graph of
its own, lays it out, and draws it with Skia, NanoVG or a CPU painter into a GLFW window (LWJGL
3.3.3). `ArchitectureTest` in the module fails the build if a class of `jxparallel-ui` or
`jxparallel-core` depends on `java.awt`, `javax.swing`, `javafx` or the JavaFX-based modules.

Screens are normally written as JXML and compiled by JXForge ([jxforge.md](jxforge.md)); this page is
about what the toolkit under them does. [native-ui-progress.md](native-ui-progress.md) covers
`jxparallel-fx`, the JavaFX-compatible API that can run on this toolkit.

## From a description to pixels

```text
JXComponent.render() / generated JXML view
        |
        v
JXElement            immutable description: a type, props, children (jxparallel-core)
        |   JXWindow.setContent(element)
        v
JXNativeNode         mounted tree: bounds, cached sizes, children (jxparallel-ui, package native2d)
        |   layout, then one pass of JXPaint over the tree
        v
JXPainter            drawing primitives, implemented by:
        |            JXSkiaPainter (Skia/Skija), JXNanoVGPainter (NanoVG), JXSoftwarePainter (CPU)
        v
GLFW window + OpenGL context        (software renderer: a GDI bitmap, no OpenGL)
```

- **Elements** are `JXElement` values from `jxparallel-core` (`com.jxparallel.ui`): a type name such as
  `column`, `button` or `input`, a props map, and children. A `JXComponent` is anything with a
  `render()` that returns one.
- **Mounting.** `JXWindow.setContent` reconciles a new element tree into the mounted
  `JXNativeNode` tree: nodes of the same type are reused and only their props are replaced; the layout
  of a node is invalidated only where a size can change; an element that is the same instance as
  before (a memoized render, `JXRenderMemo`, or a subtree JXForge hoisted) is skipped. A root of
  another type is mounted anew. `setContent` may be called from any thread.
- **Painting** is one piece of code, `JXPaint`, written against the `JXPainter` interface, so the
  three renderers draw the same shapes. Nodes outside the window are not painted
  (`JXPaintCullingTest` checks that skipping them changes no pixel).
- **Threads.** `JXDisplay` is the one thread that talks to GLFW for the whole process. Windows are
  created, painted and polled there; other threads post tasks to it. It sleeps until input, a task or a
  render request arrives.

## The window

`JXWindow` opens a native window. `show()` blocks until it closes, `open()` returns at once, and
`awaitClosed()` waits. It chooses its renderer when created:

| Renderer | Chosen when | Needs |
|---|---|---|
| Skia (`skia`) | a 64-bit JVM on Java 9 or newer | Skija 0.116.4; its native library comes with the OS-activated profiles of `jxparallel-ui` (Windows x64, Linux x64, macOS x64/arm64) |
| NanoVG (`nanovg`) | a 32-bit JVM, or Java 8 | LWJGL's NanoVG; natives for Windows x64, Windows x86 and Linux x64 come with the same profiles |
| Software (`software`) | only when asked with `-Djx.renderer=software`, on Windows | GDI; no OpenGL context |

`-Djx.renderer=skia|nanovg|software` forces one (Skia also works on Java 8 64-bit);
`-Djx.monitor=N` opens windows on another monitor. The software renderer repaints only the rectangles
that changed since the previous frame (`JXDamagePainter`); `-Djx.software.damage=false` repaints
everything, and `-Djx.software.verify=true` also paints each frame whole and reports a pixel that
differs. Skija publishes no 32-bit native library, which is why 32-bit JVMs use NanoVG.

### Platforms

| OS and CPU | Maven profile | Renderer | State |
|---|---|---|---|
| Windows x64 | `natives-windows-x64` | Skia (Java 9+), NanoVG (Java 8), software | run and measured |
| Windows x86 | `natives-windows-x86` | NanoVG | run and measured |
| Linux x64 | `natives-linux-x64` | Skia, NanoVG | unit tests in CI; window not run by the maintainers |
| macOS x64 | `natives-macos-x64` | Skia, NanoVG | **configured, awaiting CI; not run by anyone yet** |
| macOS arm64 | `natives-macos-arm64` | Skia, NanoVG | **configured, awaiting CI; not run by anyone yet** |
| Linux arm64 | `natives-linux-arm64` | NanoVG (Skija 0.116.4 has no artifact) | **configured, awaiting CI; not run by anyone yet** |
| Windows arm64 | `natives-windows-arm64` | NanoVG (Skija 0.116.4 has no artifact) | **configured, awaiting CI; not run by anyone yet** |

An x64 JVM on an ARM machine (Rosetta, Windows x64 emulation) takes the x64 profile.

**macOS.** GLFW creates windows and runs the event loop only on the process's first thread, which conflicts with
the dedicated "JX Display" thread used elsewhere. On macOS there is no such thread: start the JVM with
`-XstartOnFirstThread` and let the main thread be the display thread:

```java
public static void main(String[] args) {
    JXDisplay.runOnMainThread(() -> {
        JXWindow window = new JXWindow("App");
        window.setContent(screen());
        window.open();             // not show(): this is already the display thread
    });                            // returns after the last window is closed
}
```

`window.show()` on the main thread does the same for one window and blocks until it closes. Other threads can
still `JXDisplay.post(...)` and `call(...)`. Without `-XstartOnFirstThread`, or when a window is opened from
another thread first, the error says what to do instead of crashing in AppKit. `runOnMainThread` is accepted on
other systems too (it runs the setup and waits for the windows). Do not use AWT or Swing in the same process on
macOS with `-XstartOnFirstThread`. On macOS the OpenGL context is requested as core profile 3.2, forward
compatible (the only kind above 2.1 there); Windows and Linux keep GLFW's defaults. The framebuffer is the Retina
one and `JXScale` converts window units to pixels. "Shortcut" means Cmd there: text editing (copy, cut, paste,
select all, undo), Cmd+click in lists and `accelerator="Shortcut+S"` use `JXInputEvent.SHORTCUT`; `Ctrl+S` stays
literally Ctrl, so write `Shortcut+S` for a key that should be Cmd on macOS and Ctrl elsewhere. Fonts: Arial from
`/System/Library/Fonts/Supplemental` (or `-Djx.font`). None of this has run on a Mac.

Input: without an input listener, a primary click calls the `onClick`/`onAction` handler of the
deepest node under the pointer and keys go to the node last clicked. With `setInputListener`, every
raw event (pointer, key, character, focus, close request) goes to the listener instead.
Hit testing skips hidden and mouse-transparent nodes, and open overlays are tried first. Like a JavaFX `Pane` (`pickOnBounds=false`), the empty area of a layout pane (`Column`, `Row`, `Stack`, `Border`, `Grid`, `Pane`, `Anchor`, `Flow`, `Tile`) is not hit unless it has a `background`, `borderColor`, `theme`, `onClick`, `onAction`, `onHover`, `onDrop`, `onScroll`, `onKeyPressed`, `tooltip`, `cursor` or `focusable`, so a `Stack` over the screen holding only closed dialogs does not take the pointer; the window root and controls always are. GLFW errors go to stderr through `JXGlfwErrors`, except `GLFW_FORMAT_UNAVAILABLE`, which reading an empty or non-text clipboard raises and which is not a fault (the read returns null and a paste inserts nothing).

## What it draws

- **Layout.** Containers `column`, `row`, `stack`, `border`, `grid`, `pane`, `anchor`, `flow`, `tile`,
  `splitpane`, `scroll`, `tabs`, `titled`, `accordion`, with minimum, preferred and maximum sizes,
  grow priorities, gaps, padding and alignment. `column`, `row` and `stack` follow the JavaFX VBox, HBox
  and StackPane algorithms (`JXBoxLayout`); the other layouts are in `JXGridLayout`, `JXFlowLayout`,
  `JXTitledLayout` and `JXControlLayout`.
- **Controls.** Labels, buttons (filled, tonal, outlined, text, elevated, and a round floating
  button), text fields, password fields, text areas, check boxes, radio and toggle buttons, switches,
  combo boxes, spinners, sliders, scroll bars, progress bars and indicators, date, time and color
  pickers, chips, badges, avatars, icons, steppers, tab bars, pagination, titled panes, hyperlinks,
  tooltips, images (from a PNG, JPEG, GIF or BMP file through `JXImages`, or from pixels the application supplies) and the cells of lists and tables. The element
  types are the ones `JXControlLayout` and `JXPaint` switch on; JXML's tag list is in
  `JxmlSchema` and [jxforge-1.0.md](jxforge-1.0.md).
- **Looks.** `JXPaint` paints the JavaFX Modena look, with colors and insets measured on JavaFX 21.
  The Material-style controls (switch, chip, badge, tonal and outlined buttons and so on) use the six
  tokens of `JXTheme` (`primary`, `onPrimary`, `surface`, `onSurface`, `outline`, `error`), light or
  dark, chosen by a `theme` prop on a node for it and everything below.
- **Virtualized collections.** `list` and `table` take the number of items and a window of rows
  (`first`, `rows`), so only the rows in view reach the renderer; the application, or the JXML `<For>`
  around the row template, supplies those. `Tree` and `TreeTable` are flattened into the same rows.
- **Overlays** (`JXOverlays`): the popups of combo boxes and of the date, time and color pickers,
  menus (`menubutton`, `contextmenu`, `menubar`, with submenus), dialogs, drawers and snack bars. They
  are drawn after everything else and take the pointer first. Escape closes the topmost one and Enter runs
  the default button of an open dialog. The pointer highlights the option or menu row under it, and the arrows,
  Home/End, letters, Enter and Escape move through combo lists, menus, the calendar and the time and color grids;
  F10 or Alt opens the menu bar. Tab and Shift+Tab move the focus through the controls.
- **Text.** `JXTextEngine` measures text with HarfBuzz (kerning, ligatures, complex scripts) from the
  font file both renderers draw with, on the CPU and without a window. It is the only code allowed to
  use HarfBuzz or FreeType, so layout and the renderers agree on text sizes. `JXTextDocument` holds the caret,
  selection and editing for text inputs; `JXClipboard` is the clipboard. File dialogs are in
  `JXFileDialogs` (tinyfd).

## Writing elements by hand

Components written in Java return elements and nest freely with generated JXML views:

```java
JXPane screen = new JXPane(12);
screen.add(new JXLabel("Customers"));
screen.add(new JXButton("Load"));

JXWindow window = new JXWindow("JXParallel");
window.setContent(screen.render());
window.show();
```

`JXLabel`, `JXButton`, `JXTextField`, `JXTextArea`, `JXPasswordField`, `JXCheckBox`, `JXComboBox`,
`JXSlider`, `JXProgressBar`, `JXToggleButton` and `JXPane` are the hand-written components of
`jxparallel-ui`. `JXMarkupLoader` builds elements from plain XML: every tag becomes an element of that
type and every attribute a string prop, with no conversion. JXML is the markup that converts numbers,
booleans and colors and checks names, so screens should use it.

## Other parts of the module

- `JXResourceCache`: a bounded cache by entry count and by bytes, with hit, miss and eviction counters.
- `JXAccessibilityTree`: builds a tree of roles (`ROOT`, `GROUP`, `BUTTON`, `TEXT`, `TEXT_FIELD`,
  `CHECK_BOX`, `LIST`, `LIST_ITEM`, `UNKNOWN`) and accessible names from an element tree. Nothing
  passes it to the operating system yet.
- `JXStyleSheet`: a standalone helper with tests. The renderer does not read a `JXStyleSheet`; JXML styles are
  resolved when a file is read (see [jxforge.md](jxforge.md)).
- Animation is declarative: the props `transition` and `enter` (parsed by `JXTransitions` in `jxparallel-core`) make a
  kept node move from the value it shows to its new prop, and a new node from its entry values, for `opacity`,
  `translateX`, `translateY`, `scaleX`, `scaleY`, `rotate`, `background` and `textFill`. A node holds the animated values
  (`JXNodeAnimation`), `getProperty` answers with them, so painting, transforms and picking agree; `JXWindow` advances
  them on the display thread at each frame (`JXAnimationClock`, `JXAnimationDriver`), keeps asking for frames only while
  one runs, and `-Djx.animations=false` makes everything jump. `JXAnimation` and `JXAnimationScheduler` are deprecated:
  the scheduler ticks on a thread of its own, and only the display thread may touch nodes. See
  [jxforge.md](jxforge.md#animation).

## Rules for new code

1. New controls belong in `jxparallel-ui`, not in the JavaFX bridge modules.
2. Only package `native2d` may use GLFW, OpenGL, Skia or NanoVG, and only `text` may use HarfBuzz or
   FreeType (both are `ArchitectureTest` rules). `jxparallel-core` uses no renderer library.
3. A new element type needs its size in `JXControlLayout`, its drawing in `JXPaint`, a test, and an entry
   in `JxmlSchema` if JXML should have a tag for it.
4. Comparisons with JavaFX are tests of compatibility, never an implementation dependency.

## Not done

- Exit animations (a removed node leaves at once), keyframes, `cubic-bezier(...)`, springs and animated layout; the
  implicit `transition` and `enter` above are what exists.
- Screen-reader adapters for Linux (AT-SPI) and macOS: the accessibility tree, actions and events are
  platform-neutral, and the only platform adapter is the opt-in Windows UI Automation one ([accessibility.md](accessibility.md)).
- SVG images, and inline IME composition (committed text only, see [native-ui-progress.md](native-ui-progress.md)).

How the toolkit is tested: [testing-strategy.md](testing-strategy.md).
