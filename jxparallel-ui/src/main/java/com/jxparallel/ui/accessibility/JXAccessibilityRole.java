package com.jxparallel.ui.accessibility;

/**
 * What an element is, for an assistive technology. The set is the common denominator of the platform accessibility
 * APIs (a role here maps to one UI Automation control type, one AT-SPI role, one NSAccessibility role); it is not a
 * claim that every role is complete in every adapter.
 */
public enum JXAccessibilityRole {
    /** The window itself: the root of a live tree, named after the window's title. */
    ROOT,
    /** A container that only groups (a layout row, column, stack...). */
    GROUP,
    BUTTON,
    /** A button that stays pressed: toggle button, filter chip. */
    TOGGLE_BUTTON,
    CHECK_BOX,
    RADIO_BUTTON,
    SWITCH,
    LINK,
    /** Static text. */
    TEXT,
    /** Single or multi-line editable text; {@code PASSWORD} and {@code MULTILINE} are states. */
    TEXT_FIELD,
    COMBO_BOX,
    DATE_PICKER,
    TIME_PICKER,
    COLOR_PICKER,
    SPINNER,
    SLIDER,
    PROGRESS_BAR,
    SCROLL_BAR,
    SCROLL_PANE,
    SPLIT_PANE,
    LIST,
    LIST_ITEM,
    TREE,
    TREE_ITEM,
    TABLE,
    ROW,
    CELL,
    TAB_LIST,
    TAB,
    MENU_BAR,
    MENU,
    MENU_ITEM,
    IMAGE,
    SEPARATOR,
    DIALOG,
    TOOLTIP,
    /** A message that appears without taking the focus (a snack bar): a live region. */
    STATUS,
    UNKNOWN
}
