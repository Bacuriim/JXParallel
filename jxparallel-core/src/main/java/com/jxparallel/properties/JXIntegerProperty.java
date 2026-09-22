package com.jxparallel.properties;

public class JXIntegerProperty extends JXProperty<Integer> {
    public JXIntegerProperty() {
        super(Integer.valueOf(0));
    }

    public JXIntegerProperty(int initialValue) {
        super(Integer.valueOf(initialValue));
    }

    public void set(int value) {
        super.set(Integer.valueOf(value));
    }

    public int getInt() {
        return Integer.valueOf(super.get());
    }
}
