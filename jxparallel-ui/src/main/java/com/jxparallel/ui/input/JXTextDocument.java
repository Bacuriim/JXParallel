package com.jxparallel.ui.input;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

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
            text.deleteCharAt(caret - 1);
            caret--;
            anchor = caret;
        }
    }

    public synchronized void delete() {
        if (hasSelection()) {
            replaceSelection("");
        } else if (caret < text.length()) {
            text.deleteCharAt(caret);
        }
    }

    public synchronized void selectAll() {
        anchor = 0;
        caret = text.length();
    }

    public synchronized void copy(Clipboard clipboard) {
        if (clipboard != null && hasSelection()) {
            clipboard.setContents(new StringSelection(getSelectedText()), null);
        }
    }

    public synchronized void cut(Clipboard clipboard) {
        if (clipboard != null && hasSelection()) {
            copy(clipboard);
            replaceSelection("");
        }
    }

    public synchronized void paste(Clipboard clipboard) throws UnsupportedFlavorException, IOException {
        if (clipboard != null && clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
            insert(String.valueOf(clipboard.getData(DataFlavor.stringFlavor)));
        }
    }

    private void replaceSelection(String value) {
        int start = getSelectionStart();
        int end = getSelectionEnd();
        text.replace(start, end, value);
        caret = start + value.length();
        anchor = caret;
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(text.length(), value));
    }
}
