package com.jxparallel.javafx;

import com.jxparallel.core.JXParallel;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

public final class JXParallelFx {
    private JXParallelFx() {
    }

    public static boolean isFxThread() {
        return JXFxDispatcher.isFxThread();
    }

    public static void ui(Runnable runnable) {
        JXFxDispatcher.runLater(runnable);
    }

    public static void uiAndWait(Runnable runnable) {
        JXFxDispatcher.runAndWait(runnable);
    }

    public static <T> CompletableFuture<T> background(Callable<T> task) {
        return JXParallel.background(task);
    }

    public static CompletableFuture<Void> background(Runnable runnable) {
        return JXParallel.background(runnable);
    }

    public static <T> CompletableFuture<T> task(Callable<T> task) {
        return JXParallel.task(task);
    }
}
