package com.jxparallel.ui.native2d;

import java.util.LinkedHashMap;
import java.util.Map;

import com.jxparallel.ui.JXComponent;
import com.jxparallel.ui.JXElement;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontHinting;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.TextBlob;
import io.github.humbleui.skija.Typeface;
import io.github.humbleui.skija.shaper.Shaper;
import io.github.humbleui.types.Rect;

import com.jxparallel.ui.text.JXTextEngine;

/**
 * Paints a {@link JXNativeNode} tree onto a Skia canvas. The canvas is backed by the
 * OpenGL framebuffer of {@link JXWindow}.
 */
public final class JXSkiaRenderer {
    private static final int BACKGROUND = 0xFFF7F7F7;
    private static final int BLUE = 0xFF2D6CDF;
    private static final int DARK_GRAY = 0xFF404040;
    private static final int BORDER = 0xFF969696;
    private static final int WHITE = 0xFFFFFFFF;

    private static final float SIZE = JXTextEngine.DEFAULT_SIZE;
    private static volatile Typeface typeface;
    private static Shaper shaper;
    /** Shaped text per string, window thread only. Shaping every label every frame would dominate the frame. */
    // ponytail: one font, so the text is the key; add the font size to the key when styles set sizes
    private static final Map<String, TextBlob> BLOBS = new LinkedHashMap<String, TextBlob>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, TextBlob> eldest) {
            if (size() > 4096) {
                eldest.getValue().close();
                return true;
            }
            return false;
        }
    };

    private JXSkiaRenderer() {
    }

    /** Same font file as the layout's text engine, so drawn text has the measured width. */
    static Typeface typeface() {
        Typeface current = typeface;
        if (current == null) {
            String file = JXTextEngine.get().getFontFile();
            current = file == null ? Typeface.makeDefault() : Typeface.makeFromFile(file);
            typeface = current;
        }
        return current;
    }

    /** Unhinted, subpixel-positioned: advances match HarfBuzz's, which the layout uses. */
    static Font font() {
        return new Font(typeface(), JXTextEngine.DEFAULT_SIZE).setSubpixel(true).setHinting(FontHinting.NONE);
    }

    public static JXNativeNode mount(JXElement element) {
        if (element == null) {
            throw new IllegalArgumentException("Element cannot be null");
        }
        return JXNativeNode.createBackendNode(element);
    }

    public static JXNativeNode mount(JXComponent component) {
        if (component == null) {
            throw new IllegalArgumentException("Component cannot be null");
        }
        return mount(component.render());
    }

    public static void layout(JXNativeNode root, int width, int height) {
        requireNode(root);
        root.layoutForBackend(width, height);
    }

    /** Lays out and paints the tree. Not thread safe: call from the window thread only. */
    public static void paint(JXNativeNode root, Canvas canvas, int width, int height) {
        requireNode(root);
        layout(root, Math.max(1, width), Math.max(1, height));
        canvas.clear(BACKGROUND);
        try (Font font = font();
             Paint fill = new Paint().setAntiAlias(true).setMode(PaintMode.FILL);
             Paint stroke = new Paint().setAntiAlias(true).setMode(PaintMode.STROKE).setStrokeWidth(1.0f)) {
            paintNode(root, canvas, font, fill, stroke);
        }
    }

    private static void paintNode(JXNativeNode node, Canvas canvas, Font font, Paint fill, Paint stroke) {
        float x = node.getX();
        float y = node.getY();
        float w = node.getWidth();
        float h = node.getHeight();
        Rect bounds = Rect.makeXYWH(x, y, w, h);
        String type = node.getType();
        if ("button".equals(type) || "toggle".equals(type)) {
            canvas.drawRRect(bounds.withRadii(8.0f), fill.setColor(BLUE));
            drawCentered(canvas, text(node, "label"), bounds, font, fill.setColor(WHITE));
        } else if ("checkbox".equals(type)) {
            Rect box = Rect.makeXYWH(x, y, 18, 18);
            canvas.drawRect(box, fill.setColor(WHITE));
            canvas.drawRect(box, stroke.setColor(0xFF5A5A5A));
            if (Boolean.TRUE.equals(node.getProperty("checked"))) {
                canvas.drawLine(x + 3, y + 9, x + 8, y + 14, stroke);
                canvas.drawLine(x + 8, y + 14, x + 15, y + 3, stroke);
            }
            drawText(canvas, text(node, "label"), x + JXNativeNode.CHECK_BOX + JXNativeNode.CHECK_GAP, y, h, font,
                    fill.setColor(DARK_GRAY));
        } else if ("input".equals(type) || "textarea".equals(type)
                || "password".equals(type) || "select".equals(type)) {
            canvas.drawRect(bounds, fill.setColor(WHITE));
            canvas.drawRect(bounds, stroke.setColor(BORDER));
            drawCentered(canvas, text(node, "value"), bounds, font, fill.setColor(DARK_GRAY));
        } else if ("progress".equals(type)) {
            canvas.drawRRect(bounds.withRadii(8.0f), fill.setColor(0xFFE1E1E1));
            float progress = (float) clamp(number(node, "progress", 0.0));
            canvas.drawRRect(Rect.makeXYWH(x, y, w * progress, h).withRadii(8.0f), fill.setColor(BLUE));
        } else if ("slider".equals(type)) {
            float middle = y + h / 2.0f;
            canvas.drawLine(x, middle, x + w, middle, stroke.setColor(0xFFB4B4B4));
            double min = number(node, "min", 0.0);
            double max = number(node, "max", 100.0);
            double fraction = max <= min ? 0.0 : (number(node, "value", min) - min) / (max - min);
            canvas.drawCircle(x + (float) (w * clamp(fraction)), middle, 6.0f, fill.setColor(BLUE));
        } else if ("#text".equals(type)) {
            drawText(canvas, text(node, "value"), x, y, h, font, fill.setColor(DARK_GRAY));
        }
        for (JXNativeNode child : node.getChildren()) {
            paintNode(child, canvas, font, fill, stroke);
        }
    }

    private static void drawCentered(Canvas canvas, String value, Rect bounds, Font font, Paint paint) {
        float width = JXTextEngine.get().width(value, SIZE);
        drawText(canvas, value, bounds.getLeft() + (bounds.getWidth() - width) / 2.0f, bounds.getTop(),
                bounds.getHeight(), font, paint);
    }

    /** Draws shaped text (kerning, ligatures, other scripts) from x, centred vertically in the box. */
    private static void drawText(Canvas canvas, String value, float x, float boxTop, float boxHeight, Font font, Paint paint) {
        TextBlob blob = shaped(value, font);
        if (blob != null) {
            float blobBaseline = blob.getPositions()[1];
            canvas.drawTextBlob(blob, x, boxTop + JXTextEngine.get().baseline(boxHeight, SIZE) - blobBaseline, paint);
        }
    }

    /** Shaped text, or {@code null} when there is nothing to draw. */
    static TextBlob shaped(String value, Font font) {
        if (value.isEmpty()) {
            return null;
        }
        TextBlob blob = BLOBS.get(value);
        if (blob == null) {
            if (shaper == null) {
                shaper = Shaper.make();
            }
            blob = shaper.shape(value, font);
            if (blob == null) {
                return null;
            }
            BLOBS.put(value, blob);
        }
        return blob;
    }

    private static String text(JXNativeNode node, String property) {
        Object value = node.getProperty(property);
        return value == null ? "" : String.valueOf(value);
    }

    private static double number(JXNativeNode node, String property, double fallback) {
        Object value = node.getProperty(property);
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static void requireNode(JXNativeNode node) {
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
    }
}
