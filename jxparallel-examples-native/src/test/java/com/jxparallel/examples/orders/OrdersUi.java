package com.jxparallel.examples.orders;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.List;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.native2d.JXKeyEvent;
import com.jxparallel.ui.native2d.JXNativeNode;
import com.jxparallel.ui.native2d.JXPointerEvent;
import com.jxparallel.ui.native2d.JXWindow;

/**
 * A window of {@code w} x {@code h} showing JX Orders, driven through the calls {@code JXWindow} makes for GLFW's
 * callbacks (the same ones {@code JxmlComponentBehaviorTest.Ui} uses, which is package-private in another module's
 * tests, so a small copy lives here): the screen is built again after every gesture, as the application does, and
 * the window lays out.
 */
final class OrdersUi {
    static final int ESCAPE = 256;
    static final int ENTER = 257;
    static final int TAB = 258;
    static final int BACKSPACE = 259;
    static final int RIGHT = 262;
    static final int LEFT = 263;
    static final int DOWN = 264;
    static final int UP = 265;
    static final int HOME = 268;
    static final int END = 269;
    static final int SHIFT = 1;
    static final int CONTROL = 2;
    /** The shortcut key: Cmd on macOS, Ctrl elsewhere (multi-select, the text edits). Accelerators written Ctrl+N stay Ctrl. */
    static final int SHORTCUT = com.jxparallel.ui.native2d.JXInputEvent.SHORTCUT;

    final ShellController shell;
    final int w;
    final int h;
    JXNativeNode root;
    JXNativeNode focused;
    private int pointerX;
    private int pointerY;

    OrdersUi(ShellController shell, int w, int h) {
        this.shell = shell;
        this.w = w;
        this.h = h;
        refresh();
    }

    /** {@code setContent(shell.view())}, then the window lays out. */
    void refresh() {
        JXElement e = shell.view();
        if (root == null || !root.reconcile(e)) {
            root = JXNativeNode.createBackendNode(e);
            focused = null;
        }
        root.layoutForBackend(w, h);
    }

    JXNativeNode press(int x, int y, int modifiers) {
        pointerX = x;
        pointerY = y;
        JXNativeNode hit = root.pressPointer(new JXPointerEvent(x, y, 0, 1, modifiers));
        if (hit != null) {
            focused = hit;
            JXNativeNode.setFocusOwner(hit);
        }
        refresh();
        return hit;
    }

    void release() {
        root.releasePointer(new JXPointerEvent(pointerX, pointerY, 0));
        refresh();
    }

    JXNativeNode click(int x, int y) {
        return click(x, y, 0);
    }

    JXNativeNode click(int x, int y, int modifiers) {
        JXNativeNode hit = press(x, y, modifiers);
        release();
        return hit;
    }

    JXNativeNode click(JXNativeNode node) {
        int[] c = center(node);
        return click(c[0], c[1]);
    }

    static int[] center(JXNativeNode n) {
        return new int[] {n.getX() + n.getWidth() / 2, n.getY() + n.getHeight() / 2};
    }

    /** A key (GLFW key callback, press) with modifiers; Tab moves the focus as the window does. */
    void key(int code, int modifiers) {
        if (code == TAB) {
            JXNativeNode next = root.nextFocus(focused, (modifiers & SHIFT) != 0);
            if (next != null) {
                focused = next;
                JXNativeNode.setFocusOwner(next);
            }
        } else {
            JXNativeNode owner = root.routeKey(new JXKeyEvent(code, '\0', modifiers), focused);
            if (owner != focused) {
                focused = owner;
                JXNativeNode.setFocusOwner(owner);
            }
        }
        refresh();
    }

    void key(int code) {
        key(code, 0);
    }

    /** Typing: GLFW sends the key, then the character (which an open popup or menu that took the key does not get again). */
    void type(String text) {
        for (char ch : text.toCharArray()) {
            boolean taken = root.capturesKeys();
            JXNativeNode owner = root.routeKey(new JXKeyEvent(Character.toUpperCase(ch), '\0'), focused);
            if (owner != focused) {
                focused = owner;
                JXNativeNode.setFocusOwner(owner);
            }
            refresh();
            if (focused != null && !taken) {
                focused.dispatchKey(new JXKeyEvent(0, ch));
            }
            refresh();
        }
    }

    /** Files dropped on the window with the pointer at (x, y). */
    boolean drop(int x, int y, List<String> paths) {
        boolean taken = root.dispatchDrop(x, y, paths);
        refresh();
        return taken;
    }

    // ---- looking ----------------------------------------------------------------------------------------------

    JXNativeNode find(String type) {
        return find(type, 0);
    }

    JXNativeNode find(String type, int index) {
        List<JXNativeNode> all = findAll(type);
        assertNotNull(all.size() > index ? all.get(index) : null, "no " + type + " #" + index + " on the screen, found " + all.size());
        return all.get(index);
    }

    List<JXNativeNode> findAll(String type) {
        List<JXNativeNode> out = new ArrayList<JXNativeNode>();
        collect(root, type, out);
        return out;
    }

    private static void collect(JXNativeNode node, String type, List<JXNativeNode> out) {
        if (type.equals(node.getType())) {
            out.add(node);
        }
        for (JXNativeNode child : node.getChildren()) {
            collect(child, type, out);
        }
    }

    /** The node of {@code type} whose {@code text} or {@code value} prop is {@code text}. */
    JXNativeNode findText(String type, String text) {
        for (JXNativeNode n : findAll(type)) {
            if (text.equals(n.getProperty("text")) || text.equals(n.getProperty("value"))) {
                return n;
            }
        }
        throw new AssertionError("no " + type + " with the text '" + text + "'; the " + type + "s say " + texts(type));
    }

    List<String> texts(String type) {
        List<String> out = new ArrayList<String>();
        for (JXNativeNode n : findAll(type)) {
            Object t = n.getProperty("text") != null ? n.getProperty("text") : n.getProperty("value");
            out.add(String.valueOf(t));
        }
        return out;
    }

    boolean hasText(String text) {
        return texts("#text").contains(text);
    }

    /** What the screen paints at (x, y), with the software painter. */
    int pixel(int x, int y) {
        int[] argb = new int[w * h];
        JXWindow.paintSoftware(root, argb, w, h);
        return argb[y * w + x];
    }

    String dump() {
        StringBuilder sb = new StringBuilder();
        dump(root, 0, sb);
        return sb.toString();
    }

    private static void dump(JXNativeNode n, int depth, StringBuilder sb) {
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }
        sb.append(n.getType()).append(' ').append(n.getX()).append(',').append(n.getY()).append(' ').append(n.getWidth())
                .append('x').append(n.getHeight());
        Object t = n.getProperty("text") != null ? n.getProperty("text") : n.getProperty("value");
        if (t != null) {
            sb.append(" '").append(t).append('\'');
        }
        sb.append('\n');
        if (depth < 12) {
            for (JXNativeNode c : n.getChildren()) {
                dump(c, depth + 1, sb);
            }
        }
    }
}
