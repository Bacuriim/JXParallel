# JXForge: native screens in JXML, compiled to Java

JXForge lets a native screen be written as markup, the way FXML describes a JavaFX screen, and
then turns that markup into plain Java when the project builds, the way `protoc` turns a `.proto`
file into classes. At runtime there is no XML parser and no reflection: the generated class calls
the controller directly and builds the `JXElement` tree the native renderer draws.

The same files can also be read at runtime by an interpreter that follows exactly the same rules,
which is useful in development and for screens that only exist at runtime.

```text
.jxml ──(JXForge, at build time)──► Java class ──► JXElement ──► reconcile ──► Skia / NanoVG / software
                                        ▲
                Java widgets written by hand go straight here
```

Modules: `jxparallel-forge` (schema, interpreter, compiler, command line) and
`jxforge-maven-plugin` (goal `jxforge:generate`). Both run on Java 8 and later; the generated
code is Java 8 source, so one build serves every JVM from 8 to the newest.

## A screen

`src/main/jxml/customers.jxml`:

```xml
<Border controller="com.acme.CustomersController">
  <Row slot="top" gap="4" padding="4 8">
    <Button text="Save" onAction="#save" defaultButton="true"/>
    <Button text="Delete" onAction="#delete" disabled="${readOnly}"/>
  </Row>
  <Tabs slot="center">
    <Tab title="Details" closable="false">
      <Grid hgap="8" vgap="6" padding="12">
        <Label text="Name" row="0" column="0"/>
        <TextField text="${customer.name}" prompt="Full name" row="0" column="1" hgrow="always"/>
        <Label text="City" row="1" column="0"/>
        <ComboBox items="${cities}" value="${customer.city()}" row="1" column="1"/>
      </Grid>
    </Tab>
    <Tab title="Orders">
      <Slot content="${ordersTable}"/>
    </Tab>
  </Tabs>
</Border>
```

The build generates `CustomersView`; the application uses it like any class:

```java
JXElement screen = new CustomersView().build(controller);
window.setContent(screen);
```

## Setup

```xml
<dependency>
    <groupId>com.jxparallel</groupId>
    <artifactId>jxparallel-forge</artifactId>
    <version>${jxparallel.version}</version>
</dependency>
...
<plugin>
    <groupId>com.jxparallel</groupId>
    <artifactId>jxforge-maven-plugin</artifactId>
    <version>${jxparallel.version}</version>
    <executions>
        <execution>
            <goals><goal>generate</goal></goals>
            <configuration>
                <packageName>com.acme.views</packageName>
            </configuration>
        </execution>
    </executions>
</plugin>
```

| Parameter | Default | |
|---|---|---|
| `packageName` | (required) | package of the generated classes |
| `sourceDirectory` | `src/main/jxml` | where the `.jxml` files are, in any subfolders |
| `outputDirectory` | `target/generated-sources/jxforge` | added to the compile sources |
| `classSuffix` | `View` | `order-list.jxml` becomes `OrderListView` |
| `hoistConstants` | `true` | build subtrees without bindings once, when the class loads |

Maven only runs a plugin from the repository, so inside this repository's own build (where the
plugin is built in the same reactor) `jxparallel-examples-native` calls the command line below
through `exec-maven-plugin`; its profile `jxforge-plugin` uses the plugin after `mvn install`.

Without Maven, the same compiler runs from the command line:

```powershell
java -cp jxparallel-forge.jar;jxparallel-core.jar com.jxparallel.forge.JXForge `
     --src src/main/jxml --out target/generated-sources/jxforge --package com.acme.views
```

Files whose generated class is newer than the `.jxml` are skipped.

## Rules

| Written | Means |
|---|---|
| `gap="8"` | a literal, converted once to the type the renderer reads (`Double` here) |
| `background="#336699"` | a color literal, the ARGB `Integer` the renderer paints (`#RGB`, `#RRGGBB` opaque, `#AARRGGBB`) |
| `text="${status}"` | `controller.getStatus()` |
| `text="${customer.name}"` | `controller.getCustomer().getName()` |
| `value="${customer.city()}"` | `controller.getCustomer().city()`: record components (Java 16+) and any accessor |
| `onAction="#save"` | `controller.save()`, a public method without arguments |
| `text="\#1"` | a literal starting with `#` or `$` |
| `<Slot content="${header}"/>` | a `JXElement` or `JXComponent` built in Java, placed here |
| `<For items="${orders}" var="o" type="com.acme.Order">` | the children once per item; `${o.name}` is `o.getName()` |
| `onAction="#open(o)"` | `controller.open(o)`, with loop variables as arguments |
| `<If test="${admin}">` / `${!admin}` / `<Else>` | the children when the boolean is true (false), else the `<Else>` ones |
| `onChange="#setName"` | `controller.setName(value)` when the user edits the input |
| `text="@{customer.name}"` | reads `getCustomer().getName()`; on change calls `getCustomer().setName(value)` |

The compiler writes these as direct calls (`c.getStatus()`, `c::save`), so a misspelled name is a
`javac` error. Every generated line ends with the `.jxml` file and line it comes from:

```java
.set("onAction", (Runnable) c::sav) // customers.jxml:4
```

The interpreter (`JxmlTemplate.read(...).build(controller)`) applies the same rules by
reflection and reports the same mistakes with the file and line when the screen is built.
`JxmlTrees.diff` compares two trees; the tests use it to check that both ways agree on every
screen.

## Tags

Every tag also accepts the layout attributes `key`, `margin`, `hgrow`, `vgrow`, `minWidth`,
`prefWidth`, `maxWidth`, `minHeight`, `prefHeight`, `maxHeight`, `row`, `column`, `rowSpan`,
`columnSpan`, `halignment`, `valignment` and `slot` (`top`, `left`, `center`, `right`, `bottom`
inside a `Border`), and these, which the renderer reads from any node:

| Attribute | Type | Means |
|---|---|---|
| `top`, `right`, `bottom`, `left` | number | the distance to that edge of an `Anchor` parent |
| `x`, `y` | number | the position in a `Pane` parent |
| `opacity` | number 0..1 | how opaque the node and everything below it is painted |
| `hidden` | `true` / `false` | not painted and not hit, but it keeps its place in the layout |
| `mouseTransparent` | `true` / `false` | the pointer goes through it to what is behind |
| `onAction` | `#method` | a click on it; the renderer calls the handler of the deepest element under the pointer |
| `background`, `borderColor` | color | `#RGB`, `#RRGGBB` or `#AARRGGBB` (a binding returns an ARGB `Integer`, or text like that) |
| `backgroundRadius`, `borderRadius`, `borderWidth` | number | corner radii and border width of the two above |

| Tag | Native element | Attributes |
|---|---|---|
| `Column`, `Row` | `column`, `row` | `gap`, `padding`, `alignment`, `fillWidth` / `fillHeight` |
| `Stack` | `stack` | `padding`, `alignment` |
| `Grid` | `grid` | `hgap`, `vgap`, `padding`, `alignment` |
| `Border` | `border` | `padding`; children placed with `slot` |
| `Scroll` | `scroll` | `fitToWidth`, `fitToHeight`; one child |
| `Tabs` / `Tab` | `tabs` | `selected`; each `Tab` has `title`, `closable` and one child |
| `Flow` | `flow` | `hgap`, `vgap`, `padding`, `alignment`, `orientation`, `prefWrapLength`, `columnHalignment`, `rowValignment` |
| `Tile` | `tile` | `hgap`, `vgap`, `padding`, `alignment`, `orientation`, `prefColumns`, `prefRows`, `prefTileWidth`, `prefTileHeight`, `tileAlignment` |
| `Anchor` | `anchor` | `padding`; children placed with `top`, `right`, `bottom`, `left` |
| `Pane` | `pane` | `padding`; children placed with `x`, `y` |
| `TextFlow` | `textflow` | children are the runs of text (`Label`, `Hyperlink`) |
| `ButtonBar` | `buttonbar` | children are buttons, laid out from the right |
| `Accordion` | `accordion` | children are `TitledPane`s |
| `TitledPane` | `titled` | `text`, `expanded`, `collapsible`, `onAction`; one child |
| `Pagination` | `pagination` | `pageCount`, `current`, `maxPageIndicatorCount`; one child, the page |
| `ToggleButton` | `toggle` | `text`, `selected`, `onAction`, `disabled`, `group`, `value`, `onChange` |
| `RadioButton` | `radio` | `text`, `selected`, `onAction`, `disabled`, `group`, `value`, `onChange` |
| `Hyperlink` | `hyperlink` | `text`, `visited`, `onAction`, `disabled` |
| `ProgressIndicator` | `indicator` | `progress` (negative: indeterminate) |
| `Tooltip` | `tooltip` | `text` |
| `Image` | `image` | `pixels` (an `int[]` of ARGB), `imageWidth`, `imageHeight`, `fitWidth`, `fitHeight`, `preserveRatio` |
| `ListView` | `list` | `items`, `var`, `type`, `key`, `first`, `rows`, `cellHeight`; one child, the row template |
| `ListItem` | `cell` | `text`, `selected`, `odd`, `listFocused`, `alignment`, `onAction`; optionally one child, a graphic |
| `TableView` | `table` | `items`, `var`, `type`, `key`, `first`, `rows`, `rowHeight`, `columns`, `columnWidths`, `constrained`; one `TableRow` |
| `TableRow` | `tablerow` | `selected`, `odd`, `listFocused`; children are `TableCell`s |
| `TableCell` | `cell` | like `ListItem`, with the table's cell look |
| `Label` | `#text` | `text`, `wrapText`, `bold`, `fontSize`, `textFill` |
| `Button` | `button` | `text`, `onAction`, `disabled`, `defaultButton` |
| `TextField`, `PasswordField` | `input`, `password` | `text`, `prompt`, `columns` |
| `TextArea` | `textarea` | `text`, `prompt`, `wrapText`, `columns`, `rows` |
| `CheckBox` | `checkbox` | `text`, `selected` |
| `ComboBox` | `select` | `items` (a list or `A, B, C`), `value`, `prompt` |
| `Spinner` | `spinner` | `value`, `min`, `max` |
| `DatePicker` | `datepicker` | `value`, `prompt` |
| `Separator` | `separator` | `orientation` |
| `ProgressBar` | `progress` | `progress` |
| `Slider` | `slider` | `min`, `max`, `value` |
| `Slot` | what the binding returns | `content` |
| `For`, `If`, `Else` | none: they stand for elements | `items`, `var`, `type`, `key`, `from`, `limit`; `test` |
| Composites | none: they are rewritten (see [Composites](#composites)) | `Padding`, `Center`, `Align`, `SizedBox`, `Expanded`, `Flexible`, `Spacer`, `Box`, `Container`, `Card`, `ListTile`, `Divider`, `Visibility`, `Opacity`, `Wrap`, `Positioned`, `ChoiceBox`, `Scaffold`, `AppBar`, `BottomAppBar`, `BottomBar`, `ToolBar`, `NavigationBar`, `NavigationRail` |

`TextField`, `PasswordField`, `TextArea`, `CheckBox`, `ComboBox`, `Spinner`, `DatePicker` and
`Slider` also accept `onChange` (see [Input changes](#input-changes)); `ToggleButton` and `RadioButton` accept
it with `group` (see [Groups](#groups)).

The table lives in one place, `JxmlSchema`, read by both the interpreter and the compiler. Prop
names and value types are the ones the native renderer reads; a number written as text would be
ignored by the layout, which is why `JXMarkupLoader` (strings only) is not used here.

## Tabs

The native tab pane takes the selected tab's content as its child. JXForge builds every tab's
content, as JavaFX does, and passes the others in the `deferredTabs` prop; the host can create
their nodes ahead with `JXNativeNode.retainAhead`, so switching tabs only moves them into place.
Tab contents get the keys `tab0`, `tab1`, ...

## Loops and conditions

```xml
<Column controller="com.acme.OrdersController" gap="4">
  <Label text="Orders"/>
  <For items="${orders}" var="o" type="com.acme.Order" key="${o.id}">
    <Row>
      <Label text="${o.name}" hgrow="always"/>
      <Button text="Open" onAction="#open(o)"/>
    </Row>
  </For>
  <If test="${admin}"><Button text="Export" onAction="#export"/></If>
  <Else><Label text="Read only"/></Else>
</Column>
```

- `<For>` and `<If>` are allowed wherever a tag takes many children (`Column`, `Row`, `Stack`,
  `Grid`, `Border`, ...). Their children are placed in the parent, in place, in order, with the
  siblings before and after; no element is made for the loop itself.
- `items` is any `Iterable` or array (of objects or primitives); null loops over nothing.
  `var` names the loop variable. A binding or a handler argument may start with it: `${o.name}`
  calls `o.getName()`, `${o.total()}` calls `o.total()`, `${o}` is the item itself, and
  `#open(o)` calls `controller.open(o)` (several: `#move(a, b)`). A loop variable hides a
  controller property of the same name, and a loop inside a loop may reuse the name: the
  nearest one wins. `items` of an inner loop may start with the outer variable (`${o.lines}`).
- `type` is the class of the items. The generated code is a Java loop over a variable of that
  class, so `javac` checks every getter called on it; a misspelled `${o.nmae}` is a compile
  error pointing at the `.jxml` line. `type` is required as soon as the variable is used with
  a getter, a handler argument or `@{}` (a `JxmlException` says so); it is optional when the
  body only shows `${o}` or counts the items.
- `key` sets the `key` prop of the element the loop produces, so the renderer keeps that
  element's node when the list changes. The loop body must then be exactly one element. The
  native reconcile compares keys with `==` (identity), so a key must be a stable object: the
  same instance on every build. A `String` constant, an enum or an object of the model is;
  a boxed number above 127 or a string made on each call is not, and gets no reuse.
- `from` and `limit` (numbers or bindings) take a window of the items: at most `limit` of them, starting
  at index `from`. A start past the end gives no elements. This is what a virtual list is built on.
- `<If test="${path}">` includes its children when the boolean is true; `${!path}` negates
  (only here). An `<Else>` that directly follows the `<If>` is included otherwise. An `<Else>`
  anywhere else, or a second one, is an error. The boolean is read with the same getter rule
  as other bindings (`${admin}` calls `getAdmin()`).
- Not allowed: a loop or condition as the root, inside `Scroll`, `Tab` or `Tabs` (they take
  one child or only tabs; wrap them in a `<Column>`).

Interpreter and generated code build the same tree. In the generated class, a container with a
loop or condition collects its children in a list; every other container is unchanged.

## Input changes

The native renderer reports edits through the `onChange` prop of the node: a
`java.util.function.Consumer` that gets the new value. Inputs are controlled, as in React: the
node keeps showing its `value` prop until the screen is built again with the new one.

| Tag | New value passed | Reported when |
|---|---|---|
| `TextField`, `PasswordField`, `TextArea` | `String`, the whole text | typing, Backspace, Delete (the caret moves with the arrows, Home and End; Enter adds a line in a `TextArea`; no selection yet) |
| `CheckBox` | `boolean` | click, or space |
| `Slider` | `double` | click on the track |
| `Spinner` | `double` | Up / Down arrows, within `min` and `max` |
| `ComboBox` | `String`, the option | Up / Down arrows |
| `DatePicker` | `String`, ISO `yyyy-MM-dd` | Up / Down (day), Page Up / Page Down (month) |

Popups of the combo box and date picker are not drawn yet, so these two change with the keyboard.

```xml
<TextField text="${note}" onChange="#setNote"/>           <!-- controller.setNote(String) -->
<TextField text="@{customer.name}"/>                       <!-- getCustomer().getName() / setName(String) -->
<CheckBox text="Agree" selected="@{agree}"/>               <!-- getAgree() / setAgree(boolean) -->
<Slider value="@{volume}"/>                                <!-- getVolume() / setVolume(double) -->
<For items="${orders}" var="o" type="com.acme.Order">
  <TextField text="@{o.name}"/>                            <!-- o.getName() / o.setName(String) -->
  <TextField text="${o.name}" onChange="#rename(o)"/>      <!-- controller.rename(o, String) -->
</For>
```

- `onChange="#setName"` calls the controller method with the value, after any loop variables
  (`#rename(o)` calls `rename(o, value)`). The method must accept the type in the table, as javac
  would for a call: `setVolume(double)` and `setVolume(Double)` do, `setVolume(int)` does not.
- `@{path}` is the two-way shorthand, on the attribute that holds the edited value: `text` of the
  text inputs, `selected` of the check box, `value` of the others. It reads like `${path}`, and
  on change calls the setter `setX` of the last segment on the object the rest of the path
  returns (evaluated when the value arrives). The setter must take the type in the table.
- `@{a.b()}` (a record accessor, which has no setter), `@{o}` (nothing to set), `@{}` on other
  attributes and `@{}` together with `onChange` are errors.
- The generated code is a lambda that calls the controller, so a misspelled setter is a `javac`
  error.

## Lists and tables

`ListView` and `TableView` are virtual in the renderer: the native node lays out only the rows it is
given, at the positions of their index, and scrolls over the height of the whole model. JXML gives it
a window of the model:

```xml
<ListView items="${orders}" var="o" type="com.acme.Order" key="${o.id}"
          first="${top}" rows="30" cellHeight="24" prefHeight="300">
  <ListItem text="${o.name}" selected="${o.selected}" onAction="#select(o)"/>
</ListView>

<TableView items="${orders}" var="o" type="com.acme.Order" key="${o.id}" first="${top}" rowHeight="24"
           columns="Id, Name, Total" columnWidths="60, -1, 80">
  <TableRow selected="${o.selected}">
    <TableCell text="${o.id}"/>
    <TableCell text="${o.name}" onAction="#select(o)"/>
    <TableCell text="${o.total}"/>
  </TableRow>
</TableView>
```

- The one child is the template of a row (any element for a `ListView`; a `TableRow` for a `TableView`). The
  file is read as the list element with a `<For items var type key from="${first}" limit="${rows}">` around
  the template: `var`, `type` and `key` are those of the loop (so `key` does not set the key of the list itself),
  and `${o.name}` and `#select(o)` work as in any loop.
- `items` is the whole model (a `List`, an `Iterable` or an array). The list gets `itemCount` from it, `first`
  and `rows` choose the window, `rows` is 30 when not written. Build more rows than the viewport shows.
- `first` is the index of the row at the top of the viewport (the application's scroll state; the renderer
  reports no scroll events). It is kept within the model, and `scrollY` (the renderer's offset in pixels) is
  set to `first * height`. Keep `first` at most `itemCount` minus the rows the viewport shows: the renderer
  clamps `scrollY` to the scroll range, and a window that starts later would be drawn below the top.
- `cellHeight` (list) and `rowHeight` (table) are fixed, 24 when not written, and must be greater than 0: the
  renderer needs them to place rows without measuring the first one, and `scrollY` is computed from the same number.
- Selection is the application's state: the row template sets `selected` and `onAction="#select(o)"`; a click
  reaches the cell (the deepest element under the pointer).
- With `key` on the template's item (`key="${o.id}"`), a row that stays in the window after a scroll keeps its
  native node (`JXNativeNode.reconcile` matches by key); the keys must be the same instances on each build, as in
  any `<For key>`.
- A `TableCell` is a `cell` without its own background (the row paints it), with a column line and the table's
  padding; a `ListItem` is a `cell` as a list paints it. Both take a graphic as their one child.
- `columns` are the header titles (a list, or `A, B, C`), `columnWidths` numbers (negative: fit to the content),
  `constrained="true"` fits the columns to the width.
- A grid of items is a `Tile` with a `<For>`; a `ListView` has no columns.

## Groups

`RadioButton` and `ToggleButton` choose one value of a group that the application holds:

```xml
<RadioButton text="A" group="@{choice}" value="a"/>
<RadioButton text="B" group="${choice}" value="b" onChange="#setChoice"/>
<ToggleButton text="C" group="${choice}" value="c"/>   <!-- only shows the choice -->
<RadioButton text="Alone" selected="${on}" onAction="#toggle"/>
```

`group` is what the group holds (`${choice}` reads it, `@{choice}` also calls `setChoice(String)`), `value` is
what this button chooses: it is selected when they are equal, and a click calls the handler with its `value`.
`group` needs `value`; `group` excludes `selected`, and `onAction` cannot be mixed with the choice handler.
The renderer reports clicks as `onAction` only; the file's `onChange` becomes that action.

## Composites

A composite tag is rewritten, when the file is read, into the primitive tags it stands for, so the interpreter
and the compiler never see it. The rewritten tags keep the line of the composite. These are the expansions:

| Written | Becomes |
|---|---|
| `Wrap`, `ChoiceBox`, `Divider`, `Positioned`, `Box`, `Container`, `Padding`, `Opacity`, `Visibility` | `Flow`, `ComboBox`, `Separator`, `Stack` (the last six); the same attributes and children |
| `Center` | `Stack alignment="CENTER" hgrow="always" vgrow="always"` (written attributes win) |
| `Align alignment="BOTTOM_RIGHT"` | the same `Stack`, with that alignment |
| `SizedBox width height` | `Stack` with `minWidth`, `prefWidth` and `maxWidth` set to the width, and the three heights to the height |
| `Spacer` | `Pane hgrow="always" vgrow="always"` |
| `Expanded` / `Flexible` | its one child, with `hgrow` and `vgrow` set to `always` / `sometimes` (a written one wins; the child cannot be a `<Slot>`, `<For>` or `<If>`) |
| `Card` | `Column gap="8" padding="12" fillWidth="true"` with a white `background`, a `#D9D9D9` border and radius 6 (no shadow) |
| `ToolBar` | `Row gap="4" padding="4 8" alignment="CENTER_LEFT" background="#EDEDED"` |
| `BottomAppBar`, `BottomBar` | `Row gap="8" padding="8 12" alignment="CENTER_LEFT" background="#EDEDED"` |
| `NavigationRail` | `Column gap="8" padding="8" alignment="TOP_CENTER" background="#EDEDED"` |
| `AppBar title` | `Row` like the bottom bar, with a bold `Label` of the title that takes the free width, then the children |
| `NavigationBar` | `Row gap="0" padding="4" alignment="CENTER"`; each child gets `hgrow="always"` so they share the width |
| `ListTile title subtitle onAction` | `Row gap="12" padding="8 12" alignment="CENTER_LEFT"` with the `onAction`: the child with `slot="leading"`, then a `Column` (`hgrow="always"`, `mouseTransparent`) of a `Label` of the title and, if written, a smaller grey one of the subtitle, then the child with `slot="trailing"` |
| `Scaffold` | `Border`: an `AppBar` child goes to `top`, a `BottomAppBar` or `BottomBar` to `bottom`, the one other child to `center`; a child that writes `slot` keeps it |

Attributes written on a composite that its expansion does not give a tag are errors (`<Center> has no attribute gap`),
and a value that is wrong is reported for the tag it ends up on, at the composite's line.

## Mixing JXML and Java

The compiler only reads `.jxml` files; classes written by hand are ordinary Java that `javac`
compiles with the generated ones. Both produce `JXElement`, so they nest freely: Java places a
JXML screen with `new XView().build(controller)`, and JXML places a Java component with `<Slot>`.

## What the generated code does

- Literal values (numbers, insets, item lists, tab titles) are static constants: building a
  screen allocates no more than the interpreter, which keeps them in its template.
- With `hoistConstants`, an element whose whole subtree has no binding or handler is built once,
  when the class loads, and every build returns the same instance. Elements are immutable, so
  sharing them is safe, and the native reconcile skips a child that is the same instance.
- Containers get their own method, so a large screen never hits the 64 KB method limit, and each
  method names the `.jxml` line of its element.

## Not done yet

- The widgets of the [1.0 checklist](jxforge-1.0.md) that are not tags yet (group 4 and the language features).
- Events the renderer does not report, so no attribute exists for them: selection and scrolling of lists and
  tables (use a row's `onAction` and the application's `first`), clicks on the buttons of a `Pagination`, and the
  hover that would show a `Tooltip`. A `TitledPane` collapses when the application changes `expanded`
  (its `onAction` tells it was clicked).
- Loading an image file for `Image`: it takes the pixels an application decoded.
- Shadows (for `Card`) and the Material look of the bars: they are rows with a background.
- A `ListView` with columns, and rows of different heights.
- Selection in text inputs, and popups for the combo box and date picker.
- `<For>` over a `Map`, and the loop index as a variable.
- Development mode: the interpreter watching the files and rebuilding the screen on save.
- An XSD for autocompletion in IDEs.
- Compiling existing `.fxml` files with the same compiler, so a JavaFX application moving to the
  native UI gets compiled screens without rewriting them.
- Gradle plugin.

Measurements: [jxforge-benchmark-2026-10-21.md](jxforge-benchmark-2026-10-21.md).
