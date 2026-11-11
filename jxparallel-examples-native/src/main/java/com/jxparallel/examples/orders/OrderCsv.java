package com.jxparallel.examples.orders;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;

/**
 * Orders as CSV (RFC 4180: commas, double quotes, line breaks inside quotes), one row per line item:
 * {@code id,customer,email,date,status,description,quantity,unit_price}. Rows of the same id make one order, so
 * {@code read(write(orders))} gives the same orders back. Both run on a worker thread.
 */
public final class OrderCsv {
    public static final List<String> HEADER = Arrays.asList(
            "id", "customer", "email", "date", "status", "description", "quantity", "unit_price");
    private static final int MAX_ERRORS = 20;
    private static final int PROGRESS_EVERY = 1000;

    private OrderCsv() {
    }

    /** What {@link #read} found: the orders, and a message for each row it could not use (at most 20 are kept). */
    public static final class Result {
        private final List<Order> orders;
        private final List<String> errors;
        private final int skippedRows;

        Result(List<Order> orders, List<String> errors, int skippedRows) {
            this.orders = orders;
            this.errors = errors;
            this.skippedRows = skippedRows;
        }

        public List<Order> getOrders() {
            return orders;
        }

        public List<String> getErrors() {
            return errors;
        }

        /** How many rows were left out (can be more than the messages kept). */
        public int getSkippedRows() {
            return skippedRows;
        }
    }

    public static void write(Writer out, List<Order> orders, DoubleConsumer progress, BooleanSupplier cancelled) throws IOException {
        writeRecord(out, HEADER);
        int done = 0;
        for (Order o : orders) {
            for (LineItem l : o.getLines()) {
                writeRecord(out, Arrays.asList(Integer.toString(o.getId()), o.getCustomer(), o.getEmail(), o.getDateText(),
                        o.getStatusText(), l.getDescription(), Integer.toString(l.getQuantity()), Money.plain(l.getUnitCents())));
            }
            if (++done % PROGRESS_EVERY == 0 || done == orders.size()) {
                if (cancelled != null && cancelled.getAsBoolean()) {
                    throw new CancellationException("export cancelled");
                }
                if (progress != null) {
                    progress.accept((double) done / Math.max(1, orders.size()));
                }
            }
        }
        out.flush();
    }

    private static void writeRecord(Writer out, List<String> fields) throws IOException {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                out.write(',');
            }
            String f = fields.get(i);
            if (f.indexOf(',') >= 0 || f.indexOf('"') >= 0 || f.indexOf('\n') >= 0 || f.indexOf('\r') >= 0) {
                out.write('"');
                out.write(f.replace("\"", "\"\""));
                out.write('"');
            } else {
                out.write(f);
            }
        }
        out.write("\r\n");
    }

    public static Result read(Reader in, long sizeHint, DoubleConsumer progress, BooleanSupplier cancelled) throws IOException {
        List<List<String>> records = parse(in, sizeHint, progress, cancelled);
        List<String> errors = new ArrayList<String>();
        int skipped = 0;
        Map<Integer, List<LineItem>> lines = new LinkedHashMap<Integer, List<LineItem>>();
        Map<Integer, String[]> heads = new LinkedHashMap<Integer, String[]>();
        for (int r = 0; r < records.size(); r++) {
            List<String> f = records.get(r);
            int number = r + 1;
            if (r == 0 && !f.isEmpty() && "id".equalsIgnoreCase(f.get(0).trim())) {
                continue; // the header
            }
            if (f.size() == 1 && f.get(0).trim().isEmpty()) {
                continue; // a blank line
            }
            try {
                if (f.size() != HEADER.size()) {
                    throw new IllegalArgumentException("expected " + HEADER.size() + " columns, found " + f.size());
                }
                int id = Integer.parseInt(f.get(0).trim());
                String customer = required(f.get(1), "customer");
                String email = f.get(2).trim();
                LocalDate date = LocalDate.parse(f.get(3).trim());
                Status status = Status.fromLabel(f.get(4));
                if (status == null) {
                    throw new IllegalArgumentException("unknown status '" + f.get(4) + "'");
                }
                String description = required(f.get(5), "description");
                int quantity = Integer.parseInt(f.get(6).trim());
                if (quantity < 1) {
                    throw new IllegalArgumentException("quantity must be at least 1");
                }
                long unit = Money.parse(f.get(7));
                if (unit < 0) {
                    throw new IllegalArgumentException("unit_price cannot be negative");
                }
                String[] head = {customer, email, date.toString(), status.label()};
                String[] known = heads.get(id);
                if (known == null) {
                    heads.put(id, head);
                    lines.put(id, new ArrayList<LineItem>());
                } else if (!Arrays.equals(known, head)) {
                    throw new IllegalArgumentException("order " + id + " has different customer, e-mail, date or status on another row");
                }
                lines.get(id).add(new LineItem(description, quantity, unit));
            } catch (IllegalArgumentException | DateTimeParseException e) { // NumberFormatException is one
                skipped++;
                if (errors.size() < MAX_ERRORS) {
                    errors.add("row " + number + ": " + e.getMessage());
                }
            }
        }
        List<Order> orders = new ArrayList<Order>(heads.size());
        for (Map.Entry<Integer, String[]> e : heads.entrySet()) {
            String[] h = e.getValue();
            orders.add(new Order(e.getKey(), h[0], h[1], LocalDate.parse(h[2]), Status.fromLabel(h[3]), lines.get(e.getKey())));
        }
        return new Result(orders, errors, skipped);
    }

    private static String required(String text, String column) {
        String t = text.trim();
        if (t.isEmpty()) {
            throw new IllegalArgumentException(column + " is empty");
        }
        return t;
    }

    /** RFC 4180 records; a field can hold commas, quotes ({@code ""}) and line breaks when it is in quotes. */
    static List<List<String>> parse(Reader in, long sizeHint, DoubleConsumer progress, BooleanSupplier cancelled) throws IOException {
        List<List<String>> records = new ArrayList<List<String>>();
        List<String> fields = new ArrayList<String>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean wasQuoted = false;
        long chars = 0;
        int c;
        int pending = in.read();
        while (pending >= 0) {
            c = pending;
            pending = in.read();
            chars++;
            if (quoted) {
                if (c == '"') {
                    if (pending == '"') {
                        field.append('"');
                        pending = in.read();
                        chars++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append((char) c);
                }
                continue;
            }
            if (c == '"' && field.length() == 0 && !wasQuoted) {
                quoted = true;
                wasQuoted = true;
            } else if (c == ',') {
                fields.add(field.toString());
                field.setLength(0);
                wasQuoted = false;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && pending == '\n') {
                    pending = in.read();
                    chars++;
                }
                fields.add(field.toString());
                field.setLength(0);
                wasQuoted = false;
                records.add(fields);
                fields = new ArrayList<String>();
                if (records.size() % PROGRESS_EVERY == 0) {
                    if (cancelled != null && cancelled.getAsBoolean()) {
                        throw new CancellationException("import cancelled");
                    }
                    if (progress != null && sizeHint > 0) {
                        progress.accept(Math.min(0.99, (double) chars / sizeHint));
                    }
                }
            } else {
                field.append((char) c);
            }
        }
        if (quoted) {
            throw new IOException("the file ends inside a quoted field");
        }
        if (field.length() > 0 || !fields.isEmpty()) {
            fields.add(field.toString());
            records.add(fields);
        }
        if (progress != null) {
            progress.accept(1.0);
        }
        return records;
    }
}
