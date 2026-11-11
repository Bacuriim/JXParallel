# Modern visual layer of the JavaFX bridge

This page is about the controls of `jxparallel-javafx` (profile `legacy-javafx`), which wrap real
JavaFX controls. It does not apply to the native UI: that draws its own Material-style controls with
the theme tokens described in [native-ui.md](native-ui.md), and JXML styles are in
[jxforge.md](jxforge.md).

The wrappers keep the JavaFX programming model and add optional visual behavior:

```java
JXButton save = new JXButton("Save");
save.setVariant(JXVisualVariant.PRIMARY);
save.setDensity(JXVisualDensity.COMPACT);
save.setOnAction(event -> save());
```

The JavaFX-style methods remain the primary API; the underlying control is `node()`:

- `getText` / `setText`
- `textProperty`
- `setDisable` / `disabledProperty`
- `setOnAction` / `getOnAction`
- `getItems`
- `getSelectionModel`
- `setPromptText`
- `getStyleClass`
- `setTooltip`
- `setVisible` / `isVisible`
- `setManaged` / `isManaged`
- `setPrefWidth` / `setPrefHeight`

What `JXModernStyle` adds to a control:

- the style classes `jx-modern-control`, `jx-variant-<name>` and `jx-density-<name>`. The variants are
  `DEFAULT`, `PRIMARY`, `SECONDARY`, `GHOST`, `DANGER` and `SUCCESS`; the densities are `COMPACT`,
  `COMFORTABLE` and `SPACIOUS`. No stylesheet ships with the module, so the variant classes change
  nothing visible until the application styles them with regular JavaFX CSS;
- an inline style with a background and border radius of 8, a padding by density (5, 8 or 11
  pixels) and a transparent focus ring, unless the control already sets a background radius;
- `animateHover`, a scale to 1.015 on mouse enter (120 ms) and back on exit (140 ms), applied by
  `JXButton`;
- `animateAppear`, a 180 ms fade-in, available as `JXButton.animateAppear()`.

The helper works through JavaFX controls and CSS properties and does not replace the JavaFX control
contract, so migration stays straightforward and an application can still override the style with
`getStyleClass()` and its own CSS.
