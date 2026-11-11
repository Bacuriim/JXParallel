package com.jxparallel.ui.native2d;

/**
 * Hands a painter boxes that are never negative. {@link JXPaint} insets a control's box by its borders
 * and padding (width - 2, height - 6); a control laid out smaller than that (a ProgressBar in a Scroll
 * with fitToHeight has no height) would give a negative size, which Skia rejects ("Rect::makeXYWH
 * expected h >= 0") on every frame. A box with no room is drawn empty, as JavaFX does.
 */
final class JXNonNegativePainter implements JXPainter {
    private final JXPainter p;

    JXNonNegativePainter(JXPainter painter) {
        this.p = painter;
    }

    private static float size(float value) {
        return value > 0 ? value : 0;
    }

    @Override
    public void fillRect(float x, float y, float w, float h, int argb) {
        p.fillRect(x, y, size(w), size(h), argb);
    }

    @Override
    public void fillRoundRect(float x, float y, float w, float h, float radius, int argb) {
        p.fillRoundRect(x, y, size(w), size(h), radius, argb);
    }

    @Override
    public void fillRoundRectGradient(float x, float y, float w, float h, float radius, int top, int bottom) {
        p.fillRoundRectGradient(x, y, size(w), size(h), radius, top, bottom);
    }

    @Override
    public void strokeRoundRect(float x, float y, float w, float h, float radius, float width, int argb) {
        p.strokeRoundRect(x, y, size(w), size(h), radius, width, argb);
    }

    @Override
    public void fillOval(float x, float y, float w, float h, int argb) {
        p.fillOval(x, y, size(w), size(h), argb);
    }

    @Override
    public void strokeOval(float x, float y, float w, float h, float width, int argb) {
        p.strokeOval(x, y, size(w), size(h), width, argb);
    }

    @Override
    public void strokeArc(float x, float y, float w, float h, float startDegrees, float sweepDegrees, float width, int argb) {
        p.strokeArc(x, y, size(w), size(h), startDegrees, sweepDegrees, width, argb);
    }

    @Override
    public void line(float x1, float y1, float x2, float y2, float width, int argb) {
        p.line(x1, y1, x2, y2, width, argb);
    }

    @Override
    public void fillTriangle(float x0, float y0, float x1, float y1, float x2, float y2, int argb) {
        p.fillTriangle(x0, y0, x1, y1, x2, y2, argb);
    }

    @Override
    public void text(String value, float x, float baseline, float size, boolean bold, int argb) {
        p.text(value, x, baseline, size, bold, argb);
    }

    @Override
    public void text(String value, float x, float baseline, float size, com.jxparallel.ui.text.JXFont font, int argb) {
        p.text(value, x, baseline, size, font, argb);
    }

    @Override
    public void image(int[] pixels, int imageWidth, int imageHeight, float x, float y, float w, float h) {
        p.image(pixels, imageWidth, imageHeight, x, y, size(w), size(h));
    }

    @Override
    public void clip(float x, float y, float w, float h) {
        p.clip(x, y, size(w), size(h));
    }

    @Override
    public void layer(float opacity, float blurRadius) {
        p.layer(opacity, blurRadius);
    }

    @Override
    public void fillGradient(float x, float y, float w, float h, float radius, JXGradient gradient) {
        p.fillGradient(x, y, size(w), size(h), radius, gradient);
    }

    @Override
    public void shadow(float x, float y, float w, float h, float radius, float dx, float dy, float blur, int argb) {
        p.shadow(x, y, size(w), size(h), radius, dx, dy, blur, argb);
    }

    @Override
    public void transform(float tx, float ty, float sx, float sy, float degrees, float cx, float cy) {
        p.transform(tx, ty, sx, sy, degrees, cx, cy);
    }

    @Override
    public void restore() {
        p.restore();
    }
}
