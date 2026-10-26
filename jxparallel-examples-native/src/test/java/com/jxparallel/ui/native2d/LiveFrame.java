package com.jxparallel.ui.native2d;

/** Test bridge to the damage-tracking software frame, which is package-private: how many rectangles a frame repaints. */
public final class LiveFrame {
    private final JXSoftwareFrame frame = new JXSoftwareFrame(true);

    /** Paints the laid-out tree; returns the number of repainted rectangles (0: the frame is unchanged). */
    public int paint(JXNativeNode laidOut, int width, int height) {
        return frame.paint(laidOut, width, height, 1f);
    }
}
