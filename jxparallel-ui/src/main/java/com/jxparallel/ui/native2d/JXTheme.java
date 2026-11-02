package com.jxparallel.ui.native2d;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The design tokens the native renderer paints its Material-style controls with: {@code primary},
 * {@code onPrimary}, {@code surface}, {@code onSurface}, {@code outline} and {@code error}, as ARGB ints.
 * A node with the prop {@code theme} ({@code "light"} or {@code "dark"}) sets the theme of itself and
 * everything below it; elsewhere the light theme applies. JXML's own table of the same tokens
 * ({@code JxmlThemes} in jxparallel-forge, which cannot see this class) is checked against this one by
 * a test in jxparallel-examples-native.
 */
public final class JXTheme {
    private static final List<String> TOKENS =
            Collections.unmodifiableList(Arrays.asList("primary", "onPrimary", "surface", "onSurface", "outline", "error"));

    public static final JXTheme LIGHT = new JXTheme(0xFF1565C0, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF1C1B1F, 0xFF79747E, 0xFFB3261C);
    public static final JXTheme DARK = new JXTheme(0xFF90CAF9, 0xFF0D2B4A, 0xFF1C1B1F, 0xFFE6E1E5, 0xFF938F99, 0xFFF2B8B5);

    public final int primary;
    public final int onPrimary;
    public final int surface;
    public final int onSurface;
    public final int outline;
    public final int error;

    private JXTheme(int primary, int onPrimary, int surface, int onSurface, int outline, int error) {
        this.primary = primary;
        this.onPrimary = onPrimary;
        this.surface = surface;
        this.onSurface = onSurface;
        this.outline = outline;
        this.error = error;
    }

    /** The theme of this name ({@code "dark"}), else the light one. */
    public static JXTheme named(String name) {
        return "dark".equalsIgnoreCase(name) ? DARK : LIGHT;
    }

    /** The names of the tokens, in the order of the fields. */
    public static List<String> tokens() {
        return TOKENS;
    }

    /** The color of a token; throws for a name that is none of {@link #tokens()}. */
    public int token(String name) {
        switch (name) {
            case "primary":
                return primary;
            case "onPrimary":
                return onPrimary;
            case "surface":
                return surface;
            case "onSurface":
                return onSurface;
            case "outline":
                return outline;
            case "error":
                return error;
            default:
                throw new IllegalArgumentException("no token " + name + "; tokens: " + TOKENS);
        }
    }

    /** The fill of a tonal button or a selected chip: the primary color softly mixed into the surface. */
    public int tonal() {
        return mix(primary, surface, 0.82f);
    }

    /** The fill of an elevated button: the surface with a trace of the primary color. */
    public int elevated() {
        return mix(primary, surface, 0.94f);
    }

    /** {@code a * (1 - t) + b * t} per channel, opaque. */
    static int mix(int a, int b, float t) {
        int out = 0xFF000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            int c = Math.round(((a >>> shift) & 0xFF) * (1 - t) + ((b >>> shift) & 0xFF) * t);
            out |= (Math.max(0, Math.min(255, c))) << shift;
        }
        return out;
    }

    /** The color with its alpha replaced. */
    static int withAlpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0xFFFFFF);
    }
}
