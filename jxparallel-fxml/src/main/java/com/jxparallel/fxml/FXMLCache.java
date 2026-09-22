package com.jxparallel.fxml;

import java.util.LinkedHashMap;
import java.util.Map;

public class FXMLCache {
    private final Map<String, CacheEntry> cache = new LinkedHashMap<String, CacheEntry>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            return strategy != CacheStrategy.NONE && size() > maxSize;
        }
    };
    private final int maxSize;
    private final long ttlMillis;
    private final CacheStrategy strategy;
    private long hits;
    private long misses;
    private long evictions;

    public FXMLCache(int maxSize, long ttlMillis) {
        this(maxSize, ttlMillis, CacheStrategy.LRU);
    }

    public FXMLCache(int maxSize, long ttlMillis, CacheStrategy strategy) {
        this.maxSize = maxSize > 0 ? maxSize : 1;
        this.ttlMillis = Math.max(0L, ttlMillis);
        this.strategy = strategy == null ? CacheStrategy.LRU : strategy;
    }

    public synchronized Object get(String key) {
        if (strategy == CacheStrategy.NONE || key == null) {
            misses++;
            return null;
        }
        CacheEntry entry = cache.get(key);
        if (entry == null || expired(entry)) {
            if (entry != null) {
                cache.remove(key);
                evictions++;
            }
            misses++;
            return null;
        }
        hits++;
        return entry.value;
    }

    public synchronized void put(String key, Object value) {
        if (strategy == CacheStrategy.NONE || key == null || value == null) {
            return;
        }
        cache.put(key, new CacheEntry(value, System.currentTimeMillis()));
    }

    public synchronized void invalidate(String key) {
        cache.remove(key);
    }

    public synchronized void clear() {
        cache.clear();
    }

    public synchronized int size() {
        return cache.size();
    }

    public synchronized long getHits() {
        return hits;
    }

    public synchronized long getMisses() {
        return misses;
    }

    public synchronized long getEvictions() {
        return evictions;
    }

    public CacheStrategy getStrategy() {
        return strategy;
    }

    private boolean expired(CacheEntry entry) {
        return (strategy == CacheStrategy.TTL || strategy == CacheStrategy.LRU_TTL)
                && ttlMillis > 0
                && System.currentTimeMillis() - entry.createdAtMillis >= ttlMillis;
    }

    private static final class CacheEntry {
        private final Object value;
        private final long createdAtMillis;

        private CacheEntry(Object value, long createdAtMillis) {
            this.value = value;
            this.createdAtMillis = createdAtMillis;
        }
    }
}
