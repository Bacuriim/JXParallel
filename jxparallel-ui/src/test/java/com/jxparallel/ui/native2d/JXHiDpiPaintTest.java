package com.jxparallel.ui.native2d;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.controls.JXControls;
import com.jxparallel.ui.layout.JXLayouts;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Surface;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Painting at scale 2: the layout stays in logical pixels and the pixels are drawn at the physical
 * resolution. The scale-1 results are pinned by the golden files of the other tests.
 */
class JXHiDpiPaintTest {
    private static final int W = 240;
    private static final int H = 120;

    private static JXElement screen() {
        return JXLayouts.column(8,
                JXElement.text("Customers"),
                JXLayouts.row(8, JXControls.button("Load", null), JXControls.button("Save", null)),
                JXControls.checkbox("Active", true));
    }

    /** x0, y0, x1, y1 (exclusive) of the pixels that differ from the one at (0, 0). */
    private static int[] bounds(int[] argb, int width, int height) {
        int x0 = width;
        int y0 = height;
        int x1 = 0;
        int y1 = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (argb[y * width + x] != argb[0]) {
                    x0 = Math.min(x0, x);
                    y0 = Math.min(y0, y);
                    x1 = Math.max(x1, x + 1);
                    y1 = Math.max(y1, y + 1);
                }
            }
        }
        return new int[] {x0, y0, x1, y1};
    }

    /** True if some 2x2 block of the image is not one color: the image was drawn at 2x, not pixel-doubled. */
    private static boolean hasDetailFinerThanTwoPixels(int[] argb, int width, int height) {
        for (int y = 0; y + 1 < height; y += 2) {
            for (int x = 0; x + 1 < width; x += 2) {
                int p = argb[y * width + x];
                if (argb[y * width + x + 1] != p || argb[(y + 1) * width + x] != p || argb[(y + 1) * width + x + 1] != p) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void softwareFrameAtScaleTwoIsTwiceTheSizeAndNotPixelDoubled() {
        int[] one = JXWindow.captureSoftware(screen(), W, H, 1f);
        int[] two = JXWindow.captureSoftware(screen(), W, H, 2f);
        assertEquals(W * H, one.length);
        assertEquals(4 * W * H, two.length, "physical pixels: twice the width and the height");
        int[] b1 = bounds(one, W, H);
        int[] b2 = bounds(two, 2 * W, 2 * H);
        for (int i = 0; i < 4; i++) {
            assertEquals(2 * b1[i], b2[i], 2, "the painted area is the same logical area, twice as many pixels (coordinate " + i + ")");
        }
        assertTrue(hasDetailFinerThanTwoPixels(two, 2 * W, 2 * H), "text and edges are drawn at 2x");
        assertArrayEquals(one, JXWindow.captureSoftware(screen(), W, H), "the unscaled overload is scale 1");
    }

    @Test
    void softwarePainterMultipliesCoordinatesByTheScale() {
        int[] px = new int[8 * 8];
        JXSoftwarePainter painter = new JXSoftwarePainter(px, 8, 8, 2f);
        painter.fillRect(1, 1, 2, 2, 0xFFFF0000);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                boolean inside = x >= 2 && x < 6 && y >= 2 && y < 6;
                assertEquals(inside ? 0xFFFF0000 : 0, px[y * 8 + x], "pixel " + x + "," + y);
            }
        }
    }

    @Test
    void skiaFrameAtScaleTwoIsDrawnOnAScaledCanvas() {
        int[] one = skia(1f);
        int[] two = skia(2f);
        int[] b1 = bounds(one, W, H);
        int[] b2 = bounds(two, 2 * W, 2 * H);
        for (int i = 0; i < 4; i++) {
            assertEquals(2 * b1[i], b2[i], 2, "same logical area at twice the pixels (coordinate " + i + ")");
        }
        assertTrue(hasDetailFinerThanTwoPixels(two, 2 * W, 2 * H), "drawn at 2x, not enlarged");
    }

    @Test
    void skiaPaintOverloadScalesTheCanvasAndRestoresIt() {
        try (Surface surface = Surface.makeRasterN32Premul(2 * W, 2 * H)) {
            Canvas canvas = surface.getCanvas();
            int before = canvas.getSaveCount();
            JXSkiaRenderer.paint(JXSkiaRenderer.mount(screen()), canvas, W, H, 2f);
            assertEquals(before, canvas.getSaveCount(), "the scale is undone");
        }
    }

    @Test
    void skiaScaleOneIsThePlainPaint() {
        int[] direct;
        try (Surface surface = Surface.makeRasterN32Premul(W, H)) {
            JXSkiaRenderer.paint(JXSkiaRenderer.mount(screen()), surface.getCanvas(), W, H);
            direct = pixels(surface);
        }
        assertArrayEquals(direct, skia(1f));
    }

    private static int[] skia(float scale) {
        int pw = Math.round(W * scale);
        int ph = Math.round(H * scale);
        try (Surface surface = Surface.makeRasterN32Premul(pw, ph)) {
            JXSkiaRenderer.paint(JXSkiaRenderer.mount(screen()), surface.getCanvas(), W, H, scale);
            return pixels(surface);
        }
    }

    private static int[] pixels(Surface surface) {
        try (Image image = surface.makeImageSnapshot(); Bitmap bitmap = new Bitmap()) {
            assertTrue(bitmap.allocN32Pixels(image.getWidth(), image.getHeight()));
            assertTrue(image.readPixels(bitmap));
            byte[] bytes = bitmap.readPixels();
            int[] out = new int[bytes.length / 4];
            for (int i = 0; i < out.length; i++) {
                out[i] = (bytes[4 * i] & 0xFF) | (bytes[4 * i + 1] & 0xFF) << 8 | (bytes[4 * i + 2] & 0xFF) << 16
                        | (bytes[4 * i + 3] & 0xFF) << 24;
            }
            return out;
        }
    }
}
