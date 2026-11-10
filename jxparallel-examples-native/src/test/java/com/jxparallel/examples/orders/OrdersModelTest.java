package com.jxparallel.examples.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

import org.junit.jupiter.api.Test;

/** The model behind JX Orders: no window, no JXML. */
class OrdersModelTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    private static Order order(int id, String customer, String date, Status status, long... unitCents) {
        List<LineItem> lines = new ArrayList<LineItem>();
        for (long c : unitCents) {
            lines.add(new LineItem("Item", 1, c));
        }
        return new Order(id, customer, OrderGenerator.emailOf(customer), LocalDate.parse(date), status, lines);
    }

    // ---- money, totals ------------------------------------------------------------------------------------

    @Test
    void moneyFormatsAndParses() {
        assertEquals("$0.05", Money.format(5));
        assertEquals("$1,234.56", Money.format(123456));
        assertEquals("$1,000,000.00", Money.format(100000000));
        assertEquals("-$2.50", Money.format(-250));
        assertEquals("1234.56", Money.plain(123456));
        assertEquals(123456, Money.parse("1234.56"));
        assertEquals(123456, Money.parse("$1,234.56"));
        assertEquals(1250, Money.parse("12.5"));
        assertEquals(700, Money.parse("7"));
        assertThrows(NumberFormatException.class, () -> Money.parse("1.234"));
        assertThrows(NumberFormatException.class, () -> Money.parse("abc"));
        assertThrows(NumberFormatException.class, () -> Money.parse(" "));
    }

    @Test
    void anOrderTotalIsTheSumOfItsLines() {
        Order o = new Order(1, "Aurelia Voss", "aurelia@example.com", TODAY, Status.NEW,
                Arrays.asList(new LineItem("Keyboard", 2, 8990), new LineItem("Mouse", 3, 3450)));
        assertEquals(2 * 8990 + 3 * 3450, o.getTotalCents());
        assertEquals("$283.30", o.getTotalText());
        assertEquals("2 x Keyboard  $179.80", o.getLines().get(0).getSummary());
    }

    // ---- search, filter, sort -----------------------------------------------------------------------------

    private static List<Order> sample() {
        return Arrays.asList(
                order(1, "Aurelia Voss", "2026-01-05", Status.NEW, 1000),
                order(2, "Beto Marlowe", "2026-03-01", Status.PAID, 5000, 2500),
                order(3, "Greta Quenby", "2026-02-10", Status.SHIPPED, 700),
                order(4, "Beto Marlowe", "2025-12-31", Status.CANCELLED, 9900),
                order(5, "Aurelia Voss", "2026-03-01", Status.SHIPPED, 300));
    }

    private static List<Integer> ids(List<Order> orders) {
        List<Integer> out = new ArrayList<Integer>();
        for (Order o : orders) {
            out.add(o.getId());
        }
        return out;
    }

    @Test
    void searchMatchesEveryWordInAnyColumnIgnoringCase() {
        List<Order> all = sample();
        assertEquals(Arrays.asList(2, 4), ids(OrderQuery.apply(all, "MARLOWE", OrderQuery.ALL_STATUSES, SortKey.ID_ASC)));
        assertEquals(Arrays.asList(2), ids(OrderQuery.apply(all, "beto 2026-03", null, SortKey.ID_ASC)));
        assertEquals(Arrays.asList(3, 5), ids(OrderQuery.apply(all, "shipped", null, SortKey.ID_ASC)));
        assertEquals(Arrays.asList(4), ids(OrderQuery.apply(all, "marlowe@example.com cancelled", null, SortKey.ID_ASC)));
        assertEquals(5, OrderQuery.apply(all, "   ", null, SortKey.ID_ASC).size(), "blank text is no filter");
        assertTrue(OrderQuery.apply(all, "nobody", null, SortKey.ID_ASC).isEmpty());
    }

    @Test
    void theStatusFilterKeepsOneStatus() {
        List<Order> all = sample();
        assertEquals(Arrays.asList(3, 5), ids(OrderQuery.apply(all, "", "Shipped", SortKey.ID_ASC)));
        assertEquals(Arrays.asList(1), ids(OrderQuery.apply(all, "", "New", SortKey.ID_ASC)));
        assertEquals(5, OrderQuery.apply(all, "", "Nonsense", SortKey.ID_ASC).size(), "an unknown label is no filter");
        assertEquals(Arrays.asList(2), ids(OrderQuery.apply(all, "marlowe", "Paid", SortKey.ID_ASC)), "search and status together");
        assertEquals(Arrays.asList("All statuses", "New", "Paid", "Shipped", "Cancelled"), OrderQuery.statusChoices());
    }

    @Test
    void everySortKeyOrdersTheList() {
        List<Order> all = sample();
        assertEquals(Arrays.asList(5, 4, 3, 2, 1), ids(OrderQuery.apply(all, "", null, SortKey.ID_DESC)));
        assertEquals(Arrays.asList(1, 5, 2, 4, 3), ids(OrderQuery.apply(all, "", null, SortKey.CUSTOMER)));
        assertEquals(Arrays.asList(2, 5, 3, 1, 4), ids(OrderQuery.apply(all, "", null, SortKey.DATE_NEW)));
        assertEquals(Arrays.asList(4, 1, 3, 2, 5), ids(OrderQuery.apply(all, "", null, SortKey.DATE_OLD)));
        assertEquals(Arrays.asList(4, 2, 1, 3, 5), ids(OrderQuery.apply(all, "", null, SortKey.TOTAL_HIGH)));
        assertEquals(Arrays.asList(5, 3, 1, 2, 4), ids(OrderQuery.apply(all, "", null, SortKey.TOTAL_LOW)));
        assertEquals(SortKey.TOTAL_LOW, SortKey.fromLabel("Total, lowest first"));
        assertEquals(SortKey.ID_ASC, SortKey.fromLabel("garbage"));
    }

    @Test
    void queryDoesNotChangeItsInput() {
        List<Order> all = new ArrayList<Order>(sample());
        OrderQuery.apply(all, "", null, SortKey.TOTAL_HIGH);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5), ids(all));
    }

    // ---- the generator --------------------------------------------------------------------------------------

    @Test
    void theGeneratorIsDeterministicAndShapedLikeOrders() {
        List<Order> a = OrderGenerator.generate(2000, 7, TODAY, null, null);
        List<Order> b = OrderGenerator.generate(2000, 7, TODAY, null, null);
        assertEquals(2000, a.size());
        for (int i = 0; i < a.size(); i++) {
            assertTrue(a.get(i).sameAs(b.get(i)));
            assertEquals(i + 1, a.get(i).getId());
            assertFalse(a.get(i).getLines().isEmpty());
            assertFalse(a.get(i).getDate().isAfter(TODAY));
            assertTrue(a.get(i).getTotalCents() > 0);
        }
        List<Order> other = OrderGenerator.generate(2000, 8, TODAY, null, null);
        int same = 0;
        for (int i = 0; i < a.size(); i++) {
            same += a.get(i).sameAs(other.get(i)) ? 1 : 0;
        }
        assertTrue(same < 20, "another seed gives other orders");
    }

    @Test
    void theGeneratorReportsProgressAndCanBeCancelled() {
        List<Double> seen = new ArrayList<Double>();
        OrderGenerator.generate(2000, 1, TODAY, seen::add, () -> false);
        assertEquals(4, seen.size());
        assertEquals(1.0, seen.get(seen.size() - 1), 1e-9);
        assertThrows(CancellationException.class, () -> OrderGenerator.generate(100000, 1, TODAY, null, () -> true));
    }

    // ---- validation ------------------------------------------------------------------------------------------

    @Test
    void aBlankDraftHasMessagesForTheRequiredFields() {
        OrderDraft d = OrderDraft.blank(TODAY);
        Map<String, String> errors = d.validate(TODAY);
        assertEquals("Customer is required", errors.get("customer"));
        assertEquals("E-mail is required", errors.get("email"));
        assertEquals("Fix the items marked below", errors.get("lines"));
        assertNull(errors.get("date"), "the blank draft is dated today");
        assertNull(errors.get("status"));
    }

    @Test
    void fieldRules() {
        OrderDraft d = OrderDraft.blank(TODAY);
        d.setCustomer("A");
        assertEquals("Customer needs at least 2 letters", d.validate(TODAY).get("customer"));
        d.setCustomer("Al");
        assertNull(d.validate(TODAY).get("customer"));
        for (String bad : new String[] {"beto", "beto@", "beto@x", "@x.com", "a b@x.com", "beto@x.c om"}) {
            d.setEmail(bad);
            assertEquals("That is not an e-mail address", d.validate(TODAY).get("email"), bad);
        }
        d.setEmail("beto@x.org");
        assertNull(d.validate(TODAY).get("email"));
        d.setDateText("2026-13-01");
        assertEquals("Use the form 2026-01-31", d.validate(TODAY).get("date"));
        d.setDateText("2026-11-11");
        assertEquals("The date cannot be in the future", d.validate(TODAY).get("date"));
        d.setDateText("2026-11-10");
        assertNull(d.validate(TODAY).get("date"));
        d.setDateText("");
        assertEquals("Pick a date", d.validate(TODAY).get("date"));
        d.setStatusLabel("Lost");
        assertEquals("Choose a status", d.validate(TODAY).get("status"));
    }

    @Test
    void lineRules() {
        OrderDraft.DraftLine l = new OrderDraft.DraftLine("", 1, "");
        assertEquals("Describe the item", l.error());
        l.setDescription("Cable");
        assertEquals("Price must be an amount above zero, like 12.50", l.error());
        l.setUnitPrice("0");
        assertEquals("Price must be an amount above zero, like 12.50", l.error());
        l.setUnitPrice("1.999");
        assertEquals("Price must be an amount above zero, like 12.50", l.error());
        l.setUnitPrice("4.50");
        assertNull(l.error());
        l.setQuantity(0);
        assertEquals("Quantity must be 1 to 999", l.error());
        l.setQuantity(1000);
        assertEquals("Quantity must be 1 to 999", l.error());
        l.setQuantity(3);
        assertEquals("$13.50", l.getSubtotalText());
        assertEquals("", l.getShownError(), "no message shown while it is valid");
    }

    @Test
    void aLineShowsItsMessageOnlyAfterItWasEditedOrSaveWasPressed() {
        OrderDraft.DraftLine l = new OrderDraft.DraftLine();
        assertEquals("", l.getShownError());
        l.touch();
        assertEquals("", l.getShownError(), "still empty: nothing to complain about yet");
        l.setDescription("Cable");
        assertEquals("Price must be an amount above zero, like 12.50", l.getShownError());
        OrderDraft.DraftLine other = new OrderDraft.DraftLine();
        other.markSubmitted();
        assertEquals("Describe the item", other.getShownError());
    }

    @Test
    void theDraftTotalSumsTheValidLinesAndAnOrderIsMadeFromAValidDraft() {
        OrderDraft d = OrderDraft.blank(TODAY);
        d.setCustomer("Greta Quenby");
        d.setEmail("greta@example.com");
        OrderDraft.DraftLine first = d.getLines().get(0);
        first.setDescription("Monitor");
        first.setQuantity(2);
        first.setUnitPrice("249.00");
        OrderDraft.DraftLine second = d.addLine();
        second.setDescription("Broken");
        second.setUnitPrice("x");
        assertEquals(49800, d.totalCents(), "the invalid line adds nothing");
        assertThrows(IllegalStateException.class, () -> d.toOrder(5, TODAY));
        d.removeLine(second);
        assertTrue(d.validate(TODAY).isEmpty());
        Order o = d.toOrder(5, TODAY);
        assertEquals(5, o.getId());
        assertEquals(49800, o.getTotalCents());
        assertEquals("$498.00", d.getTotalText());
        assertEquals(Status.NEW, o.getStatus());
        assertTrue(OrderDraft.of(o).validate(TODAY).isEmpty(), "an order opens in the editor as a valid draft");
        assertTrue(o.sameAs(OrderDraft.of(o).toOrder(5, TODAY)));
        d.removeLine(first);
        assertEquals("Add at least one item", d.validate(TODAY).get("lines"));
    }

    // ---- CSV ----------------------------------------------------------------------------------------------------

    private static List<Order> roundTrip(List<Order> orders) throws IOException {
        StringWriter out = new StringWriter();
        OrderCsv.write(out, orders, null, null);
        OrderCsv.Result r = OrderCsv.read(new StringReader(out.toString()), out.getBuffer().length(), null, null);
        assertTrue(r.getErrors().isEmpty(), r.getErrors().toString());
        return r.getOrders();
    }

    @Test
    void csvRoundTripKeepsEveryOrder() throws IOException {
        List<Order> orders = OrderGenerator.generate(1500, 99, TODAY, null, null);
        List<Order> back = roundTrip(orders);
        assertEquals(orders.size(), back.size());
        for (int i = 0; i < orders.size(); i++) {
            assertTrue(orders.get(i).sameAs(back.get(i)), "order " + (i + 1));
        }
    }

    @Test
    void csvQuotesCommasQuotesAndLineBreaks() throws IOException {
        Order tricky = new Order(7, "Smith, \"Jo\"\nJr.", "jo@example.com", LocalDate.of(2026, 5, 6), Status.PAID,
                Arrays.asList(new LineItem("Cable, 2 m", 2, 1999), new LineItem("Say \"hi\"", 1, 5)));
        StringWriter out = new StringWriter();
        OrderCsv.write(out, Collections.singletonList(tricky), null, null);
        assertTrue(out.toString().contains("\"Smith, \"\"Jo\"\"\nJr.\""), out.toString());
        List<Order> back = roundTrip(Collections.singletonList(tricky));
        assertEquals(1, back.size());
        assertTrue(tricky.sameAs(back.get(0)));
    }

    @Test
    void csvReadAcceptsLfAndNoHeaderAndSkipsBadRows() throws IOException {
        String text = "1,Aurelia Voss,aurelia@example.com,2026-01-05,New,Cable,2,3.50\n"
                + "1,Aurelia Voss,aurelia@example.com,2026-01-05,New,Plug,1,1.00\n"
                + "\n"
                + "x,Bad Id,a@b.co,2026-01-05,New,Cable,1,1.00\n"
                + "2,Greta,g@b.co,2026-13-40,New,Cable,1,1.00\n"
                + "3,Beto,a@b.co,2026-01-05,Lost,Cable,1,1.00\n"
                + "4,Beto,a@b.co,2026-01-05,Paid,Cable,0,1.00\n"
                + "5,Beto,a@b.co,2026-01-05,Paid,Cable,1,1.005\n"
                + "6,Only,five,columns,here\n"
                + "7,Greta Quenby,greta@example.com,2026-02-02,Shipped,Monitor,1,249\n";
        OrderCsv.Result r = OrderCsv.read(new StringReader(text), text.length(), null, null);
        assertEquals(Arrays.asList(1, 7), ids(r.getOrders()));
        assertEquals(700 + 100, r.getOrders().get(0).getTotalCents());
        assertEquals(6, r.getSkippedRows());
        assertEquals(6, r.getErrors().size());
        assertTrue(r.getErrors().get(0).startsWith("row 4: "), r.getErrors().get(0));
    }

    @Test
    void csvRowsOfTheSameOrderMustAgree() throws IOException {
        String text = "id,customer,email,date,status,description,quantity,unit_price\r\n"
                + "1,Aurelia,aurelia@example.com,2026-01-05,New,Cable,1,1.00\r\n"
                + "1,Aurelia,aurelia@example.com,2026-01-06,New,Plug,1,1.00\r\n";
        OrderCsv.Result r = OrderCsv.read(new StringReader(text), text.length(), null, null);
        assertEquals(1, r.getOrders().size());
        assertEquals(1, r.getOrders().get(0).getLines().size());
        assertEquals(1, r.getSkippedRows());
    }

    @Test
    void csvReadFailsOnAnUnclosedQuote() {
        assertThrows(IOException.class, () -> OrderCsv.read(new StringReader("1,\"Aurelia,x"), 8, null, null));
    }

    @Test
    void csvReadAndWriteReportProgressAndCancel() throws IOException {
        List<Order> orders = OrderGenerator.generate(2000, 3, TODAY, null, null);
        StringWriter out = new StringWriter();
        List<Double> written = new ArrayList<Double>();
        OrderCsv.write(out, orders, written::add, null);
        assertEquals(1.0, written.get(written.size() - 1), 1e-9);
        List<Double> read = new ArrayList<Double>();
        OrderCsv.read(new StringReader(out.toString()), out.getBuffer().length(), read::add, null);
        assertEquals(1.0, read.get(read.size() - 1), 1e-9);
        assertTrue(read.size() > 3, "progress during the read");
        for (int i = 1; i < read.size(); i++) {
            assertTrue(read.get(i) >= read.get(i - 1), "progress only grows");
        }
        assertThrows(CancellationException.class, () -> OrderCsv.write(new StringWriter(), orders, null, () -> true));
        assertThrows(CancellationException.class,
                () -> OrderCsv.read(new StringReader(out.toString()), out.getBuffer().length(), null, () -> true));
    }

    // ---- the store, the customers and the dashboard -----------------------------------------------------------------

    @Test
    void theStoreSavesReplacesAndDeletes() {
        OrderStore store = new OrderStore();
        store.replaceAll(sample());
        int v = store.getVersion();
        assertEquals(6, store.nextId());
        store.save(order(6, "New Person", "2026-04-01", Status.NEW, 100));
        assertEquals(6, store.size());
        store.save(order(2, "Beto Marlowe", "2026-03-01", Status.SHIPPED, 1));
        assertEquals(6, store.size(), "same id: replaced");
        assertEquals(Status.SHIPPED, store.find(2).getStatus());
        store.delete(new HashSet<Integer>(Arrays.asList(1, 3)));
        assertEquals(Arrays.asList(2, 4, 5, 6), ids(store.getOrders()));
        assertTrue(store.getVersion() > v);
        assertNull(store.find(1));
        assertThrows(UnsupportedOperationException.class, () -> store.getOrders().add(null));
    }

    @Test
    void customersAreSummarisedFromTheirOrders() {
        OrderStore store = new OrderStore();
        store.replaceAll(sample());
        List<CustomerSummary> customers = store.customers();
        assertEquals(3, customers.size());
        assertEquals("Aurelia Voss", customers.get(0).getName());
        CustomerSummary marlowe = customers.get(1);
        assertEquals("Beto Marlowe", marlowe.getName());
        assertEquals(2, marlowe.getOrderCount());
        assertEquals(7500, marlowe.getSpentCents(), "the cancelled order does not count");
        assertEquals("2026-03-01", marlowe.getLastOrderText());
        assertEquals(Arrays.asList(2, 4), ids(marlowe.getRecent()));
        assertEquals("2 orders", marlowe.getOrdersText());
        assertEquals(marlowe.getAvatar(), store.customers().get(1).getAvatar());
        assertTrue(marlowe.getAvatar().matches("classpath:orders/avatars/avatar[1-8]\\.png"));
        assertTrue(store.customers() == store.customers(), "cached while the orders do not change");
    }

    @Test
    void dashboardNumbers() {
        List<Order> orders = Arrays.asList(
                order(1, "Aurelia Voss", "2026-11-02", Status.NEW, 10000),
                order(2, "Aurelia Voss", "2026-10-15", Status.PAID, 20000),
                order(3, "Aurelia Voss", "2026-10-01", Status.SHIPPED, 30000),
                order(4, "Aurelia Voss", "2026-10-01", Status.CANCELLED, 90000),
                order(5, "Aurelia Voss", "2025-01-01", Status.SHIPPED, 5000));
        DashboardStats s = new DashboardStats(orders, TODAY, 6);
        assertEquals(5, s.getOrderCount());
        assertEquals(65000, s.getRevenueCents());
        assertEquals(2, s.getOpenOrders());
        assertEquals(16250, s.getAverageCents());
        assertEquals(Arrays.asList("1", "1", "2", "1"),
                Arrays.asList(s.getByStatus().get(0).getValueText(), s.getByStatus().get(1).getValueText(),
                        s.getByStatus().get(2).getValueText(), s.getByStatus().get(3).getValueText()));
        assertEquals(1.0, s.getByStatus().get(2).getFraction(), 1e-9);
        assertEquals(6, s.getByMonth().size());
        assertEquals("Jun", s.getByMonth().get(0).getLabel());
        assertEquals("Nov", s.getByMonth().get(5).getLabel());
        assertEquals("$100", s.getByMonth().get(5).getValueText());
        assertEquals("$500", s.getByMonth().get(4).getValueText());
        assertEquals(1.0, s.getByMonth().get(4).getFraction(), 1e-9);
        assertEquals(0.2, s.getByMonth().get(5).getFraction(), 1e-9);
        assertEquals(0.0, s.getByMonth().get(0).getFraction(), 1e-9, "the 2025 order is outside the six months");
    }

    @Test
    void paletteHasBothThemes() {
        assertEquals("light", Palette.of("light").getName());
        assertEquals("dark", Palette.of("dark").getName());
        assertEquals("light", Palette.of("whatever").getName());
        assertTrue(Palette.of("dark").getBackground() != Palette.of("light").getBackground());
        assertTrue(Palette.of("dark").series(0) != Palette.of("light").series(0));
    }
}
