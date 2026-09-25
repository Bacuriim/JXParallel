package com.jxparallel.ui.native2d;

import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.input.JXClipboard;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NanoVG;
import org.lwjgl.nanovg.NanoVGGL3;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

/**
 * Native window: GLFW owns the window and the OpenGL context. 64-bit JVMs paint with Skia
 * (Skija); 32-bit JVMs, where Skija has no native libraries, paint with NanoVG.
 * {@code -Djx.renderer=skia|nanovg} forces one. {@link #show()} blocks and runs the render loop
 * on the calling thread.
 */
public final class JXWindow implements AutoCloseable {
    public static final String SKIA = "skia";
    public static final String NANOVG = "nanovg";

    private final String title;
    private final String rendererName = selectRenderer();
    private final ConcurrentLinkedQueue<Runnable> pendingActions = new ConcurrentLinkedQueue<Runnable>();
    private final AtomicBoolean renderRequested = new AtomicBoolean(true);
    private volatile long window = MemoryUtil.NULL;
    private JXNativeNode root;
    private JXNativeNode focusedNode;
    private Runnable onFirstPaint;
    private Runnable onFrame;
    private boolean firstPaintReported;
    private Backend backend;

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
        return is32Bit ? NANOVG : SKIA;
    }

    /**
     * Shows {@code element}. Calling it again with a new tree reconciles in place: unchanged nodes,
     * their layout caches and the focused node are kept. Call from the window thread
     * ({@link #invokeLater}) once the window is shown.
     */
    public void setContent(JXElement element) {
        if (root == null || !root.reconcile(element)) {
            root = JXNativeNode.createBackendNode(element);
            focusedNode = null;
        }
        requestRender();
    }

    public void setOnFirstPaint(Runnable callback) {
        onFirstPaint = callback;
    }

    /** Runs on the window thread after every presented frame (after buffer swap). */
    public void setOnFrame(Runnable callback) {
        onFrame = callback;
    }

    public void invokeLater(Runnable action) {
        if (action == null) {
            throw new IllegalArgumentException("Action cannot be null");
        }
        pendingActions.add(action);
        requestRender();
    }

    /** Coalesced: only the first request after a frame wakes the render loop. Safe from any thread. */
    public void requestRender() {
        if (renderRequested.compareAndSet(false, true) && window != MemoryUtil.NULL) {
            GLFW.glfwPostEmptyEvent();
        }
    }

    public void renderNow() {
        requestRender();
    }

    /** System clipboard through GLFW. Use from the window thread while the window is shown. */
    public JXClipboard clipboard() {
        return new JXClipboard() {
            @Override
            public String getText() {
                return GLFW.glfwGetClipboardString(window);
            }

            @Override
            public void setText(String value) {
                GLFW.glfwSetClipboardString(window, value == null ? "" : value);
            }
        };
    }

    public void show() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }
        try {
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_STENCIL_BITS, 8);
            window = GLFW.glfwCreateWindow(640, 420, title, MemoryUtil.NULL, MemoryUtil.NULL);
            if (window == MemoryUtil.NULL) {
                throw new IllegalStateException("Unable to create GLFW window");
            }
            moveToConfiguredMonitor();
            installCallbacks();
            GLFW.glfwMakeContextCurrent(window);
            GLFW.glfwSwapInterval(1);
            GL.createCapabilities();
            // The Skia class is only loaded here, so 32-bit JVMs never touch Skija.
            backend = NANOVG.equals(rendererName) ? new NanoVGBackend() : new SkiaBackend();
            loop();
        } finally {
            release();
            GLFW.glfwTerminate();
            GLFWErrorCallback previous = GLFW.glfwSetErrorCallback(null);
            if (previous != null) {
                previous.free();
            }
        }
    }

    /** {@code -Djx.monitor=N} opens the window on monitor N (0 = primary, GLFW order). */
    private void moveToConfiguredMonitor() {
        Integer index = Integer.getInteger("jx.monitor");
        org.lwjgl.PointerBuffer monitors = GLFW.glfwGetMonitors();
        if (index == null || monitors == null || index < 0 || index >= monitors.limit()) {
            return;
        }
        int[] x = new int[1];
        int[] y = new int[1];
        GLFW.glfwGetMonitorPos(monitors.get(index), x, y);
        GLFW.glfwSetWindowPos(window, x[0] + 100, y[0] + 100);
    }

    private void installCallbacks() {
        GLFW.glfwSetFramebufferSizeCallback(window, (handle, width, height) -> requestRender());
        GLFW.glfwSetMouseButtonCallback(window, (handle, button, action, mods) -> {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || action != GLFW.GLFW_PRESS || root == null) {
                return;
            }
            double[] x = new double[1];
            double[] y = new double[1];
            GLFW.glfwGetCursorPos(handle, x, y);
            JXNativeNode hit = root.hitTest((int) x[0], (int) y[0]);
            if (hit != null) {
                focusedNode = hit;
                hit.dispatchPointer(new JXPointerEvent((int) x[0], (int) y[0], button));
                requestRender();
            }
        });
        GLFW.glfwSetKeyCallback(window, (handle, key, scancode, action, mods) -> {
            if (action != GLFW.GLFW_RELEASE && focusedNode != null) {
                focusedNode.dispatchKey(new JXKeyEvent(key, '\0'));
                requestRender();
            }
        });
        GLFW.glfwSetCharCallback(window, (handle, codepoint) -> {
            if (focusedNode != null) {
                focusedNode.dispatchKey(new JXKeyEvent(0, (char) codepoint));
                requestRender();
            }
        });
    }

    private void loop() {
        while (!GLFW.glfwWindowShouldClose(window)) {
            Runnable action;
            while ((action = pendingActions.poll()) != null) {
                action.run();
            }
            if (renderRequested.getAndSet(false)) {
                drawFrame();
            }
            GLFW.glfwWaitEvents();
        }
    }

    private void drawFrame() {
        int[] fbWidth = new int[1];
        int[] fbHeight = new int[1];
        int[] winWidth = new int[1];
        int[] winHeight = new int[1];
        GLFW.glfwGetFramebufferSize(window, fbWidth, fbHeight);
        GLFW.glfwGetWindowSize(window, winWidth, winHeight);
        if (fbWidth[0] <= 0 || fbHeight[0] <= 0 || winWidth[0] <= 0 || winHeight[0] <= 0) {
            return; // minimized
        }
        backend.render(root, fbWidth[0], fbHeight[0], winWidth[0], winHeight[0]);
        GLFW.glfwSwapBuffers(window);
        if (!firstPaintReported) {
            firstPaintReported = true;
            if (onFirstPaint != null) {
                onFirstPaint.run();
            }
        }
        if (onFrame != null) {
            onFrame.run();
        }
    }

    private void release() {
        if (backend != null) {
            backend.close();
            backend = null;
        }
        if (window != MemoryUtil.NULL) {
            GLFW.glfwDestroyWindow(window);
            window = MemoryUtil.NULL;
        }
    }

    /** Asks the render loop to exit; resources are released on the window thread. Safe from any thread. */
    public void dispose() {
        long handle = window;
        if (handle != MemoryUtil.NULL) {
            GLFW.glfwSetWindowShouldClose(handle, true);
            GLFW.glfwPostEmptyEvent();
        }
    }

    @Override
    public void close() {
        dispose();
    }

    /** Paints one frame into the current OpenGL context. Window thread only. */
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
            if (root != null) {
                JXSkiaRenderer.paint(root, surface.getCanvas(), width, height);
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

        NanoVGBackend() {
            if (vg == MemoryUtil.NULL) {
                throw new IllegalStateException("Unable to create NanoVG context (OpenGL 3 required)");
            }
            String font = JXNanoVGRenderer.findFont();
            hasFont = font != null && NanoVG.nvgCreateFont(vg, JXNanoVGRenderer.FONT, font) >= 0;
            if (!hasFont) {
                System.err.println("JXParallel: no TrueType font found, text will not be drawn; set -Djx.font=<path>");
            }
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
            JXNanoVGRenderer.paint(root, vg, color, hasFont, winWidth, winHeight);
            NanoVG.nvgEndFrame(vg);
        }

        @Override
        public void close() {
            NanoVGGL3.nvgDelete(vg);
        }
    }
}
