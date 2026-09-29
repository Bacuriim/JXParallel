package com.jxparallel.ui.native2d;

import org.lwjgl.nanovg.NVGColor;

/**
 * Paints a {@link JXNativeNode} tree with NanoVG. Used by {@link JXWindow} on 32-bit JVMs and Java 8,
 * where Skia has no working native library. The drawing is {@link JXPaint}, shared with Skia.
 */
public final class JXNanoVGRenderer {
    static final int BACKGROUND = JXSkiaRenderer.BACKGROUND;
    static final String FONT = "sans";
    static final String FONT_BOLD = "sans-bold";

    private JXNanoVGRenderer() {
    }

    /**
     * Lays out and paints the tree between {@code nvgBeginFrame} and {@code nvgEndFrame}.
     * {@code color} is a scratch struct owned by the caller. Painting thread only.
     */
    public static void paint(JXNativeNode root, long vg, NVGColor color, boolean hasFont, int width, int height) {
        paint(root, vg, color, hasFont, hasFont, width, height);
    }

    static void paint(JXNativeNode root, long vg, NVGColor color, boolean hasFont, boolean hasBold, int width, int height) {
        if (root == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        root.layoutForBackend(Math.max(1, width), Math.max(1, height));
        new JXPaint(new JXNanoVGPainter(vg, color, hasFont, hasBold)).paint(root);
    }
}
