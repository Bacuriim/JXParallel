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
 * JVMs, where Skija has no native library, and Java 8 paint with NanoVG.
 * {@code -Djx.renderer=skia|nanovg} forces one (Skia also works on Java 8 64-bit).
 *
 * <p>{@link #show()} opens the window and blocks until it closes; {@link #open()} returns at once.
 * Without an {@linkplain #setInputListener input listener} a primary click calls the hit node's
 * {@code onClick}/{@code onAction} and keys go to the last clicked node; with one, every raw event
 * goes to the listener instead and the close button only reports {@link JXInputEvent.Kind#CLOSE_REQUEST}.
 */
public final class JXWindow implements AutoCloseable {
    public static final String SKIA = "skia";
    public static final String NANOVG = "nanovg";
    /** Painted on the CPU and shown with GDI: no OpenGL context (Windows; {@code -Djx.renderer=software}). */
    public static final String SOFTWARE = "software";
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
    /** The node the primary button was pressed on, while it is held (it receives the drag). */
    private JXNativeNode dragNode;
    private Runnable onFirstPaint;
    private Runnable onFrame;
    private volatile Runnable onClosed;
    private volatile Consumer<JXInputEvent> inputListener;
    private boolean firstPaintReported;
    private Backend backend;
    /** Software renderer: the minimum time between frames (one monitor refresh); 0 with OpenGL (vsync). */
    private long frameIntervalNanos;
    private long lastFrameNanos;
    private GLCapabilities capabilities;
    private double cursorX;
    private double cursorY;
    private int buttonsDown;
    private int lastButton = -1;
    private long lastPressTime;
    private double lastPressX;
    private double lastPressY;
    private int clickCount;
    /** Physical pixels per logical pixel ({@link JXScale}); all layout and application coordinates are logical. */
    private volatile float scale = JXScale.resolve(JXScale.forced(), 1f, 1f);
    /** GLFW window units (cursor positions) to logical pixels, per axis; display thread. */
    private double toLogicalX = 1;
    private double toLogicalY = 1;
    /** Framebuffer size in physical pixels as of the last {@link #syncMetrics}; display thread. */
    private int fbWidth;
    private int fbHeight;

    public JXWindow(String title) {
        this.title = title == null ? "JXParallel" : title;
    }

    /** {@link #SKIA} or {@link #NANOVG}. */
    public String getRendererName() {
        return rendererName;
    }

    static String selectRenderer() {
        String forced = System.getProperty("jx.renderer");
        if (SOFTWARE.equalsIgnoreCase(forced) && JXGdiSurface.supported()) {
            return SOFTWARE;
        }
        if (forced != null && (SKIA.equalsIgnoreCase(forced) || NANOVG.equalsIgnoreCase(forced))) {
            return forced.toLowerCase(Locale.ROOT);
        }
        String dataModel = System.getProperty("sun.arch.data.model");
        String arch = System.getProperty("os.arch", "");
        boolean is32Bit = "32".equals(dataModel)
                || (dataModel == null && (arch.equals("x86") || arch.matches("i[3-6]86")));
        // Java 8 keeps NanoVG, the measured configuration. Skija used to crash the JVM there while loading
        // its library: the types 0.1.1 it brings are Java 9 classes; with types 0.2.0 (pom) it loads.
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
            focus(null);
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
            focus(null);
        } else {
            JXDisplay.post(() -> {
                root = tree;
                focus(null);
            });
        }
        requestRender();
    }

    private volatile Object externalLock;

    /** A snack bar with a duration is open: the loop asks the overlays for its timeout at every turn (display thread). */
    private boolean timedOverlay;

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

    /**
     * Size of the content area in logical pixels (physical pixels divided by {@link #getScale()}); applied
     * when the window opens, or at once if open.
     */
    public void setSize(int newWidth, int newHeight) {
        width = Math.max(1, newWidth);
        height = Math.max(1, newHeight);
        if (window != MemoryUtil.NULL) {
            JXDisplay.post(() -> {
                if (window != MemoryUtil.NULL) {
                    applyLogicalSize(window);
                }
            });
        }
    }

    /** Width of the content area in logical pixels. */
    public int getWidth() {
        return width;
    }

    /** Height of the content area in logical pixels. */
    public int getHeight() {
        return height;
    }

    /**
     * Physical pixels per logical pixel: 1 on a 100% display, 1.5 at 150%, 2 at 200%. It is the window's
     * monitor content scale, updated when the window moves to another monitor, or {@code -Djx.scale=N}
     * when set. Before the window opens it is the forced scale or 1. Safe from any thread.
     */
    public float getScale() {
        return scale;
    }

    /** Logical pixels; applied when the window opens, and again when the scale changes. */
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

    /** Whether the user can resize the window; applied when the window opens, or at once if open. */
    public void setResizable(boolean value) {
        resizable = value;
        attribute(GLFW.GLFW_RESIZABLE, value);
    }

    public boolean isResizable() {
        return resizable;
    }

    /** Title bar and borders; false for popups and notifications. Applied when the window opens, or at once if open. */
    public void setDecorated(boolean value) {
        decorated = value;
        attribute(GLFW.GLFW_DECORATED, value);
    }

    public boolean isDecorated() {
        return decorated;
    }

    /** Always on top of other windows. Applied when the window opens, or at once if open. */
    public void setFloating(boolean value) {
        floating = value;
        attribute(GLFW.GLFW_FLOATING, value);
    }

    /** Same as {@link #setFloating}. */
    public void setAlwaysOnTop(boolean value) {
        setFloating(value);
    }

    public boolean isFloating() {
        return floating;
    }

    /** Sets a GLFW window attribute on the display thread, if the window is open. */
    private void attribute(int attrib, boolean value) {
        if (window != MemoryUtil.NULL) {
            JXDisplay.post(() -> {
                if (window != MemoryUtil.NULL) {
                    GLFW.glfwSetWindowAttrib(window, attrib, value ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
                }
            });
        }
    }

    /** Screen position of the content area as of the last move (GLFW window units), or what {@link #setPosition} set. */
    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    private volatile boolean maximized;
    private volatile boolean iconified;
    private volatile boolean fullScreen;
    private volatile List<JXWindowIcon> icons = new ArrayList<JXWindowIcon>();
    /** Position and size (GLFW window units) to go back to when the full screen ends; display thread. */
    private final int[] windowedBounds = new int[4];
    private boolean inFullScreen;

    /** Maximized: applied when the window opens, or at once if open. */
    public void setMaximized(boolean value) {
        maximized = value;
        if (window != MemoryUtil.NULL) {
            JXDisplay.post(() -> {
                if (window != MemoryUtil.NULL) {
                    if (value) {
                        GLFW.glfwMaximizeWindow(window);
                    } else if (!inFullScreen) {
                        GLFW.glfwRestoreWindow(window);
                    }
                }
            });
        }
    }

    /** The window is maximized, as the user or {@link #setMaximized} left it. */
    public boolean isMaximized() {
        return maximized;
    }

    /** Minimized to the task bar: applied when the window opens, or at once if open. */
    public void setIconified(boolean value) {
        iconified = value;
        if (window != MemoryUtil.NULL) {
            JXDisplay.post(() -> {
                if (window != MemoryUtil.NULL) {
                    if (value) {
                        GLFW.glfwIconifyWindow(window);
                    } else {
                        GLFW.glfwRestoreWindow(window);
                    }
                }
            });
        }
    }

    /** Same as {@code setIconified(true)}. */
    public void minimize() {
        setIconified(true);
    }

    public boolean isIconified() {
        return iconified;
    }

    /** Brings a minimized or maximized window back to its normal size. */
    public void restore() {
        if (window != MemoryUtil.NULL) {
            JXDisplay.post(() -> {
                if (window != MemoryUtil.NULL) {
                    GLFW.glfwRestoreWindow(window);
                }
            });
        } else {
            maximized = false;
            iconified = false;
        }
    }

    /**
     * Full screen on the primary monitor, at its current video mode; false goes back to the position and size the window
     * had. Applied when the window opens, or at once if open.
     */
    public void setFullScreen(boolean value) {
        fullScreen = value;
        if (window != MemoryUtil.NULL) {
            JXDisplay.post(() -> applyFullScreen());
        }
    }

    public boolean isFullScreen() {
        return fullScreen;
    }

    /** Display thread. */
    private void applyFullScreen() {
        long handle = window;
        if (handle == MemoryUtil.NULL || fullScreen == inFullScreen) {
            return;
        }
        if (fullScreen) {
            int[] px = new int[1];
            int[] py = new int[1];
            int[] pw = new int[1];
            int[] ph = new int[1];
            GLFW.glfwGetWindowPos(handle, px, py);
            GLFW.glfwGetWindowSize(handle, pw, ph);
            long monitor = GLFW.glfwGetPrimaryMonitor();
            org.lwjgl.glfw.GLFWVidMode mode = monitor == MemoryUtil.NULL ? null : GLFW.glfwGetVideoMode(monitor);
            if (mode == null) {
                fullScreen = false;
                return;
            }
            windowedBounds[0] = px[0];
            windowedBounds[1] = py[0];
            windowedBounds[2] = pw[0];
            windowedBounds[3] = ph[0];
            GLFW.glfwSetWindowMonitor(handle, monitor, 0, 0, mode.width(), mode.height(), mode.refreshRate());
        } else {
            GLFW.glfwSetWindowMonitor(handle, MemoryUtil.NULL, windowedBounds[0], windowedBounds[1], windowedBounds[2],
                    windowedBounds[3], GLFW.GLFW_DONT_CARE);
        }
        inFullScreen = fullScreen;
        requestRender();
    }

    /** Asks the system to give this window the keyboard focus (it may only flash in the task bar). */
    public void requestFocus() {
        if (window != MemoryUtil.NULL) {
            JXDisplay.post(() -> {
                if (window != MemoryUtil.NULL) {
                    GLFW.glfwFocusWindow(window);
                }
            });
        }
    }

    /** One icon size for the title bar and the task bar; applied when the window opens, or at once if open. */
    public void setIcon(int iconWidth, int iconHeight, int[] argb) {
        setIcons(java.util.Collections.singletonList(new JXWindowIcon(iconWidth, iconHeight, argb)));
    }

    /** Several sizes of the icon (16, 32, 48...): the system picks the closest to what it needs. Null or empty: the default icon. */
    public void setIcons(List<JXWindowIcon> sizes) {
        icons = sizes == null ? new ArrayList<JXWindowIcon>() : new ArrayList<JXWindowIcon>(sizes);
        if (window != MemoryUtil.NULL) {
            JXDisplay.post(() -> {
                if (window != MemoryUtil.NULL) {
                    applyIcons(window);
                }
            });
        }
    }

    /** The icon from a picture file ({@link JXWindowIcon#load}). */
    public void setIconFile(String source) throws java.io.IOException {
        setIcons(java.util.Collections.singletonList(JXWindowIcon.load(source)));
    }

    /** The icon from several picture files, one per size. */
    public void setIconFiles(String... sources) throws java.io.IOException {
        setIcons(JXWindowIcon.load(sources));
    }

    /** Display thread. */
    private void applyIcons(long handle) {
        List<JXWindowIcon> sizes = icons;
        if (sizes.isEmpty()) {
            GLFW.glfwSetWindowIcon(handle, (org.lwjgl.glfw.GLFWImage.Buffer) null);
            return;
        }
        org.lwjgl.glfw.GLFWImage.Buffer images = org.lwjgl.glfw.GLFWImage.malloc(sizes.size());
        java.nio.ByteBuffer[] pixels = new java.nio.ByteBuffer[sizes.size()];
        try {
            for (int i = 0; i < pixels.length; i++) {
                JXWindowIcon icon = sizes.get(i);
                byte[] rgba = icon.rgba();
                pixels[i] = MemoryUtil.memAlloc(rgba.length);
                pixels[i].put(rgba).flip();
                images.get(i).set(icon.getWidth(), icon.getHeight(), pixels[i]);
            }
            GLFW.glfwSetWindowIcon(handle, images);
        } finally {
            images.free();
            for (java.nio.ByteBuffer pixel : pixels) {
                if (pixel != null) {
                    MemoryUtil.memFree(pixel);
                }
            }
        }
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

    /**
     * The primary monitor's work area in logical pixels: x, y, width, height. Safe from any thread.
     * Where GLFW's screen coordinates are physical pixels (Windows, X11) the monitor's content scale
     * divides them; on macOS and Wayland they already are logical.
     */
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
            float s = screenCoordinatesAreLogical() ? 1f : monitorScale(monitor);
            return new int[] {Math.round(x[0] / s), Math.round(y[0] / s), JXScale.logicalExtent(w[0], s),
                    JXScale.logicalExtent(h[0], s)};
        });
    }

    /** True where one GLFW screen coordinate is one logical pixel and the framebuffer is denser (macOS, Wayland). */
    private static boolean screenCoordinatesAreLogical() {
        try {
            int platform = GLFW.glfwGetPlatform();
            return platform == GLFW.GLFW_PLATFORM_COCOA || platform == GLFW.GLFW_PLATFORM_WAYLAND;
        } catch (Throwable e) {
            return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
        }
    }

    private static float monitorScale(long monitor) {
        float[] sx = new float[1];
        float[] sy = new float[1];
        GLFW.glfwGetMonitorContentScale(monitor, sx, sy);
        return JXScale.resolve(JXScale.forced(), sx[0], sy[0]);
    }

    /**
     * Paints {@code element} with the software renderer and returns the pixels, ARGB, row by row from
     * the top. CPU only: no window, no OpenGL, any thread.
     */
    public static int[] captureSoftware(JXElement element, int width, int height) {
        return captureSoftware(element, width, height, 1f);
    }

    /**
     * Like {@link #captureSoftware(JXElement, int, int)} for a HiDPI display: {@code width x height} is the
     * logical size the tree is laid out in, and the image is {@code round(width * scale)} by
     * {@code round(height * scale)} physical pixels, drawn at that resolution (not enlarged).
     */
    public static int[] captureSoftware(JXElement element, int width, int height, float scale) {
        int pw = Math.max(1, JXScale.physicalExtent(width, scale));
        int ph = Math.max(1, JXScale.physicalExtent(height, scale));
        int[] argb = new int[pw * ph];
        JXSoftwarePainter painter = new JXSoftwarePainter(argb, pw, ph, scale);
        painter.clear(JXSkiaRenderer.BACKGROUND);
        JXNativeNode node = JXNativeNode.createBackendNode(element);
        node.layoutForBackend(Math.max(1, width), Math.max(1, height));
        new JXPaint(painter).paint(node);
        return argb;
    }

    /**
     * Paints an already laid-out tree with the software renderer into {@code argb} (at least {@code width * height}
     * entries, ARGB, row by row from the top), after clearing it. Unlike {@link #captureSoftware} it neither builds
     * nodes nor lays out, so a benchmark can time the painting alone. CPU only: no window, any thread.
     */
    public static void paintSoftware(JXNativeNode laidOut, int[] argb, int width, int height) {
        JXSoftwarePainter painter = new JXSoftwarePainter(argb, width, height, 1f);
        painter.clear(JXSkiaRenderer.BACKGROUND);
        new JXPaint(painter).paint(laidOut);
    }

    /** True when windows are painted by the software renderer ({@code -Djx.renderer=software}). */
    public static boolean softwareRenderer() {
        return SOFTWARE.equals(selectRenderer());
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
                    backend.render(JXNativeNode.createBackendNode(element), fbw[0], fbh[0], width, height, (float) fbw[0] / width);
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
        boolean software = SOFTWARE.equals(rendererName);
        long handle = software ? MemoryUtil.NULL : JXDisplay.takeSpareWindow(); // the spare window has an OpenGL context
        boolean adopted = handle != MemoryUtil.NULL;
        if (adopted) {
            // the prewarmed hidden window, its OpenGL context already made: set it up as this window
            GLFW.glfwSetWindowTitle(handle, title);
            GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_RESIZABLE, resizable ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
            GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, decorated ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
            GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_FLOATING, floating ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
            GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_FOCUS_ON_SHOW, focusOnShow ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
        } else {
            GLFW.glfwDefaultWindowHints();
            if (software) {
                GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API); // no OpenGL context: no driver memory
            } else {
                GLFW.glfwWindowHint(GLFW.GLFW_STENCIL_BITS, 8);
            }
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
        // Not GLFW_SCALE_TO_MONITOR: the size is converted here, from the scale of the monitor the window
        // ends up on, so it works the same on Windows, X11, macOS and Wayland, and for -Djx.scale.
        place(handle);
        syncMetrics(handle, false);
        installCallbacks(handle);
        if (software) {
            backend = new SoftwareBackend(org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window(handle));
            org.lwjgl.glfw.GLFWVidMode mode = GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor());
            frameIntervalNanos = 1_000_000_000L / Math.max(30, mode != null && mode.refreshRate() > 0 ? mode.refreshRate() : 60);
        } else {
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
        }
        JXDisplay.add(this);
        if (!icons.isEmpty()) {
            applyIcons(handle);
        }
        if (maximized) {
            GLFW.glfwMaximizeWindow(handle);
        }
        GLFW.glfwShowWindow(handle);
        if (iconified) {
            GLFW.glfwIconifyWindow(handle);
        }
        if (fullScreen) {
            applyFullScreen();
        }
        requestRender();
    }

    /** Configured position, else {@code -Djx.monitor=N} (0 = primary), else centred on the primary monitor. */
    private void place(long handle) {
        if (x != Integer.MIN_VALUE && y != Integer.MIN_VALUE) {
            GLFW.glfwSetWindowPos(handle, x, y);
            scale = windowScale(handle); // the monitor the window is now on
            applyLogicalSize(handle);
            return;
        }
        org.lwjgl.PointerBuffer monitors = GLFW.glfwGetMonitors();
        Integer index = Integer.getInteger("jx.monitor");
        long monitor = monitors != null && index != null && index >= 0 && index < monitors.limit()
                ? monitors.get(index) : GLFW.glfwGetPrimaryMonitor();
        if (monitor == MemoryUtil.NULL) {
            applyLogicalSize(handle);
            return;
        }
        scale = monitorScale(monitor);
        applyLogicalSize(handle);
        int[] mx = new int[1];
        int[] my = new int[1];
        int[] mw = new int[1];
        int[] mh = new int[1];
        int[] ww = new int[1];
        int[] wh = new int[1];
        GLFW.glfwGetMonitorWorkarea(monitor, mx, my, mw, mh);
        GLFW.glfwGetWindowSize(handle, ww, wh);
        GLFW.glfwSetWindowPos(handle, mx[0] + Math.max(0, (mw[0] - ww[0]) / 2), my[0] + Math.max(0, (mh[0] - wh[0]) / 2));
    }

    /** The content scale of the monitor the window is on, or the forced one. */
    private static float windowScale(long handle) {
        float[] sx = new float[1];
        float[] sy = new float[1];
        GLFW.glfwGetWindowContentScale(handle, sx, sy);
        return JXScale.resolve(JXScale.forced(), sx[0], sy[0]);
    }

    /**
     * Gives the window the logical {@link #width} x {@link #height} at the current scale, and the logical
     * minimum size (GLFW sizes are in window units: physical pixels on Windows and X11, points elsewhere).
     * Display thread.
     */
    private void applyLogicalSize(long handle) {
        applyLogicalSize(handle, true);
    }

    /** {@code resize} false only refreshes the minimum size. */
    private void applyLogicalSize(long handle, boolean resize) {
        int[] ww = new int[1];
        int[] wh = new int[1];
        int[] fw = new int[1];
        int[] fh = new int[1];
        GLFW.glfwGetWindowSize(handle, ww, wh);
        GLFW.glfwGetFramebufferSize(handle, fw, fh);
        float s = scale;
        if (minWidth > 0 || minHeight > 0) {
            GLFW.glfwSetWindowSizeLimits(handle,
                    minWidth > 0 ? JXScale.toWindowUnits(minWidth, ww[0], fw[0], s) : GLFW.GLFW_DONT_CARE,
                    minHeight > 0 ? JXScale.toWindowUnits(minHeight, wh[0], fh[0], s) : GLFW.GLFW_DONT_CARE,
                    GLFW.GLFW_DONT_CARE, GLFW.GLFW_DONT_CARE);
        }
        if (resize) {
            GLFW.glfwSetWindowSize(handle, JXScale.toWindowUnits(width, ww[0], fw[0], s),
                    JXScale.toWindowUnits(height, wh[0], fh[0], s));
        }
    }

    /**
     * Re-reads the window and framebuffer sizes: sets the cursor conversion, the framebuffer size and the
     * logical size. Reports a changed logical size to the input listener when {@code notify} is set.
     * False when the window has no area (minimized). Display thread.
     */
    private boolean syncMetrics(long handle, boolean notify) {
        int[] fw = new int[1];
        int[] fh = new int[1];
        int[] ww = new int[1];
        int[] wh = new int[1];
        GLFW.glfwGetFramebufferSize(handle, fw, fh);
        GLFW.glfwGetWindowSize(handle, ww, wh);
        if (fw[0] <= 0 || fh[0] <= 0 || ww[0] <= 0 || wh[0] <= 0) {
            return false;
        }
        float s = scale;
        fbWidth = fw[0];
        fbHeight = fh[0];
        toLogicalX = JXScale.toLogical(1, ww[0], fw[0], s);
        toLogicalY = JXScale.toLogical(1, wh[0], fh[0], s);
        int lw = JXScale.logicalExtent(fw[0], s);
        int lh = JXScale.logicalExtent(fh[0], s);
        if (lw != width || lh != height) {
            width = lw;
            height = lh;
            if (notify) {
                deliver(JXInputEvent.window(JXInputEvent.Kind.RESIZE, lw, lh));
            }
        }
        return true;
    }

    private void installCallbacks(long handle) {
        GLFW.glfwSetFramebufferSizeCallback(handle, (h, w, hh) -> requestRender());
        GLFW.glfwSetWindowSizeCallback(handle, (h, w, hh) -> {
            if (w > 0 && hh > 0 && syncMetrics(h, false)) {
                deliver(JXInputEvent.window(JXInputEvent.Kind.RESIZE, width, height)); // logical pixels
            }
            requestRender();
        });
        // The window moved to a monitor with another scale (or the user changed it): the logical size, the
        // minimum size and the cursor conversion follow. Windows resizes the window itself, keeping its logical size.
        GLFW.glfwSetWindowContentScaleCallback(handle, (h, sx, sy) -> {
            float resolved = JXScale.resolve(JXScale.forced(), sx, sy);
            if (resolved != scale) {
                scale = resolved;
                if (minWidth > 0 || minHeight > 0) {
                    applyLogicalSize(h, false);
                }
                syncMetrics(h, true);
            }
            requestRender();
        });
        GLFW.glfwSetWindowMaximizeCallback(handle, (h, value) -> maximized = value);
        GLFW.glfwSetWindowIconifyCallback(handle, (h, value) -> iconified = value);
        GLFW.glfwSetWindowPosCallback(handle, (h, wx, wy) -> {
            if (!iconified) { // a minimized window reports a position far off screen
                x = wx;
                y = wy;
            }
        });
        GLFW.glfwSetWindowRefreshCallback(handle, h -> {
            if (backend instanceof SoftwareBackend) {
                ((SoftwareBackend) backend).exposed = true; // GDI keeps no copy of an uncovered window
            }
            requestRender();
        });
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
        GLFW.glfwSetCursorPosCallback(handle, (h, rawX, rawY) -> {
            double px = rawX * toLogicalX; // window units to logical pixels
            double py = rawY * toLogicalY;
            cursorX = px;
            cursorY = py;
            if (inputListener != null) {
                deliver(JXInputEvent.pointer(JXInputEvent.Kind.MOVE, px, py, lastButton < 0 ? 0 : lastButton, currentModifiers(h),
                        0, buttonsDown != 0, path(px, py)));
            } else if (dragNode != null && buttonsDown != 0) {
                dragNode.dispatchDrag(new JXPointerEvent((int) px, (int) py, GLFW.GLFW_MOUSE_BUTTON_LEFT));
                root.movePointer((int) px, (int) py);
                requestRender();
            } else if (root != null && buttonsDown == 0) {
                hover((int) px, (int) py);
            }
        });
        GLFW.glfwSetCursorEnterCallback(handle, (h, entered) -> {
            if (!entered && inputListener != null) {
                deliver(JXInputEvent.pointer(JXInputEvent.Kind.EXIT, cursorX, cursorY, 0, 0, 0, buttonsDown != 0,
                        new ArrayList<JXNativeNode>()));
            } else if (!entered && root != null) {
                hover(-1, -1); // the pointer left the window: nothing is hovered
            }
        });
        GLFW.glfwSetMouseButtonCallback(handle, (h, glfwButton, action, mods) -> {
            int button = glfwButton == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? JXInputEvent.BUTTON_SECONDARY
                    : glfwButton == GLFW.GLFW_MOUSE_BUTTON_MIDDLE ? JXInputEvent.BUTTON_MIDDLE : JXInputEvent.BUTTON_PRIMARY;
            double[] px = new double[1];
            double[] py = new double[1];
            GLFW.glfwGetCursorPos(h, px, py);
            px[0] *= toLogicalX; // window units to logical pixels
            py[0] *= toLogicalY;
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
                dragNode = null;
            }
            if (inputListener != null) {
                deliver(JXInputEvent.pointer(action == GLFW.GLFW_PRESS ? JXInputEvent.Kind.PRESS : JXInputEvent.Kind.RELEASE,
                        px[0], py[0], button, JXKeys.modifiers(mods), clickCount, buttonsDown != 0, path(px[0], py[0])));
                return;
            }
            boolean secondary = button == JXInputEvent.BUTTON_SECONDARY; // opens a context menu
            hideTooltip(); // a press or a release puts the tooltip away
            if (button == JXInputEvent.BUTTON_PRIMARY && action == GLFW.GLFW_RELEASE && root != null) {
                root.releasePointer(new JXPointerEvent((int) px[0], (int) py[0], glfwButton)); // a button clicks on the release
                updateCursor((int) px[0], (int) py[0]); // a drag that kept the resize cursor is over
                requestRender();
                return;
            }
            if (button != JXInputEvent.BUTTON_PRIMARY && !secondary || action != GLFW.GLFW_PRESS || root == null) {
                return;
            }
            // the overlays first: a press outside an open popup or menu closes it, one inside a dialog stays in it
            JXNativeNode hit = root.pressPointer(new JXPointerEvent((int) px[0], (int) py[0], glfwButton, clickCount,
                    JXKeys.modifiers(mods)));
            if (hit != null && !secondary) {
                focus(hit);
                dragNode = hit; // a scroll bar's thumb or a split pane's divider follows the pointer until the release
            }
            requestRender();
        });
        GLFW.glfwSetScrollCallback(handle, (h, dx, dy) -> {
            if (inputListener != null) {
                deliver(JXInputEvent.scroll(cursorX, cursorY, dx, dy, currentModifiers(h), path(cursorX, cursorY)));
            } else if (root != null && JXScrolling.wheel(root, (int) cursorX, (int) cursorY, dx, dy,
                    (currentModifiers(h) & JXInputEvent.SHIFT) != 0)) {
                requestRender(); // no input listener: the wheel scrolls the pane under the cursor
            }
        });
        GLFW.glfwSetKeyCallback(handle, (h, key, scancode, action, mods) -> {
            if (inputListener != null) {
                deliver(JXInputEvent.key(action == GLFW.GLFW_RELEASE ? JXInputEvent.Kind.KEY_RELEASE : JXInputEvent.Kind.KEY_PRESS,
                        JXKeys.name(key), JXKeys.modifiers(mods)));
                return;
            }
            if (action != GLFW.GLFW_RELEASE && root != null) {
                if (key == GLFW.GLFW_KEY_TAB) { // Tab and Shift+Tab move the focus through the controls
                    JXNativeNode next = root.nextFocus(focusedNode, (mods & GLFW.GLFW_MOD_SHIFT) != 0);
                    if (next != null) {
                        focus(next);
                    }
                } else {
                    // Escape and Enter belong to an open overlay first; Shift and Ctrl select, copy, paste and undo text
                    keyTaken = root.capturesKeys(); // the character this key types, which GLFW sends next, goes with it
                    JXNativeNode owner = root.routeKey(new JXKeyEvent(key, '\0', JXKeys.modifiers(mods)), focusedNode);
                    if (owner != focusedNode) {
                        focus(owner); // Escape closed a popup or a menu: the control it belongs to has the focus again
                    }
                }
                hideTooltip();
                requestRender();
            }
        });
        GLFW.glfwSetCharModsCallback(handle, (h, codepoint, mods) -> {
            if (inputListener != null) {
                deliver(JXInputEvent.character(codepoint, JXKeys.modifiers(mods)));
                return;
            }
            if (focusedNode != null && !keyTaken) { // an open menu or popup took the key that typed it already
                focusedNode.dispatchKey(new JXKeyEvent(0, (char) codepoint));
                requestRender();
            }
        });
    }

    /** The last key press went to an open popup list or menu (see {@link JXNativeNode#capturesKeys}), not to the focused node. */
    private boolean keyTaken;

    /** How long the pointer rests on a node with a {@code tooltip} before it shows, as JavaFX's default (milliseconds). */
    private static final long TOOLTIP_DELAY = 600;
    /** The node whose tooltip waits to show (or shows), and when it is due. */
    private JXNativeNode tooltipNode;
    private long tooltipDue;

    /** The pointer is at (x, y) with no button held (-1: it left): hover states, and the tooltip of what is under it. */
    private void hover(int x, int y) {
        if (root.hoverPointer(x, y)) {
            requestRender();
        }
        updateCursor(x, y);
        JXNativeNode tip = x < 0 ? null : JXNativeNode.hoveredTooltip();
        if (tip != tooltipNode) {
            hideTooltip();
            tooltipNode = tip;
            if (tip != null) {
                tooltipDue = System.nanoTime() + TOOLTIP_DELAY * 1_000_000L;
                JXDisplay.wakeAt(tooltipDue);
            }
        }
    }

    /** GLFW standard cursors, created the first time each shape is needed and destroyed with the window; display thread. */
    private final long[] cursors = new long[JXCursor.values().length];
    private JXCursor cursorShown = JXCursor.ARROW;

    /** Shows the pointer shape of what is at (x, y) (x < 0: the pointer left, the arrow). */
    private void updateCursor(int x, int y) {
        showCursor(x < 0 || root == null ? JXCursor.ARROW : root.cursorAt(x, y));
    }

    private void showCursor(JXCursor shape) {
        if (shape == cursorShown || window == MemoryUtil.NULL) {
            return;
        }
        long cursor = cursors[shape.ordinal()];
        if (cursor == MemoryUtil.NULL) {
            cursor = GLFW.glfwCreateStandardCursor(glfwShape(shape));
            cursors[shape.ordinal()] = cursor;
        }
        if (cursor != MemoryUtil.NULL) { // a platform without that shape keeps the current one
            GLFW.glfwSetCursor(window, cursor);
            cursorShown = shape;
        }
    }

    private static int glfwShape(JXCursor shape) {
        switch (shape) {
            case IBEAM:
                return GLFW.GLFW_IBEAM_CURSOR;
            case CROSSHAIR:
                return GLFW.GLFW_CROSSHAIR_CURSOR;
            case HAND:
                return GLFW.GLFW_HAND_CURSOR;
            case RESIZE_H:
                return GLFW.GLFW_HRESIZE_CURSOR;
            case RESIZE_V:
                return GLFW.GLFW_VRESIZE_CURSOR;
            default:
                return GLFW.GLFW_ARROW_CURSOR;
        }
    }

    private void destroyCursors() {
        for (int i = 0; i < cursors.length; i++) {
            if (cursors[i] != MemoryUtil.NULL) {
                GLFW.glfwDestroyCursor(cursors[i]);
                cursors[i] = MemoryUtil.NULL;
            }
        }
        cursorShown = JXCursor.ARROW;
    }

    private void hideTooltip() {
        if (JXNativeNode.shownTooltip != null) {
            JXNativeNode.shownTooltip = null;
            requestRender();
        }
        tooltipNode = null;
    }

    /** Gives the keyboard focus to {@code node} (null: none): keys go there, and it draws its focus ring and caret. */
    private void focus(JXNativeNode node) {
        focusedNode = node;
        JXNativeNode.setFocusOwner(node);
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
        if (window == MemoryUtil.NULL) {
            return;
        }
        if (tooltipNode != null && JXNativeNode.shownTooltip == null && System.nanoTime() >= tooltipDue) {
            // the pointer rested on it: the tooltip shows below and right of the pointer, until it moves off or presses
            JXNativeNode.shownTooltip = new Object[] {tooltipNode.getProperty("tooltip"), (int) cursorX + 12, (int) cursorY + 20};
            renderRequested.set(true);
        }
        if (timedOverlay && root != null) {
            // a snack bar waits for its duration: ask it at every turn of the loop; when none waits any more, draw the result
            long wait = root.tickOverlays();
            if (wait < 0) {
                timedOverlay = false;
                renderRequested.set(true);
            } else {
                JXDisplay.wakeAt(System.nanoTime() + wait * 1_000_000L);
            }
        }
        if (!renderRequested.getAndSet(false)) {
            return;
        }
        if (!syncMetrics(window, true)) {
            return; // minimized
        }
        float frameScale = scale;
        int logicalWidth = width;
        int logicalHeight = height;
        if (frameIntervalNanos > 0) {
            // no vertical sync without OpenGL: at most one frame per refresh of the monitor
            long now = System.nanoTime();
            long due = lastFrameNanos + frameIntervalNanos;
            if (now < due) {
                renderRequested.set(true);
                JXDisplay.wakeAt(due);
                return;
            }
            lastFrameNanos = now;
        } else if (GLFW.glfwGetCurrentContext() != window) {
            // switching contexts is costly on Windows (wglMakeCurrent); with one window it is already current
            GLFW.glfwMakeContextCurrent(window);
            GL.setCapabilities(capabilities);
        }
        Object lock = externalLock;
        if (lock != null) {
            synchronized (lock) {
                backend.render(root, fbWidth, fbHeight, logicalWidth, logicalHeight, frameScale);
            }
        } else {
            backend.render(root, fbWidth, fbHeight, logicalWidth, logicalHeight, frameScale);
        }
        if (frameIntervalNanos == 0) {
            GLFW.glfwSwapBuffers(window);
        }
        timedOverlay = root != null && lock == null && root.tickOverlays() >= 0;
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
        boolean gl = capabilities != null;
        if (gl) {
            GLFW.glfwMakeContextCurrent(handle);
            GL.setCapabilities(capabilities);
        }
        if (backend != null) {
            backend.close();
            backend = null;
        }
        if (gl) {
            GLFW.glfwMakeContextCurrent(MemoryUtil.NULL);
        }
        GLFW.glfwDestroyWindow(handle);
        destroyCursors();
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
        /**
         * Paints a {@code fbWidth x fbHeight} physical framebuffer; the tree is laid out in
         * {@code winWidth x winHeight} logical pixels, each {@code scale} physical pixels wide.
         */
        void render(JXNativeNode root, int fbWidth, int fbHeight, int winWidth, int winHeight, float scale);

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
        public void render(JXNativeNode root, int fbWidth, int fbHeight, int winWidth, int winHeight, float scale) {
            if (surface == null || fbWidth != width || fbHeight != height) {
                recreateSurface(fbWidth, fbHeight);
            }
            context.resetAll(); // several windows share the thread: GL state may belong to another context's last frame
            if (root != null) {
                JXSkiaRenderer.paint(root, surface.getCanvas(), winWidth, winHeight, scale);
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

    /** The CPU painter shown with GDI ({@link JXSoftwarePainter}, {@link JXGdiSurface}). */
    private static final class SoftwareBackend implements Backend {
        private final JXGdiSurface surface;
        private final JXSoftwareFrame frame = new JXSoftwareFrame();
        private final int[] whole = new int[4];
        /** The window was uncovered: show the whole frame again, even if nothing changed. */
        boolean exposed = true;

        SoftwareBackend(long hwnd) {
            surface = new JXGdiSurface(hwnd);
        }

        @Override
        public void render(JXNativeNode root, int fbWidth, int fbHeight, int winWidth, int winHeight, float scale) {
            if (root != null) {
                root.layoutForBackend(Math.max(1, winWidth), Math.max(1, winHeight));
            }
            int rects = frame.paint(root, fbWidth, fbHeight, scale);
            if (exposed) {
                exposed = false;
                whole[2] = fbWidth;
                whole[3] = fbHeight;
                surface.present(frame.pixels(), fbWidth, fbHeight, whole, 1);
            } else if (rects > 0) {
                surface.present(frame.pixels(), fbWidth, fbHeight, frame.damaged(), rects);
            }
        }

        @Override
        public void close() {
            surface.close();
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
        public void render(JXNativeNode root, int fbWidth, int fbHeight, int winWidth, int winHeight, float scale) {
            int background = JXNanoVGRenderer.BACKGROUND;
            GL11.glViewport(0, 0, fbWidth, fbHeight);
            GL11.glClearColor(((background >> 16) & 0xFF) / 255.0f, ((background >> 8) & 0xFF) / 255.0f,
                    (background & 0xFF) / 255.0f, 1.0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            if (root == null) {
                return;
            }
            // the view is the framebuffer divided by the ratio, not the rounded-up logical size: an exact mapping
            NanoVG.nvgBeginFrame(vg, fbWidth / scale, fbHeight / scale, scale);
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
