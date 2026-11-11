# JXForge (JXML) for IntelliJ IDEA

An IntelliJ IDEA plugin for the `.jxml` screens of [JXForge](../docs/jxforge.md). A `.jxml` file is XML, so everything
the IDE does for XML keeps working; the plugin adds what is specific to JXML: the schema, and the link between a screen and
its Java controller.

- Plugin id `io.github.bacuriim.jxforge`, name "JXForge (JXML)", vendor "JXParallel".
- IntelliJ IDEA 2024.2 or newer (build 242 and up, no upper limit), with the bundled Java plugin.
- Not a Maven module: it is a standalone Gradle build in this folder.

## Install from disk

1. Build the zip (below) or take `build/distributions/jxforge-intellij-plugin-0.1.0.zip`.
2. In the IDE: Settings, Plugins, the gear icon, Install Plugin from Disk..., choose the zip, restart.

If you had registered `*.jxml` as an XML pattern by hand (or answered the "Register New File Type Association" prompt),
remove that pattern in Settings, Editor, File Types, XML: a pattern you added yourself takes precedence over the plugin.

## What it does

**File type.** `*.jxml` is the file type "JXML", with its own icon (light and dark). It is a dialect of XML (the language
`JXML` extends the XML language), so editing, folding, formatting, structure view and XML inspections apply, and the IDE no
longer asks to register the extension.

**Schema without configuration.** The plugin bundles `jxml.xsd`, generated at build time from `JxmlSchema` by
`com.jxparallel.forge.JxmlXsd`, and offers it for every `.jxml` file: completion of tags and attributes, the documentation
text of the schema in the quick documentation, and the errors for tags, attributes and children the language does not
have. No `xsi:noNamespaceSchemaLocation` is needed. A file that names its own schema keeps that one. The schema is also
registered as `urn:jxforge:jxml.xsd`, which is what the "JXML Screen" template writes in its schema line, so that the
line resolves without a copy of the file.

**Controller awareness.** References in attribute values:

| Written | Resolves to | Also |
| --- | --- | --- |
| `controller="com.acme.Ctl"` on the root | the Java class | completion of class names, error when it does not exist |
| `onAction="#save"`, `#open(item)` | the public methods of that name in the controller; the arguments to the loop variables | completion of the controller's public methods; an unresolved handler is an error with the quick fix "Create method 'save' in Controller" (the loop variables become parameters, typed when known) |
| `${a.b.c}` | each segment to the getter along the path: `getX()`, else `isX()` or `x()` | a segment that does not resolve is a warning; completion of properties inside `${...}` (the loop variables first), also after each dot |
| `@{x}`, `@{a.b}` | the getter, as above | `@{x}` without a public `setX(...)` in the owning type is an error ("cannot be bound two ways"); so are `@{a.b()}` (a record accessor) and `@{o}` (a loop variable) |
| `<For items="${list}" var="r">`, `ListView`, `TableView` | `r` is typed by `type`, or else by the element type of `items` (generics, arrays) | nested loops use the nearest variable; when no type is known nothing is reported |
| `<Include src="parts/x.jxml"/>` | the file, relative to the including file | error when it does not exist; a part without a `controller` uses the controller of a file that includes it |

The JXML runtime calls `getX()` for `${x}` and does not call `isX()`. The plugin resolves `isX()` and a bare `x()` too (so
Ctrl+click works) but marks them with a weak warning that says which getter the runtime calls.

Find Usages and Rename of a controller method or getter update the `.jxml` references: renaming `getName` to
`getFullName` changes `${customer.name}` and `@{customer.name}` to `customer.fullName`, and renaming `save` changes `#save`.
Renaming the controller class changes the `controller` attribute.

**Colors.** `${...}`, `@{...}` and `#handler` values have their own text attributes, under Settings, Editor, Color Scheme,
JXML (they inherit from the instance field, static field and instance method colors, so they differ from plain strings in
every scheme).

**Gutter icons.** In a controller class, an icon on the class name navigates to the `.jxml` files whose root `controller`
names it (a small file index maps the class name to the screens). In a `.jxml` file, an icon on the root tag navigates to
the controller.

**New file template.** New, JXML Screen (also in the Project view's New menu) creates a screen with a root `Column`, a
`controller` attribute (`<package>.<Name>Controller`, taken from the folder when it is a package) and the schema line.

## Limits

- The check of attribute values by shape (`gap="lots"`) is done by the XML validator of the IDE against the schema, not
  by the plugin; tag, attribute and child errors are reported on the fly, the value errors have not been checked in the IDE.
- A nested controller class (`com.acme.Outer.Inner`) is accepted (no error, handlers resolve) but Ctrl+click on the class
  name in the attribute does not reach the class.
- `type="Order"` without a package is read relative to the controller's package and the controller's nested classes;
  the class reference itself is soft (no error mark).
- The plugin never runs the JXForge compiler: errors that only the compiler reports (argument types of handlers, `javac`
  errors of the generated code) are not shown.

## Build

Requirements: JDK 21 for Gradle (`gradle.properties` points `org.gradle.java.home` to the author's JDK 21; change it or
remove the line and set `JAVA_HOME`), and JDK 17 plus Maven to build the forge once.

```powershell
# 1. the schema generator: jars of jxparallel-forge and jxparallel-core (from the repository root, JDK 17)
mvn -o -q -pl jxparallel-forge -am package -DskipTests

# 2. the plugin (from jxforge-intellij-plugin/)
./gradlew test                          # platform tests, headless
./gradlew buildPlugin                   # build/distributions/jxforge-intellij-plugin-0.1.0.zip
./gradlew verifyPluginProjectConfiguration verifyPlugin   # Plugin Verifier on 2024.2, 2025.2 and the latest IntelliJ IDEA
```

The Gradle task `generateJxmlXsd` runs `JxmlXsd` from those jars and puts `jxml.xsd` into the plugin resources, so the
schema always matches the checkout. The first build downloads the IntelliJ Platform (about 1 GB for each IDE the verifier
uses). The IntelliJ Platform Gradle Plugin is 2.9.0, the newest that runs on Gradle 8.10.2 (the wrapper's version).

## Tests

`./gradlew test` runs the platform tests (`BasePlatformTestCase`, and `LightJavaCodeInsightFixtureTestCase` with a JDK 17
project) for: the file type and its XML behavior (`FileTypeTest`), the schema (`SchemaTest`), the references,
diagnostics, quick fix, completion, Find Usages and rename (`ControllerReferenceTest`), and the colors, gutter icons,
index and file template (`EditorFeaturesTest`).

## Publishing

Not done from here. The `plugin.xml` has the name, vendor, description and change notes that the JetBrains Marketplace
asks for, and `META-INF/pluginIcon.svg` and `pluginIcon_dark.svg` (40x40). Signing and uploading are a separate step with
the vendor's own certificate and token.
