package com.jxparallel.ui;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JXProps {
    private static final JXProps EMPTY = new JXProps(Collections.<String, Object>emptyMap());
    private final Map<String, Object> values;

    private JXProps(Map<String, Object> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(values));
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

    public Map<String, Object> asMap() {
        return values;
    }

    public static final class Builder {
        private final Map<String, Object> values = new LinkedHashMap<String, Object>();

        public Builder set(String name, Object value) {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Property name cannot be empty");
            }
            if (value == null) {
                values.remove(name);
            } else {
                values.put(name, value);
            }
            return this;
        }

        public JXProps build() {
            if (values.isEmpty()) {
                return EMPTY;
            }
            return new JXProps(values);
        }
    }
}
