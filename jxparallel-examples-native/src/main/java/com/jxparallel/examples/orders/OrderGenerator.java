package com.jxparallel.examples.orders;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;

/** Makes the demo data: the same seed always gives the same orders. Meant to run on a worker thread. */
public final class OrderGenerator {
    static final String[] CUSTOMERS = {
        "Ada Lovelace", "Grace Hopper", "Alan Turing", "Katherine Johnson", "Linus Torvalds", "Margaret Hamilton",
        "Dennis Ritchie", "Barbara Liskov", "Donald Knuth", "Radia Perlman", "Edsger Dijkstra", "Frances Allen",
    };
    private static final String[] PRODUCTS = {
        "Mechanical keyboard", "Wireless mouse", "USB-C dock", "27 inch monitor", "Laptop stand", "Webcam",
        "Headset", "SSD 1 TB", "Desk lamp", "Cable pack", "Notebook", "Standing desk mat",
    };
    private static final long[] PRICES = {
        8990, 3450, 12900, 24900, 4500, 6990, 8900, 9990, 3200, 1500, 890, 5900,
    };
    private static final int CHUNK = 500;

    private OrderGenerator() {
    }

    /** The e-mail address of a generated customer: {@code ada.lovelace@example.com}. */
    public static String emailOf(String name) {
        return name.toLowerCase().replace(' ', '.') + "@example.com";
    }

    /**
     * {@code count} orders with ids 1 to {@code count}, dates in the 12 months before {@code today}, 1 to 4 lines each.
     *
     * @param progress told the fraction done (0 to 1) after each chunk; may be {@code null}
     * @param cancelled polled after each chunk; when true the call ends with a {@link CancellationException}
     */
    public static List<Order> generate(int count, long seed, LocalDate today, DoubleConsumer progress, BooleanSupplier cancelled) {
        Random random = new Random(seed);
        List<Order> out = new ArrayList<Order>(count);
        Status[] statuses = Status.values();
        for (int i = 1; i <= count; i++) {
            String customer = CUSTOMERS[random.nextInt(CUSTOMERS.length)];
            int lineCount = 1 + random.nextInt(4);
            List<LineItem> lines = new ArrayList<LineItem>(lineCount);
            for (int k = 0; k < lineCount; k++) {
                int p = random.nextInt(PRODUCTS.length);
                lines.add(new LineItem(PRODUCTS[p], 1 + random.nextInt(5), PRICES[p]));
            }
            // older orders are mostly shipped, recent ones mostly new or paid
            LocalDate date = today.minusDays(random.nextInt(365));
            long age = java.time.temporal.ChronoUnit.DAYS.between(date, today);
            Status status;
            int roll = random.nextInt(100);
            if (roll < 6) {
                status = Status.CANCELLED;
            } else if (age > 30) {
                status = Status.SHIPPED;
            } else {
                status = statuses[roll < 50 ? 0 : roll < 80 ? 1 : 2];
            }
            out.add(new Order(i, customer, emailOf(customer), date, status, lines));
            if (i % CHUNK == 0 || i == count) {
                if (cancelled != null && cancelled.getAsBoolean()) {
                    throw new CancellationException("generation cancelled");
                }
                if (progress != null) {
                    progress.accept((double) i / count);
                }
            }
        }
        return out;
    }
}
