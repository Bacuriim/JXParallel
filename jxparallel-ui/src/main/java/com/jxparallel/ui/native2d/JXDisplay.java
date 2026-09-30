package com.jxparallel.ui.native2d;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;

/**
 * The one thread that talks to GLFW. GLFW is initialised once per process and is not thread safe,
 * so every window of the process is created, painted and polled here, and other threads only post
 * tasks ({@link #post}, {@link #call}). The loop sleeps in {@code glfwWaitEvents} until input, a
 * task or a render request arrives.
 */
public final class JXDisplay {
    private static final Object LOCK = new Object();
    private static volatile Thread thread;
    private static final ConcurrentLinkedQueue<Runnable> TASKS = new ConcurrentLinkedQueue<Runnable>();
    private static final List<JXWindow> WINDOWS = new CopyOnWriteArrayList<JXWindow>();

    private JXDisplay() {
    }

    private static volatile boolean prewarmed;

    /**
     * Starts the costly one-time work of the first window in the background, while the application
     * builds its screen, as JavaFX starts its render thread and graphics pipeline with the toolkit:
     * GLFW, the OpenGL driver (loaded by a throwaway hidden context), the text engine and fonts, and
     * the painting classes. The first {@code show()} then only creates its window. Returns at once;
     * does nothing without a display ({@code -Djx.headless=true}) or when called again.
     */
    public static void prewarm() {
        if (prewarmed || Boolean.getBoolean("jx.headless")) {
            return;
        }
        prewarmed = true;
        Thread t = new Thread(() -> {
            try {
                post(JXDisplay::warmDriver);
                com.jxparallel.ui.text.JXTextEngine.get();
                com.jxparallel.ui.text.JXTextEngine.get(true);
                for (String name : new String[]{"JXPaint", "JXNativeNode", "JXControlLayout", "JXBoxLayout", "JXGridLayout",
                        "JXTextLayout", "JXNanoVGPainter"}) {
                    Class.forName("com.jxparallel.ui.native2d." + name, true, JXDisplay.class.getClassLoader());
                }
            } catch (Throwable e) {
                // only a head start: the first window does the same work if this failed
            }
        }, "JX prewarm");
        t.setDaemon(true);
        t.start();
    }

    /** The hidden window the prewarm made, with its OpenGL context, until the first window takes it. */
    private static long spareWindow;
    private static org.lwjgl.opengl.GLCapabilities spareCapabilities;

    /**
     * Display thread: creates a hidden window with an OpenGL context, which loads the driver before
     * the first window, and keeps it for that window: creating a second context would repeat most
     * of the driver's work (about 140 ms of CPU on Java 8 32-bit).
     */
    private static void warmDriver() {
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_STENCIL_BITS, 8);
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        long handle = GLFW.glfwCreateWindow(1, 1, "", org.lwjgl.system.MemoryUtil.NULL, org.lwjgl.system.MemoryUtil.NULL);
        if (handle != org.lwjgl.system.MemoryUtil.NULL) {
            GLFW.glfwMakeContextCurrent(handle);
            org.lwjgl.opengl.GLCapabilities capabilities = org.lwjgl.opengl.GL.createCapabilities();
            // Only the client VM (32-bit Java 8) stalled a frame compiling NanoVG's wrappers; on the
            // server VM the warm-up only costs CPU (measured on 64-bit Java 8).
            boolean clientVm = System.getProperty("java.vm.name", "").contains("Client");
            if (clientVm && JXWindow.NANOVG.equals(JXWindow.selectRenderer())) {
                try {
                    JXNanoVGRenderer.warmNatives(); // compile the natives a frame calls now, not in a frame
                } catch (Throwable e) {
                    // only a head start: the first frames link them if this failed
                }
            }
            GLFW.glfwMakeContextCurrent(org.lwjgl.system.MemoryUtil.NULL);
            if (spareWindow == org.lwjgl.system.MemoryUtil.NULL) {
                spareWindow = handle;
                spareCapabilities = capabilities;
            } else {
                GLFW.glfwDestroyWindow(handle);
            }
        }
    }

    /** Display thread: the prewarmed hidden window, once, or NULL; {@link #spareCapabilities()} are its context's. */
    static long takeSpareWindow() {
        long handle = spareWindow;
        spareWindow = org.lwjgl.system.MemoryUtil.NULL;
        return handle;
    }

    static org.lwjgl.opengl.GLCapabilities spareCapabilities() {
        return spareCapabilities;
    }

    /** True on the display thread. */
    public static boolean isDisplayThread() {
        return Thread.currentThread() == thread;
    }

    /** Runs {@code task} on the display thread, starting it if needed. Safe from any thread. */
    public static void post(Runnable task) {
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }
        start();
        TASKS.add(task);
        GLFW.glfwPostEmptyEvent();
    }

    /** Runs {@code task} on the display thread and waits for its result (directly when already there). */
    public static <T> T call(Callable<T> task) {
        if (isDisplayThread()) {
            try {
                return task.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        AtomicReference<Object> result = new AtomicReference<Object>();
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        CountDownLatch done = new CountDownLatch(1);
        post(() -> {
            try {
                result.set(task.call());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        try {
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        Throwable t = failure.get();
        if (t instanceof RuntimeException) {
            throw (RuntimeException) t;
        }
        if (t instanceof Error) {
            throw (Error) t;
        }
        if (t != null) {
            throw new IllegalStateException(t);
        }
        @SuppressWarnings("unchecked")
        T value = (T) result.get();
        return value;
    }

    /** Wakes the loop so it paints requested frames. Safe from any thread. */
    static void wake() {
        if (thread != null) {
            GLFW.glfwPostEmptyEvent();
        }
    }

    static void add(JXWindow window) {
        WINDOWS.add(window);
    }

    static void remove(JXWindow window) {
        WINDOWS.remove(window);
    }

    /** Windows open right now (display thread view). */
    static List<JXWindow> windows() {
        return WINDOWS;
    }

    private static void start() {
        if (thread != null) {
            return;
        }
        synchronized (LOCK) {
            if (thread != null) {
                return;
            }
            CountDownLatch ready = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
            Thread t = new Thread(() -> run(ready, failure), "JX Display");
            t.setDaemon(true); // windows opened with show() keep their caller waiting; nothing else must keep the JVM alive
            thread = t;
            t.start();
            try {
                ready.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (failure.get() != null) {
                thread = null;
                throw new IllegalStateException("Unable to initialize GLFW", failure.get());
            }
        }
    }

    private static void run(CountDownLatch ready, AtomicReference<Throwable> failure) {
        try {
            GLFWErrorCallback.createPrint(System.err).set();
            if (!GLFW.glfwInit()) {
                throw new IllegalStateException("glfwInit failed");
            }
        } catch (Throwable t) {
            failure.set(t);
            ready.countDown();
            return;
        }
        ready.countDown();
        while (true) {
            Runnable task;
            while ((task = TASKS.poll()) != null) {
                try {
                    task.run();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
            for (JXWindow window : WINDOWS) {
                try {
                    window.frameIfRequested();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
            if (TASKS.isEmpty()) {
                // A timeout keeps animations and timers that do not post a task ticking.
                GLFW.glfwWaitEventsTimeout(0.25);
            } else {
                GLFW.glfwPollEvents();
            }
        }
    }
}
