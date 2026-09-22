package com.jxparallel.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JXParallelLifecycleTest {
    @Test
    void shouldRestartAfterShutdown() {
        JXParallel.shutdownNow();
        JXParallel.start();

        assertEquals(7, JXParallel.background(() -> 7).join().intValue());

        JXParallel.shutdownNow();
        JXParallel.start();
    }

    @Test
    void shouldRecreatePoolWhenSubmittingAfterShutdown() {
        JXParallel.shutdownNow();

        assertEquals(9, JXParallel.background(() -> 9).join().intValue());

        JXParallel.shutdownNow();
        JXParallel.start();
    }
}
