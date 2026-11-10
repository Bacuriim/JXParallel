# Accessibility

What a screen reader or another assistive technology (AT) can learn about a JXParallel native window, and do in it. This
page makes no claim of conformance to WCAG, Section 508, EN 301 549 or any other standard: it describes what exists, how
to look at it, and what is missing.

## Layers

1. **Platform-neutral layer** (`jxparallel-ui`, package `com.jxparallel.ui.accessibility` and `JXNativeAccessibility`):
   builds the accessibility tree from the live node tree, runs actions, finds events. Always there; costs nothing until an
   adapter is installed (the window builds no tree without one).
2. **Platform adapters** (`JXAccessibilityAdapter`): translate the tree, the actions and the events to a platform API.
   Only the Windows UI Automation adapter exists (module `jxparallel-a11y-windows`, see below). **AT-SPI (Linux) and
   NSAccessibility (macOS) adapters are out of scope and not started.**

## The tree

`JXWindow.getAccessibleRoot()` (display thread) or `JXNativeAccessibility.snapshot(root)` (any laid-out `JXNativeNode`
tree, no window needed) return a `JXAccessibleNode`: an immutable snapshot, safe to hand to any thread. The root has role
`ROOT` and the window title as name; the content hangs below it.

Per node:

| Property | Meaning |
| --- | --- |
| `getId()` | stable while the node lives (reconciling in place keeps it); parts a control draws without a node (tabs, page buttons, options of an open combo box) get derived ids |
| `getRole()` | `JXAccessibilityRole`; `accessibleRole` overrides |
| `getName()` | `accessibleName`, else the label that points at it with `labelFor`, else the control's text (`label`, `prompt`, cell `value`, the text inside it) |
| `getDescription()` | `accessibleDescription`, else the `tooltip` |
| `getValue()`, `getMin/Max/Now()` | text of fields and pickers; numbers of slider, spinner, scroll bar; progress as 0..100 and "25%"; stepper "Step 2 of 4"; pagination "Page 2 of 5" |
| `getStates()` | `FOCUSABLE`, `FOCUSED`, `DISABLED`, `CHECKED`, `MIXED`, `SELECTED`, `SELECTABLE`, `EXPANDED`, `COLLAPSED`, `READ_ONLY`, `PASSWORD`, `MULTILINE`, `MODAL`, `OFFSCREEN`, `BUSY` |
| `getBounds()`, `getScreenBounds()` | logical pixels of the content, and screen pixels (window position plus logical times the HiDPI scale, in the units GLFW positions the window in) |
| `getParent()`, `getChildren()`, `getIndexInParent()` | document order |
| `getItemIndex()/getItemCount()` | row of a list among all rows, even rows not built by a virtual list |
| `getRowIndex()/getRowCount()`, `getColumnIndex()/getColumnCount()` | table rows and cells (a synthetic header row holds the column titles) |
| `getLevel()` | depth of a tree item (1 = top) |
| `getActions()` | what `perform` accepts |
| `isContent()` | false for layout-only groups and decorative images/separators (an adapter's "control view" may skip them) |

A **password field** never exposes its text: the value is empty, the `PASSWORD` state is set, and no value-changed event
is raised for it.

A node with `hidden` or `accessibleHidden` is not in the tree (nor its subtree); a closed dialog, drawer, snack bar,
context menu or popup is not either. While a **modal dialog or drawer is open the tree holds only it** (and open snack
bars), with `MODAL` set, as the keyboard and pointer are already confined to it.

### What each control exposes

| Control (native type) | Role | Notes |
| --- | --- | --- |
| `button`, `menubutton`, `chip` (assist, input) | `BUTTON` | menu button: `EXPANDED`/`COLLAPSED`, its items while open |
| `toggle`, `chip` (filter, choice) | `TOGGLE_BUTTON` | `CHECKED` = `selected` |
| `checkbox`, `radio`, `switch` | `CHECK_BOX`, `RADIO_BUTTON`, `SWITCH` | `CHECKED`; `MIXED` for `indeterminate` |
| `hyperlink` | `LINK` | |
| `#text`, `badge` | `TEXT` | |
| `input`, `password`, `textarea` | `TEXT_FIELD` | `MULTILINE`, `PASSWORD`, `READ_ONLY` (`readOnly` or `editable=false`) |
| `select`, `datepicker`, `timepicker`, `colorpicker` | `COMBO_BOX`, `DATE_PICKER`, `TIME_PICKER`, `COLOR_PICKER` | value; `EXPANDED`/`COLLAPSED`; an open `select` lists its options |
| `spinner`, `slider`, `scrollbar` | `SPINNER`, `SLIDER`, `SCROLL_BAR` | value with min/max |
| `progress`, `indicator` | `PROGRESS_BAR` | indeterminate: `BUSY`, no value |
| `list`, `cell` | `LIST`, `LIST_ITEM` | `SELECTED` from the list's selection; item index/count |
| a list or table whose rows have a `disclosure` | `TREE`, `TREE_ITEM` | level, `EXPANDED`/`COLLAPSED` |
| `table`, `tablerow`, `cell` | `TABLE`, `ROW`, `CELL` | row/column counts, header row |
| `tabs`, `tabbar` | `TAB_LIST` + `TAB` children | selected tab; the content is the first child |
| `pagination` | group + `BUTTON` children | previous, next, page numbers |
| `menubar`, `menu`, `menuitem`, `contextmenu` | `MENU_BAR`, `MENU_ITEM`, `MENU` | items of a menu only while it is expanded |
| `dialog`, `drawer`, `snackbar`, `tooltip` | `DIALOG`, `STATUS`, `TOOLTIP` | |
| `image`, `icon`, `avatar` | `IMAGE` | name from `accessibleName`, `alt`, icon name, initials; unnamed images are not content |
| `scroll`, `splitpane` | `SCROLL_PANE`, `SPLIT_PANE` | |
| `column`, `row`, `stack`, `grid`, `titled`, `accordion`... | `GROUP` | `titled` is `EXPANDED`/`COLLAPSED` and can be expanded |

### Text dump

`JXAccessibilityTree.dump(node)` (or `node.dump()`) writes the tree as text, one node per line:

```
ROOT "Demo"
  GROUP "" @0,0 400x300
    TEXT_FIELD "Name" [FOCUSABLE] value="Ana" desc="Your full name" @8,8 200x24
    BUTTON "Save the form" [FOCUSABLE,FOCUSED] @8,40 60x24
```

`dump(node, false)` leaves out the bounds, so a screen can be asserted on independent of fonts and layout. Use it in tests
and to audit a screen: every control without a name shows as `""`.

## JXML attributes

On any tag: `accessibleName`, `accessibleDescription`, `accessibleRole` (a `JXAccessibilityRole` name such as `button`,
`separator`, `progress_bar`), `accessibleHidden="true"`, and `labelFor="key"` (this element names the element that has
that `key`):

```xml
<Label text="Name" labelFor="nameField"/>
<TextField key="nameField" text="@{name}" accessibleDescription="Your full legal name"/>
<Button text="Save" accessibleName="Save the document"/>
<Separator accessibleHidden="true"/>
```

## Actions

`JXAccessibilityHost.perform(id, action, argument)` (or `JXNativeAccessibility.perform` on the display thread):

| Action | What runs |
| --- | --- |
| `DEFAULT` | a press and release at the node's centre through `pressPointer`/`releasePointer`, the path the mouse takes (so a button's `onAction`, a check box's `onChange`, a menu item, a tab, a popup opening all behave as for a click); a node that something covers gets `dispatchPointer` directly |
| `FOCUS` | the window gives the node the focus, as a click that focuses does |
| `SET_VALUE` | the node's `onChange` with a value the keyboard could have produced: text (String), number (clamped to min/max), option of a `select` (must be one of them), ISO date, `HH:mm` time, `#RRGGBB` |
| `EXPAND` / `COLLAPSE` | `onShowing` of a popup host or menu button, the click on a titled pane's header, the arrow of a tree row (its `onToggle`) |
| `SELECT` | the click on a list/table row (its `onSelect`), a tab, a page, an option, a radio button |
| `SCROLL_INTO_VIEW` | the least scrolling of the enclosing pane, list or table |

Disabled nodes refuse them. Each node lists what it supports in `getActions()`.

## Events

`JXAccessibilityEvent.Kind`: `FOCUS_CHANGED`, `VALUE_CHANGED`, `STATE_CHANGED`, `NAME_CHANGED`, `SELECTION_CHANGED`,
`STRUCTURE_CHANGED`, `ANNOUNCEMENT`.

State reaches the renderer in one place: after the application renders again, or after the keyboard/pointer changes the
focus or a popup, and the window paints a frame. While an adapter is installed the window takes a snapshot after **each
frame**, compares it with the previous one (`JXNativeAccessibility.diff`) and hands the differences to the adapter, so the
events are coalesced by construction (at most one value change per node per frame, one focus change per frame) and
reflect what was drawn. They are *not* raised from inside each setter; an input that changes and changes back within one
frame produces nothing.

`JXAccessibility.announce("Saved")` (any thread) queues a live-region message for the window the user is working in; it is
delivered as an `ANNOUNCEMENT` with the next frame (and dropped when no adapter is installed or no window is open).

**Thread.** `JXAccessibilityAdapter.attach/detach/deliver` run on the display thread. `JXAccessibilityHost.getRoot()`,
`find`, `getFocusedId` can be called from any thread (a volatile immutable snapshot); `perform` posts to the display thread
unless already there.

## Windows (UI Automation)

See the section below, written with the adapter.

## Auditing

* **Text dump** (above): the quickest check; run it in a unit test for each screen.
* **Accessibility Insights for Windows** (Microsoft, free): *Live inspect* shows the UI Automation tree of the running
  window: names, control types, patterns, bounding rectangles, and runs automated checks.
* **Narrator** (Win+Ctrl+Enter) and **NVDA** (free): move with Tab and the arrow keys and listen for the name, role and
  state of each control.

## Known gaps

* Only Windows UI Automation has an adapter; no AT-SPI, no NSAccessibility.
* The dump and the tree were checked with tests; no person has used the result with a screen reader.
* No text-range (caret, selection, per-character) information for text fields: a reader gets the whole value only.
* Transforms (`translateX`, `scaleX`, `rotate`) are not applied to bounds.
* The grids of the date, time and color popups, and the day cells of the calendar, are not exposed as nodes (the picker
  itself is, with value and `SET_VALUE`).
* `SCROLL_INTO_VIEW` of a row that a virtual list has not built is not possible (rows outside the built window do not
  exist as nodes); use the list's `first`/`onChange` windowing.
* Disabled state is the node's own `disabled` prop; the renderer does not inherit it from a disabled parent, and neither
  does the tree.
* Keyboard order is the renderer's Tab order; there are no landmarks, headings or accessible-name checks beyond the dump.
