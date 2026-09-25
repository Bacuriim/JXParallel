package com.jxparallel.ui.native2d;

import com.jxparallel.ui.JXComponent;
import com.jxparallel.ui.JXElement;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.Typeface;
import io.github.humbleui.types.Rect;

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

    private JXSkiaRenderer() {
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
        try (Font font = new Font(Typeface.makeDefault(), 13.0f);
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
            canvas.drawString(text(node, "label"), x + 24, y + 14, font, fill.setColor(DARK_GRAY));
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
            canvas.drawString(text(node, "value"), x, y + 16, font, fill.setColor(DARK_GRAY));
        }
        for (JXNativeNode child : node.getChildren()) {
            paintNode(child, canvas, font, fill, stroke);
        }
    }

    private static void drawCentered(Canvas canvas, String value, Rect bounds, Font font, Paint paint) {
        float textWidth = font.measureTextWidth(value);
        float baseline = bounds.getTop() + (bounds.getHeight() - font.getMetrics().getHeight()) / 2.0f
                - font.getMetrics().getAscent();
        canvas.drawString(value, bounds.getLeft() + (bounds.getWidth() - textWidth) / 2.0f, baseline, font, paint);
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
