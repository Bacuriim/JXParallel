package com.jxparallel.examples.orders;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * An order being edited: everything is text, as it is in the form, and {@link #validate} says what is wrong.
 * {@link #toOrder} turns a valid draft into an {@link Order}.
 */
public final class OrderDraft {
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s.]+");
    public static final int MAX_QUANTITY = 999;

    /** A line of the form. */
    public static final class DraftLine {
        private String description = "";
        private int quantity = 1;
        private String unitPrice = "";

        public DraftLine() {
        }

        public DraftLine(String description, int quantity, String unitPrice) {
            this.description = description;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String v) {
            description = v == null ? "" : v;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int v) {
            quantity = v;
        }

        /** The Spinner takes its value as text. */
        public String getQuantityText() {
            return Integer.toString(quantity);
        }

        public String getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(String v) {
            unitPrice = v == null ? "" : v;
        }

        /** Cents of the price, or -1 when it is not a valid amount. */
        public long unitCents() {
            try {
                long c = Money.parse(unitPrice);
                return c > 0 ? c : -1;
            } catch (NumberFormatException | ArithmeticException e) {
                return -1;
            }
        }

        public String getSubtotalText() {
            long c = unitCents();
            return c < 0 ? "-" : Money.format(c * quantity);
        }

        /** What is wrong with this line, or {@code null}. */
        public String error() {
            if (description.trim().isEmpty()) {
                return "Describe the item";
            }
            if (quantity < 1 || quantity > MAX_QUANTITY) {
                return "Quantity must be 1 to " + MAX_QUANTITY;
            }
            if (unitCents() < 0) {
                return "Price must be an amount above zero, like 12.50";
            }
            return null;
        }

        public boolean isEmptyLine() {
            return description.trim().isEmpty() && unitPrice.trim().isEmpty();
        }

        private boolean touched;
        private boolean submitted;

        /** The user edited this line. */
        public void touch() {
            touched = true;
        }

        /** Save was pressed: show the message even for a line nobody touched. */
        public void markSubmitted() {
            submitted = true;
        }

        /** What the form shows under the line: its message once it was edited (and is not still empty) or Save was pressed. */
        public String getShownError() {
            String e = error();
            return e != null && (submitted || (touched && !isEmptyLine())) ? e : "";
        }
    }

    private int id;
    private String customer = "";
    private String email = "";
    private String dateText = "";
    private String statusLabel = Status.NEW.label();
    private final List<DraftLine> lines = new ArrayList<DraftLine>();

    /** A blank new order dated {@code today}, with one empty line. */
    public static OrderDraft blank(LocalDate today) {
        OrderDraft d = new OrderDraft();
        d.dateText = today.toString();
        d.lines.add(new DraftLine());
        return d;
    }

    public static OrderDraft of(Order o) {
        OrderDraft d = new OrderDraft();
        d.id = o.getId();
        d.customer = o.getCustomer();
        d.email = o.getEmail();
        d.dateText = o.getDateText();
        d.statusLabel = o.getStatusText();
        for (LineItem l : o.getLines()) {
            d.lines.add(new DraftLine(l.getDescription(), l.getQuantity(), Money.plain(l.getUnitCents())));
        }
        return d;
    }

    public int getId() {
        return id;
    }

    public boolean isNew() {
        return id == 0;
    }

    public String getCustomer() {
        return customer;
    }

    public void setCustomer(String v) {
        customer = v == null ? "" : v;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String v) {
        email = v == null ? "" : v;
    }

    public String getDateText() {
        return dateText;
    }

    public void setDateText(String v) {
        dateText = v == null ? "" : v;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public void setStatusLabel(String v) {
        statusLabel = v == null ? "" : v;
    }

    public List<DraftLine> getLines() {
        return lines;
    }

    public DraftLine addLine() {
        DraftLine l = new DraftLine();
        lines.add(l);
        return l;
    }

    public void removeLine(DraftLine l) {
        lines.remove(l);
    }

    /** The sum of the lines that are valid, in cents. */
    public long totalCents() {
        long sum = 0;
        for (DraftLine l : lines) {
            long c = l.unitCents();
            if (c >= 0 && l.getQuantity() >= 1) {
                sum += c * l.getQuantity();
            }
        }
        return sum;
    }

    public String getTotalText() {
        return Money.format(totalCents());
    }

    /** The messages by field: {@code customer}, {@code email}, {@code date}, {@code status}, {@code lines}. */
    public Map<String, String> validate(LocalDate today) {
        Map<String, String> errors = new LinkedHashMap<String, String>();
        String name = customer.trim();
        if (name.isEmpty()) {
            errors.put("customer", "Customer is required");
        } else if (name.length() < 2) {
            errors.put("customer", "Customer needs at least 2 letters");
        }
        if (email.trim().isEmpty()) {
            errors.put("email", "E-mail is required");
        } else if (!EMAIL.matcher(email.trim()).matches()) {
            errors.put("email", "That is not an e-mail address");
        }
        if (dateText.trim().isEmpty()) {
            errors.put("date", "Pick a date");
        } else {
            try {
                LocalDate d = LocalDate.parse(dateText.trim());
                if (d.isAfter(today)) {
                    errors.put("date", "The date cannot be in the future");
                }
            } catch (DateTimeParseException e) {
                errors.put("date", "Use the form 2026-01-31");
            }
        }
        if (Status.fromLabel(statusLabel) == null) {
            errors.put("status", "Choose a status");
        }
        if (lines.isEmpty()) {
            errors.put("lines", "Add at least one item");
        } else {
            for (DraftLine l : lines) {
                if (l.error() != null) {
                    errors.put("lines", "Fix the items marked below");
                    break;
                }
            }
        }
        return errors;
    }

    /** The order this draft describes, with the id {@code orderId}; call it on a draft that has no errors. */
    public Order toOrder(int orderId, LocalDate today) {
        if (!validate(today).isEmpty()) {
            throw new IllegalStateException("the draft is not valid");
        }
        List<LineItem> items = new ArrayList<LineItem>();
        for (DraftLine l : lines) {
            items.add(new LineItem(l.getDescription().trim(), l.getQuantity(), l.unitCents()));
        }
        return new Order(orderId, customer.trim(), email.trim(), LocalDate.parse(dateText.trim()),
                Status.fromLabel(statusLabel), items);
    }
}
