package com.jxparallel.ui.style;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JXStyle {
    private final Map<String, Object> values;

    private JXStyle(Map<String, Object> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(values));
    }

    public static Builder builder() { return new Builder(); }
    public Object get(String name) { return values.get(name); }
    /** Colors are stored as ARGB ints (0xAARRGGBB), the format the renderer consumes. */
    public int color(String name, int fallback) {
        Object value = get(name);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    public JXStyle merge(JXStyle override) {
        if (override == null) return this;
        Builder builder = builder();
        for (Map.Entry<String, Object> entry : values.entrySet()) builder.set(entry.getKey(), entry.getValue());
        for (Map.Entry<String, Object> entry : override.values.entrySet()) builder.set(entry.getKey(), entry.getValue());
        return builder.build();
    }

    public static final class Builder {
        private final Map<String, Object> values = new LinkedHashMap<String, Object>();
        public Builder set(String name, Object value) {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Style name cannot be empty");
            }
            if (value != null) values.put(name, value);
            return this;
        }
        public JXStyle build() { return new JXStyle(values); }
    }
}
