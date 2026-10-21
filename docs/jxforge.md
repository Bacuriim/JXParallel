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
inside a `Border`).

| Tag | Native element | Attributes |
|---|---|---|
| `Column`, `Row` | `column`, `row` | `gap`, `padding`, `alignment`, `fillWidth` / `fillHeight` |
| `Stack` | `stack` | `padding`, `alignment` |
| `Grid` | `grid` | `hgap`, `vgap`, `padding`, `alignment` |
| `Border` | `border` | `padding`; children placed with `slot` |
| `Scroll` | `scroll` | `fitToWidth`, `fitToHeight`; one child |
| `Tabs` / `Tab` | `tabs` | `selected`; each `Tab` has `title`, `closable` and one child |
| `Label` | `#text` | `text`, `wrapText` |
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
| `For`, `If`, `Else` | none: they stand for elements | `items`, `var`, `type`, `key`; `test` |

`TextField`, `PasswordField`, `TextArea`, `CheckBox`, `ComboBox`, `Spinner`, `DatePicker` and
`Slider` also accept `onChange` (see [Input changes](#input-changes)).

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

- The widgets of the [1.0 checklist](jxforge-1.0.md) that are not tags yet.
- Selection in text inputs, and popups for the combo box and date picker.
- `<For>` over a `Map`, and the loop index as a variable.
- Development mode: the interpreter watching the files and rebuilding the screen on save.
- An XSD for autocompletion in IDEs.
- Compiling existing `.fxml` files with the same compiler, so a JavaFX application moving to the
  native UI gets compiled screens without rewriting them.
- Gradle plugin.

Measurements: [jxforge-benchmark-2026-10-21.md](jxforge-benchmark-2026-10-21.md).
