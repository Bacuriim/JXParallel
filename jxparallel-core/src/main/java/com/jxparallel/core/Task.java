package com.jxparallel.core;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class Task<T> implements Callable<T> {
    private final Callable<T> callable;
    private final TaskPriority priority;
    private final CancellationToken cancellationToken;
    private final Duration timeout;

    private Task(Callable<T> callable, TaskPriority priority, CancellationToken cancellationToken, Duration timeout) {
        this.callable = Objects.requireNonNull(callable, "callable must not be null");
        this.priority = priority == null ? TaskPriority.NORMAL : priority;
        this.cancellationToken = cancellationToken == null ? new CancellationToken() : cancellationToken;
        this.timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
    }

    public static <T> Task<T> of(Callable<T> callable) {
        return new Task<>(callable, TaskPriority.NORMAL, new CancellationToken(), Duration.ofSeconds(30));
    }

    public Task<T> withPriority(TaskPriority priority) {
        return new Task<>(callable, priority, cancellationToken, timeout);
    }

    public Task<T> withCancellationToken(CancellationToken cancellationToken) {
        return new Task<>(callable, priority, cancellationToken, timeout);
    }

    public Task<T> withTimeout(Duration timeout) {
        return new Task<>(callable, priority, cancellationToken, timeout);
    }

    public Task<T> withTimeout(long value, TimeUnit unit) {
        return new Task<>(callable, priority, cancellationToken, Duration.ofMillis(unit.toMillis(value)));
    }

    public TaskContext context() {
        return new TaskContext(priority, cancellationToken, timeout);
    }

    public CompletableFuture<T> submit() {
        return JXParallel.background(this);
    }

    @Override
    public T call() throws Exception {
        if (cancellationToken.isCancelled()) {
            throw new IllegalStateException("Task was cancelled before execution");
        }
        return callable.call();
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public CancellationToken getCancellationToken() {
        return cancellationToken;
    }

    public Duration getTimeout() {
        return timeout;
    }
}
