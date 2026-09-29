package com.jxparallel.javafx.layout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Accordion;
import javafx.scene.control.Separator;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.BeforeContainer;

import com.jxparallel.javafx.layout.JXLayoutDifferentialTest.Spec;
import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;
import com.jxparallel.ui.native2d.JXNativeNode;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Differential test against real JavaFX 21 with Modena for Separator, TitledPane and Accordion:
 * random boxes of separators, titled panes (expanded or not, collapsible or not, with a region or a
 * column of regions as content) and accordions (any pane or none expanded) are laid out by JavaFX
 * and by the native layout, and every node must get the same position and size. The content of a
 * collapsed pane is not compared: JavaFX hides it and the native layout does not place it.
 */
class JXTitledDifferentialTest {
    private static final String[] LABELS = {"", "A", "Title", "Customer details"};

    @BeforeContainer
    static void startToolkit() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyStarted) {
            // started by an earlier test class
        }
    }

    @Property(tries = 500)
    void titledLayoutMatchesJavaFx(@ForAll("boxes") Spec tree, @ForAll @IntRange(max = 600) int width,
                                   @ForAll @IntRange(max = 600) int height) {
        Region fx = toJavaFx(tree);
        new Scene(fx);
        fx.applyCss();
        fx.resize(width, height);
        fx.layout();

        JXNativeNode jx = JXNativeNode.createBackendNode(toElement(tree));
        jx.layoutForBackend(width, height);

        StringBuilder expected = new StringBuilder();
        describe(fx, fx, 0, expected);
        StringBuilder actual = new StringBuilder();
        describe(jx, 0, actual);
        assertEquals(expected.toString(), actual.toString(), tree.toString());
    }

    @Provide
    Arbitrary<Spec> boxes() {
        Arbitrary<Spec> item = Arbitraries.frequencyOf(
                net.jqwik.api.Tuple.of(1, separator()),
                net.jqwik.api.Tuple.of(2, titled()),
                net.jqwik.api.Tuple.of(1, accordion()));
        return Combinators.combine(Arbitraries.of("column", "row", "stack"), Arbitraries.integers().between(0, 8),
                        item.list().ofMinSize(1).ofMaxSize(3))
                .as((type, gap, children) -> {
                    Map<String, Object> props = new LinkedHashMap<String, Object>();
                    props.put("gap", gap);
                    return new Spec(type, props, children);
                });
    }

    private static Arbitrary<Spec> separator() {
        return Arbitraries.of("horizontal", "vertical").map(orientation -> {
            Map<String, Object> props = new LinkedHashMap<String, Object>();
            props.put("orientation", orientation);
            return new Spec("separator", props, new ArrayList<Spec>());
        });
    }

    private static Arbitrary<Spec> titled() {
        return Combinators.combine(Arbitraries.of(LABELS), Arbitraries.of(true, false), Arbitraries.of(true, false),
                        content())
                .as((label, expanded, collapsible, content) -> titledSpec(label, expanded, collapsible, content));
    }

    private static Spec titledSpec(String label, boolean expanded, boolean collapsible, Spec content) {
        Map<String, Object> props = new LinkedHashMap<String, Object>();
        props.put("label", label);
        props.put("expanded", expanded);
        props.put("collapsible", collapsible);
        List<Spec> children = new ArrayList<Spec>();
        children.add(content);
        return new Spec("titled", props, children);
    }

    /** Up to four panes; the chosen index opens one pane, an index past the end opens none. */
    private static Arbitrary<Spec> accordion() {
        Arbitrary<Spec> pane = Combinators.combine(Arbitraries.of(LABELS), content())
                .as((label, content) -> titledSpec(label, false, true, content));
        return Combinators.combine(pane.list().ofMinSize(1).ofMaxSize(4), Arbitraries.integers().between(0, 4))
                .as((panes, open) -> {
                    List<Spec> children = new ArrayList<Spec>();
                    for (int i = 0; i < panes.size(); i++) {
                        Spec p = panes.get(i);
                        children.add(i == open ? p.with(java.util.Collections.<String, Object>singletonMap("expanded", true)) : p);
                    }
                    return new Spec("accordion", new LinkedHashMap<String, Object>(), children);
                });
    }

    /** A region with random sizes, or a column of two of them. */
    private static Arbitrary<Spec> content() {
        Arbitrary<Spec> region = Combinators.combine(Arbitraries.integers().between(0, 60), Arbitraries.integers().between(0, 200),
                        Arbitraries.integers().between(0, 60), Arbitraries.integers().between(0, 200))
                .as((minW, prefW, minH, prefH) -> {
                    Map<String, Object> props = new LinkedHashMap<String, Object>();
                    props.put("minWidth", minW);
                    props.put("prefWidth", prefW);
                    props.put("minHeight", minH);
                    props.put("prefHeight", prefH);
                    return new Spec("region", props, new ArrayList<Spec>());
                });
        Arbitrary<Spec> column = region.list().ofSize(2).map(children -> {
            Map<String, Object> props = new LinkedHashMap<String, Object>();
            props.put("gap", 4);
            return new Spec("column", props, children);
        });
        return Arbitraries.frequencyOf(net.jqwik.api.Tuple.of(3, region), net.jqwik.api.Tuple.of(1, column));
    }

    private static JXElement toElement(Spec spec) {
        JXProps.Builder props = JXProps.builder();
        for (Map.Entry<String, Object> entry : spec.props.entrySet()) {
            props.set(entry.getKey(), entry.getValue());
        }
        JXElement[] children = new JXElement[spec.children.size()];
        for (int i = 0; i < children.length; i++) {
            children[i] = toElement(spec.children.get(i));
        }
        return JXElement.of(spec.type, props.build(), children);
    }

    private static Region toJavaFx(Spec spec) {
        double gap = spec.props.containsKey("gap") ? ((Number) spec.props.get("gap")).doubleValue() : 0;
        switch (spec.type) {
            case "column": {
                VBox box = new VBox(gap);
                spec.children.forEach(child -> box.getChildren().add(toJavaFx(child)));
                return box;
            }
            case "row": {
                HBox box = new HBox(gap);
                spec.children.forEach(child -> box.getChildren().add(toJavaFx(child)));
                return box;
            }
            case "stack": {
                StackPane stack = new StackPane();
                spec.children.forEach(child -> stack.getChildren().add(toJavaFx(child)));
                return stack;
            }
            case "separator":
                return new Separator("vertical".equals(spec.props.get("orientation")) ? Orientation.VERTICAL : Orientation.HORIZONTAL);
            case "titled": {
                TitledPane pane = new TitledPane((String) spec.props.get("label"), toJavaFx(spec.children.get(0)));
                pane.setAnimated(false);
                pane.setCollapsible((Boolean) spec.props.get("collapsible"));
                pane.setExpanded((Boolean) spec.props.get("expanded"));
                return pane;
            }
            case "accordion": {
                Accordion accordion = new Accordion();
                for (Spec child : spec.children) {
                    TitledPane pane = (TitledPane) toJavaFx(child);
                    accordion.getPanes().add(pane);
                    if ((Boolean) child.props.get("expanded")) {
                        accordion.setExpandedPane(pane);
                    }
                }
                return accordion;
            }
            default: {
                Region region = new Region();
                region.setMinSize(((Number) spec.props.get("minWidth")).doubleValue(), ((Number) spec.props.get("minHeight")).doubleValue());
                region.setPrefSize(((Number) spec.props.get("prefWidth")).doubleValue(), ((Number) spec.props.get("prefHeight")).doubleValue());
                return region;
            }
        }
    }

    /** Position relative to the root and size; for controls, only the nodes the native tree has. */
    private static void describe(Region node, Region root, int depth, StringBuilder out) {
        Point2D origin = node.localToScene(0, 0).subtract(root.localToScene(0, 0));
        indent(depth, out);
        out.append(fmt(origin.getX())).append(',').append(fmt(origin.getY())).append(' ')
                .append(fmt(node.getWidth())).append('x').append(fmt(node.getHeight())).append('\n');
        List<Node> children = new ArrayList<Node>();
        if (node instanceof TitledPane) {
            if (((TitledPane) node).isExpanded()) {
                children.add(((TitledPane) node).getContent());
            }
        } else if (node instanceof Accordion) {
            children.addAll(((Accordion) node).getPanes());
        } else if (!(node instanceof Separator)) {
            children.addAll(node.getChildrenUnmodifiable());
        }
        for (Node child : children) {
            describe((Region) child, root, depth + 1, out);
        }
    }

    private static void describe(JXNativeNode node, int depth, StringBuilder out) {
        indent(depth, out);
        out.append(node.getX()).append(',').append(node.getY()).append(' ')
                .append(node.getWidth()).append('x').append(node.getHeight()).append('\n');
        if (node.collapsed()) {
            return;
        }
        for (JXNativeNode child : node.getChildren()) {
            describe(child, depth + 1, out);
        }
    }

    private static String fmt(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private static void indent(int depth, StringBuilder out) {
        for (int i = 0; i < depth; i++) {
            out.append("  ");
        }
    }
}
