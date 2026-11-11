package com.jxparallel.ui.native2d;

import org.lwjgl.glfw.GLFWErrorCallback;

/**
 * The GLFW error callback of the display thread: it prints errors like LWJGL's own {@code createPrint}, except
 * {@code GLFW_FORMAT_UNAVAILABLE}, which {@code glfwGetClipboardString} raises when the clipboard is empty or holds no text.
 * That is no fault ({@code null} comes back and a paste inserts nothing), so it is not printed. Other errors still are.
 */
final class JXGlfwErrors {
    /** {@code GLFW_FORMAT_UNAVAILABLE}: the clipboard has no text, or another format was asked for than there is. */
    static final int FORMAT_UNAVAILABLE = 0x10009;

    private JXGlfwErrors() {
    }

    /** Whether the error is an expected, harmless one that is not reported. */
    static boolean quiet(int error) {
        return error == FORMAT_UNAVAILABLE;
    }

    /** Installs the callback; call on the thread that initialises GLFW, before {@code glfwInit}. */
    static void install() {
        final GLFWErrorCallback print = GLFWErrorCallback.createPrint(System.err);
        GLFWErrorCallback.create((error, description) -> {
            if (!quiet(error)) {
                print.invoke(error, description);
            }
        }).set();
    }
}
