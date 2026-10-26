## Per-component results (live-2026-10-26)

Each cell is the median of the runs of that mode: the value, then in parentheses its ratio against the reference row of the component (JavaFX when the component has a JavaFX counterpart, else the interpreted mode; the reference row itself has no ratio). A ratio above 1 is more cost than the reference; **bold** marks the JXML modes that cost more than JavaFX. `n/a` is a value that was not measured or a ratio with a zero or missing side. Paint compares the software painter of JXML with the Prism pipeline of JavaFX: indicative, not equivalent.

### java17-x64

#### Inputs and display, java17-x64

Time (cold is pass 1, warm the last pass):

| component | mode | n | build cold ms | build warm ms | ready cold ms | ready warm ms | update warm ms | paint warm ms (indicative) |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| Label | javafx (ref) | 5000 | 138 | 19.4 | 770 | 160 | 36.3 | 20.1 |
| Label | compiled | 5000 | 5.49 (0.04x) | 0.76 (0.04x) | 182 (0.24x) | 2.22 (0.01x) | 2.26 (0.06x) | 0.75 (0.04x) |
| Label | live | 5000 | 15.6 (0.11x) | 1.09 (0.06x) | 185 (0.24x) | 2.29 (0.01x) | 2.21 (0.06x) | 0.39 (0.02x) |
| TextField | javafx (ref) | 1500 | 130 | 11.4 | 1155 | 317 | 12.9 | 7.18 |
| TextField | compiled | 1500 | 5.34 (0.04x) | 0.34 (0.03x) | 159 (0.14x) | 1.42 (<0.01x) | 1.03 (0.08x) | 0.88 (0.12x) |
| TextField | live | 1500 | 14.5 (0.11x) | 0.67 (0.06x) | 161 (0.14x) | 2.16 (<0.01x) | 0.41 (0.03x) | 0.85 (0.12x) |
| Slider | javafx (ref) | 5000 | 143 | 20.4 | 774 | 197 | 2.72 | 39.9 |
| Slider | compiled | 5000 | 5.35 (0.04x) | 0.95 (0.05x) | 168 (0.22x) | 2.20 (0.01x) | **3.05 (1.12x)** | 2.40 (0.06x) |
| Slider | live | 5000 | 14.7 (0.10x) | 1.34 (0.07x) | 164 (0.21x) | 2.71 (0.01x) | 1.41 (0.52x) | 0.71 (0.02x) |
| ScrollBar | javafx (ref) | 1500 | 110 | 4.97 | 514 | 140 | 0.72 | 31.1 |
| ScrollBar | compiled | 1500 | 4.74 (0.04x) | 0.49 (0.10x) | 161 (0.31x) | 1.88 (0.01x) | **0.97 (1.35x)** | 0.69 (0.02x) |
| ScrollBar | live | 1500 | 13.9 (0.13x) | 0.51 (0.10x) | 157 (0.31x) | 1.78 (0.01x) | 0.43 (0.61x) | 0.66 (0.02x) |
| ProgressBar | javafx (ref) | 5000 | 140 | 18.8 | 711 | 174 | 6.74 | 6.39 |
| ProgressBar | compiled | 5000 | 5.30 (0.04x) | 0.97 (0.05x) | 162 (0.23x) | 2.44 (0.01x) | 2.66 (0.39x) | 0.50 (0.08x) |
| ProgressBar | live | 5000 | 15.1 (0.11x) | 1.20 (0.06x) | 163 (0.23x) | 2.61 (0.01x) | 2.30 (0.34x) | 0.52 (0.08x) |
| Separator | javafx (ref) | 5000 | 132 | 22.6 | 365 | 84.5 | 3.00 | 4.29 |
| Separator | compiled | 5000 | 5.45 (0.04x) | 0.89 (0.04x) | 171 (0.47x) | 2.98 (0.04x) | 2.85 (0.95x) | 2.24 (0.52x) |
| Separator | live | 5000 | 15.5 (0.12x) | 1.23 (0.05x) | 165 (0.45x) | 2.35 (0.03x) | 2.70 (0.90x) | 0.35 (0.08x) |
| Image | javafx (ref) | 5000 | 72.6 | 8.51 | 105 | 6.79 | 4.01 | 6.42 |
| Image | compiled | 5000 | 7.16 (0.10x) | 1.35 (0.16x) | **167 (1.59x)** | 2.84 (0.42x) | 3.23 (0.81x) | 5.23 (0.81x) |
| Image | live | 5000 | 17.0 (0.23x) | 1.91 (0.22x) | **165 (1.57x)** | 2.94 (0.43x) | 3.63 (0.91x) | 4.73 (0.74x) |

Memory, CPU and classes:

| component | mode | n | alloc build warm KB | alloc ready warm KB | alloc update warm KB | retained B/instance | thread CPU ms (all stages) | classes loaded | peak working set MB |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Label | javafx (ref) | 5000 | 17044 | 162473 | 18676 | 12153 | 2984 | 2808 | 715 |
| Label | compiled | 5000 | 548 (0.03x) | 1783 (0.01x) | 665 (0.04x) | 432 (0.04x) | 312 (0.10x) | 1446 (0.51x) | 244 (0.34x) |
| Label | live | 5000 | 1258 (0.07x) | 1785 (0.01x) | 123 (<0.01x) | 553 (0.05x) | 328 (0.11x) | 1466 (0.52x) | 244 (0.34x) |
| TextField | javafx (ref) | 1500 | 7096 | 216461 | 7668 | 76620 | 3891 | 3316 | 694 |
| TextField | compiled | 1500 | 192 (0.03x) | 635 (<0.01x) | 192 (0.03x) | 457 (<0.01x) | 297 (0.08x) | 1445 (0.44x) | 198 (0.29x) |
| TextField | live | 1500 | 405 (0.06x) | 636 (<0.01x) | 5.48 (<0.01x) | 572 (<0.01x) | 266 (0.07x) | 1465 (0.44x) | 211 (0.30x) |
| Slider | javafx (ref) | 5000 | 16934 | 205970 | 242 | 22947 | 3125 | 2918 | 723 |
| Slider | compiled | 5000 | 626 (0.04x) | 1785 (<0.01x) | **626 (2.58x)** | 448 (0.02x) | 250 (0.08x) | 1415 (0.48x) | 238 (0.33x) |
| Slider | live | 5000 | 1336 (0.08x) | 1785 (<0.01x) | 5.48 (0.02x) | 568 (0.02x) | 266 (0.09x) | 1435 (0.49x) | 235 (0.33x) |
| ScrollBar | javafx (ref) | 1500 | 5213 | 135468 | 76.0 | 40719 | 2328 | 2902 | 652 |
| ScrollBar | compiled | 1500 | 215 (0.04x) | 542 (<0.01x) | **215 (2.83x)** | 474 (0.01x) | 219 (0.09x) | 1415 (0.49x) | 196 (0.30x) |
| ScrollBar | live | 1500 | 428 (0.08x) | 539 (<0.01x) | 5.48 (0.07x) | 590 (0.01x) | 188 (0.08x) | 1435 (0.49x) | 198 (0.30x) |
| ProgressBar | javafx (ref) | 5000 | 17832 | 180894 | 672 | 16561 | 2562 | 2666 | 691 |
| ProgressBar | compiled | 5000 | 587 (0.03x) | 2096 (0.01x) | 587 (0.87x) | 507 (0.03x) | 234 (0.09x) | 1415 (0.53x) | 249 (0.36x) |
| ProgressBar | live | 5000 | 1297 (0.07x) | 2096 (0.01x) | 5.62 (<0.01x) | 625 (0.04x) | 266 (0.10x) | 1435 (0.54x) | 239 (0.35x) |
| Separator | javafx (ref) | 5000 | 16348 | 73179 | 123 | 8864 | 1500 | 2623 | 618 |
| Separator | compiled | 5000 | 587 (0.04x) | 2096 (0.03x) | **704 (5.73x)** | 509 (0.06x) | 266 (0.18x) | 1415 (0.54x) | 238 (0.39x) |
| Separator | live | 5000 | 1297 (0.08x) | 2097 (0.03x) | 123 (1.00x) | 629 (0.07x) | 266 (0.18x) | 1435 (0.55x) | 251 (0.41x) |
| Image | javafx (ref) | 5000 | 9004 | 1357 | 553 | 1961 | 375 | 2458 | 389 |
| Image | compiled | 5000 | 743 (0.08x) | **1940 (1.43x)** | **1017 (1.84x)** | 472 (0.24x) | 312 (0.83x) | 1417 (0.58x) | 236 (0.61x) |
| Image | live | 5000 | 1720 (0.19x) | **1941 (1.43x)** | 279 (0.50x) | 632 (0.32x) | 344 (0.92x) | 1438 (0.59x) | 244 (0.63x) |

#### Buttons and toggles, java17-x64

Time (cold is pass 1, warm the last pass):

| component | mode | n | build cold ms | build warm ms | ready cold ms | ready warm ms | update warm ms | paint warm ms (indicative) |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| Button | javafx (ref) | 5000 | 136 | 12.4 | 809 | 175 | 27.7 | 22.5 |
| Button | compiled | 5000 | 5.13 (0.04x) | 0.79 (0.06x) | 195 (0.24x) | 2.62 (0.02x) | 2.46 (0.09x) | 0.94 (0.04x) |
| Button | live | 5000 | 14.9 (0.11x) | 1.04 (0.08x) | 197 (0.24x) | 2.38 (0.01x) | 2.46 (0.09x) | 0.73 (0.03x) |

Memory, CPU and classes:

| component | mode | n | alloc build warm KB | alloc ready warm KB | alloc update warm KB | retained B/instance | thread CPU ms (all stages) | classes loaded | peak working set MB |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Button | javafx (ref) | 5000 | 17629 | 167237 | 19207 | 17874 | 3062 | 2959 | 679 |
| Button | compiled | 5000 | 548 (0.03x) | 1940 (0.01x) | 822 (0.04x) | 432 (0.02x) | 375 (0.12x) | 1446 (0.49x) | 243 (0.36x) |
| Button | live | 5000 | 1258 (0.07x) | 1941 (0.01x) | 279 (0.01x) | 549 (0.03x) | 312 (0.10x) | 1466 (0.50x) | 251 (0.37x) |

#### Containers and layout, java17-x64

Time (cold is pass 1, warm the last pass):

| component | mode | n | build cold ms | build warm ms | ready cold ms | ready warm ms | update warm ms | paint warm ms (indicative) |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| Grid | javafx (ref) | 1500 | 141 | 9.46 | 809 | 183 | 14.2 | 17.7 |
| Grid | compiled | 1500 | 5.47 (0.04x) | 0.37 (0.04x) | 191 (0.24x) | 5.74 (0.03x) | 4.17 (0.29x) | 2.18 (0.12x) |
| Grid | live | 1500 | 15.5 (0.11x) | 0.52 (0.05x) | 205 (0.25x) | 7.30 (0.04x) | 4.09 (0.29x) | 0.91 (0.05x) |

Memory, CPU and classes:

| component | mode | n | alloc build warm KB | alloc ready warm KB | alloc update warm KB | retained B/instance | thread CPU ms (all stages) | classes loaded | peak working set MB |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Grid | javafx (ref) | 1500 | 20662 | 147576 | 3579 | 41076 | 3125 | 2829 | 665 |
| Grid | compiled | 1500 | 496 (0.02x) | 5602 (0.04x) | **3590 (1.00x)** | 1713 (0.04x) | 406 (0.13x) | 1449 (0.51x) | 267 (0.40x) |
| Grid | live | 1500 | 789 (0.04x) | 5602 (0.04x) | 3381 (0.94x) | 1877 (0.05x) | 406 (0.13x) | 1469 (0.52x) | 269 (0.40x) |

#### Lists, tables and trees, java17-x64

Time (cold is pass 1, warm the last pass):

| component | mode | n | build cold ms | build warm ms | ready cold ms | ready warm ms | update warm ms | paint warm ms (indicative) |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| ListView | javafx (ref) | 20.0 | 90.0 | 1.19 | 217 | 23.8 | 2.52 | 7.02 |
| ListView | compiled | 20.0 | 4.89 (0.05x) | 0.21 (0.17x) | 118 (0.54x) | 0.37 (0.02x) | 0.66 (0.26x) | 0.79 (0.11x) |
| ListView | live | 20.0 | 11.9 (0.13x) | 0.22 (0.19x) | 118 (0.54x) | 0.39 (0.02x) | 0.73 (0.29x) | 0.84 (0.12x) |
| TableView | javafx (ref) | 20.0 | 102 | 2.45 | 404 | 79.5 | 1.36 | 7.05 |
| TableView | compiled | 20.0 | 5.83 (0.06x) | 0.32 (0.13x) | 123 (0.30x) | 1.40 (0.02x) | **1.71 (1.25x)** | 1.20 (0.17x) |
| TableView | live | 20.0 | 12.1 (0.12x) | 0.34 (0.14x) | 119 (0.29x) | 1.05 (0.01x) | **1.84 (1.35x)** | 1.59 (0.23x) |
| Tree | javafx (ref) | 8.00 | 245 | 56.8 | 192 | 19.3 | 3.18 | 6.95 |
| Tree | compiled | 8.00 | 13.6 (0.06x) | 1.39 (0.02x) | 115 (0.60x) | 0.30 (0.02x) | 2.05 (0.64x) | 0.95 (0.14x) |
| Tree | live | 8.00 | 20.2 (0.08x) | 1.21 (0.02x) | 115 (0.60x) | 0.27 (0.01x) | 2.07 (0.65x) | 1.16 (0.17x) |
| TreeTable | javafx (ref) | 8.00 | 263 | 48.5 | 381 | 55.0 | 2.60 | 8.05 |
| TreeTable | compiled | 8.00 | 14.9 (0.06x) | 1.25 (0.03x) | 122 (0.32x) | 0.58 (0.01x) | 1.88 (0.72x) | 1.25 (0.16x) |
| TreeTable | live | 8.00 | 22.0 (0.08x) | 1.27 (0.03x) | 120 (0.32x) | 0.82 (0.01x) | 1.89 (0.73x) | 1.88 (0.23x) |

Memory, CPU and classes:

| component | mode | n | alloc build warm KB | alloc ready warm KB | alloc update warm KB | retained B/instance | thread CPU ms (all stages) | classes loaded | peak working set MB |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| ListView | javafx (ref) | 20.0 | 118 | 7856 | 112 | 162968 | 703 | 3303 | 291 |
| ListView | compiled | 20.0 | 84.6 (0.72x) | 182 (0.02x) | **133 (1.19x)** | 12476 (0.08x) | 203 (0.29x) | 1451 (0.44x) | 101 (0.35x) |
| ListView | live | 20.0 | 87.8 (0.74x) | 182 (0.02x) | **131 (1.18x)** | 12746 (0.08x) | 219 (0.31x) | 1471 (0.45x) | 102 (0.35x) |
| TableView | javafx (ref) | 20.0 | 346 | 32835 | 209 | 334005 | 1453 | 3551 | 512 |
| TableView | compiled | 20.0 | 343 (0.99x) | 829 (0.03x) | **601 (2.87x)** | 55822 (0.17x) | 203 (0.14x) | 1452 (0.41x) | 127 (0.25x) |
| TableView | live | 20.0 | **347 (1.00x)** | 829 (0.03x) | **600 (2.87x)** | 56940 (0.17x) | 234 (0.16x) | 1472 (0.41x) | 128 (0.25x) |
| Tree | javafx (ref) | 8.00 | 88867 | 4090 | 225 | 5581759 | 1031 | 3299 | 652 |
| Tree | compiled | 8.00 | 112 (<0.01x) | 76.5 (0.02x) | 131 (0.58x) | 15316 (<0.01x) | 219 (0.21x) | 1456 (0.44x) | 104 (0.16x) |
| Tree | live | 8.00 | 114 (<0.01x) | 76.5 (0.02x) | 131 (0.58x) | 15606 (<0.01x) | 219 (0.21x) | 1476 (0.45x) | 107 (0.16x) |
| TreeTable | javafx (ref) | 8.00 | 90000 | 21786 | 188 | 6039624 | 1672 | 3591 | 680 |
| TreeTable | compiled | 8.00 | 215 (<0.01x) | 281 (0.01x) | **292 (1.55x)** | 49770 (<0.01x) | 234 (0.14x) | 1458 (0.41x) | 114 (0.17x) |
| TreeTable | live | 8.00 | 217 (<0.01x) | 281 (0.01x) | **291 (1.55x)** | 49979 (<0.01x) | 266 (0.16x) | 1478 (0.41x) | 117 (0.17x) |

#### Live bindings against compiled JXML and JavaFX, java17-x64

Update of one prop of every instance, warm pass. Ratios are JavaFX / mode (above 1x the mode is better than JavaFX) and live / compiled (below 1x live is better than compiled). Build and ready include the recording and its pairing with the mounted nodes.

| component | n | update ms compiled | live | JavaFX | JavaFX/compiled | JavaFX/live | live/compiled | alloc KB compiled | live | JavaFX | JavaFX/compiled | JavaFX/live | live/compiled | fallbacks | opaque builds |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Label | 5000 | 2.26 | 2.21 | 36.3 | 16.07x | 16.41x | 0.98x | 665 | 123 | 18676 | 28.07x | 152.20x | 0.18x | 0.00 | 0.00 |
| TextField | 1500 | 1.03 | 0.41 | 12.9 | 12.53x | 31.48x | 0.40x | 192 | 5.48 | 7668 | 39.95x | 1400.21x | 0.03x | 0.00 | 0.00 |
| Slider | 5000 | 3.05 | 1.41 | 2.72 | 0.89x | 1.93x | 0.46x | 626 | 5.48 | 242 | 0.39x | 44.27x | <0.01x | 0.00 | 0.00 |
| ScrollBar | 1500 | 0.97 | 0.43 | 0.72 | 0.74x | 1.65x | 0.45x | 215 | 5.48 | 76.0 | 0.35x | 13.89x | 0.03x | 0.00 | 0.00 |
| ProgressBar | 5000 | 2.66 | 2.30 | 6.74 | 2.53x | 2.94x | 0.86x | 587 | 5.62 | 672 | 1.14x | 119.49x | <0.01x | 0.00 | 0.00 |
| Separator | 5000 | 2.85 | 2.70 | 3.00 | 1.05x | 1.11x | 0.95x | 704 | 123 | 123 | 0.17x | 1.00x | 0.17x | 0.00 | 0.00 |
| Image | 5000 | 3.23 | 3.63 | 4.01 | 1.24x | 1.10x | 1.13x | 1017 | 279 | 553 | 0.54x | 1.98x | 0.27x | 0.00 | 0.00 |
| Button | 5000 | 2.46 | 2.46 | 27.7 | 11.26x | 11.23x | 1.00x | 822 | 279 | 19207 | 23.38x | 68.85x | 0.34x | 0.00 | 0.00 |
| Grid | 1500 | 4.17 | 4.09 | 14.2 | 3.39x | 3.46x | 0.98x | 3590 | 3381 | 3579 | 1.00x | 1.06x | 0.94x | 0.00 | 0.00 |
| ListView | 20.0 | 0.66 | 0.73 | 2.52 | 3.83x | 3.44x | 1.11x | 133 | 131 | 112 | 0.84x | 0.85x | 0.99x | 0.00 | 180 |
| TableView | 20.0 | 1.71 | 1.84 | 1.36 | 0.80x | 0.74x | 1.08x | 601 | 600 | 209 | 0.35x | 0.35x | 1.00x | 0.00 | 180 |
| Tree | 8.00 | 2.05 | 2.07 | 3.18 | 1.55x | 1.53x | 1.01x | 131 | 131 | 225 | 1.72x | 1.72x | 1.00x | 0.00 | 72.0 |
| TreeTable | 8.00 | 1.88 | 1.89 | 2.60 | 1.38x | 1.38x | 1.00x | 292 | 291 | 188 | 0.64x | 0.65x | 1.00x | 0.00 | 72.0 |

Build, ready, retained (compiled and live), warm:

| component | build ms compiled | live | ready ms compiled | live | build KB compiled | live | ready KB compiled | live | retained B/instance compiled | live | live/compiled |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Label | 0.76 | 1.09 | 2.22 | 2.29 | 548 | 1258 | 1783 | 1785 | 432 | 553 | 1.28x |
| TextField | 0.34 | 0.67 | 1.42 | 2.16 | 192 | 405 | 635 | 636 | 457 | 572 | 1.25x |
| Slider | 0.95 | 1.34 | 2.20 | 2.71 | 626 | 1336 | 1785 | 1785 | 448 | 568 | 1.27x |
| ScrollBar | 0.49 | 0.51 | 1.88 | 1.78 | 215 | 428 | 542 | 539 | 474 | 590 | 1.24x |
| ProgressBar | 0.97 | 1.20 | 2.44 | 2.61 | 587 | 1297 | 2096 | 2096 | 507 | 625 | 1.23x |
| Separator | 0.89 | 1.23 | 2.98 | 2.35 | 587 | 1297 | 2096 | 2097 | 509 | 629 | 1.24x |
| Image | 1.35 | 1.91 | 2.84 | 2.94 | 743 | 1720 | 1940 | 1941 | 472 | 632 | 1.34x |
| Button | 0.79 | 1.04 | 2.62 | 2.38 | 548 | 1258 | 1940 | 1941 | 432 | 549 | 1.27x |
| Grid | 0.37 | 0.52 | 5.74 | 7.30 | 496 | 789 | 5602 | 5602 | 1713 | 1877 | 1.10x |
| ListView | 0.21 | 0.22 | 0.37 | 0.39 | 84.6 | 87.8 | 182 | 182 | 12476 | 12746 | 1.02x |
| TableView | 0.32 | 0.34 | 1.40 | 1.05 | 343 | 347 | 829 | 829 | 55822 | 56940 | 1.02x |
| Tree | 1.39 | 1.21 | 0.30 | 0.27 | 112 | 114 | 76.5 | 76.5 | 15316 | 15606 | 1.02x |
| TreeTable | 1.25 | 1.27 | 0.58 | 0.82 | 215 | 217 | 281 | 281 | 49770 | 49979 | 1.00x |

#### Biggest wins and losses of compiled JXML against JavaFX, java17-x64 (build + ready warm)

| | component | compiled ms | JavaFX ms | ratio |
|---|---|---:|---:|---:|
| win | TextField | 1.77 | 328 | <0.01x |
| win | Slider | 3.16 | 217 | 0.01x |
| win | ScrollBar | 2.37 | 145 | 0.02x |
| win | Label | 2.98 | 180 | 0.02x |
| win | ProgressBar | 3.41 | 193 | 0.02x |
| win | TreeTable | 1.83 | 104 | 0.02x |
| win | Button | 3.41 | 187 | 0.02x |
| win | TableView | 1.72 | 81.9 | 0.02x |
| win | Tree | 1.69 | 76.1 | 0.02x |
| win | ListView | 0.58 | 25.0 | 0.02x |
| loss | Image | 4.19 | 15.3 | 0.27x |
| loss | Separator | 3.86 | 107 | 0.04x |
| loss | Grid | 6.11 | 193 | 0.03x |
| loss | ListView | 0.58 | 25.0 | 0.02x |
| loss | Tree | 1.69 | 76.1 | 0.02x |
| loss | TableView | 1.72 | 81.9 | 0.02x |
| loss | Button | 3.41 | 187 | 0.02x |
| loss | TreeTable | 1.83 | 104 | 0.02x |
| loss | ProgressBar | 3.41 | 193 | 0.02x |
| loss | Label | 2.98 | 180 | 0.02x |

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
