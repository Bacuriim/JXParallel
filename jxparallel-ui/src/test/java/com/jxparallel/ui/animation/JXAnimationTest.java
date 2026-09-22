package com.jxparallel.ui.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JXAnimationTest {
    @Test
    void shouldAdvanceDeterministically() {
        final double[] value = new double[1];
        JXAnimation animation = new JXAnimation(100, progress -> value[0] = progress);
        assertTrue(!animation.tick(50));
        assertEquals(0.5, value[0]);
        assertTrue(animation.tick(50));
        assertEquals(1.0, value[0]);
    }
}
