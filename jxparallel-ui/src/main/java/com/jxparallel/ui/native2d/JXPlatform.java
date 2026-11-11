package com.jxparallel.ui.native2d;

import java.lang.management.ManagementFactory;
import java.util.Locale;

import org.lwjgl.glfw.GLFW;

/**
 * What differs between operating systems for the native window, in one place. Free of GLFW calls except
 * {@link #applyContextHints()}; nothing here loads a Windows-only or macOS-only native library.
 */
final class JXPlatform {
    private static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    private static final boolean MAC = OS.startsWith("mac") || OS.contains("darwin");

    private JXPlatform() {
    }

    /** True on macOS. */
    static boolean isMac() {
        return MAC;
    }

    /** True on Windows. */
    static boolean isWindows() {
        return OS.startsWith("windows");
    }

    /**
     * True when the JVM was started with {@code -XstartOnFirstThread}: the java launcher then exports
     * {@code JAVA_STARTED_ON_FIRST_THREAD_<pid>=1}, and the thread that runs {@code main} is the process's
     * first thread, the only one macOS lets create windows and run the event loop.
     */
    static boolean startedOnFirstThread() {
        try {
            String name = ManagementFactory.getRuntimeMXBean().getName(); // "pid@host"
            int at = name.indexOf('@');
            String pid = at > 0 ? name.substring(0, at) : name;
            return "1".equals(System.getenv("JAVA_STARTED_ON_FIRST_THREAD_" + pid));
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Call after {@code glfwDefaultWindowHints()}, before {@code glfwCreateWindow}. macOS only offers OpenGL 2.1
     * (legacy) or core profile 3.2+ that is forward compatible, and both NanoVG (GL3 backend, GLSL 150) and Skia
     * need the latter; the other systems keep GLFW's defaults, which is what has been measured on them.
     */
    static void applyContextHints() {
        if (MAC) {
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        }
    }
}
