package com.jxparallel.ui.native2d;

public final class JXKeyEvent {
    private final int keyCode;
    private final char keyChar;
    private final int modifiers;

    public JXKeyEvent(int keyCode, char keyChar) {
        this(keyCode, keyChar, 0);
    }

    /** {@code modifiers}: {@link JXInputEvent#SHIFT}, {@link JXInputEvent#CONTROL} and {@link JXInputEvent#ALT}, or-ed. */
    public JXKeyEvent(int keyCode, char keyChar, int modifiers) {
        this.keyCode = keyCode;
        this.keyChar = keyChar;
        this.modifiers = modifiers;
    }

    public int getKeyCode() { return keyCode; }
    public char getKeyChar() { return keyChar; }
    public int getModifiers() { return modifiers; }
    public boolean isShiftDown() { return (modifiers & JXInputEvent.SHIFT) != 0; }
    public boolean isControlDown() { return (modifiers & JXInputEvent.CONTROL) != 0; }
}
