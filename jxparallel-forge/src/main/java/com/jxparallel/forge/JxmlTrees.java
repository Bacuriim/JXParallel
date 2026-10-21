package com.jxparallel.forge;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.jxparallel.ui.JXElement;

/** Compares element trees, for tests and tools that check two ways of building a screen agree. */
public final class JxmlTrees {
    private JxmlTrees() {
    }

    /**
     * Null when both trees have the same types, the same props with the same Java types and
     * values (arrays by content), and the same children; otherwise where they first differ.
     * Handlers ({@link Runnable}) only have to be present on both sides: call them to check them.
     */
    public static String diff(JXElement a, JXElement b) {
        return diff(a, b, "/");
    }

    private static String diff(JXElement a, JXElement b, String path) {
        if (a == null || b == null) {
            return a == b ? null : path + ": one side is null";
        }
        String here = path + a.getType();
        if (!a.getType().equals(b.getType())) {
            return here + ": type " + a.getType() + " vs " + b.getType();
        }
        Map<String, Object> pa = a.getProps().asMap();
        Map<String, Object> pb = b.getProps().asMap();
        if (!pa.keySet().equals(pb.keySet())) {
            return here + ": props " + pa.keySet() + " vs " + pb.keySet();
        }
        for (Map.Entry<String, Object> entry : pa.entrySet()) {
            String problem = value(entry.getValue(), pb.get(entry.getKey()), here + "@" + entry.getKey());
            if (problem != null) {
                return problem;
            }
        }
        List<JXElement> ca = a.getChildren();
        List<JXElement> cb = b.getChildren();
        if (ca.size() != cb.size()) {
            return here + ": " + ca.size() + " vs " + cb.size() + " children";
        }
        for (int i = 0; i < ca.size(); i++) {
            String problem = diff(ca.get(i), cb.get(i), here + "[" + i + "]/");
            if (problem != null) {
                return problem;
            }
        }
        return null;
    }

    private static String value(Object x, Object y, String where) {
        if (x == null || y == null) {
            return x == y ? null : where + ": " + x + " vs " + y;
        }
        if (x instanceof Runnable && y instanceof Runnable) {
            return null;
        }
        if (x.getClass() != y.getClass()) {
            return where + ": " + x.getClass().getSimpleName() + " vs " + y.getClass().getSimpleName();
        }
        if (x instanceof JXElement[]) {
            JXElement[] ex = (JXElement[]) x;
            JXElement[] ey = (JXElement[]) y;
            if (ex.length != ey.length) {
                return where + ": " + ex.length + " vs " + ey.length + " elements";
            }
            for (int i = 0; i < ex.length; i++) {
                String problem = diff(ex[i], ey[i], where + "[" + i + "]/");
                if (problem != null) {
                    return problem;
                }
            }
            return null;
        }
        boolean same = x.getClass().isArray()
                ? Arrays.deepEquals(new Object[] {x}, new Object[] {y})
                : x.equals(y);
        return same ? null : where + ": " + render(x) + " vs " + render(y);
    }

    private static String render(Object o) {
        return o.getClass().isArray() ? Arrays.deepToString(new Object[] {o}) : String.valueOf(o);
    }

    /** Elements in the tree, counting the deferred tab contents. */
    public static int count(JXElement e) {
        if (e == null) {
            return 0;
        }
        int n = 1;
        for (JXElement child : e.getChildren()) {
            n += count(child);
        }
        Object deferred = e.getProps().get("deferredTabs");
        if (deferred instanceof JXElement[]) {
            for (JXElement d : (JXElement[]) deferred) {
                n += count(d);
            }
        }
        return n;
    }
}
