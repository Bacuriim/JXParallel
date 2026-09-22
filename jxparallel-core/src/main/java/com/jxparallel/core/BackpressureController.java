package com.jxparallel.core;

public final class BackpressureController {
    private final int maxQueueSize;
    private final QueuePolicy queuePolicy;

    public BackpressureController(int maxQueueSize, QueuePolicy queuePolicy) {
        this.maxQueueSize = maxQueueSize;
        this.queuePolicy = queuePolicy == null ? QueuePolicy.BLOCK : queuePolicy;
    }

    public boolean shouldAccept(int currentSize) {
        if (maxQueueSize <= 0) {
            return true;
        }
        return currentSize < maxQueueSize;
    }

    public QueuePolicy getQueuePolicy() {
        return queuePolicy;
    }
}
