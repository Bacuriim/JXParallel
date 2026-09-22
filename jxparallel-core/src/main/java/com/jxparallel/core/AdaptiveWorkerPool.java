package com.jxparallel.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class AdaptiveWorkerPool {
    private final JXParallelConfig config;
    private final ThreadPoolExecutor executor;
    private final RuntimeMetrics metrics;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicLong sequence = new AtomicLong(0L);
    private final Semaphore capacity;
    private final ScheduledExecutorService timeoutScheduler;

    public AdaptiveWorkerPool(JXParallelConfig config) {
        this.config = config == null ? JXParallelConfig.defaults() : config;
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "jxparallel-worker");
            thread.setDaemon(this.config.isDaemonThreads());
            thread.setPriority(this.config.getThreadPriority());
            return thread;
        };
        this.metrics = new RuntimeMetrics(this.config.isMetricsEnabled());
        this.capacity = new Semaphore(this.config.getQueueCapacity() + this.config.getMaxThreads(), true);
        this.timeoutScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "jxparallel-timeout");
            thread.setDaemon(this.config.isDaemonThreads());
            return thread;
        });

        this.executor = new ThreadPoolExecutor(
                this.config.getMinThreads(),
                this.config.getMaxThreads(),
                this.config.getKeepAliveMillis(),
                TimeUnit.MILLISECONDS,
                new BoundedPriorityBlockingQueue(this.config.getQueueCapacity(), (left, right) -> {
                    if (left == right) {
                        return 0;
                    }
                    if (left instanceof PrioritizedRunnable && right instanceof PrioritizedRunnable) {
                        PrioritizedRunnable a = (PrioritizedRunnable) left;
                        PrioritizedRunnable b = (PrioritizedRunnable) right;
                        int priorityOrder = Integer.compare(b.priority().ordinal(), a.priority().ordinal());
                        if (priorityOrder != 0) {
                            return priorityOrder;
                        }
                        return Long.compare(a.sequence(), b.sequence());
                    }
                    return Long.compare(System.identityHashCode(left), System.identityHashCode(right));
                }),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    public void start() {
        if (started.compareAndSet(false, true)) {
            executor.prestartAllCoreThreads();
        }
    }

    public <T> CompletableFuture<T> submit(Callable<T> callable) {
        return submitInternal(callable, TaskPriority.NORMAL, null, null);
    }

    public <T> CompletableFuture<T> submit(Callable<T> callable, TaskPriority priority) {
        return submitInternal(callable, priority, null, null);
    }

    public <T> CompletableFuture<T> submit(Task<T> task) {
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }
        return submitInternal(task, task.getPriority(), task.getCancellationToken(), task.getTimeout());
    }

    private <T> CompletableFuture<T> submitInternal(Callable<T> callable, TaskPriority priority,
                                                     CancellationToken cancellationToken,
                                                     java.time.Duration timeout) {
        if (callable == null) {
            throw new IllegalArgumentException("Callable cannot be null");
        }
        TaskPriority effectivePriority = priority == null ? TaskPriority.NORMAL : priority;
        metrics.recordSubmitted();
        CompletableFuture<T> future = new CompletableFuture<T>();
        AtomicBoolean finished = new AtomicBoolean(false);
        AtomicReferenceHolder<Thread> runner = new AtomicReferenceHolder<Thread>();
        AtomicReferenceHolder<ScheduledFuture<?>> timeoutHandle = new AtomicReferenceHolder<ScheduledFuture<?>>();
        Runnable task = new PrioritizedRunnable(() -> {
            if (cancellationToken != null && cancellationToken.isCancelled()) {
                finished.set(true);
                future.cancel(false);
                capacity.release();
                return;
            }
            runner.set(Thread.currentThread());
            long start = System.nanoTime();
            try {
                T value = callable.call();
                if (finished.compareAndSet(false, true)) {
                    metrics.recordCompleted(System.nanoTime() - start);
                    future.complete(value);
                }
            } catch (Throwable throwable) {
                if (finished.compareAndSet(false, true)) {
                    metrics.recordFailed(System.nanoTime() - start);
                    future.completeExceptionally(throwable instanceof Exception
                            ? (Exception) throwable : new RuntimeException(throwable));
                }
            } finally {
                runner.set(null);
                ScheduledFuture<?> scheduled = timeoutHandle.get();
                if (scheduled != null) {
                    scheduled.cancel(false);
                }
                capacity.release();
            }
        }, future, effectivePriority, sequence.getAndIncrement(), finished, timeoutHandle);
        if (!reserveCapacity(future)) {
            metrics.recordFailed(0L);
            return future;
        }
        try {
            executor.execute(task);
        } catch (RuntimeException exception) {
            capacity.release();
            future.completeExceptionally(exception);
        }
        if (timeout != null && !timeout.isNegative() && !timeout.isZero() && !future.isDone()) {
            ScheduledFuture<?> scheduled = timeoutScheduler.schedule(() -> {
                if (finished.compareAndSet(false, true)) {
                    if (cancellationToken != null) {
                        cancellationToken.cancel();
                    }
                    Thread activeThread = runner.get();
                    if (activeThread != null) {
                        activeThread.interrupt();
                    }
                    future.completeExceptionally(new TimeoutException("JXParallel task timed out after " + timeout));
                    metrics.recordFailed(timeout.toNanos());
                }
            }, timeout.toNanos(), TimeUnit.NANOSECONDS);
            timeoutHandle.set(scheduled);
            if (future.isDone()) {
                scheduled.cancel(false);
            }
        }
        return future;
    }

    public CompletableFuture<Void> submit(Runnable runnable) {
        return submit(runnable, TaskPriority.NORMAL);
    }

    public CompletableFuture<Void> submit(Runnable runnable, TaskPriority priority) {
        if (runnable == null) {
            throw new IllegalArgumentException("Runnable cannot be null");
        }
        return submit(() -> {
            runnable.run();
            return null;
        }, priority);
    }

    public RuntimeMetrics snapshot() {
        return metrics;
    }

    public int getQueueDepth() {
        return executor.getQueue().size();
    }

    public int getActiveThreads() {
        return executor.getActiveCount();
    }

    public boolean isRunning() {
        return !executor.isShutdown();
    }

    public void shutdown() {
        executor.shutdown();
        timeoutScheduler.shutdown();
        started.set(false);
    }

    public List<Runnable> shutdownNow() {
        started.set(false);
        List<Runnable> pending = executor.shutdownNow();
        timeoutScheduler.shutdownNow();
        for (Runnable runnable : pending) {
            if (runnable instanceof PrioritizedRunnable) {
                ((PrioritizedRunnable) runnable).cancel();
            }
        }
        return pending;
    }

    public JXParallelConfig getConfig() {
        return config;
    }

    private final class PrioritizedRunnable implements Runnable {
        private final Runnable delegate;
        private final CompletableFuture<?> future;
        private final TaskPriority priority;
        private final long sequence;
        private final AtomicBoolean finished;
        private final AtomicReferenceHolder<ScheduledFuture<?>> timeoutHandle;

        private PrioritizedRunnable(Runnable delegate, CompletableFuture<?> future,
                                    TaskPriority priority, long sequence,
                                    AtomicBoolean finished,
                                    AtomicReferenceHolder<ScheduledFuture<?>> timeoutHandle) {
            this.delegate = delegate;
            this.future = future;
            this.priority = priority == null ? TaskPriority.NORMAL : priority;
            this.sequence = sequence;
            this.finished = finished;
            this.timeoutHandle = timeoutHandle;
        }

        private TaskPriority priority() {
            return priority;
        }

        private long sequence() {
            return sequence;
        }

        private void cancel() {
            finished.set(true);
            future.cancel(false);
            ScheduledFuture<?> scheduled = timeoutHandle.get();
            if (scheduled != null) {
                scheduled.cancel(false);
            }
            capacity.release();
        }

        @Override
        public void run() {
            delegate.run();
        }
    }

    private boolean reserveCapacity(CompletableFuture<?> future) {
        switch (config.getQueuePolicy()) {
            case BLOCK:
            try {
                capacity.acquire();
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            case DISCARD:
                if (capacity.tryAcquire()) {
                    return true;
                }
                future.cancel(false);
                return false;
            case DISCARD_OLDEST:
                if (capacity.tryAcquire()) {
                    return true;
                }
                PrioritizedRunnable oldest = removeOldestQueued();
                if (oldest != null) {
                    oldest.cancel();
                    if (capacity.tryAcquire()) {
                        return true;
                    }
                }
                future.cancel(false);
                return false;
            case REJECT:
            default:
                if (capacity.tryAcquire()) {
                    return true;
                }
                future.completeExceptionally(new RejectedExecutionException("JXParallel task queue is full"));
                return false;
        }
    }

    private PrioritizedRunnable removeOldestQueued() {
        PrioritizedRunnable oldest = null;
        for (Runnable runnable : executor.getQueue()) {
            if (runnable instanceof PrioritizedRunnable) {
                PrioritizedRunnable candidate = (PrioritizedRunnable) runnable;
                if (oldest == null || candidate.sequence() < oldest.sequence()) {
                    oldest = candidate;
                }
            }
        }
        if (oldest != null && executor.getQueue().remove(oldest)) {
            return oldest;
        }
        return null;
    }

    private static final class AtomicReferenceHolder<T> {
        private volatile T value;

        void set(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }
    }

    private static final class BoundedPriorityBlockingQueue extends PriorityBlockingQueue<Runnable> {
        private final Semaphore permits;

        BoundedPriorityBlockingQueue(int capacity, java.util.Comparator<Runnable> comparator) {
            super(Math.max(1, capacity), comparator);
            this.permits = new Semaphore(Math.max(1, capacity), true);
        }

        @Override
        public boolean offer(Runnable runnable) {
            if (!permits.tryAcquire()) {
                return false;
            }
            boolean offered = super.offer(runnable);
            if (!offered) {
                permits.release();
            }
            return offered;
        }

        @Override
        public Runnable poll() {
            Runnable result = super.poll();
            if (result != null) {
                permits.release();
            }
            return result;
        }

        @Override
        public Runnable take() throws InterruptedException {
            Runnable result = super.take();
            permits.release();
            return result;
        }

        @Override
        public boolean remove(Object object) {
            boolean removed = super.remove(object);
            if (removed) {
                permits.release();
            }
            return removed;
        }

        @Override
        public void clear() {
            int count = size();
            super.clear();
            permits.release(count);
        }
    }
}
