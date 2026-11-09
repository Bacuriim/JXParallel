package com.jxparallel.ui.native2d;

public final class JXPointerEvent {
    private final int x;
    private final int y;
    private final int button;
    private final int clickCount;

    public JXPointerEvent(int x, int y, int button) {
        this(x, y, button, 1);
    }

    /** {@code clickCount}: 2 for the second press of a double click, 3 for a triple click. */
    public JXPointerEvent(int x, int y, int button, int clickCount) {
        this.x = x;
        this.y = y;
        this.button = button;
        this.clickCount = Math.max(1, clickCount);
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getButton() { return button; }
    public int getClickCount() { return clickCount; }
}
