# Native UI implementation progress

The native UI path is JavaFX-free and uses Skia through Skija. AWT is retained only as the
window and input host.

## Implemented

- retained native node tree with bounds and children
- row, column, and stack composition
- Skia layout, preferred size, and painting
- dirty layout state and preferred-size caching to avoid repeated full calculations
- native AWT window and input lifecycle
- pointer hit testing and click dispatch
- keyboard dispatch to the focused native node
- native button, label, text field, text area, password field, checkbox, toggle button,
  combo box, progress bar, and slider state models
- text document with caret, selection, insert/delete, select-all, copy, cut, and paste
- accessibility tree with roles, names, descendants, and deterministic flattening
- native style tokens and clean theme
- type, class, and id style-sheet rules with deterministic merge order
- deterministic animation frames and an optional scheduled animation loop
- bounded LRU resource byte cache
- resource cache limits by entry count and total bytes with hit/miss/eviction counters
- one shared animation scheduler thread for all animation scheduler instances
- scalability regression coverage for 10,000 sibling nodes and concurrent cache access
- independent XML markup loader with external-entity protection

## Current limitations

- platform screen-reader adapters are not implemented
- advanced CSS combinators, pseudo-classes, and media queries are not implemented
- native text widgets still need to connect every keyboard command to the document model
- XML markup currently describes elements and scalar attributes; event handlers must be wired in
  Java code
- headless CI validates the model and renderer contracts, not physical pixels or OS input

## Scalability guarantees

The native UI does not create one thread per animation scheduler. It uses a shared daemon timing
executor and each scheduler owns only its animation list and cancellation handle.

Layout keeps the last bounds and preferred size for a node. Repeating a render with unchanged
dimensions does not recalculate that subtree's layout. A future mutable scene API must call
`JXNativeNode.invalidateLayout()` after changing geometry-affecting state.

Resource caching is bounded both by entry count and byte size:

```java
JXResourceCache cache = new JXResourceCache(1000, 16 * 1024 * 1024);
```

The cache exposes `getHits()`, `getMisses()`, `getEvictions()`, `size()`, and
`getCurrentBytes()` for operational monitoring.

## Example markup

```xml
<column gap="8">
    <button label="Save"/>
    <input value="Customer"/>
</column>
```

```java
JXMarkupLoader loader = new JXMarkupLoader();
JXElement screen = loader.load(stream);

JXWindow window = new JXWindow("Customers");
window.setContent(screen);
window.show();
```

The loader is intentionally independent of FXML and JavaFX. It produces JXParallel elements,
which are then mounted by `JXSkiaRenderer`.

## Text editing

```java
JXTextDocument document = new JXTextDocument("customer");
document.setCaret(8, false);
document.insert(" profile");
document.selectAll();
document.copy(clipboard);
```

The document is thread-safe for individual operations and keeps selection/caret state separate
from rendering.

## Accessibility

```java
JXAccessibleNode tree = JXAccessibilityTree.from(screen);
List<JXAccessibleNode> ordered = tree.flatten();
```

The tree exposes stable roles and accessible names to future platform adapters.

## Styling and animation

```java
JXStyleSheet sheet = new JXStyleSheet()
    .add("button", JXStyle.builder().set("radius", 8).build())
    .add("#save", JXStyle.builder().set("primary", true).build());

JXAnimationScheduler scheduler = new JXAnimationScheduler();
scheduler.add(new JXAnimation(200, progress -> updateOpacity(progress)));
scheduler.tick(16);
```
