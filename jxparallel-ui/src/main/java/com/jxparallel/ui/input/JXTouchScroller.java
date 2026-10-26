package com.jxparallel.ui.input;

/**
 * Turns a touch into a scroll: tells a tap from a drag (the touch slop), reports the scroll offset change of
 * each move, and after a quick lift continues as a fling that slows down. Platform independent: the host gives
 * positions and times in any one unit (pixels, milliseconds) and sets slop and speeds in the same unit.
 *
 * <p>Deltas are changes of the scroll offset: dragging the finger up gives a positive y, as the content moves up.
 * Call {@link #down}, then {@link #move} for each move and {@link #up}; while {@link #isFlinging} call {@link #step}
 * once per frame. A touch that ended without {@link #isScrolling} is a tap.
 */
public final class JXTouchScroller {
    private static final int MAX_SAMPLES = 32;
    /** Only the last moves count for the speed of the lift. */
    private static final long WINDOW_MILLIS = 100;
    /** Rate at which a fling slows down, per second: it travels its start speed divided by this. */
    private static final double FRICTION = 3.0;

    private final double slop;
    private final double minFling;
    private final double maxFling;
    private final long[] sampleTime = new long[MAX_SAMPLES];
    private final double[] sampleX = new double[MAX_SAMPLES];
    private final double[] sampleY = new double[MAX_SAMPLES];
    private int samples;
    private boolean down;
    private boolean scrolling;
    private boolean flinging;
    private double lastX;
    private double lastY;
    private double velocityX;
    private double velocityY;
    private long flingTime;

    /**
     * @param slop distance the touch must move before it is a scroll and no longer a tap
     * @param minFling speed (distance per second) a lift needs to start a fling
     * @param maxFling the fastest a fling starts
     */
    public JXTouchScroller(double slop, double minFling, double maxFling) {
        this.slop = slop;
        this.minFling = minFling;
        this.maxFling = maxFling;
    }

    /** A finger went down. Returns true when it stopped a fling in progress (that touch is not a tap). */
    public boolean down(double x, double y, long time) {
        boolean stopped = flinging;
        flinging = false;
        down = true;
        scrolling = false;
        lastX = x;
        lastY = y;
        samples = 0;
        return stopped;
    }

    /**
     * The finger moved. Null while it is still a tap (inside the slop); the first move out of it gives {0, 0} (the
     * scroll starts from there), then the change of the offset since the previous move.
     */
    public double[] move(double x, double y, long time) {
        if (!down) {
            return null;
        }
        if (!scrolling) {
            if (Math.hypot(x - lastX, y - lastY) <= slop) {
                return null;
            }
            scrolling = true;
            lastX = x;
            lastY = y;
            addSample(time, x, y);
            return new double[] {0, 0};
        }
        double[] delta = {lastX - x, lastY - y};
        lastX = x;
        lastY = y;
        addSample(time, x, y);
        return delta;
    }

    /** The finger lifted. True when a fling starts (then {@link #step} gives its offsets). */
    public boolean up(long time) {
        boolean wasScrolling = scrolling;
        down = false;
        scrolling = false;
        if (!wasScrolling || samples < 2) {
            return false;
        }
        int last = samples - 1;
        if (time - sampleTime[last] > WINDOW_MILLIS) {
            return false; // it stopped before the lift
        }
        int first = last;
        while (first > 0 && sampleTime[last] - sampleTime[first - 1] <= WINDOW_MILLIS) {
            first--;
        }
        long span = sampleTime[last] - sampleTime[first];
        if (span <= 0) {
            return false;
        }
        double vx = -(sampleX[last] - sampleX[first]) * 1000.0 / span;
        double vy = -(sampleY[last] - sampleY[first]) * 1000.0 / span;
        double speed = Math.hypot(vx, vy);
        if (speed < minFling) {
            return false;
        }
        if (speed > maxFling) {
            vx = vx * maxFling / speed;
            vy = vy * maxFling / speed;
        }
        velocityX = vx;
        velocityY = vy;
        flingTime = time;
        flinging = true;
        return true;
    }

    /** The offset change of a fling since the last call (or since the lift); {0, 0} when it is not flinging. */
    public double[] step(long time) {
        if (!flinging) {
            return new double[] {0, 0};
        }
        double seconds = Math.max(0, time - flingTime) / 1000.0;
        flingTime = time;
        double decay = Math.exp(-FRICTION * seconds);
        double[] delta = {velocityX * (1 - decay) / FRICTION, velocityY * (1 - decay) / FRICTION};
        velocityX *= decay;
        velocityY *= decay;
        if (Math.hypot(velocityX, velocityY) < minFling * 0.4) {
            flinging = false;
        }
        return delta;
    }

    /** Ends the touch and the fling: a content that cannot move any more, or a cancelled touch. */
    public void cancel() {
        down = false;
        scrolling = false;
        flinging = false;
    }

    /** The touch moved out of the slop and is scrolling. */
    public boolean isScrolling() {
        return scrolling;
    }

    public boolean isFlinging() {
        return flinging;
    }

    /** A finger is down and has not moved out of the slop. */
    public boolean isTap() {
        return down && !scrolling;
    }

    private void addSample(long time, double x, double y) {
        if (samples == MAX_SAMPLES) {
            System.arraycopy(sampleTime, 1, sampleTime, 0, MAX_SAMPLES - 1);
            System.arraycopy(sampleX, 1, sampleX, 0, MAX_SAMPLES - 1);
            System.arraycopy(sampleY, 1, sampleY, 0, MAX_SAMPLES - 1);
            samples--;
        }
        sampleTime[samples] = time;
        sampleX[samples] = x;
        sampleY[samples] = y;
        samples++;
    }
}
