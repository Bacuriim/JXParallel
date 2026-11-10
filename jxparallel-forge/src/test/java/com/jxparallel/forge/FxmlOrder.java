package com.jxparallel.forge;

/** A row of the orders table of the FXML tests. */
public class FxmlOrder {
    private final int id;
    private final String customer;
    private final double total;

    public FxmlOrder(int id, String customer, double total) {
        this.id = id;
        this.customer = customer;
        this.total = total;
    }

    public int getId() {
        return id;
    }

    public String getCustomer() {
        return customer;
    }

    public double getTotal() {
        return total;
    }
}
