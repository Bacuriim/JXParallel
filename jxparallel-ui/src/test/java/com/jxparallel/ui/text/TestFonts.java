package com.jxparallel.ui.text;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.TreeSet;

/**
 * Builds tiny TrueType fonts for tests, so the tests do not depend on the fonts of the machine: one square
 * glyph per code point asked for (each its own glyph id), every glyph the same advance, a name table and an
 * OS/2 table that say the family, style, weight and italic. Valid enough for HarfBuzz, FreeType and the
 * font file reader; not for Skia on every platform.
 */
public final class TestFonts {
    private TestFonts() {
    }

    /** Writes a font covering {@code chars}; each glyph is 1000-unit em, advance {@code advance}, ink x 100..500, y 0..700. */
    public static File write(File dir, String family, String style, int weight, boolean italic, int advance, String chars)
            throws IOException {
        File file = File.createTempFile("testfont", ".ttf", dir);
        file.deleteOnExit();
        Files.write(file.toPath(), build(family, style, weight, italic, advance, chars));
        return file;
    }

    public static byte[] build(String family, String style, int weight, boolean italic, int advance, String chars)
            throws IOException {
        TreeSet<Integer> set = new TreeSet<Integer>();
        for (int i = 0; i < chars.length(); ) {
            int cp = chars.codePointAt(i);
            set.add(cp);
            i += Character.charCount(cp);
        }
        Integer[] cps = set.toArray(new Integer[0]);
        int glyphs = cps.length + 1;

        byte[] cmap = cmap(cps);
        byte[] glyf = glyf(cps.length);
        byte[] loca = loca(cps.length);
        byte[] head = head(italic, weight >= 600);
        byte[] hhea = hhea(glyphs, advance);
        byte[] maxp = maxp(glyphs);
        byte[] hmtx = hmtx(glyphs, advance);
        byte[] os2 = os2(weight, italic, cps);
        byte[] name = name(family, style);
        byte[] post = post();

        String[] tags = {"OS/2", "cmap", "glyf", "head", "hhea", "hmtx", "loca", "maxp", "name", "post"};
        byte[][] tables = {os2, cmap, glyf, head, hhea, hmtx, loca, maxp, name, post};
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x00010000);
        out.writeShort(tags.length);
        out.writeShort(128);
        out.writeShort(3);
        out.writeShort(tags.length * 16 - 128);
        int offset = 12 + 16 * tags.length;
        for (int i = 0; i < tags.length; i++) {
            out.writeBytes(tags[i]);
            out.writeInt(checksum(tables[i]));
            out.writeInt(offset);
            out.writeInt(tables[i].length);
            offset += (tables[i].length + 3) & ~3;
        }
        for (byte[] table : tables) {
            out.write(table);
            for (int pad = table.length; (pad & 3) != 0; pad++) {
                out.write(0);
            }
        }
        return bytes.toByteArray();
    }

    private static int checksum(byte[] table) {
        int sum = 0;
        for (int i = 0; i < table.length; i += 4) {
            int word = 0;
            for (int k = 0; k < 4; k++) {
                word = (word << 8) | (i + k < table.length ? table[i + k] & 0xFF : 0);
            }
            sum += word;
        }
        return sum;
    }

    private static byte[] cmap(Integer[] cps) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(0);
        out.writeShort(1);
        out.writeShort(3);
        out.writeShort(10);
        out.writeInt(12);
        out.writeShort(12);
        out.writeShort(0);
        out.writeInt(16 + 12 * cps.length);
        out.writeInt(0);
        out.writeInt(cps.length);
        for (int i = 0; i < cps.length; i++) {
            out.writeInt(cps[i]);
            out.writeInt(cps[i]);
            out.writeInt(i + 1);
        }
        return bytes.toByteArray();
    }

    private static byte[] glyf(int count) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        for (int g = 0; g < count; g++) {
            out.writeShort(1); // contours
            out.writeShort(100);
            out.writeShort(0);
            out.writeShort(500);
            out.writeShort(700);
            out.writeShort(3); // end point of the contour
            out.writeShort(0); // no instructions
            out.write(new byte[] {1, 1, 1, 1}); // on curve, 16-bit deltas
            int[] dx = {100, 0, 400, 0};
            for (int d : dx) {
                out.writeShort(d);
            }
            int[] dy = {0, 700, 0, -700};
            for (int d : dy) {
                out.writeShort(d);
            }
            out.writeShort(0); // 34 bytes of glyph, padded to 36
        }
        return bytes.toByteArray();
    }

    private static byte[] loca(int count) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        int length = 36;
        out.writeInt(0);
        for (int g = 0; g <= count; g++) {
            out.writeInt(g * length);
        }
        return bytes.toByteArray();
    }

    private static byte[] head(boolean italic, boolean bold) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x00010000);
        out.writeInt(0x00010000);
        out.writeInt(0);
        out.writeInt(0x5F0F3CF5);
        out.writeShort(0);
        out.writeShort(1000);
        out.writeLong(0);
        out.writeLong(0);
        out.writeShort(0);
        out.writeShort(0);
        out.writeShort(1000);
        out.writeShort(1000);
        out.writeShort((bold ? 1 : 0) | (italic ? 2 : 0));
        out.writeShort(8);
        out.writeShort(2);
        out.writeShort(1); // long loca
        out.writeShort(0);
        return bytes.toByteArray();
    }

    private static byte[] hhea(int glyphs, int advance) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x00010000);
        out.writeShort(800);
        out.writeShort(-200);
        out.writeShort(0);
        out.writeShort(advance);
        out.writeShort(100);
        out.writeShort(100);
        out.writeShort(500);
        out.writeShort(1);
        out.writeShort(0);
        out.writeShort(0);
        for (int i = 0; i < 4; i++) {
            out.writeShort(0);
        }
        out.writeShort(0);
        out.writeShort(glyphs);
        return bytes.toByteArray();
    }

    private static byte[] maxp(int glyphs) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x00010000);
        out.writeShort(glyphs);
        out.writeShort(4);
        out.writeShort(1);
        out.writeShort(0); // composite points
        out.writeShort(0); // composite contours
        out.writeShort(2); // zones
        for (int i = 0; i < 8; i++) {
            out.writeShort(0);
        }
        return bytes.toByteArray();
    }

    private static byte[] hmtx(int glyphs, int advance) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        for (int g = 0; g < glyphs; g++) {
            out.writeShort(advance);
            out.writeShort(g == 0 ? 0 : 100);
        }
        return bytes.toByteArray();
    }

    private static byte[] os2(int weight, boolean italic, Integer[] cps) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(0);
        out.writeShort(500);
        out.writeShort(weight);
        out.writeShort(5);
        out.writeShort(0);
        for (int i = 0; i < 10; i++) {
            out.writeShort(0);
        }
        out.writeShort(0);
        out.write(new byte[10]);
        out.write(new byte[16]);
        out.writeBytes("TEST");
        out.writeShort((italic ? 1 : 0) | (weight >= 600 ? 32 : 0) | (!italic && weight < 600 ? 64 : 0));
        out.writeShort(Math.min(0xFFFF, cps.length == 0 ? 0 : cps[0]));
        out.writeShort(Math.min(0xFFFF, cps.length == 0 ? 0 : cps[cps.length - 1]));
        out.writeShort(800);
        out.writeShort(-200);
        out.writeShort(0);
        out.writeShort(800);
        out.writeShort(200);
        return bytes.toByteArray();
    }

    private static byte[] name(String family, String style) throws IOException {
        String[] values = {family, style, family + " " + style, family + "-" + style.replace(' ', '-')};
        int[] ids = {1, 2, 4, 6};
        ByteArrayOutputStream strings = new ByteArrayOutputStream();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(0);
        out.writeShort(ids.length);
        out.writeShort(6 + 12 * ids.length);
        for (int i = 0; i < ids.length; i++) {
            byte[] value = values[i].getBytes(Charset.forName("UTF-16BE"));
            out.writeShort(3);
            out.writeShort(1);
            out.writeShort(0x409);
            out.writeShort(ids[i]);
            out.writeShort(value.length);
            out.writeShort(strings.size());
            strings.write(value);
        }
        out.write(strings.toByteArray());
        return bytes.toByteArray();
    }

    private static byte[] post() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x00030000);
        out.writeInt(0);
        out.writeShort(-100);
        out.writeShort(50);
        out.writeInt(0);
        out.write(new byte[16]);
        return bytes.toByteArray();
    }

    /** All code points of the ranges, as a string: {@code range('a', 'z') + range(0x5D0, 0x5EA)}. */
    public static String range(int from, int to) {
        StringBuilder s = new StringBuilder();
        for (int cp = from; cp <= to; cp++) {
            s.appendCodePoint(cp);
        }
        return s.toString();
    }
}
