package com.jxparallel.ui.native2d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

class JXGlfwErrorsTest {
    @Test
    void anEmptyOrNonTextClipboardIsNotReported() {
        assertEquals(GLFW.GLFW_FORMAT_UNAVAILABLE, JXGlfwErrors.FORMAT_UNAVAILABLE);
        assertTrue(JXGlfwErrors.quiet(GLFW.GLFW_FORMAT_UNAVAILABLE));
    }

    @Test
    void everyOtherGlfwErrorIsStillReported() {
        int[] others = {GLFW.GLFW_NOT_INITIALIZED, GLFW.GLFW_NO_CURRENT_CONTEXT, GLFW.GLFW_INVALID_ENUM, GLFW.GLFW_INVALID_VALUE,
            GLFW.GLFW_OUT_OF_MEMORY, GLFW.GLFW_API_UNAVAILABLE, GLFW.GLFW_VERSION_UNAVAILABLE, GLFW.GLFW_PLATFORM_ERROR,
            GLFW.GLFW_NO_WINDOW_CONTEXT, 0};
        for (int error : others) {
            assertFalse(JXGlfwErrors.quiet(error), "0x" + Integer.toHexString(error));
        }
    }
}
