package com.jxparallel.android;

/**
 * The arithmetic of the Android backend that needs no Android class, so a plain JVM tests it
 * ({@code src/test/java/.../JXAndroidMathCheck}): text line metrics, density scaling and key codes.
 */
final class JXAndroidMath {
    /** Key codes the node tree understands (GLFW's). */
    static final int KEY_ESCAPE = 256;
    static final int KEY_ENTER = 257;
    static final int KEY_TAB = 258;
    static final int KEY_BACKSPACE = 259;
    static final int KEY_DELETE = 261;
    static final int KEY_RIGHT = 262;
    static final int KEY_LEFT = 263;
    static final int KEY_DOWN = 264;
    static final int KEY_UP = 265;
    static final int KEY_HOME = 268;
    static final int KEY_END = 269;

    private JXAndroidMath() {
    }

    /**
     * {@code {ascent, lineHeight}} in logical pixels, as the desktop engine computes the line: ascent and descent are
     * each rounded up, plus the leading.
     */
    static float[] lineMetrics(float ascent, float descent, float leading) {
        return new float[] {ascent, (float) (Math.ceil(ascent) + Math.ceil(descent) + leading)};
    }

    /** Physical pixels to logical ones. */
    static int logical(float physical, float density) {
        return (int) Math.floor(physical / density);
    }

    /** The size of the layout for a view of {@code physical} pixels, at least 1. */
    static int logicalSize(int physical, float density) {
        return Math.max(1, Math.round(physical / density));
    }

    /**
     * The key code the node tree expects for an Android {@code KeyEvent.KEYCODE_*}, or -1 when the key is typed text
     * (or unknown) and arrives as a character.
     */
    static int nodeKey(int androidKeyCode) {
        switch (androidKeyCode) {
            case 67: return KEY_BACKSPACE;   // KEYCODE_DEL
            case 112: return KEY_DELETE;     // KEYCODE_FORWARD_DEL
            case 66: return KEY_ENTER;       // KEYCODE_ENTER
            case 160: return KEY_ENTER;      // KEYCODE_NUMPAD_ENTER
            case 111: return KEY_ESCAPE;     // KEYCODE_ESCAPE
            case 61: return KEY_TAB;         // KEYCODE_TAB
            case 21: return KEY_LEFT;        // KEYCODE_DPAD_LEFT
            case 22: return KEY_RIGHT;       // KEYCODE_DPAD_RIGHT
            case 19: return KEY_UP;          // KEYCODE_DPAD_UP
            case 20: return KEY_DOWN;        // KEYCODE_DPAD_DOWN
            case 122: return KEY_HOME;       // KEYCODE_MOVE_HOME
            case 123: return KEY_END;        // KEYCODE_MOVE_END
            default: return -1;
        }
    }

    /** Whether a node of this type edits text, so the soft keyboard should be up while it has the focus. */
    static boolean editsText(String nodeType) {
        return "input".equals(nodeType) || "password".equals(nodeType) || "textarea".equals(nodeType);
    }
}
