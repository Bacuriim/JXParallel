package com.jxparallel.ui.native2d;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Titled panes beyond layout (JXTitledDifferentialTest in jxparallel-javafx checks layout against
 * JavaFX): collapsing in place, hit testing a collapsed pane and Modena's content padding.
 */
class JXTitledPaneTest {
    private static JXElement titled(boolean expanded, JXElement content) {
        return JXElement.of("titled", JXProps.builder().set("label", "Details").set("expanded", expanded).build(), content);
    }

    private static JXElement region(int width, int height) {
        return JXElement.of("region", JXProps.builder().set("prefWidth", width).set("prefHeight", height).build());
    }

    @Test
    void collapsingInPlaceLeavesOnlyTheTitleBar() {
        JXElement column = JXElement.of("column", JXProps.builder().build(), titled(true, region(100, 50)), region(10, 10));
        JXNativeNode root = JXNativeNode.createBackendNode(column);
        root.layoutForBackend(300, 300);
        JXNativeNode pane = root.getChildren().get(0);
        assertEquals(76, pane.getHeight());
        assertEquals(76, root.getChildren().get(1).getY());

        root.reconcile(JXElement.of("column", JXProps.builder().build(), titled(false, region(100, 50)), region(10, 10)));
        root.layoutForBackend(300, 300);

        assertSame(pane, root.getChildren().get(0), "pane node reused");
        assertEquals(25, pane.getHeight());
        assertEquals(25, root.getChildren().get(1).getY(), "the next node moves up");
    }

    @Test
    void collapsedContentIsNotHit() {
        // a stack keeps the pane at its max height, so a collapsed pane is only its title bar
        JXProps top = JXProps.builder().set("alignment", "TOP_LEFT").build();
        JXNativeNode root = JXNativeNode.createBackendNode(JXElement.of("stack", top, titled(true, region(100, 50))));
        root.layoutForBackend(200, 200);
        JXNativeNode pane = root.getChildren().get(0);
        assertSame(pane.getChildren().get(0), root.hitTest(50, 40));

        root.reconcile(JXElement.of("stack", top, titled(false, region(100, 50))));
        root.layoutForBackend(200, 200);

        assertSame(pane, root.hitTest(10, 10), "the title bar is still hit");
        assertSame(root, root.hitTest(50, 40), "below the title bar only the stack is hit");
    }

    @Test
    void layoutPaneContentGetsModenaPaddingUnlessItSetsItsOwn() {
        JXElement box = JXElement.of("column", JXProps.builder().build(), region(20, 20));
        JXNativeNode styled = JXNativeNode.createBackendNode(titled(true, box));
        styled.layoutForBackend(200, 200);
        JXNativeNode inner = styled.getChildren().get(0).getChildren().get(0);
        assertEquals(1 + 10, inner.getX(), "0.8em = 9.6 px, snapped by VBox");

        JXElement padded = JXElement.of("column", JXProps.builder().set("padding", new double[] {0, 0, 0, 0}).build(),
                region(20, 20));
        JXNativeNode plain = JXNativeNode.createBackendNode(titled(true, padded));
        plain.layoutForBackend(200, 200);
        assertEquals(1, plain.getChildren().get(0).getChildren().get(0).getX());
    }
}
