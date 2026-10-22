package com.jxparallel.ui;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * The immutable props of an element: names and values in the order they were first set. It is its own read-only
 * {@link Map} view ({@link #asMap()} returns it), over one array of keys at even and values at odd indices; building
 * an element makes this object and that array, nothing else. Lookups compare by identity first (keys are literals),
 * then by equality, and a 32-bit mask of the key hashes answers "absent" without looking at any key: the painter and
 * the layout ask for dozens of props a node does not have.
 */
public final class JXProps extends AbstractMap<String, Object> {
    private static final JXProps EMPTY = new JXProps(new Object[0], 0);

    /** Keys at even and values at odd indices; the array may be longer than {@code 2 * count} (a builder's spare room). */
    private final Object[] entries;
    private final int count;
    /** Bit {@code hash & 31} set for each key: a key whose bit is clear is not here. */
    private final int mask;
    private Set<String> keys;
    private Set<Map.Entry<String, Object>> entrySet;

    private JXProps(Object[] entries, int count) {
        this.entries = entries;
        this.count = count;
        int bits = 0;
        for (int i = 0, end = 2 * count; i < end; i += 2) {
            bits |= 1 << entries[i].hashCode();
        }
        this.mask = bits;
    }

    public static JXProps empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder(8);
    }

    /** A builder for an element with about {@code expected} props: its array has their size, not room for sixteen. */
    public static Builder builder(int expected) {
        return new Builder(expected);
    }

    /**
     * Props from name/value pairs, {@code of("gap", 2.0, "padding", 4.0)}: no builder is made, and the argument array
     * becomes the props, so the caller must not keep it. Names are non-empty and distinct (generated code knows they
     * are); a pair whose value is null is left out, as {@code builder().set(name, null)} would.
     */
    public static JXProps of(Object... keyValues) {
        int length = keyValues.length;
        if ((length & 1) != 0) {
            throw new IllegalArgumentException("Props are name/value pairs");
        }
        int kept = length / 2;
        for (int i = 1; i < length; i += 2) {
            if (keyValues[i] == null) {
                kept--;
            }
        }
        if (kept == 0) {
            return EMPTY;
        }
        if (2 * kept != length) { // rare: some values were null, keep the others in order
            Object[] compact = new Object[2 * kept];
            int to = 0;
            for (int i = 0; i < length; i += 2) {
                if (keyValues[i + 1] != null) {
                    compact[to++] = keyValues[i];
                    compact[to++] = keyValues[i + 1];
                }
            }
            keyValues = compact;
        }
        return new JXProps(keyValues, kept);
    }

    /** How many props there are; {@link #keyAt} and {@link #valueAt} read them in order, without making an iterator. */
    @Override
    public int size() {
        return count;
    }

    @Override
    public boolean isEmpty() {
        return count == 0;
    }

    public String keyAt(int index) {
        return (String) entries[2 * index];
    }

    public Object valueAt(int index) {
        return entries[2 * index + 1];
    }

    /** As {@link #get(Object)}; kept so that code compiled against the earlier JXProps, which had only this one, still links. */
    public Object get(String name) {
        return get((Object) name);
    }

    @Override
    public Object get(Object name) {
        if (name == null || (mask & (1 << name.hashCode())) == 0) {
            return null;
        }
        int at = indexOf(entries, count, name);
        return at < 0 ? null : entries[at + 1];
    }

    @Override
    public boolean containsKey(Object name) {
        if (name == null || (mask & (1 << name.hashCode())) == 0) {
            return false;
        }
        return indexOf(entries, count, name) >= 0;
    }

    public String getString(String name) {
        Object value = get(name);
        return value == null ? null : String.valueOf(value);
    }

    public boolean getBoolean(String name, boolean fallback) {
        Object value = get(name);
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    /** A read-only view, in the order the props were first set: these props themselves. */
    public Map<String, Object> asMap() {
        return this;
    }

    private static int indexOf(Object[] entries, int count, Object key) {
        int end = 2 * count;
        for (int i = 0; i < end; i += 2) {
            if (entries[i] == key) {
                return i;
            }
        }
        if (key != null) {
            for (int i = 0; i < end; i += 2) {
                if (key.equals(entries[i])) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void forEach(BiConsumer<? super String, ? super Object> action) {
        for (int i = 0; i < 2 * count; i += 2) {
            action.accept((String) entries[i], entries[i + 1]);
        }
    }

    @Override
    public Set<String> keySet() {
        if (keys == null) {
            keys = new AbstractSet<String>() {
                @Override
                public Iterator<String> iterator() {
                    return new Cursor<String>() {
                        @Override
                        String at(int i) {
                            return (String) entries[i];
                        }
                    };
                }

                @Override
                public boolean contains(Object o) {
                    return containsKey(o);
                }

                @Override
                public int size() {
                    return count;
                }
            };
        }
        return keys;
    }

    @Override
    public Set<Map.Entry<String, Object>> entrySet() {
        if (entrySet == null) {
            entrySet = new AbstractSet<Map.Entry<String, Object>>() {
                @Override
                public Iterator<Map.Entry<String, Object>> iterator() {
                    return new Cursor<Map.Entry<String, Object>>() {
                        @Override
                        Map.Entry<String, Object> at(int i) {
                            return new SimpleImmutableEntry<String, Object>((String) entries[i], entries[i + 1]);
                        }
                    };
                }

                @Override
                public int size() {
                    return count;
                }
            };
        }
        return entrySet;
    }

    private abstract class Cursor<T> implements Iterator<T> {
        private int next;

        abstract T at(int i);

        @Override
        public boolean hasNext() {
            return next < 2 * count;
        }

        @Override
        public T next() {
            if (next >= 2 * count) {
                throw new NoSuchElementException();
            }
            T value = at(next);
            next += 2;
            return value;
        }
    }

    public static final class Builder {
        /** Keys at even and values at odd indices: an element has a few dozen props at most. */
        private Object[] entries;
        private int count;
        /** The array was handed to a built JXProps: the next change works on a copy (built props never change). */
        private boolean shared;

        Builder(int expected) {
            entries = new Object[2 * Math.max(1, expected)];
        }

        public Builder set(String name, Object value) {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Property name cannot be empty");
            }
            int at = indexOf(entries, count, name);
            if (value == null && at < 0) {
                return this;
            }
            if (shared) {
                entries = Arrays.copyOf(entries, Math.max(entries.length, 2 * count + 2));
                shared = false;
            }
            if (value == null) {
                System.arraycopy(entries, at + 2, entries, at, 2 * count - at - 2);
                count--;
                entries[2 * count] = null;
                entries[2 * count + 1] = null;
            } else if (at >= 0) {
                entries[at + 1] = value;
            } else {
                if (2 * count + 2 > entries.length) {
                    entries = Arrays.copyOf(entries, entries.length * 2);
                }
                entries[2 * count] = name;
                entries[2 * count + 1] = value;
                count++;
            }
            return this;
        }

        public JXProps build() {
            if (count == 0) {
                return EMPTY;
            }
            shared = true; // handed over, not copied: elements are built once per frame, often thousands
            return new JXProps(entries, count);
        }
    }
}
