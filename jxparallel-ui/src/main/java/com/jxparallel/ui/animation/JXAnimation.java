package com.jxparallel.ui.animation;

public final class JXAnimation {
    private final long durationMillis;
    private final JXAnimationScheduler.Frame frame;
    private long elapsedMillis;
    private boolean complete;

    public JXAnimation(long durationMillis, JXAnimationScheduler.Frame frame) {
        this.durationMillis = Math.max(1L, durationMillis);
        this.frame = frame;
    }

    public boolean tick(long deltaMillis) {
        if (complete) return true;
        elapsedMillis = Math.min(durationMillis, elapsedMillis + Math.max(0L, deltaMillis));
        double progress = (double) elapsedMillis / (double) durationMillis;
        frame.apply(progress);
        complete = elapsedMillis >= durationMillis;
        return complete;
    }

    public boolean isComplete() { return complete; }
}
