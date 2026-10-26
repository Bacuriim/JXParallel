package com.jxparallel.forge;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

/** Steps the interpreter and the generated code share, so they cannot drift apart. */
public final class JxmlRuntime {
    /** Keys of tab contents: string literals, so the generated code's keys are the same instances. */
    private static final String[] TAB_KEYS = new String[64];

    static {
        for (int i = 0; i < TAB_KEYS.length; i++) {
            TAB_KEYS[i] = ("tab" + i).intern();
        }
    }

    private JxmlRuntime() {
    }

    /**
     * What a {@code <For>} loops over: any {@link Iterable} or array (of objects or of primitives);
     * null loops over nothing. The type argument is the loop's {@code type}, which lets javac check
     * what the loop body calls on each item.
     */
    @SuppressWarnings("unchecked")
    public static <T> Iterable<T> items(Object source) {
        if (source == null) {
            return java.util.Collections.emptyList();
        }
        if (source instanceof Iterable) {
            return (Iterable<T>) source;
        }
        if (source.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(source);
            java.util.List<Object> list = new java.util.ArrayList<Object>(length);
            for (int i = 0; i < length; i++) {
                list.add(java.lang.reflect.Array.get(source, i));
            }
            return (Iterable<T>) list;
        }
        throw new IllegalArgumentException("<For> loops over an Iterable or an array, not " + source.getClass().getName());
    }

    /**
     * A window of {@link #items(Object)}: at most {@code limit} items from index {@code from} (null: no
     * start offset, no limit). A start beyond the end gives an empty window. This is how a virtual
     * list builds only the rows near the top of its viewport.
     */
    @SuppressWarnings("unchecked")
    public static <T> Iterable<T> items(Object source, Integer from, Integer limit) {
        Iterable<T> all = items(source);
        int skip = from == null ? 0 : Math.max(0, from.intValue());
        int max = limit == null ? Integer.MAX_VALUE : Math.max(0, limit.intValue());
        if (skip == 0 && max == Integer.MAX_VALUE) {
            return all;
        }
        if (all instanceof java.util.List) {
            java.util.List<T> list = (java.util.List<T>) all;
            int start = Math.min(skip, list.size());
            return list.subList(start, (int) Math.min((long) list.size(), (long) start + max));
        }
        java.util.List<T> out = new java.util.ArrayList<T>();
        int index = 0;
        for (T item : all) {
            if (index++ < skip) {
                continue;
            }
            if (out.size() >= max) {
                break;
            }
            out.add(item);
        }
        return out;
    }

    /**
     * Finishes the props of the tags that derive some from others, for the interpreter and the
     * generated code alike: the first row and scroll offset of a {@code ListView} or {@code TableView},
     * and the selected state and action of a {@code RadioButton} or {@code ToggleButton} in a group.
     */
    @SuppressWarnings("unchecked")
    public static JXProps finish(String tag, JXProps props) {
        JXProps.Builder out = JXProps.builder(props.size() + 2);
        for (int i = 0, n = props.size(); i < n; i++) { // by index: no entry objects
            out.set(props.keyAt(i), props.valueAt(i));
        }
        switch (tag) {
            case "ListView":
                virtual(props, out, "cellHeight");
                return out.build();
            case "TableView":
                virtual(props, out, "rowHeight");
                return out.build();
            case "RadioButton":
            case "ToggleButton":
                if (!props.asMap().containsKey("group")) {
                    return props;
                }
                Object group = props.get("group");
                boolean selected = props.get("value") != null && props.get("value").equals(group);
                out.set("group", null).set("value", null).set("onChange", null);
                out.set("RadioButton".equals(tag) ? "checked" : "selected", Boolean.valueOf(selected));
                final java.util.function.Consumer<Object> consumer = (java.util.function.Consumer<Object>) props.get("onChange");
                final Object value = props.get("value");
                if (consumer != null) {
                    out.set("onAction", new Runnable() {
                        @Override
                        public void run() {
                            consumer.accept(value);
                        }
                    });
                }
                return out.build();
            default:
                throw new IllegalStateException(tag);
        }
    }

    /** Props of a tree item that are only the model's, and not the row's. */
    private static final java.util.Set<String> ITEM_ONLY = new java.util.HashSet<String>(
            java.util.Arrays.asList("expanded", "values", "indent", "disclosure"));

    /**
     * A {@code Tree} or {@code TreeTable} as the renderer takes it. {@code items} are the built {@code TreeItem}s, each with
     * its own items as children (the interpreter and the generated code build them with the loops and conditions of the
     * file). The visible ones are flattened in order, an item's children right after it when it is {@code expanded}; the
     * element is a {@code list} of {@code cell} rows (a {@code Tree}) or a {@code table} of {@code tablerow}s of {@code cell}s
     * (a {@code TreeTable}), of which only the window of {@code rows} from {@code first} is given, with the {@code itemCount}
     * and {@code scrollY} of the whole, as a virtual list does. A row has {@code indent} (its depth) and a
     * {@code disclosure}: {@code expanded}, {@code collapsed} or {@code leaf} (no children).
     *
     * <p>All the items are built, as JavaFX builds its tree model; only the rows of the window reach the renderer.
     */
    public static JXElement tree(String tag, JXProps props, JXElement... items) {
        boolean table = "TreeTable".equals(tag);
        String heightProp = table ? "rowHeight" : "cellHeight";
        double height = ((Number) props.get(heightProp)).doubleValue();
        if (!(height > 0)) {
            throw new IllegalArgumentException("rowHeight must be greater than 0, not " + height);
        }
        int requested = Math.max(0, ((Number) props.get("first")).intValue());
        int limit = props.get("rows") == null ? JxmlTemplate.DEFAULT_ROWS : Math.max(0, ((Number) props.get("rows")).intValue());
        // one pass over the model that counts every visible item and keeps only the rows of the window: a tree of
        // thousands of items makes a few dozen rows, and the pass makes no object for the others
        java.util.List<JXElement> top = java.util.Arrays.asList(items);
        Window window = new Window(requested, limit);
        collect(top, 0, window);
        int total = window.position;
        int first = Math.max(0, Math.min(Math.max(0, total - 1), requested));
        if (first != requested) { // the window was asked beyond the end: the last item is the first row
            window = new Window(first, limit);
            collect(top, 0, window);
        }
        java.util.List<Object[]> visible = window.rows;
        JXProps.Builder out = JXProps.builder();
        for (java.util.Map.Entry<String, Object> entry : props.asMap().entrySet()) {
            if (!"rows".equals(entry.getKey())) {
                out.set(entry.getKey(), entry.getValue());
            }
        }
        out.set("itemCount", Integer.valueOf(total)).set("first", Integer.valueOf(first))
                .set("scrollY", Double.valueOf(first * height));
        java.util.List<JXElement> rows = new java.util.ArrayList<JXElement>(visible.size());
        for (int k = 0; k < visible.size(); k++) {
            Object[] row = visible.get(k);
            rows.add(table ? tableRow((JXElement) row[0], first + k, row) : treeCell((JXElement) row[0], first + k, row, true));
        }
        return JXElement.of(table ? "table" : "list", out.build(), rows.toArray(new JXElement[0]));
    }

    /** The rows {@code [first, first + limit)} of the visible items, and how many items there are in all. */
    private static final class Window {
        final int first;
        final long end;
        final java.util.List<Object[]> rows = new java.util.ArrayList<Object[]>();
        int position;

        Window(int first, int limit) {
            this.first = first;
            this.end = (long) first + limit;
        }
    }

    /** Counts the visible items, depth first, and adds {item, depth, hasChildren, expanded} of those in the window. */
    private static void collect(java.util.List<JXElement> items, int depth, Window window) {
        for (int i = 0, n = items.size(); i < n; i++) { // indexed: no iterator for each of thousands of lists
            JXElement item = items.get(i);
            java.util.List<JXElement> below = item.getChildren();
            boolean has = !below.isEmpty();
            boolean expanded = has && Boolean.TRUE.equals(item.getProps().get("expanded"));
            if (window.position >= window.first && window.position < window.end) {
                window.rows.add(new Object[] {item, Integer.valueOf(depth), Boolean.valueOf(has), Boolean.valueOf(expanded)});
            }
            window.position++;
            if (expanded) {
                collect(below, depth + 1, window);
            }
        }
    }

    private static String disclosure(Object[] row) {
        return !((Boolean) row[2]).booleanValue() ? "leaf" : ((Boolean) row[3]).booleanValue() ? "expanded" : "collapsed";
    }

    /** The row of a tree item: a cell with its depth and arrow, and the item's other props. */
    private static JXElement treeCell(JXElement item, int index, Object[] row, boolean list) {
        if (list && item.getProps().get("values") != null) {
            throw new IllegalArgumentException("a <TreeItem> has values only in a <TreeTable>; a <Tree> has one column, its text");
        }
        JXProps.Builder b = JXProps.builder();
        for (java.util.Map.Entry<String, Object> entry : item.getProps().asMap().entrySet()) {
            if (!ITEM_ONLY.contains(entry.getKey())) {
                b.set(entry.getKey(), entry.getValue());
            }
        }
        b.set("indent", Double.valueOf(((Integer) row[1]).intValue())).set("disclosure", disclosure(row));
        if (item.getProps().get("odd") == null) {
            b.set("odd", Boolean.valueOf(index % 2 == 1));
        }
        return JXElement.of("cell", b.build());
    }

    private static final double[] CELL_PADDING = {2, 3, 2, 3};

    /** The row of a tree item in a tree table: its text with the arrow, then one cell for each of its {@code values}. */
    private static JXElement tableRow(JXElement item, int index, Object[] row) {
        JXProps.Builder r = JXProps.builder();
        for (String name : new String[] {"key", "selected", "listFocused"}) {
            r.set(name, item.getProps().get(name));
        }
        r.set("odd", Boolean.valueOf(index % 2 == 1));
        Object action = item.getProps().get("onAction");
        Object toggle = item.getProps().get("onToggle");
        java.util.List<JXElement> cells = new java.util.ArrayList<JXElement>();
        JXProps.Builder first = JXProps.builder().set("value", item.getProps().get("value"))
                .set("indent", Double.valueOf(((Integer) row[1]).intValue())).set("disclosure", disclosure(row))
                .set("tableCell", Boolean.TRUE).set("paintBackground", Boolean.FALSE).set("padding", CELL_PADDING)
                .set("onAction", action).set("onToggle", toggle);
        cells.add(JXElement.of("cell", first.build()));
        Object values = item.getProps().get("values");
        if (values instanceof String[]) {
            for (String value : (String[]) values) {
                cells.add(JXElement.of("cell", JXProps.builder().set("value", value).set("tableCell", Boolean.TRUE)
                        .set("paintBackground", Boolean.FALSE).set("padding", CELL_PADDING).set("onAction", action).build()));
            }
        }
        return JXElement.of("tablerow", r.build(), cells.toArray(new JXElement[0]));
    }

    /** {@code first} kept within the items, and the scroll offset that puts that row at the top of the viewport. */
    private static void virtual(JXProps props, JXProps.Builder out, String heightProp) {
        Object count = props.get("itemCount");
        Object first = props.get("first");
        out.set("first", virtualFirst(count, first)).set("scrollY", virtualScroll(count, first, props.get(heightProp), heightProp));
    }

    /** The {@code first} prop of a virtual list as {@code finish} derives it: kept within the items. The live plan reads it the same way. */
    public static Integer virtualFirst(Object itemCount, Object first) {
        int count = ((Number) itemCount).intValue();
        return Integer.valueOf(Math.max(0, Math.min(Math.max(0, count - 1), ((Number) first).intValue())));
    }

    /** The {@code scrollY} prop of a virtual list as {@code finish} derives it: the offset that puts row {@code first} at the top. */
    public static Double virtualScroll(Object itemCount, Object first, Object rowHeight) {
        return virtualScroll(itemCount, first, rowHeight, "rowHeight");
    }

    private static Double virtualScroll(Object itemCount, Object first, Object rowHeight, String heightProp) {
        double height = ((Number) rowHeight).doubleValue();
        if (!(height > 0)) {
            throw new IllegalArgumentException(heightProp + " must be greater than 0, not " + height);
        }
        return Double.valueOf(virtualFirst(itemCount, first).intValue() * height);
    }

    /** The key of the content of tab {@code index}; the same instance as the literal {@code "tab<index>"}. */
    public static String tabKey(int index) {
        return index < TAB_KEYS.length ? TAB_KEYS[index] : ("tab" + index).intern();
    }

    /**
     * A tab pane as the native renderer takes it: the selected tab's content is the one child; the
     * others are built too, as JavaFX builds every tab's content, and travel in the
     * {@code deferredTabs} prop so the host can mount them ahead ({@code JXNativeNode.retainAhead}).
     */
    public static JXElement tabs(JXProps.Builder props, Integer selected, String[] titles, boolean[] closable,
                                 JXElement[] contents) {
        int count = contents.length;
        int shown = count == 0 ? 0 : Math.max(0, Math.min(count - 1, selected.intValue()));
        JXElement[] deferred = new JXElement[count == 0 ? 0 : count - 1];
        for (int i = 0, d = 0; i < count; i++) {
            if (i != shown) {
                deferred[d++] = contents[i];
            }
        }
        props.set("selected", Integer.valueOf(shown)).set("titles", titles).set("closable", closable)
                .set("deferredTabs", deferred);
        return count == 0 ? JXElement.of("tabs", props.build()) : JXElement.of("tabs", props.build(), contents[shown]);
    }
}
