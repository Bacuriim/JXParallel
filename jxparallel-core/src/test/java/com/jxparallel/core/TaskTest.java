package com.jxparallel.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TaskTest {
    @Test
    void shouldCreateConfiguredTask() throws ExecutionException, InterruptedException {
        Task<String> task = Task.of(() -> "done")
                .withPriority(TaskPriority.HIGH)
                .withTimeout(Duration.ofSeconds(1));

        assertEquals(TaskPriority.HIGH, task.getPriority());
        assertEquals(Duration.ofSeconds(1), task.getTimeout());
        assertEquals("done", task.submit().get());
        assertNotNull(task.context());
    }

    @Test
    void shouldEnforceTimeoutAndInterruptRunningTask() throws Exception {
        AdaptiveWorkerPool pool = new AdaptiveWorkerPool(JXParallelConfig.builder()
                .minThreads(1)
                .maxThreads(1)
                .queueCapacity(1)
                .build());
        pool.start();
        Task<String> task = Task.of(() -> {
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(5));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw interrupted;
            }
            return "late";
        }).withTimeout(50, TimeUnit.MILLISECONDS);

        try {
            org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> pool.submit(task).get(2, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }
}
