# JXParallel FAQ

## What is the goal of JXParallel?

JXParallel is an independent Java2D/AWT UI runtime with a JavaFX-inspired developer experience,
lower overhead, safer parallelism, and bounded resource management.

## Does JXParallel replace JavaFX?

The native runtime does not depend on JavaFX. An optional legacy JavaFX profile remains available
for migration and comparison, but it is not part of the default product.

## Do I need to learn a new API?

The goal is to keep the same concepts, names, and behaviors whenever possible. Developers familiar with JavaFX should be able to adapt with minimal friction.

## Can I still use standard JavaFX controls?

Yes, through the optional `legacy-javafx` profile. Native controls do not wrap JavaFX controls.

## What is the current maturity level?

The project is a pre-1.0 release. The core runtime and native UI foundation are tested, while
advanced platform accessibility, CSS, and physical-display UI coverage are still evolving.

## Why are there multiple modes?

Modes exist to let teams trade compatibility, overhead, and throughput deliberately rather than forcing a single runtime profile for all applications.
