package com.jxparallel.ui.text;

import java.io.File;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Styles measure by their own face, missing glyphs come from a fallback face, and mixed-direction text is reordered. */
class JXShapingTest {
    private static final String LATIN = TestFonts.range('a', 'z') + " ";
    private static final String HEBREW = TestFonts.range(0x5D0, 0x5EA);

    @TempDir
    File dir;

    @AfterEach
    void forget() {
        JXFonts.clearRegistered();
    }

    @Test
    void eachStyleMeasuresWithItsOwnFace() throws Exception {
        // different advances per face, so a mix-up shows in the width
        JXFonts.register("Styles", TestFonts.write(dir, "Styles", "Regular", 400, false, 500, LATIN));
        JXFonts.register("Styles", TestFonts.write(dir, "Styles", "Bold", 700, false, 600, LATIN));
        JXFonts.register("Styles", TestFonts.write(dir, "Styles", "Italic", 400, true, 700, LATIN));
        JXFonts.register("Styles", TestFonts.write(dir, "Styles", "Bold Italic", 700, true, 800, LATIN));

        assertEquals(2 * 5.0f, JXTextEngine.get(JXFont.of("Styles", 400, false)).width("ab", 10), 0.001f);
        assertEquals(2 * 6.0f, JXTextEngine.get(JXFont.of("Styles", 700, false)).width("ab", 10), 0.001f);
        assertEquals(2 * 7.0f, JXTextEngine.get(JXFont.of("Styles", 400, true)).width("ab", 10), 0.001f);
        assertEquals(2 * 8.0f, JXTextEngine.get(JXFont.of("Styles", 700, true)).width("ab", 10), 0.001f);
        assertEquals(2 * 6.0f, JXTextEngine.get(JXFont.of("Styles", 600, false)).width("ab", 10), 0.001f, "600 -> nearest face");
    }

    @Test
    void theUiFontStaysTheLegacyEngineWithoutRequests() {
        assertSame(JXTextEngine.get(), JXTextEngine.get(JXFont.DEFAULT));
        assertSame(JXTextEngine.get(true), JXTextEngine.get(JXFont.BOLD));
        assertSame(JXTextEngine.get(true), JXTextEngine.get(JXFont.of(null, 700, false)));
        assertSame(JXTextEngine.get(), JXTextEngine.get(JXFont.of("  ", 0, false)));
    }

    @Test
    void anUnknownFamilyIsDrawnInTheUiFont() {
        assertSame(JXTextEngine.get(), JXTextEngine.get(JXFont.of("No Such Family 123", 400, false)));
        assertSame(JXTextEngine.get(true), JXTextEngine.get(JXFont.of("No Such Family 123", 700, false)));
    }

    @Test
    void aMissingGlyphComesFromAFallbackFaceThatHasIt() throws Exception {
        JXFonts.register("Primary", TestFonts.write(dir, "Primary", "Regular", 400, false, 500, LATIN));
        JXFonts.register("Backup", TestFonts.write(dir, "Backup", "Regular", 400, false, 1000, "😀"));
        JXTextEngine primary = JXTextEngine.get(JXFont.of("Primary", 400, false));

        JXShapedText shaped = primary.shape("ab😀cd");

        assertEquals(3, shaped.runs().length);
        assertSame(primary, shaped.runs()[0].face());
        assertEquals("ab", shaped.text(shaped.runs()[0]));
        JXShapedText.Run fallback = shaped.runs()[1];
        assertFalse(fallback.face() == primary);
        assertEquals("😀", shaped.text(fallback), "the surrogate pair stays whole, in the face that has it");
        assertTrue(fallback.face().covers(0xE000) && fallback.face().covers(0x1F600));
        assertEquals(2, fallback.glyphCount());
        assertSame(primary, shaped.runs()[2].face());
        // measured with each run's own face: 4 narrow glyphs of 5 + 2 wide ones of 10 at size 10
        assertEquals(4 * 5.0f + 2 * 10.0f, primary.width("ab😀cd", 10), 0.001f);
        assertFalse(shaped.simple(primary));
    }

    @Test
    void aMarkStaysWithItsBaseCharacterFace() throws Exception {
        JXFonts.register("Primary2", TestFonts.write(dir, "Primary2", "Regular", 400, false, 500, LATIN));
        JXFonts.register("Backup2", TestFonts.write(dir, "Backup2", "Regular", 400, false, 1000, "́"));
        JXTextEngine primary = JXTextEngine.get(JXFont.of("Primary2", 400, false));

        JXShapedText shaped = primary.shape("́");

        assertEquals(1, shaped.runs().length);
        assertEquals("́", shaped.text(shaped.runs()[0]));
    }

    @Test
    void fallbackPicksASystemFontThatHasACjkGlyph() {
        JXTextEngine engine = JXTextEngine.get();
        assumeTrue(engine.getFontFile() != null, "needs a real font");
        assumeTrue(!engine.covers(0x4E2D), "the UI font itself has CJK glyphs");

        JXShapedText shaped = engine.shape("中");

        JXTextEngine face = shaped.runs()[0].face();
        assumeTrue(face != engine, "no system font on this machine has U+4E2D");
        assertTrue(face.covers(0x4E2D));
    }

    @Test
    void mixedDirectionTextIsReorderedIntoVisualRuns() throws Exception {
        JXFonts.register("Bidi", TestFonts.write(dir, "Bidi", "Regular", 400, false, 500, LATIN + HEBREW));
        JXTextEngine engine = JXTextEngine.get(JXFont.of("Bidi", 400, false));
        String alef = "א";
        String bet = "ב";
        String gimel = "ג";

        JXShapedText shaped = engine.shape("ab " + alef + bet + gimel + " cd");

        JXShapedText.Run[] runs = shaped.runs();
        assertEquals(3, runs.length);
        assertEquals("ab ", shaped.text(runs[0]));
        assertFalse(runs[0].rtl());
        assertEquals(alef + bet + gimel, shaped.text(runs[1]));
        assertTrue(runs[1].rtl());
        assertEquals(" cd", shaped.text(runs[2]));
        // inside the Hebrew run the glyphs are left to right on screen: the last letter first
        int a = engine.shape(alef).runs()[0].glyphId(0);
        int b = engine.shape(bet).runs()[0].glyphId(0);
        int g = engine.shape(gimel).runs()[0].glyphId(0);
        assertArrayEquals(new int[] {g, b, a}, new int[] {runs[1].glyphId(0), runs[1].glyphId(1), runs[1].glyphId(2)});
        // the width does not depend on the order
        assertEquals(9 * 5.0f, engine.width("ab " + alef + bet + gimel + " cd", 10), 0.001f);
    }

    @Test
    void aHebrewRunIsDrawnLastLetterFirstAndNumbersKeepTheirOrder() throws Exception {
        JXFonts.register("Bidi2", TestFonts.write(dir, "Bidi2", "Regular", 400, false, 500, LATIN + HEBREW + "0123456789"));
        JXTextEngine engine = JXTextEngine.get(JXFont.of("Bidi2", 400, false));
        String text = "אבג 12";

        JXShapedText shaped = engine.shape(text);

        // Hebrew paragraph: the number sits to the left of the letters, its digits still in order
        JXShapedText.Run[] runs = shaped.runs();
        assertEquals("12", shaped.text(runs[0]));
        assertFalse(runs[0].rtl());
        JXShapedText.Run letters = runs[runs.length - 1];
        assertEquals("אבג ", shaped.text(letters), "the space between belongs to the right-to-left run");
        assertTrue(letters.rtl());
        int space = engine.shape(" ").runs()[0].glyphId(0);
        int gimel = engine.shape("ג").runs()[0].glyphId(0);
        assertEquals(space, letters.glyphId(0), "visually the last character comes first");
        assertEquals(gimel, letters.glyphId(1));
    }

    @Test
    void leftToRightTextIsOneRunAndMeasuresAsBefore() {
        JXTextEngine engine = JXTextEngine.get();
        assumeTrue(engine.getFontFile() != null, "needs a real font");

        JXShapedText shaped = engine.shape("Hello, world");

        assertNotNull(shaped);
        assertTrue(shaped.simple(engine));
        assertEquals(engine.width("Hello, world", 12), shaped.runs()[0].advance(12), 0f);
    }
}
