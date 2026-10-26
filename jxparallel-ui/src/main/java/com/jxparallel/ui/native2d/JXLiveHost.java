package com.jxparallel.ui.native2d;

import com.jxparallel.ui.JXLiveSession;

/**
 * Hosts a live screen ({@link JXLiveSession}) on a mounted tree, without a window: mounts the session's element, pairs the
 * session with the nodes, and on {@link #refresh} brings the tree up to date, mounting the screen anew when its root changed
 * type (what {@code setContent} does when a reconcile cannot keep the root). {@code JXWindow.setLive} is this plus the
 * display thread and the frame loop; backends that own their window use it directly, from the thread that owns the tree
 * (the tree, like the session, is single-threaded).
 *
 * <pre>{@code
 * JXLiveHost host = new JXLiveHost(view.live(controller));
 * JXFrame.layout(host.root(), width, height);
 * ...
 * controller.setTitle("changed");
 * host.refresh();                       // writes what changed into the nodes
 * JXFrame.layout(host.root(), width, height);
 * }</pre>
 */
public final class JXLiveHost {
    private JXLiveSession session;
    private JXNativeNode root;
    private int remounts;

    /** Mounts {@code session}'s screen and pairs the session with it. */
    public JXLiveHost(JXLiveSession session) {
        if (session == null) {
            throw new IllegalArgumentException("Session cannot be null");
        }
        mount(session);
    }

    private void mount(JXLiveSession next) {
        session = next;
        root = JXNativeNode.createBackendNode(next.element());
        next.attach(root);
    }

    /** The mounted tree: another object after a refresh that returned true. */
    public JXNativeNode root() {
        return root;
    }

    /** The session now in use: another object after a refresh that returned true. */
    public JXLiveSession session() {
        return session;
    }

    /** How many times the screen was mounted anew because its root changed. */
    public int remounts() {
        return remounts;
    }

    /**
     * Brings the mounted tree up to date with the model. Returns true when the screen's root changed type and the tree
     * was mounted anew ({@link #root()} is another node: the host must forget the old one and anything it holds on it,
     * such as the focused node). The caller lays out and paints afterwards.
     */
    public boolean refresh() {
        if (session.refresh()) {
            return false;
        }
        remounts++;
        mount(session.renew());
        return true;
    }
}
