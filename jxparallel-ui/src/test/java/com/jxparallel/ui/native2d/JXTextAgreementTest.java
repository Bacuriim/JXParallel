package com.jxparallel.ui.native2d;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.text.JXTextEngine;
import io.github.humbleui.skija.Font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The layout measures with HarfBuzz, Skia draws with its own shaper. On the same font file they
 * must agree, or text would overflow the box the layout gave it.
 */
class JXTextAgreementTest {
    @Test
    void skiaDrawsTextAsWideAsTheLayoutMeasured() {
        assumeTrue("64".equals(System.getProperty("sun.arch.data.model")), "Skia has no 32-bit natives");
        JXTextEngine engine = JXTextEngine.get();
        assumeTrue(engine.getFontFile() != null, "needs a real font");
        try (Font font = JXSkiaRenderer.font()) {
            for (String text : new String[] {"Save", "Customers", "Ada Lovelace", "AVATAR Wave", "ação é útil", "1234567890"}) {
                float drawn = JXSkiaRenderer.shaped(text, font).getBlockBounds().getWidth();
                assertEquals(engine.width(text, JXTextEngine.DEFAULT_SIZE), drawn, 0.5f, text);
            }
        }
    }
}
