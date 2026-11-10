package com.jxparallel.examples.orders;

import java.util.ArrayList;
import java.util.List;

/** What the orders screen shows of the whole list: the search text, one status (or all) and a sort. */
public final class OrderQuery {
    /** The first entry of the status filter: no filter. */
    public static final String ALL_STATUSES = "All statuses";

    private OrderQuery() {
    }

    /** The status filter's choices: {@link #ALL_STATUSES} and then each status. */
    public static List<String> statusChoices() {
        List<String> out = new ArrayList<String>();
        out.add(ALL_STATUSES);
        out.addAll(Status.labels());
        return out;
    }

    /**
     * The orders that contain every word of {@code text} (case does not matter, in the id, customer, e-mail, date or
     * status) and have the status {@code statusLabel} ({@link #ALL_STATUSES}, {@code null} or an unknown label: any),
     * in the order of {@code sort}. The input list is not changed.
     */
    public static List<Order> apply(List<Order> all, String text, String statusLabel, SortKey sort) {
        String[] words = text == null ? new String[0] : text.trim().toLowerCase().split("\\s+");
        Status status = Status.fromLabel(statusLabel);
        List<Order> out = new ArrayList<Order>();
        for (Order o : all) {
            if (status != null && o.getStatus() != status) {
                continue;
            }
            boolean ok = true;
            for (String w : words) {
                if (!o.matches(w)) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                out.add(o);
            }
        }
        out.sort((sort == null ? SortKey.ID_ASC : sort).comparator());
        return out;
    }
}
