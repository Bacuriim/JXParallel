package com.jxparallel.ui.accessibility;

/** A boolean fact about an accessible node. A state that is not in {@link JXAccessibleNode#getStates()} is false. */
public enum JXAccessibleState {
    /** The node can take the keyboard focus (and is enabled and shown). */
    FOCUSABLE,
    /** The node has the keyboard focus of its window. */
    FOCUSED,
    /** The node ignores input: its own {@code disabled} prop. */
    DISABLED,
    CHECKED,
    /** A check box in the in-between state ({@code indeterminate}). */
    MIXED,
    SELECTED,
    /** Can be selected (a row of a list with a selection, a tab, a page). */
    SELECTABLE,
    EXPANDED,
    COLLAPSED,
    READ_ONLY,
    /** A password field: its text is never exposed, the value is always empty. */
    PASSWORD,
    MULTILINE,
    /** A modal dialog or drawer: the tree holds only it while it is open. */
    MODAL,
    /** Laid out outside the visible part of its window or of a scrolled pane that clips it. */
    OFFSCREEN,
    /** Work of unknown length is going on (an indeterminate progress bar or indicator). */
    BUSY
}
