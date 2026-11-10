package com.jxparallel.core;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * A unit of work with a priority, a {@link CancellationToken} and an optional timeout.
 *
 * <p><b>Timeout.</b> A task has <em>no</em> timeout unless one is set with {@link #withTimeout}.
 * The default is {@link Duration#ZERO}, which means "no timeout". A positive timeout is enforced
 * by the pool: when it expires the future completes with a
 * {@link java.util.concurrent.TimeoutException}, the task token is cancelled and the running
 * thread is interrupted. Work that blocks for a long time on purpose (a native file dialog, a
 * user decision) must not be given a timeout.
 */
public final class Task<T> implements Callable<T> {
    private final Callable<T> callable;
    private final TaskPriority priority;
    private final CancellationToken cancellationToken;
    private final Duration timeout;

    private Task(Callable<T> callable, TaskPriority priority, CancellationToken cancellationToken, Duration timeout) {
        this.callable = Objects.requireNonNull(callable, "callable must not be null");
        this.priority = priority == null ? TaskPriority.NORMAL : priority;
        this.cancellationToken = cancellationToken == null ? new CancellationToken() : cancellationToken;
        this.timeout = timeout == null || timeout.isNegative() ? Duration.ZERO : timeout;
    }

    public static <T> Task<T> of(Callable<T> callable) {
        return new Task<>(callable, TaskPriority.NORMAL, new CancellationToken(), Duration.ZERO);
    }

    public Task<T> withPriority(TaskPriority priority) {
        return new Task<>(callable, priority, cancellationToken, timeout);
    }

    public Task<T> withCancellationToken(CancellationToken cancellationToken) {
        return new Task<>(callable, priority, cancellationToken, timeout);
    }

    /** Sets the timeout. {@code null}, a negative value or {@link Duration#ZERO} mean no timeout. */
    public Task<T> withTimeout(Duration timeout) {
        return new Task<>(callable, priority, cancellationToken, timeout);
    }

    /** Sets the timeout; zero or less means no timeout. */
    public Task<T> withTimeout(long value, TimeUnit unit) {
        return new Task<>(callable, priority, cancellationToken, Duration.ofMillis(unit.toMillis(value)));
    }

    /** Removes any timeout: the task may run as long as it needs. This is the default. */
    public Task<T> withoutTimeout() {
        return new Task<>(callable, priority, cancellationToken, Duration.ZERO);
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

    /** The timeout; {@link Duration#ZERO} means none (the default). */
    public Duration getTimeout() {
        return timeout;
    }
}
