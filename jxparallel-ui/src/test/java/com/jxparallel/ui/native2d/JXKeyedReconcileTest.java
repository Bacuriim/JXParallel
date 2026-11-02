package com.jxparallel.ui.native2d;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Children with keys keep their nodes when they move, like cells of a scrolled JavaFX list. */
class JXKeyedReconcileTest {
    private static JXElement row(Object key, String text) {
        return JXElement.of("#text", JXProps.builder().set("key", key).set("value", text).build());
    }

    private static JXElement column(JXElement... rows) {
        return JXElement.of("column", JXProps.empty(), rows);
    }

    private static List<JXNativeNode> kids(JXNativeNode n) {
        return new ArrayList<>(n.getChildren());
    }

    @Test
    void aScrollByOneRowKeepsTheRowsThatStayAndMovesThem() {
        String[] keys = {"a", "b", "c", "d"};
        JXElement a = row(keys[0], "A");
        JXElement b = row(keys[1], "B");
        JXElement c = row(keys[2], "C");
        JXNativeNode root = JXNativeNode.createBackendNode(column(a, b, c));
        root.layoutForBackend(100, 100);
        List<JXNativeNode> before = kids(root);
        int bY = before.get(1).getY();
        // scrolled by one: a leaves at the top, d enters at the bottom; b and c keep their elements
        root.reconcile(column(b, c, row(keys[3], "D")));
        root.layoutForBackend(100, 100);
        List<JXNativeNode> after = kids(root);
        assertSame(before.get(1), after.get(0), "b keeps its node");
        assertSame(before.get(2), after.get(1), "c keeps its node");
        assertEquals("D", after.get(2).getProperty("value"));
        assertEquals(0, after.get(0).getY(), "b moved to the top");
        assertEquals(bY, after.get(1).getY(), "c where b was");
    }

    @Test
    void aKeyedChildOfAnotherTypeGetsANewNode() {
        String key = "k";
        JXNativeNode root = JXNativeNode.createBackendNode(column(row(key, "x"), row("other", "y")));
        JXNativeNode first = root.getChildren().get(0);
        root.reconcile(column(JXElement.of("pane", JXProps.builder().set("key", key).build()), row("other", "y")));
        assertNotSame(first, root.getChildren().get(0));
        assertEquals("pane", root.getChildren().get(0).getType());
    }

    @Test
    void duplicateOrMissingKeysFallBackToPositions() {
        JXNativeNode root = JXNativeNode.createBackendNode(column(row("a", "1"), row("b", "2")));
        List<JXNativeNode> before = kids(root);
        root.reconcile(column(row("b", "2"), row("b", "3")));
        assertSame(before.get(0), root.getChildren().get(0), "duplicate keys: matched by position");
        assertEquals("2", root.getChildren().get(0).getProperty("value"));
        root.reconcile(column(row("x", "4"), JXElement.of("#text", JXProps.builder().set("value", "5").build())));
        assertSame(before.get(0), root.getChildren().get(0), "a child without a key: by position");
        JXElement unkeyed = JXElement.of("#text", JXProps.builder().set("value", "6").build());
        root.reconcile(column(unkeyed));
        assertSame(before.get(0), root.getChildren().get(0), "a single child without a key: by position");
    }

    private static JXElement box(Object key, double height) {
        return JXElement.of("pane", JXProps.builder().set("key", key).set("prefHeight", height).build());
    }

    @Test
    void twoKeyedChildrenSwappedKeepTheirNodes() {
        JXNativeNode root = JXNativeNode.createBackendNode(column(row("a", "A"), row("b", "B")));
        List<JXNativeNode> before = kids(root);
        root.reconcile(column(row("b", "B"), row("a", "A")));
        assertSame(before.get(1), root.getChildren().get(0), "even two children are matched by key");
        assertSame(before.get(0), root.getChildren().get(1));
    }

    @Test
    void aKeyedChildThatGrowsPushesTheNodesAfterItsParent() {
        JXElement after = JXElement.of("pane", JXProps.builder().set("prefHeight", 5.0).build());
        JXNativeNode root = JXNativeNode.createBackendNode(column(column(box("a", 10), box("b", 10)), after));
        root.layoutForBackend(100, 200);
        assertEquals(20, root.getChildren().get(1).getY());
        root.reconcile(column(column(box("a", 30), box("b", 10)), after));
        root.layoutForBackend(100, 200);
        JXNativeNode inner = root.getChildren().get(0);
        assertEquals(30, inner.getChildren().get(1).getY(), "the keyed sibling moves down");
        assertEquals(40, root.getChildren().get(1).getY(), "and the parent's size reaches its own parent");
    }

    @Test
    void keyedChildrenAddedOrRemovedChangeTheLayout() {
        JXElement after = JXElement.of("pane", JXProps.builder().set("prefHeight", 5.0).build());
        JXNativeNode root = JXNativeNode.createBackendNode(column(column(box("a", 10), box("b", 10), box("c", 10)), after));
        root.layoutForBackend(100, 200);
        assertEquals(30, root.getChildren().get(1).getY());
        root.reconcile(column(column(box("a", 10), box("c", 10)), after));
        root.layoutForBackend(100, 200);
        assertEquals(2, root.getChildren().get(0).getChildren().size(), "b dropped");
        assertEquals(20, root.getChildren().get(1).getY());
        root.reconcile(column(column(box("a", 10), box("c", 10), box("d", 10), box("e", 10)), after));
        root.layoutForBackend(100, 200);
        assertEquals(4, root.getChildren().get(0).getChildren().size(), "d and e added");
        assertEquals(40, root.getChildren().get(1).getY());
    }

    private static JXElement tabs(JXElement... content) {
        return JXElement.of("column", JXProps.builder().set("retainChildren", true).build(), content);
    }

    @Test
    void aRetainingParentGivesBackTheNodesOfChildrenThatReturn() {
        JXElement first = column(row("a1", "A"), row("a2", "B"));
        JXElement firstKeyed = JXElement.of("column", JXProps.builder().set("key", "tab1").build(), first.getChildren().toArray(new JXElement[0]));
        JXElement second = JXElement.of("column", JXProps.builder().set("key", "tab2").build());
        JXNativeNode root = JXNativeNode.createBackendNode(tabs(firstKeyed));
        JXNativeNode shown = root.getChildren().get(0);
        root.reconcile(tabs(second));
        assertNotSame(shown, root.getChildren().get(0));
        assertEquals(1, root.getChildren().size());
        root.reconcile(tabs(firstKeyed));
        assertSame(shown, root.getChildren().get(0), "the first tab's nodes come back as they were");
        assertEquals("A", root.getChildren().get(0).getChildren().get(0).getProperty("value"));
        root.reconcile(tabs());
        assertEquals(0, root.getChildren().size(), "no tab shown");
        root.reconcile(tabs(firstKeyed));
        assertSame(shown, root.getChildren().get(0), "kept while nothing was shown");
    }

    @Test
    void aRetainingParentKeepsOnlyTheLastSixteen() {
        JXNativeNode root = JXNativeNode.createBackendNode(tabs(box("k0", 1)));
        JXNativeNode k0 = root.getChildren().get(0);
        root.reconcile(tabs(box("k1", 1)));
        JXNativeNode k1 = root.getChildren().get(0);
        for (int i = 2; i <= 17; i++) {
            root.reconcile(tabs(box("k" + i, 1)));
        }
        root.reconcile(tabs(box("k1", 1)));
        assertSame(k1, root.getChildren().get(0), "among the sixteen kept");
        root.reconcile(tabs(box("k0", 1)));
        assertNotSame(k0, root.getChildren().get(0), "the oldest was let go");
    }

    @Test
    void aChildBuiltAheadIsLaidOutAndShownWithoutBeingMadeAgain() {
        JXElement one = JXElement.of("column", JXProps.builder().set("key", "one").build(), box("a", 10));
        JXElement two = JXElement.of("column", JXProps.builder().set("key", "two").build(), box("b", 10));
        JXNativeNode root = JXNativeNode.createBackendNode(tabs(one));
        root.layoutForBackend(120, 80);
        assertEquals(false, root.holds("two"));
        root.retainAhead(two);
        assertEquals(true, root.holds("two"));
        assertEquals(true, root.holds("one"));
        assertEquals(1, root.getChildren().size(), "kept aside, not shown");
        root.reconcile(tabs(two));
        JXNativeNode shown = root.getChildren().get(0);
        assertEquals(120, shown.getWidth(), "laid out ahead at the shown child's size");
        root.retainAhead(two); // already there: nothing
        root.retainAhead(JXElement.of("pane")); // no key: nothing
        root.reconcile(tabs(one));
        root.reconcile(tabs(two));
        assertSame(shown, root.getChildren().get(0));

        JXNativeNode plain = JXNativeNode.createBackendNode(column(box("x", 1), box("y", 1)));
        plain.retainAhead(two);
        assertEquals(false, plain.holds("two"), "only a retaining parent keeps children ahead");
    }

    @Test
    void withoutRetainingAChildThatLeftIsMadeAgain() {
        JXNativeNode root = JXNativeNode.createBackendNode(column(box("a", 1), box("b", 1)));
        JXNativeNode a = root.getChildren().get(0);
        root.reconcile(column(box("b", 1), box("c", 1)));
        root.reconcile(column(box("a", 1), box("b", 1)));
        assertNotSame(a, root.getChildren().get(0));
    }

    @Test
    void theSameChildrenInTheSameOrderChangeNothing() {
        JXElement a = row("a", "A");
        JXElement b = row("b", "B");
        JXNativeNode root = JXNativeNode.createBackendNode(column(a, b));
        root.layoutForBackend(100, 100);
        List<JXNativeNode> before = kids(root);
        root.reconcile(column(a, b));
        assertEquals(before, kids(root));
        root.reconcile(column(a));
        assertEquals(1, root.getChildren().size(), "a removed key drops its node");
        assertSame(before.get(0), root.getChildren().get(0));
    }

    private static final int MANY = 40; // past the size where reorders look keys up in a map

    private static JXElement rows(Object[] keys) {
        JXElement[] rows = new JXElement[keys.length];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = row(keys[i], String.valueOf(keys[i]));
        }
        return column(rows);
    }

    private static Object[] keys(int n) {
        Object[] keys = new Object[n];
        for (int i = 0; i < n; i++) {
            keys[i] = new String("k" + i); // distinct objects: keys are matched by identity
        }
        return keys;
    }

    private static void assertReused(List<JXNativeNode> before, int[] order, JXNativeNode root) {
        assertEquals(order.length, root.getChildren().size());
        for (int i = 0; i < order.length; i++) {
            assertSame(before.get(order[i]), root.getChildren().get(i), "position " + i);
        }
    }

    @Test
    void aReversedListKeepsEveryNodeSmallOrLarge() {
        for (int n : new int[] {5, MANY}) {
            Object[] keys = keys(n);
            JXNativeNode root = JXNativeNode.createBackendNode(rows(keys));
            List<JXNativeNode> before = kids(root);
            Object[] reversed = new Object[n];
            int[] order = new int[n];
            for (int i = 0; i < n; i++) {
                reversed[i] = keys[n - 1 - i];
                order[i] = n - 1 - i;
            }
            root.reconcile(rows(reversed));
            assertReused(before, order, root);
        }
    }

    @Test
    void aRowInsertedAtTheTopKeepsTheOthers() {
        for (int n : new int[] {5, MANY}) {
            Object[] keys = keys(n + 1);
            JXNativeNode root = JXNativeNode.createBackendNode(rows(java.util.Arrays.copyOfRange(keys, 1, n + 1)));
            List<JXNativeNode> before = kids(root);
            root.reconcile(rows(keys));
            assertEquals(n + 1, root.getChildren().size());
            assertEquals(keys[0], root.getChildren().get(0).getProperty("key"));
            for (int i = 0; i < n; i++) {
                assertSame(before.get(i), root.getChildren().get(i + 1), "row " + i);
            }
        }
    }

    @Test
    void aRowRemovedFromTheMiddleKeepsTheOthers() {
        for (int n : new int[] {5, MANY}) {
            Object[] keys = keys(n);
            JXNativeNode root = JXNativeNode.createBackendNode(rows(keys));
            List<JXNativeNode> before = kids(root);
            int gone = n / 2;
            Object[] left = new Object[n - 1];
            int[] order = new int[n - 1];
            for (int i = 0, j = 0; i < n; i++) {
                if (i != gone) {
                    left[j] = keys[i];
                    order[j++] = i;
                }
            }
            root.reconcile(rows(left));
            assertReused(before, order, root);
        }
    }

    @Test
    void aRepeatedKeyAmongTheCurrentChildrenGoesToTheFirstOne() {
        for (int n : new int[] {4, MANY}) {
            Object[] keys = keys(n);
            Object[] repeated = keys.clone();
            repeated[n - 1] = keys[1]; // the last row has the key of the second: written positionally, as repeats are
            JXNativeNode root = JXNativeNode.createBackendNode(rows(keys));
            root.reconcile(rows(repeated));
            List<JXNativeNode> before = kids(root);
            assertSame(root.getChildren().get(1).getProperty("key"), before.get(n - 1).getProperty("key"));
            // distinct keys again, out of order: the key goes to the first child that has it, once
            Object[] moved = new Object[n];
            moved[0] = keys[1];
            moved[1] = keys[0];
            for (int i = 2; i < n; i++) {
                moved[i] = keys[i];
            }
            root.reconcile(rows(moved));
            assertSame(before.get(1), root.getChildren().get(0), "the first child with the key");
            assertSame(before.get(0), root.getChildren().get(1));
            assertEquals(n, new java.util.HashSet<JXNativeNode>(kids(root)).size(), "no node twice");
        }
    }

    @Test
    void childrenOfARetainingParentComeBackAfterAReorderOfManyRows() {
        Object[] keys = keys(MANY);
        JXNativeNode root = JXNativeNode.createBackendNode(tabs(rowsOf(keys)));
        List<JXNativeNode> before = kids(root);
        Object[] reversed = new Object[MANY - 1]; // the first row leaves, the rest are reversed
        for (int i = 0; i < reversed.length; i++) {
            reversed[i] = keys[MANY - 1 - i];
        }
        root.reconcile(tabs(rowsOf(reversed)));
        assertEquals(MANY - 1, root.getChildren().size());
        assertSame(before.get(MANY - 1), root.getChildren().get(0));
        assertEquals(true, root.holds(keys[0]), "kept aside");
        root.reconcile(tabs(rowsOf(keys)));
        for (int i = 0; i < MANY; i++) {
            assertSame(before.get(i), root.getChildren().get(i), "row " + i + " comes back as it was");
        }
    }

    private static JXElement[] rowsOf(Object[] keys) {
        return rows(keys).getChildren().toArray(new JXElement[0]);
    }

    @Test
    void shuffledRowsAreFoundWithoutScanningTheChildrenForEachOne() {
        int n = 20000;
        Object[] keys = keys(n);
        JXNativeNode root = JXNativeNode.createBackendNode(rows(keys));
        List<JXNativeNode> before = kids(root);
        Object[] shuffled = keys.clone();
        java.util.Collections.shuffle(java.util.Arrays.asList(shuffled), new java.util.Random(7));
        JXElement next = rows(shuffled);
        long start = System.nanoTime();
        root.reconcile(next);
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertTrue(millis < 1000, "20000 shuffled rows took " + millis + " ms");
        java.util.Map<Object, JXNativeNode> was = new java.util.IdentityHashMap<>();
        for (JXNativeNode node : before) {
            was.put(node.getProperty("key"), node);
        }
        for (int i = 0; i < n; i += 997) {
            assertSame(was.get(shuffled[i]), root.getChildren().get(i));
        }
    }
}
