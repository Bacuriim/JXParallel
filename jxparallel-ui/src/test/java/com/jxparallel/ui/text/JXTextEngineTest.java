package com.jxparallel.ui.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class JXTextEngineTest {
    private static final float SIZE = JXTextEngine.DEFAULT_SIZE;
    private final JXTextEngine engine = JXTextEngine.get();

    @Test
    void emptyTextHasNoWidth() {
        assertEquals(0.0f, engine.width("", SIZE));
        assertEquals(0.0f, engine.width(null, SIZE));
    }

    @Test
    void widthGrowsWithTextAndScalesWithSize() {
        float abc = engine.width("abc", SIZE);

        assertTrue(abc > 0);
        assertTrue(engine.width("abcd", SIZE) > abc);
        assertEquals(2 * abc, engine.width("abc", 2 * SIZE), 0.01f);
    }

    @Test
    void lineMetricsAreSane() {
        float line = engine.lineHeight(SIZE);

        assertTrue(line >= SIZE && line <= 2 * SIZE, "line height " + line);
        assertTrue(engine.ascent(SIZE) > 0 && engine.ascent(SIZE) < line);
        assertEquals(engine.ascent(SIZE), engine.baseline(line, SIZE), 0.001f, "baseline of a box one line tall");
    }

    @Test
    void usesASystemFontOnDesktopSystems() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"));
        assertNotNull(engine.getFontFile(), "Windows always has Segoe UI or Arial");
    }

    /** HarfBuzz applies the font's kerning: "AV" is narrower than "A" plus "V" in Segoe UI, Arial and DejaVu. */
    @Test
    void appliesKerning() {
        assumeTrue(engine.getFontFile() != null, "needs a real font");
        assertTrue(engine.width("AV", SIZE) < engine.width("A", SIZE) + engine.width("V", SIZE));
    }

    /** Accents and scripts outside Latin-1 are shaped, not dropped. */
    @Test
    void measuresNonAsciiText() {
        assumeTrue(engine.getFontFile() != null, "needs a real font");
        assertTrue(engine.width("ação", SIZE) > engine.width("aca", SIZE));
    }

    @Test
    void isThreadSafe() throws Exception {
        float expected = engine.width("Parallel layout", SIZE);
        Thread[] threads = new Thread[4];
        float[] results = new float[threads.length];
        for (int t = 0; t < threads.length; t++) {
            final int index = t;
            threads[t] = new Thread(() -> {
                float last = 0;
                for (int i = 0; i < 2000; i++) {
                    last = engine.width("Parallel layout", SIZE);
                }
                results[index] = last;
            });
            threads[t].start();
        }
        for (int t = 0; t < threads.length; t++) {
            threads[t].join();
            assertEquals(expected, results[t]);
        }
    }
}
