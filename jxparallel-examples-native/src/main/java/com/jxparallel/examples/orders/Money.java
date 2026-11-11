package com.jxparallel.examples.orders;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Amounts are whole cents in a {@code long}; this formats and parses them. */
public final class Money {
    private Money() {
    }

    /** {@code 123456} is {@code $1,234.56}. */
    public static String format(long cents) {
        long abs = Math.abs(cents);
        String whole = Long.toString(abs / 100);
        StringBuilder grouped = new StringBuilder();
        for (int i = 0; i < whole.length(); i++) {
            if (i > 0 && (whole.length() - i) % 3 == 0) {
                grouped.append(',');
            }
            grouped.append(whole.charAt(i));
        }
        long fraction = abs % 100;
        return (cents < 0 ? "-$" : "$") + grouped + "." + (fraction < 10 ? "0" : "") + fraction;
    }

    /** {@code 123456} is {@code 1234.56}, the form the CSV file holds. */
    public static String plain(long cents) {
        long abs = Math.abs(cents);
        long fraction = abs % 100;
        return (cents < 0 ? "-" : "") + abs / 100 + "." + (fraction < 10 ? "0" : "") + fraction;
    }

    /**
     * Cents of a text such as {@code 12.5}, {@code 1234.56} or {@code $1,234.56}.
     *
     * @throws NumberFormatException when it is no amount, or has more than two decimals
     */
    public static long parse(String text) {
        if (text == null) {
            throw new NumberFormatException("empty amount");
        }
        String t = text.trim().replace("$", "").replace(",", "");
        if (t.isEmpty()) {
            throw new NumberFormatException("empty amount");
        }
        BigDecimal value = new BigDecimal(t);
        if (value.scale() > 2) {
            throw new NumberFormatException("more than two decimals: " + text);
        }
        return value.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
    }
}
