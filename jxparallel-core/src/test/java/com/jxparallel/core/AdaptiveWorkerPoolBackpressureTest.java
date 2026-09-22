package com.jxparallel.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptiveWorkerPoolBackpressureTest {
    @Test
    void shouldRejectWhenAllWorkersAndQueueSlotsAreOccupied() throws Exception {
        AdaptiveWorkerPool pool = new AdaptiveWorkerPool(JXParallelConfig.builder()
                .minThreads(1)
                .maxThreads(1)
                .queueCapacity(1)
                .queuePolicy(QueuePolicy.REJECT)
                .build());
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        pool.start();
        try {
            CompletableFuture<Void> running = pool.submit((Callable<Void>) () -> {
                started.countDown();
                release.await();
                return null;
            });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            CompletableFuture<Void> queued = pool.submit((Callable<Void>) () -> null);
            CompletableFuture<Void> rejected = pool.submit((Callable<Void>) () -> null);

            assertTrue(rejected.isCompletedExceptionally());
            release.countDown();
            running.get(2, TimeUnit.SECONDS);
            queued.get(2, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void shouldDiscardOldestQueuedTask() throws Exception {
        AdaptiveWorkerPool pool = new AdaptiveWorkerPool(JXParallelConfig.builder()
                .minThreads(1)
                .maxThreads(1)
                .queueCapacity(1)
                .queuePolicy(QueuePolicy.DISCARD_OLDEST)
                .build());
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        pool.start();
        try {
            CompletableFuture<Void> running = pool.submit((Callable<Void>) () -> {
                started.countDown();
                release.await();
                return null;
            });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            CompletableFuture<Void> oldest = pool.submit((Callable<Void>) () -> null);
            CompletableFuture<Void> newest = pool.submit((Callable<Void>) () -> null);

            assertTrue(oldest.isCancelled());
            release.countDown();
            running.get(2, TimeUnit.SECONDS);
            newest.get(2, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
