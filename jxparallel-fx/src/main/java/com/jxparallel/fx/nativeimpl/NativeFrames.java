package com.jxparallel.fx.nativeimpl;

/**
 * Presented frames of native windows, for measurement tools (the TCC benchmarks): the listener
 * runs on the display thread after every buffer swap of any native window, the counterpart of an
 * AnimationTimer pulse in JavaFX. Headless surfaces present nothing.
 */
public final class NativeFrames {
    private static volatile Runnable listener;

    private NativeFrames() {
    }

    /** Sets (or with null removes) the listener; it must be quick, it runs between frames. */
    public static void setListener(Runnable onFrame) {
        listener = onFrame;
    }

    /**
     * Asks every shown native window for one more frame, as a running AnimationTimer keeps JavaFX
     * pulsing; windows only paint when something changed otherwise. Safe from any thread.
     */
    public static void requestFrame() {
        for (NativeScene s : NativeRuntime.SCENES) {
            s.window.requestRender();
        }
    }

    static void presented() {
        Runnable l = listener;
        if (l != null) {
            l.run();
        }
    }
}
