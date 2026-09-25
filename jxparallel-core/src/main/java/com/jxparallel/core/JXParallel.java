package com.jxparallel.core;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class JXParallel {
    private static final AtomicReference<AdaptiveWorkerPool> POOL = new AtomicReference<>();
    private static volatile JXParallelConfig ACTIVE_CONFIG = loadDefaultConfiguration();

    static {
        POOL.set(new AdaptiveWorkerPool(ACTIVE_CONFIG));
        POOL.get().start();
    }

    private JXParallel() {
    }

    public static JXParallelConfig configure() {
        return ACTIVE_CONFIG;
    }

    public static JXParallelConfig.Builder configureBuilder() {
        return JXParallelConfig.builder();
    }

    public static void start() {
        getCurrentPool().start();
    }

    public static void shutdown() {
        AdaptiveWorkerPool pool = POOL.get();
        if (pool != null) {
            pool.shutdown();
        }
    }

    public static void shutdownNow() {
        AdaptiveWorkerPool pool = POOL.get();
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    public static void restart() {
        AdaptiveWorkerPool oldPool = POOL.getAndSet(null);
        if (oldPool != null) {
            oldPool.shutdownNow();
        }
        start();
    }

    public static <T> CompletableFuture<T> task(Callable<T> callable) {
        return background(callable);
    }

    public static <T> CompletableFuture<T> task(Task<T> task) {
        return background(task);
    }

    public static <T> CompletableFuture<T> background(Callable<T> callable) {
        return getCurrentPool().submit(callable);
    }

    public static <T> CompletableFuture<T> background(Task<T> task) {
        return getCurrentPool().submit(task);
    }

    public static CompletableFuture<Void> background(Runnable runnable) {
        return getCurrentPool().submit(runnable);
    }

    public static RuntimeMetrics metrics() {
        return getCurrentPool().snapshot();
    }

    public static JXParallelConfig getConfig() {
        return ACTIVE_CONFIG;
    }

    public static void applyConfiguration(JXParallelConfig config) {
        JXParallelConfig effective = config == null ? JXParallelConfig.defaults() : config;
        ACTIVE_CONFIG = effective;
        AdaptiveWorkerPool oldPool = POOL.getAndSet(new AdaptiveWorkerPool(effective));
        if (oldPool != null) {
            oldPool.shutdownNow();
        }
        POOL.get().start();
    }

    public static void applyConfigurationFromFile(Path path) throws IOException {
        applyConfiguration(JXParallelConfig.loadFromFile(path));
    }

    public static void applyConfigurationFromFile(String fileName) throws IOException {
        applyConfiguration(JXParallelConfig.loadFromFile(fileName));
    }

    public static JXParallelConfig loadDefaultConfiguration() {
        return JXParallelConfig.loadEffectiveConfiguration(Paths.get("jx-parallel.config"));
    }

    private static AdaptiveWorkerPool getCurrentPool() {
        AdaptiveWorkerPool pool = POOL.get();
        if (pool == null || !pool.isRunning()) {
            synchronized (JXParallel.class) {
                pool = POOL.get();
                if (pool == null || !pool.isRunning()) {
                    pool = new AdaptiveWorkerPool(ACTIVE_CONFIG);
                    POOL.set(pool);
                }
            }
        }
        return pool;
    }
}
