package com.jxparallel.ui.native2d;

import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_BASELINE;
import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER;
import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT;
import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE;
import static org.lwjgl.nanovg.NanoVG.nvgBeginPath;
import static org.lwjgl.nanovg.NanoVG.nvgCircle;
import static org.lwjgl.nanovg.NanoVG.nvgFill;
import static org.lwjgl.nanovg.NanoVG.nvgFillColor;
import static org.lwjgl.nanovg.NanoVG.nvgFontFace;
import static org.lwjgl.nanovg.NanoVG.nvgFontSize;
import static org.lwjgl.nanovg.NanoVG.nvgLineTo;
import static org.lwjgl.nanovg.NanoVG.nvgMoveTo;
import static org.lwjgl.nanovg.NanoVG.nvgRect;
import static org.lwjgl.nanovg.NanoVG.nvgRoundedRect;
import static org.lwjgl.nanovg.NanoVG.nvgStroke;
import static org.lwjgl.nanovg.NanoVG.nvgStrokeColor;
import static org.lwjgl.nanovg.NanoVG.nvgStrokeWidth;
import static org.lwjgl.nanovg.NanoVG.nvgText;
import static org.lwjgl.nanovg.NanoVG.nvgTextAlign;


import org.lwjgl.nanovg.NVGColor;

import com.jxparallel.ui.text.JXTextEngine;

/**
 * Paints a {@link JXNativeNode} tree with NanoVG. Used by {@link JXWindow} on 32-bit JVMs, where
 * Skia has no native libraries. Mirrors {@link JXSkiaRenderer}: same shapes, colors and sizes.
 */
public final class JXNanoVGRenderer {
    static final int BACKGROUND = 0xFFF7F7F7;
    static final String FONT = "sans";
    private static final int BLUE = 0xFF2D6CDF;
    private static final int DARK_GRAY = 0xFF404040;
    private static final int BORDER = 0xFF969696;
    private static final int WHITE = 0xFFFFFFFF;

    private JXNanoVGRenderer() {
    }

    /**
     * Lays out and paints the tree between {@code nvgBeginFrame} and {@code nvgEndFrame}.
     * {@code color} is a scratch struct owned by the caller. Window thread only.
     */
    public static void paint(JXNativeNode root, long vg, NVGColor color, boolean hasFont, int width, int height) {
        if (root == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        root.layoutForBackend(Math.max(1, width), Math.max(1, height));
        if (hasFont) {
            nvgFontFace(vg, FONT);
            nvgFontSize(vg, JXTextEngine.DEFAULT_SIZE);
        }
        paintNode(root, vg, color, hasFont);
    }

    private static void paintNode(JXNativeNode node, long vg, NVGColor color, boolean hasFont) {
        float x = node.getX();
        float y = node.getY();
        float w = node.getWidth();
        float h = node.getHeight();
        String type = node.getType();
        if ("button".equals(type) || "toggle".equals(type)) {
            nvgBeginPath(vg);
            nvgRoundedRect(vg, x, y, w, h, 8.0f);
            fill(vg, color, BLUE);
            centered(vg, color, hasFont, text(node, "label"), x, y, w, h, WHITE);
        } else if ("checkbox".equals(type)) {
            nvgBeginPath(vg);
            nvgRect(vg, x + 0.5f, y + 0.5f, 17, 17);
            fill(vg, color, WHITE);
            stroke(vg, color, 0xFF5A5A5A);
            if (Boolean.TRUE.equals(node.getProperty("checked"))) {
                nvgBeginPath(vg);
                nvgMoveTo(vg, x + 3, y + 9);
                nvgLineTo(vg, x + 8, y + 14);
                nvgLineTo(vg, x + 15, y + 3);
                stroke(vg, color, 0xFF5A5A5A);
            }
            left(vg, color, hasFont, text(node, "label"), x + 24, y + JXTextEngine.get().baseline(h, JXTextEngine.DEFAULT_SIZE), DARK_GRAY);
        } else if ("input".equals(type) || "textarea".equals(type)
                || "password".equals(type) || "select".equals(type)) {
            nvgBeginPath(vg);
            nvgRect(vg, x + 0.5f, y + 0.5f, w - 1, h - 1);
            fill(vg, color, WHITE);
            stroke(vg, color, BORDER);
            centered(vg, color, hasFont, text(node, "value"), x, y, w, h, DARK_GRAY);
        } else if ("progress".equals(type)) {
            nvgBeginPath(vg);
            nvgRoundedRect(vg, x, y, w, h, 8.0f);
            fill(vg, color, 0xFFE1E1E1);
            nvgBeginPath(vg);
            nvgRoundedRect(vg, x, y, w * (float) clamp(number(node, "progress", 0.0)), h, 8.0f);
            fill(vg, color, BLUE);
        } else if ("slider".equals(type)) {
            float middle = y + h / 2.0f;
            nvgBeginPath(vg);
            nvgMoveTo(vg, x, middle);
            nvgLineTo(vg, x + w, middle);
            stroke(vg, color, 0xFFB4B4B4);
            double min = number(node, "min", 0.0);
            double max = number(node, "max", 100.0);
            double fraction = max <= min ? 0.0 : (number(node, "value", min) - min) / (max - min);
            nvgBeginPath(vg);
            nvgCircle(vg, x + (float) (w * clamp(fraction)), middle, 6.0f);
            fill(vg, color, BLUE);
        } else if ("#text".equals(type)) {
            left(vg, color, hasFont, text(node, "value"), x, y + JXTextEngine.get().baseline(h, JXTextEngine.DEFAULT_SIZE), DARK_GRAY);
        }
        for (JXNativeNode child : node.getChildren()) {
            paintNode(child, vg, color, hasFont);
        }
    }

    private static void fill(long vg, NVGColor color, int argb) {
        nvgFillColor(vg, rgba(color, argb));
        nvgFill(vg);
    }

    private static void stroke(long vg, NVGColor color, int argb) {
        nvgStrokeColor(vg, rgba(color, argb));
        nvgStrokeWidth(vg, 1.0f);
        nvgStroke(vg);
    }

    private static void centered(long vg, NVGColor color, boolean hasFont, String value,
                                 float x, float y, float w, float h, int argb) {
        if (hasFont && !value.isEmpty()) {
            nvgTextAlign(vg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
            nvgFillColor(vg, rgba(color, argb));
            nvgText(vg, x + w / 2.0f, y + h / 2.0f, value);
        }
    }

    private static void left(long vg, NVGColor color, boolean hasFont, String value, float x, float baseline, int argb) {
        if (hasFont && !value.isEmpty()) {
            nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_BASELINE);
            nvgFillColor(vg, rgba(color, argb));
            nvgText(vg, x, baseline, value);
        }
    }

    private static NVGColor rgba(NVGColor color, int argb) {
        return color.r(((argb >> 16) & 0xFF) / 255.0f)
                .g(((argb >> 8) & 0xFF) / 255.0f)
                .b((argb & 0xFF) / 255.0f)
                .a(((argb >>> 24) & 0xFF) / 255.0f);
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
}
