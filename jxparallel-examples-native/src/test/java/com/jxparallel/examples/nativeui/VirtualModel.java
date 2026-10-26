package com.jxparallel.examples.nativeui;

import java.util.ArrayList;
import java.util.List;

/** What the virtual list tests bind: mutable rows, the first row and the number of rows of the window, and a handler that records. */
public class VirtualModel {
    /** One row of the model. */
    public static class Row {
        private final String id;
        private String name;
        private String total;
        private boolean picked;

        public Row(int k) {
            this.id = "id" + k;
            this.name = "Name " + k;
            this.total = String.valueOf(k * 37 % 1000);
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

        public boolean getPicked() {
            return picked;
        }

        public void edit(String name, String total, boolean picked) {
            this.name = name;
            this.total = total;
            this.picked = picked;
        }
    }

    public final List<String> calls = new ArrayList<String>();
    private List<Row> rows = new ArrayList<Row>();
    private int first;
    private int window = 12;

    public VirtualModel(int count) {
        for (int k = 0; k < count; k++) {
            rows.add(new Row(k));
        }
    }

    public List<Row> getRows() {
        return rows;
    }

    public void setRows(List<Row> rows) {
        this.rows = rows;
    }

    public int getFirst() {
        return first;
    }

    public void setFirst(int first) {
        this.first = first;
    }

    public int getWindow() {
        return window;
    }

    public void setWindow(int window) {
        this.window = window;
    }

    public void pick(Row row) {
        calls.add("pick " + row.getName());
    }
}
