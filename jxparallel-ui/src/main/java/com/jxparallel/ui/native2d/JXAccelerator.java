package com.jxparallel.ui.native2d;

import java.util.Locale;

/**
 * A keyboard shortcut written as text, {@code Ctrl+S}, {@code Ctrl+Shift+Z}, {@code F5}, {@code Alt+Enter}: zero or more of
 * {@code Ctrl} ({@code Control}, {@code Shortcut}), {@code Shift}, {@code Alt}, {@code Meta} ({@code Cmd}, {@code Command})
 * and then the key, joined by {@code +}, any case. The key is a letter, a digit, a punctuation mark ({@code , . - / ; =}) or a
 * name: {@code F1} to {@code F12}, {@code Enter}, {@code Esc}, {@code Tab}, {@code Space}, {@code Backspace},
 * {@code Delete}, {@code Insert}, {@code Home}, {@code End}, {@code PageUp}, {@code PageDown}, {@code Up}, {@code Down},
 * {@code Left}, {@code Right}. Free of GLFW: key codes are GLFW's.
 */
final class JXAccelerator {
    private static final int MASK = JXInputEvent.SHIFT | JXInputEvent.CONTROL | JXInputEvent.ALT | JXInputEvent.META;

    final int code;
    final int modifiers;

    private JXAccelerator(int code, int modifiers) {
        this.code = code;
        this.modifiers = modifiers;
    }

    /** The shortcut a text names, or null when it names none (empty, an unknown key, no key after the modifiers). */
    static JXAccelerator parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String[] parts = text.trim().split("\\s*\\+\\s*", -1);
        int modifiers = 0;
        for (int i = 0; i < parts.length - 1; i++) {
            switch (parts[i].toLowerCase(Locale.ROOT)) {
                case "ctrl":
                case "control":
                    modifiers |= JXInputEvent.CONTROL;
                    break;
                case "shortcut":
                    modifiers |= JXInputEvent.SHORTCUT; // Cmd on macOS, Ctrl elsewhere
                    break;
                case "shift":
                    modifiers |= JXInputEvent.SHIFT;
                    break;
                case "alt":
                    modifiers |= JXInputEvent.ALT;
                    break;
                case "meta":
                case "cmd":
                case "command":
                    modifiers |= JXInputEvent.META;
                    break;
                default:
                    return null;
            }
        }
        int code = keyCode(parts[parts.length - 1]);
        return code < 0 ? null : new JXAccelerator(code, modifiers);
    }

    private static int keyCode(String key) {
        if (key.length() == 1) {
            char c = Character.toUpperCase(key.charAt(0));
            if (c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == ',' || c == '.' || c == '-' || c == '/' || c == ';' || c == '=') {
                return c; // GLFW key codes of letters, digits and these marks are their ASCII values
            }
            return -1;
        }
        String name = key.toLowerCase(Locale.ROOT);
        if (name.matches("f([1-9]|1[0-2])")) {
            return 289 + Integer.parseInt(name.substring(1)); // F1 is 290
        }
        switch (name) {
            case "enter":
            case "return":
                return 257;
            case "esc":
            case "escape":
                return 256;
            case "tab":
                return 258;
            case "space":
                return 32;
            case "backspace":
                return 259;
            case "insert":
                return 260;
            case "delete":
            case "del":
                return 261;
            case "right":
                return 262;
            case "left":
                return 263;
            case "down":
                return 264;
            case "up":
                return 265;
            case "pageup":
                return 266;
            case "pagedown":
                return 267;
            case "home":
                return 268;
            case "end":
                return 269;
            default:
                return -1;
        }
    }

    /** The key event is this key with exactly these modifiers held. */
    boolean matches(JXKeyEvent event) {
        return event.getKeyCode() == code && (event.getModifiers() & MASK) == modifiers;
    }
}
