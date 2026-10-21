"""Generates the per-component benchmark samples of JXForge: one JXML screen and one controller per component.

Every sample is a Column holding a <For> that makes N instances of one component with realistic attributes and one
bound value per instance (text="${i}"), so builds and bindings are both exercised. The JavaFX reference runner
(jxparallel-examples ComponentBenchFx) builds the same N instances in code; the table below is the single place that
says which component maps to which JavaFX class, with how many instances, and what the update stage changes.

Output (checked in, rerun only to change the table):
  jxparallel-examples-native/src/main/jxml/components/<Name>.jxml
  jxparallel-examples-native/src/main/java/com/jxparallel/examples/nativeui/components/<Name>Controller.java
  jxparallel-examples-native/src/main/java/com/jxparallel/examples/nativeui/ComponentBenchViews.java
  jxparallel-examples-native/src/main/resources/componentbench/components.csv   (read by ComponentBench)
  jxparallel-examples/src/main/resources/componentbench/components.csv         (read by ComponentBenchFx)

N per component comes from scripts/component-bench-n.csv (name,n; written after the pilot, see the report) and falls
back to the default of its group.
"""
import csv
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
NATIVE = ROOT / "jxparallel-examples-native" / "src" / "main"
FX = ROOT / "jxparallel-examples" / "src" / "main"
PKG = "com.jxparallel.examples.nativeui.components"
N_FILE = Path(__file__).resolve().parent / "component-bench-n.csv"

DEFAULT_N = {"inputs": 300, "buttons": 300, "containers": 200, "lists": 10, "overlays": 100, "material": 300}

# kind of the loop variable -> (controller getter, Java type)
KINDS = {
    "string": ("items", "java.lang.String"),
    "digits": ("digits", "java.lang.String"),
    "dates": ("dates", "java.lang.String"),
    "colors": ("colors", "java.lang.String"),
    "times": ("times", "java.lang.String"),
    "initials": ("initials", "java.lang.String"),
    "numbers": ("numbers", "java.lang.Double"),
    "fractions": ("fractions", "java.lang.Double"),
    "widths": ("widths", "java.lang.Double"),
    "sizes": ("sizes", "java.lang.Double"),
    "ints": ("ints", "java.lang.Integer"),
    "steps": ("steps", "java.lang.Integer"),
    "pages": ("pages", "java.lang.Integer"),
    "flags": ("flags", "java.lang.Boolean"),
    "firsts": ("firsts", "java.lang.Integer"),
}

COMPONENTS = []


def add(name, group, kind, body, probe, update, fx="", note="", alias_of="", variant="closed", tag=None, root_attrs=""):
    """name: file name; body: the XML of one instance with ${i}; probe: (native type, prop, s|d|i|b) read from the
    mounted nodes; update: the prop the update stage changes; fx: the key of the JavaFX case ('' = none, note says why)."""
    COMPONENTS.append(dict(name=name, tag=tag or name.replace("Open", "") if variant == "open" else (tag or name),
                           group=group, kind=kind, body=body, probe=probe, update=update, fx=fx, note=note,
                           alias_of=alias_of, variant=variant, root_attrs=root_attrs))


T = ("#text", "value", "s")
L3 = '<Label text="${i}"/><Label text="b"/><Label text="c"/>'

# ---- inputs and display -----------------------------------------------------------------------------------
add("Label", "inputs", "string", '<Label text="${i}"/>', T, "text", fx="Label")
add("Hyperlink", "inputs", "string", '<Hyperlink text="${i}"/>', ("hyperlink", "label", "s"), "text", fx="Hyperlink")
add("TextField", "inputs", "string", '<TextField text="${i}" prompt="Type here" columns="12"/>', ("input", "value", "s"), "text", fx="TextField")
add("PasswordField", "inputs", "string", '<PasswordField text="${i}" prompt="Secret" columns="12"/>', ("password", "value", "s"), "text", fx="PasswordField")
add("TextArea", "inputs", "string", '<TextArea text="${i}" prompt="Notes" columns="20" rows="3"/>', ("textarea", "value", "s"), "text", fx="TextArea")
add("ComboBox", "inputs", "string", '<ComboBox items="${choices}" value="${i}" prompt="Choose"/>', ("select", "value", "s"), "value", fx="ComboBox")
add("ChoiceBox", "inputs", "string", '<ChoiceBox items="${choices}" value="${i}"/>', ("select", "value", "s"), "value", fx="ChoiceBox", alias_of="ComboBox",
    note="a pure rename of ComboBox: same tree; the JavaFX counterpart is the ChoiceBox class")
add("Spinner", "inputs", "digits", '<Spinner min="0" max="100" value="${i}"/>', ("spinner", "value", "s"), "value", fx="Spinner")
add("DatePicker", "inputs", "dates", '<DatePicker value="${i}" prompt="Date"/>', ("datepicker", "value", "s"), "value", fx="DatePicker")
add("TimePicker", "inputs", "times", '<TimePicker value="${i}" prompt="HH:mm" hour24="true" minuteStep="15"/>', ("timepicker", "value", "s"), "value",
    note="no JavaFX equivalent (JavaFX has no time picker)")
add("ColorPicker", "inputs", "colors", '<ColorPicker value="${i}" prompt="#RRGGBB"/>', ("colorpicker", "value", "s"), "value", fx="ColorPicker")
add("Slider", "inputs", "numbers", '<Slider min="0" max="100" value="${i}"/>', ("slider", "value", "d"), "value", fx="Slider")
add("ScrollBar", "inputs", "numbers", '<ScrollBar min="0" max="100" value="${i}" visibleAmount="10" orientation="horizontal"/>', ("scrollbar", "value", "d"), "value", fx="ScrollBar")
add("ProgressBar", "inputs", "fractions", '<ProgressBar progress="${i}" prefWidth="200"/>', ("progress", "progress", "d"), "progress", fx="ProgressBar")
add("ProgressIndicator", "inputs", "fractions", '<ProgressIndicator progress="${i}"/>', ("indicator", "progress", "d"), "progress", fx="ProgressIndicator")
add("Separator", "inputs", "widths", '<Separator orientation="horizontal" prefWidth="${i}"/>', ("separator", "prefWidth", "d"), "prefWidth", fx="Separator")
add("Divider", "inputs", "widths", '<Divider prefWidth="${i}"/>', ("separator", "prefWidth", "d"), "prefWidth", fx="Separator", alias_of="Separator",
    note="a pure rename of Separator: same tree")
add("Image", "inputs", "widths", '<Image pixels="${pixels}" imageWidth="16" imageHeight="16" fitWidth="${i}" fitHeight="32" preserveRatio="false"/>',
    ("image", "fitWidth", "d"), "fitWidth", fx="ImageView")
add("Tooltip", "inputs", "string", '<Tooltip text="${i}"/>', ("tooltip", "label", "s"), "text",
    note="JavaFX Tooltip is a popup window object, not a scene node: it has no layout or paint without a window")

# ---- buttons and toggles ----------------------------------------------------------------------------------
add("Button", "buttons", "string", '<Button text="${i}"/>', ("button", "label", "s"), "text", fx="Button")
add("ToggleButton", "buttons", "string", '<ToggleButton text="${i}"/>', ("toggle", "label", "s"), "text", fx="ToggleButton")
add("RadioButton", "buttons", "string", '<RadioButton text="${i}"/>', ("radio", "label", "s"), "text", fx="RadioButton")
add("CheckBox", "buttons", "string", '<CheckBox text="${i}"/>', ("checkbox", "label", "s"), "text", fx="CheckBox")
add("IconButton", "buttons", "flags", '<IconButton icon="add" disabled="${i}"/>', ("button", "disabled", "b"), "disabled",
    note="no JavaFX equivalent: the icon is a vector drawn by the renderer; JavaFX would need a graphic node built by hand")
add("Fab", "buttons", "string", '<Fab icon="add" text="${i}"/>', ("button", "label", "s"), "text",
    note="no JavaFX equivalent (floating action button)")

# ---- Material controls ------------------------------------------------------------------------------------
add("Switch", "material", "string", '<Switch text="${i}"/>', ("switch", "label", "s"), "text", note="no JavaFX equivalent")
add("Icon", "material", "sizes", '<Icon name="home" size="${i}"/>', ("icon", "size", "d"), "size", note="no JavaFX equivalent (built-in vector icons)")
add("Chip", "material", "string", '<Chip text="${i}" kind="assist"/>', ("chip", "label", "s"), "text", note="no JavaFX equivalent")
add("Badge", "material", "ints", '<Badge count="${i}" max="99"><Label text="x"/></Badge>', ("badge", "count", "i"), "count", note="no JavaFX equivalent")
add("Avatar", "material", "initials", '<Avatar initials="${i}" size="40"/>', ("avatar", "initials", "s"), "initials", note="no JavaFX equivalent")
add("Stepper", "material", "steps", '<Stepper steps="4" current="${i}"/>', ("stepper", "current", "i"), "current", note="no JavaFX equivalent")
add("TabBar", "material", "steps", '<TabBar titles="${titles}" selected="${i}"/>', ("tabbar", "selected", "i"), "selected",
    note="no JavaFX equivalent: a TabPane always includes the content area; the TabBar is only the header strip")
add("Card", "material", "string", '<Card><Label text="${i}"/><Label text="b"/></Card>', T, "text", note="no JavaFX equivalent (a styled Column)")
add("ListTile", "material", "string", '<ListTile title="${i}" subtitle="sub"><Label slot="leading" text="L"/><Button slot="trailing" text="T"/></ListTile>',
    T, "title", note="no JavaFX equivalent")
add("AppBar", "material", "string", '<AppBar title="${i}"><Button text="Menu"/></AppBar>', T, "title", note="no JavaFX equivalent")
add("NavigationBar", "material", "string", '<NavigationBar><Button text="${i}"/><Button text="b"/><Button text="c"/></NavigationBar>',
    ("button", "label", "s"), "text", note="no JavaFX equivalent")
add("NavigationRail", "material", "string", '<NavigationRail><Button text="${i}"/><Button text="b"/></NavigationRail>',
    ("button", "label", "s"), "text", note="no JavaFX equivalent")
add("BottomAppBar", "material", "string", '<BottomAppBar><Label text="${i}"/><Label text="b"/></BottomAppBar>', T, "text", note="no JavaFX equivalent")
add("BottomBar", "material", "string", '<BottomBar><Label text="${i}"/><Label text="b"/></BottomBar>', T, "text", alias_of="BottomAppBar",
    note="a pure rename of BottomAppBar: same tree; no JavaFX equivalent")
add("Scaffold", "material", "string", '<Scaffold prefHeight="80"><AppBar title="${i}"/><Label text="body"/><BottomAppBar><Label text="s"/></BottomAppBar></Scaffold>',
    T, "title", note="no JavaFX equivalent (a BorderPane with bars)")

# ---- containers and layout --------------------------------------------------------------------------------
add("Column", "containers", "string", '<Column gap="4">' + L3 + '</Column>', T, "text", fx="Column")
add("Row", "containers", "string", '<Row gap="4">' + L3 + '</Row>', T, "text", fx="Row")
add("Stack", "containers", "string", '<Stack alignment="CENTER">' + L3 + '</Stack>', T, "text", fx="Stack")
add("Grid", "containers", "string",
    '<Grid hgap="4" vgap="4"><Label text="${i}" row="0" column="0"/><Label text="b" row="0" column="1"/><Label text="c" row="1" column="0"/></Grid>', T, "text", fx="Grid")
add("Border", "containers", "string",
    '<Border><Label slot="top" text="${i}"/><Label slot="center" text="b"/><Label slot="bottom" text="c"/></Border>', T, "text", fx="Border")
add("Scroll", "containers", "string", '<Scroll fitToWidth="true" prefHeight="40"><Label text="${i}"/></Scroll>', T, "text", fx="Scroll")
add("Flow", "containers", "string", '<Flow hgap="4" vgap="4" prefWrapLength="200">' + L3 + '</Flow>', T, "text", fx="Flow")
add("Tile", "containers", "string",
    '<Tile hgap="4" vgap="4" prefColumns="3" prefTileWidth="60" prefTileHeight="24">' + L3 + '</Tile>', T, "text", fx="Tile")
add("Anchor", "containers", "string",
    '<Anchor prefWidth="200" prefHeight="60"><Label text="${i}" top="4" left="4"/><Label text="b" right="4" bottom="4"/><Label text="c" top="30" left="50"/></Anchor>',
    T, "text", fx="Anchor")
add("Pane", "containers", "string",
    '<Pane prefWidth="200" prefHeight="60"><Label text="${i}" x="10" y="4"/><Label text="b" x="60" y="20"/><Label text="c" x="110" y="36"/></Pane>', T, "text", fx="Pane")
add("TextFlow", "containers", "string", '<TextFlow><Label text="${i}"/><Label text="b"/><Hyperlink text="c"/></TextFlow>', T, "text", fx="TextFlow")
add("ButtonBar", "containers", "string", '<ButtonBar><Button text="${i}"/><Button text="b"/><Button text="c"/></ButtonBar>', ("button", "label", "s"), "text", fx="ButtonBar")
add("Accordion", "containers", "string",
    '<Accordion><TitledPane text="${i}" expanded="true"><Label text="a"/></TitledPane><TitledPane text="b" expanded="false"><Label text="c"/></TitledPane></Accordion>',
    ("titled", "label", "s"), "text", fx="Accordion")
add("TitledPane", "containers", "string", '<TitledPane text="${i}" expanded="true" collapsible="true"><Label text="a"/></TitledPane>',
    ("titled", "label", "s"), "text", fx="TitledPane")
add("Pagination", "containers", "pages", '<Pagination pageCount="10" current="${i}"><Label text="page"/></Pagination>',
    ("pagination", "current", "i"), "current", fx="Pagination")
add("Tabs", "containers", "string",
    '<Tabs selected="0" prefHeight="60"><Tab title="One" closable="false"><Label text="${i}"/></Tab><Tab title="Two" closable="false"><Label text="b"/></Tab>'
    '<Tab title="Three" closable="false"><Label text="c"/></Tab></Tabs>', T, "text", fx="Tabs")
add("SplitPane", "containers", "string",
    '<SplitPane orientation="horizontal" dividerPositions="0.3, 0.7" prefHeight="40">' + L3 + '</SplitPane>', T, "text", fx="SplitPane")
add("ToolBar", "containers", "string", '<ToolBar><Button text="${i}"/><Button text="b"/><Button text="c"/></ToolBar>', ("button", "label", "s"), "text", fx="ToolBar",
    note="the JXML ToolBar is a styled Row; the JavaFX ToolBar is a control with overflow handling: not equal work")
add("MenuBar", "containers", "string",
    '<MenuBar><Menu text="${i}"><MenuItem text="a"/><MenuItem text="b"/></Menu><Menu text="Edit"><MenuItem text="c"/><MenuItem text="d"/></Menu></MenuBar>',
    ("menu", "label", "s"), "text", fx="MenuBar")
add("SizedBox", "containers", "widths", '<SizedBox width="${i}" height="20"><Label text="x"/></SizedBox>', ("stack", "minWidth", "d"), "width",
    note="no JavaFX equivalent: JavaFX sets sizes on any Region, there is no separate control")
add("Spacer", "containers", "string", '<Row><Label text="${i}"/><Spacer/><Label text="b"/></Row>', T, "text",
    note="no JavaFX equivalent: a Region with grow priorities")
add("Expanded", "containers", "string", '<Row><Label text="a"/><Expanded><Label text="${i}"/></Expanded></Row>', T, "text",
    note="no JavaFX equivalent: grow priorities on the child")
add("Flexible", "containers", "string", '<Row><Label text="a"/><Flexible><Label text="${i}"/></Flexible></Row>', T, "text",
    note="no JavaFX equivalent: grow priorities on the child")
add("Center", "containers", "string", '<Center><Label text="${i}"/></Center>', T, "text", note="no JavaFX equivalent: a StackPane with alignment")
add("Align", "containers", "string", '<Align alignment="BOTTOM_RIGHT"><Label text="${i}"/></Align>', T, "text", note="no JavaFX equivalent: a StackPane with alignment")
add("Wrap", "containers", "string", '<Wrap hgap="4" vgap="4" prefWrapLength="200">' + L3 + '</Wrap>', T, "text", fx="Flow", alias_of="Flow",
    note="a pure rename of Flow: same tree")
add("Positioned", "containers", "string", '<Positioned alignment="CENTER">' + L3 + '</Positioned>', T, "text", fx="Stack", alias_of="Stack",
    note="a pure rename of Stack: same tree")
add("Container", "containers", "string", '<Container alignment="CENTER">' + L3 + '</Container>', T, "text", fx="Stack", alias_of="Stack",
    note="a pure rename of Stack: same tree")
add("Box", "containers", "string", '<Box alignment="CENTER">' + L3 + '</Box>', T, "text", fx="Stack", alias_of="Stack",
    note="a pure rename of Stack: same tree")
add("Padding", "containers", "string", '<Padding alignment="CENTER">' + L3 + '</Padding>', T, "text", fx="Stack", alias_of="Stack",
    note="a pure rename of Stack: same tree")
add("Opacity", "containers", "string", '<Opacity alignment="CENTER">' + L3 + '</Opacity>', T, "text", fx="Stack", alias_of="Stack",
    note="a pure rename of Stack: same tree")
add("Visibility", "containers", "string", '<Visibility alignment="CENTER">' + L3 + '</Visibility>', T, "text", fx="Stack", alias_of="Stack",
    note="a pure rename of Stack: same tree")

# ---- virtualized: a model of 10,000 rows, the window a real screen shows (30 rows) -------------------------
add("ListView", "lists", "firsts",
    '<ListView items="${model}" var="s" type="java.lang.String" key="${s}" first="${i}" rows="30" cellHeight="24" prefHeight="720">'
    '<ListItem text="${s}"/></ListView>', ("list", "first", "i"), "first (scroll position)", fx="ListView",
    note="window of 30 rows from a model of 10000; update scrolls by 7 rows")
add("TableView", "lists", "firsts",
    '<TableView items="${rowsModel}" var="r" type="com.jxparallel.examples.nativeui.components.BenchRow" key="${r}" first="${i}" rows="30" rowHeight="24" '
    'columns="Id, Name, Total" columnWidths="60, 160, 80" prefHeight="750"><TableRow><TableCell text="${r.id}"/><TableCell text="${r.name}"/>'
    '<TableCell text="${r.total}"/></TableRow></TableView>', ("table", "first", "i"), "first (scroll position)", fx="TableView",
    note="window of 30 rows from a model of 10000 rows of 3 columns; update scrolls by 7 rows")
add("Tree", "lists", "firsts",
    '<Tree rowHeight="24" first="${i}" rows="30" prefHeight="720"><For items="${folders}" var="f" type="com.jxparallel.examples.nativeui.components.BenchFolder" key="${f}" memo="true">'
    '<TreeItem text="${f.name}" expanded="true"><For items="${f.children}" var="c" type="java.lang.String" key="${c}"><TreeItem text="${c}"/></For></TreeItem>'
    '</For></Tree>', ("list", "first", "i"), "first (scroll position)", fx="TreeView",
    note="100 folders of 99 children, all expanded: 10000 visible rows; the folders loop is memo=\"true\", so the items are built once for as long as the model is the same list, JavaFX also builds them once")
add("TreeTable", "lists", "firsts",
    '<TreeTable rowHeight="24" first="${i}" rows="30" columns="Name, Size" columnWidths="-1, 80" constrained="true" prefHeight="750">'
    '<For items="${folders}" var="f" type="com.jxparallel.examples.nativeui.components.BenchFolder" key="${f}" memo="true">'
    '<TreeItem text="${f.name}" expanded="true" values="${f.size}"><For items="${f.children}" var="c" type="java.lang.String" key="${c}">'
    '<TreeItem text="${c}" values="${f.size}"/></For></TreeItem></For></TreeTable>', ("table", "first", "i"), "first (scroll position)", fx="TreeTableView",
    note="as Tree, with a Size column; update scrolls by 7 rows")

# ---- overlays: closed, and open (showing=true); the open ones have no JavaFX mode ------------------------------
add("MenuButton", "overlays", "string", '<MenuButton text="${i}"><MenuItem text="a"/><MenuItem text="b"/><MenuItem text="c"/></MenuButton>',
    ("menubutton", "label", "s"), "text", fx="MenuButton")
add("ContextMenu", "overlays", "string", '<ContextMenu><MenuItem text="${i}"/><MenuItem text="b"/></ContextMenu>', ("menuitem", "label", "s"), "text",
    note="JavaFX ContextMenu is a popup window object, not a scene node")
add("Modal", "overlays", "string", '<Modal showing="false" padding="12" gap="8"><Label text="${i}"/><Button text="b"/></Modal>', T, "text",
    note="no JavaFX equivalent in a scene graph: JavaFX dialogs are windows")
add("Dialog", "overlays", "string", '<Dialog title="${i}" showing="false"><Label text="content"/><Button slot="actions" text="OK"/></Dialog>', T, "title",
    note="JavaFX Dialog is a window; DialogPane without a window has no comparable layout")
add("AlertDialog", "overlays", "string", '<AlertDialog title="${i}" message="msg" confirmText="OK" cancelText="Cancel" showing="false"/>', T, "title",
    note="JavaFX Alert is a window; not comparable")
add("Drawer", "overlays", "string", '<Drawer side="left" showing="false"><Label text="${i}"/><Button text="b"/></Drawer>', T, "text", note="no JavaFX equivalent")
add("SnackBar", "overlays", "string", '<SnackBar message="${i}" action="Undo" showing="false"/>', ("snackbar", "message", "s"), "message", note="no JavaFX equivalent")

OPEN_NOTE = "open variant: {n} popups open at once (an amplifier of the cost of one popup; (open - closed) / N is per popup); JavaFX popups are windows: no JavaFX mode"
for base, body, probe, update in [
    ("ComboBox", '<ComboBox items="${choices}" value="${i}" showing="true"/>', ("select", "value", "s"), "value"),
    ("DatePicker", '<DatePicker value="${i}" showing="true"/>', ("datepicker", "value", "s"), "value"),
    ("TimePicker", '<TimePicker value="${i}" hour24="true" minuteStep="15" showing="true"/>', ("timepicker", "value", "s"), "value"),
    ("ColorPicker", '<ColorPicker value="${i}" showing="true"/>', ("colorpicker", "value", "s"), "value"),
    ("MenuButton", '<MenuButton text="${i}" showing="true"><MenuItem text="a"/><MenuItem text="b"/><MenuItem text="c"/></MenuButton>', ("menubutton", "label", "s"), "text"),
    ("ContextMenu", '<ContextMenu showing="true"><MenuItem text="${i}"/><MenuItem text="b"/></ContextMenu>', ("menuitem", "label", "s"), "text"),
    ("Modal", '<Modal showing="true" padding="12" gap="8"><Label text="${i}"/><Button text="b"/></Modal>', T, "text"),
    ("Dialog", '<Dialog title="${i}" showing="true"><Label text="content"/><Button slot="actions" text="OK"/></Dialog>', T, "title"),
    ("AlertDialog", '<AlertDialog title="${i}" message="msg" confirmText="OK" cancelText="Cancel" showing="true"/>', T, "title"),
    ("Drawer", '<Drawer side="left" showing="true"><Label text="${i}"/><Button text="b"/></Drawer>', T, "text"),
    ("SnackBar", '<SnackBar message="${i}" action="Undo" showing="true"/>', ("snackbar", "message", "s"), "message"),
]:
    kind = [c for c in COMPONENTS if c["name"] == base][0]["kind"]
    add(base + "Open", "overlays", kind, body, probe, update, note=OPEN_NOTE, variant="open", tag=base)
    # note: MenuBar cannot be opened by a prop (it opens on a press), so it has no open variant

# the Material alias BottomBar and the like are plain; nothing else to add

OVERRIDES = {}
if N_FILE.exists():
    with open(N_FILE, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            OVERRIDES[row["name"]] = int(row["n"])


def n_of(c):
    return OVERRIDES.get(c["name"], DEFAULT_N[c["group"]])


def jxml(c):
    getter, jtype = KINDS[c["kind"]]
    body = c["body"]
    inner = ('<For items="${%s}" var="i" type="%s">\n    %s\n  </For>' % (getter, jtype, body))
    return ('<?xml version="1.0" encoding="UTF-8"?>\n'
            '<!-- Generated by scripts/generate-component-bench.py. %d instances of <%s>, one bound value each. -->\n'
            '<Column controller="%s.%sController" gap="2" padding="4" alignment="TOP_LEFT" fillWidth="true">\n  %s\n</Column>\n'
            % (n_of(c), c["tag"], PKG, c["name"], inner))


def controller(c):
    return ('package %s;\n\n/** Generated by scripts/generate-component-bench.py: the controller of components/%s.jxml. */\n'
            'public class %sController extends ComponentController {\n    public %sController() {\n        super("%s");\n    }\n}\n'
            % (PKG, c["name"], c["name"], c["name"], c["name"]))


def views():
    lines = []
    for c in COMPONENTS:
        lines.append('            case "%s": return new %s.gen.%sView();' % (c["name"], PKG, c["name"]))
    ctl = []
    for c in COMPONENTS:
        ctl.append('            case "%s": return new %s.%sController();' % (c["name"], PKG, c["name"]))
    return ('package com.jxparallel.examples.nativeui;\n\nimport com.jxparallel.forge.JxmlView;\n\n'
            '/** Generated by scripts/generate-component-bench.py: the generated views of the components and their controllers. */\n'
            'final class ComponentBenchViews {\n    private ComponentBenchViews() {\n    }\n\n'
            '    @SuppressWarnings("rawtypes")\n    static JxmlView view(String name) {\n        switch (name) {\n%s\n'
            '            default: throw new IllegalArgumentException("component " + name);\n        }\n    }\n\n'
            '    static Object controller(String name) {\n        switch (name) {\n%s\n'
            '            default: throw new IllegalArgumentException("component " + name);\n        }\n    }\n}\n'
            % ("\n".join(lines), "\n".join(ctl)))


def table():
    out = ["name,tag,group,variant,n,kind,probe_type,probe_prop,probe_kind,update,fx,alias_of,note"]
    for c in COMPONENTS:
        note = c["note"].replace("{n}", "N").replace(",", ";")
        out.append(",".join([c["name"], c["tag"], c["group"], c["variant"], str(n_of(c)), c["kind"], c["probe"][0], c["probe"][1], c["probe"][2],
                             c["update"].replace(",", ";"), c["fx"], c["alias_of"], note]))
    return "\n".join(out) + "\n"


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


names = [c["name"] for c in COMPONENTS]
assert len(names) == len(set(names)), "duplicate component"
for c in COMPONENTS:
    write(NATIVE / "jxml" / "components" / (c["name"] + ".jxml"), jxml(c))
    write(NATIVE / "java" / "com" / "jxparallel" / "examples" / "nativeui" / "components" / (c["name"] + "Controller.java"), controller(c))
write(NATIVE / "java" / "com" / "jxparallel" / "examples" / "nativeui" / "ComponentBenchViews.java", views())
write(NATIVE / "resources" / "componentbench" / "components.csv", table())
write(FX / "resources" / "componentbench" / "components.csv", table())
print("%d components written (%d with a JavaFX mode)" % (len(COMPONENTS), sum(1 for c in COMPONENTS if c["fx"])))
