package com.jxparallel.ui.native2d;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.WeakHashMap;

import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.system.MemoryUtil;

import com.jxparallel.ui.text.JXFont;
import com.jxparallel.ui.text.JXShapedText;
import com.jxparallel.ui.text.JXTextEngine;

import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_BASELINE;
import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT;
import static org.lwjgl.nanovg.NanoVG.NVG_CW;
import static org.lwjgl.nanovg.NanoVG.NVG_HOLE;
import static org.lwjgl.nanovg.NanoVG.nvgArc;
import static org.lwjgl.nanovg.NanoVG.nvgBeginPath;
import static org.lwjgl.nanovg.NanoVG.nvgBoxGradient;
import static org.lwjgl.nanovg.NanoVG.nvgPathWinding;
import static org.lwjgl.nanovg.NanoVG.nvgRadialGradient;
import static org.lwjgl.nanovg.NanoVG.nvgRotate;
import static org.lwjgl.nanovg.NanoVG.nvgScale;
import static org.lwjgl.nanovg.NanoVG.nvgTranslate;
import static org.lwjgl.nanovg.NanoVG.nvgClosePath;
import static org.lwjgl.nanovg.NanoVG.nvgCreateImageRGBA;
import static org.lwjgl.nanovg.NanoVG.nvgEllipse;
import static org.lwjgl.nanovg.NanoVG.nvgFill;
import static org.lwjgl.nanovg.NanoVG.nvgFillColor;
import static org.lwjgl.nanovg.NanoVG.nvgFillPaint;
import static org.lwjgl.nanovg.NanoVG.nvgCreateFontAtIndex;
import static org.lwjgl.nanovg.NanoVG.nvgFindFont;
import static org.lwjgl.nanovg.NanoVG.nvgFontFace;
import static org.lwjgl.nanovg.NanoVG.nvgFontFaceId;
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

    /**
     * NanoVG gradients have two colors, so a gradient of n stops is n - 1 fills of the same shape: the first goes from the
     * first to the second stop (and stays the second after it); each next one goes from the previous stop, transparent, to
     * the next, which over what is already there is the exact blend of the two for opaque stops.
     */
    @Override
    public void fillGradient(float x, float y, float w, float h, float radius, JXGradient gradient) {
        float[] axis = gradient.radial ? null : gradient.axis(x, y, w, h);
        float[] circle = gradient.radial ? gradient.circle(x, y, w, h) : null;
        for (int i = 0; i + 1 < gradient.offsets.length; i++) {
            float from = gradient.offsets[i];
            float to = Math.max(gradient.offsets[i + 1], from + 0.0001f);
            int inner = i == 0 ? gradient.colors[0] : gradient.colors[i] & 0x00FFFFFF;
            int outer = gradient.colors[i + 1];
            if (axis != null) {
                float dx = axis[2] - axis[0];
                float dy = axis[3] - axis[1];
                nvgLinearGradient(vg, axis[0] + dx * from, axis[1] + dy * from, axis[0] + dx * to, axis[1] + dy * to,
                        rgba(color, inner), rgba(color2, outer), paint);
            } else {
                nvgRadialGradient(vg, circle[0], circle[1], circle[2] * from, circle[2] * to, rgba(color, inner), rgba(color2, outer), paint);
            }
            nvgBeginPath(vg);
            nvgRoundedRect(vg, x, y, w, h, radius);
            nvgFillPaint(vg, paint);
            nvgFill(vg);
        }
    }

    /** A box gradient: transparent at the feathered edge of the moved box, around a hole where the box itself is. */
    @Override
    public void shadow(float x, float y, float w, float h, float radius, float dx, float dy, float blur, int argb) {
        if (blur <= 0 || (argb >>> 24) == 0) {
            return;
        }
        nvgBoxGradient(vg, x + dx, y + dy, w, h, Math.max(0, radius), blur * 2, rgba(color, argb), rgba(color2, argb & 0x00FFFFFF), paint);
        nvgBeginPath(vg);
        nvgRect(vg, x + dx - blur - 1, y + dy - blur - 1, w + 2 * blur + 2, h + 2 * blur + 2);
        nvgRoundedRect(vg, x, y, w, h, Math.max(0, radius));
        nvgPathWinding(vg, NVG_HOLE);
        nvgFillPaint(vg, paint);
        nvgFill(vg);
    }

    @Override
    public void transform(float tx, float ty, float sx, float sy, float degrees, float cx, float cy) {
        nvgSave(vg);
        alphas.push(alphas.peek());
        nvgTranslate(vg, cx + tx, cy + ty);
        nvgRotate(vg, (float) Math.toRadians(degrees));
        nvgScale(vg, sx, sy);
        nvgTranslate(vg, -cx, -cy);
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
        text(value, x, baseline, size, JXFont.of(bold), argb);
    }

    private void plain(String value, float x, float baseline, float size, boolean bold, int argb) {
        nvgFontFace(vg, bold && hasBold ? JXNanoVGRenderer.FONT_BOLD : JXNanoVGRenderer.FONT);
        nvgFontSize(vg, size);
        nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_BASELINE);
        nvgFillColor(vg, rgba(color, argb));
        nvgText(vg, x, baseline, value);
    }

    @Override
    public void text(String value, float x, float baseline, float size, JXFont requested, int argb) {
        if (!hasFont || value == null || value.isEmpty()) {
            return;
        }
        JXTextEngine engine = JXTextEngine.get(requested);
        JXShapedText shaped = engine.shape(value);
        if (shaped == null || shaped.simple(engine) && (engine == JXTextEngine.get() || engine == JXTextEngine.get(true))) {
            plain(value, x, baseline, size, engine != JXTextEngine.get() && engine == JXTextEngine.get(true), argb);
            return;
        }
        // Another face, fallback faces or right-to-left text. NanoVG does not shape: each run is drawn with its
        // own face from the pen position HarfBuzz measured, and a right-to-left run is fed in visual order
        // (characters reversed: right to left scripts that join, like Arabic, are drawn unjoined).
        nvgFontSize(vg, size);
        nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_BASELINE);
        nvgFillColor(vg, rgba(color, argb));
        float pen = x;
        for (JXShapedText.Run run : shaped.runs()) {
            String piece = shaped.text(run);
            if (run.rtl()) {
                piece = new StringBuilder(piece).reverse().toString();
            }
            int id = fontId(run.face());
            if (id >= 0) {
                nvgFontFaceId(vg, id);
                nvgText(vg, pen, baseline, piece);
            }
            pen += run.advance(size);
        }
    }

    /** The NanoVG font of a face, created in this context on first use; -1 if it cannot be loaded. */
    private int fontId(JXTextEngine engine) {
        if (engine == JXTextEngine.get()) {
            return nvgFindFont(vg, JXNanoVGRenderer.FONT);
        }
        if (engine == JXTextEngine.get(true)) {
            return hasBold ? nvgFindFont(vg, JXNanoVGRenderer.FONT_BOLD) : nvgFindFont(vg, JXNanoVGRenderer.FONT);
        }
        String name = "f:" + engine.getFontFile() + "#" + engine.getFaceIndex();
        int id = nvgFindFont(vg, name);
        if (id < 0 && engine.getFontFile() != null) {
            id = nvgCreateFontAtIndex(vg, name, engine.getFontFile(), engine.getFaceIndex());
        }
        return id;
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
