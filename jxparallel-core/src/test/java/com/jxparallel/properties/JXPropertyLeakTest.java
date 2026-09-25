package com.jxparallel.properties;

import java.lang.ref.WeakReference;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Memory leak tests in the style of OpenJFX's JMemoryBuddy: drop the last strong reference, run
 * the GC, and check the WeakReference was cleared.
 */
class JXPropertyLeakTest {
    static void assertCollectable(WeakReference<?> reference) {
        for (int i = 0; i < 20 && reference.get() != null; i++) {
            System.gc();
            byte[][] pressure = new byte[64][];
            for (int j = 0; j < pressure.length; j++) {
                pressure[j] = new byte[64 * 1024];
            }
            Thread.yield();
        }
        assertNull(reference.get(), "object is still reachable, it leaked");
    }

    @Test
    void unboundTargetIsCollectable() {
        JXProperty<String> source = new JXProperty<String>("a");
        JXProperty<String> target = new JXProperty<String>();
        target.bind(source);
        target.unbind();
        WeakReference<JXProperty<String>> reference = new WeakReference<JXProperty<String>>(target);
        target = null;

        assertCollectable(reference);
    }

    /** Like JavaFX: a long-lived source must not keep a forgotten bound target alive. */
    @Test
    void forgottenBoundTargetIsCollectable() {
        JXProperty<String> source = new JXProperty<String>("a");
        JXProperty<String> target = new JXProperty<String>();
        target.bind(source);
        WeakReference<JXProperty<String>> reference = new WeakReference<JXProperty<String>>(target);
        target = null;

        assertCollectable(reference);
        source.set("b"); // the dead binding removes itself from the source
    }

    @Test
    void bindingStillFollowsTheSource() {
        JXProperty<String> source = new JXProperty<String>("a");
        JXProperty<String> target = new JXProperty<String>();
        target.bind(source);
        System.gc();

        source.set("b");

        assertEquals("b", target.get());
    }
}
