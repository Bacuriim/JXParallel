package com.jxparallel.ui.native2d;

import java.util.function.LongSupplier;

/**
 * The time animations run on: a monotonic nanosecond clock ({@link System#nanoTime} unless replaced; tests replace it
 * with one they advance by hand), and the switch that turns animation off.
 *
 * <p>{@code -Djx.animations=false} is the application's {@code prefers-reduced-motion}: every {@code transition} and
 * {@code enter} then jumps to the final value. It is read at the moment an animation would start, so it can be flipped
 * while the application runs.
 */
final class JXAnimationClock {
    private static final LongSupplier SYSTEM = System::nanoTime;
    private static volatile LongSupplier source = SYSTEM;

    private JXAnimationClock() {
    }

    /** The current time in nanoseconds; only differences mean something. */
    static long now() {
        return source.getAsLong();
    }

    /** Replaces the clock (tests); null restores {@link System#nanoTime}. */
    static void setSource(LongSupplier replacement) {
        source = replacement == null ? SYSTEM : replacement;
    }

    /** False when {@code -Djx.animations=false}: nothing animates. */
    static boolean enabled() {
        return !"false".equalsIgnoreCase(System.getProperty("jx.animations", "true").trim());
    }
}
