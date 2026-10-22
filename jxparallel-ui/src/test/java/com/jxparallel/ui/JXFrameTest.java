package com.jxparallel.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.controls.JXButton;
import com.jxparallel.ui.controls.JXLabel;
import com.jxparallel.ui.layout.JXPane;
import com.jxparallel.ui.native2d.JXFrame;
import com.jxparallel.ui.native2d.JXNativeNode;
import com.jxparallel.ui.native2d.JXPainter;
import com.jxparallel.ui.native2d.JXPointerEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A backend outside native2d (this test is in another package) drives the tree through {@link JXFrame} and {@link JXPainter}. */
class JXFrameTest {
    /** Records what the renderer asks a platform canvas to draw. */
    private static final class Recording implements JXPainter {
        final List<String> calls = new ArrayList<String>();
        final List<String> texts = new ArrayList<String>();
        int depth;

        public void fillRect(float x, float y, float w, float h, int argb) { calls.add("rect"); }
        public void fillRoundRect(float x, float y, float w, float h, float radius, int argb) { calls.add("round"); }
        public void fillRoundRectGradient(float x, float y, float w, float h, float radius, int top, int bottom) { calls.add("gradient"); }
        public void strokeRoundRect(float x, float y, float w, float h, float radius, float width, int argb) { calls.add("stroke"); }
        public void fillOval(float x, float y, float w, float h, int argb) { calls.add("oval"); }
        public void strokeOval(float x, float y, float w, float h, float width, int argb) { calls.add("strokeOval"); }
        public void strokeArc(float x, float y, float w, float h, float start, float sweep, float width, int argb) { calls.add("arc"); }
        public void line(float x1, float y1, float x2, float y2, float width, int argb) { calls.add("line"); }
        public void fillTriangle(float x0, float y0, float x1, float y1, float x2, float y2, int argb) { calls.add("triangle"); }
        public void text(String value, float x, float baseline, float size, boolean bold, int argb) { texts.add(value); }
        public void image(int[] pixels, int imageWidth, int imageHeight, float x, float y, float w, float h) { calls.add("image"); }
        public void clip(float x, float y, float w, float h) { depth++; }
        public void layer(float opacity, float blurRadius) { depth++; }
        public void restore() { depth--; }
    }

    @Test
    void laysOutPaintsAndRoutesAPressWithoutAWindow() {
        AtomicInteger pressed = new AtomicInteger();
        JXButton button = new JXButton("Tap me");
        button.setOnAction(pressed::incrementAndGet);
        JXPane content = new JXPane(8);
        content.add(new JXLabel("Hello"));
        content.add(button);

        JXNativeNode root = JXFrame.mount(content.render());
        JXFrame.layout(root, 320, 480);
        Recording painter = new Recording();
        JXFrame.paint(root, painter, 320, 480);

        assertTrue(painter.texts.contains("Hello"), painter.texts.toString());
        assertTrue(painter.texts.contains("Tap me"), painter.texts.toString());
        assertEquals(0, painter.depth, "every clip and layer is restored");
        assertEquals("rect", painter.calls.get(0), "the background comes first");

        // press on the button wherever the layout put it: scan down the left edge
        JXNativeNode found = null;
        for (int y = 0; y < 120 && found == null; y += 4) {
            JXNativeNode node = root.hitTest(40, y);
            if (node != null && "button".equals(node.getType())) {
                found = node;
                JXFrame.pointer(root, new JXPointerEvent(40, y, 0));
            }
        }
        assertNotNull(found, "the button is reachable by hit testing");
        assertEquals(1, pressed.get());
    }
}
