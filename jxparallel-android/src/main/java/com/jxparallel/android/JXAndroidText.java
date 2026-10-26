package com.jxparallel.android;

import android.graphics.Paint;
import android.graphics.Typeface;

import com.jxparallel.ui.text.JXTextEngine;

/**
 * The text metrics of the layout on Android. The desktop engine measures with HarfBuzz from a font file through
 * LWJGL, neither of which exists here; {@link JXTextEngine} accepts a host that measures instead
 * ({@link JXTextEngine.LineMetrics}), and this is that host: it asks an {@code android.graphics.Paint} with the very
 * typefaces {@link AndroidCanvasPainter} draws with, at the physical size (logical size times density) so the hinted
 * advances are the ones that reach the screen. Call {@link #install} before the first view is created.
 */
public final class JXAndroidText {
    private static volatile Typeface regular = Typeface.DEFAULT;
    private static volatile Typeface bold = Typeface.DEFAULT_BOLD;

    private JXAndroidText() {
    }

    static Typeface typeface(boolean heavy) {
        return heavy ? bold : regular;
    }

    /**
     * Draws and measures with other typefaces (an app font from the assets, say); null keeps the system one. Call it
     * before {@link #install}, which drops what the engine measured with the old ones.
     */
    public static void setTypefaces(Typeface regularFace, Typeface boldFace) {
        regular = regularFace == null ? Typeface.DEFAULT : regularFace;
        bold = boldFace == null ? Typeface.DEFAULT_BOLD : boldFace;
    }

    /** Makes the text engine measure through Android's Paint at the given display density (dp to px). */
    public static void install(final float density) {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setSubpixelText(true);
        paint.setLinearText(true);
        JXTextEngine.setLineMetrics(new JXTextEngine.LineMetrics() {
            @Override
            public synchronized float[] measure(float size, boolean heavy) {
                paint.setTypeface(typeface(heavy));
                paint.setTextSize(size * density);
                Paint.FontMetrics fm = paint.getFontMetrics();
                return JXAndroidMath.lineMetrics(-fm.ascent / density, fm.descent / density, fm.leading / density);
            }

            @Override
            public synchronized float width(String text, float size, boolean heavy) {
                paint.setTypeface(typeface(heavy));
                paint.setTextSize(size * density);
                return paint.measureText(text) / density;
            }
        });
    }
}
