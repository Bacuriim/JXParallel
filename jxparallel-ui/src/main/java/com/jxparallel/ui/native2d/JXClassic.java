package com.jxparallel.ui.native2d;

/**
 * The colors of the controls that keep the JavaFX sizes: text fields, combo boxes, spinners, check boxes, sliders,
 * progress bars, scroll bars, lists, tables, tabs, titled panes and the popups of the pickers. Both palettes are
 * derived from the Material tokens of their {@link JXTheme} (surface for the bodies, onSurface for text, outline for
 * the borders a control has, a lighter outline for the frames around content, primary for focus, marks and progress,
 * the tonal fill for selection), so these controls are flat and match the Material ones; only their geometry is still
 * JavaFX's. The fields kept from the Modena palette (the two ends of a gradient, say) hold one flat color each.
 */
final class JXClassic {
    /** The controls on the light theme. */
    static final JXClassic LIGHT = derive(JXTheme.LIGHT);
    /** The controls on the dark theme. */
    static final JXClassic DARK = derive(JXTheme.DARK);

    int text;
    int accent;
    int focus;
    int focusRing;
    /** The border of a control: a field, a button, a check box. */
    int outerBorder;
    /** The frame around content: a list, a table, a scroll pane, a popup. */
    int boxBorder;
    int highlight;
    int background;
    int prompt;
    /** The check mark of a check box, on the primary color. */
    int mark;
    int arrow;
    int selection;
    int selectionUnfocused;
    int selectionText;
    int oddRow;
    int rowLine;
    /** The body of a field, list, cell or popup. */
    int base;
    int hoverRow;
    /** The face of a header (a table's, a titled pane's), resting and under the pointer. */
    int ctlTop;
    int ctlBottom;
    int ctlBottomHover;
    /** The face of a button, resting and under the pointer. */
    int bodyA;
    int bodyB;
    int bodyHoverA;
    int bodyHoverB;
    int pressedOuterA;
    int pressedOuterB;
    int pressedInnerA;
    int pressedInnerB;
    int defOuterA;
    int defOuterB;
    int defInnerA;
    int defInnerB;
    int defHoverA;
    int defHoverB;
    /** The rim of a field, and the body of a track. */
    int fieldEdgeA;
    int fieldEdgeB;
    int sheenA;
    int trackA;
    /** The bar of a progress bar. */
    int barA;
    int barB;
    /** The inactive part of a slider's track (and of a progress bar's). */
    int sliderTrack;
    int thumbEdge;
    int thumbBody;
    int thumbHover;
    int thumbPressed;
    int sepLine;
    int titledSheen;
    /** Scroll bars: the lane, the arrows and the corner between two bars; the thumb is {@link #thumbBody}. */
    int barTrack;
    int barLane;
    int barArrow;
    int barCorner;
    int tabA;
    int tabB;
    /** Calendar: days of the other months, weekday names, the day under the pointer. */
    int dim;
    int weekday;
    int calHover;
    int visited;

    private JXClassic() {
    }

    /** The palette of a theme. */
    static JXClassic of(JXTheme theme) {
        return theme == JXTheme.DARK ? DARK : LIGHT;
    }

    private static JXClassic derive(JXTheme t) {
        int s = t.surface;
        int o = t.onSurface;
        int l = t.outline;
        int p = t.primary;
        boolean dark = t == JXTheme.DARK;
        JXClassic c = new JXClassic();
        c.text = o;
        c.accent = p;
        c.focus = p;
        c.focusRing = JXTheme.withAlpha(p, 0x33);
        c.outerBorder = l;
        c.boxBorder = JXTheme.mix(l, s, 0.55f); // outline variant
        c.highlight = 0x00FFFFFF; // no bevels
        c.base = s;
        c.background = JXTheme.mix(s, o, dark ? 0.04f : 0.025f); // surface container, lowest step
        c.prompt = JXTheme.mix(o, s, 0.45f);
        c.mark = t.onPrimary;
        c.arrow = JXTheme.mix(o, s, 0.25f); // on surface variant
        c.selection = t.tonal();
        c.selectionUnfocused = JXTheme.mix(s, o, 0.1f);
        c.selectionText = o;
        c.oddRow = s; // no stripes
        c.rowLine = JXTheme.mix(l, s, 0.8f);
        c.hoverRow = JXTheme.mix(s, o, 0.06f);
        int header = JXTheme.mix(s, o, dark ? 0.08f : 0.05f); // surface container
        int headerHover = JXTheme.mix(s, o, dark ? 0.12f : 0.09f);
        c.ctlTop = header;
        c.ctlBottom = header;
        c.ctlBottomHover = headerHover;
        c.bodyA = s;
        c.bodyB = s;
        c.bodyHoverA = JXTheme.mix(s, o, 0.06f);
        c.bodyHoverB = c.bodyHoverA;
        c.pressedOuterA = JXTheme.mix(s, o, 0.12f);
        c.pressedOuterB = c.pressedOuterA;
        c.pressedInnerA = c.pressedOuterA;
        c.pressedInnerB = c.pressedOuterA;
        c.defOuterA = t.tonal();
        c.defOuterB = c.defOuterA;
        c.defInnerA = c.defOuterA;
        c.defInnerB = c.defOuterA;
        c.defHoverA = JXTheme.mix(c.defOuterA, o, 0.06f);
        c.defHoverB = c.defHoverA;
        c.fieldEdgeA = l;
        c.fieldEdgeB = l;
        c.sheenA = s;
        c.trackA = s;
        c.barA = p;
        c.barB = p;
        c.sliderTrack = JXTheme.mix(p, s, dark ? 0.7f : 0.78f);
        c.thumbEdge = p;
        c.thumbBody = JXTheme.mix(o, s, dark ? 0.55f : 0.6f);
        c.thumbHover = JXTheme.mix(o, s, dark ? 0.45f : 0.5f);
        c.thumbPressed = JXTheme.mix(o, s, dark ? 0.35f : 0.4f);
        c.sepLine = c.boxBorder;
        c.titledSheen = header;
        c.barTrack = c.background;
        c.barLane = c.background;
        c.barArrow = JXTheme.mix(o, s, 0.5f);
        c.barCorner = c.background;
        c.tabA = s;
        c.tabB = s;
        c.dim = JXTheme.mix(o, s, 0.6f);
        c.weekday = JXTheme.mix(o, s, 0.35f);
        c.calHover = JXTheme.mix(s, o, 0.08f);
        c.visited = dark ? 0xFFCE93D8 : 0xFF7A3D9E;
        return c;
    }
}
