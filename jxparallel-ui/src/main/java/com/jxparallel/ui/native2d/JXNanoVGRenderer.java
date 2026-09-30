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
     * Makes the JVM compile, before the first frames, the NanoVG functions a frame calls. On the
     * 32-bit client VM the compiled wrapper of a native method is made by the calling thread once
     * the method is hot, and each waited about 15 ms for the busy compiler: the frame that first
     * stroked many borders stalled for 40 to 60 ms (seen with -XX:+PrintCompilation).
     *
     * <p>Display thread, OpenGL context current, for the only part that needs OpenGL: the glyphs of
     * the warm-up text are drawn once so they are in the font atlas. The calls themselves run on a
     * background thread on this throwaway context, which OpenGL never sees there: path functions only
     * record commands, the text's glyphs are already uploaded, frames are cancelled or ended empty.
     * The context is deleted on the display thread afterwards.
     */
    static void warmNatives() {
        final long vg = org.lwjgl.nanovg.NanoVGGL3.nvgCreate(org.lwjgl.nanovg.NanoVGGL3.NVG_ANTIALIAS
                | org.lwjgl.nanovg.NanoVGGL3.NVG_STENCIL_STROKES);
        if (vg == org.lwjgl.system.MemoryUtil.NULL) {
            return;
        }
        String font = com.jxparallel.ui.text.JXTextEngine.get().getFontFile();
        final boolean hasFont = font != null && org.lwjgl.nanovg.NanoVG.nvgCreateFont(vg, FONT, font) >= 0;
        String bold = com.jxparallel.ui.text.JXTextEngine.findBoldFontFile();
        final boolean hasBold = hasFont && bold != null && org.lwjgl.nanovg.NanoVG.nvgCreateFont(vg, FONT_BOLD, bold) >= 0;
        JXNanoVGPainter uploader = new JXNanoVGPainter(vg, NVGColor.create(), hasFont, hasBold);
        org.lwjgl.nanovg.NanoVG.nvgBeginFrame(vg, 1, 1, 1);
        uploader.text(WARM_TEXT, 0, 1, 12, false, 0xFF000000); // rasterizes and uploads the glyphs
        uploader.text(WARM_TEXT, 0, 1, 12, true, 0xFF000000);
        org.lwjgl.nanovg.NanoVG.nvgCancelFrame(vg);
        warmContext = vg;
        warmFont = hasFont;
        warmBold = hasBold;
    }

    /** The throwaway context {@link #warmNatives} prepared, until the first frame starts the calls. */
    private static volatile long warmContext;
    private static boolean warmFont;
    private static boolean warmBold;

    /**
     * Starts the warm-up calls once the first frame is on screen: during start-up they would compete
     * with the application's own compilation and delay that frame. Display thread; once.
     */
    static void startWarmCalls() {
        final long vg = warmContext;
        if (vg == org.lwjgl.system.MemoryUtil.NULL) {
            return;
        }
        warmContext = org.lwjgl.system.MemoryUtil.NULL;
        final boolean hasFont = warmFont;
        final boolean hasBold = warmBold;
        Thread calls = new Thread(() -> {
            try {
                Thread.sleep(WARM_DELAY_MS); // after the application's own start-up compilation
                JXPainter p = new JXNanoVGPainter(vg, NVGColor.create(), hasFont, hasBold);
                org.lwjgl.nanovg.NVGPaint paint = org.lwjgl.nanovg.NVGPaint.create();
                for (int i = 0; i < WARM_CALLS; i++) {
                    // Only what a screen may use for the first time well after it opened (borders,
                    // arcs, ovals, check marks, faded layers, images, the frame pair). Fills, text and
                    // clips are hot within the first frames anyway, and warming them too competes
                    // with the application's own compilation right after the first frame.
                    org.lwjgl.nanovg.NanoVG.nvgBeginFrame(vg, 8, 8, 1);
                    p.layer(0.5f, 1f);
                    p.strokeRoundRect(0, 0, 2, 2, 1, 1, 0xFF000000);
                    p.fillOval(0, 0, 2, 2, 0xFF000000);
                    p.strokeOval(0, 0, 2, 2, 1, 0xFF000000);
                    p.strokeArc(0, 0, 2, 2, 0, 90, 1, 0xFF000000);
                    p.line(0, 0, 1, 1, 1, 0xFF000000);
                    p.fillTriangle(0, 0, 1, 0, 0, 1, 0xFF000000);
                    org.lwjgl.nanovg.NanoVG.nvgImagePattern(vg, 0, 0, 1, 1, 0, 0, 1, paint);
                    p.restore();
                    org.lwjgl.nanovg.NanoVG.nvgCancelFrame(vg); // drops the recorded commands without OpenGL
                    org.lwjgl.nanovg.NanoVG.nvgBeginFrame(vg, 8, 8, 1);
                    org.lwjgl.nanovg.NanoVG.nvgEndFrame(vg); // nothing recorded: no OpenGL call
                }
            } catch (Throwable e) {
                // only a head start: the first frames compile what is left
            } finally {
                JXDisplay.post(() -> org.lwjgl.nanovg.NanoVGGL3.nvgDelete(vg));
            }
        }, "JX warm");
        calls.setDaemon(true);
        calls.setPriority(Thread.MIN_PRIORITY); // leaves the CPU to the application
        calls.start();
    }

    private static final String WARM_TEXT = "Ag";
    private static final long WARM_DELAY_MS = 1000;

    /** Enough calls of each function to pass the client VM's compile threshold (1500 by default). */
    private static final int WARM_CALLS = 1600;

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
