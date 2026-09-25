package com.jxparallel.ui.native2d;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.controls.JXControls;
import com.jxparallel.ui.layout.JXLayouts;
import com.jxparallel.ui.text.JXTextEngine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JXNativeNodeReconcileTest {
    private static JXElement screen(String title, JXElement... more) {
        JXElement[] children = new JXElement[1 + more.length];
        children[0] = JXElement.text(title);
        System.arraycopy(more, 0, children, 1, more.length);
        return JXLayouts.column(8, children);
    }

    private static int line() {
        return (int) Math.ceil(JXTextEngine.get().lineHeight(JXTextEngine.DEFAULT_SIZE));
    }

    @Test
    void textChangeReusesNodesAndRelayoutsBecauseTheWidthChanged() {
        JXNativeNode root = JXNativeNode.createBackendNode(screen("Before", JXControls.button("Save", null)));
        root.layoutForBackend(320, 200);
        JXNativeNode title = root.getChildren().get(0);
        JXNativeNode button = root.getChildren().get(1);

        assertTrue(root.reconcile(screen("After", JXControls.button("Save", null))));

        assertSame(title, root.getChildren().get(0));
        assertSame(button, root.getChildren().get(1));
        assertEquals("After", title.getProperty("value"));
        assertTrue(root.isLayoutDirty(), "a label's width depends on its text");
        root.layoutForBackend(320, 200);
        assertEquals(line() + 8, button.getY());
    }

    @Test
    void typingInAFieldDoesNotRelayout() {
        JXNativeNode root = JXNativeNode.createBackendNode(screen("Title", JXControls.input("a", "Name")));
        root.layoutForBackend(320, 200);

        assertTrue(root.reconcile(screen("Title", JXControls.input("ab", "Name"))));

        assertEquals("ab", root.getChildren().get(1).getProperty("value"));
        assertFalse(root.isLayoutDirty(), "field width comes from its column count, like JavaFX");
    }

    @Test
    void addedChildIsLaidOutOnNextPass() {
        JXNativeNode root = JXNativeNode.createBackendNode(screen("Title"));
        root.layoutForBackend(320, 200);

        assertTrue(root.reconcile(screen("Title", JXControls.button("New", null))));
        root.layoutForBackend(320, 200);

        JXNativeNode added = root.getChildren().get(1);
        assertEquals(2, root.getChildren().size());
        assertEquals(line() + 8, added.getY());
        assertEquals(line() + 2 * JXNativeNode.PAD_Y, added.getHeight());
    }

    @Test
    void childOfAnotherTypeIsReplaced() {
        JXNativeNode root = JXNativeNode.createBackendNode(screen("Title", JXElement.text("status")));
        JXNativeNode old = root.getChildren().get(1);

        assertTrue(root.reconcile(screen("Title", JXControls.button("Now a button", null))));

        assertNotSame(old, root.getChildren().get(1));
        assertEquals("button", root.getChildren().get(1).getType());
    }

    @Test
    void differentRootTypeAsksForRemount() {
        JXNativeNode root = JXNativeNode.createBackendNode(screen("Title"));
        assertFalse(root.reconcile(JXControls.button("Other root", null)));
    }
}
