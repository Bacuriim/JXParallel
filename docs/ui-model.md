# Independent UI model

JXParallel now has a minimal scene-graph model inspired by the useful parts of React and Vue:

- immutable `JXElement` values represent the declarative tree
- `JXComponent` provides a small render contract
- `JXState` provides explicit reactive state
- `JXNode` is the mutable mounted tree
- `JXRenderer` mounts and patches the tree
- `JXLayouts` provides row, column, and stack primitives
- `JXControls` provides button, input, checkbox, and select elements
- `JXTheme` provides immutable design tokens

This model is intentionally independent of JavaFX. It does not yet render pixels or implement
CSS, focus, keyboard, accessibility, animation, or platform input. Those responsibilities belong
to a later host adapter. The independent model must remain deterministic and testable before a
JavaFX or other platform renderer is added.

## Example

```java
JXState<String> name = new JXState<String>("");

JXComponent form = () -> JXLayouts.column(16,
    JXControls.input(name.get(), "Your name"),
    JXControls.button("Continue", () -> name.set("submitted"))
);

JXNode root = JXRenderer.mount(form);
```

The model favors explicit state and immutable element descriptions over hidden lifecycle magic.
That keeps the core compatible with Java 8 and makes later render adapters replaceable.
