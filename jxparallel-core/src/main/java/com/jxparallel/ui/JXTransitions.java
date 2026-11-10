package com.jxparallel.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The text of the {@code transition} and {@code enter} props of an element, parsed (strictly) into values the renderer
 * animates with.
 *
 * <pre>
 * transition = "opacity 200ms ease-out, translateX 0.3s linear 50ms"      (or "all 200ms")
 * enter      = "opacity 0 200ms, translateY 12 200ms ease-out"
 * </pre>
 *
 * A transition item is {@code property duration [easing] [delay]}; the first time is the duration, the second the delay,
 * as in CSS. An enter item is {@code property from-value duration [easing] [delay]}. Times are written {@code 200ms} or
 * {@code 0.3s}; the easing is {@code linear}, {@code ease}, {@code ease-in}, {@code ease-out} or {@code ease-in-out}
 * (default {@code ease}). The animatable properties are {@link #PROPERTIES}; {@code all} (transitions only) stands for
 * every one that has no item of its own. Anything else is an {@link IllegalArgumentException} that says what was wrong.
 * Each distinct text is parsed once.
 */
public final class JXTransitions {
    /** The props that can be animated. The first six are numbers, the last two ARGB colors. */
    public static final List<String> PROPERTIES = Collections.unmodifiableList(java.util.Arrays.asList(
            "opacity", "translateX", "translateY", "scaleX", "scaleY", "rotate", "background", "textFill"));

    /** Index of the first colour property in {@link #PROPERTIES}. */
    public static final int FIRST_COLOR = 6;

    private static final ConcurrentMap<String, Transition> TRANSITIONS = new ConcurrentHashMap<String, Transition>();
    private static final ConcurrentMap<String, Enter> ENTERS = new ConcurrentHashMap<String, Enter>();

    private JXTransitions() {
    }

    /** The index of an animatable prop in {@link #PROPERTIES}, or -1. */
    public static int indexOf(String property) {
        switch (property) {
            case "opacity":
                return 0;
            case "translateX":
                return 1;
            case "translateY":
                return 2;
            case "scaleX":
                return 3;
            case "scaleY":
                return 4;
            case "rotate":
                return 5;
            case "background":
                return 6;
            case "textFill":
                return 7;
            default:
                return -1;
        }
    }

    /** What a number property is when the element does not set it. */
    public static double defaultValue(int index) {
        return index == 0 || index == 3 || index == 4 ? 1 : 0;
    }

    /** The parsed {@code transition} text; throws IllegalArgumentException if it is not valid. */
    public static Transition transition(String text) {
        if (text == null) {
            throw new IllegalArgumentException("transition cannot be null");
        }
        Transition cached = TRANSITIONS.get(text);
        if (cached == null) {
            cached = parseTransition(text);
            TRANSITIONS.put(text, cached);
        }
        return cached;
    }

    /** The parsed {@code enter} text; throws IllegalArgumentException if it is not valid. */
    public static Enter enter(String text) {
        if (text == null) {
            throw new IllegalArgumentException("enter cannot be null");
        }
        Enter cached = ENTERS.get(text);
        if (cached == null) {
            cached = parseEnter(text);
            ENTERS.put(text, cached);
        }
        return cached;
    }

    /** Checks a {@code transition} text without keeping the result (build time). */
    public static void checkTransition(String text) {
        transition(text);
    }

    /** Checks an {@code enter} text without keeping the result (build time). */
    public static void checkEnter(String text) {
        enter(text);
    }

    /** The timing function of an item: a CSS cubic-bezier curve, or the identity for {@code linear}. */
    public static final class Easing {
        public static final Easing LINEAR = new Easing("linear", 0, 0, 1, 1);
        public static final Easing EASE = new Easing("ease", 0.25, 0.1, 0.25, 1);
        public static final Easing EASE_IN = new Easing("ease-in", 0.42, 0, 1, 1);
        public static final Easing EASE_OUT = new Easing("ease-out", 0, 0, 0.58, 1);
        public static final Easing EASE_IN_OUT = new Easing("ease-in-out", 0.42, 0, 0.58, 1);

        private final String name;
        private final double x1;
        private final double y1;
        private final double x2;
        private final double y2;

        private Easing(String name, double x1, double y1, double x2, double y2) {
            this.name = name;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }

        static Easing named(String name) {
            switch (name) {
                case "linear":
                    return LINEAR;
                case "ease":
                    return EASE;
                case "ease-in":
                    return EASE_IN;
                case "ease-out":
                    return EASE_OUT;
                case "ease-in-out":
                    return EASE_IN_OUT;
                default:
                    return null;
            }
        }

        /** The eased progress for a linear progress {@code t}: 0 at 0 and 1 at 1 (t outside 0..1 is clamped). */
        public double apply(double t) {
            if (t <= 0) {
                return 0;
            }
            if (t >= 1) {
                return 1;
            }
            if (this == LINEAR) {
                return t;
            }
            // find the curve parameter s whose x(s) = t (Newton, then bisection when the slope is too flat), and return y(s)
            double s = t;
            for (int i = 0; i < 8; i++) {
                double error = bezier(s, x1, x2) - t;
                if (Math.abs(error) < 1e-7) {
                    return bezier(s, y1, y2);
                }
                double slope = slope(s, x1, x2);
                if (Math.abs(slope) < 1e-6) {
                    break;
                }
                s -= error / slope;
            }
            double low = 0;
            double high = 1;
            s = t;
            for (int i = 0; i < 40; i++) {
                double x = bezier(s, x1, x2);
                if (Math.abs(x - t) < 1e-7) {
                    break;
                }
                if (x < t) {
                    low = s;
                } else {
                    high = s;
                }
                s = (low + high) / 2;
            }
            return bezier(s, y1, y2);
        }

        private static double bezier(double s, double p1, double p2) {
            double u = 1 - s;
            return 3 * u * u * s * p1 + 3 * u * s * s * p2 + s * s * s;
        }

        private static double slope(double s, double p1, double p2) {
            double u = 1 - s;
            return 3 * u * u * p1 + 6 * u * s * (p2 - p1) + 3 * s * s * (1 - p2);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** One animated prop: its timing, and for an enter animation the value it starts from. */
    public static final class Item {
        /** Index in {@link #PROPERTIES}, or -1 for {@code all}. */
        public final int property;
        public final long durationNanos;
        public final long delayNanos;
        public final Easing easing;
        /** The starting value of an enter item: a Double, or an Integer ARGB color; null in a transition. */
        public final Object from;

        Item(int property, long durationNanos, long delayNanos, Easing easing, Object from) {
            this.property = property;
            this.durationNanos = durationNanos;
            this.delayNanos = delayNanos;
            this.easing = easing;
            this.from = from;
        }
    }

    /** A parsed {@code transition}: the item of each prop (null: none), and the one for {@code all}. */
    public static final class Transition {
        private final Item[] byProperty;
        private final Item all;

        Transition(Item[] byProperty, Item all) {
            this.byProperty = byProperty;
            this.all = all;
        }

        /** How the prop with this index in {@link #PROPERTIES} animates; its own item first, else {@code all}; null for a jump. */
        public Item forProperty(int index) {
            Item own = byProperty[index];
            return own != null ? own : all;
        }
    }

    /** A parsed {@code enter}. */
    public static final class Enter {
        private final List<Item> items;

        Enter(List<Item> items) {
            this.items = Collections.unmodifiableList(items);
        }

        public List<Item> items() {
            return items;
        }
    }

    private static Transition parseTransition(String text) {
        Item[] byProperty = new Item[PROPERTIES.size()];
        Item all = null;
        for (String item : items(text, "transition")) {
            String[] tokens = item.split("\\s+");
            String name = tokens[0];
            boolean isAll = "all".equals(name);
            int index = isAll ? -1 : indexOf(name);
            if (!isAll && index < 0) {
                throw failure("transition", text, item, "\"" + name + "\" cannot be animated; use all or one of " + PROPERTIES);
            }
            Item parsed = timing("transition", text, item, tokens, 1, index, null);
            if (isAll) {
                if (all != null) {
                    throw failure("transition", text, item, "all is given twice");
                }
                all = parsed;
            } else {
                if (byProperty[index] != null) {
                    throw failure("transition", text, item, name + " is given twice");
                }
                byProperty[index] = parsed;
            }
        }
        return new Transition(byProperty, all);
    }

    private static Enter parseEnter(String text) {
        List<Item> parsed = new ArrayList<Item>();
        boolean[] seen = new boolean[PROPERTIES.size()];
        for (String item : items(text, "enter")) {
            String[] tokens = item.split("\\s+");
            String name = tokens[0];
            int index = indexOf(name);
            if (index < 0) {
                throw failure("enter", text, item, "\"" + name + "\" cannot be animated; use one of " + PROPERTIES);
            }
            if (seen[index]) {
                throw failure("enter", text, item, name + " is given twice");
            }
            seen[index] = true;
            if (tokens.length < 3) {
                throw failure("enter", text, item, "expected: property from-value duration [easing] [delay]");
            }
            Object from = index >= FIRST_COLOR ? (Object) color("enter", text, item, tokens[1]) : number("enter", text, item, tokens[1]);
            parsed.add(timing("enter", text, item, tokens, 2, index, from));
        }
        return new Enter(parsed);
    }

    private static List<String> items(String text, String what) {
        if (text.trim().isEmpty()) {
            throw new IllegalArgumentException(what + " is empty; expected, for example, \""
                    + ("enter".equals(what) ? "opacity 0 200ms" : "opacity 200ms ease-out") + "\"");
        }
        List<String> items = new ArrayList<String>();
        for (String part : text.split(",", -1)) {
            String item = part.trim();
            if (item.isEmpty()) {
                throw failure(what, text, part, "an item is empty (a stray comma?)");
            }
            items.add(item);
        }
        return items;
    }

    /** duration [easing] [delay], from tokens[start]. */
    private static Item timing(String what, String text, String item, String[] tokens, int start, int index, Object from) {
        Long duration = null;
        Long delay = null;
        Easing easing = null;
        if (start >= tokens.length) {
            throw failure(what, text, item, "the duration is missing, such as 200ms");
        }
        for (int i = start; i < tokens.length; i++) {
            String token = tokens[i];
            Easing named = Easing.named(token);
            if (named != null) {
                if (easing != null) {
                    throw failure(what, text, item, "two easing functions (" + easing + " and " + token + ")");
                }
                easing = named;
            } else if (duration == null) {
                duration = time(what, text, item, token);
            } else if (delay == null) {
                delay = time(what, text, item, token);
            } else {
                throw failure(what, text, item, "\"" + token + "\" is one time too many (duration, then delay)");
            }
        }
        if (duration == null) {
            throw failure(what, text, item, "the duration is missing, such as 200ms");
        }
        return new Item(index, duration, delay == null ? 0 : delay, easing == null ? Easing.EASE : easing, from);
    }

    private static long time(String what, String text, String item, String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        double scale;
        String number;
        if (lower.endsWith("ms")) {
            scale = 1_000_000d;
            number = lower.substring(0, lower.length() - 2);
        } else if (lower.endsWith("s")) {
            scale = 1_000_000_000d;
            number = lower.substring(0, lower.length() - 1);
        } else {
            throw failure(what, text, item, "\"" + token + "\" is not a time (write 200ms or 0.2s) or an easing function"
                    + " (linear, ease, ease-in, ease-out, ease-in-out)");
        }
        double value;
        try {
            value = Double.parseDouble(number);
        } catch (NumberFormatException e) {
            throw failure(what, text, item, "\"" + token + "\" is not a time (write 200ms or 0.2s)");
        }
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) {
            throw failure(what, text, item, "\"" + token + "\" is not a time of zero or more");
        }
        return Math.round(value * scale);
    }

    private static Double number(String what, String text, String item, String token) {
        try {
            double value = Double.parseDouble(token);
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                throw new NumberFormatException();
            }
            return Double.valueOf(value);
        } catch (NumberFormatException e) {
            throw failure(what, text, item, "\"" + token + "\" is not a number (the value the prop starts from)");
        }
    }

    private static Integer color(String what, String text, String item, String token) {
        String message = "\"" + token + "\" is not a color; write #RGB, #RRGGBB or #AARRGGBB";
        if (!token.startsWith("#")) {
            throw failure(what, text, item, message);
        }
        String hex = token.substring(1);
        for (int i = 0; i < hex.length(); i++) {
            if (Character.digit(hex.charAt(i), 16) < 0) {
                throw failure(what, text, item, message);
            }
        }
        if (hex.length() == 3) {
            hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
        }
        if (hex.length() == 6) {
            return Integer.valueOf((int) (0xFF000000L | Long.parseLong(hex, 16)));
        }
        if (hex.length() == 8) {
            return Integer.valueOf((int) Long.parseLong(hex, 16));
        }
        throw failure(what, text, item, message);
    }

    private static IllegalArgumentException failure(String what, String text, String item, String problem) {
        return new IllegalArgumentException("invalid " + what + " \"" + text + "\" at \"" + item.trim() + "\": " + problem);
    }
}
