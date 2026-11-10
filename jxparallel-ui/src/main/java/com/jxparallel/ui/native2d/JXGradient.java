package com.jxparallel.ui.native2d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A linear or radial gradient with any number of color stops, written as the CSS of JavaFX:
 * {@code linear-gradient(#fff, #000)} (top to bottom), {@code linear-gradient(from 0% 0% to 100% 0%, #fff 0%, #00f 40%, #000 100%)},
 * {@code linear-gradient(to right, ...)} and {@code linear-gradient(45deg, ...)} (CSS angles: 0deg is upwards, 90deg to the
 * right), {@code radial-gradient(center 50% 50%, radius 100%, #fff, #000)} (also {@code at 50% 50%}). A stop is a color
 * with an optional position in {@code %} (or a fraction 0..1); stops without one are spread evenly. Colors are {@code #RGB},
 * {@code #RRGGBB}, {@code #AARRGGBB} (like the JXML attributes), {@code rgb()}, {@code rgba()} and a few names.
 *
 * <p>The renderer keeps what it cannot draw out: {@code repeat} and {@code reflect} cycles and the focus of a radial
 * gradient are read and ignored (the last color extends), a radial gradient is a circle whose radius is a fraction of the
 * shorter side of the box. Immutable.
 */
final class JXGradient {
    final boolean radial;
    /** Positions of the stops, 0..1, never decreasing. */
    final float[] offsets;
    /** ARGB of the stops. */
    final int[] colors;
    private final boolean byAngle;
    private final float angle;
    /** Linear: x0, y0, x1, y1. Radial: centre x, centre y, radius. */
    private final float[] value;
    /** Whether each of {@link #value} is a percentage (of the width or the height) or pixels. */
    private final boolean[] percent;

    private JXGradient(boolean radial, boolean byAngle, float angle, float[] value, boolean[] percent, float[] offsets, int[] colors) {
        this.radial = radial;
        this.byAngle = byAngle;
        this.angle = angle;
        this.value = value;
        this.percent = percent;
        this.offsets = offsets;
        this.colors = colors;
    }

    /** A vertical gradient from {@code top} to {@code bottom}. */
    static JXGradient vertical(int top, int bottom) {
        return new JXGradient(false, false, 0, new float[] {0, 0, 0, 1}, new boolean[] {true, true, true, true},
                new float[] {0, 1}, new int[] {top, bottom});
    }

    private static final Map<String, JXGradient> PARSED = new HashMap<String, JXGradient>();

    /** The parsed gradient of a node's prop; remembered, since the same text is painted every frame. Null when it is not one. */
    static JXGradient of(Object text) {
        if (!(text instanceof String)) {
            return null;
        }
        String key = (String) text;
        if (PARSED.containsKey(key)) {
            return PARSED.get(key);
        }
        if (PARSED.size() > 512) {
            PARSED.clear();
        }
        JXGradient parsed = parse(key);
        PARSED.put(key, parsed);
        return parsed;
    }

    /** Parses the CSS text of a gradient; null when it is not a gradient this class understands. */
    static JXGradient parse(String text) {
        if (text == null) {
            return null;
        }
        String s = text.trim();
        String lower = s.toLowerCase(Locale.ROOT);
        boolean radial = lower.startsWith("radial-gradient");
        if (!radial && !lower.startsWith("linear-gradient")) {
            return null;
        }
        int open = s.indexOf('(');
        int close = s.lastIndexOf(')');
        if (open < 0 || close < open) {
            return null;
        }
        try {
            List<String> parts = split(s.substring(open + 1, close), ',');
            boolean byAngle = false;
            float angle = 180;
            float[] value = radial ? new float[] {50, 50, 100} : new float[] {0, 0, 0, 100};
            boolean[] percent = radial ? new boolean[] {true, true, true} : new boolean[] {true, true, true, true};
            int next = 0;
            while (next < parts.size() && isGeometry(parts.get(next))) {
                String part = parts.get(next).trim().toLowerCase(Locale.ROOT);
                if (radial) {
                    radialPart(part, value, percent);
                } else if (part.startsWith("from ")) {
                    String[] t = part.split("\\s+");
                    if (t.length != 6 || !"to".equals(t[3])) {
                        return null;
                    }
                    for (int i = 0; i < 4; i++) {
                        String n = t[i < 2 ? 1 + i : 2 + i];
                        percent[i] = n.endsWith("%");
                        value[i] = Float.parseFloat(n.replace("%", "").replace("px", ""));
                    }
                    byAngle = false;
                } else if (part.startsWith("to ")) {
                    angle = sideAngle(part.substring(3).trim());
                    byAngle = true;
                } else if (part.endsWith("deg")) {
                    angle = Float.parseFloat(part.substring(0, part.length() - 3));
                    byAngle = true;
                }
                next++;
            }
            List<Float> positions = new ArrayList<Float>();
            List<Integer> argbs = new ArrayList<Integer>();
            for (int i = next; i < parts.size(); i++) {
                List<String> tokens = split(parts.get(i).trim(), ' ');
                if (tokens.isEmpty()) {
                    return null;
                }
                Integer argb = color(tokens.get(0));
                if (argb == null) {
                    return null;
                }
                argbs.add(argb);
                positions.add(tokens.size() > 1 ? position(tokens.get(1)) : Float.NaN);
            }
            if (argbs.size() < 2) {
                return null;
            }
            float[] offsets = spread(positions);
            int[] colors = new int[argbs.size()];
            for (int i = 0; i < colors.length; i++) {
                colors[i] = argbs.get(i);
            }
            return new JXGradient(radial, byAngle, angle, value, percent, offsets, colors);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Whether a part before the stops sets the geometry (direction, centre, radius) or the cycle, not a color. */
    private static boolean isGeometry(String part) {
        String p = part.trim().toLowerCase(Locale.ROOT);
        return p.startsWith("from ") || p.startsWith("to ") || p.endsWith("deg") || p.startsWith("center ") || p.startsWith("at ")
                || p.startsWith("radius ") || p.startsWith("circle") || p.startsWith("focus-") || "repeat".equals(p)
                || "reflect".equals(p) || p.startsWith("closest-") || p.startsWith("farthest-");
    }

    private static void radialPart(String part, float[] value, boolean[] percent) {
        String[] t = part.split("\\s+");
        for (int i = 0; i < t.length; i++) {
            if (("center".equals(t[i]) || "at".equals(t[i])) && i + 2 < t.length) {
                for (int k = 0; k < 2; k++) {
                    String n = t[i + 1 + k];
                    percent[k] = n.endsWith("%");
                    value[k] = Float.parseFloat(n.replace("%", "").replace("px", ""));
                }
            } else if ("radius".equals(t[i]) && i + 1 < t.length) {
                percent[2] = t[i + 1].endsWith("%");
                value[2] = Float.parseFloat(t[i + 1].replace("%", "").replace("px", ""));
            }
        }
    }

    /** CSS angle of "to right", "to bottom left"...: 0 is upwards, clockwise. */
    private static float sideAngle(String sides) {
        boolean top = sides.contains("top");
        boolean bottom = sides.contains("bottom");
        boolean left = sides.contains("left");
        boolean right = sides.contains("right");
        if (top && right) {
            return 45;
        } else if (bottom && right) {
            return 135;
        } else if (bottom && left) {
            return 225;
        } else if (top && left) {
            return 315;
        }
        return right ? 90 : bottom ? 180 : left ? 270 : 0;
    }

    private static float position(String token) {
        String t = token.toLowerCase(Locale.ROOT);
        if (t.endsWith("%")) {
            return Float.parseFloat(t.substring(0, t.length() - 1)) / 100f;
        }
        if (t.endsWith("px")) {
            return Float.NaN; // a length: not known without the box, spread instead
        }
        float v = Float.parseFloat(t);
        return v >= 0 && v <= 1 ? v : Float.NaN;
    }

    /** Fills the stops that have no position: the first is 0, the last 1, the others evenly between their neighbours. */
    private static float[] spread(List<Float> given) {
        int n = given.size();
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            out[i] = given.get(i);
        }
        if (Float.isNaN(out[0])) {
            out[0] = 0;
        }
        if (Float.isNaN(out[n - 1])) {
            out[n - 1] = 1;
        }
        int i = 1;
        while (i < n - 1) {
            if (!Float.isNaN(out[i])) {
                i++;
                continue;
            }
            int j = i;
            while (Float.isNaN(out[j])) {
                j++;
            }
            for (int k = i; k < j; k++) {
                out[k] = out[i - 1] + (out[j] - out[i - 1]) * (k - i + 1) / (j - i + 1);
            }
            i = j;
        }
        for (int k = 1; k < n; k++) {
            out[k] = Math.max(out[k], out[k - 1]);
            out[k] = Math.min(1f, Math.max(0f, out[k]));
        }
        out[0] = Math.max(0f, out[0]);
        return out;
    }

    /** Splits at {@code separator} outside parentheses, dropping empty parts when the separator is a space. */
    private static List<String> split(String s, char separator) {
        List<String> out = new ArrayList<String>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i <= s.length(); i++) {
            char c = i < s.length() ? s.charAt(i) : separator;
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == separator && depth == 0) {
                String part = s.substring(start, i);
                if (separator != ' ' || !part.isEmpty()) {
                    out.add(part);
                }
                start = i + 1;
            }
        }
        return out;
    }

    /** An ARGB color from {@code #RGB}, {@code #RRGGBB}, {@code #AARRGGBB}, {@code rgb()}, {@code rgba()} or a name; null if none. */
    static Integer color(String token) {
        String t = token.trim().toLowerCase(Locale.ROOT);
        try {
            if (t.startsWith("#")) {
                String h = t.substring(1);
                if (h.length() == 3) {
                    h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
                }
                if (h.length() == 6) {
                    return 0xFF000000 | Integer.parseInt(h, 16);
                }
                return h.length() == 8 ? (int) Long.parseLong(h, 16) : null;
            }
            if (t.startsWith("rgb")) {
                List<String> a = split(t.substring(t.indexOf('(') + 1, t.lastIndexOf(')')), ',');
                int r = channel(a.get(0));
                int g = channel(a.get(1));
                int b = channel(a.get(2));
                int alpha = a.size() > 3 ? Math.round(255 * Math.max(0f, Math.min(1f, Float.parseFloat(a.get(3).trim())))) : 255;
                return alpha << 24 | r << 16 | g << 8 | b;
            }
        } catch (RuntimeException e) {
            return null;
        }
        switch (t) {
            case "transparent": return 0;
            case "white": return 0xFFFFFFFF;
            case "black": return 0xFF000000;
            case "red": return 0xFFFF0000;
            case "green": return 0xFF008000;
            case "blue": return 0xFF0000FF;
            case "yellow": return 0xFFFFFF00;
            case "orange": return 0xFFFFA500;
            case "gray":
            case "grey": return 0xFF808080;
            default: return null;
        }
    }

    private static int channel(String text) {
        String t = text.trim();
        float v = t.endsWith("%") ? Float.parseFloat(t.substring(0, t.length() - 1)) * 2.55f : Float.parseFloat(t);
        return Math.max(0, Math.min(255, Math.round(v)));
    }

    // ---- geometry for a box ------------------------------------------------------------------------

    private float at(int i, float size) {
        return percent[i] ? value[i] / 100f * size : value[i];
    }

    /** Where a linear gradient starts and ends over the box: x0, y0, x1, y1. */
    float[] axis(float x, float y, float w, float h) {
        if (byAngle) {
            double r = Math.toRadians(angle);
            float dx = (float) Math.sin(r);
            float dy = (float) -Math.cos(r);
            float half = (Math.abs(w * dx) + Math.abs(h * dy)) / 2; // the corner-to-corner length CSS uses
            float mx = x + w / 2;
            float my = y + h / 2;
            return new float[] {mx - dx * half, my - dy * half, mx + dx * half, my + dy * half};
        }
        return new float[] {x + at(0, w), y + at(1, h), x + at(2, w), y + at(3, h)};
    }

    /** The circle of a radial gradient over the box: centre x, centre y, radius. */
    float[] circle(float x, float y, float w, float h) {
        return new float[] {x + at(0, w), y + at(1, h), percent[2] ? value[2] / 100f * Math.min(w, h) : value[2]};
    }

    /** The position 0..1 along the gradient of a point of the box. */
    float position(float px, float py, float x, float y, float w, float h) {
        if (radial) {
            float[] c = circle(x, y, w, h);
            float dx = px - c[0];
            float dy = py - c[1];
            return c[2] <= 0 ? 1 : (float) Math.sqrt(dx * dx + dy * dy) / c[2];
        }
        float[] a = axis(x, y, w, h);
        float ax = a[2] - a[0];
        float ay = a[3] - a[1];
        float len2 = ax * ax + ay * ay;
        return len2 <= 0 ? 0 : ((px - a[0]) * ax + (py - a[1]) * ay) / len2;
    }

    /** The color at {@code t} (clamped outside the first and last stop). */
    int colorAt(float t) {
        int n = offsets.length;
        if (t <= offsets[0]) {
            return colors[0];
        }
        for (int i = 1; i < n; i++) {
            if (t <= offsets[i]) {
                float span = offsets[i] - offsets[i - 1];
                return span <= 0 ? colors[i] : mix(colors[i - 1], colors[i], (t - offsets[i - 1]) / span);
            }
        }
        return colors[n - 1];
    }

    private static int mix(int c0, int c1, float t) {
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            out |= (Math.round(((c0 >>> shift) & 0xFF) * (1 - t) + ((c1 >>> shift) & 0xFF) * t) & 0xFF) << shift;
        }
        return out;
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder(radial ? "radial" : "linear");
        out.append('[').append(byAngle ? String.format(Locale.ROOT, "%.1fdeg", angle) : "");
        for (int i = 0; i < value.length; i++) {
            out.append(' ').append(String.format(Locale.ROOT, "%.1f", value[i])).append(percent[i] ? "%" : "px");
        }
        out.append(']');
        for (int i = 0; i < offsets.length; i++) {
            out.append(' ').append(String.format(Locale.ROOT, "%.2f", offsets[i])).append(':').append(Integer.toHexString(colors[i]));
        }
        return out.toString();
    }
}
