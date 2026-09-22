package com.jxparallel.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptiveWorkerPoolTest {
    @Test
    void shouldExecuteBackgroundTask() {
        JXParallelConfig config = JXParallelConfig.builder()
                .minThreads(1)
                .maxThreads(2)
                .queueCapacity(10)
                .build();

        AdaptiveWorkerPool pool = new AdaptiveWorkerPool(config);
        pool.start();

        CompletableFuture<String> future = pool.submit(() -> "ok");
        assertEquals("ok", future.join());

        assertTrue(pool.isRunning());
        pool.shutdown();
    }
}
