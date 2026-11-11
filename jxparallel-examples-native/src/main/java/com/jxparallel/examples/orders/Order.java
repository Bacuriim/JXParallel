package com.jxparallel.examples.orders;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An order. Immutable, so a list of 10,000 of them is shared between the screens and the worker threads without
 * locks; an edit makes a new one ({@link OrderStore#save}). The getters named like the table's columns are what the
 * JXML bindings call (<code>${o.customer}</code>).
 */
public final class Order {
    private final int id;
    private final String customer;
    private final String email;
    private final LocalDate date;
    private final Status status;
    private final List<LineItem> lines;
    private final long totalCents;
    private final String searchKey;

    public Order(int id, String customer, String email, LocalDate date, Status status, List<LineItem> lines) {
        this.id = id;
        this.customer = customer;
        this.email = email;
        this.date = date;
        this.status = status;
        this.lines = Collections.unmodifiableList(new ArrayList<LineItem>(lines));
        long sum = 0;
        for (LineItem l : this.lines) {
            sum += l.getTotalCents();
        }
        this.totalCents = sum;
        this.searchKey = (id + " " + customer + " " + email + " " + date + " " + status.label()).toLowerCase();
    }

    public int getId() {
        return id;
    }

    public String getCustomer() {
        return customer;
    }

    public String getEmail() {
        return email;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getDateText() {
        return date.toString();
    }

    public Status getStatus() {
        return status;
    }

    public String getStatusText() {
        return status.label();
    }

    public List<LineItem> getLines() {
        return lines;
    }

    public long getTotalCents() {
        return totalCents;
    }

    public String getTotalText() {
        return Money.format(totalCents);
    }

    /** Lower-case text the search box looks into: id, customer, e-mail, date and status. */
    public boolean matches(String lowerCaseQuery) {
        return lowerCaseQuery.isEmpty() || searchKey.contains(lowerCaseQuery);
    }

    /** Same content, whatever the instance: what the CSV round trip must keep. */
    public boolean sameAs(Order o) {
        return id == o.id && customer.equals(o.customer) && email.equals(o.email) && date.equals(o.date)
                && status == o.status && lines.equals(o.lines);
    }
}
