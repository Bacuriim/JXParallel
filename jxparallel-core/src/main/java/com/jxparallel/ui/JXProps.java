package com.jxparallel.ui;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BiConsumer;

public final class JXProps {
    private static final JXProps EMPTY = new JXProps(new Flat(new Object[0], 0));
    private final Flat values;

    private JXProps(Flat values) {
        this.values = values;
    }

    public static JXProps empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Object get(String name) {
        return values.get(name);
    }

    public String getString(String name) {
        Object value = get(name);
        return value == null ? null : String.valueOf(value);
    }

    public boolean getBoolean(String name, boolean fallback) {
        Object value = get(name);
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    /** A read-only view, in the order the props were first set. */
    public Map<String, Object> asMap() {
        return values;
    }

    public static final class Builder {
        /** Keys at even and values at odd indices: an element has a few dozen props at most. */
        private Object[] entries = new Object[16];
        private int count;
        /** The array was handed to a built JXProps: the next change works on a copy (built props never change). */
        private boolean shared;

        public Builder set(String name, Object value) {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Property name cannot be empty");
            }
            int at = Flat.indexOf(entries, count, name);
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
            return new JXProps(new Flat(entries, count));
        }
    }

    /**
     * An immutable map over the builder's array. Lookups compare by identity first (keys are
     * literals), then by equality; no entry objects are made, and iterating the keys makes none.
     */
    static final class Flat extends AbstractMap<String, Object> {
        private final Object[] entries;
        private final int count;
        private Set<String> keys;
        private Set<Map.Entry<String, Object>> entrySet;

        Flat(Object[] entries, int count) {
            this.entries = entries;
            this.count = count;
        }

        static int indexOf(Object[] entries, int count, Object key) {
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
        public Object get(Object key) {
            int at = indexOf(entries, count, key);
            return at < 0 ? null : entries[at + 1];
        }

        @Override
        public boolean containsKey(Object key) {
            return indexOf(entries, count, key) >= 0;
        }

        @Override
        public int size() {
            return count;
        }

        @Override
        public boolean isEmpty() {
            return count == 0;
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
    }
}
