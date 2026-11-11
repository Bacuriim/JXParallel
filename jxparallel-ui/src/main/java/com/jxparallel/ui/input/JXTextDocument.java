package com.jxparallel.ui.input;

public final class JXTextDocument {
    private final StringBuilder text = new StringBuilder();
    private int caret;
    private int anchor;

    public JXTextDocument() {
        this("");
    }

    public JXTextDocument(String value) {
        setText(value);
    }

    public synchronized String getText() {
        return text.toString();
    }

    public synchronized void setText(String value) {
        text.setLength(0);
        text.append(value == null ? "" : value);
        caret = text.length();
        anchor = caret;
    }

    public synchronized int getCaret() {
        return caret;
    }

    public synchronized void setCaret(int position, boolean extendSelection) {
        int next = clamp(position);
        if (!extendSelection) {
            anchor = next;
        }
        caret = next;
    }

    public synchronized int getSelectionStart() {
        return Math.min(anchor, caret);
    }

    public synchronized int getSelectionEnd() {
        return Math.max(anchor, caret);
    }

    public synchronized boolean hasSelection() {
        return anchor != caret;
    }

    public synchronized String getSelectedText() {
        return text.substring(getSelectionStart(), getSelectionEnd());
    }

    public synchronized void insert(String value) {
        replaceSelection(value == null ? "" : value);
    }

    public synchronized void backspace() {
        if (hasSelection()) {
            replaceSelection("");
        } else if (caret > 0) {
            int from = text.offsetByCodePoints(caret, -1); // a surrogate pair (an emoji) goes as one character
            text.delete(from, caret);
            caret = from;
            anchor = caret;
        }
    }

    public synchronized void delete() {
        if (hasSelection()) {
            replaceSelection("");
        } else if (caret < text.length()) {
            text.delete(caret, text.offsetByCodePoints(caret, 1));
        }
    }

    public synchronized void selectAll() {
        anchor = 0;
        caret = text.length();
    }

    public synchronized void copy(JXClipboard clipboard) {
        if (clipboard != null && hasSelection()) {
            clipboard.setText(getSelectedText());
        }
    }

    public synchronized void cut(JXClipboard clipboard) {
        if (clipboard != null && hasSelection()) {
            copy(clipboard);
            replaceSelection("");
        }
    }

    public synchronized void paste(JXClipboard clipboard) {
        String value = clipboard == null ? null : clipboard.getText();
        if (value != null) {
            insert(value);
        }
    }

    private void replaceSelection(String value) {
        int start = getSelectionStart();
        int end = getSelectionEnd();
        text.replace(start, end, value);
        caret = start + value.length();
        anchor = caret;
    }

    /**
     * Inside the text, and never between the two halves of a surrogate pair: a position inside a pair goes to the
     * pair's far edge in the direction the caret is moving (so {@code setCaret(caret + 1)} steps over an emoji).
     */
    private int clamp(int value) {
        int at = Math.max(0, Math.min(text.length(), value));
        if (at > 0 && at < text.length() && Character.isHighSurrogate(text.charAt(at - 1))
                && Character.isLowSurrogate(text.charAt(at))) {
            at += value >= caret ? 1 : -1;
        }
        return at;
    }
}
