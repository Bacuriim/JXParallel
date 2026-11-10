package com.jxparallel.examples.orders;

/** One line of an order. Immutable. */
public final class LineItem {
    private final String description;
    private final int quantity;
    private final long unitCents;

    public LineItem(String description, int quantity, long unitCents) {
        this.description = description;
        this.quantity = quantity;
        this.unitCents = unitCents;
    }

    public String getDescription() {
        return description;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getUnitCents() {
        return unitCents;
    }

    public long getTotalCents() {
        return quantity * unitCents;
    }

    public String getUnitText() {
        return Money.format(unitCents);
    }

    public String getTotalText() {
        return Money.format(getTotalCents());
    }

    /** {@code 3 x Keyboard}: how the details panel lists it. */
    public String getSummary() {
        return quantity + " x " + description + "  " + getTotalText();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof LineItem)) {
            return false;
        }
        LineItem other = (LineItem) o;
        return quantity == other.quantity && unitCents == other.unitCents && description.equals(other.description);
    }

    @Override
    public int hashCode() {
        return description.hashCode() * 31 + quantity * 17 + (int) unitCents;
    }
}
