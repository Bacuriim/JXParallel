package com.jxparallel.examples.orders;

import java.util.ArrayList;
import java.util.List;

/**
 * State of the dashboard ({@code dashboard.jxml}): four figures and two bar visuals made of plain boxes (there is no
 * chart component): orders per status as horizontal bars, revenue of the last six months as columns.
 */
public final class DashboardController {
    static final double BAR_LENGTH = 320;
    static final double COLUMN_HEIGHT = 150;
    static final int MONTHS = 6;

    /** One figure card. */
    public static final class Kpi {
        private final String title;
        private final String value;
        private final String note;

        Kpi(String title, String value, String note) {
            this.title = title;
            this.value = value;
            this.note = note;
        }

        public String getTitle() {
            return title;
        }

        public String getValue() {
            return value;
        }

        public String getNote() {
            return note;
        }
    }

    /** One bar, in pixels, with its colour. */
    public static final class BarView {
        private final String label;
        private final String valueText;
        private final double size;
        private final int color;

        BarView(String label, String valueText, double size, int color) {
            this.label = label;
            this.valueText = valueText;
            this.size = size;
            this.color = color;
        }

        public String getLabel() {
            return label;
        }

        public String getValueText() {
            return valueText;
        }

        /** Length (horizontal bars) or height (columns) in pixels, at least 2 so an empty bar is still seen. */
        public double getSize() {
            return size;
        }

        public int getColor() {
            return color;
        }
    }

    private final Session session;
    private int statsVersion = -1;
    private DashboardStats stats;

    public DashboardController(Session session) {
        this.session = session;
    }

    public Palette getPalette() {
        return session.palette();
    }

    private DashboardStats stats() {
        if (stats == null || statsVersion != session.store().getVersion()) {
            stats = new DashboardStats(session.store().getOrders(), session.today(), MONTHS);
            statsVersion = session.store().getVersion();
        }
        return stats;
    }

    public List<Kpi> getKpis() {
        DashboardStats s = stats();
        List<Kpi> out = new ArrayList<Kpi>();
        out.add(new Kpi("Orders", OrdersController.count(s.getOrderCount()), "all statuses"));
        out.add(new Kpi("Revenue", Money.format(s.getRevenueCents()), "without cancelled orders"));
        out.add(new Kpi("Average order", Money.format(s.getAverageCents()), "without cancelled orders"));
        out.add(new Kpi("Open orders", OrdersController.count(s.getOpenOrders()), "new and paid"));
        return out;
    }

    public List<BarView> getStatusBars() {
        List<BarView> out = new ArrayList<BarView>();
        for (DashboardStats.Bar b : stats().getByStatus()) {
            out.add(new BarView(b.getLabel(), b.getValueText(), Math.max(2, b.getFraction() * BAR_LENGTH),
                    session.palette().series(b.getIndex())));
        }
        return out;
    }

    public List<BarView> getMonthBars() {
        List<BarView> out = new ArrayList<BarView>();
        for (DashboardStats.Bar b : stats().getByMonth()) {
            out.add(new BarView(b.getLabel(), b.getValueText(), Math.max(2, b.getFraction() * COLUMN_HEIGHT),
                    session.palette().series(0)));
        }
        return out;
    }

    public boolean getNoOrders() {
        return session.store().size() == 0;
    }
}
