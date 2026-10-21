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
| `Button` | `button` | `text`, `onAction`, `disabled`, `defaultButton`, `variant`, `round`, `padding`, `textFill`; an optional child is the graphic left of the text |
| `TextField`, `PasswordField` | `input`, `password` | `text`, `prompt`, `columns` |
| `TextArea` | `textarea` | `text`, `prompt`, `wrapText`, `columns`, `rows` |
| `CheckBox` | `checkbox` | `text`, `selected` |
| `ComboBox` | `select` | `items` (a list or `A, B, C`), `value`, `prompt`, `showing`, `onShowing` |
| `Spinner` | `spinner` | `value`, `min`, `max` |
| `DatePicker` | `datepicker` | `value`, `prompt`, `showing`, `onShowing` |
| `TimePicker` | `timepicker` | `value` (`HH:mm`), `prompt`, `hour24`, `minuteStep`, `disabled`, `showing`, `onShowing` |
| `ColorPicker` | `colorpicker` | `value` (`#RRGGBB`), `prompt`, `disabled`, `showing`, `onShowing` |
| `MenuButton` | `menubutton` | `text`, `disabled`, `showing`, `onShowing`; children are `MenuItem`s and `Menu`s |
| `ContextMenu` | `contextmenu` | `disabled`, `showing`, `onShowing`; children are `MenuItem`s and `Menu`s; takes no room |
| `MenuBar` | `menubar` | children are `Menu`s |
| `Menu` | `menu` | `text`, `disabled`; children are `MenuItem`s and `Menu`s (a submenu) |
| `MenuItem` | `menuitem` | `text`, `accelerator`, `disabled`, `onAction`, `separator` |
| `Modal` | `dialog` | `dismissible`, `padding`, `gap`, `alignment`, `fillWidth`, `showing`, `onShowing`; a column over a scrim; takes no room |
| `Drawer` | `drawer` | `side` (`left`, `right`), `padding`, `gap`, `alignment`, `fillWidth`, `showing`, `onShowing`; a column over a scrim; takes no room |
| `SnackBar` | `snackbar` | `message`, `action`, `onAction`, `duration`, `showing`, `onShowing`; takes no room |
| `Tree` | `list` | `rowHeight`, `first`, `rows`; children are `TreeItem`s |
| `TreeTable` | `table` | `rowHeight`, `first`, `rows`, `columns`, `columnWidths`, `constrained`; children are `TreeItem`s |
| `TreeItem` | `cell` | `text`, `expanded`, `selected`, `listFocused`, `values`, `onAction`, `onToggle`; children are `TreeItem`s |
| `Separator` | `separator` | `orientation` |
| `ProgressBar` | `progress` | `progress` |
| `Slider` | `slider` | `min`, `max`, `value` |
| `Switch` | `switch` | `text`, `selected`, `disabled`; `onChange` (Boolean) |
| `Icon` | `icon` | `name` (required), `size`, `color` |
| `Chip` | `chip` | `text`, `kind`, `icon`, `selected`, `closable`, `onClose`, `disabled`; `onChange` (Boolean) |
| `Badge` | `badge` | `count`, `text`, `dot`, `max`, `color`; one child |
| `Avatar` | `avatar` | `size`, `initials`, `pixels`, `imageWidth`, `imageHeight`, `color`, `textFill` |
| `Stepper` | `stepper` | `titles`, `steps`, `current` |
| `ScrollBar` | `scrollbar` | `min`, `max`, `value`, `visibleAmount`, `orientation`; `onChange` (Double) |
| `SplitPane` | `splitpane` | `orientation`, `dividerPositions`; `onChange` (double[]); children are the panes |
| `TabBar` | `tabbar` | `titles`, `selected`, `closable`, `onClose` (Integer); `onChange` (Integer) |
| `Slot` | what the binding returns | `content` |
| `For`, `If`, `Else` | none: they stand for elements | `items`, `var`, `type`, `key`, `from`, `limit`; `test` |
| Composites | none: they are rewritten (see [Composites](#composites)) | `Padding`, `Center`, `Align`, `SizedBox`, `Expanded`, `Flexible`, `Spacer`, `Box`, `Container`, `Card`, `ListTile`, `Divider`, `Visibility`, `Opacity`, `Wrap`, `Positioned`, `ChoiceBox`, `Scaffold`, `AppBar`, `BottomAppBar`, `BottomBar`, `ToolBar`, `NavigationBar`, `NavigationRail`, `IconButton`, `Fab`, `Dialog`, `AlertDialog`, `MenuSeparator` |

`TextField`, `PasswordField`, `TextArea`, `CheckBox`, `ComboBox`, `Spinner`, `DatePicker`, `TimePicker`, `ColorPicker` and
`Slider` also accept `onChange` (see [Input changes](#input-changes)); `ToggleButton` and `RadioButton` accept
it with `group` (see [Groups](#groups)). Tags that open over the window take `showing` and `onShowing` (see [Overlays](#overlays)).
A `MenuItem` stands only inside a `MenuButton`, `ContextMenu` or `Menu`, a `Menu` inside a `MenuBar`, `MenuButton`,
`ContextMenu` or `Menu`, a `TreeItem` inside a `Tree`, `TreeTable` or `TreeItem`; those parents take only those children
(loops and conditions around them are fine), and a mistake is a `JxmlException` at the line of the wrong tag.

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
| `ComboBox` | `String`, the option | Up / Down arrows, a press on an option of the open list |
| `DatePicker` | `String`, ISO `yyyy-MM-dd` | Up / Down (day), Page Up / Page Down (month), a press on a day of the open calendar |
| `TimePicker` | `String`, `HH:mm` | Up / Down (the `minuteStep`), Page Up / Page Down (an hour), a press on an hour, a minute or AM / PM of the open popup |
| `ColorPicker` | `String`, `#RRGGBB` | a press on a swatch of the open popup, or hex digits typed in it once the text reads `#RRGGBB` |
| `Switch`, `Chip` | `boolean` | click (and space for `Switch`) |
| `ScrollBar` | `double` | click on the track, drag of the thumb |
| `SplitPane` | `double[]`, all the divider positions | drag of a divider; `@{positions}` needs a setter taking a `double[]` |
| `TabBar` | `int`, the tab | click on another tab; `onClose` gets the index of the tab whose close icon was clicked |

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
| `Dialog title showing onShowing dismissible` | a `Modal` (`padding="24" gap="16" alignment="TOP_LEFT" minWidth="280"` unless written) of a bold `Label` of the title (size 18), the children, then the children with `slot="actions"` in a `ButtonBar` |
| `AlertDialog title message confirmText cancelText onConfirm onCancel` | a `Modal` of the title, the message (a wrapping `Label`, at most 320 wide) and a `ButtonBar` of the cancel `Button` (only when `cancelText` is written, its `onAction` is `onCancel`) and the confirm `Button` (`confirmText`, OK when not written; `defaultButton`, its `onAction` is `onConfirm`) |
| `MenuSeparator` | `MenuItem separator="true"` |

Attributes written on a composite that its expansion does not give a tag are errors (`<Center> has no attribute gap`),
and a value that is wrong is reported for the tag it ends up on, at the composite's line.

## Material controls

These tags are drawn by the renderer with the colors of the theme (see [Styles and themes](#styles-and-themes)); each is one native element and
every painter draws it, because it uses only the primitives of `JXPainter`.

- `Switch`: a 40 x 22 track and a thumb, `selected` on or off, `text` is an optional label. A click or the space bar reports the new
  Boolean through `onChange` (or `@{flag}`), like `CheckBox`.
- `Icon`: `name` is one of `menu close add remove check search arrow-back arrow-forward arrow-up arrow-down chevron-left chevron-right
  chevron-up chevron-down more-vert more-horiz edit delete settings home info warning error star favorite refresh person mail calendar
  visibility lock`. They are lines, ovals, arcs and triangles drawn on a 24 x 24 grid and scaled to `size` (24 by default); no font, file or
  network. `color` defaults to `onSurface`. An unknown name is a `JxmlException` listing these names, and `name` cannot be bound.
- `Button variant`: `filled`, `tonal`, `outlined`, `text` or `elevated` (and `fab`, which `Fab` writes). Without `variant` the button is
  the classic one, unchanged. `round="true"` makes the ends round. Hover and pressed tint the face. `elevated` and `fab` draw a soft darker
  ring below the face (two translucent rings, because not every painter has a blur), kept inside the button's bounds, which are 3 px larger
  on each side for it. A child of the button (an `Icon`) is its graphic.
- `IconButton icon size color variant`: a `Button` (`variant` `text` unless written, `round`, padding 8) whose graphic is an `Icon` that does
  not take the click, so the button gets it. `Fab icon text`: a `Button variant="fab"` with an icon, a text or both (padding 16, or 16 20 with a
  text).
- `Chip`: `kind` is `assist` (outlined, 8 px corners), `filter` (outlined pill; selected: tinted, with a check mark as leading icon),
  `choice` (outlined, 4 px corners; selected: filled with the primary color) or `input` (tinted face). `icon` is a leading icon, `closable="true"`
  adds a close icon. A click on the chip runs `onAction` and reports the new `selected` through `onChange`; a click on the close icon runs only
  `onClose`.
- `Badge`: one child; the mark sits at its top right, inside the badge's bounds. `count` (at most `max`, 99 by default, then `99+`; 0 or less shows
  nothing), `text`, or `dot="true"`; `color` defaults to `error`.
- `Avatar`: a circle of `size` (40): `initials` on `color` (a tint of the primary color), or `pixels` with `imageWidth` / `imageHeight` (as
  `Image`), cut to a circle with a soft edge.
- `Stepper`: `titles` (or `steps`, a count) and `current`: the circles before `current` are filled with a check mark, `current` is filled with its
  number, the later ones are outlined; lines join them.
- `ScrollBar`: `value` between `min` and `max`; the thumb is `visibleAmount` of the range plus itself, at least 24 px. A click on the track puts the
  middle of the thumb under the pointer; a press on the thumb grabs it and the drag follows the pointer; both report the new Double through `onChange`.
- `SplitPane`: the children are the panes, `orientation="horizontal"` (side by side, the default) or `vertical` (stacked). `dividerPositions` are
  fractions (0 to 1) of the room the panes share, one less than the panes, the missing ones spread evenly. Dragging a divider reports all the positions as
  a `double[]` through `onChange` (so `@{positions}` needs `setPositions(double[])`), the divider kept at least 20 px from its neighbours.
- `TabBar`: only the strip of tab headers (the content is the application's: show it with `<If>` on `selected`). A click on another tab reports its index
  (Integer) through `onChange` or `@{tab}`; with `closable="true"` every tab has a close icon and `onClose="#closeTab"` calls `closeTab(int index)`. `Tabs` is
  unchanged.

A drag needs the pointer moves after the press: the renderer's window delivers them to `JXNativeNode.dispatchDrag` when it has no input listener; a host
with its own listener calls that method.

## Overlays

Everything that opens over the window goes through one mechanism in the renderer (`JXOverlays`, `JXOverlayPaint`):

| Tag | Native element | What opens | Where |
|---|---|---|---|
| `ComboBox` | `select` | the list of `items` (at most ten rows, scrolled to the chosen one) | below the control, or above when there is no room |
| `DatePicker` | `datepicker` | a calendar of the month of `value` (today when empty) with arrows to the other months | below |
| `TimePicker` | `timepicker` | the hours (24, or 12 and AM / PM without `hour24`) and the minutes (every `minuteStep`, a divisor of 60; 5 when not written or not one) | below |
| `ColorPicker` | `colorpicker` | 40 swatches and a hex field | below |
| `MenuButton` | `menubutton` | the panel of its items and submenus | below the button |
| `ContextMenu` | `contextmenu` | the panel of its items and submenus | at the pointer of a secondary press on its parent |
| `MenuBar` | `menubar` | the panel of the pressed `Menu`'s items and submenus | below its title |
| `Dialog`, `AlertDialog`, `Modal` | `dialog` | a column of content, over a scrim | the middle of the window |
| `Drawer` | `drawer` | a column the height of the window, over a scrim | the left or right edge |
| `SnackBar` | `snackbar` | `message` and an `action` | bottom center, 16 px from the edge; no scrim |

```xml
<Stack controller="com.acme.OrdersController">
  <Column padding="8">
    <ComboBox items="${cities}" value="${city}" showing="${cityOpen}" onShowing="#setCityOpen" onChange="#setCity"/>
    <Button text="Delete" onAction="#askDelete"/>
  </Column>
  <AlertDialog title="Delete?" message="This cannot be undone." confirmText="Delete" cancelText="Cancel"
               showing="${deleting}" onShowing="#setDeleting" onConfirm="#delete" onCancel="#keep"/>
  <SnackBar message="Deleted" action="Undo" onAction="#undo" duration="4000" showing="${deleted}" onShowing="#setDeleted"/>
</Stack>
```

- **State.** `showing` is the open state and `onShowing` the handler (`#setCityOpen` calls `setCityOpen(boolean)`) that gets the
  state the user asked for: a press on the control (open or close), a choice in a popup, a press outside, Escape, a snack bar's
  timeout. With `showing` written, the application holds the state and the node only asks, as inputs do with `onChange`: nothing
  closes until the application renders again with `showing` false. Without it, the node keeps its own state: a `MenuButton`,
  `ContextMenu` or `MenuBar` written without `showing` opens and closes by itself. `ComboBox`, `DatePicker`, `TimePicker`, `ColorPicker`, `Modal`, `Drawer` and
  `SnackBar` default `showing` to false, so they are controlled; `MenuButton`, `ContextMenu` and `MenuBar` have no default.
  `showing` cannot be bound two ways (`@{...}`): `onShowing` is a separate handler.
- **Where it is written.** An overlay is placed over the window, not by its parent: put `Dialog`, `Drawer`, `SnackBar` and
  `ContextMenu` anywhere (a `Stack` at the root is the usual place); they take no room where they are written (size 0 by 0, and
  a `Column` still counts its `gap` for them), and a `ContextMenu` opens on the secondary press on its *parent*, the deepest
  parent under the pointer when several nest.
- **Drawing.** The open overlays are drawn after the whole tree, in tree order (a later one is above an earlier one), with
  the theme at their place. They use only the `JXPainter` primitives, so Skia, NanoVG and the software painter draw them.
  The scrim is `#66000000` over the whole window.
- **The pointer.** `JXNativeNode.hitPath` and `hitTest` find an open overlay before anything else. The window gives presses to
  `JXNativeNode.routePointer(event)`, which: sends a press on an open popup, menu or dialog there; closes the topmost open popup
  or menu on a press outside it (and goes no further, so the press does not click what is under it); asks a dialog or drawer to
  close on a press on its scrim (a `Modal` with `dismissible="false"` ignores it); opens a `ContextMenu` on a secondary press;
  and otherwise gives the press to the deepest node. A snack bar takes only the presses on it. A host that handles its own input
  (`JXWindow.setInputListener`) calls `routePointer` and `routeKey` itself.
- **Keys.** `JXNativeNode.routeKey(event, focused)`: Escape closes the topmost open overlay (a snack bar only when it is the
  only one); Enter presses the `defaultButton` of an open dialog, and closes an open popup of the focused control; every other
  key goes to the focused node. With a color picker open, `#` and hex digits are typed in its hex field (Backspace removes
  one) and each complete `#RRGGBB` goes to `onChange`.
- **Dialog.** `Dialog` is a `Modal` (a column: use `padding`, `gap`, `alignment`, `prefWidth`... on it as on a `Column`)
  with the title, the content and the buttons; `AlertDialog` adds the usual two buttons. Each button has its own `onAction`.
  The dialog is as wide as its content (at least 280 as a `Dialog`) and at most the window less 24 px on each side.
- **Menus.** A `MenuItem` runs its `onAction` and closes the menu; a disabled one and a `MenuSeparator` do nothing and the menu
  stays open; a `Menu` inside a `Menu` opens a submenu to the right of its row (to the left when there is no room); a
  `MenuBar` opens the menu of the pressed title, another title switches, the same title closes. `accelerator` is only text
  (`Ctrl+O`): nothing binds the key.
- **Snack bar.** `duration` (milliseconds) is read by the node: `JXNativeNode.tickOverlays()` (the window calls it every turn
  of its loop while one waits) asks the snack bar to close through `onShowing` when it has been showing that long. The
  application hides it by setting `showing` false. `action` and `onAction` make a button at the right end; the message is not
  a button.
- **For code.** `JXNativeNode.getPopupBounds()` and `getPopupCells()` give the rectangles of what a node has open (the
  mount tests press on them).

The JavaFX bridge (`jxparallel-fx`) sends `select` and `datepicker` with `showing` and draws their popups as separate
elements, so a `select` or `datepicker` opens its own popup only with the prop `overlay`, which `ComboBox` and `DatePicker`
write.

## Trees

```xml
<Tree rowHeight="22" first="${top}" rows="20">
  <TreeItem text="src" expanded="${srcOpen}" selected="${srcSelected}" onAction="#selectSrc" onToggle="#toggleSrc">
    <TreeItem text="main"/>
    <For items="${files}" var="f" type="com.acme.File" key="${f.id}">
      <TreeItem text="${f.name}" onAction="#select(f)"/>
    </For>
  </TreeItem>
  <TreeItem text="README.md"/>
</Tree>

<TreeTable rowHeight="24" columns="Name, Size" columnWidths="-1, 80" constrained="true">
  <TreeItem text="docs" expanded="true" values="12 KB"><TreeItem text="guide.md" values="4 KB"/></TreeItem>
</TreeTable>
```

- **The model** is the nested `TreeItem`s, with `<For>` and `<If>` anywhere between them (and `key` on a `<For>` as in any loop);
  the application's own state (`expanded`, `selected`) is read from the controller as for any other tag. An item is a *leaf*
  when it has no `TreeItem` child. A `TreeItem` outside a `Tree`, `TreeTable` or `TreeItem`, and any other tag inside them, is
  a `JxmlException`.
- **The rows.** `JxmlRuntime.tree` (shared by the interpreter and the generated code) flattens the *visible* items in order:
  an item's children follow it only when it is `expanded`. The result is a `list` of `cell`s (a `Tree`) or a `table` of
  `tablerow`s of `cell`s (a `TreeTable`), as a `ListView` or `TableView` is built, with `itemCount` (all the visible items),
  `first`, `scrollY` (`first * rowHeight`) and `cellHeight` / `rowHeight` (24 when not written, greater than 0): the same
  geometry and the same virtual rows, so scrolling is the application's `first`, and the renderer lays out only the `rows`
  (30 when not written) rows from `first`. Every row gets `odd` from its place among the visible items.
- **A row** has `indent` (its depth: 16 px each) and `disclosure`: `expanded` or `collapsed` draw an arrow, `leaf` draws none but
  keeps its room. A press on the arrow runs the item's `onToggle` (the application changes `expanded`); a press anywhere else on
  the row runs its `onAction` (the application changes `selected`). An item without `onToggle` selects on the arrow too.
- **`TreeTable`.** The first cell of a row is the tree (text, indent, arrow); `values` (a list, or `A, B`) are the texts of
  the next columns. `columns`, `columnWidths` and `constrained` are those of `TableView`. `onAction` is on every cell of the row,
  `onToggle` only on the first. `values` in a `Tree` is an error at the line of the `Tree`.
- **Keys.** An item with a `key` (the `key` of a `<For>`, or `key="..."`) keeps its node when the window scrolls, if every row of
  the window has one: the renderer matches rows by key only when all of them have it.
- **Limits.** Every item is built on each render; only the window reaches the renderer (the build is not windowed, as a
  `ListView`'s is). There is no `children="${...}"` attribute for a recursive model: nest the items, put a `<For>` at each level,
  or flatten the model yourself into a `ListView` of rows with a depth.

## Development mode

`JxmlLiveView` (in `jxparallel-forge`) reads a `.jxml` file with the interpreter and reads it again when the file, or a file it
includes, is saved. The application asks it for the screen every time it renders; a save only has to trigger a render.

```java
JxmlLiveView live = JxmlLiveView.open(new File("src/main/jxml/customers.jxml"))
        .onError(e -> System.err.println(e.getMessage()))               // "customers.jxml:7: unknown tag <Buton>"
        .onReload(() -> window.invokeLater(() -> window.setContent(live.build(controller))))
        .start();
window.setContent(live.build(controller));   // JxmlLiveView is a JxmlView<Object>, like the generated classes
```

- It watches the folder of the file and of each include with a `WatchService` on a daemon thread, and reads after 80 ms without
  another event (a save is several events). `reload()` reads at once, on the calling thread, and returns whether the new screen
  was taken; `close()` stops the thread.
- A file that cannot be read (half written, a wrong tag or attribute, a missing include) keeps the last good screen:
  `build` goes on using it, `lastError()` holds the `JxmlException` with the file and line, and the `onError` listeners get it.
  The next good save replaces the screen and clears the error. `open` throws on the first read, since there is nothing to keep yet.
- Thread safe. Listeners run on the watcher thread (or the thread that called `reload`); a window's `invokeLater` brings the work
  back to the display thread. `window.setContent` reconciles in place, so the nodes, the focus and the scroll positions that stay
  are kept.
- Controller methods are found by reflection each time; a change to the controller class is not reloaded, only to the files.

## Styles and themes

A file may start with `<Style>` elements, direct children of its root, which are not elements of the screen:

```xml
<Column controller="com.acme.HomeController" theme="dark" gap="8">
  <Style name="title" textFill="primary" bold="true" fontSize="16"/>
  <Style name="danger" background="error" textFill="onPrimary"/>
  <Label text="Orders" style="title"/>
  <Button text="Delete" style="danger" background="#00FF00"/>   <!-- the element's own attribute wins -->
  <Card style="title danger"/>                                   <!-- several styles: a later one wins -->
</Column>
```

- A style holds attributes (any attribute of any tag, but no handler and no binding: it is resolved when the file is read). `style="a b"` applies
  them in order. The order of strength is: the attribute written on the element, then the styles (later over earlier), then the defaults of the tag or of
  a composite (a style beats the white of a `Card`).
- A style attribute the tag does not have is skipped (one style serves labels and boxes); an attribute no tag has, a handler, a binding, a duplicate name in
  a file or an unknown `style` name is a `JxmlException` with the line.
- The theme: `theme="light"` or `"dark"` on the root. The six tokens `primary onPrimary surface onSurface outline error` are accepted wherever a color is
  written, as a literal (`background="surface"`), and become the color of the file's theme when it is read, so the interpreter and the compiler build the same
  props. The root also gets the prop `theme`, which the renderer inherits down the tree: it paints the themed root with `surface`, draws `Label` text in
  `onSurface` and the Material controls above in the theme's colors. The classic controls (`Button` without `variant`, `CheckBox`, `TextField` ...) keep their Modena look.
- Tokens, light / dark: primary `#1565C0` / `#90CAF9`, onPrimary `#FFFFFF` / `#0D2B4A`, surface `#FFFFFF` / `#1C1B1F`, onSurface `#1C1B1F` / `#E6E1E5`, outline
  `#79747E` / `#938F99`, error `#B3261C` / `#F2B8B5`. `JxmlThemes` (here) and `JXTheme` (the renderer) hold the same table; a test compares them.

## Reusable screen parts

```xml
<Column controller="com.acme.HomeController">
  <Include src="parts/header.jxml" hgrow="always"/>
  <Include src="parts/footer.jxml"/>
</Column>
```

- `src` is a path relative to the folder of the file that includes it (`../shared/x.jxml` works); it is read when the including file is read, by the
  interpreter and by the compiler alike, and the part's root element takes the place of the `<Include>` in the tree. Attributes written on the `<Include>`
  (placement such as `slot`, `hgrow`, `row`) are given to the part's root and win over its own. `src` cannot be bound.
- Bindings and handlers of a part resolve against the controller of the file that includes it, and inside a `<For>` they can use its loop variable. A part
  needs no `controller`; one that has a root `controller` must name the same class as the including file (an error says so), and is also a view of its own.
- A part uses the styles of the file that includes it and may define its own `<Style>`s (the including file's definition of a name wins). A part's `theme` is ignored.
- A cycle is a `JxmlException` with the chain of files; a missing file, an escape above the source folder and a part with a mistake are errors at the include's line
  or, for the mistake, at the line in the part's own file (the generated code's comments name the part too).
- Where the files are found: `JxmlTemplate.read(File)` (next to the file), `JxmlTemplate.readResource(classLoader, "screens/main.jxml")` (resources, next to the
  resource) and `JxmlTemplate.read(stream, name, JxmlLoader)`; a file read with `read(stream, name)` cannot include. The compiler's directory mode (`JXForge`, the
  Maven plugin) reads parts from the source folder; a file that is included by another and has no root `controller` gets no class, and a file that includes
  others is regenerated whenever any `.jxml` in the folder is newer than its class.

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

- Events the renderer does not report, so no attribute exists for them: selection and scrolling of lists and
  tables (use a row's `onAction` and the application's `first`), clicks on the buttons of a `Pagination`, and the
  hover that would show a `Tooltip`. A `TitledPane` collapses when the application changes `expanded`
  (its `onAction` tells it was clicked).
- Loading an image file for `Image`: it takes the pixels an application decoded.
- Shadows (for `Card`) and the Material look of the bars: they are rows with a background.
- A `ListView` with columns, and rows of different heights.
- Selection in text inputs.
- Keyboard navigation and hover in the popups and menus (see [Overlays](#overlays)), and a recursive model for `Tree`
  (see [Trees](#trees)).
- `<For>` over a `Map`, and the loop index as a variable.
- An XSD for autocompletion in IDEs.
- Compiling existing `.fxml` files with the same compiler, so a JavaFX application moving to the
  native UI gets compiled screens without rewriting them.
- Gradle plugin.

Measurements: [jxforge-benchmark-2026-10-21.md](jxforge-benchmark-2026-10-21.md) (20 form screens: compiled, interpreted, FXML) and [jxforge-components-benchmark-2026-10-21.md](jxforge-components-benchmark-2026-10-21.md) (every component one by one, against JavaFX).
