package com.jxparallel.ui.text;

import java.util.HashMap;
import java.util.Map;

/**
 * Glyphs of the UI font rasterized by FreeType, unhinted and antialiased (8-bit coverage), per
 * (bold, pixel size, glyph id), for renderers that draw glyph bitmaps themselves (the software
 * renderer). Glyph ids are those of {@link JXTextEngine#glyphs}. Display thread only.
 */
public final class JXGlyphs {
    private static volatile JXGlyphs regular;
    private static volatile JXGlyphs boldCache;
    private static final int MAX = 4096;

    private final long face;
    /** Per pixel size, the glyphs by id (grown as ids appear). */
    private final Map<Integer, Glyph[]> sizes = new HashMap<Integer, Glyph[]>();
    private int lastSize = -1;
    private Glyph[] lastGlyphs;
    private int cached;
    private int currentSize = -1;

    /** A glyph's coverage, row by row, and its offset from the pen (left) and above the baseline (top). */
    public static final class Glyph {
        public final int width;
        public final int height;
        public final int left;
        public final int top;
        public final byte[] alpha;

        Glyph(int width, int height, int left, int top, byte[] alpha) {
            this.width = width;
            this.height = height;
            this.left = left;
            this.top = top;
            this.alpha = alpha;
        }
    }

    public static JXGlyphs get(boolean bold) {
        if (bold) {
            if (boldCache == null) {
                synchronized (JXGlyphs.class) {
                    if (boldCache == null) {
                        String file = JXTextEngine.get(true).getFontFile();
                        boldCache = new JXGlyphs(file);
                    }
                }
            }
            return boldCache;
        }
        if (regular == null) {
            synchronized (JXGlyphs.class) {
                if (regular == null) {
                    regular = new JXGlyphs(JXTextEngine.get().getFontFile());
                }
            }
        }
        return regular;
    }

    private static long library;

    private JXGlyphs(String fontFile) {
        long f = 0L;
        if (fontFile != null) {
            try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
                org.lwjgl.PointerBuffer out = stack.mallocPointer(1);
                synchronized (JXGlyphs.class) {
                    if (library == 0L) {
                        if (org.lwjgl.util.freetype.FreeType.FT_Init_FreeType(out) == 0) {
                            library = out.get(0);
                        }
                    }
                }
                if (library != 0L && org.lwjgl.util.freetype.FreeType.FT_New_Face(library, fontFile, 0, out) == 0) {
                    f = out.get(0);
                }
            }
        }
        face = f;
    }

    /** The glyph at a pixel size, or null without a font. */
    public Glyph glyph(int id, float pixelSize) {
        if (face == 0L) {
            return null;
        }
        int size = Math.max(1, Math.round(pixelSize));
        if (size != lastSize) {
            lastGlyphs = sizes.get(size);
            if (lastGlyphs == null) {
                lastGlyphs = new Glyph[256];
                sizes.put(size, lastGlyphs);
            }
            lastSize = size;
        }
        if (id < 0) {
            return null;
        }
        if (id < lastGlyphs.length && lastGlyphs[id] != null) {
            return lastGlyphs[id];
        }
        Glyph g;
        org.lwjgl.util.freetype.FT_Face ft = org.lwjgl.util.freetype.FT_Face.create(face);
        if (size != currentSize) {
            org.lwjgl.util.freetype.FreeType.FT_Set_Pixel_Sizes(ft, 0, size);
            currentSize = size;
        }
        // unhinted outlines, as the layout's HarfBuzz advances; antialiased 8-bit coverage
        if (org.lwjgl.util.freetype.FreeType.FT_Load_Glyph(ft, id,
                org.lwjgl.util.freetype.FreeType.FT_LOAD_NO_HINTING | org.lwjgl.util.freetype.FreeType.FT_LOAD_RENDER) != 0) {
            g = new Glyph(0, 0, 0, 0, new byte[0]);
        } else {
            org.lwjgl.util.freetype.FT_GlyphSlot slot = ft.glyph();
            org.lwjgl.util.freetype.FT_Bitmap bitmap = slot.bitmap();
            int w = bitmap.width();
            int h = bitmap.rows();
            int pitch = bitmap.pitch();
            byte[] alpha = new byte[w * h];
            java.nio.ByteBuffer buffer = w * h == 0 ? null : bitmap.buffer(Math.abs(pitch) * h);
            for (int row = 0; row < h; row++) {
                for (int col = 0; col < w; col++) {
                    alpha[row * w + col] = buffer.get(row * pitch + col);
                }
            }
            g = new Glyph(w, h, slot.bitmap_left(), slot.bitmap_top(), alpha);
        }
        if (cached >= MAX) { // crude eviction: start over
            sizes.clear();
            cached = 0;
            lastGlyphs = new Glyph[256];
            sizes.put(size, lastGlyphs);
        }
        if (id >= lastGlyphs.length) {
            lastGlyphs = java.util.Arrays.copyOf(lastGlyphs, Math.max(id + 1, lastGlyphs.length * 2));
            sizes.put(size, lastGlyphs);
        }
        lastGlyphs[id] = g;
        cached++;
        return g;
    }
}
