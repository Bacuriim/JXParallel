package com.jxparallel.ui.native2d;

import java.awt.Dimension;
import java.awt.image.BufferedImage;

import com.jxparallel.ui.JXComponent;
import com.jxparallel.ui.JXElement;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.Typeface;
import io.github.humbleui.types.Rect;

public final class JXSkiaRenderer {
    private static final int BLUE = 0xFF2D6CDF;
    private static final int DARK_GRAY = 0xFF404040;
    private static final int BORDER = 0xFF969696;

    static {
        io.github.humbleui.skija.impl.Library.load();
    }

    private JXSkiaRenderer() {
    }

    public static JXNativeNode mount(JXElement element) {
        if (element == null) {
            throw new IllegalArgumentException("Element cannot be null");
        }
        return new JXNativeNode(element);
    }

    public static JXNativeNode mount(JXComponent component) {
        if (component == null) {
            throw new IllegalArgumentException("Component cannot be null");
        }
        return mount(component.render());
    }

    public static void layout(JXNativeNode node, int width, int height) {
        requireNode(node);
        node.layout(0, 0, width, height);
    }

    public static Dimension preferredSize(JXNativeNode node) {
        requireNode(node);
        return node.preferredSize();
    }

    public static BufferedImage render(JXNativeNode node, int width, int height) {
        requireNode(node);
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        layout(node, safeWidth, safeHeight);

        Bitmap bitmap = new Bitmap();
        if (!bitmap.allocN32Pixels(safeWidth, safeHeight)) {
            bitmap.close();
            throw new IllegalStateException("Unable to allocate Skia raster surface");
        }
        Canvas canvas = new Canvas(bitmap);
        try {
            canvas.clear(0xFFFFFFFF);
            paintNode(node, canvas);
            BufferedImage image = new BufferedImage(safeWidth, safeHeight, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < safeHeight; y++) {
                for (int x = 0; x < safeWidth; x++) {
                    image.setRGB(x, y, bitmap.getColor(x, y));
                }
            }
            return image;
        } finally {
            canvas.close();
            bitmap.close();
        }
    }

    private static void paintNode(JXNativeNode node, Canvas canvas) {
        java.awt.Rectangle bounds = node.getBounds();
        String type = node.getType();
        Paint fill = new Paint().setAntiAlias(true).setMode(PaintMode.FILL);
        Paint stroke = new Paint().setAntiAlias(true).setMode(PaintMode.STROKE).setStrokeWidth(1.0f);
        try {
            if ("button".equals(type) || "toggle".equals(type)) {
                fill.setColor(BLUE);
                canvas.drawRRect(new Rect(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height)
                        .withRadii(8.0f), fill);
                drawCenteredText(canvas, node, text(node, "label", ""), 0xFFFFFFFF);
            } else if ("checkbox".equals(type)) {
                fill.setColor(0xFFFFFFFF);
                canvas.drawRect(new Rect(bounds.x, bounds.y, bounds.x + 18, bounds.y + 18), fill);
                stroke.setColor(0xFF5A5A5A);
                canvas.drawRect(new Rect(bounds.x, bounds.y, bounds.x + 17, bounds.y + 17), stroke);
                if (Boolean.TRUE.equals(node.getProperty("checked"))) {
                    canvas.drawLine(bounds.x + 3, bounds.y + 9, bounds.x + 8, bounds.y + 14, stroke);
                    canvas.drawLine(bounds.x + 8, bounds.y + 14, bounds.x + 15, bounds.y + 3, stroke);
                }
                drawText(canvas, text(node, "label", ""), bounds.x + 24, bounds.y + 14, DARK_GRAY);
            } else if ("input".equals(type) || "textarea".equals(type)
                    || "password".equals(type) || "select".equals(type)) {
                drawField(canvas, node, bounds);
            } else if ("progress".equals(type)) {
                fill.setColor(0xFFE1E1E1);
                canvas.drawRRect(new Rect(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height)
                        .withRadii(8.0f), fill);
                fill.setColor(BLUE);
                int progressWidth = (int) (bounds.width * propertyAsDouble(node, "progress", 0.0));
                canvas.drawRRect(new Rect(bounds.x, bounds.y, bounds.x + progressWidth, bounds.y + bounds.height)
                        .withRadii(8.0f), fill);
            } else if ("slider".equals(type)) {
                stroke.setColor(0xFFB4B4B4);
                canvas.drawLine(bounds.x, bounds.y + bounds.height / 2.0f,
                        bounds.x + bounds.width, bounds.y + bounds.height / 2.0f, stroke);
                double min = propertyAsDouble(node, "min", 0.0);
                double max = propertyAsDouble(node, "max", 100.0);
                double value = propertyAsDouble(node, "value", min);
                double fraction = max <= min ? 0.0 : (value - min) / (max - min);
                float knobX = (float) (bounds.x + bounds.width * Math.max(0.0, Math.min(1.0, fraction)));
                fill.setColor(BLUE);
                canvas.drawCircle(knobX, bounds.y + bounds.height / 2.0f, 6.0f, fill);
            } else if ("#text".equals(type)) {
                drawText(canvas, text(node, "value", ""), bounds.x, bounds.y + 16, DARK_GRAY);
            }
            for (JXNativeNode child : node.getChildren()) {
                paintNode(child, canvas);
            }
        } finally {
            fill.close();
            stroke.close();
        }
    }

    private static void drawField(Canvas canvas, JXNativeNode node, java.awt.Rectangle bounds) {
        Paint fill = new Paint().setColor(0xFFFFFFFF);
        Paint stroke = new Paint().setColor(BORDER).setMode(PaintMode.STROKE);
        try {
            canvas.drawRect(new Rect(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height), fill);
            canvas.drawRect(new Rect(bounds.x, bounds.y, bounds.x + bounds.width - 1, bounds.y + bounds.height - 1),
                    stroke);
            drawCenteredText(canvas, node, text(node, "value", ""), DARK_GRAY);
        } finally {
            fill.close();
            stroke.close();
        }
    }

    private static void drawCenteredText(Canvas canvas, JXNativeNode node, String value, int color) {
        java.awt.Rectangle bounds = node.getBounds();
        Font font = new Font(Typeface.makeDefault(), 13.0f);
        try {
            float width = font.measureTextWidth(value);
            float baseline = bounds.y + (bounds.height - font.getMetrics().getHeight()) / 2.0f
                    - font.getMetrics().getTop();
            drawText(canvas, value, bounds.x + (bounds.width - width) / 2.0f, baseline, color, font);
        } finally {
            font.close();
        }
    }

    private static void drawText(Canvas canvas, String value, float x, float baseline, int color) {
        Font font = new Font(Typeface.makeDefault(), 13.0f);
        try {
            drawText(canvas, value, x, baseline, color, font);
        } finally {
            font.close();
        }
    }

    private static void drawText(Canvas canvas, String value, float x, float baseline, int color, Font font) {
        Paint paint = new Paint().setAntiAlias(true).setColor(color);
        try {
            canvas.drawString(value, x, baseline, font, paint);
        } finally {
            paint.close();
        }
    }

    private static String text(JXNativeNode node, String property, String fallback) {
        Object value = node.getProperty(property);
        return value == null ? fallback : String.valueOf(value);
    }

    private static double propertyAsDouble(JXNativeNode node, String property, double fallback) {
        Object value = node.getProperty(property);
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }

    private static void requireNode(JXNativeNode node) {
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
    }
}
