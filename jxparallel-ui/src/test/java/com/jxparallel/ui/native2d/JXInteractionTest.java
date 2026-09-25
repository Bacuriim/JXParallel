package com.jxparallel.ui.native2d;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.controls.JXButton;
import com.jxparallel.ui.layout.JXLayouts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * End to end without a window, like OpenJFX's StageLoader + MouseEventFirer tests: click a point,
 * the handler changes state, the screen is rendered again and reconciled.
 * {@link #click} does what JXWindow's mouse callback does.
 */
class JXInteractionTest {
    private static JXNativeNode click(JXNativeNode root, int x, int y) {
        JXNativeNode hit = root.hitTest(x, y);
        if (hit != null) {
            hit.dispatchPointer(new JXPointerEvent(x, y, 0));
        }
        return hit;
    }

    @Test
    void clickingAButtonRunsItsActionAndTheScreenUpdates() {
        AtomicInteger clicks = new AtomicInteger();
        JXButton button = new JXButton("Clicked 0");
        button.setOnAction(() -> button.setText("Clicked " + clicks.incrementAndGet()));
        JXNativeNode root = JXNativeNode.createBackendNode(JXLayouts.column(8, JXElement.text("Title"), button.render()));
        root.layoutForBackend(320, 200);
        JXNativeNode buttonNode = root.getChildren().get(1);

        JXNativeNode hit = click(root, buttonNode.getX() + 10, buttonNode.getY() + 10);
        root.reconcile(JXLayouts.column(8, JXElement.text("Title"), button.render()));

        assertSame(buttonNode, hit);
        assertEquals(1, clicks.get());
        assertEquals("Clicked 1", root.getChildren().get(1).getProperty("label"));
        assertSame(buttonNode, root.getChildren().get(1), "button node reused, not rebuilt");
    }

    @Test
    void disabledButtonIgnoresClicksLikeJavaFx() {
        AtomicInteger clicks = new AtomicInteger();
        JXButton button = new JXButton("Save");
        button.setOnAction(clicks::incrementAndGet);
        button.setDisable(true);
        JXNativeNode root = JXNativeNode.createBackendNode(button.render());
        root.layoutForBackend(120, 32);

        click(root, 10, 10);

        assertEquals(0, clicks.get());
    }

    @Test
    void clickOutsideEveryNodeDoesNothing() {
        JXNativeNode root = JXNativeNode.createBackendNode(JXLayouts.column(0, JXElement.text("Only")));
        root.layoutForBackend(100, 50);

        assertEquals(null, click(root, 500, 500));
    }
}
