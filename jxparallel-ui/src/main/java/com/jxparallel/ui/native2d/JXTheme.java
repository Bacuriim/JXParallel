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

    // ---- roles derived from the six tokens (Material 3), not tokens of JXML themselves ----------

    /** State layer alphas of Material 3: hover 8%, focus 12%, pressed 12%, dragged 16%. */
    public static final int HOVER = 0x14;
    public static final int FOCUS = 0x1F;
    public static final int PRESSED = 0x1F;
    public static final int DRAGGED = 0x29;
    /** Disabled: content at 38%, containers at 12% of the on-surface color. */
    public static final int DISABLED_CONTENT = 0x61;
    public static final int DISABLED_CONTAINER = 0x1F;

    /** True when the surface is dark: the roles that depend on the direction of elevation are mixed the other way. */
    public final boolean dark;
    public final int primaryContainer;
    public final int onPrimaryContainer;
    public final int secondaryContainer;
    public final int surfaceVariant;
    public final int onSurfaceVariant;
    public final int outlineVariant;
    public final int surfaceContainerLow;
    public final int surfaceContainer;
    public final int surfaceContainerHigh;
    public final int surfaceContainerHighest;
    public final int inverseSurface;
    public final int inverseOnSurface;
    /** Shadow and scrim colors (with their alpha). */
    public final int shadow;
    public final int scrim;

    private JXTheme(int primary, int onPrimary, int surface, int onSurface, int outline, int error) {
        this.primary = primary;
        this.onPrimary = onPrimary;
        this.surface = surface;
        this.onSurface = onSurface;
        this.outline = outline;
        this.error = error;
        this.dark = luminance(surface) < 0.5f;
        float d = dark ? 1.35f : 1f; // the primary tint of a raised surface is stronger on dark ones
        this.primaryContainer = mix(primary, surface, dark ? 0.66f : 0.80f);
        this.onPrimaryContainer = mix(primary, onSurface, dark ? 0.35f : 0.60f);
        this.secondaryContainer = mix(primary, surface, dark ? 0.74f : 0.86f);
        this.surfaceVariant = mix(outline, surface, dark ? 0.78f : 0.87f);
        this.onSurfaceVariant = mix(onSurface, outline, 0.45f);
        this.outlineVariant = mix(outline, surface, dark ? 0.62f : 0.58f);
        this.surfaceContainerLow = mix(primary, surface, 1 - 0.02f * d);
        this.surfaceContainer = mix(primary, surface, 1 - 0.04f * d);
        this.surfaceContainerHigh = mix(primary, surface, 1 - 0.06f * d);
        this.surfaceContainerHighest = mix(primary, surface, 1 - 0.08f * d);
        this.inverseSurface = mix(onSurface, surface, 0.06f);
        this.inverseOnSurface = mix(surface, onSurface, 0.04f);
        this.shadow = 0xFF000000;
        this.scrim = 0x52000000;
    }

    /** Perceived brightness of an ARGB color, 0 (black) to 1 (white). */
    static float luminance(int argb) {
        return (0.299f * ((argb >>> 16) & 0xFF) + 0.587f * ((argb >>> 8) & 0xFF) + 0.114f * (argb & 0xFF)) / 255f;
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
