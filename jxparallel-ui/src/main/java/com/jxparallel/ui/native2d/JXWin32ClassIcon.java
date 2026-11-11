package com.jxparallel.ui.native2d;

import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.system.JNI;
import org.lwjgl.system.windows.User32;

/**
 * Windows only: makes a window's icons the icons of its window class as well.
 *
 * <p>The task bar asks the window for its big icon ({@code WM_GETICON}). When the answer comes late, which happens while the
 * display thread is busy opening the window on a slower machine, it shows the window class's icon instead, and the class
 * GLFW registers carries the system's generic application icon. With the window's icons also set on the class, that
 * fallback shows the application's icon too.
 *
 * <p>GLFW frees a window's icons when the window is destroyed or its icons change, so the class gets back the icons it had
 * before ({@link #restore}) before that happens. Every method is a no-op outside Windows; display thread only.
 */
final class JXWin32ClassIcon {
    private static final int GCLP_HICON = -14;
    private static final int GCLP_HICONSM = -34;
    private static final int ICON_SMALL = 0;
    private static final int ICON_BIG = 1;
    static final boolean WINDOWS = System.getProperty("os.name", "").startsWith("Windows");

    private long hwnd;
    private long[] before; // the class icons before this window set its own: big, small
    private long[] mine;   // the icons this window put on the class: big, small

    /** After {@code glfwSetWindowIcon}: puts the window's icons on its class. */
    void apply(long glfwWindow) {
        if (!WINDOWS) {
            return;
        }
        try {
            restore();
            long window = GLFWNativeWin32.glfwGetWin32Window(glfwWindow);
            // SendMessage returns an LRESULT (the icon handle); LWJGL's User32.SendMessage narrows it to a boolean
            long big = JNI.callPPPP(window, User32.WM_GETICON, (long) ICON_BIG, 0L, User32.Functions.SendMessage);
            long small = JNI.callPPPP(window, User32.WM_GETICON, (long) ICON_SMALL, 0L, User32.Functions.SendMessage);
            if (big == 0L) {
                return;
            }
            if (small == 0L) {
                small = big;
            }
            long oldBig = User32.SetClassLongPtr(window, GCLP_HICON, big);
            long oldSmall = User32.SetClassLongPtr(window, GCLP_HICONSM, small);
            hwnd = window;
            before = new long[] {oldBig, oldSmall};
            mine = new long[] {big, small};
        } catch (Throwable e) {
            // a missing binding or an unusual platform: the window keeps its own icons, only the fallback is generic
            hwnd = 0L;
            before = null;
            mine = null;
        }
    }

    /** Before the window is destroyed or its icons change: gives the class back the icons it had, if it still has ours. */
    void restore() {
        if (!WINDOWS || mine == null) {
            return;
        }
        try {
            if (User32.GetClassLongPtr(hwnd, GCLP_HICON) == mine[0]) {
                User32.SetClassLongPtr(hwnd, GCLP_HICON, before[0]);
            }
            if (User32.GetClassLongPtr(hwnd, GCLP_HICONSM) == mine[1]) {
                User32.SetClassLongPtr(hwnd, GCLP_HICONSM, before[1]);
            }
        } catch (Throwable ignored) {
            // nothing to give back
        } finally {
            hwnd = 0L;
            before = null;
            mine = null;
        }
    }
}
