package com.jxparallel.ui.native2d;

import java.util.List;

/**
 * Accelerators: a {@code menuitem} or {@code button} with an {@code accelerator} prop ({@link JXAccelerator}) runs its action
 * when the key comes, from anywhere in the window, even if its menu is closed. {@link JXOverlays#key} asks after an open popup
 * or menu and a list took the key, and before the focused node gets it.
 */
final class JXAccelerators {
    private static final int KEY_A = 65;
    private static final int KEY_C = 67;
    private static final int KEY_V = 86;
    private static final int KEY_X = 88;
    private static final int KEY_Y = 89;
    private static final int KEY_Z = 90;

    private JXAccelerators() {
    }

    /**
     * Runs the first enabled, visible item or button in tree order whose accelerator the event is. With a dialog or drawer
     * open only what is in the topmost one counts; items of a disabled menu, of a hidden node or of a closed dialog do not. A
     * held key (auto-repeat) runs nothing, and a focused text field keeps the keys it edits with. True when one ran.
     */
    static boolean run(JXNativeNode root, JXKeyEvent event, JXNativeNode focused) {
        if (event.isRepeat() || textConsumes(focused, event)) {
            return false;
        }
        List<JXNativeNode> layers = JXOverlays.openLayers(root);
        JXNativeNode scope = layers.isEmpty() ? root : layers.get(layers.size() - 1);
        return find(scope, scope, event);
    }

    private static boolean find(JXNativeNode node, JXNativeNode scope, JXKeyEvent event) {
        if (Boolean.TRUE.equals(node.getProperty("hidden")) || Boolean.TRUE.equals(node.getProperty("disabled"))) {
            return false;
        }
        String type = node.getType();
        if (node != scope && ("dialog".equals(type) || "drawer".equals(type) || "snackbar".equals(type))
                && !JXOverlays.isOpen(node)) {
            return false;
        }
        if (("menuitem".equals(type) && !JXOverlays.separator(node)) || "button".equals(type)) {
            Object text = node.getProperty("accelerator");
            JXAccelerator accelerator = text instanceof String ? JXAccelerator.parse((String) text) : null;
            if (accelerator != null && accelerator.matches(event)
                    && node.runAction(new JXPointerEvent(node.getX() + node.getWidth() / 2, node.getY() + node.getHeight() / 2, 0))) {
                return true;
            }
        }
        for (JXNativeNode child : node.getChildren()) {
            if (find(child, scope, event)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the focused text input uses this key for editing: typing, the caret keys, and Ctrl+A, C, X, V, Z, Y. */
    static boolean textConsumes(JXNativeNode focused, JXKeyEvent event) {
        if (focused == null) {
            return false;
        }
        String type = focused.getType();
        if (!"input".equals(type) && !"password".equals(type) && !"textarea".equals(type)) {
            return false;
        }
        int code = event.getKeyCode();
        if (event.isControlDown()) {
            return code == KEY_A || code == KEY_C || code == KEY_X || code == KEY_V || code == KEY_Z || code == KEY_Y;
        }
        if ((event.getModifiers() & (JXInputEvent.ALT | JXInputEvent.META)) != 0) {
            return false;
        }
        return code >= 32 && code <= 96 || code == 259 || code == 261 || code == 257 || code >= 262 && code <= 265
                || code == 268 || code == 269;
    }
}
