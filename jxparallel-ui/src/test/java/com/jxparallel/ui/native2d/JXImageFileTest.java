package com.jxparallel.ui.native2d;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;
import com.jxparallel.ui.resource.JXImages;

/**
 * {@code Image} with a {@code src} instead of pixels: the size comes from the file, fitting works on that size,
 * the picture reaches the painter as the pixel array of the cache, and a file that cannot be read draws nothing.
 */
class JXImageFileTest {
    private static final String PNG = "images/pixels4x2.png"; // 4 x 2
    private static final String JPG = "images/halves16x8.jpg"; // 16 x 8

    @BeforeEach
    @AfterEach
    void freshCache() {
        JXImages.clear();
    }

    private static JXProps.Builder p() {
        return JXProps.builder();
    }

    private static JXNativeNode mount(JXProps.Builder props) {
        JXNativeNode node = JXNativeNode.createBackendNode(JXElement.of("image", props.build()));
        node.layoutForBackend(200, 100);
        return node;
    }

    @Test
    void withoutFitAttributesTheNodeIsAsBigAsTheFile() {
        assertArrayEquals(new double[] {4, 2}, JXControlLayout.imageSize(mount(p().set("src", PNG))), 0.001);
        assertArrayEquals(new double[] {16, 8}, JXControlLayout.imageSize(mount(p().set("src", JPG))), 0.001);
        JXNativeNode node = mount(p().set("src", JPG));
        assertEquals(16, node.prefWidth(), 0.001);
        assertEquals(8, node.prefHeight(), 0.001);
    }

    @Test
    void fitWidthFitHeightAndPreserveRatioWorkOnTheSizeOfTheFile() {
        assertArrayEquals(new double[] {32, 16}, JXControlLayout.imageSize(
                mount(p().set("src", JPG).set("fitWidth", 32.0).set("preserveRatio", true))), 0.001);
        assertArrayEquals(new double[] {8, 4}, JXControlLayout.imageSize(
                mount(p().set("src", JPG).set("fitHeight", 4.0).set("preserveRatio", true))), 0.001);
        assertArrayEquals(new double[] {40, 8}, JXControlLayout.imageSize(
                mount(p().set("src", JPG).set("fitWidth", 40.0))), 0.001, "without preserveRatio only the given side changes");
        assertArrayEquals(new double[] {10, 5}, JXControlLayout.imageSize(
                mount(p().set("src", JPG).set("fitWidth", 10.0).set("fitHeight", 10.0).set("preserveRatio", true))), 0.001);
    }

    @Test
    void thePainterGetsTheCachedPixelArrayOfTheFile() {
        JXNativeNode node = mount(p().set("src", PNG).set("fitWidth", 40.0).set("fitHeight", 20.0));
        final int[][] seen = new int[1][];
        final int[] size = new int[2];
        JXPaintRecordingTest.RecordingPainter painter = new JXPaintRecordingTest.RecordingPainter() {
            @Override
            public void image(int[] pixels, int imageWidth, int imageHeight, float x, float y, float w, float h) {
                seen[0] = pixels;
                size[0] = imageWidth;
                size[1] = imageHeight;
                super.image(pixels, imageWidth, imageHeight, x, y, w, h);
            }
        };
        new JXPaint(painter).paint(node);
        new JXPaint(painter).paint(node);
        assertEquals(4, size[0]);
        assertEquals(2, size[1]);
        assertTrue(seen[0] == JXImages.find(PNG).getPixels(), "every frame draws the same array, so the renderers upload it once");
        assertTrue(painter.log.toString().contains("image 8 4 2 "), painter.log.toString());
    }

    @Test
    void theSoftwarePainterDrawsTheFile() {
        // 4x2 scaled by 4: the middle of each source pixel is far from the blended edges
        JXNativeNode node = JXNativeNode.createBackendNode(JXElement.of("image",
                p().set("src", PNG).set("fitWidth", 16.0).set("fitHeight", 8.0).build()));
        node.layoutForBackend(16, 8);
        JXSoftwareFrame frame = new JXSoftwareFrame(false);
        frame.paint(node, 16, 8, 1f);
        int[] px = java.util.Arrays.copyOf(frame.pixels(), 16 * 8);
        // the software painter filters bilinearly, so a color is close to its source, not equal
        assertTrue(dominant(px[2 * 16 + 2], 16), "red " + Integer.toHexString(px[2 * 16 + 2]));
        assertTrue(dominant(px[2 * 16 + 6], 8), "green " + Integer.toHexString(px[2 * 16 + 6]));
        assertTrue(dominant(px[2 * 16 + 10], 0), "blue " + Integer.toHexString(px[2 * 16 + 10]));
        assertTrue((px[6 * 16 + 2] & 0xFFFFFF) < 0x303030, "black " + Integer.toHexString(px[6 * 16 + 2]));
    }

    /** True if the channel at this shift is above 0xA0 and the other two are below 0x70. */
    private static boolean dominant(int argb, int shift) {
        for (int s = 0; s <= 16; s += 8) {
            int channel = (argb >>> s) & 0xFF;
            if (s == shift ? channel < 0xA0 : channel > 0x70) {
                return false;
            }
        }
        return true;
    }

    @Test
    void pixelsOfTheNodeWinOverSrc() {
        int[] red = new int[] {0xFFFF0000, 0xFFFF0000, 0xFFFF0000, 0xFFFF0000};
        JXNativeNode node = mount(p().set("src", PNG).set("pixels", red).set("imageWidth", 2.0).set("imageHeight", 2.0));
        assertArrayEquals(new double[] {2, 2}, JXControlLayout.imageSize(node), 0.001);
    }

    @Test
    void aMissingOrCorruptFileDrawsNothingAndTheScreenStillLaysOut() {
        PrintStream old = System.err;
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setErr(new PrintStream(err, true));
        try {
            JXElement screen = JXElement.of("row", JXProps.empty(),
                    JXElement.of("image", p().set("src", "images/missing.png").build()),
                    JXElement.of("image", p().set("src", "images/missing.png").set("fitWidth", 9.0).set("fitHeight", 7.0).build()),
                    JXElement.of("image", p().set("src", "").build()),
                    JXElement.of("button", p().set("label", "next").build()));
            JXNativeNode root = JXNativeNode.createBackendNode(screen);
            root.layoutForBackend(300, 100);
            JXPaintRecordingTest.RecordingPainter painter = new JXPaintRecordingTest.RecordingPainter();
            new JXPaint(painter).paint(root);
            new JXPaint(painter).paint(root);
            assertFalse(painter.log.toString().contains("image "), painter.log.toString());
            assertEquals(0, root.getChildren().get(0).prefWidth(), 0.001);
            assertEquals(9, root.getChildren().get(1).prefWidth(), 0.001, "the fit size the screen asked for is kept");
            String text = new String(err.toByteArray());
            assertTrue(text.startsWith("JXParallel: cannot load image images/missing.png"), text);
            assertEquals(1, text.split("\n").length, "reported once, however many frames and nodes");
        } finally {
            System.setErr(old);
        }
    }

    @Test
    void changingSrcInPlaceRelaysOutLikeMountingAgain() {
        JXNativeNode updated = JXNativeNode.createBackendNode(row(PNG));
        updated.layoutForBackend(300, 100);
        updated.reconcile(row(JPG));
        updated.layoutForBackend(300, 100);
        JXNativeNode fresh = JXNativeNode.createBackendNode(row(JPG));
        fresh.layoutForBackend(300, 100);
        assertEquals(16, updated.getChildren().get(0).prefWidth(), 0.001);
        for (int i = 0; i < 2; i++) {
            assertEquals(fresh.getChildren().get(i).getX(), updated.getChildren().get(i).getX());
            assertEquals(fresh.getChildren().get(i).getWidth(), updated.getChildren().get(i).getWidth());
        }
    }

    private static JXElement row(String src) {
        return JXElement.of("row", JXProps.empty(),
                JXElement.of("image", p().set("src", src).build()),
                JXElement.of("button", p().set("label", "next").build()));
    }
}
