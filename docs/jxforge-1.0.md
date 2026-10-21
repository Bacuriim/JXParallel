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
| Flow / wrap | Wrap | FlowRow / FlowColumn | FlowPane | `Flow` | `flow` | 2 | todo |
| Tile grid | GridView.count | LazyVerticalGrid (fixed) | TilePane | `Tile` | `tile` | 2 | todo |
| Absolute anchors | Positioned | Modifier.offset | AnchorPane | `Anchor` | `anchor` | 2 | todo |
| Positioned child | Positioned | Modifier.align / offset | AnchorPane constraints | `Anchor` children with `top`/`left`/... | `anchor` | 2 | todo |
| Plain pane | Container (no style) | Box | Pane / Group / Region | `Pane` | `pane` | 2 | todo |
| Styled box | Container / DecoratedBox | Surface / Box with background | Region with style | `Box` (color, border, radius) | - | 4 | todo |
| Padding | Padding | Modifier.padding | padding | `padding` attribute | - | 3 | done |
| Center | Center | Box contentAlignment | alignment | `alignment` attribute | - | 3 | done |
| Align | Align | Modifier.align | StackPane.alignment | `alignment` attribute | - | 3 | done |
| Fixed size | SizedBox | Modifier.size | prefWidth / prefHeight | `prefWidth` / `prefHeight` | - | 3 | done |
| Expand / flex | Expanded / Flexible | Modifier.weight | hgrow / vgrow | `hgrow` / `vgrow` | - | 3 | done |
| Spacer | Spacer | Spacer | Region with grow | `Spacer` | `pane` | 3 | todo |
| Split pane | - | - | SplitPane | `SplitPane` | - | 4 | todo |
| Static text | Text | Text | Label / Text | `Label` | `#text` | 1 | done |
| Rich text | RichText / Text.rich | buildAnnotatedString | TextFlow | `TextFlow` | `textflow` | 2 | todo |
| Image | Image | Image | ImageView | `Image` | `image` | 2 | todo |
| Icon | Icon | Icon | Glyph / Label graphic | `Icon` | - | 4 | todo |
| Circle avatar | CircleAvatar | Image + clip | - | `Avatar` | - | 4 | todo |
| Badge | Badge | Badge / BadgedBox | - | `Badge` | - | 4 | todo |
| Opacity | Opacity | Modifier.alpha | opacity | `opacity` attribute | - | 4 | todo |
| Visibility | Visibility | if (visible) | visible | `<If>` or `visible` attribute | - | 5 | todo |
| Button | ElevatedButton / TextButton | Button / TextButton | Button | `Button` | `button` | 1 | done |
| Button variants | Elevated, Filled, Tonal, Outlined, Text | Filled, Tonal, Outlined, Text, Elevated | - | `Button` with `variant` | `button` | 4 | todo |
| Icon button | IconButton | IconButton | Button with graphic | `IconButton` | `button` | 4 | todo |
| Floating action button | FloatingActionButton | FloatingActionButton | - | `Fab` | - | 4 | todo |
| Toggle button | ToggleButtons | SegmentedButton | ToggleButton | `ToggleButton` | `toggle` | 2 | todo |
| Toggle group | ToggleButtons (selection) | SingleChoiceSegmentedButtonRow | ToggleGroup | `ToggleGroup` | - | 5 | todo |
| Link | - | ClickableText | Hyperlink | `Hyperlink` | `hyperlink` | 2 | todo |
| Button bar | OverflowBar | Row of buttons | ButtonBar | `ButtonBar` | `buttonbar` | 2 | todo |
| Text field | TextField | TextField / OutlinedTextField | TextField | `TextField` | `input` | 1 | done |
| Password field | TextField(obscureText) | OutlinedTextField + PasswordVisualTransformation | PasswordField | `PasswordField` | `password` | 1 | done |
| Text area | TextField(maxLines) | TextField(minLines) | TextArea | `TextArea` | `textarea` | 1 | done |
| Check box | Checkbox | Checkbox | CheckBox | `CheckBox` | `checkbox` | 1 | done |
| Switch | Switch | Switch | - | `Switch` | - | 4 | todo |
| Radio button | Radio | RadioButton | RadioButton | `RadioButton` | `radio` | 2 | todo |
| Slider | Slider | Slider | Slider | `Slider` | `slider` | 1 | done |
| Scroll bar | Scrollbar | VerticalScrollbar | ScrollBar | `ScrollBar` | - | 4 | todo |
| Combo box | DropdownButton | ExposedDropdownMenuBox | ComboBox | `ComboBox` | `select` | 1 | done |
| Choice box | DropdownButton | ExposedDropdownMenuBox | ChoiceBox | `ChoiceBox` (as `ComboBox`) | `select` | 3 | todo |
| Dropdown menu | DropdownMenu / PopupMenuButton | DropdownMenu | MenuButton | `ComboBox` / `MenuButton` | `select`, `popup` | 3 | todo |
| Spinner | - | - | Spinner | `Spinner` | `spinner` | 1 | done |
| Date picker | showDatePicker | DatePicker | DatePicker | `DatePicker` | `datepicker` | 1 | done |
| Time picker | showTimePicker | TimePicker | - | `TimePicker` | - | 4 | todo |
| Color picker | - | - | ColorPicker | `ColorPicker` | - | 4 | todo |
| Progress bar | LinearProgressIndicator | LinearProgressIndicator | ProgressBar | `ProgressBar` | `progress` | 1 | done |
| Progress circle | CircularProgressIndicator | CircularProgressIndicator | ProgressIndicator | `ProgressIndicator` | `indicator` | 2 | todo |
| Divider | Divider | Divider | Separator | `Separator` | `separator` | 1 | done |
| Chip | Chip / FilterChip / ChoiceChip | AssistChip / FilterChip / InputChip | - | `Chip` | - | 4 | todo |
| Card | Card | Card / ElevatedCard | - | `Card` | - | 4 | todo |
| List (virtual) | ListView | LazyColumn / LazyRow | ListView | `List` | `list` | 2 | todo |
| Grid (virtual) | GridView | LazyVerticalGrid | - | `List` with columns, or `Tile` + `<For>` | `list`, `tile` | 3 | todo |
| List item | ListTile | ListItem | ListCell | `ListItem` | `cell` | 3 | todo |
| Table | DataTable | - | TableView / TableColumn | `Table` / `Column` cells | `table`, `tablerow` | 2 | todo |
| Tree | - | - | TreeView | `Tree` | - | 4 | todo |
| Tree table | - | - | TreeTableView | `TreeTable` | - | 4 | todo |
| Tab pane | TabBarView | HorizontalPager | TabPane / Tab | `Tabs` / `Tab` | `tabs` | 1 | done |
| Tab bar | TabBar | TabRow | TabPane header | `Tabs` | `tabs` | 3 | todo |
| Accordion | ExpansionPanelList | - | Accordion | `Accordion` | `accordion` | 2 | todo |
| Titled pane | ExpansionTile | - | TitledPane | `TitledPane` | `titled` | 2 | todo |
| Pagination | PageView + indicator | HorizontalPager | Pagination | `Pagination` | `pagination` | 2 | todo |
| Stepper | Stepper | - | - | `Stepper` | - | 3 | todo |
| Scaffold | Scaffold | Scaffold | BorderPane | `Scaffold` (as `Border`) | `border` | 3 | todo |
| Top bar | AppBar | TopAppBar | ToolBar | `AppBar` | `row` | 3 | todo |
| Tool bar | - | - | ToolBar | `ToolBar` | `row` | 3 | todo |
| Bottom bar | BottomAppBar | BottomAppBar | - | `BottomBar` | `row` | 3 | todo |
| Bottom navigation | BottomNavigationBar / NavigationBar | NavigationBar | - | `NavigationBar` | `row` | 3 | todo |
| Navigation rail | NavigationRail | NavigationRail | - | `NavigationRail` | `column` | 3 | todo |
| Drawer | Drawer | ModalNavigationDrawer | - | `Drawer` | `popup` | 4 | todo |
| Menu bar | MenuBar | - | MenuBar / Menu / MenuItem | `MenuBar` | - | 4 | todo |
| Context menu | PopupMenuButton | DropdownMenu | ContextMenu | `ContextMenu` | `popup` | 4 | todo |
| Tooltip | Tooltip | TooltipBox | Tooltip | `Tooltip` | `tooltip` | 2 | todo |
| Dialog | Dialog / AlertDialog | AlertDialog / Dialog | Dialog / Alert | `Dialog` / `AlertDialog` | `popup` | 4 | todo |
| Snack bar | SnackBar | Snackbar | - | `SnackBar` | `popup` | 4 | todo |
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
| Condition | `<If test="${admin}">` ... `<Else>` ... | 5 | done |
| Negation | `test="${!admin}"` | 5 | done |
| Change events | `onChange="#setName"` on inputs, checkbox, slider, combo box, spinner, date picker | 5 | done |
| Two-way binding | `text="@{customer.name}"` | 5 | done |
| Nested loops, shadowed loop variables | `<For>` inside `<For>` | 5 | done |
| Styles and themes | shared style tokens | 5 | todo |
| Reusable screen parts | include another `.jxml` | 5 | todo |
| Development mode | the interpreter reloading on file save | 5 | todo |

## What phase 1 delivers

Marked `done` above: `For`, `If` / `Else`, change events and two-way binding, with interpreter,
compiler, schema, tests and the documentation in [jxforge.md](jxforge.md). Every widget row
marked `todo` is a later phase; rows of group 2 come first because the renderer already draws them.
