package com.jxparallel.core;

import java.util.concurrent.atomic.AtomicLong;

public final class RuntimeMetrics {
    private final boolean enabled;
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong totalDurationNanos = new AtomicLong();

    public RuntimeMetrics() {
        this(true);
    }

    public RuntimeMetrics(boolean enabled) {
        this.enabled = enabled;
    }

    public void recordSubmitted() {
        if (!enabled) {
            return;
        }
        submitted.incrementAndGet();
    }

    public void recordCompleted(long durationNanos) {
        if (!enabled) {
            return;
        }
        completed.incrementAndGet();
        totalDurationNanos.addAndGet(durationNanos);
    }

    public void recordFailed(long durationNanos) {
        if (!enabled) {
            return;
        }
        failed.incrementAndGet();
        totalDurationNanos.addAndGet(durationNanos);
    }

    public long getSubmitted() {
        return submitted.get();
    }

    public long getCompleted() {
        return completed.get();
    }

    public long getFailed() {
        return failed.get();
    }

    public long getTotalDurationNanos() {
        return totalDurationNanos.get();
    }

    public long getAverageDurationNanos() {
        long completedCount = completed.get();
        return completedCount == 0 ? 0 : totalDurationNanos.get() / completedCount;
    }
}
