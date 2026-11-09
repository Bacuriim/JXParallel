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
        if ("tabbar".equals(node.getType()) || "tabs".equals(node.getType())) {
            boolean[] closeHit = new boolean[1];
            int tab = "tabs".equals(node.getType()) ? JXControlLayout.tabAt(node, event.getX(), event.getY(), closeHit)
                    : JXControlLayout.tabbarAt(node, event.getX(), event.getY(), closeHit);
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
        } else if ("tabbar".equals(node.getType()) || "tabs".equals(node.getType())) {
            int tab = "tabs".equals(node.getType()) ? JXControlLayout.tabAt(node, event.getX(), event.getY(), null)
                    : JXControlLayout.tabbarAt(node, event.getX(), event.getY(), null);
            if (tab >= 0 && tab != (int) JXPaint.number(node, "selected", 0)) {
                onChange.accept(Integer.valueOf(tab));
            }
        } else if ("pagination".equals(node.getType())) {
            turnPage(node, event, onChange);
        } else if ("scrollbar".equals(node.getType())) {
            pressBar(node, event, onChange);
        } else if ("splitpane".equals(node.getType())) {
            pressDivider(node, event);
        } else if ("slider".equals(node.getType())) {
            node.dragThumb = true; // the thumb follows the pointer until the release
            slide(node, event, onChange);
        } else if ("spinner".equals(node.getType()) && event.getX() >= node.getX() + node.getWidth() - JXControlLayout.SPINNER_BUTTON) {
            stepBy(node, event.getY() < node.getY() + node.getHeight() / 2 ? 1 : -1, onChange); // its arrow buttons
        }
    }

    /** Puts a slider's value under the pointer (a press on the track, or the drag of its thumb). */
    private static void slide(JXNativeNode node, JXPointerEvent event, Consumer<Object> onChange) {
        double min = JXPaint.number(node, "min", 0.0);
        double max = JXPaint.number(node, "max", 100.0);
        double fraction = node.vertical()
                ? 1 - (event.getY() - node.getY() - 7.0) / (node.getHeight() - 14)
                : (event.getX() - node.getX() - 7.0) / (node.getWidth() - 14);
        fraction = Math.max(0, Math.min(1, fraction));
        onChange.accept(Double.valueOf(min + fraction * (max - min)));
    }

    /**
     * Keys on a slider, as JavaFX's: Left and Down take a {@code blockIncrement} (a tenth of the range when not set), Right
     * and Up add one, Home and End go to {@code min} and {@code max}.
     */
    private static void slideKey(JXNativeNode node, JXKeyEvent event, Consumer<Object> onChange) {
        double min = JXPaint.number(node, "min", 0.0);
        double max = JXPaint.number(node, "max", 100.0);
        double value = JXPaint.number(node, "value", min);
        double block = JXPaint.number(node, "blockIncrement", (max - min) / 10);
        int code = event.getKeyCode();
        double next = code == RIGHT || code == UP ? value + block : code == LEFT || code == DOWN ? value - block
                : code == HOME ? min : code == END ? max : value;
        next = Math.max(min, Math.min(max, next));
        if (next != value) {
            onChange.accept(Double.valueOf(next));
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
            case "slider":
                slideKey(node, event, onChange);
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

    /** A press on a page number of a pagination goes to that page, on an arrow to the one before or after. */
    private static void turnPage(JXNativeNode node, JXPointerEvent event, Consumer<Object> onChange) {
        int count = Math.max(1, (int) JXPaint.number(node, "pageCount", 1));
        int current = (int) JXPaint.number(node, "current", 0);
        for (float[] b : JXControlLayout.pageButtons(node)) {
            if (event.getX() >= b[0] && event.getX() < b[0] + b[2] && event.getY() >= b[1] && event.getY() < b[1] + b[3]) {
                int page = b[4] == -1 ? current - 1 : b[4] == -2 ? current + 1 : (int) b[4];
                page = Math.max(0, Math.min(count - 1, page));
                if (page != current) {
                    onChange.accept(Integer.valueOf(page));
                }
                return;
            }
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
        if ("slider".equals(node.getType()) && node.dragThumb) {
            slide(node, event, onChange);
        } else if ("scrollbar".equals(node.getType()) && node.dragThumb) {
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

    private static final int KEY_A = 65;
    private static final int KEY_C = 67;
    private static final int KEY_V = 86;
    private static final int KEY_X = 88;
    private static final int KEY_Y = 89;
    private static final int KEY_Z = 90;
    /** Edits a text input remembers for Ctrl+Z. */
    private static final int UNDO_LIMIT = 100;

    /**
     * Editing a text input: typing, Backspace, Delete and Enter (a text area) replace the selection, if any; the arrows,
     * Home and End move the caret, and with Shift extend the selection from where it started; Ctrl+A selects everything,
     * Ctrl+C, Ctrl+X and Ctrl+V copy, cut and paste (not the text of a password field), Ctrl+Z undoes and Ctrl+Y (or
     * Ctrl+Shift+Z) redoes.
     */
    private static void edit(JXNativeNode node, JXKeyEvent event, Consumer<Object> onChange) {
        String text = JXPaint.text(node, "value");
        boolean area = "textarea".equals(node.getType());
        boolean password = "password".equals(node.getType());
        int caret = Math.max(0, Math.min(text.length(), node.caret < 0 ? text.length() : node.caret));
        int anchor = node.anchor < 0 ? caret : Math.min(node.anchor, text.length());
        int from = Math.min(caret, anchor);
        int to = Math.max(caret, anchor);
        char typed = event.getKeyChar();
        int code = event.getKeyCode();
        boolean shift = event.isShiftDown();
        String insert = null;
        boolean move = true; // a key that moves the caret: Shift keeps the anchor, otherwise it follows
        String next = text;
        if (event.isControlDown()) {
            move = false;
            if (code == KEY_A) {
                anchor = 0;
                caret = text.length();
            } else if ((code == KEY_C || code == KEY_X) && from < to && !password) {
                clipboard().setText(text.substring(from, to));
                if (code == KEY_X) {
                    insert = "";
                }
            } else if (code == KEY_V) {
                String pasted = clipboard().getText();
                insert = pasted == null ? "" : area ? pasted : pasted.replace("\r", "").replace('\n', ' ');
            } else if (code == KEY_Z || code == KEY_Y) {
                boolean undoing = code == KEY_Z && !shift; // Ctrl+Shift+Z and Ctrl+Y redo
                java.util.Deque<Object[]> source = undoing ? node.undo() : node.redo();
                java.util.Deque<Object[]> target = undoing ? node.redo() : node.undo();
                if (!source.isEmpty()) {
                    Object[] state = source.pop();
                    target.push(new Object[] {text, Integer.valueOf(caret)});
                    node.caret = (Integer) state[1];
                    node.anchor = -1;
                    onChange.accept(state[0]);
                }
                return;
            }
        } else if (typed >= 32 && typed != 127) {
            insert = String.valueOf(typed);
        } else if (code == ENTER && area) {
            insert = "\n";
        } else if (code == BACKSPACE) {
            if (from < to) {
                insert = "";
            } else if (caret > 0) {
                from = caret - 1;
                to = caret;
                insert = "";
            }
        } else if (code == DELETE) {
            if (from < to) {
                insert = "";
            } else if (caret < text.length()) {
                from = caret;
                to = caret + 1;
                insert = "";
            }
        } else if ((code == UP || code == DOWN) && area) {
            caret = JXTextHit.areaVertical(node, caret, code == UP ? -1 : 1); // the line above or below, same x
        } else if (code == LEFT) {
            caret = !shift && from < to ? from : Math.max(0, caret - 1); // a selection collapses to its start
        } else if (code == RIGHT) {
            caret = !shift && from < to ? to : Math.min(text.length(), caret + 1);
        } else if (code == HOME) {
            caret = area ? JXTextHit.areaLineOf(node, caret)[1] : 0;
        } else if (code == END) {
            caret = area ? lineEnd(text, caret) : text.length();
        } else {
            move = false;
        }
        if (insert != null) {
            next = text.substring(0, from) + insert + text.substring(to);
            caret = from + insert.length();
            anchor = caret;
        } else if (move && !shift) {
            anchor = caret;
        }
        node.caret = caret;
        node.anchor = anchor == caret ? -1 : anchor;
        if (!next.equals(text)) {
            java.util.Deque<Object[]> undo = node.undo();
            undo.push(new Object[] {text, Integer.valueOf(Math.min(from, text.length()))});
            if (undo.size() > UNDO_LIMIT) {
                undo.removeLast();
            }
            node.redo().clear();
            onChange.accept(next);
        }
    }

    /** The index of the end of the line of a text area that {@code index} is on (the next line break, or the end). */
    private static int lineEnd(String text, int index) {
        int end = text.indexOf('\n', index);
        return end < 0 ? text.length() : end;
    }

    /** The clipboard Ctrl+C, Ctrl+X and Ctrl+V use: the system's, unless a host or a test set another. */
    static com.jxparallel.ui.input.JXClipboard clipboard() {
        com.jxparallel.ui.input.JXClipboard c = JXNativeNode.clipboard;
        return c != null ? c : JXWindow.systemClipboard();
    }

    /** Up and Down change a spinner by its {@code step} (1 if none) within {@code min} and {@code max}. */
    private static void step(JXNativeNode node, JXKeyEvent event, Consumer<Object> onChange) {
        int code = event.getKeyCode();
        if (code == UP || code == DOWN) {
            stepBy(node, code == UP ? 1 : -1, onChange);
        }
    }

    /** Adds {@code sign} steps to a spinner (its arrow keys and its arrow buttons). */
    private static void stepBy(JXNativeNode node, int sign, Consumer<Object> onChange) {
        double current;
        try {
            current = Double.parseDouble(JXPaint.text(node, "value").trim());
        } catch (NumberFormatException e) {
            current = JXPaint.number(node, "min", 0.0);
        }
        double next = current + sign * JXPaint.number(node, "step", 1.0);
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
