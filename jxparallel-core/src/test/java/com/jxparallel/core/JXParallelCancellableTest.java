package com.jxparallel.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JXParallelCancellableTest {
    @Test
    void shouldCompleteNormallyAndPassTheToken() throws Exception {
        TaskHandle<String> handle = JXParallel.background(token -> token.isCancelled() ? "cancelled" : "ok");
        assertEquals("ok", handle.future().get(5, TimeUnit.SECONDS));
        assertFalse(handle.isCancelled());
        assertTrue(handle.isDone());
    }

    @Test
    void shouldStopRunningWorkCooperatively() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        AtomicBoolean sawCancel = new AtomicBoolean();
        TaskHandle<Integer> handle = JXParallel.background(token -> {
            started.countDown();
            int loops = 0;
            while (!token.isCancelled()) {
                loops++;
                Thread.sleep(5);
            }
            sawCancel.set(true);
            stopped.countDown();
            return loops;
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));
        handle.cancel();
        assertTrue(handle.isCancelled());
        assertTrue(stopped.await(5, TimeUnit.SECONDS));
        assertTrue(sawCancel.get());
        assertThrows(CancellationException.class, () -> handle.future().get(1, TimeUnit.SECONDS));
        assertTrue(handle.future().isCancelled());
    }

    @Test
    void shouldNotInterruptTheThread() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        TaskHandle<String> handle = JXParallel.background(token -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            interrupted.set(Thread.currentThread().isInterrupted());
            return "done";
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));
        handle.cancel();
        release.countDown();
        Thread.sleep(100);
        assertFalse(interrupted.get());
    }

    @Test
    void shouldPropagateFailure() {
        TaskHandle<String> handle = JXParallel.background(token -> {
            throw new IllegalStateException("boom");
        });
        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> handle.future().get(5, TimeUnit.SECONDS));
        assertEquals("boom", failure.getCause().getMessage());
    }

    @Test
    void cancelAfterCompletionDoesNothingToTheResult() throws Exception {
        TaskHandle<String> handle = JXParallel.background(token -> "value");
        assertEquals("value", handle.future().get(5, TimeUnit.SECONDS));
        handle.cancel();
        assertEquals("value", handle.future().get());
    }

    @Test
    void shouldRejectNullWork() {
        assertThrows(IllegalArgumentException.class, () -> JXParallel.background((CancellableWork<String>) null));
    }
}
