package com.jxparallel.ui.resource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public final class JXResourceCache {
    private final int maxSize;
    private final long maxBytes;
    private final Map<String, byte[]> entries;
    private final ReentrantLock lock = new ReentrantLock();
    private long currentBytes;
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();

    public JXResourceCache(int maxSize) {
        this(maxSize, Long.MAX_VALUE);
    }

    public JXResourceCache(int maxSize, long maxBytes) {
        this.maxSize = Math.max(1, maxSize);
        this.maxBytes = Math.max(1L, maxBytes);
        this.entries = new LinkedHashMap<String, byte[]>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
                boolean remove = size() > JXResourceCache.this.maxSize
                        || currentBytes > JXResourceCache.this.maxBytes;
                if (remove) {
                    currentBytes -= eldest.getValue().length;
                    evictions.incrementAndGet();
                }
                return remove;
            }
        };
    }

    public byte[] get(String key) {
        lock.lock();
        try {
            byte[] value = entries.get(key);
            if (value == null) {
                misses.incrementAndGet();
                return null;
            }
            hits.incrementAndGet();
            return value.clone();
        } finally {
            lock.unlock();
        }
    }

    public void put(String key, byte[] value) {
        if (key == null || value == null || value.length > maxBytes) return;
        lock.lock();
        try {
            byte[] previous = entries.put(key, value.clone());
            if (previous != null) currentBytes -= previous.length;
            currentBytes += value.length;
            trim();
        } finally {
            lock.unlock();
        }
    }

    public void invalidate(String key) {
        lock.lock();
        try {
            byte[] removed = entries.remove(key);
            if (removed != null) currentBytes -= removed.length;
        } finally {
            lock.unlock();
        }
    }

    public void clear() {
        lock.lock();
        try {
            entries.clear();
            currentBytes = 0L;
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return entries.size();
        } finally {
            lock.unlock();
        }
    }

    public long getCurrentBytes() {
        lock.lock();
        try {
            return currentBytes;
        } finally {
            lock.unlock();
        }
    }
    public long getHits() { return hits.get(); }
    public long getMisses() { return misses.get(); }
    public long getEvictions() { return evictions.get(); }

    private void trim() {
        while (entries.size() > maxSize || currentBytes > maxBytes) {
            String eldestKey = entries.keySet().iterator().next();
            byte[] eldest = entries.remove(eldestKey);
            if (eldest != null) {
                currentBytes -= eldest.length;
                evictions.incrementAndGet();
            }
        }
    }
}
