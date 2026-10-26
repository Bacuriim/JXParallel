package com.jxparallel.ui.native2d;

import java.time.LocalDate;
import java.util.function.Consumer;

/**
 * Tells the application what the user changed in an input. A node whose props hold {@code onChange},
 * a {@link Consumer}, gets the new value when the user edits it: the text of {@code input},
 * {@code password} and {@code textarea} (a String), the {@code checked} state of a {@code checkbox}
 * (a Boolean), the value of a {@code slider} or {@code spinner} (a Double), the chosen option of a
 * {@code select} and the date of a {@code datepicker} (a String, ISO yyyy-MM-dd).
 *
 * <p>Inputs are controlled, like React's: the node keeps showing its {@code value} prop until the
 * application renders again with the new one, so rejecting a change is not rendering it. Text
 * editing here is the basic kind: typing, Backspace, Delete, arrows, Home, End and Enter in a
 * text area, at a caret the node remembers; there is no selection.
 */
final class JXChangeEvents {
    private static final int ENTER = 257;
    private static final int BACKSPACE = 259;
    private static final int DELETE = 261;
    private static final int RIGHT = 262;
    private static final int LEFT = 263;
    private static final int DOWN = 264;
    private static final int UP = 265;
    private static final int PAGE_UP = 266;
    private static final int PAGE_DOWN = 267;
    private static final int HOME = 268;
    private static final int END = 269;

    private JXChangeEvents() {
    }

    @SuppressWarnings("unchecked")
    private static Consumer<Object> handler(JXNativeNode node) {
        Object handler = node.getProperty("onChange");
        return handler instanceof Consumer && !Boolean.TRUE.equals(node.getProperty("disabled"))
                ? (Consumer<Object>) handler : null;
    }

    /**
     * A press on the close icon of a chip (its {@code onClose} runs) or of a tab of a tab bar (its {@code onClose},
     * a Consumer, gets the tab's index). True when the press was one: it is then not a click on the chip or the bar.
     */
    @SuppressWarnings("unchecked")
    static boolean close(JXNativeNode node, JXPointerEvent event) {
        if ("chip".equals(node.getType()) && JXControlLayout.chipCloseAt(node, event.getX())) {
            Object onClose = node.getProperty("onClose");
            if (onClose instanceof Runnable) {
                ((Runnable) onClose).run();
            }
            return true;
        }
        if ("tabbar".equals(node.getType())) {
            boolean[] closeHit = new boolean[1];
            int tab = JXControlLayout.tabbarAt(node, event.getX(), event.getY(), closeHit);
            if (tab >= 0 && closeHit[0]) {
                Object onClose = node.getProperty("onClose");
                if (onClose instanceof Consumer) {
                    ((Consumer<Object>) onClose).accept(Integer.valueOf(tab));
                }
                return true;
            }
        }
        return false;
    }

    static void pointer(JXNativeNode node, JXPointerEvent event) {
        Consumer<Object> onChange = handler(node);
        if (onChange == null) {
            return;
        }
        if ("checkbox".equals(node.getType()) || "switch".equals(node.getType())) {
            onChange.accept(Boolean.valueOf(!Boolean.TRUE.equals(node.getProperty("checked"))));
        } else if ("chip".equals(node.getType())) {
            onChange.accept(Boolean.valueOf(!Boolean.TRUE.equals(node.getProperty("selected"))));
        } else if ("tabbar".equals(node.getType())) {
            int tab = JXControlLayout.tabbarAt(node, event.getX(), event.getY(), null);
            if (tab >= 0 && tab != (int) JXPaint.number(node, "selected", 0)) {
                onChange.accept(Integer.valueOf(tab));
            }
        } else if ("scrollbar".equals(node.getType())) {
            pressBar(node, event, onChange);
        } else if ("splitpane".equals(node.getType())) {
            pressDivider(node, event);
        } else if ("slider".equals(node.getType())) {
            double min = JXPaint.number(node, "min", 0.0);
            double max = JXPaint.number(node, "max", 100.0);
            double fraction = node.vertical()
                    ? 1 - (event.getY() - node.getY() - 7.0) / (node.getHeight() - 14)
                    : (event.getX() - node.getX() - 7.0) / (node.getWidth() - 14);
            fraction = Math.max(0, Math.min(1, fraction));
            onChange.accept(Double.valueOf(min + fraction * (max - min)));
        }
    }

    static void key(JXNativeNode node, JXKeyEvent event) {
        Consumer<Object> onChange = handler(node);
        if (onChange == null) {
            return;
        }
        switch (node.getType()) {
            case "input":
            case "password":
            case "textarea":
                edit(node, event, onChange);
                break;
            case "checkbox":
            case "switch":
                if (event.getKeyChar() == ' ') {
                    onChange.accept(Boolean.valueOf(!Boolean.TRUE.equals(node.getProperty("checked"))));
                }
                break;
            case "spinner":
                step(node, event, onChange);
                break;
            case "select":
                choose(node, event, onChange);
                break;
            case "datepicker":
                pick(node, event, onChange);
                break;
            case "timepicker":
                tick(node, event, onChange);
                break;
            case "colorpicker":
                typeHex(node, event, onChange);
                break;
            default:
                break;
        }
    }

    /**
     * A press on the arrow of a tree row ({@code cell} with a {@code disclosure}) runs its {@code onToggle}. False when
     * the press was elsewhere on the row, or the row has no handler: then it is a click on the row.
     */
    static boolean disclosure(JXNativeNode node, JXPointerEvent event) {
        if (!JXControlLayout.disclosureAt(node, event.getX())) {
            return false;
        }
        Object onToggle = node.getProperty("onToggle");
        if (onToggle instanceof Runnable) {
            ((Runnable) onToggle).run();
            return true;
        }
        return false;
    }

    /** Up and Down move a time picker by its {@code minuteStep}, Page Up and Page Down by an hour; it wraps around the day. */
    private static void tick(JXNativeNode node, JXKeyEvent event, Consumer<Object> onChange) {
        int code = event.getKeyCode();
        if (code != UP && code != DOWN && code != PAGE_UP && code != PAGE_DOWN) {
            return;
        }
        int now = JXOverlays.parseTime(JXPaint.text(node, "value"));
        if (now < 0) {
            now = 0;
        }
        int step = (code == UP || code == DOWN) ? JXOverlays.minuteStep(node) : 60;
        int sign = code == UP || code == PAGE_UP ? 1 : -1;
        int next = ((now + sign * step) % 1440 + 1440) % 1440;
        onChange.accept(JXOverlays.formatTime(next / 60, next % 60));
    }

    /**
     * Typing in the hex field of an open color picker: {@code #} and hex digits are added to the text being typed,
     * Backspace removes one; every time it reads {@code #RRGGBB} the color is reported through {@code onChange}.
     */
    private static void typeHex(JXNativeNode node, JXKeyEvent event, Consumer<Object> onChange) {
        if (!JXOverlays.isOpen(node)) {
            return;
        }
        String draft = JXOverlays.hexDraft(node);
        char typed = event.getKeyChar();
        int code = event.getKeyCode();
        String next = draft;
        if (code == BACKSPACE && !draft.isEmpty()) {
            next = draft.substring(0, draft.length() - 1);
        } else if (typed == '#' && draft.isEmpty() || Character.digit(typed, 16) >= 0 && typed < 128) {
            String base = draft.isEmpty() && typed != '#' ? "#" : draft;
            if (base.length() < 7) {
                next = base + (typed == '#' ? "" : String.valueOf(Character.toUpperCase(typed)));
            }
        }
        node.hexDraft = next;
        if (!next.equals(draft) && JXOverlays.validHex(next)) {
            onChange.accept(next);
        }
    }

    // ---- scroll bar and split pane -----------------------------------------------------------

    /** The pointer's position along the axis of a scroll bar or split pane, in window pixels. */
    private static float along(JXNativeNode node, JXPointerEvent event) {
        return node.vertical() ? event.getY() : event.getX();
    }

    /** The value a scroll bar has when its thumb starts at {@code thumbStart} (the thumb is at most the track). */
    private static double barValue(JXNativeNode node, float[] g, float thumbStart) {
        double min = JXPaint.number(node, "min", 0);
        double max = JXPaint.number(node, "max", 100);
        float room = g[1] - g[3];
        double fraction = room <= 0 ? 0 : Math.max(0, Math.min(1, (thumbStart - g[0]) / room));
        return min + fraction * Math.max(0, max - min);
    }

    /** A press on the thumb grabs it; one on the track puts the middle of the thumb under the pointer. */
    private static void pressBar(JXNativeNode node, JXPointerEvent event, Consumer<Object> onChange) {
        float[] g = JXControlLayout.scrollBarGeometry(node);
        float at = along(node, event);
        node.dragThumb = at >= g[2] && at <= g[2] + g[3];
        if (node.dragThumb) {
            node.dragOffset = at - g[2];
            return;
        }
        report(node, barValue(node, g, at - g[3] / 2), onChange);
    }

    private static void report(JXNativeNode node, double value, Consumer<Object> onChange) {
        if (value != JXPaint.number(node, "value", Double.NaN)) {
            onChange.accept(Double.valueOf(value));
        }
    }

    /** A press on a divider of a split pane grabs it. */
    private static void pressDivider(JXNativeNode node, JXPointerEvent event) {
        float[][] rects = JXControlLayout.splitRects(node);
        int panes = node.getChildren().size();
        node.dragDivider = -1;
        for (int i = panes; i < rects.length; i++) {
            float[] r = rects[i];
            boolean on = event.getX() >= r[0] - 2 && event.getX() < r[0] + r[2] + 2 && event.getY() >= r[1] - 2
                    && event.getY() < r[1] + r[3] + 2;
            if (on) {
                node.dragDivider = i - panes;
                node.dragOffset = along(node, event) - (node.vertical() ? r[1] : r[0]);
                return;
            }
        }
    }

    /**
     * The pointer moved with the button held after a press that grabbed a scroll bar's thumb (its {@code onChange}
     * gets the new value, a Double) or a divider (it gets the positions of all the dividers as a double[], with the
     * grabbed one moved, kept between its neighbours and at least {@link JXControlLayout#MIN_PANE} pixels from them).
     */
    static void drag(JXNativeNode node, JXPointerEvent event) {
        Consumer<Object> onChange = handler(node);
        if (onChange == null) {
            return;
        }
        if ("scrollbar".equals(node.getType()) && node.dragThumb) {
            float[] g = JXControlLayout.scrollBarGeometry(node);
            report(node, barValue(node, g, along(node, event) - node.dragOffset), onChange);
        } else if ("splitpane".equals(node.getType()) && node.dragDivider >= 0) {
            int i = node.dragDivider;
            double[] positions = JXControlLayout.splitPositions(node);
            float room = JXControlLayout.splitRoom(node);
            if (i >= positions.length || room <= 0) {
                return;
            }
            float origin = node.vertical() ? node.getY() : node.getX();
            double start = along(node, event) - node.dragOffset - origin - i * JXControlLayout.DIVIDER;
            double min = (i == 0 ? 0 : positions[i - 1]) + JXControlLayout.MIN_PANE / (double) room;
            double max = (i == positions.length - 1 ? 1 : positions[i + 1]) - JXControlLayout.MIN_PANE / (double) room;
            double next = max < min ? positions[i] : Math.max(min, Math.min(max, start / room));
            if (Math.abs(next - positions[i]) > 1e-9) {
                double[] out = positions.clone();
                out[i] = next;
                onChange.accept(out);
            }
        }
    }

    private static void edit(JXNativeNode node, JXKeyEvent event, Consumer<Object> onChange) {
        String text = JXPaint.text(node, "value");
        int caret = Math.max(0, Math.min(text.length(), node.caret < 0 ? text.length() : node.caret));
        char typed = event.getKeyChar();
        int code = event.getKeyCode();
        String next = text;
        int anchor = node.anchor < 0 ? caret : Math.min(node.anchor, text.length());
        boolean insert = typed >= 32 && typed != 127 || code == ENTER && "textarea".equals(node.getType());
        if (anchor != caret && (insert || code == BACKSPACE || code == DELETE || code == LEFT || code == RIGHT)) {
            // a selection (a host put it there, see JXTextInput): typing and deleting replace it, the arrows end it
            int start = Math.min(anchor, caret);
            int end = Math.max(anchor, caret);
            if (insert) {
                next = text.substring(0, start) + (typed >= 32 ? String.valueOf(typed) : "\n") + text.substring(end);
                caret = start + 1;
            } else if (code == BACKSPACE || code == DELETE) {
                next = text.substring(0, start) + text.substring(end);
                caret = start;
            } else {
                caret = code == LEFT ? start : end;
            }
        } else if (typed >= 32 && typed != 127) {
            next = text.substring(0, caret) + typed + text.substring(caret);
            caret++;
        } else if (code == ENTER && "textarea".equals(node.getType())) {
            next = text.substring(0, caret) + '\n' + text.substring(caret);
            caret++;
        } else if (code == BACKSPACE && caret > 0) {
            next = text.substring(0, caret - 1) + text.substring(caret);
            caret--;
        } else if (code == DELETE && caret < text.length()) {
            next = text.substring(0, caret) + text.substring(caret + 1);
        } else if (code == LEFT) {
            caret = Math.max(0, caret - 1);
        } else if (code == RIGHT) {
            caret = Math.min(text.length(), caret + 1);
        } else if (code == HOME) {
            caret = 0;
        } else if (code == END) {
            caret = text.length();
        }
        node.caret = caret;
        node.anchor = -1;
        if (!next.equals(text)) {
            onChange.accept(next);
        }
    }

    /** Up and Down change a spinner by its {@code step} (1 if none) within {@code min} and {@code max}. */
    private static void step(JXNativeNode node, JXKeyEvent event, Consumer<Object> onChange) {
        int code = event.getKeyCode();
        if (code != UP && code != DOWN) {
            return;
        }
        double current;
        try {
            current = Double.parseDouble(JXPaint.text(node, "value").trim());
        } catch (NumberFormatException e) {
            current = JXPaint.number(node, "min", 0.0);
        }
        double next = current + (code == UP ? 1 : -1) * JXPaint.number(node, "step", 1.0);
        next = Math.max(JXPaint.number(node, "min", -Double.MAX_VALUE), Math.min(JXPaint.number(node, "max", Double.MAX_VALUE), next));
        onChange.accept(Double.valueOf(next));
    }

    /** Up and Down move to the previous or next option of a select. */
    private static void choose(JXNativeNode node, JXKeyEvent event, Consumer<Object> onChange) {
        int code = event.getKeyCode();
        String[] options = JXPaint.strings(node.getProperty("options"));
        if ((code != UP && code != DOWN) || options.length == 0) {
            return;
        }
        String value = JXPaint.text(node, "value");
        int at = java.util.Arrays.asList(options).indexOf(value);
        int next = Math.max(0, Math.min(options.length - 1, at < 0 ? (code == DOWN ? 0 : options.length - 1) : at + (code == DOWN ? 1 : -1)));
        if (next != at) {
            onChange.accept(options[next]);
        }
    }

    /** Up and Down move a date picker by a day, Page Up and Page Down by a month; an empty one starts today. */
    private static void pick(JXNativeNode node, JXKeyEvent event, Consumer<Object> onChange) {
        int code = event.getKeyCode();
        if (code != UP && code != DOWN && code != PAGE_UP && code != PAGE_DOWN) {
            return;
        }
        LocalDate date;
        try {
            date = LocalDate.parse(JXPaint.text(node, "value").trim());
        } catch (java.time.format.DateTimeParseException e) {
            date = LocalDate.now();
        }
        int sign = code == UP || code == PAGE_UP ? 1 : -1;
        onChange.accept((code == UP || code == DOWN ? date.plusDays(sign) : date.plusMonths(sign)).toString());
    }
}
