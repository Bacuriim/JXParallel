## Per-component results (live-2026-10-26)

Each cell is the median of the runs of that mode: the value, then in parentheses its ratio against the reference row of the component (JavaFX when the component has a JavaFX counterpart, else the interpreted mode; the reference row itself has no ratio). A ratio above 1 is more cost than the reference; **bold** marks the JXML modes that cost more than JavaFX. `n/a` is a value that was not measured or a ratio with a zero or missing side. Paint compares the software painter of JXML with the Prism pipeline of JavaFX: indicative, not equivalent.

### java17-x64

#### Inputs and display, java17-x64

Time (cold is pass 1, warm the last pass):

| component | mode | n | build cold ms | build warm ms | ready cold ms | ready warm ms | update warm ms | paint warm ms (indicative) |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| Label | javafx (ref) | 5000 | 131 | 20.9 | 765 | 160 | 37.2 | 20.9 |
| Label | compiled | 5000 | 5.13 (0.04x) | 0.76 (0.04x) | 186 (0.24x) | 2.28 (0.01x) | 2.41 (0.06x) | 0.63 (0.03x) |
| Label | live | 5000 | 14.7 (0.11x) | 1.01 (0.05x) | 188 (0.25x) | 2.46 (0.02x) | 2.22 (0.06x) | 0.38 (0.02x) |
| TextField | javafx (ref) | 1500 | 129 | 11.9 | 1153 | 299 | 13.2 | 7.16 |
| TextField | compiled | 1500 | 4.73 (0.04x) | 0.31 (0.03x) | 158 (0.14x) | 1.56 (<0.01x) | 0.96 (0.07x) | 0.96 (0.13x) |
| TextField | live | 1500 | 13.7 (0.11x) | 0.56 (0.05x) | 162 (0.14x) | 1.35 (<0.01x) | 0.62 (0.05x) | 0.77 (0.11x) |
| Slider | javafx (ref) | 5000 | 138 | 20.5 | 754 | 199 | 3.04 | 42.5 |
| Slider | compiled | 5000 | 5.21 (0.04x) | 1.18 (0.06x) | 167 (0.22x) | 2.20 (0.01x) | 2.24 (0.74x) | 3.08 (0.07x) |
| Slider | live | 5000 | 15.7 (0.11x) | 1.24 (0.06x) | 164 (0.22x) | 2.23 (0.01x) | 1.39 (0.46x) | 2.91 (0.07x) |
| ScrollBar | javafx (ref) | 1500 | 107 | 4.54 | 515 | 141 | 0.83 | 35.1 |
| ScrollBar | compiled | 1500 | 4.75 (0.04x) | 0.49 (0.11x) | 161 (0.31x) | 1.34 (<0.01x) | **1.11 (1.33x)** | 0.73 (0.02x) |
| ScrollBar | live | 1500 | 14.2 (0.13x) | 0.54 (0.12x) | 154 (0.30x) | 1.62 (0.01x) | 0.48 (0.57x) | 0.75 (0.02x) |
| ProgressBar | javafx (ref) | 5000 | 136 | 19.3 | 703 | 161 | 8.33 | 5.83 |
| ProgressBar | compiled | 5000 | 5.57 (0.04x) | 0.88 (0.05x) | 166 (0.24x) | 2.45 (0.02x) | 2.31 (0.28x) | 1.94 (0.33x) |
| ProgressBar | live | 5000 | 14.9 (0.11x) | 1.18 (0.06x) | 168 (0.24x) | 2.25 (0.01x) | 2.70 (0.32x) | 0.68 (0.12x) |
| Separator | javafx (ref) | 5000 | 134 | 22.1 | 348 | 107 | 2.61 | 4.39 |
| Separator | compiled | 5000 | 5.09 (0.04x) | 0.86 (0.04x) | 163 (0.47x) | 2.67 (0.02x) | **2.85 (1.09x)** | 2.18 (0.50x) |
| Separator | live | 5000 | 15.3 (0.11x) | 1.28 (0.06x) | 163 (0.47x) | 2.52 (0.02x) | **2.76 (1.06x)** | 2.93 (0.67x) |
| Image | javafx (ref) | 5000 | 69.5 | 8.69 | 99.1 | 5.82 | 4.12 | 6.01 |
| Image | compiled | 5000 | 7.05 (0.10x) | 1.41 (0.16x) | **176 (1.77x)** | 2.90 (0.50x) | 3.16 (0.77x) | 5.05 (0.84x) |
| Image | live | 5000 | 17.9 (0.26x) | 1.82 (0.21x) | **170 (1.71x)** | 2.81 (0.48x) | 2.76 (0.67x) | 4.92 (0.82x) |

Memory, CPU and classes:

| component | mode | n | alloc build warm KB | alloc ready warm KB | alloc update warm KB | retained B/instance | thread CPU ms (all stages) | classes loaded | peak working set MB |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Label | javafx (ref) | 5000 | 17046 | 162475 | 18676 | 12186 | 2984 | 2808 | 716 |
| Label | compiled | 5000 | 548 (0.03x) | 1783 (0.01x) | 665 (0.04x) | 432 (0.04x) | 312 (0.10x) | 1446 (0.51x) | 235 (0.33x) |
| Label | live | 5000 | 1257 (0.07x) | 1783 (0.01x) | 123 (<0.01x) | 555 (0.05x) | 328 (0.11x) | 1466 (0.52x) | 239 (0.33x) |
| TextField | javafx (ref) | 1500 | 7096 | 213897 | 7352 | 76672 | 3828 | 3316 | 694 |
| TextField | compiled | 1500 | 192 (0.03x) | 634 (<0.01x) | 192 (0.03x) | 455 (<0.01x) | 250 (0.07x) | 1445 (0.44x) | 208 (0.30x) |
| TextField | live | 1500 | 405 (0.06x) | 634 (<0.01x) | 5.48 (<0.01x) | 572 (<0.01x) | 297 (0.08x) | 1465 (0.44x) | 204 (0.29x) |
| Slider | javafx (ref) | 5000 | 16934 | 205970 | 240 | 22938 | 3219 | 2918 | 715 |
| Slider | compiled | 5000 | 627 (0.04x) | 1784 (<0.01x) | **626 (2.61x)** | 446 (0.02x) | 250 (0.08x) | 1415 (0.48x) | 240 (0.33x) |
| Slider | live | 5000 | 1336 (0.08x) | 1784 (<0.01x) | 5.48 (0.02x) | 571 (0.02x) | 250 (0.08x) | 1435 (0.49x) | 239 (0.33x) |
| ScrollBar | javafx (ref) | 1500 | 5211 | 135485 | 76.0 | 40774 | 2281 | 2902 | 644 |
| ScrollBar | compiled | 1500 | 215 (0.04x) | 539 (<0.01x) | **215 (2.83x)** | 474 (0.01x) | 219 (0.10x) | 1415 (0.49x) | 184 (0.29x) |
| ScrollBar | live | 1500 | 428 (0.08x) | 539 (<0.01x) | 5.48 (0.07x) | 588 (0.01x) | 203 (0.09x) | 1435 (0.49x) | 206 (0.32x) |
| ProgressBar | javafx (ref) | 5000 | 17835 | 180894 | 672 | 16557 | 2578 | 2666 | 695 |
| ProgressBar | compiled | 5000 | 588 (0.03x) | 2097 (0.01x) | 587 (0.87x) | 505 (0.03x) | 250 (0.10x) | 1415 (0.53x) | 242 (0.35x) |
| ProgressBar | live | 5000 | 1297 (0.07x) | 2096 (0.01x) | 7.85 (0.01x) | 621 (0.04x) | 281 (0.11x) | 1435 (0.54x) | 244 (0.35x) |
| Separator | javafx (ref) | 5000 | 16350 | 73181 | 123 | 8855 | 1453 | 2623 | 619 |
| Separator | compiled | 5000 | 587 (0.04x) | 2096 (0.03x) | **704 (5.73x)** | 504 (0.06x) | 219 (0.15x) | 1415 (0.54x) | 240 (0.39x) |
| Separator | live | 5000 | 1297 (0.08x) | 2098 (0.03x) | 123 (1.00x) | 629 (0.07x) | 266 (0.18x) | 1435 (0.55x) | 250 (0.40x) |
| Image | javafx (ref) | 5000 | 9004 | 1355 | 555 | 1960 | 422 | 2458 | 360 |
| Image | compiled | 5000 | 743 (0.08x) | **1940 (1.43x)** | **1017 (1.83x)** | 472 (0.24x) | 297 (0.70x) | 1417 (0.58x) | 247 (0.69x) |
| Image | live | 5000 | 1720 (0.19x) | **1940 (1.43x)** | 279 (0.50x) | 632 (0.32x) | 297 (0.70x) | 1438 (0.59x) | 246 (0.68x) |

#### Buttons and toggles, java17-x64

Time (cold is pass 1, warm the last pass):

| component | mode | n | build cold ms | build warm ms | ready cold ms | ready warm ms | update warm ms | paint warm ms (indicative) |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| Button | javafx (ref) | 5000 | 138 | 11.7 | 815 | 175 | 24.2 | 23.1 |
| Button | compiled | 5000 | 5.20 (0.04x) | 0.78 (0.07x) | 192 (0.24x) | 2.71 (0.02x) | 2.27 (0.09x) | 0.66 (0.03x) |
| Button | live | 5000 | 16.0 (0.12x) | 1.03 (0.09x) | 193 (0.24x) | 2.53 (0.01x) | 2.46 (0.10x) | 0.62 (0.03x) |

Memory, CPU and classes:

| component | mode | n | alloc build warm KB | alloc ready warm KB | alloc update warm KB | retained B/instance | thread CPU ms (all stages) | classes loaded | peak working set MB |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Button | javafx (ref) | 5000 | 17629 | 167241 | 19205 | 17901 | 3109 | 2959 | 720 |
| Button | compiled | 5000 | 548 (0.03x) | 1940 (0.01x) | 822 (0.04x) | 431 (0.02x) | 312 (0.10x) | 1446 (0.49x) | 234 (0.32x) |
| Button | live | 5000 | 1258 (0.07x) | 1941 (0.01x) | 279 (0.01x) | 549 (0.03x) | 344 (0.11x) | 1466 (0.50x) | 236 (0.33x) |

#### Containers and layout, java17-x64

Time (cold is pass 1, warm the last pass):

| component | mode | n | build cold ms | build warm ms | ready cold ms | ready warm ms | update warm ms | paint warm ms (indicative) |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| Grid | javafx (ref) | 1500 | 139 | 9.39 | 786 | 191 | 13.8 | 17.1 |
| Grid | compiled | 1500 | 5.92 (0.04x) | 0.36 (0.04x) | 191 (0.24x) | 5.99 (0.03x) | 5.90 (0.43x) | 0.59 (0.03x) |
| Grid | live | 1500 | 15.8 (0.11x) | 0.58 (0.06x) | 197 (0.25x) | 7.00 (0.04x) | 4.30 (0.31x) | 0.74 (0.04x) |

Memory, CPU and classes:

| component | mode | n | alloc build warm KB | alloc ready warm KB | alloc update warm KB | retained B/instance | thread CPU ms (all stages) | classes loaded | peak working set MB |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Grid | javafx (ref) | 1500 | 20660 | 147657 | 3546 | 41093 | 3062 | 2829 | 662 |
| Grid | compiled | 1500 | 496 (0.02x) | 5602 (0.04x) | **3593 (1.01x)** | 1712 (0.04x) | 406 (0.13x) | 1449 (0.51x) | 274 (0.41x) |
| Grid | live | 1500 | 790 (0.04x) | 5602 (0.04x) | 3381 (0.95x) | 1875 (0.05x) | 422 (0.14x) | 1469 (0.52x) | 268 (0.40x) |

#### Lists, tables and trees, java17-x64

Time (cold is pass 1, warm the last pass):

| component | mode | n | build cold ms | build warm ms | ready cold ms | ready warm ms | update warm ms | paint warm ms (indicative) |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| ListView | javafx (ref) | 20.0 | 87.9 | 1.18 | 215 | 25.7 | 2.08 | 7.75 |
| ListView | compiled | 20.0 | 5.11 (0.06x) | 0.21 (0.18x) | 116 (0.54x) | 0.46 (0.02x) | 1.07 (0.52x) | 1.01 (0.13x) |
| ListView | live | 20.0 | 11.6 (0.13x) | 0.22 (0.19x) | 117 (0.54x) | 0.38 (0.01x) | 0.68 (0.32x) | 0.72 (0.09x) |
| TableView | javafx (ref) | 20.0 | 103 | 2.45 | 414 | 77.4 | 1.95 | 6.90 |
| TableView | compiled | 20.0 | 6.37 (0.06x) | 0.35 (0.14x) | 122 (0.29x) | 1.10 (0.01x) | 1.61 (0.83x) | 1.43 (0.21x) |
| TableView | live | 20.0 | 12.8 (0.12x) | 0.37 (0.15x) | 120 (0.29x) | 1.50 (0.02x) | 1.79 (0.92x) | 1.32 (0.19x) |
| Tree | javafx (ref) | 8.00 | 244 | 64.4 | 197 | 18.6 | 2.93 | 6.84 |
| Tree | compiled | 8.00 | 13.0 (0.05x) | 1.14 (0.02x) | 115 (0.58x) | 0.24 (0.01x) | 1.94 (0.66x) | 1.11 (0.16x) |
| Tree | live | 8.00 | 19.7 (0.08x) | 1.22 (0.02x) | 114 (0.58x) | 0.24 (0.01x) | 2.41 (0.82x) | 1.06 (0.15x) |
| TreeTable | javafx (ref) | 8.00 | 268 | 52.2 | 383 | 85.5 | 2.66 | 9.26 |
| TreeTable | compiled | 8.00 | 15.1 (0.06x) | 1.32 (0.03x) | 123 (0.32x) | 0.58 (<0.01x) | 1.82 (0.68x) | 1.82 (0.20x) |
| TreeTable | live | 8.00 | 22.5 (0.08x) | 1.33 (0.03x) | 119 (0.31x) | 0.57 (<0.01x) | 1.80 (0.68x) | 2.11 (0.23x) |

Memory, CPU and classes:

| component | mode | n | alloc build warm KB | alloc ready warm KB | alloc update warm KB | retained B/instance | thread CPU ms (all stages) | classes loaded | peak working set MB |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| ListView | javafx (ref) | 20.0 | 118 | 7856 | 112 | 162968 | 719 | 3303 | 288 |
| ListView | compiled | 20.0 | 84.6 (0.72x) | 182 (0.02x) | **132 (1.18x)** | 12730 (0.08x) | 203 (0.28x) | 1451 (0.44x) | 102 (0.35x) |
| ListView | live | 20.0 | 87.8 (0.74x) | 182 (0.02x) | **132 (1.18x)** | 16079 (0.10x) | 203 (0.28x) | 1471 (0.45x) | 101 (0.35x) |
| TableView | javafx (ref) | 20.0 | 346 | 32827 | 212 | 334145 | 1500 | 3551 | 510 |
| TableView | compiled | 20.0 | 343 (0.99x) | 829 (0.03x) | **601 (2.84x)** | 56392 (0.17x) | 234 (0.16x) | 1451 (0.41x) | 128 (0.25x) |
| TableView | live | 20.0 | **347 (1.00x)** | 829 (0.03x) | **600 (2.84x)** | 72570 (0.22x) | 250 (0.17x) | 1471 (0.41x) | 129 (0.25x) |
| Tree | javafx (ref) | 8.00 | 89560 | 4088 | 225 | 5574857 | 1016 | 3299 | 638 |
| Tree | compiled | 8.00 | 112 (<0.01x) | 76.5 (0.02x) | 131 (0.58x) | 15565 (<0.01x) | 234 (0.23x) | 1456 (0.44x) | 108 (0.17x) |
| Tree | live | 8.00 | 114 (<0.01x) | 76.5 (0.02x) | 130 (0.58x) | 20325 (<0.01x) | 219 (0.22x) | 1476 (0.45x) | 107 (0.17x) |
| TreeTable | javafx (ref) | 8.00 | 89949 | 21563 | 188 | 6032500 | 1672 | 3591 | 701 |
| TreeTable | compiled | 8.00 | 215 (<0.01x) | 281 (0.01x) | **292 (1.55x)** | 49442 (<0.01x) | 250 (0.15x) | 1457 (0.41x) | 118 (0.17x) |
| TreeTable | live | 8.00 | 217 (<0.01x) | 281 (0.01x) | **291 (1.55x)** | 65898 (0.01x) | 234 (0.14x) | 1478 (0.41x) | 117 (0.17x) |

#### Live bindings against compiled JXML and JavaFX, java17-x64

Update of one prop of every instance, warm pass. Ratios are JavaFX / mode (above 1x the mode is better than JavaFX) and live / compiled (below 1x live is better than compiled). Build and ready include the recording and its pairing with the mounted nodes.

| component | n | update ms compiled | live | JavaFX | JavaFX/compiled | JavaFX/live | live/compiled | alloc KB compiled | live | JavaFX | JavaFX/compiled | JavaFX/live | live/compiled | fallbacks | opaque builds |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Label | 5000 | 2.41 | 2.22 | 37.2 | 15.42x | 16.76x | 0.92x | 665 | 123 | 18676 | 28.07x | 152.20x | 0.18x | 0.00 | 0.00 |
| TextField | 1500 | 0.96 | 0.62 | 13.2 | 13.82x | 21.44x | 0.64x | 192 | 5.48 | 7352 | 38.31x | 1342.43x | 0.03x | 0.00 | 0.00 |
| Slider | 5000 | 2.24 | 1.39 | 3.04 | 1.36x | 2.18x | 0.62x | 626 | 5.48 | 240 | 0.38x | 43.84x | <0.01x | 0.00 | 0.00 |
| ScrollBar | 1500 | 1.11 | 0.48 | 0.83 | 0.75x | 1.75x | 0.43x | 215 | 5.48 | 76.0 | 0.35x | 13.89x | 0.03x | 0.00 | 0.00 |
| ProgressBar | 5000 | 2.31 | 2.70 | 8.33 | 3.61x | 3.09x | 1.17x | 587 | 7.85 | 672 | 1.14x | 85.61x | 0.01x | 0.00 | 0.00 |
| Separator | 5000 | 2.85 | 2.76 | 2.61 | 0.92x | 0.95x | 0.97x | 704 | 123 | 123 | 0.17x | 1.00x | 0.17x | 0.00 | 0.00 |
| Image | 5000 | 3.16 | 2.76 | 4.12 | 1.30x | 1.49x | 0.87x | 1017 | 279 | 555 | 0.55x | 1.99x | 0.27x | 0.00 | 0.00 |
| Button | 5000 | 2.27 | 2.46 | 24.2 | 10.70x | 9.84x | 1.09x | 822 | 279 | 19205 | 23.37x | 68.84x | 0.34x | 0.00 | 0.00 |
| Grid | 1500 | 5.90 | 4.30 | 13.8 | 2.33x | 3.20x | 0.73x | 3593 | 3381 | 3546 | 0.99x | 1.05x | 0.94x | 0.00 | 0.00 |
| ListView | 20.0 | 1.07 | 0.68 | 2.08 | 1.94x | 3.08x | 0.63x | 132 | 132 | 112 | 0.85x | 0.85x | 1.00x | 0.00 | 180 |
| TableView | 20.0 | 1.61 | 1.79 | 1.95 | 1.21x | 1.09x | 1.11x | 601 | 600 | 212 | 0.35x | 0.35x | 1.00x | 0.00 | 180 |
| Tree | 8.00 | 1.94 | 2.41 | 2.93 | 1.51x | 1.22x | 1.24x | 131 | 130 | 225 | 1.72x | 1.73x | 1.00x | 0.00 | 72.0 |
| TreeTable | 8.00 | 1.82 | 1.80 | 2.66 | 1.46x | 1.47x | 0.99x | 292 | 291 | 188 | 0.64x | 0.65x | 1.00x | 0.00 | 72.0 |

Build, ready, retained (compiled and live), warm:

| component | build ms compiled | live | ready ms compiled | live | build KB compiled | live | ready KB compiled | live | retained B/instance compiled | live | live/compiled |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Label | 0.76 | 1.01 | 2.28 | 2.46 | 548 | 1257 | 1783 | 1783 | 432 | 555 | 1.28x |
| TextField | 0.31 | 0.56 | 1.56 | 1.35 | 192 | 405 | 634 | 634 | 455 | 572 | 1.26x |
| Slider | 1.18 | 1.24 | 2.20 | 2.23 | 627 | 1336 | 1784 | 1784 | 446 | 571 | 1.28x |
| ScrollBar | 0.49 | 0.54 | 1.34 | 1.62 | 215 | 428 | 539 | 539 | 474 | 588 | 1.24x |
| ProgressBar | 0.88 | 1.18 | 2.45 | 2.25 | 588 | 1297 | 2097 | 2096 | 505 | 621 | 1.23x |
| Separator | 0.86 | 1.28 | 2.67 | 2.52 | 587 | 1297 | 2096 | 2098 | 504 | 629 | 1.25x |
| Image | 1.41 | 1.82 | 2.90 | 2.81 | 743 | 1720 | 1940 | 1940 | 472 | 632 | 1.34x |
| Button | 0.78 | 1.03 | 2.71 | 2.53 | 548 | 1258 | 1940 | 1941 | 431 | 549 | 1.27x |
| Grid | 0.36 | 0.58 | 5.99 | 7.00 | 496 | 790 | 5602 | 5602 | 1712 | 1875 | 1.09x |
| ListView | 0.21 | 0.22 | 0.46 | 0.38 | 84.6 | 87.8 | 182 | 182 | 12730 | 16079 | 1.26x |
| TableView | 0.35 | 0.37 | 1.10 | 1.50 | 343 | 347 | 829 | 829 | 56392 | 72570 | 1.29x |
| Tree | 1.14 | 1.22 | 0.24 | 0.24 | 112 | 114 | 76.5 | 76.5 | 15565 | 20325 | 1.31x |
| TreeTable | 1.32 | 1.33 | 0.58 | 0.57 | 215 | 217 | 281 | 281 | 49442 | 65898 | 1.33x |

#### Biggest wins and losses of compiled JXML against JavaFX, java17-x64 (build + ready warm)

| | component | compiled ms | JavaFX ms | ratio |
|---|---|---:|---:|---:|
| win | TextField | 1.87 | 311 | <0.01x |
| win | ScrollBar | 1.83 | 146 | 0.01x |
| win | TreeTable | 1.90 | 138 | 0.01x |
| win | Slider | 3.38 | 219 | 0.02x |
| win | Tree | 1.38 | 83.0 | 0.02x |
| win | Label | 3.03 | 181 | 0.02x |
| win | TableView | 1.45 | 79.9 | 0.02x |
| win | ProgressBar | 3.33 | 181 | 0.02x |
| win | Button | 3.48 | 186 | 0.02x |
| win | ListView | 0.67 | 26.9 | 0.02x |
| loss | Image | 4.32 | 14.5 | 0.30x |
| loss | Grid | 6.35 | 201 | 0.03x |
| loss | Separator | 3.54 | 129 | 0.03x |
| loss | ListView | 0.67 | 26.9 | 0.02x |
| loss | Button | 3.48 | 186 | 0.02x |
| loss | ProgressBar | 3.33 | 181 | 0.02x |
| loss | TableView | 1.45 | 79.9 | 0.02x |
| loss | Label | 3.03 | 181 | 0.02x |
| loss | Tree | 1.38 | 83.0 | 0.02x |
| loss | Slider | 3.38 | 219 | 0.02x |

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
