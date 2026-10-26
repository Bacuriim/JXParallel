package com.jxparallel.ui.native2d;

import com.jxparallel.ui.JXLiveSession;

/** Test bridge to the display-thread part of {@code JXWindow.setLive}, which is package-private: no GLFW, no thread. */
public final class LiveWindow {
    private final JXWindow window = new JXWindow("live test");

    /** What {@code setLive} does on the display thread. */
    public void mount(JXLiveSession session) {
        window.mountLive(session);
    }

    /** What the frame loop does before a requested frame; true when the tree was mounted anew. */
    public boolean refresh() {
        return window.refreshLive();
    }

    public JXNativeNode root() {
        return window.getRoot();
    }
}
