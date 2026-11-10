package com.jxparallel.ui.native2d;

import java.util.ArrayList;
import java.util.List;

import com.jxparallel.ui.text.JXFont;
import com.jxparallel.ui.text.JXTextEngine;

/** Line breaking and ellipsis, measured with the same text engine as layout. */
final class JXTextLayout {
    static final String ELLIPSIS = "...";

    private JXTextLayout() {
    }

    /**
     * Lines of {@code value}: split at line breaks, then wrapped at spaces to {@code maxWidth}
     * (a word longer than a line is broken between characters). Wrapped lines keep their trailing
     * spaces, so the lines and their separators ({@link #separatorAfter}) add up to the value.
     */
    static List<String> lines(String value, float maxWidth, float size, boolean bold) {
        return lines(value, maxWidth, size, JXFont.of(bold));
    }

    static List<String> lines(String value, float maxWidth, float size, JXFont font) {
        List<String> out = new ArrayList<String>();
        JXTextEngine engine = JXTextEngine.get(font);
        int start = 0;
        int length = value.length();
        while (true) {
            int end = value.indexOf('\n', start);
            int paragraphEnd = end < 0 ? length : end;
            if (paragraphEnd > start && value.charAt(paragraphEnd - 1) == '\r') {
                paragraphEnd--;
            }
            wrap(value.substring(start, paragraphEnd), maxWidth, size, engine, out);
            if (end < 0) {
                break;
            }
            start = end + 1;
        }
        return out;
    }

    private static void wrap(String paragraph, float maxWidth, float size, JXTextEngine engine, List<String> out) {
        if (maxWidth == Float.MAX_VALUE || engine.width(paragraph, size) <= maxWidth) {
            out.add(paragraph);
            return;
        }
        int lineStart = 0;
        while (lineStart < paragraph.length()) {
            int fit = lineStart;
            int lastBreak = -1;
            for (int i = lineStart; i < paragraph.length(); i++) {
                char c = paragraph.charAt(i);
                if (Character.isHighSurrogate(c) && i + 1 < paragraph.length() && Character.isLowSurrogate(paragraph.charAt(i + 1))) {
                    continue; // a surrogate pair is one character: never end a line between its halves
                }
                if (engine.width(paragraph.substring(lineStart, i + 1).replaceAll("\\s+$", ""), size) > maxWidth) {
                    break;
                }
                fit = i + 1;
                if (c == ' ' || c == '\t') {
                    lastBreak = i + 1;
                }
            }
            if (fit >= paragraph.length()) {
                out.add(paragraph.substring(lineStart));
                return;
            }
            int end = lastBreak > lineStart ? lastBreak
                    : Math.max(fit, lineStart + Character.charCount(paragraph.codePointAt(lineStart)));
            out.add(paragraph.substring(lineStart, end));
            lineStart = end;
        }
    }

    /** Characters between a line ending at {@code index} and the next line: 1 for "\n", 2 for "\r\n", 0 for a wrap. */
    static int separatorAfter(String value, int index) {
        if (index < value.length() && value.charAt(index) == '\r' && index + 1 < value.length() && value.charAt(index + 1) == '\n') {
            return 2;
        }
        return index < value.length() && value.charAt(index) == '\n' ? 1 : 0;
    }

    /** True if cutting {@code s} at {@code index} would separate the two halves of a surrogate pair. */
    static boolean splitsPair(String s, int index) {
        return index > 0 && index < s.length() && Character.isHighSurrogate(s.charAt(index - 1))
                && Character.isLowSurrogate(s.charAt(index));
    }

    /** {@code value}, or its longest prefix followed by "..." that fits, like JavaFX's default ellipsis. */
    static String ellipsize(String value, float available, float size, boolean bold) {
        return ellipsize(value, available, size, JXFont.of(bold));
    }

    static String ellipsize(String value, float available, float size, JXFont font) {
        if (value.isEmpty()) {
            return value; // nothing to elide: an icon-only button has an empty label and may have no room at all
        }
        JXTextEngine engine = JXTextEngine.get(font);
        if (engine.width(value, size) <= available + 0.01f) {
            return value;
        }
        float ellipsis = engine.width(ELLIPSIS, size);
        int end = value.length();
        while (end > 0 && (splitsPair(value, end) || engine.width(value.substring(0, end), size) + ellipsis > available)) {
            end--;
        }
        return end == 0 ? ELLIPSIS : value.substring(0, end) + ELLIPSIS;
    }
}
