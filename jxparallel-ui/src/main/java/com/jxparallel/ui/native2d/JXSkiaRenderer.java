package com.jxparallel.ui.native2d;

import java.util.LinkedHashMap;
import java.util.Map;

import com.jxparallel.ui.JXComponent;
import com.jxparallel.ui.JXElement;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontHinting;
import io.github.humbleui.skija.TextBlob;
import io.github.humbleui.skija.Typeface;
import io.github.humbleui.skija.shaper.Shaper;

import com.jxparallel.ui.text.JXTextEngine;

/**
 * Paints a {@link JXNativeNode} tree onto a Skia canvas (the OpenGL framebuffer of a
 * {@link JXWindow}, or a raster surface in tests). The drawing itself is {@link JXPaint}.
 */
public final class JXSkiaRenderer {
    static final int BACKGROUND = 0xFFF4F4F4;

    private static volatile Typeface typeface;
    private static Shaper shaper;
    /** Shaped text per string at the default size, display thread only. Shaping every label every frame would dominate the frame. */
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

    /** Lays out and paints the tree. Not thread safe: call from the painting thread only. */
    public static void paint(JXNativeNode root, Canvas canvas, int width, int height) {
        requireNode(root);
        layout(root, Math.max(1, width), Math.max(1, height));
        canvas.clear(BACKGROUND);
        try (JXSkiaPainter painter = new JXSkiaPainter(canvas)) {
            new JXPaint(painter).paint(root);
        }
    }

    /** Shaped text at the default size, cached; {@code null} when there is nothing to draw. */
    static TextBlob shaped(String value, Font font, boolean bold) {
        if (value.isEmpty()) {
            return null;
        }
        String key = bold ? "\u0001" + value : value;
        TextBlob blob = BLOBS.get(key);
        if (blob == null) {
            blob = shapedUncached(value, font);
            if (blob == null) {
                return null;
            }
            BLOBS.put(key, blob);
        }
        return blob;
    }

    /** Shaped text the caller must close; {@code null} when there is nothing to draw. */
    static TextBlob shapedUncached(String value, Font font) {
        if (value.isEmpty()) {
            return null;
        }
        if (shaper == null) {
            shaper = Shaper.make();
        }
        return shaper.shape(value, font);
    }

    private static void requireNode(JXNativeNode node) {
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
    }
}
