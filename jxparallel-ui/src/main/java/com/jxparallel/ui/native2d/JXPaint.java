package com.jxparallel.ui.native2d;

import java.util.ArrayList;
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

    /** The theme set by the nearest ancestor with a {@code theme} prop; null when none sets one. */
    private JXTheme theme;

    JXTheme theme() {
        return theme != null ? theme : JXTheme.LIGHT;
    }

    /** How deep {@link #paint} is called from itself: 0 outside the outermost call, which draws the overlays last. */
    private int depth;
    /** The node the outermost {@link #paint} was given: the window, for the scrim of a dialog. */
    private JXNativeNode overlayRoot;
    /** Open overlays met while painting, with the theme at their place in the tree: {node, theme}. */
    private final List<Object[]> deferred = new ArrayList<Object[]>();

    /**
     * Paints the node and everything below it. The open overlays met on the way (popups, menus, dialogs, drawers, snack
     * bars: {@link JXOverlays}) are not drawn in their place but after the whole tree, in tree order, so that they are
     * over everything.
     */
    void paint(JXNativeNode node) {
        boolean outermost = depth == 0;
        if (outermost) {
            deferred.clear(); // a frame object is reused by the next frame
            overlayRoot = node;
            boolean sized = node.getHeight() > 0; // the root is the window: nothing outside it is seen
            clipTop = sized ? node.getY() : Float.NEGATIVE_INFINITY;
            clipBottom = sized ? node.getY() + node.getHeight() : Float.POSITIVE_INFINITY;
        }
        depth++;
        try {
            paintNode(node);
        } finally {
            depth--;
        }
        if (outermost) {
            paintOverlays();
        }
    }

    private void paintOverlays() {
        JXTheme saved = theme;
        depth++; // what an overlay paints below itself is nested, not a frame of its own
        try {
            for (int i = 0; i < deferred.size(); i++) { // an overlay inside an overlay is added while this runs
                Object[] entry = deferred.get(i);
                theme = (JXTheme) entry[1];
                JXOverlayPaint.paint(this, (JXNativeNode) entry[0], overlayRoot);
            }
        } finally {
            depth--;
            theme = saved;
            deferred.clear();
        }
    }

    private void paintNode(JXNativeNode node) {
        if (Boolean.TRUE.equals(node.getProperty("hidden"))) {
            return; // invisible but managed: keeps its place in the layout, like JavaFX
        }
        JXTheme outer = theme;
        Object named = node.getProperty("theme");
        if (named instanceof String) {
            theme = JXTheme.named((String) named);
        }
        try {
            paintThemed(node);
        } finally {
            theme = outer;
        }
    }

    private void paintThemed(JXNativeNode node) {
        if (node.floatingNode) {
            if (JXOverlays.isOpen(node)) {
                deferred.add(new Object[] {node, theme}); // drawn after the tree; it takes no room where it is written
            }
            return;
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
        if (node.overlayHost && JXOverlays.canOpen(node) && JXOverlays.isOpen(node)) {
            deferred.add(new Object[] {node, theme}); // its popup or menu, over everything
        }
        if (layered) {
            p.restore();
        }
    }

    /** What an overlay paints for a node of its content (a dialog's column, say). */
    void paintNested(JXNativeNode node) {
        paint(node);
    }

    /** CSS-like background and border of any node. */
    private void region(JXNativeNode node) {
        Object bg = node.getProperty("background");
        Object border = node.getProperty("borderColor");
        if (!(bg instanceof Integer) && node.getProperty("theme") instanceof String) {
            p.fillRect(node.getX(), node.getY(), node.getWidth(), node.getHeight(), theme().surface); // a themed root
        }
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

    /**
     * A node that is this far beyond the window (in pixels) is still painted: what it draws outside its own box, such as
     * a shadow, a focus ring or a line of text taller than the box, is not far from it.
     */
    private static final float CULL_MARGIN = 32;

    /** Tests turn it off to compare a frame with and without the skipped nodes; the pixels must be the same. */
    static boolean cull = true;

    /** The part of the window the children being painted can reach: the nearest ancestor's clip, top and bottom. */
    private float clipTop = Float.NEGATIVE_INFINITY;
    private float clipBottom = Float.POSITIVE_INFINITY;

    private static boolean scrolls(String type) {
        return "scroll".equals(type) || "list".equals(type) || "table".equals(type);
    }

    /**
     * True when {@code node} and everything it paints are above or below the clip, so painting it changes no pixel.
     * Only the vertical extent is looked at (text that is longer than its box runs sideways, not up or down); a node
     * that opens over the window is never skipped, and a container is skipped only when none of its descendants,
     * which may lie outside its own box, reaches the clip.
     */
    private boolean outside(JXNativeNode node) {
        if (!cull || node.floatingNode || node.overlayHost || node.overlayContent) {
            return false;
        }
        float top = Math.min(node.getY(), node.getY() + node.getHeight());
        float bottom = Math.max(node.getY(), node.getY() + node.getHeight());
        if (bottom + CULL_MARGIN >= clipTop && top - CULL_MARGIN <= clipBottom) {
            return false;
        }
        if (node.getChildren().isEmpty()) {
            return true;
        }
        float[] reach = {top, bottom};
        reach(node, reach);
        return reach[1] + CULL_MARGIN < clipTop || reach[0] - CULL_MARGIN > clipBottom;
    }

    /** Widens {@code range} (top, bottom) to what the children of {@code node} paint, as {@link #children} paints them. */
    private static void reach(JXNativeNode node, float[] range) {
        String type = node.getType();
        if (node.collapsed() || node.overlayContent || scrolls(type) || "textarea".equals(type)
                || Boolean.TRUE.equals(node.getProperty("clip"))) {
            return; // nothing of the children is drawn outside the node's own box
        }
        List<JXNativeNode> kids = node.getChildren();
        for (int i = 0, n = kids.size(); i < n; i++) {
            JXNativeNode kid = kids.get(i);
            if (kid.floatingNode) {
                continue; // painted over the window by the overlay pass, not in place
            }
            range[0] = Math.min(range[0], Math.min(kid.getY(), kid.getY() + kid.getHeight()));
            range[1] = Math.max(range[1], Math.max(kid.getY(), kid.getY() + kid.getHeight()));
            reach(kid, range);
        }
    }

    private void children(JXNativeNode node) {
        if (node.collapsed() || node.overlayContent) {
            return; // only the title bar of a collapsed pane shows; the children of a menu are drawn with its panel
        }
        String type = node.getType();
        boolean clip = Boolean.TRUE.equals(node.getProperty("clip"));
        float[] viewport = null;
        if (scrolls(type)) {
            viewport = JXControlLayout.viewport(node);
        } else if ("textarea".equals(type)) {
            viewport = new float[] {node.getX() + 1, node.getY() + 1, node.getWidth() - 2, node.getHeight() - 2};
        }
        float savedTop = clipTop;
        float savedBottom = clipBottom;
        if (viewport != null) {
            p.clip(viewport[0], viewport[1], viewport[2], viewport[3]);
            clipTop = Math.max(clipTop, viewport[1]);
            clipBottom = Math.min(clipBottom, viewport[1] + viewport[3]);
        } else if (clip) {
            p.clip(node.getX(), node.getY(), node.getWidth(), node.getHeight());
            clipTop = Math.max(clipTop, node.getY());
            clipBottom = Math.min(clipBottom, node.getY() + node.getHeight());
        }
        List<JXNativeNode> kids = node.getChildren();
        for (int i = 0, n = kids.size(); i < n; i++) {
            JXNativeNode kid = kids.get(i);
            if (!outside(kid)) {
                paint(kid); // indexed: no iterator per node per frame
            }
        }
        clipTop = savedTop;
        clipBottom = savedBottom;
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
        if ("badge".equals(type)) {
            badgeMark(node);
        } else if ("splitpane".equals(type)) {
            dividers(node);
        }
    }

    // ---- Material controls -------------------------------------------------------------------

    /** Fill, border and ring of a button variant; returns the color its label and graphic take. */
    private int variantBody(JXNativeNode node, String variant, float x, float y, float w, float h) {
        JXTheme t = theme();
        float ring = JXControlLayout.shadow(node);
        float fx = x + ring;
        float fy = y + ring;
        float fw = w - 2 * ring;
        float fh = h - 2 * ring;
        float r = Boolean.TRUE.equals(node.getProperty("round")) || "fab".equals(variant) ? fh / 2 : 4;
        int content;
        switch (variant) {
            case "filled":
                p.fillRoundRect(fx, fy, fw, fh, r, t.primary);
                content = t.onPrimary;
                break;
            case "tonal":
                p.fillRoundRect(fx, fy, fw, fh, r, t.tonal());
                content = t.onSurface;
                break;
            case "outlined":
                p.strokeRoundRect(fx, fy, fw, fh, r, 1, t.outline);
                content = t.primary;
                break;
            case "elevated":
                softRing(fx, fy, fw, fh, r);
                p.fillRoundRect(fx, fy, fw, fh, r, t.elevated());
                content = t.primary;
                break;
            case "fab":
                softRing(fx, fy, fw, fh, r);
                p.fillRoundRect(fx, fy, fw, fh, r, t.primary);
                content = t.onPrimary;
                break;
            default: // text
                content = t.primary;
                break;
        }
        if (Boolean.TRUE.equals(node.getProperty("pressed"))) {
            p.fillRoundRect(fx, fy, fw, fh, r, JXTheme.withAlpha(content, 0x24));
        } else if (Boolean.TRUE.equals(node.getProperty("hover"))) {
            p.fillRoundRect(fx, fy, fw, fh, r, JXTheme.withAlpha(content, 0x14));
        }
        focusRing(node, fx, fy, fw, fh, r);
        return content;
    }

    /** No real blur is available to every painter, so the shadow is two soft darker rings below the face. */
    private void softRing(float x, float y, float w, float h, float r) {
        p.fillRoundRect(x - 2, y - 1, w + 4, h + 4, r + 2, 0x0F000000);
        p.fillRoundRect(x - 1, y, w + 2, h + 2, r + 1, 0x18000000);
    }

    private void switchControl(JXNativeNode node, float x, float y, float h) {
        JXTheme t = theme();
        float top = y + Math.round((h - JXControlLayout.SWITCH_H) / 2f);
        float tw = JXControlLayout.SWITCH_W;
        float th = JXControlLayout.SWITCH_H;
        if (Boolean.TRUE.equals(node.getProperty("checked"))) {
            p.fillRoundRect(x, top, tw, th, th / 2, t.primary);
            p.fillOval(x + tw - 3 - 16, top + 3, 16, 16, t.onPrimary);
        } else {
            p.fillRoundRect(x, top, tw, th, th / 2, t.surface);
            p.strokeRoundRect(x, top, tw, th, th / 2, 2, t.outline);
            p.fillOval(x + 5, top + 5, 12, 12, t.outline);
        }
        focusRing(node, x, top, tw, th, th / 2);
        drawText(node, text(node, "label"), x + tw + JXControlLayout.SWITCH_GAP, y, h, color(node, t.onSurface),
                Boolean.TRUE.equals(node.getProperty("underline")));
    }

    private void chip(JXNativeNode node, float x, float y, float w, float h) {
        JXTheme t = theme();
        String kind = text(node, "kind");
        boolean selected = Boolean.TRUE.equals(node.getProperty("selected"));
        // assist: outlined, 8 px corners; filter: outlined pill; choice: outlined, 4 px corners; input: 8 px corners on a tinted face
        float r = "filter".equals(kind) ? h / 2 : "choice".equals(kind) ? 4 : 8;
        int content = t.onSurface;
        if ("input".equals(kind) && !selected) {
            p.fillRoundRect(x, y, w, h, r, t.elevated());
        }
        if (selected && "choice".equals(kind)) {
            p.fillRoundRect(x, y, w, h, r, t.primary);
            content = t.onPrimary;
        } else if (selected) {
            p.fillRoundRect(x, y, w, h, r, t.tonal());
        } else {
            p.strokeRoundRect(x, y, w, h, r, 1, t.outline);
        }
        if (Boolean.TRUE.equals(node.getProperty("pressed"))) {
            p.fillRoundRect(x, y, w, h, r, JXTheme.withAlpha(content, 0x24));
        } else if (Boolean.TRUE.equals(node.getProperty("hover"))) {
            p.fillRoundRect(x, y, w, h, r, JXTheme.withAlpha(content, 0x14));
        }
        float[] parts = JXControlLayout.chipParts(node);
        String lead = JXControlLayout.chipLeading(node);
        int iconColor = selected && "choice".equals(kind) ? t.onPrimary : t.primary;
        if (lead != null) {
            JXIcons.draw(p, lead, x + parts[0], y + (h - JXControlLayout.CHIP_ICON) / 2f, JXControlLayout.CHIP_ICON, iconColor);
        }
        drawText(node, text(node, "label"), x + parts[2], y, h, color(node, content), false);
        if (parts[5] > 0) {
            JXIcons.draw(p, "close", x + parts[4], y + (h - JXControlLayout.CHIP_ICON) / 2f, JXControlLayout.CHIP_ICON, content);
        }
        focusRing(node, x, y, w, h, r);
    }

    /** Pixels masked to a circle, by the identity of the source array. */
    private static final java.util.Map<int[], int[]> ROUND = new java.util.WeakHashMap<int[], int[]>();

    /** The image with everything outside its inscribed ellipse made transparent (a 1 px soft edge). */
    static int[] circular(int[] pixels, int w, int h) {
        synchronized (ROUND) {
            int[] cached = ROUND.get(pixels);
            if (cached != null && cached.length == w * h) {
                return cached;
            }
            int[] out = new int[w * h];
            double rx = w / 2.0;
            double ry = h / 2.0;
            for (int j = 0; j < h; j++) {
                for (int i = 0; i < w; i++) {
                    int src = j * w + i < pixels.length ? pixels[j * w + i] : 0;
                    double dx = (i + 0.5 - rx) / rx;
                    double dy = (j + 0.5 - ry) / ry;
                    double edge = (1 - Math.sqrt(dx * dx + dy * dy)) * Math.min(rx, ry); // pixels inside the edge
                    double cover = Math.max(0, Math.min(1, edge + 0.5));
                    int a = (int) Math.round((src >>> 24) * cover);
                    out[j * w + i] = (a << 24) | (src & 0xFFFFFF);
                }
            }
            ROUND.put(pixels, out);
            return out;
        }
    }

    private void avatar(JXNativeNode node, float x, float y, float w, float h) {
        JXTheme t = theme();
        Object pixels = node.getProperty("pixels");
        int iw = (int) number(node, "imageWidth", 0);
        int ih = (int) number(node, "imageHeight", 0);
        if (pixels instanceof int[] && iw > 0 && ih > 0) {
            p.image(circular((int[]) pixels, iw, ih), iw, ih, x, y, w, h);
            return;
        }
        Object fill = node.getProperty("fill");
        p.fillOval(x, y, w, h, fill instanceof Integer ? (Integer) fill : t.tonal());
        String initials = text(node, "initials");
        if (!initials.isEmpty()) {
            float size = Math.max(8, Math.min(w, h) * 0.4f);
            JXTextEngine engine = JXTextEngine.get(false);
            float tw = engine.width(initials, size);
            p.text(initials, x + (w - tw) / 2, y + engine.baseline(h, size), size, false, color(node, t.primary));
        }
    }

    private void stepper(JXNativeNode node, float x, float y, float w, float h) {
        JXTheme t = theme();
        int n = JXControlLayout.stepCount(node);
        if (n == 0) {
            return;
        }
        String[] titles = JXControlLayout.stepTitles(node);
        int current = (int) number(node, "current", 0);
        float slot = w / n;
        float d = JXControlLayout.STEP_D;
        float cy = y + d / 2;
        JXTextEngine engine = JXTextEngine.get(false);
        for (int i = 0; i < n; i++) {
            float cx = x + slot * i + slot / 2;
            if (i + 1 < n) { // the line to the next circle
                p.fillRect(cx + d / 2 + 4, cy - 1, slot - d - 8, 2, i < current ? t.primary : t.outline);
            }
            if (i < current) {
                p.fillOval(cx - d / 2, y, d, d, t.primary);
                p.line(cx - 5, cy, cx - 1.5f, cy + 3.5f, 2, t.onPrimary);
                p.line(cx - 1.5f, cy + 3.5f, cx + 5, cy - 3.5f, 2, t.onPrimary);
            } else if (i == current) {
                p.fillOval(cx - d / 2, y, d, d, t.primary);
                number(engine, String.valueOf(i + 1), cx, y, d, t.onPrimary);
            } else {
                p.strokeOval(cx - d / 2, y, d, d, 2, t.outline);
                number(engine, String.valueOf(i + 1), cx, y, d, t.outline);
            }
            if (i < titles.length) {
                float tw = engine.width(titles[i], JXTextEngine.DEFAULT_SIZE);
                p.text(titles[i], cx - tw / 2, y + d + 8 + engine.ascent(JXTextEngine.DEFAULT_SIZE), JXTextEngine.DEFAULT_SIZE,
                        false, i == current ? t.primary : t.onSurface);
            }
        }
    }

    private void number(JXTextEngine engine, String label, float cx, float y, float d, int color) {
        float size = 12;
        p.text(label, cx - engine.width(label, size) / 2, y + engine.baseline(d, size), size, false, color);
    }

    private void scrollBar(JXNativeNode node, float x, float y, float w, float h) {
        JXTheme t = theme();
        boolean vertical = node.vertical();
        float[] g = JXControlLayout.scrollBarGeometry(node);
        float r = (vertical ? w : h) / 2;
        p.fillRoundRect(x, y, w, h, r, JXTheme.withAlpha(t.outline, 0x33));
        int thumb = Boolean.TRUE.equals(node.getProperty("pressed")) ? t.primary : t.outline;
        if (vertical) {
            p.fillRoundRect(x, g[2], w, g[3], r, thumb);
        } else {
            p.fillRoundRect(g[2], y, g[3], h, r, thumb);
        }
    }

    private void tabBar(JXNativeNode node, float x, float y, float w, float h) {
        JXTheme t = theme();
        String[] titles = strings(node.getProperty("titles"));
        int selected = (int) number(node, "selected", 0);
        float[] edges = JXControlLayout.tabbarEdges(node);
        boolean closable = Boolean.TRUE.equals(node.getProperty("closable"));
        p.fillRect(x, y + h - 1, w, 1, JXTheme.withAlpha(t.outline, 0x66));
        JXTextEngine engine = JXTextEngine.get(false);
        for (int i = 0; i < titles.length; i++) {
            float tx = x + edges[i];
            float tw = edges[i + 1] - edges[i];
            int color = i == selected ? t.primary : t.onSurface;
            float room = tw - 2 * JXControlLayout.TABBAR_PAD - (closable ? JXControlLayout.TABBAR_CLOSE : 0);
            float textW = engine.width(titles[i], JXTextEngine.DEFAULT_SIZE);
            p.text(titles[i], tx + JXControlLayout.TABBAR_PAD + (room - textW) / 2, y + engine.baseline(h, JXTextEngine.DEFAULT_SIZE),
                    JXTextEngine.DEFAULT_SIZE, false, color);
            if (closable) {
                JXIcons.draw(p, "close", tx + tw - JXControlLayout.TABBAR_PAD / 2f - 14 - 2, y + (h - 14) / 2, 14, color);
            }
            if (i == selected) {
                p.fillRect(tx, y + h - 2, tw, 2, t.primary);
            }
        }
    }

    private void badgeMark(JXNativeNode node) {
        String text = JXControlLayout.badgeText(node);
        if (text == null) {
            return;
        }
        JXTheme t = theme();
        float[] size = JXControlLayout.badgeSize(node);
        float bx = node.getX() + node.getWidth() - size[0];
        float by = node.getY();
        Object custom = node.getProperty("badgeColor");
        int fill = custom instanceof Integer ? (Integer) custom : t.error;
        if (text.isEmpty()) {
            p.fillOval(bx, by, size[0], size[1], fill);
            return;
        }
        p.fillRoundRect(bx, by, size[0], size[1], size[1] / 2, fill);
        JXTextEngine engine = JXTextEngine.get(false);
        p.text(text, bx + (size[0] - engine.width(text, 11)) / 2, by + engine.baseline(size[1], 11), 11, false, t.surface);
    }

    private void dividers(JXNativeNode node) {
        JXTheme t = theme();
        float[][] rects = JXControlLayout.splitRects(node);
        boolean vertical = node.vertical();
        for (int i = node.getChildren().size(); i < rects.length; i++) {
            float[] r = rects[i];
            p.fillRect(r[0], r[1], r[2], r[3], JXTheme.withAlpha(t.outline, 0x33));
            float cx = r[0] + r[2] / 2;
            float cy = r[1] + r[3] / 2;
            for (int k = -1; k <= 1; k++) { // three grip dots
                float dx = vertical ? k * 6 : 0;
                float dy = vertical ? 0 : k * 6;
                p.fillOval(cx + dx - 1, cy + dy - 1, 2, 2, t.outline);
            }
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
            case "toggle": {
                String variant = JXControlLayout.variant(node);
                int textColor = TEXT;
                if (variant.isEmpty()) {
                    buttonBody(node, x, y, w, h, 3, Boolean.TRUE.equals(node.getProperty("selected")));
                } else {
                    textColor = variantBody(node, variant, x, y, w, h);
                }
                float[] pad = JXControlLayout.buttonPad(node);
                if (node.getChildren().isEmpty()) {
                    if (variant.isEmpty()) {
                        labeledText(node, text(node, "label"), x, y, w, h, alignmentH(node, 'C'), pad);
                    } else {
                        // the label takes the variant's color unless textFill is written
                        labeledTextColored(node, x, y, w, h, pad, color(node, textColor));
                    }
                } else {
                    float[] group = JXControlLayout.buttonContent(node);
                    String shown = JXTextLayout.ellipsize(text(node, "label"), x + w - pad[1] - group[1], fontSize(node), bold(node));
                    drawText(node, shown, group[1], y, h, color(node, textColor), Boolean.TRUE.equals(node.getProperty("underline")));
                }
                break;
            }
            case "switch":
                switchControl(node, x, y, h);
                break;
            case "icon": {
                float size = (float) JXControlLayout.iconSize(node);
                Object color = node.getProperty("color");
                JXIcons.draw(p, text(node, "name"), x + (w - size) / 2, y + (h - size) / 2, size,
                        color instanceof Integer ? (Integer) color : theme().onSurface);
                break;
            }
            case "chip":
                chip(node, x, y, w, h);
                break;
            case "avatar":
                avatar(node, x, y, w, h);
                break;
            case "stepper":
                stepper(node, x, y, w, h);
                break;
            case "scrollbar":
                scrollBar(node, x, y, w, h);
                break;
            case "tabbar":
                tabBar(node, x, y, w, h);
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
            case "timepicker":
                timePicker(node, x, y, w, h);
                break;
            case "colorpicker":
                colorPicker(node, x, y, w, h);
                break;
            case "menubutton":
                menuButton(node, x, y, w, h);
                break;
            case "menubar":
                menuBar(node, x, y, w, h);
                break;
            default:
                break;
        }
    }

    // ---- pickers, menu button and menu bar (the popups and panels are JXOverlayPaint's) ------------

    private void timePicker(JXNativeNode node, float x, float y, float w, float h) {
        field(node, x, y, w, h);
        float bw = JXControlLayout.DATE_BUTTON;
        String shown = JXOverlays.timeText(node);
        p.clip(x + 1, y, w - bw - 1, h);
        if (shown.isEmpty()) {
            drawText(node, text(node, "prompt"), x + JXNativeNode.FIELD_PAD_X, y, h, PROMPT, false);
        } else {
            drawText(node, shown, x + JXNativeNode.FIELD_PAD_X, y, h, color(node, TEXT), false);
        }
        p.restore();
        buttonBody(node, x + w - bw, y, bw, h, 0, Boolean.TRUE.equals(node.getProperty("showing")));
        float cx = x + w - bw / 2;
        float cy = y + h / 2;
        p.strokeOval(cx - 5, cy - 5, 10, 10, 1.5f, ARROW);
        p.line(cx, cy, cx, cy - 3, 1.2f, ARROW);
        p.line(cx, cy, cx + 2.5f, cy + 1.5f, 1.2f, ARROW);
    }

    private void colorPicker(JXNativeNode node, float x, float y, float w, float h) {
        field(node, x, y, w, h);
        float bw = JXControlLayout.DATE_BUTTON;
        String hex = JXOverlays.normalizeHex(text(node, "value"));
        float s = JXControlLayout.COLOR_SWATCH;
        float sx = x + JXNativeNode.FIELD_PAD_X;
        float sy = y + (h - s) / 2;
        p.fillRect(sx - 1, sy - 1, s + 2, s + 2, BOX_BORDER);
        p.fillRect(sx, sy, s, s, hex == null ? 0xFFFFFFFF : JXOverlays.colorOf(hex));
        float left = sx + s + JXNativeNode.FIELD_PAD_X;
        p.clip(left, y + 1, x + w - bw - left, h - 2);
        if (hex == null) {
            drawText(node, text(node, "prompt"), left, y, h, PROMPT, false);
        } else {
            drawText(node, hex, left, y, h, color(node, TEXT), false);
        }
        p.restore();
        buttonBody(node, x + w - bw, y, bw, h, 0, Boolean.TRUE.equals(node.getProperty("showing")));
        downArrow(x + w - bw + (bw - 8) / 2, y + (h - 4) / 2, ARROW);
    }

    private void menuButton(JXNativeNode node, float x, float y, float w, float h) {
        buttonBody(node, x, y, w, h, 3, JXOverlays.isOpen(node));
        p.clip(x, y, w - JXControlLayout.MENU_ARROW, h);
        drawText(node, text(node, "label"), x + JXNativeNode.PAD_X, y, h, color(node, TEXT), false);
        p.restore();
        downArrow(x + w - JXNativeNode.PAD_X - 8, y + Math.round((h - 4) / 2), ARROW);
    }

    private void menuBar(JXNativeNode node, float x, float y, float w, float h) {
        JXTheme t = theme();
        p.fillRect(x, y, w, h, t.surface);
        p.fillRect(x, y + h - 1, w, 1, JXTheme.withAlpha(t.outline, 0x66));
        List<JXNativeNode> headers = node.getChildren();
        for (int i = 0; i < headers.size(); i++) {
            JXNativeNode header = headers.get(i);
            if (i == node.openChild) {
                p.fillRoundRect(header.getX(), header.getY() + 2, header.getWidth(), header.getHeight() - 4, 4, t.tonal());
            }
            int color = Boolean.TRUE.equals(header.getProperty("disabled")) ? JXTheme.withAlpha(t.onSurface, 0x66) : t.onSurface;
            drawText(header, JXOverlays.label(header), header.getX() + JXOverlays.BAR_PAD, y, h, color, false);
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
        float tree = (float) JXControlLayout.treeLeft(node);
        if (tree > 0) { // a tree row: the arrow of a row that expands, after its indentation
            String disclosure = text(node, "disclosure");
            if (!"leaf".equals(disclosure)) {
                float[] pad = insetsOr(node, 3, 7);
                float ax = x + pad[3] + (float) number(node, "indent", 0) * JXControlLayout.TREE_INDENT + 4;
                float cy = y + h / 2;
                int arrow = selected && focusedList ? 0xFFFFFFFF : ARROW;
                if ("expanded".equals(disclosure)) {
                    p.fillTriangle(ax - 1, cy - 2, ax + 7, cy - 2, ax + 3, cy + 3, arrow);
                } else {
                    p.fillTriangle(ax, cy - 4, ax, cy + 4, ax + 5, cy, arrow);
                }
            }
        }
        if (!value.isEmpty()) {
            float[] pad = insetsOr(node, 3, 7);
            float graphic = node.getChildren().isEmpty() ? 0 : node.getChildren().get(0).getWidth();
            int textColor = selected && focusedList ? 0xFFFFFFFF : color(node, TEXT);
            p.clip(x, y, w, h);
            float left = x + pad[3] + tree + (graphic > 0 ? graphic + 4 : 0);
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
        labeledText(node, value, x, y, w, h, align, pad, null);
    }

    /** The label of a variant button, in the variant's color. */
    private void labeledTextColored(JXNativeNode node, float x, float y, float w, float h, float[] pad, int fallback) {
        labeledText(node, text(node, "label"), x, y, w, h, 'C', pad, fallback);
    }

    private void labeledText(JXNativeNode node, String value, float x, float y, float w, float h, char align, float[] pad,
                             Integer fallbackColor) {
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
        int color = color(node, fallbackColor != null ? fallbackColor
                : "tooltip".equals(node.getType()) ? 0xFFFFFFFF : theme != null ? theme.onSurface : TEXT);
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
