package com.jxparallel.ui.native2d;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;
import com.jxparallel.ui.native2d.JXPaintRecordingTest.RecordingPainter;

/**
 * A node above or below the window is not painted (the cost of a frame follows what is seen, not what exists), and
 * skipping it never changes a pixel: the same frame is also painted with the skipping off and compared.
 */
class JXPaintCullingTest {
    private static final int WIDTH = 300;
    private static final int HEIGHT = 200;

    @AfterEach
    void cullingBackOn() {
        JXPaint.cull = true;
    }

    private static JXProps.Builder p() {
        return JXProps.builder();
    }

    private static JXElement label(String text) {
        return JXElement.of("#text", p().set("value", text).build());
    }

    private static JXNativeNode laidOut(JXElement root) {
        JXNativeNode node = JXNativeNode.createBackendNode(root);
        node.layoutForBackend(WIDTH, HEIGHT);
        return node;
    }

    private static String record(JXNativeNode root) {
        RecordingPainter painter = new RecordingPainter();
        new JXPaint(painter).paint(root);
        return painter.log.toString();
    }

    private static int count(String log, String call) {
        int n = 0;
        for (String line : log.split("\n")) {
            if (line.startsWith(call + " ")) {
                n++;
            }
        }
        return n;
    }

    private static int[] pixels(JXNativeNode root, boolean cull) {
        JXPaint.cull = cull;
        int[] argb = new int[WIDTH * HEIGHT];
        JXWindow.paintSoftware(root, argb, WIDTH, HEIGHT);
        return argb;
    }

    @Test
    void aTallColumnPaintsOnlyTheRowsNearTheWindow() {
        JXElement[] rows = new JXElement[400];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = label("Row " + i);
        }
        JXNativeNode root = laidOut(JXElement.of("column", p().set("gap", 2.0).build(), rows));
        assertTrue(root.getChildren().get(399).getY() > 5 * HEIGHT, "the last row is far below the window");
        String log = record(root);
        assertTrue(log.contains("\"Row 0\""));
        assertFalse(log.contains("\"Row 399\""));
        assertTrue(count(log, "text") < 30, "only the rows near the window are drawn: " + count(log, "text"));

        JXPaint.cull = false;
        assertTrue(record(root).contains("\"Row 399\""), "with the skipping off every row is drawn");
        assertArrayEquals(pixels(root, false), pixels(root, true));
    }

    @Test
    void aNodeBelowTheWindowWhoseChildReachesUpIntoItIsStillPainted() {
        // the pane is far below the window, its label is placed 260 above it: inside the window
        JXElement reaching = JXElement.of("pane", p().set("prefWidth", 100.0).set("prefHeight", 20.0).set("layoutY", 400.0).build(),
                JXElement.of("#text", p().set("value", "reaches up").set("layoutY", -330.0).build()));
        JXNativeNode root = laidOut(JXElement.of("pane", p().build(), reaching));
        JXNativeNode pane = root.getChildren().get(0);
        JXNativeNode text = pane.getChildren().get(0);
        assertTrue(pane.getY() > HEIGHT + 100, "the pane is below the window: " + pane.getY());
        assertTrue(text.getY() >= 0 && text.getY() < HEIGHT, "its label is inside it: " + text.getY());
        assertTrue(record(root).contains("\"reaches up\""));
        assertArrayEquals(pixels(root, false), pixels(root, true));
        int[] blank = new int[WIDTH * HEIGHT];
        JXWindow.paintSoftware(laidOut(JXElement.of("pane", p().build())), blank, WIDTH, HEIGHT);
        assertFalse(java.util.Arrays.equals(blank, pixels(root, true)), "the text is on the screen");
    }

    @Test
    void aClippedContainerIsSkippedWholeAndTheScrolledContentOfAScrollPaneIsKept() {
        JXElement[] rows = new JXElement[300];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = label("Item " + i);
        }
        JXElement scroll = JXElement.of("scroll", p().set("prefWidth", 200.0).set("prefHeight", 80.0).build(),
                JXElement.of("column", p().build(), rows));
        JXElement below = JXElement.of("column", p().set("clip", true).build(), label("below"), label("below too"));
        JXElement[] spacers = new JXElement[60];
        for (int i = 0; i < spacers.length; i++) {
            spacers[i] = label("Spacer " + i);
        }
        JXElement[] all = new JXElement[spacers.length + 2];
        System.arraycopy(spacers, 0, all, 0, spacers.length);
        all[spacers.length] = below;
        all[spacers.length + 1] = scroll;
        JXNativeNode root = laidOut(JXElement.of("column", p().build(), all));
        String log = record(root);
        assertFalse(log.contains("\"below\""));
        assertFalse(log.contains("\"Spacer 59\""));
        assertTrue(log.contains("\"Spacer 0\""));
        assertArrayEquals(pixels(root, false), pixels(root, true));
    }

    @Test
    void aNodeThatOpensOverTheWindowIsNeverSkipped() {
        JXElement[] spacers = new JXElement[80];
        for (int i = 0; i < spacers.length; i++) {
            spacers[i] = label("Spacer " + i);
        }
        JXElement[] all = new JXElement[spacers.length + 1];
        System.arraycopy(spacers, 0, all, 0, spacers.length);
        all[spacers.length] = JXElement.of("select", p().set("value", "A").build());
        JXNativeNode root = laidOut(JXElement.of("column", p().build(), all));
        JXNativeNode select = root.getChildren().get(spacers.length);
        assertTrue(select.getY() > HEIGHT + 100);
        assertTrue(select.overlayHost);
        assertTrue(record(root).contains("\"A\""), "a host of an overlay is painted wherever it is");
    }
}
