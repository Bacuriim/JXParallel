package com.jxparallel.ui.native2d;

import java.io.File;
import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jxparallel.ui.text.JXFont;
import com.jxparallel.ui.text.JXFonts;
import com.jxparallel.ui.text.JXTextEngine;
import com.jxparallel.ui.text.TestFonts;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * What a renderer draws is where the layout measured it: for every style and for fallback runs, the ink of the
 * drawn text spans the advance that {@link JXTextEngine#width} reports. The test fonts have a square glyph from
 * 10% to 50% of the em, so the ink edges are known exactly.
 */
class JXFontPaintTest {
    private static final int W = 400;
    private static final int H = 60;
    private static final float SIZE = 100;
    private static final String LATIN = TestFonts.range('a', 'z');

    @TempDir
    File dir;

    @AfterEach
    void forget() {
        JXFonts.clearRegistered();
    }

    private void registerStyles() throws Exception {
        JXFonts.register("Paint", TestFonts.write(dir, "Paint", "Regular", 400, false, 500, LATIN));
        JXFonts.register("Paint", TestFonts.write(dir, "Paint", "Bold", 700, false, 600, LATIN));
        JXFonts.register("Paint", TestFonts.write(dir, "Paint", "Italic", 400, true, 700, LATIN));
        JXFonts.register("Paint", TestFonts.write(dir, "Paint", "Bold Italic", 700, true, 800, LATIN));
    }

    /** First and last ink column of a frame whose background is white, or null. */
    private static int[] inkColumns(int[] argb) {
        int first = -1;
        int last = -1;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if ((argb[y * W + x] & 0xFFFFFF) != 0xFFFFFF) {
                    if (first < 0 || x < first) {
                        first = x;
                    }
                    last = Math.max(last, x);
                }
            }
        }
        return first < 0 ? null : new int[] {first, last};
    }

    private static boolean inkAt(int[] argb, int column) {
        for (int y = 0; y < H; y++) {
            if ((argb[y * W + column] & 0xFFFFFF) != 0xFFFFFF) {
                return true;
            }
        }
        return false;
    }

    private static int[] software(String text, JXFont font) {
        int[] pixels = new int[W * H];
        Arrays.fill(pixels, 0xFFFFFFFF);
        new JXSoftwarePainter(pixels, W, H, 1f).text(text, 0, 50, SIZE, font, 0xFF000000);
        return pixels;
    }

    @Test
    void softwareInkSpansTheMeasuredAdvanceOfEachStyle() throws Exception {
        registerStyles();
        int[][] styles = {{400, 0, 500}, {700, 0, 600}, {400, 1, 700}, {700, 1, 800}};
        for (int[] style : styles) {
            JXFont font = JXFont.of("Paint", style[0], style[1] == 1);
            float width = JXTextEngine.get(font).width("aaa", SIZE);
            assertEquals(3 * style[2] / 10f, width, 0.01f);

            int[] ink = inkColumns(software("aaa", font));

            assertEquals(10, ink[0], 1, "left edge of the first glyph " + font);
            assertEquals(width - style[2] / 10f + 50, ink[1] + 1, 1.5f, "right edge of the last glyph " + font);
        }
    }

    @Test
    void softwareDrawsAFallbackRunWhereTheMeasurementPutsIt() throws Exception {
        JXFonts.register("PaintP", TestFonts.write(dir, "PaintP", "Regular", 400, false, 500, LATIN));
        JXFonts.register("PaintB", TestFonts.write(dir, "PaintB", "Regular", 400, false, 1000, ""));
        JXFont font = JXFont.of("PaintP", 400, false);
        assertEquals(150, JXTextEngine.get(font).width("a", SIZE), 0.01f);

        int[] pixels = software("a", font);

        assertTrue(inkAt(pixels, 30), "the Latin glyph, 10..50");
        assertTrue(!inkAt(pixels, 55), "nothing between the glyphs");
        assertTrue(inkAt(pixels, 80), "the fallback glyph starts at the pen x 50 plus 10%: 60..100");
        assertTrue(!inkAt(pixels, 105));
    }

    @Test
    void skiaInkSpansTheMeasuredAdvanceOfEachStyleAndFallbackRun() throws Exception {
        TestSkia.assume();
        registerStyles();
        JXFonts.register("PaintB", TestFonts.write(dir, "PaintB", "Regular", 400, false, 1000, ""));
        try (Surface surface = Surface.makeRasterN32Premul(W, H)) {
            for (int[] style : new int[][] {{400, 0, 500}, {700, 1, 800}}) {
                JXFont font = JXFont.of("Paint", style[0], style[1] == 1);
                int[] ink = skia(surface, "aaa", font);
                assumeTrue(ink != null, "Skia cannot load the generated test font on this platform");
                float width = JXTextEngine.get(font).width("aaa", SIZE);
                assertEquals(10, ink[0], 1.5f, "left edge " + font);
                assertEquals(width - style[2] / 10f + 50, ink[1] + 1, 1.5f, "right edge " + font);
            }
            // a fallback run: Latin from the family, the private-use glyph from the registered backup
            JXFont font = JXFont.of("Paint", 400, false);
            int[] ink = skia(surface, "a", font);
            assumeTrue(ink != null, "Skia cannot load the generated test font on this platform");
            float width = JXTextEngine.get(font).width("a", SIZE);
            assertEquals(150, width, 0.01f);
            assertEquals(width - 100 + 50, ink[1] + 1, 1.5f);
        }
    }

    /** Skia draws the system emoji font's colour glyphs through the fallback run (seen on Windows: Segoe UI Emoji). */
    @Test
    void skiaDrawsAnEmojiInColourWhenTheSystemHasAColourEmojiFont() throws Exception {
        TestSkia.assume();
        assumeTrue(new File("C:\\Windows\\Fonts\\seguiemj.ttf").isFile(), "needs Segoe UI Emoji");
        JXTextEngine ui = JXTextEngine.get();
        assumeTrue(ui.getFontFile() != null && !ui.covers(0x1F600), "the UI font draws emoji itself");
        try (Surface surface = Surface.makeRasterN32Premul(W, H)) {
            Canvas canvas = surface.getCanvas();
            canvas.clear(0xFFFFFFFF);
            try (JXSkiaPainter painter = new JXSkiaPainter(canvas)) {
                painter.text("\uD83D\uDE00", 4, 40, 28, JXFont.DEFAULT, 0xFF000000);
            }
            try (Image image = surface.makeImageSnapshot(); Bitmap bitmap = new Bitmap()) {
                bitmap.allocN32Pixels(W, H);
                image.readPixels(bitmap);
                byte[] b = bitmap.readPixels();
                int coloured = 0;
                for (int i = 0; i < b.length; i += 4) {
                    int spread = Math.max(Math.abs((b[i] & 0xFF) - (b[i + 1] & 0xFF)), Math.abs((b[i + 2] & 0xFF) - (b[i + 1] & 0xFF)));
                    if (spread > 40) {
                        coloured++;
                    }
                }
                assertTrue(coloured > 100, "the emoji has colour: " + coloured);
            }
        }
    }

    /** Draws on a white surface; first and last ink columns. */
    private static int[] skia(Surface surface, String text, JXFont font) {
        Canvas canvas = surface.getCanvas();
        canvas.clear(0xFFFFFFFF);
        try (JXSkiaPainter painter = new JXSkiaPainter(canvas)) {
            painter.text(text, 0, 50, SIZE, font, 0xFF000000);
        }
        try (Image image = surface.makeImageSnapshot(); Bitmap bitmap = new Bitmap()) {
            bitmap.allocN32Pixels(W, H);
            image.readPixels(bitmap);
            byte[] bytes = bitmap.readPixels();
            int[] argb = new int[W * H];
            for (int i = 0; i < argb.length; i++) {
                argb[i] = (bytes[4 * i + 2] & 0xFF) << 16 | (bytes[4 * i + 1] & 0xFF) << 8 | bytes[4 * i] & 0xFF;
            }
            // anti-aliased edges are not pure white: count columns that are clearly inked
            int first = -1;
            int last = -1;
            for (int x = 0; x < W; x++) {
                for (int y = 0; y < H; y++) {
                    if ((argb[y * W + x] & 0xFF) < 128) {
                        if (first < 0) {
                            first = x;
                        }
                        last = x;
                        break;
                    }
                }
            }
            return first < 0 ? null : new int[] {first, last};
        }
    }
}
