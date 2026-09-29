package com.jxparallel.ui.native2d;

import java.util.Arrays;
import java.util.List;

import com.jxparallel.ui.text.JXTextEngine;

/**
 * Sizes, layout and hit geometry of composite controls, after their JavaFX 21 / Modena skins.
 * Every method takes a laid-out node and reads only its props, children and bounds, so the layer
 * above (event handling) asks the same questions the painter does.
 *
 * <ul>
 * <li>{@code scroll} (ScrollPane): one content child; {@code hvalue}, {@code vvalue} (0..1),
 * {@code fitToWidth}, {@code fitToHeight}, {@code hbarPolicy}, {@code vbarPolicy}
 * ({@code AS_NEEDED}, {@code ALWAYS}, {@code NEVER}). 1 px border, 13 px scroll bars.</li>
 * <li>{@code list} (ListView, virtual): children are the cells of items {@code first},
 * {@code first + 1}...; {@code itemCount}, {@code scrollY} (px), {@code cellHeight}.</li>
 * <li>{@code table} (TableView, virtual): children are {@code tablerow}s of {@code cell}s;
 * {@code columns} (titles), {@code columnWidths} (negative = fit to content), {@code constrained},
 * {@code itemCount}, {@code first}, {@code scrollX}, {@code scrollY}, {@code rowHeight}.</li>
 * <li>{@code tabs} (TabPane): {@code titles}, {@code selected}, {@code closingPolicy},
 * {@code closable} (boolean[]); one child, the selected tab's content.</li>
 * <li>{@code pagination}: {@code pageCount}, {@code current}, {@code maxPageIndicatorCount}; one child, the page.</li>
 * <li>{@code buttonbar}, {@code textflow}, {@code cell} (a labeled row: {@code value} after an
 * optional graphic child), {@code input}/{@code password} with side children ({@code side}: left or right).</li>
 * </ul>
 */
public final class JXControlLayout {
    public static final int SCROLL_BAR = 13;
    /** Arrow buttons at both ends of a scroll bar track. */
    static final int BAR_BUTTON = 11;
    static final int MIN_THUMB = 12;
    public static final int TAB_HEADER = 29;
    static final int TAB_TOP = 5;
    static final int TAB_LEFT = 5;
    static final int TAB_PAD = 6;
    static final int TAB_CLOSE = 17;
    public static final int TABLE_HEADER = 24;
    static final int ROW_HEIGHT = 24;
    static final int CELL_HEIGHT = 23;
    public static final int SPINNER_BUTTON = 24;
    public static final int DATE_BUTTON = 25;
    static final int PAGE_NAV = 44;
    static final int PAGE_BUTTON = 20;
    static final int PAGE_ARROW = 26;
    static final int BUTTON_BAR_MIN = 75;
    static final int BUTTON_BAR_GAP = 10;
    static final double GOLDEN = 0.618033987;
    private static final double MAX = JXBoxLayout.MAX;

    private JXControlLayout() {
    }

    static boolean handles(String type) {
        switch (type) {
            case "button":
            case "toggle":
            case "scroll":
            case "list":
            case "table":
            case "tablerow":
            case "tabs":
            case "pagination":
            case "buttonbar":
            case "textflow":
            case "cell":
            case "input":
            case "password":
            case "spinner":
            case "datepicker":
            case "radio":
            case "hyperlink":
            case "tooltip":
            case "image":
            case "indicator":
            case "calendar":
                return true;
            default:
                return false;
        }
    }

    // ---- sizes -------------------------------------------------------------------------------

    static double[] sizes(JXNativeNode node) {
        JXTextEngine text = JXTextEngine.get(JXPaint.bold(node));
        float size = JXPaint.fontSize(node);
        double line = text.lineHeight(size);
        String type = node.getType();
        switch (type) {
            case "button":
            case "toggle": {
                // Labeled with an optional graphic left of the text (gap 4), inside the button padding
                float[] pad = JXPaint.insetsOr(node, JXNativeNode.PAD_Y, JXNativeNode.PAD_X);
                String label = JXPaint.text(node, "label");
                JXNativeNode graphic = first(node);
                double tw = label.isEmpty() ? 0 : text.width(label, size);
                double gw = graphic == null ? 0 : JXBoxLayout.ceil(graphic.prefWidth());
                double gh = graphic == null ? 0 : JXBoxLayout.ceil(graphic.prefHeight());
                double gap = graphic != null && !label.isEmpty() ? 4 : 0;
                double h = Math.max(label.isEmpty() && graphic != null ? 0 : line, gh) + pad[0] + pad[2];
                return labeled(node, tw, gw + gap + pad[1] + pad[3], h);
            }
            case "radio": {
                double w = text.width(JXPaint.text(node, "label"), size);
                return labeled(node, w, JXNativeNode.CHECK_BOX + JXNativeNode.CHECK_GAP, Math.max(JXNativeNode.CHECK_BOX, line));
            }
            case "hyperlink": {
                double w = text.width(JXPaint.text(node, "label"), size);
                float[] pad = JXPaint.insetsOr(node, 3, 4);
                return labeled(node, w, pad[1] + pad[3], line + pad[0] + pad[2]);
            }
            case "tooltip": {
                // Modena .tooltip: padding 0.333em 0.75em around one line
                double w = text.width(JXPaint.text(node, "label"), size);
                float[] pad = JXPaint.insetsOr(node, 4, 9);
                return labeled(node, w, pad[1] + pad[3], line + pad[0] + pad[2]);
            }
            case "input":
            case "password": {
                float[] pad = JXPaint.insetsOr(node, JXNativeNode.PAD_Y, JXNativeNode.FIELD_PAD_X);
                double[] sides = sideSizes(node);
                double h = Math.max(line + pad[0] + pad[2], sides[1]);
                double columns = JXPaint.number(node, "prefColumnCount", JXNativeNode.FIELD_COLUMNS);
                double extra = pad[1] + pad[3] + sides[0];
                return new double[] {extra, columns * text.width("W", size) + extra, MAX, h, h, h};
            }
            case "spinner": {
                double h = line + 2 * JXNativeNode.PAD_Y;
                double w = JXPaint.number(node, "prefColumnCount", JXNativeNode.FIELD_COLUMNS) * text.width("W", size)
                        + 2 * JXNativeNode.FIELD_PAD_X;
                return new double[] {2 * JXNativeNode.FIELD_PAD_X, w, w, h, h, h};
            }
            case "datepicker": {
                double h = line + 2 * JXNativeNode.PAD_Y;
                double w = JXNativeNode.FIELD_COLUMNS * text.width("W", size) + DATE_BUTTON;
                return new double[] {50, w, w, h, h, h};
            }
            case "image": {
                double[] wh = imageSize(node);
                return new double[] {wh[0], wh[0], wh[0], wh[1], wh[1], wh[1]};
            }
            case "indicator": {
                boolean determinate = JXPaint.number(node, "progress", -1) >= 0;
                double w = determinate ? 49 : 53;
                double h = determinate ? 40 : 53;
                return new double[] {0, w, w, 0, h, h};
            }
            case "calendar":
                return new double[] {JXCalendar.WIDTH, JXCalendar.WIDTH, JXCalendar.WIDTH, JXCalendar.HEIGHT, JXCalendar.HEIGHT, JXCalendar.HEIGHT};
            case "cell":
                return cellSizes(node, text, size, line);
            case "tablerow": {
                float[] widths = node.getParentWidths();
                double w = 0;
                for (float c : widths) {
                    w += c;
                }
                double h = rowHeight(node);
                return new double[] {w, w, MAX, h, h, h};
            }
            case "textflow": {
                double w = 0;
                double h = line;
                for (JXNativeNode run : node.getChildren()) {
                    w += run.prefWidth();
                    h = Math.max(h, run.prefHeight());
                }
                return new double[] {0, w, MAX, h, h, MAX};
            }
            case "buttonbar": {
                double w = 0;
                double h = 0;
                List<JXNativeNode> buttons = node.getChildren();
                for (JXNativeNode b : buttons) {
                    w += Math.max(BUTTON_BAR_MIN, JXBoxLayout.ceil(b.prefWidth()));
                    h = Math.max(h, b.prefHeight());
                }
                w += Math.max(0, buttons.size() - 1) * BUTTON_BAR_GAP + (buttons.isEmpty() ? 0 : 2 * BUTTON_BAR_GAP);
                return new double[] {w, w, MAX, h, h, MAX};
            }
            case "scroll": {
                JXNativeNode content = first(node);
                double prefW = content == null ? 0 : JXBoxLayout.prefAreaWidth(content);
                double prefH = content == null ? 0 : JXBoxLayout.prefAreaHeight(content);
                return new double[] {36, prefW + 2, MAX, 36, prefH + 2, MAX};
            }
            case "list":
                return new double[] {2, 400 * GOLDEN, MAX, 2, 400, MAX};
            case "table": {
                double sum = 2;
                double[] explicit = doubles(node.getProperty("columnWidths"));
                int columns = JXPaint.strings(node.getProperty("columns")).length;
                for (int i = 0; i < columns; i++) {
                    sum += i < explicit.length && explicit[i] >= 0 ? explicit[i] : 80;
                }
                return new double[] {12, Math.max(sum, 400 * GOLDEN), MAX, 26, 400, MAX};
            }
            case "tabs": {
                JXNativeNode content = first(node);
                float[] tabs = tabPositions(node);
                double headers = tabs[tabs.length - 1] + TAB_LEFT + 15;
                double w = Math.max(headers, content == null ? 0 : JXBoxLayout.prefAreaWidth(content));
                double h = TAB_HEADER + (content == null ? 0 : JXBoxLayout.prefAreaHeight(content));
                return new double[] {0, w, MAX, 0, h, MAX};
            }
            case "pagination": {
                JXNativeNode content = first(node);
                float[][] buttons = pageButtonsRelative(node, 0);
                double nav = buttons.length == 0 ? 0 : buttons[buttons.length - 1][0] + buttons[buttons.length - 1][2] - buttons[0][0];
                double w = Math.max(nav, content == null ? 0 : JXBoxLayout.prefAreaWidth(content));
                double h = PAGE_NAV + (content == null ? 0 : JXBoxLayout.prefAreaHeight(content));
                return new double[] {64, w, MAX, PAGE_NAV, h, MAX};
            }
            default:
                throw new IllegalStateException("not a control: " + type); // handles(type) is false
        }
    }

    /** Labeled: pref = text + extra, min shrinks the text to an ellipsis; max = pref. */
    private static double[] labeled(JXNativeNode node, double textWidth, double extraWidth, double height) {
        double ellipsis = JXTextEngine.get(JXPaint.bold(node)).width(JXTextLayout.ELLIPSIS, JXPaint.fontSize(node));
        double pref = textWidth + extraWidth;
        return new double[] {Math.min(textWidth, ellipsis) + extraWidth, pref, pref, height, height, height};
    }

    private static double[] cellSizes(JXNativeNode node, JXTextEngine text, float size, double line) {
        float[] pad = JXPaint.insetsOr(node, 3, 7);
        String value = JXPaint.text(node, "value");
        JXNativeNode graphic = first(node);
        double gw = graphic == null ? 0 : graphic.prefWidth();
        double gh = graphic == null ? 0 : graphic.prefHeight();
        double tw = value.isEmpty() ? 0 : text.width(value, size);
        double w = pad[1] + pad[3] + gw + (graphic != null && !value.isEmpty() ? 4 : 0) + tw;
        double h = pad[0] + pad[2] + Math.max(value.isEmpty() && graphic != null ? 0 : line, gh);
        double fixed = JXPaint.number(node, "fixedHeight", -1);
        if (fixed > 0) {
            h = fixed;
        }
        return new double[] {pad[1] + pad[3], w, MAX, h, h, MAX};
    }

    static double[] imageSize(JXNativeNode node) {
        double iw = JXPaint.number(node, "imageWidth", 0);
        double ih = JXPaint.number(node, "imageHeight", 0);
        double fw = JXPaint.number(node, "fitWidth", 0);
        double fh = JXPaint.number(node, "fitHeight", 0);
        boolean ratio = Boolean.TRUE.equals(node.getProperty("preserveRatio"));
        if (fw <= 0 && fh <= 0) {
            return new double[] {iw, ih};
        }
        if (!ratio || iw <= 0 || ih <= 0) {
            return new double[] {fw > 0 ? fw : iw, fh > 0 ? fh : ih};
        }
        double scale = fw > 0 && fh > 0 ? Math.min(fw / iw, fh / ih) : fw > 0 ? fw / iw : fh / ih;
        return new double[] {iw * scale, ih * scale};
    }

    /** Total width of side children of a text field (left + right, with a 4 px gap each) and their tallest height. */
    private static double[] sideSizes(JXNativeNode node) {
        double w = 0;
        double h = 0;
        for (JXNativeNode child : node.getChildren()) {
            w += child.prefWidth() + 4;
            h = Math.max(h, child.prefHeight());
        }
        return new double[] {w, h};
    }

    // ---- layout ------------------------------------------------------------------------------

    static void layoutChildren(JXNativeNode node) {
        String type = node.getType();
        int x = node.getX();
        int y = node.getY();
        int w = node.getWidth();
        int h = node.getHeight();
        switch (type) {
            case "button":
            case "toggle": {
                JXNativeNode graphic = first(node);
                if (graphic != null) {
                    float[] group = buttonContent(node);
                    double gh = Math.min(JXBoxLayout.ceil(graphic.prefHeight()), h);
                    graphic.layout((int) Math.round(group[0]), (int) Math.round(y + (h - gh) / 2),
                            (int) JXBoxLayout.ceil(graphic.prefWidth()), (int) gh);
                }
                break;
            }
            case "scroll": {
                JXNativeNode content = first(node);
                if (content != null) {
                    ScrollGeometry g = scrollGeometry(node);
                    content.layout((int) Math.round(g.viewX - g.scrollX), (int) Math.round(g.viewY - g.scrollY),
                            (int) Math.ceil(g.contentW), (int) Math.ceil(g.contentH));
                }
                break;
            }
            case "list": {
                ScrollGeometry g = scrollGeometry(node);
                double cell = cellHeight(node);
                int first = (int) JXPaint.number(node, "first", 0);
                List<JXNativeNode> cells = node.getChildren();
                for (int i = 0; i < cells.size(); i++) {
                    double top = g.viewY + (first + i) * cell - g.scrollY;
                    cells.get(i).layout((int) Math.round(g.viewX), (int) Math.round(top), (int) Math.floor(g.viewW), (int) cell);
                }
                break;
            }
            case "table": {
                ScrollGeometry g = scrollGeometry(node);
                double row = rowHeight(node);
                int first = (int) JXPaint.number(node, "first", 0);
                float[] widths = columnWidths(node);
                node.setChildWidths(widths);
                double total = 0;
                for (float c : widths) {
                    total += c;
                }
                List<JXNativeNode> rows = node.getChildren();
                for (int i = 0; i < rows.size(); i++) {
                    double top = g.viewY + (first + i) * row - g.scrollY;
                    rows.get(i).layout((int) Math.round(g.viewX - g.scrollX), (int) Math.round(top),
                            (int) Math.ceil(Math.max(total, g.viewW)), (int) row);
                }
                break;
            }
            case "tablerow": {
                float[] widths = node.getParentWidths();
                double cx = x;
                List<JXNativeNode> cells = node.getChildren();
                for (int i = 0; i < cells.size(); i++) {
                    double cw = i < widths.length ? widths[i] : 0;
                    cells.get(i).layout((int) Math.round(cx), y, (int) Math.round(cx + cw) - (int) Math.round(cx), h);
                    cx += cw;
                }
                break;
            }
            case "tabs": {
                JXNativeNode content = first(node);
                if (content != null) {
                    JXBoxLayout.layoutInArea(node, content, 0, TAB_HEADER, w, h - TAB_HEADER, true, true, 'T', 'L');
                }
                break;
            }
            case "pagination": {
                JXNativeNode content = first(node);
                if (content != null) {
                    JXBoxLayout.layoutInArea(node, content, 0, 0, w, h - PAGE_NAV, true, true, 'C', 'C');
                }
                break;
            }
            case "buttonbar": {
                List<JXNativeNode> buttons = node.getChildren();
                double right = x + w;
                for (int i = buttons.size() - 1; i >= 0; i--) {
                    JXNativeNode b = buttons.get(i);
                    double bw = Math.max(BUTTON_BAR_MIN, JXBoxLayout.ceil(b.prefWidth()));
                    double bh = JXBoxLayout.ceil(b.prefHeight());
                    right -= bw;
                    b.layout((int) Math.round(right), (int) Math.round(y + (h - bh) / 2), (int) bw, (int) bh);
                    right -= BUTTON_BAR_GAP;
                }
                break;
            }
            case "textflow": {
                double cx = 0;
                double cy = 0;
                double lineH = 0;
                for (JXNativeNode run : node.getChildren()) {
                    double rw = JXBoxLayout.ceil(run.prefWidth());
                    double rh = JXBoxLayout.ceil(run.prefHeight());
                    if (cx > 0 && cx + rw > w) {
                        cx = 0;
                        cy += lineH;
                        lineH = 0;
                    }
                    run.layout(x + (int) Math.round(cx), y + (int) Math.round(cy), (int) rw, (int) rh);
                    cx += rw;
                    lineH = Math.max(lineH, rh);
                }
                break;
            }
            case "cell": {
                JXNativeNode graphic = first(node);
                if (graphic != null) {
                    float[] pad = JXPaint.insetsOr(node, 3, 7);
                    double gw = JXBoxLayout.ceil(graphic.prefWidth());
                    double gh = Math.min(JXBoxLayout.ceil(graphic.prefHeight()), h - pad[0] - pad[2]);
                    boolean alone = JXPaint.text(node, "value").isEmpty();
                    if (alone && Boolean.TRUE.equals(node.getProperty("fillGraphic"))) {
                        graphic.layout(x + (int) pad[3], y + (int) pad[0], (int) (w - pad[1] - pad[3]), (int) (h - pad[0] - pad[2]));
                    } else {
                        graphic.layout(x + (int) pad[3], (int) Math.round(y + (h - gh) / 2), (int) gw, (int) Math.max(0, gh));
                    }
                }
                break;
            }
            case "input":
            case "password": {
                float[] pad = JXPaint.insetsOr(node, JXNativeNode.PAD_Y, JXNativeNode.FIELD_PAD_X);
                double left = x + pad[3];
                double right = x + w - pad[1];
                for (JXNativeNode child : node.getChildren()) {
                    double cw = JXBoxLayout.ceil(child.prefWidth());
                    double ch = Math.min(JXBoxLayout.ceil(child.prefHeight()), h);
                    int cy = (int) Math.round(y + (h - ch) / 2);
                    if ("right".equals(child.getProperty("side"))) {
                        right -= cw;
                        child.layout((int) Math.round(right), cy, (int) cw, (int) ch);
                        right -= 4;
                    } else {
                        child.layout((int) Math.round(left), cy, (int) cw, (int) ch);
                        left += cw + 4;
                    }
                }
                break;
            }
            default:
                break;
        }
    }

    /**
     * Graphic and text of a button, centred together: {left of the group, x where the text starts}.
     * Without a graphic the text is centred on its own.
     */
    static float[] buttonContent(JXNativeNode node) {
        float[] pad = JXPaint.insetsOr(node, JXNativeNode.PAD_Y, JXNativeNode.PAD_X);
        String label = JXPaint.text(node, "label");
        float size = JXPaint.fontSize(node);
        JXTextEngine engine = JXTextEngine.get(JXPaint.bold(node));
        JXNativeNode graphic = first(node);
        float available = node.getWidth() - pad[1] - pad[3];
        String shown = JXTextLayout.ellipsize(label, available - (graphic == null ? 0 : (float) JXBoxLayout.ceil(graphic.prefWidth()) + 4), size, JXPaint.bold(node));
        float tw = shown.isEmpty() ? 0 : engine.width(shown, size);
        float gw = graphic == null ? 0 : (float) JXBoxLayout.ceil(graphic.prefWidth());
        float gap = graphic != null && tw > 0 ? 4 : 0;
        float group = gw + gap + tw;
        float left = node.getX() + pad[3] + Math.max(0, (available - group) / 2);
        return new float[] {left, left + gw + gap};
    }

    /** Widths taken by left and right side children of a text field (with their gaps). */
    static float[] sideWidths(JXNativeNode node) {
        float left = 0;
        float right = 0;
        for (JXNativeNode child : node.getChildren()) {
            if ("right".equals(child.getProperty("side"))) {
                right += child.getWidth() + 4;
            } else {
                left += child.getWidth() + 4;
            }
        }
        return new float[] {left, right};
    }

    // ---- scrolling ---------------------------------------------------------------------------

    /** Geometry of a scroll pane, list or table: viewport, content, offsets and scroll bars. */
    public static final class ScrollGeometry {
        public float viewX;
        public float viewY;
        public float viewW;
        public float viewH;
        public double contentW;
        public double contentH;
        public double scrollX;
        public double scrollY;
        public boolean vertical;
        public boolean horizontal;
        public float vx;
        public float vy;
        public float vw;
        public float vh;
        public float vThumbY;
        public float vThumbH;
        public float hx;
        public float hy;
        public float hw;
        public float hh;
        public float hThumbX;
        public float hThumbW;

        /** Largest scroll offsets. */
        public double maxScrollX() {
            return Math.max(0, contentW - viewW);
        }

        public double maxScrollY() {
            return Math.max(0, contentH - viewH);
        }
    }

    public static ScrollGeometry scrollGeometry(JXNativeNode node) {
        ScrollGeometry g = new ScrollGeometry();
        String type = node.getType();
        float x = node.getX() + 1;
        float y = node.getY() + 1;
        float w = node.getWidth() - 2;
        float h = node.getHeight() - 2;
        if ("table".equals(type)) {
            y += TABLE_HEADER;
            h -= TABLE_HEADER;
        }
        String hPolicy = String.valueOf(node.getProperty("hbarPolicy"));
        String vPolicy = String.valueOf(node.getProperty("vbarPolicy"));
        boolean vBar = "ALWAYS".equals(vPolicy);
        boolean hBar = "ALWAYS".equals(hPolicy);
        for (int pass = 0; pass < 3; pass++) {
            float vw = w - (vBar ? SCROLL_BAR : 0);
            float vh = h - (hBar ? SCROLL_BAR : 0);
            double[] content = contentSize(node, vw, vh);
            g.contentW = content[0];
            g.contentH = content[1];
            boolean needV = !"NEVER".equals(vPolicy) && ("ALWAYS".equals(vPolicy) || content[1] > vh + 0.5);
            boolean needH = !"NEVER".equals(hPolicy) && ("ALWAYS".equals(hPolicy) || content[0] > vw + 0.5);
            if (needV == vBar && needH == hBar) {
                break;
            }
            vBar = needV;
            hBar = needH;
        }
        g.vertical = vBar;
        g.horizontal = hBar;
        g.viewX = x;
        g.viewY = y;
        g.viewW = Math.max(0, w - (vBar ? SCROLL_BAR : 0));
        g.viewH = Math.max(0, h - (hBar ? SCROLL_BAR : 0));
        double[] content = contentSize(node, g.viewW, g.viewH);
        g.contentW = content[0];
        g.contentH = content[1];
        if ("scroll".equals(type)) {
            g.scrollX = clamp(JXPaint.number(node, "hvalue", 0)) * g.maxScrollX();
            g.scrollY = clamp(JXPaint.number(node, "vvalue", 0)) * g.maxScrollY();
        } else {
            g.scrollX = Math.max(0, Math.min(JXPaint.number(node, "scrollX", 0), g.maxScrollX()));
            g.scrollY = Math.max(0, Math.min(JXPaint.number(node, "scrollY", 0), g.maxScrollY()));
        }
        if (vBar) {
            g.vx = x + w - SCROLL_BAR;
            g.vy = y;
            g.vw = SCROLL_BAR;
            g.vh = g.viewH;
            float track = g.vh - 2 * BAR_BUTTON;
            g.vThumbH = (float) Math.max(MIN_THUMB, Math.min(track, track * g.viewH / Math.max(1, g.contentH)));
            double max = g.maxScrollY();
            g.vThumbY = g.vy + BAR_BUTTON + (float) (max <= 0 ? 0 : (track - g.vThumbH) * g.scrollY / max);
        }
        if (hBar) {
            g.hx = x;
            g.hy = y + h - SCROLL_BAR;
            g.hw = g.viewW;
            g.hh = SCROLL_BAR;
            float track = g.hw - 2 * BAR_BUTTON;
            g.hThumbW = (float) Math.max(MIN_THUMB, Math.min(track, track * g.viewW / Math.max(1, g.contentW)));
            double max = g.maxScrollX();
            g.hThumbX = g.hx + BAR_BUTTON + (float) (max <= 0 ? 0 : (track - g.hThumbW) * g.scrollX / max);
        }
        return g;
    }

    /** The clip of a scroll pane, list or table: its viewport (below a table's header). */
    public static float[] viewport(JXNativeNode node) {
        ScrollGeometry g = scrollGeometry(node);
        return new float[] {g.viewX, g.viewY, g.viewW, g.viewH};
    }

    private static double[] contentSize(JXNativeNode node, float viewW, float viewH) {
        String type = node.getType();
        if ("scroll".equals(type)) {
            JXNativeNode content = first(node);
            if (content == null) {
                return new double[] {0, 0};
            }
            boolean fitW = Boolean.TRUE.equals(node.getProperty("fitToWidth"));
            boolean fitH = Boolean.TRUE.equals(node.getProperty("fitToHeight"));
            double cw = fitW ? JXBoxLayout.bounded(content.minWidth(), viewW, content.maxWidth())
                    : JXBoxLayout.bounded(content.minWidth(), content.prefWidth(), content.maxWidth());
            double ch = fitH ? JXBoxLayout.bounded(content.minHeight(), viewH, content.maxHeight())
                    : JXBoxLayout.bounded(content.minHeight(), content.prefHeight(), content.maxHeight());
            // like ScrollPaneSkin: content smaller than the viewport grows to it only when fitting
            return new double[] {JXBoxLayout.ceil(cw), JXBoxLayout.ceil(ch)};
        }
        if ("list".equals(type)) {
            return new double[] {viewW, JXPaint.number(node, "itemCount", 0) * cellHeight(node)};
        }
        if ("table".equals(type)) {
            float total = 0;
            for (float c : columnWidths(node, viewW)) {
                total += c;
            }
            return new double[] {total, JXPaint.number(node, "itemCount", 0) * rowHeight(node)};
        }
        return new double[] {viewW, viewH};
    }

    public static double cellHeight(JXNativeNode node) {
        double fixed = JXPaint.number(node, "cellHeight", -1);
        if (fixed > 0) {
            return fixed;
        }
        JXNativeNode cell = first(node);
        return cell == null ? CELL_HEIGHT : Math.max(1, JXBoxLayout.ceil(cell.prefHeight()));
    }

    static double rowHeight(JXNativeNode node) {
        JXNativeNode table = "tablerow".equals(node.getType()) ? null : node;
        double fixed = table == null ? -1 : JXPaint.number(table, "rowHeight", -1);
        return fixed > 0 ? fixed : ROW_HEIGHT;
    }

    /** How many cells or rows fit in the viewport of a list or table, plus one partly shown. */
    public static int visibleCount(JXNativeNode node) {
        ScrollGeometry g = scrollGeometry(node);
        double item = "table".equals(node.getType()) ? rowHeight(node) : cellHeight(node);
        return (int) Math.ceil(g.viewH / item) + 1;
    }

    /** Index of the item at window y in a list or table, or -1. */
    public static int itemAt(JXNativeNode node, double windowY) {
        ScrollGeometry g = scrollGeometry(node);
        if (windowY < g.viewY || windowY >= g.viewY + g.viewH) {
            return -1;
        }
        double item = "table".equals(node.getType()) ? rowHeight(node) : cellHeight(node);
        int index = (int) Math.floor((windowY - g.viewY + g.scrollY) / item);
        return index < JXPaint.number(node, "itemCount", 0) ? index : -1;
    }

    // ---- table columns -----------------------------------------------------------------------

    /** Resolved column widths of a table (explicit, fitted to header and cells, or constrained). */
    public static float[] columnWidths(JXNativeNode node) {
        float viewW = node.getWidth() - 2;
        if (Boolean.TRUE.equals(node.getProperty("constrained"))) {
            // constrained tables never scroll horizontally; only a vertical bar narrows them
            viewW = constrainedViewWidth(node);
        }
        return columnWidths(node, viewW);
    }

    /** View width of a constrained table, without scrollGeometry (which needs the column widths). */
    private static float constrainedViewWidth(JXNativeNode node) {
        double rows = JXPaint.number(node, "itemCount", 0) * rowHeight(node);
        float h = node.getHeight() - 2 - TABLE_HEADER;
        return node.getWidth() - 2 - (rows > h ? SCROLL_BAR : 0);
    }

    static float[] columnWidths(JXNativeNode node, float viewW) {
        String[] titles = JXPaint.strings(node.getProperty("columns"));
        double[] explicit = doubles(node.getProperty("columnWidths"));
        Object state = node.getLayoutState();
        float[] auto;
        if (state instanceof ColumnState && ((ColumnState) state).matches(titles, explicit)) {
            auto = ((ColumnState) state).widths;
        } else {
            auto = fitColumns(node, titles, explicit);
            if (!node.getChildren().isEmpty() || JXPaint.number(node, "itemCount", 0) == 0) {
                node.setLayoutState(new ColumnState(titles, explicit, auto));
            }
        }
        float[] widths = auto.clone();
        if (Boolean.TRUE.equals(node.getProperty("constrained")) && widths.length > 0) {
            float total = 0;
            for (float c : widths) {
                total += c;
            }
            if (total > 0 && viewW > 0) {
                float scale = viewW / total;
                float used = 0;
                for (int i = 0; i < widths.length; i++) {
                    widths[i] = i == widths.length - 1 ? Math.max(10, viewW - used) : Math.max(10, (float) Math.floor(widths[i] * scale));
                    used += widths[i];
                }
            }
        }
        return widths;
    }

    private static float[] fitColumns(JXNativeNode node, String[] titles, double[] explicit) {
        JXTextEngine bold = JXTextEngine.get(true);
        float[] widths = new float[titles.length];
        for (int i = 0; i < titles.length; i++) {
            if (i < explicit.length && explicit[i] >= 0) {
                widths[i] = (float) explicit[i];
                continue;
            }
            double w = bold.width(titles[i], JXTextEngine.DEFAULT_SIZE) + 18;
            for (JXNativeNode row : node.getChildren()) {
                if (i < row.getChildren().size()) {
                    w = Math.max(w, row.getChildren().get(i).prefWidth() + 10);
                }
            }
            widths[i] = (float) Math.ceil(Math.max(10, w));
        }
        return widths;
    }

    /** Auto column widths, kept while the columns stay the same so scrolling never resizes them. */
    private static final class ColumnState {
        final String[] titles;
        final double[] explicit;
        final float[] widths;

        ColumnState(String[] titles, double[] explicit, float[] widths) {
            this.titles = titles;
            this.explicit = explicit;
            this.widths = widths;
        }

        boolean matches(String[] otherTitles, double[] otherExplicit) {
            return Arrays.equals(titles, otherTitles) && Arrays.equals(explicit, otherExplicit);
        }
    }

    /** Column index at window x in a table, or -1. */
    public static int columnAt(JXNativeNode node, double windowX) {
        ScrollGeometry g = scrollGeometry(node);
        double cx = g.viewX - g.scrollX;
        float[] widths = columnWidths(node);
        for (int i = 0; i < widths.length; i++) {
            if (windowX >= cx && windowX < cx + widths[i]) {
                return i;
            }
            cx += widths[i];
        }
        return -1;
    }

    // ---- tabs --------------------------------------------------------------------------------

    /** Left edge of each tab header relative to the node, and the right edge of the last. */
    public static float[] tabPositions(JXNativeNode node) {
        String[] titles = JXPaint.strings(node.getProperty("titles"));
        float[] out = new float[titles.length + 1];
        JXTextEngine engine = JXTextEngine.get(false);
        float x = TAB_LEFT;
        for (int i = 0; i < titles.length; i++) {
            out[i] = x;
            x += (float) Math.ceil(engine.width(titles[i], JXTextEngine.DEFAULT_SIZE) + 2 * TAB_PAD
                    + (tabClosable(node, i) ? TAB_CLOSE : 0));
        }
        out[titles.length] = x;
        return out;
    }

    /** Whether tab i shows a close button (closingPolicy and the tab's own closable flag). */
    public static boolean tabClosable(JXNativeNode node, int index) {
        String policy = String.valueOf(node.getProperty("closingPolicy"));
        Object flags = node.getProperty("closable");
        boolean own = !(flags instanceof boolean[]) || index >= ((boolean[]) flags).length || ((boolean[]) flags)[index];
        if (!own || "UNAVAILABLE".equals(policy)) {
            return false;
        }
        if ("ALL_TABS".equals(policy)) {
            return true;
        }
        return index == (int) JXPaint.number(node, "selected", 0); // SELECTED_TAB, JavaFX's default
    }

    /** Tab index under window (x, y) in the header, -1 outside; {@code closeHit[0]} tells if it is the close button. */
    public static int tabAt(JXNativeNode node, double windowX, double windowY, boolean[] closeHit) {
        double rx = windowX - node.getX();
        double ry = windowY - node.getY();
        if (ry < TAB_TOP || ry >= TAB_HEADER) {
            return -1;
        }
        float[] tabs = tabPositions(node);
        for (int i = 0; i + 1 < tabs.length; i++) {
            if (rx >= tabs[i] && rx < tabs[i + 1]) {
                if (closeHit != null && closeHit.length > 0) {
                    closeHit[0] = tabClosable(node, i) && rx >= tabs[i + 1] - TAB_PAD - 12;
                }
                return i;
            }
        }
        return -1;
    }

    // ---- pagination --------------------------------------------------------------------------

    /** Navigation buttons in window coordinates: x, y, w, h, page (-1 previous, -2 next). */
    public static float[][] pageButtons(JXNativeNode node) {
        return pageButtonsRelative(node, 1);
    }

    private static float[][] pageButtonsRelative(JXNativeNode node, int absolute) {
        int count = Math.max(1, (int) JXPaint.number(node, "pageCount", 1));
        int current = Math.max(0, Math.min(count - 1, (int) JXPaint.number(node, "current", 0)));
        int shown = Math.min(count, Math.max(1, (int) JXPaint.number(node, "maxPageIndicatorCount", 10)));
        int start = Math.max(0, Math.min(current - shown / 2, count - shown));
        int buttons = shown + 2;
        float total = 2 * PAGE_ARROW + shown * PAGE_BUTTON;
        float left = absolute == 1 ? node.getX() + (node.getWidth() - total) / 2 : 0;
        float top = absolute == 1 ? node.getY() + node.getHeight() - PAGE_NAV + 5 : 0;
        float[][] out = new float[buttons][];
        out[0] = new float[] {left, top, PAGE_ARROW, 25, -1};
        for (int i = 0; i < shown; i++) {
            out[i + 1] = new float[] {left + PAGE_ARROW + i * PAGE_BUTTON, top, PAGE_BUTTON, 25, start + i};
        }
        out[buttons - 1] = new float[] {left + PAGE_ARROW + shown * PAGE_BUTTON, top, PAGE_ARROW, 25, -2};
        return out;
    }

    // ---- helpers -----------------------------------------------------------------------------

    private static JXNativeNode first(JXNativeNode node) {
        List<JXNativeNode> children = node.getChildren();
        return children.isEmpty() ? null : children.get(0);
    }

    static double[] doubles(Object value) {
        if (value instanceof double[]) {
            return (double[]) value;
        }
        if (value instanceof Object[]) {
            Object[] objects = (Object[]) value;
            double[] out = new double[objects.length];
            for (int i = 0; i < objects.length; i++) {
                out[i] = objects[i] instanceof Number ? ((Number) objects[i]).doubleValue() : -1;
            }
            return out;
        }
        return new double[0];
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
