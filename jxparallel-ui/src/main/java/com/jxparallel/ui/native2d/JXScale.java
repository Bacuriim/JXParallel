package com.jxparallel.ui.native2d;

import java.util.Locale;

/**
 * HiDPI arithmetic of {@link JXWindow}, kept free of GLFW so it can be tested.
 *
 * <p>Three units are in play. <em>Logical pixels</em> are what layout, hit-testing and the application
 * see. <em>Physical pixels</em> are the framebuffer's. <em>Window units</em> are what GLFW's window size
 * and cursor position use: physical pixels on Windows and X11, points (logical, with a denser
 * framebuffer) on macOS and Wayland. The scale is physical pixels per logical pixel; it is the same
 * number on all platforms, and the window units differ only by the measured
 * {@code framebuffer / window} ratio, so no platform has to be detected.
 */
final class JXScale {
    /** {@code -Djx.scale=1.5}: forces the scale of every window. */
    static final String PROPERTY = "jx.scale";
    static final float MIN = 0.25f;
    static final float MAX = 8f;

    private JXScale() {
    }

    /** The forced scale from {@code -Djx.scale}, or 0 when unset or not a usable number. */
    static float forced() {
        return parse(System.getProperty(PROPERTY));
    }

    /** A positive finite number within [{@link #MIN}, {@link #MAX}], else 0 (not forced). A trailing '%' is allowed. */
    static float parse(String text) {
        if (text == null) {
            return 0f;
        }
        String t = text.trim().toLowerCase(Locale.ROOT);
        boolean percent = t.endsWith("%");
        if (percent) {
            t = t.substring(0, t.length() - 1).trim();
        }
        try {
            float value = Float.parseFloat(t);
            if (percent) {
                value /= 100f;
            }
            return valid(value) ? clamp(value) : 0f;
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    /**
     * The scale of a window: the forced one if any, else the monitor's content scale (GLFW's x scale;
     * y differs only on exotic setups), else 1.
     */
    static float resolve(float forced, float contentScaleX, float contentScaleY) {
        if (valid(forced)) {
            return clamp(forced);
        }
        float s = valid(contentScaleX) ? contentScaleX : contentScaleY;
        return valid(s) ? clamp(s) : 1f;
    }

    private static boolean valid(float value) {
        return value > 0f && !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static float clamp(float value) {
        return Math.max(MIN, Math.min(MAX, value));
    }

    /**
     * Logical size covering {@code physical} pixels: {@code ceil(physical / scale)}, with a little slack so
     * that float noise (1100 / 1.1f) does not add a pixel. At scale 1 it is {@code physical}.
     */
    static int logicalExtent(int physical, float scale) {
        if (physical <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(physical / (double) scale - 1e-4));
    }

    /** Physical pixels of a logical extent, rounded to the nearest. */
    static int physicalExtent(double logical, float scale) {
        return (int) Math.round(logical * scale);
    }

    /**
     * Converts a GLFW cursor coordinate (window units) to logical pixels. {@code windowExtent} and
     * {@code framebufferExtent} are the window's size on that axis in window units and in physical pixels.
     */
    static double toLogical(double windowCoordinate, int windowExtent, int framebufferExtent, float scale) {
        return windowCoordinate * ratio(windowExtent, framebufferExtent) / scale;
    }

    /** Window units for a logical extent (the size to give {@code glfwSetWindowSize}), at least 1. */
    static int toWindowUnits(int logical, int windowExtent, int framebufferExtent, float scale) {
        return Math.max(1, (int) Math.round(logical * (double) scale / ratio(windowExtent, framebufferExtent)));
    }

    /** Framebuffer pixels per window unit: 1 on Windows and X11, the content scale on macOS and Wayland. */
    static double ratio(int windowExtent, int framebufferExtent) {
        return windowExtent > 0 && framebufferExtent > 0 ? framebufferExtent / (double) windowExtent : 1.0;
    }
}
