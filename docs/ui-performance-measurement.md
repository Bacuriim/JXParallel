# UI performance measurement

The repository includes a process-level comparison between:

- JavaFX traditional controls: `TextField`, `Button`, `Label`, `VBox`, and a background task.
- JXParallel native controls: `JXTextField`, `JXButton`, `JXLabel`, `JXPane`, and `JXParallel.background`.

The comparison intentionally does not compare the old JavaFX bridge with the native renderer.
It measures the current JavaFX-free UI path against a JavaFX application with the same visible
component structure and the same 350 ms background operation.

## Metrics

`scripts\measure-ui.ps1` collects:

- startup from Java process marker to the first rendered frame;
- interaction latency from action start to completion;
- process CPU time and CPU normalized by elapsed time and processor count;
- peak, average, and final Windows working set (resident memory);
- peak private process memory;
- Java heap delta;
- thread-count delta;
- exit code and wall-clock duration.

Heap is not a substitute for process memory. Windows working set and private bytes are collected
outside the JVM with `Get-Process`, which captures native runtime, graphics, toolkit, and JVM
memory as well.

## Running

Build the native modules and the optional JavaFX modules with the same JDK:

```powershell
mvn -Plegacy-javafx -pl jxparallel-examples -am package -DskipTests
mvn -pl jxparallel-examples-native -am package -DskipTests
```

Build a JavaFX classpath containing:

1. `jxparallel-examples\target\classes`;
2. `jxparallel-javafx\target\classes`;
3. `jxparallel-core\target\classes`.

JavaFX 9+ must also be launched with a module path containing the matching OpenJFX jars
(`javafx-base`, `javafx-graphics`, `javafx-controls` and their platform jars).

Then run:

```powershell
$javafxCp = "jxparallel-examples\target\classes;jxparallel-javafx\target\classes;jxparallel-core\target\classes"
$javafxModules = "C:\path\to\javafx-base.jar;C:\path\to\javafx-base-win.jar;C:\path\to\javafx-graphics.jar;C:\path\to\javafx-graphics-win.jar;C:\path\to\javafx-controls.jar;C:\path\to\javafx-controls-win.jar"
powershell -ExecutionPolicy Bypass -File .\scripts\measure-ui.ps1 `
  -JavaPath "$env:JAVA_HOME\bin\java.exe" `
  -JavaFxClasspath $javafxCp `
  -JavaFxModulePath $javafxModules `
  -Runs 5 `
  -Output .\docs\ui-metrics.csv
```

Use the same JDK, JVM flags, display, screen scale, power profile, and number of repetitions for
both implementations. Close other CPU- and memory-intensive applications. Report median and
percentiles, not only one run. Do not claim superiority from a single machine or from heap delta
alone.
