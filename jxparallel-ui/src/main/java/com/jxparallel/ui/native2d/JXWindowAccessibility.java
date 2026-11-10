package com.jxparallel.ui.native2d;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.jxparallel.ui.accessibility.JXAccessibility;
import com.jxparallel.ui.accessibility.JXAccessibilityAdapter;
import com.jxparallel.ui.accessibility.JXAccessibilityEvent;
import com.jxparallel.ui.accessibility.JXAccessibleAction;
import com.jxparallel.ui.accessibility.JXAccessibleNode;
import com.jxparallel.ui.accessibility.JXAccessibleState;
import com.jxparallel.ui.accessibility.JXAccessibilityHost;

/**
 * Connects one {@link JXWindow} to the installed {@link JXAccessibilityAdapter}: after each frame, while an adapter is
 * installed, it takes a snapshot of the tree, publishes it, finds what changed since the previous one and hands the
 * events to the adapter, all on the display thread. Without an adapter it does nothing (no tree is built).
 */
final class JXWindowAccessibility implements JXAccessibilityHost, JXAccessibility.WindowHook {
    private final JXWindow window;
    private final JXNativeAccessibility model = new JXNativeAccessibility();
    private final ConcurrentLinkedQueue<JXAccessibilityEvent> announcements = new ConcurrentLinkedQueue<JXAccessibilityEvent>();
    private volatile JXAccessibleNode snapshot;
    private volatile long focusedId;
    private volatile boolean active;
    private JXAccessibilityAdapter attached;
    private long handle;

    JXWindowAccessibility(JXWindow window) {
        this.window = window;
        model.setFocuser(window::focusFromAccessibility);
        JXAccessibility.register(this);
    }

    boolean isAttached() {
        return attached != null;
    }

    void setActive(boolean value) {
        active = value;
    }

    /** After a frame: display thread, with the tree laid out. */
    void frame(JXNativeNode root, int width, int height, double unitsX, double unitsY, int screenX, int screenY, long nativeHandle) {
        JXAccessibilityAdapter wanted = JXAccessibility.getAdapter();
        if (attached != wanted) {
            if (attached != null) {
                detach();
            }
            attached = wanted;
            handle = nativeHandle;
            snapshot = null;
            if (wanted != null) {
                snapshot = model.snapshot(root, window.getTitle(), width, height, unitsX, unitsY, screenX, screenY);
                focusedId = focusOf(snapshot);
                wanted.attach(this);
            }
            announcements.clear();
            return;
        }
        if (wanted == null) {
            return;
        }
        handle = nativeHandle;
        JXAccessibleNode before = snapshot;
        JXAccessibleNode after = model.snapshot(root, window.getTitle(), width, height, unitsX, unitsY, screenX, screenY);
        snapshot = after;
        focusedId = focusOf(after);
        List<JXAccessibilityEvent> events = JXNativeAccessibility.diff(before, after);
        JXAccessibilityEvent a;
        while ((a = announcements.poll()) != null) {
            events.add(a);
        }
        if (!events.isEmpty()) {
            wanted.deliver(this, events);
        }
    }

    private static long focusOf(JXAccessibleNode root) {
        for (JXAccessibleNode n : root.flatten()) {
            if (n.is(JXAccessibleState.FOCUSED)) {
                return n.getId();
            }
        }
        return 0;
    }

    private void detach() {
        JXAccessibilityAdapter a = attached;
        attached = null;
        if (a != null) {
            try {
                a.detach(this);
            } catch (RuntimeException e) {
                System.err.println("accessibility adapter detach failed: " + e);
            }
        }
    }

    /** The window is closing: display thread. */
    void close() {
        JXAccessibility.unregister(this);
        detach();
        snapshot = null;
    }

    // ---- WindowHook ----------------------------------------------------------------------------------

    @Override
    public void adapterChanged() {
        window.requestRender();
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void announce(String text, boolean assertive) {
        announcements.add(new JXAccessibilityEvent(JXAccessibilityEvent.Kind.ANNOUNCEMENT, 0, text, null, false, assertive));
        window.requestRender();
    }

    // ---- JXAccessibilityHost -------------------------------------------------------------------------

    @Override
    public String getTitle() {
        return window.getTitle();
    }

    @Override
    public long getNativeHandle() {
        return handle;
    }

    @Override
    public JXAccessibleNode getRoot() {
        return snapshot;
    }

    @Override
    public JXAccessibleNode find(long id) {
        JXAccessibleNode root = snapshot;
        return root == null ? null : root.findById(id);
    }

    @Override
    public long getFocusedId() {
        return focusedId;
    }

    @Override
    public boolean isDisplayThread() {
        return JXDisplay.isDisplayThread();
    }

    @Override
    public void invokeLater(Runnable task) {
        if (JXDisplay.isDisplayThread()) {
            task.run();
        } else {
            JXDisplay.post(task);
        }
    }

    @Override
    public boolean perform(long id, JXAccessibleAction action, Object argument) {
        JXAccessibleNode node = find(id);
        if (node == null || !node.supports(action)) {
            return false;
        }
        if (JXDisplay.isDisplayThread()) {
            boolean done = model.perform(id, action, argument);
            window.requestRender();
            return done;
        }
        JXDisplay.post(() -> {
            model.perform(id, action, argument);
            window.requestRender();
        });
        return true;
    }

    /** For tests: the tree now, on the display thread. */
    List<JXAccessibilityEvent> pendingAnnouncements() {
        return new ArrayList<JXAccessibilityEvent>(announcements);
    }
}
