# Java 8 32-bit comparison applications

The examples module contains two applications with the same user flow:

- `TraditionalJavaFxApp`: standard JavaFX `TextField`, `Button`, `Task`, and `Platform` behavior
- `JXParallelJavaFxApp`: `JXTextField`, `JXButton`, JXParallel background execution, and modern visual options

Both applications display the same screen:

1. name input
2. continue button
3. status label
4. background operation
5. result returned to the JavaFX Application Thread

## Java 8 32-bit constraint

The current reactor uses OpenJFX 21 for the development build. OpenJFX 21 cannot run on Java 8
32-bit. To run these examples on Java 8 x86, use a JavaFX 8 SDK matching the x86 JDK and compile
the JavaFX-dependent modules against that SDK.

Example Windows setup:

```powershell
$env:JAVA_HOME = 'C:\Program Files (x86)\Java\jdk1.8.0_202'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

& "$env:JAVA_HOME\bin\java.exe" -version
```

The output must identify a 32-bit Java 8 runtime. The JavaFX 8 SDK must provide matching
`jfxrt.jar`/runtime libraries. Do not mix Java 8 x86 with OpenJFX 21 artifacts.

## Comparison guidance

Run both programs with the same JVM architecture and dataset. Measure:

- startup time
- heap usage
- button interaction latency
- background completion latency
- number of worker threads
- visual behavior and animation smoothness

The JXParallel application changes the runtime and visual defaults, not the JavaFX developer
model. The controls still expose JavaFX-like methods and are mounted into the scene through
`node()`.

For automated GUI collection, compile the core, JavaFX bridge, and examples with the x86 JDK,
then run:

```powershell
$java = 'C:\Program Files (x86)\Java\jdk1.8.0_51\bin\java.exe'
$jfx = 'C:\Program Files (x86)\Java\jdk1.8.0_51\jre\lib\ext\jfxrt.jar'
$cp = 'jxparallel-examples\target\java8-metrics\examples;jxparallel-examples\target\java8-metrics\javafx;jxparallel-examples\target\java8-metrics\core;' + $jfx

& $java '-Djx.metrics.implementation=traditional' '-cp' $cp com.jxparallel.examples.JavaFxMetricsRunner
& $java '-Djx.metrics.implementation=jxparallel' '-cp' $cp com.jxparallel.examples.JavaFxMetricsRunner
```

The runner prints startup time, interaction response time, heap delta, process CPU time, and
thread-count delta.
