package com.jxparallel.android.demo;

import java.util.ArrayList;
import java.util.List;

/** State of hello.jxml: the handlers change it, the activity builds the screen again after each touch or key. */
public class HelloController {
    private String name = "";
    private boolean notify;
    private int taps;
    private int picks;
    private String picked = "Nothing picked";
    private String greeting = "Tap the button";
    private final List<String> items = new ArrayList<String>();
    private final List<String> lines = new ArrayList<String>();

    public HelloController() {
        for (int i = 1; i <= 25; i++) {
            items.add("Item " + i);
            lines.add("Line " + i + " of the scroll pane");
        }
    }

    public String getGreeting() {
        return greeting;
    }

    public String getName() {
        return name;
    }

    public void setName(String value) {
        name = value;
    }

    public boolean getNotify() {
        return notify;
    }

    public void setNotify(boolean value) {
        notify = value;
    }

    public String getNotifyText() {
        return notify ? "Notifications are on" : "Notifications are off";
    }

    public String getTaps() {
        return taps + (taps == 1 ? " tap" : " taps");
    }

    public String getPicked() {
        return picked;
    }

    public List<String> getItems() {
        return items;
    }

    public List<String> getLines() {
        return lines;
    }

    public void sayHello() {
        taps++;
        greeting = "Hello, " + (name.isEmpty() ? "world" : name) + "!";
    }

    public void pick(String item) {
        picks++;
        picked = "Picked " + item + " (" + picks + (picks == 1 ? " pick)" : " picks)");
    }
}
