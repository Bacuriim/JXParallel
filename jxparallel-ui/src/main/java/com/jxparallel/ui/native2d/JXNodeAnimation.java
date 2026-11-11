package com.jxparallel.ui.native2d;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import com.jxparallel.ui.JXTransitions;

/**
 * The animated values of one node: for each of the props in {@link JXTransitions#PROPERTIES} that is moving, where it
 * is now. {@link JXNativeNode#getProperty} answers with them instead of the props, so painting, transforms and picking
 * all read the same value. A reconcile that changes a prop that has a {@code transition} starts a track from the value
 * shown to the new prop; a new node with {@code enter} starts one from the entry value. A track starts counting at the
 * first frame that sees it (so a slow reconcile does not eat the animation), and when it is over the prop answers again.
 *
 * <p>Display thread only, like the tree it belongs to.
 */
final class JXNodeAnimation {
    private static final long PENDING = Long.MIN_VALUE;
    private static final AtomicInteger STARTS = new AtomicInteger();

    /** Bumped each time a track starts, in any tree: a window ticks its tree when this moved. */
    static int starts() {
        return STARTS.get();
    }

    private static final class Track {
        final boolean color;
        final double from;
        final double to;
        final long durationNanos;
        final long delayNanos;
        final JXTransitions.Easing easing;
        /** When the track's delay is over, or PENDING until the first frame. */
        long begin = PENDING;

        Track(boolean color, double from, double to, JXTransitions.Item item) {
            this.color = color;
            this.from = from;
            this.to = to;
            this.durationNanos = item.durationNanos;
            this.delayNanos = item.delayNanos;
            this.easing = item.easing;
        }
    }

    private final Track[] tracks = new Track[JXTransitions.PROPERTIES.size()];
    /** Where each moving prop is now (a Double, or an Integer color); null when it is not moving. */
    private final Object[] shown = new Object[JXTransitions.PROPERTIES.size()];
    private int active;

    /** The animated value of the named prop, or null when it is not animated and the prop holds. */
    Object value(String name) {
        int index = JXTransitions.indexOf(name);
        return index < 0 ? null : shown[index];
    }

    boolean isActive() {
        return active > 0;
    }

    /** Moves every track to {@code nowNanos}; returns whether any is still running. */
    boolean advance(long nowNanos) {
        for (int i = 0; i < tracks.length; i++) {
            Track t = tracks[i];
            if (t == null) {
                continue;
            }
            if (t.begin == PENDING) {
                t.begin = nowNanos + t.delayNanos;
            }
            long elapsed = nowNanos - t.begin;
            if (elapsed >= t.durationNanos) {
                tracks[i] = null;
                shown[i] = null;
                active--;
            } else if (elapsed < 0) {
                shown[i] = value(t, 0);
            } else {
                shown[i] = value(t, t.easing.apply((double) elapsed / t.durationNanos));
            }
        }
        return active > 0;
    }

    private static Object value(Track t, double progress) {
        if (!t.color) {
            return Double.valueOf(t.from + (t.to - t.from) * progress);
        }
        int a = (int) t.from;
        int b = (int) t.to;
        int out = 0;
        for (int shift = 24; shift >= 0; shift -= 8) { // each of alpha, red, green, blue on its own
            int from = (a >>> shift) & 0xFF;
            int to = (b >>> shift) & 0xFF;
            int channel = (int) Math.round(from + (to - from) * progress);
            out |= Math.max(0, Math.min(255, channel)) << shift;
        }
        return Integer.valueOf(out);
    }

    private void drop(int index) {
        if (tracks[index] != null) {
            tracks[index] = null;
            shown[index] = null;
            active--;
        }
    }

    private void start(int index, Object from, Object to, JXTransitions.Item item) {
        boolean color = index >= JXTransitions.FIRST_COLOR;
        double a = ((Number) from).doubleValue();
        double b = ((Number) to).doubleValue();
        boolean jump = !JXAnimationClock.enabled() || a == b || item.durationNanos == 0 && item.delayNanos == 0;
        if (jump) {
            drop(index);
            return;
        }
        if (tracks[index] == null) {
            active++;
        }
        tracks[index] = new Track(color, a, b, item);
        shown[index] = color ? (Object) Integer.valueOf((int) a) : Double.valueOf(a);
        STARTS.incrementAndGet();
    }

    private static Object number(Object value, int index) {
        if (value == null && index < JXTransitions.FIRST_COLOR) {
            return Double.valueOf(JXTransitions.defaultValue(index));
        }
        if (index < JXTransitions.FIRST_COLOR) {
            return value instanceof Number ? value : null;
        }
        return value instanceof Integer ? value : null; // a color needs both ends
    }

    /**
     * A reconcile of {@code node} from props {@code previous} to {@code next}: starts, retargets or drops the tracks of
     * the props that changed. An unrelated prop changing touches nothing.
     */
    static void reconciled(JXNativeNode node, Map<String, Object> previous, Map<String, Object> next) {
        Object declared = next.get("transition");
        JXNodeAnimation animation = node.animation();
        if (declared != null && !(declared instanceof String)) {
            throw new IllegalArgumentException("transition must be a string, such as \"opacity 200ms\", not " + declared);
        }
        // without a transition a changed prop jumps, and whatever was moving it (an enter) stops
        JXTransitions.Transition transition = declared == null ? null : JXTransitions.transition((String) declared);
        for (int i = 0; i < JXTransitions.PROPERTIES.size(); i++) {
            String name = JXTransitions.PROPERTIES.get(i);
            Object before = previous.get(name);
            Object after = next.get(name);
            if (Objects.equals(before, after)) {
                continue; // not this prop: a running track goes on
            }
            JXTransitions.Item item = transition == null ? null : transition.forProperty(i);
            Object to = number(after, i);
            Object from = animation != null && animation.shown[i] != null ? animation.shown[i] : number(before, i);
            if (item == null || to == null || from == null) {
                if (animation != null) {
                    animation.drop(i); // no transition for it: it jumps
                }
                continue;
            }
            if (animation == null) {
                animation = node.ensureAnimation();
            }
            animation.start(i, from, to, item);
        }
        if (animation != null && !animation.isActive()) {
            node.clearAnimation();
        }
    }

    /** A new node whose props carry {@code enter}: starts the tracks from the entry values to what the props say. */
    static void entered(JXNativeNode node, Map<String, Object> props) {
        Object declared = props.get("enter");
        if (declared == null) {
            return;
        }
        if (!(declared instanceof String)) {
            throw new IllegalArgumentException("enter must be a string, such as \"opacity 0 200ms\", not " + declared);
        }
        JXTransitions.Enter enter = JXTransitions.enter((String) declared);
        JXNodeAnimation animation = null;
        for (JXTransitions.Item item : enter.items()) {
            Object to = number(props.get(JXTransitions.PROPERTIES.get(item.property)), item.property);
            if (to == null) {
                continue; // a color it has none of: nothing to settle on
            }
            if (animation == null) {
                animation = node.ensureAnimation();
            }
            animation.start(item.property, item.from, to, item);
        }
        if (animation != null && !animation.isActive()) {
            node.clearAnimation();
        }
    }
}
