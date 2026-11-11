package com.jxparallel.examples.orders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The orders of the open file. Used from the UI thread only: workers build a new list and hand it over with
 * {@link #replaceAll}. The list itself is replaced, never changed in place, so the screens can keep a reference to it
 * for as long as they like. {@link #getVersion()} changes with every change, for caches.
 */
public final class OrderStore {
    private List<Order> orders = Collections.emptyList();
    private int version;
    private int customersVersion = -1;
    private List<CustomerSummary> customers = Collections.emptyList();

    public List<Order> getOrders() {
        return orders;
    }

    public int getVersion() {
        return version;
    }

    public int size() {
        return orders.size();
    }

    public void replaceAll(List<Order> next) {
        orders = Collections.unmodifiableList(new ArrayList<Order>(next));
        version++;
    }

    public int nextId() {
        int max = 0;
        for (Order o : orders) {
            max = Math.max(max, o.getId());
        }
        return max + 1;
    }

    public Order find(int id) {
        for (Order o : orders) {
            if (o.getId() == id) {
                return o;
            }
        }
        return null;
    }

    /** Replaces the order with the same id, or appends it. */
    public void save(Order order) {
        List<Order> next = new ArrayList<Order>(orders);
        boolean replaced = false;
        for (int i = 0; i < next.size(); i++) {
            if (next.get(i).getId() == order.getId()) {
                next.set(i, order);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            next.add(order);
        }
        replaceAll(next);
    }

    public void delete(Set<Integer> ids) {
        List<Order> next = new ArrayList<Order>(orders.size());
        for (Order o : orders) {
            if (!ids.contains(o.getId())) {
                next.add(o);
            }
        }
        replaceAll(next);
    }

    /** The customers that have orders, by name; recomputed only when the orders changed. */
    public List<CustomerSummary> customers() {
        if (customersVersion != version) {
            Map<String, List<Order>> byName = new LinkedHashMap<String, List<Order>>();
            for (Order o : orders) {
                List<Order> list = byName.get(o.getCustomer());
                if (list == null) {
                    list = new ArrayList<Order>();
                    byName.put(o.getCustomer(), list);
                }
                list.add(o);
            }
            List<CustomerSummary> out = new ArrayList<CustomerSummary>();
            for (Map.Entry<String, List<Order>> e : byName.entrySet()) {
                out.add(new CustomerSummary(e.getKey(), e.getValue()));
            }
            out.sort(Comparator.comparing(CustomerSummary::getName, String.CASE_INSENSITIVE_ORDER));
            customers = Collections.unmodifiableList(out);
            customersVersion = version;
        }
        return customers;
    }
}
