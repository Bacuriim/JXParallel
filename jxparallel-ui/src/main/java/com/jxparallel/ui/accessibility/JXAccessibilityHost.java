package com.jxparallel.ui.accessibility;

/**
 * What a {@link JXAccessibilityAdapter} sees of one window. Every method is safe to call from any thread: the tree is an
 * immutable snapshot the display thread replaces after each frame, and actions run on the display thread.
 */
public interface JXAccessibilityHost {
    /** The window's title. */
    String getTitle();

    /**
     * The native handle of the window (the HWND on Windows), or 0 when there is none (a window that is not open, a
     * window without a native handle on this platform).
     */
    long getNativeHandle();

    /** The tree as of the last frame, or null before the first one. Immutable; keep it as long as needed. */
    JXAccessibleNode getRoot();

    /** The node with this id in {@link #getRoot()}, or null (it may have left the window since). */
    JXAccessibleNode find(long id);

    /** The id of the node that has the keyboard focus in the last snapshot, or 0. */
    long getFocusedId();

    /** Whether the calling thread is the display thread, the one that draws, handles input and delivers events. */
    boolean isDisplayThread();

    /** Runs {@code task} on the display thread (directly when already there). */
    void invokeLater(Runnable task);

    /**
     * Asks a node to do something, as the pointer or keyboard would. Returns whether the node is in the last snapshot and
     * lists the action (which the node may still refuse when it runs: the window changed, the control was disabled). On
     * the display thread the action has run when this returns; elsewhere it runs on the display thread soon after. Setting
     * a value takes the new value as {@code argument}: a String for text and pickers, a Number for ranges.
     */
    boolean perform(long id, JXAccessibleAction action, Object argument);
}
