package com.jxparallel.examples.nativeui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;

import org.junit.jupiter.api.Test;

import com.jxparallel.examples.nativeui.forgebench.Screen07Controller;
import com.jxparallel.forge.JxmlTemplate;
import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.native2d.JXNativeNode;

/** The benchmark compares three ways of building the same screens: check they are the same. */
class ForgeBenchScreensTest {
    private static JxmlTemplate template(int i) throws Exception {
        String path = String.format("/forgebench/screen%02d.jxml", i);
        InputStream in = ForgeBenchScreensTest.class.getResourceAsStream(path);
        try {
            return JxmlTemplate.read(in, path.substring(1));
        } finally {
            in.close();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void interpretedAndCompiledBuildTheSameTrees() throws Exception {
        for (int i = 1; i <= 20; i++) {
            Object controller = ForgeBenchViews.controller(i);
            JXElement interpreted = template(i).build(controller);
            JXElement hoisted = ForgeBenchViews.view(i, false).build(controller);
            JXElement plain = ForgeBenchViews.view(i, true).build(controller);
            assertNull(com.jxparallel.forge.JxmlTrees.diff(interpreted, hoisted), "screen " + i + " hoisted");
            assertNull(com.jxparallel.forge.JxmlTrees.diff(interpreted, plain), "screen " + i + " plain");
        }
    }

    @Test
    void screensHaveTheFxmlBenchShape() throws Exception {
        long[] shape = new long[4];
        for (int i = 1; i <= 20; i++) {
            ForgeLoadBenchmark.shape(template(i).build(ForgeBenchViews.controller(i)), shape);
        }
        // per screen: 45 rows of a label and a control, spinner initial values 4, 9, ..., 44, grid rows 3 x (0..14)
        assertEquals(20 * (4 + 9 + 14 + 19 + 24 + 29 + 34 + 39 + 44), shape[1]);
        assertEquals(20 * 3 * 2 * 105, shape[2]);
        assertEquals(20, shape[3], "every status label shows the controller's Loaded");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buttonsCallTheController() throws Exception {
        Screen07Controller controller = new Screen07Controller();
        JXElement root = ForgeBenchViews.view(7, false).build(controller);
        ((Runnable) root.getChildren().get(0).getChildren().get(3).getProps().get("onAction")).run();
        assertEquals("Action", controller.getStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void nativeLayoutIsTheSameForBothBuilds() throws Exception {
        Object controller = ForgeBenchViews.controller(3);
        JXNativeNode a = ForgeLoadBenchmark.ready(template(3).build(controller));
        JXNativeNode b = ForgeLoadBenchmark.ready(ForgeBenchViews.view(3, false).build(controller));
        assertEquals(geometry(a), geometry(b));
        // border, toolbar row with 8 buttons, tabs, scroll, grid of 15 label/control pairs, status row with 4 labels
        assertEquals(1 + 9 + 3 + 30 + 5, geometry(a).split("\n").length);
        assertTrue(a.getChildren().get(0).getChildren().get(0).getWidth() > 0, "buttons measured");
        assertEquals(3, a.getChildren().size(), "border with top, center, bottom");
        JXNativeNode tabs = a.getChildren().get(1);
        assertTrue(tabs.holds("tab0") && tabs.holds("tab1") && tabs.holds("tab2"), "shown tab plus two mounted ahead");
    }

    private static String geometry(JXNativeNode n) {
        StringBuilder s = new StringBuilder();
        geometry(n, s);
        return s.toString();
    }

    private static void geometry(JXNativeNode n, StringBuilder s) {
        s.append(n.getType()).append(' ').append(n.getX()).append(',').append(n.getY()).append(' ')
                .append(n.getWidth()).append('x').append(n.getHeight()).append('\n');
        for (JXNativeNode child : n.getChildren()) {
            geometry(child, s);
        }
    }
}
