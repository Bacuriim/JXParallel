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
import static org.lwjgl.util.harfbuzz.HarfBuzz.HB_DIRECTION_LTR;
import static org.lwjgl.util.harfbuzz.HarfBuzz.HB_DIRECTION_RTL;
import static org.lwjgl.util.harfbuzz.HarfBuzz.hb_buffer_get_glyph_positions;
import static org.lwjgl.util.harfbuzz.HarfBuzz.hb_buffer_set_direction;
import static org.lwjgl.util.harfbuzz.HarfBuzz.hb_font_get_nominal_glyph;
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

    private final String fontFile;
    private final int faceIndex;
    /** True for a face other than the UI font and its bold: the host's line metrics do not describe it. */
    private final boolean custom;
    private final int weight;
    private final boolean italic;
    private final long font;
    private final int unitsPerEm;
    private final int ascender;
    private final int descender;
    private final int lineGap;

    /** Fallback without a font file: fixed-width estimate, so layout still works (and warns). */
    private JXTextEngine() {
        fontFile = null;
        faceIndex = 0;
        custom = false;
        weight = 400;
        italic = false;
        font = 0L;
        unitsPerEm = 1000;
        ascender = 800;
        descender = 200;
        lineGap = 50;
    }

    private JXTextEngine(String fontFile) {
        this(fontFile, 0, false, 400, false);
    }

    private JXTextEngine(String fontFile, int faceIndex, boolean custom, int weight, boolean italic) {
        this.fontFile = fontFile;
        this.faceIndex = faceIndex;
        this.custom = custom;
        this.weight = weight;
        this.italic = italic;
        long blob = hb_blob_create_from_file(fontFile);
        long face = hb_face_create(blob, faceIndex);
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
                    engine = file == null || get().getFontFile() == null ? get() : new JXTextEngine(file, 0, false, 700, false);
                    if (engine != get()) {
                        engine.bold = true;
                    }
                    boldInstance = engine;
                }
            }
        }
        return engine;
    }

    private static final ConcurrentHashMap<JXFont, JXTextEngine> FONT_ENGINES = new ConcurrentHashMap<JXFont, JXTextEngine>();
    private static final ConcurrentHashMap<String, JXTextEngine> FACE_ENGINES = new ConcurrentHashMap<String, JXTextEngine>();

    /**
     * The engine for a font request: the UI font (regular or bold) for {@link JXFont#DEFAULT} and
     * {@link JXFont#BOLD}, else the nearest face {@link JXFonts} finds for the family, weight and italic. A
     * family nothing provides is drawn in the UI font, bold from weight 600.
     */
    public static JXTextEngine get(JXFont request) {
        if (request == null) {
            return get();
        }
        if (request.isLegacy()) {
            return get(request.weight() >= 600);
        }
        JXTextEngine engine = FONT_ENGINES.get(request);
        if (engine == null) {
            JXFonts.Face face = JXFonts.resolve(request);
            engine = face == null ? get(request.isBold()) : forFace(face);
            FONT_ENGINES.put(request, engine);
        }
        return engine;
    }

    /** The engine of one face of a font file; the UI font's own files give the UI engines. */
    static JXTextEngine forFace(JXFonts.Face face) {
        String key = face.file() + "#" + face.index();
        JXTextEngine engine = FACE_ENGINES.get(key);
        if (engine == null) {
            synchronized (FACE_ENGINES) {
                engine = FACE_ENGINES.get(key);
                if (engine == null) {
                    String regular = findFontFile();
                    String boldFile = findBoldFontFile();
                    if (face.index() == 0 && regular != null && sameFile(face.file(), regular) && get().getFontFile() != null) {
                        engine = get();
                    } else if (face.index() == 0 && boldFile != null && sameFile(face.file(), boldFile) && get(true) != get()) {
                        engine = get(true);
                    } else {
                        engine = new JXTextEngine(face.file(), face.index(), true, face.weight(), face.italic());
                    }
                    FACE_ENGINES.put(key, engine);
                }
            }
        }
        return engine;
    }

    private static boolean sameFile(String a, String b) {
        return new File(a).getAbsoluteFile().equals(new File(b).getAbsoluteFile());
    }

    /** The registered fonts changed: forget what was resolved and shaped from the old set. */
    static void fontsChanged() {
        FONT_ENGINES.clear();
        FALLBACKS.clear();
        JXFonts.invalidateFallbacks();
        JXTextEngine regular = instance;
        JXTextEngine heavy = boldInstance;
        if (regular != null) {
            regular.shapes.clear();
        }
        if (heavy != null) {
            heavy.shapes.clear();
        }
        for (JXTextEngine engine : FACE_ENGINES.values()) {
            engine.shapes.clear();
        }
    }

    /** Fallback face per (code point, bold, italic): an engine, or {@link #NO_FALLBACK}. */
    private static final ConcurrentHashMap<Long, Object> FALLBACKS = new ConcurrentHashMap<Long, Object>();
    private static final Object NO_FALLBACK = new Object();

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
        LineMetrics metrics = custom ? null : lineMetrics;
        if (metrics != null && (bold || size != DEFAULT_SIZE)) {
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
        if (font == 0L) {
            return text.codePointCount(0, text.length()) * 0.6f * size;
        }
        // Unhinted advances scale linearly with size, so one shaping per text serves every size.
        return shape(text).width(size);
    }

    /**
     * The text split into runs, each in one font, script and direction, in visual (left to right) order:
     * bidirectional text is reordered ({@link java.text.Bidi}) and a character this font lacks is taken from a
     * fallback face that has it. {@link #width} and the renderers use these runs, so what is measured is what is
     * drawn. {@code null} when there is no font file or no text. Cached.
     */
    public JXShapedText shape(String text) {
        if (font == 0L || text == null || text.isEmpty()) {
            return null;
        }
        JXShapedText shaped = shapes.get(text);
        if (shaped == null) {
            shaped = itemize(text);
            if (shapes.size() >= MAX_CACHED) {
                shapes.clear(); // ponytail: crude eviction, an LRU if big apps thrash it
            }
            shapes.put(text, shaped);
        }
        return shaped;
    }

    private final ConcurrentHashMap<String, JXShapedText> shapes = new ConcurrentHashMap<String, JXShapedText>();
    /** Whether this font has a glyph for each BMP code point: 0 unknown, 1 yes, 2 no. */
    private volatile byte[] bmpCoverage;
    private final ConcurrentHashMap<Integer, Boolean> astralCoverage = new ConcurrentHashMap<Integer, Boolean>();

    /** The index of this face in its file (0 unless the file is a collection). */
    public int getFaceIndex() {
        return faceIndex;
    }

    /** True if the font has a glyph for the code point. */
    public boolean covers(int codePoint) {
        if (font == 0L) {
            return false;
        }
        if (codePoint < 0x10000) {
            byte[] table = bmpCoverage;
            if (table == null) {
                table = new byte[0x10000];
                bmpCoverage = table;
            }
            byte known = table[codePoint];
            if (known == 0) {
                known = (byte) (hasGlyph(codePoint) ? 1 : 2);
                table[codePoint] = known;
            }
            return known == 1;
        }
        Boolean known = astralCoverage.get(codePoint);
        if (known == null) {
            known = hasGlyph(codePoint);
            astralCoverage.put(codePoint, known);
        }
        return known;
    }

    private boolean hasGlyph(int codePoint) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            java.nio.IntBuffer glyph = stack.mallocInt(1);
            return hb_font_get_nominal_glyph(font, codePoint, glyph) && glyph.get(0) != 0;
        }
    }

    /** Marks, joiners, variation selectors, modifiers and controls: they belong to the character before them. */
    private static boolean sticky(int cp) {
        int type = Character.getType(cp);
        return type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK
                || type == Character.COMBINING_SPACING_MARK || type == Character.FORMAT || type == Character.CONTROL
                || cp >= 0xFE00 && cp <= 0xFE0F || cp >= 0xE0100 && cp <= 0xE01EF || cp >= 0x1F3FB && cp <= 0x1F3FF
                || cp >= 0xE0020 && cp <= 0xE007F;
    }

    /** The face for a character this font lacks: the first fallback face that has it, else this font (its notdef). */
    private JXTextEngine fallbackFor(int cp) {
        long key = ((long) cp << 2) | (weight >= 600 ? 2 : 0) | (italic ? 1 : 0);
        Object known = FALLBACKS.get(key);
        if (known == null) {
            known = NO_FALLBACK;
            for (JXFonts.Face candidate : JXFonts.fallbacks(cp)) {
                JXFonts.Face sibling = JXFonts.sibling(candidate, weight, italic);
                JXTextEngine engine = forFace(sibling);
                if (!engine.covers(cp) && sibling != candidate) {
                    engine = forFace(candidate);
                }
                if (engine.covers(cp)) {
                    known = engine;
                    break;
                }
            }
            FALLBACKS.put(key, known);
        }
        return known == NO_FALLBACK ? this : (JXTextEngine) known;
    }

    private static Character.UnicodeScript scriptGroup(int cp, Character.UnicodeScript previous) {
        Character.UnicodeScript script = Character.UnicodeScript.of(cp);
        if (script == Character.UnicodeScript.COMMON || script == Character.UnicodeScript.INHERITED) {
            return previous;
        }
        if (script == Character.UnicodeScript.HIRAGANA || script == Character.UnicodeScript.KATAKANA) {
            return Character.UnicodeScript.HAN; // one run: Japanese text mixes the three
        }
        return script;
    }

    private JXShapedText itemize(String text) {
        int length = text.length();
        java.text.Bidi bidi = null;
        if (java.text.Bidi.requiresBidi(text.toCharArray(), 0, length)) {
            java.text.Bidi analysed = new java.text.Bidi(text, java.text.Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
            if (!analysed.isLeftToRight()) {
                bidi = analysed;
            }
        }
        java.util.ArrayList<JXShapedText.Run> runs = new java.util.ArrayList<JXShapedText.Run>();
        if (bidi == null) {
            split(text, 0, length, false, runs);
        } else {
            int count = bidi.getRunCount();
            byte[] levels = new byte[count];
            Object[] groups = new Object[count];
            for (int i = 0; i < count; i++) {
                int level = bidi.getRunLevel(i);
                java.util.ArrayList<JXShapedText.Run> group = new java.util.ArrayList<JXShapedText.Run>();
                split(text, bidi.getRunStart(i), bidi.getRunLimit(i), (level & 1) != 0, group);
                if ((level & 1) != 0) {
                    java.util.Collections.reverse(group); // inside a right-to-left run the pieces go right to left
                }
                levels[i] = (byte) level;
                groups[i] = group;
            }
            java.text.Bidi.reorderVisually(levels, 0, groups, 0, count);
            for (Object group : groups) {
                @SuppressWarnings("unchecked")
                java.util.List<JXShapedText.Run> list = (java.util.List<JXShapedText.Run>) group;
                runs.addAll(list);
            }
        }
        return new JXShapedText(text, runs.toArray(new JXShapedText.Run[0]));
    }

    /** Splits [start, limit) into pieces of one font and script, each shaped, in logical order. */
    private void split(String text, int start, int limit, boolean rtl, java.util.List<JXShapedText.Run> out) {
        JXTextEngine current = null;
        Character.UnicodeScript script = null;
        int pieceStart = start;
        int i = start;
        while (i < limit) {
            int cp = text.codePointAt(i);
            int next = i + Character.charCount(cp);
            JXTextEngine face;
            Character.UnicodeScript s = script;
            if (current != null && sticky(cp)) {
                face = current;
            } else {
                face = covers(cp) || sticky(cp) ? this : fallbackFor(cp);
                s = scriptGroup(cp, script);
            }
            if (current != null && (face != current || s != script && script != null && s != null)) {
                out.add(current.shapePiece(text, pieceStart, i, rtl));
                pieceStart = i;
            }
            current = face;
            script = s != null ? s : script;
            i = next;
        }
        if (current != null) {
            out.add(current.shapePiece(text, pieceStart, limit, rtl));
        }
    }

    /** Shapes [start, end) of the text with this font alone. */
    private JXShapedText.Run shapePiece(String text, int start, int end, boolean rtl) {
        long buffer = hb_buffer_create();
        try {
            hb_buffer_add_utf16(buffer, text, start, end - start);
            hb_buffer_guess_segment_properties(buffer);
            hb_buffer_set_direction(buffer, rtl ? HB_DIRECTION_RTL : HB_DIRECTION_LTR);
            hb_shape(font, buffer, null);
            org.lwjgl.util.harfbuzz.hb_glyph_info_t.Buffer infos = org.lwjgl.util.harfbuzz.HarfBuzz.hb_buffer_get_glyph_infos(buffer);
            hb_glyph_position_t.Buffer positions = hb_buffer_get_glyph_positions(buffer);
            int n = infos == null || positions == null ? 0 : Math.min(infos.remaining(), positions.remaining());
            int[] glyphs = new int[3 * n];
            int pen = 0;
            for (int g = 0; g < n; g++) {
                hb_glyph_position_t p = positions.get(g);
                glyphs[3 * g] = infos.get(g).codepoint(); // after hb_shape, the glyph id
                glyphs[3 * g + 1] = pen + p.x_offset();
                glyphs[3 * g + 2] = -p.y_offset();
                pen += p.x_advance();
            }
            return new JXShapedText.Run(this, start, end, rtl, glyphs, pen);
        } finally {
            hb_buffer_destroy(buffer);
        }
    }

    /** Font units per em of this face. */
    int unitsPerEm() {
        return unitsPerEm;
    }

    /**
     * The shaped glyphs of {@code text}: glyph ids of this engine's font and their pen positions in
     * pixels at {@code size}, unhinted, as {@link #width} measures them. Returns the glyph count;
     * the arrays grow as needed and are returned through {@code out} ({@code out[0]} ids as
     * {@code int[]}, {@code out[1]} x offsets and {@code out[2]} y offsets as {@code float[]}).
     * For renderers that rasterize the glyphs themselves (the software renderer).
     */
    public int glyphs(String text, float size, Object[] out) {
        if (font == 0L || text == null || text.isEmpty()) {
            return 0;
        }
        // Shaped once per text, in font units (unhinted: they scale with the size), as the advances.
        int[] run = runs.get(text);
        if (run == null) {
            run = shapeRun(text);
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

    private int[] shapeRun(String text) {
        long buffer = hb_buffer_create();
        try {
            hb_buffer_add_utf16(buffer, text, 0, -1);
            hb_buffer_guess_segment_properties(buffer);
            hb_shape(font, buffer, null);
            org.lwjgl.util.harfbuzz.hb_glyph_info_t.Buffer infos = org.lwjgl.util.harfbuzz.HarfBuzz.hb_buffer_get_glyph_infos(buffer);
            hb_glyph_position_t.Buffer positions = hb_buffer_get_glyph_positions(buffer);
            int n = infos == null || positions == null ? 0 : Math.min(infos.remaining(), positions.remaining());
            int[] run = new int[3 * n];
            int pen = 0;
            for (int i = 0; i < n; i++) {
                hb_glyph_position_t p = positions.get(i);
                run[3 * i] = infos.get(i).codepoint(); // after hb_shape, the glyph id
                run[3 * i + 1] = pen + p.x_offset();
                run[3 * i + 2] = -p.y_offset();
                pen += p.x_advance();
            }
            return run;
        } finally {
            hb_buffer_destroy(buffer);
        }
    }

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
        if (metrics == null || custom) {
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
