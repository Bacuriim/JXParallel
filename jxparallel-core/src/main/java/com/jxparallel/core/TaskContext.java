package com.jxparallel.core;

import java.time.Duration;
import java.util.UUID;

public final class TaskContext {
    private final String taskId;
    private final TaskPriority priority;
    private final CancellationToken cancellationToken;
    private final Duration timeout;

    public TaskContext(TaskPriority priority, CancellationToken cancellationToken, Duration timeout) {
        this.taskId = UUID.randomUUID().toString();
        this.priority = priority == null ? TaskPriority.NORMAL : priority;
        this.cancellationToken = cancellationToken == null ? new CancellationToken() : cancellationToken;
        this.timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
    }

    public String getTaskId() {
        return taskId;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public CancellationToken getCancellationToken() {
        return cancellationToken;
    }

    public Duration getTimeout() {
        return timeout;
    }
}
