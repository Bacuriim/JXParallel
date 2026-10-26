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
    void shouldPickNanoVGOn32BitAndJava8AndSkiaOtherwise() {
        assertEquals(defaultRenderer(), JXWindow.selectRenderer());
    }

    @Test
    void shouldHonourForcedRenderer() {
        System.setProperty("jx.renderer", "NanoVG");
        assertEquals(JXWindow.NANOVG, JXWindow.selectRenderer());
        System.setProperty("jx.renderer", "skia");
        assertEquals(JXWindow.SKIA, JXWindow.selectRenderer());
        System.setProperty("jx.renderer", "vulkan");
        assertEquals(defaultRenderer(), JXWindow.selectRenderer());
    }

    private static String defaultRenderer() {
        boolean is32Bit = "32".equals(System.getProperty("sun.arch.data.model"));
        boolean java8 = System.getProperty("java.specification.version", "").startsWith("1.");
        return is32Bit || java8 ? JXWindow.NANOVG : JXWindow.SKIA;
    }
}
