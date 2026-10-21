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

    static void pointer(JXNativeNode node, JXPointerEvent event) {
        Consumer<Object> onChange = handler(node);
        if (onChange == null) {
            return;
        }
        if ("checkbox".equals(node.getType())) {
            onChange.accept(Boolean.valueOf(!Boolean.TRUE.equals(node.getProperty("checked"))));
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
            default:
                break;
        }
    }

    private static void edit(JXNativeNode node, JXKeyEvent event, Consumer<Object> onChange) {
        String text = JXPaint.text(node, "value");
        int caret = Math.max(0, Math.min(text.length(), node.caret < 0 ? text.length() : node.caret));
        char typed = event.getKeyChar();
        int code = event.getKeyCode();
        String next = text;
        if (typed >= 32 && typed != 127) {
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
