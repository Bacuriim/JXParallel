package com.jxparallel.ui.native2d;

import java.util.ArrayList;
import java.util.List;

/** The state behind the screens of {@link JxmlComponentBehaviorTest}: what each control reports ends up here. */
public class BehaviorController {
    public int clicks;
    public final List<String> picked = new ArrayList<String>();

    private boolean agree;
    private boolean on;
    private boolean chip;
    public int chipClosed;
    private boolean bold;
    private String align = "left";
    private String name = "";
    private String other = "";
    private String secret = "";
    private String notes = "";
    private double volume = 50;
    private double count = 5;
    private String fruit = "Apple";
    private boolean fruitOpen;
    private String date = "2026-09-15";
    private boolean dateOpen;
    private String time = "10:30";
    private boolean timeOpen;
    private String color = "#FF0000";
    private boolean colorOpen;
    private double bar;
    private double[] split = {0.5};
    private int tab;
    public int tabClosed = -1;
    private int page;
    private boolean expanded = true;
    private boolean dialog = true;
    private boolean drawer = true;
    private boolean snack = true;
    private boolean srcOpen;
    public final List<String> rows = new ArrayList<String>();

    public BehaviorController() {
        for (int i = 0; i < 200; i++) {
            rows.add("Row " + i);
        }
    }

    public void click() {
        clicks++;
    }

    public void pick(String row) {
        picked.add(row);
    }

    public boolean getAgree() { return agree; }
    public void setAgree(boolean v) { agree = v; }
    public boolean getOn() { return on; }
    public void setOn(boolean v) { on = v; }
    public boolean getChip() { return chip; }
    public void setChip(boolean v) { chip = v; }
    public void closeChip() { chipClosed++; }
    public boolean getBold() { return bold; }
    public void toggleBold() { bold = !bold; }
    public String getAlign() { return align; }
    public void setAlign(String v) { align = v; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public String getOther() { return other; }
    public void setOther(String v) { other = v; }
    public String getSecret() { return secret; }
    public void setSecret(String v) { secret = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { notes = v; }
    public double getVolume() { return volume; }
    public void setVolume(double v) { volume = v; }
    public String getCountText() { return String.valueOf((int) count); }
    public double getCount() { return count; }
    public void setCount(double v) { count = v; }
    public String getFruit() { return fruit; }
    public void setFruit(String v) { fruit = v; }
    public boolean getFruitOpen() { return fruitOpen; }
    public void setFruitOpen(boolean v) { fruitOpen = v; }
    public String getDate() { return date; }
    public void setDate(String v) { date = v; }
    public boolean getDateOpen() { return dateOpen; }
    public void setDateOpen(boolean v) { dateOpen = v; }
    public String getTime() { return time; }
    public void setTime(String v) { time = v; }
    public boolean getTimeOpen() { return timeOpen; }
    public void setTimeOpen(boolean v) { timeOpen = v; }
    public String getColor() { return color; }
    public void setColor(String v) { color = v; }
    public boolean getColorOpen() { return colorOpen; }
    public void setColorOpen(boolean v) { colorOpen = v; }
    public double getBar() { return bar; }
    public void setBar(double v) { bar = v; }
    public double[] getSplit() { return split; }
    public void setSplit(double[] v) { split = v; }
    public int getTab() { return tab; }
    public void setTab(int v) { tab = v; }
    public void closeTab(int v) { tabClosed = v; }
    public int getPage() { return page; }
    public void setPage(int v) { page = v; }
    public boolean getExpanded() { return expanded; }
    public void toggleExpanded() { expanded = !expanded; }
    public boolean getDialog() { return dialog; }
    public void setDialog(boolean v) { dialog = v; }
    public boolean getDrawer() { return drawer; }
    public void setDrawer(boolean v) { drawer = v; }
    public boolean getSnack() { return snack; }
    public void setSnack(boolean v) { snack = v; }
    public boolean getSrcOpen() { return srcOpen; }
    public void toggleSrc() { srcOpen = !srcOpen; }
    public List<String> getRows() { return rows; }

    /** The selection of the lists, tables and trees: the row (-1 for none) and, in multiple mode, the rows, and what was told. */
    private int selected = -1;
    private double[] chosen = new double[0];
    public final List<String> told = new ArrayList<String>();
    public int getSelected() { return selected; }
    public void setSelected(int v) { selected = v; told.add("select " + v); }
    public double[] getChosen() { return chosen; }
    public void setChosen(double[] v) { chosen = v; told.add("selection " + java.util.Arrays.toString(v)); }

    private int top;
    public double[] scrolled;
    public int getTop() { return top; }
    public void setTop(int v) { top = v; }
    public void scrolledTo(double[] v) { scrolled = v; }

    public final List<List<String>> drops = new ArrayList<List<String>>();
    public void dropped(List<String> paths) { drops.add(paths); }
    public final List<List<String>> drops2 = new ArrayList<List<String>>();
    public void droppedInner(List<String> paths) { drops2.add(paths); }
    public int saves;
    public void save() { saves++; }
}
