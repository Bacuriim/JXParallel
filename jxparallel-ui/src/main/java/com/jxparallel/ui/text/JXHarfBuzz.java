package com.jxparallel.ui.text;

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
 * The HarfBuzz font behind {@link JXTextEngine}: the only class of the text engine that touches LWJGL, so
 * a platform without it (Android, which measures text through its own host, see {@link JXTextEngine.LineMetrics})
 * never loads it.
 */
final class JXHarfBuzz {
    final long font;
    final int unitsPerEm;
    final int ascender;
    final int descender;
    final int lineGap;

    JXHarfBuzz(String fontFile) {
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

    /** Glyph id, pen x and y (y down) in font units, per glyph. */
    int[] shapeRun(String text) {
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

    /** Total advance of the shaped text in font units. */
    int shapeAdvance(String text) {
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
}
