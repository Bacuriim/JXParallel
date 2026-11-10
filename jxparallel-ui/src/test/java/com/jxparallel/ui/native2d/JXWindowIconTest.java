package com.jxparallel.ui.native2d;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

/** The GLFW-free part of the window icon: the pixels GLFW gets, and the pictures that become icons. */
class JXWindowIconTest {
    @Test
    void argbBecomesRgbaBytesRowByRow() {
        JXWindowIcon icon = new JXWindowIcon(2, 1, new int[] {0x80FF4020, 0xFF000102});
        assertEquals(2, icon.getWidth());
        assertEquals(1, icon.getHeight());
        assertArrayEquals(new byte[] {(byte) 0xFF, 0x40, 0x20, (byte) 0x80, 0x00, 0x01, 0x02, (byte) 0xFF}, icon.rgba());
    }

    @Test
    void theIconKeepsACopyOfThePixels() {
        int[] pixels = {0xFF112233};
        JXWindowIcon icon = new JXWindowIcon(1, 1, pixels);
        pixels[0] = 0;
        assertArrayEquals(new int[] {0xFF112233}, icon.getPixels());
    }

    @Test
    void badSizesAndShortArraysAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new JXWindowIcon(0, 1, new int[1]));
        assertThrows(IllegalArgumentException.class, () -> new JXWindowIcon(2, 2, new int[3]));
        assertThrows(IllegalArgumentException.class, () -> new JXWindowIcon(1, 1, null));
    }

    @Test
    void picturesBecomeIconsOfTheirOwnSizes() throws Exception {
        List<JXWindowIcon> icons = JXWindowIcon.load("classpath:images/pixels4x2.png", "classpath:images/halves16x8.jpg");
        assertEquals(2, icons.size());
        assertEquals(4, icons.get(0).getWidth());
        assertEquals(2, icons.get(0).getHeight());
        assertEquals(16, icons.get(1).getWidth());
        assertEquals(8 * 16 * 4, icons.get(1).rgba().length);
    }
}
