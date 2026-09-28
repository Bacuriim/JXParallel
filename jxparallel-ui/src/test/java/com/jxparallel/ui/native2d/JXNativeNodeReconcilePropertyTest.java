package com.jxparallel.ui.native2d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;
import net.jqwik.api.constraints.IntRange;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Property tests: for random trees a and b, updating a in place to b must give exactly the tree
 * and layout that mounting b from scratch gives. jqwik shrinks a failure to the smallest pair.
 */
class JXNativeNodeReconcilePropertyTest {
    private static final String[] LEAVES = {"#text", "button", "checkbox", "textarea", "input"};
    private static final String[] CONTAINERS = {"row", "column", "stack"};

    @Property(tries = 500)
    void reconcileEqualsFreshMount(@ForAll("trees") JXElement before, @ForAll("trees") JXElement after,
                                   @ForAll @IntRange(max = 900) int width, @ForAll @IntRange(max = 700) int height,
                                   @ForAll boolean laidOutBefore) {
        JXNativeNode updated = JXNativeNode.createBackendNode(before);
        if (laidOutBefore) {
            updated.layoutForBackend(width, height); // caches filled, so stale caches would show
        }
        updated.reconcile(after);
        updated.layoutForBackend(width, height);

        JXNativeNode fresh = JXNativeNode.createBackendNode(after);
        fresh.layoutForBackend(width, height);

        assertEquals(describe(fresh), describe(updated));
    }

    /** Small edits of the same screen, the common case in an app: most nodes are reused. */
    @Property(tries = 1000)
    void reconcileAfterSmallEditsEqualsFreshMount(@ForAll("trees") JXElement before, @ForAll Random random,
                                                  @ForAll @IntRange(max = 900) int width) {
        JXElement after = edit(before, random);
        JXNativeNode updated = JXNativeNode.createBackendNode(before);
        updated.layoutForBackend(width, 600);
        updated.reconcile(after);
        updated.layoutForBackend(width, 600);

        JXNativeNode fresh = JXNativeNode.createBackendNode(after);
        fresh.layoutForBackend(width, 600);

        assertEquals(describe(fresh), describe(updated));
    }

    @Property(tries = 200)
    void reconcilingTheSameElementIsANoOp(@ForAll("trees") JXElement tree,
                                          @ForAll @IntRange(max = 900) int width) {
        JXNativeNode node = JXNativeNode.createBackendNode(tree);
        node.layoutForBackend(width, 600);
        String laidOut = describe(node);

        node.reconcile(tree);
        node.layoutForBackend(width, 600);

        assertEquals(laidOut, describe(node));
    }

    @Provide
    Arbitrary<JXElement> trees() {
        // Same root type on both sides, otherwise reconcile asks for a remount.
        return container(Arbitraries.just("column"), element(3));
    }

    private static Arbitrary<JXElement> element(int depth) {
        Arbitrary<JXElement> leaf = Combinators.combine(Arbitraries.of(LEAVES), Arbitraries.strings().alpha().ofMaxLength(6))
                .as((type, value) -> JXElement.of(type, JXProps.builder().set("value", value).build()));
        if (depth == 0) {
            return leaf;
        }
        return Arbitraries.frequencyOf(
                Tuple.of(3, leaf),
                Tuple.of(2, container(Arbitraries.of(CONTAINERS), element(depth - 1))));
    }

    private static Arbitrary<JXElement> container(Arbitrary<String> type, Arbitrary<JXElement> child) {
        return Combinators.combine(type, Arbitraries.integers().between(0, 12), child.list().ofMaxSize(4))
                .as((name, gap, children) -> JXElement.of(name, JXProps.builder().set("gap", gap).build(),
                        children.toArray(new JXElement[0])));
    }

    /** Returns a copy of the tree where each node has a 1 in 4 chance of one edit. */
    private static JXElement edit(JXElement element, Random random) {
        List<JXElement> children = new ArrayList<JXElement>();
        for (JXElement child : element.getChildren()) {
            children.add(edit(child, random));
        }
        Object gap = element.getProps().get("gap");
        Object value = element.getProps().get("value");
        switch (random.nextInt(4) == 0 ? random.nextInt(5) : -1) {
            case 0:
                gap = random.nextInt(13);
                break;
            case 1:
                value = "v" + random.nextInt(1000);
                break;
            case 2:
                if (!children.isEmpty()) {
                    children.remove(random.nextInt(children.size()));
                }
                break;
            case 3:
                children.add(random.nextInt(children.size() + 1), JXElement.of(LEAVES[random.nextInt(LEAVES.length)]));
                break;
            case 4:
                if (!children.isEmpty()) {
                    int index = random.nextInt(children.size());
                    children.set(index, JXElement.of(CONTAINERS[random.nextInt(CONTAINERS.length)], JXProps.empty(),
                            JXElement.text("replaced")));
                }
                break;
            default:
                break;
        }
        JXProps.Builder props = JXProps.builder();
        if (gap != null) {
            props.set("gap", gap);
        }
        if (value != null) {
            props.set("value", value);
        }
        return JXElement.of(element.getType(), props.build(), children.toArray(new JXElement[0]));
    }

    private static String describe(JXNativeNode node) {
        StringBuilder out = new StringBuilder();
        describe(node, 0, out);
        return out.toString();
    }

    private static void describe(JXNativeNode node, int depth, StringBuilder out) {
        for (int i = 0; i < depth; i++) {
            out.append("  ");
        }
        out.append(node.getType()).append(' ')
                .append(node.getProperty("value")).append(" gap=").append(node.getProperty("gap"))
                .append(" [").append(node.getX()).append(',').append(node.getY()).append(' ')
                .append(node.getWidth()).append('x').append(node.getHeight()).append("]\n");
        List<JXNativeNode> children = node.getChildren();
        for (JXNativeNode child : children) {
            describe(child, depth + 1, out);
        }
    }
}
