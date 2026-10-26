package com.jxparallel.forge;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The design tokens of JXML ({@code primary}, {@code onPrimary}, {@code surface}, {@code onSurface},
 * {@code outline}, {@code error}) in the two built-in themes, and the icon names of the {@code Icon} tag.
 * The renderer paints with the same tokens ({@code JXTheme}, {@code JXIcons} in jxparallel-ui), which this
 * module cannot see; a test in jxparallel-examples-native checks that the two tables are equal.
 */
public final class JxmlThemes {
    /** The tokens, in a fixed order. */
    public static final List<String> TOKENS =
            Collections.unmodifiableList(Arrays.asList("primary", "onPrimary", "surface", "onSurface", "outline", "error"));

    /** The themes a file can choose with {@code theme="..."} on its root. */
    public static final List<String> THEMES = Collections.unmodifiableList(Arrays.asList("light", "dark"));

    /** The names {@code Icon}, {@code IconButton}, {@code Fab} and {@code Chip} accept. */
    public static final List<String> ICONS = Collections.unmodifiableList(Arrays.asList(
            "menu", "close", "add", "remove", "check", "search", "arrow-back", "arrow-forward", "arrow-up",
            "arrow-down", "chevron-left", "chevron-right", "chevron-up", "chevron-down", "more-vert", "more-horiz",
            "edit", "delete", "settings", "home", "info", "warning", "error", "star", "favorite", "refresh",
            "person", "mail", "calendar", "visibility", "lock"));

    private static final Map<String, int[]> COLORS = new LinkedHashMap<String, int[]>();

    static {
        COLORS.put("light", new int[] {0xFF1565C0, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF1C1B1F, 0xFF79747E, 0xFFB3261C});
        COLORS.put("dark", new int[] {0xFF90CAF9, 0xFF0D2B4A, 0xFF1C1B1F, 0xFFE6E1E5, 0xFF938F99, 0xFFF2B8B5});
    }

    private JxmlThemes() {
    }

    public static boolean isToken(String text) {
        return TOKENS.contains(text.trim());
    }

    /** The ARGB color of a token in a theme ({@code null} means the light theme). */
    public static int color(String theme, String token) {
        int[] colors = COLORS.get(theme == null ? "light" : theme);
        int at = TOKENS.indexOf(token.trim());
        if (colors == null || at < 0) {
            throw new IllegalArgumentException("no token " + token + " in theme " + theme);
        }
        return colors[at];
    }
}
