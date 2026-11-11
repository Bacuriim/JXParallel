# An XML Schema for JXML

`jxml.xsd` is an XML Schema (XSD 1.0) generated from `JxmlSchema`, the one table that the JXML
interpreter and the compiler both read. An editor that understands XSD can complete the tags and
attributes of a `.jxml` file and underline the ones that do not exist. Nothing in the schema is
written by hand: a tag or attribute added to `JxmlSchema` appears in the next generated file.

The schema is generated, not shipped as a file, so it always matches the version of JXForge you
build with. Generate it again after upgrading.

## Generate it

With Maven, from a project that has `jxforge-maven-plugin` in its `pom.xml` (see
[jxforge.md](jxforge.md#setup)):

```powershell
mvn io.github.bacuriim:jxforge-maven-plugin:xsd
```

The goal `xsd` is not bound to a phase. It writes `target/jxml.xsd`; the parameter `outputFile`
(property `jxforge.xsd`) changes the path:

```powershell
mvn io.github.bacuriim:jxforge-maven-plugin:xsd -Djxforge.xsd=src/main/jxml/jxml.xsd
```

From the command line, without Maven:

```powershell
java -cp jxparallel-forge.jar;jxparallel-core.jar com.jxparallel.forge.JxmlXsd --out target/jxml.xsd
```

From Java, `JxmlXsd.generate()` returns the schema as text and `JxmlXsd.write(File)` writes it.

## Point a file at it

JXML files have no XML namespace, so the schema has none. Name it on the root element with
`xsi:noNamespaceSchemaLocation`, as a path relative to the file:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Border xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:noNamespaceSchemaLocation="../jxml.xsd"
        controller="com.acme.CustomersController">
  ...
</Border>
```

The JXML reader ignores `xmlns*` and `xsi:*` attributes, so a file with these still loads, in the
interpreter and in the compiler. Only the root element needs them. If the schema is kept outside
the source folder (for example in `target/`), an editor setting that maps `*.jxml` to the schema
(below) avoids a path in every file.

## Editors

These steps follow each tool's documented XML schema support. They have not been tried in the
editors themselves for this project; if one differs in your version, the tool's own documentation
for "XML schema association" is the reference.

- **IntelliJ IDEA.** Install the plugin from `jxforge-intellij-plugin` ([README](../jxforge-intellij-plugin/README.md)): it has this schema built in, so
  no setup is needed, and it also resolves `controller`, `#handler`, `${...}` and `@{...}` against the Java controller. Without the plugin, add `*.jxml` to the
  XML file type (Settings, Editor, File Types, XML, Registered Patterns). With `xsi:noNamespaceSchemaLocation` in the file, the IDE reads the schema from it.
  Without it, map the schema by hand in Settings, Languages & Frameworks, Schemas and DTDs, with an empty namespace.
- **VS Code.** The extension in [`jxforge-vscode`](../jxforge-vscode/README.md) bundles this schema and
  registers it for `.jxml` files, with highlighting and snippets: no settings needed. It is on the Visual Studio Marketplace as **JXForge (JXML)** (publisher `bacuriim`).
  Without it, install the XML extension from Red Hat (`redhat.vscode-xml`). Treat `.jxml` as XML
  with `"files.associations": { "*.jxml": "xml" }`. The extension reads
  `xsi:noNamespaceSchemaLocation`; or associate the schema for all files in `settings.json`:

  ```json
  "xml.fileAssociations": [
    { "pattern": "**/*.jxml", "systemId": "${workspaceFolder}/target/jxml.xsd" }
  ]
  ```
- **Eclipse.** Add `*.jxml` to the XML content type (Preferences, General, Content Types, Text,
  XML) and open the file with the XML Editor (from Eclipse IDE for Enterprise Java, or the Wild Web
  Developer XML tools). It reads `xsi:noNamespaceSchemaLocation`; or register the schema in
  Preferences, XML, XML Catalog, as a "No namespace" entry for a Schema location.

## What the schema checks

- **Tags and children.** Only the tags of `JxmlSchema`, plus `Include` and `Style`. A tag that takes
  no children has none; `Tab` stands only in `Tabs`; `MenuItem`, `Menu` and `TreeItem` only in the
  parents the table lists; `Menu`/`MenuButton`/`ContextMenu`/`Tree` hold only the tags the table
  lists. `Scroll`, `Button` and the like take one child.
- **Attributes.** Only those of the tag, plus the layout attributes every tag accepts (`row`,
  `column`, `hgrow`, `margin`, `slot`, `background`...), `controller` and `style`. Attributes the
  table marks as required are required (`Icon name`, `IconButton icon`, `For items` and `var`,
  `If test`, `Include src`, `Style name`).
- **Values.** Loosely, by shape:
  - an attribute that `${...}` may bind accepts a binding as well as a literal of its type;
    one that it may not bind accepts only the literal;
  - `onAction`, `onChange` and the other handlers are `#name` or `#name(args)`;
  - numbers, booleans (`true` or `false`), insets (1, 2 or 4 numbers) and colors (`#RGB`,
    `#RRGGBB`, `#AARRGGBB` or a theme token) are checked as such;
  - an attribute with a fixed list of values (`variant`, `kind`, `orientation`, `side`, icon
    names) accepts only those, as the reader does;
  - text and lists (`text`, `items="A, B"`) accept any string.
- **Documentation.** Each element and attribute carries an `xs:documentation` text derived from the
  table: the native element a tag becomes, what it takes as children, the attribute's type, whether it
  can be bound, its allowed values and the property it sets. The table has no prose descriptions,
  so there is no more than that; the language is described in [jxforge.md](jxforge.md).

## What it does not check

The schema describes the shape of a file; the reader and the compiler remain the authority and
report the rest with the file and line.

- Which controller methods and properties a binding or a handler names, and the types of loop
  variables.
- That `controller` and `theme` are written only on the root element, and that `<Style>` stands
  directly inside the root. The schema accepts them wherever the tag is allowed.
- That `<Else>` directly follows an `<If>`.
- What a loop or condition contains: `For`, `If` and `Else` accept any child, since the schema cannot
  see which tag they stand in.
- A required attribute that a `style` supplies is still reported as missing.
- The values of a `<Style>`, which are plain text there (the reader checks them against the tag
  that uses the style).
- Surrounding spaces in a fixed-list value (`variant=" filled"`), which the reader trims and the
  schema does not.

## Tests

`JxmlXsdTest` in `jxparallel-forge` checks that the generated schema is well-formed and is accepted by
`javax.xml.validation.SchemaFactory`; that every tag and attribute of the table is declared; that every sample
screen of the forge tests, and the screens of `jxparallel-examples-native` when that module is beside it,
validate; and that an unknown tag, an unknown attribute, a wrong value and a child that the table does
not allow are rejected. `XsdMojoTest` in `jxforge-maven-plugin` checks the goal writes the file.
