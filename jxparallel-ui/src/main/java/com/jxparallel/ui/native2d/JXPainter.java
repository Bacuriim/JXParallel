package com.jxparallel.ui.native2d;

/**
 * Drawing primitives both renderers implement, so every element is painted by one piece of code
 * ({@link JXPaint}) and Skia (64-bit) and NanoVG (32-bit) draw the same shapes. Colors are ARGB.
 * Coordinates are window pixels. Not thread safe: one painter per frame, on the display thread.
 */
interface JXPainter {
    void fillRect(float x, float y, float w, float h, int argb);

    void fillRoundRect(float x, float y, float w, float h, float radius, int argb);

    /** Vertical gradient from {@code top} to {@code bottom}. */
    void fillRoundRectGradient(float x, float y, float w, float h, float radius, int top, int bottom);

    /** A rectangle outline of {@code width} drawn inside the bounds. */
    void strokeRoundRect(float x, float y, float w, float h, float radius, float width, int argb);

    void fillOval(float x, float y, float w, float h, int argb);

    void strokeOval(float x, float y, float w, float h, float width, int argb);

    /** Arc of a circle inside the box, clockwise from {@code startDegrees} (0 = 3 o'clock). */
    void strokeArc(float x, float y, float w, float h, float startDegrees, float sweepDegrees, float width, int argb);

    void line(float x1, float y1, float x2, float y2, float width, int argb);

    void fillTriangle(float x0, float y0, float x1, float y1, float x2, float y2, int argb);

    /** Shaped text starting at x with its baseline at {@code baseline}. */
    void text(String value, float x, float baseline, float size, boolean bold, int argb);

    /** Draws ARGB pixels (row by row) scaled into the box; {@code pixels} identity is the cache key. */
    void image(int[] pixels, int imageWidth, int imageHeight, float x, float y, float w, float h);

    /** Pushes a clip; every {@code clip} must be matched by {@link #restore}. */
    void clip(float x, float y, float w, float h);

    /** Pushes a layer drawn with this opacity (and a Gaussian blur radius where supported); matched by {@link #restore}. */
    void layer(float opacity, float blurRadius);

    void restore();
}
