package com.jxparallel.ui.native2d;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

/** {@code JXWindow.paintSoftware}: paints an already laid-out tree, and gives the same pixels as {@code captureSoftware}. */
class JXWindowPaintSoftwareTest {
    private static JXElement screen() {
        return JXElement.of("column", JXProps.builder().set("gap", 4.0).build(),
                JXElement.of("button", JXProps.builder().set("label", "OK").build()),
                JXElement.of("#text", JXProps.builder().set("value", "Hello").build()));
    }

    @Test
    void paintsALaidOutTreeLikeCaptureSoftwareAndReusesTheBuffer() {
        int w = 200;
        int h = 100;
        JXNativeNode node = JXNativeNode.createBackendNode(screen());
        node.layoutForBackend(w, h);
        int[] buffer = new int[w * h];
        JXWindow.paintSoftware(node, buffer, w, h);
        int[] expected = JXWindow.captureSoftware(screen(), w, h);
        assertArrayEquals(expected, buffer);
        boolean drawn = false;
        for (int p : buffer) {
            drawn |= p != buffer[0];
        }
        assertTrue(drawn, "something is painted over the background");
        // a second call on the same buffer clears it first: same result
        JXWindow.paintSoftware(node, buffer, w, h);
        assertArrayEquals(expected, buffer);
    }
}
