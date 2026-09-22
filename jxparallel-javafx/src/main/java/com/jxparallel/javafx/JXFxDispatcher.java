package com.jxparallel.javafx;

import javafx.application.Platform;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public final class JXFxDispatcher {
    private JXFxDispatcher() {
    }

    public static boolean isFxThread() {
        return Platform.isFxApplicationThread();
    }

    public static void runLater(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        if (!isToolkitAvailable()) {
            throw new IllegalStateException("JavaFX toolkit is not available");
        }
        if (Platform.isFxApplicationThread()) {
            runnable.run();
            return;
        }
        try {
            Platform.runLater(runnable);
        } catch (IllegalStateException e) {
            throw new IllegalStateException("Unable to dispatch work to the JavaFX application thread", e);
        }
    }

    public static void runAndWait(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        if (!isToolkitAvailable()) {
            throw new IllegalStateException("JavaFX toolkit is not available");
        }
        if (Platform.isFxApplicationThread()) {
            runnable.run();
            return;
        }
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        try {
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    try {
                        runnable.run();
                    } catch (Throwable throwable) {
                        error.set(throwable);
                    } finally {
                        latch.countDown();
                    }
                }
            });
            latch.await();
        } catch (IllegalStateException e) {
            throw new IllegalStateException("Unable to dispatch work to the JavaFX application thread", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for JavaFX task", e);
        }
        if (error.get() != null) {
            if (error.get() instanceof RuntimeException) {
                throw (RuntimeException) error.get();
            }
            throw new IllegalStateException(error.get());
        }
    }

    private static boolean isToolkitAvailable() {
        try {
            Platform.isFxApplicationThread();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
