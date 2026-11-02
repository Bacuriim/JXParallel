package com.jxparallel.ui.native2d;

import java.util.List;

import com.jxparallel.ui.text.JXTextEngine;

/**
 * Draws the open overlays ({@link JXOverlays}) after the rest of the window, through the {@link JXPainter}
 * primitives that every painter implements: rectangles, rounded rectangles, lines, triangles and text. A shadow is two
 * translucent rings below the face (not every painter has a blur), the scrim is a translucent rectangle.
 */
final class JXOverlayPaint {
    static final int SCRIM = 0x66000000;
    private static final int SNACK_FACE = 0xFF322F35;
    private static final int SNACK_TEXT = 0xFFF5EFF7;

    private JXOverlayPaint() {
    }

    /** Draws {@code node}'s popup, menus or layer; {@code root} is the window. */
    static void paint(JXPaint paint, JXNativeNode node, JXNativeNode root) {
        JXPainter p = paint.painter();
        switch (node.getType()) {
            case "select":
                list(p, node);
                break;
            case "datepicker": {
                JXOverlays.Popup po = JXOverlays.popup(node);
                if (po != null && po.calendar != null) {
                    ring(p, po.x, po.y, po.w, po.h, 0);
                    JXCalendar.paint(p, po.calendar, paint);
                }
                break;
            }
            case "timepicker":
                time(p, node);
                break;
            case "colorpicker":
                colors(p, node);
                break;
            case "menubutton":
            case "contextmenu":
            case "menubar":
                menus(paint, p, node);
                break;
            case "dialog":
                dialog(paint, p, node, root);
                break;
            case "drawer":
                drawer(paint, p, node, root);
                break;
            case "snackbar":
                snackbar(paint, p, node);
                break;
            default:
                break;
        }
    }

    /** The soft darker rings of a raised surface, kept outside its bounds by 2 px. */
    static void ring(JXPainter p, float x, float y, float w, float h, float r) {
        p.fillRoundRect(x - 2, y - 1, w + 4, h + 4, r + 2, 0x0F000000);
        p.fillRoundRect(x - 1, y, w + 2, h + 2, r + 1, 0x18000000);
    }

    /** The classic (Modena) popup: a gray line around white. */
    private static void box(JXPainter p, int x, int y, int w, int h) {
        ring(p, x, y, w, h, 0);
        p.fillRect(x, y, w, h, JXPaint.OUTER_BORDER);
        p.fillRect(x + 1, y + 1, w - 2, h - 2, 0xFFFFFFFF);
    }

    private static void centered(JXPainter p, String label, int[] cell, int color) {
        JXTextEngine engine = JXTextEngine.get(false);
        float size = JXTextEngine.DEFAULT_SIZE;
        float tw = engine.width(label, size);
        p.text(label, cell[0] + (cell[2] - tw) / 2, cell[1] + engine.baseline(cell[3], size), size, false, color);
    }

    // ---- popups ----------------------------------------------------------------------------------

    private static void list(JXPainter p, JXNativeNode node) {
        JXOverlays.Popup po = JXOverlays.popup(node);
        if (po == null) {
            return;
        }
        box(p, po.x, po.y, po.w, po.h);
        String value = JXPaint.text(node, "value");
        JXTextEngine engine = JXTextEngine.get(false);
        float size = JXTextEngine.DEFAULT_SIZE;
        for (int i = 0; i < po.cells.length; i++) {
            int[] c = po.cells[i];
            boolean chosen = po.labels[i].equals(value);
            if (chosen) {
                p.fillRect(c[0], c[1], c[2], c[3], JXPaint.SELECTION);
            }
            p.text(po.labels[i], c[0] + JXNativeNode.PAD_X, c[1] + engine.baseline(c[3], size), size, false,
                    chosen ? 0xFFFFFFFF : JXPaint.TEXT);
        }
    }

    private static void time(JXPainter p, JXNativeNode node) {
        JXOverlays.Popup po = JXOverlays.popup(node);
        if (po == null) {
            return;
        }
        box(p, po.x, po.y, po.w, po.h);
        for (int i = 0; i < po.cells.length; i++) {
            int[] c = po.cells[i];
            boolean current = JXOverlays.timeCurrent(node, c);
            if (current) {
                p.fillRect(c[0], c[1], c[2], c[3], JXPaint.SELECTION);
            }
            centered(p, po.labels[i], c, current ? 0xFFFFFFFF : JXPaint.TEXT);
        }
    }

    private static void colors(JXPainter p, JXNativeNode node) {
        JXOverlays.Popup po = JXOverlays.popup(node);
        if (po == null) {
            return;
        }
        box(p, po.x, po.y, po.w, po.h);
        int current = JXOverlays.colorOf(JXPaint.text(node, "value"));
        for (int[] c : po.cells) {
            p.fillRect(c[0] - 1, c[1] - 1, c[2] + 2, c[3] + 2, JXPaint.BOX_BORDER);
            p.fillRect(c[0], c[1], c[2], c[3], c[5]);
            if (current != 0 && c[5] == current) {
                p.strokeRoundRect(c[0] - 2, c[1] - 2, c[2] + 4, c[3] + 4, 0, 2, JXPaint.FOCUS);
            }
        }
        int[] f = po.field;
        p.fillRect(f[0], f[1], f[2], f[3], JXPaint.BOX_BORDER);
        p.fillRect(f[0] + 1, f[1] + 1, f[2] - 2, f[3] - 2, 0xFFFFFFFF);
        String draft = JXOverlays.hexDraft(node);
        JXTextEngine engine = JXTextEngine.get(false);
        float size = JXTextEngine.DEFAULT_SIZE;
        float baseline = f[1] + engine.baseline(f[3], size);
        if (draft.isEmpty()) {
            p.text("#RRGGBB", f[0] + JXNativeNode.FIELD_PAD_X, baseline, size, false, JXPaint.PROMPT);
        } else {
            boolean complete = JXOverlays.validHex(draft);
            p.text(draft, f[0] + JXNativeNode.FIELD_PAD_X, baseline, size, false, complete ? JXPaint.TEXT : JXTheme.LIGHT.error);
            if (Boolean.TRUE.equals(node.getProperty("focused")) && !complete) {
                float cx = f[0] + JXNativeNode.FIELD_PAD_X + engine.width(draft, size);
                p.fillRect((float) Math.floor(cx), f[1] + (f[3] - engine.lineHeight(size)) / 2, 1, engine.lineHeight(size), JXPaint.TEXT);
            }
        }
    }

    // ---- menus ---------------------------------------------------------------------------------------

    private static void menus(JXPaint paint, JXPainter p, JXNativeNode node) {
        JXOverlays.Popup po = JXOverlays.popup(node);
        if (po == null) {
            return;
        }
        JXTheme t = paint.theme();
        JXTextEngine engine = JXTextEngine.get(false);
        float size = JXTextEngine.DEFAULT_SIZE;
        for (JXOverlays.Panel panel : po.panels) {
            ring(p, panel.x, panel.y, panel.w, panel.h, 4);
            p.fillRoundRect(panel.x, panel.y, panel.w, panel.h, 4, t.surface);
            p.strokeRoundRect(panel.x, panel.y, panel.w, panel.h, 4, 1, JXTheme.withAlpha(t.outline, 0x66));
            List<JXNativeNode> rows = panel.container.getChildren();
            for (int i = 0; i < rows.size(); i++) {
                JXNativeNode row = rows.get(i);
                float x = row.getX();
                float y = row.getY();
                float w = row.getWidth();
                float h = row.getHeight();
                if (JXOverlays.separator(row)) {
                    p.fillRect(x + 8, y + h / 2, w - 16, 1, JXTheme.withAlpha(t.outline, 0x66));
                    continue;
                }
                boolean disabled = Boolean.TRUE.equals(row.getProperty("disabled"));
                int color = disabled ? JXTheme.withAlpha(t.onSurface, 0x66) : t.onSurface;
                if (i == panel.container.openChild && "menu".equals(row.getType())) {
                    p.fillRect(x, y, w, h, t.tonal());
                }
                float baseline = y + engine.baseline(h, size);
                p.text(JXOverlays.label(row), x + JXOverlays.MENU_TEXT, baseline, size, false, color);
                String accelerator = JXPaint.text(row, "accelerator");
                if ("menu".equals(row.getType())) {
                    float cy = y + h / 2;
                    float ax = x + w - JXOverlays.MENU_TEXT - 4;
                    p.fillTriangle(ax - 4, cy - 4, ax - 4, cy + 4, ax + 1, cy, color);
                } else if (!accelerator.isEmpty()) {
                    p.text(accelerator, x + w - JXOverlays.MENU_TEXT - engine.width(accelerator, size), baseline, size, false,
                            JXTheme.withAlpha(color, disabled ? 0x66 : 0xAA));
                }
            }
        }
    }

    // ---- layers --------------------------------------------------------------------------------------

    private static void dialog(JXPaint paint, JXPainter p, JXNativeNode node, JXNativeNode root) {
        JXTheme t = paint.theme();
        p.fillRect(root.getX(), root.getY(), root.getWidth(), root.getHeight(), SCRIM);
        float x = node.getX();
        float y = node.getY();
        float w = node.getWidth();
        float h = node.getHeight();
        ring(p, x, y, w, h, 12);
        Object background = node.getProperty("background");
        p.fillRoundRect(x, y, w, h, 12, background instanceof Integer ? (Integer) background : t.surface);
        Object border = node.getProperty("borderColor");
        if (border instanceof Integer) {
            p.strokeRoundRect(x, y, w, h, 12, (float) JXPaint.number(node, "borderWidth", 1), (Integer) border);
        }
        content(paint, node);
    }

    private static void drawer(JXPaint paint, JXPainter p, JXNativeNode node, JXNativeNode root) {
        JXTheme t = paint.theme();
        p.fillRect(root.getX(), root.getY(), root.getWidth(), root.getHeight(), SCRIM);
        float x = node.getX();
        float y = node.getY();
        float w = node.getWidth();
        float h = node.getHeight();
        boolean right = "right".equalsIgnoreCase(JXPaint.text(node, "side"));
        ring(p, x, y, w, h, 0);
        Object background = node.getProperty("background");
        p.fillRect(x, y, w, h, background instanceof Integer ? (Integer) background : t.surface);
        p.fillRect(right ? x : x + w - 1, y, 1, h, JXTheme.withAlpha(t.outline, 0x66));
        content(paint, node);
    }

    /** The children of a dialog or drawer, drawn like any others. */
    private static void content(JXPaint paint, JXNativeNode node) {
        List<JXNativeNode> kids = node.getChildren();
        for (int i = 0; i < kids.size(); i++) {
            paint.paintNested(kids.get(i));
        }
    }

    private static void snackbar(JXPaint paint, JXPainter p, JXNativeNode node) {
        float x = node.getX();
        float y = node.getY();
        float w = node.getWidth();
        float h = node.getHeight();
        ring(p, x, y, w, h, 4);
        p.fillRoundRect(x, y, w, h, 4, SNACK_FACE);
        JXTextEngine engine = JXTextEngine.get(false);
        float size = JXTextEngine.DEFAULT_SIZE;
        float baseline = y + engine.baseline(h, size);
        p.clip(x, y, w, h);
        p.text(JXPaint.text(node, "message"), x + JXOverlays.SNACK_PAD, baseline, size, false, SNACK_TEXT);
        String action = JXPaint.text(node, "action");
        if (!action.isEmpty()) {
            p.text(action, x + w - JXOverlays.SNACK_PAD - engine.width(action, size), baseline, size, true, JXTheme.DARK.primary);
        }
        p.restore();
    }
}
