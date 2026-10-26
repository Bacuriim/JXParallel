## Per-component results (live-2026-10-26)

Each cell is the median of the runs of that mode: the value, then in parentheses its ratio against the reference row of the component (JavaFX when the component has a JavaFX counterpart, else the interpreted mode; the reference row itself has no ratio). A ratio above 1 is more cost than the reference; **bold** marks the JXML modes that cost more than JavaFX. `n/a` is a value that was not measured or a ratio with a zero or missing side. Paint compares the software painter of JXML with the Prism pipeline of JavaFX: indicative, not equivalent.

### java17-x64

#### Inputs and display, java17-x64

Time (cold is pass 1, warm the last pass):

| component | mode | n | build cold ms | build warm ms | ready cold ms | ready warm ms | update warm ms | paint warm ms (indicative) |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| Label | javafx (ref) | 5000 | 136 | 20.7 | 790 | 165 | 33.5 | 22.0 |
| Label | compiled | 5000 | 5.62 (0.04x) | 0.74 (0.04x) | 188 (0.24x) | 2.24 (0.01x) | 2.15 (0.06x) | 0.58 (0.03x) |
| Label | live | 5000 | 15.2 (0.11x) | 1.09 (0.05x) | 187 (0.24x) | 2.28 (0.01x) | 2.24 (0.07x) | 0.35 (0.02x) |
| TextField | javafx (ref) | 1500 | 131 | 10.8 | 1179 | 314 | 15.5 | 8.01 |
| TextField | compiled | 1500 | 4.83 (0.04x) | 0.35 (0.03x) | 164 (0.14x) | 1.45 (<0.01x) | 0.83 (0.05x) | 0.97 (0.12x) |
| TextField | live | 1500 | 14.5 (0.11x) | 0.47 (0.04x) | 162 (0.14x) | 1.60 (<0.01x) | 0.45 (0.03x) | 1.16 (0.15x) |
| Slider | javafx (ref) | 5000 | 137 | 19.6 | 783 | 201 | 3.00 | 43.4 |
| Slider | compiled | 5000 | 5.45 (0.04x) | 0.91 (0.05x) | 160 (0.20x) | 2.42 (0.01x) | 1.85 (0.62x) | 0.77 (0.02x) |
| Slider | live | 5000 | 14.8 (0.11x) | 1.24 (0.06x) | 167 (0.21x) | 2.33 (0.01x) | 1.37 (0.46x) | 0.84 (0.02x) |
| ScrollBar | javafx (ref) | 1500 | 109 | 4.78 | 516 | 144 | 0.70 | 29.6 |
| ScrollBar | compiled | 1500 | 4.41 (0.04x) | 0.39 (0.08x) | 156 (0.30x) | 1.63 (0.01x) | **1.28 (1.83x)** | 0.84 (0.03x) |
| ScrollBar | live | 1500 | 14.2 (0.13x) | 0.63 (0.13x) | 153 (0.30x) | 1.34 (<0.01x) | 0.44 (0.62x) | 0.65 (0.02x) |
| ProgressBar | javafx (ref) | 5000 | 139 | 19.0 | 703 | 171 | 6.09 | 5.70 |
| ProgressBar | compiled | 5000 | 5.11 (0.04x) | 1.24 (0.07x) | 164 (0.23x) | 2.47 (0.01x) | 2.13 (0.35x) | 2.07 (0.36x) |
| ProgressBar | live | 5000 | 14.6 (0.11x) | 1.14 (0.06x) | 163 (0.23x) | 2.49 (0.01x) | 2.67 (0.44x) | 0.45 (0.08x) |
| Separator | javafx (ref) | 5000 | 133 | 22.0 | 364 | 105 | 2.60 | 5.15 |
| Separator | compiled | 5000 | 4.89 (0.04x) | 0.81 (0.04x) | 162 (0.44x) | 2.70 (0.03x) | **3.18 (1.22x)** | 2.14 (0.42x) |
| Separator | live | 5000 | 14.8 (0.11x) | 1.21 (0.05x) | 165 (0.45x) | 2.50 (0.02x) | **2.70 (1.04x)** | 0.42 (0.08x) |
| Image | javafx (ref) | 5000 | 69.9 | 8.65 | 103 | 7.09 | 3.92 | 5.93 |
| Image | compiled | 5000 | 7.34 (0.11x) | 1.41 (0.16x) | **168 (1.63x)** | 2.72 (0.38x) | 2.98 (0.76x) | 5.15 (0.87x) |
| Image | live | 5000 | 18.8 (0.27x) | 1.83 (0.21x) | **168 (1.63x)** | 2.83 (0.40x) | 3.08 (0.78x) | 4.74 (0.80x) |

Memory, CPU and classes:

| component | mode | n | alloc build warm KB | alloc ready warm KB | alloc update warm KB | retained B/instance | thread CPU ms (all stages) | classes loaded | peak working set MB |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Label | javafx (ref) | 5000 | 17044 | 162475 | 18676 | 12159 | 3031 | 2808 | 716 |
| Label | compiled | 5000 | 548 (0.03x) | 1784 (0.01x) | 665 (0.04x) | 432 (0.04x) | 344 (0.11x) | 1446 (0.51x) | 233 (0.33x) |
| Label | live | 5000 | 1258 (0.07x) | 1786 (0.01x) | 123 (<0.01x) | 550 (0.05x) | 312 (0.10x) | 1466 (0.52x) | 243 (0.34x) |
| TextField | javafx (ref) | 1500 | 7096 | 216178 | 7460 | 76632 | 3859 | 3316 | 702 |
| TextField | compiled | 1500 | 192 (0.03x) | 635 (<0.01x) | 192 (0.03x) | 455 (<0.01x) | 297 (0.08x) | 1445 (0.44x) | 198 (0.28x) |
| TextField | live | 1500 | 405 (0.06x) | 635 (<0.01x) | 5.48 (<0.01x) | 571 (<0.01x) | 281 (0.07x) | 1465 (0.44x) | 204 (0.29x) |
| Slider | javafx (ref) | 5000 | 16934 | 205970 | 240 | 22929 | 3156 | 2918 | 725 |
| Slider | compiled | 5000 | 626 (0.04x) | 1783 (<0.01x) | **626 (2.61x)** | 456 (0.02x) | 234 (0.07x) | 1415 (0.48x) | 238 (0.33x) |
| Slider | live | 5000 | 1336 (0.08x) | 1783 (<0.01x) | 5.48 (0.02x) | 566 (0.02x) | 250 (0.08x) | 1435 (0.49x) | 233 (0.32x) |
| ScrollBar | javafx (ref) | 1500 | 5211 | 135484 | 76.0 | 40779 | 2219 | 2902 | 650 |
| ScrollBar | compiled | 1500 | 215 (0.04x) | 540 (<0.01x) | **215 (2.83x)** | 473 (0.01x) | 219 (0.10x) | 1415 (0.49x) | 183 (0.28x) |
| ScrollBar | live | 1500 | 428 (0.08x) | 542 (<0.01x) | 5.48 (0.07x) | 590 (0.01x) | 203 (0.09x) | 1435 (0.49x) | 217 (0.33x) |
| ProgressBar | javafx (ref) | 5000 | 17835 | 180894 | 670 | 16562 | 2578 | 2666 | 691 |
| ProgressBar | compiled | 5000 | 587 (0.03x) | 2096 (0.01x) | 587 (0.88x) | 505 (0.03x) | 250 (0.10x) | 1415 (0.53x) | 245 (0.35x) |
| ProgressBar | live | 5000 | 1297 (0.07x) | 2097 (0.01x) | 5.51 (<0.01x) | 629 (0.04x) | 266 (0.10x) | 1435 (0.54x) | 244 (0.35x) |
| Separator | javafx (ref) | 5000 | 16349 | 73181 | 123 | 8864 | 1484 | 2623 | 618 |
| Separator | compiled | 5000 | 587 (0.04x) | 2096 (0.03x) | **704 (5.73x)** | 504 (0.06x) | 266 (0.18x) | 1415 (0.54x) | 238 (0.38x) |
| Separator | live | 5000 | 1297 (0.08x) | 2097 (0.03x) | 123 (1.00x) | 621 (0.07x) | 250 (0.17x) | 1435 (0.55x) | 257 (0.42x) |
| Image | javafx (ref) | 5000 | 9004 | 1357 | 553 | 1960 | 406 | 2458 | 409 |
| Image | compiled | 5000 | 743 (0.08x) | **1940 (1.43x)** | **1017 (1.84x)** | 471 (0.24x) | 297 (0.73x) | 1417 (0.58x) | 232 (0.57x) |
| Image | live | 5000 | 1720 (0.19x) | **1941 (1.43x)** | 279 (0.50x) | 632 (0.32x) | 312 (0.77x) | 1438 (0.59x) | 233 (0.57x) |

#### Buttons and toggles, java17-x64

Time (cold is pass 1, warm the last pass):

| component | mode | n | build cold ms | build warm ms | ready cold ms | ready warm ms | update warm ms | paint warm ms (indicative) |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| Button | javafx (ref) | 5000 | 138 | 13.4 | 821 | 174 | 27.1 | 25.6 |
| Button | compiled | 5000 | 5.18 (0.04x) | 0.79 (0.06x) | 190 (0.23x) | 3.00 (0.02x) | 2.69 (0.10x) | 0.61 (0.02x) |
| Button | live | 5000 | 14.8 (0.11x) | 1.16 (0.09x) | 186 (0.23x) | 2.44 (0.01x) | 2.60 (0.10x) | 0.96 (0.04x) |

Memory, CPU and classes:

| component | mode | n | alloc build warm KB | alloc ready warm KB | alloc update warm KB | retained B/instance | thread CPU ms (all stages) | classes loaded | peak working set MB |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Button | javafx (ref) | 5000 | 17630 | 167241 | 19205 | 17876 | 3172 | 2959 | 714 |
| Button | compiled | 5000 | 549 (0.03x) | 1940 (0.01x) | 822 (0.04x) | 430 (0.02x) | 344 (0.11x) | 1446 (0.49x) | 246 (0.34x) |
| Button | live | 5000 | 1258 (0.07x) | 1940 (0.01x) | 279 (0.01x) | 549 (0.03x) | 328 (0.10x) | 1466 (0.50x) | 242 (0.34x) |

#### Containers and layout, java17-x64

Time (cold is pass 1, warm the last pass):

| component | mode | n | build cold ms | build warm ms | ready cold ms | ready warm ms | update warm ms | paint warm ms (indicative) |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| Grid | javafx (ref) | 1500 | 138 | 9.47 | 815 | 185 | 14.0 | 17.9 |
| Grid | compiled | 1500 | 6.34 (0.05x) | 0.37 (0.04x) | 200 (0.25x) | 6.75 (0.04x) | 4.40 (0.31x) | 0.61 (0.03x) |
| Grid | live | 1500 | 16.0 (0.12x) | 0.52 (0.06x) | 201 (0.25x) | 6.66 (0.04x) | 3.54 (0.25x) | 2.89 (0.16x) |

Memory, CPU and classes:

| component | mode | n | alloc build warm KB | alloc ready warm KB | alloc update warm KB | retained B/instance | thread CPU ms (all stages) | classes loaded | peak working set MB |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Grid | javafx (ref) | 1500 | 20660 | 147538 | 3546 | 41002 | 3047 | 2829 | 698 |
| Grid | compiled | 1500 | 496 (0.02x) | 5604 (0.04x) | **3590 (1.01x)** | 1713 (0.04x) | 391 (0.13x) | 1449 (0.51x) | 264 (0.38x) |
| Grid | live | 1500 | 789 (0.04x) | 5604 (0.04x) | 3240 (0.91x) | 1877 (0.05x) | 391 (0.13x) | 1469 (0.52x) | 253 (0.36x) |

#### Lists, tables and trees, java17-x64

Time (cold is pass 1, warm the last pass):

| component | mode | n | build cold ms | build warm ms | ready cold ms | ready warm ms | update warm ms | paint warm ms (indicative) |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| ListView | javafx (ref) | 20.0 | 87.9 | 1.17 | 217 | 27.0 | 2.20 | 8.42 |
| ListView | compiled | 20.0 | 4.75 (0.05x) | 0.24 (0.21x) | 117 (0.54x) | 0.45 (0.02x) | 0.89 (0.41x) | 0.82 (0.10x) |
| ListView | live | 20.0 | 11.4 (0.13x) | 0.24 (0.21x) | 115 (0.53x) | 0.45 (0.02x) | 0.72 (0.33x) | 0.72 (0.09x) |
| TableView | javafx (ref) | 20.0 | 100 | 2.61 | 399 | 76.9 | 1.72 | 7.31 |
| TableView | compiled | 20.0 | 5.91 (0.06x) | 0.35 (0.13x) | 122 (0.30x) | 1.03 (0.01x) | **1.78 (1.04x)** | 1.48 (0.20x) |
| TableView | live | 20.0 | 12.4 (0.12x) | 0.33 (0.12x) | 121 (0.30x) | 0.97 (0.01x) | **1.91 (1.11x)** | 1.60 (0.22x) |
| Tree | javafx (ref) | 8.00 | 246 | 66.2 | 182 | 18.2 | 3.31 | 7.04 |
| Tree | compiled | 8.00 | 13.9 (0.06x) | 1.24 (0.02x) | 117 (0.64x) | 0.29 (0.02x) | 2.31 (0.70x) | 1.07 (0.15x) |
| Tree | live | 8.00 | 20.2 (0.08x) | 1.18 (0.02x) | 117 (0.64x) | 0.19 (0.01x) | 1.98 (0.60x) | 0.89 (0.13x) |
| TreeTable | javafx (ref) | 8.00 | 259 | 51.7 | 378 | 57.8 | 3.25 | 7.86 |
| TreeTable | compiled | 8.00 | 15.9 (0.06x) | 1.26 (0.02x) | 120 (0.32x) | 0.73 (0.01x) | 2.47 (0.76x) | 1.70 (0.22x) |
| TreeTable | live | 8.00 | 22.5 (0.09x) | 1.35 (0.03x) | 117 (0.31x) | 0.79 (0.01x) | 1.89 (0.58x) | 1.87 (0.24x) |

Memory, CPU and classes:

| component | mode | n | alloc build warm KB | alloc ready warm KB | alloc update warm KB | retained B/instance | thread CPU ms (all stages) | classes loaded | peak working set MB |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| ListView | javafx (ref) | 20.0 | 118 | 7856 | 112 | 162854 | 719 | 3303 | 298 |
| ListView | compiled | 20.0 | 84.6 (0.72x) | 182 (0.02x) | **133 (1.19x)** | 12626 (0.08x) | 219 (0.30x) | 1451 (0.44x) | 102 (0.34x) |
| ListView | live | 20.0 | 87.8 (0.74x) | 182 (0.02x) | **133 (1.18x)** | 12845 (0.08x) | 203 (0.28x) | 1471 (0.45x) | 102 (0.34x) |
| TableView | javafx (ref) | 20.0 | 346 | 32832 | 209 | 334144 | 1484 | 3551 | 513 |
| TableView | compiled | 20.0 | 343 (0.99x) | 829 (0.03x) | **601 (2.87x)** | 55634 (0.17x) | 250 (0.17x) | 1451 (0.41x) | 129 (0.25x) |
| TableView | live | 20.0 | **347 (1.00x)** | 829 (0.03x) | **600 (2.87x)** | 56908 (0.17x) | 234 (0.16x) | 1472 (0.41x) | 130 (0.25x) |
| Tree | javafx (ref) | 8.00 | 89319 | 4090 | 225 | 5572192 | 984 | 3299 | 662 |
| Tree | compiled | 8.00 | 112 (<0.01x) | 76.5 (0.02x) | 131 (0.58x) | 15438 (<0.01x) | 219 (0.22x) | 1456 (0.44x) | 105 (0.16x) |
| Tree | live | 8.00 | 114 (<0.01x) | 76.5 (0.02x) | 131 (0.58x) | 15357 (<0.01x) | 234 (0.24x) | 1476 (0.45x) | 106 (0.16x) |
| TreeTable | javafx (ref) | 8.00 | 87740 | 21555 | 190 | 6032605 | 1688 | 3591 | 687 |
| TreeTable | compiled | 8.00 | 215 (<0.01x) | 281 (0.01x) | **292 (1.53x)** | 48878 (<0.01x) | 234 (0.14x) | 1457 (0.41x) | 117 (0.17x) |
| TreeTable | live | 8.00 | 217 (<0.01x) | 281 (0.01x) | **291 (1.53x)** | 49343 (<0.01x) | 250 (0.15x) | 1478 (0.41x) | 117 (0.17x) |

#### Live bindings against compiled JXML and JavaFX, java17-x64

Update of one prop of every instance, warm pass. Ratios are JavaFX / mode (above 1x the mode is better than JavaFX) and live / compiled (below 1x live is better than compiled). Build and ready include the recording and its pairing with the mounted nodes.

| component | n | update ms compiled | live | JavaFX | JavaFX/compiled | JavaFX/live | live/compiled | alloc KB compiled | live | JavaFX | JavaFX/compiled | JavaFX/live | live/compiled | fallbacks | opaque builds |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Label | 5000 | 2.15 | 2.24 | 33.5 | 15.58x | 14.95x | 1.04x | 665 | 123 | 18676 | 28.07x | 152.20x | 0.18x | 0.00 | 0.00 |
| TextField | 1500 | 0.83 | 0.45 | 15.5 | 18.60x | 34.78x | 0.53x | 192 | 5.48 | 7460 | 38.87x | 1362.12x | 0.03x | 0.00 | 0.00 |
| Slider | 5000 | 1.85 | 1.37 | 3.00 | 1.62x | 2.19x | 0.74x | 626 | 5.48 | 240 | 0.38x | 43.84x | <0.01x | 0.00 | 0.00 |
| ScrollBar | 1500 | 1.28 | 0.44 | 0.70 | 0.55x | 1.60x | 0.34x | 215 | 5.48 | 76.0 | 0.35x | 13.89x | 0.03x | 0.00 | 0.00 |
| ProgressBar | 5000 | 2.13 | 2.67 | 6.09 | 2.86x | 2.29x | 1.25x | 587 | 5.51 | 670 | 1.14x | 121.61x | <0.01x | 0.00 | 0.00 |
| Separator | 5000 | 3.18 | 2.70 | 2.60 | 0.82x | 0.96x | 0.85x | 704 | 123 | 123 | 0.17x | 1.00x | 0.17x | 0.00 | 0.00 |
| Image | 5000 | 2.98 | 3.08 | 3.92 | 1.32x | 1.27x | 1.03x | 1017 | 279 | 553 | 0.54x | 1.98x | 0.27x | 0.00 | 0.00 |
| Button | 5000 | 2.69 | 2.60 | 27.1 | 10.09x | 10.44x | 0.97x | 822 | 279 | 19205 | 23.37x | 68.84x | 0.34x | 0.00 | 0.00 |
| Grid | 1500 | 4.40 | 3.54 | 14.0 | 3.18x | 3.95x | 0.80x | 3590 | 3240 | 3546 | 0.99x | 1.09x | 0.90x | 0.00 | 0.00 |
| ListView | 20.0 | 0.89 | 0.72 | 2.20 | 2.46x | 3.06x | 0.81x | 133 | 133 | 112 | 0.84x | 0.84x | 1.00x | 0.00 | 180 |
| TableView | 20.0 | 1.78 | 1.91 | 1.72 | 0.97x | 0.90x | 1.07x | 601 | 600 | 209 | 0.35x | 0.35x | 1.00x | 0.00 | 180 |
| Tree | 8.00 | 2.31 | 1.98 | 3.31 | 1.44x | 1.67x | 0.86x | 131 | 131 | 225 | 1.72x | 1.72x | 1.00x | 0.00 | 72.0 |
| TreeTable | 8.00 | 2.47 | 1.89 | 3.25 | 1.32x | 1.72x | 0.77x | 292 | 291 | 190 | 0.65x | 0.65x | 1.00x | 0.00 | 72.0 |

Build, ready, retained (compiled and live), warm:

| component | build ms compiled | live | ready ms compiled | live | build KB compiled | live | ready KB compiled | live | retained B/instance compiled | live | live/compiled |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Label | 0.74 | 1.09 | 2.24 | 2.28 | 548 | 1258 | 1784 | 1786 | 432 | 550 | 1.27x |
| TextField | 0.35 | 0.47 | 1.45 | 1.60 | 192 | 405 | 635 | 635 | 455 | 571 | 1.26x |
| Slider | 0.91 | 1.24 | 2.42 | 2.33 | 626 | 1336 | 1783 | 1783 | 456 | 566 | 1.24x |
| ScrollBar | 0.39 | 0.63 | 1.63 | 1.34 | 215 | 428 | 540 | 542 | 473 | 590 | 1.25x |
| ProgressBar | 1.24 | 1.14 | 2.47 | 2.49 | 587 | 1297 | 2096 | 2097 | 505 | 629 | 1.24x |
| Separator | 0.81 | 1.21 | 2.70 | 2.50 | 587 | 1297 | 2096 | 2097 | 504 | 621 | 1.23x |
| Image | 1.41 | 1.83 | 2.72 | 2.83 | 743 | 1720 | 1940 | 1941 | 471 | 632 | 1.34x |
| Button | 0.79 | 1.16 | 3.00 | 2.44 | 549 | 1258 | 1940 | 1940 | 430 | 549 | 1.28x |
| Grid | 0.37 | 0.52 | 6.75 | 6.66 | 496 | 789 | 5604 | 5604 | 1713 | 1877 | 1.10x |
| ListView | 0.24 | 0.24 | 0.45 | 0.45 | 84.6 | 87.8 | 182 | 182 | 12626 | 12845 | 1.02x |
| TableView | 0.35 | 0.33 | 1.03 | 0.97 | 343 | 347 | 829 | 829 | 55634 | 56908 | 1.02x |
| Tree | 1.24 | 1.18 | 0.29 | 0.19 | 112 | 114 | 76.5 | 76.5 | 15438 | 15357 | 0.99x |
| TreeTable | 1.26 | 1.35 | 0.73 | 0.79 | 215 | 217 | 281 | 281 | 48878 | 49343 | 1.01x |

#### Biggest wins and losses of compiled JXML against JavaFX, java17-x64 (build + ready warm)

| | component | compiled ms | JavaFX ms | ratio |
|---|---|---:|---:|---:|
| win | TextField | 1.80 | 325 | <0.01x |
| win | ScrollBar | 2.02 | 148 | 0.01x |
| win | Slider | 3.33 | 220 | 0.02x |
| win | Label | 2.98 | 185 | 0.02x |
| win | TableView | 1.38 | 79.5 | 0.02x |
| win | Tree | 1.53 | 84.4 | 0.02x |
| win | TreeTable | 1.99 | 110 | 0.02x |
| win | ProgressBar | 3.71 | 190 | 0.02x |
| win | Button | 3.79 | 187 | 0.02x |
| win | ListView | 0.69 | 28.2 | 0.02x |
| loss | Image | 4.12 | 15.7 | 0.26x |
| loss | Grid | 7.11 | 194 | 0.04x |
| loss | Separator | 3.51 | 127 | 0.03x |
| loss | ListView | 0.69 | 28.2 | 0.02x |
| loss | Button | 3.79 | 187 | 0.02x |
| loss | ProgressBar | 3.71 | 190 | 0.02x |
| loss | TreeTable | 1.99 | 110 | 0.02x |
| loss | Tree | 1.53 | 84.4 | 0.02x |
| loss | TableView | 1.38 | 79.5 | 0.02x |
| loss | Label | 2.98 | 185 | 0.02x |

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
