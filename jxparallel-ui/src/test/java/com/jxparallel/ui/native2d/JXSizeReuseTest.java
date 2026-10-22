package com.jxparallel.ui.native2d;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

/**
 * A node that is laid out again after a layout prop changed writes its new sizes over the old array. Reconciling a
 * tree many times must give the sizes and the boxes of a tree mounted fresh from the last element.
 */
class JXSizeReuseTest {
    private static JXElement screen(Random random) {
        List<JXElement> kids = new ArrayList<JXElement>();
        for (int i = 0; i < 12; i++) {
            double w = random.nextInt(4) == 0 ? Double.NaN : 20 + random.nextInt(200);
            JXProps.Builder b = JXProps.builder();
            if (!Double.isNaN(w)) {
                b.set("prefWidth", w);
            }
            if (random.nextBoolean()) {
                b.set("prefHeight", 10.0 + random.nextInt(30));
            }
            switch (i % 6) {
                case 0:
                    kids.add(JXElement.of("separator", b.set("orientation", random.nextBoolean() ? "vertical" : "horizontal").build()));
                    break;
                case 1:
                    kids.add(JXElement.of("slider", b.set("value", random.nextDouble()).build()));
                    break;
                case 2:
                    kids.add(JXElement.of("progress", b.set("progress", random.nextDouble()).build()));
                    break;
                case 3:
                    kids.add(JXElement.of("scrollbar", b.set("orientation", random.nextBoolean() ? "vertical" : "horizontal").build()));
                    break;
                case 4:
                    kids.add(JXElement.of("#text", b.set("value", random.nextBoolean() ? "short" : "a rather longer text").build()));
                    break;
                default:
                    kids.add(JXElement.of("input", b.set("value", "v" + random.nextInt(9)).build()));
                    break;
            }
        }
        return JXElement.of("column", JXProps.builder().set("gap", 2.0).build(), kids.toArray(new JXElement[0]));
    }

    private static void same(JXNativeNode expected, JXNativeNode actual, String where) {
        assertEquals(expected.getType(), actual.getType(), where);
        assertEquals(expected.getMinWidth(), actual.getMinWidth(), where + " minWidth");
        assertEquals(expected.getPrefWidth(), actual.getPrefWidth(), where + " prefWidth");
        assertEquals(expected.getMaxWidth(), actual.getMaxWidth(), where + " maxWidth");
        assertEquals(expected.getMinHeight(), actual.getMinHeight(), where + " minHeight");
        assertEquals(expected.getPrefHeight(), actual.getPrefHeight(), where + " prefHeight");
        assertEquals(expected.getMaxHeight(), actual.getMaxHeight(), where + " maxHeight");
        assertEquals(expected.getX(), actual.getX(), where + " x");
        assertEquals(expected.getY(), actual.getY(), where + " y");
        assertEquals(expected.getWidth(), actual.getWidth(), where + " width");
        assertEquals(expected.getHeight(), actual.getHeight(), where + " height");
        assertEquals(expected.getChildren().size(), actual.getChildren().size(), where);
        for (int i = 0; i < expected.getChildren().size(); i++) {
            same(expected.getChildren().get(i), actual.getChildren().get(i), where + "/" + i);
        }
    }

    @Test
    void reconcilingManyTimesGivesTheSizesOfAFreshTree() {
        Random random = new Random(3);
        JXElement first = screen(random);
        JXNativeNode mounted = JXNativeNode.createBackendNode(first);
        mounted.layoutForBackend(400, 600);
        for (int round = 0; round < 300; round++) {
            JXElement next = screen(random);
            mounted.reconcile(next);
            mounted.layoutForBackend(400, 600);
            JXNativeNode fresh = JXNativeNode.createBackendNode(next);
            fresh.layoutForBackend(400, 600);
            same(fresh, mounted, "round " + round);
        }
    }
}
