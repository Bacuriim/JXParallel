package com.jxparallel.ui.native2d;

public final class JXPointerEvent {
    private final int x;
    private final int y;
    private final int button;

    public JXPointerEvent(int x, int y, int button) {
        this.x = x;
        this.y = y;
        this.button = button;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getButton() { return button; }
}
