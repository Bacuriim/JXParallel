package com.jxparallel.ui.native2d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.input.JXClipboard;
import com.jxparallel.ui.text.JXTextEngine;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NanoVG;
import org.lwjgl.nanovg.NanoVGGL3;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryUtil;

/**
 * Native window: GLFW owns the window and the OpenGL context, on the {@link JXDisplay} thread
 * shared by every window of the process. 64-bit JVMs on Java 9+ paint with Skia (Skija); 32-bit
 * JVMs and Java 8, where Skija has no working native library, paint with NanoVG.
 * {@code -Djx.renderer=skia|nanovg} forces one.
 *
 * <p>{@link #show()} opens the window and blocks until it closes; {@link #open()} returns at once.
 * Without an {@linkplain #setInputListener input listener} a primary click calls the hit node's
 * {@code onClick}/{@code onAction} and keys go to the last clicked node; with one, every raw event
 * goes to the listener instead and the close button only reports {@link JXInputEvent.Kind#CLOSE_REQUEST}.
 */
public final class JXWindow implements AutoCloseable {
    public static final String SKIA = "skia";
    public static final String NANOVG = "nanovg";
    /** Two presses of the same button closer than this (ms and px) count as a double click, like Windows. */
    static final long MULTI_CLICK_MILLIS = 500;
    static final double MULTI_CLICK_DISTANCE = 4;

    private final String rendererName = selectRenderer();
    private final AtomicBoolean renderRequested = new AtomicBoolean(true);
    private final CountDownLatch closed = new CountDownLatch(1);
    private volatile String title;
    private volatile int width = 640;
    private volatile int height = 420;
    private volatile int minWidth = -1;
    private volatile int minHeight = -1;
    private volatile int x = Integer.MIN_VALUE;
    private volatile int y = Integer.MIN_VALUE;
    private volatile boolean resizable = true;
    private volatile boolean decorated = true;
    private volatile boolean floating;
    private volatile long window = MemoryUtil.NULL;
    private volatile boolean disposed;
    /* Display thread state. */
    private JXNativeNode root;
    private JXNativeNode focusedNode;
    private Runnable onFirstPaint;
    private Runnable onFrame;
    private volatile Runnable onClosed;
    private volatile Consumer<JXInputEvent> inputListener;
    private boolean firstPaintReported;
    private Backend backend;
    private GLCapabilities capabilities;
    private double cursorX;
    private double cursorY;
    private int buttonsDown;
    private int lastButton = -1;
    private long lastPressTime;
    private double lastPressX;
    private double lastPressY;
    private int clickCount;

    public JXWindow(String title) {
        this.title = title == null ? "JXParallel" : title;
    }

    /** {@link #SKIA} or {@link #NANOVG}. */
    public String getRendererName() {
        return rendererName;
    }

    static String selectRenderer() {
        String forced = System.getProperty("jx.renderer");
        if (forced != null && (SKIA.equalsIgnoreCase(forced) || NANOVG.equalsIgnoreCase(forced))) {
            return forced.toLowerCase(Locale.ROOT);
        }
        String dataModel = System.getProperty("sun.arch.data.model");
        String arch = System.getProperty("os.arch", "");
        boolean is32Bit = "32".equals(dataModel)
                || (dataModel == null && (arch.equals("x86") || arch.matches("i[3-6]86")));
        // Skija 0.116 crashes the JVM while loading its library on Java 8 (checked on 8u202 x64).
        boolean java8 = System.getProperty("java.specification.version", "").startsWith("1.");
        return is32Bit || java8 ? NANOVG : SKIA;
    }

    /**
     * Shows {@code element}. Calling it again with a new tree reconciles in place: unchanged nodes,
     * their layout caches and the focused node are kept. Safe from any thread.
     */
    public void setContent(JXElement element) {
        if (!JXDisplay.isDisplayThread()) {
            JXDisplay.post(() -> setContent(element));
            return;
        }
        if (root == null || !root.reconcile(element)) {
            root = JXNativeNode.createBackendNode(element);
            focusedNode = null;
        }
        requestRender();
    }

    /** The mounted tree; display thread only. */
    public JXNativeNode getRoot() {
        return root;
    }

    /**
     * Paints a tree owned by another thread: that thread builds, reconciles and lays it out while
     * holding {@code lock}, and every frame paints it holding the same lock. Pointer events then
     * carry no path (the owner hit-tests its own tree). Call {@link #requestRender} after changes.
     */
    public void setRoot(JXNativeNode tree, Object lock) {
        externalLock = lock;
        if (JXDisplay.isDisplayThread()) {
            root = tree;
            focusedNode = null;
        } else {
            JXDisplay.post(() -> {
                root = tree;
                focusedNode = null;
            });
        }
        requestRender();
    }

    private volatile Object externalLock;

    public void setOnFirstPaint(Runnable callback) {
        onFirstPaint = callback;
    }

    /** Runs on the display thread after every presented frame (after buffer swap). */
    public void setOnFrame(Runnable callback) {
        onFrame = callback;
    }

    /** Runs on the display thread once the window is gone. */
    public void setOnClosed(Runnable callback) {
        onClosed = callback;
    }

    /** Receives every raw event on the display thread; see the class comment. */
    public void setInputListener(Consumer<JXInputEvent> listener) {
        inputListener = listener;
    }

    public void setTitle(String value) {
        title = value == null ? "" : value;
        long handle = window;
        if (handle != MemoryUtil.NULL) {
            JXDisplay.post(() -> {
                if (window != MemoryUtil.NULL) {
                    GLFW.glfwSetWindowTitle(window, title);
                }
            });
        }
    }

    public String getTitle() {
        return title;
    }

    /** Size of the content area in pixels; applied when the window opens, or at once if open. */
    public void setSize(int newWidth, int newHeight) {
        width = Math.max(1, newWidth);
        height = Math.max(1, newHeight);
        if (window != MemoryUtil.NULL) {
            JXDisplay.post(() -> {
                if (window != MemoryUtil.NULL) {
                    GLFW.glfwSetWindowSize(window, width, height);
                }
            });
        }
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void setMinSize(int newMinWidth, int newMinHeight) {
        minWidth = newMinWidth;
        minHeight = newMinHeight;
    }

    /** Screen position of the content area; unset means centred on the primary monitor. */
    public void setPosition(int newX, int newY) {
        x = newX;
        y = newY;
        if (window != MemoryUtil.NULL) {
            JXDisplay.post(() -> {
                if (window != MemoryUtil.NULL) {
                    GLFW.glfwSetWindowPos(window, x, y);
                }
            });
        }
    }

    public void setResizable(boolean value) {
        resizable = value;
    }

    /** Title bar and borders; false for popups and notifications. Set before opening. */
    public void setDecorated(boolean value) {
        decorated = value;
    }

    /** Always on top of other windows. Set before opening. */
    public void setFloating(boolean value) {
        floating = value;
    }

    /** Whether showing the window takes the keyboard focus (false for notifications). Set before opening. */
    public void setFocusOnShow(boolean value) {
        focusOnShow = value;
    }

    private volatile boolean focusOnShow = true;

    public boolean isOpen() {
        return window != MemoryUtil.NULL;
    }

    public void invokeLater(Runnable action) {
        if (action == null) {
            throw new IllegalArgumentException("Action cannot be null");
        }
        JXDisplay.post(action);
    }

    /** Coalesced: only the first request after a frame wakes the loop. Safe from any thread. */
    public void requestRender() {
        if (renderRequested.compareAndSet(false, true)) {
            JXDisplay.wake();
        }
    }

    public void renderNow() {
        requestRender();
    }

    /** System clipboard through GLFW. Safe from any thread. */
    public JXClipboard clipboard() {
        return systemClipboard();
    }

    /** The system clipboard (text), usable without a window. Safe from any thread. */
    public static JXClipboard systemClipboard() {
        return new JXClipboard() {
            @Override
            public String getText() {
                return JXDisplay.call(() -> GLFW.glfwGetClipboardString(MemoryUtil.NULL));
            }

            @Override
            public void setText(String value) {
                JXDisplay.call(() -> {
                    GLFW.glfwSetClipboardString(MemoryUtil.NULL, value == null ? "" : value);
                    return null;
                });
            }
        };
    }

    /** Size of the primary monitor's work area: x, y, width, height. Safe from any thread. */
    public static int[] screenBounds() {
        return JXDisplay.call(() -> {
            int[] x = new int[1];
            int[] y = new int[1];
            int[] w = new int[1];
            int[] h = new int[1];
            long monitor = GLFW.glfwGetPrimaryMonitor();
            if (monitor == MemoryUtil.NULL) {
                return new int[] {0, 0, 1280, 800};
            }
            GLFW.glfwGetMonitorWorkarea(monitor, x, y, w, h);
            return new int[] {x[0], y[0], w[0], h[0]};
        });
    }

    /**
     * Paints {@code element} with NanoVG in a hidden window and returns the pixels, ARGB, row by row
     * from the top. For screen comparisons on JVMs where only NanoVG runs (32-bit, Java 8).
     */
    public static int[] captureNanoVG(JXElement element, int width, int height) {
        return JXDisplay.call(() -> {
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_STENCIL_BITS, 8);
            long hidden = GLFW.glfwCreateWindow(width, height, "capture", MemoryUtil.NULL, MemoryUtil.NULL);
            if (hidden == MemoryUtil.NULL) {
                throw new IllegalStateException("Unable to create GLFW window");
            }
            try {
                GLFW.glfwMakeContextCurrent(hidden);
                GL.createCapabilities();
                int[] fbw = new int[1];
                int[] fbh = new int[1];
                GLFW.glfwGetFramebufferSize(hidden, fbw, fbh);
                NanoVGBackend backend = new NanoVGBackend();
                try {
                    backend.render(JXNativeNode.createBackendNode(element), fbw[0], fbh[0], width, height);
                    GL11.glFinish();
                    java.nio.ByteBuffer rgba = MemoryUtil.memAlloc(fbw[0] * fbh[0] * 4);
                    try {
                        GL11.glReadPixels(0, 0, fbw[0], fbh[0], GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, rgba);
                        int[] argb = new int[width * height];
                        for (int row = 0; row < height && row < fbh[0]; row++) {
                            int offset = (fbh[0] - 1 - row) * fbw[0] * 4;
                            for (int col = 0; col < width && col < fbw[0]; col++) {
                                int i = offset + col * 4;
                                argb[row * width + col] = 0xFF000000 | (rgba.get(i) & 0xFF) << 16
                                        | (rgba.get(i + 1) & 0xFF) << 8 | (rgba.get(i + 2) & 0xFF);
                            }
                        }
                        return argb;
                    } finally {
                        MemoryUtil.memFree(rgba);
                    }
                } finally {
                    backend.close();
                }
            } finally {
                GLFW.glfwMakeContextCurrent(MemoryUtil.NULL);
                GLFW.glfwDestroyWindow(hidden);
            }
        });
    }

    /** Opens the window and returns; the window lives on the display thread. */
    public void open() {
        JXDisplay.call(() -> {
            create();
            return null;
        });
    }

    /** Opens the window and blocks until it is closed. Not on the display thread. */
    public void show() {
        if (JXDisplay.isDisplayThread()) {
            throw new IllegalStateException("show() blocks; use open() on the display thread");
        }
        open();
        awaitClosed();
    }

    /** Blocks until the window is closed. */
    public void awaitClosed() {
        try {
            closed.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void create() {
        if (window != MemoryUtil.NULL || disposed) {
            return;
        }
        long handle = JXDisplay.takeSpareWindow();
        boolean adopted = handle != MemoryUtil.NULL;
        if (adopted) {
            // the prewarmed hidden window, its OpenGL context already made: set it up as this window
            GLFW.glfwSetWindowTitle(handle, title);
            GLFW.glfwSetWindowSize(handle, width, height);
            GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_RESIZABLE, resizable ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
            GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, decorated ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
            GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_FLOATING, floating ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
            GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_FOCUS_ON_SHOW, focusOnShow ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
        } else {
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_STENCIL_BITS, 8);
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, resizable ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_DECORATED, decorated ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FLOATING, floating ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, focusOnShow ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
            handle = GLFW.glfwCreateWindow(width, height, title, MemoryUtil.NULL, MemoryUtil.NULL);
        }
        if (handle == MemoryUtil.NULL) {
            throw new IllegalStateException("Unable to create GLFW window");
        }
        window = handle;
        if (minWidth > 0 || minHeight > 0) {
            GLFW.glfwSetWindowSizeLimits(handle, minWidth > 0 ? minWidth : GLFW.GLFW_DONT_CARE,
                    minHeight > 0 ? minHeight : GLFW.GLFW_DONT_CARE, GLFW.GLFW_DONT_CARE, GLFW.GLFW_DONT_CARE);
        }
        place(handle);
        installCallbacks(handle);
        GLFW.glfwMakeContextCurrent(handle);
        GLFW.glfwSwapInterval(1);
        if (adopted && JXDisplay.spareCapabilities() != null) {
            capabilities = JXDisplay.spareCapabilities();
            GL.setCapabilities(capabilities);
        } else {
            capabilities = GL.createCapabilities();
        }
        // The Skia class is only loaded here, so 32-bit JVMs never touch Skija.
        backend = NANOVG.equals(rendererName) ? new NanoVGBackend() : new SkiaBackend();
        JXDisplay.add(this);
        GLFW.glfwShowWindow(handle);
        requestRender();
    }

    /** Configured position, else {@code -Djx.monitor=N} (0 = primary), else centred on the primary monitor. */
    private void place(long handle) {
        if (x != Integer.MIN_VALUE && y != Integer.MIN_VALUE) {
            GLFW.glfwSetWindowPos(handle, x, y);
            return;
        }
        org.lwjgl.PointerBuffer monitors = GLFW.glfwGetMonitors();
        Integer index = Integer.getInteger("jx.monitor");
        long monitor = monitors != null && index != null && index >= 0 && index < monitors.limit()
                ? monitors.get(index) : GLFW.glfwGetPrimaryMonitor();
        if (monitor == MemoryUtil.NULL) {
            return;
        }
        int[] mx = new int[1];
        int[] my = new int[1];
        int[] mw = new int[1];
        int[] mh = new int[1];
        GLFW.glfwGetMonitorWorkarea(monitor, mx, my, mw, mh);
        GLFW.glfwSetWindowPos(handle, mx[0] + Math.max(0, (mw[0] - width) / 2), my[0] + Math.max(0, (mh[0] - height) / 2));
    }

    private void installCallbacks(long handle) {
        GLFW.glfwSetFramebufferSizeCallback(handle, (h, w, hh) -> requestRender());
        GLFW.glfwSetWindowSizeCallback(handle, (h, w, hh) -> {
            if (w > 0 && hh > 0) {
                width = w;
                height = hh;
                deliver(JXInputEvent.window(JXInputEvent.Kind.RESIZE, w, hh));
            }
            requestRender();
        });
        GLFW.glfwSetWindowRefreshCallback(handle, h -> requestRender());
        GLFW.glfwSetWindowFocusCallback(handle, (h, focused) ->
                deliver(JXInputEvent.window(focused ? JXInputEvent.Kind.FOCUS_GAINED : JXInputEvent.Kind.FOCUS_LOST, width, height)));
        GLFW.glfwSetWindowCloseCallback(handle, h -> {
            if (inputListener != null) {
                GLFW.glfwSetWindowShouldClose(h, false);
                deliver(JXInputEvent.window(JXInputEvent.Kind.CLOSE_REQUEST, width, height));
            } else {
                dispose();
            }
        });
        GLFW.glfwSetCursorPosCallback(handle, (h, px, py) -> {
            cursorX = px;
            cursorY = py;
            if (inputListener != null) {
                deliver(JXInputEvent.pointer(JXInputEvent.Kind.MOVE, px, py, lastButton < 0 ? 0 : lastButton, currentModifiers(h),
                        0, buttonsDown != 0, path(px, py)));
            }
        });
        GLFW.glfwSetCursorEnterCallback(handle, (h, entered) -> {
            if (!entered && inputListener != null) {
                deliver(JXInputEvent.pointer(JXInputEvent.Kind.EXIT, cursorX, cursorY, 0, 0, 0, buttonsDown != 0,
                        new ArrayList<JXNativeNode>()));
            }
        });
        GLFW.glfwSetMouseButtonCallback(handle, (h, glfwButton, action, mods) -> {
            int button = glfwButton == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? JXInputEvent.BUTTON_SECONDARY
                    : glfwButton == GLFW.GLFW_MOUSE_BUTTON_MIDDLE ? JXInputEvent.BUTTON_MIDDLE : JXInputEvent.BUTTON_PRIMARY;
            double[] px = new double[1];
            double[] py = new double[1];
            GLFW.glfwGetCursorPos(h, px, py);
            cursorX = px[0];
            cursorY = py[0];
            if (action == GLFW.GLFW_PRESS) {
                buttonsDown |= 1 << button;
                long now = System.currentTimeMillis();
                boolean again = button == lastButton && now - lastPressTime <= MULTI_CLICK_MILLIS
                        && Math.abs(px[0] - lastPressX) <= MULTI_CLICK_DISTANCE && Math.abs(py[0] - lastPressY) <= MULTI_CLICK_DISTANCE;
                clickCount = again ? clickCount + 1 : 1;
                lastButton = button;
                lastPressTime = now;
                lastPressX = px[0];
                lastPressY = py[0];
            } else {
                buttonsDown &= ~(1 << button);
            }
            if (inputListener != null) {
                deliver(JXInputEvent.pointer(action == GLFW.GLFW_PRESS ? JXInputEvent.Kind.PRESS : JXInputEvent.Kind.RELEASE,
                        px[0], py[0], button, JXKeys.modifiers(mods), clickCount, buttonsDown != 0, path(px[0], py[0])));
                return;
            }
            if (button != JXInputEvent.BUTTON_PRIMARY || action != GLFW.GLFW_PRESS || root == null) {
                return;
            }
            JXNativeNode hit = root.hitTest((int) px[0], (int) py[0]);
            if (hit != null) {
                focusedNode = hit;
                hit.dispatchPointer(new JXPointerEvent((int) px[0], (int) py[0], glfwButton));
                requestRender();
            }
        });
        GLFW.glfwSetScrollCallback(handle, (h, dx, dy) -> {
            if (inputListener != null) {
                deliver(JXInputEvent.scroll(cursorX, cursorY, dx, dy, currentModifiers(h), path(cursorX, cursorY)));
            }
        });
        GLFW.glfwSetKeyCallback(handle, (h, key, scancode, action, mods) -> {
            if (inputListener != null) {
                deliver(JXInputEvent.key(action == GLFW.GLFW_RELEASE ? JXInputEvent.Kind.KEY_RELEASE : JXInputEvent.Kind.KEY_PRESS,
                        JXKeys.name(key), JXKeys.modifiers(mods)));
                return;
            }
            if (action != GLFW.GLFW_RELEASE && focusedNode != null) {
                focusedNode.dispatchKey(new JXKeyEvent(key, '\0'));
                requestRender();
            }
        });
        GLFW.glfwSetCharModsCallback(handle, (h, codepoint, mods) -> {
            if (inputListener != null) {
                deliver(JXInputEvent.character(codepoint, JXKeys.modifiers(mods)));
                return;
            }
            if (focusedNode != null) {
                focusedNode.dispatchKey(new JXKeyEvent(0, (char) codepoint));
                requestRender();
            }
        });
    }

    private static int currentModifiers(long handle) {
        int m = 0;
        if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS) {
            m |= JXInputEvent.SHIFT;
        }
        if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS) {
            m |= JXInputEvent.CONTROL;
        }
        if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS) {
            m |= JXInputEvent.ALT;
        }
        return m;
    }

    private List<JXNativeNode> path(double px, double py) {
        if (root == null || externalLock != null) {
            return new ArrayList<JXNativeNode>();
        }
        return root.hitPath((int) Math.floor(px), (int) Math.floor(py));
    }

    private void deliver(JXInputEvent event) {
        Consumer<JXInputEvent> listener = inputListener;
        if (listener != null) {
            listener.accept(event);
        }
    }

    /** Paints a frame if one was requested. Display thread only. */
    void frameIfRequested() {
        if (window == MemoryUtil.NULL || !renderRequested.getAndSet(false)) {
            return;
        }
        int[] fbWidth = new int[1];
        int[] fbHeight = new int[1];
        int[] winWidth = new int[1];
        int[] winHeight = new int[1];
        GLFW.glfwGetFramebufferSize(window, fbWidth, fbHeight);
        GLFW.glfwGetWindowSize(window, winWidth, winHeight);
        if (fbWidth[0] <= 0 || fbHeight[0] <= 0 || winWidth[0] <= 0 || winHeight[0] <= 0) {
            return; // minimized
        }
        if (GLFW.glfwGetCurrentContext() != window) {
            // switching contexts is costly on Windows (wglMakeCurrent); with one window it is already current
            GLFW.glfwMakeContextCurrent(window);
            GL.setCapabilities(capabilities);
        }
        Object lock = externalLock;
        if (lock != null) {
            synchronized (lock) {
                backend.render(root, fbWidth[0], fbHeight[0], winWidth[0], winHeight[0]);
            }
        } else {
            backend.render(root, fbWidth[0], fbHeight[0], winWidth[0], winHeight[0]);
        }
        GLFW.glfwSwapBuffers(window);
        if (!firstPaintReported) {
            firstPaintReported = true;
            if (backend instanceof NanoVGBackend) {
                JXNanoVGRenderer.startWarmCalls();
            }
            if (onFirstPaint != null) {
                onFirstPaint.run();
            }
        }
        if (onFrame != null) {
            onFrame.run();
        }
    }

    private void release() {
        long handle = window;
        if (handle == MemoryUtil.NULL) {
            return;
        }
        JXDisplay.remove(this);
        GLFW.glfwMakeContextCurrent(handle);
        GL.setCapabilities(capabilities);
        if (backend != null) {
            backend.close();
            backend = null;
        }
        GLFW.glfwMakeContextCurrent(MemoryUtil.NULL);
        GLFW.glfwDestroyWindow(handle);
        window = MemoryUtil.NULL;
    }

    /** Closes the window; resources are released on the display thread. Safe from any thread. */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        JXDisplay.post(() -> {
            release();
            closed.countDown();
            Runnable callback = onClosed;
            if (callback != null) {
                callback.run();
            }
        });
    }

    @Override
    public void close() {
        dispose();
    }

    /** Paints one frame into the current OpenGL context. Display thread only. */
    private interface Backend {
        void render(JXNativeNode root, int fbWidth, int fbHeight, int winWidth, int winHeight);

        void close();
    }

    private static final class SkiaBackend implements Backend {
        private static final int GL_FRAMEBUFFER_BINDING = 0x8CA6;
        private final io.github.humbleui.skija.DirectContext context = io.github.humbleui.skija.DirectContext.makeGL();
        private io.github.humbleui.skija.BackendRenderTarget renderTarget;
        private io.github.humbleui.skija.Surface surface;
        private int width;
        private int height;

        @Override
        public void render(JXNativeNode root, int fbWidth, int fbHeight, int winWidth, int winHeight) {
            if (surface == null || fbWidth != width || fbHeight != height) {
                recreateSurface(fbWidth, fbHeight);
            }
            context.resetAll(); // several windows share the thread: GL state may belong to another context's last frame
            if (root != null) {
                io.github.humbleui.skija.Canvas canvas = surface.getCanvas();
                canvas.save();
                canvas.scale((float) fbWidth / winWidth, (float) fbHeight / winHeight);
                JXSkiaRenderer.paint(root, canvas, winWidth, winHeight);
                canvas.restore();
            }
            context.flush();
        }

        private void recreateSurface(int newWidth, int newHeight) {
            closeSurface();
            width = newWidth;
            height = newHeight;
            int framebufferId = GL11.glGetInteger(GL_FRAMEBUFFER_BINDING);
            renderTarget = io.github.humbleui.skija.BackendRenderTarget.makeGL(width, height, 0, 8, framebufferId,
                    io.github.humbleui.skija.FramebufferFormat.GR_GL_RGBA8);
            surface = io.github.humbleui.skija.Surface.makeFromBackendRenderTarget(context, renderTarget,
                    io.github.humbleui.skija.SurfaceOrigin.BOTTOM_LEFT,
                    io.github.humbleui.skija.SurfaceColorFormat.RGBA_8888,
                    io.github.humbleui.skija.ColorSpace.getSRGB());
        }

        private void closeSurface() {
            if (surface != null) {
                surface.close();
                surface = null;
            }
            if (renderTarget != null) {
                renderTarget.close();
                renderTarget = null;
            }
        }

        @Override
        public void close() {
            closeSurface();
            context.close();
        }
    }

    private static final class NanoVGBackend implements Backend {
        private final long vg = NanoVGGL3.nvgCreate(NanoVGGL3.NVG_ANTIALIAS | NanoVGGL3.NVG_STENCIL_STROKES);
        private final NVGColor color = NVGColor.create();
        private final boolean hasFont;
        private boolean hasBold;

        NanoVGBackend() {
            if (vg == MemoryUtil.NULL) {
                throw new IllegalStateException("Unable to create NanoVG context (OpenGL 3 required)");
            }
            String font = JXTextEngine.get().getFontFile();
            hasFont = font != null && NanoVG.nvgCreateFont(vg, JXNanoVGRenderer.FONT, font) >= 0;
            if (!hasFont) {
                System.err.println("JXParallel: no TrueType font found, text will not be drawn; set -Djx.font=<path>");
            }
            String bold = JXTextEngine.findBoldFontFile();
            hasBold = hasFont && bold != null && NanoVG.nvgCreateFont(vg, JXNanoVGRenderer.FONT_BOLD, bold) >= 0;
        }

        @Override
        public void render(JXNativeNode root, int fbWidth, int fbHeight, int winWidth, int winHeight) {
            int background = JXNanoVGRenderer.BACKGROUND;
            GL11.glViewport(0, 0, fbWidth, fbHeight);
            GL11.glClearColor(((background >> 16) & 0xFF) / 255.0f, ((background >> 8) & 0xFF) / 255.0f,
                    (background & 0xFF) / 255.0f, 1.0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            if (root == null) {
                return;
            }
            NanoVG.nvgBeginFrame(vg, winWidth, winHeight, (float) fbWidth / winWidth);
            JXNanoVGRenderer.paint(root, vg, color, hasFont, hasBold, winWidth, winHeight);
            NanoVG.nvgEndFrame(vg);
        }

        @Override
        public void close() {
            JXNanoVGPainter.forget(vg);
            NanoVGGL3.nvgDelete(vg);
        }
    }
}
