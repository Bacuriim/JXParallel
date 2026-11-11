package com.jxparallel.ui.text;

/**
 * A text shaped into runs, in visual order (left to right on screen). Each run is a stretch of the text in
 * one font face, one script and one direction, with the glyphs HarfBuzz placed. A renderer draws the runs one
 * after another, each at the pen position the previous ones left; {@link JXTextEngine#width} is the sum of
 * their advances, so measuring and drawing use the same numbers.
 */
public final class JXShapedText {
    /** One run: glyph ids of {@link #face()} and their positions, in the face's font units. */
    public static final class Run {
        private final JXTextEngine face;
        private final int start;
        private final int end;
        private final boolean rtl;
        private final int[] glyphs;
        private final int advance;

        Run(JXTextEngine face, int start, int end, boolean rtl, int[] glyphs, int advance) {
            this.face = face;
            this.start = start;
            this.end = end;
            this.rtl = rtl;
            this.glyphs = glyphs;
            this.advance = advance;
        }

        /** The font face of this run: the requested font, or the fallback face that has its characters. */
        public JXTextEngine face() {
            return face;
        }

        /** Index in the text of the first char of the run (logical order). */
        public int start() {
            return start;
        }

        /** Index after the last char of the run. */
        public int end() {
            return end;
        }

        /** True if the run reads right to left (its glyphs are still listed left to right). */
        public boolean rtl() {
            return rtl;
        }

        public int glyphCount() {
            return glyphs.length / 3;
        }

        public int glyphId(int i) {
            return glyphs[3 * i];
        }

        /** Pen x of glyph {@code i} from the start of the run, in pixels at {@code size}. */
        public float glyphX(int i, float size) {
            return glyphs[3 * i + 1] * (size / face.unitsPerEm());
        }

        /** Vertical offset of glyph {@code i} (down is positive), in pixels at {@code size}. */
        public float glyphY(int i, float size) {
            return glyphs[3 * i + 2] * (size / face.unitsPerEm());
        }

        /** Advance of the run in pixels at {@code size}. */
        public float advance(float size) {
            return advance * size / face.unitsPerEm();
        }
    }

    private final String text;
    private final Run[] runs;

    JXShapedText(String text, Run[] runs) {
        this.text = text;
        this.runs = runs;
    }

    public String text() {
        return text;
    }

    /** The runs in visual order. */
    public Run[] runs() {
        return runs;
    }

    /** The substring of {@code run} in logical order. */
    public String text(Run run) {
        return text.substring(run.start, run.end);
    }

    /** True if the whole text is one left-to-right run of the face asked for: no fallback, no reordering. */
    public boolean simple(JXTextEngine requested) {
        return runs.length == 1 && runs[0].face == requested && !runs[0].rtl;
    }

    /** Advance width of the text in pixels at {@code size}. */
    public float width(float size) {
        if (runs.length == 1) {
            return runs[0].advance(size); // the same arithmetic the single-font measuring always had
        }
        float width = 0;
        for (Run run : runs) {
            width += run.advance(size);
        }
        return width;
    }
}
