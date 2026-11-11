package com.jxparallel.ui.native2d;

/**
 * What a window does about animations at each frame: advance them to the frame's time, and say whether another frame
 * is needed. It is a plain object so that the rule "keep asking for frames while something moves, then stop" can be
 * tested without a window. Display thread only.
 */
final class JXAnimationDriver {
    private boolean animating;
    private int seenStarts = -1;

    /**
     * Advances the animations of {@code root} to {@code nowNanos}, before the frame paints. Returns true when something
     * is still moving, so the window must paint again; false once everything has settled (or when nothing ever moved:
     * the tree is not even walked then).
     */
    boolean step(JXNativeNode root, long nowNanos) {
        if (root == null) {
            animating = false;
            return false;
        }
        int starts = JXNodeAnimation.starts();
        if (!animating && starts == seenStarts) {
            return false;
        }
        seenStarts = starts;
        animating = root.tickAnimations(nowNanos);
        return animating;
    }

    boolean isAnimating() {
        return animating;
    }
}
