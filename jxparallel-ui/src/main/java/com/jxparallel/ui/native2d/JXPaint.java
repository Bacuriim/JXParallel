package com.jxparallel.ui.native2d;

import java.util.ArrayList;
import java.util.List;

import com.jxparallel.ui.text.JXTextEngine;

/**
 * Paints every element type in Material Design 3, through a {@link JXPainter}, so Skia, NanoVG and the
 * software painter draw the same thing. Every color is a role of the {@link JXTheme} in force (light or
 * dark), the sizes are the compact ones the layout fixes: only fills, strokes, corner radii, state layers
 * (hover, focus, pressed, selected, disabled) and elevation shadows are decided here.
 *
 * <p>Props every node understands: {@code background}, {@code backgroundRadius}, {@code borderColor},
 * {@code borderWidth}, {@code borderRadius} (a region's CSS background and border, ARGB ints),
 * {@code opacity}, {@code blur} (Gaussian blur radius, Skia only), {@code clip}, {@code hidden},
 * {@code disabled} (content at 38% of the on-surface color), and for text {@code textFill}, {@code fontSize},
 * {@code bold}, {@code underline}. Controls also read {@code focused}, {@code hover} and {@code pressed}.
 */
final class JXPaint {
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
        if (Boolean.TRUE.equals(node.getProperty("disabled")) && !paintsDisabledItself(node)) {
            opacity *= JXTheme.DISABLED_CONTENT / 255.0; // content at 38%
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

    /**
     * A button without a graphic child draws its disabled state as Material asks: the container at 12% and the label
     * at 38% of the on-surface color, not the whole button faded.
     */
    private static boolean paintsDisabledItself(JXNativeNode node) {
        String type = node.getType();
        return ("button".equals(type) || "toggle".equals(type)) && node.getChildren().isEmpty();
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
        reachTop = top;
        reachBottom = bottom;
        reach(node);
        return reachBottom + CULL_MARGIN < clipTop || reachTop - CULL_MARGIN > clipBottom;
    }

    /** What {@link #reach} has found so far (top, bottom): fields, so that a test of a node makes no array. */
    private float reachTop;
    private float reachBottom;

    /** Widens {@code reachTop}/{@code reachBottom} to what the children of {@code node} paint, as {@link #children} paints them. */
    private void reach(JXNativeNode node) {
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
            reachTop = Math.min(reachTop, Math.min(kid.getY(), kid.getY() + kid.getHeight()));
            reachBottom = Math.max(reachBottom, Math.max(kid.getY(), kid.getY() + kid.getHeight()));
            reach(kid);
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

    /** Corner radius of the border of a scroll pane, list or table. */
    private static final float CONTAINER_RADIUS = 4;

    /** Parts drawn over the children: scroll bars, table header, focus rings. */
    private void after(JXNativeNode node) {
        String type = node.getType();
        if ("scroll".equals(type) || "list".equals(type) || "table".equals(type)) {
            scrollBars(node);
        }
        if ("table".equals(type)) {
            tableHeader(node);
        }
        if (scrolls(type)) {
            // the border of the container: rounded, over the rows (which are clipped to the 1 px inside it)
            boolean focused = !"scroll".equals(type) && Boolean.TRUE.equals(node.getProperty("focused"));
            p.strokeRoundRect(node.getX(), node.getY(), node.getWidth(), node.getHeight(), CONTAINER_RADIUS, 1,
                    focused ? theme().primary : theme().outlineVariant);
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
        float r = fh / 2; // fully rounded
        boolean disabled = Boolean.TRUE.equals(node.getProperty("disabled")) && node.getChildren().isEmpty();
        int content;
        switch (variant) {
            case "filled":
                p.fillRoundRect(fx, fy, fw, fh, r, disabled ? disabledContainer(t) : t.primary);
                content = t.onPrimary;
                break;
            case "tonal":
                if (!disabled && Boolean.TRUE.equals(node.getProperty("hover")) && !Boolean.TRUE.equals(node.getProperty("pressed"))) {
                    elevation(p, fx, fy, fw, fh, r, 1); // M3: a tonal button rises to level 1 under the pointer, back to 0 pressed
                }
                p.fillRoundRect(fx, fy, fw, fh, r, disabled ? disabledContainer(t) : t.secondaryContainer);
                content = t.onPrimaryContainer;
                break;
            case "toggleOff": // an unselected toggle button
                p.strokeRoundRect(fx, fy, fw, fh, r, 1, disabled ? disabledContainer(t) : t.outline);
                content = t.onSurface;
                break;
            case "outlined":
                p.strokeRoundRect(fx, fy, fw, fh, r, 1, disabled ? disabledContainer(t) : t.outline);
                content = t.primary;
                break;
            case "elevated":
                if (!disabled) {
                    elevation(p, fx, fy, fw, fh, r, 1);
                }
                p.fillRoundRect(fx, fy, fw, fh, r, disabled ? disabledContainer(t) : t.surfaceContainerLow);
                content = t.primary;
                break;
            case "fab":
                if (!disabled) {
                    elevation(p, fx, fy, fw, fh, r, 1);
                }
                p.fillRoundRect(fx, fy, fw, fh, r, disabled ? disabledContainer(t) : t.primaryContainer);
                content = t.onPrimaryContainer;
                break;
            default: // text
                content = t.primary;
                break;
        }
        if (disabled) {
            return JXTheme.withAlpha(t.onSurface, JXTheme.DISABLED_CONTENT);
        }
        int layer = Boolean.TRUE.equals(node.getProperty("pressed")) ? JXTheme.PRESSED
                : Boolean.TRUE.equals(node.getProperty("focused")) ? JXTheme.FOCUS
                : Boolean.TRUE.equals(node.getProperty("hover")) ? JXTheme.HOVER : 0;
        if (layer > 0) {
            p.fillRoundRect(fx, fy, fw, fh, r, JXTheme.withAlpha(content, layer));
        }
        focusRing(node, fx, fy, fw, fh, r);
        return content;
    }

    private static int disabledContainer(JXTheme t) {
        return JXTheme.withAlpha(t.onSurface, JXTheme.DISABLED_CONTAINER);
    }

    /**
     * The variant a button without {@code variant} is drawn as: a tonal button, a filled one when it is the default
     * button, a toggle button outlined and, selected, filled.
     */
    private static String classicVariant(JXNativeNode node) {
        if ("toggle".equals(node.getType())) {
            return Boolean.TRUE.equals(node.getProperty("selected")) ? "filled" : "toggleOff";
        }
        return Boolean.TRUE.equals(node.getProperty("defaultButton")) ? "filled" : "tonal";
    }

    /**
     * An elevation shadow: a stack of faint rings growing from the face, the darkest next to it, shifted down. No real
     * blur is available to every painter. Level 1 stays within the 3 px the layout keeps around a raised button
     * (2 px at the sides and above, 3 px below); the most any level reaches is {@code 2 + 2 * level} px, well inside
     * {@link #CULL_MARGIN}.
     */
    static void elevation(JXPainter p, float x, float y, float w, float h, float r, int level) {
        int spread = level == 1 ? 2 : 2 + 2 * level;
        int alpha = level == 1 ? 10 : 6 + level;
        int dy = Math.min(level, 3);
        for (int i = spread; i >= 1; i--) {
            p.fillRoundRect(x - i, y - i + dy, w + 2 * i, h + 2 * i, r + i, alpha << 24);
        }
    }

    private void switchControl(JXNativeNode node, float x, float y, float h) {
        JXTheme t = theme();
        float top = y + Math.round((h - JXControlLayout.SWITCH_H) / 2f);
        float tw = JXControlLayout.SWITCH_W;
        float th = JXControlLayout.SWITCH_H;
        boolean on = Boolean.TRUE.equals(node.getProperty("checked"));
        int layer = pointerLayer(node);
        float thumbX;
        float thumbD;
        if (on) {
            p.fillRoundRect(x, top, tw, th, th / 2, t.primary);
            thumbD = 16;
            thumbX = x + tw - 3 - thumbD;
        } else {
            p.fillRoundRect(x, top, tw, th, th / 2, t.surfaceVariant);
            p.strokeRoundRect(x, top, tw, th, th / 2, 2, t.outline);
            thumbD = 12;
            thumbX = x + 5;
        }
        if (layer > 0) { // the state layer of the thumb
            float cx = thumbX + thumbD / 2;
            float cy = top + th / 2;
            p.fillOval(cx - 12, cy - 12, 24, 24, JXTheme.withAlpha(on ? t.primary : t.onSurface, layer));
        }
        p.fillOval(thumbX, top + (th - thumbD) / 2, thumbD, thumbD, on ? t.onPrimary : t.outline);
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
            p.fillRoundRect(x, y, w, h, r, t.secondaryContainer);
            content = t.onPrimaryContainer;
        } else {
            p.strokeRoundRect(x, y, w, h, r, 1, t.outlineVariant);
        }
        int layer = pointerLayer(node);
        if (layer > 0) {
            p.fillRoundRect(x, y, w, h, r, JXTheme.withAlpha(content, layer));
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
        p.fillOval(x, y, w, h, fill instanceof Integer ? (Integer) fill : t.primaryContainer);
        String initials = text(node, "initials");
        if (!initials.isEmpty()) {
            float size = Math.max(8, Math.min(w, h) * 0.4f);
            JXTextEngine engine = JXTextEngine.get(false);
            float tw = engine.width(initials, size);
            p.text(initials, x + (w - tw) / 2, y + engine.baseline(h, size), size, false, color(node, t.onPrimaryContainer));
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
        p.fillRoundRect(x, y, w, h, r, t.surfaceVariant);
        int thumb = Boolean.TRUE.equals(node.getProperty("pressed")) ? t.primary : t.onSurfaceVariant;
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
        p.fillRect(x, y + h - 1, w, 1, t.outlineVariant);
        JXTextEngine engine = JXTextEngine.get(false);
        for (int i = 0; i < titles.length; i++) {
            float tx = x + edges[i];
            float tw = edges[i + 1] - edges[i];
            int color = i == selected ? t.primary : t.onSurfaceVariant;
            float room = tw - 2 * JXControlLayout.TABBAR_PAD - (closable ? JXControlLayout.TABBAR_CLOSE : 0);
            float textW = engine.width(titles[i], JXTextEngine.DEFAULT_SIZE);
            p.text(titles[i], tx + JXControlLayout.TABBAR_PAD + (room - textW) / 2, y + engine.baseline(h, JXTextEngine.DEFAULT_SIZE),
                    JXTextEngine.DEFAULT_SIZE, false, color);
            if (closable) {
                JXIcons.draw(p, "close", tx + tw - JXControlLayout.TABBAR_PAD / 2f - 14 - 2, y + (h - 14) / 2, 14, color);
            }
            if (i == selected) {
                p.fillRoundRect(tx + 2, y + h - 3, tw - 4, 3, 1.5f, t.primary); // the indicator line
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
            p.fillRect(r[0], r[1], r[2], r[3], t.surfaceVariant);
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
                int textColor = variantBody(node, variant.isEmpty() ? classicVariant(node) : variant, x, y, w, h);
                float[] pad = JXControlLayout.buttonPad(node);
                if (node.getChildren().isEmpty()) {
                    // the label takes the variant's color unless textFill is written
                    labeledText(node, text(node, "label"), x, y, w, h, variant.isEmpty() ? alignmentH(node, 'C') : 'C', pad,
                            color(node, textColor));
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
                JXTheme t = theme();
                int color = Boolean.TRUE.equals(node.getProperty("visited")) ? JXTheme.mix(t.primary, t.error, 0.5f) : t.primary;
                float[] pad = insetsOr(node, 2, 3);
                drawText(node, text(node, "label"), x + pad[3] + 1, y, h, color(node, color),
                        Boolean.TRUE.equals(node.getProperty("hover")) || Boolean.TRUE.equals(node.getProperty("underline")));
                focusRing(node, x, y, w, h, 2);
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
            case "list":
            case "table":
                p.fillRoundRect(x, y, w, h, CONTAINER_RADIUS, theme().surface); // the 1 px border is drawn over the rows: after()
                break;
            case "cell":
                cell(node, x, y, w, h);
                break;
            case "tablerow": {
                JXTheme t = theme();
                boolean selected = Boolean.TRUE.equals(node.getProperty("selected"));
                if (selected) {
                    p.fillRect(x, y, w, h, Boolean.TRUE.equals(node.getProperty("listFocused")) ? t.secondaryContainer : t.surfaceVariant);
                } else {
                    p.fillRect(x, y, w, h, t.surface);
                    int layer = pointerLayer(node);
                    if (layer > 0) {
                        p.fillRect(x, y, w, h, JXTheme.withAlpha(t.onSurface, layer));
                    }
                }
                p.fillRect(x, y + h - 1, w, 1, JXTheme.withAlpha(t.outlineVariant, 0x80));
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
                elevation(p, x, y, w, h, 4, 2);
                p.fillRoundRect(x, y, w, h, 4, theme().surfaceContainer);
                break;
            case "tooltip":
                p.fillRoundRect(x, y, w, h, 4, theme().inverseSurface);
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
        JXTheme t = theme();
        boolean showing = Boolean.TRUE.equals(node.getProperty("showing"));
        field(node, x, y, w, h, showing);
        float bw = JXControlLayout.DATE_BUTTON;
        String shown = JXOverlays.timeText(node);
        p.clip(x + 1, y, w - bw - 1, h);
        if (shown.isEmpty()) {
            drawText(node, text(node, "prompt"), x + JXNativeNode.FIELD_PAD_X, y, h, t.onSurfaceVariant, false);
        } else {
            drawText(node, shown, x + JXNativeNode.FIELD_PAD_X, y, h, color(node, t.onSurface), false);
        }
        p.restore();
        float cx = x + w - bw / 2;
        float cy = y + h / 2;
        int icon = showing ? t.primary : t.onSurfaceVariant;
        p.strokeOval(cx - 6, cy - 6, 12, 12, 1.5f, icon);
        p.line(cx, cy, cx, cy - 3.5f, 1.4f, icon);
        p.line(cx, cy, cx + 3, cy + 1.8f, 1.4f, icon);
    }

    private void colorPicker(JXNativeNode node, float x, float y, float w, float h) {
        JXTheme t = theme();
        boolean showing = Boolean.TRUE.equals(node.getProperty("showing"));
        field(node, x, y, w, h, showing);
        float bw = JXControlLayout.DATE_BUTTON;
        String hex = JXOverlays.normalizeHex(text(node, "value"));
        float s = JXControlLayout.COLOR_SWATCH;
        float sx = x + JXNativeNode.FIELD_PAD_X;
        float sy = y + (h - s) / 2;
        p.fillRoundRect(sx - 1, sy - 1, s + 2, s + 2, 4, t.outline);
        p.fillRoundRect(sx, sy, s, s, 3, hex == null ? t.surface : JXOverlays.colorOf(hex));
        float left = sx + s + JXNativeNode.FIELD_PAD_X;
        p.clip(left, y + 1, x + w - bw - left, h - 2);
        if (hex == null) {
            drawText(node, text(node, "prompt"), left, y, h, t.onSurfaceVariant, false);
        } else {
            drawText(node, hex, left, y, h, color(node, t.onSurface), false);
        }
        p.restore();
        arrow(x + w - bw + (bw - 8) / 2, y + (h - 4) / 2, showing, showing ? t.primary : t.onSurfaceVariant);
    }

    private void menuButton(JXNativeNode node, float x, float y, float w, float h) {
        JXTheme t = theme();
        boolean open = JXOverlays.isOpen(node);
        float r = h / 2;
        p.strokeRoundRect(x, y, w, h, r, 1, t.outline);
        int layer = open ? JXTheme.PRESSED : pointerLayer(node);
        if (layer > 0) {
            p.fillRoundRect(x, y, w, h, r, JXTheme.withAlpha(t.primary, layer));
        }
        focusRing(node, x, y, w, h, r);
        p.clip(x, y, w - JXControlLayout.MENU_ARROW, h);
        drawText(node, text(node, "label"), x + JXNativeNode.PAD_X, y, h, color(node, t.onSurface), false);
        p.restore();
        arrow(x + w - JXNativeNode.PAD_X - 8, y + Math.round((h - 4) / 2), open, t.onSurfaceVariant);
    }

    private void menuBar(JXNativeNode node, float x, float y, float w, float h) {
        JXTheme t = theme();
        p.fillRect(x, y, w, h, t.surface);
        p.fillRect(x, y + h - 1, w, 1, t.outlineVariant);
        List<JXNativeNode> headers = node.getChildren();
        for (int i = 0; i < headers.size(); i++) {
            JXNativeNode header = headers.get(i);
            if (i == node.openChild) {
                p.fillRoundRect(header.getX(), header.getY() + 2, header.getWidth(), header.getHeight() - 4, 4, t.secondaryContainer);
            }
            int color = Boolean.TRUE.equals(header.getProperty("disabled")) ? JXTheme.withAlpha(t.onSurface, 0x66) : t.onSurface;
            drawText(header, JXOverlays.label(header), header.getX() + JXOverlays.BAR_PAD, y, h, color, false);
        }
    }

    // ---- building blocks -------------------------------------------------------------------

    /** The state layer of a pointer: pressed 10%, hover 8%, none else. Focus is shown by the focus indicator. */
    static int pointerLayer(JXNativeNode node) {
        if (Boolean.TRUE.equals(node.getProperty("pressed"))) {
            return JXTheme.PRESSED;
        }
        return Boolean.TRUE.equals(node.getProperty("hover")) ? JXTheme.HOVER : 0;
    }

    /** The focus indicator: a 2 px primary ring around the control with a 1 px gap, drawn only for a focused one. */
    void focusRing(JXNativeNode node, float x, float y, float w, float h, float r) {
        if (Boolean.TRUE.equals(node.getProperty("focused"))) {
            p.strokeRoundRect(x - 3, y - 3, w + 6, h + 6, r + 3, 2, theme().primary);
        }
    }

    /**
     * The outlined text field of Material 3: a 1 px outline with 4 px corners (the on-surface color under the pointer),
     * 2 px primary while it has the focus.
     */
    private void field(JXNativeNode node, float x, float y, float w, float h) {
        field(node, x, y, w, h, Boolean.TRUE.equals(node.getProperty("focused")));
    }

    private void field(JXNativeNode node, float x, float y, float w, float h, boolean active) {
        JXTheme t = theme();
        p.fillRoundRect(x, y, w, h, 4, t.surface);
        if (active) {
            p.strokeRoundRect(x, y, w, h, 4, 2, t.primary);
        } else {
            p.strokeRoundRect(x, y, w, h, 4, 1, Boolean.TRUE.equals(node.getProperty("hover")) ? t.onSurface : t.outline);
        }
    }

    /** A small triangle pointing down, or up for {@code up}, 8 by 4 px. */
    private void arrow(float x, float y, boolean up, int color) {
        if (up) {
            upArrow(x, y, color);
        } else {
            downArrow(x, y, color);
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
                p.text(prompt, left, y + engine.baseline(h, size), size, bold, theme().onSurfaceVariant);
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
            p.fillRect(sx, y + (h - lineH) / 2, ex - sx, lineH, selectionColor());
        }
        if (!value.isEmpty()) {
            p.text(value, left - shift, y + engine.baseline(h, size), size, bold, color(node, theme().onSurface));
        }
        if (caret >= 0 && focused) {
            float cx = left - shift + engine.width(value.substring(0, caret), size);
            float lineH = engine.lineHeight(size);
            p.fillRect((float) Math.floor(cx), y + (h - lineH) / 2, 1, lineH, theme().primary);
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
            p.text(text(node, "prompt"), left, top + engine.ascent(size), size, bold, theme().onSurfaceVariant);
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
                        p.fillRect(sx, lineTop, Math.max(ex - sx, 2), line, selectionColor());
                    }
                }
                p.text(text, left, lineTop + engine.ascent(size), size, bold, color(node, theme().onSurface));
                if (focused && caret >= offset && caret <= offset + text.length()) {
                    float cx = left + engine.width(text.substring(0, caret - offset), size);
                    p.fillRect((float) Math.floor(cx), lineTop, 1, line, theme().primary);
                }
            }
            offset += text.length() + JXTextLayout.separatorAfter(value, offset + text.length());
        }
        p.restore();
    }

    /** The translucent primary color behind the selected text of a field. */
    private int selectionColor() {
        return JXTheme.withAlpha(theme().primary, 0x59);
    }

    private void checkBox(JXNativeNode node, float x, float y, float h) {
        JXTheme t = theme();
        float s = JXNativeNode.CHECK_BOX;
        float top = y + Math.round((h - s) / 2);
        boolean mixed = Boolean.TRUE.equals(node.getProperty("indeterminate"));
        boolean on = mixed || Boolean.TRUE.equals(node.getProperty("checked"));
        int layer = pointerLayer(node);
        if (layer > 0) { // the state layer: a 22 px disc, 3 px beyond the box
            p.fillOval(x + s / 2 - 11, top + s / 2 - 11, 22, 22, JXTheme.withAlpha(on ? t.primary : t.onSurface, layer));
        }
        if (on) {
            p.fillRoundRect(x, top, s, s, 2, t.primary);
            if (mixed) {
                p.fillRoundRect(x + 4, top + 7, s - 8, 2, 1, t.onPrimary);
            } else {
                p.line(x + 3.5f, top + 8.3f, x + 6.7f, top + 11.4f, 2, t.onPrimary);
                p.line(x + 6.7f, top + 11.4f, x + 12.6f, top + 4.7f, 2, t.onPrimary);
            }
        } else {
            p.strokeRoundRect(x, top, s, s, 2, 2, Boolean.TRUE.equals(node.getProperty("hover")) ? t.onSurface : t.onSurfaceVariant);
        }
        focusRing(node, x, top, s, s, 2);
        drawText(node, text(node, "label"), x + JXNativeNode.CHECK_BOX + JXNativeNode.CHECK_GAP, y, h, color(node, t.onSurface),
                Boolean.TRUE.equals(node.getProperty("underline")));
    }

    private void radio(JXNativeNode node, float x, float y, float h) {
        JXTheme t = theme();
        float s = JXNativeNode.CHECK_BOX;
        float top = y + Math.round((h - s) / 2);
        boolean on = Boolean.TRUE.equals(node.getProperty("checked"));
        int layer = pointerLayer(node);
        if (layer > 0) { // a 20 px disc, 2 px beyond the circle: the label, 5 px away, keeps 3
            p.fillOval(x + s / 2 - 10, top + s / 2 - 10, 20, 20, JXTheme.withAlpha(on ? t.primary : t.onSurface, layer));
        }
        // Focused: the ring sits on the edge of the 16 px box and the circle and its dot shrink inside it, so that nothing
        // passes the box and the label keeps all of its CHECK_GAP (the ring used to end 3 px beyond, 2 px from the label)
        boolean focused = Boolean.TRUE.equals(node.getProperty("focused"));
        float in = focused ? 2 : 0;
        p.strokeOval(x + in, top + in, s - 2 * in, s - 2 * in, 2,
                on ? t.primary : Boolean.TRUE.equals(node.getProperty("hover")) ? t.onSurface : t.onSurfaceVariant);
        if (on) {
            p.fillOval(x + in + 4, top + in + 4, s - 2 * in - 8, s - 2 * in - 8, t.primary);
        }
        if (focused) {
            p.strokeOval(x, top, s, s, 1.5f, t.primary);
        }
        drawText(node, text(node, "label"), x + JXNativeNode.CHECK_BOX + JXNativeNode.CHECK_GAP, y, h, color(node, t.onSurface),
                Boolean.TRUE.equals(node.getProperty("underline")));
    }

    private void select(JXNativeNode node, float x, float y, float w, float h) {
        JXTheme t = theme();
        boolean editable = Boolean.TRUE.equals(node.getProperty("editable"));
        boolean showing = Boolean.TRUE.equals(node.getProperty("showing"));
        field(node, x, y, w, h, showing || Boolean.TRUE.equals(node.getProperty("focused")));
        if (editable) {
            fieldText(node, x, y, w - JXNativeNode.ARROW, h, false);
        } else {
            String value = text(node, "value");
            boolean prompt = value.isEmpty() || Boolean.TRUE.equals(node.getProperty("promptShown"));
            if (value.isEmpty()) {
                value = text(node, "prompt");
            }
            p.clip(x, y, w - JXNativeNode.ARROW, h);
            drawText(node, value, x + JXNativeNode.PAD_X, y, h, prompt ? t.onSurfaceVariant : color(node, t.onSurface), false);
            p.restore();
        }
        float ax = x + w - JXNativeNode.ARROW + 10;
        float ay = y + Math.round((h - 4) / 2);
        arrow(ax, ay, showing, showing ? t.primary : t.onSurfaceVariant);
    }

    void downArrow(float x, float y, int color) {
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
        JXTheme t = theme();
        boolean upHover = "up".equals(node.getProperty("hoverPart"));
        boolean downHover = "down".equals(node.getProperty("hoverPart"));
        boolean pressed = Boolean.TRUE.equals(node.getProperty("pressed"));
        int hoverLayer = JXTheme.withAlpha(t.onSurface, pressed ? JXTheme.PRESSED : JXTheme.HOVER);
        if (upHover) {
            p.fillRoundRect(bx + 2, y + 3, bw - 4, half - 3, 3, hoverLayer);
        }
        if (downHover) {
            p.fillRoundRect(bx + 2, y + half, bw - 4, h - half - 3, 3, hoverLayer);
        }
        upArrow(bx + (bw - 8) / 2, y + (half - 4) / 2 + 0.5f, t.onSurfaceVariant);
        downArrow(bx + (bw - 8) / 2, y + half + (h - half - 4) / 2 - 0.5f, t.onSurfaceVariant);
    }

    private void datePicker(JXNativeNode node, float x, float y, float w, float h) {
        JXTheme t = theme();
        boolean showing = Boolean.TRUE.equals(node.getProperty("showing"));
        field(node, x, y, w, h, showing || Boolean.TRUE.equals(node.getProperty("focused")));
        float bw = JXControlLayout.DATE_BUTTON;
        fieldText(node, x, y, w - bw, h, false);
        float cx = x + w - bw + (bw - 12) / 2;
        float cy = y + (h - 12) / 2;
        int icon = showing ? t.primary : t.onSurfaceVariant;
        p.strokeRoundRect(cx, cy + 1, 12, 11, 2, 1.5f, icon); // the calendar: frame, header bar, rings and days
        p.fillRect(cx, cy + 1, 12, 4, icon);
        p.fillRoundRect(cx + 2.5f, cy - 1, 1.5f, 3, 0.75f, icon);
        p.fillRoundRect(cx + 8, cy - 1, 1.5f, 3, 0.75f, icon);
        p.fillRect(cx + 3, cy + 7, 2, 2, icon);
        p.fillRect(cx + 7, cy + 7, 2, 2, icon);
    }

    private void progressBar(JXNativeNode node, float x, float y, float w, float h) {
        JXTheme th = theme();
        float t = Math.min(h, 8); // a rounded bar of 8 px in the middle of the box, on a tonal track
        float ty = y + (h - t) / 2;
        p.fillRoundRect(x, ty, w, t, t / 2, th.primaryContainer);
        double progress = number(node, "progress", 0.0);
        if (progress < 0) {
            float band = Math.max(20, w / 4);
            float phase = (float) ((clock.getAsLong() % 1500) / 1500.0);
            float bx = x + (w - band) * phase;
            p.fillRoundRect(bx, ty, band, t, t / 2, th.primary);
            return;
        }
        float bw = (float) (w * clamp(progress));
        if (bw > 0) {
            p.fillRoundRect(x, ty, Math.max(bw, t), t, t / 2, th.primary);
        }
    }

    private void indicator(JXNativeNode node, float x, float y, float w, float h) {
        double progress = number(node, "progress", -1.0);
        JXTextEngine engine = JXTextEngine.get(false);
        float textH = progress >= 0 ? engine.lineHeight(11) : 0;
        float d = Math.max(0, Math.min(w, h - textH) - 4);
        float cx = x + (w - d) / 2;
        float cy = y + (h - textH - d) / 2;
        JXTheme t = theme();
        float ring = Math.max(2.5f, Math.min(4, d / 8)); // the stroke of the ring
        if (progress < 0) {
            long now = clock.getAsLong();
            float turn = (now % 1400) / 1400f; // one turn of the arc every 1.4 s, its length breathing
            float sweep = 90 + 180 * (float) Math.abs(Math.sin(Math.PI * ((now % 2400) / 2400.0)));
            p.strokeOval(cx, cy, d, d, ring, t.primaryContainer);
            p.strokeArc(cx, cy, d, d, -90 + 360 * turn, sweep, ring, t.primary);
            return;
        }
        p.strokeOval(cx, cy, d, d, ring, t.primaryContainer);
        if (progress > 0) {
            p.strokeArc(cx, cy, d, d, -90, (float) (360 * clamp(progress)), ring, t.primary);
        }
        String percent = Math.round(clamp(progress) * 100) + "%";
        if (progress >= 1) {
            percent = "Done";
        }
        float tw = engine.width(percent, 11);
        p.text(percent, x + (w - tw) / 2, y + h - textH + engine.ascent(11), 11, false, t.onSurfaceVariant);
    }

    private void slider(JXNativeNode node, float x, float y, float w, float h) {
        double min = number(node, "min", 0.0);
        double max = number(node, "max", 100.0);
        double fraction = max <= min ? 0.0 : clamp((number(node, "value", min) - min) / (max - min));
        JXTheme t = theme();
        if (node.vertical()) {
            float tx = x + (w - 4) / 2;
            float ty = (float) (y + (h - 14) * (1 - fraction));
            p.fillRoundRect(tx, y + 4, 4, h - 8, 2, t.primaryContainer);
            p.fillRoundRect(tx, ty + 7, 4, y + h - 4 - (ty + 7), 2, t.primary);
            thumb(node, x + (w - 14) / 2, ty);
            return;
        }
        float ty = y + (h - 4) / 2;
        float tx = (float) (x + (w - 14) * fraction);
        p.fillRoundRect(x + 4, ty, w - 8, 4, 2, t.primaryContainer);
        p.fillRoundRect(x + 4, ty, Math.max(4, tx + 7 - (x + 4)), 4, 2, t.primary);
        thumb(node, tx, y + (h - 14) / 2);
    }

    /** The round thumb: primary, its state layer a 24 px disc around it (hover, focus, pressed). */
    private void thumb(JXNativeNode node, float x, float y) {
        JXTheme t = theme();
        int layer = Boolean.TRUE.equals(node.getProperty("pressed")) ? JXTheme.PRESSED
                : Boolean.TRUE.equals(node.getProperty("focused")) ? JXTheme.FOCUS
                : Boolean.TRUE.equals(node.getProperty("hover")) ? JXTheme.HOVER : 0;
        if (layer > 0) {
            p.fillOval(x + 7 - 12, y + 7 - 12, 24, 24, JXTheme.withAlpha(t.primary, layer));
        }
        p.fillOval(x, y, 14, 14, t.primary);
    }

    private void separator(JXNativeNode node, float x, float y, float w, float h) {
        int line = theme().outlineVariant;
        if (node.vertical()) {
            float left = x + Math.round((w - JXNativeNode.SEPARATOR_THICKNESS_V) / 2.0f);
            p.fillRect(left + 1, y, 1, h, line);
        } else {
            float top = y + Math.round((h - JXNativeNode.SEPARATOR_THICKNESS_H) / 2.0f);
            p.fillRect(x, top + 1, w, 1, line);
        }
    }

    /** A titled pane: a tonal title bar on top of a body in a 1 px outline, 8 px corners. */
    private void titled(JXNativeNode node, float x, float y, float w, float h) {
        JXTheme t = theme();
        float title = JXTitledLayout.titleHeight();
        boolean open = !node.collapsed() && h > title;
        float r = 8;
        int bar = t.surfaceContainer;
        if (Boolean.TRUE.equals(node.getProperty("hover"))) {
            bar = JXTheme.mix(t.onSurface, bar, 1 - JXTheme.HOVER / 255f);
        }
        p.fillRoundRect(x, y, w, title, r, bar);
        if (open) {
            p.fillRect(x, y + title / 2, w, title / 2, bar); // square the lower corners of the bar
            p.fillRect(x + 1, y + title - 1, w - 2, 1, t.outlineVariant);
        }
        p.strokeRoundRect(x, y, w, open ? h : title, r, 1, t.outlineVariant);
        if (node.collapsible()) {
            float[] a = JXTitledLayout.arrow(node, x, y);
            p.fillTriangle(a[0], a[1], a[2], a[3], a[4], a[5], t.onSurfaceVariant);
        }
        drawText(node, text(node, "label"), x + JXTitledLayout.textOffset(node), y, title, color(node, t.onSurface), false);
        if (Boolean.TRUE.equals(node.getProperty("focused"))) {
            p.strokeRoundRect(x, y, w, title, r, 2, t.primary);
        }
    }

    private void cell(JXNativeNode node, float x, float y, float w, float h) {
        boolean selected = Boolean.TRUE.equals(node.getProperty("selected"));
        boolean focusedList = Boolean.TRUE.equals(node.getProperty("listFocused"));
        JXTheme t = theme();
        if (!Boolean.FALSE.equals(node.getProperty("paintBackground"))) {
            if (selected) { // the selection is the secondary container, paler when its list has not the focus
                p.fillRect(x, y, w, h, focusedList ? t.secondaryContainer : t.surfaceVariant);
            }
            int layer = pointerLayer(node);
            if (layer > 0) {
                p.fillRect(x, y, w, h, JXTheme.withAlpha(selected ? t.onPrimaryContainer : t.onSurface, layer));
            }
        }
        if (Boolean.TRUE.equals(node.getProperty("tableCell"))) {
            p.fillRect(x + w - 1, y, 1, h, JXTheme.withAlpha(t.outlineVariant, 0x80));
        }
        String value = text(node, "value");
        float tree = (float) JXControlLayout.treeLeft(node);
        if (tree > 0) { // a tree row: the arrow of a row that expands, after its indentation
            String disclosure = text(node, "disclosure");
            if (!"leaf".equals(disclosure)) {
                float[] pad = insetsOr(node, 3, 7);
                float ax = x + pad[3] + (float) number(node, "indent", 0) * JXControlLayout.TREE_INDENT + 4;
                float cy = y + h / 2;
                int arrow = t.onSurfaceVariant;
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
            int textColor = color(node, selected && focusedList ? t.onPrimaryContainer : t.onSurface);
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
        JXTheme t = theme();
        float header = JXControlLayout.TAB_HEADER;
        p.fillRect(x, y, w, h, t.surface);
        p.fillRect(x, y + header - 1, w, 1, t.outlineVariant);
        String[] titles = strings(node.getProperty("titles"));
        int selected = (int) number(node, "selected", 0);
        float[] tabX = JXControlLayout.tabPositions(node);
        for (int i = 0; i < titles.length; i++) {
            float tx = x + tabX[i];
            float tw = tabX[i + 1] - tabX[i];
            float ty = y + JXControlLayout.TAB_TOP;
            float th = header - JXControlLayout.TAB_TOP;
            int color = i == selected ? t.primary : t.onSurfaceVariant;
            JXTextEngine engine = JXTextEngine.get(false);
            p.text(titles[i], tx + JXControlLayout.TAB_PAD, ty + engine.baseline(th, JXTextEngine.DEFAULT_SIZE),
                    JXTextEngine.DEFAULT_SIZE, false, color);
            if (JXControlLayout.tabClosable(node, i)) {
                float cx = tx + tw - JXControlLayout.TAB_PAD - 8;
                float cy = ty + (th - 8) / 2;
                p.line(cx + 1, cy + 1, cx + 7, cy + 7, 1.5f, t.onSurfaceVariant);
                p.line(cx + 7, cy + 1, cx + 1, cy + 7, 1.5f, t.onSurfaceVariant);
            }
            if (i == selected) { // the primary indicator under the selected tab
                p.fillRoundRect(tx + 2, y + header - 3, tw - 4, 3, 1.5f, t.primary);
            }
        }
    }

    private void pagination(JXNativeNode node, float x, float y, float w, float h) {
        JXTheme t = theme();
        float[][] buttons = JXControlLayout.pageButtons(node);
        int current = (int) number(node, "current", 0);
        JXTextEngine engine = JXTextEngine.get(false);
        for (float[] b : buttons) {
            int page = (int) b[4];
            boolean arrow = page < 0;
            if (!arrow && page == current) { // the current page: a primary disc
                float d = Math.min(b[2], b[3]);
                p.fillOval(b[0] + (b[2] - d) / 2, b[1] + (b[3] - d) / 2, d, d, t.primary);
            }
            if (arrow) {
                float cy = b[1] + b[3] / 2;
                float cx = b[0] + b[2] / 2;
                if (page == -1) {
                    p.fillTriangle(cx + 3, cy - 5, cx + 3, cy + 5, cx - 3, cy, t.onSurfaceVariant);
                } else {
                    p.fillTriangle(cx - 3, cy - 5, cx - 3, cy + 5, cx + 3, cy, t.onSurfaceVariant);
                }
            } else {
                String label = String.valueOf(page + 1);
                float tw = engine.width(label, JXTextEngine.DEFAULT_SIZE);
                p.text(label, b[0] + (b[2] - tw) / 2, b[1] + engine.baseline(b[3], JXTextEngine.DEFAULT_SIZE),
                        JXTextEngine.DEFAULT_SIZE, false, page == current ? t.onPrimary : t.onSurface);
            }
        }
        int count = (int) number(node, "pageCount", 1);
        String info = (current + 1) + "/" + count;
        float tw = engine.width(info, JXTextEngine.DEFAULT_SIZE);
        p.text(info, x + (w - tw) / 2, y + h - 6, JXTextEngine.DEFAULT_SIZE, false, t.onSurfaceVariant);
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
        int line = JXTheme.withAlpha(theme().outlineVariant, 0x80);
        for (int i = count; top < viewport[1] + viewport[3]; i++, top += item) {
            float cx = viewport[0] - (float) g.scrollX;
            for (float w : widths) {
                cx += w;
                p.fillRect(cx - 1, (float) top, 1, (float) item, line);
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
            p.fillRect(g.vx, g.hy, g.vw, g.hh, theme().surface);
        }
    }

    /** A slim rounded thumb in the lane of the bar, on a track of its own: no arrows. */
    private void bar(float x, float y, float w, float h, float thumbPos, float thumbLen, boolean vertical) {
        JXTheme t = theme();
        p.fillRect(x, y, w, h, t.surface);
        int thumb = JXTheme.withAlpha(t.onSurfaceVariant, 0x99);
        if (vertical) {
            p.fillRect(x, y, 1, h, JXTheme.withAlpha(t.outlineVariant, 0x80));
            p.fillRoundRect(x + (w - 6) / 2 + 0.5f, thumbPos + 1, 6, Math.max(6, thumbLen - 2), 3, thumb);
        } else {
            p.fillRect(x, y, w, 1, JXTheme.withAlpha(t.outlineVariant, 0x80));
            p.fillRoundRect(thumbPos + 1, y + (h - 6) / 2 + 0.5f, Math.max(6, thumbLen - 2), 6, 3, thumb);
        }
    }

    private void tableHeader(JXNativeNode node) {
        JXTheme t = theme();
        float x = node.getX() + 1;
        float y = node.getY() + 1;
        float w = node.getWidth() - 2;
        float h = JXControlLayout.TABLE_HEADER;
        p.fillRect(x, y, w, h, t.surfaceContainer);
        p.fillRect(x, y + h - 1, w, 1, t.outlineVariant);
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
            p.text(titles[i], left, y + engine.baseline(h, JXTextEngine.DEFAULT_SIZE), JXTextEngine.DEFAULT_SIZE, true, t.onSurface);
            p.restore();
            cx += widths[i];
            p.fillRect(cx - 1, y + 4, 1, h - 8, t.outlineVariant);
        }
        p.restore();
    }

    // ---- text helpers ------------------------------------------------------------------------

    /** A labeled control's text inside its padding, aligned horizontally, vertically centred. */
    private void labeledText(JXNativeNode node, String value, float x, float y, float w, float h, char align, float[] pad) {
        labeledText(node, value, x, y, w, h, align, pad, null);
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
                : "tooltip".equals(node.getType()) ? theme().inverseOnSurface : theme().onSurface);
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
