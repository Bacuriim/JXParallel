# JavaFX vs JXParallel native (Skia + OpenGL): comparison, 2026-09-24

Medians of 5 runs per side, each run in a fresh JVM, alternating JavaFX and JXParallel.
Raw data: [ui-stress-results-2026-09-24.csv](ui-stress-results-2026-09-24.csv),
medians: [ui-stress-results-2026-09-24-median.csv](ui-stress-results-2026-09-24-median.csv).

## Setup

| | |
|---|---|
| Machine | Windows 11, Intel Core i7-1255U, Intel Iris Xe, display at about 102 Hz |
| JVM | Oracle JDK 17.0.12 x64, default heap settings |
| JavaFX | OpenJFX 21.0.2 (`JavaFxStressRunner`, no JXParallel on the classpath) |
| JXParallel | `JXWindow`: Skija 0.116.4 on GLFW/OpenGL, LWJGL 3.3.3 (`NativeStressRunner`, no JavaFX) |
| Scene | label, button, text field and a 5-item list (JavaFX `ListView`, JXParallel `JXComboBox`) |

Two phases per run:

1. **Burst**: each component is updated 500 times in a loop on the UI thread without waiting
   for frames. On the JXParallel side every update rebuilds the element tree
   (`setContent(content.render())`), which is what a change costs before it can be drawn.
2. **Sustained**: every presented frame updates all four components, for 600 frames,
   paced by vsync. This is the phase that includes real rendering.

Memory outside the JVM (working set, private bytes, handles) is sampled every 10 ms.
Heap, non-heap and direct memory are sampled inside the JVM every 10 ms.

## Results

| Metric | JavaFX | JXParallel | Difference |
|---|---:|---:|---:|
| Startup, JVM start to first frame | 1494 ms | 847 ms | -43% |
| Peak working set (RAM) | 248.6 MB | 159.8 MB | -36% |
| Average working set | 198.3 MB | 139.4 MB | -30% |
| Peak private bytes | 500.0 MB | 402.8 MB | -19% |
| Peak heap used | 50.0 MB | 20.0 MB | -60% |
| Heap live after GC (retained) | 10.6 MB | 2.2 MB | -80% |
| Peak non-heap (metaspace, code cache) | 30.2 MB | 9.9 MB | -67% |
| Peak direct buffers | 3.6 KB | 89.4 KB | +86 KB |
| Total allocated | 91.7 MB | 24.4 MB | -73% |
| GC collections / GC time | 2 / 11 ms | 1 / 5 ms | |
| Classes loaded | 3772 | 1650 | -56% |
| Peak threads | 15 | 11 | -4 |
| Peak OS handles | 562 | 534 | -5% |
| Total process CPU | 10.0 s | 3.1 s | -69% |
| **Burst** total (4 x 500 updates) | 239.0 ms | 66.9 ms | -72% |
| Burst: label | 11.6 ms | 30.0 ms | **+160%** |
| Burst: button | 19.9 ms | 11.1 ms | -44% |
| Burst: list | 30.4 ms | 15.7 ms | -48% |
| Burst: text field | 179.2 ms | 8.2 ms | -95% |
| Burst CPU | 969 ms | 219 ms | -77% |
| **Sustained** CPU (600 frames) | 6375 ms | 1781 ms | -72% |
| Sustained CPU per frame | 10.6 ms | 3.0 ms | -72% |
| Sustained allocation | 34.3 MB | 5.2 MB | -85% |
| Frames per second | 101.9 | 101.6 | same (vsync) |
| Frame interval p50 / p95 / p99 | 9.3 / 16.4 / 17.8 ms | 8.7 / 17.2 / 18.2 ms | about equal |
| Worst frame | 53.2 ms | 21.4 ms | -60% |
| Frames over 25 ms (jank) | 3 | 0 | |

### Rerun on an idle machine, 2026-09-25

Background load 2 to 9%, IDE and browser closed, but the Windows session was **locked**, which
throttles frame presentation for both sides (about 30 and 25 fps, worst frame 0.54 s).
Frame pacing from this run is not valid; memory, CPU and burst numbers are.
Data: [ui-stress-results-2026-09-25.csv](ui-stress-results-2026-09-25.csv).

| Metric | JavaFX | JXParallel | Difference |
|---|---:|---:|---:|
| Peak working set | 237.3 MB | 155.8 MB | -34% |
| Heap live after GC | 10.6 MB | 2.2 MB | -80% |
| Startup, JVM start to first frame | 1447 ms | 1016 ms | -30% |
| Burst total (4 x 500 updates) | 173.3 ms | 39.4 ms | -77% |
| Burst: label | 7.1 ms | 12.7 ms | **+80%** |
| Sustained CPU per frame | 10.1 ms | 5.3 ms | -47% |
| Sustained allocation (600 frames) | 37.2 MB | 7.9 MB | -79% |

Peak working set over the 5 runs: JavaFX 228 to 239 MB, JXParallel 155 to 156 MB.

## What the numbers do not show

- **The two sides do not draw the same thing.** JavaFX runs CSS, control skins, LCD text,
  focus rings and a real `ListView` with cells. The JXParallel renderer draws rectangles and
  text only, with no CSS, and the combo box is a single field. Part of the CPU and memory gap
  comes from doing less work, not from doing the same work faster.
- **Label updates are slower in JXParallel** (30.0 vs 11.6 ms per 500). Each change rebuilds
  the whole element tree. With a larger scene this cost grows with the node count; JavaFX only
  invalidates the changed node.
- **Frame rate is equal** because both are capped by vsync. The difference is in how much CPU
  each frame costs (10.6 vs 3.0 ms) and in the worst frames.
- Committed heap is identical (252 MB) because both use the JVM's default sizing; compare used
  and live heap, not committed.
- Windows reports process CPU in 15.6 ms steps, so CPU values under about 50 ms are coarse.
- The native side needs a 64-bit JVM (Skija has no 32-bit natives), so this comparison cannot
  run on the Java 8 32-bit target.

## Reproduce

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
mvn -Plegacy-javafx -pl jxparallel-examples,jxparallel-examples-native -am compile
mvn -pl jxparallel-examples-native dependency:build-classpath "-Dmdep.outputFile=$env:TEMP\native-cp.txt"
.\scripts\measure-ui-stress.ps1 -JavaPath "$env:JAVA_HOME\bin\java.exe" `
  -JavaFxClasspath jxparallel-examples\target\classes `
  -JavaFxModulePath "<javafx-base-win.jar>;<javafx-graphics-win.jar>;<javafx-controls-win.jar>" `
  -NativeDependencies (Get-Content "$env:TEMP\native-cp.txt") `
  -Runs 5 -SustainedFrames 600 -Output docs\ui-stress-results.csv
```

If an old build is installed in `~/.m2`, its `jxparallel-ui` and `jxparallel-core` jars are
harmless: the script puts the reactor `target\classes` first on the classpath.
