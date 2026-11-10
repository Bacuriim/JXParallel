package com.jxparallel.examples.orders;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** A customer as the orders show it: who, how many orders, how much they spent (cancelled orders do not count). */
public final class CustomerSummary {
    /** How many avatar pictures are bundled ({@code orders/avatars/avatar1.png} ...). */
    public static final int AVATARS = 8;

    private final String name;
    private final String email;
    private final int orderCount;
    private final long spentCents;
    private final LocalDate lastOrder;
    private final List<Order> recent;

    public CustomerSummary(String name, List<Order> orders) {
        this.name = name;
        long spent = 0;
        LocalDate last = null;
        List<Order> sorted = new ArrayList<Order>(orders);
        sorted.sort(Comparator.comparing(Order::getDate).reversed().thenComparing(Comparator.comparingInt(Order::getId).reversed()));
        for (Order o : orders) {
            if (o.getStatus() != Status.CANCELLED) {
                spent += o.getTotalCents();
            }
            if (last == null || o.getDate().isAfter(last)) {
                last = o.getDate();
            }
        }
        this.email = orders.isEmpty() ? "" : sorted.get(0).getEmail();
        this.orderCount = orders.size();
        this.spentCents = spent;
        this.lastOrder = last;
        this.recent = Collections.unmodifiableList(new ArrayList<Order>(sorted.subList(0, Math.min(5, sorted.size()))));
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public int getOrderCount() {
        return orderCount;
    }

    public long getSpentCents() {
        return spentCents;
    }

    public String getSpentText() {
        return Money.format(spentCents);
    }

    public String getAverageText() {
        return Money.format(orderCount == 0 ? 0 : spentCents / orderCount);
    }

    public String getLastOrderText() {
        return lastOrder == null ? "-" : lastOrder.toString();
    }

    public List<Order> getRecent() {
        return recent;
    }

    public String getOrdersText() {
        return orderCount + (orderCount == 1 ? " order" : " orders");
    }

    /** The bundled picture for this customer (a class path resource of the jar): the same name, the same picture. */
    public String getAvatar() {
        return "classpath:orders/avatars/avatar" +(1 + Math.floorMod(name.hashCode(), AVATARS)) + ".png";
    }
}
