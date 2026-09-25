package com.jxparallel.ui.text;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.harfbuzz.hb_font_extents_t;
import org.lwjgl.util.harfbuzz.hb_glyph_position_t;

import static org.lwjgl.util.harfbuzz.HarfBuzz.hb_blob_create_from_file;
import static org.lwjgl.util.harfbuzz.HarfBuzz.hb_blob_destroy;
import static org.lwjgl.util.harfbuzz.HarfBuzz.hb_buffer_add_utf16;
import static org.lwjgl.util.harfbuzz.HarfBuzz.hb_buffer_create;
import static org.lwjgl.util.harfbuzz.HarfBuzz.hb_buffer_destroy;
import static org.lwjgl.util.harfbuzz.HarfBuzz.hb_buffer_get_glyph_positions;
import static org.lwjgl.util.harfbuzz.HarfBuzz.hb_buffer_guess_segment_properties;
import static org.lwjgl.util.harfbuzz.HarfBuzz.hb_face_create;
import static org.lwjgl.util.harfbuzz.HarfBuzz.hb_face_get_upem;
import static org.lwjgl.util.harfbuzz.HarfBuzz.hb_font_create;
import static org.lwjgl.util.harfbuzz.HarfBuzz.hb_font_get_h_extents;
import static org.lwjgl.util.harfbuzz.HarfBuzz.hb_font_set_scale;
import static org.lwjgl.util.harfbuzz.HarfBuzz.hb_shape;

/**
 * Measures text with HarfBuzz (kerning, ligatures, complex scripts) from the font file both
 * renderers draw with. CPU only: no window or GPU, and the same numbers on 32-bit and 64-bit JVMs,
 * so layout does not depend on the renderer. Thread safe: the font is read-only once created.
 */
public final class JXTextEngine {
    /** Default text size in pixels, used by the layout and both renderers. */
    public static final float DEFAULT_SIZE = 13.0f;

    /** Candidate system fonts; {@code -Djx.font=path} takes precedence. */
    private static final String[] FONT_CANDIDATES = {
            "C:\\Windows\\Fonts\\segoeui.ttf",
            "C:\\Windows\\Fonts\\arial.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/dejavu/DejaVuSans.ttf",
            "/System/Library/Fonts/Supplemental/Arial.ttf",
            "/Library/Fonts/Arial.ttf",
    };

    private static final int MAX_CACHED = 16384;
    private static volatile JXTextEngine instance;

    private final ConcurrentHashMap<String, Integer> advances = new ConcurrentHashMap<String, Integer>();

    private final String fontFile;
    private final long font;
    private final int unitsPerEm;
    private final int ascender;
    private final int descender;
    private final int lineGap;

    /** Fallback without a font file: fixed-width estimate, so layout still works (and warns). */
    private JXTextEngine() {
        fontFile = null;
        font = 0L;
        unitsPerEm = 1000;
        ascender = 800;
        descender = 200;
        lineGap = 50;
    }

    private JXTextEngine(String fontFile) {
        this.fontFile = fontFile;
        long blob = hb_blob_create_from_file(fontFile);
        long face = hb_face_create(blob, 0);
        hb_blob_destroy(blob); // the face keeps its own reference
        unitsPerEm = hb_face_get_upem(face);
        font = hb_font_create(face); // the font keeps the face; neither is freed, one per process
        hb_font_set_scale(font, unitsPerEm, unitsPerEm);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            hb_font_extents_t extents = hb_font_extents_t.malloc(stack);
            hb_font_get_h_extents(font, extents);
            ascender = extents.ascender();
            descender = -extents.descender();
            lineGap = extents.line_gap();
        }
    }

    /**
     * The engine for the system UI font. Without a font file it estimates 0.6 em per character
     * and prints a warning once; renderers then draw no text.
     */
    public static JXTextEngine get() {
        JXTextEngine engine = instance;
        if (engine == null) {
            synchronized (JXTextEngine.class) {
                engine = instance;
                if (engine == null) {
                    String file = findFontFile();
                    if (file == null) {
                        System.err.println("JXParallel: no TrueType font found, text sizes are estimated; "
                                + "set -Djx.font=<path to .ttf>");
                        engine = new JXTextEngine();
                    } else {
                        engine = new JXTextEngine(file);
                    }
                    instance = engine;
                }
            }
        }
        return engine;
    }

    /** Returns the UI font file, or {@code null} if none is found. */
    public static String findFontFile() {
        String configured = System.getProperty("jx.font");
        if (configured != null && new File(configured).isFile()) {
            return configured;
        }
        for (String candidate : FONT_CANDIDATES) {
            if (new File(candidate).isFile()) {
                return candidate;
            }
        }
        return null;
    }

    /** The font file, or {@code null} when sizes are estimated. */
    public String getFontFile() {
        return fontFile;
    }

    /** Advance width of the shaped text in pixels. */
    public float width(String text, float size) {
        if (text == null || text.isEmpty()) {
            return 0.0f;
        }
        if (font == 0L) {
            return text.codePointCount(0, text.length()) * 0.6f * size;
        }
        // Unhinted advances scale linearly with size, so one entry per text serves every size.
        Integer units = advances.get(text);
        if (units == null) {
            units = shapeAdvance(text);
            if (advances.size() >= MAX_CACHED) {
                advances.clear(); // ponytail: crude eviction, an LRU if big apps thrash it
            }
            advances.put(text, units);
        }
        return units * size / unitsPerEm;
    }

    /** Total advance of the shaped text in font units. */
    private int shapeAdvance(String text) {
        long buffer = hb_buffer_create();
        try {
            hb_buffer_add_utf16(buffer, text, 0, -1);
            hb_buffer_guess_segment_properties(buffer);
            hb_shape(font, buffer, null);
            hb_glyph_position_t.Buffer positions = hb_buffer_get_glyph_positions(buffer);
            int advance = 0;
            for (int i = 0; positions != null && i < positions.remaining(); i++) {
                advance += positions.get(i).x_advance();
            }
            return advance;
        } finally {
            hb_buffer_destroy(buffer);
        }
    }

    /** Distance from the top of a line to the baseline. */
    public float ascent(float size) {
        return ascender * size / unitsPerEm;
    }

    /** Baseline offset that centres one line of text vertically in a box of the given height. */
    public float baseline(float boxHeight, float size) {
        return (boxHeight - lineHeight(size)) / 2.0f + ascent(size);
    }

    /** Height of one line: ascent, descent and the font's line gap. */
    public float lineHeight(float size) {
        return (ascender + descender + lineGap) * size / unitsPerEm;
    }
}
