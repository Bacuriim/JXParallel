package com.jxparallel.ui.native2d;

public final class JXKeyEvent {
    private final int keyCode;
    private final char keyChar;
    private final int modifiers;
    private final int codePoint;

    public JXKeyEvent(int keyCode, char keyChar) {
        this(keyCode, keyChar, 0);
    }

    /** {@code modifiers}: {@link JXInputEvent#SHIFT}, {@link JXInputEvent#CONTROL} and {@link JXInputEvent#ALT}, or-ed. */
    public JXKeyEvent(int keyCode, char keyChar, int modifiers) {
        this(keyCode, keyChar, keyChar, modifiers, false);
    }

    /** {@code repeat}: the key is held and the system repeats it (accelerators ignore these). */
    public JXKeyEvent(int keyCode, char keyChar, int modifiers, boolean repeat) {
        this(keyCode, keyChar, keyChar, modifiers, repeat);
    }

    private JXKeyEvent(int keyCode, char keyChar, int codePoint, int modifiers, boolean repeat) {
        this.keyCode = keyCode;
        this.keyChar = keyChar;
        this.codePoint = codePoint;
        this.modifiers = modifiers;
        this.repeat = repeat;
    }

    private final boolean repeat;

    public boolean isRepeat() { return repeat; }

    /**
     * A typed character as the operating system delivered it: a whole code point, so an emoji or any other
     * supplementary character (two {@code char}s in a Java string) arrives as one event.
     * {@link #getKeyChar()} is the char itself for the Basic Multilingual Plane and the high surrogate otherwise.
     */
    public static JXKeyEvent typed(int codePoint, int modifiers) {
        char first = Character.isBmpCodePoint(codePoint) ? (char) codePoint : Character.highSurrogate(codePoint);
        return new JXKeyEvent(0, first, codePoint, modifiers, false);
    }

    public int getKeyCode() { return keyCode; }
    public char getKeyChar() { return keyChar; }
    /** The typed code point; the same as {@link #getKeyChar()} unless the character is supplementary. */
    public int getKeyCodePoint() { return codePoint; }
    public int getModifiers() { return modifiers; }
    public boolean isShiftDown() { return (modifiers & JXInputEvent.SHIFT) != 0; }
    public boolean isControlDown() { return (modifiers & JXInputEvent.CONTROL) != 0; }
    /** Cmd on macOS, Ctrl elsewhere. */
    public boolean isShortcutDown() { return (modifiers & JXInputEvent.SHORTCUT) != 0; }
}
