# Native UI architecture

## Direction

JXParallel is being refactored into an independent UI framework. JavaFX is not the runtime
foundation of the native product.

The native path is:

```text
JXParallel Core
    -> JXParallel Native UI
        -> Java2D/AWT window and renderer
```

JavaFX is kept outside this path as a legacy compatibility module while the controls are migrated.
The default Maven reactor excludes that module and does not resolve OpenJFX.
New native code must not import `javafx.*`.

## Current native foundation

The `jxparallel-ui` module currently provides:

- `JXNativeNode`: independent scene node with properties and children
- `JX2DRenderer`: mount, layout, preferred-size, and Java2D painting operations
- `JXWindow`: AWT window lifecycle for native JXParallel content
- native controls:
  - `JXButton`
  - `JXLabel`
  - `JXTextField`
- native layout:
  - `JXPane`
- support for the initial declarative types:
  - `row`
  - `column`
  - `stack`
  - `button`
  - `input`
  - `#text`

Example:

```java
JXPane screen = new JXPane(12);
screen.add(new JXLabel("Customers"));
screen.add(new JXButton("Load"));

JXWindow window = new JXWindow("JXParallel");
window.setContent(screen.render());
window.show();
```

`JXButton.fire()` and the `JXButton` action property are native state operations. Window-level
mouse and keyboard routing is still being implemented; the current AWT window is a renderer
host, not yet a complete input toolkit.

## Migration rules

1. New controls belong in `jxparallel-ui`, not `jxparallel-javafx`.
2. Native scene nodes must not expose JavaFX `Node` instances.
3. Rendering, layout, input, focus, styling, and accessibility must have native contracts.
4. JavaFX comparisons are compatibility tests, not implementation dependencies.
5. A feature is not marked complete until it has native behavior, tests, and documentation.

## Remaining work

The native foundation is intentionally small. The following areas still require implementation:

- retained scene graph lifecycle and invalidation
- mouse, keyboard, focus, and text input dispatch
- independent control state and selection models
- complete layout family
- CSS/theme engine
- accessibility abstraction
- animation/timing system
- resource and image loading
- FXML-compatible or independent declarative loader
- automated native window and rendering tests

Until those areas are implemented, the project must not claim full replacement compatibility with
JavaFX. The legacy adapter can still be built explicitly with `-Plegacy-javafx`, but it is not
part of the native product.
