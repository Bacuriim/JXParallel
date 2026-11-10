package com.jxparallel.examples.orders;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * How the orders table is sorted: each key is one column in one direction, so a press on a column header picks one
 * ({@link #of(int, boolean)}). The {@code label} names the key in the status text and the tests.
 */
public enum SortKey {
    ID_ASC("Id, lowest first", 0, true, Comparator.comparingInt(Order::getId)),
    ID_DESC("Id, highest first", 0, false, Comparator.comparingInt(Order::getId).reversed()),
    CUSTOMER("Customer, A to Z", 1, true, Comparator.comparing(Order::getCustomer, String.CASE_INSENSITIVE_ORDER)
            .thenComparingInt(Order::getId)),
    CUSTOMER_DESC("Customer, Z to A", 1, false, Comparator.comparing(Order::getCustomer, String.CASE_INSENSITIVE_ORDER)
            .reversed().thenComparingInt(Order::getId)),
    DATE_OLD("Date, oldest first", 2, true, Comparator.comparing(Order::getDate).thenComparingInt(Order::getId)),
    DATE_NEW("Date, newest first", 2, false, Comparator.comparing(Order::getDate).reversed().thenComparingInt(Order::getId)),
    STATUS_ASC("Status, first to last", 3, true, Comparator.comparing(Order::getStatus).thenComparingInt(Order::getId)),
    STATUS_DESC("Status, last to first", 3, false, Comparator.comparing(Order::getStatus).reversed()
            .thenComparingInt(Order::getId)),
    TOTAL_LOW("Total, lowest first", 4, true, Comparator.comparingLong(Order::getTotalCents).thenComparingInt(Order::getId)),
    TOTAL_HIGH("Total, highest first", 4, false, Comparator.comparingLong(Order::getTotalCents).reversed()
            .thenComparingInt(Order::getId));

    private final String label;
    private final int column;
    private final boolean ascending;
    private final Comparator<Order> comparator;

    SortKey(String label, int column, boolean ascending, Comparator<Order> comparator) {
        this.label = label;
        this.column = column;
        this.ascending = ascending;
        this.comparator = comparator;
    }

    public String label() {
        return label;
    }

    /** The column of the table (0 Id, 1 Customer, 2 Date, 3 Status, 4 Total) this key sorts by. */
    public int column() {
        return column;
    }

    public boolean ascending() {
        return ascending;
    }

    public Comparator<Order> comparator() {
        return comparator;
    }

    /** The key for a column and a direction; no column (-1, the third press of a header) or an unknown one is the id order. */
    public static SortKey of(int column, boolean ascending) {
        for (SortKey k : values()) {
            if (k.column == column && k.ascending == ascending) {
                return k;
            }
        }
        return ID_ASC;
    }

    public static SortKey fromLabel(String text) {
        for (SortKey k : values()) {
            if (k.label.equals(text)) {
                return k;
            }
        }
        return ID_ASC;
    }

    public static List<String> labels() {
        List<String> out = new ArrayList<String>();
        for (SortKey k : values()) {
            out.add(k.label);
        }
        return out;
    }
}
