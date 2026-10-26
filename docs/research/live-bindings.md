# Live bindings: writing only what changed into the mounted nodes (research)

Branch `research/live-bindings`. This is research for a possible master's degree: it is not merged into `main` or into any
open pull request, and the numbers and claims here are about this branch only. Data: `docs/research/live-2026-10-26/`.

## 1. Problem

In JXForge an update rebuilds the whole immutable `JXElement` tree of a screen from the controller and reconciles it into the
mounted `JXNativeNode` tree. For one value changing on thousands of small controls that allocates more than JavaFX does when
it sets one property. The last full series (Java 17, 5 runs, `docs/components-2026-10-21-final/`) had, as the ratio
JavaFX / JXML of the allocation of one update: Separator 0.17x, ScrollBar 0.35x, TableView 0.35x, Slider 0.38x, Image 0.55x,
TreeTable 0.64x, ListView 0.84x; and ScrollBar update time 0.48x. The garbage per instance that was left (the element, its
props, the children plumbing, about 120 to 150 B) is the cost of the design, and
[the optimizations page](../jxforge-optimizations-2026-10-21.md) ends by naming the way out: a generated view that records,
for each controller-dependent prop, where it lands and how to read it, re-reads those on an update and sets only what changed,
falling back to rebuild and reconcile when the shape of the screen changes. This page is that, built, tested and measured.

## 2. What was built

| Piece | Where | What it does |
|---|---|---|
| `JXLiveNode` | core | The small interface a live session needs from a mounted node (forge does not depend on the renderer): `getSource`, `getLiveChildren`, `setProp`, `commit`, `setSource`, `reconcileLive`. |
| `JXProps.replaceValue / indexOfKey / setValueAt` | core | The one in-place write of a value (keys never change). |
| `JXNativeNode.setProp / commit / reconcileLive / setSource` | ui | `setProp` changes one prop in place, decides by the same per-key rules as a reconcile whether sizes can change (`keyAffects`, the per-key form of `sizeAffected`), reads again the layout fields the prop feeds, and returns what happened; `commit` settles layout with exactly the end of `reconcileInPlace` (`outcome`): `RESIZED`, `SUBTREE` or `UNCHANGED` for the parent. |
| `JxmlView.live(controller)` | forge | Default method: no plan, every refresh rebuilds and reconciles (the interpreter, `JxmlTemplate`, keeps exactly that). |
| `JxmlCompiler` plan | forge | Beside `build`, a nested class `Live` (loaded only when a screen is first made live, so a screen that is only built pays nothing) with the plan: static getters that read every bound prop with the same conversion `build` uses, the loops, conditions and slots, and the parts that are *opaque*. `build(c)` is not touched. |
| `JxmlLive<C>` | forge | The session: the built tree, the recording, `attach(JXLiveNode)`, `refresh()`. |

### 2.1 The recording

`view.live(c)` builds the tree with the ordinary `build(c)` and walks the plan against it once. The walk records:

* a **site** for every element with something live on or below it (its position among its parent's children, its parent
  site, its node once attached): about 32 B;
* a **binding** for every prop that depends on the controller or on a loop variable: the shared static getter, the scope of the
  iteration that holds the loop variables, the last value;
* a **scope** (24 B) for each iteration of each `<For>`, holding the item: the getters read loop variables from it, so a
  list that is replaced by another list of the same size is rebound by moving the new item of each position into the scope;
* **checks**, which decide the shape of the screen: for a `<For>` its size, its window (`from`, `limit`) and, for `memo`,
  the identity of the list; for an `<If>` its test; for a `<Slot>` the element by identity; for a keyed element its key by
  identity (as a keyed reconcile compares keys); for an element-valued prop its element;
* **opaque** parts (below).

`attach(root)` pairs the sites with the mounted nodes by walking the element tree and the node tree together (each node's
source must be the very element of the recording), so the recording needs no path per binding and the nodes need no parent
pointer.

### 2.2 Refresh

1. **All the checks first**, nothing written. A changed shape means the **fallback**: build the screen, reconcile it into
   the mounted root (`reconcileLive`), record again, attach again. Before any write, so the mounted tree is untouched.
2. **Bindings**: read every getter again; compare with the last value (`equals` when both are of one class that is not an
   array, `Objects.deepEquals` otherwise); for a changed one `setProp` on the node. No element, no props array, no list.
3. **Opaque parts** are built again and reconciled into their own node with `reconcileLive`; every ancestor with a site gets a
   new element that has the new part as a child (same type, same props object, new child list) and its node is told with
   `setSource`, so the element tree of the session stays the mounted one and the stale part can be collected.
4. **Settle**: the sites that were touched, deepest first, one `commit` each, the result handed to the parent site. When many
   sites were touched the recorded sites are walked once from the highest dirty one down (a parent is always below its
   children in the list); when few, a heap keeps the cost proportional to the number touched. Retained cost: the sites;
   no field was added to a node.
5. A prop that appears or disappears (a bound value that becomes `null`, or comes back) is another set of keys, which only a
   reconcile does: it is applied after the others are settled, and then the fallback runs.

Translations (`%{key(args)}`) are bindings that are read again only when the locale changed (the generated `Strings.locale()`
is watched) or one of the bound arguments of the message did, because formatting a message makes garbage; a bound date
argument, which can change in place, is always read again.

### 2.3 What is opaque and what falls back

| Case | What happens | Why |
|---|---|---|
| A bound prop of a plain element (all the common controls, layouts, material controls, overlays, menus) | written in place | the point |
| `<For>`: the list is another object of the same size, the window is the same, items changed | items moved into the scopes, bindings read again, no fallback | the benchmark's lists are replaced by new ones on every update; a rule on list identity would send all 94 components to the fallback |
| `<For>`: size or window changed | fallback | children added, removed, or other rows |
| `<For>`: an item changed and a handler in the body holds the loop variable (`#open(o)`, `@{o.name}`) | fallback | the handler is not made again and would hold the previous item |
| `<For memo>`: another list object, size or window | fallback | memo means the elements are shared while those are equal; the body is counted, not recorded |
| `<If>` flips, `<Slot>` gets another element, a key (identity) changes | fallback | shape |
| A prop that becomes or stops being `null` | fallback | set of keys |
| Locale changed | the translated props are read again, no fallback | |
| `Tabs`, a radio or toggle button *with* `group` | **opaque**: built again and reconciled into its own node on every refresh (the rest of the screen is live) | the runtime derives their props (the selected tab, the group) from the bound ones, so a changed binding is not a change of one prop in one node |
| `ListView`, `TableView` whose row template is made of plain elements (section 2.6) | **live**: the list element's props are bound (`first` and `scrollY` read as `finish` derives them), the rows are kept by key; a change of the window or of the values keeps the rows that stay, with their nodes, and builds only the rows that come in | the second series |
| `ListView`, `TableView`: the number of items, or the number of rows asked for (`rows`), changes | fallback | shape |
| `Tree`, `TreeTable` | **rebuilt, rows given back** (section 2.7): the tree is still built again on every refresh (all its items are built), but the rows of the items that stay are not made again | the rows are derived from the items by `JxmlRuntime.tree` |
| The interpreter (`JxmlTemplate`) | every refresh is a fallback | as specified |

Of the 94 benchmark components 91 are fully live (no opaque part, no fallback) and 3 have an opaque part: `Tabs`, `Tree`,
`TreeTable` (the first series, before section 2.6, had 5: also `ListView` and `TableView`). No component of the benchmark takes
the global fallback, in either value set (`live-coverage.csv`; `LiveComponentsTest` asserts it, and asserts that nothing but
those three is rebuilt as a whole).

### 2.4 The window host (second series)

`JXWindow` did not call `refresh()`; now it can. The layering holds (forge depends on core, ui does not depend on forge):

| Piece | Where | What it does |
|---|---|---|
| `JXLiveSession` | core | What a host needs from a live screen: `element()`, `attach(JXLiveNode)`, `refresh()`, `renew()` (a new session for the same screen and model). `JxmlLive` implements it. |
| `JXLiveHost` | ui | Mounts the session's element, pairs the session with the nodes, and `refresh()` brings the tree up to date; when the root changed type it mounts `session.renew()` anew (what `setContent` does when a reconcile cannot keep the root) and returns true. No window, no display thread: a backend that owns its window uses it directly (`JXFrame.mountLive`). |
| `JXWindow.setLive(session)` | ui | On the display thread (posted with `JXDisplay.post` when called from another thread, like `setContent`): mounts the host, which becomes the window's root. From then on every frame that is going to be painted calls `refresh()` first, just before the backend renders (after the minimized and frame-slot checks, so a frame that waits for its slot is refreshed once), and takes the new root when the host mounted anew (the focused node is forgotten). `setContent` and `setRoot` end the live screen; `setLive(null)` stops refreshing. |

Thread rule: the tree and the session are single-threaded, on the display thread. The model must be changed there too (an event
handler, or `invokeLater`), followed by `requestRender()` (a handler already causes one). The window never refreshes by itself:
nothing watches the model.

### 2.5 Deviations from the brief

| Brief | Built | Why |
|---|---|---|
| `JxmlLive.attach(JXNativeNode)` | `attach(JXLiveNode)`, `JXLiveNode` in core | forge depends on core only; the layering tests must stay true |
| `build`/`live` generate a recording while building | the plan is generated beside `build`; `live(c)` calls `build(c)` and walks the plan | `build` is the benchmark's compiled mode and keeps its bytes and its tree; the price is that getters are read twice at build (build alloc 1.6x to 2.3x of compiled) |
| `setProp` copy-on-writes the props once per refresh | writes **in place** into the props object the element has: the node's props and the element's are one object | zero allocation; keeps the element tree equal to what is mounted, so a later reconcile compares against the right previous values and needs no "live-owned" flag. The price: elements of a live session are not immutable: the session owns the props of the elements it recorded (hoisted constants and memoized elements have no bindings, so they are never written) |
| `For` guard on list identity | size, window, memo identity, keys by identity; items rebound per position | see the table above |
| Parent pointers, or a walk from the root | sparse records in the session (sites with their parent), settled by scan or heap | no node field; the cost is measured in 5.3 |
| Translations: rebound or structural fallback | rebound, watching locale and arguments | tested (`LiveLocaleTest`) |
| Tabs, trees, virtual lists "handled or routed to the fallback" | a third way: opaque, rebuilt on their own | the fallback rebuilds the whole screen; the rest of it can stay live |
| Harness classpath | `measure-components.ps1` takes the module classes from `target\classes` | the jars named in `classpath.txt` are the ones installed in `~/.m2`, another version of the code |

### 2.6 Virtual lists: `ListView` and `TableView`

The schema turns `<ListView items var key first rows>` into the list element with one child, a `<For items from=first
limit=rows>` whose body is the row template; `JxmlRuntime.finish` then derives `first` (kept within the items) and `scrollY`
from `itemCount`, `first` and the row height. The compiler keeps such a list live when the row is one element made of plain
elements (bindings and static children; no loop, condition, slot, element-valued prop, tab pane, tree, list or group button
below it, and the loop is not `memo`); otherwise it stays opaque as before.

* The list element is an ordinary element of the plan. Its binds are the explicit ones, except that `first` and `scrollY` are
  binds whose getters are `JxmlRuntime.virtualFirst/virtualScroll`, the very functions `finish` uses (so the two cannot drift).
* The loop is `JxmlLive.rows(items, from, limit, key, capturing, build, row)`. `build` is the expression the generated `build`
  uses for one row, with the loop scope. The session keeps for each list a **group**: the rows of the current window, each with
  its scope (the item), its key, its element, its node and a recording of its own (its sites, parents first, and bindings), not
  spliced into the session's arrays (the numbering of the sites stays fixed).
* Check (before any write): the number of items, and `limit`, are what they were. The window (`from`) is not part of it.
* Refresh: (1) for each group, read the window and decide which rows stay: by key, compared by identity as a keyed reconcile
  compares them (no key: by position); a row whose key is in the window keeps its record and gets the new item in its scope
  (when a handler of the row holds the item and the item is another object, the row is made anew); the others are built by
  `build` and recorded. When the window is the same rows in the same places, nothing else is allocated (a buffer and a sub list
  per group). (2) the ordinary bindings, which include the list's `itemCount`, `first` and `scrollY`. (3) the bindings of the rows
  that stay are read and written in place, and the row's sites are settled deepest first with the row's own result going to the
  list site. (4) when the rows are not the same ones in the same places, the list node is reconciled against a new list element
  (same type and props, children = the staying row elements, which are the very objects the nodes have as source, and the new row
  elements); the keyed reconcile then finds the staying rows `UNCHANGED` and moves them, creates the new ones and drops the others,
  and the session pairs the new rows with their nodes. The list's ancestors get new elements as for an opaque part
  (`replaceAncestors`).
* A window of zero or one row, duplicate keys or no key is reconciled by the node by position (`keyed()`); the session pairs
  every row with the node at its position afterwards, so the rows that stay keep their element, and are paired with whatever
  node ends up at their position.

### 2.7 `Tree` and `TreeTable`

Their rows are not template rows: `JxmlRuntime.tree` flattens the item elements (all of them are built: a tree of thousands
of items makes a few dozen rows) and derives a `cell` (or `tablerow` of `cell`s) for each row of the window, with `indent`,
`disclosure` and `odd` from the position. These were left opaque and are still **rebuilt on every refresh**, but a live session
hands `tree` the rows of the last window (`JxmlRuntime.RowCache/TreeRows`, a per-tree cache on the opaque site, a thread local
that only a live refresh sets): a row whose item is the same element, with the same depth and arrow, is not made again; its
`odd` is set in place to the parity of its new position. With a memoized model (`memo="true"`, which the benchmark's trees use)
the item elements are the same objects from refresh to refresh and every row that stays is reused; with a model built anew each
time no row is reused and the cost is what it was. The nodes of rows that stay are kept in both cases (the keyed reconcile always
did that). The first refresh of a tree makes all its rows (the first build is not through the cache). `odd` is written in place
without telling the node because nothing in the renderer reads it; a backend that did would not see the change.

## 3. Invariants

1. `view.live(c).element()` is `JxmlTrees.diff`-equal to `view.build(c)` (all 94 components, the 14 shared samples, and the
   kitchen-sink screen of the structure tests).
2. After any `refresh()`, the session's element tree is `JxmlTrees.diff`-equal to a fresh `build`, and the mounted tree is the
   tree a fresh build and mount gives: every node's type, props, min/pref/max sizes, laid-out box, and the whole 1280x800
   pixel buffer of `JXWindow.paintSoftware`.
3. After any refresh, a full reconcile with a rebuilt element gives what a fresh mount gives (`JXLiveNodeTest`, property
   style: live changes, then a reconcile, against a fresh mount).
4. No check is passed unless all of them are; a failed check writes nothing.
5. A node is settled once per refresh, children before parents, with the results a reconcile gives.
6. Handlers are never rebound; a loop that holds an item in a handler never keeps an item that changed.
7. (second series) A row of a virtual list that stays in the window keeps its node and its element, an element that is the
   very object the node has as source; a row that comes in is built and gets a node; a row that leaves is gone (no node of
   it is kept). A row is the same row when its key is the same object (by identity, as the keyed reconcile compares them).
8. (second series) The window host refreshes once per painted frame, on the display thread, and what it shows after a
   refresh is what a fresh build and mount of the model gives.

`Tooltip` was first an exception: the reconcile left the column that holds 5000 tooltips with a stale preferred width when
their texts changed, because `sizeAffected` did not know that a tooltip's `label` sizes it, and the live path inherited that.
Looking for the cause found two more props missing in the same way (a select's `prompt`, a text area's `prefRowCount`); the
fix went to `main` (`3278a4e`, `JXSizePropsReconcileTest`) and was merged here, with `keyAffects` given the same three. Since
then live equals a fresh mount for all 94 components, `Tooltip` included.

## 4. Tests

Counts (this branch, Java 17 unless noted): core 36, ui 291 (1 skipped), forge 253 (on Java 8: 253 with 1 skipped, on Java
21: 253), examples-native 60. The first series added 1 test class to ui (`JXLiveNodeTest`, 5 tests), 1 to forge
(`JxmlLiveTest`, 2 tests: a generated view's session against a node that only mirrors the element tree, and the interpreter's
no-plan case) and 5 to examples-native (13 tests: 48 in all). The second series added `JXLiveHostTest` (ui, 1 test, a fake
session), and in examples-native `LiveHostTest` (3), `LiveVirtualTest` (6) and `LiveTreeTest` (3): 60 in all. Test helpers:
`LiveRig` (a live screen, the current path, the model, and the comparisons below), `VirtualModel`, `TreeModel`, `LiveWindow`.

* **Node level** (`JXLiveNodeTest`, ui): on 600 random trees, `setProp` on every changed prop and a bottom-up `commit` give the
  nodes, sizes and boxes that reconciling the rebuilt tree gives and that a fresh mount gives; 400 more with three further
  full reconciles after the live changes; a live `prefWidth` 50 to 55 and back to 50 by a reconcile is laid out at 50; a prop
  that appears or goes away is refused (`-1`); `setProp` of a cached boxed value allocates nothing (measured over 100000 calls).
* **(a) The 94 components** (`LiveComponentsTest`): mount live, switch the value set 1, 0, 1, 0, 1, 0 through `refresh()`; after
  each step compare with the current reconcile path and with a fresh build and mount: all nodes, sizes, boxes, the whole pixel
  buffer, and `JxmlTrees.diff` of the session's element tree against a fresh build. No component takes a fallback (asserted);
  `Tabs`, `Tree` and `TreeTable` are the only ones with an opaque part (asserted for the others; `live-coverage.csv`).
  A test expects that no component diverges from a fresh mount.
* **(b) Structure** (`LiveStructureTest`): a kitchen-sink screen with an `<If>`/`<Else>`, an unkeyed loop with handlers, a
  keyed loop, a window, a memo loop, a slot and a handler. Value changes (title, amount, gap, an item edited in place, a list
  copied into a new list object) never fall back; each structural change (flag, add, remove, reorder, other item objects,
  `from`, `limit`, memo list, slot) falls back exactly once, matches a fresh mount, and the session goes on live; 240 random
  operations of both kinds, each checked against a fresh mount and the current path; a list replaced by another of the same
  size is rebound without a fallback; a handler of a loop that holds its item gets the new item after the fallback; a `null`
  prop and back.
* **(c) Allocation** (`LiveAllocationTest`, `ThreadMXBean`, 40 refreshes after 60 warm-up ones, bytes of `refresh()` alone):
  5000 Separators, 5000 Sliders and 5000 Labels, one value changed in every one, **0.02 B per node per refresh**; 1500
  ScrollBars 0.07 B (about 100 B per refresh, in all). The bound asserted is 8 B. Where that remainder comes from is not
  attributed (the benchmark's values are cached boxed objects and the conversions return the same instance, so it is not
  boxing; the two `ThreadMXBean` reads and the loop are in it); the layout the host runs afterwards is not in this number
  and is measured in 5.1 and 5.2.
* **(d) Nothing changed**: the same, with no change: 0.02 B per node. A screen with 14 translated bindings: 0 B per refresh
  over 200 refreshes (`LiveLocaleTest`). With `JXSoftwareFrame`, a refresh that found nothing changed repaints 0 rectangles;
  after a change it repaints (`LiveAllocationTest`).
* **(e) The samples** (`LiveSamplesTest`): each of the 14 sample screens is compiled in memory and builds the same tree live,
  plain and interpreted; changing the sample controller in several ways at once four times, the live screen equals a fresh
  mount node by node, and pixel by pixel except in `controls`, which has an indeterminate progress bar that draws by the clock
  (two paints of one tree differ); there the nodes are compared, the pixels are not.
* **Translations** (`LiveLocaleTest`): a locale change and a changed argument are written in place; no fallback.
* **(f) The host** (`LiveHostTest`, `JXLiveHostTest`): `JXLiveHost` (through `JXFrame.mountLive`) and the display-thread part
  of `JXWindow.setLive` (`mountLive`/`refreshLive`, reached through a test bridge; no GLFW) on a compiled screen: the session is
  paired; value changes refresh in place (same root, no fallback) and equal a fresh mount, nodes and pixels; a shape change
  falls back and keeps the root; a screen whose root is a slot, given an element of another type, is mounted anew
  (`remounts`, new root, new session) and goes on live; a window with no live screen does not refresh. The ui test drives the
  host with a fake session (attach, refresh, renew). Not tested: the posting to the display thread and the frame loop, which
  need GLFW.
* **(g) Virtual lists** (`LiveVirtualTest`; list, list without a key, list with a handler that holds the row's item, table):
  random scrolls (`first` negative, beyond the end) 3 seeds x 40 steps each; windows of 0, 1, 2 and 3 rows (so a window shorter
  than asked at the end); 60 mixed steps (an item edited, the same objects in another list, other objects of the same size,
  a scroll); each step compared with the current path and a fresh mount (nodes, sizes, boxes, pixels, element tree), never a
  fallback, no opaque build; an item added or removed, or another `rows`, falls back exactly once and the session goes on live;
  node identity (rows that stay are the same node objects, 5 rows come in as 5 built rows, the 5 that left are different nodes
  when they come back); a handler holds the item of its key, also after another list of new objects replaces the model.
* **(h) Trees** (`LiveTreeTest`; tree and tree table, memoized or not): random scrolls, windows of 0, 1, 2 rows, and 60 mixed
  steps (a folder edited or collapsed, a snapshot list, folders and children added or removed, another window, a scroll),
  against the current path and a fresh mount, no fallback; the nodes of the rows that stay are kept, and with a memoized
  model so are their elements, also when the shift flips their `odd`. (The tree tables use explicit column widths: see 6.)

## 5. Results

Machine: ASUS, Intel Core i5-12400F (6 cores, 12 threads), 15.8 GB, Windows 11 Enterprise 10.0.26300, background CPU 7% mean
before the series, JDK 17, fixed heap 512 MB. 13 components of the 94, each: 5 runs of each mode in a fresh JVM (JavaFX,
compiled, live; the modes rotate their place), 8 passes, the stage values are the medians of the 5 runs. Values and ratios
below are medians. JavaFX is JavaFX 21.0.2. The fingerprint of every run (instances, probed values before and after the
update) equals the reference's: 65 of 65 live runs verified. The full series of the three JXML modes and three JVMs was not
run (2.5 hours; not asked).

A ratio JavaFX / mode above 1 means the mode is better than JavaFX; live / compiled below 1 means live is better than compiled.

### 5.1 Update of one prop of every instance: the 8th pass of a fresh JVM

This is how the earlier series measure (`update_warm`): the last of 8 passes, which is the time of a pass while the JIT is
still settling. Time includes `refresh()` or build + reconcile, and `layoutForBackend`; allocation is everything in the pass.

| component | n | time ms: compiled | live | JavaFX | JavaFX / compiled | JavaFX / live | live / compiled | alloc KB: compiled | live | JavaFX | JavaFX / compiled | JavaFX / live |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Separator | 5000 | 2.85 | 2.70 | 3.00 | 1.05x | 1.11x | 0.95x | 704.5 | 122.7 | 122.9 | 0.17x | 1.00x |
| Slider | 5000 | 3.05 | 1.41 | 2.72 | 0.89x | 1.93x | 0.46x | 626.4 | 5.5 | 242.5 | 0.39x | 44.27x |
| ScrollBar | 1500 | 0.97 | 0.43 | 0.72 | 0.74x | 1.65x | 0.45x | 215.4 | 5.5 | 76.0 | 0.35x | 13.89x |
| ProgressBar | 5000 | 2.66 | 2.30 | 6.74 | 2.53x | 2.94x | 0.86x | 587.3 | 5.6 | 672.1 | 1.14x | 119.49x |
| Image | 5000 | 3.23 | 3.63 | 4.01 | 1.24x | 1.10x | 1.13x | 1017.0 | 279.0 | 552.6 | 0.54x | 1.98x |
| TableView | 20 | 1.71 | 1.84 | 1.36 | 0.80x | 0.74x | 1.08x | 601.0 | 600.4 | 209.2 | 0.35x | 0.35x |
| ListView | 20 | 0.66 | 0.73 | 2.52 | 3.83x | 3.44x | 1.11x | 133.1 | 131.4 | 111.7 | 0.84x | 0.85x |
| Tree | 8 | 2.05 | 2.07 | 3.18 | 1.55x | 1.53x | 1.01x | 131.0 | 130.6 | 225.1 | 1.72x | 1.72x |
| TreeTable | 8 | 1.88 | 1.89 | 2.60 | 1.38x | 1.38x | 1.00x | 291.9 | 291.4 | 188.0 | 0.64x | 0.65x |
| Label | 5000 | 2.26 | 2.21 | 36.30 | 16.07x | 16.41x | 0.98x | 665.4 | 122.7 | 18676.1 | 28.07x | 152.20x |
| Button | 5000 | 2.46 | 2.46 | 27.67 | 11.26x | 11.23x | 1.00x | 821.7 | 279.0 | 19207.3 | 23.38x | 68.85x |
| TextField | 1500 | 1.03 | 0.41 | 12.94 | 12.53x | 31.48x | 0.40x | 191.9 | 5.5 | 7668.3 | 39.95x | 1400.21x |
| Grid | 1500 | 4.17 | 4.09 | 14.16 | 3.39x | 3.46x | 0.98x | 3590.4 | 3380.5 | 3578.5 | 1.00x | 1.06x |

What the allocation column says:

* The controls the series had behind JavaFX in allocation are now at or ahead of it: Slider 0.39x to **44x**, ScrollBar
  0.35x to **13.9x**, ProgressBar 1.14x to **119x**, Image 0.54x to **1.98x**, Separator 0.17x to **1.00x**.
* The 5.5 KB that remain for Slider, ScrollBar and ProgressBar are the layout, the controller's `setPhase`, the measurement
  and the first calls of the pass; `refresh()` alone allocates about 100 B (4c). For Separator, Image, Label, Button and Grid the rest is the **layout the host runs after the refresh**
  (`layoutForBackend`), about 24 B per laid-out child, which is the same for the compiled path and was not touched. Separator's 122.7 KB is
  exactly that, and by coincidence JavaFX's 122.9 KB too. Closing that is a different change (layout).
* ListView, TableView, Tree, TreeTable are the opaque parts: the same allocation as compiled, as expected. TableView
  (0.35x) and TreeTable (0.65x) stay behind JavaFX, and ListView (0.85x) too. These are **not** improved by this work.

What the time column says is weaker, and the 8th pass of a fresh JVM is a poor place to read it (the earlier series say so for
`TableView` and `Image`: two identical builds differ by up to 20%): the controls whose changed prop does not change a size
(Slider, ScrollBar, TextField) are 0.40x to 0.46x of the compiled time, and ScrollBar's update goes from 0.74x of JavaFX (it was
0.48x in the final series) to 1.65x; the others are within noise of compiled (0.86x to 1.13x). Section 5.2 measures it once the JIT has settled.

### 5.2 Update in steady state

`LiveSteadyBench`: in one JVM the compiled screen and the live screen are mounted from the same controller and updated in
turns for 2000 rounds (the value sets alternate); the first 1000 are not counted. Medians of 5 JVMs. Compiled is build +
reconcile + layout; live is refresh + layout, in microseconds. (No JavaFX here: the harness measures that in its own JVM.)

| component | n | compiled: build | reconcile | layout | total us | live: refresh | layout | total us | live / compiled | alloc KB: compiled | live |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Separator | 5000 | 55 | 348 | 216 | 628 | 323 | 218 | 545 | 0.87x | 700.1 | 118.4 |
| Slider | 5000 | 58 | 261 | 40 | 362 | 229 | 40 | 268 | 0.74x | 621.9 | 1.1 |
| ScrollBar | 1500 | 42 | 121 | 20 | 184 | 50 | 21 | 72 | 0.39x | 211.1 | 1.2 |
| ProgressBar | 5000 | 55 | 288 | 90 | 433 | 292 | 85 | 380 | 0.88x | 582.9 | 1.2 |
| Image | 5000 | 120 | 396 | 200 | 718 | 376 | 209 | 586 | 0.82x | 856.4 | 118.4 |
| TableView | 20 | 37 | 238 | 49 | 325 | 279 | 50 | 331 | 1.02x | 594.6 | 594.0 |
| ListView | 20 | 14 | 108 | 9 | 133 | 124 | 9 | 134 | 1.01x | 125.5 | 124.9 |
| Tree | 8 | 226 | 55 | 4 | 286 | 288 | 5 | 293 | 1.02x | 102.2 | 101.8 |
| TreeTable | 8 | 259 | 85 | 13 | 359 | 355 | 20 | 375 | 1.05x | 279.6 | 279.2 |
| Label | 5000 | 55 | 287 | 208 | 554 | 280 | 194 | 483 | 0.87x | 661.0 | 118.4 |
| Button | 5000 | 53 | 313 | 185 | 552 | 297 | 198 | 498 | 0.90x | 661.0 | 118.4 |
| TextField | 1500 | 38 | 104 | 8 | 150 | 52 | 8 | 60 | 0.40x | 187.7 | 1.2 |
| Grid | 1500 | 55 | 659 | 497 | 1220 | 468 | 495 | 972 | 0.80x | 3410.3 | 2919.2 |

Reading it: live removes the element build, and what that costs is only part of the update. Where the changed prop leaves
every size alone (Slider's `value`, ScrollBar, TextField) the update is 0.39x to 0.74x of compiled; where it changes a size
(Separator's `prefWidth`, Label's text, Image's `fitWidth`, ProgressBar) the work of `outcome`, the sizes and the layout is
the same in both paths and live is 0.82x to 0.90x. The opaque parts are 1.01x to 1.05x: the same rebuild plus the
bookkeeping of the sites. The live allocation column is `refresh()` (about 100 B for 5000 nodes, 4c), the layout and the two
reads of the allocation counter: for Slider, ScrollBar, ProgressBar and TextField, whose update changes no size, it is 1.1 to
1.2 KB; for the others it is the layout.

### 5.3 What the recording costs: build, ready, retained

Build is `view.live(c)` (the tree and the recording); ready is the mount, the layout and, for live, `attach` (about 45 ns
per node: 0.07 to 0.35 ms, in the ready column); retained is the live heap after a GC with the instances held minus after
releasing them, per instance, the session included.

| component | build ms: compiled | live | ready ms: compiled | live | build alloc KB: compiled | live | retained B per instance: compiled | live | difference B | live / compiled |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Separator | 0.89 | 1.23 | 2.98 | 2.35 | 587 | 1297 | 509 | 629 | 120 | 1.24x |
| Slider | 0.95 | 1.34 | 2.20 | 2.71 | 626 | 1336 | 448 | 568 | 120 | 1.27x |
| ScrollBar | 0.49 | 0.51 | 1.88 | 1.78 | 215 | 428 | 474 | 590 | 116 | 1.24x |
| ProgressBar | 0.97 | 1.20 | 2.44 | 2.61 | 587 | 1297 | 507 | 625 | 118 | 1.23x |
| Image | 1.35 | 1.91 | 2.84 | 2.94 | 743 | 1720 | 472 | 632 | 160 | 1.34x |
| TableView | 0.32 | 0.34 | 1.40 | 1.05 | 343 | 347 | 55822 | 56940 | 1118 | 1.02x |
| ListView | 0.21 | 0.22 | 0.37 | 0.39 | 85 | 88 | 12476 | 12746 | 271 | 1.02x |
| Tree | 1.39 | 1.21 | 0.30 | 0.27 | 112 | 114 | 15316 | 15606 | 290 | 1.02x |
| TreeTable | 1.25 | 1.27 | 0.58 | 0.82 | 215 | 217 | 49770 | 49979 | 209 | 1.00x |
| Label | 0.76 | 1.09 | 2.22 | 2.29 | 548 | 1258 | 432 | 553 | 121 | 1.28x |
| Button | 0.79 | 1.04 | 2.62 | 2.38 | 548 | 1258 | 432 | 549 | 117 | 1.27x |
| TextField | 0.34 | 0.67 | 1.42 | 2.16 | 192 | 405 | 457 | 572 | 115 | 1.25x |
| Grid | 0.37 | 0.52 | 5.74 | 7.30 | 496 | 789 | 1713 | 1877 | 165 | 1.10x |

* **Retained memory: +115 to +120 B per bound instance** (+23% to +28%): a site (32 B), a binding (32 B), a scope (24 B) and
  the slots of the three arrays. Image +160 B (two bindings), Grid +165 B for 1500 instances of three children. The opaque
  parts are +0% to +2% (the recording is a few sites). An earlier run, in which the benchmark still held the first element
  tree of the session after the update, read +26% to +37% for them: a session's element tree is the mounted one (2.2), so
  holding it costs nothing, but a tree kept from before the first refresh would be a second copy of every opaque part.
* **Build: +0.3 ms and 1.6x to 2.3x the allocation** for 5000 instances (the recording and the second read of the getters),
  one-off per screen; the update gains back 0.1 to 0.2 ms each, so it pays for itself after a handful of updates for the controls
  whose value does not change a size, and after a few dozen for the others.
* Ready is not slower (the attach is the price of 45 ns per node and is lost in the noise of the mount).

For scale: the earlier series has compiled JXML retaining 51x less per instance than JavaFX (geometric mean of the 43
components with a counterpart); a +25% on every instance would make that about 41x. That is arithmetic on the earlier mean,
not a measurement of the 43.

### 5.4 Second series: virtual lists (`docs/research/live-lists-2026-10-26/`)

Not measured yet: the code of sections 2.4 to 2.7 and the tests are committed, the series is run on frozen code afterwards
(`scripts\measure-components.ps1 ... -Components ListView,TableView,Tree,TreeTable,Separator,Slider`, then the steady-state
bench for the same components). The first series above is not touched and is the baseline: TableView allocation 0.35x of
JavaFX, TreeTable 0.65x, ListView 0.85x, Tree 1.72x.

## 6. Threats to validity

* **One machine, one JDK for the series**: i5-12400F, Windows 11, JDK 17 (the examples ran on 8, 17 and 21 for the tests, not
  for the benchmark). The 8th-pass numbers carry the JIT noise the earlier pages describe; that is why the steady-state series
  exists, and the two disagree on the size of the gain (5.1 vs 5.2) for the same components. Neither is a response time in an
  application.
* **Updates in the benchmark are the best case for live**: every instance of the screen has one bound prop, the values come
  from a cached list of boxed values, the structure never changes. A screen where each update also adds rows or flips an `<If>`
  takes the fallback, which costs the compiled path plus the check pass and a second walk of the plan.
* **The part that is common to both paths is large**: layout is run after both, and for Separator, Image, Label, Button and
  Grid it is half the time and all the remaining allocation. The tables give both; a claim about "update" should say which
  part of the update.
* **JavaFX is measured in its own JVM and way** (`ComponentBenchFx`, as in the earlier series); only the ratios against the
  earlier series, not the absolute numbers, are comparable across sessions.
* **The recording trusts the plan**: the plan is generated from the template by the same code that generates `build`, and
  the tests check that the walk consumes exactly the elements of the tree; a desynchronization turns the session into one
  that only falls back (`problem()`), it does not write. Nothing proves that every generated view's plan is right except the
  tests: 94 components, 14 samples, the kitchen-sink screen.
* **The element tree is no longer immutable for a live session.** Any code that keeps the root element of a live screen and
  expects it to stay as it was built (a cache, a comparison) sees it change under it. Hoisted constants and memoized
  elements are shared across screens and are never written (they have no bindings), which is the property the design relies on;
  it is checked by the structure tests but not enforced by the type system.
* **What the JVM matrix covers**: forge ran on Java 8, 17 and 21 with `JxmlLiveTest`, which drives a generated view's
  session (recording, checks, writes in place, the rebuilt tab pane, the fallbacks) against a node that only mirrors the
  element tree, and compiles the generated `Live` class at `-source 8`. The renderer's nodes (`setProp`, `commit`, layout),
  the 94 components, the samples and all the measurements ran on Java 17 only.
* **The host is tested without a display.** `JXWindow.setLive` posts to the display thread and refreshes in the frame loop;
  the tests call the display-thread parts directly (`JXLiveHost`, `mountLive`, `refreshLive`), so that the posting, the
  frame loop and a real window with a real controller changing on another thread were not run. The NanoVG and Skia renderers
  read the same nodes and were not run. Java 8 x86 (32-bit) was not measured (the tests for forge ran on 8). Threads: the
  session is single-threaded, like the tree; a model changed from another thread while a frame refreshes is a race the
  window does not prevent.
* **Virtual lists are live only for plain row templates**, and only while the number of items and of rows is the same: adding
  a row to the model, which is what a real list does, is a fallback (the whole screen is built and reconciled). The tests
  prove parity for scrolls and edits; they do not prove it for every row template (the two shapes tested: one `cell`, one
  `tablerow` of three `cell`s).
* **Row identity is the key's identity**, as the node's keyed reconcile has it: a key that is a fresh boxed number each time
  (`key="${r.id}"` on an `int`) never matches and every row is built anew on every scroll, the same as the reconcile it
  replaces. A list without a key is matched by position.
* **Trees only save the rows.** A tree still builds all its items on every refresh; the saving is the rows of the window, and
  only for a memoized model (a model built anew has new item elements each time). The first refresh makes all the rows.
  `odd` is written in place without notifying the node (nothing in the renderer reads it).
* **A fitted table column keeps the width fitted to the first rows it showed** (`ColumnState`): a reconcile, and so live,
  differs from a fresh mount when later rows are wider, for a `TreeTable` (or a table) with a column width of -1. Found when
  a first test used such a column with a window of one row; it is in the reconcile of `main`, not in this work, and the tree
  table tests use explicit widths.
* **Test oracles**: parity is against the current reconcile and against a fresh mount; if both were wrong in the same way
  (the `Tooltip` case, since fixed, was one the two shared) the tests would not see it. The pixel buffer comparison skips drawings that depend
  on the clock.

## 7. Open questions

* **Virtual lists: what is left.** A list that gains or loses an item takes the global fallback; the item count could be a
  window change too (rows are only added or dropped at the end). Trees build all their items on every refresh; recording the
  item model (the generated code would have to give the item elements apart from the rows) would let a tree bind the item
  values and make no element for rows that stay, also for a model that is not memoized. Row templates with loops or
  conditions in the row are still opaque. `Tabs` and grouped radios are opaque.
* **Scoping the fallback.** The fallback rebuilds the whole screen. The compiler already emits one method per container; a
  fallback to the smallest enclosing container whose children are keyed would make "one row was added" cost one row.
* **A smaller recording.** 120 B per bound instance is the site, the binding and the scope. A site and a binding could be one
  object for an element with one binding; a scope for a body with one element could be the site. The array of sites could be
  replaced by walking the node tree with a cursor.
* **Skipping the reads.** A binding that reads only its loop variable cannot have changed when the item of its scope did not
  (`==`): it could be skipped; a refresh that finds nothing changed would then cost one comparison per item instead of one
  getter per binding. Needs the plan to say which getters read the controller.
* **Layout.** Both paths pay about 24 B and 0.2 to 0.5 µs per child in `layoutForBackend` after a size-changing update, and the
  box layout lays out again every child of a column that has a `RESIZED` child. Nothing in this work shortens that; it is now
  what is left of the allocation.
* **Retaining the element tree at all.** The session needs it only to pair nodes. After attach, the elements could be released
  and the node's props (the same objects) kept; the element tree would be rebuilt on demand.
* **Hooking the host: what is left.** `JXWindow.setLive` exists (2.4), but nothing tells the window the model changed: the
  application calls `requestRender()`. Whether a model that notifies (a change event, an observable) should do it, and how
  that meets the single-threaded tree, is open; so is measuring a real window.
* **Handlers that hold loop variables** could read them from the scope, which would make the loop that holds its item stop
  falling back when the list is replaced; that touches the code `build` runs, so it was not done here.
* **Does it generalize?** The benchmark updates one prop of every instance. An application's updates are one or a few
  props of a few controls: live pays a getter read per binding per refresh (about 50 ns) however few changed. Whether the
  skip above, or dependency tracking on the controller, is needed to win there is not measured.

## 8. Reproduce

(The numbers of 5.1 to 5.3 are the first series, before the host and the virtual lists of 2.4 to 2.7. The view generator skips
a screen whose `.jxml` is unchanged even when the compiler changed: delete `jxparallel-examples-native\target\generated-sources`
before a build that must regenerate the benchmark's views.)

```powershell
# build (the four modules in one reactor: the generated views need this tree's forge, not the installed one)
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'; $env:MAVEN_OPTS='-Djavax.net.ssl.trustStoreType=Windows-ROOT'
mvn -o -pl jxparallel-core,jxparallel-ui,jxparallel-forge,jxparallel-examples-native test
mvn -o -P legacy-javafx -pl jxparallel-examples -am compile          # the JavaFX counterparts
mvn -o -pl jxparallel-examples-native dependency:build-classpath '-Dmdep.outputFile=target/classpath.txt'
# the series of the subset (about 8 minutes), then the steady state (about 10), then the report
scripts\measure-components.ps1 -Runs 5 -Passes 8 -Jvms java17-x64 -Modes javafx,compiled,live -Out docs\research\live-2026-10-26 `
    -Components Separator,Slider,ScrollBar,ProgressBar,Image,TableView,ListView,Tree,TreeTable,Label,Button,TextField,Grid
scripts\measure-live-steady.ps1 -Rounds 2000
python -I scripts\summarize-components.py --dir docs\research\live-2026-10-26 --out docs\research\live-2026-10-26\report.md
```

Files in `docs/research/live-2026-10-26/`: `components-java17-x64.csv` (a row per run), `-median.csv`, `environment.json`,
`report.md` (the summarizer's tables, with the live section), `steady-runs.csv`, `steady-median.csv`, `live-coverage.csv`
(per component of the 94: refreshes, fallbacks, opaque builds, props written, size of the recording; written by
`LiveComponentsTest`).
