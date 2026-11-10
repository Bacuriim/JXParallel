package com.jxparallel.a11y.windows;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jxparallel.ui.accessibility.JXAccessibilityAdapter;
import com.jxparallel.ui.accessibility.JXAccessibilityEvent;
import com.jxparallel.ui.accessibility.JXAccessibilityHost;
import com.jxparallel.ui.accessibility.JXAccessibleNode;
import com.jxparallel.ui.accessibility.JXAccessibleState;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

/**
 * Exposes the windows of jxparallel-ui to Windows UI Automation (experimental, 64-bit Windows only).
 *
 * <p>Enable it with {@code -Djx.accessibility=uia} or {@code JXAccessibility.install(new UiaAdapter())}. For each window
 * it subclasses the window procedure (on the display thread, where the window lives), answers {@code WM_GETOBJECT} with a
 * server-side provider tree that reads the immutable snapshot the window publishes after each frame, runs the patterns'
 * actions on the display thread, and raises UI Automation events for the changes the window finds.
 *
 * <p>Provider calls arrive on UI Automation's own threads ({@code ProviderOptions_ServerSideProvider}, no COM threading):
 * they only read snapshots and post actions, they never wait for the display thread.
 */
public final class UiaAdapter implements JXAccessibilityAdapter {
    /** The name for {@code -Djx.accessibility}. */
    public static final String NAME = "uia";

    private final Map<JXAccessibilityHost, UiaWindow> windows = new HashMap<JXAccessibilityHost, UiaWindow>();

    @Override
    public String getName() {
        return NAME;
    }

    /** Whether this process can run the adapter: 64-bit Windows. */
    public static boolean supported() {
        return System.getProperty("os.name", "").startsWith("Windows") && Native.POINTER_SIZE == 8;
    }

    @Override
    public void attach(JXAccessibilityHost host) {
        if (!supported()) {
            System.err.println("jx.accessibility=uia: needs 64-bit Windows");
            return;
        }
        long handle = host.getNativeHandle();
        if (handle == 0 || windows.containsKey(host)) {
            return;
        }
        try {
            UiaWindow window = new UiaWindow(host, new Pointer(handle));
            windows.put(host, window);
            window.subclass();
        } catch (Throwable t) { // a missing UIAutomationCore or JNA problem: the application runs on without accessibility
            windows.remove(host);
            System.err.println("jx.accessibility=uia: could not attach: " + t);
        }
    }

    @Override
    public void detach(JXAccessibilityHost host) {
        UiaWindow window = windows.remove(host);
        if (window != null) {
            window.unsubclass();
        }
    }

    @Override
    public void deliver(JXAccessibilityHost host, List<JXAccessibilityEvent> events) {
        UiaWindow window = windows.get(host);
        if (window == null || !window.clientsListening()) {
            return;
        }
        for (JXAccessibilityEvent e : events) {
            try {
                window.raise(e);
            } catch (Throwable t) {
                System.err.println("uia: event " + e + " failed: " + t);
            }
        }
    }

    static boolean is(JXAccessibleNode node, JXAccessibleState state) {
        return node.is(state);
    }
}
