# JXParallel compatibility model

The native JXParallel UI is not implemented by wrapping JavaFX. JavaFX support is an optional
legacy compatibility module used only for migration and comparison. The default Maven reactor
does not include or resolve OpenJFX.

## Compatibility levels

### FULL
- API and behavior match JavaFX closely enough for direct migration
- expected lifecycle and event semantics are preserved

### PARTIAL
- most APIs are available, but some behavior differs under edge cases
- differences are explicitly documented

### EXPERIMENTAL
- early-stage implementation or feature gated
- not recommended for production without validation

### UNSUPPORTED
- feature depends on JavaFX internals or platform-specific behavior that cannot be safely reproduced

## Current scope

### Core runtime
- FULL: scheduler lifecycle, task submission, cancellation tokens, metrics

### Native UI
- EXPERIMENTAL: Skia scene foundation through Skija, native nodes, initial layouts, and window lifecycle

### Legacy JavaFX integration
- PARTIAL: optional adapter retained for migration and comparison

### FXML support
- EXPERIMENTAL: loader and configurable source cache (`NONE`, `LRU`, `TTL`, `LRU_TTL`)

### Controls
- PARTIAL: first bridge controls expose JavaFX-like state and `node()`

### Layouts and virtualized controls
- PARTIAL: `JXPane`, `JXHBox`, and `JXVBox` delegate to JavaFX layout nodes
- PARTIAL: `JXGridPane`, `JXBorderPane`, `JXStackPane`, and `JXAnchorPane` delegate to JavaFX layout nodes
- PARTIAL: `JXListView` delegates to JavaFX `ListView`, including its native cell virtualization

The legacy JavaFX module intentionally reuses JavaFX's scene-graph behavior. It is not part of
the native UI runtime and does not define the long-term architecture.

The visual layer is intentionally additive: JavaFX method names remain the primary contract,
while modern variants, density, hover transitions, and appearance animation are optional
enhancements. Applications can continue using regular JavaFX CSS and style classes.

## Principle

We prefer behavioral compatibility over micro-optimization. If a performance optimization changes observable behavior, it must be opt-in and tested.

## Build matrix status

The core runtime remains Java 8 compatible. The native UI uses Skija 0.116.4, whose current
artifacts target Java 11, so native UI applications require Java 11 or newer. The optional
JavaFX profile has its own JDK requirements.
## Current control status

The first control classes are compatibility bridges, not replacements for the JavaFX scene graph:

- `JXLabel`: PARTIAL
- `JXButton`: PARTIAL
- `JXTextField`: PARTIAL
- `JXTextArea`: PARTIAL
- `JXCheckBox`: PARTIAL
- `JXComboBox`: PARTIAL
- `JXPasswordField`: PARTIAL
- `JXProgressBar`: PARTIAL
- `JXProgressIndicator`: PARTIAL
- `JXRadioButton`: PARTIAL
- `JXSlider`: PARTIAL
- `JXTabPane`: PARTIAL
- `JXToggleButton`: PARTIAL
- `JXScrollPane`: PARTIAL
- `JXSeparator`: PARTIAL

### Test integrations
- PARTIAL: JUnit 4 rule and JUnit 5 extension manage runtime lifecycle
- PARTIAL: Mockito adapter provides explicit mock reset support
- EXPERIMENTAL: PowerMock adapter; instrumentation compatibility is not claimed universally

They preserve common JavaFX-style methods and expose the underlying JavaFX `Node` through
`node()`. This is intentionally explicit. A true lightweight scene-graph implementation
requires independent layout, CSS, input, accessibility, and rendering work, and will only be
promoted after compatibility tests and benchmarks demonstrate equivalent behavior.
