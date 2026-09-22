package com.jxparallel.ui.native2d;

public final class JXKeyEvent {
    private final int keyCode;
    private final char keyChar;

    public JXKeyEvent(int keyCode, char keyChar) {
        this.keyCode = keyCode;
        this.keyChar = keyChar;
    }

    public int getKeyCode() { return keyCode; }
    public char getKeyChar() { return keyChar; }
}
