# UI performance comparison — 2026-09-22

This report compares the same visible component structure in two separate processes:

- **JavaFX:** `TextField`, `Button`, `Label`, `VBox`, JavaFX Application Thread.
- **JXParallel native:** `JXTextField`, `JXButton`, `JXLabel`, `JXPane`, Skia through Skija
  0.116.4, hosted by AWT.

Both applications perform the same automatic interaction after the first rendered frame and
execute a 350 ms background operation. The runs used Java 21.0.8 x64, OpenJFX 21.0.2 on
Windows, the same display session, and five repetitions per implementation.

## Median results

| Metric | JavaFX | JXParallel native | Interpretation |
|---|---:|---:|---|
| Startup to first paint | 312.404 ms | 396.723 ms | JXParallel +27.0% |
| Interaction completion | 353.486 ms | 364.305 ms | Both include the same 350 ms task |
| Process CPU time | 750.000 ms | 468.750 ms | JXParallel used less process CPU in this run |
| Normalized CPU | 3.261% | 2.309% | Lower average process CPU share |
| Peak working set | 10.01 MB | 10.04 MB | Effectively equal in this sample |
| Peak private memory | 1.74 MB | 1.96 MB | Skija native overhead is visible |
| Java heap delta | 7.19 MB | 10.15 MB | Skija initialization increased heap delta |

The raw data is in `ui-metrics-2026-09-22.csv`.

## Correct interpretation

The Skija renderer used less process CPU but was slower to first paint and showed a larger Java
heap delta in this small cold-process workload. Its interaction latency is intentionally different
in this harness because the native application waits for the same 350 ms background task through
`JXParallel`; the JavaFX case reports the button action completion through its worker callback.
This is a workload result, not proof that either toolkit is universally faster.

Working set and private bytes were sampled externally with `Get-Process`, while heap delta and
process CPU were reported by the JVM. External memory is the relevant number for total application
RAM; heap delta alone must not be used as a RAM comparison.

The JavaFX process reported two additional threads relative to its baseline; the native Skija
process reported six. This reflects the different toolkit, native library, and scheduler
lifecycle, not necessarily a problem by itself.

The sample is valid for this machine and configuration only. For release claims, repeat it with
multiple JVM versions, display configurations, cold and warm launches, longer workloads, and
larger component trees. Summaries should use medians and percentiles rather than one run.
