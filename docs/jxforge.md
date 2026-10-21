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

The table lives in one place, `JxmlSchema`, read by both the interpreter and the compiler. Prop
names and value types are the ones the native renderer reads; a number written as text would be
ignored by the layout, which is why `JXMarkupLoader` (strings only) is not used here.

## Tabs

The native tab pane takes the selected tab's content as its child. JXForge builds every tab's
content, as JavaFX does, and passes the others in the `deferredTabs` prop; the host can create
their nodes ahead with `JXNativeNode.retainAhead`, so switching tabs only moves them into place.
Tab contents get the keys `tab0`, `tab1`, ...

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

- Loops and conditions in the markup (lists of rows, optional parts); today they go in a `<Slot>`.
- Development mode: the interpreter watching the files and rebuilding the screen on save.
- An XSD for autocompletion in IDEs.
- Compiling existing `.fxml` files with the same compiler, so a JavaFX application moving to the
  native UI gets compiled screens without rewriting them.
- Gradle plugin.

Measurements: [jxforge-benchmark-2026-10-21.md](jxforge-benchmark-2026-10-21.md).
