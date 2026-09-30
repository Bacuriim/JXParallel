# DeviceConfig-shaped screen: plain JavaFX vs JX API on JavaFX vs JX native, 2026-09-29

The question for the TCC: what does running an application on JXParallel's native mode cost or
save, compared with the same application on JavaFX? The real DeviceConfig screens need its
homologation database, so this measures a screen built like them, with the same code on three
stacks:

- **Plain JavaFX**: `PureFxBench`, the application with `javafx.*` imports (DeviceConfig today).
- **JX API on JavaFX**: `FxBackendBench` on `com.jxparallel.fx` with the default backend, every JX
  object backed by a JavaFX one (DeviceConfig after phase 1 of the migration).
- **JX native**: the same `FxBackendBench` with `-Djx.backend=native` (phase 2b). JavaFX is only
  the event loop; NanoVG (Java 8) draws.

`PureFxBench` is generated from `FxBackendBench` by `scripts/make-purefx-bench.py`, which only
swaps the imports, so the three run identical application code.

## Setup

| | |
|---|---|
| Machine | Intel Core i5-12400F (6 cores, 12 threads), 15.8 GB RAM, Windows 11 Enterprise |
| GPU and display | NVIDIA GeForce RTX 3060, 2560x1440 at 239 Hz |
| JVM 32-bit | Oracle JDK 1.8.0_51 x86, client VM, JavaFX 8 bundled (DeviceConfig's runtime) |
| JVM 64-bit | Oracle JDK 1.8.0_421 x64, server VM, JavaFX 8 bundled |
| Native renderer | NanoVG on OpenGL (chosen automatically on Java 8) |
| Runs | 7 per stack and JVM, fresh JVM each, stacks alternating; medians |
| Raw data | [x86 CSV](fx-backends-x86-2026-09-29.csv), [x64 CSV](fx-backends-x64-2026-09-29.csv), [x86 before the optimizations](fx-backends-x86-2026-09-29-baseline.csv) |

## Workload

One window, 1100x760. It has a toolbar and a tab pane with three tabs:

1. An accordion of 6 titled panes inside a scroll pane. Each pane is a grid of 10 labelled fields,
   cycling text field, combo box (10 items), check box, spinner, date picker, and text field with
   prompt.
2. A table of 10,000 records with five `PropertyValueFactory` columns over properties.
3. A list of 1,000 items.

Below the tabs is a status bar.

Phases, each waiting for the frames that show it:

1. **Build**: create the scene graph, show it, first complete frame.
2. **Records**: load 50 records into the 60 fields (DeviceConfig's "open a record").
3. **Tabs**: 30 tab switches.
4. **Scroll**: 200 `scrollTo` steps through the table.
5. **Sustained**: 600 frames, each updating the status label, the progress bar and a field.
6. **Big table**: replace the table items with 100,000 records and scroll to the middle.

A frame "shows" a change when it is the second frame presented after it: on JavaFX an
`AnimationTimer` pulse, on native the window's buffer swap (`NativeFrames`). Process memory is
sampled every 10 ms from outside the JVM (`scripts/measure-fx-backends.ps1`). CPU is reported as
the process total (graphics driver threads included) and, separately, as the JVM's own threads.

## 32-bit (DeviceConfig's environment)

| Metric | Plain JavaFX | JX API on JavaFX | JX native | Native vs plain JavaFX |
|---|---:|---:|---:|---:|
| JVM start to first complete frame | 1,109 ms | 1,198 ms | 1,089 ms | -2% |
| Build screen and show, to its frame | 709 ms | 768 ms | 835 ms | +18% |
| Build: CPU | 891 ms | 1,000 ms | 1,031 ms | +16% |
| Build: allocated | 59.9 MB | 64.6 MB | 37.2 MB | -38% |
| Load 50 records: application work | 120 ms | 127 ms | 17.97 ms | -85% |
| Load 50 records: process CPU | 844 ms | 906 ms | 562 ms | -33% |
| 30 tab switches, to frames | 477 ms | 473 ms | 322 ms | -33% |
| 200 table scrolls, to frames | 3,216 ms | 3,216 ms | 1,684 ms | -48% |
| 200 table scrolls: process CPU | 1,391 ms | 1,547 ms | 1,250 ms | -10% |
| 200 table scrolls: JVM threads CPU | 906 ms | 1,016 ms | 734 ms | -19% |
| 200 table scrolls: allocated | 147 MB | 159 MB | 385 MB | +161% |
| Sustained 600 frames: process CPU | 1,938 ms | 1,438 ms | 1,500 ms | -23% |
| Sustained: JVM threads CPU | 1,062 ms | 938 ms | 875 ms | -18% |
| Sustained: application thread CPU | 266 ms | 469 ms | 609 ms | +129% |
| Sustained: render thread CPU | 734 ms | 500 ms | 219 ms | -70% |
| Sustained: allocated | 21.7 MB | 21.5 MB | 360 MB | +1561% |
| Frame interval p95 | 6.95 ms | 6.96 ms | 4.28 ms | -38% |
| Frame interval p99 | 7.89 ms | 7.80 ms | 4.44 ms | -44% |
| Worst frame | 12.14 ms | 15.55 ms | 44.0 ms | +263% |
| 100k-row table: items to frame | 93.7 ms | 125 ms | 124 ms | +33% |
| Peak working set | 141 MB | 154 MB | 159 MB | +12% |
| Peak private bytes | 196 MB | 217 MB | 205 MB | +5% |
| Peak heap | 58.8 MB | 64.3 MB | 50.6 MB | -14% |
| Heap live after GC | 44.4 MB | 52.6 MB | 42.2 MB | -5% |
| Total allocated | 312 MB | 339 MB | 933 MB | +199% |
| GC collections | 36.0 | 36.0 | 138 | +283% |
| GC time | 194 ms | 234 ms | 255 ms | +31% |
| Classes loaded | 3,550 | 3,695 | 3,210 | -10% |
| Total process CPU | 5.72 s | 5.56 s | 5.03 s | -12% |
| Total JVM threads CPU | 3.62 s | 3.70 s | 3.23 s | -11% |

## 64-bit

| Metric | Plain JavaFX | JX API on JavaFX | JX native | Native vs plain JavaFX |
|---|---:|---:|---:|---:|
| JVM start to first complete frame | 1,169 ms | 1,244 ms | 1,204 ms | +3% |
| Build screen and show, to its frame | 729 ms | 778 ms | 888 ms | +22% |
| Build: CPU | 2,250 ms | 2,516 ms | 1,578 ms | -30% |
| Build: allocated | 59.9 MB | 65.0 MB | 39.9 MB | -33% |
| Load 50 records: application work | 109 ms | 173 ms | 18.41 ms | -83% |
| Load 50 records: process CPU | 2,812 ms | 3,047 ms | 1,547 ms | -45% |
| 30 tab switches, to frames | 578 ms | 480 ms | 268 ms | -54% |
| 200 table scrolls, to frames | 3,200 ms | 3,216 ms | 1,779 ms | -44% |
| 200 table scrolls: process CPU | 4,156 ms | 4,062 ms | 3,453 ms | -17% |
| 200 table scrolls: JVM threads CPU | 781 ms | 906 ms | 891 ms | +14% |
| 200 table scrolls: allocated | 166 MB | 179 MB | 287 MB | +74% |
| Sustained 600 frames: process CPU | 2,250 ms | 2,344 ms | 1,203 ms | -47% |
| Sustained: JVM threads CPU | 812 ms | 859 ms | 594 ms | -27% |
| Sustained: application thread CPU | 281 ms | 297 ms | 234 ms | -17% |
| Sustained: render thread CPU | 500 ms | 484 ms | 266 ms | -47% |
| Sustained: allocated | 22.6 MB | 22.6 MB | 213 MB | +841% |
| Frame interval p95 | 7.22 ms | 7.12 ms | 4.28 ms | -41% |
| Frame interval p99 | 8.09 ms | 8.06 ms | 4.39 ms | -46% |
| Worst frame | 11.68 ms | 11.45 ms | 6.71 ms | -43% |
| 100k-row table: items to frame | 24.2 ms | 33.0 ms | 44.5 ms | +84% |
| Peak working set | 291 MB | 315 MB | 285 MB | -2% |
| Peak private bytes | 618 MB | 641 MB | 636 MB | +3% |
| Peak heap | 86.1 MB | 105 MB | 75.3 MB | -13% |
| Heap live after GC | 48.3 MB | 60.6 MB | 48.3 MB | -0% |
| Total allocated | 326 MB | 358 MB | 670 MB | +105% |
| GC collections | 6.0 | 6.0 | 12.0 | +100% |
| GC time | 115 ms | 73.0 ms | 81.0 ms | -30% |
| Classes loaded | 3,603 | 3,747 | 3,286 | -9% |
| Total process CPU | 12.81 s | 12.72 s | 9.45 s | -26% |
| Total JVM threads CPU | 3.36 s | 3.70 s | 3.38 s | +0% |

## Reading the numbers

- **Where native is faster, it is by a lot, and on both JVMs.** Loading a record into 60 fields
  costs the application 83 to 85% less (18 against 109 to 120 ms for 50 records), tab switches
  take a third to a half less time and scrolling the table takes about half the time. Frame pacing is tighter: p99 4.4 ms against
  7.8 ms. At 239 Hz a frame lasts 4.2 ms, so native keeps up where JavaFX misses about one frame in
  several.
- **CPU.** On 32-bit, native uses 12% less process CPU over the whole run; on 64-bit 26% less, and
  47% less while updating on every frame. The JVM-threads rows show where it goes: native's render
  thread needs 47 to 70% less. Its application thread needs 17% less on 64-bit but more than twice
  as much on 32-bit, where the client VM makes the per-frame rebuild of the element tree costlier.
- **Memory.** Fewer classes (-10%), a smaller peak heap (-13%) and the same or a smaller live heap.
  The process working set is 12% larger on 32-bit (159 vs 141 MB) and the same on 64-bit.
- **Where native is worse:**
  - **Allocation.** 2 to 3x in total, 9 to 17x while updating on every frame. Each frame rebuilds the element
    tree of the visible screen and reconciles it, where JavaFX only changes the nodes that changed.
    It shows as 4x the young collections on 32-bit (138 vs 36; 255 vs 194 ms in total). Memoizing
    the elements of unchanged subtrees is the next optimization.
  - **Building the screen** takes 18 to 22% longer to its first frame.
  - **Replacing the items with 100,000 rows** is 33 to 84% slower.
  - **Worst frame on 32-bit.** One frame of 44 ms in the sustained phase, the first paint of the
    form after coming back from the table tab (the NanoVG painting path warming up). There is no
    such frame on 64-bit.
- **The JX layer over JavaFX (phase 1) costs little but not nothing**: 7 to 8% on building, 8 to
  9% on working set, and on 64-bit 59% more application time to load records (173 vs 109 ms, the
  conversions at every call); the rest is within noise. The gains come from the native backend,
  not from the migration itself.

## Measurement-driven fixes (same day)

The first measurement on 32-bit found the native backend using 38% more CPU and 7x the
allocation of the JX API on JavaFX. Profiling (`hprof` CPU samples and allocation sites on
Java 8) located the causes. All are fixed and covered by the existing tests.

| Cause | Fix | Effect (32-bit, native) |
|---|---|---|
| `Class.getSimpleName()` is not cached on Java 8; every generated API call built its dispatch key with it | simple names kept on the model; dispatch keys cached per method | part of the CPU drop below |
| getters of unset properties looked up JavaFX's default by reflection and threw `NoSuchMethodException` each time | defaults cached, misses included | part of the CPU drop below |
| each table cell created a `CellDataFeatures` and called the value factory on every frame | the observable value is taken once per row item and read each frame, as a JavaFX `TableCell` listens to it | scroll: 3.9 to 1.3 s CPU, 1.58 to 0.39 GB allocated |
| `glfwMakeContextCurrent` on every frame (a costly `wglMakeCurrent`) | only when another context is current | render thread CPU while updating: about 1.2 to 0.22 s |
| JavaFX initialised its Direct3D pipeline although it draws nothing in native mode | `prism.order=sw` by default in native mode | working set 174 to 159 MB, private bytes 251 to 205 MB, CPU -10% |
| props maps copied twice per element, coordinates boxed per node per frame, an iterator per painted node | the builder's map handed over (copy on write), coordinates stored only when they move, indexed loops | allocation |

Overall on 32-bit, the native backend went from 7.92 to 5.03 s of process CPU, from 2.25 to
0.93 GB allocated, from 317 to 138 collections and from 186 to 159 MB of working set.

## Incremental rendering (after this report)

The per-frame rebuild of the element tree, the main remaining cost above, is gone: like JavaFX
syncing only its dirty nodes, a frame now rebuilds the elements of the nodes that changed and of
their ancestors, and reuses every other element as the same instance, which the reconcile then
skips (`NativeElements.toElement`, `NativeModel.version`). What invalidates an element:

- a property or list of the node, a change of its own state;
- for properties CSS matching reads or descendants inherit (classes, id, style, disabled, the
  pseudo-class states), and for a node that moved, everything below it too;
- a change of hover, pressed or focus state;
- the value a table cell shows (it now listens to it, like `TableCell`);
- a spinner's value factory;
- an image that finished loading;
- the scene's stylesheets (everything).

These runs were taken while the Windows session was locked, where JavaFX drops to about 32 frames
per second and the native window's swap stops waiting for vsync, so throughput and frame pacing
are not comparable with the tables above. Allocation and application-thread work per frame are,
so these are the numbers reported (32-bit, native, medians of 5 runs):

| Metric | Native, tables above | Native, incremental | Plain JavaFX, tables above |
|---|---:|---:|---:|
| Sustained 600 frames: allocated | 360 MB | 38 MB | 22 MB |
| Sustained: application thread CPU | 609 ms | 125 ms | 266 ms |
| Load 50 records: allocated | 62 MB | 28.5 MB | 25 MB |
| 30 tab switches: allocated | 19 MB | 11 MB | 1.9 MB |
| 200 table scrolls: allocated | 385 MB | 299 MB | 147 MB |
| Total allocated | 933 MB | 481 MB | 312 MB |
| GC collections | 138 | 40 | 36 |

## The remaining losses (after incremental rendering)

The same locked session, so again work and allocation rather than pacing. 32-bit, native, 3 runs:

- **Building the screen to its first frame**: 790 ms (tables above) to about 435 ms, where plain
  JavaFX takes 670 ms under the same conditions. From JVM start to the first complete frame: about
  650 ms against 1050 ms. Most of the build was not building elements. The display thread only
  started with the first `show()`, and then loaded the OpenGL driver while creating the window,
  after the application had built its screen. `JXDisplay.prewarm()` now does that, and loads fonts
  and painting classes, on its own thread as soon as native mode starts, in parallel with
  `Application.start()`, as JavaFX starts its render thread with the toolkit.
- **Scrolling the table**: 299 MB to 210 MB allocated (plain JavaFX: 147 MB). Rows and cells
  scrolled out of view are now kept and reused for the rows that scroll in, like JavaFX's
  `VirtualFlow`, instead of a new row of cells per row shown: 40 pages of a table now use the
  same few dozen cell objects. The rest is the element of each reused cell, rebuilt because it
  shows another item, where JavaFX updates the existing node.
- **Replacing the items with 100,000 rows**: native (124 ms) is as fast as the JX API on JavaFX
  (125 ms). The gap to plain JavaFX (94 ms) is the application's own rows. Each JX property is a
  JX object over a JavaFX peer, two objects where plain JavaFX has one; that belongs to the
  phase-1 API, not to native rendering.
- **The 44 ms frame on 32-bit** was the first paint of the form after coming back from the table
  tab. It cannot be checked while the session is locked (frames are not paced by the display);
  the painting classes are now loaded in advance.

The complete three-way measurement is to be repeated with the session unlocked.

## Scrolling that moves nodes instead of rebuilding them (keyed reconcile)

Before, the tree of drawn nodes was reconciled by position. After a scroll by one row, the node in
slot 0 received the element of the row that had been in slot 1, and so on down the table: every
visible row was updated even when nothing in it had changed. Now every element carries a `key`
(its `NativeModel`). When all children of a node have distinct keys, `JXNativeNode.reconcile`
matches them by key. A row that stays on screen keeps its node, which the layout only moves, and
its element is the same instance as before (`NativeCells.rowElement` memoizes it on the row's
version, selection, parity, focus and cell elements), so the reconcile skips it. Only the rows
that scroll in are updated, as JavaFX's `VirtualFlow` does.

Measuring this turned up a regression in the invalidation. A cell refers to its table
(`tableView`), and that reference was treated as the table moving to a new parent. The table's
whole subtree was invalidated each time, including the rows and cells kept in its pool. Scroll
allocation rose to 734 MB. Reference properties (`tableView`, `tableRow`, `tableColumn`,
`listView`, `toggleGroup`, `owner`...) no longer count as reparenting. The regression is covered
by `NativeIncrementalTest.aCellPointingAtItsTableLeavesTheTableRowsAlone`.

32-bit, native, locked session (allocation and work only):

| Scroll | Positional | Keyed | Plain JavaFX |
|---|---:|---:|---:|
| 200 steps of 1 row (`-Dbench.table=200`), allocated | 36.2 MB | 31.1 MB | 26.0 MB |
| 200 steps of 50 rows (default), allocated | 202 MB | 206 MB | 147 MB |

These are 3 runs for the one-row scroll and 2 runs for the 50-row jumps. Process CPU for the
one-row scroll was 0.72 to 0.88 s keyed against 0.89 to 0.92 s positional. A jump of 50 rows
replaces every visible row, so keys cannot help there, and the two variants are within noise.
Most of the gap to JavaFX in that case is the element of each reused cell, rebuilt because the
cell shows another item.

## Closing the scroll gap: where the allocation went

With keyed reconcile in place, native scrolling still allocated 206 MB against plain JavaFX's
147 MB. The benchmark now also reports allocation per thread (`scroll_app_allocated_bytes`,
`scroll_render_allocated_bytes`). The application thread accounted for almost all of it: 186 MB
against JavaFX's 131 MB. The native render thread allocated less than JavaFX's (7 against 16 MB).
Each phase of `NativeScene.render` and of the table's build was then measured with
`ThreadMXBean.getThreadAllocatedBytes`. The hprof sites undercounted by about 8x and the 8u421
flight recorder writes the old format, so neither was usable. The findings, in order of
weight (400 scroll steps, measurement overhead included):

| Where | Before | After | Fix |
|---|---:|---:|---|
| Cell element: layout constraints | 187 MB | 45 MB | `common()` concatenated 14 constraint keys (`"AnchorPane." + side + "Anchor"`...) per element; keys are now constants, and the bold check of a font is cached |
| Cell value (`PropertyValueFactory`) | 73 MB | 5 MB | each row went through a JX `CellDataFeatures` with its JavaFX peer and two proxy adapters; the JX `PropertyValueFactory` is now read directly (`valueFor`), unless a subclass overrides `call` |
| Second build of the table per frame | 2x cells | 1x | setting a cell's index and selection while the table built its element made that element stale at once, so the next frame visited every cell again; invalidation now stops at the list or table being built (`NativeElements.building`) |
| CSS | 22 MB | 2 MB | the rules in scope, looked-up colors and the props of each set of matched rules are cached for the render; the cells of a table share a few sets |
| Reconcile | 30 MB | 19 MB | keyed matching compares pairs instead of building two identity maps per row, with no copy when the order is unchanged |
| Props | | | `JXProps` keeps keys and values in one flat array instead of a `LinkedHashMap` with entries and unmodifiable views; `JXElement` copies its children once |
| Boxing | | | number and flag properties are watched with invalidation listeners (no boxed old and new values); laid-out pixel coordinates reuse cached `Double`s |

A table cell also keeps one repaint listener instead of a new lambda for every row it shows.

The same measurement found a bug. A table's selected rows were never shown as selected in native
mode: the cell code only knew the list's selection model, not the table's.

### Result (32-bit, locked session, median of 3)

| Metric | Plain JavaFX | JX API on JavaFX | JX native |
|---|---:|---:|---:|
| Scroll, allocated (200 steps of 50 rows) | 147.6 MB | 159.4 MB | **51.9 MB** |
| of which application thread | 131.7 MB | 143.5 MB | 44.8 MB |
| of which render thread | 15.8 MB | 15.8 MB | 7.1 MB |
| Scroll, process CPU | 1.30 s | 1.55 s | 0.98 s |
| Scroll, one row per step (`-Dbench.table=200`), allocated | 26.1 MB | | 15.5 MB |
| Build to first frame | 632 ms | 688 ms | 422 ms |
| Records, allocated | 24.5 MB | 24.8 MB | 9.1 MB |
| Sustained updates, application thread CPU | 313 ms | 453 ms | 78 ms |
| Sustained updates, allocated | 21.7 MB | 21.5 MB | 18.2 MB |
| Tab switches, allocated | 1.9 MB | 1.9 MB | 6.0 MB |
| 100,000 rows, allocated | 48.7 MB | 57.5 MB | 54.7 MB |
| Allocated over the whole run | 312.6 MB | 338.7 MB | 185.2 MB |
| Live heap after GC | 46.6 MB | 55.1 MB | 44.7 MB |
| Peak working set | 146.8 MB | 159.5 MB | 166.9 MB |

The one-row scroll figures are from single runs before the median set. Frame pacing is still not
comparable while the session is locked (JavaFX at about 32 fps), so times that wait for frames
(`scroll_ns`) are not a result. Native mode still loses on tab switches: their content is rebuilt
when it is shown again. It also loses on 100,000 rows, where the cost is the JX API's objects,
and on peak working set.

## The last losses: tabs, 100,000 rows, memory, start-up CPU

| Loss | Cause | Fix |
|---|---|---|
| Tab switches allocated 6.0 MB (JavaFX 1.9) | selecting a `Tab` invalidated its whole form (`selected` restyles a subtree, and a tab's content counted as its child), and the native nodes of the tab that left were dropped and made again | a `Tab` is not its content's CSS parent in JavaFX (the tab pane's content region is), so its state and classes no longer reach the form (`disable` still does); the tab pane keeps the native nodes of tabs that leave (`retainChildren`, up to 16) |
| First show of each tab | JavaFX builds every tab's content with the scene; native mode built it on the first switch | the other tabs are built when the window is idle, one per turn, starting 300 ms after the first frame (`NativeScene.prebuildHiddenTab`, `JXNativeNode.retainAhead`) |
| 100,000 rows allocated 54.7 MB (JavaFX 48.7) | every JX property was a JX object plus a JavaFX peer, where JavaFX has one object | `Simple*Property` makes its JavaFX peer on first need (a listener, a binding, JavaFX reading it) and holds its value until then; subclasses still get theirs at once (`scripts/make-lazy-properties.py`) |
| Start-up: the display thread spent 453 ms | the prewarm loaded the OpenGL driver with a throwaway hidden window, and the first window created a second context | the prewarmed window and its context become the first window |

Final measurement (32-bit, locked session, median of 3):

| Metric | Plain JavaFX | JX API on JavaFX | JX native |
|---|---:|---:|---:|
| Build to first frame | 637 ms | 680 ms | **497 ms** |
| Process CPU from JVM start to first frame | 1234 ms | 1313 ms | 1313 ms |
| Records, allocated | 24.5 MB | 24.6 MB | **10.3 MB** |
| Records, CPU | 828 ms | 859 ms | **547 ms** |
| Tab switches, allocated | 1.88 MB | 1.90 MB | **1.24 MB** |
| Scroll, allocated | 147.3 MB | 160.7 MB | **52.5 MB** |
| Scroll, CPU | 1.39 s | 1.55 s | **1.06 s** |
| Sustained updates, process CPU | 1.80 s | 1.83 s | **0.55 s** |
| 100,000 rows, allocated | 48.4 MB | 45.5 MB | **42.2 MB** |
| 100,000 rows, to frame | 123 ms | 123 ms | **101 ms** |
| Allocated over the whole run | 312 MB | 327 MB | **168 MB** |
| Live heap after GC | 46.7 MB | 42.9 MB | **32.5 MB** |
| Peak private bytes | 205 MB | 198 MB | **187 MB** |
| Peak working set | 146.6 MB | 144.9 MB | 152.9 MB |

What is left:

- **Peak working set.** An A/B of four runs each showed that building the hidden tabs while idle
  raises it by about 8 MB: 148 to 152 MB with the idle build, 140 to 143 MB without. Private
  bytes stay the same (about 178 MB both ways; JavaFX: 205 MB). The difference is therefore pages
  Windows keeps in the working set, not memory the process owns. The same content is built at
  the first switch without the idle build. Native mode keeps the idle build, since the first
  switch to a tab is then as immediate as with JavaFX.
- **Start-up CPU.** On Java threads the two now spend the same up to the first frame: 951 ms
  native (display 421, application 406) against 952 ms JavaFX (application 640, renderer 203).
  The process difference, about 80 to 110 ms, is outside Java threads: the OpenGL driver's own
  threads and the JIT. `build_cpu_ns` counts from `start()`, after JavaFX has already started its
  graphics pipeline with the toolkit, while the native driver loads in parallel with `start()`.
  So that metric favours JavaFX, and `first_frame_process_cpu_ns` is the fair one. The native
  first frame comes 140 ms sooner (280 ms counted from JVM start).
- CPU figures of a single phase differ by one or two ticks of Windows' 15.6 ms process clock
  (tab switches 78 against 63 ms, 100,000 rows 109 against 78 ms). They moved both ways between
  runs.

## Final measurement with statistics (10 runs per stack)

The evaluated version was measured again with 10 runs per stack. That is enough for the exact
Mann-Whitney test to reach 5%: with n = m = 10 the smallest two-sided p-value is 2/C(20,10)
= 1.1e-5, while with 3 runs it is 0.1. The analysis is `scripts/analyze-bench.py`: exact
permutation p-values, Cliff's delta, and Holm correction over the 42 comparisons (21 metrics,
two stacks against plain JavaFX). Raw data: [runs](fx-backends-x86-2026-09-29-n10.csv),
[medians](fx-backends-x86-2026-09-29-n10-median.csv),
[statistics](fx-backends-x86-2026-09-29-n10-stats.csv).

The conditions were those of the evening series: 32-bit, session without vertical sync. The
table covers allocation and memory, which do not depend on frame pacing.

| Metric (median) | Plain JavaFX | JX API on JavaFX | JX native | Cliff's delta (native) | Holm p (native) |
|---|---:|---:|---:|---:|---:|
| Build to frame | 638 ms | 680 ms | 494 ms | -1.00 | < 0.001 |
| Process CPU, JVM start to first frame | 1195 ms | 1289 ms | 1352 ms | +0.62 | 0.19 |
| Scroll, allocated | 147.3 MB | 160.7 MB | 52.3 MB | -1.00 | < 0.001 |
| Records, allocated | 24.3 MB | 24.6 MB | 10.3 MB | -1.00 | < 0.001 |
| Tab switches, allocated | 1.88 MB | 1.90 MB | 1.24 MB | -1.00 | < 0.001 |
| 100,000 rows, allocated | 48.6 MB | 45.4 MB | 42.5 MB | -1.00 | < 0.001 |
| Whole run, allocated | 312.2 MB | 326.4 MB | 168.2 MB | -1.00 | < 0.001 |
| Live heap after GC | 46.6 MB | 42.9 MB | 32.5 MB | -1.00 | < 0.001 |
| Peak private bytes | 205.1 MB | 198.5 MB | 186.8 MB | -1.00 | < 0.001 |
| Peak working set | 146.7 MB | 144.9 MB | 154.1 MB | +0.58 | 0.26 |

A delta of -1 means every native run was below every plain JavaFX run. The working set and the
start-up CPU are higher in native mode, but neither difference is significant after the
correction. CPU in the phases that wait for frames (records, tabs, scroll, sustained) was also
lower in native mode (Holm p ≤ 0.002). That CPU depends on frame pacing, which was not
synchronized in this series, so it was measured again with a synchronized display (next section).

## Synchronized display (10 runs per stack, both architectures)

Measured again with the Windows session unlocked and the display on at 239 Hz, so frames are
presented with vertical sync and the frame and CPU rows are valid. Same analysis as above (Holm
over 66 comparisons). Raw data: 32-bit [runs](fx-backends-x86-2026-09-29-vsync-n10.csv),
[medians](fx-backends-x86-2026-09-29-vsync-n10-median.csv),
[statistics](fx-backends-x86-2026-09-29-vsync-n10-stats.csv); 64-bit
[runs](fx-backends-x64-2026-09-29-vsync-n10.csv),
[medians](fx-backends-x64-2026-09-29-vsync-n10-median.csv),
[statistics](fx-backends-x64-2026-09-29-vsync-n10-stats.csv).

| Metric (median) | 32-bit JavaFX | 32-bit native | Holm p | 64-bit JavaFX | 64-bit native | Holm p |
|---|---:|---:|---:|---:|---:|---:|
| Build to frame | 717 ms | 532 ms | < 0.001 | 739 ms | 629 ms | 0.06 |
| JVM start to first frame | 1136 ms | 791 ms | < 0.001 | 1204 ms | 938 ms | 0.05 |
| Process CPU to first frame | 1375 ms | 1422 ms | 1 | 2938 ms | 2430 ms | < 0.001 |
| Records, CPU | 859 ms | 609 ms | 0.002 | 3484 ms | 1250 ms | < 0.001 |
| Tab switches, time | 478 ms | 287 ms | < 0.001 | 477 ms | 252 ms | < 0.001 |
| Tab switches, CPU | 117 ms | 188 ms | 1 | 484 ms | 445 ms | 1 |
| Scroll, CPU | 1438 ms | 1180 ms | 0.005 | 4352 ms | 1969 ms | < 0.001 |
| Sustained updates, CPU | 1883 ms | 984 ms | < 0.001 | 2109 ms | 1117 ms | < 0.001 |
| Frame interval p50 | 4.14 ms | 4.16 ms | < 0.001 | 4.15 ms | 4.16 ms | 1 |
| Frame interval p95 | 6.97 ms | 4.28 ms | < 0.001 | 7.18 ms | 4.28 ms | < 0.001 |
| Frame interval p99 | 7.78 ms | 4.38 ms | < 0.001 | 8.29 ms | 4.36 ms | < 0.001 |
| Worst frame | 15.9 ms | 5.2 ms | < 0.001 | 12.0 ms | 4.6 ms | < 0.001 |
| 100,000 rows, time to frame | 97 ms | 108 ms | 1 | 24 ms | 21 ms | 0.89 |
| Whole run, allocated | 312.7 MB | 166.0 MB | < 0.001 | 326.4 MB | 161.6 MB | < 0.001 |
| Live heap after GC | 46.6 MB | 32.5 MB | < 0.001 | 51.7 MB | 40.3 MB | < 0.001 |
| Peak working set | 150.7 MB | 150.2 MB | 1 | 309.3 MB | 284.3 MB | < 0.001 |
| Whole run, process CPU | 5.75 s | 4.59 s | < 0.001 | 13.57 s | 7.43 s | < 0.001 |

No time, CPU or memory metric is significantly worse in native mode on either architecture. The
32-bit frame median is 0.4% higher, which is significant but is not slower frames: native
sustains more frames per second (239.9 against 237.7), and JavaFX is timed at pulse start rather
than at the buffer swap.

### A 44 ms frame on 32-bit: native wrappers compiled by the caller

Before this series, native mode on 32-bit had one frame of about 44 ms per run, a few seconds in.
GC, layout, the swap, timer resolution (`-XX:+ForceTimeHighResolution`) and text were ruled out
one by one. `-XX:+PrintCompilation` showed the cause: on the 32-bit client VM, the wrapper of a
native method (`nnvgStrokeColor`, `nnvgArc`...) is generated when the method gets hot, and the
calling thread waits for it, about 15 ms each. Three of them landing in one frame made the 44 ms
frame. The fix warms those natives on a background thread one second after the first frame
(`JXNanoVGRenderer.startWarmCalls`): NanoVG path calls only record commands, `nvgCancelFrame`
drops them, and an empty `nvgEndFrame` makes no GL call, so the thread needs no GL context. The
glyphs it uses are uploaded on the display thread beforehand (`warmNatives`). It runs only on the
client VM: on 64-bit (server VM) wrappers are made without that wait, and the extra work made the
tab-switch CPU worse there. Warming earlier was tried and rejected: on the display thread it
delayed the first frame by 400 ms, and at start-up by about as much.

## Other raw data

- [fx-backends-x86-2026-09-29-final.csv](fx-backends-x86-2026-09-29-final.csv) and its
  [medians](fx-backends-x86-2026-09-29-final-median.csv): the evaluated version, 32-bit, three
  runs per stack, before the 10-run series.
- [ab-prebuild-idle.csv](ab-prebuild-idle.csv) and [ab-prebuild-off.csv](ab-prebuild-off.csv):
  native mode with and without building the hidden tabs while the window is idle.

## Threats to validity

- A screen shaped like DeviceConfig's is not DeviceConfig: no database, no controllers, no
  application CSS. The real screens remain to be measured (`scripts/NativeScreenBatch.java`).
- One machine and one display (239 Hz). A 60 Hz display paces all three stacks at 60 frames per
  second; there the differences would show in CPU and frame times, not in throughput.
- The JavaFX side is timed at pulse start and the native side at buffer swap. The two-frame wait
  makes both certain to include the change, at the cost of one frame of latency on each.
- Process CPU includes the graphics driver's threads, which differ between Direct3D (JavaFX) and
  OpenGL (native); the JVM-threads rows exclude them.
- Windows reports process CPU in 15.6 ms steps.

## Reproduce

```bash
python scripts/make-purefx-bench.py
```

Then, with the classpath of `jxparallel-fx` written by `mvn -pl jxparallel-fx dependency:build-classpath
-Dmdep.includeScope=runtime` under the JDK being measured, compile `scripts/FxBackendBench.java`
into `target/bench` and `target/purefx/PureFxBench.java` into `target/purefx`, and run
`scripts/measure-fx-backends.ps1 -Classpath "target\bench;<jxparallel-fx jar>;<classpath>"
-Cases purefx,javafx,native -Runs 7`.
