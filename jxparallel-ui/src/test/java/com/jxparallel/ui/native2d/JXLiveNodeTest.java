package com.jxparallel.ui.native2d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXLiveNode;
import com.jxparallel.ui.JXProps;

/**
 * {@link JXNativeNode#setProp} and {@link JXNativeNode#commit}, the node half of live bindings: changing props in place
 * and settling layout once, bottom up, must give exactly what reconciling the rebuilt tree gives (types, props, sizes,
 * boxes), and a reconcile after live changes must give what a fresh mount gives.
 */
class JXLiveNodeTest {
    private static final String[] LEAVES = {"#text", "button", "checkbox", "input", "slider", "separator", "progress", "textarea"};
    private static final String[] CONTAINERS = {"row", "column", "stack"};
    private static final String[] GROW = {"always", "sometimes", "never"};
    private static final String[] WORDS = {"a", "Hello", "a longer text here", "", "wide wide wide wide"};

    /** A tree whose every node has its own props (live sessions own their elements' props). Same seed, same tree. */
    private static JXElement tree(Random r, int depth, boolean mutateFrom, JXElement base) {
        // base == null: a fresh random tree; otherwise the same shape and keys as base with some values changed
        boolean container = base != null ? !base.getChildren().isEmpty() : depth > 0 && r.nextInt(3) > 0;
        String type = base != null ? base.getType() : container ? CONTAINERS[r.nextInt(CONTAINERS.length)] : LEAVES[r.nextInt(LEAVES.length)];
        JXProps.Builder props = JXProps.builder();
        if (base == null) {
            if (r.nextBoolean()) {
                props.set("value", WORDS[r.nextInt(WORDS.length)]);
            }
            if (r.nextBoolean()) {
                props.set("label", WORDS[r.nextInt(WORDS.length)]);
            }
            if (r.nextBoolean()) {
                props.set("gap", Double.valueOf(r.nextInt(12)));
            }
            if (r.nextBoolean()) {
                props.set("prefWidth", Double.valueOf(20 + r.nextInt(200)));
            }
            if (r.nextBoolean()) {
                props.set("vgrow", GROW[r.nextInt(3)]);
            }
            if (r.nextBoolean()) {
                props.set("hgrow", GROW[r.nextInt(3)]);
            }
            if (r.nextBoolean()) {
                props.set("padding", Double.valueOf(r.nextInt(8)));
            }
            if (r.nextBoolean()) {
                props.set("fontSize", Double.valueOf(10 + r.nextInt(10)));
            }
            if (r.nextBoolean()) {
                props.set("bold", Boolean.valueOf(r.nextBoolean()));
            }
            if (r.nextBoolean()) {
                props.set("disabled", Boolean.valueOf(r.nextBoolean()));
            }
            if (r.nextBoolean()) {
                props.set("alignment", r.nextBoolean() ? "CENTER" : "TOP_LEFT");
            }
        } else {
            for (int i = 0; i < base.getProps().size(); i++) {
                String key = base.getProps().keyAt(i);
                Object value = base.getProps().valueAt(i);
                if (mutateFrom && r.nextInt(3) == 0) {
                    value = another(key, value, r);
                }
                props.set(key, value);
            }
        }
        List<JXElement> children = new ArrayList<JXElement>();
        if (base != null) {
            for (JXElement child : base.getChildren()) {
                children.add(tree(r, depth - 1, mutateFrom, child));
            }
        } else if (container) {
            int n = 1 + r.nextInt(4);
            for (int i = 0; i < n; i++) {
                children.add(tree(r, depth - 1, false, null));
            }
        }
        return JXElement.of(type, props.build(), children.toArray(new JXElement[0]));
    }

    private static Object another(String key, Object value, Random r) {
        switch (key) {
            case "value":
            case "label":
                return WORDS[r.nextInt(WORDS.length)];
            case "gap":
            case "padding":
                return Double.valueOf(r.nextInt(12));
            case "prefWidth":
                return Double.valueOf(20 + r.nextInt(200));
            case "vgrow":
            case "hgrow":
                return GROW[r.nextInt(3)];
            case "fontSize":
                return Double.valueOf(10 + r.nextInt(10));
            case "alignment":
                return r.nextBoolean() ? "CENTER" : "BOTTOM_RIGHT";
            default:
                return Boolean.valueOf(!Boolean.TRUE.equals(value));
        }
    }

    /** The test's stand-in for a live session: sets the props that changed, settles bottom up; the outcome for the parent. */
    private static int apply(JXLiveNode node, JXElement before, JXElement after) {
        int bits = 0;
        for (int i = 0; i < before.getProps().size(); i++) {
            String key = before.getProps().keyAt(i);
            Object next = after.getProps().get(key);
            if (!Objects.deepEquals(before.getProps().valueAt(i), next)) {
                int r = node.setProp(key, next);
                assertTrue(r >= 0, key);
                bits |= (r & JXLiveNode.SET_RELAYOUT) != 0 ? JXLiveNode.RELAYOUT : 0;
                bits |= (r & JXLiveNode.SET_PLACEMENT) != 0 ? JXLiveNode.PLACEMENT : 0;
            }
        }
        List<? extends JXLiveNode> children = node.getLiveChildren();
        for (int i = 0; i < children.size(); i++) {
            int r = apply(children.get(i), before.getChildren().get(i), after.getChildren().get(i));
            bits |= r == JXLiveNode.RESIZED ? JXLiveNode.RELAYOUT : r == JXLiveNode.SUBTREE ? JXLiveNode.CHILD_SUBTREE : 0;
        }
        return node.commit(bits);
    }

    /** A deep copy with its own props, so a tree can be mounted and changed in place without touching its twin. */
    private static JXElement copy(JXElement e) {
        JXProps.Builder props = JXProps.builder();
        for (int i = 0; i < e.getProps().size(); i++) {
            props.set(e.getProps().keyAt(i), e.getProps().valueAt(i));
        }
        List<JXElement> children = new ArrayList<JXElement>();
        for (JXElement child : e.getChildren()) {
            children.add(copy(child));
        }
        return JXElement.of(e.getType(), props.build(), children.toArray(new JXElement[0]));
    }

    static String describe(JXNativeNode node) {
        StringBuilder out = new StringBuilder();
        describe(node, 0, out);
        return out.toString();
    }

    private static void describe(JXNativeNode node, int depth, StringBuilder out) {
        for (int i = 0; i < depth; i++) {
            out.append("  ");
        }
        Map<String, Object> sorted = new TreeMap<String, Object>(node.getProps());
        out.append(node.getType()).append(' ').append(sorted)
                .append(" [").append(node.getX()).append(',').append(node.getY()).append(' ')
                .append(node.getWidth()).append('x').append(node.getHeight()).append("] min=")
                .append(node.getMinWidth()).append('x').append(node.getMinHeight()).append(" pref=")
                .append(node.getPrefWidth()).append('x').append(node.getPrefHeight()).append(" max=")
                .append(node.getMaxWidth()).append('x').append(node.getMaxHeight()).append('\n');
        for (JXNativeNode child : node.getChildren()) {
            describe(child, depth + 1, out);
        }
    }

    @Test
    void liveChangesGiveWhatReconcilingTheRebuiltTreeGives() {
        for (int seed = 0; seed < 600; seed++) {
            Random r = new Random(seed);
            JXElement a = tree(r, 3, false, null);
            JXElement b = tree(new Random(seed * 31L + 7), 3, true, a);
            int width = 100 + r.nextInt(800);

            JXNativeNode live = JXNativeNode.createBackendNode(copy(a));
            live.layoutForBackend(width, 600);
            apply(live, a, b);
            live.layoutForBackend(width, 600);

            JXNativeNode reconciled = JXNativeNode.createBackendNode(copy(a));
            reconciled.layoutForBackend(width, 600);
            reconciled.reconcile(b);
            reconciled.layoutForBackend(width, 600);

            JXNativeNode fresh = JXNativeNode.createBackendNode(copy(b));
            fresh.layoutForBackend(width, 600);

            assertEquals(describe(reconciled), describe(live), "seed " + seed + " live vs reconcile");
            assertEquals(describe(fresh), describe(live), "seed " + seed + " live vs fresh mount");
        }
    }

    @Test
    void aReconcileAfterLiveChangesGivesWhatAFreshMountGives() {
        for (int seed = 0; seed < 400; seed++) {
            Random r = new Random(seed);
            JXElement a = tree(r, 3, false, null);
            JXElement b = tree(new Random(seed * 17L + 3), 3, true, a);
            JXElement c = tree(new Random(seed * 13L + 5), 3, true, b); // another change of values, the same shape
            JXElement d = tree(new Random(seed * 5L + 1), 3, true, a);   // and one that goes back toward a
            int width = 150 + r.nextInt(600);

            JXElement mounted = copy(a);
            JXNativeNode live = JXNativeNode.createBackendNode(mounted);
            live.layoutForBackend(width, 500);
            apply(live, a, b);
            live.layoutForBackend(width, 500);
            // the next full rebuild arrives as new elements: it must be compared with what the node holds NOW
            for (JXElement next : new JXElement[] {c, d, a}) {
                assertTrue(live.reconcile(copy(next)));
                live.layoutForBackend(width, 500);
                JXNativeNode fresh = JXNativeNode.createBackendNode(copy(next));
                fresh.layoutForBackend(width, 500);
                assertEquals(describe(fresh), describe(live), "seed " + seed);
            }
        }
    }

    @Test
    void aLivePrefWidthThatGoesBackIsLaidOutAgain() {
        JXElement sep = JXElement.of("separator", JXProps.builder().set("prefWidth", Double.valueOf(50)).build());
        JXElement root = JXElement.of("column", JXProps.builder().set("alignment", "TOP_LEFT").set("fillWidth", Boolean.FALSE).build(), sep);
        JXNativeNode node = JXNativeNode.createBackendNode(root);
        node.layoutForBackend(400, 300);
        assertEquals(50, node.getChildren().get(0).getWidth());

        JXNativeNode child = node.getChildren().get(0);
        int r = child.setProp("prefWidth", Double.valueOf(55));
        assertTrue((r & JXLiveNode.SET_RELAYOUT) != 0);
        assertEquals(JXLiveNode.RESIZED, child.commit(JXLiveNode.RELAYOUT));
        assertEquals(JXLiveNode.RESIZED, node.commit(JXLiveNode.RELAYOUT)); // the column's preferred width is its child's
        node.layoutForBackend(400, 300);
        assertEquals(55, node.getChildren().get(0).getWidth());

        JXElement back = JXElement.of("column", JXProps.builder().set("alignment", "TOP_LEFT").set("fillWidth", Boolean.FALSE).build(),
                JXElement.of("separator", JXProps.builder().set("prefWidth", Double.valueOf(50)).build()));
        assertTrue(node.reconcile(back));
        node.layoutForBackend(400, 300);
        assertEquals(50, node.getChildren().get(0).getWidth());
    }

    @Test
    void aPropThatAppearsOrDisappearsIsNotDoneInPlace() {
        JXElement root = JXElement.of("column", JXProps.builder().set("gap", Double.valueOf(2)).build(),
                JXElement.of("#text", JXProps.builder().set("value", "x").build()));
        JXNativeNode node = JXNativeNode.createBackendNode(root);
        assertEquals(-1, node.setProp("gap", null));
        assertEquals(-1, node.setProp("padding", Double.valueOf(3))); // not there: that is a different set of keys
        assertEquals(Double.valueOf(2), node.getProperty("gap"));
        assertFalse(node.getProps().containsKey("padding"));
    }

    @Test
    void nothingChangedAllocatesNothing() {
        JXElement leaf = JXElement.of("slider", JXProps.builder().set("value", Double.valueOf(1)).build());
        JXNativeNode node = JXNativeNode.createBackendNode(JXElement.of("column", JXProps.empty(), leaf));
        node.layoutForBackend(300, 200);
        JXNativeNode child = node.getChildren().get(0);
        Double[] values = {Double.valueOf(10), Double.valueOf(20)};
        for (int i = 0; i < 20000; i++) { // warm up
            child.setProp("value", values[i & 1]);
            child.commit(0);
        }
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        long id = Thread.currentThread().getId();
        long before = bean.getThreadAllocatedBytes(id);
        for (int i = 0; i < 100000; i++) {
            child.setProp("value", values[i & 1]);
            child.commit(0);
        }
        long bytes = bean.getThreadAllocatedBytes(id) - before;
        assertTrue(bytes < 4096, "setProp of a cached boxed value allocates nothing per call, measured " + bytes + " B for 100000");
        assertSame(values[1], node.getChildren().get(0).getProperty("value"));
    }
}
