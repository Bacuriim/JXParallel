package com.jxparallel.core;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class TaskQueue<T> {
    private final BlockingQueue<T> queue;

    public TaskQueue(int capacity) {
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    public boolean offer(T task) {
        return queue.offer(task);
    }

    public T take() throws InterruptedException {
        return queue.take();
    }

    public int size() {
        return queue.size();
    }

    public int remainingCapacity() {
        return queue.remainingCapacity();
    }
}
