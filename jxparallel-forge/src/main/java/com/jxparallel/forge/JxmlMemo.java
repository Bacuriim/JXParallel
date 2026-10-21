package com.jxparallel.forge;

import com.jxparallel.ui.JXElement;

/**
 * What a {@code <For memo="true">} built the last time, kept for the next build with the same inputs: the same
 * controller, the same {@code items} object with the same size, the same {@code from} and {@code limit}. Elements are
 * immutable, so the loop hands out the very same instances again and the renderer, which skips an element it already
 * holds, does not look below them.
 *
 * <p>Only the last inputs are kept, one entry for each loop of a file. A loop whose items are changed in place (an item
 * that is edited, as opposed to a list that is added to or replaced) must not use {@code memo}.
 */
public final class JxmlMemo {
    private static final class Entry {
        final Object controller;
        final Object items;
        final int size;
        final Integer from;
        final Integer limit;
        final JXElement[] built;

        Entry(Object controller, Object items, int size, Integer from, Integer limit, JXElement[] built) {
            this.controller = controller;
            this.items = items;
            this.size = size;
            this.from = from;
            this.limit = limit;
            this.built = built;
        }
    }

    private volatile Entry entry;

    /** The elements built for these inputs, or null when the loop has to build them. */
    public JXElement[] get(Object controller, Object items, Integer from, Integer limit) {
        Entry last = entry;
        if (last == null || last.controller != controller || last.items != items || last.size != sizeOf(items)
                || !same(last.from, from) || !same(last.limit, limit)) {
            return null;
        }
        return last.built;
    }

    /** Keeps {@code built} as what these inputs give; returns it. */
    public JXElement[] put(Object controller, Object items, Integer from, Integer limit, JXElement[] built) {
        int size = sizeOf(items);
        if (size >= 0) { // something that cannot be measured cannot be told apart from a changed list: never kept
            entry = new Entry(controller, items, size, from, limit, built);
        }
        return built;
    }

    private static int sizeOf(Object items) {
        if (items == null) {
            return 0;
        }
        if (items instanceof java.util.Collection) {
            return ((java.util.Collection<?>) items).size();
        }
        if (items.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(items);
        }
        return -1;
    }

    private static boolean same(Integer a, Integer b) {
        return a == null ? b == null : a.equals(b);
    }
}
