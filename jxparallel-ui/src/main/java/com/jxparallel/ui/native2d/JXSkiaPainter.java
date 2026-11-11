package com.jxparallel.ui.native2d;

import java.util.Map;
import java.util.WeakHashMap;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorAlphaType;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.FilterBlurMode;
import io.github.humbleui.skija.FilterTileMode;
import io.github.humbleui.skija.MaskFilter;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontHinting;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageFilter;
import io.github.humbleui.skija.ImageInfo;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.skija.TextBlob;
import io.github.humbleui.skija.TextBlobBuilder;
import io.github.humbleui.skija.Typeface;
import io.github.humbleui.types.Point;
import io.github.humbleui.types.Rect;

import com.jxparallel.ui.text.JXFont;
import com.jxparallel.ui.text.JXShapedText;
import com.jxparallel.ui.text.JXTextEngine;

/** {@link JXPainter} on a Skia canvas. */
final class JXSkiaPainter implements JXPainter, AutoCloseable {
    private static volatile Typeface boldTypeface;
    /** Decoded images by pixel array; display thread only. */
    private static final Map<int[], Image> IMAGES = new WeakHashMap<int[], Image>();
    private final Canvas canvas;
    private final Paint fill = new Paint().setAntiAlias(true).setMode(PaintMode.FILL);
    private final Paint stroke = new Paint().setAntiAlias(true).setMode(PaintMode.STROKE);
    private final Font regular = JXSkiaRenderer.font();
    private Font boldFont;

    JXSkiaPainter(Canvas canvas) {
        this.canvas = canvas;
    }

    static Typeface boldTypeface() {
        Typeface current = boldTypeface;
        if (current == null) {
            String file = JXTextEngine.findBoldFontFile();
            current = file == null || JXTextEngine.get().getFontFile() == null ? JXSkiaRenderer.typeface() : Typeface.makeFromFile(file);
            boldTypeface = current;
        }
        return current;
    }

    @Override
    public void fillRect(float x, float y, float w, float h, int argb) {
        canvas.drawRect(Rect.makeXYWH(x, y, w, h), fill.setColor(argb));
    }

    @Override
    public void fillRoundRect(float x, float y, float w, float h, float radius, int argb) {
        if (radius <= 0) {
            fillRect(x, y, w, h, argb);
            return;
        }
        canvas.drawRRect(Rect.makeXYWH(x, y, w, h).withRadii(radius), fill.setColor(argb));
    }

    @Override
    public void fillRoundRectGradient(float x, float y, float w, float h, float radius, int top, int bottom) {
        try (Shader shader = Shader.makeLinearGradient(x, y, x, y + h, new int[] {top, bottom})) {
            fill.setColor(0xFFFFFFFF).setShader(shader);
            if (radius <= 0) {
                canvas.drawRect(Rect.makeXYWH(x, y, w, h), fill);
            } else {
                canvas.drawRRect(Rect.makeXYWH(x, y, w, h).withRadii(radius), fill);
            }
            fill.setShader(null);
        }
    }

    @Override
    public void fillGradient(float x, float y, float w, float h, float radius, JXGradient gradient) {
        float[] positions = gradient.offsets;
        Shader shader;
        if (gradient.radial) {
            float[] c = gradient.circle(x, y, w, h);
            shader = Shader.makeRadialGradient(c[0], c[1], Math.max(c[2], 0.001f), gradient.colors, positions);
        } else {
            float[] a = gradient.axis(x, y, w, h);
            if (a[0] == a[2] && a[1] == a[3]) {
                a[3] += 0.001f; // Skia rejects an empty axis
            }
            shader = Shader.makeLinearGradient(a[0], a[1], a[2], a[3], gradient.colors, positions);
        }
        try (Shader s = shader) {
            fill.setColor(0xFFFFFFFF).setShader(s);
            if (radius <= 0) {
                canvas.drawRect(Rect.makeXYWH(x, y, w, h), fill);
            } else {
                canvas.drawRRect(Rect.makeXYWH(x, y, w, h).withRadii(radius), fill);
            }
            fill.setShader(null);
        }
    }

    @Override
    public void shadow(float x, float y, float w, float h, float radius, float dx, float dy, float blur, int argb) {
        if (blur <= 0 || (argb >>> 24) == 0) {
            return;
        }
        canvas.save();
        canvas.clipRRect(Rect.makeXYWH(x, y, w, h).withRadii(Math.max(0, radius)), ClipMode.DIFFERENCE, true); // the box stays clear
        // a Gaussian whose visible reach is about the blur radius, like JavaFX's DropShadow and CSS blur radii
        try (MaskFilter soft = MaskFilter.makeBlur(FilterBlurMode.NORMAL, blur / 2f);
                Paint paint = new Paint().setAntiAlias(true).setColor(argb).setMaskFilter(soft)) {
            canvas.drawRRect(Rect.makeXYWH(x + dx, y + dy, w, h).withRadii(Math.max(0, radius)), paint);
        }
        canvas.restore();
    }

    @Override
    public void transform(float tx, float ty, float sx, float sy, float degrees, float cx, float cy) {
        canvas.save();
        canvas.translate(cx + tx, cy + ty);
        if (degrees != 0) {
            canvas.rotate(degrees);
        }
        canvas.scale(sx, sy);
        canvas.translate(-cx, -cy);
    }

    @Override
    public void strokeRoundRect(float x, float y, float w, float h, float radius, float width, int argb) {
        float half = width / 2;
        stroke.setStrokeWidth(width).setColor(argb);
        Rect r = Rect.makeXYWH(x + half, y + half, w - width, h - width);
        if (radius <= 0) {
            canvas.drawRect(r, stroke);
        } else {
            canvas.drawRRect(r.withRadii(Math.max(0, radius - half)), stroke);
        }
    }

    @Override
    public void fillOval(float x, float y, float w, float h, int argb) {
        canvas.drawOval(Rect.makeXYWH(x, y, w, h), fill.setColor(argb));
    }

    @Override
    public void strokeOval(float x, float y, float w, float h, float width, int argb) {
        float half = width / 2;
        canvas.drawOval(Rect.makeXYWH(x + half, y + half, w - width, h - width), stroke.setStrokeWidth(width).setColor(argb));
    }

    @Override
    public void strokeArc(float x, float y, float w, float h, float startDegrees, float sweepDegrees, float width, int argb) {
        float half = width / 2;
        canvas.drawArc(x + half, y + half, x + w - half, y + h - half, startDegrees, sweepDegrees, false,
                stroke.setStrokeWidth(width).setColor(argb));
    }

    @Override
    public void line(float x1, float y1, float x2, float y2, float width, int argb) {
        canvas.drawLine(x1, y1, x2, y2, stroke.setStrokeWidth(width).setColor(argb));
    }

    @Override
    public void fillTriangle(float x0, float y0, float x1, float y1, float x2, float y2, int argb) {
        try (Path path = new Path().moveTo(x0, y0).lineTo(x1, y1).lineTo(x2, y2).closePath()) {
            canvas.drawPath(path, fill.setColor(argb));
        }
    }

    @Override
    public void text(String value, float x, float baseline, float size, boolean bold, int argb) {
        text(value, x, baseline, size, JXFont.of(bold), argb);
    }

    @Override
    public void text(String value, float x, float baseline, float size, JXFont requested, int argb) {
        if (value == null || value.isEmpty()) {
            return;
        }
        JXTextEngine engine = JXTextEngine.get(requested);
        JXShapedText shaped = engine.shape(value);
        if (shaped != null && !shaped.simple(engine)) {
            runs(shaped, x, baseline, size, argb); // fallback faces or right-to-left: the glyphs HarfBuzz placed
            return;
        }
        boolean bold = engine != JXTextEngine.get() && engine == JXTextEngine.get(true);
        if (engine != JXTextEngine.get() && !bold) {
            // another family, weight or italic: its typeface, shaped by Skia like the UI font
            try (Font sized = new Font(JXSkiaRenderer.typeface(engine), size).setSubpixel(true).setHinting(FontHinting.NONE)) {
                TextBlob blob = JXSkiaRenderer.shapedUncached(value, sized);
                if (blob != null) {
                    try {
                        canvas.drawTextBlob(blob, x, baseline - blob.getPositions()[1], fill.setColor(argb));
                    } finally {
                        blob.close();
                    }
                }
            }
            return;
        }
        // The shaper places glyphs on a baseline at the blob's first y position; move that onto ours.
        if (size == JXTextEngine.DEFAULT_SIZE) {
            TextBlob blob = JXSkiaRenderer.shaped(value, bold ? bold() : regular, bold);
            if (blob != null) {
                canvas.drawTextBlob(blob, x, baseline - blob.getPositions()[1], fill.setColor(argb));
            }
            return;
        }
        try (Font sized = new Font(bold ? boldTypeface() : JXSkiaRenderer.typeface(), size).setSubpixel(true).setHinting(FontHinting.NONE)) {
            TextBlob blob = JXSkiaRenderer.shapedUncached(value, sized);
            if (blob != null) {
                try {
                    canvas.drawTextBlob(blob, x, baseline - blob.getPositions()[1], fill.setColor(argb));
                } finally {
                    blob.close();
                }
            }
        }
    }

    /** Draws shaped runs (visual order) from the glyph ids and positions of the text engine, one font per run. */
    private void runs(JXShapedText shaped, float x, float baseline, float size, int argb) {
        try (TextBlobBuilder builder = new TextBlobBuilder()) {
            float pen = 0;
            boolean any = false;
            for (JXShapedText.Run run : shaped.runs()) {
                int n = run.glyphCount();
                if (n > 0) {
                    short[] ids = new short[n];
                    Point[] at = new Point[n];
                    for (int i = 0; i < n; i++) {
                        ids[i] = (short) run.glyphId(i);
                        at[i] = new Point(pen + run.glyphX(i, size), run.glyphY(i, size));
                    }
                    try (Font f = new Font(JXSkiaRenderer.typeface(run.face()), size).setSubpixel(true).setHinting(FontHinting.NONE)) {
                        builder.appendRunPos(f, ids, at);
                        any = true;
                    }
                }
                pen += run.advance(size);
            }
            if (any) {
                try (TextBlob blob = builder.build()) {
                    if (blob != null) {
                        canvas.drawTextBlob(blob, x, baseline, fill.setColor(argb));
                    }
                }
            }
        }
    }

    private Font bold() {
        if (boldFont == null) {
            boldFont = new Font(boldTypeface(), JXTextEngine.DEFAULT_SIZE).setSubpixel(true).setHinting(FontHinting.NONE);
        }
        return boldFont;
    }

    @Override
    public void image(int[] pixels, int imageWidth, int imageHeight, float x, float y, float w, float h) {
        if (pixels == null || imageWidth <= 0 || imageHeight <= 0) {
            return;
        }
        Image image = IMAGES.get(pixels);
        if (image == null) {
            byte[] bytes = new byte[imageWidth * imageHeight * 4];
            for (int i = 0; i < imageWidth * imageHeight && i < pixels.length; i++) {
                int p = pixels[i];
                bytes[i * 4] = (byte) p;             // B
                bytes[i * 4 + 1] = (byte) (p >> 8);  // G
                bytes[i * 4 + 2] = (byte) (p >> 16); // R
                bytes[i * 4 + 3] = (byte) (p >>> 24); // A
            }
            image = Image.makeRaster(new ImageInfo(imageWidth, imageHeight, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL),
                    bytes, imageWidth * 4L);
            IMAGES.put(pixels, image);
        }
        canvas.drawImageRect(image, Rect.makeXYWH(x, y, w, h));
    }

    @Override
    public void clip(float x, float y, float w, float h) {
        canvas.save();
        canvas.clipRect(Rect.makeXYWH(x, y, w, h));
    }

    @Override
    public void layer(float opacity, float blurRadius) {
        try (Paint layer = new Paint().setAlphaf(Math.max(0, Math.min(1, opacity)))) {
            if (blurRadius > 0) {
                // JavaFX GaussianBlur radius r is about 3 standard deviations
                float sigma = blurRadius / 3.0f;
                try (ImageFilter blur = ImageFilter.makeBlur(sigma, sigma, FilterTileMode.DECAL)) {
                    layer.setImageFilter(blur);
                    canvas.saveLayer(null, layer);
                }
            } else {
                canvas.saveLayer(null, layer);
            }
        }
    }

    @Override
    public void restore() {
        canvas.restore();
    }

    @Override
    public void close() {
        fill.close();
        stroke.close();
        regular.close();
        if (boldFont != null) {
            boldFont.close();
        }
    }
}
