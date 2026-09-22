package com.jxparallel.properties;

public class JXDoubleProperty extends JXProperty<Double> {
    public JXDoubleProperty() {
        super(Double.valueOf(0.0));
    }

    public JXDoubleProperty(double initialValue) {
        super(Double.valueOf(initialValue));
    }

    public void set(double value) {
        super.set(Double.valueOf(value));
    }

    public double getDouble() {
        return Double.valueOf(super.get());
    }
}
