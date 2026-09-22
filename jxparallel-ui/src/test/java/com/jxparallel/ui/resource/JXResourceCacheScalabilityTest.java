package com.jxparallel.ui.resource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JXResourceCacheScalabilityTest {
    @Test
    void shouldEnforceEntryAndByteLimitsUnderConcurrentAccess() throws Exception {
        final JXResourceCache cache = new JXResourceCache(32, 1024);
        ExecutorService workers = Executors.newFixedThreadPool(4);
        CountDownLatch done = new CountDownLatch(4);
        for (int worker = 0; worker < 4; worker++) {
            final int id = worker;
            workers.execute(() -> {
                try {
                    for (int index = 0; index < 500; index++) {
                        String key = "resource-" + id + "-" + index;
                        cache.put(key, new byte[64]);
                        cache.get(key);
                    }
                } finally {
                    done.countDown();
                }
            });
        }
        assertTrue(done.await(10, TimeUnit.SECONDS));
        workers.shutdownNow();
        assertTrue(cache.size() <= 32);
        assertTrue(cache.getCurrentBytes() <= 1024);
        assertTrue(cache.getEvictions() > 0);
    }
}
