package com.jxparallel.ui.native2d;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JXWindowRendererTest {
    @AfterEach
    void clearOverride() {
        System.clearProperty("jx.renderer");
    }

    @Test
    void shouldPickNanoVGOn32BitAndSkiaOn64Bit() {
        String expected = "32".equals(System.getProperty("sun.arch.data.model")) ? JXWindow.NANOVG : JXWindow.SKIA;
        assertEquals(expected, JXWindow.selectRenderer());
    }

    @Test
    void shouldHonourForcedRenderer() {
        System.setProperty("jx.renderer", "NanoVG");
        assertEquals(JXWindow.NANOVG, JXWindow.selectRenderer());
        System.setProperty("jx.renderer", "skia");
        assertEquals(JXWindow.SKIA, JXWindow.selectRenderer());
        System.setProperty("jx.renderer", "vulkan");
        assertEquals("32".equals(System.getProperty("sun.arch.data.model")) ? JXWindow.NANOVG : JXWindow.SKIA,
                JXWindow.selectRenderer());
    }
}
