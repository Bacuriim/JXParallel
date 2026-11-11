package com.jxparallel.ui.native2d;

public final class JXPointerEvent {
    private final int x;
    private final int y;
    private final int button;
    private final int clickCount;
    private final int modifiers;

    public JXPointerEvent(int x, int y, int button) {
        this(x, y, button, 1);
    }

    /** {@code clickCount}: 2 for the second press of a double click, 3 for a triple click. */
    public JXPointerEvent(int x, int y, int button, int clickCount) {
        this(x, y, button, clickCount, 0);
    }

    /** {@code modifiers}: the {@link JXInputEvent} bits ({@code SHIFT}, {@code CONTROL}...) held during the press. */
    public JXPointerEvent(int x, int y, int button, int clickCount, int modifiers) {
        this.x = x;
        this.y = y;
        this.button = button;
        this.clickCount = Math.max(1, clickCount);
        this.modifiers = modifiers;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getButton() { return button; }
    public int getClickCount() { return clickCount; }
    public int getModifiers() { return modifiers; }
    public boolean isShiftDown() { return (modifiers & JXInputEvent.SHIFT) != 0; }
    public boolean isControlDown() { return (modifiers & JXInputEvent.CONTROL) != 0; }
    /** Cmd on macOS, Ctrl elsewhere. */
    public boolean isShortcutDown() { return (modifiers & JXInputEvent.SHORTCUT) != 0; }
}
