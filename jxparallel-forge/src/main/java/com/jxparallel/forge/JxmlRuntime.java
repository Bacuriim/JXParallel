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
        JXProps.Builder out = JXProps.builder();
        for (java.util.Map.Entry<String, Object> entry : props.asMap().entrySet()) {
            out.set(entry.getKey(), entry.getValue());
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

    /** {@code first} kept within the items, and the scroll offset that puts that row at the top of the viewport. */
    private static void virtual(JXProps props, JXProps.Builder out, String heightProp) {
        int count = ((Number) props.get("itemCount")).intValue();
        double height = ((Number) props.get(heightProp)).doubleValue();
        if (!(height > 0)) {
            throw new IllegalArgumentException(heightProp + " must be greater than 0, not " + height);
        }
        int first = Math.max(0, Math.min(Math.max(0, count - 1), ((Number) props.get("first")).intValue()));
        out.set("first", Integer.valueOf(first)).set("scrollY", Double.valueOf(first * height));
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
