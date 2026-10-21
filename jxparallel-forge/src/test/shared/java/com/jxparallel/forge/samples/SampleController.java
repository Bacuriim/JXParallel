package com.jxparallel.forge.samples;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.jxparallel.ui.JXComponent;
import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

/**
 * The controller of the sample screens ({@code /samples/*.jxml}), shared by the forge tests and by the
 * mount tests of {@code jxparallel-examples-native}: one getter for everything the samples bind.
 */
public class SampleController {
    public final List<String> calls = new ArrayList<String>();
    public final List<Row> rows = new ArrayList<Row>();
    public String name = "Ada";
    public String pin = "1234";
    public String city = "Recife";
    public String date = "2026-10-21";
    public double rating = 3;
    public String note = "notes";
    public boolean agree;
    public double volume = 2;
    public boolean busy;
    public boolean on = true;
    public boolean open = true;
    public int top = 2;
    public int limit = 3;
    public int page = 1;
    public String choice = "b";
    public double alpha = 0.5;
    public boolean hidden;
    public final int[] pixels = {0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFFFF};

    public SampleController() {
        for (int i = 0; i < 40; i++) {
            rows.add(new Row(i, "Item " + i, i * 1.5, i == 3));
        }
    }

    public int unread = 3;
    public double scroll = 20;
    public double[] positions = {0.3, 0.7};
    public int tabIndex = 1;
    public final List<Integer> closed = new ArrayList<Integer>();

    public int getUnread() {
        return unread;
    }

    public double getScroll() {
        return scroll;
    }

    public void setScroll(double value) {
        scroll = value;
    }

    public double[] getPositions() {
        return positions;
    }

    public void setPositions(double[] value) {
        positions = value;
    }

    public int getTabIndex() {
        return tabIndex;
    }

    public void setTabIndex(int value) {
        tabIndex = value;
    }

    public String[] getTabTitles() {
        return new String[] {"Inbox", "Sent", "Drafts"};
    }

    public void closeTab(int index) {
        closed.add(Integer.valueOf(index));
    }

    public String getTitle() {
        return "Sample";
    }

    public String getSubtitle() {
        return "Second line";
    }

    public double getGap() {
        return 6;
    }

    public boolean getBusy() {
        return busy;
    }

    public boolean getOn() {
        return on;
    }

    public boolean getOpen() {
        return open;
    }

    public boolean getHidden() {
        return hidden;
    }

    public double getAlpha() {
        return alpha;
    }

    public Integer getColor() {
        return Integer.valueOf(0xFF336699);
    }

    public int getTab() {
        return 1;
    }

    public int getTop() {
        return top;
    }

    public int getLimit() {
        return limit;
    }

    public int getPage() {
        return page;
    }

    public int getPageCount() {
        return 12;
    }

    public double getProgress() {
        return 0.25;
    }

    public List<String> getCities() {
        return Arrays.asList("Fortaleza", "Recife");
    }

    public List<Row> getRows() {
        return rows;
    }

    public double[] getWidths() {
        return new double[] {60, -1, 80};
    }

    public int[] getPixels() {
        return pixels;
    }

    public String getName() {
        return name;
    }

    public String getPin() {
        return pin;
    }

    public String getCity() {
        return city;
    }

    public String getDate() {
        return date;
    }

    public double getRating() {
        return rating;
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

    public String getChoice() {
        return choice;
    }

    /** A widget written in Java, hosted by a Slot. */
    public JXComponent getBanner() {
        return new JXComponent() {
            @Override
            public JXElement render() {
                return JXElement.of("#text", JXProps.builder().set("value", "hand-written").build());
            }
        };
    }

    public void save() {
        calls.add("save");
    }

    public void select(Row row) {
        calls.add("select " + row.getName());
    }

    public void setName(String value) {
        name = value;
    }

    public void setPin(String value) {
        pin = value;
    }

    public void setCity(String value) {
        city = value;
    }

    public void setDate(String value) {
        date = value;
    }

    public void setRating(double value) {
        rating = value;
    }

    public void setNote(String value) {
        note = value;
    }

    public void setAgree(boolean value) {
        agree = value;
    }

    public void setVolume(double value) {
        volume = value;
    }

    public void setChoice(String value) {
        choice = value;
    }

    /** One row of the lists and tables. */
    public static class Row {
        private final Integer id;
        private final String name;
        private final double price;
        private final boolean selected;

        Row(int id, String name, double price, boolean selected) {
            this.id = Integer.valueOf(id);
            this.name = name;
            this.price = price;
            this.selected = selected;
        }

        public Integer getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public double getPrice() {
            return price;
        }

        public boolean getSelected() {
            return selected;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
