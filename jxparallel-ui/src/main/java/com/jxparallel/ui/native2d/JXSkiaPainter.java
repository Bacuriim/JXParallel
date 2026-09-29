package com.jxparallel.ui.native2d;

import java.util.Map;
import java.util.WeakHashMap;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorAlphaType;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.FilterTileMode;
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
import io.github.humbleui.skija.Typeface;
import io.github.humbleui.types.Rect;

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
        if (value == null || value.isEmpty()) {
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
