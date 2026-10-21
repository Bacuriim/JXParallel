package com.jxparallel.forge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Controller of the language tests (For, If, change events): public state, and a log of what ran. */
public class LanguageController {
    public final List<String> calls = new ArrayList<String>();
    public final List<Order> orders = new ArrayList<Order>(Arrays.asList(
            new Order(1, "A", true, "x", "y"), new Order(2, "B", false), new Order(3, "C", true, "z")));
    public boolean admin;
    public String note = "";
    public boolean agree;
    public double volume;
    public double age;
    public String city = "";
    public final Customer customer = new Customer();

    public List<Order> getOrders() {
        return orders;
    }

    public boolean getAdmin() {
        return admin;
    }

    public Customer getCustomer() {
        return customer;
    }

    public String[] getNames() {
        return new String[] {"n1", "n2"};
    }

    public int[] getNumbers() {
        return new int[] {7, 8, 9};
    }

    public String getNote() {
        return note;
    }

    public boolean getAgree() {
        return agree;
    }

    public double getVolume() {
        return volume;
    }

    public void open(Order order) {
        calls.add("open " + order.getName());
    }

    public void rename(Order order, String name) {
        calls.add("rename " + order.getName() + " " + name);
    }

    public void setNote(String value) {
        note = value;
    }

    public void setAgree(boolean value) {
        agree = value;
    }

    public void setAge(double value) {
        age = value;
    }

    public void setCity(String value) {
        city = value;
    }

    public static class Customer {
        public String name = "Ada";
        public boolean active;
        public double score;

        public String getName() {
            return name;
        }

        public void setName(String value) {
            name = value;
        }

        public boolean getActive() {
            return active;
        }

        public void setActive(boolean value) {
            active = value;
        }

        public double getScore() {
            return score;
        }

        public void setScore(double value) {
            score = value;
        }

        public double getRating() {
            return 1;
        }

        /** A setter that does not take the double a slider passes. */
        public void setRating(String value) {
        }

        /** A record-style accessor, which has no setter. */
        public String city() {
            return "Fortaleza";
        }
    }

    public static class Order {
        private final Integer id;
        private String name;
        private final boolean paid;
        private final List<Line> lines = new ArrayList<Line>();

        Order(int id, String name, boolean paid, String... lines) {
            this.id = Integer.valueOf(id);
            this.name = name;
            this.paid = paid;
            for (String line : lines) {
                this.lines.add(new Line(line));
            }
        }

        public Integer getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public void setName(String value) {
            name = value;
        }

        public boolean getPaid() {
            return paid;
        }

        public List<Line> getLines() {
            return lines;
        }
    }

    public static class Line {
        private final String text;

        Line(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }
    }
}
