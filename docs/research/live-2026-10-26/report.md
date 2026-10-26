## Per-component results (live-2026-10-26)

Each cell is the median of the runs of that mode: the value, then in parentheses its ratio against the reference row of the component (JavaFX when the component has a JavaFX counterpart, else the interpreted mode; the reference row itself has no ratio). A ratio above 1 is more cost than the reference; **bold** marks the JXML modes that cost more than JavaFX. `n/a` is a value that was not measured or a ratio with a zero or missing side. Paint compares the software painter of JXML with the Prism pipeline of JavaFX: indicative, not equivalent.

### java17-x64

#### Inputs and display, java17-x64

Time (cold is pass 1, warm the last pass):

| component | mode | n | build cold ms | build warm ms | ready cold ms | ready warm ms | update warm ms | paint warm ms (indicative) |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| Label | javafx (ref) | 5000 | 132 | 23.5 | 772 | 156 | 34.4 | 19.2 |
| Label | compiled | 5000 | 5.50 (0.04x) | 0.75 (0.03x) | 190 (0.25x) | 2.14 (0.01x) | 2.34 (0.07x) | 0.53 (0.03x) |
| Label | live | 5000 | 14.8 (0.11x) | 1.02 (0.04x) | 186 (0.24x) | 2.17 (0.01x) | 3.41 (0.10x) | 0.47 (0.02x) |
| TextField | javafx (ref) | 1500 | 135 | 12.5 | 1173 | 326 | 12.9 | 7.96 |
| TextField | compiled | 1500 | 4.90 (0.04x) | 0.30 (0.02x) | 160 (0.14x) | 1.18 (<0.01x) | 0.95 (0.07x) | 0.85 (0.11x) |
| TextField | live | 1500 | 14.2 (0.11x) | 0.87 (0.07x) | 159 (0.14x) | 1.38 (<0.01x) | 0.58 (0.05x) | 0.69 (0.09x) |
| Slider | javafx (ref) | 5000 | 139 | 20.8 | 760 | 198 | 2.65 | 39.9 |
| Slider | compiled | 5000 | 5.30 (0.04x) | 1.02 (0.05x) | 171 (0.23x) | 2.39 (0.01x) | 1.71 (0.65x) | 1.10 (0.03x) |
| Slider | live | 5000 | 15.9 (0.11x) | 1.19 (0.06x) | 164 (0.22x) | 2.77 (0.01x) | 2.32 (0.88x) | 0.76 (0.02x) |
| ScrollBar | javafx (ref) | 1500 | 109 | 4.65 | 512 | 141 | 0.59 | 26.8 |
| ScrollBar | compiled | 1500 | 4.67 (0.04x) | 0.42 (0.09x) | 161 (0.31x) | 1.22 (<0.01x) | **1.19 (2.04x)** | 1.14 (0.04x) |
| ScrollBar | live | 1500 | 14.4 (0.13x) | 0.65 (0.14x) | 163 (0.32x) | 1.68 (0.01x) | **0.68 (1.17x)** | 1.00 (0.04x) |
| ProgressBar | javafx (ref) | 5000 | 140 | 18.7 | 725 | 170 | 7.16 | 6.25 |
| ProgressBar | compiled | 5000 | 5.75 (0.04x) | 1.19 (0.06x) | 167 (0.23x) | 2.35 (0.01x) | 2.44 (0.34x) | 0.82 (0.13x) |
| ProgressBar | live | 5000 | 15.0 (0.11x) | 1.18 (0.06x) | 164 (0.23x) | 2.42 (0.01x) | 3.45 (0.48x) | 0.52 (0.08x) |
| Separator | javafx (ref) | 5000 | 135 | 21.5 | 357 | 121 | 2.63 | 4.71 |
| Separator | compiled | 5000 | 5.14 (0.04x) | 0.82 (0.04x) | 170 (0.48x) | 3.10 (0.03x) | **2.85 (1.08x)** | 2.88 (0.61x) |
| Separator | live | 5000 | 14.4 (0.11x) | 1.15 (0.05x) | 166 (0.47x) | 2.51 (0.02x) | **3.96 (1.51x)** | 2.26 (0.48x) |
| Image | javafx (ref) | 5000 | 68.7 | 9.41 | 101 | 5.94 | 4.14 | 7.07 |
| Image | compiled | 5000 | 7.10 (0.10x) | 2.24 (0.24x) | **167 (1.66x)** | 2.53 (0.43x) | 3.03 (0.73x) | 4.97 (0.70x) |
| Image | live | 5000 | 18.1 (0.26x) | 1.97 (0.21x) | **168 (1.66x)** | 2.96 (0.50x) | 3.80 (0.92x) | 5.03 (0.71x) |

Memory, CPU and classes:

| component | mode | n | alloc build warm KB | alloc ready warm KB | alloc update warm KB | retained B/instance | thread CPU ms (all stages) | classes loaded | peak working set MB |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Label | javafx (ref) | 5000 | 17046 | 162475 | 18676 | 12164 | 2969 | 2808 | 717 |
| Label | compiled | 5000 | 548 (0.03x) | 1783 (0.01x) | 665 (0.04x) | 431 (0.04x) | 328 (0.11x) | 1446 (0.51x) | 244 (0.34x) |
| Label | live | 5000 | 1296 (0.08x) | 1785 (0.01x) | 123 (<0.01x) | 565 (0.05x) | 359 (0.12x) | 1466 (0.52x) | 243 (0.34x) |
| TextField | javafx (ref) | 1500 | 7096 | 216355 | 7354 | 76776 | 3891 | 3316 | 691 |
| TextField | compiled | 1500 | 192 (0.03x) | 636 (<0.01x) | 192 (0.03x) | 455 (<0.01x) | 297 (0.08x) | 1445 (0.44x) | 204 (0.30x) |
| TextField | live | 1500 | 416 (0.06x) | 634 (<0.01x) | 5.48 (<0.01x) | 582 (<0.01x) | 281 (0.07x) | 1465 (0.44x) | 213 (0.31x) |
| Slider | javafx (ref) | 5000 | 16936 | 205970 | 240 | 22942 | 3141 | 2918 | 720 |
| Slider | compiled | 5000 | 627 (0.04x) | 1783 (<0.01x) | **626 (2.61x)** | 446 (0.02x) | 250 (0.08x) | 1415 (0.48x) | 243 (0.34x) |
| Slider | live | 5000 | 1375 (0.08x) | 1785 (<0.01x) | 5.48 (0.02x) | 573 (0.02x) | 250 (0.08x) | 1435 (0.49x) | 242 (0.34x) |
| ScrollBar | javafx (ref) | 1500 | 5211 | 135467 | 76.0 | 40764 | 2266 | 2902 | 648 |
| ScrollBar | compiled | 1500 | 215 (0.04x) | 539 (<0.01x) | **215 (2.83x)** | 474 (0.01x) | 250 (0.11x) | 1415 (0.49x) | 214 (0.33x) |
| ScrollBar | live | 1500 | 440 (0.08x) | 540 (<0.01x) | 5.48 (0.07x) | 595 (0.01x) | 203 (0.09x) | 1435 (0.49x) | 201 (0.31x) |
| ProgressBar | javafx (ref) | 5000 | 17835 | 180818 | 672 | 16566 | 2578 | 2666 | 689 |
| ProgressBar | compiled | 5000 | 587 (0.03x) | 2096 (0.01x) | 587 (0.87x) | 504 (0.03x) | 250 (0.10x) | 1415 (0.53x) | 251 (0.36x) |
| ProgressBar | live | 5000 | 1336 (0.07x) | 2096 (0.01x) | 5.51 (<0.01x) | 631 (0.04x) | 234 (0.09x) | 1435 (0.54x) | 254 (0.37x) |
| Separator | javafx (ref) | 5000 | 16348 | 73186 | 123 | 8851 | 1484 | 2623 | 586 |
| Separator | compiled | 5000 | 588 (0.04x) | 2096 (0.03x) | **704 (5.73x)** | 504 (0.06x) | 266 (0.18x) | 1415 (0.54x) | 242 (0.41x) |
| Separator | live | 5000 | 1336 (0.08x) | 2099 (0.03x) | 123 (1.00x) | 629 (0.07x) | 266 (0.18x) | 1435 (0.55x) | 246 (0.42x) |
| Image | javafx (ref) | 5000 | 9004 | 1355 | 553 | 1959 | 422 | 2458 | 392 |
| Image | compiled | 5000 | 743 (0.08x) | **1940 (1.43x)** | **1017 (1.84x)** | 472 (0.24x) | 312 (0.74x) | 1417 (0.58x) | 237 (0.61x) |
| Image | live | 5000 | 1759 (0.20x) | **1940 (1.43x)** | 279 (0.50x) | 639 (0.33x) | 312 (0.74x) | 1438 (0.59x) | 242 (0.62x) |

#### Buttons and toggles, java17-x64

Time (cold is pass 1, warm the last pass):

| component | mode | n | build cold ms | build warm ms | ready cold ms | ready warm ms | update warm ms | paint warm ms (indicative) |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| Button | javafx (ref) | 5000 | 135 | 12.0 | 823 | 183 | 25.9 | 22.7 |
| Button | compiled | 5000 | 5.80 (0.04x) | 0.77 (0.06x) | 185 (0.22x) | 2.54 (0.01x) | 2.63 (0.10x) | 0.93 (0.04x) |
| Button | live | 5000 | 15.4 (0.11x) | 1.15 (0.10x) | 193 (0.23x) | 2.60 (0.01x) | 3.55 (0.14x) | 1.10 (0.05x) |

Memory, CPU and classes:

| component | mode | n | alloc build warm KB | alloc ready warm KB | alloc update warm KB | retained B/instance | thread CPU ms (all stages) | classes loaded | peak working set MB |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Button | javafx (ref) | 5000 | 17630 | 167241 | 19205 | 17863 | 3125 | 2959 | 712 |
| Button | compiled | 5000 | 548 (0.03x) | 1940 (0.01x) | 822 (0.04x) | 435 (0.02x) | 344 (0.11x) | 1446 (0.49x) | 250 (0.35x) |
| Button | live | 5000 | 1296 (0.07x) | 1940 (0.01x) | 281 (0.01x) | 552 (0.03x) | 344 (0.11x) | 1466 (0.50x) | 245 (0.34x) |

#### Containers and layout, java17-x64

Time (cold is pass 1, warm the last pass):

| component | mode | n | build cold ms | build warm ms | ready cold ms | ready warm ms | update warm ms | paint warm ms (indicative) |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| Grid | javafx (ref) | 1500 | 141 | 9.53 | 799 | 183 | 14.6 | 18.2 |
| Grid | compiled | 1500 | 5.36 (0.04x) | 0.35 (0.04x) | 203 (0.25x) | 6.84 (0.04x) | 4.29 (0.29x) | 0.63 (0.03x) |
| Grid | live | 1500 | 15.2 (0.11x) | 0.56 (0.06x) | 194 (0.24x) | 6.71 (0.04x) | 5.56 (0.38x) | 0.61 (0.03x) |

Memory, CPU and classes:

| component | mode | n | alloc build warm KB | alloc ready warm KB | alloc update warm KB | retained B/instance | thread CPU ms (all stages) | classes loaded | peak working set MB |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Grid | javafx (ref) | 1500 | 20662 | 147585 | 3579 | 41023 | 3062 | 2829 | 662 |
| Grid | compiled | 1500 | 496 (0.02x) | 5602 (0.04x) | **3590 (1.00x)** | 1713 (0.04x) | 359 (0.12x) | 1449 (0.51x) | 265 (0.40x) |
| Grid | live | 1500 | 813 (0.04x) | 5604 (0.04x) | 3310 (0.93x) | 1904 (0.05x) | 422 (0.14x) | 1469 (0.52x) | 268 (0.40x) |

#### Lists, tables and trees, java17-x64

Time (cold is pass 1, warm the last pass):

| component | mode | n | build cold ms | build warm ms | ready cold ms | ready warm ms | update warm ms | paint warm ms (indicative) |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| ListView | javafx (ref) | 20.0 | 85.9 | 1.22 | 220 | 27.0 | 2.16 | 7.51 |
| ListView | compiled | 20.0 | 4.88 (0.06x) | 0.25 (0.21x) | 117 (0.53x) | 0.37 (0.01x) | 0.77 (0.36x) | 1.11 (0.15x) |
| ListView | live | 20.0 | 11.5 (0.13x) | 0.25 (0.21x) | 116 (0.53x) | 0.36 (0.01x) | 0.73 (0.34x) | 1.22 (0.16x) |
| TableView | javafx (ref) | 20.0 | 101 | 2.53 | 412 | 76.1 | 1.52 | 6.73 |
| TableView | compiled | 20.0 | 6.04 (0.06x) | 0.35 (0.14x) | 118 (0.29x) | 1.08 (0.01x) | **1.65 (1.09x)** | 1.60 (0.24x) |
| TableView | live | 20.0 | 12.7 (0.13x) | 0.34 (0.14x) | 123 (0.30x) | 0.94 (0.01x) | **2.03 (1.34x)** | 1.63 (0.24x) |
| Tree | javafx (ref) | 8.00 | 249 | 59.7 | 194 | 20.2 | 3.10 | 7.02 |
| Tree | compiled | 8.00 | 13.3 (0.05x) | 1.20 (0.02x) | 116 (0.60x) | 0.29 (0.01x) | 1.87 (0.60x) | 0.99 (0.14x) |
| Tree | live | 8.00 | 20.9 (0.08x) | 1.47 (0.02x) | 116 (0.60x) | 0.31 (0.02x) | 2.11 (0.68x) | 0.84 (0.12x) |
| TreeTable | javafx (ref) | 8.00 | 263 | 52.2 | 375 | 80.6 | 2.39 | 8.95 |
| TreeTable | compiled | 8.00 | 15.6 (0.06x) | 1.79 (0.03x) | 122 (0.33x) | 0.59 (<0.01x) | 1.93 (0.81x) | 1.53 (0.17x) |
| TreeTable | live | 8.00 | 22.0 (0.08x) | 1.27 (0.02x) | 120 (0.32x) | 0.62 (<0.01x) | **2.50 (1.04x)** | 1.74 (0.19x) |

Memory, CPU and classes:

| component | mode | n | alloc build warm KB | alloc ready warm KB | alloc update warm KB | retained B/instance | thread CPU ms (all stages) | classes loaded | peak working set MB |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| ListView | javafx (ref) | 20.0 | 118 | 7857 | 114 | 162700 | 719 | 3303 | 297 |
| ListView | compiled | 20.0 | 84.6 (0.72x) | 182 (0.02x) | **133 (1.17x)** | 12727 (0.08x) | 203 (0.28x) | 1451 (0.44x) | 102 (0.34x) |
| ListView | live | 20.0 | 87.9 (0.74x) | 182 (0.02x) | **132 (1.16x)** | 16027 (0.10x) | 203 (0.28x) | 1471 (0.45x) | 102 (0.34x) |
| TableView | javafx (ref) | 20.0 | 346 | 32821 | 209 | 334175 | 1406 | 3551 | 512 |
| TableView | compiled | 20.0 | 343 (0.99x) | 829 (0.03x) | **601 (2.87x)** | 56677 (0.17x) | 234 (0.17x) | 1452 (0.41x) | 129 (0.25x) |
| TableView | live | 20.0 | **347 (1.00x)** | 829 (0.03x) | **600 (2.87x)** | 71620 (0.21x) | 250 (0.18x) | 1472 (0.41x) | 129 (0.25x) |
| Tree | javafx (ref) | 8.00 | 90105 | 4088 | 225 | 5579999 | 1016 | 3299 | 643 |
| Tree | compiled | 8.00 | 112 (<0.01x) | 76.5 (0.02x) | 131 (0.58x) | 15703 (<0.01x) | 234 (0.23x) | 1456 (0.44x) | 104 (0.16x) |
| Tree | live | 8.00 | 114 (<0.01x) | 76.5 (0.02x) | 130 (0.58x) | 21118 (<0.01x) | 203 (0.20x) | 1476 (0.45x) | 106 (0.16x) |
| TreeTable | javafx (ref) | 8.00 | 89550 | 21567 | 188 | 6035609 | 1672 | 3591 | 698 |
| TreeTable | compiled | 8.00 | 218 (<0.01x) | 281 (0.01x) | **292 (1.55x)** | 49660 (<0.01x) | 250 (0.15x) | 1458 (0.41x) | 117 (0.17x) |
| TreeTable | live | 8.00 | 217 (<0.01x) | 281 (0.01x) | **291 (1.55x)** | 65996 (0.01x) | 266 (0.16x) | 1478 (0.41x) | 117 (0.17x) |

#### Live bindings against compiled JXML and JavaFX, java17-x64

Update of one prop of every instance, warm pass. Ratios are JavaFX / mode (above 1x the mode is better than JavaFX) and live / compiled (below 1x live is better than compiled). Build and ready include the recording and its pairing with the mounted nodes.

| component | n | update ms compiled | live | JavaFX | JavaFX/compiled | JavaFX/live | live/compiled | alloc KB compiled | live | JavaFX | JavaFX/compiled | JavaFX/live | live/compiled | fallbacks | opaque builds |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Label | 5000 | 2.34 | 3.41 | 34.4 | 14.68x | 10.10x | 1.45x | 665 | 123 | 18676 | 28.07x | 152.20x | 0.18x | 0.00 | 0.00 |
| TextField | 1500 | 0.95 | 0.58 | 12.9 | 13.57x | 22.13x | 0.61x | 192 | 5.48 | 7354 | 38.32x | 1342.86x | 0.03x | 0.00 | 0.00 |
| Slider | 5000 | 1.71 | 2.32 | 2.65 | 1.54x | 1.14x | 1.35x | 626 | 5.48 | 240 | 0.38x | 43.84x | <0.01x | 0.00 | 0.00 |
| ScrollBar | 1500 | 1.19 | 0.68 | 0.59 | 0.49x | 0.86x | 0.57x | 215 | 5.48 | 76.0 | 0.35x | 13.89x | 0.03x | 0.00 | 0.00 |
| ProgressBar | 5000 | 2.44 | 3.45 | 7.16 | 2.94x | 2.08x | 1.42x | 587 | 5.51 | 672 | 1.14x | 122.04x | <0.01x | 0.00 | 0.00 |
| Separator | 5000 | 2.85 | 3.96 | 2.63 | 0.92x | 0.66x | 1.39x | 704 | 123 | 123 | 0.17x | 1.00x | 0.17x | 0.00 | 0.00 |
| Image | 5000 | 3.03 | 3.80 | 4.14 | 1.37x | 1.09x | 1.26x | 1017 | 279 | 553 | 0.54x | 1.98x | 0.27x | 0.00 | 0.00 |
| Button | 5000 | 2.63 | 3.55 | 25.9 | 9.85x | 7.29x | 1.35x | 822 | 281 | 19205 | 23.37x | 68.27x | 0.34x | 0.00 | 0.00 |
| Grid | 1500 | 4.29 | 5.56 | 14.6 | 3.40x | 2.63x | 1.29x | 3590 | 3310 | 3579 | 1.00x | 1.08x | 0.92x | 0.00 | 0.00 |
| ListView | 20.0 | 0.77 | 0.73 | 2.16 | 2.81x | 2.94x | 0.95x | 133 | 132 | 114 | 0.86x | 0.86x | 0.99x | 0.00 | 180 |
| TableView | 20.0 | 1.65 | 2.03 | 1.52 | 0.92x | 0.75x | 1.23x | 601 | 600 | 209 | 0.35x | 0.35x | 1.00x | 0.00 | 180 |
| Tree | 8.00 | 1.87 | 2.11 | 3.10 | 1.66x | 1.47x | 1.13x | 131 | 130 | 225 | 1.72x | 1.73x | 1.00x | 0.00 | 72.0 |
| TreeTable | 8.00 | 1.93 | 2.50 | 2.39 | 1.24x | 0.96x | 1.29x | 292 | 291 | 188 | 0.64x | 0.65x | 1.00x | 0.00 | 72.0 |

Build, ready, retained (compiled and live), warm:

| component | build ms compiled | live | ready ms compiled | live | build KB compiled | live | ready KB compiled | live | retained B/instance compiled | live | live/compiled |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Label | 0.75 | 1.02 | 2.14 | 2.17 | 548 | 1296 | 1783 | 1785 | 431 | 565 | 1.31x |
| TextField | 0.30 | 0.87 | 1.18 | 1.38 | 192 | 416 | 636 | 634 | 455 | 582 | 1.28x |
| Slider | 1.02 | 1.19 | 2.39 | 2.77 | 627 | 1375 | 1783 | 1785 | 446 | 573 | 1.28x |
| ScrollBar | 0.42 | 0.65 | 1.22 | 1.68 | 215 | 440 | 539 | 540 | 474 | 595 | 1.26x |
| ProgressBar | 1.19 | 1.18 | 2.35 | 2.42 | 587 | 1336 | 2096 | 2096 | 504 | 631 | 1.25x |
| Separator | 0.82 | 1.15 | 3.10 | 2.51 | 588 | 1336 | 2096 | 2099 | 504 | 629 | 1.25x |
| Image | 2.24 | 1.97 | 2.53 | 2.96 | 743 | 1759 | 1940 | 1940 | 472 | 639 | 1.35x |
| Button | 0.77 | 1.15 | 2.54 | 2.60 | 548 | 1296 | 1940 | 1940 | 435 | 552 | 1.27x |
| Grid | 0.35 | 0.56 | 6.84 | 6.71 | 496 | 813 | 5602 | 5604 | 1713 | 1904 | 1.11x |
| ListView | 0.25 | 0.25 | 0.37 | 0.36 | 84.6 | 87.9 | 182 | 182 | 12727 | 16027 | 1.26x |
| TableView | 0.35 | 0.34 | 1.08 | 0.94 | 343 | 347 | 829 | 829 | 56677 | 71620 | 1.26x |
| Tree | 1.20 | 1.47 | 0.29 | 0.31 | 112 | 114 | 76.5 | 76.5 | 15703 | 21118 | 1.34x |
| TreeTable | 1.79 | 1.27 | 0.59 | 0.62 | 218 | 217 | 281 | 281 | 49660 | 65996 | 1.33x |

#### Biggest wins and losses of compiled JXML against JavaFX, java17-x64 (build + ready warm)

| | component | compiled ms | JavaFX ms | ratio |
|---|---|---:|---:|---:|
| win | TextField | 1.48 | 339 | <0.01x |
| win | ScrollBar | 1.64 | 146 | 0.01x |
| win | Slider | 3.41 | 219 | 0.02x |
| win | Label | 2.89 | 180 | 0.02x |
| win | Button | 3.31 | 195 | 0.02x |
| win | TreeTable | 2.39 | 133 | 0.02x |
| win | TableView | 1.43 | 78.6 | 0.02x |
| win | Tree | 1.49 | 79.9 | 0.02x |
| win | ProgressBar | 3.55 | 189 | 0.02x |
| win | ListView | 0.62 | 28.2 | 0.02x |
| loss | Image | 4.77 | 15.3 | 0.31x |
| loss | Grid | 7.19 | 192 | 0.04x |
| loss | Separator | 3.92 | 142 | 0.03x |
| loss | ListView | 0.62 | 28.2 | 0.02x |
| loss | ProgressBar | 3.55 | 189 | 0.02x |
| loss | Tree | 1.49 | 79.9 | 0.02x |
| loss | TableView | 1.43 | 78.6 | 0.02x |
| loss | TreeTable | 2.39 | 133 | 0.02x |
| loss | Button | 3.31 | 195 | 0.02x |
| loss | Label | 2.89 | 180 | 0.02x |

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
