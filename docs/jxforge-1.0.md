# JXForge 1.0: scope checklist

Decision: JXForge 1.0 closes when basically every widget tag that JavaFX FXML has, and that is
commonly used in Flutter (Material) and Jetpack Compose (Material 3), exists in JXML and is
tested. This page is the checklist: the union of the three catalogs, one row per widget.

Out of scope for 1.0: animations and transitions, Hero / shared-element transitions, custom
painters (Canvas, CustomPaint, Compose Canvas), WebView, and OS dialogs (FileChooser).

The "JavaFX FXML" column is only a coverage reference: it says that JXML has an equivalent of
that FXML control. JXML and JXForge use nothing of JavaFX.

## Groups

| Group | Meaning |
|---|---|
| 1 | already a JXML tag |
| 2 | the native renderer draws it (a native element type exists), the tag is missing |
| 3 | a composite of existing primitives: a tag that expands into other tags, or an attribute |
| 4 | needs new drawing in the renderer (`jxparallel-ui`) first |
| 5 | a language feature, not a widget |

Status is `done` only when the tag (or feature) exists in the schema, the interpreter and the
compiler build the same tree for it, and a test covers it. Everything else is `todo`.
"Native element" is the element type the renderer already knows (`JXNativeNode`,
`JXControlLayout`, `JXPaint`); a dash means none yet.

## Widgets

| Widget | Flutter | Compose | JavaFX FXML (reference) | JXML tag | Native element | Group | Status |
|---|---|---|---|---|---|---|---|
| Vertical box | Column | Column | VBox | `Column` | `column` | 1 | done |
| Horizontal box | Row | Row | HBox | `Row` | `row` | 1 | done |
| Overlay | Stack | Box | StackPane | `Stack` | `stack` | 1 | done |
| Border layout | Scaffold body | Scaffold | BorderPane | `Border` | `border` | 1 | done |
| Grid | Table / GridView (static) | Grid (custom) | GridPane | `Grid` | `grid` | 1 | done |
| Scrolling | SingleChildScrollView | verticalScroll | ScrollPane | `Scroll` | `scroll` | 1 | done |
| Flow / wrap | Wrap | FlowRow / FlowColumn | FlowPane | `Flow` (`Wrap`) | `flow` | 2 | done |
| Tile grid | GridView.count | LazyVerticalGrid (fixed) | TilePane | `Tile` | `tile` | 2 | done |
| Absolute anchors | Positioned | Modifier.offset | AnchorPane | `Anchor` | `anchor` | 2 | done |
| Positioned child | Positioned | Modifier.align / offset | AnchorPane constraints | `top` / `right` / `bottom` / `left` on any child of `Anchor`; `Positioned`; `x` / `y` in a `Pane` | `anchor`, `pane` | 2 | done |
| Plain pane | Container (no style) | Box | Pane / Group / Region | `Pane` | `pane` | 2 | done |
| Styled box | Container / DecoratedBox | Surface / Box with background | Region with style | `Box` / `Container`; `background`, `borderColor`, ... on any tag | `stack` | 3 | done |
| Padding | Padding | Modifier.padding | padding | `padding` attribute; `Padding` | `stack` | 3 | done |
| Center | Center | Box contentAlignment | alignment | `alignment` attribute; `Center` | `stack` | 3 | done |
| Align | Align | Modifier.align | StackPane.alignment | `alignment` attribute; `Align` | `stack` | 3 | done |
| Fixed size | SizedBox | Modifier.size | prefWidth / prefHeight | `prefWidth` / `prefHeight`; `SizedBox` | `stack` | 3 | done |
| Expand / flex | Expanded / Flexible | Modifier.weight | hgrow / vgrow | `hgrow` / `vgrow`; `Expanded`, `Flexible` | - | 3 | done |
| Spacer | Spacer | Spacer | Region with grow | `Spacer` | `pane` | 3 | done |
| Split pane | - | - | SplitPane | `SplitPane` | `splitpane` | 4 | done |
| Static text | Text | Text | Label / Text | `Label` | `#text` | 1 | done |
| Rich text | RichText / Text.rich | buildAnnotatedString | TextFlow | `TextFlow` | `textflow` | 2 | done |
| Image | Image | Image | ImageView | `Image` (pixels from Java; no file decoding) | `image` | 2 | done |
| Icon | Icon | Icon | Glyph / Label graphic | `Icon` | `icon` | 4 | done |
| Circle avatar | CircleAvatar | Image + clip | - | `Avatar` | `avatar` | 4 | done |
| Badge | Badge | Badge / BadgedBox | - | `Badge` | `badge` | 4 | done |
| Opacity | Opacity | Modifier.alpha | opacity | `opacity` attribute on any tag; `Opacity` | `stack` | 3 | done |
| Visibility | Visibility | if (visible) | visible | `<If>`, or the `hidden` attribute on any tag; `Visibility` | `stack` | 3 | done |
| Button | ElevatedButton / TextButton | Button / TextButton | Button | `Button` | `button` | 1 | done |
| Button variants | Elevated, Filled, Tonal, Outlined, Text | Filled, Tonal, Outlined, Text, Elevated | - | `Button` with `variant` | `button` | 4 | done |
| Icon button | IconButton | IconButton | Button with graphic | `IconButton` | `button` | 4 | done |
| Floating action button | FloatingActionButton | FloatingActionButton | - | `Fab` | `button` | 4 | done |
| Toggle button | ToggleButtons | SegmentedButton | ToggleButton | `ToggleButton` | `toggle` | 2 | done |
| Toggle group | ToggleButtons (selection) | SingleChoiceSegmentedButtonRow | ToggleGroup | `group` and `value` on `ToggleButton` / `RadioButton` | `toggle`, `radio` | 5 | done |
| Link | - | ClickableText | Hyperlink | `Hyperlink` | `hyperlink` | 2 | done |
| Button bar | OverflowBar | Row of buttons | ButtonBar | `ButtonBar` | `buttonbar` | 2 | done |
| Text field | TextField | TextField / OutlinedTextField | TextField | `TextField` | `input` | 1 | done |
| Password field | TextField(obscureText) | OutlinedTextField + PasswordVisualTransformation | PasswordField | `PasswordField` | `password` | 1 | done |
| Text area | TextField(maxLines) | TextField(minLines) | TextArea | `TextArea` | `textarea` | 1 | done |
| Check box | Checkbox | Checkbox | CheckBox | `CheckBox` | `checkbox` | 1 | done |
| Switch | Switch | Switch | - | `Switch` | `switch` | 4 | done |
| Radio button | Radio | RadioButton | RadioButton | `RadioButton` (alone, or with `group`) | `radio` | 2 | done |
| Slider | Slider | Slider | Slider | `Slider` | `slider` | 1 | done |
| Scroll bar | Scrollbar | VerticalScrollbar | ScrollBar | `ScrollBar` | `scrollbar` | 4 | done |
| Combo box | DropdownButton | ExposedDropdownMenuBox | ComboBox | `ComboBox` (the option list opens with `showing`) | `select` | 1 | done |
| Choice box | DropdownButton | ExposedDropdownMenuBox | ChoiceBox | `ChoiceBox` (as `ComboBox`) | `select` | 3 | done |
| Dropdown menu | DropdownMenu / PopupMenuButton | DropdownMenu | MenuButton | `ComboBox` / `MenuButton` | `select`, `menubutton` | 4 | done |
| Spinner | - | - | Spinner | `Spinner` | `spinner` | 1 | done |
| Date picker | showDatePicker | DatePicker | DatePicker | `DatePicker` (the calendar opens with `showing`) | `datepicker` | 1 | done |
| Time picker | showTimePicker | TimePicker | - | `TimePicker` | `timepicker` | 4 | done |
| Color picker | - | - | ColorPicker | `ColorPicker` | `colorpicker` | 4 | done |
| Progress bar | LinearProgressIndicator | LinearProgressIndicator | ProgressBar | `ProgressBar` | `progress` | 1 | done |
| Progress circle | CircularProgressIndicator | CircularProgressIndicator | ProgressIndicator | `ProgressIndicator` | `indicator` | 2 | done |
| Divider | Divider | Divider | Separator | `Separator` (`Divider`) | `separator` | 1 | done |
| Chip | Chip / FilterChip / ChoiceChip | AssistChip / FilterChip / InputChip | - | `Chip` | `chip` | 4 | done |
| Card | Card | Card / ElevatedCard | - | `Card` (no shadow) | `column` | 3 | done |
| List (virtual) | ListView | LazyColumn / LazyRow | ListView | `ListView` | `list` | 2 | done |
| Grid (virtual) | GridView | LazyVerticalGrid | - | `Tile` + `<For>` (the `list` element has no columns) | `tile` | 3 | done |
| List item | ListTile | ListItem | ListCell | `ListItem`, `ListTile` | `cell`, `row` | 3 | done |
| Table | DataTable | - | TableView / TableColumn | `TableView`, `TableRow`, `TableCell` | `table`, `tablerow`, `cell` | 2 | done |
| Tree | - | - | TreeView | `Tree`, `TreeItem` | `list` of `cell` rows (with `indent` and `disclosure`) | 4 | done |
| Tree table | - | - | TreeTableView | `TreeTable`, `TreeItem` | `table` of `tablerow`s of `cell`s | 4 | done |
| Tab pane | TabBarView | HorizontalPager | TabPane / Tab | `Tabs` / `Tab` | `tabs` | 1 | done |
| Tab bar | TabBar | TabRow | TabPane header | `TabBar` | `tabbar` | 4 | done |
| Accordion | ExpansionPanelList | - | Accordion | `Accordion` | `accordion` | 2 | done |
| Titled pane | ExpansionTile | - | TitledPane | `TitledPane` | `titled` | 2 | done |
| Pagination | PageView + indicator | HorizontalPager | Pagination | `Pagination` | `pagination` | 2 | done |
| Stepper | Stepper | - | - | `Stepper` | `stepper` | 4 | done |
| Scaffold | Scaffold | Scaffold | BorderPane | `Scaffold` (as `Border`) | `border` | 3 | done |
| Top bar | AppBar | TopAppBar | ToolBar | `AppBar` | `row` | 3 | done |
| Tool bar | - | - | ToolBar | `ToolBar` | `row` | 3 | done |
| Bottom bar | BottomAppBar | BottomAppBar | - | `BottomAppBar` (`BottomBar`) | `row` | 3 | done |
| Bottom navigation | BottomNavigationBar / NavigationBar | NavigationBar | - | `NavigationBar` | `row` | 3 | done |
| Navigation rail | NavigationRail | NavigationRail | - | `NavigationRail` | `column` | 3 | done |
| Drawer | Drawer | ModalNavigationDrawer | - | `Drawer` | `drawer` | 4 | done |
| Menu bar | MenuBar | - | MenuBar / Menu / MenuItem | `MenuBar`, `Menu`, `MenuItem`, `MenuSeparator` | `menubar`, `menu`, `menuitem` | 4 | done |
| Context menu | PopupMenuButton | DropdownMenu | ContextMenu | `ContextMenu` | `contextmenu` | 4 | done |
| Tooltip | Tooltip | TooltipBox | Tooltip | `Tooltip` (drawn where it is placed; no hover trigger) | `tooltip` | 2 | done |
| Dialog | Dialog / AlertDialog | AlertDialog / Dialog | Dialog / Alert | `Dialog` / `AlertDialog` (made of `Modal`) | `dialog` | 4 | done |
| Snack bar | SnackBar | Snackbar | - | `SnackBar` | `snackbar` | 4 | done |
| File chooser | file_picker (plugin) | ActivityResult | FileChooser | - | - | - | out of scope (OS dialog) |
| Canvas | CustomPaint | Canvas | Canvas | - | - | - | out of scope (custom painters) |
| Web view | WebView (plugin) | AndroidView | WebView | - | - | - | out of scope |
| Animation | AnimatedContainer, Hero | AnimatedVisibility | Transitions | - | - | - | out of scope |
| Slot for Java widgets | - | - | fx:include / custom | `Slot` | any | 1 | done |

## Language features

| Feature | Syntax | Group | Status |
|---|---|---|---|
| Loop, item templates | `<For items="${orders}" var="o" type="com.acme.Order">` with any children; `${o.total}`, `#open(o)` | 5 | done |
| Loop keys | `key="${o.id}"` on `<For>` | 5 | done |
| Loop window | `<For items="${orders}" var="o" from="${first}" limit="30">` builds 30 items from `first` | 5 | done |
| Condition | `<If test="${admin}">` ... `<Else>` ... | 5 | done |
| Negation | `test="${!admin}"` | 5 | done |
| Change events | `onChange="#setName"` on inputs, checkbox, slider, combo box, spinner, date picker, time picker, color picker | 5 | done |
| Two-way binding | `text="@{customer.name}"` | 5 | done |
| Nested loops, shadowed loop variables | `<For>` inside `<For>` | 5 | done |
| Styles and themes | `<Style name="primary" background="primary"/>`, `style="primary"`, `theme="dark"` on the root | 5 | done |
| Reusable screen parts | `<Include src="parts/header.jxml"/>` | 5 | done |
| Development mode | the interpreter reloading on file save: `JxmlLiveView` | 5 | done |

## What phase 1 delivers

Marked `done` above: `For`, `If` / `Else`, change events and two-way binding, with interpreter,
compiler, schema, tests and the documentation in [jxforge.md](jxforge.md). Every widget row
marked `todo` is a later phase; rows of group 2 come first because the renderer already draws them.

## What phase 2 delivers

Every row of group 2 and group 3 is `done` except the three moved to group 4 below; each has a
sample in `jxparallel-forge/src/test/shared/samples`, which the forge tests build with the interpreter
and the compiler (hoisting on and off) and `jxparallel-examples-native` mounts on the native renderer.
Rows that came out of group 4 or 5 because the renderer already reads what they need:

| Row | Why it is not group 4 |
|---|---|
| Styled box, Card | `JXPaint.region` paints `background`, `borderColor` and their radii for any node (no shadow) |
| Opacity | `JXPaint.paint` reads `opacity` of any node |
| Visibility | `JXPaint.paint` skips a node with `hidden`, which keeps its place in the layout |
| Toggle group | the group is state of the application: `group` and `value` derive `checked` / `selected` |

Rows moved to group 4:

| Row | Reason |
|---|---|
| Dropdown menu (`MenuButton`) | no menu-button element exists, and the popups of `select` and `datepicker` are not drawn yet (phase 3B draws them) |
| Tab bar | a header strip whose content lives elsewhere has no element: a `<Tab>` needs its content as a child, and the renderer reports no header clicks (`Tabs` is the tab pane, row "Tab pane") |
| Stepper | the active and done look of a step depends on a bound step index, which a composite (expanded when the file is read) cannot compute; it needs a derived-props step like `ListView`'s, and a design for the step markers |

Limits of the renderer that rows marked `done` carry (see "Not done yet" in [jxforge.md](jxforge.md)):
`ListView` and `TableView` report no selection or scroll events (a row's `onAction` and the
application's `first` do that), `Pagination` and `TitledPane` change only when the application changes
their props, and `Tooltip` has no hover trigger.

## What phase 3A delivers

The rows above marked done in this phase: Switch, Icon (31 built-in vector icons), Button variants, IconButton, Fab, Chip, Badge, Avatar, Stepper, ScrollBar, SplitPane, TabBar, and the language features Styles and themes and Reusable screen parts. Each has a schema entry, a native element in the renderer (switch, icon, chip, badge, avatar, stepper, scrollbar, splitpane, tabbar; IconButton and Fab are composites of button and icon) drawn only through the existing JXPainter primitives, a sample in jxparallel-forge/src/test/shared/samples (material.jxml, includes.jxml, parts/), tests in the forge, the renderer (JXMaterialControlsTest) and the mount tests, and documentation in [jxforge.md](jxforge.md).

Limits: dragging a scroll bar thumb or a divider with a real mouse needs the window to pass the move events (JXWindow does when it has no input listener; a host with its own listener calls JXNativeNode.dispatchDrag); the headless tests drive dispatchPointer and dispatchDrag directly. The renderer draws a shadow as a soft darker ring, because not every painter has a blur. Dark theme re-colors the new controls, the themed root surface and the default text of Label; the classic Modena controls keep their look.

## What phase 3B delivers

The last rows: everything that opens over the window, the trees and the development mode. One mechanism serves the
overlays (see "Overlays" in [jxforge.md](jxforge.md)): the popups of `ComboBox`, `DatePicker`, `TimePicker` and
`ColorPicker`, the menus of `MenuButton`, `ContextMenu` and `MenuBar`, and the layers `Dialog` / `AlertDialog`,
`Drawer` and `SnackBar`. They are drawn after everything else through the existing `JXPainter` primitives (no new
primitive in any painter), take the pointer before everything else, and close on a press outside and on Escape.

Each has a schema entry, a native element and its drawing in `jxparallel-ui` (`JXOverlays`, `JXOverlayPaint`), a sample in
`jxparallel-forge/src/test/shared/samples` (`pickers.jxml`, `menus.jxml`, `layers.jxml`, `trees.jxml`), tests in the forge
(`JxmlOverlaysTest`, `JxmlTagsTest`), the renderer (`JXOverlaysTest`) and the mount tests (`ForgeTagsMountTest`).

Limits:

- A `ComboBox` or `DatePicker` draws its popup only with the prop `overlay` (the tags write it): the JavaFX bridge sends
  the same two elements with `showing` and draws their popups as elements of its own.
- Keyboard navigation inside the popups is not there: Up / Down on the focused combo, date and time picker change the
  value as before, Escape and Enter close; menus are used with the pointer. There is no hover highlight (the renderer
  reports no hover).
- The popup of a `ComboBox` shows at most ten options and scrolls to the chosen one; it has no scroll bar.
- A `Tree` or `TreeTable` builds every item of its model on each render (like JavaFX's tree model) and gives the renderer
  only the window of rows; it is virtual in the renderer, not in the build. The model is nested items (with `<For>` and
  `<If>` inside them); there is no recursive `children="${...}"` attribute: a model of unbounded depth is a
  loop per level, or a flat list of rows with their own depth in a `ListView`.
- A `SnackBar`'s `duration` is carried by the node and the window's loop asks the tree about it once per turn
  (`JXNativeNode.tickOverlays`); the snack bar asks to close through `onShowing`, the application hides it.
- A dialog is centered and sized to its content (at most the window less a margin); it does not move or resize.
- Development mode watches files with `java.nio.file.WatchService` (on macOS the JDK polls, so a save can take seconds).
