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
        this(keyCode, keyChar, modifiers, false);
    }

    /** {@code repeat}: the key is held and the system repeats it (accelerators ignore these). */
    public JXKeyEvent(int keyCode, char keyChar, int modifiers, boolean repeat) {
        this.keyCode = keyCode;
        this.keyChar = keyChar;
        this.modifiers = modifiers;
        this.repeat = repeat;
    }

    private final boolean repeat;

    public boolean isRepeat() { return repeat; }

    public int getKeyCode() { return keyCode; }
    public char getKeyChar() { return keyChar; }
    public int getModifiers() { return modifiers; }
    public boolean isShiftDown() { return (modifiers & JXInputEvent.SHIFT) != 0; }
    public boolean isControlDown() { return (modifiers & JXInputEvent.CONTROL) != 0; }
}
