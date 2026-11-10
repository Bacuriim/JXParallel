package com.jxparallel.examples.orders;

/**
 * The colours the screens bind to (<code>background="${palette.card}"</code>). The window's root has a bound
 * {@code theme="${themeName}"}, which switches the controls the renderer themes itself and the six theme tokens
 * ({@code surface}, {@code primary} ...) written in {@code shell.jxml}. What the palette still gives is what a token
 * cannot: the greys of the page, the sidebar, the lines and the muted text are not among the six, and each screen is
 * its own file (built by its own controller), whose tokens resolve with that file's theme, which is none.
 */
public final class Palette {
    public static final String LIGHT = "light";
    public static final String DARK = "dark";

    private static final Palette LIGHT_PALETTE = new Palette(LIGHT, 0xFFF4F6FA, 0xFFFFFFFF, 0xFFE3E7EF, 0xFF1C1B1F, 0xFF5F6670,
            0xFF1565C0, 0xFFFFFFFF, 0xFFB3261C, 0xFF2E7D32, 0xFFE8EEF8, 0xFFDCE6F7);
    private static final Palette DARK_PALETTE = new Palette(DARK, 0xFF121317, 0xFF1C1B1F, 0xFF3A3D45, 0xFFE6E1E5, 0xFFA7ACB5,
            0xFF90CAF9, 0xFF0D2B4A, 0xFFF2B8B5, 0xFF81C784, 0xFF23252B, 0xFF2C3A52);

    private final String name;
    private final int background;
    private final int card;
    private final int line;
    private final int text;
    private final int muted;
    private final int primary;
    private final int onPrimary;
    private final int error;
    private final int success;
    private final int sidebar;
    private final int selection;

    private Palette(String name, int background, int card, int line, int text, int muted, int primary, int onPrimary,
                    int error, int success, int sidebar, int selection) {
        this.name = name;
        this.background = background;
        this.card = card;
        this.line = line;
        this.text = text;
        this.muted = muted;
        this.primary = primary;
        this.onPrimary = onPrimary;
        this.error = error;
        this.success = success;
        this.sidebar = sidebar;
        this.selection = selection;
    }

    /** {@code "dark"} gives the dark palette, anything else the light one. */
    public static Palette of(String theme) {
        return DARK.equals(theme) ? DARK_PALETTE : LIGHT_PALETTE;
    }

    /** The value of the root's {@code theme} attribute. */
    public String getName() { return name; }
    public int getBackground() { return background; }
    public int getCard() { return card; }
    public int getLine() { return line; }
    public int getText() { return text; }
    public int getMuted() { return muted; }
    public int getPrimary() { return primary; }
    public int getOnPrimary() { return onPrimary; }
    public int getError() { return error; }
    public int getSuccess() { return success; }
    public int getSidebar() { return sidebar; }
    public int getSelection() { return selection; }

    /** Colour of series {@code index} in the bar visuals. */
    public int series(int index) {
        int[] light = {0xFF1976D2, 0xFF2E9E5B, 0xFF7E57C2, 0xFFD64545};
        int[] dark = {0xFF64B5F6, 0xFF81C784, 0xFFB39DDB, 0xFFE57373};
        return (DARK.equals(name) ? dark : light)[Math.floorMod(index, 4)];
    }
}
