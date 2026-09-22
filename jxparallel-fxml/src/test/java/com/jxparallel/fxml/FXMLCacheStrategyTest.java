package com.jxparallel.fxml;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class FXMLCacheStrategyTest {
    @Test
    void shouldSupportNoCacheStrategy() {
        FXMLCache cache = new FXMLCache(10, 0L, CacheStrategy.NONE);
        Object value = new Object();
        cache.put("key", value);
        assertNull(cache.get("key"));
    }

    @Test
    void shouldExpireTtlEntries() throws InterruptedException {
        FXMLCache cache = new FXMLCache(10, 1L, CacheStrategy.TTL);
        Object value = new Object();
        cache.put("key", value);
        Thread.sleep(5L);
        assertNull(cache.get("key"));
    }

    @Test
    void shouldTrackLruHits() {
        FXMLCache cache = new FXMLCache(10, 0L, CacheStrategy.LRU);
        Object value = new Object();
        cache.put("key", value);
        assertSame(value, cache.get("key"));
        assertSame(value, cache.get("key"));
        org.junit.jupiter.api.Assertions.assertEquals(2L, cache.getHits());
    }

    @Test
    void ttlCacheShouldStillRespectMaximumSize() {
        FXMLCache cache = new FXMLCache(1, 60_000L, CacheStrategy.TTL);
        cache.put("first", new Object());
        cache.put("second", new Object());
        org.junit.jupiter.api.Assertions.assertEquals(1, cache.size());
    }
}
