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

    /** Default: the work runs on the caller, so what was queued before runs after it, as in JavaFX. */
    @Test
    void byDefaultQueuedEventsWaitForTheWorkLikeJavaFx() throws Exception {
        CompletableFuture<String> order = new CompletableFuture<>();
        StringBuilder steps = new StringBuilder();
        Platform.runLater(() -> {
            Platform.runLater(() -> {
                steps.append("queued;");
                order.complete(steps.toString());
            });
            try {
                OffFxThread.call(() -> {
                    assertTrue(Platform.isFxApplicationThread(), "runs on the caller");
                    Thread.sleep(200);
                    steps.append("work;");
                    return null;
                });
            } catch (Exception e) {
                order.completeExceptionally(e);
            }
        });
        assertEquals("work;queued;", order.get(5, TimeUnit.SECONDS));
    }

    /** Nested mode (opt-in): the work runs elsewhere and the event loop keeps running while it waits. */
    @Test
    void nestedModeKeepsProcessingEventsWhileWorkRuns() throws Exception {
        System.setProperty("jx.offfx.nested", "true");
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
        try {
            assertEquals("done", outcome.get(5, TimeUnit.SECONDS));
            assertTrue(eventRanDuringWork.get());
        } finally {
            System.clearProperty("jx.offfx.nested");
        }
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
