package com.jxparallel.forge;

import java.util.ArrayList;
import java.util.List;

/** The controller of {@link JxmlLiveTest}: a title, a gap, a flag and a list of values. */
public class LiveController {
    private String title = "title";
    private double gap = 2;
    private boolean flag;
    private List<Double> amounts = new ArrayList<Double>();

    public LiveController() {
        for (int i = 0; i < 5; i++) {
            amounts.add(Double.valueOf(i * 10));
        }
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

    public boolean getFlag() {
        return flag;
    }

    public void setFlag(boolean flag) {
        this.flag = flag;
    }

    public List<Double> getAmounts() {
        return amounts;
    }

    public void setAmounts(List<Double> amounts) {
        this.amounts = amounts;
    }
}
