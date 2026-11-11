package com.jxparallel.examples.orders;

import java.io.File;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.jxparallel.core.CancellationToken;
import com.jxparallel.core.JXParallel;
import com.jxparallel.core.Task;

/**
 * A {@link Host} for tests: the real JXParallel worker pool, and a thread named {@code test-ui} standing for the display
 * thread, so a test can tell where each piece of work ran.
 */
final class TestHost implements Host {
    final ExecutorService ui = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "test-ui");
        t.setDaemon(true);
        return t;
    });
    final AtomicInteger renders = new AtomicInteger();
    final Set<String> workerThreads = ConcurrentHashMap.newKeySet();
    final Set<String> dialogThreads = ConcurrentHashMap.newKeySet();
    volatile File csvToOpen;
    volatile File csvToSave;
    volatile boolean exited;

    @Override
    public void runOnUi(Runnable action) {
        ui.execute(action);
    }

    @Override
    public void requestRender() {
        renders.incrementAndGet();
    }

    @Override
    public <T> CompletableFuture<T> background(Callable<T> work, CancellationToken token) {
        return JXParallel.background(Task.of(() -> {
            workerThreads.add(Thread.currentThread().getName());
            return work.call();
        }).withCancellationToken(token).withTimeout(Duration.ZERO));
    }

    @Override
    public File chooseCsvToOpen() {
        dialogThreads.add(Thread.currentThread().getName());
        return csvToOpen;
    }

    @Override
    public File chooseCsvToSave(String suggestedName) {
        dialogThreads.add(Thread.currentThread().getName());
        return csvToSave;
    }

    @Override
    public void exit() {
        exited = true;
    }

    /** Runs {@code action} on the {@code test-ui} thread and waits: how a test calls what the window would call. */
    void onUi(Runnable action) throws Exception {
        CompletableFuture<Void> done = new CompletableFuture<Void>();
        ui.execute(() -> {
            try {
                action.run();
                done.complete(null);
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        });
        done.get(30, TimeUnit.SECONDS);
    }
}
