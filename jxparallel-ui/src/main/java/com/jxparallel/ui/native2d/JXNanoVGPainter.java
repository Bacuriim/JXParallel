package com.jxparallel.ui.native2d;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.WeakHashMap;

import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_BASELINE;
import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT;
import static org.lwjgl.nanovg.NanoVG.NVG_CW;
import static org.lwjgl.nanovg.NanoVG.nvgArc;
import static org.lwjgl.nanovg.NanoVG.nvgBeginPath;
import static org.lwjgl.nanovg.NanoVG.nvgClosePath;
import static org.lwjgl.nanovg.NanoVG.nvgCreateImageRGBA;
import static org.lwjgl.nanovg.NanoVG.nvgEllipse;
import static org.lwjgl.nanovg.NanoVG.nvgFill;
import static org.lwjgl.nanovg.NanoVG.nvgFillColor;
import static org.lwjgl.nanovg.NanoVG.nvgFillPaint;
import static org.lwjgl.nanovg.NanoVG.nvgFontFace;
import static org.lwjgl.nanovg.NanoVG.nvgFontSize;
import static org.lwjgl.nanovg.NanoVG.nvgGlobalAlpha;
import static org.lwjgl.nanovg.NanoVG.nvgImagePattern;
import static org.lwjgl.nanovg.NanoVG.nvgIntersectScissor;
import static org.lwjgl.nanovg.NanoVG.nvgLineTo;
import static org.lwjgl.nanovg.NanoVG.nvgLinearGradient;
import static org.lwjgl.nanovg.NanoVG.nvgMoveTo;
import static org.lwjgl.nanovg.NanoVG.nvgRect;
import static org.lwjgl.nanovg.NanoVG.nvgRestore;
import static org.lwjgl.nanovg.NanoVG.nvgRoundedRect;
import static org.lwjgl.nanovg.NanoVG.nvgSave;
import static org.lwjgl.nanovg.NanoVG.nvgStroke;
import static org.lwjgl.nanovg.NanoVG.nvgStrokeColor;
import static org.lwjgl.nanovg.NanoVG.nvgStrokeWidth;
import static org.lwjgl.nanovg.NanoVG.nvgText;
import static org.lwjgl.nanovg.NanoVG.nvgTextAlign;

/** {@link JXPainter} on a NanoVG context. Blur is not available; layers only apply opacity. */
final class JXNanoVGPainter implements JXPainter {
    /** Images uploaded per NanoVG context; display thread only. */
    private static final Map<Long, Map<int[], Integer>> IMAGES = new java.util.HashMap<Long, Map<int[], Integer>>();
    private final long vg;
    private final NVGColor color;
    private final NVGColor color2 = NVGColor.create();
    private final NVGPaint paint = NVGPaint.create();
    private final boolean hasFont;
    private final boolean hasBold;

    JXNanoVGPainter(long vg, NVGColor color, boolean hasFont, boolean hasBold) {
        this.vg = vg;
        this.color = color;
        this.hasFont = hasFont;
        this.hasBold = hasBold;
    }

    static void forget(long vg) {
        IMAGES.remove(vg);
    }

    private static NVGColor rgba(NVGColor c, int argb) {
        return c.r(((argb >> 16) & 0xFF) / 255.0f).g(((argb >> 8) & 0xFF) / 255.0f).b((argb & 0xFF) / 255.0f)
                .a(((argb >>> 24) & 0xFF) / 255.0f);
    }

    private void fill(int argb) {
        nvgFillColor(vg, rgba(color, argb));
        nvgFill(vg);
    }

    @Override
    public void fillRect(float x, float y, float w, float h, int argb) {
        nvgBeginPath(vg);
        nvgRect(vg, x, y, w, h);
        fill(argb);
    }

    @Override
    public void fillRoundRect(float x, float y, float w, float h, float radius, int argb) {
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, w, h, radius);
        fill(argb);
    }

    @Override
    public void fillRoundRectGradient(float x, float y, float w, float h, float radius, int top, int bottom) {
        nvgLinearGradient(vg, x, y, x, y + h, rgba(color, top), rgba(color2, bottom), paint);
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, w, h, radius);
        nvgFillPaint(vg, paint);
        nvgFill(vg);
    }

    @Override
    public void strokeRoundRect(float x, float y, float w, float h, float radius, float width, int argb) {
        float half = width / 2;
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x + half, y + half, w - width, h - width, Math.max(0, radius - half));
        stroke(width, argb);
    }

    private void stroke(float width, int argb) {
        nvgStrokeColor(vg, rgba(color, argb));
        nvgStrokeWidth(vg, width);
        nvgStroke(vg);
    }

    @Override
    public void fillOval(float x, float y, float w, float h, int argb) {
        nvgBeginPath(vg);
        nvgEllipse(vg, x + w / 2, y + h / 2, w / 2, h / 2);
        fill(argb);
    }

    @Override
    public void strokeOval(float x, float y, float w, float h, float width, int argb) {
        float half = width / 2;
        nvgBeginPath(vg);
        nvgEllipse(vg, x + w / 2, y + h / 2, w / 2 - half, h / 2 - half);
        stroke(width, argb);
    }

    @Override
    public void strokeArc(float x, float y, float w, float h, float startDegrees, float sweepDegrees, float width, int argb) {
        float r = Math.min(w, h) / 2 - width / 2;
        float a0 = (float) Math.toRadians(startDegrees);
        float a1 = (float) Math.toRadians(startDegrees + sweepDegrees);
        nvgBeginPath(vg);
        nvgArc(vg, x + w / 2, y + h / 2, r, a0, a1, NVG_CW);
        stroke(width, argb);
    }

    @Override
    public void line(float x1, float y1, float x2, float y2, float width, int argb) {
        nvgBeginPath(vg);
        nvgMoveTo(vg, x1, y1);
        nvgLineTo(vg, x2, y2);
        stroke(width, argb);
    }

    @Override
    public void fillTriangle(float x0, float y0, float x1, float y1, float x2, float y2, int argb) {
        nvgBeginPath(vg);
        nvgMoveTo(vg, x0, y0);
        nvgLineTo(vg, x1, y1);
        nvgLineTo(vg, x2, y2);
        nvgClosePath(vg);
        fill(argb);
    }

    @Override
    public void text(String value, float x, float baseline, float size, boolean bold, int argb) {
        if (!hasFont || value == null || value.isEmpty()) {
            return;
        }
        nvgFontFace(vg, bold && hasBold ? JXNanoVGRenderer.FONT_BOLD : JXNanoVGRenderer.FONT);
        nvgFontSize(vg, size);
        nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_BASELINE);
        nvgFillColor(vg, rgba(color, argb));
        nvgText(vg, x, baseline, value);
    }

    @Override
    public void image(int[] pixels, int imageWidth, int imageHeight, float x, float y, float w, float h) {
        if (pixels == null || imageWidth <= 0 || imageHeight <= 0) {
            return;
        }
        Map<int[], Integer> images = IMAGES.get(vg);
        if (images == null) {
            images = new WeakHashMap<int[], Integer>();
            IMAGES.put(vg, images);
        }
        Integer id = images.get(pixels);
        if (id == null) {
            ByteBuffer rgba = MemoryUtil.memAlloc(imageWidth * imageHeight * 4);
            try {
                for (int i = 0; i < imageWidth * imageHeight; i++) {
                    int p = i < pixels.length ? pixels[i] : 0;
                    rgba.put((byte) (p >> 16)).put((byte) (p >> 8)).put((byte) p).put((byte) (p >>> 24));
                }
                rgba.flip();
                id = nvgCreateImageRGBA(vg, imageWidth, imageHeight, 0, rgba);
            } finally {
                MemoryUtil.memFree(rgba);
            }
            images.put(pixels, id);
        }
        nvgImagePattern(vg, x, y, w, h, 0, id, 1.0f, paint);
        nvgBeginPath(vg);
        nvgRect(vg, x, y, w, h);
        nvgFillPaint(vg, paint);
        nvgFill(vg);
    }

    @Override
    public void clip(float x, float y, float w, float h) {
        nvgSave(vg);
        alphas.push(alphas.peek());
        nvgIntersectScissor(vg, x, y, w, h);
    }

    @Override
    public void layer(float opacity, float blurRadius) {
        nvgSave(vg);
        float alpha = alphas.peek() * Math.max(0, Math.min(1, opacity)); // nested layers multiply, like Skia's
        alphas.push(alpha);
        nvgGlobalAlpha(vg, alpha);
    }

    @Override
    public void restore() {
        nvgRestore(vg);
        if (alphas.size() > 1) {
            alphas.pop();
        }
    }

    private final java.util.ArrayDeque<Float> alphas = new java.util.ArrayDeque<Float>(java.util.Collections.singleton(1.0f));
}
