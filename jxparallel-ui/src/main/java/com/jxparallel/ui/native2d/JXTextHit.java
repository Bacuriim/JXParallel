package com.jxparallel.ui.native2d;

import java.util.List;

import com.jxparallel.ui.text.JXTextEngine;

/**
 * Where a pointer falls in the text of an input or text area, computed exactly as {@link JXPaint}
 * places the text (padding, side children, horizontal scroll to the caret, wrapped lines).
 */
public final class JXTextHit {
    private JXTextHit() {
    }

    /** Caret index nearest to window x in a one-line input ({@code input}, {@code password}, {@code spinner}...). */
    public static int fieldIndex(JXNativeNode node, double windowX) {
        String value = JXPaint.text(node, "value");
        if ("password".equals(node.getType())) {
            value = JXPaint.bullets(value.length());
        }
        float size = JXPaint.fontSize(node);
        JXTextEngine engine = JXTextEngine.get(JXPaint.bold(node));
        float[] pad = JXPaint.insetsOr(node, JXNativeNode.PAD_Y, JXNativeNode.FIELD_PAD_X);
        float[] sides = JXControlLayout.sideWidths(node);
        float left = node.getX() + pad[3] + sides[0];
        float right = node.getX() + node.getWidth() - pad[1] - sides[1] - buttonWidth(node);
        float available = right - left;
        float full = engine.width(value, size);
        int caret = (int) JXPaint.number(node, "caret", -1);
        float shift = 0;
        String align = String.valueOf(node.getProperty("textAlignment"));
        if (full < available && "CENTER".equalsIgnoreCase(align)) {
            shift = -(available - full) / 2;
        } else if (full < available && "RIGHT".equalsIgnoreCase(align)) {
            shift = -(available - full);
        } else if (caret >= 0 && caret <= value.length()) {
            shift = Math.max(0, engine.width(value.substring(0, caret), size) - available + 1);
        }
        return nearest(value, (float) (windowX - left + shift), engine, size);
    }

    /** Caret index nearest to window (x, y) in a text area. */
    public static int areaIndex(JXNativeNode node, double windowX, double windowY) {
        String value = JXPaint.text(node, "value");
        float size = JXPaint.fontSize(node);
        boolean bold = JXPaint.bold(node);
        JXTextEngine engine = JXTextEngine.get(bold);
        float line = engine.lineHeight(size);
        float left = node.getX() + 1 + JXNativeNode.FIELD_PAD_X;
        float top = node.getY() + 1 + JXNativeNode.PAD_Y - (float) JXPaint.number(node, "scrollTop", 0);
        List<String> lines = lines(node, value, size, bold);
        int row = (int) Math.floor((windowY - top) / line);
        row = Math.max(0, Math.min(lines.size() - 1, row));
        int offset = 0;
        for (int i = 0; i < row; i++) {
            offset += lines.get(i).length() + JXTextLayout.separatorAfter(value, offset + lines.get(i).length());
        }
        return offset + nearest(lines.get(row), (float) (windowX - left), engine, size);
    }

    /** Line and column of a caret index in a text area: {line, index of the line's first character}. */
    public static int[] areaLineOf(JXNativeNode node, int index) {
        String value = JXPaint.text(node, "value");
        List<String> lines = lines(node, value, JXPaint.fontSize(node), JXPaint.bold(node));
        int offset = 0;
        for (int i = 0; i < lines.size(); i++) {
            int end = offset + lines.get(i).length();
            if (index <= end || i == lines.size() - 1) {
                return new int[] {i, offset};
            }
            offset = end + JXTextLayout.separatorAfter(value, end);
        }
        return new int[] {0, 0};
    }

    /** Caret index on line {@code line} nearest to the x of {@code fromIndex}, for up/down arrows. */
    public static int areaVertical(JXNativeNode node, int fromIndex, int lineDelta) {
        String value = JXPaint.text(node, "value");
        float size = JXPaint.fontSize(node);
        boolean bold = JXPaint.bold(node);
        JXTextEngine engine = JXTextEngine.get(bold);
        List<String> lines = lines(node, value, size, bold);
        int[] at = areaLineOf(node, fromIndex);
        int target = at[0] + lineDelta;
        if (target < 0) {
            return 0;
        }
        if (target >= lines.size()) {
            return value.length();
        }
        String current = lines.get(at[0]);
        float x = engine.width(current.substring(0, Math.min(current.length(), fromIndex - at[1])), size);
        int offset = 0;
        for (int i = 0; i < target; i++) {
            offset += lines.get(i).length() + JXTextLayout.separatorAfter(value, offset + lines.get(i).length());
        }
        return offset + nearest(lines.get(target), x, engine, size);
    }

    /** Number of lines a text area shows its value in (after wrapping). */
    public static int areaLineCount(JXNativeNode node) {
        return lines(node, JXPaint.text(node, "value"), JXPaint.fontSize(node), JXPaint.bold(node)).size();
    }

    /** Height of one text line of the node. */
    public static float lineHeight(JXNativeNode node) {
        return JXTextEngine.get(JXPaint.bold(node)).lineHeight(JXPaint.fontSize(node));
    }

    private static List<String> lines(JXNativeNode node, String value, float size, boolean bold) {
        float width = Boolean.TRUE.equals(node.getProperty("wrapText"))
                ? node.getWidth() - 2 - 2 * JXNativeNode.FIELD_PAD_X : Float.MAX_VALUE;
        return JXTextLayout.lines(value, width, size, bold);
    }

    private static int nearest(String text, float x, JXTextEngine engine, float size) {
        if (x <= 0) {
            return 0;
        }
        float previous = 0;
        for (int i = 1; i <= text.length(); i++) {
            float w = engine.width(text.substring(0, i), size);
            if (x < (previous + w) / 2) {
                return i - 1;
            }
            previous = w;
        }
        return text.length();
    }

    private static float buttonWidth(JXNativeNode node) {
        String type = node.getType();
        if ("spinner".equals(type)) {
            return JXControlLayout.SPINNER_BUTTON;
        }
        if ("datepicker".equals(type)) {
            return JXControlLayout.DATE_BUTTON;
        }
        if ("select".equals(type)) {
            return JXNativeNode.ARROW;
        }
        return 0;
    }
}
