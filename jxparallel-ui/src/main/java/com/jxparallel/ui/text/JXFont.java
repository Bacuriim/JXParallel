package com.jxparallel.ui.text;

/**
 * What a piece of text asks of the font: a family name, a numeric weight (CSS scale, 400 regular,
 * 700 bold) and italic. Immutable. A {@code null} family is the UI font; the plain and bold UI font
 * are what the renderers always drew, so {@link #DEFAULT} and {@link #BOLD} change nothing for a
 * screen that does not ask for more. Resolved to a face by {@link JXFonts}.
 */
public final class JXFont {
    /** The UI font, regular. */
    public static final JXFont DEFAULT = new JXFont(null, 400, false);
    /** The UI font, bold. */
    public static final JXFont BOLD = new JXFont(null, 700, false);

    private final String family;
    private final int weight;
    private final boolean italic;

    private JXFont(String family, int weight, boolean italic) {
        this.family = family;
        this.weight = weight;
        this.italic = italic;
    }

    /** The UI font, bold or regular. */
    public static JXFont of(boolean bold) {
        return bold ? BOLD : DEFAULT;
    }

    /**
     * A font request. A {@code null}, empty or blank family is the UI font; the weight is clamped to 1..1000
     * (0 or less means regular).
     */
    public static JXFont of(String family, int weight, boolean italic) {
        String name = family == null || family.trim().isEmpty() ? null : family.trim();
        int w = weight <= 0 ? 400 : Math.min(1000, weight);
        if (name == null && !italic) {
            if (w == 400) {
                return DEFAULT;
            }
            if (w == 700) {
                return BOLD;
            }
        }
        return new JXFont(name, w, italic);
    }

    /** The family name, or {@code null} for the UI font. */
    public String family() {
        return family;
    }

    /** Numeric weight, 100 thin to 900 black; 400 is regular and 700 bold. */
    public int weight() {
        return weight;
    }

    public boolean italic() {
        return italic;
    }

    /** True from semibold (600) up: what a renderer that only has regular and bold draws bold. */
    public boolean isBold() {
        return weight >= 600;
    }

    /** True for the plain or bold UI font: the faces the renderers have always used. */
    boolean isLegacy() {
        return family == null && !italic && (weight == 400 || weight == 700);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof JXFont)) {
            return false;
        }
        JXFont other = (JXFont) o;
        return weight == other.weight && italic == other.italic
                && (family == null ? other.family == null : family.equalsIgnoreCase(other.family));
    }

    @Override
    public int hashCode() {
        int h = family == null ? 0 : family.toLowerCase(java.util.Locale.ROOT).hashCode();
        return (h * 31 + weight) * 2 + (italic ? 1 : 0);
    }

    @Override
    public String toString() {
        return (family == null ? "UI font" : family) + " " + weight + (italic ? " italic" : "");
    }
}
