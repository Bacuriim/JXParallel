package com.jxparallel.ui.native2d;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;

import org.junit.jupiter.api.Test;

/** A translucent rectangle gives, on every pixel, what the source-over of that pixel gives: the span loop is the same math. */
class JXSoftwareSpanTest {
    @Test
    void aTranslucentRectangleIsSourceOverOnEveryPixel() {
        int w = 40;
        int h = 12;
        Random random = new Random(7);
        int[] background = new int[w * h];
        for (int i = 0; i < background.length; i++) {
            background[i] = 0xFF000000 | random.nextInt(0x1000000);
        }
        for (int alpha : new int[] {1, 17, 128, 186, 254}) {
            int color = (alpha << 24) | random.nextInt(0x1000000);
            int[] pixels = background.clone();
            new JXSoftwarePainter(pixels, w, h, 1f).fillRect(0, 0, w, h, color);
            for (int i = 0; i < pixels.length; i++) {
                assertEquals(JXSoftwarePainter.over(background[i], color, alpha), pixels[i], "alpha " + alpha + " pixel " + i);
            }
        }
    }

    @Test
    void anOpaqueRectangleReplacesThePixelsAndAnInvisibleOneChangesNone() {
        int[] pixels = new int[20 * 4];
        java.util.Arrays.fill(pixels, 0xFF336699);
        int[] before = pixels.clone();
        JXSoftwarePainter painter = new JXSoftwarePainter(pixels, 20, 4, 1f);
        painter.fillRect(0, 0, 20, 4, 0x00FF0000);
        org.junit.jupiter.api.Assertions.assertArrayEquals(before, pixels);
        painter.fillRect(0, 0, 20, 4, 0xFF112233);
        for (int p : pixels) {
            assertEquals(0xFF112233, p);
        }
    }
}
