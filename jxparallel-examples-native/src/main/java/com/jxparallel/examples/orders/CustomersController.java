package com.jxparallel.examples.orders;

import java.util.ArrayList;
import java.util.List;

/**
 * State of the customers screen ({@code customers.jxml}): a list with avatars, and for the selected customer a big
 * card, four figure cards that lift when clicked, and the latest orders.
 */
public final class CustomersController {
    /** A figure card: a title and a value. {@code lifted} is the card the user clicked last. */
    public static final class Figure {
        private final String key;
        private final String title;
        private final String value;
        private final boolean lifted;

        Figure(String key, String title, String value, boolean lifted) {
            this.key = key;
            this.title = title;
            this.value = value;
            this.lifted = lifted;
        }

        public String getKey() {
            return key;
        }

        public String getTitle() {
            return title;
        }

        public String getValue() {
            return value;
        }

        public boolean getLifted() {
            return lifted;
        }

        /** The card rises 4 px: a {@code transition} on {@code translateY} animates the change. */
        public double getRise() {
            return lifted ? -4 : 0;
        }

        public double getShadow() {
            return lifted ? 10 : 2;
        }
    }

    private final Session session;
    private int current;
    private int top;
    private String liftedKey = "";

    public CustomersController(Session session) {
        this.session = session;
    }

    public Palette getPalette() {
        return session.palette();
    }

    public List<CustomerSummary> getCustomers() {
        return session.store().customers();
    }

    public int getCurrent() {
        return getCustomers().isEmpty() ? -1 : Math.min(current, getCustomers().size() - 1);
    }

    public void setCurrent(int v) {
        current = Math.max(0, v);
        liftedKey = "";
        session.host().requestRender();
    }

    public int getTop() {
        return top;
    }

    public void setTop(int v) {
        top = Math.max(0, v);
        session.host().requestRender();
    }

    public boolean getHasSelected() {
        return getCurrent() >= 0;
    }

    public boolean getNoCustomers() {
        return getCustomers().isEmpty();
    }

    /** The selected customer; read it under {@code getHasSelected()}. */
    public CustomerSummary getSelected() {
        int i = getCurrent();
        return i < 0 ? null : getCustomers().get(i);
    }

    public List<Order> getRecentOrders() {
        CustomerSummary c = getSelected();
        return c == null ? new ArrayList<Order>() : c.getRecent();
    }

    public List<Figure> getFigures() {
        CustomerSummary c = getSelected();
        List<Figure> out = new ArrayList<Figure>();
        if (c != null) {
            out.add(new Figure("orders", "Orders", Integer.toString(c.getOrderCount()), "orders".equals(liftedKey)));
            out.add(new Figure("spent", "Spent", c.getSpentText(), "spent".equals(liftedKey)));
            out.add(new Figure("average", "Average order", c.getAverageText(), "average".equals(liftedKey)));
            out.add(new Figure("last", "Last order", c.getLastOrderText(), "last".equals(liftedKey)));
        }
        return out;
    }

    /** A click on a figure card: it rises, and falls back when it is clicked again. */
    public void lift(Figure figure) {
        liftedKey = figure.getKey().equals(liftedKey) ? "" : figure.getKey();
        session.host().requestRender();
    }
}
