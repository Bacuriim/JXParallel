package com.jxparallel.ui.native2d;

/**
 * The colors of the classic (Modena) controls: text fields, combo boxes, spinners, check boxes, sliders, progress bars,
 * scroll bars, lists, tables, tabs, titled panes and the popups of the pickers. {@link #LIGHT} is Modena itself, the
 * values {@link JXPaint} always painted; {@link #DARK} is derived from the {@link JXTheme#DARK} tokens (surface for the
 * bodies, onSurface for text, outline for the borders, primary for focus and selection), so a node with
 * {@code theme="dark"} paints the same controls in a dark palette. The Material controls take their colors
 * straight from {@link JXTheme}.
 */
final class JXClassic {
    /** Modena. */
    static final JXClassic LIGHT = light();
    /** The classic controls on the dark theme. */
    static final JXClassic DARK = dark(JXTheme.DARK);

    int text;
    int accent;
    int focus;
    int focusRing;
    int outerBorder;
    int boxBorder;
    int highlight;
    int background;
    int prompt;
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
    /** The two ends of the gradient of a raised control (a button's rim, a header, a thumb's edge). */
    int ctlTop;
    int ctlBottom;
    int ctlBottomHover;
    /** The inner gradient of a button, resting and under the pointer. */
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
    /** The rim of a field or track, and the shade at the top of its body. */
    int fieldEdgeA;
    int fieldEdgeB;
    int sheenA;
    int trackA;
    int barA;
    int barB;
    int sliderTrack;
    int thumbEdge;
    int thumbBody;
    int thumbHover;
    int thumbPressed;
    int sepLine;
    int titledSheen;
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

    /** The palette of a theme: Modena for the light one. */
    static JXClassic of(JXTheme theme) {
        return theme == JXTheme.DARK ? DARK : LIGHT;
    }

    private static JXClassic light() {
        JXClassic c = new JXClassic();
        c.text = JXPaint.TEXT;
        c.accent = JXPaint.ACCENT;
        c.focus = JXPaint.FOCUS;
        c.focusRing = 0x66039ED3;
        c.outerBorder = JXPaint.OUTER_BORDER;
        c.boxBorder = JXPaint.BOX_BORDER;
        c.highlight = JXPaint.HIGHLIGHT;
        c.background = JXPaint.BACKGROUND;
        c.prompt = JXPaint.PROMPT;
        c.mark = JXPaint.MARK;
        c.arrow = JXPaint.ARROW;
        c.selection = JXPaint.SELECTION;
        c.selectionUnfocused = JXPaint.SELECTION_UNFOCUSED;
        c.selectionText = 0xFFFFFFFF;
        c.oddRow = JXPaint.ODD_ROW;
        c.rowLine = 0xFFEDEDED;
        c.base = 0xFFFFFFFF;
        c.hoverRow = 0xFFEAF4FA;
        c.ctlTop = 0xFFFDFDFD;
        c.ctlBottom = 0xFFE2E2E2;
        c.ctlBottomHover = 0xFFEAEAEA;
        c.bodyA = 0xFFEFEFEF;
        c.bodyB = 0xFFD9D9D9;
        c.bodyHoverA = 0xFFF8F8F8;
        c.bodyHoverB = 0xFFE3E3E3;
        c.pressedOuterA = 0xFFCACACA;
        c.pressedOuterB = 0xFFBCBCBC;
        c.pressedInnerA = 0xFFC6C6C6;
        c.pressedInnerB = 0xFFB5B5B5;
        c.defOuterA = 0xFFB9D7F1;
        c.defOuterB = 0xFF8EC4E8;
        c.defInnerA = 0xFFA3CDEE;
        c.defInnerB = 0xFF7FB8E4;
        c.defHoverA = 0xFFB7D8F4;
        c.defHoverB = 0xFF94C6EB;
        c.fieldEdgeA = 0xFFBBBBBB;
        c.fieldEdgeB = 0xFFCFCFCF;
        c.sheenA = 0xFFE8E8E8;
        c.trackA = 0xFFEDEDED;
        c.barA = 0xFF008CBB;
        c.barB = 0xFF0097C9;
        c.sliderTrack = 0xFFF0F0F0;
        c.thumbEdge = 0xFFA0A0A0;
        c.thumbBody = 0xFFE9E9E9;
        c.thumbHover = 0xFFF4F4F4;
        c.thumbPressed = 0xFFD0D0D0;
        c.sepLine = 0xFFCFCFCF;
        c.titledSheen = 0xFFE5E5E5;
        c.barTrack = 0xFFD9D9D9;
        c.barLane = 0xFFEBEBEB;
        c.barArrow = 0xFF828282;
        c.barCorner = 0xFFEAEAEA;
        c.tabA = 0xFFCFCFCF;
        c.tabB = 0xFFDEDEDE;
        c.dim = 0xFFAAAAAA;
        c.weekday = 0xFF7A7A7A;
        c.calHover = JXOverlayPaint.HOVER;
        c.visited = 0xFF7A3D9E;
        return c;
    }

    /** Surfaces lifted toward onSurface by a few percent, borders from outline, focus and selection from primary. */
    private static JXClassic dark(JXTheme t) {
        int black = 0xFF000000;
        int s = t.surface;
        int o = t.onSurface;
        int l = t.outline;
        int p = t.primary;
        JXClassic c = new JXClassic();
        c.text = o;
        c.accent = p;
        c.focus = p;
        c.focusRing = JXTheme.withAlpha(p, 0x66);
        c.outerBorder = JXTheme.mix(l, s, 0.35f);
        c.boxBorder = JXTheme.mix(l, s, 0.6f);
        c.highlight = JXTheme.withAlpha(o, 0x10);
        c.background = JXTheme.mix(s, o, 0.03f);
        c.prompt = JXTheme.mix(o, s, 0.5f);
        c.mark = o;
        c.arrow = JXTheme.mix(o, s, 0.2f);
        c.selection = JXTheme.mix(p, s, 0.6f);
        c.selectionUnfocused = JXTheme.mix(s, o, 0.22f);
        c.selectionText = 0xFFFFFFFF;
        c.base = JXTheme.mix(s, black, 0.2f);
        c.oddRow = JXTheme.mix(c.base, o, 0.05f);
        c.rowLine = JXTheme.mix(c.base, o, 0.1f);
        c.hoverRow = JXTheme.mix(c.base, p, 0.2f);
        c.ctlTop = JXTheme.mix(s, o, 0.16f);
        c.ctlBottom = JXTheme.mix(s, o, 0.08f);
        c.ctlBottomHover = JXTheme.mix(s, o, 0.13f);
        c.bodyA = JXTheme.mix(s, o, 0.10f);
        c.bodyB = JXTheme.mix(s, o, 0.05f);
        c.bodyHoverA = JXTheme.mix(s, o, 0.14f);
        c.bodyHoverB = JXTheme.mix(s, o, 0.08f);
        c.pressedOuterA = JXTheme.mix(s, black, 0.2f);
        c.pressedOuterB = JXTheme.mix(s, black, 0.35f);
        c.pressedInnerA = JXTheme.mix(s, black, 0.28f);
        c.pressedInnerB = JXTheme.mix(s, black, 0.4f);
        c.defOuterA = JXTheme.mix(p, s, 0.62f);
        c.defOuterB = JXTheme.mix(p, s, 0.76f);
        c.defInnerA = JXTheme.mix(p, s, 0.67f);
        c.defInnerB = JXTheme.mix(p, s, 0.8f);
        c.defHoverA = JXTheme.mix(p, s, 0.58f);
        c.defHoverB = JXTheme.mix(p, s, 0.72f);
        c.fieldEdgeA = JXTheme.mix(l, s, 0.3f);
        c.fieldEdgeB = JXTheme.mix(l, s, 0.5f);
        c.sheenA = JXTheme.mix(c.base, black, 0.3f);
        c.trackA = c.sheenA;
        c.barA = JXTheme.mix(p, black, 0.15f);
        c.barB = p;
        c.sliderTrack = JXTheme.mix(c.base, o, 0.12f);
        c.thumbEdge = JXTheme.mix(l, s, 0.2f);
        c.thumbBody = JXTheme.mix(s, o, 0.22f);
        c.thumbHover = JXTheme.mix(s, o, 0.3f);
        c.thumbPressed = JXTheme.mix(s, o, 0.12f);
        c.sepLine = JXTheme.mix(s, o, 0.2f);
        c.titledSheen = JXTheme.mix(c.background, black, 0.25f);
        c.barTrack = JXTheme.mix(s, o, 0.1f);
        c.barLane = JXTheme.mix(s, black, 0.15f);
        c.barArrow = JXTheme.mix(o, s, 0.3f);
        c.barCorner = JXTheme.mix(s, o, 0.08f);
        c.tabA = JXTheme.mix(s, o, 0.10f);
        c.tabB = JXTheme.mix(s, o, 0.06f);
        c.dim = JXTheme.mix(o, s, 0.55f);
        c.weekday = JXTheme.mix(o, s, 0.35f);
        c.calHover = JXTheme.mix(c.base, p, 0.3f);
        c.visited = 0xFFCE93D8;
        return c;
    }
}
