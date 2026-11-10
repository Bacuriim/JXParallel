package com.jxparallel.examples.orders;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The numbers of the dashboard, computed from the orders in one pass. */
public final class DashboardStats {
    /** One bar of a bar visual: a label, the value as text, and its size as a fraction of the largest (0 to 1). */
    public static final class Bar {
        private final String label;
        private final String valueText;
        private final double fraction;
        private final int index;

        Bar(String label, String valueText, double fraction, int index) {
            this.label = label;
            this.valueText = valueText;
            this.fraction = fraction;
            this.index = index;
        }

        public String getLabel() {
            return label;
        }

        public String getValueText() {
            return valueText;
        }

        public double getFraction() {
            return fraction;
        }

        /** Which colour of the palette the bar uses. */
        public int getIndex() {
            return index;
        }
    }

    private final int orderCount;
    private final long revenueCents;
    private final int openOrders;
    private final long averageCents;
    private final List<Bar> byStatus = new ArrayList<Bar>();
    private final List<Bar> byMonth = new ArrayList<Bar>();

    public DashboardStats(List<Order> orders, LocalDate today, int months) {
        long revenue = 0;
        int counted = 0;
        int open = 0;
        int[] perStatus = new int[Status.values().length];
        long[] perMonth = new long[months];
        YearMonth current = YearMonth.from(today);
        for (Order o : orders) {
            perStatus[o.getStatus().ordinal()]++;
            if (o.getStatus() == Status.NEW || o.getStatus() == Status.PAID) {
                open++;
            }
            if (o.getStatus() != Status.CANCELLED) {
                revenue += o.getTotalCents();
                counted++;
                int back = (int) YearMonth.from(o.getDate()).until(current, java.time.temporal.ChronoUnit.MONTHS);
                if (back >= 0 && back < months) {
                    perMonth[months - 1 - back] += o.getTotalCents();
                }
            }
        }
        this.orderCount = orders.size();
        this.revenueCents = revenue;
        this.openOrders = open;
        this.averageCents = counted == 0 ? 0 : revenue / counted;
        int maxStatus = 1;
        for (int n : perStatus) {
            maxStatus = Math.max(maxStatus, n);
        }
        for (Status s : Status.values()) {
            byStatus.add(new Bar(s.label(), Integer.toString(perStatus[s.ordinal()]),
                    (double) perStatus[s.ordinal()] / maxStatus, s.ordinal()));
        }
        long maxMonth = 1;
        for (long v : perMonth) {
            maxMonth = Math.max(maxMonth, v);
        }
        for (int i = 0; i < months; i++) {
            YearMonth m = current.minusMonths(months - 1 - i);
            byMonth.add(new Bar(m.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    Money.format(perMonth[i]).replaceAll("\\.\\d\\d$", ""), (double) perMonth[i] / maxMonth, 0));
        }
    }

    public int getOrderCount() {
        return orderCount;
    }

    public long getRevenueCents() {
        return revenueCents;
    }

    public int getOpenOrders() {
        return openOrders;
    }

    public long getAverageCents() {
        return averageCents;
    }

    public List<Bar> getByStatus() {
        return byStatus;
    }

    public List<Bar> getByMonth() {
        return byMonth;
    }
}
