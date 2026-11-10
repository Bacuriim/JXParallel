package com.jxparallel.examples.orders;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * How the orders table is sorted. The native {@code TableView} has no clickable column headers, so the choice is a
 * {@code ComboBox} of these labels.
 */
public enum SortKey {
    ID_ASC("Id, lowest first", Comparator.comparingInt(Order::getId)),
    ID_DESC("Id, highest first", Comparator.comparingInt(Order::getId).reversed()),
    CUSTOMER("Customer, A to Z", Comparator.comparing(Order::getCustomer, String.CASE_INSENSITIVE_ORDER)
            .thenComparingInt(Order::getId)),
    DATE_NEW("Date, newest first", Comparator.comparing(Order::getDate).reversed().thenComparingInt(Order::getId)),
    DATE_OLD("Date, oldest first", Comparator.comparing(Order::getDate).thenComparingInt(Order::getId)),
    TOTAL_HIGH("Total, highest first", Comparator.comparingLong(Order::getTotalCents).reversed()
            .thenComparingInt(Order::getId)),
    TOTAL_LOW("Total, lowest first", Comparator.comparingLong(Order::getTotalCents).thenComparingInt(Order::getId));

    private final String label;
    private final Comparator<Order> comparator;

    SortKey(String label, Comparator<Order> comparator) {
        this.label = label;
        this.comparator = comparator;
    }

    public String label() {
        return label;
    }

    public Comparator<Order> comparator() {
        return comparator;
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
