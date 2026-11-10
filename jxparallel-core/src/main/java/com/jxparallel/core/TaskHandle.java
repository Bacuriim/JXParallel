package com.jxparallel.core;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Result of {@link JXParallel#background(CancellableWork)}: the future and a way to cancel.
 */
public final class TaskHandle<T> {
    private final CompletableFuture<T> future;
    private final CancellationToken token;

    TaskHandle(CompletableFuture<T> future, CancellationToken token) {
        this.future = Objects.requireNonNull(future, "future");
        this.token = Objects.requireNonNull(token, "token");
    }

    /** The future of the work. After {@link #cancel()} it is cancelled. */
    public CompletableFuture<T> future() {
        return future;
    }

    /** The token the work received. */
    public CancellationToken token() {
        return token;
    }

    /**
     * Cancels the work: the token is cancelled (work still queued never starts; running work sees
     * {@code token.isCancelled()} and should stop) and the future is cancelled at once, so
     * dependent stages see a {@link java.util.concurrent.CancellationException}. The thread is not
     * interrupted. Calling it again, or after completion, does nothing.
     */
    public void cancel() {
        token.cancel();
        future.cancel(false);
    }

    public boolean isCancelled() {
        return token.isCancelled();
    }

    public boolean isDone() {
        return future.isDone();
    }
}
