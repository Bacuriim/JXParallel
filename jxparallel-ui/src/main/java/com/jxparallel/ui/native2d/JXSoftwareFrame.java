package com.jxparallel.ui.native2d;

/**
 * The frame of the software renderer, repainted only where it changed: each frame the tree is first
 * walked by {@link JXDamagePainter}, then every damaged rectangle is cleared and painted again, with
 * every drawing call that reaches it, by {@link JXSoftwarePainter}. The pixels outside keep the
 * previous frame's. Display thread only.
 */
final class JXSoftwareFrame {
    /** {@code -Djx.software.damage=false}: repaint the whole frame every time. */
    private static final boolean DAMAGE = !"false".equals(System.getProperty("jx.software.damage"));
    /** {@code -Djx.software.verify=true}: also paint every frame whole and report a pixel that differs. */
    private static final boolean VERIFY = Boolean.getBoolean("jx.software.verify");

    private final JXSoftwarePainter painter = new JXSoftwarePainter();
    private final JXDamagePainter damage = new JXDamagePainter();
    private final JXPaint recorder = new JXPaint(damage);
    private final JXPaint paint = new JXPaint(painter);
    private final boolean useDamage;
    private int[] pixels = new int[0];
    private int[] check = new int[0];

    JXSoftwareFrame() {
        this(DAMAGE);
    }

    JXSoftwareFrame(boolean useDamage) {
        this.useDamage = useDamage;
    }

    /** ARGB, row by row; the frame's {@code width * height} first entries are the image. */
    int[] pixels() {
        return pixels;
    }

    /** The rectangles the last {@link #paint} repainted: x0, y0, x1, y1 each, in frame pixels. */
    int[] damaged() {
        return damage.rects;
    }

    /**
     * Paints the laid-out {@code root} (null: only the background) into a {@code width x height}
     * frame at {@code scale} frame pixels per window pixel. Returns the number of repainted
     * rectangles ({@link #damaged}); 0 when the frame is unchanged.
     */
    int paint(JXNativeNode root, int width, int height, float scale) {
        int n = width * height;
        if (pixels.length < n) {
            pixels = new int[n];
            damage.invalidate();
        }
        damage.begin(width, height, scale);
        if (!useDamage) {
            damage.invalidate();
        } else if (root != null) {
            recorder.paint(root);
        }
        int rects = damage.damage();
        int[] r = damage.rects;
        for (int i = 0; i < rects; i++) {
            painter.reset(pixels, width, height, scale, r[4 * i], r[4 * i + 1], r[4 * i + 2], r[4 * i + 3]);
            painter.clear(JXSkiaRenderer.BACKGROUND);
            if (root != null) {
                paint.paint(root);
            }
        }
        if (VERIFY && root != null && !verify(root, width, height, scale)) {
            damage.rects[0] = 0;
            damage.rects[1] = 0;
            damage.rects[2] = width;
            damage.rects[3] = height;
            return 1;
        }
        return rects;
    }

    /** False (and the whole frame repainted) when a pixel differs from a whole repaint. */
    private boolean verify(JXNativeNode root, int width, int height, float scale) {
        int n = width * height;
        if (check.length < n) {
            check = new int[n];
        }
        painter.reset(check, width, height, scale, 0, 0, width, height);
        painter.clear(JXSkiaRenderer.BACKGROUND);
        paint.paint(root);
        for (int i = 0; i < n; i++) {
            if (check[i] != pixels[i]) {
                System.err.println("jx.software.verify: pixel " + (i % width) + "," + (i / width)
                        + " is " + Integer.toHexString(pixels[i]) + " but a whole frame paints " + Integer.toHexString(check[i]));
                System.arraycopy(check, 0, pixels, 0, n);
                return false;
            }
        }
        return true;
    }
}
