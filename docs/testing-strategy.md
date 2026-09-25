# Testing strategy, compared with OpenJFX

JXParallel is tested in five layers taken from how large Java libraries are tested (Guava,
Caffeine, Netty, the JDK itself). This page lists each layer, the bugs it found in this code base,
and what OpenJFX does for the same concern.

OpenJFX numbers come from the public repository `openjdk/jfx`, branch `master` (JavaFX 28 in
development), read on 2026-09-25: file counts through Sourcegraph code search, build settings from
`build.gradle`, test dependencies from `gradle/verification-metadata.xml`, CI from
`.github/workflows/submit.yml`. Counts are files, not test methods.

## Summary

| Layer | Tool | JXParallel | OpenJFX | Bugs found here |
|---|---|---|---|---|
| Unit | JUnit 5.10 | 82 tests in 40 files (core 28, UI 37, FXML 10, JavaFX bridge 6, Mockito 1) | 1,192 test files under `modules/*/src/test` (JUnit 6.1.3) | |
| 1. Properties and differential | jqwik 1.8.5 | reconcile vs fresh mount; FxmlTemplate vs FXMLLoader on random FXML | none (no property-based library in the build) | 2 in FxmlTemplate |
| 2. Concurrency | jcstress 0.16 (OpenJDK) | 5 tests, about 419 million samples per run | none; 216 test files use `CountDownLatch`/`CyclicBarrier` ad hoc | 3 races |
| 3. Snapshot and interaction | text snapshot, Skia golden PNG, headless click test | 1 screen, all controls | 174 robot test files in `tests/system`, pixel checks at single points | 1 (clicks never reached buttons) |
| 4. Performance regression | JMH 1.37 + ratio gate in CI | reconcile, observables | no JMH; 11 files of manual performance apps in `tests/performance` | |
| 5. Contract | ArchUnit, JaCoCo, PIT, leak tests | 4 architecture rules, coverage and mutation reports, 3 leak tests | JMemoryBuddy leak tests (28 files); coverage with JCov only in closed builds | 1 leak |

## How OpenJFX tests

- **Unit tests run without a screen.** `-Djavafx.toolkit=test.com.sun.javafx.pgstub.StubToolkit`
  (76 test files reference `StubToolkit`). Tests reach package-private internals through 182
  "shim" classes compiled into the modules with `--patch-module`. A newer `HEADLESS_TEST` flag
  runs on the Headless glass platform with the software pipeline (`prism.order=sw`).
- **System tests** (499 files in `tests/system`) start the real toolkit; 174 of them use a
  `Robot` and check colours at chosen points. They only run with `-PFULL_TEST=true
  -PUSE_ROBOT=true`; the default Gradle run is the smoke subset.
- **Manual tests** (257 files in `tests/manual`) are apps a person runs and looks at.
- **Timeouts** are global: 120 s per test, 20 s per lifecycle method.
- **Unstable tests** are skipped unless `-PUNSTABLE_TEST=true`.
- **CI** (GitHub Actions) builds and runs the headless tests on Linux x64/aarch64, macOS
  x64/aarch64 and Windows, excluding WebKit (`gradlew test -x :web:test`).
- **Only dependency for tests:** JUnit (Jupiter, Platform, Params) 6.1.3. No jqwik, jcstress,
  JMH, ArchUnit, Mockito, AssertJ, TestFX or JaCoCo appears in `verification-metadata.xml`.

The OpenJFX test sources are GPLv2 with the Classpath Exception, so none of them are copied here;
the ideas are reimplemented.

## 1. Property-based and differential tests

`JXNativeNodeReconcilePropertyTest` (jxparallel-ui) generates random trees of rows, columns,
stacks and controls. For any trees `a` and `b`, updating `a` in place to `b` must give the same
node types, props and bounds as mounting `b` from scratch. A second property applies small edits
(gap change, text change, child added, removed or replaced) to one tree, which is what an app
does between two frames.

Injecting a bug (ignoring `gap` changes in reconcile) showed why the second property is needed:
500 pairs of independent random trees missed it, because independent trees almost never differ
only in `gap`. The small-edits property caught it and jqwik shrank the case in 19 steps.

`FxmlTemplateDifferentialTest` (jxparallel-fxml) generates FXML documents (nested beans,
`@NamedArg` constructors with int or double values, static properties, `fx:id`, optional
attributes) and loads each with JavaFX's `FXMLLoader` and with `FxmlTemplate`. The two object
graphs must be equal; `FXMLLoader` is the specification. 300 documents per run.

Bugs it found in `FxmlTemplate`:

1. `fx:id` was copied to any `id` property. `FXMLLoader` copies it only when the class has
   `@IDProperty` (JavaFX `Node` does). Harmless for `Node`s, wrong for plain beans.
2. Class names were resolved more loosely than `FXMLLoader`: a wildcard import of an outer class
   (`<?import a.Outer.*?>`) and a short name for an imported nested class (`<Inner>`) worked in
   JXParallel but fail in JavaFX. FXML written against JXParallel could then not be loaded by
   plain JavaFX. The resolver now follows `FXMLLoader.getType` and `loadType`: the package ends
   before the first segment that starts upper case, and nested classes keep their `Outer.Inner`
   name.

After the fixes the differential test passes and the 20-screen FXML benchmark still loads all
screens from templates (`templates_cached=20`, same structural fingerprint).

## 2. Concurrency: jcstress

jcstress is the OpenJDK tool used to test `java.util.concurrent`. Each test runs the same small
scenario tens of millions of times across JIT modes and thread placements and counts every
outcome. Module `jxparallel-jcstress`, profile `jcstress`:

```
mvn -Pjcstress -pl jxparallel-jcstress -am package
java -jar jxparallel-jcstress/target/jcstress.jar -m quick
python scripts/jcstress-summary.py results
```

| Test | Contract | Before | After |
|---|---|---:|---:|
| `BoundedQueuePermitsTest` | queued items + free permits == capacity | 0 in 109,398,852 | 0 in 185,397,984 |
| `JXObservableListEventOrderTest` | ADD events arrive in index order | 28,027 out of order in 30,370,385 | 0 in 34,759,505 |
| `JXPropertySetTest.DifferentValues` | listener's last value == property value | 66,544 wrong in 44,682,037 | 0 in 57,282,723 |
| `JXPropertySetTest.SameValue` | setting the same value twice is one change | 2,956,817 duplicates in 46,820,771 | 0 in 62,901,494 |
| `JXStateNotificationOrderTest` | subscriber's last value == state value | 9,926 wrong in 54,083,570 | 0 in 78,968,699 |

JDK 17.0.12, Windows 11, i7-1255U, jcstress `-m quick`. The queue test guards the permit leak
fixed on 2026-09-24.

Cause of the three races: the value was changed under a lock, but listeners were called after
the lock was released, so two threads could deliver their events in the opposite order, and
`JXProperty.set` had no lock at all. Fix: a change and its notifications now run under one lock
(for the list, a separate write lock so `get` and `size` are not blocked by slow listeners), and
listener lists are `CopyOnWriteArrayList`. Readers never block.

Cost, JMH, single thread, same machine, old and new code run back to back:

| Operation, one listener | Before | After |
|---|---:|---:|
| `JXObservableList` add + remove | 105.9 ns | 64.9 ns |
| `JXProperty.set` | 37.8 ns | 28.8 ns |
| `JXState.set` | 31.6 ns | 24.2 ns |

The locks cost less than the listener-list copy that each notification used to make.

jcstress 0.16 cannot run on Java 8 (it calls `Thread.onSpinWait`, added in Java 9). The tests
run on JDK 17 against the same Java 8 bytecode of jxparallel-core.

OpenJFX has no equivalent: JavaFX properties and collections are not thread safe and must be used
from the FX thread, so there is no contract to test. Its tests coordinate threads with latches.

## 3. Snapshot and interaction tests

`JXSnapshotTest` renders one screen with every control.

- **Layout snapshot:** node types and bounds as text, compared with
  `src/test/resources/snapshots/showcase-layout.txt`. Runs on every OS.
- **Golden image:** the Skia renderer draws into a CPU raster surface (no GPU, no window) and the
  pixels are compared with `showcase-skia.png`: at most 0.2% of pixels may differ by more than 8
  levels per channel. Fonts differ between systems, so this part runs on 64-bit Windows only.
  Changing the button blue by 9 levels (`0xFF2D6CDF` to `0xFF2D6CE8`) fails the test with
  11,911 different pixels (6.9%).

A missing snapshot is written and the test fails once so a person reviews it; a mismatch writes
the new version to `target/snapshots`.

`JXInteractionTest` is the counterpart of OpenJFX's `StageLoader` + `MouseEventFirer` tests:
hit-test a point, dispatch the click, re-render, reconcile. It found that **no button of the
native UI reacted to clicks**: `JXButton` and `JXControls.button` store the handler as
`onAction`, and `JXNativeNode.dispatchPointer` read only `onClick`. It also found that disabled
buttons would have received clicks, which JavaFX does not deliver. Both fixed.

NanoVG (32-bit) needs an OpenGL context, so it has no golden image yet.

OpenJFX checks pixels in robot tests (for example `RectangleTest`, `StageRobotTest`) by sampling
colours at chosen points; it has no golden image files.

## 4. Performance regression gate

`ReconcileBenchmark` (1,001-node screen) and `ObservableBenchmark` in jxparallel-benchmarks.
CI runs them for a short time and `scripts/check-jmh.py` checks ratios between benchmarks of the
same run, which do not depend on how fast the runner is:

| Ratio | Measured | Floor |
|---|---:|---:|
| mount from scratch / reconcile after one label changed | 57.2x | 10x |
| mount from scratch / reconcile with nothing changed | 23,978x | 1000x |

Removing the identity shortcut in reconcile (a real regression) drops the ratios to 6.0x and
4.7x and fails the gate.

OpenJFX has no JMH benchmarks; its `tests/performance` folder holds apps that print frame rates.
The JDK itself keeps JMH benchmarks in `test/micro` but does not gate CI on them.

## 5. Contract tests

- **Architecture (ArchUnit):** core and UI use no AWT, Swing or JavaFX; core uses no LWJGL or
  Skija; only `native2d` touches GLFW, Skia and NanoVG; no package cycles. All pass.
- **Memory leaks** (OpenJFX's JMemoryBuddy pattern: drop the reference, run GC, check the
  `WeakReference`): a `JXProperty` bound to a long-lived source and then forgotten was never
  collected, because the source's listener held it. JavaFX bindings hold the target weakly. The
  binding listener is now a static class with a `WeakReference` that removes itself once the
  target is gone.
- **Coverage (JaCoCo,** `mvn -Pcoverage test`): jxparallel-core 74.2% of lines and 53.8% of
  branches; jxparallel-ui 52.2% and 42.6%. The OpenGL paths of `JXWindow` and both renderers'
  window setup do not run without a GPU.
- **Mutation testing (PIT 1.15.8,** `-Pmutation`): PIT changes the code (flips a condition, removes a call, changes a constant) and checks that some test fails. First run: core 152 of 389 mutants killed (39.1%), `JXNativeNode` and `JXRenderMemo` 89 of 115 (77.4%). It showed that deleting the listener call in `JXObservableList.add` or `remove` broke no test, and that no test checked the value copied by `bind`. After adding tests for those and hand-computed layout answers: core 172 of 389 (44.2%), UI 103 of 115 (89.6%). The remaining core survivors are mostly in `AdaptiveWorkerPool` and `JXParallelConfig`. Property tests that compare two code paths cannot kill a mutant in code both paths share (the preferred-size formula); tests with known answers can.
- **API compatibility (japicmp):** skipped until 0.1.0 is released, since there is no earlier
  version to compare with.
