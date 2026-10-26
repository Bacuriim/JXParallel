package com.jxparallel.examples.nativeui;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.native2d.JXNativeNode;
import com.jxparallel.ui.native2d.JXWindow;

/** What the live tests compare: a mounted tree as text (types, props, sizes, boxes) and as pixels. */
final class LiveSupport {
    static final int W = ComponentBench.WIDTH;
    static final int H = ComponentBench.HEIGHT;

    private LiveSupport() {
    }

    /** Every node: its type, props (handlers only as present), min/pref/max sizes and laid-out box. */
    static String describe(JXNativeNode node) {
        StringBuilder out = new StringBuilder();
        describe(node, 0, out);
        return out.toString();
    }

    private static void describe(JXNativeNode node, int depth, StringBuilder out) {
        for (int i = 0; i < depth; i++) {
            out.append(' ');
        }
        Map<String, Object> sorted = new TreeMap<String, Object>();
        for (Map.Entry<String, Object> e : node.getProps().entrySet()) {
            Object v = e.getValue();
            if (v instanceof Runnable || v instanceof Consumer) {
                v = "<handler>";
            } else if (v instanceof JXElement[]) {
                v = "<" + ((JXElement[]) v).length + " elements>";
            } else if (v != null && v.getClass().isArray()) {
                v = Arrays.deepToString(new Object[] {v});
            }
            sorted.put(e.getKey(), v);
        }
        out.append(node.getType()).append(' ').append(sorted).append(" [").append(node.getX()).append(',').append(node.getY())
                .append(' ').append(node.getWidth()).append('x').append(node.getHeight()).append("] min=")
                .append(node.getMinWidth()).append('x').append(node.getMinHeight()).append(" pref=")
                .append(node.getPrefWidth()).append('x').append(node.getPrefHeight()).append(" max=")
                .append(node.getMaxWidth()).append('x').append(node.getMaxHeight()).append('\n');
        for (JXNativeNode child : node.getChildren()) {
            describe(child, depth + 1, out);
        }
    }

    static int[] paint(JXNativeNode laidOut) {
        int[] pixels = new int[W * H];
        JXWindow.paintSoftware(laidOut, pixels, W, H);
        return pixels;
    }

    /** First line where two descriptions differ, or null. */
    static String firstDifference(String expected, String actual) {
        if (expected.equals(actual)) {
            return null;
        }
        String[] a = expected.split("\n");
        String[] b = actual.split("\n");
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            if (!a[i].equals(b[i])) {
                return "line " + i + "\n  fresh: " + a[i] + "\n  live:  " + b[i];
            }
        }
        return "lengths " + a.length + " vs " + b.length;
    }
}
