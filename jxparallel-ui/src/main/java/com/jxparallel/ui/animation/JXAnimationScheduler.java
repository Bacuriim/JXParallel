package com.jxparallel.ui.animation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class JXAnimationScheduler implements AutoCloseable {
    private static final ScheduledExecutorService SHARED_EXECUTOR =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "jxparallel-animation");
                thread.setDaemon(true);
                return thread;
            });
    private final List<JXAnimation> animations = new ArrayList<JXAnimation>();
    private ScheduledFuture<?> scheduled;

    public interface Frame {
        void apply(double progress);
    }

    public synchronized void add(JXAnimation animation) {
        if (animation != null) animations.add(animation);
    }

    public synchronized int tick(long deltaMillis) {
        Iterator<JXAnimation> iterator = animations.iterator();
        while (iterator.hasNext()) if (iterator.next().tick(deltaMillis)) iterator.remove();
        return animations.size();
    }

    public synchronized void start(long frameMillis) {
        if (scheduled != null) return;
        long interval = Math.max(1L, frameMillis);
        scheduled = SHARED_EXECUTOR.scheduleAtFixedRate(() -> tick(interval), interval, interval,
                TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void close() {
        if (scheduled != null) scheduled.cancel(false);
        scheduled = null;
        animations.clear();
    }
}
