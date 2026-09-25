# FXML loading: JavaFX FXMLLoader vs JXParallel

20 generated screens (`jxparallel-examples/src/main/resources/fxmlbench`), each a form with a
toolbar, 3 tabs of 15 labelled fields (TextField, ComboBox, CheckBox, DatePicker, Spinner) and
its own controller with 21 `@FXML` fields and an `initialize()`. About 135 elements per file.
Regenerate with `python scripts/generate-fxml-bench.py`.

Each mode runs in a fresh JVM and loads all 20 screens three times: pass 1 is cold (class
loading, interpreter), pass 3 is warm (JIT compiled). Every pass checks that all 20 controllers
ran `initialize()`.

| Mode | How |
|---|---|
| JavaFX | `FXMLLoader.load` for each screen, one after the other, on the FX thread |
| JXParallel | `FXMLLoaderService.loadAsync` for all 20 at once on the worker pool (8 threads), FXML cache on |

Raw data: [fxml-load-results-2026-09-25.csv](fxml-load-results-2026-09-25.csv),
medians: [fxml-load-results-2026-09-25-median.csv](fxml-load-results-2026-09-25-median.csv).

## Results

Median of 5 runs, 2026-09-25, idle machine (2 to 14% CPU from other processes).
JDK 17.0.12 x64, OpenJFX 21.0.2, Core i7-1255U (12 threads).

| Metric | JavaFX | JXParallel | Difference |
|---|---:|---:|---:|
| Cold, 20 screens | 1163 ms | 725 ms | **1.6x faster** |
| Warm, 20 screens | 665 ms | 194 ms | **3.4x faster** |
| FX thread blocked while loading (cold) | 1163 ms | 0 ms | UI stays responsive |
| Cold, first screen ready | 371 ms | 536 ms | 1.4x slower |
| Warm, first screen ready | 30 ms | 72 ms | 2.4x slower |
| CPU, cold pass | 3.70 s | 4.78 s | +29% |
| CPU, warm pass | 1.98 s | 1.86 s | -6% |
| Peak working set | 319 MB | 395 MB | +24% |
| Peak heap | 150 MB | 192 MB | +27% |
| Allocated per warm pass | 213 MB | 215 MB | same |

Spread across the 5 runs (warm pass): JavaFX 620 to 770 ms, JXParallel 191 to 214 ms.
The ranges do not overlap.

Scaling with worker threads (warm pass, median of 3, busier machine on 2026-09-24):
1 thread 1221 ms, 2 threads 1170 ms, 4 threads 619 ms, 8 threads 442 ms. A first batch that
day with 27 to 42% background load gave slower absolute times for both sides but the same
direction (1.6 to 3x); it was discarded.

## Findings

1. **Parallel loading is 1.6x faster cold and 3.4x faster warm** for the full set, and it never
   blocks the FX thread. With plain JavaFX the UI is frozen for the whole load.
2. **The first screen arrives later.** All 20 loads compete for the same cores, so the one the
   user is waiting for is not favoured. The pool already supports priorities; the screen being
   opened should be submitted with `TaskPriority.HIGH` and the rest in the background.
3. **The FXML cache does not help.** It stores the file bytes (40 hits in 60 loads), but reading
   bytes is not the cost: warm passes allocate the same 213 to 215 MB as JavaFX. The cost is
   XML parsing, reflection to build nodes, and `@FXML` injection.
4. **Memory peaks higher** (+24% working set) because several screens are built at the same time.

## Bug fixed during this test

`AdaptiveWorkerPool` never grew past `threads.min` (2): `ThreadPoolExecutor` only adds threads
beyond the core size when the queue is full, and the queue holds thousands of tasks.
`threads.auto-scale` was read but unused. With auto-scale on, core size is now `threads.max`
and idle workers expire after the keep-alive. That exposed a second bug: the bounded queue did
not release its permit in `poll(timeout)`, which is what expiring workers call, so the queue
filled up with phantom entries and rejected tasks. `AdaptiveWorkerPoolScalingTest` covers it.

## Next steps to make the cache worth having

- Cache what is expensive: a parsed, pre-resolved template of the FXML (element tree with
  classes and setters already looked up), so later loads skip XML parsing and reflection lookup.
- Or compile FXML to Java at build time: no XML and no reflection at runtime at all.

## Reproduce

Close the IDE and browsers first; background load changes absolute times by up to 2x.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
mvn -Plegacy-javafx -pl jxparallel-examples -am compile
$fx = "$env:USERPROFILE\.m2\repository\org\openjfx"
$mp = (@('javafx-base','javafx-graphics','javafx-controls','javafx-fxml') |
       ForEach-Object { "$fx\$_\21.0.2\$_-21.0.2-win.jar" }) -join ';'
.\scripts\measure-fxml.ps1 -JavaPath "$env:JAVA_HOME\bin\java.exe" -JavaFxModulePath $mp -Runs 5
```

Worker count can be overridden per run with `-Dthreads.max=N`.
