package com.jxparallel.android;

import java.util.Map;
import java.util.WeakHashMap;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

import com.jxparallel.ui.native2d.JXPainter;

/**
 * {@link JXPainter} on an {@code android.graphics.Canvas}. Coordinates are the logical pixels the layout used; the
 * caller scales the canvas by the display density before painting, so a 12 px label is 12 dp. Text is drawn with the
 * typefaces of {@link JXAndroidText}, the same the layout measured with.
 *
 * <p>Layers are {@code saveLayerAlpha}: the opacity is honoured, a blur radius is not (Canvas has no group blur).
 * Not thread safe: the UI thread.
 */
public final class AndroidCanvasPainter implements JXPainter {
    /** Decoded images by pixel array identity; UI thread only. */
    private static final Map<int[], Bitmap> IMAGES = new WeakHashMap<int[], Bitmap>();

    private Canvas canvas;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmap = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();

    public AndroidCanvasPainter() {
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        text.setStyle(Paint.Style.FILL);
        text.setSubpixelText(true);
        text.setLinearText(true);
    }

    /** The canvas of the frame being painted. */
    public void begin(Canvas target) {
        canvas = target;
    }

    @Override
    public void fillRect(float x, float y, float w, float h, int argb) {
        fill.setShader(null);
        fill.setColor(argb);
        canvas.drawRect(x, y, x + w, y + h, fill);
    }

    @Override
    public void fillRoundRect(float x, float y, float w, float h, float radius, int argb) {
        if (radius <= 0) {
            fillRect(x, y, w, h, argb);
            return;
        }
        fill.setShader(null);
        fill.setColor(argb);
        rect.set(x, y, x + w, y + h);
        canvas.drawRoundRect(rect, radius, radius, fill);
    }

    @Override
    public void fillRoundRectGradient(float x, float y, float w, float h, float radius, int top, int bottom) {
        fill.setColor(0xFFFFFFFF);
        fill.setShader(new LinearGradient(x, y, x, y + h, top, bottom, Shader.TileMode.CLAMP));
        rect.set(x, y, x + w, y + h);
        if (radius <= 0) {
            canvas.drawRect(rect, fill);
        } else {
            canvas.drawRoundRect(rect, radius, radius, fill);
        }
        fill.setShader(null);
    }

    @Override
    public void strokeRoundRect(float x, float y, float w, float h, float radius, float width, int argb) {
        float half = width / 2;
        stroke.setStrokeWidth(width);
        stroke.setColor(argb);
        rect.set(x + half, y + half, x + w - half, y + h - half);
        if (radius <= 0) {
            canvas.drawRect(rect, stroke);
        } else {
            float r = Math.max(0, radius - half);
            canvas.drawRoundRect(rect, r, r, stroke);
        }
    }

    @Override
    public void fillOval(float x, float y, float w, float h, int argb) {
        fill.setShader(null);
        fill.setColor(argb);
        rect.set(x, y, x + w, y + h);
        canvas.drawOval(rect, fill);
    }

    @Override
    public void strokeOval(float x, float y, float w, float h, float width, int argb) {
        float half = width / 2;
        stroke.setStrokeWidth(width);
        stroke.setColor(argb);
        rect.set(x + half, y + half, x + w - half, y + h - half);
        canvas.drawOval(rect, stroke);
    }

    @Override
    public void strokeArc(float x, float y, float w, float h, float startDegrees, float sweepDegrees, float width, int argb) {
        float half = width / 2;
        stroke.setStrokeWidth(width);
        stroke.setColor(argb);
        rect.set(x + half, y + half, x + w - half, y + h - half);
        canvas.drawArc(rect, startDegrees, sweepDegrees, false, stroke);
    }

    @Override
    public void line(float x1, float y1, float x2, float y2, float width, int argb) {
        stroke.setStrokeWidth(width);
        stroke.setColor(argb);
        canvas.drawLine(x1, y1, x2, y2, stroke);
    }

    @Override
    public void fillTriangle(float x0, float y0, float x1, float y1, float x2, float y2, int argb) {
        fill.setShader(null);
        fill.setColor(argb);
        path.rewind();
        path.moveTo(x0, y0);
        path.lineTo(x1, y1);
        path.lineTo(x2, y2);
        path.close();
        canvas.drawPath(path, fill);
    }

    @Override
    public void text(String value, float x, float baseline, float size, boolean bold, int argb) {
        if (value == null || value.isEmpty()) {
            return;
        }
        text.setTypeface(JXAndroidText.typeface(bold));
        text.setTextSize(size);
        text.setColor(argb);
        canvas.drawText(value, x, baseline, text);
    }

    @Override
    public void image(int[] pixels, int imageWidth, int imageHeight, float x, float y, float w, float h) {
        if (pixels == null || imageWidth <= 0 || imageHeight <= 0 || pixels.length < imageWidth * imageHeight) {
            return;
        }
        Bitmap decoded = IMAGES.get(pixels);
        if (decoded == null) {
            decoded = Bitmap.createBitmap(pixels, imageWidth, imageHeight, Bitmap.Config.ARGB_8888);
            IMAGES.put(pixels, decoded);
        }
        rect.set(x, y, x + w, y + h);
        canvas.drawBitmap(decoded, null, rect, bitmap);
    }

    @Override
    public void clip(float x, float y, float w, float h) {
        canvas.save();
        canvas.clipRect(x, y, x + w, y + h);
    }

    @Override
    public void layer(float opacity, float blurRadius) {
        int alpha = Math.round(Math.max(0f, Math.min(1f, opacity)) * 255f);
        canvas.saveLayerAlpha(null, alpha);
    }

    @Override
    public void restore() {
        canvas.restore();
    }
}
