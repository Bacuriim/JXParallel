package com.jxparallel.examples.orders;

import java.util.ArrayList;
import java.util.List;

/** The life of an order. The label is what the screens show and what the CSV file holds. */
public enum Status {
    NEW("New"),
    PAID("Paid"),
    SHIPPED("Shipped"),
    CANCELLED("Cancelled");

    private final String label;

    Status(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** The status with that label (any case), or {@code null}. */
    public static Status fromLabel(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        for (Status s : values()) {
            if (s.label.equalsIgnoreCase(t)) {
                return s;
            }
        }
        return null;
    }

    public static List<String> labels() {
        List<String> out = new ArrayList<String>();
        for (Status s : values()) {
            out.add(s.label);
        }
        return out;
    }
}
