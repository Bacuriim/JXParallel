package com.jxparallel.ui.native2d;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;
import com.jxparallel.ui.controls.JXControls;
import com.jxparallel.ui.layout.JXLayouts;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.EncoderPNG;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Snapshot tests of one screen with every control. The layout snapshot is plain text and runs
 * everywhere. The Skia image is rendered to a CPU raster (no GPU, no window) and compared with a
 * golden PNG; fonts differ between systems, so it runs on 64-bit Windows only.
 * A missing snapshot is written to src/test/resources and the test fails once so it gets reviewed.
 * A mismatch writes the new version to target/snapshots.
 */
class JXSnapshotTest {
    private static final Path GOLDEN = Paths.get("src/test/resources/snapshots");
    private static final Path ACTUAL = Paths.get("target/snapshots");
    private static final int WIDTH = 480;
    private static final int HEIGHT = 360;
    /** Per channel. Anti-aliased edges may move by a few levels between Skia builds. */
    private static final int CHANNEL_TOLERANCE = 8;
    private static final double MAX_DIFFERENT_PIXELS = 0.002;

    static JXElement showcase() {
        return JXLayouts.column(8,
                JXElement.text("Customers"),
                JXLayouts.row(8, JXControls.button("Load", null), JXControls.button("Save", null)),
                JXControls.input("Ada Lovelace", "Name"),
                JXControls.checkbox("Active", true),
                JXControls.checkbox("Archived", false),
                JXControls.select("Fortaleza", "Fortaleza", "Recife"),
                JXElement.of("progress", JXProps.builder().set("progress", 0.4).build()),
                JXElement.of("slider", JXProps.builder().set("value", 75.0).build()));
    }

    @Test
    void layoutMatchesSnapshot() throws IOException {
        JXNativeNode root = JXNativeNode.createBackendNode(showcase());
        root.layoutForBackend(WIDTH, HEIGHT);
        StringBuilder out = new StringBuilder();
        describe(root, 0, out);

        byte[] actual = out.toString().getBytes("UTF-8");
        byte[] golden = goldenOrCreate("showcase-layout.txt", actual);
        if (!new String(golden, "UTF-8").replace("\r\n", "\n").equals(out.toString())) {
            Files.createDirectories(ACTUAL);
            Files.write(ACTUAL.resolve("showcase-layout.txt"), actual);
            assertEquals(new String(golden, "UTF-8"), out.toString(), "layout changed, new version in " + ACTUAL);
        }
    }

    @Test
    void skiaRasterMatchesGoldenImage() throws IOException {
        assumeTrue(System.getProperty("os.name").startsWith("Windows")
                && "64".equals(System.getProperty("sun.arch.data.model")), "golden image is for 64-bit Windows");
        JXNativeNode root = JXSkiaRenderer.mount(showcase());
        byte[] png;
        byte[] pixels;
        try (Surface surface = Surface.makeRasterN32Premul(WIDTH, HEIGHT)) {
            JXSkiaRenderer.paint(root, surface.getCanvas(), WIDTH, HEIGHT);
            try (Image image = surface.makeImageSnapshot()) {
                png = EncoderPNG.encode(image).getBytes();
                pixels = pixels(image);
            }
        }

        byte[] golden = goldenOrCreate("showcase-skia.png", png);
        byte[] expected;
        try (Image image = Image.makeFromEncoded(golden)) {
            assertEquals(WIDTH, image.getWidth());
            expected = pixels(image);
        }
        int different = 0;
        for (int i = 0; i < pixels.length; i += 4) {
            for (int c = 0; c < 4; c++) {
                if (Math.abs((pixels[i + c] & 0xFF) - (expected[i + c] & 0xFF)) > CHANNEL_TOLERANCE) {
                    different++;
                    break;
                }
            }
        }
        double ratio = different / (double) (WIDTH * HEIGHT);
        if (ratio > MAX_DIFFERENT_PIXELS) {
            Files.createDirectories(ACTUAL);
            Files.write(ACTUAL.resolve("showcase-skia.png"), png);
            fail(String.format("%d pixels (%.3f%%) differ from the golden image, new render in %s",
                    different, ratio * 100, ACTUAL));
        }
    }

    private static byte[] pixels(Image image) {
        try (Bitmap bitmap = new Bitmap()) {
            assertTrue(bitmap.allocN32Pixels(image.getWidth(), image.getHeight()));
            assertTrue(image.readPixels(bitmap));
            return bitmap.readPixels();
        }
    }

    private static byte[] goldenOrCreate(String name, byte[] actual) throws IOException {
        Path file = GOLDEN.resolve(name);
        if (Files.exists(file)) {
            return Files.readAllBytes(file);
        }
        Files.createDirectories(GOLDEN);
        Files.write(file, actual);
        fail("created snapshot " + file + ", review it and run again");
        return actual;
    }

    private static void describe(JXNativeNode node, int depth, StringBuilder out) {
        for (int i = 0; i < depth; i++) {
            out.append("  ");
        }
        out.append(node.getType()).append(" [").append(node.getX()).append(',').append(node.getY()).append(' ')
                .append(node.getWidth()).append('x').append(node.getHeight()).append("]\n");
        for (JXNativeNode child : node.getChildren()) {
            describe(child, depth + 1, out);
        }
    }
}
