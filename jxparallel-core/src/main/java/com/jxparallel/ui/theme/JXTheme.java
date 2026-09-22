package com.jxparallel.ui.theme;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JXTheme {
    private final Map<String, String> colors;
    private final Map<String, Double> spacing;
    private final String fontFamily;
    private final double fontSize;

    private JXTheme(Builder builder) {
        colors = Collections.unmodifiableMap(new LinkedHashMap<String, String>(builder.colors));
        spacing = Collections.unmodifiableMap(new LinkedHashMap<String, Double>(builder.spacing));
        fontFamily = builder.fontFamily;
        fontSize = builder.fontSize;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static JXTheme clean() {
        return builder()
                .color("background", "#ffffff")
                .color("surface", "#f8fafc")
                .color("text", "#0f172a")
                .color("muted", "#64748b")
                .color("primary", "#2563eb")
                .color("danger", "#dc2626")
                .spacing("xs", 4)
                .spacing("sm", 8)
                .spacing("md", 16)
                .spacing("lg", 24)
                .fontFamily("Inter, system-ui, sans-serif")
                .fontSize(14)
                .build();
    }

    public String color(String name) {
        return colors.get(name);
    }

    public Double spacing(String name) {
        return spacing.get(name);
    }

    public String getFontFamily() {
        return fontFamily;
    }

    public double getFontSize() {
        return fontSize;
    }

    public static final class Builder {
        private final Map<String, String> colors = new LinkedHashMap<String, String>();
        private final Map<String, Double> spacing = new LinkedHashMap<String, Double>();
        private String fontFamily = "system-ui, sans-serif";
        private double fontSize = 14;

        public Builder color(String name, String value) {
            colors.put(name, value);
            return this;
        }

        public Builder spacing(String name, double value) {
            spacing.put(name, Math.max(0, value));
            return this;
        }

        public Builder fontFamily(String value) {
            fontFamily = value == null ? "system-ui, sans-serif" : value;
            return this;
        }

        public Builder fontSize(double value) {
            fontSize = Math.max(1, value);
            return this;
        }

        public JXTheme build() {
            return new JXTheme(this);
        }
    }
}
