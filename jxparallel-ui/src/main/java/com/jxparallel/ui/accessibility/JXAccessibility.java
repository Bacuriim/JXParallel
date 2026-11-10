package com.jxparallel.ui.accessibility;

import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Entry point of the platform adapters: which {@link JXAccessibilityAdapter} the windows talk to, and the live-region
 * {@link #announce}. Nothing is installed by default; the tree is only built (and the events only looked for) in a window
 * while an adapter is installed.
 *
 * <p>Enable an adapter in code with {@link #install}, or from the command line with {@code -Djx.accessibility=uia}, which
 * loads the adapter of that {@link JXAccessibilityAdapter#getName() name} from the class path with
 * {@link ServiceLoader} (the {@code jxparallel-a11y-windows} module provides {@code uia}). A name no adapter has is
 * reported once on the error stream and ignored.
 */
public final class JXAccessibility {
    /** {@code -Djx.accessibility=<adapter name>}. */
    public static final String PROPERTY = "jx.accessibility";

    private static volatile JXAccessibilityAdapter adapter;
    private static volatile boolean resolved;
    private static final List<WindowHook> HOOKS = new CopyOnWriteArrayList<WindowHook>();

    private JXAccessibility() {}

    /** What a window registers to hear of {@link #install}, {@link #uninstall} and {@link #announce}. */
    public interface WindowHook {
        /** The installed adapter changed: look at {@link #getAdapter()} on the next frame. */
        void adapterChanged();

        /** Whether this is the window the user is working in (an announcement goes to it). */
        boolean isActive();

        /** A live-region message; the window keeps it for its next frame. */
        void announce(String text, boolean assertive);
    }

    /** Makes {@code newAdapter} the adapter of every window, open or opened later; replaces the previous one. */
    public static void install(JXAccessibilityAdapter newAdapter) {
        if (newAdapter == null) {
            throw new IllegalArgumentException("Adapter cannot be null");
        }
        adapter = newAdapter;
        resolved = true;
        notifyHooks();
    }

    /** No adapter any more; the windows detach from the one they had. */
    public static void uninstall() {
        adapter = null;
        resolved = true;
        notifyHooks();
    }

    /** The installed adapter, or the one {@code -Djx.accessibility} names (loaded on the first call), or null. */
    public static JXAccessibilityAdapter getAdapter() {
        if (!resolved) {
            synchronized (JXAccessibility.class) {
                if (!resolved) {
                    if (adapter == null) {
                        adapter = fromProperty(System.getProperty(PROPERTY));
                    }
                    resolved = true;
                }
            }
        }
        return adapter;
    }

    /** Whether an adapter is installed: windows build their accessibility tree only then. */
    public static boolean isEnabled() {
        return getAdapter() != null;
    }

    private static JXAccessibilityAdapter fromProperty(String name) {
        if (name == null || name.trim().isEmpty() || "none".equalsIgnoreCase(name.trim()) || "off".equalsIgnoreCase(name.trim())) {
            return null;
        }
        String wanted = name.trim();
        try {
            Iterator<JXAccessibilityAdapter> found = ServiceLoader.load(JXAccessibilityAdapter.class).iterator();
            while (found.hasNext()) {
                JXAccessibilityAdapter candidate = found.next();
                if (wanted.equalsIgnoreCase(candidate.getName())) {
                    return candidate;
                }
            }
        } catch (Throwable t) { // a service that cannot load (missing native library) is not a reason to stop the application
            System.err.println("jx.accessibility=" + wanted + ": the adapter could not be loaded: " + t);
            return null;
        }
        System.err.println("jx.accessibility=" + wanted + ": no adapter of that name is on the class path");
        return null;
    }

    /** Called by a window when it opens; a closed window unregisters. */
    public static void register(WindowHook hook) {
        HOOKS.add(hook);
    }

    public static void unregister(WindowHook hook) {
        HOOKS.remove(hook);
    }

    private static void notifyHooks() {
        for (WindowHook hook : HOOKS) {
            hook.adapterChanged();
        }
    }

    /** {@link #announce(String, boolean)} that waits its turn. */
    public static void announce(String text) {
        announce(text, false);
    }

    /**
     * Has a screen reader say {@code text} without moving the focus ("Saved", "3 results"): a live-region message. It goes
     * to the window the user is working in (the first one when none is), is delivered with the next frame as an
     * {@link JXAccessibilityEvent.Kind#ANNOUNCEMENT} to the adapter and is dropped when no adapter is installed or no window is
     * open. {@code assertive} asks to interrupt what is being read. Safe from any thread.
     */
    public static void announce(String text, boolean assertive) {
        if (text == null || text.isEmpty()) {
            return;
        }
        WindowHook target = null;
        for (WindowHook hook : HOOKS) {
            if (target == null) {
                target = hook;
            }
            if (hook.isActive()) {
                target = hook;
                break;
            }
        }
        if (target != null && getAdapter() != null) {
            target.announce(text, assertive);
        }
    }
}
