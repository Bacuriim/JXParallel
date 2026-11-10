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
    void shouldHaveNoTimeoutByDefault() {
        Task<String> task = Task.of(() -> "x");
        assertEquals(Duration.ZERO, task.getTimeout());
        assertEquals(Duration.ZERO, task.context().getTimeout());
        assertEquals(Duration.ZERO, task.withTimeout(null).getTimeout());
        assertEquals(Duration.ZERO, task.withTimeout(Duration.ofSeconds(-1)).getTimeout());
        assertEquals(Duration.ZERO, task.withTimeout(1, TimeUnit.SECONDS).withoutTimeout().getTimeout());
    }

    @Test
    void shouldLetTaskWithoutTimeoutRunToTheEnd() throws Exception {
        AdaptiveWorkerPool pool = new AdaptiveWorkerPool(JXParallelConfig.builder()
                .minThreads(1).maxThreads(1).queueCapacity(1).build());
        pool.start();
        try {
            Task<String> task = Task.of(() -> {
                Thread.sleep(300);
                return "finished";
            });
            assertEquals("finished", pool.submit(task).get(5, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
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
