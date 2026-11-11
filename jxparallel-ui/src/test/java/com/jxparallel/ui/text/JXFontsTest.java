package com.jxparallel.ui.text;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** The font registry: registering from a file, a stream and the classpath, reading style from the file, matching. */
class JXFontsTest {
    @TempDir
    File dir;

    @AfterEach
    void forget() {
        JXFonts.clearRegistered();
    }

    private File font(String family, String style, int weight, boolean italic) throws IOException {
        return TestFonts.write(dir, family, style, weight, italic, 600, "abc");
    }

    @Test
    void registersAFileUnderAFamilyAndReadsItsStyleFromTheFile() throws IOException {
        assertEquals(1, JXFonts.register("Reg Family", font("Original Name", "Light Italic", 300, true)));

        assertTrue(JXFonts.hasFamily("reg family"), "names compare ignoring case");
        assertTrue(JXFonts.families().contains("Reg Family"));
        JXFonts.Face face = JXFonts.faces("Reg Family").get(0);
        assertEquals(300, face.weight());
        assertTrue(face.italic());
        assertTrue(face.registered());
    }

    @Test
    void anExplicitWeightAndItalicOverrideTheFile() throws IOException {
        JXFonts.register("Over Family", font("X", "Regular", 400, false), 800, true);

        JXFonts.Face face = JXFonts.faces("Over Family").get(0);
        assertEquals(800, face.weight());
        assertTrue(face.italic());
    }

    @Test
    void matchesItalicFirstThenTheNearestWeight() throws IOException {
        File regular = font("M", "Regular", 400, false);
        File bold = font("M", "Bold", 700, false);
        File italic = font("M", "Italic", 400, true);
        File boldItalic = font("M", "Bold Italic", 700, true);
        for (File f : new File[] {regular, bold, italic, boldItalic}) {
            JXFonts.register("Match", f);
        }

        assertEquals(regular.getAbsolutePath(), JXFonts.resolve(JXFont.of("Match", 400, false)).file());
        assertEquals(bold.getAbsolutePath(), JXFonts.resolve(JXFont.of("Match", 700, false)).file());
        assertEquals(italic.getAbsolutePath(), JXFonts.resolve(JXFont.of("Match", 400, true)).file());
        assertEquals(boldItalic.getAbsolutePath(), JXFonts.resolve(JXFont.of("match", 700, true)).file());
        assertEquals(bold.getAbsolutePath(), JXFonts.resolve(JXFont.of("Match", 600, false)).file(), "600 is nearer 700");
        assertEquals(regular.getAbsolutePath(), JXFonts.resolve(JXFont.of("Match", 300, false)).file());
        assertEquals(boldItalic.getAbsolutePath(), JXFonts.resolve(JXFont.of("Match", 900, true)).file());
    }

    @Test
    void aFamilyWithoutAnItalicFaceUsesTheUprightOne() throws IOException {
        File regular = font("N", "Regular", 400, false);
        JXFonts.register("No Italic", regular);

        assertEquals(regular.getAbsolutePath(), JXFonts.resolve(JXFont.of("No Italic", 400, true)).file());
    }

    @Test
    void registersFromAStream() throws IOException {
        byte[] bytes = TestFonts.build("S", "Regular", 400, false, 600, "abc");

        assertEquals(1, JXFonts.register("Stream Family", new ByteArrayInputStream(bytes)));

        JXFonts.Face face = JXFonts.resolve(JXFont.of("Stream Family", 400, false));
        assertNotNull(face);
        assertTrue(new File(face.file()).isFile());
    }

    @Test
    void registersFromTheClasspath() throws Exception {
        File root = new File(dir, "cp");
        File fonts = new File(root, "fonts");
        assertTrue(fonts.mkdirs());
        try (FileOutputStream out = new FileOutputStream(new File(fonts, "R.ttf"))) {
            out.write(TestFonts.build("C", "Regular", 400, false, 600, "abc"));
        }
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader loader = new URLClassLoader(new URL[] {root.toURI().toURL()}, previous)) {
            Thread.currentThread().setContextClassLoader(loader);
            assertEquals(1, JXFonts.registerResource("Resource Family", "/fonts/R.ttf"));
            assertThrows(IOException.class, () -> JXFonts.registerResource("Nope", "/fonts/missing.ttf"));
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
        assertNotNull(JXFonts.resolve(JXFont.of("Resource Family", 400, false)));
    }

    @Test
    void rejectsAFileThatIsNotAFont() throws IOException {
        File junk = new File(dir, "junk.ttf");
        Files.write(junk.toPath(), new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14});

        assertThrows(IllegalArgumentException.class, () -> JXFonts.register("Junk", junk));
        assertThrows(IllegalArgumentException.class, () -> JXFonts.register(" ", font("Z", "Regular", 400, false)));
        assertFalse(JXFonts.hasFamily("Junk"));
    }

    @Test
    void anUnknownFamilyResolvesToNothing() {
        assertNull(JXFonts.resolve(JXFont.of("No Such Family 123", 400, false)));
    }

    @Test
    void aRegisteredFamilyReplacesTheSystemFamilyOfTheSameName() throws IOException {
        File mine = font("Mine", "Regular", 400, false);
        JXFonts.register("Arial", mine);

        assertEquals(mine.getAbsolutePath(), JXFonts.resolve(JXFont.of("arial", 400, false)).file());
    }

    @Test
    void findsSystemFontsByFamilyName() {
        String ui = JXFonts.uiFamily();
        assumeTrue(ui != null, "no UI font on this machine");

        JXFonts.Face face = JXFonts.resolve(JXFont.of(ui, 400, false));

        assertNotNull(face, "the UI font's own family is found");
        assertFalse(JXFonts.families().isEmpty());
    }

    @Test
    void theUiFontWithAnotherWeightComesFromItsOwnFamily() {
        String ui = JXFonts.uiFamily();
        assumeTrue(ui != null, "no UI font on this machine");
        JXFonts.Face bold = JXFonts.resolve(JXFont.of(null, 700, false));

        assumeTrue(bold != null);
        assertTrue(bold.weight() >= 600, "the bold face of " + ui + " is bold: " + bold);
    }
}
