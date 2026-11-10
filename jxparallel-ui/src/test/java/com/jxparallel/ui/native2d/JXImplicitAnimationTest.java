package com.jxparallel.ui.native2d;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;
import com.jxparallel.ui.native2d.JXPaintRecordingTest.RecordingPainter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Implicit animation on the retained tree, on a clock the test turns by hand: what a node answers while a transition or
 * an enter runs, retargeting, the switch that turns it off, what the window's driver asks for, and where a moving node
 * is picked.
 */
class JXImplicitAnimationTest {
    private static final long MS = 1_000_000L;
    private static final long T0 = 5_000 * MS;
    private static final int SIZE = 200;

    @BeforeEach
    @AfterEach
    void restore() {
        System.clearProperty("jx.animations");
        JXAnimationClock.setSource(null);
    }

    private static JXProps.Builder box() {
        return JXProps.builder().set("layoutX", 60.0).set("layoutY", 60.0).set("prefWidth", 40.0).set("prefHeight", 40.0);
    }

    private static JXElement scene(JXProps.Builder box) {
        return JXElement.of("pane", JXProps.builder().build(), JXElement.of("pane", box.build(), new JXElement[0]));
    }

    private static JXNativeNode mount(JXProps.Builder box) {
        JXNativeNode root = JXNativeNode.createBackendNode(scene(box));
        root.layoutForBackend(SIZE, SIZE);
        return root;
    }

    private static JXNativeNode kid(JXNativeNode root) {
        return root.getChildren().get(0);
    }

    private static void reconcile(JXNativeNode root, JXProps.Builder box) {
        assertTrue(root.reconcile(scene(box)));
        root.layoutForBackend(SIZE, SIZE);
    }

    private static double number(JXNativeNode node, String name) {
        return ((Number) node.getProperty(name)).doubleValue();
    }

    // ---- interpolation --------------------------------------------------------------------------------------

    @Test
    void aChangedPropOfAKeptNodeAnimatesFromWhereItWas() {
        JXNativeNode root = mount(box().set("opacity", 0.0).set("transition", "opacity 100ms linear"));
        JXNativeNode box = kid(root);
        reconcile(root, box().set("opacity", 1.0).set("transition", "opacity 100ms linear"));
        assertSame(box, kid(root), "the node is kept");
        assertEquals(0.0, number(box, "opacity"), 1e-9, "shown as before until a frame runs");
        assertTrue(root.tickAnimations(T0), "the first frame starts the clock");
        assertEquals(0.0, number(box, "opacity"), 1e-9);
        assertTrue(root.tickAnimations(T0 + 25 * MS));
        assertEquals(0.25, number(box, "opacity"), 1e-9);
        assertTrue(root.tickAnimations(T0 + 75 * MS));
        assertEquals(0.75, number(box, "opacity"), 1e-9);
        assertFalse(root.tickAnimations(T0 + 100 * MS), "over exactly when the duration has passed");
        assertEquals(1.0, number(box, "opacity"), 1e-9);
        assertNull(box.animation(), "and the node answers from its props again");
        assertEquals(1.0, box.getProps().get("opacity"));
    }

    @Test
    void anEasingAndADelayShapeTheMotion() {
        JXNativeNode root = mount(box().set("translateX", 0.0).set("transition", "translateX 100ms ease-in 50ms"));
        reconcile(root, box().set("translateX", 100.0).set("transition", "translateX 100ms ease-in 50ms"));
        JXNativeNode box = kid(root);
        root.tickAnimations(T0);
        root.tickAnimations(T0 + 40 * MS);
        assertEquals(0.0, number(box, "translateX"), 1e-9, "held during the delay");
        root.tickAnimations(T0 + 100 * MS); // halfway through the 100 ms that began at 50
        double halfway = number(box, "translateX");
        assertTrue(halfway > 0 && halfway < 50, "ease-in is behind linear at half time: " + halfway);
        root.tickAnimations(T0 + 150 * MS);
        assertEquals(100.0, number(box, "translateX"), 1e-9);
    }

    @Test
    void colorsInterpolateChannelByChannel() {
        JXNativeNode root = mount(box().set("background", 0xFF000000).set("transition", "background 100ms linear"));
        reconcile(root, box().set("background", 0xFFFF8000).set("transition", "background 100ms linear"));
        JXNativeNode box = kid(root);
        root.tickAnimations(T0);
        root.tickAnimations(T0 + 50 * MS);
        assertEquals(0xFF804000, ((Integer) box.getProperty("background")).intValue());
        root.tickAnimations(T0 + 100 * MS);
        assertEquals(0xFFFF8000, ((Integer) box.getProperty("background")).intValue());
    }

    @Test
    void aPropWithoutATransitionJumps() {
        JXNativeNode root = mount(box().set("opacity", 0.0).set("translateX", 0.0).set("transition", "opacity 100ms"));
        reconcile(root, box().set("opacity", 1.0).set("translateX", 50.0).set("transition", "opacity 100ms"));
        assertEquals(50.0, number(kid(root), "translateX"), 1e-9, "translateX has no transition");
        assertEquals(0.0, number(kid(root), "opacity"), 1e-9, "opacity is going to move");
    }

    @Test
    void allCoversEveryAnimatableProp_andAnItemOfItsOwnWins() {
        String spec = "all 100ms linear, rotate 200ms linear";
        JXNativeNode root = mount(box().set("transition", spec));
        reconcile(root, box().set("opacity", 0.5).set("scaleX", 2.0).set("rotate", 90.0).set("background", 0xFF112233).set("transition", spec));
        JXNativeNode box = kid(root);
        root.tickAnimations(T0);
        root.tickAnimations(T0 + 50 * MS);
        assertEquals(0.75, number(box, "opacity"), 1e-9, "from the default 1 to 0.5");
        assertEquals(1.5, number(box, "scaleX"), 1e-9);
        assertEquals(22.5, number(box, "rotate"), 1e-9, "its own 200 ms");
        assertNull(box.animation().value("background"), "a colour with no previous value has nothing to start from: it jumps");
        assertEquals(0xFF112233, ((Integer) box.getProperty("background")).intValue());
    }

    // ---- retargeting ----------------------------------------------------------------------------------------

    @Test
    void aChangeInFlightStartsFromTheValueShown() {
        JXNativeNode root = mount(box().set("translateX", 0.0).set("transition", "translateX 100ms linear"));
        JXNativeNode box = kid(root);
        reconcile(root, box().set("translateX", 100.0).set("transition", "translateX 100ms linear"));
        root.tickAnimations(T0);
        root.tickAnimations(T0 + 50 * MS);
        assertEquals(50.0, number(box, "translateX"), 1e-9);
        reconcile(root, box().set("translateX", 0.0).set("transition", "translateX 100ms linear")); // turns back
        assertEquals(50.0, number(box, "translateX"), 1e-9, "no jump to either end");
        root.tickAnimations(T0 + 60 * MS); // the new track starts at this frame
        assertEquals(50.0, number(box, "translateX"), 1e-9);
        root.tickAnimations(T0 + 110 * MS);
        assertEquals(25.0, number(box, "translateX"), 1e-9, "from 50 towards 0 over a full duration");
        assertFalse(root.tickAnimations(T0 + 160 * MS));
        assertEquals(0.0, number(box, "translateX"), 1e-9);
    }

    @Test
    void anUnrelatedPropDoesNotRestartARunningAnimation() {
        String spec = "translateX 100ms linear";
        JXNativeNode root = mount(box().set("translateX", 0.0).set("transition", spec));
        reconcile(root, box().set("translateX", 100.0).set("transition", spec));
        JXNativeNode box = kid(root);
        root.tickAnimations(T0);
        root.tickAnimations(T0 + 40 * MS);
        reconcile(root, box().set("translateX", 100.0).set("transition", spec).set("tooltip", "hello").set("background", 0xFF00FF00));
        assertEquals(40.0, number(box, "translateX"), 1e-9, "still where it was");
        root.tickAnimations(T0 + 80 * MS);
        assertEquals(80.0, number(box, "translateX"), 1e-9, "and on its original schedule");
        assertFalse(root.tickAnimations(T0 + 100 * MS));
    }

    @Test
    void sameValueAgainStartsNothing() {
        String spec = "opacity 100ms";
        JXNativeNode root = mount(box().set("opacity", 0.5).set("transition", spec));
        int before = JXNodeAnimation.starts();
        reconcile(root, box().set("opacity", 0.5).set("transition", spec));
        assertEquals(before, JXNodeAnimation.starts());
        assertNull(kid(root).animation());
    }

    // ---- enter ----------------------------------------------------------------------------------------------

    @Test
    void aNewNodeAnimatesFromItsEnterValues() {
        JXNativeNode root = mount(box().set("opacity", 0.8).set("translateY", 4.0).set("enter", "opacity 0 100ms linear, translateY 20 100ms linear"));
        JXNativeNode box = kid(root);
        assertEquals(0.0, number(box, "opacity"), 1e-9, "the first paint shows the starting values");
        assertEquals(20.0, number(box, "translateY"), 1e-9);
        root.tickAnimations(T0);
        root.tickAnimations(T0 + 50 * MS);
        assertEquals(0.4, number(box, "opacity"), 1e-9);
        assertEquals(12.0, number(box, "translateY"), 1e-9);
        assertFalse(root.tickAnimations(T0 + 100 * MS));
        assertEquals(0.8, number(box, "opacity"), 1e-9);
        assertEquals(4.0, number(box, "translateY"), 1e-9);
    }

    @Test
    void aNodeAddedByAReconcileEntersAndTheOnesKeptDoNot() {
        JXElement before = JXElement.of("pane", JXProps.builder().build(), JXElement.of("pane", box().build()));
        JXElement after = JXElement.of("pane", JXProps.builder().build(), JXElement.of("pane", box().build()),
                JXElement.of("pane", box().set("layoutX", 10.0).set("enter", "opacity 0 100ms").build()));
        JXNativeNode root = JXNativeNode.createBackendNode(before);
        assertTrue(root.reconcile(after));
        assertNull(root.getChildren().get(0).animation());
        assertEquals(0.0, number(root.getChildren().get(1), "opacity"), 1e-9);
        // a reconcile with the same enter does not enter again
        JXNativeNode added = root.getChildren().get(1);
        root.tickAnimations(T0);
        root.tickAnimations(T0 + 100 * MS);
        assertTrue(root.reconcile(JXElement.of("pane", JXProps.builder().build(), JXElement.of("pane", box().build()),
                JXElement.of("pane", box().set("layoutX", 10.0).set("enter", "opacity 0 100ms").build()))));
        assertSame(added, root.getChildren().get(1));
        assertNull(added.animation());
    }

    @Test
    void anEnterDoesNotMeetAnUnrelatedReconcile() {
        JXNativeNode root = mount(box().set("enter", "opacity 0 100ms linear"));
        root.tickAnimations(T0);
        root.tickAnimations(T0 + 50 * MS);
        reconcile(root, box().set("enter", "opacity 0 100ms linear").set("tooltip", "x"));
        assertEquals(0.5, number(kid(root), "opacity"), 1e-9);
    }

    // ---- reduced motion -------------------------------------------------------------------------------------

    @Test
    void withAnimationsOffEverythingJumps() {
        System.setProperty("jx.animations", "false");
        JXNativeNode root = mount(box().set("opacity", 0.0).set("enter", "opacity 0.3 100ms").set("transition", "all 100ms"));
        assertEquals(0.0, number(kid(root), "opacity"), 1e-9, "the entering node shows its props at once");
        assertNull(kid(root).animation());
        reconcile(root, box().set("opacity", 1.0).set("transition", "all 100ms"));
        assertEquals(1.0, number(kid(root), "opacity"), 1e-9);
        assertNull(kid(root).animation());
        assertFalse(new JXAnimationDriver().step(root, T0));
    }

    // ---- the clock and the window's driver ------------------------------------------------------------------

    @Test
    void theDriverAsksForFramesWhileSomethingMovesAndThenStops() {
        long[] now = {T0};
        JXAnimationClock.setSource(() -> now[0]);
        JXAnimationDriver driver = new JXAnimationDriver();
        JXNativeNode root = mount(box().set("opacity", 0.0).set("transition", "opacity 100ms linear"));
        assertFalse(driver.step(root, JXAnimationClock.now()), "nothing moves, nothing is asked for");
        reconcile(root, box().set("opacity", 1.0).set("transition", "opacity 100ms linear"));
        int frames = 0;
        while (driver.step(root, JXAnimationClock.now())) {
            frames++;
            now[0] += 16 * MS;
            assertTrue(frames < 100, "it must stop");
        }
        assertEquals(7, frames, "frames at 0, 16, ... 96 ms ask for another; the one at 112 ms settles it");
        assertEquals(1.0, number(kid(root), "opacity"), 1e-9);
        assertFalse(driver.isAnimating());
        for (int i = 0; i < 5; i++) { // later frames walk nothing and ask for nothing
            now[0] += 16 * MS;
            assertFalse(driver.step(root, JXAnimationClock.now()));
        }
    }

    @Test
    void theDefaultClockIsMonotonicNanoTime() {
        long a = JXAnimationClock.now();
        long b = JXAnimationClock.now();
        assertTrue(b >= a);
        JXAnimationClock.setSource(() -> 42L);
        assertEquals(42L, JXAnimationClock.now());
        JXAnimationClock.setSource(null);
        assertTrue(JXAnimationClock.now() != 42L);
    }

    @Test
    void anInvalidTransitionFailsWithAClearMessage() {
        JXNativeNode root = mount(box().set("opacity", 0.0).set("transition", "opacity 100ms"));
        IllegalArgumentException e = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> reconcile(root, box().set("opacity", 1.0).set("transition", "opacity fast")));
        assertTrue(e.getMessage().contains("opacity fast"), e.getMessage());
    }

    // ---- picking and painting -------------------------------------------------------------------------------

    private static JXNativeNode clickable(JXProps.Builder extra, AtomicInteger clicks) {
        return mount(extra.set("onClick", (Runnable) clicks::incrementAndGet));
    }

    @Test
    void aClickDuringAnAnimationHitsWhereTheNodeIsDrawn() {
        AtomicInteger clicks = new AtomicInteger();
        String spec = "translateX 100ms linear";
        JXNativeNode root = clickable(box().set("translateX", 0.0).set("transition", spec), clicks);
        reconcile(root, box().set("translateX", 100.0).set("transition", spec).set("onClick", (Runnable) clicks::incrementAndGet));
        JXNativeNode box = kid(root);
        root.tickAnimations(T0);
        root.tickAnimations(T0 + 50 * MS); // drawn 50 to the right: x 110..150
        assertEquals(60, box.getX(), "the layout bounds do not move");
        assertSame(box, root.hitTest(130, 80));
        assertSame(root, root.hitTest(80, 80), "nothing is where it was laid out");
        assertSame(root, root.hitTest(170, 80), "or where it is going");
        root.tickAnimations(T0 + 100 * MS);
        assertSame(box, root.hitTest(170, 80));
        assertSame(root, root.hitTest(130, 80), "and once it has arrived, not where it passed");
    }

    @Test
    void theTransformPushedAroundAMovingNodeFollowsTheFrame() {
        String spec = "translateX 100ms linear";
        JXNativeNode root = mount(box().set("background", 0xFFFF0000).set("translateX", 0.0).set("transition", spec));
        reconcile(root, box().set("background", 0xFFFF0000).set("translateX", 100.0).set("transition", spec));
        root.tickAnimations(T0);
        root.tickAnimations(T0 + 50 * MS);
        assertTrue(record(root).contains("transform 50.00 0.00 1.00 1.00 0.00 80.00 80.00"), record(root));
    }

    @Test
    void aFinishedAnimationPaintsExactlyAsTheStaticTreeDoes() {
        JXProps.Builder end = box().set("background", 0xFFFF0000).set("opacity", 0.5).set("translateX", 30.0).set("rotate", 10.0);
        String still = record(mount(end));
        JXNativeNode root = mount(box().set("background", 0xFF0000FF).set("transition", "all 100ms"));
        reconcile(root, box().set("background", 0xFFFF0000).set("opacity", 0.5).set("translateX", 30.0).set("rotate", 10.0).set("transition", "all 100ms"));
        root.tickAnimations(T0);
        root.tickAnimations(T0 + 200 * MS);
        assertEquals(still, record(root));
    }

    @Test
    void aTreeWithoutAnimationPropsIsUntouched() {
        JXNativeNode root = mount(box().set("background", 0xFFFF0000).set("opacity", 0.5));
        assertNull(kid(root).animation());
        assertFalse(root.tickAnimations(T0));
        reconcile(root, box().set("background", 0xFF00FF00).set("opacity", 1.0));
        assertNull(kid(root).animation());
        assertEquals(1.0, number(kid(root), "opacity"), 1e-9);
    }

    private static String record(JXNativeNode root) {
        RecordingPainter painter = new RecordingPainter();
        new JXPaint(painter).paint(root);
        return painter.log.toString();
    }
}
