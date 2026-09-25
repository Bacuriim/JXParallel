package com.jxparallel.ui.input;

/** System clipboard for plain text. {@code JXWindow#clipboard()} provides the GLFW-backed one. */
public interface JXClipboard {
    /** Returns the clipboard text, or {@code null} if it holds no text. */
    String getText();

    void setText(String value);
}
