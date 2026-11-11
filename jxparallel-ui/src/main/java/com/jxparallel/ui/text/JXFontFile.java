package com.jxparallel.ui.text;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the names and style of the faces in a TrueType/OpenType file (.ttf, .otf, and the .ttc/.otc
 * collections) from its {@code name}, {@code OS/2} and {@code head} tables. Pure Java and cheap: only the
 * table directory and those small tables are read, so scanning a system font folder does not load the fonts.
 */
final class JXFontFile {
    /** One face of a file. */
    static final class Info {
        /** Family as the font's designer grouped it (typographic family when there is one). */
        final String family;
        /** Family of the 4-style legacy grouping (name id 1), e.g. "Segoe UI Semibold"; may equal {@link #family}. */
        final String legacyFamily;
        final String style;
        final int weight;
        final boolean italic;
        final int index;

        Info(String family, String legacyFamily, String style, int weight, boolean italic, int index) {
            this.family = family;
            this.legacyFamily = legacyFamily;
            this.style = style;
            this.weight = weight;
            this.italic = italic;
            this.index = index;
        }
    }

    private static final int TAG_TTCF = 0x74746366;
    private static final int TAG_NAME = 0x6E616D65;
    private static final int TAG_OS2 = 0x4F532F32;
    private static final int TAG_HEAD = 0x68656164;

    private JXFontFile() {
    }

    /** The faces of the file; empty if it is not a font this reader understands. */
    static List<Info> read(File file) {
        List<Info> out = new ArrayList<Info>();
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            long length = in.length();
            if (length < 12) {
                return out;
            }
            in.seek(0);
            int tag = in.readInt();
            if (tag == TAG_TTCF) {
                in.skipBytes(4); // version
                int count = in.readInt();
                if (count < 1 || count > 256) {
                    return out;
                }
                long[] offsets = new long[count];
                for (int i = 0; i < count; i++) {
                    offsets[i] = in.readInt() & 0xFFFFFFFFL;
                }
                for (int i = 0; i < count; i++) {
                    Info info = face(in, offsets[i], i, length);
                    if (info != null) {
                        out.add(info);
                    }
                }
            } else {
                Info info = face(in, 0, 0, length);
                if (info != null) {
                    out.add(info);
                }
            }
        } catch (IOException | RuntimeException e) {
            // unreadable or damaged: not a usable font
        }
        return out;
    }

    private static Info face(RandomAccessFile in, long start, int index, long length) throws IOException {
        in.seek(start);
        int version = in.readInt();
        if (version != 0x00010000 && version != 0x4F54544F /* OTTO */ && version != 0x74727565 /* true */) {
            return null;
        }
        int tables = in.readUnsignedShort();
        in.skipBytes(6);
        long name = -1;
        long os2 = -1;
        long head = -1;
        long nameLength = 0;
        long os2Length = 0;
        for (int i = 0; i < tables; i++) {
            int tag = in.readInt();
            in.skipBytes(4); // checksum
            long offset = in.readInt() & 0xFFFFFFFFL;
            long size = in.readInt() & 0xFFFFFFFFL;
            if (offset + size > length) {
                continue;
            }
            if (tag == TAG_NAME) {
                name = offset;
                nameLength = size;
            } else if (tag == TAG_OS2) {
                os2 = offset;
                os2Length = size;
            } else if (tag == TAG_HEAD) {
                head = offset;
            }
        }
        if (name < 0) {
            return null;
        }
        String[] names = names(in, name, nameLength);
        String legacy = names[1];
        String family = names[16] != null ? names[16] : legacy;
        String style = names[17] != null ? names[17] : names[2];
        if (family == null) {
            return null;
        }
        int weight = 0;
        boolean italic = false;
        boolean bold = false;
        if (os2 >= 0 && os2Length >= 64) {
            in.seek(os2 + 4);
            weight = in.readUnsignedShort();
            in.seek(os2 + 62);
            int selection = in.readUnsignedShort();
            italic = (selection & 1) != 0;
            bold = (selection & 32) != 0;
        }
        if (head >= 0) {
            in.seek(head + 44);
            int mac = in.readUnsignedShort();
            bold |= (mac & 1) != 0;
            italic |= (mac & 2) != 0;
        }
        if (weight < 1 || weight > 1000) {
            weight = bold ? 700 : 400;
        }
        if (!italic && style != null) {
            String s = style.toLowerCase(java.util.Locale.ROOT);
            italic = s.contains("italic") || s.contains("oblique");
        }
        return new Info(family, legacy == null ? family : legacy, style == null ? "" : style, weight, italic, index);
    }

    /** Name ids 1, 2, 16 and 17 (others null), preferring English (US) strings. */
    private static String[] names(RandomAccessFile in, long table, long size) throws IOException {
        String[] found = new String[18];
        int[] score = new int[18];
        in.seek(table + 2);
        int count = in.readUnsignedShort();
        int storage = in.readUnsignedShort();
        if (count > 4096) {
            return found;
        }
        int[][] records = new int[count][];
        for (int i = 0; i < count; i++) {
            records[i] = new int[] {in.readUnsignedShort(), in.readUnsignedShort(), in.readUnsignedShort(),
                    in.readUnsignedShort(), in.readUnsignedShort(), in.readUnsignedShort()};
        }
        for (int[] r : records) {
            int id = r[3];
            if (id != 1 && id != 2 && id != 16 && id != 17) {
                continue;
            }
            int platform = r[0];
            int language = r[2];
            int s;
            if (platform == 3 && language == 0x409) {
                s = 4;
            } else if (platform == 3) {
                s = 2;
            } else if (platform == 0) {
                s = 2;
            } else if (platform == 1 && language == 0) {
                s = 3;
            } else {
                continue;
            }
            if (s <= score[id] || r[4] == 0 || storage + r[5] + r[4] > size) {
                continue;
            }
            byte[] bytes = new byte[r[4]];
            in.seek(table + storage + r[5]);
            in.readFully(bytes);
            String value = new String(bytes, platform == 1 ? Charset.forName("ISO-8859-1") : Charset.forName("UTF-16BE")).trim();
            if (!value.isEmpty()) {
                found[id] = value;
                score[id] = s;
            }
        }
        return found;
    }
}
