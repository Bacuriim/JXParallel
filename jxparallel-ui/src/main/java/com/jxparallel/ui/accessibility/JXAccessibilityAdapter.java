package com.jxparallel.ui.accessibility;

import java.util.List;

/**
 * The bridge to a platform's accessibility API (UI Automation, AT-SPI, NSAccessibility). Register one with
 * {@link JXAccessibility#install}, or name it with {@code -Djx.accessibility=<name>} when it is on the class path as a
 * {@link java.util.ServiceLoader} service. The platform-neutral layer builds the tree, runs the actions and finds the
 * events; an adapter only translates.
 *
 * <p><b>Threads.</b> {@link #attach}, {@link #detach} and {@link #deliver} are called on the display thread, never
 * concurrently for one window. The {@link JXAccessibilityHost} may be used from any thread, so an adapter whose platform
 * calls it from its own threads (UI Automation does) can read the latest snapshot without locking and post actions.
 */
public interface JXAccessibilityAdapter {
    /** The name {@code -Djx.accessibility} selects this adapter by ("uia"). */
    String getName();

    /** A window with a native handle was opened (or the adapter was installed while it was open). */
    void attach(JXAccessibilityHost host);

    /** The window is closing (or the adapter was uninstalled): release what {@link #attach} acquired. */
    void detach(JXAccessibilityHost host);

    /**
     * Changes found after a frame, coalesced: at most one value change per node and one focus change per frame, in the
     * order they were found. {@code host.getRoot()} already is the tree they describe.
     */
    void deliver(JXAccessibilityHost host, List<JXAccessibilityEvent> events);
}
