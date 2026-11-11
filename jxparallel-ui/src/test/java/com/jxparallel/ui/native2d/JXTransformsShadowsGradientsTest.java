package com.jxparallel.ui.native2d;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;
import com.jxparallel.ui.native2d.JXPaintRecordingTest.RecordingPainter;

import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Translate, scale and rotate, shadows and gradients: what each painter is asked to draw (recordings), what the software and
 * Skia renderers draw (pixels; NanoVG too where a hidden OpenGL window can be made), and where a transformed node is picked.
 */
class JXTransformsShadowsGradientsTest {
    private static final int SIZE = 200;
    private static final int RED = 0xFFFF0000;
    private static final int BG = JXSkiaRenderer.BACKGROUND;

    private interface Renderer {
        int[] render(JXElement scene);
    }

    private static JXProps.Builder p() {
        return JXProps.builder();
    }

    /** A 40 x 40 box at (60, 60) in a pane, with {@code extra} props on top of a red background. */
    private static JXElement scene(JXProps.Builder extra) {
        JXProps.Builder box = p().set("layoutX", 60.0).set("layoutY", 60.0).set("prefWidth", 40.0).set("prefHeight", 40.0)
                .set("background", RED);
        return JXElement.of("pane", p().build(), JXElement.of("pane", merge(box, extra), new JXElement[0]));
    }

    private static JXProps merge(JXProps.Builder base, JXProps.Builder extra) {
        JXProps built = extra.build();
        for (int i = 0; i < built.size(); i++) {
            base.set(built.keyAt(i), built.valueAt(i));
        }
        return base.build();
    }

    private static final Renderer SOFTWARE = scene -> JXWindow.captureSoftware(scene, SIZE, SIZE);

    private static final Renderer SKIA = scene -> {
        JXNativeNode root = JXNativeNode.createBackendNode(scene);
        root.layoutForBackend(SIZE, SIZE);
        try (Surface surface = Surface.makeRasterN32Premul(SIZE, SIZE)) {
            JXSkiaRenderer.paint(root, surface.getCanvas(), SIZE, SIZE);
            try (Image image = surface.makeImageSnapshot(); Bitmap bitmap = new Bitmap()) {
                assertTrue(bitmap.allocN32Pixels(SIZE, SIZE));
                assertTrue(image.readPixels(bitmap));
                byte[] bytes = bitmap.readPixels();
                int[] argb = new int[SIZE * SIZE];
                for (int i = 0; i < argb.length; i++) { // BGRA
                    argb[i] = (bytes[4 * i + 3] & 0xFF) << 24 | (bytes[4 * i + 2] & 0xFF) << 16 | (bytes[4 * i + 1] & 0xFF) << 8
                            | (bytes[4 * i] & 0xFF);
                }
                return argb;
            }
        }
    };

    private static final Renderer NANOVG = scene -> JXWindow.captureNanoVG(scene, SIZE, SIZE);

    private static boolean skiaAvailable() {
        try {
            SKIA.render(scene(p()));
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private static boolean nanoVgAvailable() {
        try {
            return NANOVG.render(scene(p()))[0] != 0;
        } catch (Throwable e) {
            return false;
        }
    }

    private static int at(int[] px, int x, int y) {
        return px[y * SIZE + x] | 0xFF000000;
    }

    private static boolean red(int[] px, int x, int y) {
        int c = at(px, x, y);
        return ((c >> 16) & 0xFF) > 200 && ((c >> 8) & 0xFF) < 60 && (c & 0xFF) < 60;
    }

    private static boolean background(int[] px, int x, int y) {
        return at(px, x, y) == (BG | 0xFF000000);
    }

    private static int channel(int[] px, int x, int y, int shift) {
        return (at(px, x, y) >> shift) & 0xFF;
    }

    // ---- what the painters are asked to draw -------------------------------------------------------------

    private static String record(JXElement scene) {
        JXNativeNode root = JXNativeNode.createBackendNode(scene);
        root.layoutForBackend(SIZE, SIZE);
        RecordingPainter painter = new RecordingPainter();
        new JXPaint(painter).paint(root);
        return painter.log.toString();
    }

    @Test
    void aNodeWithoutATransformPushesNone() {
        String log = record(scene(p()));
        assertFalse(log.contains("transform"), log);
        assertFalse(log.contains("shadow"), log);
        assertFalse(log.contains("gradientStops"), log);
    }

    @Test
    void aTransformIsPushedAroundTheNodeAndItsSubtreeAboutItsCentre() {
        String log = record(scene(p().set("translateX", 5.0).set("scaleX", 2.0).set("rotate", 30.0)));
        // the box is at 60,60 and 40 wide: the centre is 80,80
        int at = log.indexOf("transform 5.00 0.00 2.00 1.00 30.00 80.00 80.00");
        assertTrue(at >= 0, log);
        int fill = log.indexOf("fillRoundRect 60.00 60.00 40.00 40.00");
        assertTrue(fill > at, "the box is drawn inside the transform: " + log);
        assertTrue(log.indexOf("restore", fill) > fill, log);
    }

    @Test
    void aShadowIsDrawnBeforeTheBoxOnlyWhenThereIsAnElevation() {
        String log = record(scene(p().set("elevation", 8.0).set("backgroundRadius", 4.0)));
        int shadow = log.indexOf("shadow 60.00 60.00 40.00 40.00 4.00 0.00 4.00 16.00 40000000");
        assertTrue(shadow >= 0, log);
        assertTrue(log.indexOf("fillRoundRect 60.00 60.00") > shadow, log);
        String custom = record(scene(p().set("elevation", 6.0).set("shadowColor", 0x80FF0000).set("shadowOffsetX", 2.0)
                .set("shadowOffsetY", 3.0)));
        assertTrue(custom.contains("shadow 60.00 60.00 40.00 40.00 0.00 2.00 3.00 12.00 80ff0000"), custom);
        assertFalse(record(scene(p().set("elevation", 0.0))).contains("shadow"));
    }

    @Test
    void aGradientReplacesTheBackgroundAndKeepsEveryStop() {
        String log = record(scene(p().set("backgroundGradient", "linear-gradient(to right, #f00, #0f0 30%, #00f)")));
        assertTrue(log.contains("gradientStops 60.00 60.00 40.00 40.00 0.00 linear[90.0deg 0.0% 0.0% 0.0% 100.0%] "
                + "0.00:ffff0000 0.30:ff00ff00 1.00:ff0000ff"), log);
        assertFalse(log.contains("fillRoundRect 60.00 60.00"), "the gradient is painted instead of the background: " + log);
        assertTrue(record(scene(p().set("backgroundGradient", "nonsense"))).contains("fillRoundRect 60.00 60.00"),
                "a text that is no gradient leaves the background");
    }

    @Test
    void theGradientTextIsParsedWithAllItsStops() {
        JXGradient g = JXGradient.parse("linear-gradient(from 0% 0% to 100% 100%, #fff 0%, rgba(0,0,0,0.5), #00f 80%, #000)");
        assertNotNull(g);
        assertEquals(4, g.colors.length);
        assertEquals(0f, g.offsets[0], 1e-6);
        assertEquals(0.4f, g.offsets[1], 1e-6); // between 0 and 0.8
        assertEquals(0.8f, g.offsets[2], 1e-6);
        assertEquals(1f, g.offsets[3], 1e-6);
        assertEquals(0x80000000, g.colors[1]);
        assertEquals(0xFFFFFFFF, g.colorAt(0f));
        assertEquals(0x80000000, g.colorAt(0.4f));
        JXGradient simple = JXGradient.parse("linear-gradient(#f2f2f2, #e4e4e4)");
        float[] axis = simple.axis(10, 20, 30, 40);
        assertEquals(10f, axis[0], 1e-4);
        assertEquals(20f, axis[1], 1e-4);
        assertEquals(10f, axis[2], 1e-4);
        assertEquals(60f, axis[3], 1e-4); // top to bottom, like JavaFX's default
        JXGradient radial = JXGradient.parse("radial-gradient(center 50% 50%, radius 100%, white, black)");
        assertTrue(radial.radial);
        float[] circle = radial.circle(0, 0, 100, 60);
        assertEquals(50f, circle[0], 1e-4);
        assertEquals(30f, circle[1], 1e-4);
        assertEquals(60f, circle[2], 1e-4);
        assertNull(JXGradient.parse("linear-gradient(#fff)"));
        assertNull(JXGradient.parse("#fff"));
        assertNull(JXGradient.parse("linear-gradient(nope, #fff)"));
    }

    // ---- hit-testing -------------------------------------------------------------------------------------

    private static JXNativeNode clickable(JXProps.Builder extra, AtomicInteger clicks) {
        JXProps.Builder box = p().set("layoutX", 60.0).set("layoutY", 60.0).set("prefWidth", 40.0).set("prefHeight", 40.0)
                .set("onClick", (Runnable) clicks::incrementAndGet);
        JXNativeNode root = JXNativeNode.createBackendNode(
                JXElement.of("pane", p().build(), JXElement.of("pane", merge(box, extra), new JXElement[0])));
        root.layoutForBackend(SIZE, SIZE);
        return root;
    }

    private static void click(JXNativeNode root, int x, int y) {
        JXNativeNode hit = root.hitTest(x, y);
        if (hit != null) {
            hit.dispatchPointer(hit.localize(new JXPointerEvent(x, y, 0)));
        }
    }

    @Test
    void aMovedNodeIsPickedWhereItIsDrawn() {
        AtomicInteger clicks = new AtomicInteger();
        JXNativeNode root = clickable(p().set("translateX", 60.0), clicks);
        JXNativeNode box = root.getChildren().get(0);
        assertEquals(60, box.getX(), "the layout bounds do not move");
        assertSame(root, root.hitTest(80, 80), "nothing is where the box was laid out");
        assertSame(box, root.hitTest(140, 80));
        click(root, 80, 80);
        assertEquals(0, clicks.get());
        click(root, 140, 80);
        assertEquals(1, clicks.get());
    }

    @Test
    void aScaledNodeIsPickedOverTheWholeScaledArea() {
        AtomicInteger clicks = new AtomicInteger();
        JXNativeNode root = clickable(p().set("scaleX", 2.0).set("scaleY", 2.0), clicks);
        JXNativeNode box = root.getChildren().get(0); // 60..100 drawn as 40..120
        assertSame(box, root.hitTest(45, 45));
        assertSame(box, root.hitTest(115, 115));
        assertSame(root, root.hitTest(35, 80));
        assertSame(root, root.hitTest(125, 80));
        JXNativeNode flat = clickable(p().set("scaleX", 0.0), clicks); // a collapsed node is not there at all
        assertSame(flat, flat.hitTest(80, 80));
    }

    @Test
    void aRotatedNodeIsPickedOnItsRotatedShape() {
        AtomicInteger clicks = new AtomicInteger();
        // 100 x 20 at (50, 90): centre (100, 100); a quarter turn makes it 20 wide and 100 tall
        JXProps.Builder box = p().set("layoutX", 50.0).set("layoutY", 90.0).set("prefWidth", 100.0).set("prefHeight", 20.0)
                .set("rotate", 90.0).set("onClick", (Runnable) clicks::incrementAndGet);
        JXNativeNode root = JXNativeNode.createBackendNode(JXElement.of("pane", p().build(), JXElement.of("pane", box.build(), new JXElement[0])));
        root.layoutForBackend(SIZE, SIZE);
        JXNativeNode node = root.getChildren().get(0);
        assertSame(node, root.hitTest(100, 55), "the top end of the turned bar");
        assertSame(node, root.hitTest(100, 145));
        assertSame(root, root.hitTest(55, 100), "where the left end was before turning");
        assertSame(root, root.hitTest(145, 100));
        // the event the node's handler gets is in the node's own space: the point (100, 55) was the centre's left end
        JXPointerEvent local = node.localize(new JXPointerEvent(100, 55, 0));
        assertEquals(55, local.getX());
        assertEquals(100, local.getY());
    }

    @Test
    void theChildrenOfATransformedNodeMoveWithIt() {
        AtomicInteger clicks = new AtomicInteger();
        JXProps.Builder inner = p().set("layoutX", 5.0).set("layoutY", 5.0).set("prefWidth", 10.0).set("prefHeight", 10.0)
                .set("onClick", (Runnable) clicks::incrementAndGet);
        JXElement outer = JXElement.of("pane", p().set("layoutX", 60.0).set("layoutY", 60.0).set("prefWidth", 40.0)
                .set("prefHeight", 40.0).set("translateY", 50.0).build(), JXElement.of("pane", inner.build(), new JXElement[0]));
        JXNativeNode root = JXNativeNode.createBackendNode(JXElement.of("pane", p().build(), outer));
        root.layoutForBackend(SIZE, SIZE);
        JXNativeNode child = root.getChildren().get(0).getChildren().get(0);
        assertSame(child, root.hitTest(70, 120)); // laid out at 65..75 x 65..75, drawn 50 lower
        assertFalse(root.hitTest(70, 70) == child);
        click(root, 70, 120);
        assertEquals(1, clicks.get());
    }

    @Test
    void anUntransformedTreeIsPickedAsBefore() {
        AtomicInteger clicks = new AtomicInteger();
        JXNativeNode root = clickable(p(), clicks);
        assertSame(root.getChildren().get(0), root.hitTest(80, 80));
        click(root, 80, 80);
        assertEquals(1, clicks.get());
    }

    @Test
    void aTransformedNodeBelowTheWindowIsStillDrawnWhenItMovesIn() {
        // laid out far below the window, moved up into it
        JXProps.Builder box = p().set("layoutX", 60.0).set("layoutY", 900.0).set("prefWidth", 40.0).set("prefHeight", 40.0)
                .set("background", RED).set("translateY", -800.0);
        JXElement scene = JXElement.of("pane", p().build(), JXElement.of("pane", box.build(), new JXElement[0]));
        assertTrue(red(SOFTWARE.render(scene), 80, 120));
        JXElement still = JXElement.of("pane", p().build(), JXElement.of("pane", box.set("translateY", 0.0).build(), new JXElement[0]));
        assertTrue(background(SOFTWARE.render(still), 80, 120));
    }

    // ---- pixels ------------------------------------------------------------------------------------------

    private static void movesAndScales(Renderer r) {
        int[] moved = r.render(scene(p().set("translateX", 60.0)));
        assertTrue(red(moved, 140, 80), "drawn 60 to the right");
        assertTrue(background(moved, 80, 80), "and no longer where it was laid out");

        int[] scaled = r.render(scene(p().set("scaleX", 2.0).set("scaleY", 2.0)));
        assertTrue(red(scaled, 45, 80) && red(scaled, 115, 80) && red(scaled, 80, 45) && red(scaled, 80, 115), "twice the size, about the centre");
        assertTrue(background(scaled, 35, 80) && background(scaled, 125, 80), "and not more");

        int[] plain = r.render(scene(p()));
        assertTrue(red(plain, 61, 61) && red(plain, 98, 98));
        assertTrue(background(plain, 45, 80) && background(plain, 115, 80));
    }

    private static void shadowsAndGradients(Renderer r) {
        int[] without = r.render(scene(p().set("background", 0xFFFFFFFF)));
        int[] with = r.render(scene(p().set("background", 0xFFFFFFFF).set("elevation", 8.0)));
        assertTrue(background(without, 80, 108));
        int below = channel(with, 80, 108, 0);
        assertTrue(below < channel(without, 80, 108, 0) - 8, "darker below the box: " + below);
        assertTrue(channel(with, 80, 104, 0) < channel(with, 80, 118, 0), "fainter further away");
        assertEquals(0xFFFFFFFF, at(with, 80, 80), "the box itself is as it was");
        assertTrue(background(with, 5, 5), "far from the box nothing changes");
        assertTrue(channel(with, 80, 108, 0) < channel(with, 80, 52, 0), "the shadow is lower than the box: more below than above");

        int[] linear = r.render(scene(p().set("backgroundGradient", "linear-gradient(to right, #ff0000, #00ff00, #0000ff)")));
        assertTrue(channel(linear, 62, 80, 16) > 200 && channel(linear, 62, 80, 0) < 80, "red at the left");
        assertTrue(channel(linear, 80, 80, 8) > 200 && channel(linear, 80, 80, 16) < 80 && channel(linear, 80, 80, 0) < 80, "green in the middle: a stop that is not the first");
        assertTrue(channel(linear, 97, 80, 0) > 200 && channel(linear, 97, 80, 16) < 80, "blue at the right");

        int[] radial = r.render(scene(p().set("backgroundGradient", "radial-gradient(center 50% 50%, radius 50%, #ffffff, #000000)")));
        assertTrue(channel(radial, 80, 80, 8) > 230, "white in the centre");
        assertTrue(channel(radial, 61, 80, 8) < 60, "dark at the edge");
        assertTrue(channel(radial, 70, 80, 8) < channel(radial, 75, 80, 8), "darker further out");
    }

    @Test
    void softwareMovesAndScalesAndLeavesTheRotationOut() {
        movesAndScales(SOFTWARE);
        int[] turned = SOFTWARE.render(scene(p().set("rotate", 45.0)));
        assertTrue(red(turned, 61, 61), "no rotation in the software renderer: drawn unrotated");
        int[] unevenly = SOFTWARE.render(scene(p().set("scaleX", 2.0)));
        assertTrue(red(unevenly, 70, 61) && background(unevenly, 45, 80), "a scale that differs on x and y is left out there");
    }

    @Test
    void softwareDrawsShadowsAndGradients() {
        shadowsAndGradients(SOFTWARE);
    }

    @Test
    void softwareAShiftedFrameRepaintsOnlyTheDamageAndGivesTheSamePixels() {
        JXSoftwareFrame frame = new JXSoftwareFrame(true);
        JXNativeNode a = JXNativeNode.createBackendNode(scene(p().set("translateX", 10.0)));
        a.layoutForBackend(SIZE, SIZE);
        frame.paint(a, SIZE, SIZE, 1f);
        a.reconcile(scene(p().set("translateX", 70.0)));
        a.layoutForBackend(SIZE, SIZE);
        frame.paint(a, SIZE, SIZE, 1f);
        int[] expected = SOFTWARE.render(scene(p().set("translateX", 70.0)));
        assertTrue(java.util.Arrays.equals(expected, java.util.Arrays.copyOf(frame.pixels(), SIZE * SIZE)),
                "the damage painter follows the transform: the incremental frame equals a whole repaint");
    }

    @Test
    void skiaMovesScalesAndRotates() {
        assumeTrue(skiaAvailable(), "Skia is not available here");
        movesAndScales(SKIA);
        int[] turned = SKIA.render(scene(p().set("rotate", 45.0)));
        assertTrue(background(turned, 61, 61), "the corner moved away");
        assertTrue(red(turned, 80, 56), "the top corner of the diamond");
        assertTrue(red(turned, 80, 80));
        int[] quarter = SKIA.render(scene(p().set("rotate", 90.0).set("prefWidth", 80.0).set("layoutX", 40.0)));
        assertTrue(red(quarter, 80, 45) && red(quarter, 80, 115) && background(quarter, 50, 80), "a bar turned upright");
    }

    @Test
    void skiaDrawsShadowsAndGradients() {
        assumeTrue(skiaAvailable(), "Skia is not available here");
        shadowsAndGradients(SKIA);
    }

    @Test
    void nanoVgMovesScalesShadowsAndGradientsWhereAnOpenGlWindowCanBeMade() {
        assumeTrue(nanoVgAvailable(), "no hidden OpenGL window here: the NanoVG path is only compiled");
        movesAndScales(NANOVG);
        shadowsAndGradients(NANOVG);
    }
}
