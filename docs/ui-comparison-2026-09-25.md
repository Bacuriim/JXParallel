# JavaFX vs JXParallel UI, 32-bit and 64-bit, 2026-09-25

Same runners and method as [ui-stress-comparison-2026-09-24.md](ui-stress-comparison-2026-09-24.md):
fresh JVM per run, 5 runs alternating JavaFX and JXParallel, medians. Burst: 4 components x 500
updates, each pushed to the renderer. Sustained: 600 vsync-paced frames updating every component.
Display at about 118 Hz, session unlocked, 5 to 23% background CPU.

| | 32-bit | 64-bit |
|---|---|---|
| JVM | Oracle JDK 1.8.0_51 x86 (client VM) | Oracle JDK 17.0.12 x64 |
| JavaFX | JavaFX 8 bundled with the JDK | OpenJFX 21.0.2 |
| JXParallel renderer | NanoVG (chosen automatically) | Skia (chosen automatically) |
| Raw data | [x86 CSV](ui-stress-results-x86-2026-09-25.csv) | [x64 CSV](ui-stress-results-x64-2026-09-25.csv) |

![NanoVG on Java 8 32-bit and Skia on Java 17 64-bit](assets/renderers-nanovg-vs-skia.png)

## 32-bit: JavaFX 8 vs JXParallel NanoVG

| Metric | JavaFX 8 | JXParallel | Difference |
|---|---:|---:|---:|
| JVM start to first frame | 777 ms | 609 ms | -22% |
| Peak working set | 97.3 MB | 89.3 MB | -8% |
| Peak private bytes | 97.4 MB | 64.6 MB | -34% |
| Peak heap | 11.1 MB | 6.3 MB | -43% |
| Heap live after GC | 7.6 MB | 2.1 MB | -72% |
| Peak non-heap | 12.1 MB | 6.3 MB | -48% |
| Total allocated | 55.1 MB | 21.6 MB | -61% |
| GC collections / time | 12 / 26 ms | 5 / 8 ms | |
| Classes loaded | 2808 | 1236 | -56% |
| Threads | 12 | 8 | |
| Total process CPU | 3.63 s | 1.61 s | -56% |
| Burst total | 131.3 ms | 53.7 ms | -59% |
| Burst: label | 13.8 ms | 30.8 ms | **+124%** |
| Sustained CPU per frame | 3.9 ms | 1.5 ms | -62% |
| Sustained allocation | 24.5 MB | 3.9 MB | -84% |
| Frames per second | 118.2 | 118.5 | same (vsync) |
| Frame interval p95 / p99 | 15.1 / 16.3 ms | 10.5 / 14.2 ms | |
| Worst frame | 30.4 ms | 40.2 ms | **+32%** |
| Frames over 25 ms | 1 | 1 | |

## 64-bit: JavaFX 21 vs JXParallel Skia

| Metric | JavaFX 21 | JXParallel | Difference |
|---|---:|---:|---:|
| JVM start to first frame | 1494 ms | 840 ms | -44% |
| Peak working set | 247.5 MB | 160.1 MB | -35% |
| Peak heap | 50.0 MB | 20.0 MB | -60% |
| Heap live after GC | 10.6 MB | 2.1 MB | -80% |
| Total allocated | 91.6 MB | 24.0 MB | -74% |
| Classes loaded | 3776 | 1603 | -58% |
| Total process CPU | 10.5 s | 3.19 s | -70% |
| Burst total | 255.7 ms | 87.2 ms | -66% |
| Burst: label | 14.7 ms | 17.6 ms | **+20%** |
| Sustained CPU per frame | 11.3 ms | 2.9 ms | -74% |
| Sustained allocation | 34.1 MB | 5.0 MB | -85% |
| Frames per second | 117.3 | 118.7 | same (vsync) |
| Frame interval p95 / p99 | 14.7 / 16.0 ms | 10.1 / 13.0 ms | |
| Worst frame | 55.0 ms | 39.1 ms | -29% |
| Frames over 25 ms | 2 | 1 | |

## Reading the numbers

- **32-bit is where memory is tight, and the gap there is smaller in working set (-8%) but
  large in private bytes, heap and allocation.** JavaFX 8 on the client VM is lean; the gain comes
  from allocating less (24.5 vs 3.9 MB over 600 frames) and loading half the classes.
- **CPU per frame is 2.5x to 4x lower** on both platforms, with the same frame rate.
- **Label updates are still slower** because each change rebuilds the element tree: +124% on
  32-bit, +20% on 64-bit. The 64-bit value has ranged from 12.7 to 30.0 ms across batches, so
  treat it as noisy; incremental tree updates are the fix.
- **NanoVG had one worse worst-frame** (40 vs 30 ms) on 32-bit; p95 and p99 are better. One
  outlier per 600 frames on both sides, so this needs more runs before drawing a conclusion.
- JavaFX draws more (CSS, skins, a real `ListView`); part of every gap comes from that.

## Reproduce

32-bit (JavaFX 8 is on the JDK 8 classpath already, no module path):

```powershell
$env:JAVA_HOME = 'C:\Program Files (x86)\Java\jdk1.8.0_51'
mvn -pl jxparallel-examples-native dependency:build-classpath "-Dmdep.outputFile=$env:TEMP\cp-x86.txt"
.\scripts\measure-ui-stress.ps1 -JavaPath "$env:JAVA_HOME\bin\java.exe" `
  -JavaFxClasspath jxparallel-examples\target\classes `
  -NativeDependencies (Get-Content "$env:TEMP\cp-x86.txt") -Runs 5 -Output docs\ui-stress-results-x86.csv
```

64-bit: see the 2026-09-24 report. `-Monitor 1` opens both windows on the second monitor.
