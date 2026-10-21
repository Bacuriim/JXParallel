# JXML components one by one, against JavaFX: time, memory, CPU and classes

Each JXML component was measured alone, in four modes, with the JavaFX control it corresponds to
built in plain Java (no FXML) as the reference. 84 components were measured (94 were generated; the 10 that are pure renames of another tag
were skipped); **43 have a JavaFX equivalent** and are compared, 41 have none and are reported in absolute numbers.

Per-component tables (every component, every mode, every JVM): [jxforge-components-tables-2026-10-21.md](jxforge-components-tables-2026-10-21.md).
Raw data: `docs/components-2026-10-21/` (one CSV row per run, and a `-median.csv` with median, minimum and maximum).

## Summary

Compiled JXML against JavaFX, geometric mean over the 43 components that have a JavaFX
equivalent, on Java 17 x64 (the other JVMs are below). Above 1x, JXML is better.

| What | JavaFX / JXML |
|---|---:|
| Build the components, cold (first pass) | 19.6x faster |
| Build the components, warm | 16.7x faster |
| Ready to paint, cold | 4.9x faster |
| Ready to paint, warm | 79.6x faster |
| Change one prop of every instance | 4.4x faster |
| Paint (indicative, different pipelines) | 3.4x faster |
| Memory allocated, build / ready / update (warm) | 15.2x / 78.2x / 1.8x less |
| Memory retained per instance | 39.4x less |
| Classes loaded | 2.1x fewer |
| CPU of the whole process, from JVM start | 7.3x less |

![Compiled JXML against JavaFX, Java 17 x64: 20x faster to build cold, 17x warm, 80x ready to paint warm, 4.4x to update, 39x less retained memory](assets/charts/headline-java17.svg)

The charts are SVG (light and dark) with a PNG copy of each in `docs/assets/charts/`, drawn by `scripts/make-component-charts.py` from the CSVs.

**Where JXML is not ahead** (details and causes in "Where JXML is not ahead"):

- **Updating** `Tree` and `TreeTable` (0.37x to 0.60x, and 100x more allocation): every render flattens the whole model.
- **Updating** `ScrollBar`, `Separator`, `Slider`, `ProgressBar` and `Image`: a JavaFX property set costs less than rebuilding an immutable element, so JXML allocates 3x to 16x more per update, and it is slower for `ScrollBar` and `Separator`.
- **Painting** `TextField`, `PasswordField` and `Tabs` (0.63x to 0.76x): the software painter does not skip nodes outside the window.
- **`Image`** is the weakest component on most columns.

How to read the big ratios: "ready to paint" compares native nodes laid out against a JavaFX
`Scene` with CSS applied and a skin built for every control. The two do different work by design,
which is the architecture difference this project is about, not a faster implementation of the same
work. The ratios in "build" and "update" are closer to like for like, and the cold "ready" ratios
are small because the JXML side pays a fixed first-use cost of about 140 ms (class loading and
JIT of the layout code) that does not depend on the component.

## Method

| | |
|---|---|
| Machine | Windows 11, Intel Core i5-12400F (6 cores, 12 threads), 15.8 GB; background CPU 3 to 6% on average, 15% at most (recorded before each series in `environment.json`) |
| JVMs | Oracle JDK 1.8.0_51 x86 (client VM, JavaFX 8 bundled), Oracle JDK 17.0.12 x64 and JDK 21.0.8 x64 (OpenJFX 21.0.2) |
| Modes | `javafx`: the control built in code on the FX thread. `interpreted`: the JXML file read and checked on every build. `template`: read once, built many times. `compiled`: the class JXForge generated at build time (hoisting on) |
| Runs | 5 per component, mode and JVM, each a fresh JVM, modes rotating their place in every run, components in the outer loop; 8 passes of N instances inside each run; medians of the runs |
| Heap | `-Xms512m -Xmx512m` for every JVM and mode, so the working set and the collector's work do not depend on the machine's RAM |
| N | instances per pass, fixed per component and equal for every mode and JVM (column `n` of the CSVs); 5,000 for light leaves, 1,500 for heavy leaves and plain containers, 800 for heavy containers, 20 for list and table (over a 10,000-row model, about 30 rows built), 8 for trees |
| Verification | every run compares a fingerprint (instances, node count, the value read back from the nodes before and after the update) with the other modes; a separate JVM per component checks that interpreted, template and compiled build identical trees. **All 4,425 measured runs are `ok` and verified**, and the 252 equivalence checks (84 per JVM) pass |

Stages inside every JVM, in this order:

1. **build**: N instances in a container. JXML builds the element tree (bindings through a `<For>`); JavaFX creates the controls in a `VBox`.
2. **ready**: JXML creates the native nodes and lays them out at 1280x800, mounting deferred tabs; JavaFX makes a `Scene` of 1280x800, applies CSS and lays out. The tree or the controls are built before the clock starts.
3. **update**: the value of one prop of every instance changes (text, value or selected, two value sets alternating). JXML builds the new element tree, reconciles it into the mounted nodes and lays out again; JavaFX sets the property on each control and lays out. For the lists and trees, the update scrolls 7 rows.
4. **paint**: JXML paints the laid-out nodes with the software painter into a pixel buffer; JavaFX takes a `Scene.snapshot`. This is indicative only: they are different pipelines.
5. **retained**: the last instances are kept alive and the live heap is read after a full collection with and without them; the retained size per instance is the difference over N.

Cold is pass 1 (class loading, interpreter, first JIT), warm is pass 8. Allocation is per thread
(`ThreadMXBean`); CPU is the whole process, from JVM start to the end of the run.

## Results by JVM

Every table compares the 43 components that have a JavaFX equivalent. "Worst" is the component
where JXML is least ahead (or behind, below 1x); "JXML worse in" counts the components below 1x.

﻿![The same metrics on Java 8 x86, 17 x64 and 21 x64, as grouped bars](assets/charts/by-jvm.svg)

### java8-x86: 43 components with a JavaFX equivalent

JavaFX divided by JXML compiled (above 1x, JXML is better):

| Metric | geo mean | median | best | worst | JXML worse in |
|---|---:|---:|---:|---:|---:|
| build cold | 17.7x | 19.1x | 27x (Spinner) | 6.51x (TreeTable) | 0 of 43 |
| build warm | 37.0x | 27.4x | 291x (Stack) | 4.13x (TreeTable) | 0 of 43 |
| ready cold | 3.9x | 3.6x | 18x (Pagination) | 0.67x (Image) | 1 of 43 |
| ready warm | 160.6x | 149.3x | 4938x (Pagination) | 1.11x (Image) | 0 of 43 |
| update warm | 5.0x | 5.3x | 69x (Pagination) | 0.21x (Tree) | 6 of 43 |
| paint warm (indicative) | 5.0x | 6.0x | 32x (ProgressIndicator) | 0.34x (Image) | 5 of 43 |
| alloc, build warm | 16.3x | 20.8x | 36x (Spinner) | 0.55x (TableView) | 2 of 43 |
| alloc, ready warm | 64.0x | 56.2x | 765x (ComboBox) | 0.49x (Image) | 1 of 43 |
| alloc, update warm | 2.1x | 2.2x | 28x (DatePicker) | 0.01x (Tree) | 10 of 43 |
| classes loaded | 2.0x | 1.9x | 2x (TreeTable) | 1.68x (Image) | 0 of 43 |
| whole-process CPU | 6.9x | 6.5x | 63x (Pagination) | 1.49x (Image) | 0 of 43 |
| retained | n/a (not reliable on this JVM) | | | | |

JavaFX divided by JXML interpreted (above 1x, JXML is better):

| Metric | geo mean | median | best | worst | JXML worse in |
|---|---:|---:|---:|---:|---:|
| build cold | 2.8x | 2.9x | 4x (Spinner) | 1.71x (TreeTable) | 0 of 43 |
| build warm | 3.8x | 2.8x | 22x (Button) | 0.70x (ListView) | 2 of 43 |
| ready cold | 4.0x | 3.6x | 18x (Pagination) | 0.70x (Image) | 1 of 43 |
| ready warm | 166.9x | 141.5x | 9835x (Pagination) | 1.14x (Image) | 0 of 43 |
| update warm | 1.9x | 2.1x | 15x (CheckBox) | 0.07x (TreeTable) | 8 of 43 |
| paint warm (indicative) | 5.0x | 5.7x | 32x (ProgressIndicator) | 0.33x (Image) | 5 of 43 |
| alloc, build warm | 5.4x | 7.0x | 15x (Spinner) | 0.17x (TableView) | 2 of 43 |
| alloc, ready warm | 64.0x | 56.2x | 765x (ComboBox) | 0.49x (Image) | 1 of 43 |
| alloc, update warm | 0.9x | 0.8x | 13x (CheckBox) | 0.00x (Tree) | 25 of 43 |
| classes loaded | 1.7x | 1.6x | 2x (TreeTable) | 1.42x (Image) | 0 of 43 |
| whole-process CPU | 5.7x | 5.5x | 52x (Pagination) | 1.23x (Image) | 0 of 43 |
| retained | n/a (not reliable on this JVM) | | | | |

Where compiled JXML is worse than JavaFX:

- build cold: none
- build warm: none
- ready cold: Image 0.67x
- ready warm: none
- update warm: Tree 0.21x, TreeTable 0.23x, ScrollBar 0.30x, Slider 0.34x, Image 0.42x, Separator 0.49x
- paint warm (indicative): Image 0.34x, Tabs 0.39x, PasswordField 0.45x, TextField 0.51x, TextArea 0.66x
- alloc, build warm: TableView 0.55x, ListView 0.63x
- alloc, ready warm: Image 0.49x
- alloc, update warm: Tree 0.01x, TreeTable 0.03x, Separator 0.04x, Slider 0.11x, ScrollBar 0.11x, TableView 0.19x, Image 0.27x, ProgressBar 0.30x
- classes loaded: none
- whole-process CPU: none

### java17-x64: 43 components with a JavaFX equivalent

JavaFX divided by JXML compiled (above 1x, JXML is better):

| Metric | geo mean | median | best | worst | JXML worse in |
|---|---:|---:|---:|---:|---:|
| build cold | 19.6x | 20.6x | 29x (Spinner) | 7.88x (Image) | 0 of 43 |
| build warm | 16.7x | 20.0x | 51x (Spinner) | 4.26x (TreeTable) | 0 of 43 |
| ready cold | 4.9x | 4.7x | 14x (ComboBox) | 0.68x (Image) | 1 of 43 |
| ready warm | 79.6x | 67.5x | 1294x (ComboBox) | 2.13x (Image) | 0 of 43 |
| update warm | 4.4x | 5.0x | 32x (DatePicker) | 0.37x (TreeTable) | 4 of 43 |
| paint warm (indicative) | 3.4x | 4.0x | 12x (ProgressIndicator) | 0.63x (PasswordField) | 3 of 43 |
| alloc, build warm | 15.2x | 21.1x | 39x (Spinner) | 0.62x (TableView) | 2 of 43 |
| alloc, ready warm | 78.2x | 64.9x | 1044x (ComboBox) | 0.51x (Image) | 1 of 43 |
| alloc, update warm | 1.8x | 1.7x | 35x (DatePicker) | 0.01x (TreeTable) | 12 of 43 |
| classes loaded | 2.1x | 2.0x | 2x (TreeTable) | 1.73x (Image) | 0 of 43 |
| whole-process CPU | 7.3x | 6.8x | 73x (ComboBox) | 1.45x (Image) | 0 of 43 |
| retained | 39.4x | 32.7x | 693x (Pagination) | 3.17x (Image) | 0 of 43 |

JavaFX divided by JXML interpreted (above 1x, JXML is better):

| Metric | geo mean | median | best | worst | JXML worse in |
|---|---:|---:|---:|---:|---:|
| build cold | 2.3x | 2.4x | 3x (Spinner) | 1.15x (Image) | 0 of 43 |
| build warm | 2.5x | 2.3x | 7x (Label) | 0.55x (ListView) | 3 of 43 |
| ready cold | 5.0x | 4.7x | 15x (Pagination) | 0.63x (Image) | 1 of 43 |
| ready warm | 79.1x | 67.8x | 1432x (ComboBox) | 1.60x (Image) | 0 of 43 |
| update warm | 1.9x | 1.8x | 13x (DatePicker) | 0.13x (TreeTable) | 8 of 43 |
| paint warm (indicative) | 3.5x | 4.1x | 10x (ProgressIndicator) | 0.60x (PasswordField) | 3 of 43 |
| alloc, build warm | 4.9x | 6.5x | 15x (Spinner) | 0.19x (TableView) | 2 of 43 |
| alloc, ready warm | 78.4x | 64.9x | 1047x (ComboBox) | 0.51x (Image) | 1 of 43 |
| alloc, update warm | 0.8x | 0.7x | 16x (DatePicker) | 0.00x (TreeTable) | 27 of 43 |
| classes loaded | 1.8x | 1.8x | 2x (TreeTable) | 1.50x (Image) | 0 of 43 |
| whole-process CPU | 6.0x | 5.8x | 58x (ComboBox) | 1.11x (Image) | 0 of 43 |
| retained | 36.6x | 29.4x | 582x (Pagination) | 3.18x (Image) | 0 of 43 |

Where compiled JXML is worse than JavaFX:

- build cold: none
- build warm: none
- ready cold: Image 0.68x
- ready warm: none
- update warm: TreeTable 0.37x, ScrollBar 0.46x, Tree 0.60x, Separator 0.88x
- paint warm (indicative): PasswordField 0.63x, TextField 0.67x, Tabs 0.76x
- alloc, build warm: TableView 0.62x, ListView 0.79x
- alloc, ready warm: Image 0.51x
- alloc, update warm: TreeTable 0.01x, Tree 0.01x, Separator 0.06x, Slider 0.16x, ScrollBar 0.16x, TableView 0.21x, Image 0.28x, ProgressBar 0.33x
- classes loaded: none
- whole-process CPU: none
- retained: none

### java21-x64: 43 components with a JavaFX equivalent

JavaFX divided by JXML compiled (above 1x, JXML is better):

| Metric | geo mean | median | best | worst | JXML worse in |
|---|---:|---:|---:|---:|---:|
| build cold | 19.3x | 19.8x | 30x (Spinner) | 7.84x (Image) | 0 of 43 |
| build warm | 14.9x | 15.6x | 46x (Row) | 3.45x (Tree) | 0 of 43 |
| ready cold | 4.1x | 3.9x | 12x (Pagination) | 0.58x (Image) | 1 of 43 |
| ready warm | 71.0x | 60.3x | 1940x (ComboBox) | 2.40x (Image) | 0 of 43 |
| update warm | 4.1x | 4.7x | 24x (Pagination) | 0.23x (TreeTable) | 6 of 43 |
| paint warm (indicative) | 3.2x | 3.6x | 12x (ProgressIndicator) | 0.55x (TextField) | 4 of 43 |
| alloc, build warm | 15.1x | 21.1x | 40x (Spinner) | 0.62x (TableView) | 2 of 43 |
| alloc, ready warm | 80.7x | 69.7x | 1050x (ComboBox) | 0.51x (Image) | 1 of 43 |
| alloc, update warm | 1.9x | 1.9x | 35x (DatePicker) | 0.01x (TreeTable) | 11 of 43 |
| classes loaded | 2.1x | 2.0x | 2x (TreeTable) | 1.71x (Image) | 0 of 43 |
| whole-process CPU | 6.8x | 6.1x | 91x (ComboBox) | 1.51x (Image) | 0 of 43 |
| retained | 39.5x | 32.7x | 695x (Pagination) | 3.20x (Image) | 0 of 43 |

JavaFX divided by JXML interpreted (above 1x, JXML is better):

| Metric | geo mean | median | best | worst | JXML worse in |
|---|---:|---:|---:|---:|---:|
| build cold | 2.8x | 2.9x | 4x (Spinner) | 1.22x (Image) | 0 of 43 |
| build warm | 2.4x | 2.4x | 8x (Spinner) | 0.45x (ListView) | 4 of 43 |
| ready cold | 4.3x | 3.9x | 13x (Pagination) | 0.59x (Image) | 1 of 43 |
| ready warm | 71.6x | 56.5x | 1983x (ComboBox) | 1.70x (Image) | 0 of 43 |
| update warm | 1.9x | 1.9x | 10x (DatePicker) | 0.14x (TreeTable) | 8 of 43 |
| paint warm (indicative) | 3.2x | 3.2x | 15x (ProgressIndicator) | 0.43x (TextField) | 4 of 43 |
| alloc, build warm | 4.9x | 6.5x | 15x (Spinner) | 0.19x (TableView) | 2 of 43 |
| alloc, ready warm | 81.3x | 69.7x | 1050x (ComboBox) | 0.51x (Image) | 1 of 43 |
| alloc, update warm | 0.9x | 0.9x | 16x (DatePicker) | 0.00x (TreeTable) | 25 of 43 |
| classes loaded | 1.8x | 1.8x | 2x (TreeTable) | 1.51x (Image) | 0 of 43 |
| whole-process CPU | 5.6x | 5.1x | 70x (ComboBox) | 1.10x (Image) | 0 of 43 |
| retained | 36.7x | 29.5x | 607x (Pagination) | 3.22x (Image) | 0 of 43 |

Where compiled JXML is worse than JavaFX:

- build cold: none
- build warm: none
- ready cold: Image 0.58x
- ready warm: none
- update warm: TreeTable 0.23x, Tree 0.62x, ScrollBar 0.69x, Separator 0.80x, TableView 0.90x, Image 0.97x
- paint warm (indicative): TextField 0.55x, PasswordField 0.59x, Tabs 0.61x, TextArea 0.66x
- alloc, build warm: TableView 0.62x, ListView 0.78x
- alloc, ready warm: Image 0.51x
- alloc, update warm: TreeTable 0.01x, Tree 0.01x, Separator 0.06x, Slider 0.16x, ScrollBar 0.17x, TableView 0.21x, Image 0.28x, ProgressBar 0.33x
- classes loaded: none
- whole-process CPU: none
- retained: none


## Per instance, Java 17 x64

![Memory kept alive per instance for twelve components, JavaFX against compiled JXML](assets/charts/retained-per-instance.svg)

| Component | N | build warm µs | ready warm µs | update warm µs | alloc ready warm B | retained B |
|---|---:|---:|---:|---:|---:|---:|
| Label | 5000 | 4.42 / 0.160 | 33.0 / 0.52 | 6.98 / 0.548 | 33274 / 513 | 12085 / 607 |
| Button | 5000 | 2.37 / 0.172 | 36.0 / 0.70 | 5.75 / 0.655 | 34251 / 545 | 17855 / 611 |
| CheckBox | 5000 | 2.26 / 0.287 | 59.2 / 0.51 | 8.39 / 0.676 | 50340 / 512 | 25284 / 611 |
| TextField | 1500 | 7.31 / 0.250 | 207.8 / 0.87 | 8.38 / 0.654 | 145947 / 635 | 76748 / 609 |
| ComboBox | 1500 | 7.52 / 0.488 | 1541.1 / 1.19 | 10.00 / 1.145 | 539498 / 517 | 188133 / 633 |
| DatePicker | 1500 | 6.88 / 0.305 | 338.0 / 0.95 | 23.06 / 0.720 | 244935 / 516 | 104914 / 603 |
| Slider | 5000 | 4.25 / 0.262 | 38.8 / 0.56 | 0.52 / 0.435 | 42183 / 512 | 22963 / 634 |
| Row | 1500 | 5.13 / 0.205 | 119.1 / 2.35 | 7.14 / 1.970 | 99742 / 2211 | 39121 / 2188 |
| Column | 1500 | 6.61 / 0.227 | 142.1 / 2.12 | 10.02 / 1.998 | 99966 / 2131 | 39059 / 2190 |
| Grid | 1500 | 6.48 / 0.297 | 127.2 / 4.97 | 9.48 / 3.185 | 100738 / 4349 | 41040 / 2188 |
| Stack | 1500 | 6.50 / 0.217 | 117.7 / 2.32 | 6.68 / 1.670 | 99702 / 2147 | 38973 / 2192 |
| Tabs | 800 | 9.09 / 0.539 | 634.2 / 3.38 | 6.36 / 2.390 | 451600 / 2069 | 209890 / 2289 |
| TitledPane | 800 | 4.62 / 0.244 | 184.8 / 1.78 | 13.92 / 1.475 | 115098 / 1092 | 46744 / 1147 |
| ProgressBar | 5000 | 4.12 / 0.217 | 33.7 / 0.59 | 1.33 / 0.647 | 37047 / 576 | 16566 / 673 |
| Image | 5000 | 1.96 / 0.441 | 1.2 / 0.55 | 0.86 / 0.800 | 277 / 544 | 1961 / 619 |

(JavaFX / compiled JXML in each cell.)

### Lists and trees (a 10,000-row model, about 30 rows built; per pass, milliseconds)

| Component | N | build warm | ready warm | update warm (scroll 7 rows) | alloc update warm KB |
|---|---:|---:|---:|---:|---:|
| ListView | 20 | 1.25 / 0.23 | 26.41 / 0.45 | 2.34 / 0.90 | 112 / 241 |
| TableView | 20 | 2.66 / 0.39 | 67.94 / 1.25 | 1.86 / 1.53 | 209 / 983 |
| Tree | 8 | 67.72 / 11.53 | 18.48 / 0.20 | 3.33 / 5.59 | 225 / 21409 |
| TreeTable | 8 | 51.63 / 12.12 | 69.71 / 0.63 | 2.35 / 6.33 | 188 / 23477 |

(JavaFX / compiled JXML in each cell.)

### Components with no JavaFX equivalent (compiled JXML, per instance)

| Component | N | build warm µs | ready warm µs | update warm µs | alloc ready warm B | retained B |
|---|---:|---:|---:|---:|---:|---:|
| AlertDialog | 100 | 1.476 | 13.61 | 3.762 | 3724 | 3297 |
| Align | 1500 | 0.259 | 1.83 | 1.879 | 1371 | 1370 |
| AppBar | 2000 | 0.435 | 2.73 | 2.504 | 1965 | 1834 |
| Avatar | 2000 | 0.203 | 1.18 | 0.631 | 516 | 602 |
| Badge | 2000 | 0.166 | 1.37 | 0.838 | 1172 | 1130 |
| BottomAppBar | 2000 | 0.318 | 2.27 | 2.755 | 1933 | 1834 |
| Card | 2000 | 0.522 | 1.96 | 2.343 | 1700 | 1786 |
| Center | 1500 | 0.247 | 1.53 | 1.848 | 1203 | 1322 |
| Chip | 2000 | 0.252 | 0.96 | 0.840 | 564 | 602 |
| ContextMenu | 1000 | 0.185 | 1.43 | 0.981 | 1437 | 1458 |
| Dialog | 100 | 1.112 | 7.55 | 5.266 | 3157 | 2825 |
| Drawer | 100 | 0.668 | 5.03 | 3.802 | 1748 | 1769 |
| Expanded | 1500 | 0.249 | 1.74 | 2.553 | 1715 | 1586 |
| Fab | 2000 | 0.202 | 2.14 | 1.143 | 1292 | 1194 |
| Flexible | 1500 | 0.248 | 2.25 | 2.107 | 1715 | 1586 |
| Icon | 2000 | 0.207 | 0.85 | 0.970 | 516 | 610 |
| IconButton | 2000 | 0.282 | 1.55 | 0.797 | 1228 | 1194 |
| ListTile | 2000 | 0.514 | 4.43 | 4.670 | 3725 | 3442 |
| Modal | 100 | 0.928 | 7.85 | 5.182 | 1796 | 1818 |
| NavigationBar | 2000 | 0.326 | 2.62 | 3.156 | 2356 | 2210 |
| NavigationRail | 2000 | 0.315 | 2.54 | 2.149 | 1933 | 1834 |
| Scaffold | 2000 | 0.527 | 5.50 | 4.514 | 4261 | 3610 |
| SizedBox | 1500 | 0.431 | 1.64 | 1.611 | 1267 | 1266 |
| SnackBar | 1000 | 0.284 | 1.11 | 1.162 | 517 | 586 |
| Spacer | 1500 | 0.204 | 2.06 | 2.135 | 2227 | 2036 |
| Stepper | 2000 | 0.198 | 0.99 | 0.761 | 564 | 586 |
| Switch | 2000 | 0.196 | 0.90 | 0.742 | 516 | 602 |
| TabBar | 2000 | 0.419 | 1.03 | 1.058 | 556 | 618 |
| TimePicker | 1500 | 0.345 | 1.13 | 0.909 | 517 | 603 |
| Tooltip | 5000 | 0.177 | 0.54 | 0.369 | 544 | 607 |


## Where JXML is not ahead

What the data shows, and what is only a likely cause.

![Changing one prop of every instance, per component: JXML is faster for 39 of 43, slower for TreeTable, ScrollBar, Tree and Separator](assets/charts/update-by-component.svg)

![Painting per component (indicative): JXML is slower for text inputs and tabs](assets/charts/paint-by-component.svg)

![Memory allocated by the update, per component: tiny updates and the trees allocate more in JXML](assets/charts/alloc-update-by-component.svg)

- **Tree and TreeTable update.** 100x more allocation and 0.2x to 0.6x the speed of scrolling 7 rows (5.6 ms against 3.3 ms for `Tree` on Java 17, 21 MB allocated against 225 KB). Cause, from the code: `JxmlRuntime.tree` flattens the whole model (10,100 items) on every build and only then windows it for the renderer, so the cost follows the model, not the screen. A lazy flatten of the visible window would remove it. `ListView` and `TableView` do not have this problem: they are ahead of JavaFX on update time on Java 8 and 17 and at 0.9x for `TableView` on Java 21.
- **Small updates (`ScrollBar`, `Slider`, `ProgressBar`, `Separator`, `Image`).** For a prop that is a plain field in JavaFX, a set is almost free, while JXML builds a new immutable element and reconciles it: 3x to 16x more allocation, and slower for `ScrollBar` and `Separator` (0.46x and 0.88x on Java 17). This is the cost of the model, not a defect of one component; for every component with real work per instance (text, combo, date, tabs) the update is faster in JXML.
- **Paint of text inputs and tabs.** 0.55x to 0.76x on Java 17 and 21. `JXPaint` has no test that skips nodes outside the window (read in the source, not isolated by an experiment): with 1,500 instances and about 30 visible, the JXML painter paints all of them, and JavaFX does not. Culling in the painter should close most of it.
- **`TableView` and `ListView` build allocation** (0.62x and 0.79x): JXML builds the 30 rows and their cells in the build stage, while JavaFX creates its rows during layout. In the ready stage, which includes that work, JXML allocates less (see the lists table).
- **`Image`** is the weakest component: slower cold ready (0.58x to 0.68x), twice the allocation and the smallest wins elsewhere. Cause not investigated.
- **Cold start.** The JXML side pays about 140 ms in the cold ready stage whatever the component is, so for one or a few components the cold ready win is small (4x to 5x on average); the warm win is the one that grows with the number of components.

## What cannot be said from these numbers

- **CPU per stage.** Windows' process CPU has a granularity of about 15.6 ms, and the JXML stages take a few milliseconds per pass: the per-thread CPU of the build stage reads zero in 248 of 420 compiled runs on Java 8 and 141 on Java 17. The whole-process CPU is the only CPU comparison reported; it includes the JVM start and, for JavaFX, the toolkit start, so part of the ratio is that fixed cost, not the component.
- **Retained memory on Java 8 x86.** After `System.gc()` the client VM reports the live heap in steps of about 2.9 MB, more than a JXML component retains (the values jump by about 2.86 MB, for example from 1,809,616 to -1,053,040 bytes, and many come out negative). They are not reported for that JVM; allocation per pass, which is exact, is.
- **Paint.** JXML paints with its software painter; JavaFX with Prism. They are different pipelines, so the paint columns are indicative. Neither measures the GPU path (Skia, NanoVG): that needs a window.
- **"Ready" is not the same work** (see the summary): the ratio shows the architecture, not a faster build of the same objects.
- **Popups, dialogs and menus have no JavaFX comparison**, since JavaFX creates windows for them. They are reported in absolute numbers, closed and open; N popups open at once is not a real screen, so the cost of one popup is (open − closed) / N.
- **N was chosen by hand.** The aim of a build pass of at least 50 ms could not be met on the JXML side (a compiled build costs 0.15 to 0.5 µs per instance) without running JavaFX out of memory at 512 MB. `TextArea` was lowered to 500 and `Pagination` to 300 after the first series ran JavaFX out of memory at 1,500 and 800 instances in the 512 MB heap (JavaFX builds a scroll pane and a page for each); the same N is used by every mode and JVM.
- **The virtualized components differ in structure.** `Pagination` creates its page lazily in JavaFX, so its node counts differ; `ListView`, `TableView`, `Tree` and `TreeTable` count 30 built rows in JXML and one node in JavaFX.
- **One machine, one series.** No run was repeated on another machine or under another load.

## Reproduce

```powershell
mvn -pl jxparallel-forge,jxparallel-ui,jxparallel-examples-native -am install -DskipTests
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
mvn -P legacy-javafx -pl jxparallel-examples -am compile          # the JavaFX runner
mvn -pl jxparallel-examples-native dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
scripts\measure-components.ps1 -Runs 5 -Passes 8                   # about 2.5 hours; resumable
python -I scripts\summarize-components.py --dir docs\components-<date> --out tables.md
```

`scripts/generate-component-bench.py` regenerates the JXML screens, the controllers and the table of
N from `scripts/component-bench-n.csv`. A first series measured no JavaFX at all because a loop in
`measure-components.ps1` overwrote its own `$Modes` parameter (PowerShell names are case
insensitive); it was found by auditing the rows per mode, fixed, and the whole series was run again.
