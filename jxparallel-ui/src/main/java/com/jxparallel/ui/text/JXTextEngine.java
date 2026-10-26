package com.jxparallel.ui.text;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Measures text with HarfBuzz (kerning, ligatures, complex scripts) from the font file both
 * renderers draw with. CPU only: no window or GPU, and the same numbers on 32-bit and 64-bit JVMs,
 * so layout does not depend on the renderer. Thread safe: the font is read-only once created.
 */
public final class JXTextEngine {
    /** Default text size in pixels, used by the layout and both renderers. */
    public static final float DEFAULT_SIZE = 12.0f;

    /** Candidate system fonts; {@code -Djx.font=path} takes precedence. */
    private static final String[] FONT_CANDIDATES = {
            "C:\\Windows\\Fonts\\segoeui.ttf",
            "C:\\Windows\\Fonts\\arial.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/dejavu/DejaVuSans.ttf",
            "/System/Library/Fonts/Supplemental/Arial.ttf",
            "/Library/Fonts/Arial.ttf",
    };

    /** Bold faces of the candidates above, same order; {@code -Djx.font.bold=path} takes precedence. */
    private static final String[] BOLD_CANDIDATES = {
            "C:\\Windows\\Fonts\\segoeuib.ttf",
            "C:\\Windows\\Fonts\\arialbd.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
            "/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf",
            "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
            "/Library/Fonts/Arial Bold.ttf",
    };

    private static final int MAX_CACHED = 16384;
    private static volatile JXTextEngine instance;
    private static volatile JXTextEngine boldInstance;

    private final ConcurrentHashMap<String, Integer> advances = new ConcurrentHashMap<String, Integer>();

    private final String fontFile;
    /** The HarfBuzz font, or null when sizes come from the host's {@link LineMetrics} or are estimated. */
    private final JXHarfBuzz harfBuzz;
    private final int unitsPerEm;
    private final int ascender;
    private final int descender;
    private final int lineGap;

    /** Fallback without a font file: fixed-width estimate, so layout still works (and warns). */
    private JXTextEngine() {
        fontFile = null;
        harfBuzz = null;
        unitsPerEm = 1000;
        ascender = 800;
        descender = 200;
        lineGap = 50;
    }

    private JXTextEngine(String fontFile) {
        this.fontFile = fontFile;
        harfBuzz = new JXHarfBuzz(fontFile);
        unitsPerEm = harfBuzz.unitsPerEm;
        ascender = harfBuzz.ascender;
        descender = harfBuzz.descender;
        lineGap = harfBuzz.lineGap;
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
                        if (lineMetrics == null) System.err.println("JXParallel: no TrueType font found, text sizes are estimated; "
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

    /** The engine for {@code bold} text: the bold face when there is one, else the regular engine. */
    public static JXTextEngine get(boolean bold) {
        if (!bold) {
            return get();
        }
        JXTextEngine engine = boldInstance;
        if (engine == null) {
            synchronized (JXTextEngine.class) {
                engine = boldInstance;
                if (engine == null) {
                    String file = findBoldFontFile();
                    if (get().getFontFile() == null && lineMetrics != null) {
                        engine = new JXTextEngine(); // a host measures text (Android): it also tells bold from regular
                        engine.bold = true;
                    } else {
                        engine = file == null || get().getFontFile() == null ? get() : new JXTextEngine(file);
                    }
                    if (engine != get() && !engine.bold) {
                        engine.bold = true;
                    }
                    boldInstance = engine;
                }
            }
        }
        return engine;
    }

    /** The bold UI font file, or {@code null}. */
    public static String findBoldFontFile() {
        String configured = System.getProperty("jx.font.bold");
        if (configured != null && new File(configured).isFile()) {
            return configured;
        }
        if (System.getProperty("jx.font") != null) {
            return null; // a configured regular font has no known bold sibling
        }
        for (String candidate : BOLD_CANDIDATES) {
            if (new File(candidate).isFile()) {
                return candidate;
            }
        }
        return null;
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
        LineMetrics metrics = lineMetrics;
        if (metrics != null && (bold || size != DEFAULT_SIZE || harfBuzz == null)) {
            // hinted advances drift from the scaled ones away from the default size: ask the host
            String key = size + "|" + text;
            Float known = measuredWidths.get(key);
            if (known == null) {
                float measured = metrics.width(text, size, bold);
                if (!Float.isNaN(measured)) {
                    if (measuredWidths.size() >= MAX_CACHED) {
                        measuredWidths.clear();
                    }
                    measuredWidths.put(key, measured);
                    known = measured;
                }
            }
            if (known != null) {
                return known;
            }
        }
        if (harfBuzz == null) {
            return text.codePointCount(0, text.length()) * 0.6f * size;
        }
        // Unhinted advances scale linearly with size, so one entry per text serves every size.
        Integer units = advances.get(text);
        if (units == null) {
            units = harfBuzz.shapeAdvance(text);
            if (advances.size() >= MAX_CACHED) {
                advances.clear(); // ponytail: crude eviction, an LRU if big apps thrash it
            }
            advances.put(text, units);
        }
        return units * size / unitsPerEm;
    }

    /**
     * The shaped glyphs of {@code text}: glyph ids of this engine's font and their pen positions in
     * pixels at {@code size}, unhinted, as {@link #width} measures them. Returns the glyph count;
     * the arrays grow as needed and are returned through {@code out} ({@code out[0]} ids as
     * {@code int[]}, {@code out[1]} x offsets and {@code out[2]} y offsets as {@code float[]}).
     * For renderers that rasterize the glyphs themselves (the software renderer).
     */
    public int glyphs(String text, float size, Object[] out) {
        if (harfBuzz == null || text == null || text.isEmpty()) {
            return 0;
        }
        // Shaped once per text, in font units (unhinted: they scale with the size), as the advances.
        int[] run = runs.get(text);
        if (run == null) {
            run = harfBuzz.shapeRun(text);
            if (runs.size() >= MAX_CACHED) {
                runs.clear();
            }
            runs.put(text, run);
        }
        int n = run.length / 3;
        int[] ids = out[0] instanceof int[] && ((int[]) out[0]).length >= n ? (int[]) out[0] : new int[Math.max(16, n)];
        float[] xs = out[1] instanceof float[] && ((float[]) out[1]).length >= n ? (float[]) out[1] : new float[ids.length];
        float[] ys = out[2] instanceof float[] && ((float[]) out[2]).length >= n ? (float[]) out[2] : new float[ids.length];
        float scale = size / unitsPerEm;
        for (int i = 0; i < n; i++) {
            ids[i] = run[3 * i];
            xs[i] = run[3 * i + 1] * scale;
            ys[i] = run[3 * i + 2] * scale;
        }
        out[0] = ids;
        out[1] = xs;
        out[2] = ys;
        return n;
    }

    /** Glyph id, pen x and y (y down) in font units, per glyph. */
    private final ConcurrentHashMap<String, int[]> runs = new ConcurrentHashMap<String, int[]>();

    /** Distance from the top of a line to the baseline. */
    public float ascent(float size) {
        float[] measured = measured(size);
        return measured != null ? measured[0] : ascender * size / unitsPerEm;
    }

    /**
     * Line ascent and height as a host toolkit measures them, {@code {ascent, lineHeight}} or null.
     * JavaFX's text metrics are hinted (DirectWrite on Windows: Segoe UI lines are 17 px at 12 px
     * but 25 px at 16 px), which the scaled font units cannot reproduce; a compatibility layer that
     * runs JavaFX installs its measurements here so native layout matches it exactly.
     */
    public interface LineMetrics {
        float[] measure(float size, boolean bold);

        /** Advance width of text as the host draws it, or NaN to use the engine's own shaping. */
        default float width(String text, float size, boolean bold) {
            return Float.NaN;
        }
    }

    private final ConcurrentHashMap<String, Float> measuredWidths = new ConcurrentHashMap<String, Float>();

    private static volatile LineMetrics lineMetrics;
    private final ConcurrentHashMap<Float, float[]> measuredLines = new ConcurrentHashMap<Float, float[]>();
    private volatile boolean bold;

    /** Installs (or with null removes) the measured line metrics for every engine. */
    public static void setLineMetrics(LineMetrics metrics) {
        lineMetrics = metrics;
        JXTextEngine regular = instance;
        JXTextEngine heavy = boldInstance;
        if (regular != null) {
            regular.measuredLines.clear();
            regular.measuredWidths.clear();
        }
        if (heavy != null) {
            heavy.measuredLines.clear();
            heavy.measuredWidths.clear();
        }
    }

    private float[] measured(float size) {
        LineMetrics metrics = lineMetrics;
        if (metrics == null) {
            return null;
        }
        float[] cached = measuredLines.get(size);
        if (cached == null) {
            cached = metrics.measure(size, bold);
            if (cached == null) {
                return null;
            }
            measuredLines.put(size, cached);
        }
        return cached;
    }

    /** Baseline offset that centres one line of text vertically in a box of the given height. */
    public float baseline(float boxHeight, float size) {
        return (boxHeight - lineHeight(size)) / 2.0f + ascent(size);
    }

    /**
     * Height of one line. Ascent and descent are each rounded up, which is what JavaFX 21 reports
     * (17 px for Segoe UI at 12 px, where the exact sum is 15.96).
     */
    public float lineHeight(float size) {
        float[] measured = measured(size);
        if (measured != null) {
            return measured[1];
        }
        return (float) (Math.ceil(ascender * size / unitsPerEm) + Math.ceil(descender * size / unitsPerEm)
                + lineGap * size / unitsPerEm);
    }
}
