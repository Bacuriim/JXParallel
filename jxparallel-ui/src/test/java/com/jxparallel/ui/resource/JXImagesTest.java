package com.jxparallel.ui.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Decoding of the committed fixtures in {@code src/test/resources/images} (made once with ImageIO; this module
 * itself never uses AWT): pixel values, the three ways in (path, resource, stream and bytes), the cache and
 * what a missing or corrupt file does.
 */
class JXImagesTest {
    @TempDir
    Path dir;

    @BeforeEach
    @AfterEach
    void freshCache() {
        JXImages.clear();
        JXImages.setCacheLimits(64, 64L << 20);
    }

    private static byte[] bytes(String resource) throws IOException {
        InputStream in = JXImagesTest.class.getResourceAsStream("/images/" + resource);
        assertNotNull(in, resource);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[512];
            int n;
            while ((n = in.read(chunk)) > 0) {
                out.write(chunk, 0, n);
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    private static Path copy(Path dir, String resource, String as) throws IOException {
        Path out = dir.resolve(as);
        Files.write(out, bytes(resource));
        return out;
    }

    /** True if each channel of the pixel is within {@code tolerance} of the expected ARGB. */
    private static boolean near(int expected, int actual, int tolerance) {
        for (int shift = 0; shift < 32; shift += 8) {
            if (Math.abs(((expected >>> shift) & 0xFF) - ((actual >>> shift) & 0xFF)) > tolerance) {
                return false;
            }
        }
        return true;
    }

    // ---- formats -----------------------------------------------------------------------------

    @Test
    void aPngKeepsItsSizeItsColorsAndItsAlphaNotPremultiplied() throws IOException {
        JXImage image = JXImages.load(copy(dir, "pixels4x2.png", "a.png"));
        assertEquals(4, image.getWidth());
        assertEquals(2, image.getHeight());
        assertEquals(8, image.getPixels().length);
        int[] expected = {0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0x80FFFFFF, 0xFF000000, 0xFFFFFF00, 0xFF00FFFF, 0x00000000};
        for (int i = 0; i < 7; i++) {
            assertEquals(expected[i], image.getPixels()[i], "pixel " + i);
        }
        assertEquals(0, image.getPixels()[7] >>> 24, "a transparent pixel stays transparent");
    }

    @Test
    void aJpegDecodesToItsColorsWithinTheLossyTolerance() throws IOException {
        JXImage image = JXImages.load(bytes("halves16x8.jpg"));
        assertEquals(16, image.getWidth());
        assertEquals(8, image.getHeight());
        assertTrue(near(0xFFFF0000, image.getPixels()[2 * 16 + 2], 40), Integer.toHexString(image.getPixels()[2 * 16 + 2]));
        assertTrue(near(0xFF0000FF, image.getPixels()[5 * 16 + 13], 40), Integer.toHexString(image.getPixels()[5 * 16 + 13]));
        assertEquals(0xFF, image.getPixels()[0] >>> 24, "a JPEG is opaque");
    }

    @Test
    void aBmpAndTheFirstFrameOfAGifDecodeToo() throws IOException {
        JXImage bmp = JXImages.load(new ByteArrayInputStream(bytes("rgb3x1.bmp")));
        assertEquals(3, bmp.getWidth());
        assertEquals(1, bmp.getHeight());
        assertEquals(0xFFFF0000, bmp.getPixels()[0]);
        assertEquals(0xFF00FF00, bmp.getPixels()[1]);
        assertEquals(0xFF0000FF, bmp.getPixels()[2]);
        JXImage gif = JXImages.load(bytes("red2x2.gif"));
        assertEquals(2, gif.getWidth());
        assertEquals(0xFFFF0000, gif.getPixels()[3]);
    }

    // ---- ways in -----------------------------------------------------------------------------

    @Test
    void aResourceIsFoundAbsoluteOrRelativeToAClass() throws IOException {
        JXImage absolute = JXImages.loadResource(JXImagesTest.class, "/images/pixels4x2.png");
        assertEquals(4, absolute.getWidth());
        // relative to com/jxparallel/ui/resource/: nothing there
        assertThrows(IOException.class, () -> JXImages.loadResource(JXImagesTest.class, "pixels4x2.png"));
        JXImage byLoader = JXImages.loadResource(JXImagesTest.class.getClassLoader(), "images/pixels4x2.png");
        assertSame(absolute, byLoader, "the same resource by either way is one cached picture");
    }

    @Test
    void aSourceStringIsAResourceThenAFile() throws IOException {
        JXImage resource = JXImages.load("images/pixels4x2.png");
        assertEquals(4, resource.getWidth());
        assertSame(resource, JXImages.load("images/pixels4x2.png"));
        assertEquals(4, JXImages.load("classpath:images/pixels4x2.png").getWidth());
        Path file = copy(dir, "halves16x8.jpg", "only-a-file.jpg");
        assertEquals(16, JXImages.load(file.toString()).getWidth());
        assertEquals(16, JXImages.load("file:" + file).getWidth());
    }

    // ---- cache -------------------------------------------------------------------------------

    @Test
    void theSameFileIsDecodedOnceAndGivesTheSamePixelArray() throws IOException {
        Path file = copy(dir, "pixels4x2.png", "once.png");
        JXImage first = JXImages.load(file);
        assertSame(first, JXImages.load(file));
        assertSame(first, JXImages.load(file.toAbsolutePath().normalize()));
        assertSame(first.getPixels(), JXImages.load(file).getPixels());
        assertEquals(1, JXImages.cacheSize());
        assertEquals(32, JXImages.cacheBytes());
        // a stream is never cached
        assertNotSame(first, JXImages.load(Files.newInputStream(file)));
        assertEquals(1, JXImages.cacheSize());
    }

    @Test
    void invalidatingMakesTheNextLoadReadTheFileAgain() throws IOException {
        Path file = copy(dir, "pixels4x2.png", "again.png");
        JXImage first = JXImages.load(file);
        Files.write(file, bytes("halves16x8.jpg"));
        assertSame(first, JXImages.load(file), "the cache does not watch the file");
        JXImages.invalidate("file:" + file.toAbsolutePath().normalize());
        assertEquals(16, JXImages.load(file).getWidth());
    }

    @Test
    void theCacheIsBoundedByCountAndByBytesAndDropsTheLeastRecentlyUsed() throws IOException {
        JXImages.setCacheLimits(2, 1L << 20);
        Path a = copy(dir, "pixels4x2.png", "a.png");
        Path b = copy(dir, "pixels4x2.png", "b.png");
        Path c = copy(dir, "pixels4x2.png", "c.png");
        JXImage ia = JXImages.load(a);
        JXImages.load(b);
        assertSame(ia, JXImages.load(a)); // a is now the most recent
        JXImages.load(c); // drops b
        assertEquals(2, JXImages.cacheSize());
        assertSame(ia, JXImages.load(a));
        JXImage reloaded = JXImages.load(b);
        assertEquals(2, JXImages.cacheSize());
        assertEquals(4, reloaded.getWidth());
        // 16x8 = 512 bytes of pixels does not fit in 100 bytes: it is returned but not kept
        JXImages.setCacheLimits(10, 100);
        JXImages.clear();
        JXImage big = JXImages.load(copy(dir, "halves16x8.jpg", "big.jpg"));
        assertEquals(16, big.getWidth());
        assertEquals(0, JXImages.cacheSize());
    }

    @Test
    void anyThreadMayLoadAndTheyAllGetTheSameArray() throws Exception {
        final Path file = copy(dir, "halves16x8.jpg", "threads.jpg");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<JXImage>> results = new ArrayList<Future<JXImage>>();
            for (int i = 0; i < 32; i++) {
                results.add(pool.submit(new Callable<JXImage>() {
                    @Override
                    public JXImage call() throws Exception {
                        return JXImages.load(file);
                    }
                }));
            }
            JXImage first = results.get(0).get();
            for (Future<JXImage> result : results) {
                assertSame(first, result.get());
            }
            assertSame(first, JXImages.preload("file:" + file.toAbsolutePath().normalize()).get());
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- failures ----------------------------------------------------------------------------

    private static String captureErr(Runnable action) {
        PrintStream old = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setErr(new PrintStream(out, true));
        try {
            action.run();
        } finally {
            System.setErr(old);
        }
        return new String(out.toByteArray());
    }

    @Test
    void aMissingFileThrowsFromLoadAndIsNullAndReportedOnceFromFind() {
        final String missing = dir.resolve("nope.png").toString();
        assertThrows(IOException.class, () -> JXImages.load(missing));
        assertThrows(IOException.class, () -> JXImages.load(dir.resolve("nope.png")));
        String first = captureErr(() -> assertNull(JXImages.find(missing)));
        assertTrue(first.startsWith("JXParallel: cannot load image "), first);
        assertTrue(first.contains("nope.png"), first);
        assertEquals("", captureErr(() -> assertNull(JXImages.find(missing))), "a failure is reported once, not every frame");
        assertNull(JXImages.find(null));
        assertNull(JXImages.find("  "));
    }

    @Test
    void aFileThatAppearsAfterAFailureIsLoadedOnceTheFailureIsInvalidated() throws IOException {
        Path later = dir.resolve("later.png");
        captureErr(() -> assertNull(JXImages.find(later.toString())));
        Files.write(later, bytes("pixels4x2.png"));
        assertNull(JXImages.find(later.toString()), "the failure is remembered");
        JXImages.invalidate(later.toString());
        assertNotNull(JXImages.find(later.toString()));
    }

    @Test
    void aCorruptOrEmptyFileIsAnErrorNotACrash() throws IOException {
        byte[] png = bytes("pixels4x2.png");
        byte[] cut = java.util.Arrays.copyOf(png, 20); // the header and then nothing
        assertThrows(IOException.class, () -> JXImages.load(cut));
        assertThrows(IOException.class, () -> JXImages.load(new byte[0]));
        assertThrows(IOException.class, () -> JXImages.load("this is text, not a picture".getBytes()));
        Path garbage = dir.resolve("garbage.png");
        Files.write(garbage, "GARBAGE".getBytes());
        IOException e = assertThrows(IOException.class, () -> JXImages.load(garbage));
        assertTrue(e.getMessage().contains("not a picture"), e.getMessage());
        String err = captureErr(() -> assertNull(JXImages.find(garbage.toString())));
        assertTrue(err.contains("garbage.png"), err);
        assertFalse(err.isEmpty());
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> JXImages.load((Path) null));
        assertThrows(IllegalArgumentException.class, () -> JXImages.load((InputStream) null));
        assertThrows(IllegalArgumentException.class, () -> JXImages.load((byte[]) null));
        assertThrows(IllegalArgumentException.class, () -> JXImages.load((String) null));
    }
}
