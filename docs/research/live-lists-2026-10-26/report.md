## Per-component results (live-lists-2026-10-26)

Each cell is the median of the runs of that mode: the value, then in parentheses its ratio against the reference row of the component (JavaFX when the component has a JavaFX counterpart, else the interpreted mode; the reference row itself has no ratio). A ratio above 1 is more cost than the reference; **bold** marks the JXML modes that cost more than JavaFX. `n/a` is a value that was not measured or a ratio with a zero or missing side. Paint compares the software painter of JXML with the Prism pipeline of JavaFX: indicative, not equivalent.

### java17-x64

#### Inputs and display, java17-x64

Time (cold is pass 1, warm the last pass):

| component | mode | n | build cold ms | build warm ms | ready cold ms | ready warm ms | update warm ms | paint warm ms (indicative) |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| Slider | javafx (ref) | 5000 | 136 | 20.3 | 735 | 204 | 2.51 | 40.7 |
| Slider | compiled | 5000 | 5.81 (0.04x) | 1.15 (0.06x) | 168 (0.23x) | 2.17 (0.01x) | 2.02 (0.81x) | 0.91 (0.02x) |
| Slider | live | 5000 | 17.0 (0.13x) | 1.26 (0.06x) | 167 (0.23x) | 2.28 (0.01x) | 1.19 (0.47x) | 0.93 (0.02x) |
| Separator | javafx (ref) | 5000 | 139 | 22.2 | 359 | 108 | 2.73 | 4.53 |
| Separator | compiled | 5000 | 5.15 (0.04x) | 0.89 (0.04x) | 167 (0.47x) | 2.45 (0.02x) | **2.78 (1.02x)** | 2.15 (0.48x) |
| Separator | live | 5000 | 17.1 (0.12x) | 1.12 (0.05x) | 169 (0.47x) | 2.53 (0.02x) | 2.39 (0.87x) | 0.30 (0.07x) |

Memory, CPU and classes:

| component | mode | n | alloc build warm KB | alloc ready warm KB | alloc update warm KB | retained B/instance | thread CPU ms (all stages) | classes loaded | peak working set MB |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Slider | javafx (ref) | 5000 | 16934 | 205971 | 242 | 22949 | 3141 | 2918 | 713 |
| Slider | compiled | 5000 | 626 (0.04x) | 1783 (<0.01x) | **626 (2.58x)** | 448 (0.02x) | 250 (0.08x) | 1416 (0.49x) | 237 (0.33x) |
| Slider | live | 5000 | 1336 (0.08x) | 1784 (<0.01x) | 5.48 (0.02x) | 565 (0.02x) | 266 (0.08x) | 1440 (0.49x) | 229 (0.32x) |
| Separator | javafx (ref) | 5000 | 16349 | 73184 | 123 | 8851 | 1516 | 2623 | 613 |
| Separator | compiled | 5000 | 587 (0.04x) | 2096 (0.03x) | **704 (5.73x)** | 504 (0.06x) | 281 (0.19x) | 1416 (0.54x) | 242 (0.39x) |
| Separator | live | 5000 | 1297 (0.08x) | 2098 (0.03x) | 123 (1.00x) | 621 (0.07x) | 266 (0.18x) | 1440 (0.55x) | 248 (0.40x) |

#### Lists, tables and trees, java17-x64

Time (cold is pass 1, warm the last pass):

| component | mode | n | build cold ms | build warm ms | ready cold ms | ready warm ms | update warm ms | paint warm ms (indicative) |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| ListView | javafx (ref) | 20.0 | 86.9 | 1.21 | 217 | 24.1 | 2.49 | 7.70 |
| ListView | compiled | 20.0 | 5.09 (0.06x) | 0.20 (0.17x) | 114 (0.53x) | 0.40 (0.02x) | 0.72 (0.29x) | 0.68 (0.09x) |
| ListView | live | 20.0 | 18.1 (0.21x) | 0.81 (0.67x) | 128 (0.59x) | 0.37 (0.02x) | 1.07 (0.43x) | 0.88 (0.11x) |
| TableView | javafx (ref) | 20.0 | 99.0 | 2.68 | 402 | 87.3 | 1.50 | 7.81 |
| TableView | compiled | 20.0 | 6.09 (0.06x) | 0.34 (0.13x) | 117 (0.29x) | 1.04 (0.01x) | **1.59 (1.06x)** | 1.37 (0.18x) |
| TableView | live | 20.0 | 20.3 (0.20x) | 1.24 (0.46x) | 131 (0.33x) | 1.06 (0.01x) | **1.71 (1.14x)** | 1.90 (0.24x) |
| Tree | javafx (ref) | 8.00 | 251 | 56.8 | 181 | 17.4 | 3.05 | 6.64 |
| Tree | compiled | 8.00 | 13.6 (0.05x) | 1.14 (0.02x) | 118 (0.65x) | 0.24 (0.01x) | 2.11 (0.69x) | 1.03 (0.16x) |
| Tree | live | 8.00 | 22.5 (0.09x) | 1.20 (0.02x) | 115 (0.63x) | 0.27 (0.02x) | 1.81 (0.59x) | 1.13 (0.17x) |
| TreeTable | javafx (ref) | 8.00 | 260 | 58.1 | 382 | 67.1 | 2.53 | 8.55 |
| TreeTable | compiled | 8.00 | 14.7 (0.06x) | 1.27 (0.02x) | 121 (0.32x) | 0.72 (0.01x) | 2.41 (0.95x) | 1.81 (0.21x) |
| TreeTable | live | 8.00 | 23.9 (0.09x) | 1.37 (0.02x) | 122 (0.32x) | 0.76 (0.01x) | 1.86 (0.73x) | 1.30 (0.15x) |

Memory, CPU and classes:

| component | mode | n | alloc build warm KB | alloc ready warm KB | alloc update warm KB | retained B/instance | thread CPU ms (all stages) | classes loaded | peak working set MB |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| ListView | javafx (ref) | 20.0 | 119 | 7856 | 112 | 162704 | 688 | 3303 | 294 |
| ListView | compiled | 20.0 | 84.6 (0.71x) | 182 (0.02x) | **133 (1.19x)** | 12490 (0.08x) | 203 (0.30x) | 1452 (0.44x) | 102 (0.34x) |
| ListView | live | 20.0 | **302 (2.54x)** | 182 (0.02x) | **124 (1.11x)** | 18706 (0.11x) | 219 (0.32x) | 1486 (0.45x) | 130 (0.44x) |
| TableView | javafx (ref) | 20.0 | 346 | 32840 | 209 | 333750 | 1484 | 3551 | 514 |
| TableView | compiled | 20.0 | 343 (0.99x) | 829 (0.03x) | **601 (2.87x)** | 57344 (0.17x) | 234 (0.16x) | 1453 (0.41x) | 128 (0.25x) |
| TableView | live | 20.0 | **664 (1.92x)** | 829 (0.03x) | **353 (1.69x)** | 66818 (0.20x) | 266 (0.18x) | 1489 (0.42x) | 144 (0.28x) |
| Tree | javafx (ref) | 8.00 | 89017 | 4090 | 225 | 5567780 | 984 | 3299 | 650 |
| Tree | compiled | 8.00 | 112 (<0.01x) | 76.5 (0.02x) | 131 (0.58x) | 15273 (<0.01x) | 234 (0.24x) | 1457 (0.44x) | 107 (0.16x) |
| Tree | live | 8.00 | 114 (<0.01x) | 76.5 (0.02x) | 74.5 (0.33x) | 16746 (<0.01x) | 219 (0.22x) | 1482 (0.45x) | 105 (0.16x) |
| TreeTable | javafx (ref) | 8.00 | 89552 | 21558 | 188 | 6037070 | 1672 | 3591 | 689 |
| TreeTable | compiled | 8.00 | 215 (<0.01x) | 281 (0.01x) | **292 (1.55x)** | 49510 (<0.01x) | 250 (0.15x) | 1459 (0.41x) | 117 (0.17x) |
| TreeTable | live | 8.00 | 217 (<0.01x) | 281 (0.01x) | 139 (0.74x) | 50952 (<0.01x) | 250 (0.15x) | 1484 (0.41x) | 115 (0.17x) |

#### Live bindings against compiled JXML and JavaFX, java17-x64

Update of one prop of every instance, warm pass. Ratios are JavaFX / mode (above 1x the mode is better than JavaFX) and live / compiled (below 1x live is better than compiled). Build and ready include the recording and its pairing with the mounted nodes.

| component | n | update ms compiled | live | JavaFX | JavaFX/compiled | JavaFX/live | live/compiled | alloc KB compiled | live | JavaFX | JavaFX/compiled | JavaFX/live | live/compiled | fallbacks | opaque builds |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Slider | 5000 | 2.02 | 1.19 | 2.51 | 1.24x | 2.11x | 0.59x | 626 | 5.48 | 242 | 0.39x | 44.27x | <0.01x | 0.00 | 0.00 |
| Separator | 5000 | 2.78 | 2.39 | 2.73 | 0.98x | 1.14x | 0.86x | 704 | 123 | 123 | 0.17x | 1.00x | 0.17x | 0.00 | 0.00 |
| ListView | 20.0 | 0.72 | 1.07 | 2.49 | 3.45x | 2.33x | 1.48x | 133 | 124 | 112 | 0.84x | 0.90x | 0.93x | 0.00 | 0.00 |
| TableView | 20.0 | 1.59 | 1.71 | 1.50 | 0.94x | 0.88x | 1.07x | 601 | 353 | 209 | 0.35x | 0.59x | 0.59x | 0.00 | 0.00 |
| Tree | 8.00 | 2.11 | 1.81 | 3.05 | 1.44x | 1.69x | 0.86x | 131 | 74.5 | 225 | 1.72x | 3.02x | 0.57x | 0.00 | 72.0 |
| TreeTable | 8.00 | 2.41 | 1.86 | 2.53 | 1.05x | 1.36x | 0.77x | 292 | 139 | 188 | 0.64x | 1.35x | 0.48x | 0.00 | 72.0 |

Build, ready, retained (compiled and live), warm:

| component | build ms compiled | live | ready ms compiled | live | build KB compiled | live | ready KB compiled | live | retained B/instance compiled | live | live/compiled |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Slider | 1.15 | 1.26 | 2.17 | 2.28 | 626 | 1336 | 1783 | 1784 | 448 | 565 | 1.26x |
| Separator | 0.89 | 1.12 | 2.45 | 2.53 | 587 | 1297 | 2096 | 2098 | 504 | 621 | 1.23x |
| ListView | 0.20 | 0.81 | 0.40 | 0.37 | 84.6 | 302 | 182 | 182 | 12490 | 18706 | 1.50x |
| TableView | 0.34 | 1.24 | 1.04 | 1.06 | 343 | 664 | 829 | 829 | 57344 | 66818 | 1.17x |
| Tree | 1.14 | 1.20 | 0.24 | 0.27 | 112 | 114 | 76.5 | 76.5 | 15273 | 16746 | 1.10x |
| TreeTable | 1.27 | 1.37 | 0.72 | 0.76 | 215 | 217 | 281 | 281 | 49510 | 50952 | 1.03x |

#### Biggest wins and losses of compiled JXML against JavaFX, java17-x64 (build + ready warm)

| | component | compiled ms | JavaFX ms | ratio |
|---|---|---:|---:|---:|
| win | Slider | 3.33 | 224 | 0.01x |
| win | TableView | 1.38 | 90.0 | 0.02x |
| win | TreeTable | 1.99 | 125 | 0.02x |
| win | Tree | 1.38 | 74.2 | 0.02x |
| win | ListView | 0.60 | 25.3 | 0.02x |
| win | Separator | 3.34 | 130 | 0.03x |
| loss | Separator | 3.34 | 130 | 0.03x |
| loss | ListView | 0.60 | 25.3 | 0.02x |
| loss | Tree | 1.38 | 74.2 | 0.02x |
| loss | TreeTable | 1.99 | 125 | 0.02x |
| loss | TableView | 1.38 | 90.0 | 0.02x |
| loss | Slider | 3.33 | 224 | 0.01x |

#### Runs that failed or did not verify, java17-x64

None.

### Components without a JavaFX equivalent

Reported as absolute numbers (no ratio against JavaFX):

- TimePicker: no JavaFX equivalent (JavaFX has no time picker)
- Tooltip: JavaFX Tooltip is a popup window object; not a scene node: it has no layout or paint without a window
- IconButton: no JavaFX equivalent: the icon is a vector drawn by the renderer; JavaFX would need a graphic node built by hand
- Fab: no JavaFX equivalent (floating action button)
- Switch: no JavaFX equivalent
- Icon: no JavaFX equivalent (built-in vector icons)
- Chip: no JavaFX equivalent
- Badge: no JavaFX equivalent
- Avatar: no JavaFX equivalent
- Stepper: no JavaFX equivalent
- TabBar: no JavaFX equivalent: a TabPane always includes the content area; the TabBar is only the header strip
- Card: no JavaFX equivalent (a styled Column)
- ListTile: no JavaFX equivalent
- AppBar: no JavaFX equivalent
- NavigationBar: no JavaFX equivalent
- NavigationRail: no JavaFX equivalent
- BottomAppBar: no JavaFX equivalent
- BottomBar: a pure rename of BottomAppBar: same tree; no JavaFX equivalent
- Scaffold: no JavaFX equivalent (a BorderPane with bars)
- SizedBox: no JavaFX equivalent: JavaFX sets sizes on any Region; there is no separate control
- Spacer: no JavaFX equivalent: a Region with grow priorities
- Expanded: no JavaFX equivalent: grow priorities on the child
- Flexible: no JavaFX equivalent: grow priorities on the child
- Center: no JavaFX equivalent: a StackPane with alignment
- Align: no JavaFX equivalent: a StackPane with alignment
- ContextMenu: JavaFX ContextMenu is a popup window object; not a scene node
- Modal: no JavaFX equivalent in a scene graph: JavaFX dialogs are windows
- Dialog: JavaFX Dialog is a window; DialogPane without a window has no comparable layout
- AlertDialog: JavaFX Alert is a window; not comparable
- Drawer: no JavaFX equivalent
- SnackBar: no JavaFX equivalent
- ComboBoxOpen: open variant: N popups open at once (an amplifier of the cost of one popup; (open - closed) / N is per popup); JavaFX popups are windows: no JavaFX mode
- DatePickerOpen: open variant: N popups open at once (an amplifier of the cost of one popup; (open - closed) / N is per popup); JavaFX popups are windows: no JavaFX mode
- TimePickerOpen: open variant: N popups open at once (an amplifier of the cost of one popup; (open - closed) / N is per popup); JavaFX popups are windows: no JavaFX mode
- ColorPickerOpen: open variant: N popups open at once (an amplifier of the cost of one popup; (open - closed) / N is per popup); JavaFX popups are windows: no JavaFX mode
- MenuButtonOpen: open variant: N popups open at once (an amplifier of the cost of one popup; (open - closed) / N is per popup); JavaFX popups are windows: no JavaFX mode
- ContextMenuOpen: open variant: N popups open at once (an amplifier of the cost of one popup; (open - closed) / N is per popup); JavaFX popups are windows: no JavaFX mode
- ModalOpen: open variant: N popups open at once (an amplifier of the cost of one popup; (open - closed) / N is per popup); JavaFX popups are windows: no JavaFX mode
- DialogOpen: open variant: N popups open at once (an amplifier of the cost of one popup; (open - closed) / N is per popup); JavaFX popups are windows: no JavaFX mode
- AlertDialogOpen: open variant: N popups open at once (an amplifier of the cost of one popup; (open - closed) / N is per popup); JavaFX popups are windows: no JavaFX mode
- DrawerOpen: open variant: N popups open at once (an amplifier of the cost of one popup; (open - closed) / N is per popup); JavaFX popups are windows: no JavaFX mode
- SnackBarOpen: open variant: N popups open at once (an amplifier of the cost of one popup; (open - closed) / N is per popup); JavaFX popups are windows: no JavaFX mode

### Pure renames

These tags build the same tree as the tag they rename (checked by the tests); they are not independent results:

- ChoiceBox = ComboBox
- Divider = Separator
- BottomBar = BottomAppBar
- Wrap = Flow
- Positioned = Stack
- Container = Stack
- Box = Stack
- Padding = Stack
- Opacity = Stack
- Visibility = Stack
