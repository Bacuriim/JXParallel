package com.jxparallel.ui.native2d;

import com.jxparallel.ui.text.JXGlyphs;
import com.jxparallel.ui.text.JXTextEngine;

/**
 * Paints into an ARGB pixel array on the CPU: the renderer that needs no OpenGL context, for form
 * screens where the context (tens of MB of driver memory per process) costs more than painting.
 *
 * <p>Shapes are antialiased by coverage from a signed distance, computed only where an edge passes:
 * the inside of a rectangle is filled a row at a time. Text is shaped by {@link JXTextEngine} (the
 * same HarfBuzz positions the layout measures) and its glyphs rasterized by FreeType once
 * ({@link JXGlyphs}). Layers multiply the opacity of what they contain (no offscreen group,
 * no blur). Coordinates are window pixels, multiplied by {@code scale} for high-DPI frames.
 * Reused from frame to frame ({@link #reset}); display thread only.
 */
final class JXSoftwarePainter implements JXPainter {
    private int[] px;
    private int width;
    private int height;
    private float scale;
    /** Current clip, in device pixels: [x0, y0, x1, y1). */
    private int cx0;
    private int cy0;
    private int cx1;
    private int cy1;
    /** Layer opacity, 0..256. */
    private int alpha = 256;
    /** Saved clips and opacities, five ints per {@link #clip} or {@link #layer}. */
    private int[] saved = new int[5 * 32];
    private int depth;
    private final Object[] shaped = new Object[3];

    JXSoftwarePainter(int[] pixels, int width, int height, float scale) {
        reset(pixels, width, height, scale, 0, 0, width, height);
    }

    JXSoftwarePainter() {
    }

    /** Starts painting a frame: only pixels inside [x0, x1) x [y0, y1) are touched. */
    void reset(int[] pixels, int frameWidth, int frameHeight, float frameScale, int x0, int y0, int x1, int y1) {
        px = pixels;
        width = frameWidth;
        height = frameHeight;
        scale = frameScale;
        cx0 = Math.max(0, x0);
        cy0 = Math.max(0, y0);
        cx1 = Math.min(frameWidth, x1);
        cy1 = Math.min(frameHeight, y1);
        alpha = 256;
        depth = 0;
    }

    /** Fills the clip (the whole frame unless {@link #reset} narrowed it) with an opaque color. */
    void clear(int argb) {
        int c = argb | 0xFF000000;
        for (int y = cy0; y < cy1; y++) {
            java.util.Arrays.fill(px, y * width + cx0, y * width + cx1, c);
        }
    }

    // ---- rectangles ------------------------------------------------------------------------------

    @Override
    public void fillRect(float x, float y, float w, float h, int argb) {
        rect(x * scale, y * scale, (x + w) * scale, (y + h) * scale, argb, argb);
    }

    /** An axis-aligned rectangle with fractional edges: exact coverage, rows blended at once inside. */
    private void rect(float x0, float y0, float x1, float y1, int top, int bottom) {
        int ix0 = Math.max(cx0, (int) Math.floor(x0));
        int ix1 = Math.min(cx1, (int) Math.ceil(x1));
        int iy0 = Math.max(cy0, (int) Math.floor(y0));
        int iy1 = Math.min(cy1, (int) Math.ceil(y1));
        if (ix0 >= ix1 || iy0 >= iy1) {
            return;
        }
        float h = y1 - y0;
        // whole pixels inside the rectangle on x, where only the row coverage counts
        int fx0 = Math.min(ix1, Math.max(ix0, (int) Math.ceil(x0)));
        int fx1 = Math.max(fx0, Math.min(ix1, (int) Math.floor(x1)));
        for (int y = iy0; y < iy1; y++) {
            float cy = Math.min(y + 1, y1) - Math.max(y, y0);
            int color = top == bottom ? top : mix(top, bottom, h <= 0 ? 0 : ((y + 0.5f) - y0) / h);
            int row = y * width;
            for (int x = ix0; x < fx0; x++) {
                blend(row + x, color, cy * (Math.min(x + 1, x1) - Math.max(x, x0)));
            }
            span(row + fx0, row + fx1, color, cov256(cy));
            for (int x = fx1; x < ix1; x++) {
                blend(row + x, color, cy * (Math.min(x + 1, x1) - Math.max(x, x0)));
            }
        }
    }

    @Override
    public void fillRoundRect(float x, float y, float w, float h, float radius, int argb) {
        roundRect(x, y, w, h, radius, argb, argb, -1);
    }

    @Override
    public void fillRoundRectGradient(float x, float y, float w, float h, float radius, int top, int bottom) {
        roundRect(x, y, w, h, radius, top, bottom, -1);
    }

    @Override
    public void strokeRoundRect(float x, float y, float w, float h, float radius, float width, int argb) {
        roundRect(x, y, w, h, radius, argb, argb, width);
    }

    /**
     * A rounded rectangle, filled ({@code stroke < 0}) or outlined inside its bounds. Rows away from
     * the corners and, for an outline, the interior between its sides are skipped or filled directly.
     */
    private void roundRect(float x, float y, float w, float h, float radius, int top, int bottom, float stroke) {
        float x0 = x * scale;
        float y0 = y * scale;
        float x1 = (x + w) * scale;
        float y1 = (y + h) * scale;
        float r = Math.max(0, Math.min(radius * scale, Math.min(x1 - x0, y1 - y0) / 2));
        float s = stroke * scale;
        if (r < 0.5f && stroke < 0) {
            rect(x0, y0, x1, y1, top, bottom);
            return;
        }
        if (r < 0.5f && s > 0) { // square outline: four rectangles
            rect(x0, y0, x1, y0 + s, top, top);
            rect(x0, y1 - s, x1, y1, bottom, bottom);
            rect(x0, y0 + s, x0 + s, y1 - s, top, bottom);
            rect(x1 - s, y0 + s, x1, y1 - s, top, bottom);
            return;
        }
        int ix0 = Math.max(cx0, (int) Math.floor(x0));
        int ix1 = Math.min(cx1, (int) Math.ceil(x1));
        int iy0 = Math.max(cy0, (int) Math.floor(y0));
        int iy1 = Math.min(cy1, (int) Math.ceil(y1));
        if (ix0 >= ix1 || iy0 >= iy1) {
            return;
        }
        float hx = (x1 - x0) / 2;
        float hy = (y1 - y0) / 2;
        float mx = x0 + hx;
        float my = y0 + hy;
        float ir = Math.max(0, r - s);
        float edge = Math.max(r, s) + 1; // band near the border where coverage is computed
        for (int yy = iy0; yy < iy1; yy++) {
            float py = yy + 0.5f;
            int color = top == bottom ? top : mix(top, bottom, ((py) - y0) / Math.max(1e-3f, y1 - y0));
            int row = yy * width;
            boolean bandRow = py < y0 + edge || py > y1 - edge;
            int xa = ix0;
            int xb = ix1;
            if (!bandRow) {
                // the middle rows: only the left and right borders need coverage
                int left = Math.min(ix1, (int) Math.ceil(x0 + edge));
                int right = Math.max(left, (int) Math.floor(x1 - edge));
                for (int xx = ix0; xx < left; xx++) {
                    pixel(row, xx, py, mx, my, hx, hy, r, ir, s, color);
                }
                if (stroke < 0) {
                    span(row + Math.max(left, ix0), row + Math.min(right, ix1), color, 256);
                }
                xa = Math.max(right, ix0);
                xb = ix1;
            }
            for (int xx = xa; xx < xb; xx++) {
                pixel(row, xx, py, mx, my, hx, hy, r, ir, s, color);
            }
        }
    }

    private void pixel(int row, int x, float py, float mx, float my, float hx, float hy, float r, float ir, float s, int color) {
        float px0 = x + 0.5f;
        float d = roundBox(px0 - mx, py - my, hx, hy, r);
        float cov = clamp01(0.5f - d);
        if (s > 0 && cov > 0) {
            float di = roundBox(px0 - mx, py - my, hx - s, hy - s, ir);
            cov -= clamp01(0.5f - di);
        }
        if (cov > 0) {
            blend(row + x, color, cov);
        }
    }

    /** Signed distance to a rounded box centred at the origin (half sizes hx, hy, corner radius r). */
    private static float roundBox(float x, float y, float hx, float hy, float r) {
        if (hx <= 0 || hy <= 0) {
            return 1e9f;
        }
        float qx = Math.abs(x) - (hx - r);
        float qy = Math.abs(y) - (hy - r);
        float ox = Math.max(qx, 0);
        float oy = Math.max(qy, 0);
        return (float) Math.sqrt(ox * ox + oy * oy) + Math.min(Math.max(qx, qy), 0) - r;
    }

    // ---- ovals, arcs, lines, triangles -----------------------------------------------------------

    @Override
    public void fillOval(float x, float y, float w, float h, int argb) {
        oval(x, y, w, h, -1, argb, 0, 360);
    }

    @Override
    public void strokeOval(float x, float y, float w, float h, float width, int argb) {
        oval(x, y, w, h, width, argb, 0, 360);
    }

    @Override
    public void strokeArc(float x, float y, float w, float h, float startDegrees, float sweepDegrees, float width, int argb) {
        oval(x, y, w, h, width, argb, startDegrees, sweepDegrees);
    }

    /** An ellipse filled, or its outline of {@code stroke} centred on the edge, optionally only an arc. */
    private void oval(float x, float y, float w, float h, float stroke, int argb, float start, float sweep) {
        float rx = w * scale / 2;
        float ry = h * scale / 2;
        float mx = x * scale + rx;
        float my = y * scale + ry;
        float s = stroke * scale;
        float pad = s > 0 ? s / 2 + 1 : 1;
        int ix0 = Math.max(cx0, (int) Math.floor(mx - rx - pad));
        int ix1 = Math.min(cx1, (int) Math.ceil(mx + rx + pad));
        int iy0 = Math.max(cy0, (int) Math.floor(my - ry - pad));
        int iy1 = Math.min(cy1, (int) Math.ceil(my + ry + pad));
        boolean arc = Math.abs(sweep) < 360;
        double a0 = Math.toRadians(start);
        double span = Math.toRadians(Math.abs(sweep));
        double dir = Math.signum(sweep == 0 ? 1 : sweep);
        for (int yy = iy0; yy < iy1; yy++) {
            float py = yy + 0.5f - my;
            int row = yy * width;
            for (int xx = ix0; xx < ix1; xx++) {
                float pxx = xx + 0.5f - mx;
                float d = ellipse(pxx, py, rx, ry);
                float cov = s > 0 ? clamp01(0.5f - (Math.abs(d) - s / 2)) : clamp01(0.5f - d);
                if (cov <= 0) {
                    continue;
                }
                if (arc) {
                    double a = Math.atan2(py, pxx); // y down: clockwise from 3 o'clock, as the API
                    double rel = ((a - a0) * dir) % (2 * Math.PI);
                    if (rel < 0) {
                        rel += 2 * Math.PI;
                    }
                    if (rel > span) {
                        continue;
                    }
                }
                blend(row + xx, argb, cov);
            }
        }
    }

    /** Approximate signed distance to an ellipse (exact for circles). */
    private static float ellipse(float x, float y, float rx, float ry) {
        if (rx <= 0 || ry <= 0) {
            return 1e9f;
        }
        if (Math.abs(rx - ry) < 1e-3f) {
            return (float) Math.sqrt(x * x + y * y) - rx;
        }
        float k0 = (float) Math.sqrt((x / rx) * (x / rx) + (y / ry) * (y / ry));
        float k1 = (float) Math.sqrt((x / (rx * rx)) * (x / (rx * rx)) + (y / (ry * ry)) * (y / (ry * ry)));
        return k1 == 0 ? -Math.min(rx, ry) : k0 * (k0 - 1) / k1;
    }

    @Override
    public void line(float x1, float y1, float x2, float y2, float strokeWidth, int argb) {
        float ax = x1 * scale;
        float ay = y1 * scale;
        float bx = x2 * scale;
        float by = y2 * scale;
        float hw = Math.max(0.5f, strokeWidth * scale / 2);
        if (ay == by || ax == bx) { // axis-aligned: a rectangle
            rect(Math.min(ax, bx) - (ax == bx ? hw : 0), Math.min(ay, by) - (ay == by ? hw : 0),
                    Math.max(ax, bx) + (ax == bx ? hw : 0), Math.max(ay, by) + (ay == by ? hw : 0), argb, argb);
            return;
        }
        float dx = bx - ax;
        float dy = by - ay;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        float ux = dx / len;
        float uy = dy / len;
        int ix0 = Math.max(cx0, (int) Math.floor(Math.min(ax, bx) - hw - 1));
        int ix1 = Math.min(cx1, (int) Math.ceil(Math.max(ax, bx) + hw + 1));
        int iy0 = Math.max(cy0, (int) Math.floor(Math.min(ay, by) - hw - 1));
        int iy1 = Math.min(cy1, (int) Math.ceil(Math.max(ay, by) + hw + 1));
        for (int yy = iy0; yy < iy1; yy++) {
            int row = yy * width;
            for (int xx = ix0; xx < ix1; xx++) {
                float qx = xx + 0.5f - ax;
                float qy = yy + 0.5f - ay;
                float along = qx * ux + qy * uy;
                float across = Math.abs(-qx * uy + qy * ux);
                // butt caps: a box along the segment
                float d = Math.max(across - hw, Math.max(-along, along - len));
                float cov = clamp01(0.5f - d);
                if (cov > 0) {
                    blend(row + xx, argb, cov);
                }
            }
        }
    }

    @Override
    public void fillTriangle(float x0, float y0, float x1, float y1, float x2, float y2, int argb) {
        float[] xs = {x0 * scale, x1 * scale, x2 * scale};
        float[] ys = {y0 * scale, y1 * scale, y2 * scale};
        float area = (xs[1] - xs[0]) * (ys[2] - ys[0]) - (xs[2] - xs[0]) * (ys[1] - ys[0]);
        float sign = area < 0 ? -1 : 1;
        int ix0 = Math.max(cx0, (int) Math.floor(Math.min(xs[0], Math.min(xs[1], xs[2])) - 1));
        int ix1 = Math.min(cx1, (int) Math.ceil(Math.max(xs[0], Math.max(xs[1], xs[2])) + 1));
        int iy0 = Math.max(cy0, (int) Math.floor(Math.min(ys[0], Math.min(ys[1], ys[2])) - 1));
        int iy1 = Math.min(cy1, (int) Math.ceil(Math.max(ys[0], Math.max(ys[1], ys[2])) + 1));
        for (int yy = iy0; yy < iy1; yy++) {
            int row = yy * width;
            for (int xx = ix0; xx < ix1; xx++) {
                float pxx = xx + 0.5f;
                float pyy = yy + 0.5f;
                float d = -1e9f;
                for (int e = 0; e < 3; e++) {
                    int n = (e + 1) % 3;
                    float ex = xs[n] - xs[e];
                    float ey = ys[n] - ys[e];
                    float el = (float) Math.sqrt(ex * ex + ey * ey);
                    if (el == 0) {
                        continue;
                    }
                    // distance outside this edge (positive outside), for a convex shape the max of the three
                    float out = -sign * ((pxx - xs[e]) * ey - (pyy - ys[e]) * ex) / el;
                    d = Math.max(d, -out);
                }
                float cov = clamp01(0.5f - d);
                if (cov > 0) {
                    blend(row + xx, argb, cov);
                }
            }
        }
    }

    // ---- text and images -------------------------------------------------------------------------

    @Override
    public void text(String value, float x, float baseline, float size, boolean bold, int argb) {
        JXTextEngine engine = JXTextEngine.get(bold);
        int n = engine.glyphs(value, size, shaped);
        if (n == 0) {
            return;
        }
        int[] ids = (int[]) shaped[0];
        float[] xs = (float[]) shaped[1];
        float[] ys = (float[]) shaped[2];
        float px0 = x * scale;
        float py0 = baseline * scale;
        JXGlyphs cache = JXGlyphs.get(bold);
        for (int i = 0; i < n; i++) {
            JXGlyphs.Glyph g = cache.glyph(ids[i], size * scale);
            if (g == null || g.width == 0) {
                continue;
            }
            int gx = Math.round(px0 + xs[i] * scale) + g.left;
            int gy = Math.round(py0 + ys[i] * scale) - g.top;
            for (int row = 0; row < g.height; row++) {
                int yy = gy + row;
                if (yy < cy0 || yy >= cy1) {
                    continue;
                }
                int dst = yy * width;
                int src = row * g.width;
                for (int col = 0; col < g.width; col++) {
                    int xx = gx + col;
                    int a = g.alpha[src + col] & 0xFF;
                    if (a != 0 && xx >= cx0 && xx < cx1) {
                        blend(dst + xx, argb, a + (a >> 7));
                    }
                }
            }
        }
    }

    @Override
    public void image(int[] pixels, int imageWidth, int imageHeight, float x, float y, float w, float h) {
        float x0 = x * scale;
        float y0 = y * scale;
        float dw = w * scale;
        float dh = h * scale;
        if (dw <= 0 || dh <= 0 || imageWidth <= 0 || imageHeight <= 0) {
            return;
        }
        int ix0 = Math.max(cx0, (int) Math.floor(x0));
        int ix1 = Math.min(cx1, (int) Math.ceil(x0 + dw));
        int iy0 = Math.max(cy0, (int) Math.floor(y0));
        int iy1 = Math.min(cy1, (int) Math.ceil(y0 + dh));
        float sx = imageWidth / dw;
        float sy = imageHeight / dh;
        for (int yy = iy0; yy < iy1; yy++) {
            float v = (yy + 0.5f - y0) * sy - 0.5f;
            int row = yy * width;
            for (int xx = ix0; xx < ix1; xx++) {
                float u = (xx + 0.5f - x0) * sx - 0.5f;
                int c = bilinear(pixels, imageWidth, imageHeight, u, v);
                int a = c >>> 24;
                if (a != 0) {
                    blend(row + xx, c | 0xFF000000, a + (a >> 7));
                }
            }
        }
    }

    private static int bilinear(int[] p, int w, int h, float u, float v) {
        int x0 = (int) Math.floor(u);
        int y0 = (int) Math.floor(v);
        float fx = u - x0;
        float fy = v - y0;
        int c00 = at(p, w, h, x0, y0);
        int c10 = at(p, w, h, x0 + 1, y0);
        int c01 = at(p, w, h, x0, y0 + 1);
        int c11 = at(p, w, h, x0 + 1, y0 + 1);
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            float top = ((c00 >>> shift) & 0xFF) * (1 - fx) + ((c10 >>> shift) & 0xFF) * fx;
            float bottom = ((c01 >>> shift) & 0xFF) * (1 - fx) + ((c11 >>> shift) & 0xFF) * fx;
            out |= (Math.round(top * (1 - fy) + bottom * fy) & 0xFF) << shift;
        }
        return out;
    }

    private static int at(int[] p, int w, int h, int x, int y) {
        return p[Math.max(0, Math.min(h - 1, y)) * w + Math.max(0, Math.min(w - 1, x))];
    }

    // ---- state -----------------------------------------------------------------------------------

    @Override
    public void clip(float x, float y, float w, float h) {
        push();
        cx0 = Math.max(cx0, (int) Math.floor(x * scale));
        cy0 = Math.max(cy0, (int) Math.floor(y * scale));
        cx1 = Math.min(cx1, (int) Math.ceil((x + w) * scale));
        cy1 = Math.min(cy1, (int) Math.ceil((y + h) * scale));
    }

    @Override
    public void layer(float opacity, float blurRadius) {
        push();
        alpha = alpha * Math.round(256 * Math.max(0, Math.min(1, opacity))) >> 8;
    }

    @Override
    public void restore() {
        depth -= 5;
        cx0 = saved[depth];
        cy0 = saved[depth + 1];
        cx1 = saved[depth + 2];
        cy1 = saved[depth + 3];
        alpha = saved[depth + 4];
    }

    private void push() {
        if (depth + 5 > saved.length) {
            saved = java.util.Arrays.copyOf(saved, saved.length * 2);
        }
        saved[depth] = cx0;
        saved[depth + 1] = cy0;
        saved[depth + 2] = cx1;
        saved[depth + 3] = cy1;
        saved[depth + 4] = alpha;
        depth += 5;
    }

    // ---- pixels ----------------------------------------------------------------------------------

    private static int cov256(float coverage) {
        return coverage <= 0 ? 0 : coverage >= 1 ? 256 : (int) (coverage * 256 + 0.5f);
    }

    private void blend(int i, int argb, float coverage) {
        blend(i, argb, cov256(coverage));
    }

    /** Source-over of {@code argb} with {@code coverage} (0..256, and the layer opacity) onto pixel {@code i}. */
    private void blend(int i, int argb, int coverage) {
        int a = ((argb >>> 24) * coverage >> 8) * alpha >> 8; // 0..255
        if (a >= 255) {
            px[i] = argb | 0xFF000000;
        } else if (a > 0) {
            px[i] = over(px[i], argb, a);
        }
    }

    /** One color over the pixels [from, to) of a row: filled when opaque, else blended with one alpha. */
    private void span(int from, int to, int argb, int coverage) {
        if (from >= to) {
            return;
        }
        int a = ((argb >>> 24) * coverage >> 8) * alpha >> 8;
        if (a >= 255) {
            java.util.Arrays.fill(px, from, to, argb | 0xFF000000);
        } else if (a > 0) {
            for (int i = from; i < to; i++) {
                px[i] = over(px[i], argb, a);
            }
        }
    }

    /** Source-over with alpha 0..255 on red and blue in one multiply, green in another; divided by 255 rounded. */
    static int over(int d, int s, int a) {
        int b = 255 - a;
        int rb = (s & 0xFF00FF) * a + (d & 0xFF00FF) * b + 0x800080;
        int g = (s & 0x00FF00) * a + (d & 0x00FF00) * b + 0x008000;
        rb = ((rb + ((rb >>> 8) & 0xFF00FF)) >>> 8) & 0xFF00FF;
        g = ((g + ((g >>> 8) & 0x00FF00)) >>> 8) & 0x00FF00;
        return 0xFF000000 | rb | g;
    }

    private static int mix(int c0, int c1, float t) {
        t = clamp01(t);
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            out |= (Math.round(((c0 >>> shift) & 0xFF) * (1 - t) + ((c1 >>> shift) & 0xFF) * t) & 0xFF) << shift;
        }
        return out;
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }
}
