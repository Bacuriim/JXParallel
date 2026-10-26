package com.jxparallel.android.demo;

/** State of hello.jxml: the handlers change it, the activity builds the screen again after each touch or key. */
public class HelloController {
    private String name = "";
    private boolean notify;
    private int taps;
    private String greeting = "Tap the button";

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

    public void sayHello() {
        taps++;
        greeting = "Hello, " + (name.isEmpty() ? "world" : name) + "!";
    }
}
