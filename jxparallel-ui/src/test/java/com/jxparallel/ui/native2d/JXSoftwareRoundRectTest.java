package com.jxparallel.ui.native2d;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * The straight part of a rounded rectangle is blended as one span per row; it must give, on every pixel of the frame,
 * what the pixel by pixel version gives. Text clipped by the painter must equal the unclipped text cropped.
 */
class JXSoftwareRoundRectTest {
    private static final float[] SCALES = {1f, 1.25f, 1.5f, 2f};

    private interface Shape {
        void paint(JXSoftwarePainter painter);
    }

    private static int[] frame(int w, int h, boolean runs, long seed, Shape shape, float scale) {
        Random random = new Random(seed);
        int[] pixels = new int[w * h];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = 0xFF000000 | random.nextInt(0x1000000);
        }
        boolean saved = JXSoftwarePainter.runs;
        JXSoftwarePainter.runs = runs;
        try {
            shape.paint(new JXSoftwarePainter(pixels, w, h, scale));
        } finally {
            JXSoftwarePainter.runs = saved;
        }
        return pixels;
    }

    private static void same(int w, int h, long seed, float scale, String what, Shape shape) {
        assertArrayEquals(frame(w, h, false, seed, shape, scale), frame(w, h, true, seed, shape, scale), what + " at scale " + scale);
    }

    @Test
    void randomRoundedRectanglesAreTheSameWithAndWithoutRuns() {
        Random random = new Random(11);
        int w = 120;
        int h = 70;
        for (int n = 0; n < 400; n++) {
            final float x = random.nextInt(40) + (random.nextBoolean() ? random.nextFloat() : 0);
            final float y = random.nextInt(20) + (random.nextBoolean() ? random.nextFloat() : 0);
            final float rw = 1 + random.nextInt(90) + (random.nextBoolean() ? random.nextFloat() : 0);
            final float rh = 1 + random.nextInt(40) + (random.nextBoolean() ? random.nextFloat() : 0);
            final float radius = random.nextInt(4) == 0 ? 0 : random.nextFloat() * Math.min(rw, rh) * (random.nextInt(5) == 0 ? 1.5f : 0.5f);
            final float stroke = random.nextInt(3) == 0 ? 0.5f : 1 + random.nextInt(3);
            final int color = (random.nextBoolean() ? 0xFF000000 : (1 + random.nextInt(254)) << 24) | random.nextInt(0x1000000);
            final int bottom = (random.nextBoolean() ? 0xFF000000 : (1 + random.nextInt(254)) << 24) | random.nextInt(0x1000000);
            final float scale = SCALES[random.nextInt(SCALES.length)];
            final String what = "x=" + x + " y=" + y + " w=" + rw + " h=" + rh + " r=" + radius + " s=" + stroke;
            long seed = n;
            same(w, h, seed, scale, "fill " + what, p -> p.fillRoundRect(x, y, rw, rh, radius, color));
            same(w, h, seed, scale, "stroke " + what, p -> p.strokeRoundRect(x, y, rw, rh, radius, stroke, color));
            same(w, h, seed, scale, "gradient " + what, p -> p.fillRoundRectGradient(x, y, rw, rh, radius, color, bottom));
        }
    }

    @Test
    void theFieldOutlineOfMaterialIsTheSameAtEveryWidth() {
        for (float scale : SCALES) {
            for (int width = 8; width < 140; width += 3) {
                final float rw = width;
                same(150, 60, width, scale, "field " + width, p -> {
                    p.fillRoundRect(5, 5, rw, 36, 4, 0xFFFEF7FF);
                    p.strokeRoundRect(5, 5, rw, 36, 4, 1, 0xFF79747E);
                    p.strokeRoundRect(5.5f, 5.25f, rw, 36, 4, 2, 0x80FF0000);
                });
            }
        }
    }

    @Test
    void clippedTextIsTheUnclippedTextCropped() {
        int w = 160;
        int h = 40;
        final String text = "Clipped text, 0123 AWjg";
        int[] all = frame(w, h, true, 5, p -> p.text(text, 3.3f, 25, 14, false, 0xFF202020), 1f);
        int[] before = frame(w, h, true, 5, p -> { }, 1f);
        for (int[] box : new int[][] {{0, 0, 160, 40}, {10, 5, 50, 30}, {-20, -5, 60, 20}, {100, 15, 80, 40}, {40, 22, 3, 2}, {70, 0, 1, 40}}) {
            final int[] clip = box;
            int[] clipped = frame(w, h, true, 5, p -> {
                p.clip(clip[0], clip[1], clip[2], clip[3]);
                p.text(text, 3.3f, 25, 14, false, 0xFF202020);
                p.restore();
            }, 1f);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    boolean inside = x >= clip[0] && x < clip[0] + clip[2] && y >= clip[1] && y < clip[1] + clip[3];
                    assertEquals(inside ? all[y * w + x] : before[y * w + x], clipped[y * w + x], "pixel " + x + "," + y + " clip " + java.util.Arrays.toString(clip));
                }
            }
        }
    }
}
