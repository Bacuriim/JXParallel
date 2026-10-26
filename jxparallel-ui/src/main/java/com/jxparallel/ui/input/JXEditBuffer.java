package com.jxparallel.ui.input;

/**
 * The text a soft keyboard edits, with its selection and its composing region: the state machine behind Android's
 * {@code InputConnection} (setComposingText, commitText, finishComposingText, setComposingRegion, setSelection,
 * deleteSurroundingText and the reads of the text around the cursor), without any Android class. The host copies the
 * outcome ({@link #getText}, {@link #getAnchor}, {@link #getCaret}) to the field after each operation.
 *
 * <p>Positions are indexes of chars. The composing region is {@code [composingStart, composingEnd)}, both -1 when
 * there is none. {@code newCursor} is as Android defines it: greater than 0 is relative to the end of the inserted text
 * minus one (1 is just after it), 0 or less is relative to its start.
 */
public final class JXEditBuffer {
    private final StringBuilder text = new StringBuilder();
    private int anchor;
    private int caret;
    private int composingStart = -1;
    private int composingEnd = -1;

    public JXEditBuffer(String value, int anchor, int caret) {
        reset(value, anchor, caret);
    }

    /** Replaces everything, the composing region included. */
    public void reset(String value, int anchor, int caret) {
        text.setLength(0);
        text.append(value == null ? "" : value);
        this.anchor = clamp(anchor);
        this.caret = clamp(caret);
        composingStart = -1;
        composingEnd = -1;
    }

    public String getText() {
        return text.toString();
    }

    public int length() {
        return text.length();
    }

    public int getAnchor() {
        return anchor;
    }

    public int getCaret() {
        return caret;
    }

    public int getSelectionStart() {
        return Math.min(anchor, caret);
    }

    public int getSelectionEnd() {
        return Math.max(anchor, caret);
    }

    public boolean hasSelection() {
        return anchor != caret;
    }

    public int getComposingStart() {
        return composingStart;
    }

    public int getComposingEnd() {
        return composingEnd;
    }

    public boolean isComposing() {
        return composingStart >= 0;
    }

    public String getSelectedText() {
        return text.substring(getSelectionStart(), getSelectionEnd());
    }

    /** Up to {@code n} chars before the selection (before the cursor when there is none). */
    public String getTextBeforeCursor(int n) {
        int end = getSelectionStart();
        return text.substring(Math.max(0, end - Math.max(0, n)), end);
    }

    /** Up to {@code n} chars after the selection. */
    public String getTextAfterCursor(int n) {
        int start = getSelectionEnd();
        return text.substring(start, Math.min(text.length(), start + Math.max(0, n)));
    }

    /** Puts {@code value} in the composing region (or in place of the selection) and makes it the composing region. */
    public void setComposingText(CharSequence value, int newCursor) {
        replace(value, newCursor, true);
    }

    /** Puts {@code value} in place of the composing region (or the selection) and ends the composition. */
    public void commitText(CharSequence value, int newCursor) {
        replace(value, newCursor, false);
    }

    /** The composing text stays as it is, composing ends. */
    public void finishComposingText() {
        composingStart = -1;
        composingEnd = -1;
    }

    /** Marks {@code [start, end)} as composing; the ends may come in either order, an empty region is none. */
    public void setComposingRegion(int start, int end) {
        int from = clamp(Math.min(start, end));
        int to = clamp(Math.max(start, end));
        if (from == to) {
            finishComposingText();
        } else {
            composingStart = from;
            composingEnd = to;
        }
    }

    public void setSelection(int start, int end) {
        anchor = clamp(start);
        caret = clamp(end);
    }

    /** Deletes {@code before} chars before the selection and {@code after} chars after it; a surrogate pair goes whole. */
    public void deleteSurroundingText(int before, int after) {
        int from = Math.max(0, getSelectionStart() - Math.max(0, before));
        int to = Math.min(text.length(), getSelectionEnd() + Math.max(0, after));
        if (from > 0 && from < text.length() && Character.isLowSurrogate(text.charAt(from))
                && Character.isHighSurrogate(text.charAt(from - 1))) {
            from--;
        }
        if (to > 0 && to < text.length() && Character.isLowSurrogate(text.charAt(to))
                && Character.isHighSurrogate(text.charAt(to - 1))) {
            to++;
        }
        deleteAround(from, to);
    }

    /** As {@link #deleteSurroundingText} with the counts in code points. */
    public void deleteSurroundingTextInCodePoints(int before, int after) {
        int from = getSelectionStart();
        for (int i = 0; i < before && from > 0; i++) {
            from -= Character.charCount(text.codePointBefore(from));
        }
        int to = getSelectionEnd();
        for (int i = 0; i < after && to < text.length(); i++) {
            to += Character.charCount(text.codePointAt(to));
        }
        deleteAround(from, to);
    }

    /** The selection goes; with none, the code point before the cursor. */
    public void backspace() {
        if (hasSelection()) {
            delete(getSelectionStart(), getSelectionEnd());
        } else if (caret > 0) {
            delete(caret - Character.charCount(text.codePointBefore(caret)), caret);
        }
    }

    /** The selection goes; with none, the code point after the cursor. */
    public void deleteForward() {
        if (hasSelection()) {
            delete(getSelectionStart(), getSelectionEnd());
        } else if (caret < text.length()) {
            delete(caret, caret + Character.charCount(text.codePointAt(caret)));
        }
    }

    private void replace(CharSequence value, int newCursor, boolean composing) {
        String inserted = value == null ? "" : value.toString();
        int from;
        int to;
        if (isComposing()) {
            from = composingStart;
            to = composingEnd;
        } else {
            from = getSelectionStart();
            to = getSelectionEnd();
        }
        text.replace(from, to, inserted);
        int end = from + inserted.length();
        if (composing && !inserted.isEmpty()) {
            composingStart = from;
            composingEnd = end;
        } else {
            finishComposingText();
        }
        int position = clamp(newCursor > 0 ? end + newCursor - 1 : from + newCursor);
        anchor = position;
        caret = position;
    }

    /** Deletes the chars around the selection, which stays on the same text. */
    private void deleteAround(int from, int to) {
        int start = getSelectionStart();
        int end = getSelectionEnd();
        if (to > end) {
            delete(end, to);
        }
        if (from < start) {
            delete(from, start);
        }
    }

    private void delete(int from, int to) {
        if (from >= to) {
            return;
        }
        text.delete(from, to);
        anchor = shift(anchor, from, to);
        caret = shift(caret, from, to);
        if (isComposing()) {
            int s = shift(composingStart, from, to);
            int e = shift(composingEnd, from, to);
            if (s >= e) {
                finishComposingText();
            } else {
                composingStart = s;
                composingEnd = e;
            }
        }
    }

    private static int shift(int position, int from, int to) {
        if (position <= from) {
            return position;
        }
        return position >= to ? position - (to - from) : from;
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(text.length(), value));
    }
}
