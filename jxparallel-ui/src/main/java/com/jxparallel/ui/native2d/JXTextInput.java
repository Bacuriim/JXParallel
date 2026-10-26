package com.jxparallel.ui.native2d;

import java.util.function.Consumer;

import com.jxparallel.ui.input.JXClipboard;
import com.jxparallel.ui.input.JXTextDocument;

/**
 * Caret, selection and clipboard of the text inputs ({@code input}, {@code password}, {@code textarea}) for hosts that
 * edit text outside the node tree (a soft keyboard, a hand-made menu). Inputs are controlled: every change goes to the
 * node's {@code onChange} once, and the node shows it when the application builds its tree again; the caret and the
 * selection are the node's own and show while the host has given it the focus ({@link JXFrame#focus}).
 */
public final class JXTextInput {
    private JXTextInput() {
    }

    /** A node whose text the user edits. */
    public static boolean isEditable(JXNativeNode node) {
        return node != null && ("input".equals(node.getType()) || "password".equals(node.getType()) || "textarea".equals(node.getType()))
                && !Boolean.TRUE.equals(node.getProperty("disabled")) && !Boolean.TRUE.equals(node.getProperty("readonly"));
    }

    public static boolean isMultiline(JXNativeNode node) {
        return "textarea".equals(node.getType());
    }

    public static boolean isPassword(JXNativeNode node) {
        return "password".equals(node.getType());
    }

    /** The text the node shows now. */
    public static String text(JXNativeNode node) {
        return JXPaint.text(node, "value");
    }

    /** The caret, within the text; the end when the user has not put it anywhere. */
    public static int caret(JXNativeNode node) {
        int length = text(node).length();
        return node.caret < 0 ? length : Math.min(node.caret, length);
    }

    /** The end of the selection that stays where it is; the caret when nothing is selected. */
    public static int anchor(JXNativeNode node) {
        int length = text(node).length();
        return node.anchor < 0 ? caret(node) : Math.min(node.anchor, length);
    }

    public static void select(JXNativeNode node, int anchor, int caret) {
        int length = text(node).length();
        node.caret = Math.max(0, Math.min(length, caret));
        node.anchor = Math.max(0, Math.min(length, anchor));
    }

    /** Shows {@code next} (the application's {@code onChange} gets it when it differs) with this selection. */
    @SuppressWarnings("unchecked")
    public static void setText(JXNativeNode node, String next, int anchor, int caret) {
        String old = text(node);
        node.caret = Math.max(0, Math.min(next.length(), caret));
        node.anchor = Math.max(0, Math.min(next.length(), anchor));
        Object handler = node.getProperty("onChange");
        if (!next.equals(old) && handler instanceof Consumer && !Boolean.TRUE.equals(node.getProperty("disabled"))) {
            ((Consumer<Object>) handler).accept(next);
        }
    }

    /** Puts the caret where the pointer is (window pixels) and drops the selection. */
    public static void placeCaret(JXNativeNode node, int x, int y) {
        int index = isMultiline(node) ? JXTextHit.areaIndex(node, x, y) : JXTextHit.fieldIndex(node, x);
        select(node, index, index);
    }

    public static void selectAll(JXNativeNode node) {
        select(node, 0, text(node).length());
    }

    public static boolean hasSelection(JXNativeNode node) {
        return anchor(node) != caret(node);
    }

    /** The selected text, null for a password (that is never copied). */
    public static String selectedText(JXNativeNode node) {
        if (isPassword(node)) {
            return null;
        }
        return text(node).substring(Math.min(anchor(node), caret(node)), Math.max(anchor(node), caret(node)));
    }

    /** Puts the selection on the clipboard. False when nothing was copied. */
    public static boolean copy(JXNativeNode node, JXClipboard clipboard) {
        if (isPassword(node) || !hasSelection(node)) {
            return false;
        }
        document(node).copy(clipboard);
        return true;
    }

    /** Moves the selection to the clipboard. False when nothing was cut. */
    public static boolean cut(JXNativeNode node, JXClipboard clipboard) {
        if (isPassword(node) || !hasSelection(node) || !isEditable(node)) {
            return false;
        }
        JXTextDocument document = document(node);
        document.cut(clipboard);
        apply(node, document);
        return true;
    }

    /** Replaces the selection (or inserts at the caret) with the clipboard's text. False when it has none. */
    public static boolean paste(JXNativeNode node, JXClipboard clipboard) {
        String value = clipboard == null ? null : clipboard.getText();
        if (value == null || value.isEmpty() || !isEditable(node)) {
            return false;
        }
        if (!isMultiline(node)) {
            value = value.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ');
        }
        JXTextDocument document = document(node);
        document.insert(value);
        apply(node, document);
        return true;
    }

    /** Selects the word at {@code index} ({@code {start, end}} of a run of letters or digits); the caret when there is none. */
    public static void selectWordAt(JXNativeNode node, int index) {
        int[] word = wordAt(text(node), index);
        select(node, word[0], word[1]);
    }

    /** {@code {start, end}} of the word at or just before {@code index}; start equals end when there is no word. */
    public static int[] wordAt(String text, int index) {
        int at = Math.max(0, Math.min(text.length(), index));
        int start = at;
        int end = at;
        if (at == text.length() || !isWordChar(text.charAt(at))) {
            if (at > 0 && isWordChar(text.charAt(at - 1))) {
                start = at - 1;
                end = at - 1;
            } else {
                return new int[] {at, at};
            }
        }
        while (start > 0 && isWordChar(text.charAt(start - 1))) {
            start--;
        }
        while (end < text.length() && isWordChar(text.charAt(end))) {
            end++;
        }
        return new int[] {start, end};
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static JXTextDocument document(JXNativeNode node) {
        JXTextDocument document = new JXTextDocument(text(node));
        document.setCaret(anchor(node), false);
        document.setCaret(caret(node), true);
        return document;
    }

    private static void apply(JXNativeNode node, JXTextDocument document) {
        setText(node, document.getText(), document.getCaret(), document.getCaret());
    }
}
