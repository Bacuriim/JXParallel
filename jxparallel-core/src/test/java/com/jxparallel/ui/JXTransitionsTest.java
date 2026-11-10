package com.jxparallel.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JXTransitionsTest {
    private static final long MS = 1_000_000L;

    @Test
    void easingsStartAtZeroAndEndAtOne() {
        for (JXTransitions.Easing e : new JXTransitions.Easing[] {JXTransitions.Easing.LINEAR, JXTransitions.Easing.EASE,
                JXTransitions.Easing.EASE_IN, JXTransitions.Easing.EASE_OUT, JXTransitions.Easing.EASE_IN_OUT}) {
            assertEquals(0, e.apply(0), 1e-9, e.toString());
            assertEquals(1, e.apply(1), 1e-9, e.toString());
            assertEquals(0, e.apply(-3), 1e-9);
            assertEquals(1, e.apply(7), 1e-9);
            double last = 0;
            for (int i = 1; i <= 100; i++) {
                double y = e.apply(i / 100.0);
                assertTrue(y >= last - 1e-9, e + " is monotonic at " + i);
                last = y;
            }
        }
    }

    @Test
    void easingsFollowTheCssCurves() {
        assertEquals(0.5, JXTransitions.Easing.LINEAR.apply(0.5), 1e-12);
        assertEquals(0.5, JXTransitions.Easing.EASE_IN_OUT.apply(0.5), 1e-6, "symmetric");
        assertEquals(0.8024, JXTransitions.Easing.EASE.apply(0.5), 1e-3, "the CSS ease at half time");
        assertEquals(0.3153, JXTransitions.Easing.EASE_IN.apply(0.5), 1e-3);
        assertEquals(0.6846, JXTransitions.Easing.EASE_OUT.apply(0.5), 1e-3);
        assertTrue(JXTransitions.Easing.EASE_OUT.apply(0.2) > 0.2 && JXTransitions.Easing.EASE_IN.apply(0.2) < 0.2);
    }

    @Test
    void aTransitionHasAnItemPerPropAndAllForTheRest() {
        JXTransitions.Transition t = JXTransitions.transition("opacity 200ms ease-out, translateX 0.3s linear 50ms, all 1s");
        JXTransitions.Item opacity = t.forProperty(JXTransitions.indexOf("opacity"));
        assertEquals(200 * MS, opacity.durationNanos);
        assertEquals(0, opacity.delayNanos);
        assertSame(JXTransitions.Easing.EASE_OUT, opacity.easing);
        JXTransitions.Item x = t.forProperty(JXTransitions.indexOf("translateX"));
        assertEquals(300 * MS, x.durationNanos);
        assertEquals(50 * MS, x.delayNanos);
        assertSame(JXTransitions.Easing.LINEAR, x.easing);
        JXTransitions.Item rest = t.forProperty(JXTransitions.indexOf("rotate"));
        assertEquals(1000 * MS, rest.durationNanos);
        assertSame(JXTransitions.Easing.EASE, rest.easing, "ease unless said");
        assertNull(JXTransitions.transition("opacity 1s").forProperty(JXTransitions.indexOf("rotate")));
    }

    @Test
    void anEnterHasTheValuesItStartsFrom() {
        JXTransitions.Enter e = JXTransitions.enter("opacity 0 200ms, translateY 12 150ms ease-in 10ms, background #f00 1s");
        assertEquals(3, e.items().size());
        assertEquals(0.0, e.items().get(0).from);
        assertEquals(12.0, e.items().get(1).from);
        assertEquals(10 * MS, e.items().get(1).delayNanos);
        assertEquals(0xFFFF0000, e.items().get(2).from);
    }

    @Test
    void eachDistinctTextIsParsedOnce() {
        assertSame(JXTransitions.transition("opacity 1s"), JXTransitions.transition(new String("opacity 1s")));
        assertSame(JXTransitions.enter("opacity 0 1s"), JXTransitions.enter(new String("opacity 0 1s")));
    }

    @Test
    void badTextIsRefusedWithTheReason() {
        assertBad("transition", "", "empty");
        assertBad("transition", "opacity", "duration is missing");
        assertBad("transition", "opacity 200", "not a time");
        assertBad("transition", "opacity 200ms, ", "stray comma");
        assertBad("transition", "width 200ms", "cannot be animated");
        assertBad("transition", "opacity 200ms ease-out linear", "two easing");
        assertBad("transition", "opacity 1s 2s 3s", "one time too many");
        assertBad("transition", "opacity 1s, opacity 2s", "given twice");
        assertBad("transition", "all 1s, all 2s", "given twice");
        assertBad("transition", "opacity -1s", "zero or more");
        assertBad("transition", "opacity fast", "not a time");
        assertBad("enter", "opacity 200ms", "from-value");
        assertBad("enter", "opacity zero 200ms", "not a number");
        assertBad("enter", "all 0 200ms", "cannot be animated");
        assertBad("enter", "background red 200ms", "not a color");
        assertBad("enter", "opacity 0 1s, opacity 1 1s", "given twice");
    }

    private static void assertBad(String kind, String text, String reason) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> {
                    if ("enter".equals(kind)) {
                        JXTransitions.enter(text);
                    } else {
                        JXTransitions.transition(text);
                    }
                }, text);
        assertTrue(e.getMessage().contains(reason), "\"" + text + "\" -> " + e.getMessage());
    }
}
