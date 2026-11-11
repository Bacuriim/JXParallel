# FXML to JXML: a JavaFX screen on the native UI

`FxmlToJxml` (in `jxparallel-forge`, no JavaFX dependency) reads an existing `.fxml` file and produces the JXML
that [JXForge](jxforge.md) reads, so the same interpreter and compiler run it. What it cannot map is not dropped
silently: every such case is an entry of a report with the file, line, element, attribute and the reason.

```text
screen.fxml ──FxmlToJxml──► JXML tree (and .jxml text) ──► JxmlTemplate (interpreter) / JxmlCompiler ──► JXElement
                  └──► FxmlReport: file:line: LEVEL <Element> attribute: reason
```

It converts the *screen*: structure, layout, text, bindings and handlers. It does not convert the *controller*. Read
[The fx:id limit](#the-fxid-limit) before you start: that is where the work of a migration is.

## Running it

**Maven** (the goal `fxml` converts, the goal `generate` compiles what it wrote; keep the order in the POM):

```xml
<plugin>
    <groupId>io.github.bacuriim</groupId>
    <artifactId>jxforge-maven-plugin</artifactId>
    <version>${jxparallel.version}</version>
    <executions>
        <execution>
            <id>fxml</id>
            <goals><goal>fxml</goal></goals>
            <configuration>
                <strict>false</strict>
                <bundle>src/main/resources/messages.properties</bundle>
                <itemTypes><orders>com.acme.Order</orders></itemTypes>
            </configuration>
        </execution>
        <execution>
            <goals><goal>generate</goal></goals>
            <configuration><packageName>com.acme.views</packageName></configuration>
        </execution>
    </executions>
</plugin>
```

| `fxml` parameter | Default | |
|---|---|---|
| `sourceDirectory` | `src/main/resources` | every `.fxml` below it |
| `outputDirectory` | `target/generated-jxml` | the `.jxml` files, at the same relative paths; `generate` reads this folder too (`fxmlJxmlDirectory`) |
| `strict` | `false` | fail the build when anything is not converted (see [Report](#the-report)) |
| `bundle` | none | a `.properties` file that resolves the `%key` values |
| `itemTypes` | none | the row class of a table, by its `fx:id` (`*` for all) |
| `reportFile` | `target/jxforge-fxml-report.txt` | the report |

`FxmlToJxml.convertDirectory` also lists as a failure an `fx:include` whose file declares another `fx:controller`
than the including file: a JXML part uses the controller of the file that includes it.

**Command line** (same style as `JXForge`):

```powershell
java -cp jxparallel-forge.jar;jxparallel-core.jar com.jxparallel.forge.FxmlToJxml `
     --src src/main/resources --out target/generated-jxml [--strict] [--bundle messages.properties] `
     [--item-type orders=com.acme.Order] [--report report.txt]
```

`--src` is a folder or one `.fxml`. The report goes to the file of `--report`, else to the console; with `--strict` the
exit status is 1 when something is not converted. Then compile the output with `JXForge` as usual.

**At run time**, for the interpreter, with no text in between:

```java
FxmlToJxml.Options options = new FxmlToJxml.Options().bundle(bundle).itemType("orders", "com.acme.Order");
JxmlTemplate screen = JxmlTemplate.readFxml(stream, "screens/main.fxml", JxmlLoader.classpath(loader), options);
JXElement tree = screen.build(controller);
options.report();   // what was not converted
```

`JxmlTemplate.readFxml(stream, name)` is the short form (no includes, no options). An `fx:include` of `a.fxml` includes that
FXML, converted the same way. Errors of the template cite the line of the FXML.

## The fx:id limit

JavaFX loads the FXML, creates the nodes and **injects each node with an `fx:id` into the `@FXML` field** of the
controller; the controller then calls methods on it (`name.setText(...)`, `table.getItems().add(...)`,
`button.setDisable(true)`, `combo.getSelectionModel()`). The native UI has no node objects to inject: a screen is a
tree of `JXElement` values built from the controller's state, and the controller is read through getters.

The converter does not fake this. For every `fx:id` it:

- writes `key="<id>"` on the element (except on a `Tab`, and on `TableView` / `ListView`, whose `key` means something else),
  so the element can be told apart in the tree, and
- adds a `NOTE` to the report: `fx:id="name" is kept as key="name"; JavaFX injects this TextField into the controller field "name" ...`.

The list of notes is the list of places where controller code has to change: state that the code pushed into a node
(`label.setText(x)`) becomes a getter that the screen binds to (`text="${message}"`), and state read from a node
(`field.getText()`) becomes a property bound two ways (`text="@{name}"`, which needs the form `@{...}` written by hand;
the converter writes `${name}` because FXML cannot say it). A table or list is bound to a getter named after its `fx:id`
(`items="${orders}"` calls `getOrders()`), which replaces `table.setItems(...)`. A `ToggleGroup` becomes a getter
(`getKind()`, a `String`) that returns the `value` of the chosen button. Handlers must be public methods with no
arguments (`onAction="#save"` calls `controller.save()`): a note lists each handler name. An expression such as
`${nameField.text}` that reads a property of another node is reported as not converted for the same reason.

Nothing here is checked against a controller: the generated Java class fails to compile where the controller lacks a
getter or a handler, and the interpreter fails when the screen is built. That is the migration to-do list, with file and line.

## The report

`FxmlReport` has one `Entry` per finding: `level`, `file`, `line`, `element`, `attribute` (or null) and `reason`. In text:
`orders.fxml:14: UNSUPPORTED <Button> onKeyPressed: onKeyPressed has no JXML equivalent ...`.

| Level | Means | Strict mode |
|---|---|---|
| `NOTE` | converted; the developer has something to do (`fx:id`, a handler's signature, where the rows of a table come from) | does not fail |
| `APPROXIMATE` | converted to something close, not the same (a CSS class kept as an empty `<Style>`, `visible="${x}"` as an `<If>`, `onAction` of a `ComboBox` as `onChange`, grid column widths) | fails |
| `UNSUPPORTED` | not in the output | fails |

Strict mode (`Options.strict(true)`, `--strict`, `<strict>`) throws `FxmlConversionException` (its `getReport()` has every
entry; a directory run converts all files first). Not strict, the conversion always produces output, with the unsupported
parts left out.

A few attributes are ignored without an entry because they have no effect on a screen with no mnemonics and no
pick-on-bounds: `mnemonicParsing`, `pickOnBounds`; the default sizes (`-1`, `USE_COMPUTED_SIZE`, an unbounded
`maxWidth`); `layoutX` / `layoutY` in a parent that ignores them in JavaFX too (a `VBox`, a `GridPane`...).

## What is converted

The table is what `FxmlToJxmlTest` asserts (the *Test* column names the fixture).

| FXML | JXML | Test |
|---|---|---|
| `VBox`, `HBox` | `Column`, `Row` (`spacing` to `gap`, `alignment`, `padding`, `fillWidth` / `fillHeight`) | form, panes |
| `StackPane`, `Pane`, `Region` | `Stack`, `Pane`, `Pane` (a `Region` is an empty pane: spacers) | panes |
| `BorderPane` | `Border`; `<top>`, `<left>`, `<center>`, `<right>`, `<bottom>` and a direct child become `slot="..."` | form |
| `GridPane` | `Grid` (`hgap`, `vgap`); `GridPane.rowIndex`, `columnIndex`, `rowSpan`, `columnSpan` become `row`, `column`...; `hgrow`, `vgrow`, `halignment`, `valignment` of the `columnConstraints` / `rowConstraints` are copied to the cells of that column / row | form |
| `AnchorPane` | `Anchor`; `AnchorPane.topAnchor`... become `top`, `right`, `bottom`, `left`; `layoutX/Y` of a child without anchors | panes |
| `FlowPane`, `TilePane` | `Flow`, `Tile` and their attributes | panes |
| `ScrollPane`, `SplitPane`, `Accordion`, `TitledPane`, `ToolBar`, `ButtonBar` | `Scroll`, `SplitPane`, `Accordion`, `TitledPane`, `ToolBar`, `ButtonBar` | panes, form |
| `TabPane` / `Tab` | `Tabs` / `Tab` (`text` to `title`, `closable`; `tabClosingPolicy` `UNAVAILABLE` / `ALL_TABS` to each tab's `closable`) | panes, screen01 |
| `Label`, `Text`, `Button` (+ `<graphic>`), `Hyperlink`, `CheckBox`, `ToggleButton`, `RadioButton` | the same tags | form, panes, small features |
| `ToggleGroup` in `fx:define` + `toggleGroup="$g"` | `group="${g}"` and `value` (the `userData`, else the `text`) | form |
| `TextField`, `PasswordField`, `TextArea`, `Spinner` (also `<valueFactory>`), `Slider`, `DatePicker`, `ColorPicker`, `ProgressBar`, `ProgressIndicator`, `Separator`, `ScrollBar` | the same tags (`promptText` to `prompt`, `prefColumnCount` to `columns`, `initialValue` to `value`...) | form, panes, small features |
| `ComboBox`, `ChoiceBox` | `ComboBox`, `ChoiceBox`; static `<FXCollections>` items become `items="A, B"` (a comma in an item is reported) | form, panes |
| `ListView` | `ListView items="${id}" var="item"` with a `ListItem text="${item}"` | not tested (see below) |
| `TableView` / `TableColumn` | `TableView items="${id}" var="row" type="..."` with `columns`, `columnWidths` (80 when `prefWidth` is missing, as JavaFX), `constrained` for `CONSTRAINED_RESIZE_POLICY`, and a `TableRow` of `TableCell text="${row.property}"` from each `PropertyValueFactory`; the row type is `fx:typeArguments` or `Options.itemType` | table |
| `TreeView` / `TreeItem` | `Tree` / `TreeItem` (`value` to `text`, `expanded`); `showRoot="false"` lifts the children of the root | panes |
| `MenuBar`, `Menu`, `MenuItem`, `SeparatorMenuItem`, `MenuButton` | the same tags (`MenuSeparator` for a separator); `CheckMenuItem` / `RadioMenuItem` as a plain `MenuItem` (`APPROXIMATE`) | form, small features |
| `ImageView` with `<Image url="@x.png"/>` | `Image src="x.png"` (`@/p/x.png` to `classpath:p/x.png`); `fitWidth`, `fitHeight`, `preserveRatio` | panes |
| `<tooltip><Tooltip text/></tooltip>` | `tooltip="..."` | small features |
| `text`, `promptText`, `title`..., `prefWidth` ... `maxHeight`, `opacity`, `padding` (`<Insets>` or `topRightBottomLeft`), `VBox.margin` etc., `VBox.vgrow` / `HBox.hgrow` (`ALWAYS`, `SOMETIMES`), `StackPane.alignment` / `BorderPane.alignment` (to `halignment` + `valignment`) | the same-named attribute; `USE_PREF_SIZE` on a min or max becomes the preferred size | form, panes, small features |
| `disable` | `disabled` (a tag without it: `UNSUPPORTED`) | form |
| `visible="false"` / `visible="${x}"` | `hidden="true"` / `<If test="${x}">` around the element (`APPROXIMATE`: removed, not hidden) | form |
| `<font><Font name size/></font>`, `textFill` (`#rgb`, `0x`, `rgb()`, a basic name, `<Color>`) on `Label` / `Button` | `fontSize`, `bold`, `textFill` | form, small features |
| `style="-fx-..."` | `background`, `textFill`, `fontSize`, `bold`, `borderColor`, `borderWidth`, `borderRadius`, `backgroundRadius`, `opacity`, `padding`, `alignment`, `gap`, sizes; an attribute written on the element wins; each other property is `UNSUPPORTED` | form, small features |
| `styleClass="a b"` | `style="a b"` and an empty `<Style name="a"/>` for each class (`APPROXIMATE`: the CSS rules are not converted) | form |
| `fx:controller` | `controller` | form |
| `onAction="#save"` on a `Button`, `MenuItem`, `Hyperlink`, `ToggleButton`, `RadioButton`, `CheckBox` | `onAction="#save"` (`NOTE`: no-argument method) | form |
| `onAction` on `ComboBox`, `ChoiceBox`, `DatePicker`, `ColorPicker` | `onChange` (`APPROXIMATE`: gets the new value) | small features |
| `onMouseClicked` | `onAction` (`APPROXIMATE`) | small features |
| `text="${controller.name}"`, `${model.x}` | `${name}`, `${model.x}`: a plain property path, read from the controller's getters; `controller.` is dropped | form |
| `%key`, `\%` | the text from `Options.bundle`; without one the key stays as the text and is `UNSUPPORTED` (JXML has no i18n lookup); `\%x` is the literal `%x` | unsupported fixture |
| `fx:include source="a.fxml"` | `<Include src="a.jxml"/>` (`a.fxml` when read at run time); placement attributes go to the part | panes |
| `fx:root` | its `type` converted as a plain pane (`NOTE`) | not tested |
| `fx:id` | `key` plus a `NOTE` (see above) | form |

Fixtures: `customer-form.fxml` and `orders-table.fxml` (Scene Builder style), `panes.fxml`, `unsupported.fxml`, and the
`screen01.fxml` of `jxparallel-examples/fxmlbench`, `Outer`, `Inner`, `InnerWithId` and `Handlers` of `jxparallel-fx`.
The form and the table are compiled by `JxmlCompiler` and their trees compared with the interpreter's, with and without hoisting
(`JxmlTrees.diff`); every produced text validates against the generated XSD (`JxmlXsd`).

## What is not converted

Each of these is an `UNSUPPORTED` (or `APPROXIMATE`) entry where it appears; the *Test* column says whether a test asserts it.

| FXML | Why | Test |
|---|---|---|
| Custom classes (`<com.acme.Gauge>`), `Canvas`, `Group`, `WebView`, charts, `Shape`s, `Pagination`, `HTMLEditor`... | no JXML tag; the element and its children are left out | Gauge, Canvas |
| `fx:script`, `fx:copy`, `fx:reference` (other than `toggleGroup`), `fx:define` of anything but a `ToggleGroup`, a nested `fx:controller`, `fx:factory` / `fx:value` / `fx:constant` (except the cases above) | not part of the model | partly (`fx:define` and nested controllers through `Outer`) |
| Expressions that are not a plain path (`${a + b}`, `${!x}`, `${node.text}` of another node) | JXML reads controller getters only | yes |
| Handlers other than click and value change (`onKeyPressed`, `onMouseEntered`, `onDragDetected`...) | the renderer reports clicks and value changes | `onKeyPressed` |
| `onAction` of a `TextField` (fires on Enter) | JXML `onAction` is a click; use `onChange` | yes |
| `managed`; `visible` on a `Tab`, `Menu`, `MenuItem`, `TreeItem` | no layout-only visibility | `managed` |
| CSS: stylesheets, `id`, selectors, most `-fx-` properties (`-fx-effect`, gradients, multiple backgrounds, fonts) | JXML has `<Style>` attributes, not CSS | `-fx-effect` |
| Column and row sizes of a `GridPane` (`prefWidth`, `percentWidth`, `minWidth`, `fillWidth`...) | `Grid` has no track definitions; only `hgrow` / `vgrow` / alignment are copied (`APPROXIMATE`) | yes |
| Static `items` of a `TableView` / `ListView`, `cellFactory`, `selectionModel`, `placeholder`, editing | the rows come from a binding | not tested |
| `TextField.editable`, `focusTraversable`, `cursor`, `effect`, `rotate`, `accelerator` of menu items, `mnemonic`s, `Tab.graphic`, `Label.graphic`, `Label.alignment` | no equivalent | `editable` |
| `ButtonBar.buttonData`, `SplitPane.resizableWithParent` | no equivalent | `buttonData` |
| Pictures from `http:` URLs, `Image.requestedWidth` | `src` reads local picture files | not tested |
| A table whose row type is unknown | the `${row.x}` bindings need `type`; give `fx:typeArguments`, `Options.itemType` or `--item-type` | yes |
| The `fx:id` injection | [see above](#the-fxid-limit) | form |

## What this has not been tried on

The converter was written against the FXML files in this repository (the Scene Builder style form and table, the
benchmark screens of `jxparallel-examples`, and the fixtures of `jxparallel-fx`). It has not been run on the screens of
a real application, `ListView` has no test of its own, the Maven goals were run only through a unit test of the mojo (not in
a real project build), and the converted screens were not rendered in a window. The `GridPane` column widths, CSS and
custom controls of a real application are where it is most likely to report more than the fixtures do.
