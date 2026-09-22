package com.jxparallel.javafx.controls;

import javafx.scene.control.Slider;

public class JXSlider implements JXControl {
    private final Slider node = new Slider();

    public JXSlider() {
        JXModernStyle.apply(node, JXVisualVariant.PRIMARY, JXVisualDensity.COMFORTABLE);
    }

    public double getValue() {
        return node.getValue();
    }

    public void setValue(double value) {
        node.setValue(value);
    }

    public double getMin() {
        return node.getMin();
    }

    public void setMin(double value) {
        node.setMin(value);
    }

    public double getMax() {
        return node.getMax();
    }

    public void setMax(double value) {
        node.setMax(value);
    }

    public Slider node() {
        return node;
    }
}
