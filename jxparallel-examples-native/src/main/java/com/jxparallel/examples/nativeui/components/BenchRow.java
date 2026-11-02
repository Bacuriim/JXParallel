package com.jxparallel.examples.nativeui.components;

/** A row of the 10,000 row model of the TableView sample. */
public final class BenchRow {
    private final String id;
    private final String name;
    private final String total;

    BenchRow(int k) {
        this.id = String.valueOf(k);
        this.name = "Name " + k;
        this.total = String.valueOf((k * 37) % 1000);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTotal() {
        return total;
    }
}
