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
