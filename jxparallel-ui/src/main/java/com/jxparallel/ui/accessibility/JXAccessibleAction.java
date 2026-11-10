package com.jxparallel.ui.accessibility;

/**
 * What an assistive technology can ask a node to do. Each one is carried out by the code the pointer or the keyboard
 * use, so the application's {@code onChange}, {@code onAction}, {@code onSelect}, {@code onShowing} and {@code onToggle}
 * handlers run exactly as they do for a user.
 */
public enum JXAccessibleAction {
    /** The click: press a button, toggle a check box, follow a link, pick a menu item, open a combo box. */
    DEFAULT,
    /** Give the node the keyboard focus. */
    FOCUS,
    /** Put a new value: a String for text and pickers, a Number for sliders, spinners and scroll bars. */
    SET_VALUE,
    EXPAND,
    COLLAPSE,
    /** Select a row, tab, page, option or radio button. */
    SELECT,
    /** Scroll the node's pane the least that shows it. */
    SCROLL_INTO_VIEW
}
