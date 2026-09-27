package com.jxparallel.fx.concurrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.javafx.application.PlatformImpl;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OffFxThreadTest {

    @BeforeAll
    static void startToolkit() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            PlatformImpl.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        started.await();
    }

    /** On the FX thread: the work runs elsewhere and the event loop keeps running while it waits. */
    @Test
    void fxThreadKeepsProcessingEventsWhileWorkRuns() throws Exception {
        CompletableFuture<String> outcome = new CompletableFuture<>();
        AtomicBoolean eventRanDuringWork = new AtomicBoolean();
        Platform.runLater(() -> {
            try {
                String value = OffFxThread.call(() -> {
                    assertFalse(Platform.isFxApplicationThread());
                    CountDownLatch pumped = new CountDownLatch(1);
                    Platform.runLater(pumped::countDown);
                    eventRanDuringWork.set(pumped.await(2, TimeUnit.SECONDS));
                    return "done";
                });
                outcome.complete(value);
            } catch (Exception e) {
                outcome.completeExceptionally(e);
            }
        });
        assertEquals("done", outcome.get(5, TimeUnit.SECONDS));
        assertTrue(eventRanDuringWork.get());
    }

    @Test
    void exceptionsReachTheCaller() throws Exception {
        CompletableFuture<Throwable> outcome = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                OffFxThread.call(() -> {
                    throw new java.io.IOException("db down");
                });
                outcome.complete(null);
            } catch (Exception e) {
                outcome.complete(e);
            }
        });
        Throwable error = outcome.get(5, TimeUnit.SECONDS);
        assertTrue(error instanceof java.io.IOException);
        assertEquals("db down", error.getMessage());
    }
}
