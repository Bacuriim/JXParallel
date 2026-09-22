# Modern JavaFX visual layer

The JXParallel controls preserve the JavaFX programming model and add optional visual behavior:

```java
JXButton save = new JXButton("Save");
save.setVariant(JXVisualVariant.PRIMARY);
save.setDensity(JXVisualDensity.COMPACT);
save.setOnAction(event -> save());
```

The JavaFX-style methods remain the primary API:

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

Additional visual features are intentionally opt-in or lightweight defaults:

- variants: `DEFAULT`, `PRIMARY`, `SECONDARY`, `GHOST`, `DANGER`, `SUCCESS`
- density: `COMPACT`, `COMFORTABLE`, `SPACIOUS`
- rounded modern surfaces
- transparent focus decoration
- hover scale transition
- fade-in appearance animation

The styling helper uses JavaFX controls and CSS properties rather than replacing the JavaFX
control contract. This keeps migration straightforward and allows applications to override the
style with regular JavaFX CSS or `getStyleClass()`.
