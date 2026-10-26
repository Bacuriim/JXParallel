package com.jxparallel.examples.nativeui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

/** What the live structure tests bind: lists, flags, a window, a memoized list, a slot, and handlers that record their calls. */
public class LiveModel {
    /** One row of the model. */
    public static class Item {
        private final int id;
        private String name;
        private double value;

        public Item(int id, String name, double value) {
            this.id = id;
            this.name = name;
            this.value = value;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public double getValue() {
            return value;
        }

        public void edit(String name, double value) {
            this.name = name;
            this.value = value;
        }
    }

    public final List<String> calls = new ArrayList<String>();
    private List<Item> items = new ArrayList<Item>();
    private List<Double> amounts = new ArrayList<Double>();
    private List<String> names = new ArrayList<String>(Arrays.asList("x", "y", "z"));
    private String title = "title";
    private double gap = 2;
    private double amount = 10;
    private boolean flag;
    private int from;
    private int limit = 3;
    private JXElement banner = JXElement.of("#text", JXProps.builder().set("value", "banner").build());

    public LiveModel() {
        for (int i = 0; i < 6; i++) {
            items.add(new Item(i, "item " + i, i * 10));
            amounts.add(Double.valueOf(i * 7));
        }
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public List<Double> getAmounts() {
        return amounts;
    }

    public void setAmounts(List<Double> amounts) {
        this.amounts = amounts;
    }

    public List<String> getNames() {
        return names;
    }

    public void setNames(List<String> names) {
        this.names = names;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public double getGap() {
        return gap;
    }

    public void setGap(double gap) {
        this.gap = gap;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public boolean getFlag() {
        return flag;
    }

    public void setFlag(boolean flag) {
        this.flag = flag;
    }

    public int getFrom() {
        return from;
    }

    public void setFrom(int from) {
        this.from = from;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public JXElement getBanner() {
        return banner;
    }

    public void setBanner(JXElement banner) {
        this.banner = banner;
    }

    private int[] pixels;

    public int[] getPixels() {
        return pixels;
    }

    public void setPixels(int[] pixels) {
        this.pixels = pixels;
    }

    public void ping() {
        calls.add("ping");
    }

    public void pick(Item item) {
        calls.add("pick " + item.getName());
    }
}
