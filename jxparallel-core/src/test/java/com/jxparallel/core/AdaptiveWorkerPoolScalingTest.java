package com.jxparallel.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptiveWorkerPoolScalingTest {
    @Test
    void shouldRunUpToMaxThreadsConcurrentlyWhenAutoScaleIsOn() throws Exception {
        AdaptiveWorkerPool pool = new AdaptiveWorkerPool(JXParallelConfig.builder()
                .minThreads(2)
                .maxThreads(6)
                .autoScale(true)
                .queueCapacity(1000)
                .build());
        CountDownLatch allRunning = new CountDownLatch(6);
        CountDownLatch release = new CountDownLatch(1);
        pool.start();
        try {
            for (int i = 0; i < 6; i++) {
                pool.submit((Callable<Void>) () -> {
                    allRunning.countDown();
                    release.await();
                    return null;
                });
            }
            // With core = min and a large queue, only 2 tasks would ever run at once.
            assertTrue(allRunning.await(2, TimeUnit.SECONDS), "pool did not scale beyond min threads");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
