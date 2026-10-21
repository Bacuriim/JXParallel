package com.jxparallel.ui.native2d;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The built-in icons of the {@code icon} element: simple geometry (lines, ovals, arcs, triangles) drawn
 * through {@link JXPainter} on a 24 x 24 grid, scaled to the icon's size, so every painter draws them and
 * no font, file or network is involved. The shapes are made here; they follow no icon set's path data.
 * Strokes are 2 units wide with round ends, as the usual outline icons. An unknown name draws nothing.
 */
public final class JXIcons {
    private static final List<String> NAMES = Collections.unmodifiableList(Arrays.asList(
            "menu", "close", "add", "remove", "check", "search", "arrow-back", "arrow-forward", "arrow-up",
            "arrow-down", "chevron-left", "chevron-right", "chevron-up", "chevron-down", "more-vert", "more-horiz",
            "edit", "delete", "settings", "home", "info", "warning", "error", "star", "favorite", "refresh",
            "person", "mail", "calendar", "visibility", "lock"));

    private JXIcons() {
    }

    /** The icon names, in a fixed order. */
    public static List<String> names() {
        return NAMES;
    }

    public static boolean has(String name) {
        return NAMES.contains(name);
    }

    /** Draws the icon in the square of side {@code size} whose top left corner is (x, y). */
    static void draw(JXPainter p, String name, float x, float y, float size, int color) {
        if (name == null || !has(name) || size <= 0) {
            return;
        }
        new Pen(p, x, y, size / 24f, color).icon(name);
    }

    /** A painter bound to the 24-unit grid of one icon. */
    private static final class Pen {
        private final JXPainter p;
        private final float ox;
        private final float oy;
        private final float s;
        private final int color;
        private final float w;

        Pen(JXPainter p, float ox, float oy, float s, int color) {
            this.p = p;
            this.ox = ox;
            this.oy = oy;
            this.s = s;
            this.color = color;
            this.w = Math.max(1f, 2f * s);
        }

        private float X(float u) {
            return ox + u * s;
        }

        private float Y(float v) {
            return oy + v * s;
        }

        /** A segment with round ends. */
        void ln(float x1, float y1, float x2, float y2) {
            lnw(x1, y1, x2, y2, w);
        }

        void lnw(float x1, float y1, float x2, float y2, float width) {
            p.line(X(x1), Y(y1), X(x2), Y(y2), width, color);
            p.fillOval(X(x1) - width / 2, Y(y1) - width / 2, width, width, color);
            p.fillOval(X(x2) - width / 2, Y(y2) - width / 2, width, width, color);
        }

        /** Joined segments through the points (x0, y0, x1, y1, ...). */
        void path(float... pts) {
            for (int i = 0; i + 3 < pts.length; i += 2) {
                ln(pts[i], pts[i + 1], pts[i + 2], pts[i + 3]);
            }
        }

        void ring(float cx, float cy, float r) {
            p.strokeOval(X(cx - r) - w / 2, Y(cy - r) - w / 2, 2 * r * s + w, 2 * r * s + w, w, color);
        }

        void disc(float cx, float cy, float r) {
            p.fillOval(X(cx - r), Y(cy - r), 2 * r * s, 2 * r * s, color);
        }

        void box(float x, float y, float width, float height, float r) {
            p.strokeRoundRect(X(x) - w / 2, Y(y) - w / 2, width * s + w, height * s + w, r * s + w / 2, w, color);
        }

        void solid(float x, float y, float width, float height, float r) {
            p.fillRoundRect(X(x), Y(y), width * s, height * s, r * s, color);
        }

        void tri(float x0, float y0, float x1, float y1, float x2, float y2) {
            p.fillTriangle(X(x0), Y(y0), X(x1), Y(y1), X(x2), Y(y2), color);
        }

        void icon(String name) {
            switch (name) {
                case "menu":
                    ln(4, 6, 20, 6);
                    ln(4, 12, 20, 12);
                    ln(4, 18, 20, 18);
                    break;
                case "close":
                    ln(6, 6, 18, 18);
                    ln(18, 6, 6, 18);
                    break;
                case "add":
                    ln(12, 5, 12, 19);
                    ln(5, 12, 19, 12);
                    break;
                case "remove":
                    ln(5, 12, 19, 12);
                    break;
                case "check":
                    path(5, 12.5f, 10, 17.5f, 19, 7);
                    break;
                case "search":
                    ring(10.5f, 10.5f, 6);
                    ln(15, 15, 20, 20);
                    break;
                case "arrow-back":
                    ln(20, 12, 5, 12);
                    path(11, 6, 5, 12, 11, 18);
                    break;
                case "arrow-forward":
                    ln(4, 12, 19, 12);
                    path(13, 6, 19, 12, 13, 18);
                    break;
                case "arrow-up":
                    ln(12, 20, 12, 5);
                    path(6, 11, 12, 5, 18, 11);
                    break;
                case "arrow-down":
                    ln(12, 4, 12, 19);
                    path(6, 13, 12, 19, 18, 13);
                    break;
                case "chevron-left":
                    path(15, 5, 8, 12, 15, 19);
                    break;
                case "chevron-right":
                    path(9, 5, 16, 12, 9, 19);
                    break;
                case "chevron-up":
                    path(5, 15, 12, 8, 19, 15);
                    break;
                case "chevron-down":
                    path(5, 9, 12, 16, 19, 9);
                    break;
                case "more-vert":
                    disc(12, 5, 1.9f);
                    disc(12, 12, 1.9f);
                    disc(12, 19, 1.9f);
                    break;
                case "more-horiz":
                    disc(5, 12, 1.9f);
                    disc(12, 12, 1.9f);
                    disc(19, 12, 1.9f);
                    break;
                case "edit":
                    lnw(8, 16, 17, 7, 4 * s);
                    tri(5, 19.5f, 6.2f, 14.5f, 9.5f, 17.8f);
                    ln(5, 20, 19, 20);
                    break;
                case "delete":
                    ln(5, 7, 19, 7);
                    path(9, 7, 9, 4.5f, 15, 4.5f, 15, 7);
                    path(7, 10, 7.8f, 20, 16.2f, 20, 17, 10);
                    ln(10.5f, 11.5f, 10.5f, 17);
                    ln(13.5f, 11.5f, 13.5f, 17);
                    break;
                case "settings":
                    ring(12, 12, 6.5f);
                    disc(12, 12, 2.2f);
                    for (int i = 0; i < 8; i++) {
                        double a = Math.toRadians(i * 45);
                        float c = (float) Math.cos(a);
                        float d = (float) Math.sin(a);
                        lnw(12 + c * 8f, 12 + d * 8f, 12 + c * 10f, 12 + d * 10f, 2.6f * s);
                    }
                    break;
                case "home":
                    path(3, 12, 12, 4, 21, 12);
                    path(6, 10.5f, 6, 20, 18, 20, 18, 10.5f);
                    path(10, 20, 10, 14, 14, 14, 14, 20);
                    break;
                case "info":
                    ring(12, 12, 9);
                    ln(12, 11, 12, 17);
                    disc(12, 7.6f, 1.3f);
                    break;
                case "warning":
                    path(12, 4, 21, 20, 3, 20, 12, 4);
                    ln(12, 10, 12, 14.5f);
                    disc(12, 17.2f, 1.2f);
                    break;
                case "error":
                    ring(12, 12, 9);
                    ln(12, 7, 12, 13);
                    disc(12, 16.5f, 1.3f);
                    break;
                case "star":
                    star();
                    break;
                case "favorite":
                    disc(8.2f, 9, 4.6f);
                    disc(15.8f, 9, 4.6f);
                    tri(3.8f, 10.6f, 20.2f, 10.6f, 12, 20.5f);
                    break;
                case "refresh":
                    refresh();
                    break;
                case "person":
                    disc(12, 8, 4.2f);
                    solid(5, 14, 14, 7, 3.5f);
                    break;
                case "mail":
                    box(3, 6, 18, 13, 2);
                    path(4, 8, 12, 14, 20, 8);
                    break;
                case "calendar":
                    box(4, 6, 16, 14, 2);
                    ln(4, 10.5f, 20, 10.5f);
                    ln(8, 3.5f, 8, 8);
                    ln(16, 3.5f, 16, 8);
                    disc(8, 14.5f, 1.2f);
                    disc(12, 14.5f, 1.2f);
                    disc(16, 14.5f, 1.2f);
                    disc(8, 18, 1.2f);
                    disc(12, 18, 1.2f);
                    break;
                case "visibility":
                    p.strokeOval(X(2), Y(7), 20 * s, 10 * s, w, color);
                    disc(12, 12, 3.2f);
                    break;
                case "lock":
                    solid(5, 11, 14, 10, 2);
                    p.strokeArc(X(8), Y(4), 8 * s, 8 * s, 180, 180, w, color);
                    ln(8, 8, 8, 11.5f);
                    ln(16, 8, 16, 11.5f);
                    break;
                default:
                    break;
            }
        }

        /** Five points, filled as ten triangles around the center. */
        private void star() {
            float cx = 12;
            float cy = 13;
            float[] vx = new float[10];
            float[] vy = new float[10];
            for (int i = 0; i < 10; i++) {
                double a = Math.toRadians(-90 + 36 * i);
                float r = i % 2 == 0 ? 10 : 4.3f;
                vx[i] = cx + (float) Math.cos(a) * r;
                vy[i] = cy + (float) Math.sin(a) * r;
            }
            for (int i = 0; i < 10; i++) {
                int n = (i + 1) % 10;
                tri(cx, cy, vx[i], vy[i], vx[n], vy[n]);
            }
        }

        /** Most of a circle clockwise, with an arrow head at its end. */
        private void refresh() {
            float start = 40;
            float sweep = 280;
            p.strokeArc(X(5) - w / 2, Y(5) - w / 2, 14 * s + w, 14 * s + w, start, sweep, w, color);
            double a = Math.toRadians(start + sweep);
            float c = (float) Math.cos(a);
            float d = (float) Math.sin(a);
            float ex = 12 + c * 7;
            float ey = 12 + d * 7;
            float tx = -d; // clockwise tangent
            float ty = c;
            tri(ex + tx * 5, ey + ty * 5, ex + c * 4, ey + d * 4, ex - c * 4, ey - d * 4);
        }
    }
}
