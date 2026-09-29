package com.jxparallel.ui.native2d;

import java.util.List;

import com.jxparallel.ui.text.JXTextEngine;

/**
 * Paints every element type with the look of JavaFX's Modena theme, through a {@link JXPainter}, so
 * Skia and NanoVG draw the same thing. Colors and insets come from Modena (measured on JavaFX 21);
 * gradients are kept where Modena layers them, shadows are left out.
 *
 * <p>Props every node understands: {@code background}, {@code backgroundRadius}, {@code borderColor},
 * {@code borderWidth}, {@code borderRadius} (a region's CSS background and border, ARGB ints),
 * {@code opacity}, {@code blur} (Gaussian blur radius, Skia only), {@code clip}, {@code hidden},
 * {@code disabled} (drawn at 40% like Modena), and for text {@code textFill}, {@code fontSize},
 * {@code bold}, {@code underline}. Controls also read {@code focused}, {@code hover} and {@code pressed}.
 */
final class JXPaint {
    static final int TEXT = 0xFF333333;
    static final int ACCENT = 0xFF0096C9;
    static final int FOCUS = 0xFF039ED3;
    static final int OUTER_BORDER = 0xFFB6B6B6;
    static final int BOX_BORDER = 0xFFC9C9C9;
    static final int HIGHLIGHT = 0xBAFFFFFF;
    static final int BACKGROUND = 0xFFF4F4F4;
    static final int PROMPT = 0xFF999999;
    static final int MARK = 0xFF333333;
    static final int ARROW = 0xFF575757;
    static final int SELECTION = 0xFF0096C9;
    static final int SELECTION_UNFOCUSED = 0xFFD3D3D3;
    static final int ODD_ROW = 0xFFFAFAFA;
    static final int SCROLL_BAR = 13;

    private final JXPainter p;

    /** Time for indeterminate animations; tests fix it so recordings are repeatable. */
    static java.util.function.LongSupplier clock = System::currentTimeMillis;

    JXPaint(JXPainter painter) {
        this.p = painter;
    }

    void paint(JXNativeNode node) {
        if (Boolean.TRUE.equals(node.getProperty("hidden"))) {
            return; // invisible but managed: keeps its place in the layout, like JavaFX
        }
        double opacity = number(node, "opacity", 1.0);
        if (Boolean.TRUE.equals(node.getProperty("disabled"))) {
            opacity *= 0.4;
        }
        double blur = number(node, "blur", 0.0);
        boolean layered = opacity < 1.0 || blur > 0;
        if (layered) {
            p.layer((float) opacity, (float) blur);
        }
        region(node);
        self(node);
        children(node);
        if (layered) {
            p.restore();
        }
    }

    /** CSS-like background and border of any node. */
    private void region(JXNativeNode node) {
        Object bg = node.getProperty("background");
        Object border = node.getProperty("borderColor");
        if (!(bg instanceof Integer) && !(border instanceof Integer)) {
            return;
        }
        float x = node.getX();
        float y = node.getY();
        float w = node.getWidth();
        float h = node.getHeight();
        if (bg instanceof Integer) {
            p.fillRoundRect(x, y, w, h, (float) number(node, "backgroundRadius", 0), (Integer) bg);
        }
        if (border instanceof Integer) {
            p.strokeRoundRect(x, y, w, h, (float) number(node, "borderRadius", 0), (float) number(node, "borderWidth", 1), (Integer) border);
        }
    }

    private void children(JXNativeNode node) {
        if (node.collapsed()) {
            return; // only the title bar of a collapsed pane shows
        }
        String type = node.getType();
        boolean clip = Boolean.TRUE.equals(node.getProperty("clip"));
        float[] viewport = null;
        if ("scroll".equals(type) || "list".equals(type) || "table".equals(type)) {
            viewport = JXControlLayout.viewport(node);
        } else if ("textarea".equals(type)) {
            viewport = new float[] {node.getX() + 1, node.getY() + 1, node.getWidth() - 2, node.getHeight() - 2};
        }
        if (viewport != null) {
            p.clip(viewport[0], viewport[1], viewport[2], viewport[3]);
        } else if (clip) {
            p.clip(node.getX(), node.getY(), node.getWidth(), node.getHeight());
        }
        List<JXNativeNode> kids = node.getChildren();
        for (int i = 0, n = kids.size(); i < n; i++) {
            paint(kids.get(i)); // indexed: no iterator per node per frame
        }
        if (viewport != null && ("list".equals(type) || "table".equals(type)) && node.getProperty("popupOf") == null) {
            emptyRows(node, viewport);
        }
        if (viewport != null || clip) {
            p.restore();
        }
        after(node);
    }

    /** Parts drawn over the children: scroll bars, table header, focus rings. */
    private void after(JXNativeNode node) {
        String type = node.getType();
        if ("scroll".equals(type) || "list".equals(type) || "table".equals(type)) {
            scrollBars(node);
        }
        if ("table".equals(type)) {
            tableHeader(node);
        }
    }

    private void self(JXNativeNode node) {
        float x = node.getX();
        float y = node.getY();
        float w = node.getWidth();
        float h = node.getHeight();
        String type = node.getType();
        switch (type) {
            case "button":
            case "toggle":
                buttonBody(node, x, y, w, h, 3, Boolean.TRUE.equals(node.getProperty("selected")));
                if (node.getChildren().isEmpty()) {
                    labeledText(node, text(node, "label"), x, y, w, h, alignmentH(node, 'C'),
                            insetsOr(node, JXNativeNode.PAD_Y, JXNativeNode.PAD_X));
                } else {
                    float[] group = JXControlLayout.buttonContent(node);
                    float[] pad = insetsOr(node, JXNativeNode.PAD_Y, JXNativeNode.PAD_X);
                    String shown = JXTextLayout.ellipsize(text(node, "label"), x + w - pad[1] - group[1], fontSize(node), bold(node));
                    drawText(node, shown, group[1], y, h, color(node, TEXT), Boolean.TRUE.equals(node.getProperty("underline")));
                }
                break;
            case "hyperlink": {
                int color = Boolean.TRUE.equals(node.getProperty("visited")) ? 0xFF7A3D9E : ACCENT;
                float[] pad = insetsOr(node, 2, 3);
                drawText(node, text(node, "label"), x + pad[3] + 1, y, h, color(node, color),
                        Boolean.TRUE.equals(node.getProperty("hover")) || Boolean.TRUE.equals(node.getProperty("underline")));
                focusRing(node, x, y, w, h, 0);
                break;
            }
            case "checkbox":
                checkBox(node, x, y, h);
                break;
            case "radio":
                radio(node, x, y, h);
                break;
            case "input":
            case "password":
                field(node, x, y, w, h);
                fieldText(node, x, y, w, h, "password".equals(type));
                break;
            case "textarea":
                field(node, x, y, w, h);
                textAreaText(node, x, y, w, h);
                break;
            case "select":
                select(node, x, y, w, h);
                break;
            case "spinner":
                spinner(node, x, y, w, h);
                break;
            case "datepicker":
                datePicker(node, x, y, w, h);
                break;
            case "progress":
                progressBar(node, x, y, w, h);
                break;
            case "indicator":
                indicator(node, x, y, w, h);
                break;
            case "slider":
                slider(node, x, y, w, h);
                break;
            case "#text":
                labeledText(node, text(node, "value"), x, y, w, h, alignmentH(node, 'L'), padding(node));
                break;
            case "separator":
                separator(node, x, y, w, h);
                break;
            case "titled":
                titled(node, x, y, w, h);
                break;
            case "scroll":
                p.fillRect(x, y, w, h, BOX_BORDER);
                p.fillRect(x + 1, y + 1, w - 2, h - 2, BACKGROUND);
                break;
            case "list":
            case "table":
                p.fillRect(x, y, w, h, Boolean.TRUE.equals(node.getProperty("focused")) ? FOCUS : BOX_BORDER);
                p.fillRect(x + 1, y + 1, w - 2, h - 2, 0xFFFFFFFF);
                break;
            case "cell":
                cell(node, x, y, w, h);
                break;
            case "tablerow": {
                boolean selected = Boolean.TRUE.equals(node.getProperty("selected"));
                int bg = selected ? (Boolean.TRUE.equals(node.getProperty("listFocused")) ? SELECTION : SELECTION_UNFOCUSED)
                        : Boolean.TRUE.equals(node.getProperty("odd")) ? ODD_ROW : 0xFFFFFFFF;
                p.fillRect(x, y, w, h, bg);
                p.fillRect(x, y + h - 1, w, 1, 0xFFEDEDED);
                break;
            }
            case "tabs":
                tabs(node, x, y, w, h);
                break;
            case "pagination":
                pagination(node, x, y, w, h);
                break;
            case "image":
                image(node, x, y, w, h);
                break;
            case "popup":
                p.fillRect(x, y, w, h, 0x33000000);
                p.fillRect(x, y, w - 1, h - 1, BOX_BORDER);
                p.fillRect(x + 1, y + 1, w - 3, h - 3, 0xFFFFFFFF);
                break;
            case "tooltip":
                p.fillRoundRect(x, y, w, h, 6, 0xCC1E1E1E);
                labeledText(node, text(node, "label"), x, y, w, h, 'L', new float[] {4, 9, 4, 9});
                break;
            case "calendar":
                JXCalendar.paint(p, node, this);
                break;
            default:
                break;
        }
    }

    // ---- building blocks -------------------------------------------------------------------

    /** Modena button: highlight, outer border, inner border and body, with hover/armed variants. */
    void buttonBody(JXNativeNode node, float x, float y, float w, float h, float r, boolean selected) {
        boolean pressed = Boolean.TRUE.equals(node.getProperty("pressed")) || selected;
        boolean hover = Boolean.TRUE.equals(node.getProperty("hover"));
        boolean isDefault = Boolean.TRUE.equals(node.getProperty("defaultButton"));
        p.fillRoundRect(x, y + 1, w, h, r, HIGHLIGHT);
        p.fillRoundRect(x, y, w, h, r, OUTER_BORDER);
        if (pressed) {
            p.fillRoundRectGradient(x + 1, y + 1, w - 2, h - 2, r - 1, 0xFFCACACA, 0xFFBCBCBC);
            p.fillRoundRectGradient(x + 2, y + 2, w - 4, h - 4, Math.max(0, r - 2), 0xFFC6C6C6, 0xFFB5B5B5);
        } else if (isDefault) {
            p.fillRoundRectGradient(x + 1, y + 1, w - 2, h - 2, r - 1, 0xFFB9D7F1, 0xFF8EC4E8);
            p.fillRoundRectGradient(x + 2, y + 2, w - 4, h - 4, Math.max(0, r - 2), hover ? 0xFFB7D8F4 : 0xFFA3CDEE,
                    hover ? 0xFF94C6EB : 0xFF7FB8E4);
        } else {
            p.fillRoundRectGradient(x + 1, y + 1, w - 2, h - 2, r - 1, 0xFFFDFDFD, 0xFFE2E2E2);
            p.fillRoundRectGradient(x + 2, y + 2, w - 4, h - 4, Math.max(0, r - 2), hover ? 0xFFF8F8F8 : 0xFFEFEFEF,
                    hover ? 0xFFE3E3E3 : 0xFFD9D9D9);
        }
        focusRing(node, x, y, w, h, r);
    }

    void focusRing(JXNativeNode node, float x, float y, float w, float h, float r) {
        if (Boolean.TRUE.equals(node.getProperty("focused"))) {
            p.strokeRoundRect(x - 1, y - 1, w + 2, h + 2, r + 1, 1, 0x66039ED3);
            p.strokeRoundRect(x, y, w, h, r, 1, FOCUS);
        }
    }

    /** Modena text-field background: gradient border and white body, blue when focused. */
    private void field(JXNativeNode node, float x, float y, float w, float h) {
        boolean focused = Boolean.TRUE.equals(node.getProperty("focused"));
        if (focused) {
            p.fillRoundRect(x - 1, y - 1, w + 2, h + 2, 4, 0x66039ED3);
            p.fillRoundRect(x, y, w, h, 3, FOCUS);
        } else {
            p.fillRoundRectGradient(x, y, w, h, 3, 0xFFBBBBBB, 0xFFCFCFCF);
        }
        p.fillRoundRect(x + 1, y + 1, w - 2, h - 2, 2, 0xFFFFFFFF);
        if (!focused) {
            p.fillRoundRectGradient(x + 1, y + 1, w - 2, Math.min(5, h - 2), 2, 0xFFE8E8E8, 0xFFFFFFFF);
        }
    }

    /** Value (or prompt), selection and caret of a one-line text input, scrolled to keep the caret visible. */
    private void fieldText(JXNativeNode node, float x, float y, float w, float h, boolean password) {
        float[] pad = insetsOr(node, JXNativeNode.PAD_Y, JXNativeNode.FIELD_PAD_X);
        float[] sides = JXControlLayout.sideWidths(node);
        float left = x + pad[3] + sides[0];
        float right = x + w - pad[1] - sides[1];
        String value = text(node, "value");
        if (password) {
            value = bullets(value.length());
        }
        float size = fontSize(node);
        boolean bold = bold(node);
        JXTextEngine engine = JXTextEngine.get(bold);
        boolean focused = Boolean.TRUE.equals(node.getProperty("focused"));
        if (value.isEmpty()) {
            String prompt = text(node, "prompt");
            if (!prompt.isEmpty() && !focused) {
                p.text(prompt, left, y + engine.baseline(h, size), size, bold, PROMPT);
            }
        }
        int caret = (int) number(node, "caret", -1);
        int anchor = (int) number(node, "anchor", caret);
        caret = Math.max(-1, Math.min(caret, value.length()));
        anchor = Math.max(-1, Math.min(anchor, value.length()));
        float available = right - left;
        float full = engine.width(value, size);
        float shift = 0;
        String align = String.valueOf(node.getProperty("textAlignment"));
        if (full < available && "CENTER".equalsIgnoreCase(align)) {
            shift = -(available - full) / 2;
        } else if (full < available && "RIGHT".equalsIgnoreCase(align)) {
            shift = -(available - full);
        } else if (caret >= 0) {
            float caretX = engine.width(value.substring(0, caret), size);
            shift = Math.max(0, caretX - available + 1);
        }
        p.clip(left - 1, y, right - left + 2, h);
        if (caret >= 0 && anchor >= 0 && anchor != caret && focused) {
            int from = Math.min(caret, anchor);
            int to = Math.max(caret, anchor);
            float sx = left - shift + engine.width(value.substring(0, from), size);
            float ex = left - shift + engine.width(value.substring(0, to), size);
            float lineH = engine.lineHeight(size);
            p.fillRect(sx, y + (h - lineH) / 2, ex - sx, lineH, SELECTION);
        }
        if (!value.isEmpty()) {
            p.text(value, left - shift, y + engine.baseline(h, size), size, bold, color(node, TEXT));
        }
        if (caret >= 0 && focused) {
            float cx = left - shift + engine.width(value.substring(0, caret), size);
            float lineH = engine.lineHeight(size);
            p.fillRect((float) Math.floor(cx), y + (h - lineH) / 2, 1, lineH, TEXT);
        }
        p.restore();
    }

    private void textAreaText(JXNativeNode node, float x, float y, float w, float h) {
        String value = text(node, "value");
        float size = fontSize(node);
        boolean bold = bold(node);
        JXTextEngine engine = JXTextEngine.get(bold);
        float line = engine.lineHeight(size);
        float left = x + 1 + JXNativeNode.FIELD_PAD_X;
        float top = y + 1 + JXNativeNode.PAD_Y - (float) number(node, "scrollTop", 0);
        boolean focused = Boolean.TRUE.equals(node.getProperty("focused"));
        p.clip(x + 1, y + 1, w - 2, h - 2);
        if (value.isEmpty() && !focused) {
            p.text(text(node, "prompt"), left, top + engine.ascent(size), size, bold, PROMPT);
        }
        List<String> lines = JXTextLayout.lines(value, Boolean.TRUE.equals(node.getProperty("wrapText"))
                ? w - 2 - 2 * JXNativeNode.FIELD_PAD_X : Float.MAX_VALUE, size, bold);
        int caret = (int) number(node, "caret", -1);
        int anchor = (int) number(node, "anchor", caret);
        int offset = 0;
        for (int i = 0; i < lines.size(); i++) {
            String text = lines.get(i);
            float lineTop = top + i * line;
            if (lineTop + line >= y && lineTop <= y + h) {
                if (focused && caret >= 0 && anchor >= 0 && anchor != caret) {
                    int from = Math.max(Math.min(caret, anchor) - offset, 0);
                    int to = Math.min(Math.max(caret, anchor) - offset, text.length());
                    if (from < to || (from <= text.length() && to > text.length())) {
                        float sx = left + engine.width(text.substring(0, Math.min(from, text.length())), size);
                        float ex = left + engine.width(text.substring(0, Math.min(to, text.length())), size);
                        p.fillRect(sx, lineTop, Math.max(ex - sx, 2), line, SELECTION);
                    }
                }
                p.text(text, left, lineTop + engine.ascent(size), size, bold, color(node, TEXT));
                if (focused && caret >= offset && caret <= offset + text.length()) {
                    float cx = left + engine.width(text.substring(0, caret - offset), size);
                    p.fillRect((float) Math.floor(cx), lineTop, 1, line, TEXT);
                }
            }
            offset += text.length() + JXTextLayout.separatorAfter(value, offset + text.length());
        }
        p.restore();
    }

    private void checkBox(JXNativeNode node, float x, float y, float h) {
        float s = JXNativeNode.CHECK_BOX;
        float top = y + Math.round((h - s) / 2);
        buttonBody(node, x, top, s, s, 3, false);
        if (Boolean.TRUE.equals(node.getProperty("indeterminate"))) {
            p.fillRect(x + 4, top + 7, s - 8, 2, MARK);
        } else if (Boolean.TRUE.equals(node.getProperty("checked"))) {
            p.line(x + 4, top + 8.5f, x + 7, top + 11.5f, 2, MARK);
            p.line(x + 7, top + 11.5f, x + 12.5f, top + 4.5f, 2, MARK);
        }
        drawText(node, text(node, "label"), x + JXNativeNode.CHECK_BOX + JXNativeNode.CHECK_GAP, y, h, color(node, TEXT),
                Boolean.TRUE.equals(node.getProperty("underline")));
    }

    private void radio(JXNativeNode node, float x, float y, float h) {
        float s = JXNativeNode.CHECK_BOX;
        float top = y + Math.round((h - s) / 2);
        boolean focused = Boolean.TRUE.equals(node.getProperty("focused"));
        p.fillOval(x, top + 1, s, s, HIGHLIGHT);
        p.fillOval(x, top, s, s, focused ? FOCUS : OUTER_BORDER);
        p.fillOval(x + 1, top + 1, s - 2, s - 2, 0xFFFDFDFD);
        p.fillOval(x + 2, top + 2, s - 4, s - 4, Boolean.TRUE.equals(node.getProperty("hover")) ? 0xFFF4F4F4 : 0xFFE9E9E9);
        if (Boolean.TRUE.equals(node.getProperty("checked"))) {
            p.fillOval(x + 4, top + 4, s - 8, s - 8, MARK);
        }
        drawText(node, text(node, "label"), x + JXNativeNode.CHECK_BOX + JXNativeNode.CHECK_GAP, y, h, color(node, TEXT),
                Boolean.TRUE.equals(node.getProperty("underline")));
    }

    private void select(JXNativeNode node, float x, float y, float w, float h) {
        boolean editable = Boolean.TRUE.equals(node.getProperty("editable"));
        if (editable) {
            field(node, x, y, w, h);
            fieldText(node, x, y, w - JXNativeNode.ARROW, h, false);
            p.fillRect(x + w - JXNativeNode.ARROW, y + 1, 1, h - 2, OUTER_BORDER);
        } else {
            buttonBody(node, x, y, w, h, 3, Boolean.TRUE.equals(node.getProperty("showing")));
            String value = text(node, "value");
            boolean prompt = value.isEmpty() || Boolean.TRUE.equals(node.getProperty("promptShown"));
            if (value.isEmpty()) {
                value = text(node, "prompt");
            }
            p.clip(x, y, w - JXNativeNode.ARROW, h);
            drawText(node, value, x + JXNativeNode.PAD_X, y, h, prompt ? PROMPT : color(node, TEXT), false);
            p.restore();
        }
        float ax = x + w - JXNativeNode.ARROW + 10;
        float ay = y + Math.round((h - 4) / 2);
        downArrow(ax, ay, ARROW);
    }

    void downArrow(float x, float y, int color) {
        p.fillTriangle(x, y + 1, x + 8, y + 1, x + 4, y + 5, HIGHLIGHT);
        p.fillTriangle(x, y, x + 8, y, x + 4, y + 4, color);
    }

    void upArrow(float x, float y, int color) {
        p.fillTriangle(x, y + 4, x + 8, y + 4, x + 4, y, color);
    }

    private void spinner(JXNativeNode node, float x, float y, float w, float h) {
        field(node, x, y, w, h);
        float bw = JXControlLayout.SPINNER_BUTTON;
        fieldText(node, x, y, w - bw, h, false);
        float bx = x + w - bw;
        float half = (float) Math.floor(h / 2);
        boolean upHover = "up".equals(node.getProperty("hoverPart"));
        boolean downHover = "down".equals(node.getProperty("hoverPart"));
        p.fillRect(bx, y, bw, h, OUTER_BORDER);
        p.fillRoundRectGradient(bx + 1, y + 1, bw - 2, half - 1, 0, 0xFFFDFDFD, upHover ? 0xFFEAEAEA : 0xFFE2E2E2);
        p.fillRoundRectGradient(bx + 1, y + half + 1, bw - 2, h - half - 2, 0, 0xFFFDFDFD, downHover ? 0xFFEAEAEA : 0xFFE2E2E2);
        upArrow(bx + (bw - 8) / 2, y + (half - 4) / 2 + 0.5f, ARROW);
        downArrow(bx + (bw - 8) / 2, y + half + (h - half - 4) / 2 - 0.5f, ARROW);
    }

    private void datePicker(JXNativeNode node, float x, float y, float w, float h) {
        field(node, x, y, w, h);
        float bw = JXControlLayout.DATE_BUTTON;
        fieldText(node, x, y, w - bw, h, false);
        buttonBody(node, x + w - bw, y, bw, h, 0, Boolean.TRUE.equals(node.getProperty("showing")));
        float cx = x + w - bw + (bw - 11) / 2;
        float cy = y + (h - 10) / 2;
        p.fillRect(cx, cy, 11, 10, ARROW);
        p.fillRect(cx + 1, cy + 3, 9, 6, 0xFFFFFFFF);
        p.fillRect(cx + 2, cy + 4, 2, 2, ARROW);
        p.fillRect(cx + 5, cy + 4, 2, 2, ARROW);
        p.fillRect(cx + 2, cy + 6, 2, 2, ARROW);
    }

    private void progressBar(JXNativeNode node, float x, float y, float w, float h) {
        p.fillRoundRect(x, y + 1, w, h, 4, HIGHLIGHT);
        p.fillRoundRectGradient(x, y, w, h, 3, 0xFFBBBBBB, 0xFFCFCFCF);
        p.fillRoundRectGradient(x + 1, y + 1, w - 2, h - 2, 2, 0xFFEDEDED, 0xFFFFFFFF);
        double progress = number(node, "progress", 0.0);
        if (progress < 0) {
            float band = Math.max(20, w / 4);
            float t = (float) ((clock.getAsLong() % 1500) / 1500.0);
            float bx = x + 3 + (w - 6 - band) * t;
            p.fillRoundRectGradient(bx, y + 3, band, h - 6, 2, 0xFF008CBB, 0xFF0097C9);
            return;
        }
        float bw = (float) ((w - 6) * clamp(progress));
        if (bw > 0) {
            p.fillRoundRectGradient(x + 3, y + 3, bw, h - 6, 2, 0xFF008CBB, 0xFF0097C9);
        }
    }

    private void indicator(JXNativeNode node, float x, float y, float w, float h) {
        double progress = number(node, "progress", -1.0);
        JXTextEngine engine = JXTextEngine.get(false);
        float textH = progress >= 0 ? engine.lineHeight(11) : 0;
        float d = Math.max(0, Math.min(w, h - textH) - 4);
        float cx = x + (w - d) / 2;
        float cy = y + (h - textH - d) / 2;
        if (progress < 0) {
            long now = clock.getAsLong();
            int lit = (int) ((now / 100) % 8);
            float r = d / 2;
            for (int i = 0; i < 8; i++) {
                double a = Math.toRadians(i * 45 - 90);
                float dot = Math.max(2, d / 7);
                float px = (float) (cx + r + Math.cos(a) * (r - dot)) - dot / 2;
                float py = (float) (cy + r + Math.sin(a) * (r - dot)) - dot / 2;
                int alpha = 0x40 + ((i - lit + 8) % 8) * 0x18;
                p.fillOval(px, py, dot, dot, (Math.min(alpha, 0xFF) << 24) | 0x0096C9);
            }
            return;
        }
        p.fillOval(cx, cy, d, d, BOX_BORDER);
        p.fillOval(cx + 1, cy + 1, d - 2, d - 2, 0xFFFFFFFF);
        if (progress > 0) {
            p.strokeArc(cx + 3, cy + 3, d - 6, d - 6, -90, (float) (360 * clamp(progress)), (d - 6) / 2, ACCENT);
        }
        String percent = Math.round(clamp(progress) * 100) + "%";
        if (progress >= 1) {
            percent = "Done";
        }
        float tw = engine.width(percent, 11);
        p.text(percent, x + (w - tw) / 2, y + h - textH + engine.ascent(11), 11, false, TEXT);
    }

    private void slider(JXNativeNode node, float x, float y, float w, float h) {
        double min = number(node, "min", 0.0);
        double max = number(node, "max", 100.0);
        double fraction = max <= min ? 0.0 : clamp((number(node, "value", min) - min) / (max - min));
        if (node.vertical()) {
            float tx = x + (w - 6) / 2;
            p.fillRoundRectGradient(tx, y + 4, 6, h - 8, 3, 0xFFBBBBBB, 0xFFCFCFCF);
            p.fillRoundRect(tx + 1, y + 5, 4, h - 10, 2, 0xFFF0F0F0);
            float ty = (float) (y + (h - 14) * (1 - fraction));
            thumb(node, x + (w - 14) / 2, ty);
            return;
        }
        float ty = y + (h - 6) / 2;
        p.fillRoundRect(x + 4, ty + 1, w - 8, 6, 3, HIGHLIGHT);
        p.fillRoundRectGradient(x + 4, ty, w - 8, 6, 3, 0xFFBBBBBB, 0xFFCFCFCF);
        p.fillRoundRectGradient(x + 5, ty + 1, w - 10, 4, 2, 0xFFE8E8E8, 0xFFFFFFFF);
        thumb(node, (float) (x + (w - 14) * fraction), y + (h - 14) / 2);
    }

    private void thumb(JXNativeNode node, float x, float y) {
        p.fillOval(x, y, 14, 14, Boolean.TRUE.equals(node.getProperty("focused")) ? FOCUS : 0xFFA0A0A0);
        p.fillOval(x + 1, y + 1, 12, 12, 0xFFFDFDFD);
        p.fillOval(x + 2, y + 2, 10, 10, Boolean.TRUE.equals(node.getProperty("pressed")) ? 0xFFD0D0D0 : 0xFFE9E9E9);
    }

    private void separator(JXNativeNode node, float x, float y, float w, float h) {
        if (node.vertical()) {
            float left = x + Math.round((w - JXNativeNode.SEPARATOR_THICKNESS_V) / 2.0f);
            p.fillRect(left, y, 3, h, HIGHLIGHT);
            p.fillRect(left + 1, y, 1, h, 0xFFCFCFCF);
        } else {
            float top = y + Math.round((h - JXNativeNode.SEPARATOR_THICKNESS_H) / 2.0f);
            p.fillRect(x, top, w, 1, 0xFFCFCFCF);
            p.fillRect(x, top + 1, w, 1, HIGHLIGHT);
        }
    }

    private void titled(JXNativeNode node, float x, float y, float w, float h) {
        float title = JXTitledLayout.titleHeight();
        if (!node.collapsed() && h > title) {
            p.fillRect(x, y + title, w, h - title, BOX_BORDER);
            p.fillRoundRectGradient(x + 1, y + title, w - 2, Math.min(5, h - title - 1), 0, 0xFFE5E5E5, BACKGROUND);
            p.fillRect(x + 1, y + title + 5, w - 2, Math.max(0, h - title - 6), BACKGROUND);
        }
        p.fillRoundRect(x, y, w, title, 3, BOX_BORDER);
        boolean hover = Boolean.TRUE.equals(node.getProperty("hover"));
        p.fillRoundRectGradient(x + 1, y + 1, w - 2, title - 2, 2, 0xFFFDFDFD, hover ? 0xFFEAEAEA : 0xFFE2E2E2);
        if (node.collapsible()) {
            float[] a = JXTitledLayout.arrow(node, x, y);
            p.fillTriangle(a[0], a[1], a[2], a[3], a[4], a[5], ARROW);
        }
        drawText(node, text(node, "label"), x + JXTitledLayout.textOffset(node), y, title, color(node, TEXT), false);
        if (Boolean.TRUE.equals(node.getProperty("focused"))) {
            p.strokeRoundRect(x, y, w, title, 3, 1, FOCUS);
        }
    }

    private void cell(JXNativeNode node, float x, float y, float w, float h) {
        boolean selected = Boolean.TRUE.equals(node.getProperty("selected"));
        boolean focusedList = Boolean.TRUE.equals(node.getProperty("listFocused"));
        int bg = selected ? (focusedList ? SELECTION : SELECTION_UNFOCUSED)
                : Boolean.TRUE.equals(node.getProperty("odd")) ? ODD_ROW : 0xFFFFFFFF;
        if (Boolean.TRUE.equals(node.getProperty("hover")) && !selected) {
            bg = 0xFFEAF4FA;
        }
        if (!Boolean.FALSE.equals(node.getProperty("paintBackground"))) {
            p.fillRect(x, y, w, h, bg);
        }
        if (Boolean.TRUE.equals(node.getProperty("tableCell"))) {
            p.fillRect(x + w - 1, y, 1, h, 0xFFEDEDED);
        }
        String value = text(node, "value");
        if (!value.isEmpty()) {
            float[] pad = insetsOr(node, 3, 7);
            float graphic = node.getChildren().isEmpty() ? 0 : node.getChildren().get(0).getWidth();
            int textColor = selected && focusedList ? 0xFFFFFFFF : color(node, TEXT);
            p.clip(x, y, w, h);
            float left = x + pad[3] + (graphic > 0 ? graphic + 4 : 0);
            char align = alignmentH(node, 'L');
            if (align != 'L') {
                float tw = JXTextEngine.get(bold(node)).width(value, fontSize(node));
                float space = w - pad[1] - pad[3] - (graphic > 0 ? graphic + 4 : 0);
                left += align == 'C' ? (space - tw) / 2 : space - tw;
            }
            drawText(node, value, left, y, h, textColor, Boolean.TRUE.equals(node.getProperty("underline")));
            p.restore();
        }
    }

    private void tabs(JXNativeNode node, float x, float y, float w, float h) {
        float header = JXControlLayout.TAB_HEADER;
        p.fillRect(x, y + header, w, h - header, BACKGROUND);
        p.fillRect(x, y, w, header, OUTER_BORDER);
        p.fillRoundRectGradient(x, y + 1, w, header - 1, 0, 0xFFCFCFCF, 0xFFDEDEDE);
        String[] titles = strings(node.getProperty("titles"));
        int selected = (int) number(node, "selected", 0);
        float[] tabX = JXControlLayout.tabPositions(node);
        for (int i = 0; i < titles.length; i++) {
            float tx = x + tabX[i];
            float tw = tabX[i + 1] - tabX[i];
            float ty = y + JXControlLayout.TAB_TOP;
            float th = header - JXControlLayout.TAB_TOP;
            p.fillRoundRect(tx, ty, tw, th + 3, 3, OUTER_BORDER);
            if (i == selected) {
                p.fillRoundRect(tx + 1, ty + 1, tw - 2, th + 2, 2, BACKGROUND);
            } else {
                p.fillRoundRectGradient(tx + 1, ty + 1, tw - 2, th + 2, 2, 0xFFFDFDFD, 0xFFE2E2E2);
            }
            JXTextEngine engine = JXTextEngine.get(false);
            p.text(titles[i], tx + JXControlLayout.TAB_PAD, ty + engine.baseline(th, JXTextEngine.DEFAULT_SIZE),
                    JXTextEngine.DEFAULT_SIZE, false, TEXT);
            if (JXControlLayout.tabClosable(node, i)) {
                float cx = tx + tw - JXControlLayout.TAB_PAD - 8;
                float cy = ty + (th - 8) / 2;
                p.line(cx + 1, cy + 1, cx + 7, cy + 7, 1.5f, ARROW);
                p.line(cx + 7, cy + 1, cx + 1, cy + 7, 1.5f, ARROW);
            }
        }
        p.fillRect(x, y + header - 1, w, 1, OUTER_BORDER);
        if (selected >= 0 && selected < titles.length) {
            p.fillRect(x + tabX[selected] + 1, y + header - 1, tabX[selected + 1] - tabX[selected] - 2, 1, BACKGROUND);
        }
    }

    private void pagination(JXNativeNode node, float x, float y, float w, float h) {
        float[][] buttons = JXControlLayout.pageButtons(node);
        int current = (int) number(node, "current", 0);
        JXTextEngine engine = JXTextEngine.get(false);
        for (float[] b : buttons) {
            int page = (int) b[4];
            boolean arrow = page < 0;
            if (!arrow && page == current) {
                p.fillRoundRect(b[0], b[1], b[2], b[3], 3, OUTER_BORDER);
                p.fillRoundRect(b[0] + 1, b[1] + 1, b[2] - 2, b[3] - 2, 2, 0xFFFFFFFF);
            }
            if (arrow) {
                float cy = b[1] + b[3] / 2;
                float cx = b[0] + b[2] / 2;
                if (page == -1) {
                    p.fillTriangle(cx + 3, cy - 5, cx + 3, cy + 5, cx - 3, cy, ARROW);
                } else {
                    p.fillTriangle(cx - 3, cy - 5, cx - 3, cy + 5, cx + 3, cy, ARROW);
                }
            } else {
                String label = String.valueOf(page + 1);
                float tw = engine.width(label, JXTextEngine.DEFAULT_SIZE);
                p.text(label, b[0] + (b[2] - tw) / 2, b[1] + engine.baseline(b[3], JXTextEngine.DEFAULT_SIZE),
                        JXTextEngine.DEFAULT_SIZE, false, page == current ? ACCENT : TEXT);
            }
        }
        int count = (int) number(node, "pageCount", 1);
        String info = (current + 1) + "/" + count;
        float tw = engine.width(info, JXTextEngine.DEFAULT_SIZE);
        p.text(info, x + (w - tw) / 2, y + h - 6, JXTextEngine.DEFAULT_SIZE, false, TEXT);
    }

    private void image(JXNativeNode node, float x, float y, float w, float h) {
        Object pixels = node.getProperty("pixels");
        if (pixels instanceof int[]) {
            p.image((int[]) pixels, (int) number(node, "imageWidth", 0), (int) number(node, "imageHeight", 0), x, y, w, h);
        }
    }

    /** Like VirtualFlow's empty cells: stripes (and a table's column lines) below the last item. */
    private void emptyRows(JXNativeNode node, float[] viewport) {
        boolean table = "table".equals(node.getType());
        double item = table ? JXControlLayout.rowHeight(node) : JXControlLayout.cellHeight(node);
        int count = (int) number(node, "itemCount", 0);
        JXControlLayout.ScrollGeometry g = JXControlLayout.scrollGeometry(node);
        double top = viewport[1] + count * item - g.scrollY;
        float[] widths = table ? JXControlLayout.columnWidths(node) : new float[0];
        for (int i = count; top < viewport[1] + viewport[3]; i++, top += item) {
            if (i % 2 == 1) {
                p.fillRect(viewport[0], (float) top, viewport[2], (float) item, ODD_ROW);
            }
            float cx = viewport[0] - (float) g.scrollX;
            for (float w : widths) {
                cx += w;
                p.fillRect(cx - 1, (float) top, 1, (float) item, 0xFFEDEDED);
            }
        }
    }

    private void scrollBars(JXNativeNode node) {
        JXControlLayout.ScrollGeometry g = JXControlLayout.scrollGeometry(node);
        if (g.vertical) {
            bar(g.vx, g.vy, g.vw, g.vh, g.vThumbY, g.vThumbH, true);
        }
        if (g.horizontal) {
            bar(g.hx, g.hy, g.hw, g.hh, g.hThumbX, g.hThumbW, false);
        }
        if (g.vertical && g.horizontal) {
            p.fillRect(g.vx, g.hy, g.vw, g.hh, 0xFFEAEAEA);
        }
    }

    private void bar(float x, float y, float w, float h, float thumbPos, float thumbLen, boolean vertical) {
        p.fillRect(x, y, w, h, 0xFFD9D9D9);
        if (vertical) {
            p.fillRect(x + 1, y, w - 1, h, 0xFFEBEBEB);
            upArrow(x + (w - 8) / 2, y + 3, 0xFF828282);
            downArrow(x + (w - 8) / 2, y + h - 7, 0xFF828282);
            p.fillRoundRect(x + 2, thumbPos, w - 4, thumbLen, 3, OUTER_BORDER);
            p.fillRoundRectGradient(x + 3, thumbPos + 1, w - 6, thumbLen - 2, 2, 0xFFFDFDFD, 0xFFE2E2E2);
        } else {
            p.fillRect(x, y + 1, w, h - 1, 0xFFEBEBEB);
            float cy = y + (h - 8) / 2;
            p.fillTriangle(x + 7, cy, x + 7, cy + 8, x + 3, cy + 4, 0xFF828282);
            p.fillTriangle(x + w - 7, cy, x + w - 7, cy + 8, x + w - 3, cy + 4, 0xFF828282);
            p.fillRoundRect(thumbPos, y + 2, thumbLen, h - 4, 3, OUTER_BORDER);
            p.fillRoundRectGradient(thumbPos + 1, y + 3, thumbLen - 2, h - 6, 2, 0xFFFDFDFD, 0xFFE2E2E2);
        }
    }

    private void tableHeader(JXNativeNode node) {
        float x = node.getX() + 1;
        float y = node.getY() + 1;
        float w = node.getWidth() - 2;
        float h = JXControlLayout.TABLE_HEADER;
        p.fillRoundRectGradient(x, y, w, h, 0, 0xFFFDFDFD, 0xFFE2E2E2);
        p.fillRoundRectGradient(x, y + 1, w, h - 1, 0, 0xFFEFEFEF, 0xFFD9D9D9);
        p.fillRect(x, y + h - 1, w, 1, BOX_BORDER);
        String[] titles = strings(node.getProperty("columns"));
        float[] widths = JXControlLayout.columnWidths(node);
        float scrollX = (float) JXControlLayout.scrollGeometry(node).scrollX;
        p.clip(x, y, w, h);
        JXTextEngine engine = JXTextEngine.get(true);
        float cx = x - scrollX;
        for (int i = 0; i < titles.length && i < widths.length; i++) {
            float tw = engine.width(titles[i], JXTextEngine.DEFAULT_SIZE);
            float left = cx + Math.max(4, (widths[i] - tw) / 2);
            p.clip(cx, y, widths[i], h);
            p.text(titles[i], left, y + engine.baseline(h, JXTextEngine.DEFAULT_SIZE), JXTextEngine.DEFAULT_SIZE, true, TEXT);
            p.restore();
            cx += widths[i];
            p.fillRect(cx - 1, y, 1, h, BOX_BORDER);
        }
        p.restore();
    }

    // ---- text helpers ------------------------------------------------------------------------

    /** A labeled control's text inside its padding, aligned horizontally, vertically centred. */
    private void labeledText(JXNativeNode node, String value, float x, float y, float w, float h, char align, float[] pad) {
        if (value.isEmpty()) {
            return;
        }
        float size = fontSize(node);
        boolean bold = bold(node);
        JXTextEngine engine = JXTextEngine.get(bold);
        float available = w - pad[1] - pad[3];
        String shown = JXTextLayout.ellipsize(value, available, size, bold);
        float tw = engine.width(shown, size);
        float left = x + pad[3];
        if (align == 'C') {
            left += (available - tw) / 2;
        } else if (align == 'R') {
            left += available - tw;
        }
        boolean wrap = Boolean.TRUE.equals(node.getProperty("wrapText"));
        int color = color(node, "tooltip".equals(node.getType()) ? 0xFFFFFFFF : TEXT);
        if (wrap && engine.width(value, size) > available + 0.01f) {
            List<String> lines = JXTextLayout.lines(value, available, size, bold);
            float line = engine.lineHeight(size);
            float top = y + pad[0] + Math.max(0, (h - pad[0] - pad[2] - lines.size() * line) / 2);
            for (int i = 0; i < lines.size(); i++) {
                String shownLine = lines.get(i).replaceAll("\\s+$", "");
                float lw = engine.width(shownLine, size);
                float lx = x + pad[3] + (align == 'C' ? (available - lw) / 2 : align == 'R' ? available - lw : 0);
                p.text(shownLine, lx, top + i * line + engine.ascent(size), size, bold, color);
            }
            return;
        }
        float innerTop = y + pad[0];
        float innerH = h - pad[0] - pad[2];
        char valign = alignmentV(node, 'C');
        float lineH = engine.lineHeight(size);
        float baseline = valign == 'T' ? innerTop + engine.ascent(size)
                : valign == 'B' ? innerTop + innerH - lineH + engine.ascent(size)
                : innerTop + engine.baseline(innerH, size);
        p.text(shown, left, baseline, size, bold, color);
        if (Boolean.TRUE.equals(node.getProperty("underline"))) {
            p.fillRect(left, baseline + 2, tw, 1, color);
        }
    }

    /** One line of text from x, vertically centred in the box. */
    void drawText(JXNativeNode node, String value, float x, float boxTop, float boxHeight, int color, boolean underline) {
        if (value == null || value.isEmpty()) {
            return;
        }
        float size = fontSize(node);
        boolean bold = bold(node);
        JXTextEngine engine = JXTextEngine.get(bold);
        float baseline = boxTop + engine.baseline(boxHeight, size);
        p.text(value, x, baseline, size, bold, color);
        if (underline) {
            p.fillRect(x, baseline + 2, engine.width(value, size), 1, color);
        }
    }

    static float fontSize(JXNativeNode node) {
        Object size = node.getProperty("fontSize");
        return size instanceof Number && ((Number) size).doubleValue() > 0 ? ((Number) size).floatValue() : JXTextEngine.DEFAULT_SIZE;
    }

    static boolean bold(JXNativeNode node) {
        return Boolean.TRUE.equals(node.getProperty("bold"));
    }

    static int color(JXNativeNode node, int fallback) {
        Object fill = node.getProperty("textFill");
        return fill instanceof Integer ? (Integer) fill : fallback;
    }

    private static char alignmentH(JXNativeNode node, char fallback) {
        Object a = node.getProperty("textAlignment");
        if (a == null) {
            a = node.getProperty("alignment");
        }
        if (!(a instanceof String)) {
            return fallback;
        }
        String s = ((String) a).toUpperCase();
        if (s.endsWith("CENTER") || s.equals("CENTER") || s.equals("BASELINE_CENTER")) {
            return s.startsWith("CENTER_LEFT") ? 'L' : s.startsWith("CENTER_RIGHT") ? 'R' : 'C';
        }
        if (s.endsWith("RIGHT")) {
            return 'R';
        }
        return 'L';
    }

    private static char alignmentV(JXNativeNode node, char fallback) {
        Object a = node.getProperty("alignment");
        if (!(a instanceof String)) {
            return fallback;
        }
        String s = ((String) a).toUpperCase();
        return s.startsWith("TOP") ? 'T' : s.startsWith("BOTTOM") ? 'B' : 'C';
    }

    static float[] padding(JXNativeNode node) {
        double[] pad = node.padding();
        return new float[] {(float) pad[0], (float) pad[1], (float) pad[2], (float) pad[3]};
    }

    /** The node's padding if set, else the control default (top/bottom, left/right). */
    static float[] insetsOr(JXNativeNode node, float vertical, float horizontal) {
        if (node.getProperty("padding") != null) {
            return padding(node);
        }
        return new float[] {vertical, horizontal, vertical, horizontal};
    }

    static String text(JXNativeNode node, String property) {
        Object value = node.getProperty(property);
        return value == null ? "" : String.valueOf(value);
    }

    static double number(JXNativeNode node, String property, double fallback) {
        Object value = node.getProperty(property);
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }

    static String[] strings(Object value) {
        if (value instanceof String[]) {
            return (String[]) value;
        }
        if (value instanceof Object[]) {
            Object[] objects = (Object[]) value;
            String[] out = new String[objects.length];
            for (int i = 0; i < objects.length; i++) {
                out[i] = String.valueOf(objects[i]);
            }
            return out;
        }
        return new String[0];
    }

    static String bullets(int count) {
        StringBuilder s = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            s.append('●');
        }
        return s.toString();
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    JXPainter painter() {
        return p;
    }
}
