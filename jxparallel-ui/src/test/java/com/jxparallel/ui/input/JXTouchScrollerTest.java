package com.jxparallel.ui.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JXTouchScrollerTest {
    private static JXTouchScroller scroller() {
        return new JXTouchScroller(8, 50, 8000);
    }

    @Test
    void aTouchInsideTheSlopIsATap() {
        JXTouchScroller s = scroller();
        assertFalse(s.down(100, 100, 0));
        assertNull(s.move(103, 104, 10));
        assertNull(s.move(100, 107, 20));
        assertTrue(s.isTap());
        assertFalse(s.up(30));
        assertFalse(s.isScrolling());
    }

    @Test
    void movingPastTheSlopScrollsFromThatPointAndTheContentFollowsTheFinger() {
        JXTouchScroller s = scroller();
        s.down(100, 300, 0);
        assertArrayEquals(new double[] {0, 0}, s.move(100, 280, 10), "the slop is crossed: scrolling starts, nothing moves yet");
        assertTrue(s.isScrolling());
        assertFalse(s.isTap());
        assertArrayEquals(new double[] {0, 30}, s.move(100, 250, 20), "the finger went up 30: the offset grows by 30");
        assertArrayEquals(new double[] {5, -10}, s.move(95, 260, 30));
    }

    @Test
    void aFastLiftFlingsAndSlowsDownToAStop() {
        JXTouchScroller s = scroller();
        s.down(0, 1000, 0);
        s.move(0, 990, 10);
        for (int i = 1; i <= 8; i++) {
            s.move(0, 990 - i * 20, 10 + i * 10);   // 20 px per 10 ms: 2000 px/s
        }
        assertTrue(s.up(90));
        assertTrue(s.isFlinging());
        double total = 0;
        double previous = Double.MAX_VALUE;
        long t = 90;
        int frames = 0;
        while (s.isFlinging() && frames++ < 1000) {
            t += 16;
            double[] d = s.step(t);
            assertEquals(0, d[0], 1e-9);
            assertTrue(d[1] > 0, "the content keeps going the way the finger went");
            assertTrue(d[1] <= previous + 1e-9, "and slows down");
            previous = d[1];
            total += d[1];
        }
        assertFalse(s.isFlinging());
        assertEquals(2000 / 3.0, total, 40, "it travels about its speed divided by the friction");
    }

    @Test
    void aSlowOrAHeldLiftDoesNotFling() {
        JXTouchScroller s = scroller();
        s.down(0, 500, 0);
        s.move(0, 480, 10);
        s.move(0, 479, 500);
        s.move(0, 478, 1000);
        assertFalse(s.up(1010), "1 px in 500 ms is below the minimum");

        s.down(0, 500, 2000);
        s.move(0, 480, 2010);
        s.move(0, 400, 2020);
        assertFalse(s.up(2400), "the finger stood still before the lift");
        assertFalse(s.isFlinging());
    }

    @Test
    void theSpeedOfAFlingIsCapped() {
        JXTouchScroller s = new JXTouchScroller(8, 50, 1000);
        s.down(0, 5000, 0);
        s.move(0, 4980, 5);
        s.move(0, 3000, 15);
        assertTrue(s.up(16));
        double[] d = s.step(1016);   // one second at once: the first second of a 1000 px/s fling
        assertTrue(d[1] <= 1000 / 3.0 + 1e-6, String.valueOf(d[1]));
    }

    @Test
    void aFingerDownStopsAFlingAndThatTouchIsNotATap() {
        JXTouchScroller s = scroller();
        s.down(0, 1000, 0);
        s.move(0, 980, 10);
        s.move(0, 900, 20);
        assertTrue(s.up(25));
        assertTrue(s.down(0, 500, 40), "the fling was running");
        assertFalse(s.isFlinging());
        assertArrayEquals(new double[] {0, 0}, s.step(60));
    }

    @Test
    void cancelEndsEverything() {
        JXTouchScroller s = scroller();
        s.down(0, 1000, 0);
        s.move(0, 980, 10);
        s.cancel();
        assertFalse(s.isScrolling());
        assertNull(s.move(0, 900, 20));
    }
}
