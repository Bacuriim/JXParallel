package com.jxparallel.ui.native2d;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The software renderer: it paints the controls like the reviewed Skia image (within antialiasing
 * and glyph placement, the blurred text and the smoothed image left out), and repainting only the damage gives exactly the pixels of a whole repaint.
 */
class JXSoftwareRendererTest {
    private static final Path GOLDEN = Paths.get("src/test/resources/snapshots");
    private static final Path ACTUAL = Paths.get("target/snapshots");
    private static final int WIDTH = 760;
    private static final int HEIGHT = 620;

    private static JXProps.Builder p() {
        return JXProps.builder();
    }

    private static JXElement e(String type, JXProps.Builder props, JXElement... children) {
        return JXElement.of(type, props.build(), children);
    }

    @Test
    void theControlsLookLikeTheReviewedSkiaImage() throws IOException {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"), "the golden image uses the Windows UI font");
        JXNativeNode root = JXNativeNode.createBackendNode(JXControlsSnapshotTest.controls());
        root.layoutForBackend(WIDTH, HEIGHT);
        JXSoftwareFrame frame = new JXSoftwareFrame(true);
        frame.paint(root, WIDTH, HEIGHT, 1f);
        int[] actual = frame.pixels();
        BufferedImage golden = ImageIO.read(GOLDEN.resolve("controls-skia.png").toFile());
        assertEquals(WIDTH, golden.getWidth());
        List<int[]> unlike = new ArrayList<int[]>();
        unlikeSkia(root, unlike);
        assertEquals(2, unlike.size(), "the sheet has one blurred text and one smoothed image, and the test must know both");
        int different = 0;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (inside(unlike, x, y)) {
                    continue;
                }
                int a = actual[y * WIDTH + x];
                int b = golden.getRGB(x, y);
                for (int shift = 0; shift <= 16; shift += 8) {
                    if (Math.abs(((a >> shift) & 0xFF) - ((b >> shift) & 0xFF)) > 48) {
                        different++;
                        break;
                    }
                }
            }
        }
        // edges of glyphs differ (whole-pixel glyph positions, FreeType vs Skia rasterization): 1% of the pixels may
        double ratio = different / (double) (WIDTH * HEIGHT);
        if (ratio > 0.01) {
            BufferedImage out = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
            out.setRGB(0, 0, WIDTH, HEIGHT, actual, 0, WIDTH);
            Files.createDirectories(ACTUAL);
            ImageIO.write(out, "png", ACTUAL.resolve("controls-software.png").toFile());
            fail(String.format("%.3f%% of the pixels differ from the Skia image, render in %s", ratio * 100, ACTUAL));
        }
    }

    /**
     * The boxes (x, y, right, bottom) of what Skia draws and the software painter does not: a blurred text (Skia blurs
     * the layer; the software painter has no blur and draws the text sharp, and the blur spreads about three times its
     * radius around the box) and an image (Skia smooths it when it scales it, the software painter takes the nearest
     * pixel). They are left out of the comparison instead of letting the tolerance grow to absorb them.
     */
    static void unlikeSkia(JXNativeNode node, List<int[]> out) {
        double blur = JXPaint.number(node, "blur", 0);
        if (blur > 0) {
            int margin = (int) Math.ceil(3 * blur) + 1;
            out.add(new int[] {node.getX() - margin, node.getY() - margin, node.getX() + node.getWidth() + margin,
                node.getY() + node.getHeight() + margin});
        } else if ("image".equals(node.getType())) {
            out.add(new int[] {node.getX() - 1, node.getY() - 1, node.getX() + node.getWidth() + 1, node.getY() + node.getHeight() + 1});
        }
        for (JXNativeNode child : node.getChildren()) {
            unlikeSkia(child, out);
        }
    }

    static boolean inside(List<int[]> boxes, int x, int y) {
        for (int[] b : boxes) {
            if (x >= b[0] && y >= b[1] && x < b[2] && y < b[3]) {
                return true;
            }
        }
        return false;
    }

    @Test
    void anUnchangedFrameRepaintsNothing() {
        JXNativeNode root = JXNativeNode.createBackendNode(form("Pronto", 1, false, "valor"));
        root.layoutForBackend(400, 300);
        JXSoftwareFrame frame = new JXSoftwareFrame(true);
        assertEquals(1, frame.paint(root, 400, 300, 1f));
        assertArrayEquals(new int[] {0, 0, 400, 300}, java.util.Arrays.copyOf(frame.damaged(), 4));
        assertEquals(0, frame.paint(root, 400, 300, 1f));
    }

    @Test
    void repaintingOnlyTheDamageGivesTheWholeRepaintsPixels() {
        JXElement[] steps = {
                form("Pronto", 1, false, "valor"),
                form("Salvando", 1, false, "valor"),       // one label
                form("Salvando", 2, false, "valor"),       // the selected row moves
                form("Salvando", 2, true, "valor"),        // a check mark
                form("Salvando", 2, true, "valor maior"),  // a longer value: relayout of its row
                form("Pronto", 0, false, ""),              // everything at once
                form("Pronto", 0, false, ""),              // nothing
        };
        JXNativeNode root = JXNativeNode.createBackendNode(steps[0]);
        JXSoftwareFrame incremental = new JXSoftwareFrame(true);
        for (int i = 0; i < steps.length; i++) {
            if (i > 0) {
                assertTrue(root.reconcile(steps[i]));
            }
            root.layoutForBackend(400, 300);
            int rects = incremental.paint(root, 400, 300, 1f);
            JXSoftwareFrame whole = new JXSoftwareFrame(false);
            whole.paint(root, 400, 300, 1f);
            assertArrayEquals(java.util.Arrays.copyOf(whole.pixels(), 400 * 300),
                    java.util.Arrays.copyOf(incremental.pixels(), 400 * 300), "step " + i);
            if (i == 1) {
                long area = 0;
                for (int r = 0; r < rects; r++) {
                    int[] d = incremental.damaged();
                    area += (long) (d[4 * r + 2] - d[4 * r]) * (d[4 * r + 3] - d[4 * r + 1]);
                }
                assertTrue(rects > 0 && area < 400 * 300 / 4, "a new label repaints a small part, not " + area + " pixels");
            }
            if (i == steps.length - 1) {
                assertEquals(0, rects);
            }
        }
    }

    @Test
    void anOpacityChangeOnAPaneRepaintsWhatItContains() {
        JXNativeNode root = JXNativeNode.createBackendNode(faded(1.0));
        root.layoutForBackend(200, 100);
        JXSoftwareFrame incremental = new JXSoftwareFrame(true);
        incremental.paint(root, 200, 100, 1f);
        assertTrue(root.reconcile(faded(0.5)));
        root.layoutForBackend(200, 100);
        assertTrue(incremental.paint(root, 200, 100, 1f) > 0);
        JXSoftwareFrame whole = new JXSoftwareFrame(false);
        whole.paint(root, 200, 100, 1f);
        assertArrayEquals(java.util.Arrays.copyOf(whole.pixels(), 200 * 100), java.util.Arrays.copyOf(incremental.pixels(), 200 * 100));
    }

    @Test
    void blendingIsSourceOverRoundedToTheNearestLevel() {
        int[] levels = {0, 1, 17, 128, 200, 254, 255};
        for (int a = 0; a <= 255; a++) {
            for (int s : levels) {
                for (int d : levels) {
                    int out = JXSoftwarePainter.over(d << 16 | d << 8 | d, s << 16 | s << 8 | s, a);
                    double exact = (s * a + d * (255.0 - a)) / 255.0;
                    for (int shift = 0; shift <= 16; shift += 8) {
                        int c = (out >> shift) & 0xFF;
                        assertTrue(Math.abs(c - exact) <= 0.5 + 1e-9, "a=" + a + " s=" + s + " d=" + d + " gave " + c + ", exact " + exact);
                    }
                    assertEquals(0xFF, out >>> 24);
                }
            }
        }
    }

    private static JXElement faded(double opacity) {
        return e("column", p().set("padding", new double[] {10, 10, 10, 10}),
                e("pane", p().set("opacity", opacity).set("prefWidth", 120.0).set("prefHeight", 40.0).set("background", 0xFF3366CC),
                        e("#text", p().set("value", "dentro"))));
    }

    /** A small form: a status label, a list with one selected row, a check box and a field. */
    private static JXElement form(String status, int selected, boolean checked, String value) {
        JXElement[] cells = new JXElement[4];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = e("cell", p().set("value", "Linha " + i).set("selected", i == selected).set("odd", i % 2 == 1));
        }
        return e("column", p().set("gap", 8.0).set("padding", new double[] {10, 10, 10, 10}),
                e("#text", p().set("value", status)),
                e("list", p().set("itemCount", cells.length).set("prefWidth", 160.0).set("prefHeight", 100.0), cells),
                e("checkbox", p().set("label", "Habilitado").set("selected", checked)),
                e("row", p().set("gap", 6.0), e("#text", p().set("value", "Campo")), e("input", p().set("value", value))));
    }
}
