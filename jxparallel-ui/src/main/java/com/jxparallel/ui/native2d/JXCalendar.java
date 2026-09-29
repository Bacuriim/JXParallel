package com.jxparallel.ui.native2d;

import java.time.DayOfWeek;
import java.time.LocalDate;

import com.jxparallel.ui.text.JXTextEngine;

/**
 * The month grid of a DatePicker popup ({@code calendar} element). Props: {@code year},
 * {@code month} (1-12), {@code selectedYear}/{@code selectedMonth}/{@code selectedDay},
 * {@code todayYear}/{@code todayMonth}/{@code todayDay}, {@code firstDayOfWeek} (1 = Monday ... 7 =
 * Sunday), {@code weekdays} (seven short names from the first day of the week) and {@code title}
 * ("September 2026"); names come from the caller's locale.
 */
public final class JXCalendar {
    static final int PAD = 6;
    static final int HEADER = 30;
    static final int WEEKDAYS = 22;
    static final int CELL_W = 32;
    static final int CELL_H = 24;
    static final int WIDTH = PAD * 2 + 7 * CELL_W;
    static final int HEIGHT = PAD * 2 + HEADER + WEEKDAYS + 6 * CELL_H;

    /** {@link #hit} results. */
    public static final int NONE = 0;
    public static final int DAY = 1;
    public static final int PREVIOUS = 2;
    public static final int NEXT = 3;

    private JXCalendar() {
    }

    /** First date shown in the grid (the start of the week that holds the 1st). */
    static LocalDate gridStart(int year, int month, int firstDayOfWeek) {
        LocalDate first = LocalDate.of(year, month, 1);
        int shift = (first.getDayOfWeek().getValue() - firstDayOfWeek + 7) % 7;
        return first.minusDays(shift);
    }

    /**
     * What is at window (x, y): {@code {DAY, year, month, day}}, {@code {PREVIOUS}}, {@code {NEXT}}
     * or {@code {NONE}}. Days of the neighbouring months shown in the grid are hits too, like JavaFX.
     */
    public static int[] hit(JXNativeNode node, double x, double y) {
        double rx = x - node.getX();
        double ry = y - node.getY();
        if (ry >= PAD && ry < PAD + HEADER) {
            if (rx >= PAD && rx < PAD + 28) {
                return new int[] {PREVIOUS};
            }
            if (rx >= WIDTH - PAD - 28 && rx < WIDTH - PAD) {
                return new int[] {NEXT};
            }
            return new int[] {NONE};
        }
        double gy = ry - PAD - HEADER - WEEKDAYS;
        double gx = rx - PAD;
        if (gy < 0 || gx < 0 || gx >= 7 * CELL_W || gy >= 6 * CELL_H) {
            return new int[] {NONE};
        }
        int index = (int) (gy / CELL_H) * 7 + (int) (gx / CELL_W);
        LocalDate date = gridStart(year(node), month(node), firstDay(node)).plusDays(index);
        return new int[] {DAY, date.getYear(), date.getMonthValue(), date.getDayOfMonth()};
    }

    static void paint(JXPainter p, JXNativeNode node, JXPaint paint) {
        float x = node.getX();
        float y = node.getY();
        p.fillRect(x, y, WIDTH, HEIGHT, JXPaint.BOX_BORDER);
        p.fillRect(x + 1, y + 1, WIDTH - 2, HEIGHT - 2, 0xFFFFFFFF);
        JXTextEngine engine = JXTextEngine.get(false);
        JXTextEngine bold = JXTextEngine.get(true);
        float size = JXTextEngine.DEFAULT_SIZE;
        // header: arrows and month title
        float hy = y + PAD;
        p.fillRoundRectGradient(x + PAD, hy, WIDTH - 2 * PAD, HEADER - 4, 3, 0xFFFDFDFD, 0xFFE2E2E2);
        float cy = hy + (HEADER - 4) / 2;
        p.fillTriangle(x + PAD + 16, cy - 5, x + PAD + 16, cy + 5, x + PAD + 10, cy, JXPaint.ARROW);
        p.fillTriangle(x + WIDTH - PAD - 16, cy - 5, x + WIDTH - PAD - 16, cy + 5, x + WIDTH - PAD - 10, cy, JXPaint.ARROW);
        String title = JXPaint.text(node, "title");
        if (title.isEmpty()) {
            title = month(node) + "/" + year(node);
        }
        float tw = bold.width(title, size);
        p.text(title, x + (WIDTH - tw) / 2, hy + bold.baseline(HEADER - 4, size), size, true, JXPaint.TEXT);
        // weekday names
        String[] names = JXPaint.strings(node.getProperty("weekdays"));
        float wy = y + PAD + HEADER;
        for (int i = 0; i < 7; i++) {
            String name = i < names.length ? names[i] : DayOfWeek.of((firstDay(node) - 1 + i) % 7 + 1).name().substring(0, 2);
            float nw = engine.width(name, 11);
            p.text(name, x + PAD + i * CELL_W + (CELL_W - nw) / 2, wy + engine.baseline(WEEKDAYS, 11), 11, false, 0xFF7A7A7A);
        }
        // days
        LocalDate start = gridStart(year(node), month(node), firstDay(node));
        int selY = (int) JXPaint.number(node, "selectedYear", 0);
        int selM = (int) JXPaint.number(node, "selectedMonth", 0);
        int selD = (int) JXPaint.number(node, "selectedDay", 0);
        int todayY = (int) JXPaint.number(node, "todayYear", 0);
        int todayM = (int) JXPaint.number(node, "todayMonth", 0);
        int todayD = (int) JXPaint.number(node, "todayDay", 0);
        float gy = wy + WEEKDAYS;
        for (int i = 0; i < 42; i++) {
            LocalDate d = start.plusDays(i);
            float cx = x + PAD + (i % 7) * CELL_W;
            float ry = gy + (i / 7) * CELL_H;
            boolean inMonth = d.getMonthValue() == month(node);
            boolean selected = d.getYear() == selY && d.getMonthValue() == selM && d.getDayOfMonth() == selD;
            boolean today = d.getYear() == todayY && d.getMonthValue() == todayM && d.getDayOfMonth() == todayD;
            if (selected) {
                p.fillRect(cx + 1, ry + 1, CELL_W - 2, CELL_H - 2, JXPaint.SELECTION);
            } else if (today) {
                p.strokeRoundRect(cx + 1, ry + 1, CELL_W - 2, CELL_H - 2, 0, 1, JXPaint.ACCENT);
            }
            String label = String.valueOf(d.getDayOfMonth());
            float lw = engine.width(label, size);
            int color = selected ? 0xFFFFFFFF : inMonth ? JXPaint.TEXT : 0xFFAAAAAA;
            p.text(label, cx + (CELL_W - lw) / 2, ry + engine.baseline(CELL_H, size), size, false, color);
        }
    }

    private static int year(JXNativeNode node) {
        return (int) JXPaint.number(node, "year", LocalDate.now().getYear());
    }

    private static int month(JXNativeNode node) {
        return Math.max(1, Math.min(12, (int) JXPaint.number(node, "month", LocalDate.now().getMonthValue())));
    }

    private static int firstDay(JXNativeNode node) {
        int first = (int) JXPaint.number(node, "firstDayOfWeek", 7);
        return first < 1 || first > 7 ? 7 : first;
    }
}
