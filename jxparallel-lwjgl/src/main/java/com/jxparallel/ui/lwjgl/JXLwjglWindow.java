package com.jxparallel.ui.lwjgl;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.native2d.JXNativeNode;
import com.jxparallel.ui.native2d.JXPointerEvent;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

public final class JXLwjglWindow implements AutoCloseable {
    private final String title;
    private long window;
    private JXNativeNode root;
    private int width = 640;
    private int height = 420;

    public JXLwjglWindow(String title) {
        this.title = title == null ? "JXParallel" : title;
    }

    public void setContent(JXElement element) {
        root = JXLwjglRenderer.mount(element);
    }

    public void show() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }
        try {
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 2);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 1);
            window = GLFW.glfwCreateWindow(width, height, title, MemoryUtil.NULL, MemoryUtil.NULL);
            if (window == MemoryUtil.NULL) {
                throw new IllegalStateException("Unable to create GLFW window");
            }
            GLFW.glfwSetMouseButtonCallback(window, (handle, button, action, mods) -> {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && action == GLFW.GLFW_PRESS && root != null) {
                    double[] x = new double[1];
                    double[] y = new double[1];
                    GLFW.glfwGetCursorPos(handle, x, y);
                    JXNativeNode hit = root.hitTest((int) x[0], (int) y[0]);
                    if (hit != null) {
                        hit.dispatchPointer(new JXPointerEvent((int) x[0], (int) y[0], button));
                    }
                }
            });
            GLFW.glfwMakeContextCurrent(window);
            GLFW.glfwSwapInterval(1);
            GL.createCapabilities();
            while (!GLFW.glfwWindowShouldClose(window)) {
                int[] framebufferWidth = new int[1];
                int[] framebufferHeight = new int[1];
                GLFW.glfwGetFramebufferSize(window, framebufferWidth, framebufferHeight);
                width = Math.max(1, framebufferWidth[0]);
                height = Math.max(1, framebufferHeight[0]);
                JXLwjglRenderer.paint(root, width, height);
                GLFW.glfwSwapBuffers(window);
                GLFW.glfwPollEvents();
            }
        } finally {
            close();
            GLFW.glfwTerminate();
            GLFW.glfwSetErrorCallback(null);
        }
    }

    @Override
    public void close() {
        if (window != MemoryUtil.NULL) {
            GLFW.glfwDestroyWindow(window);
            window = MemoryUtil.NULL;
        }
    }
}
