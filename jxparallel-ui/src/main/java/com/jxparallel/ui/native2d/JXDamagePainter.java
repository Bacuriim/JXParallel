package com.jxparallel.ui.native2d;

import java.util.Arrays;

/**
 * Finds the parts of a frame that changed, for the software renderer: paints nothing, but records
 * every drawing call as a hash of its arguments (with the clip and opacity in force) and the pixels it
 * can touch. {@link #damage} compares the calls with the previous frame's.
 *
 * <p>A pixel outside the damage is covered by the same calls, in the same order, as in the previous
 * frame, so it keeps its color: every call that differs, appeared, disappeared or changed place
 * relative to the others adds its old and new bounds. Text bounds are an estimate that can only be
 * too large (no shaping here). Display thread only; reused from frame to frame.
 */
final class JXDamagePainter implements JXPainter {
    /** At most this many damaged rectangles; more are merged. */
    static final int MAX_RECTS = 6;

    private float scale = 1f;
    private int width;
    private int height;
    private int cx0;
    private int cy0;
    private int cx1;
    private int cy1;
    private int alpha = 256;
    private int[] saved = new int[5 * 32];
    private int depth;

    // this frame's calls and the previous frame's (swapped in begin)
    private long[] hashes = new long[1024];
    private int[] bounds = new int[4 * 1024];
    private int count;
    private long[] previousHashes = new long[1024];
    private int[] previousBounds = new int[4 * 1024];
    private int previousCount = -1;

    // previous calls by hash: open addressing over hash -> first index, then a chain of equal hashes
    private long[] tableKeys = new long[2048];
    private int[] tableHeads = new int[2048];
    private int[] chainNext = new int[1024];
    private boolean[] used = new boolean[1024];

    /** Damage of the last {@link #damage}: rectangles x0, y0, x1, y1 in device pixels. */
    final int[] rects = new int[4 * (MAX_RECTS + 1)];
    int rectCount;

    /** Starts recording a frame; the previous one becomes the reference. */
    void begin(int frameWidth, int frameHeight, float frameScale) {
        long[] h = previousHashes;
        previousHashes = hashes;
        hashes = h;
        int[] b = previousBounds;
        previousBounds = bounds;
        bounds = b;
        if (frameWidth != width || frameHeight != height || frameScale != scale) {
            previousCount = -1; // a new size: everything is damage
        } else {
            previousCount = count;
        }
        count = 0;
        width = frameWidth;
        height = frameHeight;
        scale = frameScale;
        cx0 = 0;
        cy0 = 0;
        cx1 = frameWidth;
        cy1 = frameHeight;
        alpha = 256;
        depth = 0;
    }

    /** Forgets the previous frame: the next {@link #damage} is the whole frame. */
    void invalidate() {
        previousCount = -1;
        count = 0;
    }

    /**
     * Compares the recorded frame with the previous one and fills {@link #rects}. Returns the number
     * of rectangles: 0 when nothing changed.
     */
    int damage() {
        rectCount = 0;
        if (previousCount < 0) {
            add(0, 0, width, height);
            return rectCount;
        }
        index();
        int last = -1;
        for (int i = 0; i < count; i++) {
            int match = take(hashes[i]);
            if (match < 0) {
                add(bounds, i);
            } else if (match < last) {
                add(bounds, i); // painted in another order relative to the calls before it
                add(previousBounds, match);
            } else {
                last = match;
            }
        }
        for (int j = 0; j < previousCount; j++) {
            if (!used[j]) {
                add(previousBounds, j);
            }
        }
        long area = 0;
        for (int r = 0; r < rectCount; r++) {
            area += (long) (rects[4 * r + 2] - rects[4 * r]) * (rects[4 * r + 3] - rects[4 * r + 1]);
        }
        if (area * 10 > (long) width * height * 6) { // mostly damaged: one pass is cheaper
            rectCount = 0;
            add(0, 0, width, height);
        }
        return rectCount;
    }

    private void index() {
        int size = tableKeys.length;
        while (size < previousCount * 2) {
            size *= 2;
        }
        if (size != tableKeys.length) {
            tableKeys = new long[size];
            tableHeads = new int[size];
        }
        Arrays.fill(tableHeads, -1);
        if (chainNext.length < previousCount) {
            chainNext = new int[previousHashes.length];
            used = new boolean[previousHashes.length];
        }
        Arrays.fill(used, 0, previousCount, false);
        // backwards, so each chain lists equal calls in painting order
        for (int j = previousCount - 1; j >= 0; j--) {
            int slot = slot(previousHashes[j]);
            chainNext[j] = tableHeads[slot];
            tableKeys[slot] = previousHashes[j];
            tableHeads[slot] = j;
        }
    }

    private int slot(long hash) {
        int mask = tableKeys.length - 1;
        int s = (int) (hash ^ (hash >>> 32)) & mask;
        while (tableHeads[s] != -1 && tableKeys[s] != hash) { // -1: free; -2: a key whose calls are all taken
            s = (s + 1) & mask;
        }
        return s;
    }

    /** The first unmatched previous call with this hash, or -1. */
    private int take(long hash) {
        int s = slot(hash);
        int j = tableHeads[s];
        if (j < 0 || tableKeys[s] != hash) {
            return -1;
        }
        tableHeads[s] = chainNext[j]; // a chain is consumed in order
        if (tableHeads[s] < 0) {
            tableHeads[s] = -2; // emptied, but keeps the probe sequence of other keys going
        }
        used[j] = true;
        return j;
    }

    private void add(int[] from, int i) {
        add(from[4 * i], from[4 * i + 1], from[4 * i + 2], from[4 * i + 3]);
    }

    /** Adds a rectangle, merged with one it touches, or merging the cheapest pair when too many. */
    private void add(int x0, int y0, int x1, int y1) {
        if (x0 >= x1 || y0 >= y1) {
            return;
        }
        for (int r = 0; r < rectCount; r++) {
            int k = 4 * r;
            if (x0 <= rects[k + 2] && rects[k] <= x1 && y0 <= rects[k + 3] && rects[k + 1] <= y1) {
                rects[k] = Math.min(rects[k], x0);
                rects[k + 1] = Math.min(rects[k + 1], y0);
                rects[k + 2] = Math.max(rects[k + 2], x1);
                rects[k + 3] = Math.max(rects[k + 3], y1);
                return;
            }
        }
        int k = 4 * rectCount++;
        rects[k] = x0;
        rects[k + 1] = y0;
        rects[k + 2] = x1;
        rects[k + 3] = y1;
        if (rectCount > MAX_RECTS) {
            mergeCheapestPair();
        }
    }

    private void mergeCheapestPair() {
        int bestA = 0;
        int bestB = 1;
        long best = Long.MAX_VALUE;
        for (int a = 0; a < rectCount; a++) {
            for (int b = a + 1; b < rectCount; b++) {
                long cost = area(union(a, b, 0), union(a, b, 1), union(a, b, 2), union(a, b, 3))
                        - area(rects[4 * a], rects[4 * a + 1], rects[4 * a + 2], rects[4 * a + 3])
                        - area(rects[4 * b], rects[4 * b + 1], rects[4 * b + 2], rects[4 * b + 3]);
                if (cost < best) {
                    best = cost;
                    bestA = a;
                    bestB = b;
                }
            }
        }
        for (int c = 0; c < 4; c++) {
            rects[4 * bestA + c] = union(bestA, bestB, c);
        }
        int last = 4 * (rectCount - 1);
        System.arraycopy(rects, last, rects, 4 * bestB, 4);
        rectCount--;
    }

    private int union(int a, int b, int c) {
        return c < 2 ? Math.min(rects[4 * a + c], rects[4 * b + c]) : Math.max(rects[4 * a + c], rects[4 * b + c]);
    }

    private static long area(int x0, int y0, int x1, int y1) {
        return (long) (x1 - x0) * (y1 - y0);
    }

    // ---- recording -------------------------------------------------------------------------------

    private static long mix(long h, long v) {
        h ^= v;
        h *= 0x9E3779B97F4A7C15L;
        return h ^ (h >>> 29);
    }

    private static long mix(long h, float v) {
        return mix(h, Float.floatToIntBits(v));
    }

    private long start(int op) {
        long h = mix(0x51ED27A3L, op);
        h = mix(h, cx0);
        h = mix(h, cy0);
        h = mix(h, cx1);
        h = mix(h, cy1);
        return mix(h, alpha);
    }

    /** Records a call touching the window-pixel box, with a one-pixel margin for antialiasing. */
    private void record(long hash, float x0, float y0, float x1, float y1) {
        int ix0 = Math.max(cx0, (int) Math.floor(Math.min(x0, x1) * scale) - 1);
        int iy0 = Math.max(cy0, (int) Math.floor(Math.min(y0, y1) * scale) - 1);
        int ix1 = Math.min(cx1, (int) Math.ceil(Math.max(x0, x1) * scale) + 1);
        int iy1 = Math.min(cy1, (int) Math.ceil(Math.max(y0, y1) * scale) + 1);
        if (ix0 >= ix1 || iy0 >= iy1) {
            return; // clipped away: changes nothing on screen
        }
        if (count == hashes.length) {
            hashes = Arrays.copyOf(hashes, count * 2);
            bounds = Arrays.copyOf(bounds, count * 8);
        }
        hashes[count] = hash;
        int k = 4 * count++;
        bounds[k] = ix0;
        bounds[k + 1] = iy0;
        bounds[k + 2] = ix1;
        bounds[k + 3] = iy1;
    }

    private long box(int op, float x, float y, float w, float h) {
        long hash = start(op);
        hash = mix(hash, x);
        hash = mix(hash, y);
        hash = mix(hash, w);
        return mix(hash, h);
    }

    @Override
    public void fillRect(float x, float y, float w, float h, int argb) {
        record(mix(box(1, x, y, w, h), argb), x, y, x + w, y + h);
    }

    @Override
    public void fillRoundRect(float x, float y, float w, float h, float radius, int argb) {
        record(mix(mix(box(2, x, y, w, h), radius), argb), x, y, x + w, y + h);
    }

    @Override
    public void fillRoundRectGradient(float x, float y, float w, float h, float radius, int top, int bottom) {
        record(mix(mix(mix(box(3, x, y, w, h), radius), top), bottom), x, y, x + w, y + h);
    }

    @Override
    public void strokeRoundRect(float x, float y, float w, float h, float radius, float strokeWidth, int argb) {
        record(mix(mix(mix(box(4, x, y, w, h), radius), strokeWidth), argb), x, y, x + w, y + h);
    }

    @Override
    public void fillOval(float x, float y, float w, float h, int argb) {
        record(mix(box(5, x, y, w, h), argb), x, y, x + w, y + h);
    }

    @Override
    public void strokeOval(float x, float y, float w, float h, float strokeWidth, int argb) {
        float pad = strokeWidth / 2;
        record(mix(mix(box(6, x, y, w, h), strokeWidth), argb), x - pad, y - pad, x + w + pad, y + h + pad);
    }

    @Override
    public void strokeArc(float x, float y, float w, float h, float startDegrees, float sweepDegrees, float strokeWidth, int argb) {
        float pad = strokeWidth / 2;
        long hash = mix(mix(mix(mix(box(7, x, y, w, h), startDegrees), sweepDegrees), strokeWidth), argb);
        record(hash, x - pad, y - pad, x + w + pad, y + h + pad);
    }

    @Override
    public void line(float x1, float y1, float x2, float y2, float strokeWidth, int argb) {
        float pad = Math.max(0.5f, strokeWidth / 2);
        record(mix(mix(box(8, x1, y1, x2, y2), strokeWidth), argb),
                Math.min(x1, x2) - pad, Math.min(y1, y2) - pad, Math.max(x1, x2) + pad, Math.max(y1, y2) + pad);
    }

    @Override
    public void fillTriangle(float x0, float y0, float x1, float y1, float x2, float y2, int argb) {
        long hash = mix(mix(mix(box(9, x0, y0, x1, y1), x2), y2), argb);
        record(hash, Math.min(x0, Math.min(x1, x2)), Math.min(y0, Math.min(y1, y2)),
                Math.max(x0, Math.max(x1, x2)), Math.max(y0, Math.max(y1, y2)));
    }

    @Override
    public void text(String value, float x, float baseline, float size, boolean bold, int argb) {
        long hash = mix(mix(mix(box(10, x, baseline, size, bold ? 1 : 0), argb), value.length()), 0);
        for (int i = 0; i < value.length(); i++) {
            hash = mix(hash, value.charAt(i));
        }
        // no glyph is wider than 1.25 em or reaches further than an em around the line
        record(hash, x - size, baseline - 1.5f * size, x + size * (1.25f * value.length() + 1), baseline + size);
    }

    @Override
    public void image(int[] pixels, int imageWidth, int imageHeight, float x, float y, float w, float h) {
        long hash = mix(mix(mix(box(11, x, y, w, h), imageWidth), imageHeight), System.identityHashCode(pixels));
        // the content too, as an application may change the pixels of the same array
        int n = Math.min(pixels.length, imageWidth * imageHeight);
        int step = Math.max(1, n / 16384);
        for (int i = 0; i < n; i += step) {
            hash = mix(hash, pixels[i]);
        }
        record(hash, x, y, x + w, y + h);
    }

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
            saved = Arrays.copyOf(saved, saved.length * 2);
        }
        saved[depth] = cx0;
        saved[depth + 1] = cy0;
        saved[depth + 2] = cx1;
        saved[depth + 3] = cy1;
        saved[depth + 4] = alpha;
        depth += 5;
    }
}
