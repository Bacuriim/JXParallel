package com.jxparallel.javafx.layout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.geometry.Orientation;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.BeforeContainer;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;
import com.jxparallel.ui.native2d.JXNativeNode;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Differential test against real JavaFX: random trees of VBox, HBox, StackPane, BorderPane and
 * Region with random min/pref/max sizes, grow priorities, spacing, alignment, fill flags, padding,
 * margins and per-child alignment are laid out by JavaFX and by the native layout, and every node
 * must get the same position and size. JavaFX is the specification.
 */
class JXLayoutDifferentialTest {
    private static final String[] POSITIONS = {"TOP_LEFT", "TOP_CENTER", "TOP_RIGHT", "CENTER_LEFT", "CENTER",
            "CENTER_RIGHT", "BOTTOM_LEFT", "BOTTOM_CENTER", "BOTTOM_RIGHT"};
    private static final String[] SIZE_KEYS = {"minWidth", "prefWidth", "maxWidth", "minHeight", "prefHeight", "maxHeight"};
    private static final String[] SLOTS = {"top", "bottom", "left", "right", "center"};

    /** One node of a generated tree: type, props and children. */
    static final class Spec {
        final String type;
        final Map<String, Object> props;
        final List<Spec> children;

        Spec(String type, Map<String, Object> props, List<Spec> children) {
            this.type = type;
            this.props = props;
            this.children = children;
        }

        /** A copy with more props: generated values are shared by jqwik while shrinking, so never mutate them. */
        Spec with(Map<String, Object> extra) {
            Map<String, Object> merged = new LinkedHashMap<String, Object>(props);
            merged.putAll(extra);
            return new Spec(type, merged, children);
        }

        @Override
        public String toString() {
            return type + props + (children.isEmpty() ? "" : children.toString());
        }
    }

    @Property(tries = 1000)
    void nativeLayoutMatchesJavaFx(@ForAll("trees") Spec tree, @ForAll @IntRange(max = 600) int width,
                                   @ForAll @IntRange(max = 600) int height) {
        assertSameLayout(tree, width, height);
    }

    @Property(tries = 1000)
    void gridLayoutMatchesJavaFx(@ForAll("grids") Spec tree, @ForAll @IntRange(max = 600) int width,
                                 @ForAll @IntRange(max = 600) int height) {
        assertSameLayout(tree, width, height);
    }

    @Property(tries = 1000)
    void absoluteLayoutMatchesJavaFx(@ForAll("absolutes") Spec tree, @ForAll @IntRange(max = 600) int width,
                                     @ForAll @IntRange(max = 600) int height) {
        assertSameLayout(tree, width, height);
    }

    /** FlowPane and TilePane as the root: their content bias does not come into play (see JXFlowLayout). */
    @Property(tries = 1000)
    void flowAndTileLayoutMatchesJavaFx(@ForAll("flows") Spec tree, @ForAll @IntRange(max = 600) int width,
                                        @ForAll @IntRange(max = 600) int height) {
        assertSameLayout(tree, width, height);
    }

    @BeforeContainer
    static void startToolkit() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyStarted) {
            // started by an earlier test class
        }
    }

    /**
     * Buttons with the sizes Scene Builder writes: pref set, min and max unset (-1), USE_PREF_SIZE
     * (-Infinity) or a number. Control sizes need Modena, so the JavaFX tree gets a scene and CSS.
     */
    @Property(tries = 500)
    void controlSizesMatchJavaFx(@ForAll("buttonBoxes") Spec tree, @ForAll @IntRange(max = 600) int width,
                                 @ForAll @IntRange(max = 600) int height) {
        Region fx = toJavaFx(tree, null);
        new Scene(fx);
        fx.applyCss();
        assertSameLayout(tree, fx, width, height);
    }

    @Provide
    Arbitrary<Spec> buttonBoxes() {
        Arbitrary<Double> sizeLimit = Arbitraries.frequencyOf(Tuple.of(1, Arbitraries.just(-1.0)),
                Tuple.of(1, Arbitraries.just(Double.NEGATIVE_INFINITY)), Tuple.of(1, Arbitraries.integers().between(0, 200).map(Integer::doubleValue)));
        Arbitrary<Double> minLimit = Arbitraries.frequencyOf(Tuple.of(1, Arbitraries.just(Double.NEGATIVE_INFINITY)),
                Tuple.of(1, Arbitraries.integers().between(0, 60).map(Integer::doubleValue)));
        Arbitrary<Spec> button = Combinators.combine(Arbitraries.integers().between(10, 200), Arbitraries.integers().between(10, 60),
                        minLimit, sizeLimit, minLimit, sizeLimit)
                .as((pw, ph, minW, maxW, minH, maxH) -> {
                    Map<String, Object> props = new LinkedHashMap<String, Object>();
                    props.put("label", "OK");
                    props.put("prefWidth", pw.doubleValue());
                    props.put("prefHeight", ph.doubleValue());
                    props.put("minWidth", minW);
                    props.put("minHeight", minH);
                    if (maxW != -1.0) {
                        props.put("maxWidth", maxW);
                    }
                    if (maxH != -1.0) {
                        props.put("maxHeight", maxH);
                    }
                    return new Spec("button", props, new ArrayList<Spec>());
                });
        return Combinators.combine(Arbitraries.of("column", "row"), Arbitraries.integers().between(0, 12),
                        Arbitraries.of(POSITIONS), Arbitraries.of(true, false), button.list().ofMinSize(1).ofMaxSize(4))
                .as((type, gap, alignment, fill, children) -> {
                    Map<String, Object> props = new LinkedHashMap<String, Object>();
                    props.put("gap", gap);
                    props.put("alignment", alignment);
                    props.put("column".equals(type) ? "fillWidth" : "fillHeight", fill);
                    return new Spec(type, props, children);
                });
    }

    private static void assertSameLayout(Spec tree, int width, int height) {
        assertSameLayout(tree, toJavaFx(tree, null), width, height);
    }

    private static void assertSameLayout(Spec tree, Region fx, int width, int height) {
        fx.resize(width, height);
        fx.layout();

        JXNativeNode jx = JXNativeNode.createBackendNode(toElement(tree));
        jx.layoutForBackend(width, height);

        StringBuilder expected = new StringBuilder();
        describe(fx, 0, 0, 0, expected);
        StringBuilder actual = new StringBuilder();
        describe(jx, 0, actual);
        assertEquals(expected.toString(), actual.toString(), tree.toString());
    }

    @Provide
    Arbitrary<Spec> trees() {
        return container(3);
    }

    @Provide
    Arbitrary<Spec> grids() {
        return grid(1);
    }

    @Provide
    Arbitrary<Spec> absolutes() {
        return absolute(1);
    }

    @Provide
    Arbitrary<Spec> flows() {
        Arbitrary<Map<String, Object>> common = Combinators.combine(Arbitraries.of("horizontal", "vertical"),
                        Arbitraries.integers().between(0, 8), Arbitraries.integers().between(0, 8), Arbitraries.of(POSITIONS))
                .as((orientation, hgap, vgap, alignment) -> {
                    Map<String, Object> props = new LinkedHashMap<String, Object>();
                    props.put("orientation", orientation);
                    props.put("hgap", hgap);
                    props.put("vgap", vgap);
                    props.put("alignment", alignment);
                    return props;
                });
        Arbitrary<Map<String, Object>> flow = Combinators.combine(Arbitraries.integers().between(0, 500),
                        Arbitraries.of("LEFT", "CENTER", "RIGHT"), Arbitraries.of("TOP", "CENTER", "BOTTOM"))
                .as((wrap, column, row) -> {
                    Map<String, Object> props = new LinkedHashMap<String, Object>();
                    props.put("prefWrapLength", wrap);
                    props.put("columnHalignment", column);
                    props.put("rowValignment", row);
                    return props;
                });
        Arbitrary<Integer> unset = Arbitraries.just(-1);
        Arbitrary<Map<String, Object>> tile = Combinators.combine(Arbitraries.integers().between(1, 6),
                        Arbitraries.integers().between(1, 6), Arbitraries.oneOf(unset, Arbitraries.integers().between(0, 120)),
                        Arbitraries.oneOf(unset, Arbitraries.integers().between(0, 120)), Arbitraries.of(POSITIONS))
                .as((columns, rows, tileWidth, tileHeight, tileAlignment) -> {
                    Map<String, Object> props = new LinkedHashMap<String, Object>();
                    props.put("prefColumns", columns);
                    props.put("prefRows", rows);
                    putIfSet(props, "prefTileWidth", tileWidth);
                    putIfSet(props, "prefTileHeight", tileHeight);
                    props.put("tileAlignment", tileAlignment);
                    return props;
                });
        return Combinators.combine(Arbitraries.of("flow", "tile"), common, flow, tile, node(1).list().ofMaxSize(8))
                .as((type, commonProps, flowProps, tileProps, children) -> {
                    Map<String, Object> props = new LinkedHashMap<String, Object>(commonProps);
                    props.putAll("flow".equals(type) ? flowProps : tileProps);
                    return new Spec(type, props, children);
                })
                .flatMap(spec -> extras().map(spec::with));
    }

    /** A Pane or AnchorPane: children at random layout positions, with random anchors on anchor panes. */
    private static Arbitrary<Spec> absolute(int depth) {
        Arbitrary<Integer> anchor = Arbitraries.frequencyOf(Tuple.of(1, Arbitraries.just(-1)),
                Tuple.of(1, Arbitraries.integers().between(0, 40)));
        Arbitrary<Map<String, Object>> place = Combinators.combine(Arbitraries.integers().between(0, 100),
                        Arbitraries.integers().between(0, 100), anchor, anchor, anchor, anchor)
                .as((x, y, left, right, top, bottom) -> {
                    Map<String, Object> props = new LinkedHashMap<String, Object>();
                    props.put("layoutX", x);
                    props.put("layoutY", y);
                    putIfSet(props, "leftAnchor", left);
                    putIfSet(props, "rightAnchor", right);
                    putIfSet(props, "topAnchor", top);
                    putIfSet(props, "bottomAnchor", bottom);
                    return props;
                });
        return Combinators.combine(Arbitraries.of("pane", "anchor"), node(depth).list().ofMaxSize(5),
                        place.list().ofSize(5))
                .as((type, children, places) -> {
                    List<Spec> placed = new ArrayList<Spec>();
                    for (int i = 0; i < children.size(); i++) {
                        Map<String, Object> props = new LinkedHashMap<String, Object>(places.get(i));
                        if ("pane".equals(type)) {
                            props.keySet().retainAll(java.util.Arrays.asList("layoutX", "layoutY"));
                        }
                        placed.add(children.get(i).with(props));
                    }
                    return new Spec(type, new LinkedHashMap<String, Object>(), placed);
                })
                .flatMap(spec -> extras().map(spec::with));
    }

    /** 1 or 2 cells, or GridPane.REMAINING (Integer.MAX_VALUE, up to the last column or row). */
    private static Arbitrary<Integer> spans() {
        return Arbitraries.frequencyOf(Tuple.of(4, Arbitraries.integers().between(1, 2)), Tuple.of(1, Arbitraries.just(Integer.MAX_VALUE)));
    }

    /** A grid whose children get random cells (overlaps allowed, as in JavaFX) and constraints. */
    private static Arbitrary<Spec> grid(int depth) {
        Arbitrary<Map<String, Object>> cell = Combinators.combine(Arbitraries.integers().between(0, 3),
                        Arbitraries.integers().between(0, 3), spans(),
                        spans(), Arbitraries.of("", "true", "false"),
                        Arbitraries.of("", "true", "false"))
                .as((column, row, columnSpan, rowSpan, fillWidth, fillHeight) -> {
                    Map<String, Object> props = new LinkedHashMap<String, Object>();
                    props.put("column", column);
                    props.put("row", row);
                    props.put("columnSpan", columnSpan);
                    props.put("rowSpan", rowSpan);
                    if (!fillWidth.isEmpty()) {
                        props.put("cellFillWidth", Boolean.valueOf(fillWidth));
                    }
                    if (!fillHeight.isEmpty()) {
                        props.put("cellFillHeight", Boolean.valueOf(fillHeight));
                    }
                    return props;
                });
        return Combinators.combine(node(depth).list().ofMaxSize(6), cell.list().ofSize(6), constraints(true),
                        constraints(false), Arbitraries.integers().between(0, 8), Arbitraries.integers().between(0, 8),
                        Arbitraries.of(POSITIONS))
                .as((children, cells, columns, rows, hgap, vgap, alignment) -> {
                    List<Spec> placed = new ArrayList<Spec>();
                    for (int i = 0; i < children.size(); i++) {
                        Map<String, Object> placement = new LinkedHashMap<String, Object>(cells.get(i));
                        // known divergence (see JXGridLayout): a child spanning a fixed (USE_PREF_SIZE) track
                        if (spansFixed(columns, (Integer) placement.get("column"), (Integer) placement.get("columnSpan"))) {
                            placement.put("columnSpan", 1);
                        }
                        if (spansFixed(rows, (Integer) placement.get("row"), (Integer) placement.get("rowSpan"))) {
                            placement.put("rowSpan", 1);
                        }
                        placed.add(children.get(i).with(placement));
                    }
                    Map<String, Object> props = new LinkedHashMap<String, Object>();
                    props.put("hgap", hgap);
                    props.put("vgap", vgap);
                    props.put("alignment", alignment);
                    props.put("columns", columns);
                    props.put("rows", rows);
                    return new Spec("grid", props, placed);
                })
                .flatMap(spec -> extras().map(spec::with));
    }

    /** True if a span of more than one track covers a track whose min is USE_PREF_SIZE. */
    private static boolean spansFixed(List<Map<String, Object>> tracks, int start, int span) {
        if (span == 1) {
            return false;
        }
        int end = span == Integer.MAX_VALUE ? tracks.size() : Math.min(tracks.size(), start + span);
        for (int i = start; i < end; i++) {
            for (Object v : tracks.get(i).values()) {
                if (v instanceof Double && ((Double) v) == Double.NEGATIVE_INFINITY) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Column or row constraints, each value set sometimes; percents stay small so they rarely pass 100. */
    private static Arbitrary<List<Map<String, Object>>> constraints(boolean columns) {
        String axis = columns ? "Width" : "Height";
        Arbitrary<Integer> unset = Arbitraries.just(-1);
        Arbitrary<Map<String, Object>> one = Combinators.combine(
                        Arbitraries.oneOf(unset, Arbitraries.integers().between(0, 60)),
                        Arbitraries.oneOf(unset, Arbitraries.integers().between(0, 150)),
                        Arbitraries.oneOf(unset, Arbitraries.integers().between(0, 200)),
                        Arbitraries.frequencyOf(Tuple.of(3, unset), Tuple.of(1, Arbitraries.integers().between(0, 60))),
                        grow(),
                        columns ? Arbitraries.of("", "LEFT", "CENTER", "RIGHT") : Arbitraries.of("", "TOP", "CENTER", "BOTTOM"),
                        Arbitraries.of("", "true", "false"))
                .as((min, pref, max, percent, grow, alignment, fill) -> {
                    Map<String, Object> constraint = new LinkedHashMap<String, Object>();
                    putIfSet(constraint, "min" + axis, min);
                    putIfSet(constraint, "pref" + axis, pref);
                    putIfSet(constraint, "max" + axis, max);
                    putIfSet(constraint, "percent" + axis, percent);
                    putIfPresent(constraint, columns ? "hgrow" : "vgrow", grow);
                    putIfPresent(constraint, columns ? "halignment" : "valignment", alignment);
                    if (!fill.isEmpty()) {
                        constraint.put("fill" + axis, Boolean.valueOf(fill));
                    }
                    return constraint;
                });
        // sometimes a fixed size, as new ColumnConstraints(w) / RowConstraints(h) make: min and max USE_PREF_SIZE
        Arbitrary<Map<String, Object>> fixed = Arbitraries.integers().between(0, 150).map(size -> {
            Map<String, Object> constraint = new LinkedHashMap<String, Object>();
            constraint.put("min" + axis, Double.NEGATIVE_INFINITY);
            constraint.put("pref" + axis, size);
            constraint.put("max" + axis, Double.NEGATIVE_INFINITY);
            return constraint;
        });
        return Arbitraries.frequencyOf(Tuple.of(4, one), Tuple.of(1, fixed)).list().ofMaxSize(4);
    }

    private static void putIfSet(Map<String, Object> props, String key, int value) {
        if (value >= 0) {
            props.put(key, value);
        }
    }

    private static Arbitrary<Spec> node(int depth) {
        return depth == 0 ? region() : Arbitraries.frequencyOf(
                Tuple.of(3, region()), Tuple.of(1, container(depth - 1)));
    }

    private static Arbitrary<Spec> region() {
        return Combinators.combine(sizes(), grow(), grow(), extras())
                .as((sizes, vgrow, hgrow, extras) -> {
                    Map<String, Object> props = new LinkedHashMap<String, Object>(sizes);
                    putIfPresent(props, "vgrow", vgrow);
                    putIfPresent(props, "hgrow", hgrow);
                    props.putAll(extras);
                    return new Spec("region", props, new ArrayList<Spec>());
                });
    }

    /** Padding, margin in the parent, and alignment in a stack or border cell, each sometimes. */
    private static Arbitrary<Map<String, Object>> extras() {
        Arbitrary<int[]> insets = Arbitraries.integers().between(0, 10).array(int[].class).ofSize(4);
        Arbitrary<int[]> none = Arbitraries.just(new int[0]);
        return Combinators.combine(Arbitraries.frequencyOf(Tuple.of(2, none), Tuple.of(1, insets)),
                        Arbitraries.frequencyOf(Tuple.of(2, none), Tuple.of(1, insets)),
                        Arbitraries.frequencyOf(Tuple.of(3, Arbitraries.just("")), Tuple.of(1, Arbitraries.of(POSITIONS))))
                .as((padding, margin, alignment) -> {
                    Map<String, Object> props = new LinkedHashMap<String, Object>();
                    if (padding.length == 4) {
                        props.put("padding", padding);
                    }
                    if (margin.length == 4) {
                        props.put("margin", margin);
                    }
                    if (!alignment.isEmpty()) {
                        Pos pos = Pos.valueOf(alignment);
                        props.put("halignment", pos.getHpos().name());
                        props.put("valignment", pos.getVpos().name());
                    }
                    return props;
                });
    }

    private static Arbitrary<Spec> container(int depth) {
        return Combinators.combine(Arbitraries.of("column", "row", "stack", "border"), Arbitraries.integers().between(0, 12),
                        Arbitraries.of(POSITIONS), Arbitraries.of(true, false), node(depth).list().ofMaxSize(4),
                        grow(), grow(), Arbitraries.frequencyOf(Tuple.of(3, Arbitraries.just(new LinkedHashMap<String, Object>())),
                                Tuple.of(1, sizes())))
                .as((type, gap, alignment, fill, children, vgrow, hgrow, sizes) -> {
                    Map<String, Object> props = new LinkedHashMap<String, Object>(sizes);
                    props.put("gap", gap);
                    props.put("alignment", alignment);
                    props.put("column".equals(type) ? "fillWidth" : "fillHeight", fill);
                    putIfPresent(props, "vgrow", vgrow);
                    putIfPresent(props, "hgrow", hgrow);
                    if ("border".equals(type)) {
                        // one child per slot, in generated order; the gap picks a rotation of the slots
                        List<Spec> placed = new ArrayList<Spec>();
                        for (int i = 0; i < children.size() && i < SLOTS.length; i++) {
                            placed.add(children.get(i).with(java.util.Collections.<String, Object>singletonMap(
                                    "position", SLOTS[(i + gap) % SLOTS.length])));
                        }
                        return new Spec(type, props, placed);
                    }
                    return new Spec(type, props, children);
                })
                .flatMap(spec -> extras().map(spec::with));
    }

    /** Each size is set with probability 1/2; unset means computed, like JavaFX's USE_COMPUTED_SIZE. */
    private static Arbitrary<Map<String, Object>> sizes() {
        Arbitrary<Integer> small = Arbitraries.integers().between(0, 60);
        Arbitrary<Integer> medium = Arbitraries.integers().between(0, 200);
        Arbitrary<Integer> unset = Arbitraries.just(-1);
        return Combinators.combine(Arbitraries.oneOf(unset, small), Arbitraries.oneOf(unset, medium),
                        Arbitraries.oneOf(unset, medium), Arbitraries.oneOf(unset, small),
                        Arbitraries.oneOf(unset, medium), Arbitraries.oneOf(unset, medium))
                .as((a, b, c, d, e, f) -> {
                    int[] values = {a, b, c, d, e, f};
                    Map<String, Object> props = new LinkedHashMap<String, Object>();
                    for (int i = 0; i < values.length; i++) {
                        if (values[i] >= 0) {
                            props.put(SIZE_KEYS[i], values[i]);
                        }
                    }
                    return props;
                });
    }

    private static Arbitrary<String> grow() {
        return Arbitraries.of("", "always", "sometimes", "never");
    }

    private static void putIfPresent(Map<String, Object> props, String key, String value) {
        if (!value.isEmpty()) {
            props.put(key, value);
        }
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

    private static Region toJavaFx(Spec spec, String parentType) {
        Region region;
        Object gap = spec.props.get("gap");
        Pos alignment = spec.props.containsKey("alignment") ? Pos.valueOf((String) spec.props.get("alignment")) : null;
        if ("column".equals(spec.type)) {
            VBox box = new VBox(((Number) gap).doubleValue());
            box.setAlignment(alignment);
            box.setFillWidth((Boolean) spec.props.get("fillWidth"));
            region = box;
        } else if ("row".equals(spec.type)) {
            HBox box = new HBox(((Number) gap).doubleValue());
            box.setAlignment(alignment);
            box.setFillHeight((Boolean) spec.props.get("fillHeight"));
            region = box;
        } else if ("stack".equals(spec.type)) {
            StackPane stack = new StackPane();
            stack.setAlignment(alignment);
            region = stack;
        } else if ("border".equals(spec.type)) {
            region = new BorderPane();
        } else if ("flow".equals(spec.type)) {
            FlowPane flow = new FlowPane();
            flow.setOrientation("vertical".equals(spec.props.get("orientation")) ? Orientation.VERTICAL : Orientation.HORIZONTAL);
            flow.setHgap(((Number) spec.props.get("hgap")).doubleValue());
            flow.setVgap(((Number) spec.props.get("vgap")).doubleValue());
            flow.setAlignment(alignment);
            flow.setPrefWrapLength(((Number) spec.props.get("prefWrapLength")).doubleValue());
            flow.setColumnHalignment(HPos.valueOf((String) spec.props.get("columnHalignment")));
            flow.setRowValignment(VPos.valueOf((String) spec.props.get("rowValignment")));
            region = flow;
        } else if ("tile".equals(spec.type)) {
            TilePane tile = new TilePane();
            tile.setOrientation("vertical".equals(spec.props.get("orientation")) ? Orientation.VERTICAL : Orientation.HORIZONTAL);
            tile.setHgap(((Number) spec.props.get("hgap")).doubleValue());
            tile.setVgap(((Number) spec.props.get("vgap")).doubleValue());
            tile.setAlignment(alignment);
            tile.setPrefColumns((Integer) spec.props.get("prefColumns"));
            tile.setPrefRows((Integer) spec.props.get("prefRows"));
            if (spec.props.containsKey("prefTileWidth")) {
                tile.setPrefTileWidth(((Number) spec.props.get("prefTileWidth")).doubleValue());
            }
            if (spec.props.containsKey("prefTileHeight")) {
                tile.setPrefTileHeight(((Number) spec.props.get("prefTileHeight")).doubleValue());
            }
            tile.setTileAlignment(Pos.valueOf((String) spec.props.get("tileAlignment")));
            region = tile;
        } else if ("pane".equals(spec.type)) {
            region = new Pane();
        } else if ("anchor".equals(spec.type)) {
            region = new AnchorPane();
        } else if ("grid".equals(spec.type)) {
            GridPane grid = new GridPane();
            grid.setHgap(((Number) spec.props.get("hgap")).doubleValue());
            grid.setVgap(((Number) spec.props.get("vgap")).doubleValue());
            grid.setAlignment(alignment);
            for (Object item : (List<?>) spec.props.get("columns")) {
                grid.getColumnConstraints().add(columnConstraints((Map<?, ?>) item));
            }
            for (Object item : (List<?>) spec.props.get("rows")) {
                grid.getRowConstraints().add(rowConstraints((Map<?, ?>) item));
            }
            region = grid;
        } else if ("button".equals(spec.type)) {
            region = new Button((String) spec.props.get("label"));
        } else {
            region = new Region();
        }
        setIfPresent(spec, "minWidth", region::setMinWidth);
        setIfPresent(spec, "prefWidth", region::setPrefWidth);
        setIfPresent(spec, "maxWidth", region::setMaxWidth);
        setIfPresent(spec, "minHeight", region::setMinHeight);
        setIfPresent(spec, "prefHeight", region::setPrefHeight);
        setIfPresent(spec, "maxHeight", region::setMaxHeight);
        if (spec.props.containsKey("padding")) {
            region.setPadding(insets(spec.props.get("padding")));
        }
        for (Spec child : spec.children) {
            Region childRegion = toJavaFx(child, spec.type);
            Insets margin = child.props.containsKey("margin") ? insets(child.props.get("margin")) : null;
            Pos cell = child.props.containsKey("halignment") ? pos(child) : null;
            if (region instanceof GridPane) {
                GridPane.setConstraints(childRegion, (Integer) child.props.get("column"), (Integer) child.props.get("row"),
                        (Integer) child.props.get("columnSpan"), (Integer) child.props.get("rowSpan"));
                ((GridPane) region).getChildren().add(childRegion);
                GridPane.setMargin(childRegion, margin);
                if (child.props.containsKey("halignment")) {
                    GridPane.setHalignment(childRegion, HPos.valueOf((String) child.props.get("halignment")));
                    GridPane.setValignment(childRegion, VPos.valueOf((String) child.props.get("valignment")));
                }
                if (child.props.containsKey("hgrow")) {
                    GridPane.setHgrow(childRegion, Priority.valueOf(((String) child.props.get("hgrow")).toUpperCase()));
                }
                if (child.props.containsKey("vgrow")) {
                    GridPane.setVgrow(childRegion, Priority.valueOf(((String) child.props.get("vgrow")).toUpperCase()));
                }
                GridPane.setFillWidth(childRegion, (Boolean) child.props.get("cellFillWidth"));
                GridPane.setFillHeight(childRegion, (Boolean) child.props.get("cellFillHeight"));
                continue;
            }
            if (region instanceof BorderPane) {
                BorderPane border = (BorderPane) region;
                String slot = (String) child.props.get("position");
                if ("top".equals(slot)) {
                    border.setTop(childRegion);
                } else if ("bottom".equals(slot)) {
                    border.setBottom(childRegion);
                } else if ("left".equals(slot)) {
                    border.setLeft(childRegion);
                } else if ("right".equals(slot)) {
                    border.setRight(childRegion);
                } else {
                    border.setCenter(childRegion);
                }
                BorderPane.setMargin(childRegion, margin);
                BorderPane.setAlignment(childRegion, cell);
                continue;
            }
            ((javafx.scene.layout.Pane) region).getChildren().add(childRegion);
            if (child.props.containsKey("layoutX")) {
                childRegion.setLayoutX(((Number) child.props.get("layoutX")).doubleValue());
                childRegion.setLayoutY(((Number) child.props.get("layoutY")).doubleValue());
            }
            if (region instanceof AnchorPane) {
                anchor(child, "leftAnchor", value -> AnchorPane.setLeftAnchor(childRegion, value));
                anchor(child, "rightAnchor", value -> AnchorPane.setRightAnchor(childRegion, value));
                anchor(child, "topAnchor", value -> AnchorPane.setTopAnchor(childRegion, value));
                anchor(child, "bottomAnchor", value -> AnchorPane.setBottomAnchor(childRegion, value));
            }
            if (region instanceof FlowPane) {
                FlowPane.setMargin(childRegion, margin);
            } else if (region instanceof TilePane) {
                TilePane.setMargin(childRegion, margin);
                TilePane.setAlignment(childRegion, cell);
            } else if (region instanceof VBox) {
                VBox.setMargin(childRegion, margin);
            } else if (region instanceof HBox) {
                HBox.setMargin(childRegion, margin);
            } else if (region instanceof StackPane) {
                StackPane.setMargin(childRegion, margin);
                StackPane.setAlignment(childRegion, cell);
            }
            if ("column".equals(spec.type) && child.props.containsKey("vgrow")) {
                VBox.setVgrow(childRegion, Priority.valueOf(((String) child.props.get("vgrow")).toUpperCase()));
            }
            if ("row".equals(spec.type) && child.props.containsKey("hgrow")) {
                HBox.setHgrow(childRegion, Priority.valueOf(((String) child.props.get("hgrow")).toUpperCase()));
            }
        }
        return region;
    }

    private static ColumnConstraints columnConstraints(Map<?, ?> values) {
        ColumnConstraints constraints = new ColumnConstraints();
        if (values.containsKey("minWidth")) {
            constraints.setMinWidth(((Number) values.get("minWidth")).doubleValue());
        }
        if (values.containsKey("prefWidth")) {
            constraints.setPrefWidth(((Number) values.get("prefWidth")).doubleValue());
        }
        if (values.containsKey("maxWidth")) {
            constraints.setMaxWidth(((Number) values.get("maxWidth")).doubleValue());
        }
        if (values.containsKey("percentWidth")) {
            constraints.setPercentWidth(((Number) values.get("percentWidth")).doubleValue());
        }
        if (values.containsKey("hgrow")) {
            constraints.setHgrow(Priority.valueOf(((String) values.get("hgrow")).toUpperCase()));
        }
        if (values.containsKey("halignment")) {
            constraints.setHalignment(HPos.valueOf((String) values.get("halignment")));
        }
        if (values.containsKey("fillWidth")) {
            constraints.setFillWidth((Boolean) values.get("fillWidth"));
        }
        return constraints;
    }

    private static RowConstraints rowConstraints(Map<?, ?> values) {
        RowConstraints constraints = new RowConstraints();
        if (values.containsKey("minHeight")) {
            constraints.setMinHeight(((Number) values.get("minHeight")).doubleValue());
        }
        if (values.containsKey("prefHeight")) {
            constraints.setPrefHeight(((Number) values.get("prefHeight")).doubleValue());
        }
        if (values.containsKey("maxHeight")) {
            constraints.setMaxHeight(((Number) values.get("maxHeight")).doubleValue());
        }
        if (values.containsKey("percentHeight")) {
            constraints.setPercentHeight(((Number) values.get("percentHeight")).doubleValue());
        }
        if (values.containsKey("vgrow")) {
            constraints.setVgrow(Priority.valueOf(((String) values.get("vgrow")).toUpperCase()));
        }
        if (values.containsKey("valignment")) {
            constraints.setValignment(VPos.valueOf((String) values.get("valignment")));
        }
        if (values.containsKey("fillHeight")) {
            constraints.setFillHeight((Boolean) values.get("fillHeight"));
        }
        return constraints;
    }

    private static void anchor(Spec child, String key, java.util.function.Consumer<Double> setter) {
        Object value = child.props.get(key);
        if (value instanceof Number) {
            setter.accept(((Number) value).doubleValue());
        }
    }

    private static Insets insets(Object value) {
        int[] sides = (int[]) value;
        return new Insets(sides[0], sides[1], sides[2], sides[3]);
    }

    /** JavaFX Pos from the valignment and halignment props ("CENTER" and "CENTER" is Pos.CENTER). */
    private static Pos pos(Spec child) {
        String name = child.props.get("valignment") + "_" + child.props.get("halignment");
        return "CENTER_CENTER".equals(name) ? Pos.CENTER : Pos.valueOf(name);
    }

    private interface DoubleSetter {
        void set(double value);
    }

    private static void setIfPresent(Spec spec, String key, DoubleSetter setter) {
        Object value = spec.props.get(key);
        if (value instanceof Number) {
            setter.set(((Number) value).doubleValue());
        }
    }

    private static void describe(Node node, double parentX, double parentY, int depth, StringBuilder out) {
        double x = parentX + node.getLayoutX();
        double y = parentY + node.getLayoutY();
        Region region = (Region) node;
        indent(depth, out);
        out.append(fmt(x)).append(',').append(fmt(y)).append(' ')
                .append(fmt(region.getWidth())).append('x').append(fmt(region.getHeight())).append('\n');
        if (region instanceof Control) {
            return; // its skin's nodes (text, graphic) have no native counterpart
        }
        for (Node child : region.getChildrenUnmodifiable()) {
            describe(child, x, y, depth + 1, out);
        }
    }

    private static void describe(JXNativeNode node, int depth, StringBuilder out) {
        indent(depth, out);
        out.append(node.getX()).append(',').append(node.getY()).append(' ')
                .append(node.getWidth()).append('x').append(node.getHeight()).append('\n');
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
