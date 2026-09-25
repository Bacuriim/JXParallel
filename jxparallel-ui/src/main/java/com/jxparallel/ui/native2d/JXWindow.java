package com.jxparallel.ui.native2d;

import java.util.concurrent.ConcurrentLinkedQueue;

import com.jxparallel.ui.JXElement;
import io.github.humbleui.skija.BackendRenderTarget;
import io.github.humbleui.skija.ColorSpace;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.FramebufferFormat;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.SurfaceColorFormat;
import io.github.humbleui.skija.SurfaceOrigin;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

/**
 * Native window: GLFW owns the window and the OpenGL context, Skia paints into the
 * default framebuffer. {@link #show()} blocks and runs the render loop on the calling thread.
 */
public final class JXWindow implements AutoCloseable {
    private static final int GL_FRAMEBUFFER_BINDING = 0x8CA6;

    private final String title;
    private final ConcurrentLinkedQueue<Runnable> pendingActions = new ConcurrentLinkedQueue<Runnable>();
    private volatile boolean renderRequested = true;
    private volatile long window = MemoryUtil.NULL;
    private JXNativeNode root;
    private JXNativeNode focusedNode;
    private Runnable onFirstPaint;
    private Runnable onFrame;
    private boolean firstPaintReported;

    private DirectContext context;
    private BackendRenderTarget renderTarget;
    private Surface surface;
    private int surfaceWidth;
    private int surfaceHeight;

    public JXWindow(String title) {
        this.title = title == null ? "JXParallel" : title;
    }

    public void setContent(JXElement element) {
        root = JXSkiaRenderer.mount(element);
        focusedNode = null;
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

    public void requestRender() {
        renderRequested = true;
        if (window != MemoryUtil.NULL) {
            GLFW.glfwPostEmptyEvent();
        }
    }

    public void renderNow() {
        requestRender();
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
            installCallbacks();
            GLFW.glfwMakeContextCurrent(window);
            GLFW.glfwSwapInterval(1);
            GL.createCapabilities();
            context = DirectContext.makeGL();
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
            if (renderRequested) {
                renderRequested = false;
                drawFrame();
            }
            GLFW.glfwWaitEvents();
        }
    }

    private void drawFrame() {
        int[] width = new int[1];
        int[] height = new int[1];
        GLFW.glfwGetFramebufferSize(window, width, height);
        if (width[0] <= 0 || height[0] <= 0) {
            return; // minimized
        }
        if (surface == null || width[0] != surfaceWidth || height[0] != surfaceHeight) {
            recreateSurface(width[0], height[0]);
        }
        if (root != null) {
            JXSkiaRenderer.paint(root, surface.getCanvas(), surfaceWidth, surfaceHeight);
        }
        context.flush();
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

    private void recreateSurface(int width, int height) {
        closeSurface();
        surfaceWidth = width;
        surfaceHeight = height;
        int framebufferId = GL11.glGetInteger(GL_FRAMEBUFFER_BINDING);
        renderTarget = BackendRenderTarget.makeGL(width, height, 0, 8, framebufferId, FramebufferFormat.GR_GL_RGBA8);
        surface = Surface.makeFromBackendRenderTarget(context, renderTarget, SurfaceOrigin.BOTTOM_LEFT,
                SurfaceColorFormat.RGBA_8888, ColorSpace.getSRGB());
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

    private void release() {
        closeSurface();
        if (context != null) {
            context.close();
            context = null;
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
}
