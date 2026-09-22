package com.jxparallel.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptiveWorkerPoolShutdownTest {
    @Test
    void shutdownNowShouldCompleteQueuedFuture() throws Exception {
        AdaptiveWorkerPool pool = new AdaptiveWorkerPool(JXParallelConfig.builder()
                .minThreads(1)
                .maxThreads(1)
                .queueCapacity(4)
                .build());
        pool.start();
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        pool.submit(() -> {
            running.countDown();
            release.await();
            return 1;
        });
        assertTrue(running.await(2, TimeUnit.SECONDS));
        CompletableFuture<Integer> queued = pool.submit(() -> 2);

        pool.shutdownNow();
        release.countDown();

        assertTrue(queued.isCancelled() || queued.isCompletedExceptionally());
    }
}
